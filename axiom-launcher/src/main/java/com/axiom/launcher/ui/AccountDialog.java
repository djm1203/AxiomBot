package com.axiom.launcher.ui;

import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.db.ProxyRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/** Modal dialog for adding or editing an Account. */
public class AccountDialog
{
    private final Stage stage;
    private Account result;

    private final TextField     displayNameField      = new TextField();
    private final TextField     jagexCharacterIdField = new TextField();
    private final PasswordField bankPinField          = new PasswordField();
    private final TextField     preferredWorldField   = new TextField("0");
    private final ChoiceBox<ProxyItem> proxyChoice    = new ChoiceBox<>();
    private final TextArea      notesArea             = new TextArea();

    /** Add-mode. */
    public AccountDialog(Window owner, ProxyRepository proxyRepo) { this(owner, null, proxyRepo); }

    /**
     * Edit-mode when {@code existing} is non-null; Add-mode when null.
     */
    public AccountDialog(Window owner, Account existing, ProxyRepository proxyRepo)
    {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "Add Account" : "Edit Account — " + existing.displayName);
        stage.setResizable(false);

        displayNameField.setPromptText("Main Account");
        jagexCharacterIdField.setPromptText("jagex-char-id (optional)");
        bankPinField.setPromptText("4-digit PIN (optional)");
        preferredWorldField.setPromptText("0 = any world");
        notesArea.setPromptText("Notes…");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);
        proxyChoice.getStyleClass().add("choice-box");

        // Populate proxy list
        List<Proxy> proxies = proxyRepo.findAll();
        proxyChoice.getItems().add(new ProxyItem(null, "— No Proxy —"));
        for (Proxy p : proxies) proxyChoice.getItems().add(new ProxyItem(p.id, p.name));
        proxyChoice.getSelectionModel().selectFirst();

        if (existing != null)
        {
            displayNameField.setText(existing.displayName);
            if (existing.jagexCharacterId != null) jagexCharacterIdField.setText(existing.jagexCharacterId);
            // bank PIN not pre-filled — keep blank to preserve existing
            preferredWorldField.setText(String.valueOf(existing.preferredWorld));
            if (existing.proxyId != null)
            {
                proxyChoice.getItems().stream()
                    .filter(it -> existing.proxyId.equals(it.id))
                    .findFirst()
                    .ifPresent(it -> proxyChoice.getSelectionModel().select(it));
            }
            if (existing.notes != null) notesArea.setText(existing.notes);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(24, 24, 16, 24));

        int row = 0;
        addRow(grid, row++, "Display Name",  displayNameField);
        addRow(grid, row++, "Jagex Char ID", jagexCharacterIdField);
        addRow(grid, row++, "Bank PIN",      bankPinField);
        addRow(grid, row++, "World",         preferredWorldField);
        addRow(grid, row++, "Proxy",         proxyChoice);
        addRow(grid, row++, "Notes",         notesArea);

        ColumnConstraints c0 = new ColumnConstraints(96);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setMinWidth(240);
        grid.getColumnConstraints().addAll(c0, c1);

        if (existing != null)
        {
            Label hint = new Label("Leave Bank PIN blank to keep the existing value.");
            hint.getStyleClass().add("label-muted");
            grid.add(hint, 1, row++);
        }

        Button saveBtn   = new Button("Save");
        Button cancelBtn = new Button("Cancel");
        saveBtn.getStyleClass().addAll("btn", "btn-accent");
        cancelBtn.getStyleClass().add("btn");

        saveBtn.setOnAction(e -> { if (validate()) { result = buildAccount(existing); stage.close(); } });
        cancelBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(8, cancelBtn, saveBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(0, 24, 24, 24));

        VBox root = new VBox(grid, buttons);
        root.getStyleClass().add("modal-root");

        Scene scene = new Scene(root, 430, existing != null ? 420 : 400);
        scene.getStylesheets().add(css());
        stage.setScene(scene);
    }

    /** Blocks until save/cancel. Returns configured Account, or empty on cancel. */
    public Optional<Account> showAndWait()
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
        if (displayNameField.getText().trim().isEmpty())
        {
            err("Display name is required.");
            return false;
        }
        String worldStr = preferredWorldField.getText().trim();
        if (!worldStr.isEmpty())
        {
            try { Integer.parseInt(worldStr); }
            catch (NumberFormatException e) { err("World must be a number (0 = any)."); return false; }
        }
        return true;
    }

    private void err(String msg)
    {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(stage);
        a.showAndWait();
    }

    private Account buildAccount(Account existing)
    {
        Account a          = (existing != null) ? existing : new Account();
        a.displayName      = displayNameField.getText().trim();
        a.jagexCharacterId = jagexCharacterIdField.getText().trim();
        String pin         = bankPinField.getText();
        if (!pin.isEmpty()) a.bankPinEnc = pin; // plaintext; AccountRepository encrypts on write
        String worldStr    = preferredWorldField.getText().trim();
        a.preferredWorld   = worldStr.isEmpty() ? 0 : Integer.parseInt(worldStr);
        ProxyItem sel      = proxyChoice.getSelectionModel().getSelectedItem();
        a.proxyId          = (sel != null) ? sel.id : null;
        a.notes            = notesArea.getText().trim();
        return a;
    }

    private static String css()
    {
        return AccountDialog.class
            .getResource("/com/axiom/launcher/ui/main.css").toExternalForm();
    }

    // ── Inner type ─────────────────────────────────────────────────────────────

    private static class ProxyItem
    {
        final Integer id;
        final String  label;
        ProxyItem(Integer id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }
}
