package accounting.controller;

import accounting.model.IncomeStatement;
import accounting.util.FinancialSummaryService;
import accounting.util.FormatUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

public class IncomeStatementController {

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button viewButton;
    @FXML private VBox revenueVBox;
    @FXML private VBox expenseVBox;
    @FXML private Label netIncomeLabel;

    private final FinancialSummaryService summaryService = new FinancialSummaryService();

    @FXML
    public void initialize() {
        fromDatePicker.setValue(LocalDate.now().withDayOfYear(1));
        toDatePicker.setValue(LocalDate.now());
        viewButton.setOnAction(e -> loadData());
        loadData();
    }

    private void loadData() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        if (from == null || to == null) return;

        try {
            IncomeStatement statement = summaryService.getIncomeStatement(from, to);
            populateReport(statement);
        } catch (SQLException e) {
            e.printStackTrace();
            // Show error alert
        }
    }

    private void populateReport(IncomeStatement statement) {
        // Clear previous data
        revenueVBox.getChildren().subList(1, revenueVBox.getChildren().size()).clear();
        expenseVBox.getChildren().subList(1, expenseVBox.getChildren().size()).clear();

        // Populate revenues
        for (Map.Entry<String, Double> entry : statement.getRevenueDetails().entrySet()) {
            revenueVBox.getChildren().add(new Label(entry.getKey() + ": " + FormatUtils.formatCurrency(entry.getValue())));
        }
        revenueVBox.getChildren().add(new Label("إجمالي الإيرادات: " + FormatUtils.formatCurrency(statement.getTotalRevenue())));

        // Populate expenses
        for (Map.Entry<String, Double> entry : statement.getExpenseDetails().entrySet()) {
            expenseVBox.getChildren().add(new Label(entry.getKey() + ": " + FormatUtils.formatCurrency(entry.getValue())));
        }
        expenseVBox.getChildren().add(new Label("إجمالي المصروفات: " + FormatUtils.formatCurrency(statement.getTotalExpenses())));

        // Populate net income
        double netIncome = statement.getNetIncome();
        netIncomeLabel.setText(FormatUtils.formatCurrency(netIncome));
        netIncomeLabel.setTextFill(netIncome >= 0 ? Color.GREEN : Color.RED);
    }
}