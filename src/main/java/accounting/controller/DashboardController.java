package accounting.controller;

import accounting.model.FinancialTransaction; // هذا السطر سيصبح غير مستخدم، ويمكن حذفه
import accounting.util.CropDataService;
import accounting.util.FinancialSummaryService;
import accounting.util.FormatUtils;
import accounting.util.PurchaseDataService;
import accounting.util.SaleDataService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
// import javafx.scene.control.cell.PropertyValueFactory; // لم نعد بحاجة لهذا

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DashboardController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());

    // خدمات البيانات
    private SaleDataService saleDataService;
    private PurchaseDataService purchaseDataService;
    private CropDataService cropDataService;
    private FinancialSummaryService summaryService;

    // تسميات الإحصائيات
    @FXML private Label totalRevenueLabel;
    @FXML private Label totalExpensesLabel;
    @FXML private Label netProfitLabel;
    @FXML private Label inventoryValueLabel;

    // الرسوم البيانية
    @FXML private BarChart<String, Number> monthlySalesChart;
    @FXML private CategoryAxis salesXAxis;
    @FXML private NumberAxis salesYAxis;
    @FXML private PieChart cropDistributionChart;
    
    // --- تم حذف كل ما يتعلق بالجدول من هنا ---
    // @FXML private TableView<FinancialTransaction> recentTransactionsTable;
    // @FXML private TableColumn<FinancialTransaction, LocalDate> transactionDateColumn;
    // ... إلخ

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.saleDataService = new SaleDataService();
        this.purchaseDataService = new PurchaseDataService();
        this.cropDataService = new CropDataService();
        this.summaryService = new FinancialSummaryService();
        
        // --- تم حذف استدعاء دالة إعداد الجدول ---
        // setupRecentTransactionsTable(); 
        
        setupCharts();
        loadDashboardData();
    }

    // --- تم حذف دالة setupRecentTransactionsTable بالكامل ---
    /*
    private void setupRecentTransactionsTable() {
        ...
    }
    */

    private void setupCharts() {
        salesXAxis.setLabel("الشهر");
        salesYAxis.setLabel("المبلغ");
        monthlySalesChart.setTitle("المبيعات الشهرية");
        cropDistributionChart.setTitle("توزيع قيمة المخزون حسب المحصول");
    }

    private void loadDashboardData() {
        updateStatistics();
        loadMonthlySalesChart();
        loadCropDistributionChart();
        // لم نعد بحاجة لتحميل بيانات الجدول
    }

    private void updateStatistics() {
        try {
            LocalDate today = LocalDate.now();
            LocalDate firstDayOfYear = today.withDayOfYear(1);

            Map<String, Double> salesStats = saleDataService.getSalesStatistics(firstDayOfYear, today);
            PurchaseDataService.PurchaseStatistics purchaseStats = purchaseDataService.getPurchaseStatistics(firstDayOfYear, today, null, null);
            
            double totalRevenue = salesStats.getOrDefault("total_revenue", 0.0);
            double totalExpenses = purchaseStats.getTotalCost();
            double netProfit = summaryService.getNetProfitForCurrentYear();
            
            double inventoryValue = cropDataService.getInventoryValuePerCrop().values().stream().mapToDouble(Double::doubleValue).sum();

            totalRevenueLabel.setText(FormatUtils.formatCurrency(totalRevenue));
            totalExpensesLabel.setText(FormatUtils.formatCurrency(totalExpenses));
            netProfitLabel.setText(FormatUtils.formatCurrency(netProfit));
            inventoryValueLabel.setText(FormatUtils.formatCurrency(inventoryValue));

            // تطبيق الأنماط الجديدة للألوان
            netProfitLabel.getStyleClass().removeAll("success-text", "danger-text");
            if (netProfit >= 0) {
                netProfitLabel.getStyleClass().add("success-text");
            } else {
                netProfitLabel.getStyleClass().add("danger-text");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "فشل في تحديث الإحصائيات", e);
            // يمكنك إظهار رسالة خطأ هنا
        }
    }

    private void loadMonthlySalesChart() {
        try {
            int currentYear = LocalDate.now().getYear();
            Map<String, Number> monthlyData = saleDataService.getMonthlySalesForChart(currentYear);
            
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("مبيعات " + currentYear);

            Map<String, String> monthNames = new HashMap<>();
            for (int i = 1; i <= 12; i++) {
                monthNames.put(String.format("%02d", i), java.time.Month.of(i).getDisplayName(TextStyle.FULL, new Locale("ar")));
            }
            
            monthlyData.forEach((monthNumber, total) -> {
                series.getData().add(new XYChart.Data<>(monthNames.get(monthNumber), total));
            });
            
            monthlySalesChart.getData().clear();
            monthlySalesChart.getData().add(series);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "فشل في تحميل الرسم البياني للمبيعات", e);
        }
    }

    private void loadCropDistributionChart() {
        try {
            Map<String, Double> inventoryValues = cropDataService.getInventoryValuePerCrop();
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

            if (inventoryValues.isEmpty()) {
                pieChartData.add(new PieChart.Data("لا يوجد مخزون", 1));
            } else {
                inventoryValues.forEach((cropName, value) -> {
                    pieChartData.add(new PieChart.Data(cropName + " (" + FormatUtils.formatCurrency(value) + ")", value));
                });
            }
            
            cropDistributionChart.setData(pieChartData);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "فشل في تحميل الرسم البياني للمخزون", e);
        }
    }
}