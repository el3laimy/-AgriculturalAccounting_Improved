package accounting.controller;

import accounting.model.BalanceSheet;
import accounting.util.FinancialSummaryService;
import accounting.util.FormatUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

public class BalanceSheetController {

    @FXML private DatePicker toDatePicker;
    @FXML private Button viewButton;
    @FXML private VBox assetsVBox;
    @FXML private VBox liabilitiesAndEquityVBox;

    private final FinancialSummaryService summaryService = new FinancialSummaryService();

    @FXML
    public void initialize() {
        toDatePicker.setValue(LocalDate.now());
        viewButton.setOnAction(e -> loadData());
        loadData();
    }

    private void loadData() {
        LocalDate toDate = toDatePicker.getValue();
        if (toDate == null) return;

        try {
            BalanceSheet statement = summaryService.getBalanceSheet(toDate);
            populateReport(statement);
        } catch (SQLException e) {
            e.printStackTrace();
            // Show error alert
        }
    }

    private void populateReport(BalanceSheet statement) {
        // Clear previous data
        assetsVBox.getChildren().subList(1, assetsVBox.getChildren().size()).clear();
        liabilitiesAndEquityVBox.getChildren().subList(1, liabilitiesAndEquityVBox.getChildren().size()).clear();

        // Populate Assets
        for (Map.Entry<String, Double> entry : statement.getAssets().entrySet()) {
            assetsVBox.getChildren().add(new Label(entry.getKey() + ": " + FormatUtils.formatCurrency(entry.getValue())));
        }
        Label totalAssetsLabel = new Label("إجمالي الأصول: " + FormatUtils.formatCurrency(statement.getTotalAssets()));
        totalAssetsLabel.setStyle("-fx-font-weight: bold; -fx-padding-top: 10px;");
        assetsVBox.getChildren().add(totalAssetsLabel);

        // Populate Liabilities
        Label liabilitiesHeader = new Label("الخصوم:");
        liabilitiesHeader.setStyle("-fx-font-weight: bold; -fx-underline: true;");
        liabilitiesAndEquityVBox.getChildren().add(liabilitiesHeader);
        for (Map.Entry<String, Double> entry : statement.getLiabilities().entrySet()) {
            liabilitiesAndEquityVBox.getChildren().add(new Label("  " + entry.getKey() + ": " + FormatUtils.formatCurrency(entry.getValue())));
        }

        // Populate Equity
        Label equityHeader = new Label("حقوق الملكية:");
        equityHeader.setStyle("-fx-font-weight: bold; -fx-underline: true; -fx-padding-top: 10px;");
        liabilitiesAndEquityVBox.getChildren().add(equityHeader);
        for (Map.Entry<String, Double> entry : statement.getEquity().entrySet()) {
            liabilitiesAndEquityVBox.getChildren().add(new Label("  " + entry.getKey() + ": " + FormatUtils.formatCurrency(entry.getValue())));
        }
        
        Label totalLiabilitiesAndEquityLabel = new Label("إجمالي الخصوم وحقوق الملكية: " + FormatUtils.formatCurrency(statement.getTotalLiabilitiesAndEquity()));
        totalLiabilitiesAndEquityLabel.setStyle("-fx-font-weight: bold; -fx-padding-top: 10px;");
        liabilitiesAndEquityVBox.getChildren().add(totalLiabilitiesAndEquityLabel);
    }
}