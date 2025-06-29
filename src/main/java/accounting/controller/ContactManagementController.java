package accounting.controller;

import accounting.model.Contact;
import accounting.util.ContactDataService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu; // <-- Import جديد
import javafx.scene.control.MenuItem;   // <-- Import جديد
import javafx.scene.control.SeparatorMenuItem;
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

public class ContactManagementController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ContactManagementController.class.getName());

    @FXML private TableView<Contact> contactTable;
    @FXML private TableColumn<Contact, Integer> contactIdColumn;
    @FXML private TableColumn<Contact, String> nameColumn;
    @FXML private TableColumn<Contact, String> typeColumn;
    @FXML private TableColumn<Contact, String> phoneColumn;
    @FXML private TableColumn<Contact, String> addressColumn;

    private ContactDataService contactDataService;
    private ObservableList<Contact> contactList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.contactDataService = new ContactDataService();
        setupTable();
        setupContextMenu(); // <<<--- تم إضافة هذا السطر
        loadContacts();
    }

    private void setupTable() {
        contactList = FXCollections.observableArrayList();
        contactTable.setItems(contactList);

        contactIdColumn.setCellValueFactory(new PropertyValueFactory<>("contactId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("contactType"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
    }

    // <<<--- تم إضافة هذه الدالة الجديدة بالكامل
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem viewStatementItem = new MenuItem("عرض كشف الحساب");
        viewStatementItem.setOnAction(e -> handleViewStatement());

        MenuItem editItem = new MenuItem("تعديل البيانات");
        editItem.setOnAction(e -> handleEditContact());

        MenuItem deleteItem = new MenuItem("حذف");
        deleteItem.setOnAction(e -> handleDeleteContact());

        contextMenu.getItems().addAll(viewStatementItem, new SeparatorMenuItem(), editItem, deleteItem);

        contactTable.setRowFactory(tv -> {
            TableRow<Contact> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });
    }

    private void loadContacts() {
        try {
            contactList.setAll(contactDataService.getAllContacts());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load contacts", e);
            showErrorAlert("خطأ", "فشل تحميل قائمة جهات التعامل.");
        }
    }

    @FXML
    private void handleAddContact() {
        Contact newContact = new Contact();
        boolean okClicked = showContactEditDialog(newContact);
        if (okClicked) {
            try {
                contactDataService.addContact(newContact);
                loadContacts();
            } catch (SQLException e) {
                showErrorAlert("خطأ في الحفظ", "فشل إضافة جهة التعامل الجديدة.\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void handleEditContact() {
        Contact selectedContact = contactTable.getSelectionModel().getSelectedItem();
        if (selectedContact != null) {
            boolean okClicked = showContactEditDialog(selectedContact);
            if (okClicked) {
                try {
                    contactDataService.updateContact(selectedContact);
                    loadContacts();
                } catch (SQLException e) {
                    showErrorAlert("خطأ في التعديل", "فشل تحديث بيانات جهة التعامل.\n" + e.getMessage());
                }
            }
        } else {
            showInfoAlert("لا يوجد تحديد", "الرجاء تحديد جهة التعامل التي تريد تعديلها.");
        }
    }

    @FXML
    private void handleDeleteContact() {
        Contact selectedContact = contactTable.getSelectionModel().getSelectedItem();
        if (selectedContact != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("تأكيد الحذف");
            alert.setContentText("هل أنت متأكد من حذف: " + selectedContact.getName() + "؟");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    contactDataService.deleteContact(selectedContact.getContactId());
                    loadContacts();
                } catch (SQLException e) {
                     showErrorAlert("خطأ في الحذف", "فشل حذف جهة التعامل.\n" + e.getMessage());
                }
            }
        } else {
            showInfoAlert("لا يوجد تحديد", "الرجاء تحديد جهة التعامل التي تريد حذفها.");
        }
    }

    private boolean showContactEditDialog(Contact contact) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ContactForm.fxml"));
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("تحرير بيانات جهة التعامل");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            ContactFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setContact(contact);

            dialogStage.showAndWait();
            return controller.isOkClicked();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load contact form", e);
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
    
    @FXML
    private void handleViewStatement() {
        Contact selectedContact = contactTable.getSelectionModel().getSelectedItem();
        if (selectedContact != null) {
            showContactLedger(selectedContact);
        } else {
            showInfoAlert("لا يوجد تحديد", "الرجاء تحديد جهة التعامل لعرض كشف الحساب.");
        }
    }

    private void showContactLedger(Contact contact) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ContactLedger.fxml"));
            Parent page = loader.load(); 

            Stage dialogStage = new Stage();
            dialogStage.setTitle("كشف حساب: " + contact.getName());
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            ContactLedgerController controller = loader.getController();
            controller.setContact(contact);

            dialogStage.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load contact ledger form", e);
            showErrorAlert("خطأ في التحميل", "فشل تحميل واجهة كشف الحساب.");
        }
    }
}