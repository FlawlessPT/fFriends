package pt.flawless.ffriends.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pt.flawless.ffriends.FriendsCommand;
import pt.flawless.ffriends.FriendsConfig;
import pt.flawless.ffriends.FriendsService.AddResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** "Adicionar amigo" do menu: o próximo texto no chat é o nome. Só thread principal; o listener de chat chama {@link #handle}. */
public class AddPrompt {

    private final Plugin plugin;
    private final FriendsConfig config;
    private final FriendsCommand command;
    private final Consumer<Player> onAdded;
    /** Jogador -> instante em que o prompt expira. */
    private final Map<UUID, Long> prompts = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    AddPrompt(Plugin plugin, FriendsConfig config, FriendsCommand command, Consumer<Player> onAdded) {
        this.plugin = plugin;
        this.config = config;
        this.command = command;
        this.onAdded = onAdded;
    }

    public void start(Player p) {
        UUID uuid = p.getUniqueId();
        long expires = System.currentTimeMillis() + config.promptSeconds() * 1000L;
        prompts.put(uuid, expires);
        p.sendMessage(config.msg("prompt-start").replace("{n}", String.valueOf(config.promptSeconds())));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // só expira este prompt, não um mais recente
            if (!prompts.remove(uuid, expires)) return;
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) online.sendMessage(config.msg("prompt-expired"));
        }, config.promptSeconds() * 20L);
    }

    public boolean isPrompting(UUID uuid) {
        Long expires = prompts.get(uuid);
        return expires != null && expires > System.currentTimeMillis();
    }

    /** Em erro o prompt continua ativo até expirar; em SENT/ACCEPTED termina e reabre o menu. */
    public void handle(Player p, String text) {
        UUID uuid = p.getUniqueId();
        String name = text.trim();
        if (name.equalsIgnoreCase("cancelar")) {
            prompts.remove(uuid);
            p.sendMessage(config.msg("prompt-cancelled"));
            return;
        }
        if (busy.contains(uuid)) return;
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            p.sendMessage(config.msg("player-not-found"));
            return;
        }
        busy.add(uuid);
        // as mensagens (e a de erro) ficam a cargo do comando
        command.add(p, target).whenComplete((result, e) -> Bukkit.getScheduler().runTask(plugin, () -> {
            busy.remove(uuid);
            if (e != null || (result != AddResult.SENT && result != AddResult.ACCEPTED)) return;
            if (prompts.remove(uuid) != null && p.isOnline()) onAdded.accept(p);
        }));
    }
}
