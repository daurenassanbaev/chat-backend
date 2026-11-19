# Локальный чат

Из корня проекта запустите `docker compose -f infra/docker-compose.yml up -d --build`. Откройте [мессенджер](http://localhost:8080/messenger.html). Для запуска одного Keycloak используйте `docker compose -f infra/docker-compose.yml up -d keycloak`. При первом старте он импортирует realm `chat`. Конфигурация и пароли предназначены для локальной разработки.

| Логин | Пароль | UUID для нового чата |
|---|---|---|
| alice | alice-local | 00000000-0000-4000-8000-000000000001 |
| bob | bob-local | 00000000-0000-4000-8000-000000000002 |

## Получение access token через curl

Для public client `chat-web` используется Authorization Code с PKCE S256. В одном терминале создайте одноразовый verifier и выведите URL для входа.

```sh
oidc=http://localhost:8083/realms/chat/protocol/openid-connect
redirect=http://localhost:8080/messenger.html
verifier=$(openssl rand -hex 32)
challenge=$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
printf '%s\n' "$oidc/auth?client_id=chat-web&response_type=code&scope=openid&redirect_uri=$redirect&code_challenge_method=S256&code_challenge=$challenge&response_mode=query"
```

Откройте URL в браузере и войдите. В Network инструментов разработчика включите сохранение запросов перед входом. В заголовке Location ответа с перенаправлением на messenger.html скопируйте значение параметра `code`. Сразу обменяйте его в том же терминале (понадобится jq).

```sh
read -r code
# Вставьте значение code и нажмите Enter.
token=$(curl -fsS "$oidc/token" \
  -d grant_type=authorization_code -d client_id=chat-web \
  --data-urlencode "redirect_uri=$redirect" --data-urlencode "code=$code" \
  --data-urlencode "code_verifier=$verifier" | jq -er .access_token)
curl -fsS -H "Authorization: Bearer $token" http://localhost:8081/chats
```

Access token действует 10 минут. Одна пара code/verifier используется один раз.
