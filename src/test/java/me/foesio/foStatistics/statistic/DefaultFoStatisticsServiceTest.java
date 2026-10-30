package me.foesio.foStatistics.statistic;

import me.foesio.foStatistics.FoStatistics;
import me.foesio.foStatistics.PluginSettings;
import me.foesio.foStatistics.api.StatisticEditAction;
import me.foesio.foStatistics.api.StatisticEditResult;
import me.foesio.foStatistics.api.StatisticTarget;
import me.foesio.foStatistics.api.event.FoStatisticEditedEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultFoStatisticsServiceTest {
    @Test
    void apiEditChangesStatisticAndFiresPostEvent() {
        FoStatistics plugin = plugin(100, true);
        Plugin caller = mock(Plugin.class);
        when(caller.isEnabled()).thenReturn(true);
        when(caller.getName()).thenReturn("QuestPlugin");
        OfflinePlayer player = player(4);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            StatisticEditResult result = new DefaultFoStatisticsService(plugin).edit(caller, player,
                    StatisticTarget.of(Statistic.JUMP), StatisticEditAction.ADD, 3);

            assertEquals(StatisticEditResult.Status.CHANGED, result.status());
            assertEquals(4, result.previousValue());
            assertEquals(7, result.currentValue());
            InOrder order = inOrder(player, pluginManager);
            order.verify(player).setStatistic(Statistic.JUMP, 7);
            ArgumentCaptor<FoStatisticEditedEvent> event = ArgumentCaptor.forClass(FoStatisticEditedEvent.class);
            order.verify(pluginManager).callEvent(event.capture());
            assertEquals(player.getUniqueId(), event.getValue().getPlayerId());
            assertEquals(4, event.getValue().getPreviousValue());
            assertEquals(7, event.getValue().getNewValue());
            assertEquals(FoStatisticEditedEvent.Origin.API, event.getValue().getOrigin());
            assertEquals("QuestPlugin", event.getValue().getSourceName());
            assertNull(event.getValue().getActorId());
            assertFalse(event.getValue().isAsynchronous());
        }
    }

    @Test
    void unchangedEditDoesNotCallSetterOrEvent() {
        FoStatistics plugin = plugin(100, true);
        Plugin caller = mock(Plugin.class);
        when(caller.isEnabled()).thenReturn(true);
        OfflinePlayer player = player(4);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            StatisticEditResult result = new DefaultFoStatisticsService(plugin).edit(caller, player,
                    StatisticTarget.of(Statistic.JUMP), StatisticEditAction.SET, 4);

            assertEquals(StatisticEditResult.Status.UNCHANGED, result.status());
            verify(player, never()).setStatistic(Statistic.JUMP, 4);
            verify(pluginManager, never()).callEvent(org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void offlineRuleAndLimitRejectWithoutMutation() {
        FoStatistics plugin = plugin(5, false);
        Plugin caller = mock(Plugin.class);
        when(caller.isEnabled()).thenReturn(true);
        OfflinePlayer player = player(4);
        when(player.isOnline()).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            DefaultFoStatisticsService service = new DefaultFoStatisticsService(plugin);

            assertEquals(StatisticEditResult.Status.OFFLINE_NOT_ALLOWED,
                    service.edit(caller, player, StatisticTarget.of(Statistic.JUMP), StatisticEditAction.ADD, 1).status());
            assertEquals(StatisticEditResult.Status.AMOUNT_TOO_LARGE,
                    service.edit(caller, player, StatisticTarget.of(Statistic.JUMP), StatisticEditAction.ADD, 6).status());
            verify(player, never()).setStatistic(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Test
    void commandEditHasCommandOriginAndTakeStopsAtZero() {
        FoStatistics plugin = plugin(100, true);
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("CONSOLE");
        OfflinePlayer player = player(4);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            StatisticEditResult result = new DefaultFoStatisticsService(plugin).editFromCommand(sender, player,
                    StatisticTarget.of(Statistic.JUMP), StatisticEditAction.TAKE, 10);

            assertTrue(result.successful());
            assertEquals(0, result.currentValue());
            verify(player).setStatistic(Statistic.JUMP, 0);
            ArgumentCaptor<FoStatisticEditedEvent> event = ArgumentCaptor.forClass(FoStatisticEditedEvent.class);
            verify(pluginManager).callEvent(event.capture());
            assertEquals(FoStatisticEditedEvent.Origin.COMMAND, event.getValue().getOrigin());
            assertEquals("CONSOLE", event.getValue().getSourceName());
        }
    }

    @Test
    void cachedServiceStopsWhenProviderDisables() {
        FoStatistics plugin = plugin(100, true);
        Plugin caller = mock(Plugin.class);
        when(caller.isEnabled()).thenReturn(true);
        OfflinePlayer player = player(4);
        DefaultFoStatisticsService service = new DefaultFoStatisticsService(plugin);
        service.deactivate();

        assertEquals(StatisticEditResult.Status.DISABLED,
                service.edit(caller, player, StatisticTarget.of(Statistic.JUMP), StatisticEditAction.ADD, 1).status());
        verify(player, never()).setStatistic(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void offThreadEditReturnsFailureBeforeReadingPlayer() {
        FoStatistics plugin = plugin(100, true);
        Plugin caller = mock(Plugin.class);
        when(caller.isEnabled()).thenReturn(true);
        OfflinePlayer player = player(4);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

            assertEquals(StatisticEditResult.Status.WRONG_THREAD,
                    new DefaultFoStatisticsService(plugin).edit(caller, player,
                            StatisticTarget.of(Statistic.JUMP), StatisticEditAction.ADD, 1).status());
            verify(player, never()).getStatistic(Statistic.JUMP);
        }
    }

    @Test
    void unknownOfflinePlayerIsRejectedWhenDisabledBySettings() {
        FoStatistics plugin = plugin(100, true);
        Plugin caller = mock(Plugin.class);
        when(caller.isEnabled()).thenReturn(true);
        OfflinePlayer player = player(4);
        when(player.isOnline()).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            assertEquals(StatisticEditResult.Status.UNKNOWN_PLAYER,
                    new DefaultFoStatisticsService(plugin).edit(caller, player,
                            StatisticTarget.of(Statistic.JUMP), StatisticEditAction.ADD, 1).status());
            verify(player, never()).getStatistic(Statistic.JUMP);
        }
    }

    @Test
    void typedStatisticUsesEntityOverloadAndPreservesTargetInEvent() {
        FoStatistics plugin = plugin(100, true);
        Plugin caller = mock(Plugin.class);
        when(caller.isEnabled()).thenReturn(true);
        when(caller.getName()).thenReturn("QuestPlugin");
        OfflinePlayer player = player(4);
        when(player.getStatistic(Statistic.KILL_ENTITY, EntityType.ZOMBIE)).thenReturn(2);
        PluginManager pluginManager = mock(PluginManager.class);
        StatisticTarget target = StatisticTarget.of(Statistic.KILL_ENTITY, EntityType.ZOMBIE);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            StatisticEditResult result = new DefaultFoStatisticsService(plugin).edit(caller, player,
                    target, StatisticEditAction.ADD, 1);

            assertEquals(StatisticEditResult.Status.CHANGED, result.status());
            verify(player).setStatistic(Statistic.KILL_ENTITY, EntityType.ZOMBIE, 3);
            ArgumentCaptor<FoStatisticEditedEvent> event = ArgumentCaptor.forClass(FoStatisticEditedEvent.class);
            verify(pluginManager).callEvent(event.capture());
            assertEquals(target, event.getValue().getTarget());
        }
    }

    private FoStatistics plugin(int maximum, boolean allowOffline) {
        FoStatistics plugin = mock(FoStatistics.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.settings()).thenReturn(new PluginSettings(maximum, allowOffline, false, false));
        return plugin;
    }

    private OfflinePlayer player(int statisticValue) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getStatistic(Statistic.JUMP)).thenReturn(statisticValue);
        return player;
    }
}
