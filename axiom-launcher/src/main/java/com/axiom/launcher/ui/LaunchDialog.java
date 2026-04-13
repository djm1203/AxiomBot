package com.axiom.launcher.ui;

import com.axiom.launcher.client.ClientInstance;
import com.axiom.launcher.client.ClientManager;
import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.AccountRepository;
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
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;

/** Modal dialog for selecting account + script and launching a RuneLite client. */
public class LaunchDialog
{
    private final Stage stage;
    private ClientInstance launched;

    private final ChoiceBox<Account>   accountChoice = new ChoiceBox<>();
    private final ChoiceBox<ScriptInfo> scriptChoice = new ChoiceBox<>();
    private final TextField            worldField    = new TextField("0");
    private final TextField            heapField;
    private final ChoiceBox<ProxyItem> proxyChoice   = new ChoiceBox<>();
    private final TextField            windowXField  = new TextField("0");
    private final TextField            windowYField  = new TextField("0");

    private final ClientManager clientManager;

    public LaunchDialog(Window owner,
                        ClientManager    clientManager,
                        AccountRepository accountRepo,
                        ProxyRepository  proxyRepo,
                        List<ScriptInfo> scripts)
    {
        this(owner, clientManager, accountRepo, proxyRepo, scripts, null);
    }

    public LaunchDialog(Window owner,
                        ClientManager    clientManager,
                        AccountRepository accountRepo,
                        ProxyRepository  proxyRepo,
                        List<ScriptInfo> scripts,
                        ScriptInfo       preselected)
    {
        this.clientManager = clientManager;
        this.heapField     = new TextField(String.valueOf(clientManager.getDefaultHeapMb()));

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Launch Client");
        stage.setResizable(false);

        // ── Account selector ──────────────────────────────────────────────────
        List<Account> accounts = accountRepo.findAll();
        accountChoice.getItems().addAll(accounts);
        accountChoice.setConverter(new StringConverter<Account>()
        {
            @Override public String  toString(Account a)   { return a == null ? "" : a.displayName; }
            @Override public Account fromString(String s)  { return null; }
        });
        accountChoice.getStyleClass().add("choice-box");
        if (!accounts.isEmpty()) accountChoice.getSelectionModel().selectFirst();

        // ── Script selector ───────────────────────────────────────────────────
        scriptChoice.getItems().addAll(scripts);
        scriptChoice.setConverter(new StringConverter<ScriptInfo>()
        {
            @Override public String     toString(ScriptInfo s) { return s == null ? "" : s.emoji + " " + s.name; }
            @Override public ScriptInfo fromString(String s)   { return null; }
        });
        scriptChoice.getStyleClass().add("choice-box");
        if (preselected != null) scriptChoice.getSelectionModel().select(preselected);
        else if (!scripts.isEmpty()) scriptChoice.getSelectionModel().selectFirst();

        // ── Proxy selector ────────────────────────────────────────────────────
        proxyChoice.getItems().add(new ProxyItem(null, null, "— Account Default —"));
        for (Proxy p : proxyRepo.findAll())
            proxyChoice.getItems().add(new ProxyItem(p.id, p, p.name + "  " + p.host + ":" + p.port));
        proxyChoice.getSelectionModel().selectFirst();
        proxyChoice.getStyleClass().add("choice-box");

        // ── Form layout ───────────────────────────────────────────────────────
        worldField.setPromptText("0 = default");
        heapField.setPromptText("MB");
        windowXField.setPrefWidth(70);
        windowYField.setPrefWidth(70);

        HBox windowPos = new HBox(6, windowXField, new Label("×"), windowYField);
        windowPos.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(24, 24, 16, 24));

        int row = 0;
        addRow(grid, row++, "Account",    accountChoice);
        addRow(grid, row++, "Script",     scriptChoice);
        addRow(grid, row++, "World",      worldField);
        addRow(grid, row++, "Heap (MB)",  heapField);
        addRow(grid, row++, "Proxy",      proxyChoice);
        addRow(grid, row++, "Window X, Y", windowPos);

        ColumnConstraints c0 = new ColumnConstraints(96);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        // ── Error label + buttons ─────────────────────────────────────────────
        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 10;");
        errorLbl.setPadding(new Insets(0, 24, 0, 24));

        Button launchBtn = new Button("Launch");
        Button cancelBtn = new Button("Cancel");
        launchBtn.getStyleClass().addAll("btn", "btn-accent");
        cancelBtn.getStyleClass().add("btn");

        launchBtn.setOnAction(e ->
        {
            Optional<ClientInstance> ci = doLaunch(errorLbl);
            ci.ifPresent(inst -> { launched = inst; stage.close(); });
        });
        cancelBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(8, cancelBtn, launchBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(0, 24, 24, 24));

        VBox root = new VBox(grid, errorLbl, buttons);
        root.getStyleClass().add("modal-root");

        Scene scene = new Scene(root, 430, 370);
        scene.getStylesheets().add(css());
        stage.setScene(scene);
    }

    /**
     * Blocks until the user launches or cancels.
     * Returns the running ClientInstance, or empty if cancelled.
     */
    public Optional<ClientInstance> showAndWait()
    {
        stage.showAndWait();
        return Optional.ofNullable(launched);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Optional<ClientInstance> doLaunch(Label errorLbl)
    {
        Account    account = accountChoice.getSelectionModel().getSelectedItem();
        ScriptInfo script  = scriptChoice.getSelectionModel().getSelectedItem();
        if (account == null) { errorLbl.setText("Select an account."); return Optional.empty(); }
        if (script  == null) { errorLbl.setText("Select a script.");   return Optional.empty(); }

        int world, heap, winX, winY;
        try { world = Integer.parseInt(worldField.getText().trim()); }
        catch (NumberFormatException e) { errorLbl.setText("World must be a number."); return Optional.empty(); }
        try { heap = Integer.parseInt(heapField.getText().trim()); }
        catch (NumberFormatException e) { errorLbl.setText("Heap must be a number."); return Optional.empty(); }
        try { winX = Integer.parseInt(windowXField.getText().trim()); }
        catch (NumberFormatException e) { errorLbl.setText("Window X must be a number."); return Optional.empty(); }
        try { winY = Integer.parseInt(windowYField.getText().trim()); }
        catch (NumberFormatException e) { errorLbl.setText("Window Y must be a number."); return Optional.empty(); }

        ProxyItem proxyItem = proxyChoice.getSelectionModel().getSelectedItem();
        Proxy proxy = (proxyItem != null) ? proxyItem.proxy : null;

        try
        {
            return Optional.of(clientManager.launch(account, script.name, world, proxy, heap, winX, winY));
        }
        catch (RuntimeException ex)
        {
            errorLbl.setText(ex.getMessage());
            return Optional.empty();
        }
    }

    private void addRow(GridPane grid, int row, String text, javafx.scene.Node field)
    {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("form-label");
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
    }

    private static String css()
    {
        return LaunchDialog.class
            .getResource("/com/axiom/launcher/ui/main.css").toExternalForm();
    }

    // ── Inner type ─────────────────────────────────────────────────────────────

    private static class ProxyItem
    {
        final Integer id;
        final Proxy   proxy;
        final String  label;
        ProxyItem(Integer id, Proxy proxy, String label) { this.id = id; this.proxy = proxy; this.label = label; }
        @Override public String toString() { return label; }
    }
}
