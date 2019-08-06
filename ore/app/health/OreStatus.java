package health;

public interface OreStatus {

    OreComponentState dbState();

    OreComponentState authState();

    OreComponentState forumState();
}
