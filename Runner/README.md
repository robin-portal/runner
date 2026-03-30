# 🌍 SeedRunner — Plugin Paper 1.21.1

> Gérez des mondes Minecraft à l'**infini** — Overworld, Nether, End — avec une seule commande.

---

## 📦 Installation

1. **Compiler** le plugin (voir section Compilation ci-dessous)
2. Placer le `.jar` dans le dossier `plugins/` de votre serveur Paper 1.21.1
3. Démarrer le serveur
4. Le dossier `plugins/SeedRunner/` sera créé automatiquement

---

## 🔨 Compilation

### Prérequis
- Java 21+
- Maven 3.8+

### Commandes
```bash
cd SeedRunner
mvn clean package
```
Le JAR se trouve dans `target/SeedRunner-1.0.0.jar`.

---

## 🎮 Commandes

| Commande | Description |
|---|---|
| `/runner create <nom> [type] [seed]` | Crée un nouveau monde |
| `/runner tp <nom>` | Téléporte vers un monde |
| `/runner list` | Liste tous les mondes |
| `/runner info <nom>` | Informations détaillées |
| `/runner load <nom>` | Charge un monde sauvegardé |
| `/runner unload <nom>` | Décharge un monde de la mémoire |
| `/runner delete <nom> confirm` | Supprime définitivement un monde |
| `/runner help` | Affiche l'aide |

### Alias disponibles
`/world`, `/monde`

### Types de mondes
| Type | Alias acceptés |
|---|---|
| `overworld` | (défaut) |
| `nether` | `hell` |
| `end` | `ender`, `the_end` |

### Exemples
```
/runner create SurvieNormal
/runner create MonNether nether
/runner create Exploration overworld 12345
/runner create EndGame end 99999
/runner tp SurvieNormal
/runner list
/runner delete MonNether confirm
```

---

## 🔐 Permissions

| Permission | Description | Défaut |
|---|---|---|
| `seedrunner.runner` | Accès à `/runner` | op |
| `seedrunner.runner.create` | Créer des mondes | op |
| `seedrunner.runner.delete` | Supprimer des mondes | op |
| `seedrunner.runner.tp` | Se téléporter | op |
| `seedrunner.admin` | Accès complet | op |

---

## ⚙️ Configuration (`config.yml`)

```yaml
world-defaults:
  difficulty: NORMAL        # PEACEFUL / EASY / NORMAL / HARD
  allow-monsters: true
  allow-animals: true
  pvp: true
  generate-structures: true

auto-load:
  enabled: true             # Charge les mondes au démarrage
```

---

## 📁 Structure du projet

```
SeedRunner/
├── pom.xml
└── src/main/
    ├── java/fr/seedrunner/seedrunner/
    │   ├── SeedRunner.java              ← Classe principale
    │   ├── commands/
    │   │   └── RunnerCommand.java       ← Commande /runner + tab-complete
    │   ├── managers/
    │   │   ├── WorldManager.java        ← Logique création/chargement/suppression
    │   │   └── MessageManager.java      ← Système de messages MiniMessage
    │   └── models/
    │       └── WorldData.java           ← Modèle de données d'un monde
    └── resources/
        ├── plugin.yml
        └── config.yml
```

---

## 🔄 Fonctionnement interne

- Les mondes sont créés de manière **async** puis initialisés sur le thread principal (compatible Paper)
- Les métadonnées sont persistées dans `plugins/SeedRunner/worlds.yml`
- Au redémarrage, les mondes sont **rechargés automatiquement**
- Les joueurs présents dans un monde déchargé sont téléportés vers le monde principal
- La suppression d'un monde nécessite une **confirmation** (`confirm`) pour éviter les accidents

---

## ✅ Compatibilité

- **Paper 1.21.1** (et versions proches)
- Java 21 minimum
- Pas de dépendances externes

---

*Plugin inspiré de Multiverse-Core, réécrit from scratch pour Paper moderne.*
