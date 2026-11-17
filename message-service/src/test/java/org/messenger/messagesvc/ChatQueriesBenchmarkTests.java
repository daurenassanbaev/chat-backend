package org.messenger.messagesvc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Tag("benchmark")
class ChatQueriesBenchmarkTests {
    @Test
    void compareMillionRowChatPreviewQueries() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                    postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement()) {
                for (String migration : List.of("V20260818180000__create_users.sql",
                        "V20260818180100__create_chats.sql", "V20260818180300__create_messages.sql")) {
                    try (var resource = getClass().getResourceAsStream("/db/migration/" + migration)) {
                        sql.execute(new String(Objects.requireNonNull(resource).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                sql.execute("INSERT INTO users(id) VALUES (md5('user')::uuid)");
                sql.execute("""
                        INSERT INTO chats(id,type,name,created_at,created_by)
                        SELECT md5('chat-' || n)::uuid, 'DIRECT',
                            CASE WHEN n % 5 = 0 THEN 'selected' ELSE 'other' END,
                            timestamp '2026-01-01', md5('user')::uuid
                        FROM generate_series(0,99) n
                        """);
                sql.execute("""
                        INSERT INTO messages(id,chat_id,sender_id,content,created_at,status)
                        SELECT md5('message-' || n)::uuid, md5('chat-' || (n % 100))::uuid,
                            md5('user')::uuid, repeat('x',100),
                            timestamp '2026-01-01' + n * interval '1 millisecond', 'SENT'
                        FROM generate_series(1,1000000) n
                        """);
                sql.execute("ANALYZE messages");
                var ids = new ArrayList<String>();
                try (var rows = sql.executeQuery("SELECT id FROM chats WHERE name='selected'")) {
                    while (rows.next()) ids.add("'" + rows.getString(1) + "'::uuid");
                }
                String filter = " WHERE chat_id IN (" + String.join(",", ids) + ")";
                String before = "SELECT * FROM messages" + filter + " ORDER BY created_at DESC";
                String after = "SELECT DISTINCT ON (chat_id) * FROM messages" + filter
                        + " ORDER BY chat_id, created_at DESC, id DESC";
                String lateral = org.messenger.messagesvc.message.repository.MessageRepository.class
                        .getMethod("findLatestForChats", List.class)
                        .getAnnotation(org.springframework.data.jpa.repository.Query.class).value()
                        .replace(":chatIds", String.join(",", ids));
                var beforePlans = plans(sql, before);
                sql.execute("CREATE INDEX idx_messages_chat_created_id ON messages(chat_id,created_at DESC,id DESC)");
                sql.execute("ANALYZE messages");
                var distinctPlans = plans(sql, after);
                var lateralPlans = plans(sql, lateral);
                for (String plan : lateralPlans) {
                    org.junit.jupiter.api.Assertions.assertTrue(plan.contains("Index Scan using idx_messages_chat_created_id"),
                            "LATERAL should use the existing composite index: " + plan);
                }
                var expected = messageIds(sql, after);
                var actual = messageIds(sql, lateral);
                org.junit.jupiter.api.Assertions.assertEquals(20, actual.size());
                org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
                String report = """
                        # getChats: было / DISTINCT ON / LATERAL

                        Команда: `./gradlew :message-service:queryBenchmark --rerun-tasks`

                        PostgreSQL 16 Alpine в Testcontainers, настройки по умолчанию.
                        1 000 000 сообщений, 100 чатов, выбраны 20 чатов, текст сообщения — 100 символов.
                        По 10 000 сообщений на чат; время сообщения растёт с его номером.
                        Данные создаются через generate_series в отдельном одноразовом контейнере.

                        Один прогрев и три EXPLAIN (ANALYZE, BUFFERS) для каждого варианта.
                        Все варианты проверены последовательно на одном наборе данных в одном контейнере.
                        Для «было» оставлены исходные индексы отдельных колонок; перед DISTINCT ON
                        и LATERAL добавлен существующий в проекте индекс
                        (chat_id, created_at DESC, id DESC). Настройки планировщика не менялись.
                        Замер касается только SQL: передача ответа и создание Java-объектов не измеряются.
                        Это локальные результаты на искусственных данных, а не обещание времени ответа API.

                        | Вариант | Возвращено строк | Три запуска, мс | Медиана, мс |
                        |---|---:|---|---:|
                        """;
                report += summary("Было", 200000, beforePlans)
                        + summary("DISTINCT ON", 20, distinctPlans)
                        + summary("LATERAL", 20, lateralPlans);
                report += """

                        ## Что изменилось

                        Исходный запрос возвращал все 200 000 сообщений выбранных чатов,
                        после чего Java оставляла последнее для каждого чата.
                        DISTINCT ON возвращает 20 сообщений, но сначала обрабатывает большую историю;
                        конкретные чтения, сортировки и возможная запись на диск видны в планах ниже.
                        LATERAL для каждого из 20 chat_id находит первую запись в уже упорядоченном индексе
                        и останавливается после неё. Ограничение LIMIT 1 применяется отдельно к каждому чату.
                        В тесте проверяются использование idx_messages_chat_created_id и совпадение
                        выбранных сообщений с DISTINCT ON. Сам LATERAL взят из аннотации репозитория,
                        чтобы замер соответствовал запросу приложения.

                        Пустые чаты не дают строки сообщения: ChatService по-прежнему строит ответ
                        из списка чатов пользователя и подставляет null для отсутствующего сообщения.
                        При равном времени последняя запись выбирается по id DESC.
                        Преимущество проверено для 20 чатов с большой историей; для другого числа чатов
                        и объёма истории нужны отдельные измерения.

                        """;
                report += "Было:\n```sql\n" + before + "\n```\n\n";
                report += "DISTINCT ON:\n```sql\n" + after + "\n```\n\n";
                report += "LATERAL:\n```sql\n" + lateral + "\n```\n\n";
                report += format("Было", beforePlans) + format("DISTINCT ON", distinctPlans)
                        + format("LATERAL", lateralPlans);
                Path output = Path.of(System.getProperty("benchmark.report"));
                Files.createDirectories(output.getParent());
                Files.writeString(output, report);
            }
        }
    }
    private Set<UUID> messageIds(Statement sql, String query) throws SQLException {
        var ids = new HashSet<UUID>();
        try (var rows = sql.executeQuery(query)) {
            while (rows.next()) ids.add(rows.getObject("id", UUID.class));
        }
        return ids;
    }
    private String summary(String name, int rows, List<String> plans) {
        var times = plans.stream().map(plan -> {
            var matcher = java.util.regex.Pattern.compile("Execution Time: ([0-9.]+) ms").matcher(plan);
            if (!matcher.find()) throw new IllegalArgumentException("No execution time: " + plan);
            return Double.parseDouble(matcher.group(1));
        }).toList();
        var sorted = times.stream().sorted().toList();
        return "| " + name + " | " + rows + " | " + times.stream()
                .map(time -> String.format(Locale.ROOT, "%.3f", time)).collect(Collectors.joining(" / "))
                + " | " + String.format(Locale.ROOT, "%.3f", sorted.get(1)) + " |\n";
    }
    private List<String> plans(Statement sql, String query) throws SQLException {
        try (var rows = sql.executeQuery("EXPLAIN ANALYZE " + query)) { while (rows.next()) {} }
        var plans = new ArrayList<String>();
        for (int run = 0; run < 3; run++) {
            var lines = new ArrayList<String>();
            try (var rows = sql.executeQuery("EXPLAIN (ANALYZE, BUFFERS) " + query)) {
                while (rows.next()) lines.add(rows.getString(1));
            }
            plans.add(String.join("\n", lines));
        }
        return plans;
    }
    private String format(String label, List<String> plans) {
        return "## " + label + "\n\n" + plans.stream()
                .map(plan -> "```text\n" + plan + "\n```\n")
                .collect(Collectors.joining("\n")) + "\n";
    }
}
