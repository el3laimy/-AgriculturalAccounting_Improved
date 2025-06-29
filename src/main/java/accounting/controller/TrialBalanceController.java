package accounting.controller;

import accounting.model.TrialBalanceEntry;
import accounting.util.FinancialSummaryService;
import accounting.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class TrialBalanceController {

    @FXML private DatePicker toDatePicker;
    @FXML private Button viewButton;
    @FXML private TableView<TrialBalanceEntry> trialBalanceTable;
    @FXML private TableColumn<TrialBalanceEntry, Integer> accountIdColumn;
    @FXML private TableColumn<TrialBalanceEntry, String> accountNameColumn;
    @FXML private TableColumn<TrialBalanceEntry, Double> debitColumn;
    @FXML private TableColumn<TrialBalanceEntry, Double> creditColumn;
    @FXML private Label totalDebitLabel;
    @FXML private Label totalCreditLabel;
    @FXML private Label statusLabel;

    private FinancialSummaryService summaryService;
    private ObservableList<TrialBalanceEntry> trialBalanceList;

    @FXML
    public void initialize() {
        this.summaryService = new FinancialSummaryService();
        this.trialBalanceList = FXCollections.observableArrayList();
        
        toDatePicker.setValue(LocalDate.now());
        setupTable();
        
        viewButton.setOnAction(e -> loadData());
        loadData(); // تحميل البيانات عند فتح الشاشة لأول مرة
    }

    private void setupTable() {
        trialBalanceTable.setItems(trialBalanceList);
        accountIdColumn.setCellValueFactory(new PropertyValueFactory<>("accountId"));
        accountNameColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        debitColumn.setCellValueFactory(new PropertyValueFactory<>("totalDebit"));
        creditColumn.setCellValueFactory(new PropertyValueFactory<>("totalCredit"));

        formatCurrencyCell(debitColumn);
        formatCurrencyCell(creditColumn);
    }

    private void loadData() {
        LocalDate toDate = toDatePicker.getValue();
        if (toDate == null) return;
        
        try {
            List<TrialBalanceEntry> data = summaryService.getTrialBalance(toDate);
            trialBalanceList.setAll(data);
            updateTotals();
        } catch (SQLException e) {
            e.printStackTrace(); // عرض خطأ للمستخدم
        }
    }

    private void updateTotals() {
        double totalDebit = trialBalanceList.stream().mapToDouble(TrialBalanceEntry::getTotalDebit).sum();
        double totalCredit = trialBalanceList.stream().mapToDouble(TrialBalanceEntry::getTotalCredit).sum();

        totalDebitLabel.setText("الإجمالي المدين: " + FormatUtils.formatCurrency(totalDebit));
        totalCreditLabel.setText("الإجمالي الدائن: " + FormatUtils.formatCurrency(totalCredit));

        if (Math.abs(totalDebit - totalCredit) < 0.01) {
            statusLabel.setText("الحالة: متزن");
            statusLabel.setTextFill(Color.GREEN);
        } else {
            statusLabel.setText("الحالة: غير متزن!");
            statusLabel.setTextFill(Color.RED);
        }
    }

    private void formatCurrencyCell(TableColumn<TrialBalanceEntry, Double> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : FormatUtils.formatCurrency(item));
            }
        });
    }
}