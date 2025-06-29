package accounting.controller;

import accounting.util.SmartAlertSystem;
import accounting.util.SmartAlertSystem.SmartAlert;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AlertsController implements Initializable {

    @FXML
    private ListView<SmartAlert> alertsListView;

    private final SmartAlertSystem alertSystem = new SmartAlertSystem();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // إعداد الخلية المخصصة لعرض التنبيهات
        alertsListView.setCellFactory(param -> new AlertListCell());
        loadAlerts();
    }

    private void loadAlerts() {
        List<SmartAlert> activeAlerts = alertSystem.getAllActiveAlerts();
        alertsListView.setItems(FXCollections.observableArrayList(activeAlerts));
    }

    /**
     * كلاس داخلي لتمثيل خلية واحدة (بطاقة تنبيه) في القائمة
     */
    private static class AlertListCell extends ListCell<SmartAlert> {
        private final VBox card = new VBox(10);
        private final HBox header = new HBox(10);
        private final Rectangle priorityIndicator = new Rectangle(10, 10);
        private final Label titleLabel = new Label();
        private final Label messageLabel = new Label();
        private final Label recommendationLabel = new Label();
        private final Label dateLabel = new Label();

        public AlertListCell() {
            super();
            // تصميم رأس البطاقة (مؤشر الأولوية والعنوان)
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            header.getChildren().addAll(priorityIndicator, titleLabel);

            // تصميم باقي البطاقة
            messageLabel.setWrapText(true);
            recommendationLabel.setWrapText(true);
            recommendationLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #007bff;");
            dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
            
            card.setPadding(new Insets(15));
            card.setStyle("-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");
            card.getChildren().addAll(header, messageLabel, recommendationLabel, dateLabel);
        }

        @Override
        protected void updateItem(SmartAlert alert, boolean empty) {
            super.updateItem(alert, empty);
            if (empty || alert == null) {
                setGraphic(null);
            } else {
                priorityIndicator.setFill(Color.web(alert.getPriorityColor()));
                titleLabel.setText(alert.getTitle());
                messageLabel.setText(alert.getMessage());
                recommendationLabel.setText("التوصية: " + alert.getRecommendation());
                dateLabel.setText("التاريخ: " + alert.getFormattedDate());
                setGraphic(card);
            }
        }
    }
}