package me.foesio.foStatistics.api.event;

import me.foesio.foStatistics.api.StatisticEditAction;
import me.foesio.foStatistics.api.StatisticTarget;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/** Fired synchronously after a FoStatistics edit changes a Bukkit statistic. Never cancellable. */
public final class FoStatisticEditedEvent extends Event {
    public enum Origin { COMMAND, API }

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final StatisticTarget target;
    private final StatisticEditAction action;
    private final int previousValue;
    private final int newValue;
    private final Origin origin;
    private final String sourceName;
    private final UUID actorId;

    public FoStatisticEditedEvent(UUID playerId, StatisticTarget target, StatisticEditAction action,
                                  int previousValue, int newValue, Origin origin, String sourceName, UUID actorId) {
        super(false);
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.target = Objects.requireNonNull(target, "target");
        this.action = Objects.requireNonNull(action, "action");
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.actorId = actorId;
    }

    public UUID getPlayerId() { return playerId; }
    public StatisticTarget getTarget() { return target; }
    public StatisticEditAction getAction() { return action; }
    public int getPreviousValue() { return previousValue; }
    public int getNewValue() { return newValue; }
    public Origin getOrigin() { return origin; }
    /** Command sender name for COMMAND, plugin name for API. */
    public String getSourceName() { return sourceName; }
    /** UUID of a player command sender, otherwise null. */
    public UUID getActorId() { return actorId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
