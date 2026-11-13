CREATE TABLE chat_members (
    chat_id UUID NOT NULL REFERENCES chats (id),
    user_id UUID NOT NULL REFERENCES users (id),
    role TEXT NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX idx_chat_members_user_id ON chat_members (user_id);
