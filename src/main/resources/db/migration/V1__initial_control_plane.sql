CREATE TABLE household (
    id UUID PRIMARY KEY,
    encrypted_display_name BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE household_member (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    encrypted_display_name BYTEA NOT NULL,
    role VARCHAR(24) NOT NULL CHECK (role IN ('MEMBER', 'ADMIN', 'ROOT_ADMIN')),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE web_device (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    member_id UUID NOT NULL REFERENCES household_member(id),
    encrypted_display_name BYTEA NOT NULL,
    public_identity BYTEA NOT NULL,
    acceptance_method VARCHAR(32) NOT NULL CHECK (acceptance_method IN ('ADMIN_APPROVAL', 'HOME_LAN_AUTOMATIC')),
    accepted_by UUID REFERENCES household_member(id),
    accepted_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL CHECK (status IN ('ACCEPTED', 'BLOCKED', 'REVOKED')),
    credential_generation INTEGER NOT NULL DEFAULT 1 CHECK (credential_generation > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE web_session (
    id UUID PRIMARY KEY,
    web_device_id UUID NOT NULL REFERENCES web_device(id),
    backend_node_id VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    end_reason VARCHAR(48),
    protocol_version INTEGER NOT NULL,
    CHECK (expires_at > started_at)
);

CREATE TABLE web_access_policy (
    household_id UUID PRIMARY KEY REFERENCES household(id),
    web_access_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    android_enrollment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    android_enrollment_mode VARCHAR(32) NOT NULL DEFAULT 'OPEN_ON_HOME_LAN',
    web_enrollment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    web_enrollment_mode VARCHAR(32) NOT NULL DEFAULT 'ADMIN_APPROVAL_REQUIRED',
    idle_timeout_seconds INTEGER NOT NULL DEFAULT 1800 CHECK (idle_timeout_seconds BETWEEN 300 AND 43200),
    max_concurrent_sessions INTEGER NOT NULL DEFAULT 2 CHECK (max_concurrent_sessions BETWEEN 1 AND 10),
    synchronization_mode VARCHAR(32) NOT NULL DEFAULT 'AFTER_EACH_SAVE',
    periodic_interval_seconds INTEGER NOT NULL DEFAULT 30 CHECK (periodic_interval_seconds BETWEEN 15 AND 86400),
    retained_revisions INTEGER NOT NULL DEFAULT 5 CHECK (retained_revisions BETWEEN 1 AND 100),
    activity_retention_days INTEGER NOT NULL DEFAULT 100 CHECK (activity_retention_days BETWEEN 1 AND 3650),
    trash_retention_days INTEGER NOT NULL DEFAULT 30 CHECK (trash_retention_days BETWEEN 1 AND 365),
    version BIGINT NOT NULL DEFAULT 1,
    changed_by UUID NOT NULL REFERENCES household_member(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin_passkey_credential (
    id UUID PRIMARY KEY,
    admin_member_id UUID NOT NULL REFERENCES household_member(id),
    credential_id BYTEA NOT NULL UNIQUE,
    public_key BYTEA NOT NULL,
    signature_counter BIGINT NOT NULL DEFAULT 0 CHECK (signature_counter >= 0),
    transports VARCHAR(256),
    encrypted_friendly_name BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE TABLE note (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    current_revision_id UUID,
    conflict_state BOOLEAN NOT NULL DEFAULT FALSE,
    trashed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE note_revision (
    id UUID PRIMARY KEY,
    note_id UUID NOT NULL REFERENCES note(id),
    author_member_id UUID NOT NULL REFERENCES household_member(id),
    origin_device_id UUID,
    encrypted_payload BYTEA NOT NULL,
    payload_nonce BYTEA NOT NULL,
    payload_key_version INTEGER NOT NULL CHECK (payload_key_version > 0),
    content_digest BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (note_id, content_digest)
);

ALTER TABLE note
    ADD CONSTRAINT fk_note_current_revision
    FOREIGN KEY (current_revision_id) REFERENCES note_revision(id);

CREATE TABLE note_revision_parent (
    revision_id UUID NOT NULL REFERENCES note_revision(id) ON DELETE CASCADE,
    parent_revision_id UUID NOT NULL REFERENCES note_revision(id),
    PRIMARY KEY (revision_id, parent_revision_id),
    CHECK (revision_id <> parent_revision_id)
);

CREATE TABLE activity_event (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    actor_member_id UUID REFERENCES household_member(id),
    origin_device_id UUID,
    event_type VARCHAR(80) NOT NULL,
    encrypted_metadata BYTEA,
    previous_event_hash BYTEA,
    event_hash BYTEA NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sync_receipt (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    revision_id UUID NOT NULL REFERENCES note_revision(id),
    target_device_id UUID NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('PENDING', 'ACKNOWLEDGED', 'FAILED')),
    attempted_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    encrypted_failure_detail BYTEA,
    UNIQUE (revision_id, target_device_id)
);

CREATE TABLE idempotency_record (
    idempotency_key UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES household(id),
    web_device_id UUID NOT NULL REFERENCES web_device(id),
    command_type VARCHAR(64) NOT NULL,
    request_digest BYTEA NOT NULL,
    encrypted_result BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_web_device_household_status ON web_device(household_id, status);
CREATE INDEX idx_web_session_device_active ON web_session(web_device_id, ended_at, expires_at);
CREATE INDEX idx_note_household_updated ON note(household_id, updated_at DESC) WHERE trashed_at IS NULL;
CREATE INDEX idx_note_revision_note_created ON note_revision(note_id, created_at DESC);
CREATE INDEX idx_activity_household_time ON activity_event(household_id, occurred_at DESC);
CREATE INDEX idx_sync_receipt_target_state ON sync_receipt(target_device_id, state);
