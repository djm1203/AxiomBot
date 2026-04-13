package com.axiom.launcher.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.util.Properties;

/**
 * Settings panel — edits ~/.axiom/launcher.properties.
 * Changes take effect on next launch; RuneLite JAR path and scripts dir
 * can be browsed via native file/folder choosers.
 */
public class SettingsView extends VBox
{
    private static final String PROPS_FILE      = System.getProperty("user.home") + "/.axiom/launcher.properties";
    public  static final String KEY_JAR_PATH    = "runelite.jar.path";
    public  static final String KEY_HEAP        = "default.heap.mb";
    public  static final String KEY_DELAY       = "launch.delay.ms";
    public  static final String KEY_SCRIPTS_DIR = "scripts.dir";
    public  static final String KEY_DEV_MODE    = "developer.mode";

    private final Stage    owner;
    private final Runnable onSaved;

    private final TextField jarPathField    = new TextField();
    private final TextField heapField       = new TextField();
    private final TextField delayField      = new TextField();
    private final TextField scriptsDirField = new TextField();
    private final CheckBox  devModeBox      = new CheckBox("Developer Mode  (--developer-mode)");
    private final Label     statusLabel     = new Label();

    /**
     * @param owner   stage used as parent for file/directory choosers
     * @param onSaved called on the JavaFX thread after settings are persisted
     */
    public SettingsView(Stage owner, Runnable onSaved)
    {
        this.owner   = owner;
        this.onSaved = onSaved;

        getStyleClass().add("content-pane");
        setSpacing(16);
        setPadding(new Insets(24));
        setMaxWidth(640);

        buildUI();
        loadSettings();
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void buildUI()
    {
        Label title    = new Label("Settings");
        Label subtitle = new Label("Saved to ~/.axiom/launcher.properties — takes effect on next launch.");
        title.getStyleClass().add("panel-title");
        subtitle.getStyleClass().add("panel-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(8, 0, 0, 0));

        ColumnConstraints c0 = new ColumnConstraints(145);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(90);
        grid.getColumnConstraints().addAll(c0, c1, c2);

        jarPathField.setPromptText("/path/to/runelite.jar");
        heapField.setPromptText("512");
        delayField.setPromptText("3000");
        scriptsDirField.setPromptText("~/.axiom/scripts");
        devModeBox.getStyleClass().add("check-box");

        Button browseJar     = new Button("Browse…");
        Button browseScripts = new Button("Browse…");
        browseJar.getStyleClass().add("btn");
        browseScripts.getStyleClass().add("btn");
        browseJar.setOnAction(e     -> pickFile("RuneLite JAR", jarPathField));
        browseScripts.setOnAction(e -> pickDir("Scripts Directory", scriptsDirField));

        int row = 0;
        addRow(grid, row++, "RuneLite JAR Path",  jarPathField,    browseJar);
        addRow(grid, row++, "Default Heap (MB)",  heapField,       null);
        addRow(grid, row++, "Launch Delay (ms)",  delayField,      null);
        addRow(grid, row++, "Scripts Directory",  scriptsDirField, browseScripts);

        grid.add(formLabel("Developer Mode"), 0, row);
        grid.add(devModeBox, 1, row, 2, 1);

        Button saveBtn = new Button("Save Settings");
        saveBtn.getStyleClass().addAll("btn", "btn-accent");
        saveBtn.setOnAction(e -> saveSettings());

        statusLabel.getStyleClass().add("save-success");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        HBox footer = new HBox(12, saveBtn, statusLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(4, 0, 0, 0));

        getChildren().addAll(title, subtitle, new Separator(), grid, footer);
    }

    private void addRow(GridPane grid, int row, String labelText, TextField field, Button action)
    {
        grid.add(formLabel(labelText), 0, row);
        grid.add(field, 1, row);
        if (action != null) grid.add(action, 2, row);
    }

    private Label formLabel(String text)
    {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("form-label");
        return lbl;
    }

    // ── Load / Save ────────────────────────────────────────────────────────────

    private void loadSettings()
    {
        Properties p = readProps();
        jarPathField.setText(p.getProperty(KEY_JAR_PATH, ""));
        heapField.setText(p.getProperty(KEY_HEAP, "512"));
        delayField.setText(p.getProperty(KEY_DELAY, "3000"));
        scriptsDirField.setText(p.getProperty(KEY_SCRIPTS_DIR, "~/.axiom/scripts"));
        devModeBox.setSelected(Boolean.parseBoolean(p.getProperty(KEY_DEV_MODE, "false")));
    }

    private void saveSettings()
    {
        String heap  = heapField.getText().trim();
        String delay = delayField.getText().trim();
        if (!heap.isEmpty())
        {
            try { Integer.parseInt(heap); }
            catch (NumberFormatException e) { err("Heap must be a number."); return; }
        }
        if (!delay.isEmpty())
        {
            try { Long.parseLong(delay); }
            catch (NumberFormatException e) { err("Launch delay must be a number."); return; }
        }

        Properties p = readProps();
        p.setProperty(KEY_JAR_PATH,    jarPathField.getText().trim());
        p.setProperty(KEY_HEAP,        heap.isEmpty() ? "512" : heap);
        p.setProperty(KEY_DELAY,       delay.isEmpty() ? "3000" : delay);
        p.setProperty(KEY_SCRIPTS_DIR, scriptsDirField.getText().trim());
        p.setProperty(KEY_DEV_MODE,    String.valueOf(devModeBox.isSelected()));

        File file = new File(PROPS_FILE);
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file))
        {
            p.store(out, "Axiom Launcher — user configuration");
        }
        catch (IOException ex)
        {
            err("Could not save settings: " + ex.getMessage());
            return;
        }

        statusLabel.setText("Saved.");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> { statusLabel.setVisible(false); statusLabel.setManaged(false); });
        pause.play();

        if (onSaved != null) onSaved.run();
    }

    // ── File / Directory choosers ──────────────────────────────────────────────

    private void pickFile(String title, TextField target)
    {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select " + title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));
        String cur = target.getText().trim();
        if (!cur.isEmpty()) { File f = new File(cur); if (f.getParentFile() != null && f.getParentFile().isDirectory()) fc.setInitialDirectory(f.getParentFile()); }
        File sel = fc.showOpenDialog(owner);
        if (sel != null) target.setText(sel.getAbsolutePath());
    }

    private void pickDir(String title, TextField target)
    {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select " + title);
        String cur = target.getText().trim().replace("~", System.getProperty("user.home"));
        File curDir = new File(cur);
        if (curDir.isDirectory()) dc.setInitialDirectory(curDir);
        File sel = dc.showDialog(owner);
        if (sel != null) target.setText(sel.getAbsolutePath());
    }

    private void err(String msg)
    {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(owner);
        a.showAndWait();
    }

    // ── Static helper ──────────────────────────────────────────────────────────

    /** Reads scripts.dir from launcher.properties (may start with ~). */
    public static String currentScriptsDir()
    {
        Properties p = new Properties();
        File file    = new File(PROPS_FILE);
        if (file.isFile())
        {
            try (InputStream in = new FileInputStream(file)) { p.load(in); }
            catch (IOException ignored) {}
        }
        return p.getProperty(KEY_SCRIPTS_DIR, "~/.axiom/scripts");
    }

    private static Properties readProps()
    {
        Properties p = new Properties();
        File file    = new File(PROPS_FILE);
        if (file.isFile())
        {
            try (InputStream in = new FileInputStream(file)) { p.load(in); }
            catch (IOException ignored) {}
        }
        return p;
    }
}
