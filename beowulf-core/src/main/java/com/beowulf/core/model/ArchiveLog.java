package com.beowulf.core.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ArchiveLog {

    private long id;
    private UUID userId;
    private UUID archiveId;

    private String operation;
    private String status;
    private String archivePath;
    private String targetPath;
    private String format;
    private String compression;
    private long sizeBytes;
    private long durationMs;
    private OffsetDateTime createdAt;

    private Long splitPartsCount;
    private Long splitTotalSize;
    private String splitFirstPath;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(UUID archiveId) {
        this.archiveId = archiveId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getSplitPartsCount() {
        return splitPartsCount;
    }

    public void setSplitPartsCount(Long splitPartsCount) {
        this.splitPartsCount = splitPartsCount;
    }

    public Long getSplitTotalSize() {
        return splitTotalSize;
    }

    public void setSplitTotalSize(Long splitTotalSize) {
        this.splitTotalSize = splitTotalSize;
    }

    public String getSplitFirstPath() {
        return splitFirstPath;
    }

    public void setSplitFirstPath(String splitFirstPath) {
        this.splitFirstPath = splitFirstPath;
    }
}
