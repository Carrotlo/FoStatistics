package me.foesio.foStatistics.command;

import me.foesio.core.material.MaterialTypes;
import me.foesio.core.mob.MobTypes;
import me.foesio.core.number.DurationParser;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.number.TickDuration;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.foStatistics.FoStatistics;
import me.foesio.foStatistics.statistic.StatisticKey;
import me.foesio.foStatistics.statistic.StatisticParseException;
import me.foesio.foStatistics.statistic.StatisticParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements TabExecutor {
    private static final int SUGGESTION_LIMIT = 80;
    private static final int TYPED_TARGET_SUGGESTION_LIMIT = 50;

    private static final List<String> ROOT_ARGUMENTS = List.of(
            "help",
            "version",
            "reload",
            "editor",
            "view",
            "setstatistic",
            "addstatistic",
            "takestatistic"
    );

    private final FoStatistics plugin;
    private final StatisticParser statisticParser = new StatisticParser();

    public AdminCommand(FoStatistics plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(FoStatistics.ADMIN_PERMISSION)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.messages().send(sender, "help", Map.of("label", label));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "version" -> sendVersion(sender);
            case "reload" -> reload(sender);
            case "editor" -> openEditor(sender);
            case "view", "viewstatistic" -> viewStatistic(sender, label, args);
            case "set", "setstatistic" -> editStatistic(sender, label, args, EditAction.SET);
            case "add", "addstatistic" -> editStatistic(sender, label, args, EditAction.ADD);
            case "take", "takestatistic" -> editStatistic(sender, label, args, EditAction.TAKE);
            default -> plugin.messages().send(sender, "unknown-command", Map.of("label", label));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(FoStatistics.ADMIN_PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(ROOT_ARGUMENTS, args[0]);
        }

        if (!isEditAction(args[0]) && !isViewAction(args[0])) {
            return List.of();
        }

        if (args.length == 2) {
            return statisticSuggestions(args[1]);
        }
        if (args.length == 3) {
            return playerSuggestions(args[2]);
        }
        if (args.length == 4 && isEditAction(args[0])) {
            try {
                StatisticKey statisticKey = statisticParser.parse(args[1]);
                if (statisticKey.isTimePlayed()) {
                    return filter(List.of("1s", "30s", "1m", "10m", "1h", "1d", "1w"), args[3]);
                }
            } catch (StatisticParseException ignored) {
            }
            return filter(List.of("0", "1", "10", "100", "1000"), args[3]);
        }
        return List.of();
    }

    private void sendVersion(CommandSender sender) {
        plugin.logInfo(sender.getName() + " used version check.");
        plugin.updateNotices().checkAndSendVersion(sender);
    }

    private void reload(CommandSender sender) {
        plugin.logInfo(sender.getName() + " started reload.");
        FoReloadResult result = plugin.reloadPluginFiles();
        if (result.successful()) {
            plugin.messages().send(sender, "reload-success");
            return;
        }

        plugin.messages().send(sender, "reload-failed", Map.of("error", result.failedStep() + ": " + result.errorMessage()));
        plugin.logError("Reload failed at " + result.failedStep() + ".", result.error());
    }

    private void openEditor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        plugin.editorGui().open(player);
    }

    private void viewStatistic(CommandSender sender, String label, String[] args) {
        if (args.length != 3) {
            plugin.messages().send(sender, "statistic-view-usage", Map.of("label", label));
            return;
        }

        StatisticKey statisticKey;
        try {
            statisticKey = statisticParser.parse(args[1]);
        } catch (StatisticParseException exception) {
            plugin.messages().send(sender, "invalid-statistic", Map.of("reason", exception.getMessage()));
            return;
        }

        OfflinePlayer target = resolveTarget(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "unknown-player", Map.of("player", args[2]));
            return;
        }

        int value;
        try {
            value = statisticKey.get(target);
        } catch (IllegalArgumentException exception) {
            plugin.messages().send(sender, "statistic-api-error", Map.of("error", exception.getMessage()));
            plugin.logWarning("Statistic API rejected view for " + statisticKey.displayName() + ": " + exception.getMessage());
            return;
        }

        String playerName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        Map<String, String> placeholders = Map.of(
                "statistic", statisticKey.displayName(),
                "player", playerName,
                "value", formatStatisticValue(statisticKey, value)
        );
        plugin.messages().send(sender, "statistic-view", placeholders);
        plugin.logInfo(sender.getName() + " viewed " + playerName + " statistic "
                + statisticKey.displayName() + ": " + value + ".");
    }

    private void editStatistic(CommandSender sender, String label, String[] args, EditAction action) {
        if (args.length != 4) {
            plugin.messages().send(sender, "statistic-usage", Map.of(
                    "label", label,
                    "action", args[0].toLowerCase(Locale.ROOT)
            ));
            return;
        }

        StatisticKey statisticKey;
        try {
            statisticKey = statisticParser.parse(args[1]);
        } catch (StatisticParseException exception) {
            plugin.messages().send(sender, "invalid-statistic", Map.of("reason", exception.getMessage()));
            return;
        }

        OfflinePlayer target = resolveTarget(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "unknown-player", Map.of("player", args[2]));
            return;
        }

        AmountInput amount = parseAmount(args[3], sender, statisticKey);
        if (amount == null) {
            return;
        }

        int maxAmount = plugin.settings().maxStatisticAmount();
        if (amount.value() > maxAmount) {
            plugin.messages().send(sender, "amount-too-large", Map.of(
                    "amount", amount.display(),
                    "max", String.valueOf(maxAmount)
            ));
            return;
        }

        int oldValue;
        int newValue;
        try {
            oldValue = statisticKey.get(target);
            newValue = calculateNewValue(action, oldValue, amount.value(), maxAmount);
            statisticKey.set(target, newValue);
        } catch (ArithmeticException exception) {
            plugin.messages().send(sender, "amount-too-large", Map.of(
                    "amount", amount.display(),
                    "max", String.valueOf(maxAmount)
            ));
            return;
        } catch (IllegalArgumentException exception) {
            plugin.messages().send(sender, "statistic-api-error", Map.of("error", exception.getMessage()));
            plugin.logWarning("Statistic API rejected edit for " + statisticKey.displayName() + ": " + exception.getMessage());
            return;
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
        plugin.messages().send(sender, messageKey, placeholders);
        plugin.logInfo(sender.getName() + " changed " + playerName + " statistic "
                + statisticKey.displayName() + " from " + oldValue + " to " + newValue + ".");
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

    private boolean isEditAction(String argument) {
        return switch (argument.toLowerCase(Locale.ROOT)) {
            case "set", "setstatistic", "add", "addstatistic", "take", "takestatistic" -> true;
            default -> false;
        };
    }

    private boolean isViewAction(String argument) {
        return switch (argument.toLowerCase(Locale.ROOT)) {
            case "view", "viewstatistic" -> true;
            default -> false;
        };
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

    private enum EditAction {
        SET,
        ADD,
        TAKE
    }

    private String formatStatisticValue(StatisticKey statisticKey, int value) {
        if (!statisticKey.isTimePlayed()) {
            return String.valueOf(value);
        }
        return TickDuration.ofTicks(Math.max(0, value)).compact();
    }

    private record AmountInput(int value, String display) {
    }
}
