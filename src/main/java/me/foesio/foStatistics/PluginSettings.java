package me.foesio.foStatistics;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
        int maxStatisticAmount,
        boolean allowOfflinePlayerEdits,
        boolean allowUnknownOfflineTargets,
        boolean fileLogging
) {
    public static PluginSettings from(FileConfiguration config) {
        int maxAmount = config.getInt("statistics.max-statistic-amount", Integer.MAX_VALUE);
        if (maxAmount < 0) {
            maxAmount = Integer.MAX_VALUE;
        }

        return new PluginSettings(
                maxAmount,
                config.getBoolean("statistics.allow-offline-player-edits", true),
                config.getBoolean("statistics.allow-unknown-offline-targets", false),
                config.getBoolean("file-logging", false)
        );
    }
}
