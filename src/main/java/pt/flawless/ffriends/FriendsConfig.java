package pt.flawless.ffriends;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configuração do fFriends: limites, prazos, mundos do tp automático e mensagens (com {@code &} já traduzido). */
public record FriendsConfig(int defaultLimit, Map<String, Integer> limits, int requestExpireDays,
                            int confirmSeconds, int promptSeconds, Set<String> allowedWorlds,
                            Map<String, String> messages) {

    /** Máximo absoluto: é o que cabe no menu sem paginação. */
    public static final int HARD_CAP = 28;

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            // gerais
            Map.entry("players-only", "&cApenas jogadores."),
            Map.entry("error", "&cOcorreu um erro. Tenta mais tarde."),
            Map.entry("menu-unavailable", "&cMenu indisponível."),
            Map.entry("player-not-found", "&cJogador não encontrado ou offline."),
            Map.entry("friend-offline", "&c{jogador} não está online."),
            Map.entry("not-friend", "&c{jogador} não é teu amigo."),
            // uso
            Map.entry("usage-general", "&eUso: /amigo <add|aceitar|recusar|remover|listar|pedidos|tp|tpauto|avisos|chat|help>"),
            Map.entry("usage-add", "&eUso: /amigo add <jogador>"),
            Map.entry("usage-aceitar", "&eUso: /amigo aceitar <jogador>"),
            Map.entry("usage-recusar", "&eUso: /amigo recusar <jogador>"),
            Map.entry("usage-remover", "&eUso: /amigo remover <jogador>"),
            Map.entry("usage-tp", "&eUso: /amigo tp <amigo>"),
            Map.entry("usage-tpauto", "&eUso: /amigo tpauto <amigo> [on|off]"),
            Map.entry("usage-avisos", "&eUso: /amigo avisos <amigo> [on|off]"),
            Map.entry("usage-chat", "&eUso: /amigo chat [amigo]"),
            // help (formato do /money help)
            Map.entry("help-add", "&e/amigo add <jogador> &7- Envia um pedido de amizade"),
            Map.entry("help-aceitar", "&e/amigo aceitar <jogador> &7- Aceita um pedido de amizade"),
            Map.entry("help-recusar", "&e/amigo recusar <jogador> &7- Recusa um pedido de amizade"),
            Map.entry("help-remover", "&e/amigo remover <jogador> &7- Remove um amigo"),
            Map.entry("help-listar", "&e/amigo listar &7- Lista os teus amigos"),
            Map.entry("help-pedidos", "&e/amigo pedidos &7- Mostra os pedidos pendentes"),
            Map.entry("help-tp", "&e/amigo tp <amigo> &7- Vai ter com um amigo"),
            Map.entry("help-tpauto", "&e/amigo tpauto <amigo> [on|off] &7- Deixa o amigo vir ter contigo sem pedido"),
            Map.entry("help-avisos", "&e/amigo avisos <amigo> [on|off] &7- Avisa quando o amigo entra ou sai"),
            Map.entry("help-chat", "&e/amigo chat [amigo] &7- Liga ou desliga o chat privado"),
            Map.entry("help-help", "&e/amigo help &7- Mostra esta lista"),
            // pedidos
            Map.entry("request-sent", "&aPedido de amizade enviado a {jogador}."),
            Map.entry("request-received", "&e{jogador} enviou-te um pedido de amizade. Usa &f/amigo aceitar {jogador}&e."),
            Map.entry("add-self", "&cNão te podes adicionar a ti próprio."),
            Map.entry("add-already-friends", "&c{jogador} já é teu amigo."),
            Map.entry("add-already-requested", "&cJá enviaste um pedido a {jogador}."),
            Map.entry("limit-self", "&cAtingiste o limite de {n} amigos."),
            Map.entry("limit-other", "&c{jogador} atingiu o limite de amigos."),
            Map.entry("now-friends", "&aTu e {jogador} são agora amigos."),
            Map.entry("no-request", "&cNão tens nenhum pedido de {jogador}."),
            Map.entry("declined", "&7Recusaste o pedido de {jogador}."),
            Map.entry("requests-header", "&6Pedidos de amizade:"),
            Map.entry("requests-empty", "&7Não tens pedidos pendentes."),
            Map.entry("requests-pending", "&eTens {n} pedido(s) de amizade. Usa &f/amigo pedidos&e."),
            Map.entry("button-accept", "&a[ACEITAR]"),
            Map.entry("button-decline", "&c[RECUSAR]"),
            // amigos
            Map.entry("remove-confirm", "&eRepete o comando em {n}s para remover {jogador}."),
            Map.entry("removed", "&7{jogador} foi removido dos teus amigos."),
            Map.entry("list-header", "&6Amigos ({n}):"),
            Map.entry("list-empty", "&7Ainda não tens amigos."),
            Map.entry("list-online", "&a{jogador}"),
            Map.entry("list-offline", "&7{jogador}"),
            Map.entry("tp-done", "&aTeleportado para {jogador}."),
            Map.entry("tpauto-on", "&aTeleporte automático ligado para {jogador}."),
            Map.entry("tpauto-off", "&7Teleporte automático desligado para {jogador}."),
            Map.entry("notify-on", "&aAvisos de {jogador} ligados."),
            Map.entry("notify-off", "&7Avisos de {jogador} desligados."),
            Map.entry("friend-join", "&7O teu amigo &a{jogador} &7acabou de entrar."),
            Map.entry("friend-quit", "&7O teu amigo &c{jogador} &7acabou de sair."),
            // chat privado
            Map.entry("chat-on", "&dChat privado com {jogador} ligado. Usa &f/amigo chat &dpara sair."),
            Map.entry("chat-off", "&7Chat privado desligado."),
            Map.entry("chat-partner-left", "&7{jogador} saiu; o chat privado foi desligado."),
            Map.entry("private-sent", "&7[&dPara &f{jogador}&7] &f{mensagem}"),
            Map.entry("private-received", "&7[&dDe &f{jogador}&7] &f{mensagem}"),
            Map.entry("chat-actionbar", "&dChat privado com {jogador} &7— /amigo chat para sair"),
            // prompt de adicionar
            Map.entry("prompt-start", "&eEscreve no chat o nome do jogador a adicionar ({n}s). Escreve &ccancelar &epara desistir."),
            Map.entry("prompt-cancelled", "&7Adição cancelada."),
            Map.entry("prompt-expired", "&7O tempo para adicionar um amigo acabou."),
            // menus
            Map.entry("menu-main-title", "Amigos"),
            Map.entry("menu-requests-title", "Pedidos de amizade"),
            Map.entry("menu-friend-title", "{jogador}"),
            Map.entry("menu-add", "&aAdicionar amigo"),
            Map.entry("menu-requests", "&ePedidos ({n})"),
            Map.entry("menu-back", "&7Voltar"),
            Map.entry("menu-online", "&aOnline"),
            Map.entry("menu-offline", "&7Offline"),
            Map.entry("menu-manage", "&7Clica para gerir"),
            Map.entry("menu-since", "&7Amigos desde {data}"),
            Map.entry("menu-request-lore", "&aEsquerdo: aceitar &7| &cDireito: recusar"),
            Map.entry("menu-tpauto", "&bTeleporte automático"),
            Map.entry("menu-notify", "&eAvisos de entrada e saída"),
            Map.entry("menu-chat", "&dChat privado"),
            Map.entry("menu-on", "&aLigado"),
            Map.entry("menu-off", "&cDesligado"),
            Map.entry("menu-goto", "&aIr ter com"),
            Map.entry("menu-remove", "&cRemover"),
            Map.entry("menu-remove-confirm", "&cClica outra vez para confirmar"));

    public static FriendsConfig from(ConfigurationSection c) {
        Map<String, Integer> limits = new HashMap<>();
        ConfigurationSection section = c.getConfigurationSection("limits");
        if (section != null) {
            for (String group : section.getKeys(false)) {
                limits.put(group.toLowerCase(Locale.ROOT), section.getInt(group));
            }
        }
        Map<String, String> messages = new HashMap<>();
        DEFAULT_MESSAGES.forEach((k, v) ->
                messages.put(k, ChatColor.translateAlternateColorCodes('&', c.getString("messages." + k, v))));
        return new FriendsConfig(
                cap(c.getInt("default-limit", 10)),
                Map.copyOf(limits),
                Math.max(1, c.getInt("request-expire-days", 7)),
                Math.max(1, c.getInt("confirm-seconds", 15)),
                Math.max(1, c.getInt("prompt-seconds", 15)),
                Set.copyOf(c.getStringList("tp.allowed-worlds")),
                Map.copyOf(messages));
    }

    /** Maior limite entre os grupos com limite configurado; sem nenhum, o limite por omissão. Nunca acima de {@link #HARD_CAP}. */
    public int limitFor(Set<String> groups) {
        return cap(groups.stream()
                .map(g -> limits.get(g.toLowerCase(Locale.ROOT)))
                .filter(l -> l != null)
                .max(Integer::compare)
                .orElse(defaultLimit));
    }

    /** Mensagem já com cores; uma chave desconhecida devolve a própria chave para se notar o erro. */
    public String msg(String key) {
        return messages.getOrDefault(key, key);
    }

    private static int cap(int limit) {
        return Math.max(0, Math.min(HARD_CAP, limit));
    }
}
