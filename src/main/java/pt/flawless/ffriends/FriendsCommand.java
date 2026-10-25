package pt.flawless.ffriends;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

/** {@code /amigo}: só jogadores. Os futuros do serviço voltam à thread principal antes de falar com o Bukkit. */
public class FriendsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("add", "aceitar", "recusar", "remover", "listar", "pedidos",
            "tp", "tpauto", "avisos", "chat", "help");
    /** Aliases em inglês: aceites mas fora do help e do tab-complete. */
    private static final Map<String, String> ALIASES = Map.of(
            "accept", "aceitar", "decline", "recusar", "deny", "recusar", "remove", "remover",
            "list", "listar", "requests", "pedidos", "notify", "avisos");

    private record Pending(UUID friend, long expires) {
    }

    private final FFriends plugin;
    private final FriendsService service;
    private final FriendsConfig config;
    private final Map<UUID, Pending> removals = new HashMap<>();

    /** Ligados pelas tarefas 5 (menu) e 6 (chat privado); até lá o comando diz "indisponível". */
    private Consumer<Player> menuOpener;
    private BiConsumer<Player, Player> chatToggler;

    public FriendsCommand(FFriends plugin, FriendsService service, FriendsConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
    }

    public void setMenuOpener(Consumer<Player> menuOpener) {
        this.menuOpener = menuOpener;
    }

    /** O segundo jogador é {@code null} para desligar o chat privado. */
    public void setChatToggler(BiConsumer<Player, Player> chatToggler) {
        this.chatToggler = chatToggler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(config.msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            if (menuOpener == null) p.sendMessage(config.msg("menu-unavailable"));
            else menuOpener.accept(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        sub = ALIASES.getOrDefault(sub, sub);
        switch (sub) {
            case "add" -> add(p, args);
            case "aceitar" -> answer(p, args, true);
            case "recusar" -> answer(p, args, false);
            case "remover" -> remove(p, args);
            case "listar" -> list(p);
            case "pedidos" -> requests(p);
            case "tp" -> tp(p, args);
            case "tpauto" -> option(p, args, true);
            case "avisos" -> option(p, args, false);
            case "chat" -> chat(p, args);
            case "help" -> help(p);
            default -> p.sendMessage(config.msg("usage-general"));
        }
        return true;
    }

    private void add(Player p, String[] args) {
        if (args.length != 2) {
            p.sendMessage(config.msg("usage-add"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            p.sendMessage(config.msg("player-not-found"));
            return;
        }
        add(p, target);
    }

    /** Envia o pedido e mostra as mensagens; partilhado com o menu. */
    public CompletableFuture<FriendsService.AddResult> add(Player p, Player target) {
        CompletableFuture<FriendsService.AddResult> future = service.request(p.getUniqueId(), target.getUniqueId());
        run(p, future, result -> {
            switch (result) {
                case SENT -> {
                    p.sendMessage(msg("request-sent", target.getName()));
                    target.sendMessage(msg("request-received", p.getName()));
                }
                case ACCEPTED -> {
                    p.sendMessage(msg("now-friends", target.getName()));
                    target.sendMessage(msg("now-friends", p.getName()));
                }
                case SELF -> p.sendMessage(config.msg("add-self"));
                case ALREADY_FRIENDS -> p.sendMessage(msg("add-already-friends", target.getName()));
                case ALREADY_REQUESTED -> p.sendMessage(msg("add-already-requested", target.getName()));
                case SELF_FULL -> limitSelf(p);
                case TARGET_FULL -> p.sendMessage(msg("limit-other", target.getName()));
            }
        });
        return future;
    }

    private void answer(Player p, String[] args, boolean accept) {
        if (args.length != 2) {
            p.sendMessage(config.msg(accept ? "usage-aceitar" : "usage-recusar"));
            return;
        }
        // só nomes dos remetentes dos pedidos pendentes: sem lookup de nomes desconhecidos
        run(p, service.requestsTo(p.getUniqueId()), pending -> {
            Optional<UUID> from = pending.stream().map(FriendRequest::from)
                    .filter(u -> name(u).equalsIgnoreCase(args[1])).findFirst();
            if (from.isEmpty()) {
                p.sendMessage(msg("no-request", args[1]));
                return;
            }
            answer(p, from.get(), accept);
        });
    }

    /** Aceita ou recusa o pedido de {@code id} e mostra as mensagens; partilhado com o menu. */
    public CompletableFuture<?> answer(Player p, UUID id, boolean accept) {
        String name = name(id);
        if (!accept) {
            CompletableFuture<Boolean> future = service.decline(p.getUniqueId(), id);
            run(p, future, ok -> p.sendMessage(ok ? msg("declined", name) : msg("no-request", name)));
            return future;
        }
        CompletableFuture<FriendsService.AcceptResult> future = service.accept(p.getUniqueId(), id);
        run(p, future, result -> {
            switch (result) {
                case OK -> {
                    p.sendMessage(msg("now-friends", name));
                    Player other = Bukkit.getPlayer(id);
                    if (other != null) other.sendMessage(msg("now-friends", p.getName()));
                }
                case NO_REQUEST -> p.sendMessage(msg("no-request", name));
                case SELF_FULL -> limitSelf(p);
                case OTHER_FULL -> p.sendMessage(msg("limit-other", name));
            }
        });
        return future;
    }

    private void remove(Player p, String[] args) {
        if (args.length != 2) {
            p.sendMessage(config.msg("usage-remover"));
            return;
        }
        Optional<UUID> friend = friendByName(p, args[1]);
        if (friend.isEmpty()) {
            p.sendMessage(msg("not-friend", args[1]));
            return;
        }
        UUID id = friend.get();
        String name = name(id);
        long now = System.currentTimeMillis();
        Pending pending = removals.get(p.getUniqueId());
        if (pending == null || !pending.friend().equals(id) || pending.expires() < now) {
            removals.put(p.getUniqueId(), new Pending(id, now + config.confirmSeconds() * 1000L));
            p.sendMessage(msg("remove-confirm", name).replace("{n}", String.valueOf(config.confirmSeconds())));
            return;
        }
        removals.remove(p.getUniqueId());
        remove(p, id);
    }

    /** Remove já, sem confirmação (o menu tem a sua); mostra as mensagens. */
    public CompletableFuture<Boolean> remove(Player p, UUID id) {
        String name = name(id);
        CompletableFuture<Boolean> future = service.remove(p.getUniqueId(), id);
        run(p, future, ok -> p.sendMessage(ok ? msg("removed", name) : msg("not-friend", name)));
        return future;
    }

    private void list(Player p) {
        Map<UUID, Friend> friends = service.friendsOf(p.getUniqueId());
        if (friends.isEmpty()) {
            p.sendMessage(config.msg("list-empty"));
            return;
        }
        List<UUID> sorted = new ArrayList<>(friends.keySet());
        sorted.sort(Comparator.<UUID, Boolean>comparing(u -> Bukkit.getPlayer(u) == null)
                .thenComparing(u -> name(u).toLowerCase(Locale.ROOT)));
        List<CompletableFuture<String>> labels = sorted.stream().map(service::rankLabel).toList();
        run(p, CompletableFuture.allOf(labels.toArray(CompletableFuture[]::new)), v -> {
            p.sendMessage(config.msg("list-header").replace("{n}", String.valueOf(sorted.size())));
            for (int i = 0; i < sorted.size(); i++) {
                UUID u = sorted.get(i);
                String label = labels.get(i).join();
                String line = msg(Bukkit.getPlayer(u) != null ? "list-online" : "list-offline", name(u));
                p.sendMessage(label.isEmpty() ? line : label + " " + line);
            }
        });
    }

    private void requests(Player p) {
        run(p, service.requestsTo(p.getUniqueId()), pending -> {
            if (pending.isEmpty()) {
                p.sendMessage(config.msg("requests-empty"));
                return;
            }
            p.sendMessage(config.msg("requests-header"));
            for (FriendRequest r : pending) {
                String name = name(r.from());
                p.sendMessage(Component.text(name + "  ")
                        .append(button("button-accept", "/amigo aceitar " + name))
                        .append(Component.text("  "))
                        .append(button("button-decline", "/amigo recusar " + name)));
            }
        });
    }

    private Component button(String key, String command) {
        return LegacyComponentSerializer.legacySection().deserialize(config.msg(key))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private void tp(Player p, String[] args) {
        if (args.length != 2) {
            p.sendMessage(config.msg("usage-tp"));
            return;
        }
        Optional<UUID> friend = friendByName(p, args[1]);
        if (friend.isEmpty()) {
            p.sendMessage(msg("not-friend", args[1]));
            return;
        }
        goTo(p, friend.get());
    }

    /** Lógica do {@code /amigo tp}, partilhada com o botão "Ir ter com" do menu. */
    public void goTo(Player p, UUID friend) {
        Player target = Bukkit.getPlayer(friend);
        if (target == null) {
            p.sendMessage(msg("friend-offline", name(friend)));
            return;
        }
        if (service.teleportOrRequest(p, target)) p.sendMessage(msg("tp-done", target.getName()));
    }

    /** {@code tpauto} (true) ou {@code avisos} (false): [on|off] opcional, sem ele alterna. */
    private void option(Player p, String[] args, boolean tpAuto) {
        if (args.length < 2 || args.length > 3) {
            p.sendMessage(config.msg(tpAuto ? "usage-tpauto" : "usage-avisos"));
            return;
        }
        Optional<UUID> friend = friendByName(p, args[1]);
        if (friend.isEmpty()) {
            p.sendMessage(msg("not-friend", args[1]));
            return;
        }
        UUID id = friend.get();
        Friend current = service.friendsOf(p.getUniqueId()).get(id);
        boolean value;
        if (args.length == 3) {
            if (args[2].equalsIgnoreCase("on")) value = true;
            else if (args[2].equalsIgnoreCase("off")) value = false;
            else {
                p.sendMessage(config.msg(tpAuto ? "usage-tpauto" : "usage-avisos"));
                return;
            }
        } else {
            value = !(tpAuto ? current.tpAuto() : current.notifyEnabled());
        }
        setOption(p, id, tpAuto, value);
    }

    /** Muda o tp automático (true) ou os avisos (false) e mostra a mensagem; partilhado com o menu. */
    public CompletableFuture<Boolean> setOption(Player p, UUID id, boolean tpAuto, boolean value) {
        String name = name(id);
        CompletableFuture<Boolean> future = tpAuto
                ? service.setTpAuto(p.getUniqueId(), id, value) : service.setNotify(p.getUniqueId(), id, value);
        run(p, future, ok -> {
            if (!ok) {
                p.sendMessage(msg("not-friend", name));
                return;
            }
            String key = (tpAuto ? "tpauto-" : "notify-") + (value ? "on" : "off");
            p.sendMessage(msg(key, name));
        });
        return future;
    }

    private void chat(Player p, String[] args) {
        if (args.length > 2) {
            p.sendMessage(config.msg("usage-chat"));
            return;
        }
        if (chatToggler == null) {
            p.sendMessage(config.msg("menu-unavailable"));
            return;
        }
        if (args.length == 1) {
            toggleChat(p, null);
            return;
        }
        Optional<UUID> friend = friendByName(p, args[1]);
        if (friend.isEmpty()) {
            p.sendMessage(msg("not-friend", args[1]));
            return;
        }
        Player target = Bukkit.getPlayer(friend.get());
        if (target == null) {
            p.sendMessage(msg("friend-offline", name(friend.get())));
            return;
        }
        toggleChat(p, target);
    }

    /** Liga o chat privado com {@code target}, ou desliga com {@code null}; partilhado com o menu. */
    public void toggleChat(Player p, Player target) {
        if (chatToggler == null) p.sendMessage(config.msg("menu-unavailable"));
        else chatToggler.accept(p, target);
    }

    private void help(Player p) {
        for (String sub : SUBS) p.sendMessage(config.msg("help-" + sub));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1) return filter(SUBS, args[0]);
        String sub = ALIASES.getOrDefault(args[0].toLowerCase(Locale.ROOT), args[0].toLowerCase(Locale.ROOT));
        if (args.length == 2) {
            switch (sub) {
                case "remover", "tp", "tpauto", "avisos", "chat" -> {
                    return filter(service.friendsOf(p.getUniqueId()).keySet().stream().map(FriendsCommand::name).toList(), args[1]);
                }
                case "add" -> {
                    Map<UUID, Friend> friends = service.friendsOf(p.getUniqueId());
                    return filter(Bukkit.getOnlinePlayers().stream()
                            .filter(o -> !o.equals(p) && !friends.containsKey(o.getUniqueId()))
                            .map(Player::getName).toList(), args[1]);
                }
                default -> {
                    // aceitar/recusar: os remetentes vêm de uma consulta assíncrona, sem tab-complete
                    return List.of();
                }
            }
        }
        if (args.length == 3 && (sub.equals("tpauto") || sub.equals("avisos"))) return filter(List.of("on", "off"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private void limitSelf(Player p) {
        run(p, service.limitOf(p.getUniqueId()), limit ->
                p.sendMessage(config.msg("limit-self").replace("{n}", String.valueOf(limit))));
    }

    /** Amigo (da cache) com esse nome, sem distinguir maiúsculas. */
    private Optional<UUID> friendByName(Player p, String name) {
        return service.friendsOf(p.getUniqueId()).keySet().stream()
                .filter(u -> name(u).equalsIgnoreCase(name)).findFirst();
    }

    /** Nome conhecido do jogador (sem lookup); o UUID se o servidor nunca o viu. */
    public static String name(UUID uuid) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(uuid);
        return o.getName() != null ? o.getName() : uuid.toString();
    }

    private String msg(String key, String player) {
        return config.msg(key).replace("{jogador}", player);
    }

    /** Volta à thread principal; qualquer falha do futuro mostra a mensagem {@code error}. */
    private <T> void run(Player p, CompletableFuture<T> future, Consumer<T> onSuccess) {
        future.whenComplete((value, e) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (e != null) {
                plugin.getLogger().log(Level.WARNING, "falha num comando /amigo", e);
                p.sendMessage(config.msg("error"));
                return;
            }
            onSuccess.accept(value);
        }));
    }
}
