package accounting.controller;

import accounting.model.Crop;
import accounting.util.CropDataService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu; // <-- Import جديد
import javafx.scene.control.MenuItem;   // <-- Import جديد
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;   // <-- Import جديد
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CropManagementController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(CropManagementController.class.getName());

    @FXML private TableView<Crop> cropTable;
    @FXML private TableColumn<Crop, Integer> cropIdColumn;
    @FXML private TableColumn<Crop, String> cropNameColumn;
    @FXML private TableColumn<Crop, String> pricingUnitsColumn;

    private CropDataService cropDataService;
    private ObservableList<Crop> cropList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.cropDataService = new CropDataService();
        setupTable();
        setupContextMenu(); // <<<--- تم إضافة هذا السطر
        loadCrops();
    }

    private void setupTable() {
        cropList = FXCollections.observableArrayList();
        cropTable.setItems(cropList);

        cropIdColumn.setCellValueFactory(new PropertyValueFactory<>("cropId"));
        cropNameColumn.setCellValueFactory(new PropertyValueFactory<>("cropName"));
        pricingUnitsColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.join(", ", cellData.getValue().getAllowedPricingUnits()))
        );
    }

    // <<<--- تم إضافة هذه الدالة الجديدة بالكامل
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem editItem = new MenuItem("تعديل المحصول");
        editItem.setOnAction(e -> handleEditCrop());

        MenuItem deleteItem = new MenuItem("حذف المحصول");
        deleteItem.setOnAction(e -> handleDeleteCrop());

        contextMenu.getItems().addAll(editItem, deleteItem);

        cropTable.setRowFactory(tv -> {
            TableRow<Crop> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });
    }

    private void loadCrops() {
        try {
            cropList.setAll(cropDataService.getAllActiveCrops());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load crops", e);
            showErrorAlert("خطأ", "فشل تحميل قائمة المحاصيل من قاعدة البيانات.");
        }
    }

    @FXML
    private void handleAddCrop() {
        Crop newCrop = new Crop();
        boolean okClicked = showCropEditDialog(newCrop);

        if (okClicked) {
            try {
                Crop existingCrop = cropDataService.findCropByName(newCrop.getCropName());
                if (existingCrop != null && !existingCrop.isActive()) {
                    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmAlert.setTitle("محصول موجود");
                    confirmAlert.setHeaderText("يوجد محصول محذوف بنفس الاسم.");
                    confirmAlert.setContentText("هل تريد استعادة بيانات المحصول القديم بدلاً من إنشاء سجل جديد؟");
                    
                    Optional<ButtonType> result = confirmAlert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        cropDataService.reactivateCrop(existingCrop.getCropId());
                    }
                } else {
                    cropDataService.addCrop(newCrop);
                }
                loadCrops();

            } catch (SQLException e) {
                showErrorAlert("خطأ في الحفظ", "فشل إضافة المحصول. قد يكون الاسم مستخدماً بالفعل.\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void handleEditCrop() {
        Crop selectedCrop = cropTable.getSelectionModel().getSelectedItem();
        if (selectedCrop != null) {
            boolean okClicked = showCropEditDialog(selectedCrop);
            if (okClicked) {
                try {
                    cropDataService.updateCrop(selectedCrop);
                    loadCrops();
                } catch (SQLException e) {
                    showErrorAlert("خطأ في التعديل", "فشل تحديث بيانات المحصول.\n" + e.getMessage());
                }
            }
        } else {
            showInfoAlert("لا يوجد تحديد", "الرجاء تحديد المحصول الذي تريد تعديله.");
        }
    }

    @FXML
    private void handleDeleteCrop() {
        Crop selectedCrop = cropTable.getSelectionModel().getSelectedItem();
        if (selectedCrop != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("تأكيد الحذف");
            alert.setHeaderText("هل أنت متأكد؟");
            alert.setContentText("سيتم حذف المحصول: " + selectedCrop.getCropName());
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    cropDataService.deleteCrop(selectedCrop.getCropId());
                    loadCrops();
                } catch (SQLException e) {
                     showErrorAlert("خطأ في الحذف", "فشل حذف المحصول.\n" + e.getMessage());
                }
            }
        } else {
            showInfoAlert("لا يوجد تحديد", "الرجاء تحديد المحصول الذي تريد حذفه.");
        }
    }

    private boolean showCropEditDialog(Crop crop) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CropForm.fxml"));
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("تحرير بيانات المحصول");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            CropFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setCrop(crop);

            dialogStage.showAndWait();
            return controller.isOkClicked();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load crop form", e);
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

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
}