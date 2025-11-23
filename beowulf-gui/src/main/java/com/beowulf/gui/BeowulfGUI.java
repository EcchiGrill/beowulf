package com.beowulf.gui;

import com.beowulf.core.db.DataSourceFactory;
import com.beowulf.core.db.DbMigrations;
import com.beowulf.core.facade.ArchiveLogService;
import com.beowulf.core.facade.ArchivePersistenceService;
import com.beowulf.core.factory.ArchiverFactory;
import com.beowulf.core.interfaces.Archiver;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.AppUserService;
import com.beowulf.core.visitor.ArchiveLog;
import com.beowulf.core.visitor.ArchiverLogger;
import com.beowulf.core.visitor.ArchiveVisitor;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class BeowulfGUI extends Application {

    private AppUserService identityProvider;
    private ArchivePersistenceService persistenceService;
    private ArchiveLogService logQueryService;
    private ArchiveVisitor persistVisitor;
    private ArchiverFactory archiverFactory;

    private Stage primaryStage;

    private final ObservableList<LogRow> logRows = FXCollections.observableArrayList();

    private static final DateTimeFormatter TABLE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss yyyy-MM-dd");

    private static final DateTimeFormatter DETAIL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd MMMM yyyy",
            Locale.ENGLISH);

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        initCore();

        TabPane tabPane = new TabPane();
        tabPane.setTabMinWidth(130);

        tabPane.getTabs().addAll(
                buildCompressTab(primaryStage),
                buildDecompressTab(primaryStage),
                buildLogsTab());

        BorderPane root = new BorderPane(tabPane);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 1150, 720);
        root.setStyle("""
                    -fx-background-color: #f2f3f5;
                    -fx-font-family: "Segoe UI", "Roboto", sans-serif;
                    -fx-font-size: 16px;
                """);

        primaryStage.setTitle("Beowulf Archiver");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();

        reloadLogs();
    }

    private void initCore() {
        DbMigrations.migrate();
        this.identityProvider = new AppUserService();
        this.persistenceService = new ArchivePersistenceService();
        this.logQueryService = new ArchiveLogService();
        this.persistVisitor = new ArchiveVisitor(persistenceService);
        this.archiverFactory = new ArchiverFactory();
    }

    private Tab buildCompressTab(Stage stage) {
        TextField sourceField = new TextField();
        sourceField.setPromptText("Select or paste path to file/folder...");
        sourceField.setPrefHeight(34);

        Button browseSourceBtn = new Button("Browse…");
        browseSourceBtn.setPrefHeight(34);

        ContextMenu sourceMenu = new ContextMenu();
        MenuItem fileItem = new MenuItem("Select file…");
        MenuItem folderItem = new MenuItem("Select folder…");

        fileItem.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select file to compress");
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                sourceField.setText(file.toPath().toString());
            }
        });

        folderItem.setOnAction(ev -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select folder to compress");
            File dir = chooser.showDialog(stage);
            if (dir != null) {
                sourceField.setText(dir.toPath().toString());
            }
        });

        sourceMenu.getItems().addAll(fileItem, folderItem);
        browseSourceBtn.setOnAction(e -> sourceMenu.show(browseSourceBtn, Side.BOTTOM, 0, 0));

        HBox sourceRow = new HBox(8, sourceField, browseSourceBtn);
        HBox.setHgrow(sourceField, Priority.ALWAYS);

        TextField outputDirField = new TextField();
        outputDirField.setPromptText("Select or paste output folder...");
        outputDirField.setPrefHeight(34);

        Button outputBtn = new Button("Browse…");
        outputBtn.setPrefHeight(34);
        outputBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select output folder");
            File dir = chooser.showDialog(stage);
            if (dir != null) {
                outputDirField.setText(dir.toPath().toString());
            }
        });

        HBox outputRow = new HBox(8, outputDirField, outputBtn);
        HBox.setHgrow(outputDirField, Priority.ALWAYS);

        TextField archiveNameField = new TextField();
        archiveNameField.setPrefHeight(34);

        ComboBox<String> formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll(
                "ZIP (.zip)",
                "TAR.GZ (.tar.gz)",
                "RAR (.rar)");
        formatCombo.getSelectionModel().selectFirst();
        formatCombo.setPrefHeight(34);

        String initialExt = getExtForFormatLabel(formatCombo.getSelectionModel().getSelectedItem());
        archiveNameField.setPromptText("beowulf" + initialExt);

        formatCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            adjustArchiveNameForFormat(archiveNameField, newVal);
        });

        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(260);
        progressBar.setPrefHeight(18);

        Button compressBtn = new Button("Compress");
        compressBtn.setPrefHeight(36);
        compressBtn.setStyle("""
                    -fx-padding: 6 22;
                    -fx-font-size: 15px;
                    -fx-background-color: #4a4a4a;
                    -fx-text-fill: #ffffff;
                """);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(18));
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(24);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(76);
        grid.getColumnConstraints().addAll(col1, col2);

        Label title = new Label("Create archive");
        title.setStyle("-fx-text-fill: #202124; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Select source, output location and archive format.");
        subtitle.setStyle("-fx-text-fill: #5f6368;");

        Label sourceLabel = label("Source:");
        Label outputLabel = label("Output folder:");
        Label nameLabel = label("Archive name:");
        Label formatLabel = label("Format:");

        int row = 0;
        grid.add(title, 0, row, 2, 1);
        row++;
        grid.add(subtitle, 0, row, 2, 1);
        row++;
        grid.add(new Separator(), 0, row, 2, 1);
        row++;

        grid.add(sourceLabel, 0, row);
        grid.add(sourceRow, 1, row);
        row++;

        grid.add(outputLabel, 0, row);
        grid.add(outputRow, 1, row);
        row++;

        grid.add(nameLabel, 0, row);
        grid.add(archiveNameField, 1, row);
        row++;

        grid.add(formatLabel, 0, row);
        grid.add(formatCombo, 1, row);
        row++;

        grid.add(new Separator(), 0, row, 2, 1);
        row++;

        HBox bottomRow = new HBox(12, progressBar, compressBtn);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        grid.add(bottomRow, 0, row, 2, 1);

        VBox card = new VBox(grid);
        card.setPadding(new Insets(16));
        card.setStyle("""
                    -fx-background-color: #ffffff;
                    -fx-background-radius: 8;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 18, 0, 0, 4);
                """);
        card.setMaxWidth(Double.MAX_VALUE);

        StackPane root = new StackPane(card);
        StackPane.setMargin(card, new Insets(12));
        root.setPadding(new Insets(4));

        compressBtn.setOnAction(e -> {
            String src = sourceField.getText();
            String outDir = outputDirField.getText();
            String baseName = archiveNameField.getText();

            if (src == null || src.isBlank()) {
                showError("Validation error", "Please select or enter a source path.");
                return;
            }
            if (outDir == null || outDir.isBlank()) {
                showError("Validation error", "Please select or enter an output folder.");
                return;
            }
            if (baseName == null || baseName.isBlank()) {
                showError("Validation error", "Please enter archive name.");
                return;
            }

            String ext = getExtForFormatLabel(
                    formatCombo.getSelectionModel().getSelectedItem());

            String fileName = baseName.trim();
            fileName = stripKnownExt(fileName) + ext;

            Path sourcePath = Paths.get(src);
            Path outputDir = Paths.get(outDir);
            Path targetArchive = outputDir.resolve(fileName);

            compressBtn.setDisable(true);
            progressBar.setVisible(true);

            long startTime = System.currentTimeMillis();

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    Archiver archiver = createLoggingArchiver(targetArchive);
                    archiver.compress(sourcePath, targetArchive);
                    return null;
                }

                @Override
                protected void succeeded() {
                    runWithMinLoading(startTime, () -> {
                        compressBtn.setDisable(false);
                        progressBar.setVisible(false);
                        reloadLogs();
                        showInfo("Compression complete",
                                "Archive created:\n" + targetArchive);
                    });
                }

                @Override
                protected void failed() {
                    Throwable ex = getException();
                    runWithMinLoading(startTime, () -> {
                        compressBtn.setDisable(false);
                        progressBar.setVisible(false);
                        showError("Compression failed",
                                ex != null ? ex.getMessage() : "Unknown error");
                    });
                }
            };

            new Thread(task, "compress-thread").start();
        });

        Tab tab = new Tab("Compress", root);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildDecompressTab(Stage stage) {
        TextField archiveField = new TextField();
        archiveField.setPromptText("Select or paste archive path...");
        archiveField.setPrefHeight(34);

        Button browseArchiveBtn = new Button("Browse…");
        browseArchiveBtn.setPrefHeight(34);
        browseArchiveBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select archive");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Archives",
                            "*.zip", "*.tar.gz", "*.tgz", "*.rar", "*.7z", "*.tar", "*.ace"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                archiveField.setText(file.toPath().toString());
            }
        });

        HBox archiveRow = new HBox(8, archiveField, browseArchiveBtn);
        HBox.setHgrow(archiveField, Priority.ALWAYS);

        TextField targetDirField = new TextField();
        targetDirField.setPromptText("Select or paste output folder...");
        targetDirField.setPrefHeight(34);

        Button browseTargetBtn = new Button("Browse…");
        browseTargetBtn.setPrefHeight(34);
        browseTargetBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select output folder");
            File dir = chooser.showDialog(stage);
            if (dir != null) {
                targetDirField.setText(dir.toPath().toString());
            }
        });

        HBox targetRow = new HBox(8, targetDirField, browseTargetBtn);
        HBox.setHgrow(targetDirField, Priority.ALWAYS);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(260);
        progressBar.setPrefHeight(18);

        Button decompressBtn = new Button("Decompress");
        decompressBtn.setPrefHeight(36);
        decompressBtn.setStyle("""
                    -fx-padding: 6 22;
                    -fx-font-size: 15px;
                    -fx-background-color: #4a4a4a;
                    -fx-text-fill: #ffffff;
                """);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(18));
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(24);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(76);
        grid.getColumnConstraints().addAll(col1, col2);

        Label title = new Label("Extract archive");
        title.setStyle("-fx-text-fill: #202124; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Select an archive and destination folder for extraction.");
        subtitle.setStyle("-fx-text-fill: #5f6368;");

        Label archiveLabel = label("Archive:");
        Label outLabel = label("Output folder:");

        int row = 0;
        grid.add(title, 0, row, 2, 1);
        row++;
        grid.add(subtitle, 0, row, 2, 1);
        row++;
        grid.add(new Separator(), 0, row, 2, 1);
        row++;

        grid.add(archiveLabel, 0, row);
        grid.add(archiveRow, 1, row);
        row++;

        grid.add(outLabel, 0, row);
        grid.add(targetRow, 1, row);
        row++;

        grid.add(new Separator(), 0, row, 2, 1);
        row++;

        HBox bottomRow = new HBox(12, progressBar, decompressBtn);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        grid.add(bottomRow, 0, row, 2, 1);

        VBox card = new VBox(grid);
        card.setPadding(new Insets(16));
        card.setStyle("""
                    -fx-background-color: #ffffff;
                    -fx-background-radius: 8;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 18, 0, 0, 4);
                """);
        card.setMaxWidth(Double.MAX_VALUE);

        StackPane root = new StackPane(card);
        StackPane.setMargin(card, new Insets(12));
        root.setPadding(new Insets(4));

        decompressBtn.setOnAction(e -> {
            String arch = archiveField.getText();
            String tgt = targetDirField.getText();

            if (arch == null || arch.isBlank()) {
                showError("Validation error", "Please select or enter an archive path.");
                return;
            }
            if (tgt == null || tgt.isBlank()) {
                showError("Validation error", "Please select or enter an output folder.");
                return;
            }

            Path archive = Paths.get(arch);
            Path targetDir = Paths.get(tgt);

            decompressBtn.setDisable(true);
            progressBar.setVisible(true);

            long startTime = System.currentTimeMillis();

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    Archiver archiver = createLoggingArchiver(archive);
                    archiver.decompress(archive, targetDir);
                    return null;
                }

                @Override
                protected void succeeded() {
                    runWithMinLoading(startTime, () -> {
                        decompressBtn.setDisable(false);
                        progressBar.setVisible(false);
                        reloadLogs();
                        showInfo("Extraction complete",
                                "Archive extracted to:\n" + targetDir);
                    });
                }

                @Override
                protected void failed() {
                    Throwable ex = getException();
                    runWithMinLoading(startTime, () -> {
                        decompressBtn.setDisable(false);
                        progressBar.setVisible(false);
                        showError("Decompression failed",
                                ex != null ? ex.getMessage() : "Unknown error");
                    });
                }
            };

            new Thread(task, "decompress-thread").start();
        });

        Tab tab = new Tab("Decompress", root);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildLogsTab() {
        TableView<LogRow> table = new TableView<>(logRows);

        TableColumn<LogRow, String> tsCol = new TableColumn<>("Time");
        tsCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        TableColumn<LogRow, String> opCol = new TableColumn<>("Operation");
        opCol.setCellValueFactory(new PropertyValueFactory<>("operation"));

        TableColumn<LogRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        TableColumn<LogRow, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("archivePath"));

        TableColumn<LogRow, String> formatCol = new TableColumn<>("Format");
        formatCol.setCellValueFactory(new PropertyValueFactory<>("format"));

        TableColumn<LogRow, String> compCol = new TableColumn<>("Compression");
        compCol.setCellValueFactory(new PropertyValueFactory<>("compression"));

        TableColumn<LogRow, Long> sizeCol = new TableColumn<>("Size (bytes)");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("sizeBytes"));

        TableColumn<LogRow, Long> durCol = new TableColumn<>("Duration (ms)");
        durCol.setCellValueFactory(new PropertyValueFactory<>("durationMs"));

        table.getColumns().setAll(List.of(tsCol, opCol, statusCol, pathCol,
                formatCol, compCol, sizeCol, durCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setRowFactory(tv -> {
            TableRow<LogRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    showLogDetails(row.getItem());
                }
            });
            return row;
        });

        Button reloadBtn = new Button("Reload");
        reloadBtn.setStyle("-fx-font-size: 15px;");
        Label hint = new Label("Double-click a row to see details.");
        hint.setStyle("-fx-text-fill: #666666;");

        HBox topBar = new HBox(10, reloadBtn, hint);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 8, 0));

        VBox root = new VBox(8, topBar, table);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        reloadBtn.setOnAction(e -> reloadLogs());

        Tab tab = new Tab("Logs", root);
        tab.setClosable(false);
        return tab;
    }

    private void showLogDetails(LogRow row) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            boolean wasMax = primaryStage != null && primaryStage.isMaximized();

            if (primaryStage != null) {
                alert.initOwner(primaryStage);
                alert.initModality(Modality.WINDOW_MODAL);
            }
            alert.setTitle("Log entry details");
            alert.setHeaderText(null);

            DialogPane pane = alert.getDialogPane();
            pane.setMinWidth(640);
            pane.setMinHeight(360);
            pane.setStyle("-fx-font-size: 16px;");

            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(10);
            grid.setPadding(new Insets(10));

            int r = 0;

            Label timeLabel = boldLabel("Time:");
            String timeStr = "";
            if (row.getCreatedAtRaw() != null) {
                ZonedDateTime local = row.getCreatedAtRaw().atZoneSameInstant(ZoneId.systemDefault());
                timeStr = local.format(DETAIL_TIME_FORMAT);
            }
            Label timeValue = new Label(timeStr);
            grid.add(timeLabel, 0, r);
            grid.add(timeValue, 1, r);
            r++;

            Label opLabel = boldLabel("Operation:");
            Label opValue = new Label(row.getOperation());
            grid.add(opLabel, 0, r);
            grid.add(opValue, 1, r);
            r++;

            Label statusLabel = boldLabel("Status:");
            Label statusValue = new Label(row.getStatus());
            if ("SUCCESS".equalsIgnoreCase(row.getStatus())) {
                statusValue.setStyle("-fx-text-fill: #0f9d58; -fx-font-weight: bold;");
            } else if ("FAILED".equalsIgnoreCase(row.getStatus())) {
                statusValue.setStyle("-fx-text-fill: #d93025; -fx-font-weight: bold;");
            } else {
                statusValue.setStyle("-fx-font-weight: bold;");
            }
            grid.add(statusLabel, 0, r);
            grid.add(statusValue, 1, r);
            r++;

            Label durLabel = boldLabel("Duration:");
            Label durValue = new Label(row.getDurationMs() + " ms");
            grid.add(durLabel, 0, r);
            grid.add(durValue, 1, r);
            r++;

            Label pathLabel = boldLabel("Path:");
            Hyperlink pathLink = new Hyperlink(row.getArchivePath());
            pathLink.setOnAction(ev -> openInFileManager(row.getArchivePath()));
            pathLink.setMaxWidth(Double.MAX_VALUE);
            pathLink.setWrapText(true);
            grid.add(pathLabel, 0, r);
            grid.add(pathLink, 1, r);
            r++;

            Label formatLabel = boldLabel("Format:");
            Label formatValue = new Label(row.getFormat());
            grid.add(formatLabel, 0, r);
            grid.add(formatValue, 1, r);
            r++;

            Label compLabel = boldLabel("Compression:");
            Label compValue = new Label(row.getCompression());
            grid.add(compLabel, 0, r);
            grid.add(compValue, 1, r);
            r++;

            Label sizeLabel = boldLabel("Size:");
            Label sizeValue = new Label(row.getSizeBytes() + " bytes");
            grid.add(sizeLabel, 0, r);
            grid.add(sizeValue, 1, r);

            pane.setContent(grid);
            alert.showAndWait();

            if (primaryStage != null) {
                primaryStage.setMaximized(wasMax);
            }
        });
    }

    private Archiver createLoggingArchiver(Path archivePathOrTarget) {
        Archiver base = archiverFactory.getArchiver(archivePathOrTarget);
        return new ArchiverLogger(base, identityProvider, persistVisitor);
    }

    private void reloadLogs() {
        Task<List<ArchiveLog>> task = new Task<>() {
            @Override
            protected List<ArchiveLog> call() {
                AppUser user = identityProvider.resolveCurrentUser();
                return logQueryService.findRecentLogs(user.getId(), 200);
            }

            @Override
            protected void succeeded() {
                logRows.clear();

                for (ArchiveLog r : getValue()) {
                    OffsetDateTime raw = r.getCreatedAt();
                    String shortStr = "";
                    if (raw != null) {
                        ZonedDateTime local = raw.atZoneSameInstant(ZoneId.systemDefault());
                        shortStr = local.format(TABLE_TIME_FORMAT);
                    }

                    String op = r.getOperation();
                    String archivePath = r.getArchivePath();
                    String targetPath = r.getTargetPath();

                    String pathForUi;

                    if ("DECOMPRESS".equalsIgnoreCase(op)) {
                        if (targetPath != null && !targetPath.isBlank()) {
                            pathForUi = targetPath;
                        } else {
                            pathForUi = "";
                        }
                    } else {
                        pathForUi = (archivePath != null) ? archivePath : "";
                    }

                    logRows.add(new LogRow(
                            raw,
                            shortStr,
                            r.getOperation(),
                            r.getStatus(),
                            pathForUi,
                            r.getFormat(),
                            r.getCompression(),
                            r.getSizeBytes(),
                            r.getDurationMs()));
                }
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                showError("Failed to load logs",
                        ex != null ? ex.getMessage() : "Unknown error");
            }
        };

        new Thread(task, "load-logs-thread").start();
    }

    private void runWithMinLoading(long startTimeMillis, Runnable uiFinishAction) {
        long elapsed = System.currentTimeMillis() - startTimeMillis;
        long remaining = 2000 - elapsed;
        if (remaining <= 0) {
            uiFinishAction.run();
        } else {
            PauseTransition pt = new PauseTransition(Duration.millis(remaining));
            pt.setOnFinished(e -> uiFinishAction.run());
            pt.play();
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            boolean wasMax = primaryStage != null && primaryStage.isMaximized();

            if (primaryStage != null) {
                alert.initOwner(primaryStage);
                alert.initModality(Modality.WINDOW_MODAL);
            }
            alert.setTitle(title);
            alert.setHeaderText(null);

            DialogPane pane = alert.getDialogPane();
            pane.setMinWidth(460);
            pane.setMinHeight(220);
            pane.setStyle("-fx-font-size: 16px;");

            pane.setContent(new Label(message));
            alert.showAndWait();

            if (primaryStage != null) {
                primaryStage.setMaximized(wasMax);
            }
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            boolean wasMax = primaryStage != null && primaryStage.isMaximized();

            if (primaryStage != null) {
                alert.initOwner(primaryStage);
                alert.initModality(Modality.WINDOW_MODAL);
            }
            alert.setTitle(title);
            alert.setHeaderText(null);

            DialogPane pane = alert.getDialogPane();
            pane.setMinWidth(460);
            pane.setMinHeight(220);
            pane.setStyle("-fx-font-size: 16px;");

            pane.setContent(new Label(message));
            alert.showAndWait();

            if (primaryStage != null) {
                primaryStage.setMaximized(wasMax);
            }
        });
    }

    private void openInFileManager(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return;
        }

        Path path;
        try {
            path = Paths.get(pathString);
        } catch (Exception e) {
            showError("Open path failed", "Invalid path:\n" + pathString);
            return;
        }

        if (!Files.exists(path)) {
            showError("Open path failed", "Path does not exist:\n" + pathString);
            return;
        }

        if (!Desktop.isDesktopSupported()) {
            showError("Open path failed", "Desktop operations are not supported on this system.");
            return;
        }

        new Thread(() -> {
            try {
                Desktop desktop = Desktop.getDesktop();

                Path toOpen;
                if (Files.isDirectory(path)) {
                    toOpen = path;
                } else {
                    Path parent = path.getParent();
                    toOpen = (parent != null && Files.exists(parent)) ? parent : path;
                }

                desktop.open(toOpen.toFile());
            } catch (Exception e) {
                showError("Open path failed",
                        "Cannot open path:\n" + pathString + "\n\n" + e.getMessage());
            }
        }, "open-path-thread").start();
    }

    private Label label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #202124;");
        return l;
    }

    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: #202124;");
        return l;
    }

    private String getExtForFormatLabel(String label) {
        if (label == null)
            return ".zip";
        if (label.startsWith("ZIP"))
            return ".zip";
        if (label.startsWith("TAR"))
            return ".tar.gz";
        if (label.startsWith("RAR"))
            return ".rar";
        return ".zip";
    }

    private void adjustArchiveNameForFormat(TextField field, String selectedFormat) {
        if (selectedFormat == null) {
            return;
        }
        String ext = getExtForFormatLabel(selectedFormat);

        String text = field.getText();
        if (text == null || text.isBlank()) {
            field.setPromptText("beowulf" + ext);
            return;
        }

        String trimmed = text.trim();
        String base = stripKnownExt(trimmed);
        String withExt = base + ext;
        field.setText(withExt);
        field.setPromptText("beowulf" + ext);
    }

    private String stripKnownExt(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String[] exts = { ".tar.gz", ".zip", ".rar" };
        for (String e : exts) {
            if (lower.endsWith(e)) {
                return name.substring(0, name.length() - e.length());
            }
        }
        return name;
    }

    @Override
    public void stop() {
        DataSourceFactory.close();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class LogRow {
        private final OffsetDateTime createdAtRaw;
        private final SimpleStringProperty createdAt;
        private final SimpleStringProperty operation;
        private final SimpleStringProperty status;
        private final SimpleStringProperty archivePath;
        private final SimpleStringProperty format;
        private final SimpleStringProperty compression;
        private final SimpleLongProperty sizeBytes;
        private final SimpleLongProperty durationMs;

        public LogRow(OffsetDateTime createdAtRaw,
                String createdAtFormatted,
                String operation,
                String status,
                String archivePath,
                String format,
                String compression,
                long sizeBytes,
                long durationMs) {
            this.createdAtRaw = createdAtRaw;
            this.createdAt = new SimpleStringProperty(createdAtFormatted);
            this.operation = new SimpleStringProperty(operation);
            this.status = new SimpleStringProperty(status);
            this.archivePath = new SimpleStringProperty(archivePath);
            this.format = new SimpleStringProperty(format);
            this.compression = new SimpleStringProperty(compression);
            this.sizeBytes = new SimpleLongProperty(sizeBytes);
            this.durationMs = new SimpleLongProperty(durationMs);
        }

        public OffsetDateTime getCreatedAtRaw() {
            return createdAtRaw;
        }

        public String getCreatedAt() {
            return createdAt.get();
        }

        public String getOperation() {
            return operation.get();
        }

        public String getStatus() {
            return status.get();
        }

        public String getArchivePath() {
            return archivePath.get();
        }

        public String getFormat() {
            return format.get();
        }

        public String getCompression() {
            return compression.get();
        }

        public long getSizeBytes() {
            return sizeBytes.get();
        }

        public long getDurationMs() {
            return durationMs.get();
        }
    }
}
