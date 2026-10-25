package pt.flawless.ffriends;

import java.sql.Connection;
import java.sql.SQLException;

/** Cópia do helper de transações do SqlTagsRepository do fTags. */
final class Tx {

    private Tx() {}

    @FunctionalInterface
    interface Work<R> {
        R run() throws SQLException;
    }

    /** Corre o trabalho numa transação: commit se correr bem, rollback antes de propagar qualquer falha. */
    static <R> R inTransaction(Connection connection, Work<R> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            R result = work.run();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        }
    }
}
