package accounting.controller;

import accounting.model.FinancialAccount;
import accounting.model.FinancialTransaction;
import accounting.util.FinancialTransactionDataService;
import accounting.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExpensesController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ExpensesController.class.getName());

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button filterBtn;
    @FXML private Button clearFilterBtn;
    @FXML private TableView<FinancialTransaction> expensesTable;
    @FXML private TableColumn<FinancialTransaction, LocalDate> dateColumn;
    @FXML private TableColumn<FinancialTransaction, String> accountColumn;
    @FXML private TableColumn<FinancialTransaction, String> typeColumn;
    @FXML private TableColumn<FinancialTransaction, String> descriptionColumn;
    @FXML private TableColumn<FinancialTransaction, Double> amountColumn;
    @FXML private Button addExpenseBtn;
    @FXML private Button editExpenseBtn;
    @FXML private Button deleteExpenseBtn;
    @FXML private Label recordCountLabel;
    @FXML private Label totalAmountLabel;

    private FinancialTransactionDataService transactionDataService;
    private ObservableList<FinancialTransaction> expensesList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        transactionDataService = new FinancialTransactionDataService();
        setupTable();
        setupEventHandlers();
        loadData();
    }

    private void setupTable() {
        expensesList = FXCollections.observableArrayList();
        expensesTable.setItems(expensesList);

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        dateColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : FormatUtils.formatDateForDisplay(item));
            }
        });

        accountColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getAccount().getAccountName())
        );
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(FormatUtils.formatCurrency(Math.abs(item))); // عرض القيمة المطلقة
                    setStyle("-fx-text-fill: red;"); // اللون الأحمر للمصروفات
                }
            }
        });
    }

    private void setupEventHandlers() {
        filterBtn.setOnAction(e -> loadData());
        clearFilterBtn.setOnAction(e -> {
            fromDatePicker.setValue(null);
            toDatePicker.setValue(null);
            loadData();
        });
        addExpenseBtn.setOnAction(e -> handleAddExpense());
    }

    private void loadData() {
        try {
            List<FinancialTransaction> data = transactionDataService.getTransactions(
                fromDatePicker.getValue(),
                toDatePicker.getValue(),
                null, // لا فلترة بالحساب حاليا
                null, // لا فلترة بالنوع حاليا
                true  // جلب المصروفات العامة فقط
            );
            expensesList.setAll(data);
            updateStatistics();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load expenses", e);
            showErrorAlert("خطأ في قاعدة البيانات", "فشل تحميل بيانات المصروفات.");
        }
    }

    private void updateStatistics() {
        recordCountLabel.setText(expensesList.size() + " سجل");
        double total = expensesList.stream().mapToDouble(FinancialTransaction::getAmount).sum();
        totalAmountLabel.setText("إجمالي المصروفات: " + FormatUtils.formatCurrency(Math.abs(total)));
    }

    @FXML
    private void handleAddExpense() {
        FinancialTransaction newTransaction = new FinancialTransaction();
        boolean okClicked = showTransactionDialog(newTransaction, "إضافة مصروف جديد");
        if (okClicked) {
            loadData();
        }
    }

    private boolean showTransactionDialog(FinancialTransaction transaction, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/FinancialTransactionForm.fxml"));
            AnchorPane page = loader.load();
            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);
            
            FinancialTransactionFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setTransaction(transaction);
            
            dialogStage.showAndWait();
            return controller.isOkClicked();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load transaction form", e);
            return false;
        }
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}