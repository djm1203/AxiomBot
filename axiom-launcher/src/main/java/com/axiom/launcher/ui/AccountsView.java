package com.axiom.launcher.ui;

import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.AccountRepository;
import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.db.ProxyRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accounts panel — searchable TableView of managed OSRS accounts with
 * add, edit, and delete actions.
 */
public class AccountsView extends VBox
{
    private final AccountRepository accountRepo;
    private final ProxyRepository   proxyRepo;
    private final Stage             owner;

    private final ObservableList<Account> items     = FXCollections.observableArrayList();
    private final TableView<Account>      tableView = new TableView<>(items);
    private final TextField               searchField = new TextField();

    private List<Account>       allAccounts;
    private Map<Integer, String> proxyNames = new HashMap<>();

    public AccountsView(AccountRepository accountRepo, ProxyRepository proxyRepo, Stage owner)
    {
        this.accountRepo = accountRepo;
        this.proxyRepo   = proxyRepo;
        this.owner       = owner;

        getStyleClass().add("content-pane");
        setSpacing(16);
        setPadding(new Insets(24));

        buildHeader();
        buildTable();
        refresh();
    }

    // ── Data ───────────────────────────────────────────────────────────────────

    public void refresh()
    {
        allAccounts = accountRepo.findAll();
        proxyNames.clear();
        for (Proxy p : proxyRepo.findAll()) proxyNames.put(p.id, p.name);
        applySearch(searchField.getText());
    }

    private void applySearch(String query)
    {
        String q = (query == null) ? "" : query.trim().toLowerCase();
        items.clear();
        for (Account a : allAccounts)
        {
            if (q.isEmpty() || a.displayName.toLowerCase().contains(q))
                items.add(a);
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void buildHeader()
    {
        Label title    = new Label("Accounts");
        Label subtitle = new Label("Manage OSRS accounts for multi-client sessions.");
        title.getStyleClass().add("panel-title");
        subtitle.getStyleClass().add("panel-subtitle");

        searchField.setPromptText("Search accounts…");
        searchField.setPrefWidth(220);
        searchField.textProperty().addListener((obs, old, now) -> applySearch(now));

        Button addBtn = new Button("+ Add Account");
        addBtn.getStyleClass().addAll("btn", "btn-accent");
        addBtn.setOnAction(e -> new AccountDialog(owner, proxyRepo).showAndWait().ifPresent(a ->
        {
            accountRepo.insert(a);
            refresh();
        }));

        Region spacer   = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, searchField, spacer, addBtn);
        toolbar.setAlignment(Pos.CENTER);

        getChildren().addAll(title, subtitle, new Separator(), toolbar);
    }

    @SuppressWarnings("unchecked")
    private void buildTable()
    {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(placeholder());
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // Avatar column
        TableColumn<Account, String> avatarCol = new TableColumn<>("");
        avatarCol.setPrefWidth(48);
        avatarCol.setMaxWidth(48);
        avatarCol.setResizable(false);
        avatarCol.setCellFactory(col -> new TableCell<Account, String>()
        {
            @Override protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                Account a   = (Account) getTableRow().getItem();
                String  ch  = (a.displayName != null && !a.displayName.isEmpty())
                    ? String.valueOf(a.displayName.charAt(0)).toUpperCase() : "?";
                Label lbl   = new Label(ch);
                lbl.getStyleClass().add("avatar");
                setGraphic(lbl);
                setText(null);
            }
        });

        // Display name column
        TableColumn<Account, String> nameCol = new TableColumn<>("Account");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        nameCol.setPrefWidth(160);

        // Jagex Char ID column
        TableColumn<Account, String> charIdCol = new TableColumn<>("Jagex ID");
        charIdCol.setCellValueFactory(new PropertyValueFactory<>("jagexCharacterId"));
        charIdCol.setPrefWidth(130);
        charIdCol.setCellFactory(col -> new TableCell<Account, String>()
        {
            @Override protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) { setText("—"); getStyleClass().add("label-muted"); }
                else { setText(item); }
            }
        });

        // World column
        TableColumn<Account, Integer> worldCol = new TableColumn<>("World");
        worldCol.setCellValueFactory(new PropertyValueFactory<>("preferredWorld"));
        worldCol.setPrefWidth(60);
        worldCol.setCellFactory(col -> new TableCell<Account, Integer>()
        {
            @Override protected void updateItem(Integer item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item == 0 ? "Any" : String.valueOf(item));
            }
        });

        // Proxy column
        TableColumn<Account, Integer> proxyCol = new TableColumn<>("Proxy");
        proxyCol.setCellValueFactory(new PropertyValueFactory<>("proxyId"));
        proxyCol.setPrefWidth(120);
        proxyCol.setCellFactory(col -> new TableCell<Account, Integer>()
        {
            @Override protected void updateItem(Integer proxyId, boolean empty)
            {
                super.updateItem(proxyId, empty);
                if (empty) { setText(null); return; }
                if (proxyId == null) { setText("—"); getStyleClass().add("label-muted"); }
                else setText(proxyNames.getOrDefault(proxyId, "id:" + proxyId));
            }
        });

        // Actions column
        TableColumn<Account, Void> actionsCol = new TableColumn<>("");
        actionsCol.setPrefWidth(140);
        actionsCol.setResizable(false);
        actionsCol.setCellFactory(buildActionsCellFactory());

        tableView.getColumns().setAll(avatarCol, nameCol, charIdCol, worldCol, proxyCol, actionsCol);
        getChildren().add(tableView);
    }

    private Callback<TableColumn<Account, Void>, TableCell<Account, Void>> buildActionsCellFactory()
    {
        return col -> new TableCell<Account, Void>()
        {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            {
                editBtn.getStyleClass().add("btn");
                deleteBtn.getStyleClass().addAll("btn", "btn-danger");
                editBtn.setPadding(new Insets(4, 10, 4, 10));
                deleteBtn.setPadding(new Insets(4, 10, 4, 10));

                editBtn.setOnAction(e ->
                {
                    Account a = getTableRow().getItem();
                    if (a == null) return;
                    new AccountDialog(owner, a, proxyRepo).showAndWait().ifPresent(updated ->
                    {
                        accountRepo.update(updated);
                        refresh();
                    });
                });

                deleteBtn.setOnAction(e ->
                {
                    Account a = getTableRow().getItem();
                    if (a == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete account \"" + a.displayName + "\"?", ButtonType.YES, ButtonType.NO);
                    confirm.initOwner(owner);
                    confirm.showAndWait()
                        .filter(btn -> btn == ButtonType.YES)
                        .ifPresent(btn -> { accountRepo.delete(a.id); refresh(); });
                });
            }

            @Override protected void updateItem(Void item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                HBox box = new HBox(6, editBtn, deleteBtn);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        };
    }

    private Label placeholder()
    {
        Label lbl = new Label("No accounts yet — click '+ Add Account' to get started.");
        lbl.getStyleClass().add("label-muted");
        return lbl;
    }
}
