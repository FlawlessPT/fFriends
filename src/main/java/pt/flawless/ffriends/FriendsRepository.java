package pt.flawless.ffriends;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Acesso às tabelas {@code ffriends_links} e {@code ffriends_requests}. */
public interface FriendsRepository {

    CompletableFuture<List<Friend>> findFriends(UUID owner);

    /** Só pedidos não expirados. */
    CompletableFuture<List<FriendRequest>> findRequestsTo(UUID to);

    /** @return {@code false} se o pedido já existia */
    CompletableFuture<Boolean> insertRequest(UUID from, UUID to);

    /** Apaga o pedido {@code from→to} (e o cruzado) e cria a amizade. @return {@code false} se não havia pedido */
    CompletableFuture<Boolean> acceptRequest(UUID from, UUID to);

    CompletableFuture<Boolean> deleteRequest(UUID from, UUID to);

    /** @return {@code false} se não eram amigos */
    CompletableFuture<Boolean> removeFriend(UUID a, UUID b);

    /** @return {@code false} se não são amigos */
    CompletableFuture<Boolean> setTpAuto(UUID owner, UUID friend, boolean value);

    /** @return {@code false} se não são amigos */
    CompletableFuture<Boolean> setNotify(UUID owner, UUID friend, boolean value);

    CompletableFuture<Integer> countFriends(UUID owner);

    CompletableFuture<Void> purgeExpired(int days);
}
