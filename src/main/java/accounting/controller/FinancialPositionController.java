package accounting.controller;

import accounting.model.FinancialAccount;
import accounting.util.FinancialSummaryService;
import accounting.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * وحدة التحكم الخاصة بواجهة عرض "المركز المالي".
 * تقوم بتجميع البيانات من FinancialSummaryService وعرضها في الواجهة.
 */
public class FinancialPositionController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(FinancialPositionController.class.getName());

    // الأصول
    @FXML private Label totalCashLabel;
    @FXML private TableView<FinancialAccount> cashAccountsTable;
    @FXML private TableColumn<FinancialAccount, String> cashAccountNameColumn;
    @FXML private TableColumn<FinancialAccount, Double> cashAccountBalanceColumn;
    @FXML private Label totalInventoryLabel;
    @FXML private PieChart inventoryPieChart;
    @FXML private Label totalReceivablesLabel;
    @FXML private ListView<String> topDebtorsListView;
    @FXML private Label totalAssetsLabel;

    // المطلوبات وحقوق الملكية
    @FXML private Label totalPayablesLabel;
    @FXML private ListView<String> topCreditorsListView;
    @FXML private Label openingCapitalLabel;
    @FXML private Label netProfitLabel;
    @FXML private Label totalLiabilitiesAndEquityLabel;

    private FinancialSummaryService summaryService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.summaryService = new FinancialSummaryService();
        setupTables();
        loadFinancialPositionData();
    }

    /**
     * إعداد أعمدة الجداول وتنسيقها.
     */
    private void setupTables() {
        cashAccountNameColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        cashAccountBalanceColumn.setCellValueFactory(new PropertyValueFactory<>("currentBalance")); // تم التعديل للاعتماد على حقل سيتم حسابه

        // تنسيق عمود الرصيد كعملة
        cashAccountBalanceColumn.setCellFactory(col -> new TableCell<>() { // تم إصلاح النوع واستخدام <>
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatCurrency(item));
                }
            }
        });
    }

    /**
     * تحميل جميع بيانات المركز المالي وعرضها في الواجهة.
     */
    private void loadFinancialPositionData() {
        try {
            // --- حساب وعرض الأصول ---
            double totalCash = summaryService.getTotalCashBalances();
            double totalInventory = summaryService.getTotalInventoryValue();
            double totalReceivables = summaryService.getTotalAccountsReceivable();
            double totalAssets = totalCash + totalInventory + totalReceivables;

            totalCashLabel.setText(FormatUtils.formatCurrency(totalCash));
            totalInventoryLabel.setText(FormatUtils.formatCurrency(totalInventory));
            totalReceivablesLabel.setText(FormatUtils.formatCurrency(totalReceivables));
            totalAssetsLabel.setText(FormatUtils.formatCurrency(totalAssets));
            
            // تعبئة جدول الحسابات النقدية
            populateCashDetails();

            // --- حساب وعرض المطلوبات وحقوق الملكية ---
            double totalPayables = summaryService.getTotalAccountsPayable();
            double netProfit = summaryService.getNetProfitForCurrentYear();
            double totalLiabilitiesAndEquity = totalPayables + netProfit; // يجب إضافة رأس المال لاحقاً

            totalPayablesLabel.setText(FormatUtils.formatCurrency(totalPayables));
            netProfitLabel.setText(FormatUtils.formatCurrency(netProfit));
            totalLiabilitiesAndEquityLabel.setText(FormatUtils.formatCurrency(totalLiabilitiesAndEquity));
            
            // تعبئة التفاصيل (الجداول والرسوم البيانية)
            populateInventoryChart();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load financial summary data.", e);
        }
    }
    
    /**
     * تعبئة جدول تفاصيل الحسابات النقدية.
     */
    private void populateCashDetails() throws SQLException {
         // ملاحظة: getCurrentBalance() في الخدمة سيقوم باللازم
        List<FinancialAccount> accounts = summaryService.getAccountBalances();
        cashAccountsTable.setItems(FXCollections.observableArrayList(accounts));
    }
    
    /**
     * تعبئة الرسم البياني الخاص بتوزيع قيمة المخزون.
     */
    private void populateInventoryChart() {
        try {
            // تم التعديل لاستدعاء الدالة العامة بدلاً من الوصول المباشر
            Map<String, Double> inventoryValues = summaryService.getInventoryDistribution();
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

            if (inventoryValues.isEmpty()) {
                pieChartData.add(new PieChart.Data("لا يوجد مخزون", 1));
            } else {
                inventoryValues.forEach((cropName, value) -> {
                    String label = String.format("%s (%s)", cropName, FormatUtils.formatCurrency(value));
                    pieChartData.add(new PieChart.Data(label, value));
                });
            }
            inventoryPieChart.setData(pieChartData);
        } catch (SQLException e) {
             LOGGER.log(Level.WARNING, "Failed to populate inventory pie chart.", e);
        }
    }
}