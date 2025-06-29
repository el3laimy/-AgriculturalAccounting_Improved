package accounting.controller;

import accounting.model.Contact;
import accounting.model.Crop;
import accounting.model.SaleRecord;
import accounting.util.ContactDataService;
import accounting.util.CropDataService;
import accounting.util.FormatUtils;
import accounting.util.SaleDataService;
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
import javafx.util.Callback;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * تحكم تبويب المبيعات
 */
public class SalesController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(SalesController.class.getName());

    // خدمات البيانات
    private SaleDataService saleDataService;
    private CropDataService cropDataService;
    private ContactDataService contactDataService;

    // عناصر الفلترة
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<Crop> cropFilterCombo;
    @FXML private ComboBox<Contact> customerFilterCombo;
    @FXML private Button filterBtn;
    @FXML private Button clearFilterBtn;

    // الجدول والأعمدة
    @FXML private TableView<SaleRecord> salesTable;
    @FXML private TableColumn<SaleRecord, Integer> saleIdColumn;
    @FXML private TableColumn<SaleRecord, LocalDate> saleDateColumn;
    @FXML private TableColumn<SaleRecord, String> cropNameColumn;
    @FXML private TableColumn<SaleRecord, String> customerNameColumn;
    @FXML private TableColumn<SaleRecord, Double> quantityColumn;
    @FXML private TableColumn<SaleRecord, String> pricingUnitColumn;
    @FXML private TableColumn<SaleRecord, Double> unitPriceColumn;
    @FXML private TableColumn<SaleRecord, Double> totalAmountColumn;
    @FXML private TableColumn<SaleRecord, String> invoiceNumberColumn;

    // أزرار الإجراءات
    @FXML private Button addSaleBtn;
    // تم حذف أزرار الإجراءات الأخرى لأنها ستكون في قائمة السياق

    // تسميات الإحصائيات
    @FXML private Label recordCountLabel;
    @FXML private Label totalQuantityLabel;
    @FXML private Label totalAmountLabel;

    private ObservableList<SaleRecord> salesList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // تهيئة خدمات البيانات
        this.saleDataService = new SaleDataService();
        this.cropDataService = new CropDataService();
        this.contactDataService = new ContactDataService();
        
        setupTable();
        setupContextMenu(); // <<<--- تم إضافة هذا السطر
        setupFilters();
        loadData();
        setupEventHandlers();
    }

    private void setupTable() {
        salesList = FXCollections.observableArrayList();
        salesTable.setItems(salesList);

        saleIdColumn.setCellValueFactory(new PropertyValueFactory<>("saleId"));
        
        saleDateColumn.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        saleDateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : FormatUtils.formatDateForDisplay(date));
            }
        });

        cropNameColumn.setCellValueFactory(cellData -> {
            Crop crop = cellData.getValue().getCrop();
            return new SimpleStringProperty(crop != null ? crop.getCropName() : "");
        });

        customerNameColumn.setCellValueFactory(cellData -> {
            Contact customer = cellData.getValue().getCustomer();
            return new SimpleStringProperty(customer != null ? customer.getName() : "");
        });

        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantitySoldKg"));
        quantityColumn.setCellFactory(tc -> new TableCell<>() {
             @Override
            protected void updateItem(Double quantity, boolean empty) {
                super.updateItem(quantity, empty);
                setText(empty || quantity == null ? null : FormatUtils.formatQuantityWithUnit(quantity, "كجم"));
            }
        });

        pricingUnitColumn.setCellValueFactory(new PropertyValueFactory<>("sellingPricingUnit"));

        unitPriceColumn.setCellValueFactory(new PropertyValueFactory<>("sellingUnitPrice"));
        unitPriceColumn.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : FormatUtils.formatCurrency(price));
            }
        });

        totalAmountColumn.setCellValueFactory(new PropertyValueFactory<>("totalSaleAmount"));
        totalAmountColumn.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : FormatUtils.formatCurrency(amount));
            }
        });
        
        invoiceNumberColumn.setCellValueFactory(new PropertyValueFactory<>("saleInvoiceNumber"));
    }

    // <<<--- تم إضافة هذه الدالة الجديدة بالكامل
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("تعديل الفاتورة");
        MenuItem deleteItem = new MenuItem("حذف الفاتورة");
        MenuItem returnItem = new MenuItem("تسجيل مرتجع لهذه الفاتورة");
        // MenuItem returnItem = new MenuItem("تسجيل مرتجع لهذه الفاتورة"); // يمكن إضافتها لاحقاً

        editItem.setOnAction(e -> handleEditSale());
        deleteItem.setOnAction(e -> handleDeleteSale());
        returnItem.setOnAction(e -> {
            SaleRecord selected = salesTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleReturnSale(selected);
            }
        });
        // returnItem.setOnAction(e -> handleReturnSale());

        contextMenu.getItems().addAll(editItem, deleteItem);

        salesTable.setRowFactory(tv -> {
            TableRow<SaleRecord> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });
    }
    private void handleReturnSale(SaleRecord saleToReturn) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SaleReturnView.fxml"));
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("تسجيل مرتجع مبيعات");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            SaleReturnController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setSaleToReturn(saleToReturn);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                loadData();
            }

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("خطأ", "فشل تحميل واجهة تسجيل المرتجعات.");
        }
    }

    private void setupFilters() {
        try {
            List<Crop> allCrops = cropDataService.getAllActiveCrops();
            List<Contact> allCustomers = contactDataService.getAllContacts().stream()
                    .filter(Contact::isCustomer).toList();

            cropFilterCombo.setItems(FXCollections.observableArrayList(allCrops));
            customerFilterCombo.setItems(FXCollections.observableArrayList(allCustomers));

            cropFilterCombo.setCellFactory(createCropCellFactory());
            cropFilterCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Crop item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "كل المحاصيل" : item.getCropName());
                }
            });

            customerFilterCombo.setCellFactory(createCustomerCellFactory());
            customerFilterCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Contact item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "كل العملاء" : item.getName());
                }
            });

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "فشل في تحميل بيانات الفلاتر للمبيعات", e);
            showErrorAlert("خطأ في قاعدة البيانات", "لم يتم تحميل بيانات الفلاتر بنجاح.");
        }
    }
    
    private Callback<ListView<Crop>, ListCell<Crop>> createCropCellFactory() {
        return listView -> new ListCell<>() {
            @Override
            protected void updateItem(Crop crop, boolean empty) {
                super.updateItem(crop, empty);
                setText(empty || crop == null ? null : crop.getCropName());
            }
        };
    }

    private Callback<ListView<Contact>, ListCell<Contact>> createCustomerCellFactory() {
        return listView -> new ListCell<>() {
            @Override
            protected void updateItem(Contact contact, boolean empty) {
                super.updateItem(contact, empty);
                setText(empty || contact == null ? null : contact.getName());
            }
        };
    }
    
    private void loadData() {
        handleFilter();
    }

    private void setupEventHandlers() {
        // تم إزالة مستمع تحديد الجدول لأنه لم يعد ضرورياً لتعطيل الأزرار
        filterBtn.setOnAction(e -> handleFilter());
        clearFilterBtn.setOnAction(e -> handleClearFilter());
    }

    private void updateStatistics() {
        int recordCount = salesList.size();
        double totalQuantity = salesList.stream().mapToDouble(SaleRecord::getQuantitySoldKg).sum();
        double totalAmount = salesList.stream().mapToDouble(SaleRecord::getTotalSaleAmount).sum();

        recordCountLabel.setText(recordCount + " سجل");
        totalQuantityLabel.setText("إجمالي الكمية: " + FormatUtils.formatQuantityWithUnit(totalQuantity, "كجم"));
        totalAmountLabel.setText("إجمالي المبيعات: " + FormatUtils.formatCurrency(totalAmount));
    }

    @FXML
    private void handleFilter() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();
        Integer cropId = Optional.ofNullable(cropFilterCombo.getValue()).map(Crop::getCropId).orElse(null);
        Integer customerId = Optional.ofNullable(customerFilterCombo.getValue()).map(Contact::getContactId).orElse(null);

        try {
            List<SaleRecord> filteredData = saleDataService.getSales(fromDate, toDate, cropId, customerId, 0, 0); 
            salesList.setAll(filteredData);
            updateStatistics();
        } catch (SQLException e) {
             LOGGER.log(Level.SEVERE, "فشل في فلترة بيانات المبيعات", e);
             showErrorAlert("خطأ في قاعدة البيانات", "لم يتم جلب البيانات بنجاح.");
        }
    }

    @FXML
    private void handleClearFilter() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        cropFilterCombo.setValue(null);
        customerFilterCombo.setValue(null);
        handleFilter();
    }
    
    @FXML
    private void handleAddSale() {
        SaleRecord newSale = new SaleRecord();
        boolean okClicked = showSaleEditDialog(newSale);
        if (okClicked) {
            loadData();
        }
    }
    private boolean showSaleEditDialog(SaleRecord sale) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SaleForm.fxml"));
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("تحرير بيانات البيع");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            SaleFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setSale(sale);

            dialogStage.showAndWait();

            return controller.isOkClicked();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("خطأ في التحميل", "فشل في تحميل واجهة إدخال بيانات البيع.");
            return false;
        }
    }

    @FXML private void handleEditSale() { 
        SaleRecord selectedSale = salesTable.getSelectionModel().getSelectedItem();
        if (selectedSale != null) {
            showInfoAlert("تعديل بيع", "سيتم تنفيذ هذه الميزة لاحقاً."); 
        } else {
            showInfoAlert("تنبيه", "الرجاء تحديد فاتورة لتعديلها.");
        }
    }
   
    @FXML private void handleDeleteSale() { 
        SaleRecord selectedSale = salesTable.getSelectionModel().getSelectedItem();
        if (selectedSale != null) {
            showInfoAlert("حذف بيع", "سيتم تنفيذ هذه الميزة لاحقاً."); 
        } else {
            showInfoAlert("تنبيه", "الرجاء تحديد فاتورة لحذفها.");
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}