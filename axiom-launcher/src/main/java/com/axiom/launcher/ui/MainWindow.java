package com.axiom.launcher.ui;

import com.axiom.launcher.client.ClientManager;
import com.axiom.launcher.db.AccountRepository;
import com.axiom.launcher.db.ProxyRepository;
import javafx.animation.FadeTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

/**
 * Root application window for the Axiom Launcher.
 *
 * Layout: BorderPane
 *   TOP  — topbar  (logo, version badge, running-clients badge)
 *   LEFT — sidenav (Sessions / Accounts / Proxies / Scripts / Settings)
 *   CENTER — StackPane content area, one view visible at a time with fade transition
 *
 * All shared infrastructure objects are created here and passed into the views
 * and dialogs that need them.
 */
public class MainWindow
{
    private static final PseudoClass NAV_SELECTED = PseudoClass.getPseudoClass("selected");

    private final Stage stage;

    // ── Shared state ──────────────────────────────────────────────────────────
    private final ClientManager     clientManager = new ClientManager();
    private final AccountRepository accountRepo   = new AccountRepository();
    private final ProxyRepository   proxyRepo     = new ProxyRepository();

    // ── Views ─────────────────────────────────────────────────────────────────
    private final SessionsView sessionsView;
    private final AccountsView accountsView;
    private final ProxiesView  proxiesView;
    private final ScriptsView  scriptsView;
    private final SettingsView settingsView;

    // ── Topbar badge ──────────────────────────────────────────────────────────
    private final Label runningBadge = new Label("0");

    // ── Content area ──────────────────────────────────────────────────────────
    private final StackPane contentArea = new StackPane();
    private       Node      currentView = null;

    public MainWindow(Stage stage)
    {
        this.stage = stage;
        stage.setTitle("Axiom Launcher");
        stage.setWidth(1020);
        stage.setHeight(660);
        stage.setMinWidth(800);
        stage.setMinHeight(520);

        // ── Initialise views ──────────────────────────────────────────────────
        sessionsView = new SessionsView(clientManager);
        accountsView = new AccountsView(accountRepo, proxyRepo, stage);
        proxiesView  = new ProxiesView(proxyRepo, stage);

        scriptsView = new ScriptsView(
            () -> ScriptRegistry.loadScripts(SettingsView.currentScriptsDir()),
            clientManager,
            accountRepo,
            proxyRepo,
            stage,
            () -> switchTo(sessionsView)   // after launch → go to Sessions
        );

        settingsView = new SettingsView(stage, () ->
        {
            // Reload script list when settings (scripts.dir) change
            scriptsView.refresh();
        });

        // Feed running-count updates to topbar badge
        sessionsView.setOnRunningCountChanged(this::updateRunningBadge);

        // ── Start client monitor ──────────────────────────────────────────────
        clientManager.startMonitorThread();

        // ── Build and show the scene ──────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildTopbar());
        root.setLeft(buildSidenav());
        root.setCenter(contentArea);

        contentArea.getChildren().addAll(
            sessionsView, accountsView, proxiesView, scriptsView, settingsView);

        // Show Sessions by default
        for (Node n : contentArea.getChildren()) n.setVisible(false);
        sessionsView.setVisible(true);
        currentView = sessionsView;

        Scene scene = new Scene(root, 1020, 660);
        scene.getStylesheets().add(
            MainWindow.class.getResource("/com/axiom/launcher/ui/main.css").toExternalForm());
        stage.setScene(scene);

        stage.setOnCloseRequest(e -> sessionsView.stop());
    }

    public void show() { stage.show(); }

    // ── Topbar ─────────────────────────────────────────────────────────────────

    private HBox buildTopbar()
    {
        Label logo = new Label("Axiom");
        logo.getStyleClass().add("logo-text");

        Label version = new Label("v1.0");
        version.getStyleClass().add("version-badge");

        runningBadge.getStyleClass().add("nav-badge-inactive");
        runningBadge.setMinWidth(24);

        Label runningLabel = new Label("clients");
        runningLabel.getStyleClass().add("status-label");

        HBox badgeGroup = new HBox(6, runningBadge, runningLabel);
        badgeGroup.setAlignment(Pos.CENTER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topbar = new HBox(10, logo, version, spacer, badgeGroup);
        topbar.getStyleClass().add("topbar");
        topbar.setPadding(new Insets(12, 20, 12, 20));
        topbar.setAlignment(Pos.CENTER_LEFT);
        return topbar;
    }

    private void updateRunningBadge()
    {
        int count = sessionsView.getRunningCount();
        runningBadge.setText(String.valueOf(count));
        if (count > 0) runningBadge.getStyleClass().setAll("nav-badge");
        else           runningBadge.getStyleClass().setAll("nav-badge-inactive");
    }

    // ── Sidenav ────────────────────────────────────────────────────────────────

    private VBox buildSidenav()
    {
        ToggleGroup  group   = new ToggleGroup();
        ToggleButton[] navBtns = {
            navButton("▶",  "Sessions",  group, sessionsView),
            navButton("👤", "Accounts",  group, accountsView),
            navButton("🔀", "Proxies",   group, proxiesView),
            navButton("📜", "Scripts",   group, scriptsView),
            navButton("⚙",  "Settings",  group, settingsView),
        };

        // Sessions selected by default
        navBtns[0].setSelected(true);

        Region divider = new Region();
        divider.getStyleClass().add("nav-divider");
        divider.setPrefHeight(1);
        VBox.setMargin(divider, new Insets(8, 0, 8, 0));

        VBox sidenav = new VBox();
        sidenav.getStyleClass().add("sidenav");
        sidenav.setPadding(new Insets(12, 8, 12, 8));
        sidenav.setSpacing(2);
        sidenav.setPrefWidth(160);

        Label section1 = new Label("MANAGEMENT");
        section1.getStyleClass().add("nav-section");
        sidenav.getChildren().add(section1);
        sidenav.getChildren().addAll(navBtns[0], navBtns[1], navBtns[2]);
        sidenav.getChildren().add(divider);
        Label section2 = new Label("CONFIGURATION");
        section2.getStyleClass().add("nav-section");
        sidenav.getChildren().add(section2);
        sidenav.getChildren().addAll(navBtns[3], navBtns[4]);

        return sidenav;
    }

    private ToggleButton navButton(String icon, String label, ToggleGroup group, Node target)
    {
        Label iconLbl  = new Label(icon);
        Label labelLbl = new Label(label);
        iconLbl.getStyleClass().add("nav-icon");
        labelLbl.getStyleClass().add("nav-label");

        HBox content = new HBox(10, iconLbl, labelLbl);
        content.setAlignment(Pos.CENTER_LEFT);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);
        btn.getStyleClass().add("nav-item");
        btn.setToggleGroup(group);
        btn.setMaxWidth(Double.MAX_VALUE);

        btn.selectedProperty().addListener((obs, old, now) ->
        {
            if (Boolean.TRUE.equals(now)) switchTo(target);
        });

        // Prevent deselection by clicking the already-selected button
        btn.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e ->
        {
            if (btn.isSelected()) e.consume();
        });

        return btn;
    }

    // ── View switching ─────────────────────────────────────────────────────────

    private void switchTo(Node target)
    {
        if (target == currentView) return;

        Node prev = currentView;
        currentView = target;

        target.setVisible(true);
        target.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(160), target);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        if (prev != null)
        {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(120), prev);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> prev.setVisible(false));
            fadeOut.play();
        }

        // Refresh data when navigating to a view
        if      (target == accountsView) accountsView.refresh();
        else if (target == proxiesView)  proxiesView.refresh();
    }
}
