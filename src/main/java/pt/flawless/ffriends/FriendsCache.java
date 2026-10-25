package pt.flawless.ffriends;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Amigos dos jogadores online e autenticados. Só há entrada entre {@link #start} e {@link #remove}. */
final class FriendsCache {

    private final ConcurrentHashMap<UUID, Map<UUID, Friend>> byOwner = new ConcurrentHashMap<>();

    void start(UUID owner) {
        byOwner.put(owner, Map.of());
    }

    /** Só substitui se o jogador ainda está carregado: um load tardio não ressuscita quem já saiu. */
    void replace(UUID owner, List<Friend> friends) {
        Map<UUID, Friend> map = new LinkedHashMap<>();
        friends.forEach(f -> map.put(f.uuid(), f));
        byOwner.replace(owner, Collections.unmodifiableMap(map));
    }

    void remove(UUID owner) {
        byOwner.remove(owner);
    }

    boolean isLoaded(UUID owner) {
        return byOwner.containsKey(owner);
    }

    Map<UUID, Friend> friendsOf(UUID owner) {
        return byOwner.getOrDefault(owner, Map.of());
    }
}
