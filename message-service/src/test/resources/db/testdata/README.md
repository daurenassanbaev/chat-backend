# Test data

These scripts are not Flyway migrations and are not applied by the application.
Run them manually in numeric order after the schema migrations:

1. `01_insert_users.sql`
2. `02_insert_chats.sql`
3. `03_insert_chat_members.sql`
4. `04_insert_messages.sql`
5. `05_insert_outbox_events.sql`
