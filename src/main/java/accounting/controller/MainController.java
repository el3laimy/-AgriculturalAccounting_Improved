package accounting.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class MainController implements Initializable {

    // --- عناصر الواجهة الجديدة ---
    @FXML private VBox sideNavigationBar;
    @FXML private StackPane contentArea;
    @FXML private Label viewTitleLabel;
    @FXML private Label dateTimeLabel;

    // --- أزرار التنقل ---
    @FXML private Button dashboardBtn;
    @FXML private Button purchasesBtn;
    @FXML private Button salesBtn;
    @FXML private Button inventoryBtn;
    @FXML private Button expensesBtn;
    @FXML private Button contactsBtn;
    @FXML private Button generalLedgerBtn;
    @FXML private Button reportsBtn;
    @FXML private Button settingsBtn;
    
    private Button currentActiveButton;
    private Timer statusTimer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupStatusBar();
        // تحميل لوحة التحكم كشاشة إفتراضية
        showDashboard();
    }

    private void setupStatusBar() {
        statusTimer = new Timer(true);
        statusTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (dateTimeLabel != null) {
                         dateTimeLabel.setText(java.time.LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
                    }
                });
            }
        }, 0, 1000);
    }
    
    private void loadView(String fxmlPath, String title, Button activeButton) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource("/fxml/" + fxmlPath));
            contentArea.getChildren().setAll(view);
            viewTitleLabel.setText(title);
            
            // تحديث حالة الزر النشط
            if (currentActiveButton != null) {
                currentActiveButton.getStyleClass().remove("active");
            }
            currentActiveButton = activeButton;
            currentActiveButton.getStyleClass().add("active");

        } catch (IOException e) {
            e.printStackTrace();
            // يمكنك عرض رسالة خطأ هنا
        }
    }

    @FXML private void showDashboard() { loadView("Dashboard.fxml", "لوحة التحكم", dashboardBtn); }
    @FXML private void showPurchases() { loadView("Purchases.fxml", "المشتريات", purchasesBtn); }
    @FXML private void showSales() { loadView("Sales.fxml", "المبيعات", salesBtn); }
    @FXML private void showInventory() { loadView("Inventory.fxml", "المخزون", inventoryBtn); }
    @FXML private void showExpenses() { loadView("Expenses.fxml", "المصروفات", expensesBtn); }
    @FXML private void handleManageContacts() { loadView("ContactManagement.fxml", "جهات التعامل", contactsBtn); }
    @FXML private void handleViewGeneralLedger() { loadView("GeneralLedgerView.fxml", "دفتر الأستاذ العام", generalLedgerBtn); }
    @FXML private void showReportsDashboard() { loadView("ReportsDashboard.fxml", "التقارير المتقدمة", reportsBtn); }
    @FXML private void handleSettings() { /* لا يوجد واجهة بعد */ }


    public void cleanup() {
        if (statusTimer != null) {
            statusTimer.cancel();
        }
    }
}