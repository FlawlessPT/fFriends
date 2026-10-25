package pt.flawless.ffriends;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.database.DatabaseService;
import pt.flawless.fapi.database.Databases;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.friends.FriendsApi;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.fapi.permissions.Permissions;
import pt.flawless.fapi.permissions.PermissionsApi;
import pt.flawless.fapi.permissions.PermissionsUnavailableException;
import pt.flawless.ffriends.menu.FriendsMenu;

import java.sql.SQLException;
import java.util.List;

public class FFriends extends JavaPlugin {
    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());
    private boolean enabledOk = false;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            FriendsConfig config = FriendsConfig.from(getConfig());
            DatabaseService database = Databases.get();
            database.migrate(this, List.of("db/ffriends/V001__create_friends.sql"));
            FriendsRepository repository = new SqlFriendsRepository(database, config.requestExpireDays());
            FriendsService service = new FriendsService(config, repository, permissions(), getLogger());
            getServer().getServicesManager().register(FriendsApi.class, service, this, ServicePriority.Normal);

            FriendsCommand command = new FriendsCommand(this, service, config);
            getCommand("amigo").setExecutor(command);
            getCommand("amigo").setTabCompleter(command);
            FriendsMenu menu = new FriendsMenu(this, service, config, command);
            command.setMenuOpener(menu::openMain);
            getServer().getPluginManager().registerEvents(menu, this);

            repository.purgeExpired(config.requestExpireDays()).exceptionally(e -> {
                consoleLogger.sendMessage("falha ao limpar pedidos expirados: " + e.getMessage(), EMessageTypeEnum.ERROR);
                return null;
            });
        } catch (SQLException | RuntimeException e) {
            // inclui DatabaseUnavailableException (RuntimeException)
            consoleLogger.sendMessage("não foi possível iniciar os amigos: " + e.getMessage(), EMessageTypeEnum.ERROR);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        enabledOk = true;
        consoleLogger.sendEnablePluginMessage();
    }

    /** fPermissions é opcional: sem ele todos ficam com o limite por omissão. */
    private PermissionsApi permissions() {
        try {
            return Permissions.get();
        } catch (PermissionsUnavailableException e) {
            consoleLogger.sendMessage("fPermissions indisponível: limite de amigos por omissão para todos",
                    EMessageTypeEnum.INFO);
            return null;
        }
    }

    @Override
    public void onDisable() {
        if (enabledOk) consoleLogger.sendDisablePluginMessage();
    }
}
