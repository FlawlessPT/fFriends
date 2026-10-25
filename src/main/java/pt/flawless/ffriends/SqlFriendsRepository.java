package pt.flawless.ffriends;

import pt.flawless.fapi.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Acesso às tabelas do fFriends. Todos os métodos correm no pool do {@link DatabaseService} e os futuros
 * falham com a {@link SQLException} original. Um pedido mais antigo que {@code expireDays} conta como inexistente.
 */
public class SqlFriendsRepository implements FriendsRepository {

    private static final String SELECT_FRIENDS = "SELECT friend, tp_auto, notify, since FROM ffriends_links WHERE owner = ?";
    private static final String SELECT_REQUESTS_TO = "SELECT from_uuid, created FROM ffriends_requests "
            + "WHERE to_uuid = ? AND created > NOW() - INTERVAL ? DAY ORDER BY created";
    private static final String DELETE_EXPIRED_PAIR = "DELETE FROM ffriends_requests "
            + "WHERE from_uuid = ? AND to_uuid = ? AND created <= NOW() - INTERVAL ? DAY";
    private static final String INSERT_REQUEST = "INSERT IGNORE INTO ffriends_requests (from_uuid, to_uuid) VALUES (?, ?)";
    private static final String DELETE_VALID_REQUEST = "DELETE FROM ffriends_requests "
            + "WHERE from_uuid = ? AND to_uuid = ? AND created > NOW() - INTERVAL ? DAY";
    private static final String DELETE_REQUEST = "DELETE FROM ffriends_requests WHERE from_uuid = ? AND to_uuid = ?";
    private static final String INSERT_LINK = "INSERT IGNORE INTO ffriends_links (owner, friend) VALUES (?, ?)";
    private static final String DELETE_LINKS = "DELETE FROM ffriends_links "
            + "WHERE (owner = ? AND friend = ?) OR (owner = ? AND friend = ?)";
    private static final String EXISTS_LINK = "SELECT 1 FROM ffriends_links WHERE owner = ? AND friend = ?";
    private static final String SET_TP_AUTO = "UPDATE ffriends_links SET tp_auto = ? WHERE owner = ? AND friend = ?";
    private static final String SET_NOTIFY = "UPDATE ffriends_links SET notify = ? WHERE owner = ? AND friend = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM ffriends_links WHERE owner = ?";
    private static final String PURGE = "DELETE FROM ffriends_requests WHERE created <= NOW() - INTERVAL ? DAY";

    private final DatabaseService database;
    private final int expireDays;

    public SqlFriendsRepository(DatabaseService database, int expireDays) {
        this.database = database;
        this.expireDays = expireDays;
    }

    @Override
    public CompletableFuture<List<Friend>> findFriends(UUID owner) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_FRIENDS)) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Friend> friends = new ArrayList<>();
                    while (rs.next()) {
                        Timestamp since = rs.getTimestamp(4);
                        friends.add(new Friend(UUID.fromString(rs.getString(1)), rs.getBoolean(2), rs.getBoolean(3),
                                since == null ? null : since.toInstant()));
                    }
                    return friends;
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<FriendRequest>> findRequestsTo(UUID to) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_REQUESTS_TO)) {
                ps.setString(1, to.toString());
                ps.setInt(2, expireDays);
                try (ResultSet rs = ps.executeQuery()) {
                    List<FriendRequest> requests = new ArrayList<>();
                    while (rs.next()) {
                        requests.add(new FriendRequest(UUID.fromString(rs.getString(1)), to,
                                rs.getTimestamp(2).toInstant()));
                    }
                    return requests;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> insertRequest(UUID from, UUID to) {
        return database.supplyAsync(connection -> {
            // um pedido expirado ainda por limpar não pode bloquear o INSERT IGNORE
            pairUpdate(connection, DELETE_EXPIRED_PAIR, from, to, true);
            return pairUpdate(connection, INSERT_REQUEST, from, to, false) > 0;
        });
    }

    @Override
    public CompletableFuture<Boolean> acceptRequest(UUID from, UUID to) {
        return database.supplyAsync(connection -> Tx.inTransaction(connection, () -> {
            if (pairUpdate(connection, DELETE_VALID_REQUEST, from, to, true) == 0) {
                return false;
            }
            pairUpdate(connection, DELETE_REQUEST, to, from, false);
            pairUpdate(connection, INSERT_LINK, from, to, false);
            pairUpdate(connection, INSERT_LINK, to, from, false);
            return true;
        }));
    }

    @Override
    public CompletableFuture<Boolean> deleteRequest(UUID from, UUID to) {
        return database.supplyAsync(connection -> pairUpdate(connection, DELETE_REQUEST, from, to, false) > 0);
    }

    @Override
    public CompletableFuture<Boolean> removeFriend(UUID a, UUID b) {
        // um só DELETE já é atómico: as duas linhas saem juntas
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(DELETE_LINKS)) {
                ps.setString(1, a.toString());
                ps.setString(2, b.toString());
                ps.setString(3, b.toString());
                ps.setString(4, a.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> setTpAuto(UUID owner, UUID friend, boolean value) {
        return setOption(SET_TP_AUTO, owner, friend, value);
    }

    @Override
    public CompletableFuture<Boolean> setNotify(UUID owner, UUID friend, boolean value) {
        return setOption(SET_NOTIFY, owner, friend, value);
    }

    @Override
    public CompletableFuture<Integer> countFriends(UUID owner) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(COUNT)) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> purgeExpired(int days) {
        return database.runAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(PURGE)) {
                ps.setInt(1, days);
                ps.executeUpdate();
            }
        });
    }

    /** SELECT prévio como no fTags: não depende de o driver devolver linhas encontradas ou alteradas. */
    private CompletableFuture<Boolean> setOption(String sql, UUID owner, UUID friend, boolean value) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(EXISTS_LINK)) {
                ps.setString(1, owner.toString());
                ps.setString(2, friend.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, value);
                ps.setString(2, owner.toString());
                ps.setString(3, friend.toString());
                ps.executeUpdate();
            }
            return true;
        });
    }

    /** Executa um statement com o par (a, b) e, se {@code withDays}, o prazo de expiração em terceiro. */
    private int pairUpdate(Connection connection, String sql, UUID a, UUID b, boolean withDays) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, a.toString());
            ps.setString(2, b.toString());
            if (withDays) {
                ps.setInt(3, expireDays);
            }
            return ps.executeUpdate();
        }
    }
}
