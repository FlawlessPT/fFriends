package pt.flawless.ffriends;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Quem está em chat privado e com quem. Concorrente: o chat assíncrono lê {@link #partnerOf}. */
public class PrivateChats {

    private final Map<UUID, UUID> partners = new ConcurrentHashMap<>();

    public void enable(UUID player, UUID friend) {
        partners.put(player, friend);
    }

    public void disable(UUID player) {
        partners.remove(player);
    }

    public Optional<UUID> partnerOf(UUID player) {
        return Optional.ofNullable(partners.get(player));
    }

    /** Desliga o modo de quem fala com {@code friend} e devolve esses jogadores para os avisar. */
    public List<UUID> disableAllWith(UUID friend) {
        List<UUID> affected = new ArrayList<>();
        partners.forEach((player, partner) -> {
            if (partner.equals(friend) && partners.remove(player, partner)) affected.add(player);
        });
        return affected;
    }

    public void forEach(BiConsumer<UUID, UUID> action) {
        partners.forEach(action);
    }
}
