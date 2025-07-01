package accounting.controller;

import accounting.model.Contact;
import accounting.model.Crop;
import accounting.model.FinancialAccount;
import accounting.model.SaleRecord;
import accounting.util.ContactDataService;
import accounting.util.CropDataService;
import accounting.util.FinancialAccountDataService;
import accounting.util.SaleDataService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.SQLException;

public class SaleFormController {

    @FXML private DatePicker saleDatePicker;
    @FXML private ComboBox<Crop> cropComboBox;
    @FXML private ComboBox<Contact> customerComboBox;
    @FXML private ComboBox<FinancialAccount> paymentAccountComboBox; // تم تغيير الاسم
    @FXML private TextField quantityKgField;
    @FXML private ComboBox<String> pricingUnitComboBox;
    @FXML private TextField unitPriceField;
    @FXML private TextField totalAmountField;
    @FXML private TextField invoiceNumberField;
    @FXML private TextField amountReceivedField; // حقل جديد

    private Stage dialogStage;
    private SaleRecord sale;
    private boolean okClicked = false;

    private SaleDataService saleDataService;
    private CropDataService cropDataService;
    private ContactDataService contactDataService;
    private FinancialAccountDataService accountDataService;

    @FXML
    private void initialize() {
        this.saleDataService = new SaleDataService();
        this.cropDataService = new CropDataService();
        this.contactDataService = new ContactDataService();
        this.accountDataService = new FinancialAccountDataService();
        
        cropComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> onCropSelected(newVal));
        quantityKgField.textProperty().addListener((obs, oldVal, newVal) -> calculateTotalAmount());
        unitPriceField.textProperty().addListener((obs, oldVal, newVal) -> calculateTotalAmount());
        pricingUnitComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> calculateTotalAmount());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setSale(SaleRecord sale) {
        this.sale = sale;
        loadComboBoxData();

        if (sale.getSaleId() != 0) { // وضع التعديل (سيتم تفعيله لاحقاً)
            saleDatePicker.setValue(sale.getSaleDate());
            cropComboBox.setValue(sale.getCrop());
            customerComboBox.setValue(sale.getCustomer());
            quantityKgField.setText(String.valueOf(sale.getQuantitySoldKg()));
            pricingUnitComboBox.setValue(sale.getSellingPricingUnit());
            unitPriceField.setText(String.valueOf(sale.getSellingUnitPrice()));
            totalAmountField.setText(String.valueOf(sale.getTotalSaleAmount()));
            invoiceNumberField.setText(sale.getSaleInvoiceNumber());
            paymentAccountComboBox.setDisable(true);
            amountReceivedField.setDisable(true);
        }
    }

    private void loadComboBoxData() {
        try {
            cropComboBox.setItems(FXCollections.observableArrayList(cropDataService.getAllActiveCrops()));
            customerComboBox.setItems(FXCollections.observableArrayList(
                contactDataService.getAllContacts().stream().filter(Contact::isCustomer).toList()
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
    
    private void calculateTotalAmount() {
        try {
            double quantityKg = Double.parseDouble(quantityKgField.getText());
            double unitPrice = Double.parseDouble(unitPriceField.getText());
            String selectedUnit = pricingUnitComboBox.getValue();
            Crop selectedCrop = cropComboBox.getValue();

            if (selectedUnit == null || selectedCrop == null) {
                totalAmountField.clear();
                return;
            }

            double conversionFactor = selectedCrop.getFirstConversionFactor(selectedUnit);
            if (conversionFactor <= 0) conversionFactor = 1.0;

            double totalAmount = (quantityKg / conversionFactor) * unitPrice;
            totalAmountField.setText(String.format("%.2f", totalAmount));
        } catch (NumberFormatException e) {
            totalAmountField.clear();
        }
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            sale.setSaleDate(saleDatePicker.getValue());
            sale.setCrop(cropComboBox.getValue());
            sale.setCustomer(customerComboBox.getValue());
            sale.setQuantitySoldKg(Double.parseDouble(quantityKgField.getText()));
            sale.setSellingPricingUnit(pricingUnitComboBox.getValue());
            sale.setSellingUnitPrice(Double.parseDouble(unitPriceField.getText()));
            sale.setTotalSaleAmount(Double.parseDouble(totalAmountField.getText()));
            sale.setSaleInvoiceNumber(invoiceNumberField.getText());
            sale.setSpecificSellingFactor(cropComboBox.getValue().getFirstConversionFactor(pricingUnitComboBox.getValue())); 

            double amountReceived = Double.parseDouble(amountReceivedField.getText());
            FinancialAccount paymentAccount = paymentAccountComboBox.getValue();

            try {
                if (sale.getSaleId() == 0) { // New Sale
                    saleDataService.addSale(sale, paymentAccount, amountReceived);
                } else { // Existing Sale - Update
                    // For update, we pass the sale object.
                    // The SaleDataService.updateSale method will need to handle
                    // the logic, including fetching the original record if needed for comparisons
                    // and determining how to handle financial adjustments.
                    // As per earlier discussion, paymentAccount and amountReceived are currently
                    // disabled for edits in this form, simplifying this call.
                    // If they were enabled, they would be passed here too.
                    saleDataService.updateSale(sale);
                }
                okClicked = true;
                dialogStage.close();
            } catch (SQLException e) {
                // Consider adding more specific error logging using Logger
                // LOGGER.log(Level.SEVERE, "Error saving sale.", e);
                e.printStackTrace(); // Keep for debugging during development
                showErrorAlert("خطأ في الحفظ", "فشل حفظ بيانات البيع: " + e.getMessage());
            } catch (Exception e) { // Catch unexpected errors
                // LOGGER.log(Level.SEVERE, "Unexpected error saving sale.", e);
                e.printStackTrace();
                showErrorAlert("خطأ غير متوقع", "حدث خطأ غير متوقع أثناء حفظ بيانات البيع.");
            }
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean isInputValid() {
        String errorMessage = "";
        double amountReceived = 0;
        double totalAmount = 0;

        if (saleDatePicker.getValue() == null) errorMessage += "تاريخ البيع غير صالح.\n";
        if (customerComboBox.getValue() == null) errorMessage += "يجب اختيار عميل.\n";
        if (cropComboBox.getValue() == null) errorMessage += "يجب اختيار محصول.\n";
        
        try {
            totalAmount = Double.parseDouble(totalAmountField.getText());
        } catch(Exception e) { /* تجاهل */ }

        try {
            amountReceived = Double.parseDouble(amountReceivedField.getText());
            if (amountReceived > 0 && paymentAccountComboBox.getValue() == null) {
                errorMessage += "يجب اختيار حساب للإيداع فيه.\n";
            }
            if (amountReceived > totalAmount) {
                errorMessage += "المبلغ المقبوض لا يمكن أن يكون أكبر من إجمالي الفاتورة.\n";
            }
        } catch (NumberFormatException e) {
            errorMessage += "المبلغ المقبوض يجب أن يكون رقماً.\n";
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