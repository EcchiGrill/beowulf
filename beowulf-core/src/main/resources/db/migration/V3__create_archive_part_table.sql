CREATE TABLE IF NOT EXISTS archive_part (
    id          BIGSERIAL PRIMARY KEY,
    archive_id  UUID      NOT NULL REFERENCES archive(id) ON DELETE CASCADE,
    part_index  INT       NOT NULL,
    path        TEXT      NOT NULL,
    size_bytes  BIGINT    NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_archive_part_archive_idx
    ON archive_part(archive_id, part_index);
