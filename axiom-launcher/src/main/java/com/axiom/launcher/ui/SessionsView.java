package com.axiom.launcher.ui;

import com.axiom.launcher.client.ClientInstance;
import com.axiom.launcher.client.ClientManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.Instant;
import java.util.List;

/**
 * Sessions panel — live view of all running RuneLite client processes.
 *
 * <p>Shows 4 stat cards (Running / Breaking / Paused / Total) and a table
 * of active client instances that refreshes every second.
 *
 * <p>Session history (past runs) is not yet persisted; only active instances
 * from ClientManager are shown.
 */
public class SessionsView extends VBox
{
    private final ClientManager clientManager;

    private final ObservableList<ClientInstance> tableItems = FXCollections.observableArrayList();
    private final TableView<ClientInstance>      tableView  = new TableView<>(tableItems);

    private final Label runningValue  = new Label("0");
    private final Label breakingValue = new Label("0");
    private final Label pausedValue   = new Label("0");
    private final Label totalValue    = new Label("0");

    private Timeline refreshTimeline;

    /** Callback updated every tick so MainWindow topbar can show running count. */
    private Runnable onRunningCountChanged;

    public SessionsView(ClientManager clientManager)
    {
        this.clientManager = clientManager;

        getStyleClass().add("content-pane");
        setSpacing(16);
        setPadding(new Insets(24));

        buildHeader();
        buildStatCards();
        buildTable();
        startRefreshTimeline();
    }

    /** Called by MainWindow — fires whenever the running count changes. */
    public void setOnRunningCountChanged(Runnable cb) { this.onRunningCountChanged = cb; }

    /** Returns the current number of RUNNING (non-paused, non-broken) clients. */
    public int getRunningCount()
    {
        return (int) tableItems.stream().filter(i -> "RUNNING".equals(i.getStatus())).count();
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void buildHeader()
    {
        Label title    = new Label("Sessions");
        Label subtitle = new Label("Active RuneLite clients managed by the launcher.");
        title.getStyleClass().add("panel-title");
        subtitle.getStyleClass().add("panel-subtitle");

        Button stopAllBtn = new Button("Stop All");
        stopAllBtn.getStyleClass().addAll("btn", "btn-danger");
        stopAllBtn.setOnAction(e ->
        {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Stop all running clients?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait()
                .filter(btn -> btn == ButtonType.YES)
                .ifPresent(btn -> { clientManager.stopAll(); syncTable(); });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8);
        titleRow.getChildren().addAll(new VBox(2, title, subtitle), spacer, stopAllBtn);
        titleRow.setAlignment(Pos.CENTER);

        getChildren().addAll(titleRow, new Separator());
    }

    private void buildStatCards()
    {
        runningValue.getStyleClass().addAll("stat-value", "stat-value-accent");
        breakingValue.getStyleClass().add("stat-value");
        pausedValue.getStyleClass().add("stat-value");
        totalValue.getStyleClass().addAll("stat-value", "stat-value-green");

        HBox cards = new HBox(12,
            statCard("Running",  runningValue),
            statCard("Breaking", breakingValue),
            statCard("Paused",   pausedValue),
            statCard("Total",    totalValue)
        );

        getChildren().add(cards);
    }

    private VBox statCard(String label, Label valueLabel)
    {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");
        VBox card = new VBox(4, lbl, valueLabel);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    @SuppressWarnings("unchecked")
    private void buildTable()
    {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(placeholder());
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // Account column
        TableColumn<ClientInstance, String> accountCol = new TableColumn<>("Account");
        accountCol.setPrefWidth(140);
        accountCol.setCellFactory(col -> new TableCell<ClientInstance, String>()
        {
            @Override protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setText(null); return; }
                ClientInstance ci = (ClientInstance) getTableRow().getItem();
                setText(ci.getAccount() != null ? ci.getAccount().displayName : "?");
            }
        });

        // Script column
        TableColumn<ClientInstance, String> scriptCol = new TableColumn<>("Script");
        scriptCol.setPrefWidth(160);
        scriptCol.setCellFactory(col -> new TableCell<ClientInstance, String>()
        {
            @Override protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setText(null); return; }
                setText(((ClientInstance) getTableRow().getItem()).getScriptName());
            }
        });

        // World column
        TableColumn<ClientInstance, String> worldCol = new TableColumn<>("World");
        worldCol.setPrefWidth(60);
        worldCol.setCellFactory(col -> new TableCell<ClientInstance, String>()
        {
            @Override protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setText(null); return; }
                int w = ((ClientInstance) getTableRow().getItem()).getWorld();
                setText(w == 0 ? "—" : String.valueOf(w));
            }
        });

        // Runtime column
        TableColumn<ClientInstance, String> runtimeCol = new TableColumn<>("Runtime");
        runtimeCol.setPrefWidth(90);
        runtimeCol.setCellFactory(col -> new TableCell<ClientInstance, String>()
        {
            @Override protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setText(null); return; }
                ClientInstance ci = (ClientInstance) getTableRow().getItem();
                setText(formatRuntime(ci.getStartedAt()));
                getStyleClass().add("label-mono");
            }
        });

        // Status column
        TableColumn<ClientInstance, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(90);
        statusCol.setCellFactory(col -> new TableCell<ClientInstance, String>()
        {
            @Override protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                String status = ((ClientInstance) getTableRow().getItem()).getStatus();
                Label pill = new Label(status);
                pill.getStyleClass().add("status-" + status.toLowerCase());
                setGraphic(pill);
                setText(null);
            }
        });

        // Actions column
        TableColumn<ClientInstance, Void> actionsCol = new TableColumn<>("");
        actionsCol.setPrefWidth(80);
        actionsCol.setResizable(false);
        actionsCol.setCellFactory(col -> new TableCell<ClientInstance, Void>()
        {
            private final Button stopBtn = new Button("Stop");
            {
                stopBtn.getStyleClass().addAll("btn", "btn-danger");
                stopBtn.setPadding(new Insets(4, 10, 4, 10));
                stopBtn.setOnAction(e ->
                {
                    ClientInstance ci = (ClientInstance) getTableRow().getItem();
                    if (ci == null) return;
                    clientManager.stopInstance(ci);
                    syncTable();
                });
            }

            @Override protected void updateItem(Void item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                setGraphic(stopBtn);
            }
        });

        tableView.getColumns().setAll(accountCol, scriptCol, worldCol, runtimeCol, statusCol, actionsCol);
        getChildren().add(tableView);
    }

    // ── Refresh logic ──────────────────────────────────────────────────────────

    private void startRefreshTimeline()
    {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> syncTable()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    /** Stops the background timeline — call this when the view is being removed. */
    public void stop() { if (refreshTimeline != null) refreshTimeline.stop(); }

    private void syncTable()
    {
        List<ClientInstance> active = clientManager.getActiveInstances();
        tableItems.setAll(active);
        tableView.refresh();

        long running  = active.stream().filter(i -> "RUNNING" .equals(i.getStatus())).count();
        long breaking = active.stream().filter(i -> "BREAKING".equals(i.getStatus())).count();
        long paused   = active.stream().filter(i -> "PAUSED"  .equals(i.getStatus())).count();

        runningValue.setText(String.valueOf(running));
        breakingValue.setText(String.valueOf(breaking));
        pausedValue.setText(String.valueOf(paused));
        totalValue.setText(String.valueOf(active.size()));

        if (onRunningCountChanged != null) onRunningCountChanged.run();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String formatRuntime(Instant startedAt)
    {
        long seconds = java.time.Duration.between(startedAt, Instant.now()).getSeconds();
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return s + "s";
    }

    private Label placeholder()
    {
        Label lbl = new Label("No active clients — launch a script to get started.");
        lbl.getStyleClass().add("label-muted");
        return lbl;
    }
}
