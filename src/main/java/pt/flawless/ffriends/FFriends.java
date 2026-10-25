package pt.flawless.ffriends;

import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.database.DatabaseService;
import pt.flawless.fapi.database.Databases;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;

import java.sql.SQLException;
import java.util.List;

public class FFriends extends JavaPlugin {
    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());
    private boolean enabledOk = false;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            DatabaseService database = Databases.get();
            database.migrate(this, List.of("db/ffriends/V001__create_friends.sql"));
        } catch (SQLException | RuntimeException e) {
            // inclui DatabaseUnavailableException (RuntimeException)
            consoleLogger.sendMessage("não foi possível iniciar os amigos: " + e.getMessage(), EMessageTypeEnum.ERROR);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        enabledOk = true;
        consoleLogger.sendEnablePluginMessage();
    }

    @Override
    public void onDisable() {
        if (enabledOk) consoleLogger.sendDisablePluginMessage();
    }
}
