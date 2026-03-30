package fr.seedrunner.seedrunner.managers;

import fr.seedrunner.seedrunner.SeedRunner;
import fr.seedrunner.seedrunner.models.WorldData;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Gère toute la logique de création, chargement, suppression et téléportation de mondes.
 * Compatible Paper 1.21.1 — utilise l'API async world loading quand disponible.
 */
public class WorldManager {

    private final SeedRunner plugin;
    private final Map<String, WorldData> worlds = new LinkedHashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public WorldManager(SeedRunner plugin) {
        this.plugin = plugin;
        setupDataFile();
    }

    // ─────────────────────────────────────────────
    //  Initialisation du fichier de données
    // ─────────────────────────────────────────────

    private void setupDataFile() {
        dataFile = new File(plugin.getDataFolder(), "worlds.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer worlds.yml : " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveDataFile() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde de worlds.yml : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  Chargement des mondes au démarrage
    // ─────────────────────────────────────────────

    public void loadSavedWorlds() {
        if (!dataConfig.contains("worlds")) return;

        for (String name : dataConfig.getConfigurationSection("worlds").getKeys(false)) {
            String path = "worlds." + name + ".";
            WorldData.WorldType type = WorldData.WorldType.fromString(
                    dataConfig.getString(path + "type", "OVERWORLD"));
            long seed = dataConfig.getLong(path + "seed", 0L);
            long createdAt = dataConfig.getLong(path + "createdAt", System.currentTimeMillis());

            WorldData data = new WorldData(name, type, seed, createdAt, false);
            worlds.put(name, data);

            // Auto-charge si activé
            if (plugin.getConfig().getBoolean("auto-load.enabled", true)) {
                loadWorld(name, null);
            }
        }

        plugin.getLogger().info("[SeedRunner] " + worlds.size() + " monde(s) trouvé(s) dans worlds.yml.");
    }

    public void saveAllWorlds() {
        for (WorldData data : worlds.values()) {
            persistWorld(data);
        }
        saveDataFile();
    }

    private void persistWorld(WorldData data) {
        String path = "worlds." + data.getName() + ".";
        dataConfig.set(path + "type", data.getType().name());
        dataConfig.set(path + "seed", data.getSeed());
        dataConfig.set(path + "createdAt", data.getCreatedAt());
    }

    // ─────────────────────────────────────────────
    //  Création d'un monde
    // ─────────────────────────────────────────────

    /**
     * Crée un nouveau monde de manière asynchrone (Paper).
     * @param name   Nom du monde
     * @param type   Type (OVERWORLD, NETHER, END)
     * @param seed   0 = aléatoire
     * @param sender Joueur/console qui a lancé la commande
     * @return CompletableFuture<World>
     */
    public CompletableFuture<World> createWorld(String name, WorldData.WorldType type, long seed, org.bukkit.command.CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();

        if (worlds.containsKey(name)) {
            msg.send(sender, MessageManager.WORLD_EXISTS, "world", name);
            return CompletableFuture.completedFuture(null);
        }

        msg.send(sender, MessageManager.WORLD_CREATING, "world", name);

        WorldCreator creator = new WorldCreator(name);
        creator.environment(type.getEnvironment());

        // Générateur adapté selon le type
        switch (type) {
            case NETHER -> creator.type(WorldType.NORMAL); // Paper gère le nether via environment
            case END    -> creator.type(WorldType.NORMAL);
            default     -> creator.type(WorldType.NORMAL);
        }

        if (seed != 0) creator.seed(seed);

        // Création synchrone mais rapide grâce à Paper
        return CompletableFuture.supplyAsync(() -> {
            try {
                // La création de monde doit être sur le thread principal
                final CompletableFuture<World> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        World world = creator.createWorld();
                        if (world != null) {
                            applyWorldSettings(world);
                            long actualSeed = seed != 0 ? seed : world.getSeed();
                            WorldData data = new WorldData(name, type, actualSeed);
                            data.setLoaded(true);
                            worlds.put(name, data);
                            persistWorld(data);
                            saveDataFile();
                            msg.send(sender, MessageManager.WORLD_CREATED,
                                    "world", name,
                                    "type", type.getDisplayName(),
                                    "seed", String.valueOf(actualSeed));
                            future.complete(world);
                        } else {
                            future.complete(null);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erreur création monde " + name + " : " + e.getMessage());
                        future.completeExceptionally(e);
                    }
                });
                return future.join();
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur async création monde : " + e.getMessage());
                return null;
            }
        });
    }

    // ─────────────────────────────────────────────
    //  Chargement d'un monde existant
    // ─────────────────────────────────────────────

    public CompletableFuture<World> loadWorld(String name, org.bukkit.command.CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();

        WorldData data = worlds.get(name);
        if (data == null) {
            if (sender != null) msg.send(sender, MessageManager.WORLD_NOT_FOUND, "world", name);
            return CompletableFuture.completedFuture(null);
        }

        // Déjà chargé ?
        if (Bukkit.getWorld(name) != null) {
            data.setLoaded(true);
            return CompletableFuture.completedFuture(Bukkit.getWorld(name));
        }

        return CompletableFuture.supplyAsync(() -> {
            final CompletableFuture<World> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                WorldCreator creator = new WorldCreator(name);
                creator.environment(data.getType().getEnvironment());
                if (data.getSeed() != 0) creator.seed(data.getSeed());

                World world = creator.createWorld();
                if (world != null) {
                    applyWorldSettings(world);
                    data.setLoaded(true);
                    if (sender != null) msg.send(sender, MessageManager.WORLD_LOADED, "world", name);
                }
                future.complete(world);
            });
            return future.join();
        });
    }

    // ─────────────────────────────────────────────
    //  Déchargement d'un monde
    // ─────────────────────────────────────────────

    public boolean unloadWorld(String name, org.bukkit.command.CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();
        WorldData data = worlds.get(name);

        if (data == null) {
            if (sender != null) msg.send(sender, MessageManager.WORLD_NOT_FOUND, "world", name);
            return false;
        }

        World world = Bukkit.getWorld(name);
        if (world == null) {
            data.setLoaded(false);
            return true;
        }

        // Téléporte les joueurs hors du monde avant de le décharger
        World fallback = Bukkit.getWorlds().stream()
                .filter(w -> !w.getName().equals(name))
                .findFirst().orElse(null);

        if (fallback != null) {
            for (Player p : world.getPlayers()) {
                p.teleport(fallback.getSpawnLocation());
            }
        }

        boolean success = Bukkit.unloadWorld(world, true);
        if (success) {
            data.setLoaded(false);
            if (sender != null) msg.send(sender, MessageManager.WORLD_UNLOADED, "world", name);
        }
        return success;
    }

    // ─────────────────────────────────────────────
    //  Suppression d'un monde
    // ─────────────────────────────────────────────

    public boolean deleteWorld(String name, org.bukkit.command.CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();

        // Protection du monde principal
        if (Bukkit.getWorlds().get(0).getName().equals(name)) {
            if (sender != null) msg.send(sender, MessageManager.DELETING_DEFAULT);
            return false;
        }

        WorldData data = worlds.get(name);
        if (data == null) {
            if (sender != null) msg.send(sender, MessageManager.WORLD_NOT_FOUND, "world", name);
            return false;
        }

        // Décharge d'abord
        unloadWorld(name, null);

        // Supprime le dossier
        File worldFolder = new File(Bukkit.getWorldContainer(), name);
        if (worldFolder.exists()) deleteFolder(worldFolder);

        // Retire des données
        worlds.remove(name);
        dataConfig.set("worlds." + name, null);
        saveDataFile();

        if (sender != null) msg.send(sender, MessageManager.WORLD_DELETED, "world", name);
        return true;
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteFolder(f);
                else f.delete();
            }
        }
        folder.delete();
    }

    // ─────────────────────────────────────────────
    //  Téléportation vers un monde
    // ─────────────────────────────────────────────

    public void teleportToWorld(Player player, String name) {
        MessageManager msg = plugin.getMessageManager();
        WorldData data = worlds.get(name);

        if (data == null) {
            msg.send(player, MessageManager.WORLD_NOT_FOUND, "world", name);
            return;
        }

        World world = Bukkit.getWorld(name);

        if (world == null) {
            // Charge le monde puis TP
            msg.send(player, MessageManager.WORLD_TP, "world", name);
            loadWorld(name, null).thenAccept(w -> {
                if (w != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.teleport(w.getSpawnLocation()));
                }
            });
        } else {
            msg.send(player, MessageManager.WORLD_TP, "world", name);
            player.teleport(world.getSpawnLocation());
        }
    }

    // ─────────────────────────────────────────────
    //  Paramètres du monde
    // ─────────────────────────────────────────────

    private void applyWorldSettings(World world) {
        FileConfiguration cfg = plugin.getConfig();

        world.setDifficulty(Difficulty.valueOf(
                cfg.getString("world-defaults.difficulty", "NORMAL")));
        world.setSpawnFlags(
                cfg.getBoolean("world-defaults.allow-monsters", true),
                cfg.getBoolean("world-defaults.allow-animals", true));
        world.setPVP(cfg.getBoolean("world-defaults.pvp", true));
        world.setAutoSave(true);
    }

    // ─────────────────────────────────────────────
    //  Reset universel : /runner reset [seed]
    //  Crée un overworld, nether et end frais,
    //  supprime tous les anciens mondes SeedRunner,
    //  TP + reset tous les joueurs en ligne.
    // ─────────────────────────────────────────────

    /**
     * Lance un reset complet :
     * 1. Supprime tous les mondes actuellement gérés par SeedRunner.
     * 2. Crée trois nouveaux mondes : overworld, nether, end avec la seed donnée.
     * 3. Téléporte tous les joueurs dans le nouvel overworld.
     * 4. Réinitialise leur inventaire, armure, effets, stats et gamemode.
     *
     * @param seed   0 = seed aléatoire
     * @param sender Initiateur de la commande
     */
    public void resetAll(long seed, org.bukkit.command.CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();

        final String base   = "runner";
        final String ovName = base;
        final String neName = base + "_nether";
        final String enName = base + "_the_end";

        // ── 1. TP immédiat dans le lobby ──────────────────────────────────
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getLobbyManager().sendToLobby(player);
        }
        // Un seul message broadcast pour tous
        Bukkit.broadcast(msg.parse("<dark_gray>[<gold>Runner</gold>]</dark_gray> <gray>Changement de map en cours..."));

        // ── 2. Suppression des anciens mondes SeedRunner ──────────────────
        List<String> toDelete = new ArrayList<>(worlds.keySet());
        for (String name : toDelete) {
            deleteWorld(name, null);
        }

        // ── 3. Création des 3 nouveaux mondes sur le main thread ──────────
        Bukkit.getScheduler().runTask(plugin, () -> {
            World overworld = createWorldSync(ovName, WorldData.WorldType.OVERWORLD, seed, sender);
            World nether    = createWorldSync(neName, WorldData.WorldType.NETHER,    seed, sender);
            World end       = createWorldSync(enName, WorldData.WorldType.END,       seed, sender);

            if (overworld == null) {
                msg.send(sender, "<dark_gray>[<gold>Runner</gold>]</dark_gray> <red>Échec de la création de l'overworld.");
                return;
            }

            registerInBukkitYml(ovName, neName, enName, sender);

            // ── 4. TP + reset dans l'overworld ────────────────────────────
            for (Player player : Bukkit.getOnlinePlayers()) {
                resetPlayer(player);
                player.teleport(overworld.getSpawnLocation());
            }

            // Reset et démarrage du chrono
            plugin.getChronoScoreboard().resetAndStart();

            // Reset des annonces de dimension (nether/end)
            plugin.getDimensionAnnounceListener().resetAnnouncements();

            Bukkit.broadcast(msg.parse(
                    "<dark_gray>[<gold>Runner</gold>]</dark_gray> <green>Run started"));
        });
    }

    /**
     * Déclare les trois mondes runner dans bukkit.yml.
     * Paper utilise la convention <overworld>_nether / <overworld>_the_end
     * pour résoudre les destinations de portails automatiquement.
     * Cette méthode s'assure juste que les entrées existent dans bukkit.yml
     * pour éviter tout warning au démarrage.
     */
    private void registerInBukkitYml(String ovName, String neName, String enName,
                                      org.bukkit.command.CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();

        // bukkit.yml est toujours à la racine du serveur (parent du world-container)
        File serverRoot = Bukkit.getWorldContainer().getAbsoluteFile().getParentFile();
        if (serverRoot == null) serverRoot = new File(".");
        File bukkitYml = new File(serverRoot, "bukkit.yml");
        if (!bukkitYml.exists()) bukkitYml = new File("bukkit.yml");

        try {
            YamlConfiguration bukkit = YamlConfiguration.loadConfiguration(bukkitYml);

            // Déclare chaque monde avec son environment — Paper s'en sert
            // pour valider les portails et éviter les warnings "unregistered world"
            if (!bukkit.contains("worlds." + ovName)) {
                bukkit.set("worlds." + ovName + ".generator", (Object) null);
            }
            if (!bukkit.contains("worlds." + neName)) {
                bukkit.set("worlds." + neName + ".generator", (Object) null);
            }
            if (!bukkit.contains("worlds." + enName)) {
                bukkit.set("worlds." + enName + ".generator", (Object) null);
            }

            bukkit.save(bukkitYml);

        } catch (Exception e) {
            plugin.getLogger().warning("[SeedRunner] Impossible de modifier bukkit.yml : " + e.getMessage());
            // Non bloquant — la convention de nommage suffit pour les portails
        }
    }

    /**
     * Crée un monde de manière synchrone (doit être appelé depuis le main thread).
     */
    private World createWorldSync(String name, WorldData.WorldType type, long seed,
                                   org.bukkit.command.CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();

        // Sécurité : supprime d'éventuels restes
        if (worlds.containsKey(name)) deleteWorld(name, null);

        WorldCreator creator = new WorldCreator(name);
        creator.environment(type.getEnvironment());
        creator.type(WorldType.NORMAL);
        creator.generateStructures(
                plugin.getConfig().getBoolean("world-defaults.generate-structures", true));
        if (seed != 0) creator.seed(seed);

        try {
            World world = creator.createWorld();
            if (world != null) {
                applyWorldSettings(world);
                long actualSeed = (seed != 0) ? seed : world.getSeed();
                WorldData data = new WorldData(name, type, actualSeed);
                data.setLoaded(true);
                worlds.put(name, data);
                persistWorld(data);
                saveDataFile();
            }
            return world;
        } catch (Exception e) {
            plugin.getLogger().severe("[SeedRunner] Erreur création " + name + " : " + e.getMessage());
            return null;
        }
    }

    /**
     * Réinitialise complètement un joueur :
     * inventaire, armure, offhand, effets, stats vitales et gamemode.
     */
    private void resetPlayer(Player player) {
        FileConfiguration cfg = plugin.getConfig();

        // Inventaire & équipement
        player.getInventory().clear();
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        // Effets de potion
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

        // Stats vitales
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExp(0);
        player.setLevel(0);
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setFallDistance(0);

        // Gamemode
        String gm = cfg.getString("world-defaults.gamemode", "SURVIVAL");
        try {
            player.setGameMode(GameMode.valueOf(gm.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    // ─────────────────────────────────────────────
    //  Getters / utilitaires
    // ─────────────────────────────────────────────

    public Map<String, WorldData> getWorlds() { return Collections.unmodifiableMap(worlds); }

    public WorldData getWorldData(String name) { return worlds.get(name); }

    public boolean worldExists(String name) { return worlds.containsKey(name); }

    public List<String> getWorldNames() { return new ArrayList<>(worlds.keySet()); }

    /**
     * Retourne le "base name" SeedRunner d'un monde, ou null si ce monde
     * n'est pas géré par SeedRunner.
     *
     * Exemples :
     *   "runner"          → "runner"
     *   "runner_nether"   → "runner"
     *   "runner_the_end"  → "runner"
     *   "world"           → null  (monde vanilla, non géré)
     *
     * Utilisé par PortalListener pour déterminer si on doit intercepter
     * un PlayerPortalEvent et vers quel monde rediriger.
     */
    public String getRunnerBase(String worldName) {
        // Vérifie d'abord si c'est directement un monde SeedRunner
        if (worlds.containsKey(worldName)) {
            // C'est l'overworld runner lui-même → la base c'est son nom
            // mais seulement si c'est un OVERWORLD
            WorldData data = worlds.get(worldName);
            if (data.getType() == fr.seedrunner.seedrunner.models.WorldData.WorldType.OVERWORLD) {
                return worldName;
            }
            // C'est un nether ou end runner : dérive la base depuis le nom
        }

        // Essaie de dériver la base depuis le suffixe
        if (worldName.endsWith("_nether")) {
            String base = worldName.substring(0, worldName.length() - "_nether".length());
            if (worlds.containsKey(base) || worlds.containsKey(worldName)) {
                return base;
            }
        }
        if (worldName.endsWith("_the_end")) {
            String base = worldName.substring(0, worldName.length() - "_the_end".length());
            if (worlds.containsKey(base) || worlds.containsKey(worldName)) {
                return base;
            }
        }

        // Dernier recours : le monde est-il dans la map SeedRunner
        // (nether ou end) ? On cherche par nom exact.
        if (worlds.containsKey(worldName)) {
            WorldData data = worlds.get(worldName);
            // Nether : retire "_nether" depuis le nom enregistré
            if (data.getType() == fr.seedrunner.seedrunner.models.WorldData.WorldType.NETHER
                    && worldName.endsWith("_nether")) {
                return worldName.substring(0, worldName.length() - "_nether".length());
            }
            if (data.getType() == fr.seedrunner.seedrunner.models.WorldData.WorldType.END
                    && worldName.endsWith("_the_end")) {
                return worldName.substring(0, worldName.length() - "_the_end".length());
            }
        }

        return null; // monde non géré par SeedRunner
    }
}
