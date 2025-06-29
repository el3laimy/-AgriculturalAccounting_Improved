package accounting.controller;

import accounting.model.FinancialAccount;
import accounting.util.FinancialAccountDataService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
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

public class FinancialAccountManagementController implements Initializable {

    @FXML private TableView<FinancialAccount> accountTable;
    @FXML private TableColumn<FinancialAccount, Integer> accountIdColumn;
    @FXML private TableColumn<FinancialAccount, String> accountNameColumn;
    @FXML private TableColumn<FinancialAccount, String> accountTypeColumn;
    @FXML private TableColumn<FinancialAccount, Double> openingBalanceColumn;
    @FXML private TableColumn<FinancialAccount, Double> currentBalanceColumn;

    private FinancialAccountDataService accountDataService;
    private ObservableList<FinancialAccount> accountList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.accountDataService = new FinancialAccountDataService();
        setupTable();
        setupContextMenu(); // <<<--- تم إضافة هذا السطر
        loadAccounts();
    }

    private void setupTable() {
        accountList = FXCollections.observableArrayList();
        accountTable.setItems(accountList);

        accountIdColumn.setCellValueFactory(new PropertyValueFactory<>("accountId"));
        accountNameColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        accountTypeColumn.setCellValueFactory(new PropertyValueFactory<>("accountType"));
        openingBalanceColumn.setCellValueFactory(new PropertyValueFactory<>("openingBalance"));
        currentBalanceColumn.setCellValueFactory(new PropertyValueFactory<>("currentBalance"));
    }

    // <<<--- تم إضافة هذه الدالة الجديدة بالكامل
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("تعديل الحساب");
        MenuItem deleteItem = new MenuItem("حذف الحساب");

        editItem.setOnAction(e -> handleEditAccount());
        deleteItem.setOnAction(e -> handleDeleteAccount());

        contextMenu.getItems().addAll(editItem, deleteItem);

        accountTable.setRowFactory(tv -> {
            TableRow<FinancialAccount> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });
    }

    private void loadAccounts() {
        try {
            accountList.setAll(accountDataService.getAllAccounts());
        } catch (SQLException e) {
            showErrorAlert("خطأ", "فشل تحميل قائمة الحسابات المالية.");
        }
    }

    @FXML
    private void handleAddAccount() {
        FinancialAccount newAccount = new FinancialAccount();
        boolean okClicked = showAccountEditDialog(newAccount);
        if (okClicked) {
            try {
                accountDataService.addAccount(newAccount);
                loadAccounts();
            } catch (SQLException e) {
                showErrorAlert("خطأ في الحفظ", "فشل إضافة الحساب الجديد.\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void handleEditAccount() {
        FinancialAccount selectedAccount = accountTable.getSelectionModel().getSelectedItem();
        if (selectedAccount != null) {
            boolean okClicked = showAccountEditDialog(selectedAccount);
            if (okClicked) {
                try {
                    accountDataService.updateAccount(selectedAccount);
                    loadAccounts();
                } catch (SQLException e) {
                    showErrorAlert("خطأ في التعديل", "فشل تحديث بيانات الحساب.\n" + e.getMessage());
                }
            }
        } else {
            showInfoAlert("لا يوجد تحديد", "الرجاء تحديد الحساب الذي تريد تعديله.");
        }
    }

    @FXML
    private void handleDeleteAccount() {
        FinancialAccount selectedAccount = accountTable.getSelectionModel().getSelectedItem();
        if (selectedAccount != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("تأكيد الحذف");
            alert.setContentText("هل أنت متأكد من حذف الحساب: " + selectedAccount.getAccountName() + "؟");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    accountDataService.deleteAccount(selectedAccount.getAccountId());
                    loadAccounts();
                } catch (SQLException e) {
                     showErrorAlert("خطأ في الحذف", "فشل حذف الحساب.\n" + e.getMessage());
                }
            }
        } else {
            showInfoAlert("لا يوجد تحديد", "الرجاء تحديد الحساب الذي تريد حذفه.");
        }
    }

    private boolean showAccountEditDialog(FinancialAccount account) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/FinancialAccountForm.fxml"));
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("تحرير بيانات الحساب المالي");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            FinancialAccountFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setAccount(account);

            dialogStage.showAndWait();
            return controller.isOkClicked();
        } catch (IOException e) {
            e.printStackTrace();
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