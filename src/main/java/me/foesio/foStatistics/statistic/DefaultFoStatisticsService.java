package me.foesio.foStatistics.statistic;

import me.foesio.foStatistics.FoStatistics;
import me.foesio.foStatistics.api.FoStatisticsService;
import me.foesio.foStatistics.api.StatisticEditAction;
import me.foesio.foStatistics.api.StatisticEditResult;
import me.foesio.foStatistics.api.StatisticTarget;
import me.foesio.foStatistics.api.event.FoStatisticEditedEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;

/** Shared mutation path for the public service and admin commands. */
public final class DefaultFoStatisticsService implements FoStatisticsService {
    private final FoStatistics plugin;
    private volatile boolean active = true;

    public DefaultFoStatisticsService(FoStatistics plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void deactivate() {
        active = false;
    }

    @Override
    public StatisticEditResult edit(Plugin caller, OfflinePlayer player, StatisticTarget target,
                                    StatisticEditAction action, int amount) {
        Objects.requireNonNull(caller, "caller");
        if (!caller.isEnabled()) {
            return StatisticEditResult.rejected(StatisticEditResult.Status.CALLER_DISABLED, null);
        }
        StatisticEditResult result = editInternal(player, target, action, amount,
                FoStatisticEditedEvent.Origin.API, caller.getName(), null);
        if (result.status() == StatisticEditResult.Status.CHANGED) {
            plugin.logInfo(caller.getName() + " changed " + player.getUniqueId() + " statistic "
                    + target + " from " + result.previousValue() + " to " + result.currentValue() + ".");
        }
        return result;
    }

    public StatisticEditResult editFromCommand(CommandSender sender, OfflinePlayer player,
                                               StatisticTarget target, StatisticEditAction action, int amount) {
        Objects.requireNonNull(sender, "sender");
        UUID actorId = sender instanceof Player actor ? actor.getUniqueId() : null;
        return editInternal(player, target, action, amount,
                FoStatisticEditedEvent.Origin.COMMAND, sender.getName(), actorId);
    }

    private StatisticEditResult editInternal(OfflinePlayer player, StatisticTarget target, StatisticEditAction action,
                                             int amount, FoStatisticEditedEvent.Origin origin,
                                             String sourceName, UUID actorId) {
        if (!active || !plugin.isEnabled()) {
            return StatisticEditResult.rejected(StatisticEditResult.Status.DISABLED, null);
        }
        if (!Bukkit.isPrimaryThread()) {
            return StatisticEditResult.rejected(StatisticEditResult.Status.WRONG_THREAD, null);
        }
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");

        if (amount < 0) {
            return StatisticEditResult.rejected(StatisticEditResult.Status.INVALID_AMOUNT, null);
        }
        int maximum = plugin.settings().maxStatisticAmount();
        if (amount > maximum) {
            return StatisticEditResult.rejected(StatisticEditResult.Status.AMOUNT_TOO_LARGE, null);
        }
        if (!player.isOnline()) {
            if (!plugin.settings().allowOfflinePlayerEdits()) {
                return StatisticEditResult.rejected(StatisticEditResult.Status.OFFLINE_NOT_ALLOWED, null);
            }
            if (!plugin.settings().allowUnknownOfflineTargets() && !player.hasPlayedBefore()) {
                return StatisticEditResult.rejected(StatisticEditResult.Status.UNKNOWN_PLAYER, null);
            }
        }

        int previousValue;
        int currentValue;
        UUID playerId = player.getUniqueId();
        try {
            previousValue = read(player, target);
            currentValue = switch (action) {
                case SET -> amount;
                case ADD -> {
                    long sum = (long) previousValue + amount;
                    if (sum > maximum || sum > Integer.MAX_VALUE) {
                        yield -1;
                    }
                    yield (int) sum;
                }
                case TAKE -> Math.max(0, previousValue - amount);
            };
            if (currentValue < 0) {
                return StatisticEditResult.rejected(StatisticEditResult.Status.AMOUNT_TOO_LARGE, null);
            }
            if (currentValue == previousValue) {
                return StatisticEditResult.applied(previousValue, currentValue);
            }
            write(player, target, currentValue);
        } catch (IllegalArgumentException exception) {
            return StatisticEditResult.rejected(StatisticEditResult.Status.INVALID_STATISTIC, exception.getMessage());
        }

        Bukkit.getPluginManager().callEvent(new FoStatisticEditedEvent(playerId, target, action,
                previousValue, currentValue, origin, sourceName, actorId));
        return StatisticEditResult.applied(previousValue, currentValue);
    }

    private int read(OfflinePlayer player, StatisticTarget target) {
        return switch (target.statistic().getType()) {
            case UNTYPED -> player.getStatistic(target.statistic());
            case BLOCK, ITEM -> player.getStatistic(target.statistic(), target.material());
            case ENTITY -> player.getStatistic(target.statistic(), target.entityType());
        };
    }

    private void write(OfflinePlayer player, StatisticTarget target, int value) {
        switch (target.statistic().getType()) {
            case UNTYPED -> player.setStatistic(target.statistic(), value);
            case BLOCK, ITEM -> player.setStatistic(target.statistic(), target.material(), value);
            case ENTITY -> player.setStatistic(target.statistic(), target.entityType(), value);
        }
    }
}
