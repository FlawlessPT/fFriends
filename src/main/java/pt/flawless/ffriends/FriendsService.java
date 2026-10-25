package pt.flawless.ffriends;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pt.flawless.fapi.friends.FriendsApi;
import pt.flawless.fapi.permissions.PermissionsApi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.CompletableFuture.completedFuture;

/** Regras dos amigos e dos pedidos. Os futuros completam fora da thread principal. */
public class FriendsService implements FriendsApi {

    public enum AddResult { SENT, SELF, ALREADY_FRIENDS, ALREADY_REQUESTED, SELF_FULL, TARGET_FULL, ACCEPTED }

    public enum AcceptResult { OK, NO_REQUEST, SELF_FULL, OTHER_FULL }

    private final FriendsConfig config;
    private final FriendsRepository repository;
    private final PermissionsApi permissions;
    private final Logger logger;
    private final FriendsCache cache = new FriendsCache();

    /** @param permissions {@code null} sem fPermissions: todos ficam com o limite por omissão */
    public FriendsService(FriendsConfig config, FriendsRepository repository, PermissionsApi permissions, Logger logger) {
        this.config = config;
        this.repository = repository;
        this.permissions = permissions;
        this.logger = logger;
    }

    /** Ordem das verificações: o próprio, já amigos, pedido repetido, limite de quem pede, limite do alvo. */
    public CompletableFuture<AddResult> request(UUID from, UUID to) {
        if (from.equals(to)) return completedFuture(AddResult.SELF);
        if (areFriends(from, to)) return completedFuture(AddResult.ALREADY_FRIENDS);
        return repository.findRequestsTo(to).thenCompose(pending -> {
            if (pending.stream().anyMatch(r -> r.from().equals(from))) {
                return completedFuture(AddResult.ALREADY_REQUESTED);
            }
            return checkLimits(from, to).thenCompose(limits -> switch (limits) {
                case SELF_FULL -> completedFuture(AddResult.SELF_FULL);
                case OTHER_FULL -> completedFuture(AddResult.TARGET_FULL);
                // havendo pedido cruzado (to→from), aceitá-lo já é a amizade
                default -> repository.acceptRequest(to, from).thenCompose(accepted -> accepted
                        ? reloadBoth(from, to).thenApply(v -> AddResult.ACCEPTED)
                        : repository.insertRequest(from, to)
                                .thenApply(ok -> ok ? AddResult.SENT : AddResult.ALREADY_REQUESTED));
            });
        });
    }

    public CompletableFuture<AcceptResult> accept(UUID owner, UUID from) {
        return repository.findRequestsTo(owner).thenCompose(pending -> {
            if (pending.stream().noneMatch(r -> r.from().equals(from))) {
                return completedFuture(AcceptResult.NO_REQUEST);
            }
            return checkLimits(owner, from).thenCompose(limits -> limits != AcceptResult.OK
                    ? completedFuture(limits)
                    : repository.acceptRequest(from, owner).thenCompose(ok -> ok
                            ? reloadBoth(owner, from).thenApply(v -> AcceptResult.OK)
                            : completedFuture(AcceptResult.NO_REQUEST)));
        });
    }

    /** @return {@code false} se não havia pedido */
    public CompletableFuture<Boolean> decline(UUID owner, UUID from) {
        return repository.deleteRequest(from, owner);
    }

    /** @return {@code false} se não eram amigos */
    public CompletableFuture<Boolean> remove(UUID a, UUID b) {
        return repository.removeFriend(a, b).thenCompose(ok -> ok
                ? reloadBoth(a, b).thenApply(v -> true)
                : completedFuture(false));
    }

    /** @return {@code false} se não são amigos */
    public CompletableFuture<Boolean> setTpAuto(UUID owner, UUID friend, boolean value) {
        return repository.setTpAuto(owner, friend, value).thenCompose(ok -> afterOption(owner, ok));
    }

    /** @return {@code false} se não são amigos */
    public CompletableFuture<Boolean> setNotify(UUID owner, UUID friend, boolean value) {
        return repository.setNotify(owner, friend, value).thenCompose(ok -> afterOption(owner, ok));
    }

    public CompletableFuture<List<FriendRequest>> requestsTo(UUID uuid) {
        return repository.findRequestsTo(uuid);
    }

    public CompletableFuture<Integer> limitOf(UUID uuid) {
        if (permissions == null) return completedFuture(config.limitFor(Set.of()));
        // shortcut: só grupos explícitos (getGroups ignora a herança); usar os grupos efetivos quando a PermissionsApi os expuser
        return permissions.getGroups(uuid).thenApply(config::limitFor);
    }

    /** Marca o jogador como carregado e lê os amigos; chamar depois da autenticação. */
    public CompletableFuture<Void> load(UUID uuid) {
        cache.start(uuid);
        return reload(uuid);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    /** Volta a ler da BD os amigos de um jogador carregado; não faz nada a quem não está carregado. */
    public CompletableFuture<Void> reload(UUID uuid) {
        if (!cache.isLoaded(uuid)) return completedFuture(null);
        return repository.findFriends(uuid)
                .thenAccept(friends -> cache.replace(uuid, friends))
                .exceptionally(e -> {
                    logger.log(Level.WARNING, "falha ao carregar os amigos de " + uuid, e);
                    return null;
                });
    }

    /** Amigos de um jogador carregado, com as opções dele; vazio se não estiver carregado. */
    public Map<UUID, Friend> friendsOf(UUID uuid) {
        return cache.friendsOf(uuid);
    }

    public boolean isLoaded(UUID uuid) {
        return cache.isLoaded(uuid);
    }

    @Override
    public boolean areFriends(UUID a, UUID b) {
        return cache.friendsOf(a).containsKey(b);
    }

    @Override
    public Set<UUID> getFriends(UUID uuid) {
        return cache.friendsOf(uuid).keySet();
    }

    @Override
    public boolean canAutoTp(UUID target, UUID requester) {
        Friend friend = cache.friendsOf(target).get(requester);
        if (friend == null || !friend.tpAuto()) return false;
        Player player = Bukkit.getPlayer(target);
        return player != null && config.allowedWorlds().contains(player.getWorld().getName());
    }

    private CompletableFuture<Boolean> afterOption(UUID owner, boolean ok) {
        return ok ? reload(owner).thenApply(v -> true) : completedFuture(false);
    }

    private CompletableFuture<Void> reloadBoth(UUID a, UUID b) {
        return CompletableFuture.allOf(reload(a), reload(b));
    }

    /** OK, ou qual dos lados já está no limite (primeiro {@code self}). */
    private CompletableFuture<AcceptResult> checkLimits(UUID self, UUID other) {
        return isFull(self).thenCompose(selfFull -> selfFull
                ? completedFuture(AcceptResult.SELF_FULL)
                : isFull(other).thenApply(full -> full ? AcceptResult.OTHER_FULL : AcceptResult.OK));
    }

    private CompletableFuture<Boolean> isFull(UUID uuid) {
        return limitOf(uuid).thenCombine(repository.countFriends(uuid), (limit, count) -> count >= limit);
    }
}
