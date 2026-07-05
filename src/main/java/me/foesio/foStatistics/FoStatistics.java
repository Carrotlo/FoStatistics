package me.foesio.foStatistics;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foStatistics.command.AdminCommand;
import me.foesio.foStatistics.editor.EditorGui;
import me.foesio.foStatistics.input.EditorInputService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public final class FoStatistics extends JavaPlugin {
    public static final String ADMIN_PERMISSION = "fostatistics.admin";
    private static final String MODRINTH_PROJECT_ID = "fostatistics";

    private PluginSettings settings;
    private FoMessageService messages;
    private FoFileLogger fileLogger;
    private UpdateNoticeService updateNotices;
    private EditorGui editorGui;
    private EditorInputService editorInputService;
    private FoCoreContext core;

    @Override
    public void onEnable() {
        ensureResourceFiles();

        reloadConfig();
        this.settings = PluginSettings.from(getConfig());
        reloadCoreContext();
        this.messages = FoMessageService.load(this);
        ensurePluginMessageDefaults();

        this.fileLogger = FoFileLogger.create(this);
        this.fileLogger.configure(settings.fileLogging(), true);

        this.editorInputService = new EditorInputService(this);
        this.editorInputService.reload();

        this.updateNotices = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID).start();
        this.editorGui = new EditorGui(this);
        this.editorGui.reload();

        registerAdminCommand();
        registerListeners();

        logInfo("Enabled version " + getPluginMeta().getVersion() + ".");
    }

    @Override
    public void onDisable() {
        logInfo("Disabled.");
        if (fileLogger != null) {
            fileLogger.shutdown();
        }
        closeCoreContext();
    }

    private void registerAdminCommand() {
        AdminCommand adminCommand = new AdminCommand(this);
        PluginCommand command = getCommand("fostatisticsadmin");
        if (command != null) {
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        } else {
            logWarning("Admin command missing from plugin.yml.");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(editorGui, this);
    }

    public PluginSettings settings() {
        return settings;
    }

    public FoMessageService messages() {
        return messages;
    }

    public UpdateNoticeService updateNotices() {
        return updateNotices;
    }

    public EditorGui editorGui() {
        return editorGui;
    }

    public EditorInputService editorInputService() {
        return editorInputService;
    }

    public FoCoreContext core() {
        return core;
    }

    public FoReloadResult reloadPluginFiles() {
        FoReloadRegistry registry = FoReloadRegistry.create()
                .add("config", () -> {
                    reloadConfig();
                    this.settings = PluginSettings.from(getConfig());
                    reloadCoreContext();
                })
                .add("messages", this::reloadMessages)
                .add("input", editorInputService::reload)
                .add("editor", editorGui::reload)
                .add("file logging", () -> fileLogger.configure(settings.fileLogging(), false));

        FoReloadResult result = registry.reload();
        if (result.successful()) {
            logInfo("Config, messages, editor, and file logging reloaded.");
        }
        return result;
    }

    public void logInfo(String message) {
        getLogger().info("[FoStatistics] " + message);
        if (fileLogger != null) {
            fileLogger.info(message);
        }
    }

    public void logWarning(String message) {
        getLogger().warning("[FoStatistics] " + message);
        if (fileLogger != null) {
            fileLogger.warn(message);
        }
    }

    public void logError(String message, Throwable throwable) {
        if (throwable != null) {
            getLogger().log(Level.SEVERE, "[FoStatistics] " + message, throwable);
        } else {
            getLogger().severe("[FoStatistics] " + message);
        }
        if (fileLogger != null) {
            fileLogger.error(message, throwable);
        }
    }

    private void ensureResourceFiles() {
        saveDefaultConfig();
        NativeDialogConfigDefaults.addDefaults(this);
        saveConfig();
        ResourceFiles.saveDefault(this, "messages.yml");
    }

    private void reloadMessages() {
        messages.reload();
        ensurePluginMessageDefaults();
    }

    private void ensurePluginMessageDefaults() {
        FileConfiguration defaults = bundledMessages();
        if (defaults == null) {
            return;
        }

        FileConfiguration config = messages.config();
        if (!addMissingDefaults(config, defaults, "")) {
            return;
        }

        config.options().copyDefaults(true);
        messages.save();
        messages.reload();
    }

    private FileConfiguration bundledMessages() {
        try (InputStream stream = getResource("messages.yml")) {
            if (stream == null) {
                logWarning("Bundled messages.yml is missing.");
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            logError("Could not load bundled messages.yml defaults.", exception);
            return null;
        }
    }

    private boolean addMissingDefaults(ConfigurationSection target, ConfigurationSection source, String prefix) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String path = prefix.isBlank() ? key : prefix + "." + key;
            if (source.isConfigurationSection(key)) {
                ConfigurationSection child = source.getConfigurationSection(key);
                if (child != null) {
                    changed |= addMissingDefaults(target, child, path);
                }
                continue;
            }
            if (!target.contains(path)) {
                target.addDefault(path, source.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private void reloadCoreContext() {
        if (core != null) {
            core.close();
        }
        this.core = FoPluginCore.create(this);
        this.core.warnIfNativeDialogsUnavailable();
    }

    private void closeCoreContext() {
        if (core != null) {
            core.close();
            core = null;
        }
    }
}
