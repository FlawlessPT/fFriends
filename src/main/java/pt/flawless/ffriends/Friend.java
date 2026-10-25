package pt.flawless.ffriends;

import java.time.Instant;
import java.util.UUID;

/**
 * Amigo visto do lado do dono: as opções são de quem guarda a linha.
 * {@code notifyEnabled} e não {@code notify}: o Java proíbe esse nome (colide com {@code Object.notify()}).
 * {@code since} pode ser {@code null} se a BD não tiver a data.
 */
public record Friend(UUID uuid, boolean tpAuto, boolean notifyEnabled, Instant since) {
}
