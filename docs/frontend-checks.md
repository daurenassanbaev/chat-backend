# Проверка messenger.html

Java/STOMP и Kafka: `./gradlew test`.

DOM-проверки без изменения сборки frontend (нужен Node.js, здесь запускались на v26.6.0):

```sh
npm install --prefix /tmp/chat-ui-check --no-package-lock jsdom@31.0.1
NODE_PATH=/tmp/chat-ui-check/node_modules node --test scripts/test-messenger.cjs
```

Проверяется настоящий inline-скрипт HTML в DOM jsdom: немедленное отображение,
ошибка отправки, связывание clientMessageId с серверным messageId в обоих порядках,
игнорирование устаревших HTTP-ответов, повторное открытие чата без дублирования.
Это не визуальный тест и не полный браузерный E2E. Реальный WebSocket/STOMP
проверяется Java-интеграционными тестами с Testcontainers.

`/user/queue/send-results` связывает временную строку с серверным ID после принятия
Kafka. Это не подтверждение сохранения в PostgreSQL или получения собеседником.
clientMessageId не хранится в PostgreSQL и не обеспечивает дедупликацию ручных повторов.
