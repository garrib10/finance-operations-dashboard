-- One row per login/device session family. IDs are application-generated UUIDs.
CREATE TABLE refresh_sessions (
    id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revocation_reason VARCHAR(24) NULL,
    PRIMARY KEY (id),
    INDEX idx_refresh_sessions_user_revoked (user_id, revoked_at),
    INDEX idx_refresh_sessions_expires_at (expires_at),
    CONSTRAINT fk_refresh_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_refresh_sessions_expiration
        CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_sessions_revocation_pair
        CHECK ((revoked_at IS NULL AND revocation_reason IS NULL)
            OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)),
    -- CASE mirrors V3: explicit comparison for MySQL, no IN predicate for H2.
    CONSTRAINT ck_refresh_sessions_revocation_reason
        CHECK (revocation_reason IS NULL OR (CASE revocation_reason
            WHEN 'LOGOUT' THEN 1
            WHEN 'PASSWORD_CHANGE' THEN 1
            WHEN 'REUSE_DETECTED' THEN 1
            ELSE 0 END) = 1)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- Token history within a family. Only SHA-256 digests are stored, never raw tokens.
-- No CHECK references session_id: MySQL forbids CHECK on cascading FK columns.
CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id CHAR(36) NOT NULL,
    token_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_tokens_session_id (session_id),
    CONSTRAINT fk_refresh_tokens_session
        FOREIGN KEY (session_id) REFERENCES refresh_sessions (id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
