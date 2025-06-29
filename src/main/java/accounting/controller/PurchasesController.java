package accounting.controller;

import accounting.model.Contact;
import accounting.model.Crop;
import accounting.model.PurchaseRecord;
import accounting.util.ContactDataService;
import accounting.util.CropDataService;
import accounting.util.FormatUtils;
import accounting.util.PurchaseDataService;
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
 * تحكم تبويب المشتريات
 */
public class PurchasesController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(PurchasesController.class.getName());

    // خدمات البيانات
    private PurchaseDataService purchaseDataService;
    private CropDataService cropDataService;
    private ContactDataService contactDataService;

    // عناصر الواجهة الرسومية
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<Crop> cropFilterCombo;
    @FXML private ComboBox<Contact> supplierFilterCombo;
    @FXML private Button filterBtn;
    @FXML private Button clearFilterBtn;
    @FXML private TableView<PurchaseRecord> purchasesTable;
    @FXML private TableColumn<PurchaseRecord, Integer> purchaseIdColumn;
    @FXML private TableColumn<PurchaseRecord, LocalDate> purchaseDateColumn;
    @FXML private TableColumn<PurchaseRecord, String> cropNameColumn;
    @FXML private TableColumn<PurchaseRecord, String> supplierNameColumn;
    @FXML private TableColumn<PurchaseRecord, Double> quantityColumn;
    @FXML private TableColumn<PurchaseRecord, String> pricingUnitColumn;
    @FXML private TableColumn<PurchaseRecord, Double> unitPriceColumn;
    @FXML private TableColumn<PurchaseRecord, Double> totalCostColumn;
    @FXML private TableColumn<PurchaseRecord, String> invoiceNumberColumn;
    @FXML private Button addPurchaseBtn;
    @FXML private Button exportBtn;
    @FXML private Button printBtn;
    @FXML private Label recordCountLabel;
    @FXML private Label totalQuantityLabel;
    @FXML private Label totalCostLabel;

    private ObservableList<PurchaseRecord> purchasesList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.purchaseDataService = new PurchaseDataService();
        this.cropDataService = new CropDataService();
        this.contactDataService = new ContactDataService();
        
        setupTable();
        setupContextMenu(); // إعداد قائمة الكليك يمين
        setupFilters();
        loadData();
        setupEventHandlers();
    }

    private void setupTable() {
        purchasesList = FXCollections.observableArrayList();
        purchasesTable.setItems(purchasesList);

        purchaseIdColumn.setCellValueFactory(new PropertyValueFactory<>("purchaseId"));
        purchaseDateColumn.setCellValueFactory(new PropertyValueFactory<>("purchaseDate"));
        purchaseDateColumn.setCellFactory(column -> new TableCell<>() {
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

        supplierNameColumn.setCellValueFactory(cellData -> {
            Contact supplier = cellData.getValue().getSupplier();
            return new SimpleStringProperty(supplier != null ? supplier.getName() : "");
        });

        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantityKg"));
        quantityColumn.setCellFactory(tc -> new TableCell<>() {
             @Override
            protected void updateItem(Double quantity, boolean empty) {
                super.updateItem(quantity, empty);
                setText(empty || quantity == null ? null : FormatUtils.formatQuantityWithUnit(quantity, "كجم"));
            }
        });

        pricingUnitColumn.setCellValueFactory(new PropertyValueFactory<>("pricingUnit"));

        unitPriceColumn.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        unitPriceColumn.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : FormatUtils.formatCurrency(price));
            }
        });

        totalCostColumn.setCellValueFactory(new PropertyValueFactory<>("totalCost"));
        totalCostColumn.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double cost, boolean empty) {
                super.updateItem(cost, empty);
                setText(empty || cost == null ? null : FormatUtils.formatCurrency(cost));
            }
        });
        
        invoiceNumberColumn.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editMenuItem = new MenuItem("تعديل الفاتورة");
        MenuItem deleteMenuItem = new MenuItem("حذف الفاتورة");
        MenuItem returnMenuItem = new MenuItem("تسجيل مرتجع لهذه الفاتورة");
        SeparatorMenuItem separator = new SeparatorMenuItem();

        editMenuItem.setOnAction(event -> handleEditPurchase());
        deleteMenuItem.setOnAction(event -> handleDeletePurchase());
        returnMenuItem.setOnAction(event -> {
            PurchaseRecord selected = purchasesTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleReturnPurchase(selected);
            }
        });

        contextMenu.getItems().addAll(editMenuItem, deleteMenuItem, separator, returnMenuItem);

        purchasesTable.setRowFactory(tv -> {
            TableRow<PurchaseRecord> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty()) {
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });
    }

    private void setupFilters() {
        try {
            List<Crop> allCrops = cropDataService.getAllActiveCrops();
            List<Contact> allSuppliers = contactDataService.getAllContacts().stream().filter(Contact::isSupplier).toList();

            cropFilterCombo.setItems(FXCollections.observableArrayList(allCrops));
            supplierFilterCombo.setItems(FXCollections.observableArrayList(allSuppliers));

            cropFilterCombo.setCellFactory(createCropCellFactory());
            cropFilterCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Crop item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "كل المحاصيل" : item.getCropName());
                }
            });

            supplierFilterCombo.setCellFactory(createSupplierCellFactory());
            supplierFilterCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Contact item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "كل الموردين" : item.getName());
                }
            });
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "فشل في تحميل بيانات الفلاتر", e);
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

    private Callback<ListView<Contact>, ListCell<Contact>> createSupplierCellFactory() {
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
        // لا حاجة لتعطيل الأزرار بعد الآن، ولكن يمكننا الإبقاء على هذا المنطق إذا أردنا عرض تفاصيل العنصر المحدد في مكان آخر
        // purchasesTable.getSelectionModel().selectedItemProperty().addListener(...);

        filterBtn.setOnAction(e -> handleFilter());
        clearFilterBtn.setOnAction(e -> handleClearFilter());
        addPurchaseBtn.setOnAction(e -> handleAddPurchase());
    }

    private void updateStatistics() {
        int recordCount = purchasesList.size();
        double totalQuantity = purchasesList.stream().mapToDouble(PurchaseRecord::getQuantityKg).sum();
        double totalCost = purchasesList.stream().mapToDouble(PurchaseRecord::getTotalCost).sum();

        recordCountLabel.setText(recordCount + " سجل");
        totalQuantityLabel.setText("إجمالي الكمية: " + FormatUtils.formatQuantityWithUnit(totalQuantity, "كجم"));
        totalCostLabel.setText("إجمالي التكلفة: " + FormatUtils.formatCurrency(totalCost));
    }

    @FXML
    private void handleFilter() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();
        Integer cropId = Optional.ofNullable(cropFilterCombo.getValue()).map(Crop::getCropId).orElse(null);
        Integer supplierId = Optional.ofNullable(supplierFilterCombo.getValue()).map(Contact::getContactId).orElse(null);

        try {
            List<PurchaseRecord> filteredData = purchaseDataService.getPurchases(fromDate, toDate, cropId, supplierId, 0, 0);
            purchasesList.setAll(filteredData);
            updateStatistics();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "فشل في فلترة بيانات المشتريات", e);
            showErrorAlert("خطأ في قاعدة البيانات", "لم يتم جلب البيانات بنجاح.");
        }
    }

    @FXML
    private void handleClearFilter() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        cropFilterCombo.setValue(null);
        supplierFilterCombo.setValue(null);
        handleFilter();
    }

    private void handleDeletePurchase() {
        PurchaseRecord selected = purchasesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("تأكيد الحذف");
            alert.setHeaderText(null);
            alert.setContentText("هل أنت متأكد من حذف سجل الشراء رقم " + selected.getPurchaseId() + "؟");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    boolean deleted = purchaseDataService.deletePurchase(selected.getPurchaseId());
                    if (deleted) {
                        loadData(); // إعادة تحميل البيانات لتحديث الجدول
                        showInfoAlert("حذف", "تم حذف سجل الشراء بنجاح");
                    } else {
                        showErrorAlert("خطأ", "لم يتم حذف السجل.");
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "فشل في حذف سجل الشراء", e);
                    showErrorAlert("خطأ في قاعدة البيانات", "حدث خطأ أثناء محاولة الحذف: " + e.getMessage());
                }
            }
        }
    }
    
    @FXML
    private void handleAddPurchase() {
        PurchaseRecord newPurchase = new PurchaseRecord();
        boolean okClicked = showPurchaseEditDialog(newPurchase);
        if (okClicked) {
            loadData();
        }
    }

    private void handleEditPurchase() {
        PurchaseRecord selectedPurchase = purchasesTable.getSelectionModel().getSelectedItem();
        if (selectedPurchase != null) {
            boolean okClicked = showPurchaseEditDialog(selectedPurchase);
            if (okClicked) {
                loadData();
            }
        }
    }
    
    private void handleReturnPurchase(PurchaseRecord purchaseToReturn) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PurchaseReturnView.fxml"));
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("تسجيل مرتجع شراء");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            PurchaseReturnController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setPurchaseToReturn(purchaseToReturn);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                loadData();
            }

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("خطأ", "فشل تحميل واجهة تسجيل المرتجعات.");
        }
    }

    private boolean showPurchaseEditDialog(PurchaseRecord purchase) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PurchaseForm.fxml"));
            AnchorPane page = loader.load();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("تحرير بيانات الشراء");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);
            PurchaseFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setPurchase(purchase);
            dialogStage.showAndWait();
            return controller.isOkClicked();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @FXML
    private void handleExport() {
        showInfoAlert("تصدير", "سيتم تصدير البيانات إلى ملف Excel (سيتم تنفيذها في مرحلة لاحقة)");
    }

    @FXML
    private void handlePrint() {
        showInfoAlert("طباعة", "سيتم طباعة قائمة المشتريات (سيتم تنفيذها في مرحلة لاحقة)");
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}