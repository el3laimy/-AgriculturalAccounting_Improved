package accounting.controller;

import accounting.model.Contact;
import accounting.model.FinancialAccount;
import accounting.model.FinancialTransaction;
import accounting.util.ContactDataService;
import accounting.util.FinancialAccountDataService;
import accounting.util.FinancialTransactionDataService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class FinancialTransactionFormController {

    @FXML private RadioButton expenseRadioButton;
    @FXML private RadioButton incomeRadioButton;
    @FXML private ToggleGroup transactionToggleGroup;
    @FXML private DatePicker transactionDatePicker;
    @FXML private ComboBox<FinancialAccount> accountComboBox;
    @FXML private Label categoryLabel;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private ComboBox<Contact> contactComboBox;
    @FXML private TextField amountField;
    @FXML private TextArea descriptionArea;

    private Stage dialogStage;
    private FinancialTransaction transaction;
    private boolean okClicked = false;

    private FinancialAccountDataService accountDataService;
    private ContactDataService contactDataService;
    private FinancialTransactionDataService transactionDataService;
    
    // قوائم الفئات
    private final List<String> expenseCategories = List.of(
    	    "مصروفات إدارية", "أجور ورواتب", "صيانة", "نقل ومواصلات", 
    	    "فواتير ومرافق", "مسحوبات شخصية", "مصروفات أخرى" // <-- تمت إضافة "مسحوبات شخصية"
    	);
    	private final List<String> incomeCategories = List.of(
    	    "إيرادات متنوعة", "بيع أصول", "دعم", "إضافة رأس مال", "إيرادات أخرى" // <-- تمت إضافة "إضافة رأس مال"
    	);
    
    @FXML
    private void initialize() {
        this.accountDataService = new FinancialAccountDataService();
        this.contactDataService = new ContactDataService();
        this.transactionDataService = new FinancialTransactionDataService();
        
        loadComboBoxData();
        
        // ربط حدث التغيير بين أزرار الاختيار
        transactionToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> updateCategoryComboBox());
        
        // تحديث القائمة للمرة الأولى
        updateCategoryComboBox();
    }
    
    private void updateCategoryComboBox() {
        if (expenseRadioButton.isSelected()) {
            categoryLabel.setText("فئة المصروف:");
            categoryComboBox.setItems(FXCollections.observableArrayList(expenseCategories));
        } else {
            categoryLabel.setText("فئة الإيراد:");
            categoryComboBox.setItems(FXCollections.observableArrayList(incomeCategories));
        }
        categoryComboBox.getSelectionModel().selectFirst();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTransaction(FinancialTransaction transaction) {
        this.transaction = transaction;
        
        // للتعامل مع الدفعات القادمة من شاشة كشف الحساب
        if (transaction.getRelatedContact() != null) {
            contactComboBox.setValue(transaction.getRelatedContact());
            contactComboBox.setDisable(true);
        }
        // ... (يمكن إضافة منطق لتعبئة البيانات في حالة التعديل مستقبلاً)
    }

    private void loadComboBoxData() {
        try {
            accountComboBox.setItems(FXCollections.observableArrayList(accountDataService.getAllAccounts()));
            contactComboBox.setItems(FXCollections.observableArrayList(contactDataService.getAllContacts()));
        } catch (SQLException e) {
            showErrorAlert("خطأ", "فشل تحميل البيانات الأساسية.");
        }
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            double amountValue = Double.parseDouble(amountField.getText());
            // جعل المبلغ سالباً تلقائياً إذا كان مصروفاً
            double finalAmount = expenseRadioButton.isSelected() ? -Math.abs(amountValue) : Math.abs(amountValue);

            transaction.setTransactionDate(transactionDatePicker.getValue());
            transaction.setAccount(accountComboBox.getValue());
            transaction.setTransactionType(categoryComboBox.getValue());
            transaction.setAmount(finalAmount);
            transaction.setDescription(descriptionArea.getText());
            transaction.setRelatedContact(contactComboBox.getValue());

            try {
                transactionDataService.addTransaction(transaction);
                okClicked = true;
                dialogStage.close();
            } catch (SQLException e) {
                showErrorAlert("خطأ في الحفظ", "فشل حفظ الحركة المالية.\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean isInputValid() {
        String errorMessage = "";
        if (transactionDatePicker.getValue() == null) errorMessage += "تاريخ الحركة مطلوب.\n";
        if (accountComboBox.getValue() == null) errorMessage += "يجب اختيار حساب مالي.\n";
        if (categoryComboBox.getValue() == null) errorMessage += "يجب اختيار فئة للحركة.\n";
        if (amountField.getText() == null || amountField.getText().trim().isEmpty()) errorMessage += "المبلغ مطلوب.\n";
        
        try {
            double amount = Double.parseDouble(amountField.getText());
            if (amount <= 0) {
                errorMessage += "المبلغ يجب أن يكون أكبر من صفر.\n";
            }
        } catch (NumberFormatException e) {
            errorMessage += "المبلغ يجب أن يكون رقماً صحيحاً.\n";
        }

        if (errorMessage.isEmpty()) {
            return true;
        } else {
            showErrorAlert("حقول غير صالحة", errorMessage);
            return false;
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(dialogStage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}