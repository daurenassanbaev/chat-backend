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
                var beforePlans = plans(sql, before);
                sql.execute("CREATE INDEX idx_messages_chat_created_id ON messages(chat_id,created_at DESC,id DESC)");
                sql.execute("ANALYZE messages");
                var afterPlans = plans(sql, after);
                String report = """
                        # getChats: SQL experiment

                        Run: `./gradlew :message-service:queryBenchmark --rerun-tasks`

                        PostgreSQL 16 Alpine in Testcontainers, default settings. 1,000,000 messages,
                        100 chats, 20 selected chats, payload 100 characters, evenly distributed history.
                        Before: original single-column indexes; query returns 200,000 history rows.
                        After: added (chat_id, created_at DESC, id DESC); query returns 20 latest messages.
                        One warm-up plus three EXPLAIN (ANALYZE, BUFFERS) runs per query, same container.
                        This measures SQL only, excluding network transfer and Java entity allocation.
                        Local timings are not a production SLA; the dataset is synthetic.

                        """;
                report += "Before SQL:\n```sql\n" + before + "\n```\n\n";
                report += "After SQL:\n```sql\n" + after + "\n```\n\n";
                report += format("Before", beforePlans) + format("After", afterPlans);
                Path output = Path.of(System.getProperty("benchmark.report"));
                Files.createDirectories(output.getParent());
                Files.writeString(output, report);
            }
        }
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
