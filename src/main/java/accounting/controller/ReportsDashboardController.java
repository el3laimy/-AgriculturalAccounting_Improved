package accounting.controller;

import accounting.util.SmartReportGenerator;
import accounting.util.SmartReportGenerator.ComprehensivePerformanceReport;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;

public class ReportsDashboardController {

    @FXML private DatePicker perfFromDatePicker;
    @FXML private DatePicker perfToDatePicker;
    @FXML private Button generatePerformanceReportBtn;
    @FXML private TextField forecastMonthsField;
    @FXML private Button generateForecastReportBtn;

    private final SmartReportGenerator reportGenerator = new SmartReportGenerator();

    @FXML
    private void initialize() {
        // يمكن إضافة أي تهيئة مطلوبة هنا
    }

    @FXML
    private void handleGeneratePerformanceReport() {
        LocalDate fromDate = perfFromDatePicker.getValue();
        LocalDate toDate = perfToDatePicker.getValue();

        if (fromDate == null || toDate == null) {
            showErrorAlert("بيانات ناقصة", "الرجاء تحديد تاريخ البدء وتاريخ الانتهاء للتقرير.");
            return;
        }

        try {
            // 1. توليد التقرير
            ComprehensivePerformanceReport report = reportGenerator.generatePerformanceReport(fromDate, toDate);

            // 2. عرض التقرير في نافذة جديدة
            showPerformanceReportWindow(report);

        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert("خطأ في إنشاء التقرير", "حدث خطأ أثناء محاولة إنشاء التقرير: " + e.getMessage());
        }
    }

    @FXML
    private void handleGenerateForecastReport() {
        System.out.println("طلب إنشاء تقرير التوقعات...");
        try {
            int months = Integer.parseInt(forecastMonthsField.getText());
            System.out.println("لعدد: " + months + " أشهر");
            // هنا سنضيف لاحقاً كود فتح نافذة تقرير التوقعات
        } catch (NumberFormatException e) {
            showErrorAlert("خطأ في الإدخال", "الرجاء إدخال عدد أشهر صحيح.");
        }
    }

    private void showPerformanceReportWindow(ComprehensivePerformanceReport report) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PerformanceReportView.fxml"));
            Parent root = loader.load();

            // الحصول على وحدة التحكم الخاصة بالتقرير وتمرير البيانات إليها
            PerformanceReportController controller = loader.getController();
            controller.setReport(report);

            Stage reportStage = new Stage();
            reportStage.setTitle("تقرير الأداء الشامل");
            reportStage.setScene(new Scene(root, 900, 700));
            reportStage.setMinWidth(800);
            reportStage.setMinHeight(600);
            
            // جعل النافذة غير مرتبطة بالنافذة الرئيسية لتصفح أسهل
            reportStage.initModality(Modality.NONE); 
            
            reportStage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("خطأ في عرض التقرير", "فشل تحميل واجهة عرض التقرير.");
        }
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}