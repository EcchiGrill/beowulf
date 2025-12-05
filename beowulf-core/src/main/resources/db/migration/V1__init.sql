CREATE TABLE IF NOT EXISTS app_user (
    id         UUID PRIMARY KEY,
    username   TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS checksum (
    id         UUID PRIMARY KEY,
    type       VARCHAR(20) NOT NULL, -- CRC32 / SHA256
    value      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS archive (
    id          UUID PRIMARY KEY,
    user_id     UUID      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    checksum_id UUID      NOT NULL REFERENCES checksum(id) ON DELETE CASCADE,
    format      VARCHAR(20) NOT NULL, -- ZIP / TAR / RAR / ACE
    compression VARCHAR(20) NOT NULL, -- GZIP / BZIP2 / LZMA / XZ / ZSTD
    path        TEXT        NOT NULL,
    size_bytes  BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS archive_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    archive_id  UUID      NOT NULL REFERENCES archive(id) ON DELETE CASCADE,
    operation   VARCHAR(20) NOT NULL, -- COMPRESS / DECOMPRESS / UPDATE
    status      VARCHAR(20) NOT NULL, -- SUCCESS / FAILED
    duration_ms BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
