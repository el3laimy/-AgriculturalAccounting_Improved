package accounting.controller;

import accounting.model.Crop;
import accounting.model.UnitFactor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CropFormController {

    @FXML private TextField cropNameField;
    @FXML private TableView<UnitFactor> unitsTable;
    @FXML private TableColumn<UnitFactor, String> unitNameColumn;
    @FXML private TableColumn<UnitFactor, Double> conversionFactorColumn;

    private Stage dialogStage;
    private Crop crop;
    private boolean okClicked = false;
    private ObservableList<UnitFactor> unitFactorsList;

    @FXML
    private void initialize() {
        unitFactorsList = FXCollections.observableArrayList();
        unitsTable.setItems(unitFactorsList);

        // السماح بتعديل الخلايا مباشرة في الجدول
        unitNameColumn.setCellValueFactory(cellData -> cellData.getValue().unitNameProperty());
        unitNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        unitNameColumn.setOnEditCommit(event -> {
            event.getTableView().getItems().get(event.getTablePosition().getRow()).setUnitName(event.getNewValue());
        });

        conversionFactorColumn.setCellValueFactory(cellData -> cellData.getValue().conversionFactorProperty().asObject());
        conversionFactorColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        conversionFactorColumn.setOnEditCommit(event -> {
            event.getTableView().getItems().get(event.getTablePosition().getRow()).setConversionFactor(event.getNewValue());
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setCrop(Crop crop) {
        this.crop = crop;
        cropNameField.setText(crop.getCropName());

        // تحويل الخريطة إلى قائمة لعرضها في الجدول
        if (crop.getConversionFactors() != null) {
            crop.getConversionFactors().forEach((unit, factors) -> {
                if (factors != null && !factors.isEmpty()) {
                    unitFactorsList.add(new UnitFactor(unit, factors.get(0)));
                }
            });
        }
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    @FXML
    private void handleAddUnit() {
        unitFactorsList.add(new UnitFactor("وحدة جديدة", 1.0));
    }

    @FXML
    private void handleRemoveUnit() {
        UnitFactor selectedUnit = unitsTable.getSelectionModel().getSelectedItem();
        if (selectedUnit != null) {
            unitFactorsList.remove(selectedUnit);
        } else {
            showErrorAlert("خطأ", "الرجاء تحديد الوحدة التي تريد حذفها أولاً.");
        }
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            crop.setCropName(cropNameField.getText());

            // تحويل القائمة من الجدول إلى خريطة ليتم حفظها
            Map<String, List<Double>> conversionFactorsMap = new HashMap<>();
            List<String> allowedUnits = new ArrayList<>();
            
            for (UnitFactor uf : unitFactorsList) {
                allowedUnits.add(uf.getUnitName());
                conversionFactorsMap.put(uf.getUnitName(), List.of(uf.getConversionFactor()));
            }

            crop.setAllowedPricingUnits(allowedUnits);
            crop.setConversionFactors(conversionFactorsMap);
            
            okClicked = true;
            dialogStage.close();
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean isInputValid() {
        if (cropNameField.getText() == null || cropNameField.getText().trim().isEmpty()) {
            showErrorAlert("خطأ في الإدخال", "اسم المحصول مطلوب.");
            return false;
        }
        for (UnitFactor uf : unitFactorsList) {
            if (uf.getUnitName() == null || uf.getUnitName().trim().isEmpty() || uf.getConversionFactor() <= 0) {
                showErrorAlert("خطأ في الإدخال", "كل الوحدات يجب أن تحتوي على اسم صحيح ومعامل تحويل أكبر من صفر.");
                return false;
            }
        }
        return true;
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