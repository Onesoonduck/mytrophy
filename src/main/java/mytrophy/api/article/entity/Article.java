package mytrophy.api.article.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import mytrophy.api.article.dto.ArticleRequestDto;
import mytrophy.api.article.enumentity.Header;
import mytrophy.api.comment.entity.Comment;
import mytrophy.api.common.base.BaseEntity;
import mytrophy.api.member.entity.Member;

import java.util.List;


@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 생성자를 protected로 설정
public class Article extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 생성되는 값으로 설정
    private Long id;

    @Enumerated(EnumType.STRING)
    private Header header;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false,length = 2000)
    private String content;

    @Column(nullable = true)
    private Integer partySize; // 파티원 수

    @Column(nullable = true)
    private String partyTime; // 파티 시간

    @Column(nullable = true)
    private String partyOption; // 파티 조건

    private int cntUp; // 좋아요 수

    private String imagePath; // 이미지 경로

    private Long appId; // 게임 Id

    private int commentCount; // 댓글 수

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    @JsonIgnoreProperties("articles") // 순환 참조 방지
    private Member member; // 게시글 작성자

    @OneToMany(mappedBy = "article", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Comment> comments;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArticleLike> likes;

    @Builder // 빌더 패턴 적용
    public Article(Long id, Header header, String name, String content, int cntUp, String imagePath, Member member, Long appId, Integer partySize, String partyTime, String partyOption) {
        this.id = id;
        this.header = header;
        this.name = name;
        this.content = content;
        this.cntUp = cntUp;
        this.imagePath = imagePath;
        this.member = member;
        this.appId = appId;
        this.partySize = partySize;
        this.partyTime = partyTime;
        this.partyOption = partyOption;
    }

    // 게시글 생성 로직
    public static Article createArticle(ArticleRequestDto articleRequestDto, Member member, Long appId) {
        return Article.builder()
                .header(articleRequestDto.getHeader())
                .name(articleRequestDto.getName())
                .content(articleRequestDto.getContent())
                .cntUp(0)
                .imagePath(articleRequestDto.getImagePath())
                .member(member)
                .appId(appId)
                .build();
    }

    // 게시글 수정
    public void updateArticle(Header header, String name, String content, Integer partySize, String partyTime, String partyOption, String imagePath, Long appId) {
        this.header = header;
        this.name = name;
        this.content = content;
        this.partySize = partySize;
        this.partyTime = partyTime;
        this.partyOption = partyOption;
        this.imagePath = imagePath;
        this.appId = appId;
    }

    // 좋아요 증가
    public void likeUp() {
        this.cntUp++;
    }

    // 좋아요 감소
    public void likeDown() {
        this.cntUp--;
    }

    // 댓글 수 반환
    public int getCommentCount() {
        if (comments == null) {
            return 0; // comments가 null인 경우 0 반환
        }
        return comments.size(); // comments가 null이 아니라면 리스트의 크기 반환
    }

}
