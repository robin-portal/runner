package fr.seedrunner.seedrunner;

import fr.seedrunner.seedrunner.commands.LobbyCommand;
import fr.seedrunner.seedrunner.commands.RunnerCommand;
import fr.seedrunner.seedrunner.commands.ShareCommand;
import fr.seedrunner.seedrunner.listeners.DimensionAnnounceListener;
import fr.seedrunner.seedrunner.listeners.JoinListener;
import fr.seedrunner.seedrunner.listeners.LobbySignListener;
import fr.seedrunner.seedrunner.listeners.PortalListener;
import fr.seedrunner.seedrunner.listeners.RespawnListener;
import fr.seedrunner.seedrunner.managers.LobbyManager;
import fr.seedrunner.seedrunner.managers.MessageManager;
import fr.seedrunner.seedrunner.managers.WorldManager;
import fr.seedrunner.seedrunner.scoreboard.ChronoScoreboard;
import fr.seedrunner.seedrunner.shared.AbsorptionListener;
import fr.seedrunner.seedrunner.shared.DamageListener;
import fr.seedrunner.seedrunner.shared.FoodListener;
import fr.seedrunner.seedrunner.shared.RegenListener;
import fr.seedrunner.seedrunner.shared.SyncManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SeedRunner extends JavaPlugin {

    private static SeedRunner instance;

    private WorldManager             worldManager;
    private MessageManager           messageManager;
    private LobbyManager             lobbyManager;
    private SyncManager              syncManager;
    private ChronoScoreboard         chronoScoreboard;
    private DimensionAnnounceListener dimensionAnnounceListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Managers
        messageManager           = new MessageManager(this);
        worldManager             = new WorldManager(this);
        lobbyManager             = new LobbyManager(this);
        syncManager              = new SyncManager(this);
        chronoScoreboard         = new ChronoScoreboard(this);
        dimensionAnnounceListener = new DimensionAnnounceListener(this);

        // Lobby
        lobbyManager.initLobby();

        // Commandes
        registerCommand("run",   new RunnerCommand(this));
        registerCommand("share", new ShareCommand(this));
        registerCommand("lobby", new LobbyCommand(this));

        // Listeners — portails, respawn, lobby, reco
        register(new PortalListener(this));
        register(new RespawnListener(this));
        register(new LobbySignListener(this));
        register(new JoinListener(this));
        register(dimensionAnnounceListener);

        // Listeners — SharedLife
        register(new DamageListener(syncManager));
        register(new RegenListener(this, syncManager));
        register(new AbsorptionListener(this, syncManager));
        register(new FoodListener(syncManager));

        // Resync sécurité SharedLife (toutes les 20 ticks)
        syncManager.startSecuritySync();

        // Auto-charge les mondes sauvegardés
        worldManager.loadSavedWorlds();

        getLogger().info("Runner v1.0 actif. /run [seed] | /share | /lobby");
    }

    @Override
    public void onDisable() {
        if (chronoScoreboard != null) chronoScoreboard.stop();
        if (worldManager != null) worldManager.saveAllWorlds();
        getLogger().info("Runner désactivé.");
    }

    private void register(org.bukkit.event.Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(executor);
    }

    public static SeedRunner getInstance()                      { return instance; }
    public WorldManager getWorldManager()                       { return worldManager; }
    public MessageManager getMessageManager()                   { return messageManager; }
    public LobbyManager getLobbyManager()                       { return lobbyManager; }
    public SyncManager getSyncManager()                         { return syncManager; }
    public ChronoScoreboard getChronoScoreboard()               { return chronoScoreboard; }
    public DimensionAnnounceListener getDimensionAnnounceListener() { return dimensionAnnounceListener; }
}
