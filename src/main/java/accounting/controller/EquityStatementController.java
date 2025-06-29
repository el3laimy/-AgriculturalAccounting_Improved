package accounting.controller;

import accounting.util.FinancialSummaryService;
import accounting.util.FormatUtils;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import java.sql.SQLException;
import java.time.LocalDate;

public class EquityStatementController {

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button generateReportBtn;
    @FXML private Label beginningEquityLabel;
    @FXML private Label netProfitLabel;
    @FXML private Label capitalAdditionsLabel;
    @FXML private Label ownerWithdrawalsLabel;
    @FXML private Label endingEquityLabel;
    @FXML private LineChart<String, Number> equityChart;

    private final FinancialSummaryService summaryService = new FinancialSummaryService();

    @FXML
    private void handleGenerateReport() {
        LocalDate startDate = fromDatePicker.getValue();
        LocalDate endDate = toDatePicker.getValue();

        if (startDate == null || endDate == null) {
            // أظهر رسالة خطأ للمستخدم
            return;
        }

        try {
            FinancialSummaryService.EquityStatement statement = summaryService.generateEquityStatement(startDate, endDate);
            
            // تعبئة البيانات في الواجهة
            beginningEquityLabel.setText(FormatUtils.formatCurrency(statement.beginningEquity));
            netProfitLabel.setText(FormatUtils.formatCurrency(statement.periodNetProfit));
            capitalAdditionsLabel.setText(FormatUtils.formatCurrency(statement.capitalAdditions));
            ownerWithdrawalsLabel.setText(FormatUtils.formatCurrency(statement.ownerWithdrawals));
            endingEquityLabel.setText(FormatUtils.formatCurrency(statement.endingEquity));
            
            // (اختياري) تحديث الرسم البياني
            updateChart(statement);

        } catch (SQLException e) {
            e.printStackTrace();
            // أظهر رسالة خطأ للمستخدم
        }
    }
    
    private void updateChart(FinancialSummaryService.EquityStatement statement) {
        equityChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("حقوق الملكية");
        
        series.getData().add(new XYChart.Data<>("بداية الفترة", statement.beginningEquity));
        series.getData().add(new XYChart.Data<>("نهاية الفترة", statement.endingEquity));
        
        equityChart.getData().add(series);
    }
}