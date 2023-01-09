CREATE INDEX idx_event_pubkey_kind_created_at
    ON event (pubkey, kind, created_at)
    WHERE deleted = FALSE;


