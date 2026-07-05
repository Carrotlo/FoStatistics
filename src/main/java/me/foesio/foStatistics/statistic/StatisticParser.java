package me.foesio.foStatistics.statistic;

import me.foesio.core.material.MaterialTypes;
import me.foesio.core.mob.MobTypes;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public final class StatisticParser {
    public StatisticKey parse(String input) throws StatisticParseException {
        String normalized = normalize(input);
        String[] parts = normalized.split(":", 2);
        Statistic statistic = parseStatistic(parts[0]);
        String target = parts.length == 2 ? parts[1] : null;

        return switch (statistic.getType()) {
            case UNTYPED -> parseUntyped(statistic, target);
            case BLOCK -> parseBlock(statistic, target);
            case ITEM -> parseItem(statistic, target);
            case ENTITY -> parseEntity(statistic, target);
        };
    }

    private StatisticKey parseUntyped(Statistic statistic, String target) throws StatisticParseException {
        if (target != null && !target.isBlank()) {
            throw new StatisticParseException(displayName(statistic) + " does not use a target.");
        }
        return new StatisticKey(statistic, null, null);
    }

    private StatisticKey parseBlock(Statistic statistic, String target) throws StatisticParseException {
        Material material = parseMaterial(statistic, target);
        if (!material.isBlock()) {
            throw new StatisticParseException(material.name().toLowerCase(Locale.ROOT) + " is not a block.");
        }
        return new StatisticKey(statistic, material, null);
    }

    private StatisticKey parseItem(Statistic statistic, String target) throws StatisticParseException {
        Material material = parseMaterial(statistic, target);
        if (!material.isItem()) {
            throw new StatisticParseException(material.name().toLowerCase(Locale.ROOT) + " is not an item.");
        }
        return new StatisticKey(statistic, material, null);
    }

    private StatisticKey parseEntity(Statistic statistic, String target) throws StatisticParseException {
        if (target == null || target.isBlank()) {
            throw new StatisticParseException(displayName(statistic) + " needs an entity, like " + displayName(statistic) + ":zombie.");
        }

        EntityType entityType = MobTypes.match(target);
        if (entityType == null) {
            throw new StatisticParseException("Unknown entity: " + target.toLowerCase(Locale.ROOT) + ".");
        }
        return new StatisticKey(statistic, null, entityType);
    }

    private Material parseMaterial(Statistic statistic, String target) throws StatisticParseException {
        if (target == null || target.isBlank()) {
            throw new StatisticParseException(displayName(statistic) + " needs a material, like " + displayName(statistic) + ":stone.");
        }

        Material material = MaterialTypes.match(target);
        if (material == null) {
            throw new StatisticParseException("Unknown material: " + target.toLowerCase(Locale.ROOT) + ".");
        }
        return material;
    }

    private Statistic parseStatistic(String input) throws StatisticParseException {
        String statisticName = input.equals("TIME_PLAYED") ? "PLAY_ONE_MINUTE" : input;
        try {
            return Statistic.valueOf(statisticName);
        } catch (IllegalArgumentException exception) {
            throw new StatisticParseException("Unknown statistic: " + input.toLowerCase(Locale.ROOT) + ".");
        }
    }

    private String normalize(String input) {
        return input.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    private String displayName(Statistic statistic) {
        return statistic == Statistic.PLAY_ONE_MINUTE
                ? "time_played"
                : statistic.name().toLowerCase(Locale.ROOT);
    }
}
