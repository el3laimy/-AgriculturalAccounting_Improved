package accounting.controller;

import accounting.util.CropDataService;
import accounting.util.CropDataService.CropStatistics;
import accounting.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InventoryController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(InventoryController.class.getName());

    @FXML private TableView<CropStatistics> inventoryTable;
    @FXML private TableColumn<CropStatistics, String> cropNameColumn;
    @FXML private TableColumn<CropStatistics, Double> currentStockColumn;
    @FXML private TableColumn<CropStatistics, Double> avgCostColumn;
    @FXML private TableColumn<CropStatistics, Double> inventoryValueColumn;
    @FXML private Button refreshButton;
    @FXML private Label totalInventoryValueLabel;
    @FXML private Button addAdjustmentButton;


    private CropDataService cropDataService;
    private ObservableList<CropStatistics> inventoryList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.cropDataService = new CropDataService();
        setupTable();
        loadInventoryData();
        refreshButton.setOnAction(e -> loadInventoryData());
        addAdjustmentButton.setOnAction(e -> handleAddAdjustment());
    }

    private void setupTable() {
        inventoryList = FXCollections.observableArrayList();
        inventoryTable.setItems(inventoryList);

        cropNameColumn.setCellValueFactory(new PropertyValueFactory<>("cropName"));
        currentStockColumn.setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        avgCostColumn.setCellValueFactory(new PropertyValueFactory<>("averageCost"));
        inventoryValueColumn.setCellValueFactory(new PropertyValueFactory<>("inventoryValue"));

        // تنسيق الخلايا الرقمية والعملات
        currentStockColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : FormatUtils.formatQuantityWithUnit(item, "كجم"));
            }
        });

        avgCostColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : FormatUtils.formatCurrency(item));
            }
        });

        inventoryValueColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : FormatUtils.formatCurrency(item));
            }
        });
    }

    private void loadInventoryData() {
        try {
            List<CropStatistics> allStats = cropDataService.getAllCropStatistics();
            inventoryList.setAll(allStats);
            updateTotalValue();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load inventory statistics.", e);
            // يمكنك إضافة إظهار تنبيه للمستخدم هنا
        }
    }

    private void updateTotalValue() {
        double totalValue = inventoryList.stream()
                .mapToDouble(CropStatistics::getInventoryValue)
                .sum();
        totalInventoryValueLabel.setText("إجمالي قيمة المخزون: " + FormatUtils.formatCurrency(totalValue));
    }
    @FXML
    private void handleAddAdjustment() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/InventoryAdjustmentView.fxml"));
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("تسوية مخزون");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            InventoryAdjustmentController controller = loader.getController();
            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // تحديث البيانات إذا تم الحفظ بنجاح
            if (controller.isSaved()) {
                loadInventoryData();
            }

        } catch (IOException e) {
            e.printStackTrace();
            // يمكنك إظهار رسالة خطأ هنا
        }
    }
}