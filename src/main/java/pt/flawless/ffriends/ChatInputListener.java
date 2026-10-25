package pt.flawless.ffriends;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
    public void onPrompt(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!addPrompt.isPrompting(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        try {
            Bukkit.getScheduler().runTask(plugin, () -> addPrompt.handle(p, text));
        } catch (RuntimeException ignored) {
            // plugin a desligar
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrivate(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Optional<UUID> partner = privateChats.partnerOf(p.getUniqueId());
        if (partner.isEmpty()) return;
        // cancela sempre: uma mensagem pensada como privada nunca vai ao chat público
        e.setCancelled(true);
        Player target = Bukkit.getPlayer(partner.get());
        if (target == null) {
            privateChats.disable(p.getUniqueId());
            p.sendMessage(config.msg("chat-partner-left").replace("{jogador}", FriendsCommand.name(partner.get())));
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        p.sendMessage(config.msg("private-sent").replace("{jogador}", target.getName()).replace("{mensagem}", text));
        target.sendMessage(config.msg("private-received").replace("{jogador}", p.getName()).replace("{mensagem}", text));
    }
}
