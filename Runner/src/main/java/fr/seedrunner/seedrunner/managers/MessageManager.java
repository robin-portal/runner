package fr.seedrunner.seedrunner.managers;

import fr.seedrunner.seedrunner.SeedRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * Gère les messages envoyés aux joueurs avec support MiniMessage (Paper).
 */
public class MessageManager {

    private final SeedRunner plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Préfixe principal
    private static final String PREFIX = "<dark_gray>[<gold><bold>SeedRunner</bold></gold><dark_gray>]</dark_gray> ";

    // Messages prédéfinis
    public static final String WORLD_CREATING     = PREFIX + "<yellow>⚙ Création du monde <white><world></white> en cours...";
    public static final String WORLD_CREATED      = PREFIX + "<green>✔ Monde <white><world></white> créé avec succès ! <gray>(<type> | Seed: <seed>)";
    public static final String WORLD_DELETED      = PREFIX + "<red>✖ Monde <white><world></white> supprimé.";
    public static final String WORLD_LOADED       = PREFIX + "<green>✔ Monde <white><world></white> chargé.";
    public static final String WORLD_UNLOADED     = PREFIX + "<yellow>Monde <white><world></white> déchargé.";
    public static final String WORLD_TP           = PREFIX + "<aqua>➤ Téléportation vers <white><world></white>...";
    public static final String WORLD_NOT_FOUND    = PREFIX + "<red>✖ Monde introuvable : <white><world></white>";
    public static final String WORLD_EXISTS       = PREFIX + "<red>✖ Un monde nommé <white><world></white> existe déjà !";
    public static final String NO_PERMISSION      = PREFIX + "<red>✖ Tu n'as pas la permission d'utiliser cette commande.";
    public static final String INVALID_USAGE      = PREFIX + "<red>Usage : <white>/runner <create|load|delete|list|tp> [nom] [type] [seed]";
    public static final String WORLD_LIST_HEADER  = PREFIX + "<gold>━━━━━ Mondes SeedRunner (<count>) ━━━━━";
    public static final String WORLD_LIST_ENTRY   = "  <gray>• <white><world> <dark_gray>(<type><dark_gray>) <gray>Seed: <white><seed> <dark_gray>| <status>";
    public static final String WORLD_LIST_EMPTY   = PREFIX + "<gray>Aucun monde créé. Utilise <white>/runner create <nom></white> pour commencer.";
    public static final String ONLY_PLAYER        = PREFIX + "<red>✖ Cette action nécessite d'être en jeu.";
    public static final String DELETING_DEFAULT   = PREFIX + "<red>✖ Impossible de supprimer le monde principal du serveur.";

    public MessageManager(SeedRunner plugin) {
        this.plugin = plugin;
    }

    /**
     * Envoie un message formaté à un CommandSender.
     */
    public void send(CommandSender sender, String template, String... placeholders) {
        String msg = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace("<" + placeholders[i] + ">", placeholders[i + 1]);
        }
        sender.sendMessage(mm.deserialize(msg));
    }

    /**
     * Renvoie un Component depuis un template.
     */
    public Component parse(String template, String... placeholders) {
        String msg = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace("<" + placeholders[i] + ">", placeholders[i + 1]);
        }
        return mm.deserialize(msg);
    }

    /**
     * Envoie un séparateur stylisé.
     */
    public void sendSeparator(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }
}
