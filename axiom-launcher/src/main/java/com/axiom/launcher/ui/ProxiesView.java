package com.axiom.launcher.ui;

import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.db.ProxyRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Proxies panel — shows all configured proxies as cards with
 * an async socket-test button and add/edit/delete actions.
 */
public class ProxiesView extends VBox
{
    private final ProxyRepository proxyRepo;
    private final Stage           owner;
    private final VBox            cardList = new VBox(10);

    public ProxiesView(ProxyRepository proxyRepo, Stage owner)
    {
        this.proxyRepo = proxyRepo;
        this.owner     = owner;

        getStyleClass().add("content-pane");
        setSpacing(16);
        setPadding(new Insets(24));

        buildHeader();

        ScrollPane scroll = new ScrollPane(cardList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        refresh();
    }

    // ── Data ───────────────────────────────────────────────────────────────────

    public void refresh()
    {
        cardList.getChildren().clear();
        List<Proxy> proxies = proxyRepo.findAll();

        if (proxies.isEmpty())
        {
            cardList.getChildren().add(emptyState());
        }
        else
        {
            for (Proxy p : proxies) cardList.getChildren().add(buildCard(p));
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void buildHeader()
    {
        Label title    = new Label("Proxies");
        Label subtitle = new Label("Manage HTTP/HTTPS proxy configurations.");
        title.getStyleClass().add("panel-title");
        subtitle.getStyleClass().add("panel-subtitle");

        Button addBtn = new Button("+ Add Proxy");
        addBtn.getStyleClass().addAll("btn", "btn-accent");
        addBtn.setOnAction(e -> new ProxyDialog(owner).showAndWait().ifPresent(p ->
        {
            proxyRepo.insert(p);
            refresh();
        }));

        HBox titleRow = new HBox();
        VBox titleBlock = new VBox(2, title, subtitle);
        Region spacer   = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(titleBlock, spacer, addBtn);
        titleRow.setAlignment(Pos.CENTER);

        getChildren().addAll(titleRow, new Separator());
    }

    private VBox buildCard(Proxy p)
    {
        Label nameLabel = new Label(p.name);
        nameLabel.getStyleClass().add("proxy-name");

        String auth = (p.username != null && !p.username.isEmpty()) ? "  ·  " + p.username : "";
        Label hostLabel = new Label(p.host + ":" + p.port + auth);
        hostLabel.getStyleClass().add("proxy-host");

        Label latencyLabel = new Label("—");
        latencyLabel.getStyleClass().add("latency-idle");

        Button testBtn   = new Button("Test");
        Button editBtn   = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        testBtn.getStyleClass().add("btn");
        editBtn.getStyleClass().add("btn");
        deleteBtn.getStyleClass().addAll("btn", "btn-danger");

        testBtn.setOnAction(e -> runLatencyTest(p, latencyLabel, testBtn));
        editBtn.setOnAction(e -> new ProxyDialog(owner, p).showAndWait().ifPresent(updated ->
        {
            proxyRepo.update(updated);
            refresh();
        }));
        deleteBtn.setOnAction(e -> deleteProxy(p));

        Region spacer  = new Region();
        HBox   actions = new HBox(6, latencyLabel, spacer, testBtn, editBtn, deleteBtn);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, new VBox(3, nameLabel, hostLabel), actions);
        card.getStyleClass().add("proxy-card");
        return card;
    }

    private VBox emptyState()
    {
        Label title    = new Label("No Proxies Configured");
        Label subtitle = new Label("Click '+ Add Proxy' to get started.");
        title.getStyleClass().add("empty-title");
        subtitle.getStyleClass().add("empty-subtitle");

        VBox box = new VBox(6, title, subtitle);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        VBox.setMargin(box, new Insets(40, 0, 0, 0));
        return box;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void deleteProxy(Proxy p)
    {
        String body = proxyRepo.hasLinkedAccounts(p.id)
            ? "\"" + p.name + "\" has linked accounts — they will be set to direct connection. Delete anyway?"
            : "Delete proxy \"" + p.name + "\"?";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Proxy");
        confirm.initOwner(owner);
        confirm.showAndWait()
            .filter(btn -> btn == ButtonType.YES)
            .ifPresent(btn -> { proxyRepo.delete(p.id); refresh(); });
    }

    private void runLatencyTest(Proxy p, Label latencyLabel, Button testBtn)
    {
        testBtn.setDisable(true);
        latencyLabel.getStyleClass().setAll("latency-idle");
        latencyLabel.setText("testing…");

        Thread t = new Thread(() ->
        {
            long    start     = System.currentTimeMillis();
            boolean connected = false;
            try (Socket s = new Socket())
            {
                s.connect(new InetSocketAddress(p.host, p.port), 5_000);
                connected = true;
            }
            catch (Exception ignored) {}
            long ms = System.currentTimeMillis() - start;

            final boolean ok      = connected;
            final long    elapsed = ms;

            Platform.runLater(() ->
            {
                testBtn.setDisable(false);
                if (!ok)
                {
                    latencyLabel.getStyleClass().setAll("latency-bad");
                    latencyLabel.setText("unreachable");
                }
                else if (elapsed < 150)
                {
                    latencyLabel.getStyleClass().setAll("latency-ok");
                    latencyLabel.setText(elapsed + " ms");
                }
                else if (elapsed < 400)
                {
                    latencyLabel.getStyleClass().setAll("latency-warn");
                    latencyLabel.setText(elapsed + " ms");
                }
                else
                {
                    latencyLabel.getStyleClass().setAll("latency-bad");
                    latencyLabel.setText(elapsed + " ms");
                }
            });
        }, "axiom-proxy-test-" + p.id);
        t.setDaemon(true);
        t.start();
    }
}
