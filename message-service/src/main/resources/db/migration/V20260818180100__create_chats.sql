CREATE TABLE chats (
    id UUID PRIMARY KEY,
    type TEXT NOT NULL,
    name TEXT,
    created_at TIMESTAMP NOT NULL,
    created_by UUID REFERENCES users (id)
);
