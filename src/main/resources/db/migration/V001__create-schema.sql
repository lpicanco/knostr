CREATE TABLE event(
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    pubkey VARCHAR(64) NOT NULL,
    created_at INT NOT NULL,
    kind INT NOT NULL,
    content TEXT NOT NULL,
    sig VARCHAR(128) NOT NULL,
    tags jsonb NOT NULL
);

CREATE INDEX idx_event_pubkey ON event(pubkey);
CREATE INDEX idx_event_created_at ON event(created_at);
CREATE INDEX idx_event_tags ON event USING  gin (tags jsonb_path_ops);
