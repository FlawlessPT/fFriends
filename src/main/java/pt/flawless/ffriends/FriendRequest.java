package pt.flawless.ffriends;

import java.time.Instant;
import java.util.UUID;

/** Pedido de amizade pendente de {@code from} para {@code to}. */
public record FriendRequest(UUID from, UUID to, Instant created) {
}
