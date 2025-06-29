package accounting.controller;

import accounting.model.LedgerEntry;
import accounting.util.FinancialTransactionDataService;
import accounting.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class GeneralLedgerController implements Initializable {

    // FXML Components
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button filterButton;
    @FXML private Button clearButton;
    @FXML private TableView<LedgerEntry> ledgerTable;
    @FXML private TableColumn<LedgerEntry, LocalDate> dateColumn;
    @FXML private TableColumn<LedgerEntry, String> refColumn;
    @FXML private TableColumn<LedgerEntry, String> descriptionColumn;
    @FXML private TableColumn<LedgerEntry, Double> debitColumn;
    @FXML private TableColumn<LedgerEntry, Double> creditColumn;
    @FXML private Label totalDebitLabel;
    @FXML private Label totalCreditLabel;
    @FXML private Label balanceLabel;

    // Services and Data
    private FinancialTransactionDataService transactionService;
    private ObservableList<LedgerEntry> ledgerEntries;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.transactionService = new FinancialTransactionDataService();
        this.ledgerEntries = FXCollections.observableArrayList();
        
        setupTable();
        setupEventHandlers();
        loadData();
    }

    private void setupTable() {
        ledgerTable.setItems(ledgerEntries);

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        refColumn.setCellValueFactory(new PropertyValueFactory<>("reference"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        debitColumn.setCellValueFactory(new PropertyValueFactory<>("debit"));
        creditColumn.setCellValueFactory(new PropertyValueFactory<>("credit"));

        // Format date and currency cells
        formatDateCell(dateColumn);
        formatCurrencyCell(debitColumn);
        formatCurrencyCell(creditColumn);
    }

    private void setupEventHandlers() {
        filterButton.setOnAction(e -> loadData());
        clearButton.setOnAction(e -> {
            fromDatePicker.setValue(null);
            toDatePicker.setValue(null);
            loadData();
        });
    }

    private void loadData() {
        try {
            List<LedgerEntry> data = transactionService.getGeneralLedgerEntries(
                fromDatePicker.getValue(),
                toDatePicker.getValue()
            );
            ledgerEntries.setAll(data);
            updateTotals();
        } catch (SQLException e) {
            e.printStackTrace();
            // Show error alert to the user
        }
    }
    
    private void updateTotals() {
        double totalDebit = ledgerEntries.stream().mapToDouble(LedgerEntry::getDebit).sum();
        double totalCredit = ledgerEntries.stream().mapToDouble(LedgerEntry::getCredit).sum();

        totalDebitLabel.setText("إجمالي المدين: " + FormatUtils.formatCurrency(totalDebit));
        totalCreditLabel.setText("إجمالي الدائن: " + FormatUtils.formatCurrency(totalCredit));

        if (Math.abs(totalDebit - totalCredit) < 0.01) {
            balanceLabel.setText("الحالة: متزن");
            balanceLabel.setTextFill(Color.GREEN);
        } else {
            balanceLabel.setText("الحالة: غير متزن");
            balanceLabel.setTextFill(Color.RED);
        }
    }

    private void formatDateCell(TableColumn<LedgerEntry, LocalDate> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : FormatUtils.formatDateForDisplay(item));
            }
        });
    }

    private void formatCurrencyCell(TableColumn<LedgerEntry, Double> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item == 0) {
                    setText("");
                } else {
                    setText(FormatUtils.formatCurrency(item));
                }
            }
        });
    }
}