package com.beowulf.gui;

import com.beowulf.core.db.DataSourceFactory;
import com.beowulf.core.db.DbMigrations;
import com.beowulf.core.facade.ArchiveEditService;
import com.beowulf.core.facade.ArchiveLogService;
import com.beowulf.core.facade.ArchivePersistenceService;
import com.beowulf.core.factory.ArchiverFactory;
import com.beowulf.core.interfaces.Archiver;
import com.beowulf.core.model.ArchiveLog;
import com.beowulf.core.model.ArchiveVisitor;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.AppUserService;
import com.beowulf.core.visitor.ArchiverLogger;
import com.beowulf.core.model.ArchivePart;

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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.UUID;

public class BeowulfGUI extends Application {

    private AppUserService identityProvider;
    private ArchivePersistenceService persistenceService;
    private ArchiveLogService logQueryService;
    private ArchiveVisitor persistVisitor;
    private ArchiverFactory archiverFactory;
    private ArchiveEditService editService;

    private Stage primaryStage;

    private final ObservableList<LogRow> logRows = FXCollections.observableArrayList();

    private static final DateTimeFormatter TABLE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss yyyy-MM-dd");
    private static final DateTimeFormatter DETAIL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd MMMM yyyy",
            Locale.ENGLISH);

    private TextField editArchiveField;
    private TreeView<FileNode> editTreeView;
    private ProgressBar editProgressBar;
    private Label editStatusLabel;

    private EditSession currentSession;

    private static final DataFormat EDIT_INTERNAL_MOVE = new DataFormat("beowulf/edit-internal-move");
    private Path dragSourcePath;

    private static final String[] ARCHIVE_EXTENSIONS = {
            ".zip", ".tar.gz", ".tgz", ".rar", ".7z", ".tar", ".ace"
    };

    private Image folderClosedIcon;
    private Image folderOpenIcon;
    private Image fileIcon;
    private Image archiveIcon;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        initCore();
        loadIcons();

        TabPane tabPane = new TabPane();
        tabPane.setTabMinWidth(130);

        tabPane.getTabs().addAll(
                buildCompressTab(primaryStage),
                buildDecompressTab(primaryStage),
                buildEditTab(primaryStage),
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
        this.editService = new ArchiveEditService(archiverFactory, identityProvider, persistVisitor);
    }

    private void loadIcons() {
        folderClosedIcon = loadIcon("/icons/folder-closed.png");
        folderOpenIcon = loadIcon("/icons/folder-open.png");
        fileIcon = loadIcon("/icons/file.png");
        archiveIcon = loadIcon("/icons/archive.png");
    }

    private Image loadIcon(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ImageView createIconView(Image img) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(16);
        iv.setFitHeight(16);
        return iv;
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

        formatCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> adjustArchiveNameForFormat(archiveNameField, newVal));

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

    private Tab buildEditTab(Stage stage) {
        editArchiveField = new TextField();
        editArchiveField.setPromptText("Select or paste archive to edit...");
        editArchiveField.setPrefHeight(34);

        Button browseBtn = new Button("Browse…");
        browseBtn.setPrefHeight(34);
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select archive to edit");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Archives",
                            "*.zip", "*.tar.gz", "*.tgz", "*.rar", "*.7z", "*.tar", "*.ace"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                editArchiveField.setText(file.toPath().toString());
                loadRootArchiveForEdit(file.toPath());
            }
        });

        editArchiveField.setOnAction(e -> {
            String text = editArchiveField.getText();
            if (text != null && !text.isBlank()) {
                loadRootArchiveForEdit(Paths.get(text.trim()));
            }
        });

        HBox archiveRow = new HBox(8, editArchiveField, browseBtn);
        HBox.setHgrow(editArchiveField, Priority.ALWAYS);

        editTreeView = new TreeView<>();
        editTreeView.setShowRoot(true);
        editTreeView.setContextMenu(createEditContextMenuForEmptySpace());

        editTreeView.setCellFactory(tv -> {
            TreeCell<FileNode> cell = new TreeCell<>() {
                @Override
                protected void updateItem(FileNode item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        Path p = item.getPath();
                        String name = item.getName();
                        setText(name);

                        Node graphic = null;
                        TreeItem<FileNode> ti = getTreeItem();
                        boolean isRoot = ti != null && ti.getParent() == null;

                        if (isRoot) {
                            if (archiveIcon != null) {
                                graphic = createIconView(archiveIcon);
                            }
                        } else if (item.isDirectory()) {
                            boolean expanded = ti != null && ti.isExpanded();
                            Image iconImg = null;
                            if (expanded && folderOpenIcon != null) {
                                iconImg = folderOpenIcon;
                            } else if (folderClosedIcon != null) {
                                iconImg = folderClosedIcon;
                            }
                            if (iconImg != null) {
                                graphic = createIconView(iconImg);
                            }
                        } else if (isArchiveFile(p)) {
                            if (archiveIcon != null) {
                                graphic = createIconView(archiveIcon);
                            }
                        } else {
                            if (fileIcon != null) {
                                graphic = createIconView(fileIcon);
                            }
                        }

                        setGraphic(graphic);
                        setStyle("");
                    }
                }
            };

            cell.setOnContextMenuRequested(e -> {
                if (!cell.isEmpty()) {
                    editTreeView.getSelectionModel().select(cell.getTreeItem());
                }
            });

            cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(createEditContextMenu());
                }
            });

            cell.setOnMouseClicked(e -> {
                if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) {
                    return;
                }
                if (cell.isEmpty()) {
                    return;
                }
                FileNode node = cell.getItem();
                Path path = node.getPath();
                if (currentSession == null) {
                    return;
                }

                if (node.isDirectory() && path.equals(currentSession.workDir)) {
                    handleEditRename();
                } else if (node.isDirectory()) {
                    handleEditRename();
                } else if (isArchiveFile(path)) {
                    openNestedArchive(path, node.getName());
                } else {
                    openFileWithDesktop(path);
                }
            });

            cell.setOnDragDetected(e -> {
                if (cell.isEmpty() || currentSession == null || !currentSession.canSave) {
                    return;
                }
                FileNode node = cell.getItem();
                Path path = node.getPath();
                if (currentSession.workDir != null && path.equals(currentSession.workDir)) {
                    return;
                }

                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.put(EDIT_INTERNAL_MOVE, path.toString());
                db.setContent(content);

                dragSourcePath = path;
                e.consume();
            });

            cell.setOnDragOver(e -> {
                if (currentSession == null || !currentSession.canSave) {
                    return;
                }
                Dragboard db = e.getDragboard();
                if (db.hasContent(EDIT_INTERNAL_MOVE) || db.hasFiles()) {
                    e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    Scene scene = cell.getScene();
                    if (scene != null) {
                        scene.setCursor(Cursor.HAND);
                    }
                }
                e.consume();
            });

            cell.setOnDragExited(e -> {
                Scene scene = cell.getScene();
                if (scene != null) {
                    scene.setCursor(Cursor.DEFAULT);
                }
            });

            cell.setOnDragDropped(e -> {
                if (currentSession == null || !currentSession.canSave) {
                    e.setDropCompleted(false);
                    e.consume();
                    return;
                }

                Dragboard db = e.getDragboard();
                boolean success = false;

                TreeItem<FileNode> targetItem = cell.getTreeItem();
                Path targetDir = resolveDropTargetDirectory(targetItem);

                try {
                    if (db.hasContent(EDIT_INTERNAL_MOVE) && dragSourcePath != null) {
                        if (!dragSourcePath.startsWith(currentSession.workDir)) {
                            e.setDropCompleted(false);
                            e.consume();
                            return;
                        }
                        if (!dragSourcePath.equals(targetDir)) {
                            movePathIntoDirectory(dragSourcePath, targetDir);
                            success = true;
                        }
                    } else if (db.hasFiles()) {
                        for (File f : db.getFiles()) {
                            Path src = f.toPath();
                            if (Files.isDirectory(src)) {
                                Path dest = targetDir.resolve(src.getFileName().toString());
                                copyRecursive(src, dest);
                            } else {
                                Files.copy(src, targetDir.resolve(src.getFileName().toString()),
                                        StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                        success = true;
                    }
                } catch (IOException ex) {
                    showError("Drag-and-drop failed", ex.getMessage());
                }

                if (success) {
                    rebuildEditTree();
                    scheduleRepackAfterEdit("Drag-and-drop edit");
                }

                e.setDropCompleted(success);
                e.consume();
            });

            return cell;
        });

        editTreeView.setOnDragOver(e -> {
            if (currentSession == null || !currentSession.canSave || currentSession.workDir == null) {
                return;
            }
            Node node = e.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof TreeCell)) {
                node = node.getParent();
            }
            if (node != null) {
                return;
            }

            Dragboard db = e.getDragboard();
            if (db.hasContent(EDIT_INTERNAL_MOVE) || db.hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                Scene scene = editTreeView.getScene();
                if (scene != null) {
                    scene.setCursor(Cursor.HAND);
                }
            }
            e.consume();
        });

        editTreeView.setOnDragDropped(e -> {
            if (currentSession == null || !currentSession.canSave || currentSession.workDir == null) {
                e.setDropCompleted(false);
                e.consume();
                return;
            }

            Node node = e.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof TreeCell)) {
                node = node.getParent();
            }
            if (node != null) {
                return;
            }

            Dragboard db = e.getDragboard();
            boolean success = false;
            Path targetDir = currentSession.workDir;

            try {
                if (db.hasContent(EDIT_INTERNAL_MOVE) && dragSourcePath != null) {
                    if (!dragSourcePath.startsWith(targetDir)) {
                        e.setDropCompleted(false);
                        e.consume();
                        return;
                    }
                    if (!dragSourcePath.equals(targetDir)) {
                        movePathIntoDirectory(dragSourcePath, targetDir);
                        success = true;
                    }
                } else if (db.hasFiles()) {
                    for (File f : db.getFiles()) {
                        Path src = f.toPath();
                        if (Files.isDirectory(src)) {
                            Path dest = targetDir.resolve(src.getFileName().toString());
                            copyRecursive(src, dest);
                        } else {
                            Files.copy(src, targetDir.resolve(src.getFileName().toString()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    success = true;
                }
            } catch (IOException ex) {
                showError("Drag-and-drop failed", ex.getMessage());
            }

            if (success) {
                rebuildEditTree();
                scheduleRepackAfterEdit("Drag-and-drop edit");
            }

            e.setDropCompleted(success);
            e.consume();
        });

        editTreeView.setOnDragExited(e -> {
            Scene scene = editTreeView.getScene();
            if (scene != null) {
                scene.setCursor(Cursor.DEFAULT);
            }
        });

        editTreeView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                handleEditDelete();
                event.consume();
            } else if (event.getCode() == KeyCode.F2) {
                handleEditRename();
                event.consume();
            }
        });

        Label nameLabel = boldLabel("Name:");
        Label pathLabel = boldLabel("Path:");
        Label sizeLabel = boldLabel("Size:");
        Label modifiedLabel = boldLabel("Modified:");

        Label nameValue = new Label("-");
        Label pathValue = new Label("-");
        Label sizeValue = new Label("-");
        Label modifiedValue = new Label("-");

        nameValue.setWrapText(true);
        pathValue.setWrapText(true);

        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(10);
        detailsGrid.setVgap(8);
        detailsGrid.setPadding(new Insets(10));

        ColumnConstraints dc1 = new ColumnConstraints();
        dc1.setMinWidth(70);
        dc1.setPrefWidth(80);
        ColumnConstraints dc2 = new ColumnConstraints();
        dc2.setHgrow(Priority.ALWAYS);
        detailsGrid.getColumnConstraints().addAll(dc1, dc2);

        int dr = 0;
        detailsGrid.add(nameLabel, 0, dr);
        detailsGrid.add(nameValue, 1, dr);
        dr++;
        detailsGrid.add(pathLabel, 0, dr);
        detailsGrid.add(pathValue, 1, dr);
        dr++;
        detailsGrid.add(sizeLabel, 0, dr);
        detailsGrid.add(sizeValue, 1, dr);
        dr++;
        detailsGrid.add(modifiedLabel, 0, dr);
        detailsGrid.add(modifiedValue, 1, dr);

        editTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.getValue() == null || currentSession == null) {
                nameValue.setText("-");
                pathValue.setText("-");
                sizeValue.setText("-");
                modifiedValue.setText("-");
            } else {
                Path p = newVal.getValue().getPath();
                nameValue.setText(newVal.getValue().getName());

                try {
                    if (Files.exists(p)) {
                        Path base = currentSession.workDir;
                        if (base != null && p.startsWith(base)) {
                            Path rel = base.relativize(p);
                            pathValue.setText(rel.toString());
                        } else {
                            pathValue.setText(p.toAbsolutePath().toString());
                        }

                        if (Files.isDirectory(p)) {
                            try (Stream<Path> s = Files.list(p)) {
                                long count = s.count();
                                sizeValue.setText(count + " item(s)");
                            }
                        } else {
                            long size = Files.size(p);
                            sizeValue.setText(size + " bytes");
                        }
                        modifiedValue.setText(
                                Files.getLastModifiedTime(p).toInstant()
                                        .atZone(ZoneId.systemDefault())
                                        .format(TABLE_TIME_FORMAT));
                    } else {
                        pathValue.setText(p.toAbsolutePath().toString());
                        sizeValue.setText("N/A");
                        modifiedValue.setText("N/A");
                    }
                } catch (IOException ex) {
                    sizeValue.setText("N/A");
                    modifiedValue.setText("N/A");
                }
            }
        });

        SplitPane centerPane = new SplitPane();
        centerPane.setDividerPositions(0.45);
        centerPane.getItems().addAll(editTreeView, detailsGrid);

        editProgressBar = new ProgressBar();
        editProgressBar.setVisible(false);
        editProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        editProgressBar.setPrefWidth(260);
        editProgressBar.setPrefHeight(18);

        editStatusLabel = new Label();
        editStatusLabel.setStyle("-fx-text-fill: #5f6368;");

        HBox bottomBar = new HBox(12, editProgressBar, editStatusLabel);
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(18));
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints ec1 = new ColumnConstraints();
        ec1.setPercentWidth(24);
        ColumnConstraints ec2 = new ColumnConstraints();
        ec2.setPercentWidth(76);
        grid.getColumnConstraints().addAll(ec1, ec2);

        Label title = new Label("Edit archive");
        title.setStyle("-fx-text-fill: #202124; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Browse and modify archive contents.");
        subtitle.setStyle("-fx-text-fill: #5f6368;");

        int row = 0;
        grid.add(title, 0, row, 2, 1);
        row++;
        grid.add(subtitle, 0, row, 2, 1);
        row++;
        grid.add(new Separator(), 0, row, 2, 1);
        row++;

        grid.add(label("Archive:"), 0, row);
        grid.add(archiveRow, 1, row);
        row++;

        grid.add(new Separator(), 0, row, 2, 1);
        row++;

        grid.add(centerPane, 0, row, 2, 1);
        GridPane.setVgrow(centerPane, Priority.ALWAYS);
        row++;

        grid.add(new Separator(), 0, row, 2, 1);
        row++;

        grid.add(bottomBar, 0, row, 2, 1);

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

        Tab tab = new Tab("Edit", root);
        tab.setClosable(false);
        return tab;
    }

    private boolean isArchiveFile(Path path) {
        if (path == null)
            return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : ARCHIVE_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private ContextMenu createEditContextMenu() {
        MenuItem newFolder = new MenuItem("New folder…");
        newFolder.setOnAction(e -> handleEditNewFolder());

        MenuItem addFile = new MenuItem("Add file…");
        addFile.setOnAction(e -> handleEditAddFile());

        MenuItem rename = new MenuItem("Rename…");
        rename.setOnAction(e -> handleEditRename());

        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> handleEditDelete());

        return new ContextMenu(newFolder, addFile, rename, delete);
    }

    private ContextMenu createEditContextMenuForEmptySpace() {
        MenuItem newFolder = new MenuItem("New folder…");
        newFolder.setOnAction(e -> handleEditNewFolder());

        MenuItem addFile = new MenuItem("Add file…");
        addFile.setOnAction(e -> handleEditAddFile());

        return new ContextMenu(newFolder, addFile);
    }

    private void loadRootArchiveForEdit(Path archivePath) {
        if (archivePath == null) {
            return;
        }
        if (!Files.exists(archivePath)) {
            showError("Archive not found", "File does not exist:\n" + archivePath);
            return;
        }

        closeAllEditSessions();

        String lower = archivePath.toString().toLowerCase(Locale.ROOT);
        boolean canSave = !lower.endsWith(".ace");

        EditSession root = new EditSession();
        root.archiveFile = archivePath;
        root.displayPath = archivePath.toString();
        root.canSave = canSave;
        root.parent = null;

        try {
            root.workDir = Files.createTempDirectory("beowulf-edit-");
        } catch (IOException e) {
            showError("Edit failed", "Cannot create temp directory:\n" + e.getMessage());
            return;
        }

        currentSession = root;
        editArchiveField.setText(root.displayPath);

        editProgressBar.setVisible(true);
        editStatusLabel.setText("");
        editTreeView.setDisable(true);
        editArchiveField.setDisable(true);

        long startTime = System.currentTimeMillis();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Archiver archiver = createLoggingArchiver(archivePath);
                archiver.decompress(archivePath, root.workDir);
                return null;
            }

            @Override
            protected void succeeded() {
                runWithMinLoading(startTime, () -> {
                    editProgressBar.setVisible(false);
                    editStatusLabel.setText("");
                    editArchiveField.setDisable(false);
                    editTreeView.setDisable(false);
                    rebuildEditTree();
                });
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                runWithMinLoading(startTime, () -> {
                    editProgressBar.setVisible(false);
                    editArchiveField.setDisable(false);
                    editTreeView.setDisable(false);
                    editStatusLabel.setText("");
                    showError("Failed to load archive",
                            ex != null ? ex.getMessage() : "Unknown error");
                });
            }
        };

        new Thread(task, "edit-load-archive").start();
    }

    private void openNestedArchive(Path nestedArchive, String name) {
        if (currentSession == null) {
            return;
        }
        if (!Files.exists(nestedArchive)) {
            showError("Archive not found", "File does not exist:\n" + nestedArchive);
            return;
        }

        String lower = nestedArchive.toString().toLowerCase(Locale.ROOT);
        boolean canSave = !lower.endsWith(".ace");

        EditSession parent = currentSession;

        EditSession nested = new EditSession();
        nested.archiveFile = nestedArchive;
        nested.displayPath = parent.displayPath + " » " + name;
        nested.canSave = canSave;
        nested.parent = parent;

        try {
            nested.workDir = Files.createTempDirectory("beowulf-edit-");
        } catch (IOException e) {
            showError("Edit failed", "Cannot create temp directory:\n" + e.getMessage());
            return;
        }

        currentSession = nested;
        editArchiveField.setText(nested.displayPath);

        editProgressBar.setVisible(true);
        editStatusLabel.setText("");
        editTreeView.setDisable(true);
        editArchiveField.setDisable(true);

        long startTime = System.currentTimeMillis();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Archiver archiver = createLoggingArchiver(nestedArchive);
                archiver.decompress(nestedArchive, nested.workDir);
                return null;
            }

            @Override
            protected void succeeded() {
                runWithMinLoading(startTime, () -> {
                    editProgressBar.setVisible(false);
                    editStatusLabel.setText("");
                    editArchiveField.setDisable(false);
                    editTreeView.setDisable(false);
                    rebuildEditTree();
                });
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                runWithMinLoading(startTime, () -> {
                    editProgressBar.setVisible(false);
                    editArchiveField.setDisable(false);
                    editTreeView.setDisable(false);
                    editStatusLabel.setText("");
                    showError("Failed to load nested archive",
                            ex != null ? ex.getMessage() : "Unknown error");
                });
            }
        };

        new Thread(task, "edit-load-nested").start();
    }

    private void rebuildEditTree() {
        if (editTreeView == null || currentSession == null || currentSession.workDir == null) {
            return;
        }

        try {
            Path rootDir = currentSession.workDir;
            String rootName = currentSession.archiveFile != null
                    ? currentSession.archiveFile.getFileName().toString()
                    : rootDir.getFileName().toString();

            FileNode rootNode = new FileNode(rootDir, rootName, true);
            TreeItem<FileNode> rootItem = new TreeItem<>(rootNode);
            rootItem.setExpanded(true);
            populateTreeChildren(rootItem);
            editTreeView.setRoot(rootItem);
        } catch (IOException ex) {
            showError("Failed to build tree", ex.getMessage());
        }
    }

    private void populateTreeChildren(TreeItem<FileNode> parent) throws IOException {
        Path dir = parent.getValue().getPath();
        if (!Files.isDirectory(dir))
            return;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.sorted((p1, p2) -> {
                try {
                    boolean d1 = Files.isDirectory(p1);
                    boolean d2 = Files.isDirectory(p2);
                    if (d1 != d2) {
                        return d1 ? -1 : 1;
                    }
                } finally {
                }
                return p1.getFileName().toString().compareToIgnoreCase(
                        p2.getFileName().toString());
            }).forEach(childPath -> {
                boolean isDir = Files.isDirectory(childPath);
                FileNode node = new FileNode(
                        childPath,
                        childPath.getFileName().toString(),
                        isDir);
                TreeItem<FileNode> item = new TreeItem<>(node);
                parent.getChildren().add(item);
                if (isDir) {
                    try {
                        populateTreeChildren(item);
                    } catch (IOException ignored) {
                    }
                }
            });
        }
    }

    private Path resolveDropTargetDirectory(TreeItem<FileNode> targetItem) {
        if (currentSession == null || currentSession.workDir == null) {
            return null;
        }
        if (targetItem == null || targetItem.getValue() == null) {
            return currentSession.workDir;
        }

        Path p = targetItem.getValue().getPath();
        if (Files.isDirectory(p)) {
            return p;
        }
        Path parent = p.getParent();
        return parent != null ? parent : currentSession.workDir;
    }

    private void movePathIntoDirectory(Path source, Path targetDir) throws IOException {
        if (!Files.exists(source) || targetDir == null) {
            return;
        }
        if (!Files.isDirectory(targetDir)) {
            return;
        }
        if (targetDir.startsWith(source)) {
            return;
        }

        Path dest = targetDir.resolve(source.getFileName().toString());
        Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path resolveTargetDirForSelection() {
        if (currentSession == null || currentSession.workDir == null)
            return null;

        TreeItem<FileNode> selected = editTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            return currentSession.workDir;
        }

        Path p = selected.getValue().getPath();
        if (Files.isDirectory(p)) {
            return p;
        }
        Path parent = p.getParent();
        return parent != null ? parent : currentSession.workDir;
    }

    private void handleEditNewFolder() {
        if (currentSession == null || !currentSession.canSave) {
            showError("Editing not supported",
                    "This archive format is read-only. You can only view contents.");
            return;
        }
        if (currentSession.workDir == null) {
            showError("No archive loaded", "Load an archive before editing.");
            return;
        }

        Path targetDir = resolveTargetDirForSelection();
        if (targetDir == null)
            return;

        TextInputDialog dialog = new TextInputDialog("NewFolder");
        dialog.setTitle("New folder");
        dialog.setHeaderText("Create a folder inside archive");
        dialog.setContentText("Folder name:");
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty())
                return;

            Path newDir = targetDir.resolve(trimmed);
            try {
                Files.createDirectories(newDir);
                rebuildEditTree();
                scheduleRepackAfterEdit("Create folder: " + trimmed);
            } catch (IOException ex) {
                showError("Failed to create folder", ex.getMessage());
            }
        });
    }

    private void handleEditAddFile() {
        if (currentSession == null || !currentSession.canSave) {
            showError("Editing not supported",
                    "This archive format is read-only. You can only view contents.");
            return;
        }
        if (currentSession.workDir == null) {
            showError("No archive loaded", "Load an archive before editing.");
            return;
        }

        Path targetDir = resolveTargetDirForSelection();
        if (targetDir == null)
            return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file(s) to add");
        List<File> files = chooser.showOpenMultipleDialog(primaryStage);
        if (files == null || files.isEmpty())
            return;

        boolean changed = false;
        for (File f : files) {
            Path src = f.toPath();
            try {
                if (Files.isDirectory(src)) {
                    Path dest = targetDir.resolve(src.getFileName().toString());
                    copyRecursive(src, dest);
                } else {
                    Files.copy(src, targetDir.resolve(src.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                changed = true;
            } catch (IOException ex) {
                showError("Add file failed", ex.getMessage());
            }
        }

        if (changed) {
            rebuildEditTree();
            scheduleRepackAfterEdit("Add files");
        }
    }

    private void handleEditRename() {
        if (currentSession == null || !currentSession.canSave) {
            showError("Editing not supported",
                    "This archive format is read-only. You can only view contents.");
            return;
        }
        if (currentSession.workDir == null) {
            showError("No archive loaded", "Load an archive before editing.");
            return;
        }

        TreeItem<FileNode> selected = editTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            return;
        }

        FileNode node = selected.getValue();
        Path path = node.getPath();

        boolean isRoot = path.equals(currentSession.workDir);

        if (isRoot) {
            Path archiveFile = currentSession.archiveFile;
            if (archiveFile == null)
                return;

            TextInputDialog dialog = new TextInputDialog(archiveFile.getFileName().toString());
            dialog.setTitle("Rename archive");
            dialog.setHeaderText("Rename archive file");
            dialog.setContentText("New name:");
            if (primaryStage != null) {
                dialog.initOwner(primaryStage);
                dialog.initModality(Modality.WINDOW_MODAL);
            }

            dialog.showAndWait().ifPresent(name -> {
                String trimmed = name == null ? "" : name.trim();
                if (trimmed.isEmpty() || trimmed.equals(archiveFile.getFileName().toString()))
                    return;

                Path target = archiveFile.resolveSibling(trimmed);
                try {
                    Files.move(archiveFile, target, StandardCopyOption.REPLACE_EXISTING);
                    currentSession.archiveFile = target;

                    if (currentSession.parent != null) {
                        currentSession.displayPath = currentSession.parent.displayPath + " » " + trimmed;
                    } else {
                        currentSession.displayPath = target.toString();
                    }
                    editArchiveField.setText(currentSession.displayPath);
                    rebuildEditTree();
                    scheduleRepackAfterEdit("Rename archive file");
                } catch (IOException ex) {
                    showError("Rename failed", ex.getMessage());
                }
            });

            return;
        }

        TextInputDialog dialog = new TextInputDialog(node.getName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename file or folder");
        dialog.setContentText("New name:");
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty() || trimmed.equals(node.getName()))
                return;

            Path target = path.resolveSibling(trimmed);
            try {
                Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
                rebuildEditTree();
                scheduleRepackAfterEdit("Rename " + node.getName() + " -> " + trimmed);
            } catch (IOException ex) {
                showError("Rename failed", ex.getMessage());
            }
        });
    }

    private void handleEditDelete() {
        if (currentSession == null || !currentSession.canSave) {
            showError("Editing not supported",
                    "This archive format is read-only. You can only view contents.");
            return;
        }
        if (currentSession.workDir == null) {
            showError("No archive loaded", "Load an archive before editing.");
            return;
        }

        TreeItem<FileNode> selected = editTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            return;
        }

        FileNode node = selected.getValue();
        Path path = node.getPath();
        if (path.equals(currentSession.workDir)) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete '" + node.getName() + "' from archive?");
        if (primaryStage != null) {
            confirm.initOwner(primaryStage);
            confirm.initModality(Modality.WINDOW_MODAL);
        }

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    deleteRecursive(path);
                    rebuildEditTree();
                    scheduleRepackAfterEdit("Delete " + node.getName());
                } catch (IOException ex) {
                    showError("Delete failed", ex.getMessage());
                }
            }
        });
    }

    private void scheduleRepackAfterEdit(String reason) {
        if (currentSession == null || !currentSession.canSave) {
            return;
        }
        if (currentSession.workDir == null || currentSession.archiveFile == null) {
            return;
        }

        EditSession sessionToSaveFrom = currentSession;

        editProgressBar.setVisible(true);
        editStatusLabel.setText("");
        editTreeView.setDisable(true);
        editArchiveField.setDisable(true);

        long startTime = System.currentTimeMillis();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                EditSession s = sessionToSaveFrom;
                while (s != null) {
                    editService.saveArchive(s.workDir, s.archiveFile);
                    s = s.parent;
                }
                return null;
            }

            @Override
            protected void succeeded() {
                runWithMinLoading(startTime, () -> {
                    editProgressBar.setVisible(false);
                    editStatusLabel.setText("");
                    editArchiveField.setDisable(false);
                    editTreeView.setDisable(false);
                    reloadLogs();
                });
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                runWithMinLoading(startTime, () -> {
                    editProgressBar.setVisible(false);
                    editArchiveField.setDisable(false);
                    editTreeView.setDisable(false);
                    editStatusLabel.setText("");
                    showError("Failed to save changes",
                            ex != null ? ex.getMessage() : "Unknown error");
                });
            }
        };

        new Thread(task, "edit-repack-archive").start();
    }

    private void closeAllEditSessions() {
        EditSession s = currentSession;
        while (s != null) {
            Path wd = s.workDir;
            if (wd != null && Files.exists(wd)) {
                try (Stream<Path> walk = Files.walk(wd)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                } catch (IOException ignored) {
                }
            }
            s = s.parent;
        }
        currentSession = null;
    }

    private void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    private void copyRecursive(Path src, Path dest) throws IOException {
        if (Files.isDirectory(src)) {
            try (Stream<Path> walk = Files.walk(src)) {
                walk.forEach(p -> {
                    Path relative = src.relativize(p);
                    Path target = dest.resolve(relative.toString());
                    try {
                        if (Files.isDirectory(p)) {
                            Files.createDirectories(target);
                        } else {
                            Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException ignored) {
                    }
                });
            }
        } else {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void openFileWithDesktop(Path path) {
        if (path == null || !Files.exists(path)) {
            showError("Open file failed", "File does not exist:\n" + path);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            showError("Open file failed", "Desktop operations are not supported on this system.");
            return;
        }

        new Thread(() -> {
            try {
                Desktop.getDesktop().open(path.toFile());
            } catch (Exception e) {
                showError("Open file failed",
                        "Cannot open file:\n" + path + "\n\n" + e.getMessage());
            }
        }, "open-file-thread").start();
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
        List<ArchivePart> parts = List.of();
        RuntimeException partsError = null;

        if (row.getSplitParts() > 1 && row.getArchiveId() != null) {
            try {
                parts = logQueryService.findArchiveParts(row.getArchiveId());
            } catch (RuntimeException ex) {
                partsError = ex;
            }
        }

        final List<ArchivePart> partsForUi = parts;
        final RuntimeException partsErrorFinal = partsError;

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
            String sizeText = row.getSizeBytes() + " bytes";
            if (row.getSizeBytes() > 0) {
                sizeText += " (" + humanReadableBytes(row.getSizeBytes()) + ")";
            }
            Label sizeValue = new Label(sizeText);
            grid.add(sizeLabel, 0, r);
            grid.add(sizeValue, 1, r);
            r++;

            Label splitLabel = boldLabel("Split archive:");

            if (row.getSplitParts() <= 1) {
                Label splitValue = new Label("No (single file)");
                grid.add(splitLabel, 0, r);
                grid.add(splitValue, 1, r);
            } else {
                VBox splitBox = new VBox(4);

                long partsCount = row.getSplitParts();
                long totalSize = row.getSplitTotalSize();
                long approxPerPart = (partsCount > 0 && totalSize > 0) ? totalSize / partsCount : 0;

                StringBuilder headerSb = new StringBuilder();
                headerSb.append(partsCount).append(" part(s)");
                if (totalSize > 0) {
                    headerSb.append(", total ").append(totalSize)
                            .append(" bytes (").append(humanReadableBytes(totalSize)).append(")");
                }
                if (approxPerPart > 0) {
                    headerSb.append(", ~").append(humanReadableBytes(approxPerPart)).append(" each");
                }

                Label headerLabel = new Label(headerSb.toString());
                headerLabel.setStyle("-fx-font-weight: bold;");
                headerLabel.setWrapText(true);
                splitBox.getChildren().add(headerLabel);

                if (partsErrorFinal != null) {
                    Label errorLabel = new Label("Failed to load part list: " + partsErrorFinal.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #d93025;");
                    errorLabel.setWrapText(true);
                    splitBox.getChildren().add(errorLabel);
                } else if (partsForUi != null && !partsForUi.isEmpty()) {
                    for (ArchivePart p : partsForUi) {
                        String line = String.format(
                                Locale.ENGLISH,
                                "Part %d: %s\n  %d bytes (%s)",
                                p.getPartIndex(),
                                p.getPath(),
                                p.getSizeBytes(),
                                humanReadableBytes(p.getSizeBytes()));
                        Label partLabel = new Label(line);
                        partLabel.setWrapText(true);
                        splitBox.getChildren().add(partLabel);
                    }
                } else {
                    Label emptyLabel = new Label("No detailed part records found in database.");
                    emptyLabel.setWrapText(true);
                    splitBox.getChildren().add(emptyLabel);
                }

                grid.add(splitLabel, 0, r);
                grid.add(splitBox, 1, r);
            }

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

                    long splitParts = 0;
                    if (r.getSplitPartsCount() != null && r.getSplitPartsCount() > 0) {
                        splitParts = r.getSplitPartsCount();
                    }

                    long splitTotalSize = 0L;
                    if (r.getSplitTotalSize() != null && r.getSplitTotalSize() > 0) {
                        splitTotalSize = r.getSplitTotalSize();
                    }

                    String splitFirstPath = r.getSplitFirstPath();

                    logRows.add(new LogRow(
                            raw,
                            shortStr,
                            r.getOperation(),
                            r.getStatus(),
                            pathForUi,
                            r.getFormat(),
                            r.getCompression(),
                            r.getSizeBytes(),
                            r.getDurationMs(),
                            splitParts,
                            splitTotalSize,
                            splitFirstPath,
                            r.getArchiveId()));
                }
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                if (ex != null) {
                    ex.printStackTrace();
                }
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

    private String humanReadableBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "KB", "MB", "GB", "TB" };
        int unit = 0;
        value = value / 1024.0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value = value / 1024.0;
            unit++;
        }
        return String.format(Locale.ENGLISH, "%.1f %s", value, units[unit]);
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
        closeAllEditSessions();
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

        private final SimpleLongProperty splitParts;
        private final SimpleLongProperty splitTotalSize;
        private final SimpleStringProperty splitFirstPath;

        private final UUID archiveId;

        public LogRow(OffsetDateTime createdAtRaw,
                String createdAtFormatted,
                String operation,
                String status,
                String archivePath,
                String format,
                String compression,
                long sizeBytes,
                long durationMs,
                long splitParts,
                long splitTotalSize,
                String splitFirstPath,
                UUID archiveId) {
            this.createdAtRaw = createdAtRaw;
            this.createdAt = new SimpleStringProperty(createdAtFormatted);
            this.operation = new SimpleStringProperty(operation);
            this.status = new SimpleStringProperty(status);
            this.archivePath = new SimpleStringProperty(archivePath);
            this.format = new SimpleStringProperty(format);
            this.compression = new SimpleStringProperty(compression);
            this.sizeBytes = new SimpleLongProperty(sizeBytes);
            this.durationMs = new SimpleLongProperty(durationMs);

            this.splitParts = new SimpleLongProperty(splitParts);
            this.splitTotalSize = new SimpleLongProperty(splitTotalSize);
            this.splitFirstPath = new SimpleStringProperty(
                    splitFirstPath != null ? splitFirstPath : "");

            this.archiveId = archiveId;
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

        public long getSplitParts() {
            return splitParts.get();
        }

        public long getSplitTotalSize() {
            return splitTotalSize.get();
        }

        public String getSplitFirstPath() {
            return splitFirstPath.get();
        }

        public UUID getArchiveId() {
            return archiveId;
        }

    }

    private static class FileNode {
        private final Path path;
        private final String name;
        private final boolean directory;

        FileNode(Path path, String name, boolean directory) {
            this.path = path;
            this.name = name;
            this.directory = directory;
        }

        public Path getPath() {
            return path;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return directory;
        }
    }

    private static class EditSession {
        Path archiveFile;
        Path workDir;
        String displayPath;
        boolean canSave;
        EditSession parent;
    }
}
