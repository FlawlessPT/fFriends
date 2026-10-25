package pt.flawless.ffriends;

import java.util.UUID;

/**
 * Amigo visto do lado do dono: as opções são de quem guarda a linha.
 * {@code notifyEnabled} e não {@code notify}: o Java proíbe esse nome (colide com {@code Object.notify()}).
 */
public record Friend(UUID uuid, boolean tpAuto, boolean notifyEnabled) {
}
