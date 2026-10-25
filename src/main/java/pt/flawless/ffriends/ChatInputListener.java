package pt.flawless.ffriends;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pt.flawless.ffriends.menu.AddPrompt;

import java.util.Optional;
import java.util.UUID;

/** Chat assíncrono: captura o prompt de "adicionar amigo" e encaminha o chat privado. */
public class ChatInputListener implements Listener {

    private final FFriends plugin;
    private final FriendsConfig config;
    private final AddPrompt addPrompt;
    private final PrivateChats privateChats;

    public ChatInputListener(FFriends plugin, FriendsConfig config, AddPrompt addPrompt, PrivateChats privateChats) {
        this.plugin = plugin;
        this.config = config;
        this.addPrompt = addPrompt;
        this.privateChats = privateChats;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrompt(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!addPrompt.isPrompting(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = e.getMessage();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) addPrompt.handle(p, text);
            });
        } catch (RuntimeException ignored) {
            // plugin a desligar
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrivate(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Optional<UUID> partner = privateChats.partnerOf(p.getUniqueId());
        if (partner.isEmpty()) return;
        // cancela sempre: uma mensagem pensada como privada nunca vai ao chat público
        e.setCancelled(true);
        Player target = Bukkit.getPlayer(partner.get());
        if (target == null) {
            privateChats.disable(p.getUniqueId());
            UUID partnerId = partner.get();
            try {
                // o nome offline resolve-se na thread principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) {
                        p.sendMessage(config.msg("chat-partner-left").replace("{jogador}", FriendsCommand.name(partnerId)));
                    }
                });
            } catch (RuntimeException ignored) {
                // plugin a desligar
            }
            return;
        }
        String text = e.getMessage();
        p.sendMessage(config.msg("private-sent").replace("{jogador}", target.getName()).replace("{mensagem}", text));
        target.sendMessage(config.msg("private-received").replace("{jogador}", p.getName()).replace("{mensagem}", text));
    }
}
