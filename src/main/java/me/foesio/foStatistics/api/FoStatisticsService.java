package me.foesio.foStatistics.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/** Supported cross-plugin entry point for edits governed by FoStatistics settings. */
public interface FoStatisticsService {
    /**
     * Applies one edit on the primary server thread. Amount is non-negative and measured in
     * raw statistic units; time played uses ticks. The caller must be an enabled plugin.
     * A successful no-op returns UNCHANGED and does not fire an event.
     * Bukkit owns statistic persistence; CHANGED means its setter returned successfully.
     * The caller must not invoke this from an asynchronous or region thread.
     */
    StatisticEditResult edit(Plugin caller, OfflinePlayer player, StatisticTarget target,
                             StatisticEditAction action, int amount);
}
