package pt.flawless.ffriends.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import pt.flawless.fapi.enums.FInventorySize;
import pt.flawless.fapi.inventory.FInventory;
import pt.flawless.fapi.items.FItemStack;
import pt.flawless.ffriends.Friend;
import pt.flawless.ffriends.FriendRequest;
import pt.flawless.ffriends.FriendsCommand;
import pt.flawless.ffriends.FriendsConfig;
import pt.flawless.ffriends.FriendsService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.logging.Level;

import static pt.flawless.ffriends.FriendsCommand.name;
import static pt.flawless.ffriends.menu.FriendsMenuLayout.*;

/** Menus de amigos (principal, pedidos, amigo). Os futuros do serviço completam fora da thread principal. */
public class FriendsMenu implements Listener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    /** Identifica os nossos inventários nos eventos. */
    private abstract static class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MainHolder extends Holder {
        final List<UUID> friends;

        MainHolder(List<UUID> friends) {
            this.friends = friends;
        }
    }

    private static final class RequestsHolder extends Holder {
        final List<UUID> senders;

        RequestsHolder(List<UUID> senders) {
            this.senders = senders;
        }
    }

    private static final class FriendHolder extends Holder {
        final UUID friend;
        /** Fim da janela de confirmação do "Remover"; 0 = sem confirmação pendente. */
        long confirmUntil;

        FriendHolder(UUID friend) {
            this.friend = friend;
        }
    }

    private final Plugin plugin;
    private final FriendsService service;
    private final FriendsConfig config;
    private final FriendsCommand command;
    private final AddPrompt addPrompt;
    /** Jogadores com uma operação ainda por completar: os cliques deles são ignorados. */
    private final Set<UUID> busy = new HashSet<>();
    /** Parceiro do chat privado (Task 6); {@code null} até ser ligado. */
    private Function<UUID, Optional<UUID>> chatPartner;

    public FriendsMenu(Plugin plugin, FriendsService service, FriendsConfig config, FriendsCommand command) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
        this.command = command;
        this.addPrompt = new AddPrompt(plugin, config, command, this::openMain);
    }

    /** Para o listener de chat. */
    public AddPrompt addPrompt() {
        return addPrompt;
    }

    /** Ligado pela Task 6 ({@code PrivateChats::partnerOf}); sem ele o vidro do chat fica desligado. */
    public void setChatPartner(Function<UUID, Optional<UUID>> chatPartner) {
        this.chatPartner = chatPartner;
    }

    public void openMain(Player p) {
        List<UUID> friends = new ArrayList<>(service.friendsOf(p.getUniqueId()).keySet());
        friends.sort(Comparator.<UUID, Boolean>comparing(u -> Bukkit.getPlayer(u) == null)
                .thenComparing(u -> name(u).toLowerCase(Locale.ROOT)));
        List<UUID> shown = friends.size() > MAX_ITEMS ? friends.subList(0, MAX_ITEMS) : friends;
        List<CompletableFuture<String>> ranks = shown.stream().map(service::rankLabel).toList();
        CompletableFuture<List<FriendRequest>> pending = service.requestsTo(p.getUniqueId());
        List<CompletableFuture<?>> all = new ArrayList<>(ranks);
        all.add(pending);
        run(p, CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)), v -> {
            MainHolder holder = new MainHolder(List.copyOf(shown));
            Inventory inv = create(holder, FInventorySize.SIZE_54, config.msg("menu-main-title"));
            border(inv);
            for (int i = 0; i < shown.size(); i++) {
                UUID u = shown.get(i);
                boolean online = Bukkit.getPlayer(u) != null;
                List<String> lore = new ArrayList<>();
                lore.add(config.msg(online ? "menu-online" : "menu-offline"));
                String rank = ranks.get(i).join();
                if (!rank.isEmpty()) lore.add(rank);
                lore.add(config.msg("menu-manage"));
                inv.setItem(friendSlot(i), head(u, (online ? "§a" : "§7") + name(u), lore));
            }
            inv.setItem(ADD_SLOT, item(Material.LIME_DYE, config.msg("menu-add")));
            inv.setItem(REQUESTS_SLOT, item(Material.WRITABLE_BOOK,
                    config.msg("menu-requests").replace("{n}", String.valueOf(pending.join().size()))));
            p.openInventory(inv);
        });
    }

    public void openRequests(Player p) {
        run(p, service.requestsTo(p.getUniqueId()), pending -> {
            List<UUID> senders = pending.stream().map(FriendRequest::from).limit(MAX_ITEMS).toList();
            RequestsHolder holder = new RequestsHolder(senders);
            Inventory inv = create(holder, FInventorySize.SIZE_54, config.msg("menu-requests-title"));
            border(inv);
            for (int i = 0; i < senders.size(); i++) {
                UUID u = senders.get(i);
                inv.setItem(friendSlot(i), head(u, "§e" + name(u), List.of(config.msg("menu-request-lore"))));
            }
            inv.setItem(REQUESTS_BACK_SLOT, item(Material.ARROW, config.msg("menu-back")));
            p.openInventory(inv);
        });
    }

    public void openFriend(Player p, UUID friendId) {
        Friend friend = service.friendsOf(p.getUniqueId()).get(friendId);
        if (friend == null) {
            p.sendMessage(config.msg("not-friend").replace("{jogador}", name(friendId)));
            p.closeInventory();
            return;
        }
        FriendHolder holder = new FriendHolder(friendId);
        Inventory inv = create(holder, FInventorySize.SIZE_36,
                config.msg("menu-friend-title").replace("{jogador}", name(friendId)));
        List<String> lore = new ArrayList<>();
        lore.add(config.msg(Bukkit.getPlayer(friendId) != null ? "menu-online" : "menu-offline"));
        if (friend.since() != null) lore.add(config.msg("menu-since").replace("{data}", DATE.format(friend.since())));
        inv.setItem(HEAD_SLOT, head(friendId, "§e" + name(friendId), lore));
        inv.setItem(TPAUTO_SLOT, item(Material.ENDER_PEARL, config.msg("menu-tpauto")));
        inv.setItem(NOTIFY_SLOT, item(Material.BELL, config.msg("menu-notify")));
        inv.setItem(CHAT_SLOT, item(Material.WRITABLE_BOOK, config.msg("menu-chat")));
        setGlass(inv, TPAUTO_SLOT, friend.tpAuto());
        setGlass(inv, NOTIFY_SLOT, friend.notifyEnabled());
        setGlass(inv, CHAT_SLOT, inChatWith(p, friendId));
        inv.setItem(GOTO_SLOT, item(Material.COMPASS, config.msg("menu-goto")));
        inv.setItem(REMOVE_SLOT, item(Material.BARRIER, config.msg("menu-remove")));
        inv.setItem(FRIEND_BACK_SLOT, item(Material.ARROW, config.msg("menu-back")));
        for (int slot = 0; slot < FRIEND_SIZE; slot++) {
            if (inv.getItem(slot) == null) inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // top inventory nosso: cobre também shift-click/hotbar vindos do inventário do jogador
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize() || !(e.getWhoClicked() instanceof Player p)) return;
        if (busy.contains(p.getUniqueId())) return;
        int slot = e.getRawSlot();
        switch (holder) {
            case MainHolder h -> clickMain(p, h, slot);
            case RequestsHolder h -> clickRequests(p, h, slot, e.isLeftClick(), e.isRightClick());
            case FriendHolder h -> clickFriend(p, h, slot, e.getClick());
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void clickMain(Player p, MainHolder h, int slot) {
        if (slot == ADD_SLOT) {
            p.closeInventory();
            addPrompt.start(p);
        } else if (slot == REQUESTS_SLOT) {
            openRequests(p);
        } else {
            int i = indexOf(slot);
            if (i >= 0 && i < h.friends.size()) openFriend(p, h.friends.get(i));
        }
    }

    private void clickRequests(Player p, RequestsHolder h, int slot, boolean left, boolean right) {
        if (slot == REQUESTS_BACK_SLOT) {
            openMain(p);
            return;
        }
        int i = indexOf(slot);
        if (i < 0 || i >= h.senders.size() || !(left || right)) return;
        // mensagens e erro a cargo do comando; o menu só reabre
        afterCommand(p, command.answer(p, h.senders.get(i), left), () -> {
            if (p.getOpenInventory().getTopInventory().getHolder() == h) openRequests(p);
        });
    }

    private void clickFriend(Player p, FriendHolder h, int slot, ClickType click) {
        UUID friendId = h.friend;
        switch (slot) {
            case TPAUTO_SLOT, TPAUTO_SLOT + GLASS_OFFSET, NOTIFY_SLOT, NOTIFY_SLOT + GLASS_OFFSET -> {
                boolean tpAuto = slot == TPAUTO_SLOT || slot == TPAUTO_SLOT + GLASS_OFFSET;
                Friend friend = service.friendsOf(p.getUniqueId()).get(friendId);
                if (friend == null) {
                    openMain(p);
                    return;
                }
                boolean value = !(tpAuto ? friend.tpAuto() : friend.notifyEnabled());
                CompletableFuture<Boolean> future = command.setOption(p, friendId, tpAuto, value);
                afterCommand(p, future, () -> {
                    if (future.join()) setGlass(h.inventory, tpAuto ? TPAUTO_SLOT : NOTIFY_SLOT, value);
                    else openMain(p);
                });
            }
            case CHAT_SLOT, CHAT_SLOT + GLASS_OFFSET -> {
                if (inChatWith(p, friendId)) {
                    command.toggleChat(p, null);
                } else {
                    Player target = Bukkit.getPlayer(friendId);
                    if (target == null) {
                        p.sendMessage(config.msg("friend-offline").replace("{jogador}", name(friendId)));
                        return;
                    }
                    command.toggleChat(p, target);
                }
                setGlass(h.inventory, CHAT_SLOT, inChatWith(p, friendId));
            }
            case GOTO_SLOT -> {
                p.closeInventory();
                command.goTo(p, friendId);
            }
            case REMOVE_SLOT -> {
                // o duplo clique gera um evento extra que confirmaria logo
                if (click != ClickType.DOUBLE_CLICK) remove(p, h);
            }
            case FRIEND_BACK_SLOT -> openMain(p);
            default -> {
            }
        }
    }

    /** Primeiro clique pede confirmação durante {@code confirm-seconds}; o segundo remove. */
    private void remove(Player p, FriendHolder h) {
        long now = System.currentTimeMillis();
        if (h.confirmUntil > now) {
            h.confirmUntil = 0;
            afterCommand(p, command.remove(p, h.friend), () -> {
                if (p.isOnline()) openMain(p);
            });
            return;
        }
        h.confirmUntil = now + config.confirmSeconds() * 1000L;
        h.inventory.setItem(REMOVE_SLOT, item(Material.BARRIER, config.msg("menu-remove-confirm")));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // um clique depois de expirar abre uma nova janela: só repor quando já não houver nenhuma
            if (System.currentTimeMillis() >= h.confirmUntil) {
                h.inventory.setItem(REMOVE_SLOT, item(Material.BARRIER, config.msg("menu-remove")));
            }
        }, config.confirmSeconds() * 20L);
    }

    private boolean inChatWith(Player p, UUID friendId) {
        return chatPartner != null && chatPartner.apply(p.getUniqueId()).filter(friendId::equals).isPresent();
    }

    /** Bloqueia os cliques até o futuro (de um método do comando, que já trata as mensagens) completar. */
    private void afterCommand(Player p, CompletableFuture<?> future, Runnable then) {
        UUID uuid = p.getUniqueId();
        busy.add(uuid);
        future.whenComplete((v, e) -> runSync(() -> {
            busy.remove(uuid);
            if (e == null && p.isOnline()) then.run();
        }));
    }

    /** Volta à thread principal; uma falha mostra {@code error} e regista WARNING. */
    private <T> void run(Player p, CompletableFuture<T> future, java.util.function.Consumer<T> onSuccess) {
        future.whenComplete((value, e) -> runSync(() -> {
            if (e != null) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                plugin.getLogger().log(Level.WARNING, "falha num menu de amigos", cause);
                p.sendMessage(config.msg("error"));
                return;
            }
            if (p.isOnline()) onSuccess.accept(value);
        }));
    }

    private void runSync(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (RuntimeException ex) {
            // plugin a desligar
        }
    }

    private void setGlass(Inventory inv, int iconSlot, boolean on) {
        inv.setItem(iconSlot + GLASS_OFFSET, item(on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                config.msg(on ? "menu-on" : "menu-off")));
    }

    private static Inventory create(Holder holder, FInventorySize size, String title) {
        holder.inventory = new FInventory(holder, size, title).getInventory();
        return holder.inventory;
    }

    private static void border(Inventory inv) {
        for (int slot = 0; slot < SIZE; slot++) {
            if (isBorder(slot)) inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    private static ItemStack item(Material material, String name) {
        return new FItemStack(material, 1, name).createItem();
    }

    /** Cabeça do jogador; o dono vem do UUID (nunca por nome, para não haver lookup). */
    private static ItemStack head(UUID uuid, String name, List<String> lore) {
        ItemStack head = new FItemStack(Material.PLAYER_HEAD, 1, name).setLore(lore).createItem();
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        head.setItemMeta(meta);
        return head;
    }
}
