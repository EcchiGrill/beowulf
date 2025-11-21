package com.beowulf.core.visitor;

import com.beowulf.core.user.AppUser;

import java.util.UUID;

public class ArchiveOperationContext implements LogVisitable {

    private AppUser user;

    private UUID archiveId;
    private String format; // ZIP / TAR / ...
    private String compression; // GZIP / LZMA / ...
    private String archivePath;
    private long sizeBytes;

    private UUID checksumId;
    private String checksumType; // CRC32, SHA256
    private String checksumValue;

    private String operation; // COMPRESS / DECOMPRESS
    private String operationPath; // path involved (e.g. source or target)
    private String status; // SUCCESS / FAILED
    private long durationMs;

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public UUID getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(UUID archiveId) {
        this.archiveId = archiveId;
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

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public UUID getChecksumId() {
        return checksumId;
    }

    public void setChecksumId(UUID checksumId) {
        this.checksumId = checksumId;
    }

    public String getChecksumType() {
        return checksumType;
    }

    public void setChecksumType(String checksumType) {
        this.checksumType = checksumType;
    }

    public String getChecksumValue() {
        return checksumValue;
    }

    public void setChecksumValue(String checksumValue) {
        this.checksumValue = checksumValue;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperationPath() {
        return operationPath;
    }

    public void setOperationPath(String operationPath) {
        this.operationPath = operationPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public void accept(LogVisitor visitor) {
        visitor.visit(this);
    }
}
