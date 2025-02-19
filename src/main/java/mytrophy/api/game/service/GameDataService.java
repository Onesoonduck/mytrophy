package mytrophy.api.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mytrophy.api.game.entity.*;
import mytrophy.api.game.dto.ResponseDTO.GetGamePlayerNumberDTO;
import mytrophy.api.game.enums.Positive;
import mytrophy.api.game.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Transactional
public class GameDataService {

    private final GameRepository gameRepository;
    private final AchievementRepository achievementRepository;
    private final CategoryRepository categoryRepository;
    private final ScreenshotRepository screenshotRepository;
    private final GameCategoryRepository gameCategoryRepository;
    private final GameDataRepository gameDataRepository;
    private final GameReadRepository gameReadRepository;
    private final TopGameRepository topGameRepository;

    // application.properties에서 설정된 값 주입
    @Value("${steam.api-key}")
    private String steamKey;

    @Autowired
    public GameDataService(GameRepository gameRepository, AchievementRepository achievementRepository,
                           CategoryRepository categoryRepository, ScreenshotRepository screenshotRepository,
                           GameCategoryRepository gameCategoryRepository, GameDataRepository gameDataRepository,
                           GameReadRepository gameReadRepository, TopGameRepository topGameRepository) {
        this.gameRepository = gameRepository;
        this.achievementRepository = achievementRepository;
        this.categoryRepository = categoryRepository;
        this.screenshotRepository = screenshotRepository;
        this.gameCategoryRepository = gameCategoryRepository;
        this.gameDataRepository = gameDataRepository;
        this.gameReadRepository = gameReadRepository;
        this.topGameRepository = topGameRepository;
    }

    // 스팀 게임 목록을 받아와 DB에 저장하는 메서드
    public void receiveSteamGameList() throws JsonProcessingException {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://api.steampowered.com/ISteamApps/GetAppList/v2/";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        JsonNode rootNode = new ObjectMapper().readTree(response.getBody());
        JsonNode appsNode = rootNode.get("applist").get("apps");

        List<GameData> gameDataList = new ArrayList<>();
        List<Integer> gameDataListCheck = new ArrayList<>();

        for (JsonNode appNode : appsNode){
            int id = appNode.get("appid").asInt();
            if(!gameDataRepository.existsByAppId(id) && !gameDataListCheck.contains(id)){
                gameDataList.add(new GameData(null, id));
                gameDataListCheck.add(id);
            }
        }
        gameDataRepository.saveAll(gameDataList);
    }

    public Boolean receiveSteamGameListByDb(int size, boolean isContinue) throws JsonProcessingException {
        // 마지막에 저장한 appId 불러오기
        List<GameRead> gameReadList = gameReadRepository.findAll();

        GameRead gameRead =
                (!gameReadList.isEmpty())?gameReadList.get(0):new GameRead(1L,0);

        // 스팀에서 받아온 모든 게임목록 불러온 후 오름차순 정렬
        List<GameData> gameDataList = gameDataRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(GameData::getId))
                .toList();

        // 마지막에 저장한 appId가 null 이거나 사용자가 직접 선택한 경우 처음부터 순회하며 다운
        int startIndex = 0;
        if (gameRead != null && isContinue && gameRead.getLastAppId() != 0) {
            GameData gameData = gameDataRepository.findByAppId(gameRead.getLastAppId());
            if (gameData != null) {
                startIndex = gameDataList.indexOf(gameData) + 1;
            }
        }
        System.out.println("마지막 다운 위치 : " + (startIndex - 1));


        // DB에 있는 스팀게임 목록을 스팀에 요청하여 다운받기
        int count = 1;
        int currentCount = 0;
        for (int i = startIndex; i < gameDataList.size(); i++) {
            int appId = gameDataList.get(i).getAppId();
            System.out.println("다운받는 APP-ID : " + appId);
            System.out.println("다운받는 APP의 위치 : " + i);
            gameDetail(appId);
            gameReadRepository.save(new GameRead(1L,appId));
            if(count >= size) break;
            count++;
            currentCount = i;
        }

        if (currentCount == gameDataList.size()) {
            return true;
        }

        // false 로 바꾸기 테스트 하는동안 true
        return false;
    }

    // 스팀 게임 top100 목록을 저장하는 메서드
    public void receiveTopSteamGameList(int size) throws JsonProcessingException {

        RestTemplate restTemplate = new RestTemplate();
        String url = "https://steamspy.com/api.php?request=top100in2weeks";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        JsonNode rootNode = new ObjectMapper().readTree(response.getBody());

        int count = 1;
        List<TopGameRead> appList = new ArrayList<>();

        for (JsonNode appNode : rootNode) {
            int appId = appNode.get("appid").asInt();
            System.out.println("Rank : " +  count);
            System.out.println(appId);
            TopGameRead topGameRead = new TopGameRead(Long.valueOf(count),appId);
            gameDetail(appId);
            appList.add(topGameRead);
            if (count >= size) break;
            count++;
        }
        topGameRepository.saveAll(appList);
    }

    // 특정 게임의 상세 정보를 받아와 DB에 저장하는 메서드
    public void gameDetail(int appId) throws JsonProcessingException {
        if(gameRepository.existsByAppId(appId)) return;
        String url = "https://store.steampowered.com/api/appdetails?appids=" + appId + "&l=korean";
        JsonNode appNode = getAppNodeFromUrl(url, String.valueOf(appId));
        if (appNode == null) {
            return;
        }
        // 게임이 아닐경우 다음 앱 검색
        Boolean isSuccess = appNode.get("success").asBoolean();
        if(!isSuccess){
            return;
        }
        String type = appNode.get("data").get("type").asText();
        if(!type.equals("game")){
            return;
        }

        // JSON 데이터를 가지고 게임 엔티티를 생성하고 저장
        Game game = createGameFromJson(appNode.get("data"), appId);
        // 출시 예정 게임이므로 건너뛰기
        if (game.getReleaseDate() == null) {
            return;
        }

        List<Achievement> gameAchievementList = game.getAchievementList();
        if (gameAchievementList == null) {
            gameAchievementList = new ArrayList<>();
        }
        gameAchievementList.addAll(achievementRepository.saveAll(saveGameAchievement(appId)));
        game.setAchievementList(gameAchievementList);

        List<Screenshot> gameScreenshotList = game.getScreenshotList();
        if (gameScreenshotList == null) {
            gameScreenshotList = new ArrayList<>();
        }
        gameScreenshotList.addAll(screenshotRepository.saveAll(saveGameScreenshot(appNode.get("data").get("screenshots"))));
        game.setScreenshotList(gameScreenshotList);

        game = gameRepository.save(game);

        // 받아온 게임의 카테고리 연결하여 DB에 저장
        saveGameCategory(appNode.get("data").get("genres"),game);
        saveGameGenres(appNode.get("data").get("categories"),game);
    }


    // 주어진 URL로부터 JSON 데이터를 받아와서 해당 앱의 노드를 반환
    private JsonNode getAppNodeFromUrl(String url, String strId) {
        try {
            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.GET, null, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode rootNode = new ObjectMapper().readTree(response.getBody());
                if(strId.equals("0")){
                    return rootNode.get("game");
                }
                return rootNode.get(strId);
            } else {
                // 서버에서 OK 상태 코드를 반환하지 않았을 때는 null을 반환
                return null;
            }
        } catch (Exception e) {
            // 예외가 발생했을 때는 null을 반환
            return null;
        }
    }
    // JSON 데이터에서 게임 정보를 추출하여 게임 엔티티를 생성
    private Game createGameFromJson(JsonNode appNode, int appId) throws JsonProcessingException {
        String name = appNode.hasNonNull("name") ? appNode.get("name").asText() : null;
        String description = appNode.hasNonNull("short_description") ? appNode.get("short_description").asText() : null;

        // 게임 개발사
        String gameDeveloper = "";
        JsonNode developersNode = appNode.get("developers");
        if (developersNode != null && developersNode.isArray() && developersNode.size() > 0) {
            for (JsonNode developerNode : developersNode) {
                gameDeveloper += developerNode.asText();
                gameDeveloper += "&";
            }
        }

        // 게임 공급사
        String gamePublisher = "";
        JsonNode publishersNode = appNode.get("publishers");
        if (publishersNode != null && publishersNode.isArray() && publishersNode.size() > 0) {
            for (JsonNode publisherNode : publishersNode) {
                gamePublisher += publisherNode.asText();
                gamePublisher += "&";
            }
        }

        // 지원하는 언어
        String languages = appNode.hasNonNull("supported_languages") ? appNode.get("supported_languages").asText() : null;
        List<Boolean> checkList = languagePosible(languages);
        Boolean enPossible = checkList.get(0);
        Boolean koPossible = checkList.get(1);
        Boolean jpPossible = checkList.get(2);

        // 출시 날짜
        LocalDate date = null;
        String dateString = appNode.hasNonNull("release_date") ? appNode.get("release_date").get("date").asText() : null;
        Boolean commingsoon = appNode.hasNonNull("release_date") ? appNode.get("release_date").get("coming_soon").asBoolean() : true;
        if(!commingsoon) date = convertToDate(dateString);

        // 추천수
        Integer recommandation = appNode.hasNonNull("recommendations") ? appNode.get("recommendations").get("total").asInt() : 0;

        // 헤어 이미지x
        String headerImagePath = appNode.hasNonNull("header_image") ? appNode.get("header_image").asText() : null;

        // 게임 가격
        Boolean isFree = appNode.hasNonNull("is_free") ? appNode.get("is_free").asBoolean() : null;
        Integer price;
        if (isFree != null && isFree) {
            price = 0;
        } else {
            price = appNode.hasNonNull("price_overview") ?
                    appNode.get("price_overview").get("final").asInt() / 100 : null;
        }


        Positive positive = getGamePositiveNumber(appId);

        // 컴퓨터 권장 사양
        JsonNode requirementHader = appNode.hasNonNull("pc_requirements") ? appNode.get("pc_requirements") : null;
        String requirement = requirementHader.hasNonNull("minimum") ? requirementHader.get("minimum").asText() : null;

        Game target = gameRepository.findByAppId(appId);

        if (target != null) {
            target.setName(name);
            target.setDescription(description);
            target.setDeveloper(gameDeveloper);
            target.setPublisher(gamePublisher);
            target.setRequirement(requirement);
            target.setPrice(price);
            target.setReleaseDate(date);
            target.setRecommendation(recommandation);
            target.setPositive(positive);
            target.setHeaderImagePath(headerImagePath);
            target.setKoIsPossible(koPossible);
            target.setEnIsPossible(enPossible);
            target.setJpIsPossible(jpPossible);
            return target;
        }
        return new Game(null, appId, name, description, gameDeveloper, gamePublisher, requirement, price, date, recommandation, positive, headerImagePath, koPossible, enPossible, jpPossible, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);
    }

    // 게임 업적을 받아와서 업적 리스트를 반환하는 메서드
    private List<Achievement> saveGameAchievement(int appId) {
        try {
            String url = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=" + steamKey + "&appid=" + appId + "&l=koreana";
            JsonNode rootNode = getAppNodeFromUrl(url, "0");
            JsonNode achievementsNode = (rootNode != null && rootNode.has("availableGameStats")) ?
                    rootNode.get("availableGameStats").get("achievements") :
                    null;
            return achievementsNode != null ? parseAchievements(achievementsNode) : Collections.emptyList();
        } catch (Exception e) {
            // 서버에서 OK 상태 코드를 반환하지 않았을 때는 null을 반환
            return null;
        }
    }


    // JSON 데이터에서 업적 정보를 추출하여 업적 리스트를 반환하는 메서드
    private List<Achievement> parseAchievements(JsonNode achievementsNode) {
        List<Achievement> achievementList = new ArrayList<>();
        boolean isExist;
        for (JsonNode achievementNode : achievementsNode) {
            String name = achievementNode.get("displayName").asText();
            String imagePath = achievementNode.get("icon").asText();
            JsonNode hiddenNode = achievementNode.get("hidden");
            Boolean hidden = hiddenNode != null && hiddenNode.asBoolean();
            JsonNode descriptionNode = achievementNode.get("description");
            String description = descriptionNode != null ? descriptionNode.asText() : null;
            if (hidden && description == null) {
                description = "숨겨진 업적 입니다.";
            }
            isExist = achievementRepository.existsByName(name);
            if(!isExist)achievementList.add(new Achievement(null, name, imagePath,hidden,description));
        }
        return achievementList;
    }

    // JSON 데이터에서 스크린샷 정보를 추출하여 스크린샷 리스트를 반환하는 메서드
    private List<Screenshot> saveGameScreenshot(JsonNode appsNode) {
        List<Screenshot> screenshotList = new ArrayList<>();
        boolean isExist;
        if (appsNode == null) {
            return Collections.emptyList();
        }
        for (JsonNode appNode : appsNode) {
            String thumbnailImagePath = appNode.get("path_thumbnail").asText();
            String fullImagePath = appNode.get("path_full").asText();
            isExist = screenshotRepository.existsByThumbnailImagePathAndFullImagePath(thumbnailImagePath, fullImagePath);
            if(!isExist) screenshotList.add(new Screenshot(null, thumbnailImagePath, fullImagePath));
        }
        return screenshotList;
    }

    public List<Boolean> languagePosible(String text) {

        List<Boolean> list = new ArrayList<>();

        if (text == null) {
            list.add(false);
            list.add(false);
            list.add(false);
            return list;
        }

        // "영어"가 포함되어 있는지 검사
        if (text.contains("영어")) {
            list.add(true);
        } else {
            list.add(false);
        }

        // "한국어"가 포함되어 있는지 검사
        if (text.contains("한국어")) {
            list.add(true);
        } else {
            list.add(false);
        }

        // "일본어"가 포함되어 있는지 검사
        if (text.contains("일본어")) {
            list.add(true);
        } else {
            list.add(false);
        }

        return list;
    }

    public void readCategoryList() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Resource resource = new ClassPathResource("static/json/genres.json");
            InputStream inputStream = resource.getInputStream();
            JsonNode jsonNode = objectMapper.readTree(inputStream);

            Map<Integer, String> idNameMap = new HashMap<>();
            jsonNode.fields().forEachRemaining(entry -> {
                idNameMap.put(Integer.parseInt(entry.getKey()), entry.getValue().asText());
            });

            idNameMap.forEach((id, name) -> categoryRepository.save(saveCategoryToDb(id,name)));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Category saveCategoryToDb(int receiveId, String name) {
        Long id = (long) receiveId;
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    @Transactional
    public void saveGameCategory(JsonNode appsNode, Game game) {
        // null 값이면 바로 종료
        if (appsNode == null) return;

        // 엔티티 병합
        game = gameRepository.findById(game.getId()).orElse(game);

        if (game.getGameCategoryList() == null) {
            game.setGameCategoryList(new ArrayList<>());
        }

        // 현재 게임의 카테고리 목록을 복사하여 임시 저장
        List<GameCategory> currentCategories = new ArrayList<>(game.getGameCategoryList());

        // 새로운 카테고리 목록을 생성
        List<GameCategory> newCategories = new ArrayList<>();

        // 게임과 카테고리 연결
        for (JsonNode appNode : appsNode) {
            Long categoryId = appNode.get("id").asLong() + 100L;
            String categoryName = appNode.get("description").asText();

            Category existingCategory = categoryRepository.findById(categoryId).orElse(null);
            if (existingCategory == null) {
                existingCategory = new Category(categoryId, categoryName, null, null);
                existingCategory = categoryRepository.save(existingCategory);
            }

            // 새로운 GameCategory 객체 생성
            GameCategory gameCategory = new GameCategory();
            gameCategory.setGame(game);
            gameCategory.setCategory(existingCategory);

            // 새로운 카테고리 목록에 추가
            newCategories.add(gameCategory);
        }

        // 게임의 카테고리 목록을 새로운 카테고리 목록으로 교체
        game.getGameCategoryList().clear();
        game.getGameCategoryList().addAll(newCategories);

        // 변경사항 저장
        gameRepository.save(game);
    }


    private void saveGameGenres(JsonNode appsNode, Game game) {

        // null 값이면 바로 종료
        if (appsNode == null) return;

        // 엔티티 병합
        game = gameRepository.findById(game.getId()).orElse(game);

        if (game.getGameCategoryList() == null) {
            game.setGameCategoryList(new ArrayList<>());
        }

        // 현재 게임의 카테고리 목록을 복사하여 임시 저장
        List<GameCategory> currentCategories = new ArrayList<>(game.getGameCategoryList());

        // 새로운 카테고리 목록을 생성
        List<GameCategory> newCategories = new ArrayList<>();

        // 게임과 카테고리 연결
        for (JsonNode appNode : appsNode) {
            Long categoryId = appNode.get("id").asLong();
            String categoryName = appNode.get("description").asText();

            Category existingCategory = categoryRepository.findById(categoryId).orElse(null);
            if (existingCategory == null) {
                existingCategory = new Category(categoryId, categoryName, null, null);
                existingCategory = categoryRepository.save(existingCategory);
            }

            // 새로운 GameCategory 객체 생성
            GameCategory gameCategory = new GameCategory();
            gameCategory.setGame(game);
            gameCategory.setCategory(existingCategory);

            // 새로운 카테고리 목록에 추가
            newCategories.add(gameCategory);
        }

        // 게임의 카테고리 목록을 새로운 카테고리 목록으로 교체
        game.getGameCategoryList().clear();
        game.getGameCategoryList().addAll(newCategories);

        // 변경사항 저장
        gameRepository.save(game);
    }

    public static LocalDate convertToDate(String dateString) {
        try {
            // 날짜 형식 지정
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
            // 문자열을 LocalDate로 변환하여 반환
            return LocalDate.parse(dateString, formatter);
        } catch (DateTimeParseException e) {
            // 예외 발생 시 기본값으로 null 반환
            return null;
        }
    }

    public GetGamePlayerNumberDTO getGamePlayerNumber(String id) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?key=" + steamKey + "&appid=" + id;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            JsonNode rootNode = new ObjectMapper().readTree(response.getBody());
            Integer result = rootNode.get("response").get("result").asInt();
            String playerNumber = (result == 1) ? rootNode.get("response").get("player_count").asText() : "조회 불가 게임";
            return new GetGamePlayerNumberDTO(playerNumber);
        } catch (HttpClientErrorException.NotFound e) {
            // 404 에러가 발생할 경우 처리
            return new GetGamePlayerNumberDTO("조회 불가 게임");
        } catch (JsonProcessingException e) {
            // JSON 처리 오류가 발생할 경우 처리
            e.printStackTrace();
            return new GetGamePlayerNumberDTO("조회 불가 게임");
        } catch (Exception e) {
            // 기타 예외 발생 시 처리
            e.printStackTrace();
            return new GetGamePlayerNumberDTO("조회 불가 게임");
        }
    }

    public Positive getGamePositiveNumber(Integer id) throws JsonProcessingException {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://steamspy.com/api.php?request=appdetails&appid=" + id + "&l=korean";

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("SteamSpy API에서 게임 정보를 가져오는 데 실패했습니다");
            }

            JsonNode rootNode = new ObjectMapper().readTree(response.getBody());
            JsonNode positiveNode = rootNode.path("positive");
            JsonNode negativeNode = rootNode.path("negative");

            if (positiveNode.isMissingNode() || negativeNode.isMissingNode()) {
                throw new RuntimeException("API 응답에서 긍정적 또는 부정적인 리뷰 수를 찾을 수 없습니다");
            }

            int positive = positiveNode.asInt();
            int negative = negativeNode.asInt();

            if (positive + negative == 0) {
                return Positive.UNKNOWN; // 리뷰 없음
            }

            int result = positive * 100 / (positive + negative);

            if(positive+negative>500 && result>=95){
                return Positive.OVERWHELMING_POSITIVE;
            }else if (result >= 80) {
                return Positive.VERY_POSITIVE;
            } else if (result >= 70) {
                return Positive.MOSTLY_POSITIVE;
            } else if (result >= 40) {
                return Positive.MIXED;
            } else if (result >= 20) {
                return Positive.MOSTLY_NEGATIVE;
            }
            return Positive.MOSTLY_NEGATIVE;

        } catch (Exception e) {
            // 에러 메시지 한국어로 반환
            throw new RuntimeException("게임 평가를 가져오는 도중에 오류가 발생했습니다");
        }

    }



}
