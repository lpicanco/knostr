ALTER TABLE event
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
