package accounting.controller;

import accounting.model.Contact;
import accounting.model.Crop;
import accounting.model.FinancialAccount;
import accounting.model.PurchaseRecord;
import accounting.util.ContactDataService;
import accounting.util.CropDataService;
import accounting.util.FinancialAccountDataService;
import accounting.util.PurchaseDataService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.sql.SQLException;

public class PurchaseFormController {

    @FXML private DatePicker purchaseDatePicker;
    @FXML private ComboBox<Crop> cropComboBox;
    @FXML private ComboBox<Contact> supplierComboBox;
    @FXML private ComboBox<FinancialAccount> paymentAccountComboBox; // تم تغيير الاسم
    @FXML private TextField quantityKgField;
    @FXML private ComboBox<String> pricingUnitComboBox;
    @FXML private TextField unitPriceField;
    @FXML private TextField totalCostField;
    @FXML private TextField invoiceNumberField;
    @FXML private TextField amountPaidField; // حقل جديد

    private Stage dialogStage;
    private PurchaseRecord purchase;
    private boolean okClicked = false;

    private PurchaseDataService purchaseDataService;
    private CropDataService cropDataService;
    private ContactDataService contactDataService;
    private FinancialAccountDataService accountDataService;

    @FXML
    private void initialize() {
        this.purchaseDataService = new PurchaseDataService();
        this.cropDataService = new CropDataService();
        this.contactDataService = new ContactDataService();
        this.accountDataService = new FinancialAccountDataService();
        
        cropComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> onCropSelected(newVal));
        quantityKgField.textProperty().addListener((obs, oldVal, newVal) -> calculateTotalCost());
        unitPriceField.textProperty().addListener((obs, oldVal, newVal) -> calculateTotalCost());
        pricingUnitComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> calculateTotalCost());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setPurchase(PurchaseRecord purchase) {
        this.purchase = purchase;
        loadComboBoxData();

        if (purchase.getPurchaseId() != 0) { // وضع التعديل
            purchaseDatePicker.setValue(purchase.getPurchaseDate());
            cropComboBox.setValue(purchase.getCrop());
            supplierComboBox.setValue(purchase.getSupplier());
            quantityKgField.setText(String.valueOf(purchase.getQuantityKg()));
            pricingUnitComboBox.setValue(purchase.getPricingUnit());
            unitPriceField.setText(String.valueOf(purchase.getUnitPrice()));
            totalCostField.setText(String.valueOf(purchase.getTotalCost()));
            invoiceNumberField.setText(purchase.getInvoiceNumber());
            paymentAccountComboBox.setDisable(true);
            amountPaidField.setDisable(true);
        }
    }

    private void loadComboBoxData() {
        try {
            cropComboBox.setItems(FXCollections.observableArrayList(cropDataService.getAllActiveCrops()));
            supplierComboBox.setItems(FXCollections.observableArrayList(
                contactDataService.getAllContacts().stream().filter(Contact::isSupplier).toList()
            ));
            paymentAccountComboBox.setItems(FXCollections.observableArrayList(accountDataService.getAllAccounts()));
        } catch (SQLException e) {
            showErrorAlert("خطأ", "فشل تحميل البيانات الأساسية.");
        }
    }

    private void onCropSelected(Crop selectedCrop) {
        if (selectedCrop != null) {
            pricingUnitComboBox.setItems(FXCollections.observableArrayList(selectedCrop.getAllowedPricingUnits()));
            pricingUnitComboBox.getSelectionModel().selectFirst();
        } else {
            pricingUnitComboBox.getItems().clear();
        }
    }
    
    private void calculateTotalCost() {
        try {
            double quantityKg = Double.parseDouble(quantityKgField.getText());
            double unitPrice = Double.parseDouble(unitPriceField.getText());
            String selectedUnit = pricingUnitComboBox.getValue();
            Crop selectedCrop = cropComboBox.getValue();

            if (selectedUnit == null || selectedCrop == null) {
                totalCostField.clear();
                return;
            }

            double conversionFactor = selectedCrop.getFirstConversionFactor(selectedUnit);
            if (conversionFactor <= 0) conversionFactor = 1.0;

            double totalCost = (quantityKg / conversionFactor) * unitPrice;
            totalCostField.setText(String.format("%.2f", totalCost));
        } catch (NumberFormatException e) {
            totalCostField.clear();
        }
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            purchase.setPurchaseDate(purchaseDatePicker.getValue());
            purchase.setCrop(cropComboBox.getValue());
            purchase.setSupplier(supplierComboBox.getValue());
            purchase.setQuantityKg(Double.parseDouble(quantityKgField.getText()));
            purchase.setPricingUnit(pricingUnitComboBox.getValue());
            purchase.setUnitPrice(Double.parseDouble(unitPriceField.getText()));
            purchase.setTotalCost(Double.parseDouble(totalCostField.getText()));
            purchase.setInvoiceNumber(invoiceNumberField.getText());
            purchase.setSpecificFactor(cropComboBox.getValue().getFirstConversionFactor(pricingUnitComboBox.getValue())); 

            double amountPaid = Double.parseDouble(amountPaidField.getText());
            FinancialAccount paymentAccount = paymentAccountComboBox.getValue();

            try {
                if (purchase.getPurchaseId() == 0) {
                    purchaseDataService.addPurchase(purchase, paymentAccount, amountPaid);
                } else {
                    // purchaseDataService.updatePurchase(purchase); // منطق التعديل يحتاج تحديثاً مماثلاً
                }
                okClicked = true;
                dialogStage.close();
            } catch (SQLException e) {
                showErrorAlert("خطأ في الحفظ", "فشل حفظ بيانات الشراء.\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean isInputValid() {
        String errorMessage = "";
        double amountPaid = 0;
        double totalCost = 0;

        if (purchaseDatePicker.getValue() == null) errorMessage += "تاريخ الشراء غير صالح.\n";
        if (cropComboBox.getValue() == null) errorMessage += "يجب اختيار محصول.\n";
        if (supplierComboBox.getValue() == null) errorMessage += "يجب اختيار مورد.\n";
        
        try {
            totalCost = Double.parseDouble(totalCostField.getText());
        } catch(Exception e) { /* تجاهل */ }

        try {
            amountPaid = Double.parseDouble(amountPaidField.getText());
            if (amountPaid > 0 && paymentAccountComboBox.getValue() == null) {
                errorMessage += "يجب اختيار حساب للدفع منه.\n";
            }
            if (amountPaid > totalCost) {
                errorMessage += "المبلغ المدفوع لا يمكن أن يكون أكبر من إجمالي التكلفة.\n";
            }
        } catch (NumberFormatException e) {
            errorMessage += "المبلغ المدفوع يجب أن يكون رقماً.\n";
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