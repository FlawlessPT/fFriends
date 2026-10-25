package pt.flawless.ffriends.menu;

/**
 * Posições dos menus de amigos. Principal e pedidos: 54 slots com borda de vidro e interior 4x7 (linhas 1-4,
 * colunas 1-7). Amigo: 36 slots. Sem Bukkit.
 */
final class FriendsMenuLayout {
    static final int SIZE = 54;
    static final int MAX_ITEMS = 28;
    private static final int ROW = 9;

    // principal
    static final int ADD_SLOT = 48;
    static final int REQUESTS_SLOT = 50;
    // pedidos
    static final int REQUESTS_BACK_SLOT = 49;
    // amigo
    static final int FRIEND_SIZE = 36;
    static final int HEAD_SLOT = 4;
    static final int TPAUTO_SLOT = 10;
    static final int NOTIFY_SLOT = 13;
    static final int CHAT_SLOT = 16;
    /** Vidro de estado: logo por baixo do ícone. */
    static final int GLASS_OFFSET = ROW;
    static final int GOTO_SLOT = 29;
    static final int FRIEND_BACK_SLOT = 31;
    static final int REMOVE_SLOT = 33;

    private FriendsMenuLayout() {}

    /** Slot do item {@code index} (0..27) no interior. */
    static int friendSlot(int index) {
        return ROW * (1 + index / 7) + 1 + index % 7;
    }

    /** Índice do item num slot do interior, ou -1. */
    static int indexOf(int slot) {
        int row = slot / ROW, col = slot % ROW;
        if (row < 1 || row > 4 || col < 1 || col > 7) return -1;
        return (row - 1) * 7 + col - 1;
    }

    /** Linhas 0 e 5, colunas 0 e 8. */
    static boolean isBorder(int slot) {
        int row = slot / ROW, col = slot % ROW;
        return row == 0 || row == SIZE / ROW - 1 || col == 0 || col == ROW - 1;
    }
}
