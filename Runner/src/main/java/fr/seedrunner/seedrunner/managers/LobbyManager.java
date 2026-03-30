package fr.seedrunner.seedrunner.managers;

import fr.seedrunner.seedrunner.SeedRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;

/**
 * LobbyManager — gère le monde lobby temporaire de SeedRunner.
 *
 * ═══════════════════════════════════════════════════════════════════
 * RÔLE
 * ═══════════════════════════════════════════════════════════════════
 *
 * Lors d'un /run, les joueurs sont immédiatement TP dans le lobby
 * pendant que les 3 mondes runner sont créés.
 * Une fois la création terminée, ils sont TP dans runner.
 *
 * ═══════════════════════════════════════════════════════════════════
 * STRUCTURE DU LOBBY
 * ═══════════════════════════════════════════════════════════════════
 *
 * - Monde void (superflat vide)
 * - Plateforme 10×10 en BARRIER (invisible mais solide)
 *   centrée en (0, 64, 0)
 * - Pancarte colorée en (0, 65, -3) face au joueur (au nord de la plateforme)
 *   Clic droit → TP dans runner si disponible
 * - World border de 22 blocs — joueurs confinés sur la plateforme
 * - Pas de monstres, pas de dégâts, heure fixe
 *
 * ═══════════════════════════════════════════════════════════════════
 * PANCARTE
 * ═══════════════════════════════════════════════════════════════════
 *
 * La pancarte est un OAK_WALL_SIGN placé contre un BARRIER invisible.
 * Elle indique "Rejoindre la map" et est cliquable (PlayerInteractEvent
 * géré dans LobbySignListener).
 * On utilise l'API Paper Sign pour écrire des lignes colorées via
 * Adventure components.
 *
 * ═══════════════════════════════════════════════════════════════════
 */
public class LobbyManager {

    public static final String LOBBY_WORLD_NAME = "seedrunner_lobby";

    // Position de la pancarte (face south, visible depuis le spawn)
    public static final int SIGN_X = 0;
    public static final int SIGN_Y = 66;
    public static final int SIGN_Z = -3;

    private static final int    PLATFORM_HALF = 15;
    private static final int    PLATFORM_Y    = 64;
    private static final double BORDER_SIZE   = 22;

    private final SeedRunner plugin;
    private World lobbyWorld;

    public LobbyManager(SeedRunner plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────
    //  Initialisation
    // ─────────────────────────────────────────────

    public void initLobby() {
        lobbyWorld = Bukkit.getWorld(LOBBY_WORLD_NAME);
        if (lobbyWorld != null) {
            ensurePlatform();
            applyLobbySettings();
            ensureSign();
            return;
        }

        WorldCreator creator = new WorldCreator(LOBBY_WORLD_NAME);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[],\"biome\":\"minecraft:the_void\"}");
        creator.generateStructures(false);

        lobbyWorld = creator.createWorld();

        if (lobbyWorld == null) {
            plugin.getLogger().severe("[SeedRunner] Impossible de créer le monde lobby !");
            return;
        }

        applyLobbySettings();
        buildPlatform();
        setSpawn();
        placeSign();

        plugin.getLogger().info("[SeedRunner] Monde lobby créé.");
    }

    // ─────────────────────────────────────────────
    //  Paramètres du lobby
    // ─────────────────────────────────────────────

    private void applyLobbySettings() {
        if (lobbyWorld == null) return;
        lobbyWorld.setDifficulty(Difficulty.PEACEFUL);
        lobbyWorld.setSpawnFlags(false, false);
        lobbyWorld.setPVP(false);
        lobbyWorld.setAutoSave(false);
        lobbyWorld.setTime(6000);
        lobbyWorld.setStorm(false);
        lobbyWorld.setThundering(false);
        lobbyWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        lobbyWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        lobbyWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        lobbyWorld.setGameRule(GameRule.FALL_DAMAGE, false);
        lobbyWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);

        WorldBorder border = lobbyWorld.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(BORDER_SIZE);
        border.setWarningDistance(0);
        border.setWarningTime(0);
    }

    // ─────────────────────────────────────────────
    //  Plateforme
    // ─────────────────────────────────────────────

    private void buildPlatform() {
        if (lobbyWorld == null) return;
        for (int x = -PLATFORM_HALF; x < PLATFORM_HALF; x++) {
            for (int z = -PLATFORM_HALF; z < PLATFORM_HALF; z++) {
                lobbyWorld.getBlockAt(x, PLATFORM_Y, z).setType(Material.BARRIER);
            }
        }
    }

    private void ensurePlatform() {
        if (lobbyWorld == null) return;
        if (lobbyWorld.getBlockAt(0, PLATFORM_Y, 0).getType() != Material.BARRIER) {
            buildPlatform();
        }
    }

    private void setSpawn() {
        if (lobbyWorld == null) return;
        lobbyWorld.setSpawnLocation(0, PLATFORM_Y + 1, 0);
    }

    // ─────────────────────────────────────────────
    //  Pancarte cliquable
    // ─────────────────────────────────────────────

    /**
     * Place la pancarte colorée sur la plateforme.
     *
     * Structure :
     *   - Un bloc BARRIER en (0, 65, -4) sert de support mural invisible
     *   - La pancarte OAK_WALL_SIGN est placée en (0, 65, -3) face au joueur (south)
     *
     * Lignes de la pancarte :
     *   Ligne 1 : ══════════  (or)
     *   Ligne 2 : ► Rejoindre (vert vif, gras)
     *   Ligne 3 : la map      (jaune)
     *   Ligne 4 : ══════════  (or)
     *
     * Note Paper API :
     *   On utilise block.getState() → Sign → sign.getSide(Side.FRONT)
     *   → signSide.line(index, component) → block.update()
     *   C'est l'API Adventure Paper 1.20+ pour les pancartes.
     */
    private void placeSign() {
        if (lobbyWorld == null) return;

        // Support invisible derrière la pancarte
        Block support = lobbyWorld.getBlockAt(SIGN_X, SIGN_Y, SIGN_Z - 1);
        support.setType(Material.BARRIER);

        // La pancarte elle-même, orientée vers le sud (face au joueur)
        Block signBlock = lobbyWorld.getBlockAt(SIGN_X, SIGN_Y, SIGN_Z);
        signBlock.setType(Material.OAK_WALL_SIGN);

        // Orientation south via BlockData
        org.bukkit.block.data.type.WallSign wallSignData =
                (org.bukkit.block.data.type.WallSign) signBlock.getBlockData();
        wallSignData.setFacing(org.bukkit.block.BlockFace.SOUTH);
        signBlock.setBlockData(wallSignData);

        // Écriture des lignes colorées
        writeSign(signBlock);
    }

    private void writeSign(Block signBlock) {
        if (!(signBlock.getState() instanceof Sign sign)) return;

        SignSide front = sign.getSide(Side.FRONT);

        // Ligne 1 : vide
        front.line(0, Component.empty());
        // Ligne 2 : "Go map" en vert
        front.line(1, Component.text("Go map")
                .color(NamedTextColor.GREEN));
        // Ligne 3 : "❤" en jaune doré (#F6F800)
        front.line(2, Component.text("<3")
                .color(net.kyori.adventure.text.format.TextColor.fromHexString("#F6F800")));
        // Ligne 4 : vide
        front.line(3, Component.empty());

        front.setGlowingText(true);
        sign.update();
    }

    private void ensureSign() {
        if (lobbyWorld == null) return;
        Block signBlock = lobbyWorld.getBlockAt(SIGN_X, SIGN_Y, SIGN_Z);
        if (!(signBlock.getType() == Material.OAK_WALL_SIGN)) {
            placeSign();
        }
    }

    /**
     * Retourne true si le bloc cliqué est la pancarte du lobby.
     * Utilisé par LobbySignListener pour détecter le clic droit.
     */
    public boolean isLobbySign(Block block) {
        return lobbyWorld != null
                && block.getWorld().equals(lobbyWorld)
                && block.getX() == SIGN_X
                && block.getY() == SIGN_Y
                && block.getZ() == SIGN_Z;
    }

    // ─────────────────────────────────────────────
    //  Téléportation
    // ─────────────────────────────────────────────

    public void sendToLobby(Player player) {
        if (lobbyWorld == null) {
            plugin.getLogger().warning("[SeedRunner] Lobby non initialisé, TP annulé pour " + player.getName());
            return;
        }
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.teleport(lobbyWorld.getSpawnLocation());
    }

    // ─────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────

    public World getLobbyWorld() { return lobbyWorld; }

    public boolean isLobbyWorld(World world) {
        return world != null && LOBBY_WORLD_NAME.equals(world.getName());
    }
}
