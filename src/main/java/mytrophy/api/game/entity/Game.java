package mytrophy.api.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    // 게임 목록의 id를 그대로 entity 저장
    @Id
    private Long id;

    @Column
    private String name;

    @Column(length = 2000)
    private String description;

    @Column
    private String developer;

    @Column
    private String publisher;

    @Column(length = 2000)
    private String requirement;

    @Column
    private Integer price;

    @Column
    private String releaseDate;

    @Column
    private Integer recommendation;

    @Column
    private String headerImagePath;

    @Column
    private Boolean koIsPosible;

    @Column
    private Boolean enIsPosible;

    @Column
    private Boolean jpIsPosible;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "game")
    private List<GameCategory> gameCategoryList;

    // 카테고리를 제외한 나머지는 단방향 맵핑
    @OneToMany(fetch = FetchType.LAZY)
    private List<Achievement> achievementList;

    @OneToMany(fetch = FetchType.LAZY)
    private List<Screenshot> screenshotList;
}
