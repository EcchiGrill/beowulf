package com.beowulf.core.visitor;

import com.beowulf.core.model.ArchivePart;
import com.beowulf.core.user.AppUser;

import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

public class ArchiveOperation {

    private AppUser user;

    private String operation;
    private String status;
    private String archivePath;
    private String targetPath;

    private String format; // ZIP / TAR / RAR / ACE
    private String compression; // GZIP / BZIP2 / LZMA / XZ / ZSTD
    private String checksumType; // SHA256 / CRC32
    private String checksumValue;
    private long sizeBytes;
    private long durationMs;

    private UUID checksumId;
    private UUID archiveId;

    private List<ArchivePart> parts;

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
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

    public UUID getChecksumId() {
        return checksumId;
    }

    public void setChecksumId(UUID checksumId) {
        this.checksumId = checksumId;
    }

    public UUID getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(UUID archiveId) {
        this.archiveId = archiveId;
    }

    public List<ArchivePart> getParts() {
        return parts;
    }

    public void setParts(List<ArchivePart> parts) {
        this.parts = parts;
    }

    public void addPart(ArchivePart part) {
        if (part == null) {
            return;
        }
        if (this.parts == null) {
            this.parts = new ArrayList<>();
        }
        this.parts.add(part);
    }

}
