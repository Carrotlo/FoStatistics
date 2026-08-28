package me.foesio.foStatistics;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.config.FoConfigDefaults;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foStatistics.command.AdminCommand;
import me.foesio.foStatistics.editor.EditorGui;
import me.foesio.foStatistics.input.EditorInputService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class FoStatistics extends JavaPlugin {
    public static final String ADMIN_PERMISSION = "fostatistics.admin";
    private static final String MODRINTH_PROJECT_ID = "fostatistics";
    private static final int BSTATS_PLUGIN_ID = 32419;

    private PluginSettings settings;
    private FoMessageService messages;
    private FoFileLogger fileLogger;
    private UpdateNoticeService updateNotices;
    private EditorGui editorGui;
    private EditorInputService editorInputService;
    private FoCoreContext core;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;

    @Override
    public void onEnable() {
        FoConfigDefaults.ensureDefaultConfig(this);
        saveConfig();
        this.settings = PluginSettings.from(getConfig());
        reloadCoreContext();
        this.messages = FoMessageService.load(this);
        this.sounds = core.createSounds();
        this.adminSounds = FoAdminSounds.create(sounds);
        this.editorSounds = FoEditorSounds.create(sounds);

        this.fileLogger = FoFileLogger.create(this);
        this.fileLogger.configure(settings.fileLogging(), true);

        this.editorInputService = new EditorInputService(this);
        this.editorInputService.reload();

        this.updateNotices = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();
        this.editorGui = new EditorGui(this, editorSounds);
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
        if (!new AdminCommand(this).register(reloadRegistry())) {
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

    public FoSoundService sounds() {
        return sounds;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public FoEditorSounds editorSounds() {
        return editorSounds;
    }

    public FoReloadResult reloadPluginFiles() {
        FoReloadResult result = reloadRegistry().reload();
        if (result.successful()) {
            logInfo("Config, messages, editor, and file logging reloaded.");
        }
        return result;
    }

    public FoReloadRegistry reloadRegistry() {
        return FoReloadRegistry.create()
                .add("config", () -> {
                    reloadConfig();
                    FoConfigDefaults.addStandardDefaults(this);
                    saveConfig();
                    this.settings = PluginSettings.from(getConfig());
                    reloadCoreContext();
                })
                .addMessages(messages)
                .add("sounds", sounds::reload)
                .add("input", editorInputService::reload)
                .add("editor", editorGui::reload)
                .add("file logging", () -> fileLogger.configure(settings.fileLogging(), false));
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

    private void reloadCoreContext() {
        if (core != null) {
            core.close();
        }
        this.core = FoPluginCore.create(this);
        this.core.metrics(BSTATS_PLUGIN_ID);
        this.core.warnIfNativeDialogsUnavailable();
        if (sounds != null) {
            sounds.reload();
        }
    }

    private void closeCoreContext() {
        if (core != null) {
            core.close();
            core = null;
        }
    }
}
