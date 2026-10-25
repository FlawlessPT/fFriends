# TODO fFriends

- tp só no mundo dos terrenos (hoje `tp.allowed-worlds` vazio = desligado).
- Bloquear jogadores.
- `/tpahere`.
- Limites por grupo ignoram a herança (`PermissionsApi.getGroups` só devolve os grupos explícitos; ver o `shortcut:` em `FriendsService.limitOf`).
- Menu de 28 amigos sem paginação.
- O limite de amigos não é atómico: accepts/pedidos concorrentes do mesmo jogador podem passar o limite em 1 e o teto de 28 do menu; aceite por ser raro.
