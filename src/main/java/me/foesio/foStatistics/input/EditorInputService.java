package me.foesio.foStatistics.input;

import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.foStatistics.FoStatistics;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EditorInputService {
    private final FoStatistics plugin;
    private final Set<UUID> warnedPlayers = ConcurrentHashMap.newKeySet();

    public EditorInputService(FoStatistics plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        warnedPlayers.clear();
        NativeDialogSupport support = plugin.core().nativeDialogs();
        if (support.canUseNativeDialogs()) {
            plugin.logInfo("Native dialog input enabled.");
        }
    }

    public void openNumberInput(Player player, String currentValue, Consumer<String> onSubmit, Runnable onCancel) {
        TextDialogRequest request = editorNumberRequest(currentValue);
        NativeDialogSupport support = plugin.core().nativeDialogs();
        if (!support.canUseNativeDialogs()) {
            warnFallback(player, support);
        }
        plugin.core().scheduler().runLaterForPlayer(player, () -> EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.core().inventoryCloseSuppressor(),
                plugin.core().dialogService(),
                player,
                request,
                onSubmit,
                onCancel
        ), 1L);
    }

    private TextDialogRequest editorNumberRequest(String currentValue) {
        return TextDialogRequest.number(
                List.of(render("{muted}Enter a whole number 0 or higher.")),
                currentValue,
                "2147483647"
        );
    }

    private void warnFallback(Player player, NativeDialogSupport support) {
        if (!support.configEnabled()
                || support.canUseNativeDialogs()
                || !support.warnOnFallback()
                || !warnedPlayers.add(player.getUniqueId())) {
            return;
        }
        plugin.messages().send(player, "native-dialog-fallback", Map.of("reason", support.availability().reason()));
    }

    private String render(String template) {
        return plugin.messages().renderTemplate(template, Map.of());
    }

}
