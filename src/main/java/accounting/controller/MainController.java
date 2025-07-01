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
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Alert;

public class MainController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

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
    @FXML private Button alertsBtn; // New button for Alerts
    // Removed settingsBtn as it's replaced by a MenuButton

    // --- New MenuButtons and MenuItems ---
    @FXML private javafx.scene.control.MenuButton financialReportsMenuBtn;
    @FXML private MenuItem trialBalanceMenuItem;
    @FXML private MenuItem incomeStatementMenuItem;
    @FXML private MenuItem balanceSheetMenuItem;

    @FXML private javafx.scene.control.MenuButton settingsMenuBtn;
    @FXML private MenuItem cropManagementMenuItem;
    @FXML private MenuItem financialAccountManagementMenuItem;
    
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
            if (activeButton != null) {
                if (currentActiveButton != null) {
                    currentActiveButton.getStyleClass().remove("active");
                }
                currentActiveButton = activeButton;
                currentActiveButton.getStyleClass().add("active");
            } else { // A MenuItem was clicked, so no button should be active
                 if (currentActiveButton != null) {
                    currentActiveButton.getStyleClass().remove("active");
                    currentActiveButton = null;
                }
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML view: " + fxmlPath, e);
            showErrorAlert("خطأ في تحميل الواجهة",
                           "لم يتم تحميل الواجهة المطلوبة (" + title + "). قد يكون الملف مفقوداً أو تالفاً.\n" +
                           "الرجاء مراجعة سجلات البرنامج لمزيد من التفاصيل.");
            // Optionally, load a default error view or clear the content area
            contentArea.getChildren().clear();
            viewTitleLabel.setText("خطأ");
        } catch (Exception e) { // Catch any other unexpected errors during view loading
            LOGGER.log(Level.SEVERE, "Unexpected error loading FXML view: " + fxmlPath, e);
            showErrorAlert("خطأ غير متوقع",
                           "حدث خطأ غير متوقع أثناء محاولة تحميل الواجهة: " + title + ".");
            contentArea.getChildren().clear();
            viewTitleLabel.setText("خطأ");
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        // It's good practice to set the owner if this alert can pop up without a clear parent stage context
        // However, in MainController, contentArea.getScene().getWindow() might be null if called too early
        // or if the scene isn't fully set up. For now, not setting owner here.
        alert.showAndWait();
    }

    // Existing handlers
    @FXML private void showDashboard() { loadView("Dashboard.fxml", "لوحة التحكم", dashboardBtn); }
    @FXML private void showPurchases() { loadView("Purchases.fxml", "المشتريات", purchasesBtn); }
    @FXML private void showSales() { loadView("Sales.fxml", "المبيعات", salesBtn); }
    @FXML private void showInventory() { loadView("Inventory.fxml", "المخزون", inventoryBtn); }
    @FXML private void showExpenses() { loadView("Expenses.fxml", "المصروفات", expensesBtn); }
    @FXML private void handleManageContacts() { loadView("ContactManagement.fxml", "جهات التعامل", contactsBtn); }
    @FXML private void handleViewGeneralLedger() { loadView("GeneralLedgerView.fxml", "دفتر الأستاذ العام", generalLedgerBtn); }
    @FXML private void showReportsDashboard() { loadView("ReportsDashboard.fxml", "التقارير المتقدمة", reportsBtn); }

    // Handler for the new Alerts button
    @FXML private void handleShowAlerts() { loadView("AlertsView.fxml", "التنبيهات", alertsBtn); }
    // Removed handleSettings as it's replaced by MenuButton items

    // --- New Handlers for MenuItems ---
    @FXML private void handleShowTrialBalance() { loadView("TrialBalanceView.fxml", "ميزان المراجعة", null); }
    @FXML private void handleShowIncomeStatement() { loadView("IncomeStatementView.fxml", "قائمة الدخل", null); }
    @FXML private void handleShowBalanceSheet() { loadView("BalanceSheetView.fxml", "الميزانية العمومية", null); }
    @FXML private void handleShowCropManagement() { loadView("CropManagement.fxml", "إدارة المحاصيل", null); }
    @FXML private void handleShowFinancialAccountManagement() { loadView("FinancialAccountManagement.fxml", "إدارة الحسابات المالية", null); }

    public void cleanup() {
        if (statusTimer != null) {
            statusTimer.cancel();
        }
    }
}