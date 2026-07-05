package me.foesio.foStatistics.statistic;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public record StatisticKey(Statistic statistic, Material material, EntityType entityType) {
    private static final String TIME_PLAYED_ALIAS = "time_played";

    public int get(OfflinePlayer player) {
        return switch (statistic.getType()) {
            case UNTYPED -> player.getStatistic(statistic);
            case BLOCK, ITEM -> player.getStatistic(statistic, material);
            case ENTITY -> player.getStatistic(statistic, entityType);
        };
    }

    public void set(OfflinePlayer player, int value) {
        switch (statistic.getType()) {
            case UNTYPED -> player.setStatistic(statistic, value);
            case BLOCK, ITEM -> player.setStatistic(statistic, material, value);
            case ENTITY -> player.setStatistic(statistic, entityType, value);
        }
    }

    public String displayName() {
        String base = statistic == Statistic.PLAY_ONE_MINUTE
                ? TIME_PLAYED_ALIAS
                : statistic.name().toLowerCase(Locale.ROOT);
        return switch (statistic.getType()) {
            case UNTYPED -> base;
            case BLOCK, ITEM -> base + ":" + material.name().toLowerCase(Locale.ROOT);
            case ENTITY -> base + ":" + entityType.name().toLowerCase(Locale.ROOT);
        };
    }

    public boolean isTimePlayed() {
        return statistic == Statistic.PLAY_ONE_MINUTE;
    }
}
