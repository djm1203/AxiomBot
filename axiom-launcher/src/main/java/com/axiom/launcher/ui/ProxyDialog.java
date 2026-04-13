package com.axiom.launcher.ui;

import com.axiom.launcher.db.Proxy;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/** Modal dialog for adding or editing a Proxy. */
public class ProxyDialog
{
    private final Stage stage;
    private Proxy result;

    private final TextField     nameField     = new TextField();
    private final TextField     hostField     = new TextField();
    private final TextField     portField     = new TextField();
    private final TextField     usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();

    /** Add-mode constructor. */
    public ProxyDialog(Window owner) { this(owner, null); }

    /**
     * Edit-mode constructor when {@code existing} is non-null;
     * Add-mode when null.
     */
    public ProxyDialog(Window owner, Proxy existing)
    {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "Add Proxy" : "Edit Proxy — " + existing.name);
        stage.setResizable(false);

        nameField.setPromptText("US Residential 1");
        hostField.setPromptText("proxy.example.com");
        portField.setPromptText("8080");
        usernameField.setPromptText("optional");
        passwordField.setPromptText("optional");

        if (existing != null)
        {
            nameField.setText(existing.name);
            hostField.setText(existing.host);
            portField.setText(String.valueOf(existing.port));
            if (existing.username != null) usernameField.setText(existing.username);
            // password left blank — empty means "keep existing value"
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(24, 24, 16, 24));

        int row = 0;
        addRow(grid, row++, "Name",     nameField);
        addRow(grid, row++, "Host",     hostField);
        addRow(grid, row++, "Port",     portField);
        addRow(grid, row++, "Username", usernameField);
        addRow(grid, row++, "Password", passwordField);

        ColumnConstraints c0 = new ColumnConstraints(90);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setMinWidth(220);
        grid.getColumnConstraints().addAll(c0, c1);

        if (existing != null)
        {
            Label hint = new Label("Leave password blank to keep the existing value.");
            hint.getStyleClass().add("label-muted");
            grid.add(hint, 1, row++);
        }

        Button saveBtn   = new Button("Save");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().addAll("btn", "btn-accent");
        cancelBtn.getStyleClass().add("btn");

        saveBtn.setOnAction(e -> { if (validate()) { result = buildProxy(existing); stage.close(); } });
        cancelBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(8, cancelBtn, saveBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(0, 24, 24, 24));

        VBox root = new VBox(grid, buttons);
        root.getStyleClass().add("modal-root");

        Scene scene = new Scene(root, 380, existing != null ? 310 : 290);
        scene.getStylesheets().add(css());
        stage.setScene(scene);
    }

    /** Blocks until the user saves or cancels. Returns the configured Proxy, or empty. */
    public Optional<Proxy> showAndWait()
    {
        stage.showAndWait();
        return Optional.ofNullable(result);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void addRow(GridPane grid, int row, String text, Control field)
    {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("form-label");
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
    }

    private boolean validate()
    {
        if (nameField.getText().trim().isEmpty())  { err("Name is required.");         return false; }
        if (hostField.getText().trim().isEmpty())  { err("Host is required.");         return false; }
        String portStr = portField.getText().trim();
        try
        {
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535)          { err("Port must be 1–65535.");     return false; }
        }
        catch (NumberFormatException e)            { err("Port must be a number.");    return false; }
        return true;
    }

    private void err(String msg)
    {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(stage);
        a.showAndWait();
    }

    private Proxy buildProxy(Proxy existing)
    {
        Proxy p   = (existing != null) ? existing : new Proxy();
        p.name    = nameField.getText().trim();
        p.host    = hostField.getText().trim();
        p.port    = Integer.parseInt(portField.getText().trim());
        p.username = usernameField.getText().trim();
        String pw = passwordField.getText();
        if (!pw.isEmpty()) p.passwordEnc = pw; // plaintext; ProxyRepository encrypts on write before storing
        return p;
    }

    private static String css()
    {
        return ProxyDialog.class
            .getResource("/com/axiom/launcher/ui/main.css").toExternalForm();
    }
}
