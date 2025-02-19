package mytrophy.api.game.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QGame is a Querydsl query type for Game
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QGame extends EntityPathBase<Game> {

    private static final long serialVersionUID = 1602302055L;

    public static final QGame game = new QGame("game");

    public final mytrophy.api.common.base.QBaseEntity _super = new mytrophy.api.common.base.QBaseEntity(this);

    public final ListPath<Achievement, QAchievement> achievementList = this.<Achievement, QAchievement>createList("achievementList", Achievement.class, QAchievement.class, PathInits.DIRECT2);

    public final NumberPath<Integer> appId = createNumber("appId", Integer.class);

    public final StringPath averageEmbeddingVector = createString("averageEmbeddingVector");

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    public final StringPath description = createString("description");

    public final StringPath developer = createString("developer");

    public final BooleanPath enIsPossible = createBoolean("enIsPossible");

    public final ListPath<GameCategory, QGameCategory> gameCategoryList = this.<GameCategory, QGameCategory>createList("gameCategoryList", GameCategory.class, QGameCategory.class, PathInits.DIRECT2);

    public final StringPath headerImagePath = createString("headerImagePath");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath jpIsPossible = createBoolean("jpIsPossible");

    public final BooleanPath koIsPossible = createBoolean("koIsPossible");

    public final StringPath name = createString("name");

    public final EnumPath<mytrophy.api.game.enums.Positive> positive = createEnum("positive", mytrophy.api.game.enums.Positive.class);

    public final NumberPath<Integer> price = createNumber("price", Integer.class);

    public final StringPath publisher = createString("publisher");

    public final NumberPath<Integer> recommendation = createNumber("recommendation", Integer.class);

    public final DatePath<java.time.LocalDate> releaseDate = createDate("releaseDate", java.time.LocalDate.class);

    public final StringPath requirement = createString("requirement");

    public final ListPath<Screenshot, QScreenshot> screenshotList = this.<Screenshot, QScreenshot>createList("screenshotList", Screenshot.class, QScreenshot.class, PathInits.DIRECT2);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    public QGame(String variable) {
        super(Game.class, forVariable(variable));
    }

    public QGame(Path<? extends Game> path) {
        super(path.getType(), path.getMetadata());
    }

    public QGame(PathMetadata metadata) {
        super(Game.class, metadata);
    }

}

