# 🐺 Beowulf Archiver

Beowulf is a Java-based multi-module archiver application with:

- CLI tool (`beowulf-cli`)
- Desktop GUI in JavaFX (`beowulf-gui`)
- Core library with pluggable archive strategies (`beowulf-core`)
- PostgreSQL-backed logging with Flyway migrations & HikariCP
- Support for ZIP, TAR.GZ, RAR, ACE

It is designed as a coursework project to demonstrate classic design patterns:
**Strategy, Adapter, Factory Method, Facade, Visitor** and DB integration.

---

## 🔧 Tech Stack

- **Language:** Java 21
- **Build:** Gradle (multi-module)
- **UI:** JavaFX 21
- **Archiving:**
  - [Apache Commons Compress] for ZIP / TAR.GZ
  - External `rar` binary for RAR
  - External `unace` binary for ACE (**decompression**)
- **Database:** PostgreSQL 15
- **Connection Pool:** HikariCP
- **Migrations:** Flyway
- **Logging:** SQL tables `app_user`, `archive`, `archive_log`, `checksum`

---

## 📁 Project Structure

High-level modules:

```text
.
├── beowulf-core/        # Core domain logic, patterns, DB, visitors
├── beowulf-cli/         # Command-line interface (compress/decompress/logs)
├── beowulf-gui/         # JavaFX desktop GUI
├── reports/             # Coursework / lab reports (architecture, design, etc.)
├── build.gradle.kts
├── settings.gradle.kts
└── docker-compose.yml
```

---

## 🧱 Architecture & Design Patterns

### Strategy – `Archiver` Implementations

Core abstraction:

```java
public interface Archiver {
    /**
     * Compresses the given source directory or file into a single archive file.
     *
     * @param sourceDir     path to file or directory to compress
     * @param targetArchive path to resulting archive file
     */
    void compress(Path sourceDir, Path targetArchive) throws IOException;

    /**
     * Extracts the given archive file into the target directory.
     *
     * @param archive   path to existing archive
     * @param targetDir destination directory
     */
    void decompress(Path archive, Path targetDir) throws IOException;

    String getName();
}
```

Concrete strategies:

- `ZipArchiver` – ZIP archives via `ZipAdapter`
- `TarGzArchiver` – `.tar.gz` via `TarGzAdapter`
- `RarArchiver` – `.rar` via external `rar` tool
- `AceArchiver` – `.ace` via external `unace` tool

### Adapter – Wrap Libraries / Binaries

Examples:

- `ZipAdapter` – wraps Apache Commons Compress for ZIP I/O
- `TarGzAdapter` – wraps tar + gzip streams
- `RarAdapter` – calls the external `rar` binary via `ProcessBuilder`

  - Detects `rar` in `PATH`
  - Throws clear `IOException` if `rar` is missing
  - GUI/CLI catch this and display a user-friendly error

- `AceAdapter` – calls the external `unace` binary via `ProcessBuilder`

This keeps the core `Archiver` logic independent from specific libraries or OS tools.

### Factory Method – `ArchiverFactory`

`ArchiverFactory` chooses the correct `Archiver` based on the target path / extension:

```java
Archiver archiver = archiverFactory.getArchiver(targetArchive);
// e.g. *.zip -> ZipArchiver, *.tar.gz -> TarGzArchiver, *.rar -> RarArchiver, *.ace -> AceArchiver,
```

This is used by both CLI and GUI.

### Facade – Core Facade for CLI/GUI

The core module exposes a simplified façade (through `ArchiverFactory`, `ArchiverLogger`, and DB services) so that:

- CLI (`BeowulfCLI`) can just say:
  `archiver.compress(source, target);`
- GUI (`BeowulfGUI`) can do the same, without knowing about HikariCP, Flyway, SQL, etc.

### Visitor – Logging & Persistence

The **Visitor pattern** is used to decouple archiving operations from database persistence:

- `ArchiveOperation`

  - Holds operation data: `operation`, `status`, `archivePath`, `targetPath`, `format`, `compression`, `checksumType`, `checksumValue`, `sizeBytes`, `durationMs`, `user`, etc.

- `ArchiveVisitor`

  - Visitor that calls `ArchivePersistenceService` to persist the context into DB

- `ArchiverLogger`

  - Wraps a concrete `Archiver`
  - Measures time and status, builds an `ArchiveOperation`
  - Calls `accept(visitor)` to persist logs

So adding “logging” required 0 changes to the basic archivers — just wrap them in `ArchiverLogger`.

---

## 🗄️ Database & Logging

### Schema (simplified)

```sql
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
    path        TEXT        NOT NULL, -- full path to archive file
    size_bytes  BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS archive_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    archive_id  UUID      NOT NULL REFERENCES archive(id) ON DELETE CASCADE,
    operation   VARCHAR(20) NOT NULL, -- COMPRESS / DECOMPRESS
    status      VARCHAR(20) NOT NULL, -- SUCCESS / FAILED
    target_path TEXT        NOT NULL,
    duration_ms BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS archive_part (
    id          BIGSERIAL PRIMARY KEY,
    archive_id  UUID      NOT NULL REFERENCES archive(id) ON DELETE CASCADE,
    part_index  INT       NOT NULL,
    path        TEXT      NOT NULL,
    size_bytes  BIGINT    NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Key ideas:

- `app_user` – logical user of the app.
- `checksum` – stored checksums (`CRC32`, `SHA256`, etc.).
- `archive` – metadata about archive files: format, compression type, size, path.
- `archive_log` – history of operations per user + outcome and duration.
- `archive_part` - split archive part

### User Identity

`AppUserService`:

- Derives a stable `AppUser` from local environment (`user.name`, home dir, etc).
- Stores it (if needed) in a config under `~/.beowulf`.
- Ensures all operations are associated with the same `app_user.id`.

### Persistence Services

- `ArchivePersistenceService`

  - Uses JDBC + HikariCP (`DataSourceFactory.getDataSource()`)
  - Within one transaction:

    - upserts `app_user`
    - inserts `checksum`
    - inserts `archive`
    - inserts `archive_log`

- `ArchiveLogService`

  - Executes read-only queries joining `archive_log`, `archive`, `checksum`
  - Returns `ArchiveLog` DTOs for GUI / CLI to render

All connections are taken from HikariCP; Flyway applies migrations before first use (`DbMigrations.migrate()`).

---

## 🐚 CLI Usage

### Build

From the project root:

```bash
./gradlew :beowulf-cli:installDist
```

This creates a distribution under:

```text
beowulf-cli/build/install/beowulf/
```

Main executable script:

```bash
beowulf-cli/build/install/beowulf/bin/beowulf
```

### Commands

General form:

```bash
beowulf <command> [args...]
```

Supported commands:

- `compress <sourceDirOrFile> <targetArchive>`
- `decompress <archiveFile> <targetDir>`
- `logs` – show recent DB logs for current `app_user`

#### Examples (local)

```bash
# Compress folder into zip
beowulf compress ./docs ./backup.zip

# Compress folder into tar.gz
beowulf compress ./logs ./logs.tar.gz

# Decompress into output dir
beowulf decompress ./backup.zip ./restored

# Show recent logged operations
beowulf logs
```

Errors (e.g. missing `rar` binary, DB connection issues) are printed with stack traces and a clear message.

---

## 🖥️ JavaFX GUI

Module: `beowulf-gui`

### Running

```bash
./gradlew :beowulf-gui:run
```

Requires:

- JavaFX runtime (handled via Gradle dependencies)
- PostgreSQL running and reachable from `DataSourceFactory` configuration.

### GUI Features

- **Compress tab**

  - Select **source** (file or folder) via `Browse…` or paste path manually.
  - Select **output folder** for archive.
  - Choose **archive format**: `ZIP`, `TAR.GZ`, `RAR`.
  - Archive name:

    - User types any base name.
    - Placeholder shows example with chosen extension (`my-archive.zip`, `...tar.gz`, `...rar`).
    - On compress, the correct extension is always added.

  - Progress:

    - Progress bar shown for at least 2 seconds (even on fast operations).
    - On success – info modal; on failure – error modal with message (including “RAR not installed” etc.).

- **Decompress tab**

  - Select archive file (`.zip`, `.tar.gz`, `.rar`, etc.).
  - Select or paste output directory.
  - Same progress bar + success/error modal behaviour.

- **Logs tab**

  - Table showing:

    - `Time` (local, `HH:mm:ss yyyy-MM-dd`)
    - `Operation` (`COMPRESS` / `DECOMPRESS`)
    - `Status` (`SUCCESS` / `FAILED`)
    - `Path`:

      - `COMPRESS` – full path to archive file
      - `DECOMPRESS` – output directory
      - `UPDATE` - full path to archive file

    - `Format`, `Compression`, `Size`, `Duration`, `Split archive`

  - Double-click a row:

    - Opens a detail modal:

      - Time -> formatted as `HH:mm dd MMMM yyyy`
      - Status colored (green/red)
      - Split archive parts info
      - Clickable `Path` link:

        - For files – opens containing folder in OS file manager
        - For directories – opens that directory

All GUI operations are performed on background `Task`s and marshalled back to the JavaFX thread.

---

## 🐘 PostgreSQL & Docker

### Docker Compose

Example `docker-compose.yml:

```yaml
services:
  postgres:
    image: postgres:15
    container_name: beowulf-postgres
    environment:
      POSTGRES_USER: beowulf
      POSTGRES_PASSWORD: beowulf
      POSTGRES_DB: beowulf_db
    ports:
      - "5432:5432"
    volumes:
      - pg_data:/var/lib/postgresql/data

volumes:
  pg_data:
```

- Postgres is exposed on `localhost:5432` for host tools / GUI.
- CLI container sees it as `postgres:5432` (service name).

### Running CLI in Docker

From repo root:

```bash
# Start only database
docker-compose up -d postgres

# Run a one-shot CLI command (container exits after)
docker-compose run --rm beowulf compress /data/Documents /data/example.zip

docker-compose run --rm beowulf decompress /data/example.zip /data/output

docker-compose run --rm beowulf logs
```

`./data` on host is mounted as `/data` in the container, so use `/data/...` paths inside CLI calls.

### DataSource Configuration

`DataSourceFactory` configures HikariCP with a JDBC URL like:

```java
config.setJdbcUrl("jdbc:postgresql://postgres:5432/beowulf_db"); // in container
config.setUsername("beowulf");
config.setPassword("beowulf");
```

For a pure desktop / local setup you can adapt it to:

```java
config.setJdbcUrl("jdbc:postgresql://localhost:5432/beowulf_db");
```

or make it configurable via environment variables
(`BEOWULF_DB_URL`, `BEOWULF_DB_USER`, `BEOWULF_DB_PASS`).

---

## 🧪 Testing

Example unit test (`ZipArchiverTest`) to verify round-trip compression:

```java
class ZipArchiverTest {
    @Test
    void roundTripCompression() throws Exception {
        Path inputDir = Files.createTempDirectory("beowulf-in-dir");
        Path file = inputDir.resolve("compress-me.txt");
        String content = "Compress me in, Beowulf!";
        Files.writeString(file, content);

        Path archive = Files.createTempFile("beowulf-archive", ".zip");
        Path outputDir = Files.createTempDirectory("beowulf-out-dir");

        ZipArchiver archiver = new ZipArchiver();
        archiver.compress(inputDir, archive);
        archiver.decompress(archive, outputDir);

        Path extractedFile = outputDir.resolve("compress-me.txt");

        assertTrue(Files.exists(archive), "Archive should exist after compression");
        assertTrue(Files.exists(extractedFile), "File should exist after decompression");

        String extractedContent = Files.readString(extractedFile);
        assertEquals(content, extractedContent, "Decompressed file content should match original");
    }
}
```

Similar tests can be added for TAR.GZ and RAR (where supported).

---

## 📚 Reports

The **`reports/`** folder in the project root contains the **course / lab reports** for this project, including:

- requirements and problem statement;
- UML diagrams (class diagrams, component diagrams, etc.);
- explanation of patterns (Strategy, Adapter, Factory Method, Facade, Visitor);
- database design and migration strategy;
- deployment and usage patterns (CLI, GUI, Docker, DB).

These reports are part of the formal documentation for the coursework and complement this README.
