package pt.flawless.ffriends;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import pt.flawless.fapi.events.PlayerAuthEvent.PlayerAuthEvent;

import java.util.UUID;
import java.util.logging.Level;

/** Carrega os amigos ao autenticar e avisa entradas e saídas. */
public class FriendsSessionListener implements Listener {

    private final FFriends plugin;
    private final FriendsService service;
    private final FriendsConfig config;
    private final PrivateChats privateChats;

    public FriendsSessionListener(FFriends plugin, FriendsService service, FriendsConfig config, PrivateChats privateChats) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
        this.privateChats = privateChats;
    }

    @EventHandler
    public void onAuth(PlayerAuthEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        service.load(uuid);
        announce(p, "friend-join");
        service.requestsTo(uuid).whenComplete((requests, ex) -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "falha ao ler os pedidos de " + p.getName(), ex);
                return;
            }
            if (requests.isEmpty()) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) {
                        p.sendMessage(config.msg("requests-pending").replace("{n}", String.valueOf(requests.size())));
                    }
                });
            } catch (RuntimeException ignored) {
                // plugin a desligar
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        // quem nunca se autenticou não está carregado e não avisa ninguém
        if (!service.isLoaded(uuid)) return;
        announce(p, "friend-quit");
        privateChats.disable(uuid);
        for (UUID other : privateChats.disableAllWith(uuid)) {
            Player o = Bukkit.getPlayer(other);
            if (o != null) o.sendMessage(config.msg("chat-partner-left").replace("{jogador}", p.getName()));
        }
        service.unload(uuid);
    }

    /** Avisa os amigos online que têm os avisos deste jogador ligados. */
    private void announce(Player p, String key) {
        String text = config.msg(key).replace("{jogador}", p.getName());
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            Friend f = service.friendsOf(o.getUniqueId()).get(p.getUniqueId());
            if (f != null && f.notifyEnabled()) o.sendMessage(text);
        }
    }
}
