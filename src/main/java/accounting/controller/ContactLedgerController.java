package accounting.controller;

import accounting.model.Contact;
import accounting.model.FinancialAccount;
import accounting.model.FinancialTransaction;
import accounting.model.LedgerEntry;
import accounting.model.PurchaseRecord;
import accounting.model.SaleRecord;
import accounting.util.FinancialAccountDataService;
import accounting.util.FinancialTransactionDataService;
import accounting.util.FormatUtils;
import accounting.util.PurchaseDataService;
import accounting.util.SaleDataService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.util.Pair;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ContactLedgerController {

    @FXML private Label contactNameLabel;
    @FXML private Label contactTypeLabel;
    @FXML private Label balanceLabel;
    @FXML private Button addPaymentButton; // <<< تأكد من وجود هذا الزر في ملف FXML
    @FXML private TableView<LedgerEntry> ledgerTable;
    @FXML private TableColumn<LedgerEntry, LocalDate> dateColumn;
    @FXML private TableColumn<LedgerEntry, String> descriptionColumn;
    @FXML private TableColumn<LedgerEntry, String> referenceColumn;
    @FXML private TableColumn<LedgerEntry, Double> debitColumn;
    @FXML private TableColumn<LedgerEntry, Double> creditColumn;
    @FXML private TableColumn<LedgerEntry, Double> balanceColumn;

    private Contact contact;
    private final ObservableList<LedgerEntry> ledgerEntries = FXCollections.observableArrayList();

    private final PurchaseDataService purchaseService = new PurchaseDataService();
    private final SaleDataService saleService = new SaleDataService();
    private final FinancialTransactionDataService transactionService = new FinancialTransactionDataService();
    private final FinancialAccountDataService accountService = new FinancialAccountDataService();

    public void setContact(Contact contact) {
        this.contact = contact;
        contactNameLabel.setText("كشف حساب: " + contact.getName());
        contactTypeLabel.setText("النوع: " + contact.getContactType());
        loadLedgerData();
    }

    @FXML
    private void initialize() {
        setupTable();
        // ربط الحدث بالزر
        addPaymentButton.setOnAction(e -> handleAddPayment());
    }

    private void setupTable() {
        ledgerTable.setItems(ledgerEntries);
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        referenceColumn.setCellValueFactory(new PropertyValueFactory<>("reference"));
        debitColumn.setCellValueFactory(new PropertyValueFactory<>("debit"));
        creditColumn.setCellValueFactory(new PropertyValueFactory<>("credit"));
        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("balance"));
        
        formatDateCell(dateColumn);
        formatCurrencyCell(debitColumn);
        formatCurrencyCell(creditColumn);
        formatBalanceCell(balanceColumn);
    }

    @FXML
    private void handleAddPayment() {
        if (contact == null) return;

        // إنشاء نافذة منبثقة مخصصة لإدخال الدفعة
        Dialog<FinancialTransaction> dialog = new Dialog<>();
        dialog.setTitle("تسجيل دفعة");
        dialog.setHeaderText("تسجيل دفعة لـ: " + contact.getName());

        ButtonType saveButtonType = new ButtonType("حفظ", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField amountField = new TextField();
        amountField.setPromptText("المبلغ");
        ComboBox<FinancialAccount> accountsComboBox = new ComboBox<>();
        
        try {
            accountsComboBox.setItems(FXCollections.observableArrayList(accountService.getAllAccounts()));
        } catch (SQLException e) {
             e.printStackTrace(); // Handle exception
        }
        accountsComboBox.setPromptText("اختر حساب الدفع");


        grid.add(new Label("التاريخ:"), 0, 0);
        grid.add(datePicker, 1, 0);
        grid.add(new Label("المبلغ:"), 0, 1);
        grid.add(amountField, 1, 1);
        grid.add(new Label("من/إلى حساب:"), 0, 2);
        grid.add(accountsComboBox, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // تحويل النتيجة عند الضغط على حفظ
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    FinancialTransaction transaction = new FinancialTransaction();
                    transaction.setTransactionDate(datePicker.getValue());
                    
                    // المبلغ يكون بالسالب للمورد (مصروف) وبالموجب للعميل (إيراد)
                    double amount = Double.parseDouble(amountField.getText());
                    transaction.setAmount(contact.isSupplier() ? -Math.abs(amount) : Math.abs(amount));

                    transaction.setAccount(accountsComboBox.getValue());
                    transaction.setRelatedContact(contact);
                    transaction.setTransactionType(contact.isSupplier() ? "دفعة لمورد" : "دفعة من عميل");
                    transaction.setDescription(transaction.getTransactionType() + ": " + contact.getName());
                    
                    return transaction;
                } catch (NumberFormatException e) {
                    // يمكنك إظهار رسالة خطأ هنا
                    return null;
                }
            }
            return null;
        });

        Optional<FinancialTransaction> result = dialog.showAndWait();

        result.ifPresent(transaction -> {
            try {
                transactionService.addTransaction(transaction);
                loadLedgerData(); // تحديث كشف الحساب فوراً
            } catch (SQLException e) {
                e.printStackTrace(); // يمكنك إظهار رسالة خطأ للمستخدم
            }
        });
    }

    private void loadLedgerData() {
        if (contact == null) return;

        List<LedgerEntry> allEntries = new ArrayList<>();

        try {
            // 1. Get all purchases for this contact (supplier)
            if (contact.isSupplier()) {
                List<PurchaseRecord> purchases = purchaseService.getPurchases(null, null, null, contact.getContactId(), 0, 0);
                for (PurchaseRecord p : purchases) {
                    // A purchase means the contact (supplier) is owed money (credit)
                    allEntries.add(new LedgerEntry(p.getPurchaseDate(), "فاتورة شراء " + p.getCrop().getCropName(), p.getInvoiceNumber(), 0, p.getTotalCost()));
                }
            }

            // 2. Get all sales for this contact (customer)
            if (contact.isCustomer()) {
                List<SaleRecord> sales = saleService.getSales(null, null, null, contact.getContactId(), 0, 0);
                for (SaleRecord s : sales) {
                    // A sale means the contact (customer) owes us money (debit)
                    allEntries.add(new LedgerEntry(s.getSaleDate(), "فاتورة بيع " + s.getCrop().getCropName(), s.getSaleInvoiceNumber(), s.getTotalSaleAmount(), 0));
                }
            }
            
            // 3. Get all financial transactions (payments) for this contact
            List<FinancialTransaction> transactions = transactionService.getTransactionsByContact(contact.getContactId());
            for(FinancialTransaction t : transactions) {
                if(t.getAmount() > 0) { // Payment received from a customer (Credit to their account)
                    allEntries.add(new LedgerEntry(t.getTransactionDate(), t.getDescription(), t.getTransactionType(), 0, t.getAmount()));
                } else { // Payment made to a supplier (Debit to their account)
                    allEntries.add(new LedgerEntry(t.getTransactionDate(), t.getDescription(), t.getTransactionType(), Math.abs(t.getAmount()), 0));
                }
            }

            // 4. Sort all entries by date
            allEntries.sort(Comparator.comparing(LedgerEntry::getDate));

            // 5. Calculate running balance
            double runningBalance = 0;
            for (LedgerEntry entry : allEntries) {
                // Balance increases with debits (what they owe us) and decreases with credits (what we owe them or what they paid)
                runningBalance += entry.getDebit() - entry.getCredit();
                entry.setBalance(runningBalance);
            }
            
            // 6. Populate the table
            ledgerEntries.setAll(allEntries);
            balanceLabel.setText("الرصيد النهائي: " + FormatUtils.formatCurrency(runningBalance));

        } catch (SQLException e) {
            e.printStackTrace();
            // You can show an error alert to the user here
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
    
    private void formatBalanceCell(TableColumn<LedgerEntry, Double> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                
                getStyleClass().removeAll("positive-balance", "negative-balance");

                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatCurrency(item));
                    // If balance > 0, the contact owes us (good for us)
                    if (item > 0) {
                        getStyleClass().add("positive-balance");
                    } 
                    // If balance < 0, we owe the contact (bad for us)
                    else if (item < 0) {
                        getStyleClass().add("negative-balance");
                    }
                }
            }
        });
    }
}