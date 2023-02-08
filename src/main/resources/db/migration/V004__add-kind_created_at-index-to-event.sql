DROP INDEX idx_event_tags;
CREATE INDEX idx_event_tags ON event USING  gin (tags jsonb_path_ops)
    WHERE deleted = FALSE;

CREATE INDEX idx_event_kind_created_at
    ON event (kind, created_at desc)
    WHERE deleted = FALSE;

