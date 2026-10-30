package me.foesio.foStatistics.api;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.Objects;

/** Identifies one Minecraft statistic, including its required material or entity type. */
public record StatisticTarget(Statistic statistic, Material material, EntityType entityType) {
    public StatisticTarget {
        Objects.requireNonNull(statistic, "statistic");
        switch (statistic.getType()) {
            case UNTYPED -> {
                if (material != null || entityType != null) {
                    throw new IllegalArgumentException("Untyped statistics have no target.");
                }
            }
            case BLOCK -> {
                if (material == null || !material.isBlock() || entityType != null) {
                    throw new IllegalArgumentException("Block statistics require a block material.");
                }
            }
            case ITEM -> {
                if (material == null || !material.isItem() || entityType != null) {
                    throw new IllegalArgumentException("Item statistics require an item material.");
                }
            }
            case ENTITY -> {
                if (entityType == null || material != null) {
                    throw new IllegalArgumentException("Entity statistics require an entity type.");
                }
            }
        }
    }

    public static StatisticTarget of(Statistic statistic) {
        return new StatisticTarget(statistic, null, null);
    }

    public static StatisticTarget of(Statistic statistic, Material material) {
        return new StatisticTarget(statistic, material, null);
    }

    public static StatisticTarget of(Statistic statistic, EntityType entityType) {
        return new StatisticTarget(statistic, null, entityType);
    }
}
