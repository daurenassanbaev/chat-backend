INSERT INTO outbox_events (id, topic, payload, sent, created_at)
VALUES
    (
        '40000000-0000-0000-0000-000000000001',
        'message-saved',
        '{"messageId":"30000000-0000-0000-0000-000000000001","eventType":"MESSAGE_SAVED"}',
        FALSE,
        TIMESTAMP '2026-08-01 09:05:01'
    ),
    (
        '40000000-0000-0000-0000-000000000002',
        'message-saved',
        '{"messageId":"30000000-0000-0000-0000-000000000002","eventType":"MESSAGE_SAVED"}',
        TRUE,
        TIMESTAMP '2026-08-01 09:06:01'
    );
