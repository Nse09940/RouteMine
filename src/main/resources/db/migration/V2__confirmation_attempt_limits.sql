ALTER TABLE deliveries
    ADD COLUMN failed_confirmation_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (failed_confirmation_attempts >= 0),
    ADD COLUMN confirmation_locked_until TIMESTAMPTZ;
