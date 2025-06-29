package accounting.util;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * فئة مساعدة للتنسيق والتحويلات
 */
public class FormatUtils {
    
    // --- *** بداية التعديل *** ---
    // تم تغيير Locale من "ar", "EG" إلى Locale.US لفرض استخدام الأرقام العربية (0-9)
    // مع الحفاظ على رمز العملة "ج.م."
    private static final NumberFormat CURRENCY_FORMATTER;

    static {
        CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(new Locale("us", "EG"));
        // هذا السطر يضمن استخدام الأرقام العربية القياسية
        CURRENCY_FORMATTER.setGroupingUsed(true); 
    }
    // --- *** نهاية التعديل *** ---
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * تنسيق المبلغ كعملة
     * @param amount المبلغ
     * @return المبلغ منسق كعملة
     */
    public static String formatCurrency(double amount) {
        return CURRENCY_FORMATTER.format(amount);
    }

    /**
     * تنسيق التاريخ للعرض
     * @param date التاريخ
     * @return التاريخ منسق للعرض
     */
    public static String formatDateForDisplay(LocalDate date) {
        if (date == null) return "";
        return date.format(DISPLAY_DATE_FORMATTER);
    }

    /**
     * تنسيق التاريخ لقاعدة البيانات
     * @param date التاريخ
     * @return التاريخ منسق لقاعدة البيانات
     */
    public static String formatDateForDatabase(LocalDate date) {
        if (date == null) return null;
        return date.format(DATE_FORMATTER);
    }

    /**
     * تحويل نص التاريخ من قاعدة البيانات إلى LocalDate
     * @param dateString نص التاريخ
     * @return كائن LocalDate أو null إذا كان النص فارغ
     */
    public static LocalDate parseDateFromDatabase(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateString, DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * تنسيق الكمية مع وحدة القياس
     * @param quantity الكمية
     * @param unit الوحدة
     * @return الكمية منسقة مع الوحدة
     */
    public static String formatQuantityWithUnit(double quantity, String unit) {
        return String.format("%.2f %s", quantity, unit != null ? unit : "");
    }
    /**
     * تحقق من صحة الرقم
     * @param value القيمة
     * @return true إذا كانت القيمة صحيحة
     */
    public static boolean isValidNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /**
     * تحقق من صحة الرقم الموجب
     * @param value القيمة
     * @return true إذا كانت القيمة موجبة وصحيحة
     */
    public static boolean isValidPositiveNumber(double value) {
        return isValidNumber(value) && value > 0;
    }

    /**
     * تحقق من صحة النص
     * @param text النص
     * @return true إذا كان النص غير فارغ
     */
    public static boolean isValidText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}

