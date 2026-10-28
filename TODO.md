# TODO fFriends

- tp só no mundo dos terrenos (hoje `tp.allowed-worlds` vazio = desligado).
- Bloquear jogadores.
- `/tpahere`.
- Limites por grupo ignoram a herança (`PermissionsApi.getGroups` só devolve os grupos explícitos; ver o `shortcut:` em `FriendsService.limitOf`).
- Menu de 28 amigos sem paginação.
- O limite de amigos não é atómico: accepts/pedidos concorrentes do mesmo jogador podem passar o limite em 1 e o teto de 28 do menu; aceite por ser raro.
- Quit não limpa prompts de adicionar nem confirmações de remover (expiram sozinhos).
- Após `/reload`, o onEnable carrega todos os online, mesmo os ainda não autenticados no fAuth (que não suporta `/reload`).
- Tab-complete de `aceitar`/`recusar` sem nomes (pedidos são consulta assíncrona).
- Chat privado unidirecional: só fala em privado quem o ligou.

- Reload de config exige `/freload plugin` (o `FriendsConfig` é partilhado por serviço, menu, comandos e listeners).
