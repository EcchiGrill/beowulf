package com.beowulf.core.model;

import java.util.UUID;

public class ArchivePart {
    private Long id;
    private UUID archiveId;
    private int partIndex;
    private String path;
    private long sizeBytes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(UUID archiveId) {
        this.archiveId = archiveId;
    }

    public int getPartIndex() {
        return partIndex;
    }

    public void setPartIndex(int partIndex) {
        this.partIndex = partIndex;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
