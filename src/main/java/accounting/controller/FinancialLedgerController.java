package accounting.controller;

import accounting.model.FinancialAccount;
import accounting.model.FinancialTransaction;
import accounting.util.FinancialAccountDataService;
import accounting.util.FinancialTransactionDataService;
import accounting.util.FormatUtils;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

public class FinancialLedgerController implements Initializable {

    @FXML private ListView<FinancialAccount> accountsListView;
    @FXML private Label accountNameLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private TableView<TransactionRow> transactionsTableView;
    @FXML private TableColumn<TransactionRow, LocalDate> dateColumn;
    @FXML private TableColumn<TransactionRow, String> descriptionColumn;
    @FXML private TableColumn<TransactionRow, Double> debitColumn;
    @FXML private TableColumn<TransactionRow, Double> creditColumn;
    @FXML private TableColumn<TransactionRow, Double> balanceColumn;

    private FinancialAccountDataService accountService;
    private FinancialTransactionDataService transactionService;

    private ObservableList<FinancialAccount> accountsList;
    private ObservableList<TransactionRow> transactionRowsList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.accountService = new FinancialAccountDataService();
        this.transactionService = new FinancialTransactionDataService();
        
        accountsList = FXCollections.observableArrayList();
        transactionRowsList = FXCollections.observableArrayList();
        
        setupAccountsListView();
        setupTransactionsTableView();
        
        loadAccounts();
    }

    private void setupAccountsListView() {
        accountsListView.setItems(accountsList);
        accountsListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    loadAccountTransactions(newSelection);
                }
            }
        );
    }
    
    private void setupTransactionsTableView() {
        transactionsTableView.setItems(transactionRowsList);
        
        dateColumn.setCellValueFactory(cellData -> cellData.getValue().getTransaction().transactionDateProperty());
        descriptionColumn.setCellValueFactory(cellData -> cellData.getValue().getTransaction().descriptionProperty());
        
        debitColumn.setCellValueFactory(cellData -> {
            double amount = cellData.getValue().getTransaction().getAmount();
            return amount < 0 ? new SimpleDoubleProperty(Math.abs(amount)).asObject() : null;
        });
        
        creditColumn.setCellValueFactory(cellData -> {
            double amount = cellData.getValue().getTransaction().getAmount();
            return amount >= 0 ? new SimpleDoubleProperty(amount).asObject() : null;
        });

        balanceColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().getRunningBalance()).asObject());
        
        // Format cells
        formatCurrencyCell(debitColumn);
        formatCurrencyCell(creditColumn);
        formatCurrencyCell(balanceColumn);
    }

    private void loadAccounts() {
        try {
            accountsList.setAll(accountService.getAllAccounts());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadAccountTransactions(FinancialAccount account) {
        accountNameLabel.setText("كشف حساب: " + account.getAccountName());
        transactionRowsList.clear();

        try {
            List<FinancialTransaction> transactions = transactionService.getTransactions(null, null, account, null, false);
            transactions.sort(Comparator.comparing(FinancialTransaction::getTransactionDate));
            
            double runningBalance = account.getOpeningBalance();
            
            // Add opening balance as the first row
            transactionRowsList.add(new TransactionRow(
                createOpeningBalanceTransaction(account), account.getOpeningBalance()
            ));

            for (FinancialTransaction t : transactions) {
                runningBalance += t.getAmount();
                transactionRowsList.add(new TransactionRow(t, runningBalance));
            }

            currentBalanceLabel.setText("الرصيد الحالي: " + FormatUtils.formatCurrency(runningBalance));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private FinancialTransaction createOpeningBalanceTransaction(FinancialAccount account) {
        FinancialTransaction opening = new FinancialTransaction();
        opening.setTransactionDate(account.getOpeningBalanceDate());
        opening.setDescription("رصيد افتتاحي");
        opening.setAmount(account.getOpeningBalance());
        return opening;
    }

    private void formatCurrencyCell(TableColumn<TransactionRow, Double> column) {
        column.setCellFactory(col -> new TableCell<>() {
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

    // Helper class to hold a transaction and its running balance
    public static class TransactionRow {
        private final FinancialTransaction transaction;
        private final double runningBalance;

        public TransactionRow(FinancialTransaction transaction, double runningBalance) {
            this.transaction = transaction;
            this.runningBalance = runningBalance;
        }

        public FinancialTransaction getTransaction() { return transaction; }
        public double getRunningBalance() { return runningBalance; }
    }
}