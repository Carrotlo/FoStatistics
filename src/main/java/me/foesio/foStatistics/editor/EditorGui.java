package me.foesio.foStatistics.editor;

import me.foesio.core.editor.ConfigEditorButton;
import me.foesio.core.editor.ConfigEditorMenu;
import me.foesio.core.editor.ConfigEditorValueType;
import me.foesio.core.editor.EditorMenuHolder;
import me.foesio.core.editor.EditorSaveResult;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.foStatistics.FoStatistics;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public final class EditorGui implements Listener {
    private static final String MENU_ID = "fostatistics-editor";
    private static final String FILE_LOGGING_BUTTON = "buttons.file-logging";
    private static final String ALLOW_OFFLINE_BUTTON = "buttons.allow-offline";
    private static final String ALLOW_UNKNOWN_BUTTON = "buttons.allow-unknown";
    private static final String MAX_AMOUNT_BUTTON = "buttons.max-amount";

    private static final int FILE_LOGGING_SLOT = 11;
    private static final int ALLOW_OFFLINE_SLOT = 13;
    private static final int ALLOW_UNKNOWN_SLOT = 14;
    private static final int MAX_AMOUNT_SLOT = 15;

    private final FoStatistics plugin;
    private ConfigEditorMenu editor;

    public EditorGui(FoStatistics plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.editor = createEditor();
    }

    public void open(Player player) {
        if (!player.hasPermission(FoStatistics.ADMIN_PERMISSION)) {
            plugin.messages().send(player, "no-permission");
            return;
        }

        editor().open(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorMenuHolder holder)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission(FoStatistics.ADMIN_PERMISSION)) {
            plugin.messages().send(player, "no-permission");
            player.closeInventory();
            return;
        }

        Optional<ConfigEditorButton> clicked = editor().buttonAt(holder, event.getRawSlot());
        if (clicked.isEmpty()) {
            return;
        }

        ConfigEditorButton button = clicked.get();
        if (button.type() == ConfigEditorValueType.BOOLEAN) {
            save(player, button, editor().toggle(button), editor().displayValue(button));
            return;
        }
        if (button.type() == ConfigEditorValueType.INTEGER) {
            openIntegerInput(player, button);
        }
    }

    private void openIntegerInput(Player player, ConfigEditorButton button) {
        plugin.editorInputService().openNumberInput(
                player,
                String.valueOf(button.integerValue()),
                value -> handleIntegerInput(player, button, value),
                () -> {
                    plugin.messages().send(player, "editor-prompt-cancelled");
                    open(player);
                }
        );
    }

    private void handleIntegerInput(Player player, ConfigEditorButton button, String message) {
        OptionalInt parsed = editor().parseInteger(message, 0, Integer.MAX_VALUE);
        if (parsed.isEmpty()) {
            plugin.messages().send(player, "invalid-amount");
            open(player);
            return;
        }

        int value = parsed.getAsInt();
        save(player, button, editor().save(button, value), String.valueOf(value));
    }

    private void save(Player player, ConfigEditorButton button, EditorSaveResult result, String value) {
        if (result.successful()) {
            plugin.messages().send(player, "editor-setting-saved", Map.of("setting", button.label(), "value", value));
            plugin.logInfo(player.getName() + " changed editor setting " + button.configPath() + " to " + value + ".");
        } else {
            plugin.messages().send(player, "editor-setting-failed", Map.of("setting", button.label()));
            plugin.logWarning("Failed saving editor setting " + button.configPath() + ": " + result.errorMessage());
        }
        open(player);
    }

    private ConfigEditorMenu editor() {
        if (editor == null) {
            reload();
        }
        return editor;
    }

    private ConfigEditorMenu createEditor() {
        return ConfigEditorMenu.builder(plugin, plugin.messages(), editorDefaults())
                .id(MENU_ID)
                .title("title", "FoStatistics Editor")
                .size("size", 27)
                .filler("filler", true, Material.GRAY_STAINED_GLASS_PANE)
                .reloadSettings(this::reloadPluginSettings)
                .button(ConfigEditorButton.booleanSetting("file-logging", "file-logging",
                        "File logging", FILE_LOGGING_BUTTON, FILE_LOGGING_SLOT,
                        () -> plugin.settings().fileLogging()).build())
                .button(ConfigEditorButton.booleanSetting("allow-offline", "statistics.allow-offline-player-edits",
                        "Offline edits", ALLOW_OFFLINE_BUTTON, ALLOW_OFFLINE_SLOT,
                        () -> plugin.settings().allowOfflinePlayerEdits()).build())
                .button(ConfigEditorButton.booleanSetting("allow-unknown", "statistics.allow-unknown-offline-targets",
                        "Unknown offline targets", ALLOW_UNKNOWN_BUTTON, ALLOW_UNKNOWN_SLOT,
                        () -> plugin.settings().allowUnknownOfflineTargets()).build())
                .button(ConfigEditorButton.integerSetting("max-amount", "statistics.max-statistic-amount",
                        "Max statistic amount", MAX_AMOUNT_BUTTON, MAX_AMOUNT_SLOT,
                        () -> plugin.settings().maxStatisticAmount())
                        .fallbackMaterial(Material.COMPARATOR)
                        .build())
                .build();
    }

    private FileConfiguration editorDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("title", "FoStatistics Editor");
        defaults.set("size", 27);
        defaults.set("filler.enabled", true);
        defaults.set("filler.material", Material.GRAY_STAINED_GLASS_PANE.name());
        button(defaults, FILE_LOGGING_BUTTON, FILE_LOGGING_SLOT,
                "File Logging",
                List.of("{white}Current: {value}",
                        "{white}Saves activity to logs/latest.log.",
                        "{white}Click to toggle."));
        button(defaults, ALLOW_OFFLINE_BUTTON, ALLOW_OFFLINE_SLOT,
                "Offline Player Edits",
                List.of("{white}Current: {value}",
                        "{white}Allows edits for known offline players.",
                        "{white}Click to toggle."));
        button(defaults, ALLOW_UNKNOWN_BUTTON, ALLOW_UNKNOWN_SLOT,
                "Unknown Offline Targets",
                List.of("{white}Current: {value}",
                        "{white}When disabled, typos cannot create player data.",
                        "{white}Click to toggle."));
        button(defaults, MAX_AMOUNT_BUTTON, MAX_AMOUNT_SLOT,
                "Max Statistic Amount",
                List.of("{white}Current: {theme}{value}",
                        "{white}Click to type a new value."));
        defaults.set(MAX_AMOUNT_BUTTON + ".material", Material.COMPARATOR.name());
        return defaults;
    }

    private void button(YamlConfiguration defaults, String path, int slot, String name, List<String> lore) {
        defaults.set(path + ".slot", slot);
        defaults.set(path + ".name", "{theme}" + name);
        defaults.set(path + ".lore", lore);
    }

    private void reloadPluginSettings() {
        FoReloadResult result = plugin.reloadPluginFiles();
        if (!result.successful()) {
            throw new IllegalStateException(result.failedStep() + ": " + result.errorMessage());
        }
    }
}
