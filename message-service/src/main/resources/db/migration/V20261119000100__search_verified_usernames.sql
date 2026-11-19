ALTER TABLE users ADD COLUMN username_verified boolean NOT NULL DEFAULT false;
CREATE INDEX idx_users_username_prefix ON users (lower(username) text_pattern_ops) WHERE username_verified;
