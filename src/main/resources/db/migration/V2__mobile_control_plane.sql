ALTER TABLE household_member ADD COLUMN IF NOT EXISTS display_name_hash VARCHAR(64);

ALTER TABLE web_device
    ADD COLUMN IF NOT EXISTS external_identity VARCHAR(160),
    ADD COLUMN IF NOT EXISTS device_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS app_name VARCHAR(96),
    ADD COLUMN IF NOT EXISTS model_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(24),
    ADD COLUMN IF NOT EXISTS platform VARCHAR(24),
    ADD COLUMN IF NOT EXISTS last_connected_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_device_external_identity
    ON web_device(external_identity)
    WHERE external_identity IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_web_device_token_hash
    ON web_device(device_token_hash)
    WHERE device_token_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_household_member_display_name_hash
    ON household_member(household_id, display_name_hash)
    WHERE display_name_hash IS NOT NULL;
