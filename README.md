![FoStatistics cover](docs/assets/cover.png)

# FoStatistics

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fostatistics) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fostatistics/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Safe in-game viewing and editing of Minecraft player statistics.

*Developed by Carrotio.*

## Features

- Reads and edits Minecraft statistics through the server API.
- Supports set, add, take, view, and editor workflows.
- Handles typed block, item, entity, and time-played statistics.
- Supports configurable limits and offline-player behavior.
- Keeps messages, sounds, and file logging configurable.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fostatistics). Check the [versions page](https://modrinth.com/plugin/fostatistics/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** None

## Screenshots

![Browse and edit player statistics.](docs/assets/screenshots/fostatistics-admin-editor.png)

*Browse and edit player statistics.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fostatisticsadmin <help|view|set|add|take|editor>` — FoStatistics admin command. Aliases: `/statisticsadmin`.

### Permissions

- `fostatistics.admin` — Allows FoStatistics admin commands. Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `statistics`, `file-logging`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoStatistics` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoStatistics</artifactId>
  <version>1.7</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foStatistics.api.FoStatisticsService;
import me.foesio.foStatistics.api.StatisticTarget;
import me.foesio.foStatistics.api.StatisticEditAction;
import org.bukkit.Statistic;
import me.foesio.foStatistics.api.event.FoStatisticEditedEvent;

FoStatisticsService api = Bukkit.getServicesManager().load(FoStatisticsService.class);
if (api != null) api.edit(this, player, StatisticTarget.of(Statistic.JUMP), StatisticEditAction.ADD, 1);

@EventHandler
public void onFoStatisticEditedEvent(FoStatisticEditedEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `FoStatisticsService`: `edit`.

**Other API types:** `StatisticEditAction`, `StatisticEditResult`, `StatisticTarget`.

**Events:** `FoStatisticEditedEvent`.

[Browse API source](src/main/java/me/foesio/foStatistics/api) for signatures and event details.

</details>
