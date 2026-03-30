package fr.seedrunner.seedrunner.models;

import org.bukkit.World;

/**
 * Représente les métadonnées d'un monde géré par SeedRunner.
 */
public class WorldData {

    private final String name;
    private final WorldType type;
    private final long seed;
    private final long createdAt;
    private boolean loaded;

    public enum WorldType {
        OVERWORLD("Overworld", World.Environment.NORMAL),
        NETHER("Nether", World.Environment.NETHER),
        END("End", World.Environment.THE_END);

        private final String displayName;
        private final World.Environment environment;

        WorldType(String displayName, World.Environment environment) {
            this.displayName = displayName;
            this.environment = environment;
        }

        public String getDisplayName() { return displayName; }
        public World.Environment getEnvironment() { return environment; }

        public static WorldType fromString(String s) {
            return switch (s.toUpperCase()) {
                case "NETHER", "HELL" -> NETHER;
                case "END", "THE_END", "ENDER" -> END;
                default -> OVERWORLD;
            };
        }

        public String getIcon() {
            return switch (this) {
                case OVERWORLD -> "🌍";
                case NETHER -> "🔥";
                case END -> "🌌";
            };
        }
    }

    public WorldData(String name, WorldType type, long seed) {
        this.name = name;
        this.type = type;
        this.seed = seed;
        this.createdAt = System.currentTimeMillis();
        this.loaded = false;
    }

    public WorldData(String name, WorldType type, long seed, long createdAt, boolean loaded) {
        this.name = name;
        this.type = type;
        this.seed = seed;
        this.createdAt = createdAt;
        this.loaded = loaded;
    }

    public String getName() { return name; }
    public WorldType getType() { return type; }
    public long getSeed() { return seed; }
    public long getCreatedAt() { return createdAt; }
    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }

    public String getFormattedSeed() {
        return seed == 0 ? "Aléatoire" : String.valueOf(seed);
    }
}
