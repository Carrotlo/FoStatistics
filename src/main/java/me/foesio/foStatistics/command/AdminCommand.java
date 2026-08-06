package me.foesio.foStatistics.command;

import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminCommandContext;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.mob.MobTypes;
import me.foesio.core.number.DurationParser;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.number.TickDuration;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.foStatistics.FoStatistics;
import me.foesio.foStatistics.statistic.StatisticKey;
import me.foesio.foStatistics.statistic.StatisticParseException;
import me.foesio.foStatistics.statistic.StatisticParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand {
    private static final int SUGGESTION_LIMIT = 80;
    private static final int TYPED_TARGET_SUGGESTION_LIMIT = 50;

    private final FoStatistics plugin;
    private final StatisticParser statisticParser = new StatisticParser();

    public AdminCommand(FoStatistics plugin) {
        this.plugin = plugin;
    }

    public boolean register(FoReloadRegistry reloadRegistry) {
        FoAdminMessages adminMessages = FoAdminMessages.builder()
                .generalNoPermission("no-permission", "{prefix}{bad}No permission.")
                .generalPlayerOnly("player-only", "{prefix}{bad}Player only.")
                .usage("unknown-command", "{prefix}{bad}Unknown command. {muted}Use {theme}/{label} help{muted}.")
                .reloadSuccess("reload-success", "{prefix}{good}Reloaded config, messages, editor, and logging.")
                .reloadFailed("reload-failed", "{prefix}{bad}Reload failed. {muted}{error}")
                .build();

        return FoAdminCommand.builder(plugin, plugin.messages())
                .commandName("fostatisticsadmin")
                .permission(FoStatistics.ADMIN_PERMISSION)
                .reloads(reloadRegistry)
                .updates(plugin.updateNotices())
                .adminMessages(adminMessages)
                .defaultExecutor(this::sendHelp)
                .addSubcommand(helpSubcommand())
                .addSubcommand(editorSubcommand())
                .addSubcommand(viewSubcommand())
                .addSubcommand(editSubcommand("set", EditAction.SET, "setstatistic"))
                .addSubcommand(editSubcommand("add", EditAction.ADD, "addstatistic"))
                .addSubcommand(editSubcommand("take", EditAction.TAKE, "takestatistic"))
                .build()
                .register();
    }

    private FoAdminSubcommand helpSubcommand() {
        return FoAdminSubcommand.builder("help", this::sendHelp)
                .usage("help")
                .build();
    }

    private boolean sendHelp(FoAdminCommandContext context) {
        plugin.messages().sendList(context.sender(), "help", Map.of("label", context.label()));
        return true;
    }

    private FoAdminSubcommand editorSubcommand() {
        return FoAdminSubcommand.builder("editor", context -> {
                    plugin.editorGui().open(context.playerOrNull());
                    return true;
                })
                .usage("editor")
                .playerOnly()
                .build();
    }

    private FoAdminSubcommand viewSubcommand() {
        return FoAdminSubcommand.builder("view", this::viewStatistic)
                .aliases("viewstatistic")
                .usage("view <statistic> <player>")
                .tabCompleter(context -> {
                    if (context.args().length == 2) {
                        return statisticSuggestions(context.arg(1));
                    }
                    if (context.args().length == 3) {
                        return playerSuggestions(context.arg(2));
                    }
                    return List.of();
                })
                .build();
    }

    private FoAdminSubcommand editSubcommand(String name, EditAction action, String alias) {
        return FoAdminSubcommand.builder(name, context -> editStatistic(context, action))
                .aliases(alias)
                .usage(name + " <statistic> <player> <amount>")
                .tabCompleter(context -> {
                    if (context.args().length == 2) {
                        return statisticSuggestions(context.arg(1));
                    }
                    if (context.args().length == 3) {
                        return playerSuggestions(context.arg(2));
                    }
                    if (context.args().length == 4) {
                        return amountSuggestions(context.arg(1), context.arg(3));
                    }
                    return List.of();
                })
                .build();
    }

    private boolean viewStatistic(FoAdminCommandContext context) {
        String[] args = context.args();
        if (args.length != 3) {
            plugin.messages().send(context.sender(), "statistic-view-usage", Map.of("label", context.label()));
            return true;
        }

        StatisticKey statisticKey;
        try {
            statisticKey = statisticParser.parse(args[1]);
        } catch (StatisticParseException exception) {
            plugin.messages().send(context.sender(), "invalid-statistic", Map.of("reason", exception.getMessage()));
            return true;
        }

        OfflinePlayer target = resolveTarget(args[2]);
        if (target == null) {
            plugin.messages().send(context.sender(), "unknown-player", Map.of("player", args[2]));
            return true;
        }

        int value;
        try {
            value = statisticKey.get(target);
        } catch (IllegalArgumentException exception) {
            plugin.messages().send(context.sender(), "statistic-api-error", Map.of("error", exception.getMessage()));
            plugin.logWarning("Statistic API rejected view for " + statisticKey.displayName() + ": " + exception.getMessage());
            return true;
        }

        String playerName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        Map<String, String> placeholders = Map.of(
                "statistic", statisticKey.displayName(),
                "player", playerName,
                "value", formatStatisticValue(statisticKey, value)
        );
        plugin.messages().send(context.sender(), "statistic-view", placeholders);
        plugin.logInfo(context.sender().getName() + " viewed " + playerName + " statistic "
                + statisticKey.displayName() + ": " + value + ".");
        return true;
    }

    private boolean editStatistic(FoAdminCommandContext context, EditAction action) {
        String[] args = context.args();
        if (args.length != 4) {
            plugin.messages().send(context.sender(), "statistic-usage", Map.of(
                    "label", context.label(),
                    "action", args[0].toLowerCase(Locale.ROOT)
            ));
            return true;
        }

        StatisticKey statisticKey;
        try {
            statisticKey = statisticParser.parse(args[1]);
        } catch (StatisticParseException exception) {
            plugin.messages().send(context.sender(), "invalid-statistic", Map.of("reason", exception.getMessage()));
            return true;
        }

        OfflinePlayer target = resolveTarget(args[2]);
        if (target == null) {
            plugin.messages().send(context.sender(), "unknown-player", Map.of("player", args[2]));
            return true;
        }

        AmountInput amount = parseAmount(args[3], context.sender(), statisticKey);
        if (amount == null) {
            return true;
        }

        int maxAmount = plugin.settings().maxStatisticAmount();
        if (amount.value() > maxAmount) {
            plugin.messages().send(context.sender(), "amount-too-large", Map.of(
                    "amount", amount.display(),
                    "max", String.valueOf(maxAmount)
            ));
            return true;
        }

        int oldValue;
        int newValue;
        try {
            oldValue = statisticKey.get(target);
            newValue = calculateNewValue(action, oldValue, amount.value(), maxAmount);
            statisticKey.set(target, newValue);
        } catch (ArithmeticException exception) {
            plugin.messages().send(context.sender(), "amount-too-large", Map.of(
                    "amount", amount.display(),
                    "max", String.valueOf(maxAmount)
            ));
            return true;
        } catch (IllegalArgumentException exception) {
            plugin.messages().send(context.sender(), "statistic-api-error", Map.of("error", exception.getMessage()));
            plugin.logWarning("Statistic API rejected edit for " + statisticKey.displayName() + ": " + exception.getMessage());
            return true;
        }

        String messageKey = switch (action) {
            case SET -> "statistic-set";
            case ADD -> "statistic-added";
            case TAKE -> "statistic-taken";
        };

        String playerName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        Map<String, String> placeholders = Map.of(
                "statistic", statisticKey.displayName(),
                "player", playerName,
                "amount", amount.display(),
                "old", formatStatisticValue(statisticKey, oldValue),
                "new", formatStatisticValue(statisticKey, newValue)
        );
        plugin.messages().send(context.sender(), messageKey, placeholders);
        plugin.logInfo(context.sender().getName() + " changed " + playerName + " statistic "
                + statisticKey.displayName() + " from " + oldValue + " to " + newValue + ".");
        return true;
    }

    private OfflinePlayer resolveTarget(String name) {
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        if (!plugin.settings().allowOfflinePlayerEdits()) {
            return null;
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String offlineName = offlinePlayer.getName();
            if (offlineName != null && offlineName.equalsIgnoreCase(name)) {
                return offlinePlayer;
            }
        }

        if (!plugin.settings().allowUnknownOfflineTargets()) {
            return null;
        }

        return Bukkit.getOfflinePlayer(name);
    }

    private AmountInput parseAmount(String input, CommandSender sender, StatisticKey statisticKey) {
        if (statisticKey.isTimePlayed()) {
            return parseTimePlayedAmount(input, sender);
        }

        var parsed = LargeNumberParser.parse(input);
        if (parsed.isEmpty() || parsed.get().signum() < 0 || parsed.get().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            plugin.messages().send(sender, "invalid-amount");
            return null;
        }

        try {
            int amount = parsed.get().intValueExact();
            return new AmountInput(amount, String.valueOf(amount));
        } catch (ArithmeticException exception) {
            plugin.messages().send(sender, "invalid-amount");
            return null;
        }
    }

    private AmountInput parseTimePlayedAmount(String input, CommandSender sender) {
        var parsed = DurationParser.parse(input);
        if (parsed.isEmpty()) {
            plugin.messages().send(sender, "invalid-amount");
            return null;
        }

        try {
            TickDuration duration = parsed.get();
            int amount = duration.intTicksExact();
            return new AmountInput(amount, duration.compact());
        } catch (ArithmeticException exception) {
            plugin.messages().send(sender, "invalid-amount");
            return null;
        }
    }

    private int calculateNewValue(EditAction action, int oldValue, int amount, int maxAmount) {
        return switch (action) {
            case SET -> amount;
            case ADD -> {
                long result = (long) oldValue + amount;
                if (result > maxAmount || result > Integer.MAX_VALUE) {
                    throw new ArithmeticException("Statistic value too large.");
                }
                yield (int) result;
            }
            case TAKE -> Math.max(0, oldValue - amount);
        };
    }

    private List<String> amountSuggestions(String statisticInput, String partial) {
        List<String> suggestions;
        try {
            StatisticKey statisticKey = statisticParser.parse(statisticInput);
            if (statisticKey.isTimePlayed()) {
                suggestions = List.of("1s", "30s", "1m", "10m", "1h", "1d", "1w");
                return filter(suggestions, partial);
            }
        } catch (StatisticParseException ignored) {
        }
        suggestions = List.of("0", "1", "10", "100", "1000");
        return filter(suggestions, partial);
    }

    private List<String> statisticSuggestions(String input) {
        String normalized = input.toUpperCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (separator > 0) {
            String statisticName = normalized.substring(0, separator).replace('-', '_');
            if (statisticName.equals("TIME_PLAYED")) {
                statisticName = "PLAY_ONE_MINUTE";
            }
            String lookupPrefix = input.toLowerCase(Locale.ROOT).replace('-', '_');
            Statistic statistic;
            try {
                statistic = Statistic.valueOf(statisticName);
            } catch (IllegalArgumentException exception) {
                return List.of();
            }
            String prefix = statistic.name().toLowerCase(Locale.ROOT) + ":";
            return switch (statistic.getType()) {
                case BLOCK -> MaterialTypes.allBlocks().stream()
                        .map(material -> prefix + material.name().toLowerCase(Locale.ROOT))
                        .filter(value -> value.startsWith(lookupPrefix))
                        .limit(TYPED_TARGET_SUGGESTION_LIMIT)
                        .toList();
                case ITEM -> MaterialTypes.allItems().stream()
                        .map(material -> prefix + material.name().toLowerCase(Locale.ROOT))
                        .filter(value -> value.startsWith(lookupPrefix))
                        .limit(TYPED_TARGET_SUGGESTION_LIMIT)
                        .toList();
                case ENTITY -> MobTypes.allLiving().stream()
                        .map(entityType -> prefix + entityType.name().toLowerCase(Locale.ROOT))
                        .filter(value -> value.startsWith(lookupPrefix))
                        .limit(TYPED_TARGET_SUGGESTION_LIMIT)
                        .toList();
                case UNTYPED -> List.of();
            };
        }

        List<String> suggestions = new ArrayList<>();
        for (Statistic statistic : Statistic.values()) {
            String value = statistic == Statistic.PLAY_ONE_MINUTE
                    ? "time_played"
                    : statistic.name().toLowerCase(Locale.ROOT);
            if (statistic.getType() != Statistic.Type.UNTYPED) {
                value += ":";
            }
            suggestions.add(value);
        }
        return filter(suggestions, input);
    }

    private List<String> playerSuggestions(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            addIfMatching(suggestions, player.getName(), normalized);
        }
        return suggestions;
    }

    private void addIfMatching(List<String> suggestions, String value, String normalizedInput) {
        if (suggestions.size() >= SUGGESTION_LIMIT || !value.toLowerCase(Locale.ROOT).startsWith(normalizedInput) || suggestions.contains(value)) {
            return;
        }
        suggestions.add(value);
    }

    private List<String> filter(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .limit(SUGGESTION_LIMIT)
                .toList();
    }

    private String formatStatisticValue(StatisticKey statisticKey, int value) {
        if (!statisticKey.isTimePlayed()) {
            return String.valueOf(value);
        }
        return TickDuration.ofTicks(Math.max(0, value)).compact();
    }

    private enum EditAction {
        SET,
        ADD,
        TAKE
    }

    private record AmountInput(int value, String display) {
    }
}
