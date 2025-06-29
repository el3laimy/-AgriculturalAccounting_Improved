package accounting.util;

import accounting.model.Contact;
import accounting.model.FinancialAccount;
import accounting.model.FinancialTransaction;
import accounting.model.LedgerEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * خدمات البيانات المحسنة للحركات المالية
 */
public class FinancialTransactionDataService {

    private final ImprovedDataManager dataManager;

    public FinancialTransactionDataService() {
        this.dataManager = ImprovedDataManager.getInstance();
    }

    /**
     * إضافة حركة مالية جديدة مع تسجيل القيد المزدوج وتحديث رصيد الحساب.
     */
    public int addTransaction(FinancialTransaction transaction) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            
            // 1. إضافة الحركة في الجدول الأصلي (للاحتفاظ بالتفاصيل الإضافية)
            String transactionSql = """
                INSERT INTO financial_transactions (account_id, transaction_date, transaction_type,
                                   description, amount, related_contact_id,
                                   related_purchase_id, related_sale_id, reference_number, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            int transactionId;
            try (PreparedStatement stmt = conn.prepareStatement(transactionSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, transaction.getAccount().getAccountId());
                stmt.setString(2, FormatUtils.formatDateForDatabase(transaction.getTransactionDate()));
                stmt.setString(3, transaction.getTransactionType());
                stmt.setString(4, transaction.getDescription());
                stmt.setDouble(5, transaction.getAmount());
                stmt.setObject(6, transaction.getRelatedContact() != null ? transaction.getRelatedContact().getContactId() : null, Types.INTEGER);
                stmt.setObject(7, transaction.getRelatedPurchase() != null ? transaction.getRelatedPurchase().getPurchaseId() : null, Types.INTEGER);
                stmt.setObject(8, transaction.getRelatedSale() != null ? transaction.getRelatedSale().getSaleId() : null, Types.INTEGER);
                stmt.setString(9, null);
                stmt.setString(10, transaction.getNotes());

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        transactionId = rs.getInt(1);
                    } else {
                        throw new SQLException("Creating financial transaction failed, no ID obtained.");
                    }
                }
            }

            // --- *** تطبيق القيد المزدوج *** ---

            String transactionRef = "TRN-" + transactionId;
            double absAmount = Math.abs(transaction.getAmount());
            int debitAccountId;
            int creditAccountId;

            if (transaction.getAmount() < 0) { // مصروف أو دفعة لمورد
                creditAccountId = transaction.getAccount().getAccountId(); // حساب النقدية/البنك دائن (نقص)
                debitAccountId = getAccountIdForTransactionType(transaction.getTransactionType(), transaction.getRelatedContact());
            } else { // إيراد أو تحصيل من عميل
                debitAccountId = transaction.getAccount().getAccountId(); // حساب النقدية/البنك مدين (زيادة)
                creditAccountId = getAccountIdForTransactionType(transaction.getTransactionType(), transaction.getRelatedContact());
            }

            // التأكد من أن الحسابات ليست صفراً قبل تسجيل القيد
            if (debitAccountId != 0 && creditAccountId != 0) {
                 // تسجيل القيد المدين
                dataManager.addLedgerEntry(conn, transactionRef, transaction.getTransactionDate(), debitAccountId, absAmount, 0.0, transaction.getDescription());

                // تسجيل القيد الدائن
                dataManager.addLedgerEntry(conn, transactionRef, transaction.getTransactionDate(), creditAccountId, 0.0, absAmount, transaction.getDescription());
            } else {
                // يمكنك إلقاء استثناء هنا إذا كان عدم وجود الحساب يعتبر خطأ فادحاً
                System.err.println("لم يتم العثور على حساب مقابل للعملية: " + transaction.getTransactionType());
            }
           
            // تحديث رصيد الحساب المالي (هذه الخطوة تبقى لتحديث الرصيد الظاهر في الواجهات بسرعة)
            dataManager.updateAccountBalance(transaction.getAccount().getAccountId(), transaction.getAmount(), conn);
            
            // تسجيل عملية التدقيق
            String auditDescription = String.format("مبلغ %.2f - %s", transaction.getAmount(), transaction.getDescription());
            dataManager.logAuditEntry("financial_transactions", transactionId, "INSERT", null, auditDescription, "SYSTEM", conn);

            return transactionId;
        });
    }

    /**
     * دالة مساعدة لتحديد الحساب المقابل في القيد المزدوج.
     * @param type نوع الحركة (مثل: مصروفات إدارية، دفعة لمورد).
     * @param contact جهة التعامل المرتبطة (إن وجدت).
     * @return رقم الحساب (accountId).
     */
    private int getAccountIdForTransactionType(String type, Contact contact) {
        // في تطبيق حقيقي، يجب أن يكون هناك واجهة للمستخدم لربط أنواع الحركات بالحسابات
        // لكن حالياً سنستخدم قيم ثابتة بناءً على شجرة الحسابات التي أنشأناها
        switch (type) {
            case "دفعة لمورد":
                return 20101; // ID حساب الذمم الدائنة
            case "دفعة من عميل":
                return 10104; // ID حساب الذمم المدينة
            case "مصروفات إدارية":
                return 50102; // ID حساب المصروفات الإدارية
            case "إضافة رأس مال":
                return 30101; // ID حساب رأس المال
            case "مسحوبات شخصية":
                 return 30102; // ID حساب المسحوبات الشخصية
            case "إيرادات متنوعة":
                return 40101; // ID حساب إيرادات المبيعات (يمكن إنشاء حساب إيرادات أخرى)
            default:
                // حساب افتراضي في حالة عدم العثور على تطابق
                return 50102; 
        }
    }
    
    /**
     * الحصول على الحركات المالية مع إمكانية الفلترة
     */
    public List<FinancialTransaction> getTransactions(LocalDate from, LocalDate to, FinancialAccount account, String transactionType, boolean excludeLinked) throws SQLException {
        List<FinancialTransaction> transactions = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder("""
            SELECT t.*, a.account_name 
            FROM financial_transactions t 
            JOIN financial_accounts a ON t.account_id = a.account_id 
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();

        if (from != null) {
            sql.append(" AND t.transaction_date >= ?");
            params.add(FormatUtils.formatDateForDatabase(from));
        }
        if (to != null) {
            sql.append(" AND t.transaction_date <= ?");
            params.add(FormatUtils.formatDateForDatabase(to));
        }
        if (account != null) {
            sql.append(" AND t.account_id = ?");
            params.add(account.getAccountId());
        }
        if (transactionType != null && !transactionType.isEmpty()) {
            sql.append(" AND t.transaction_type = ?");
            params.add(transactionType);
        }
        if (excludeLinked) {
            sql.append(" AND t.related_purchase_id IS NULL AND t.related_sale_id IS NULL");
        }

        sql.append(" ORDER BY t.transaction_date DESC, t.transaction_id DESC");

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FinancialAccount acc = new FinancialAccount();
                    acc.setAccountId(rs.getInt("account_id"));
                    acc.setAccountName(rs.getString("account_name"));

                    FinancialTransaction trans = new FinancialTransaction(
                        rs.getInt("transaction_id"),
                        FormatUtils.parseDateFromDatabase(rs.getString("transaction_date")),
                        acc,
                        rs.getString("transaction_type"),
                        rs.getString("description"),
                        rs.getDouble("amount"),
                        null, null, null, 
                        rs.getString("notes")
                    );
                    transactions.add(trans);
                }
            }
        }
        return transactions;
    }

    /**
     * الحصول على الحركات المالية المرتبطة بجهة تعامل معينة (الدفعات)
     */
    public List<FinancialTransaction> getTransactionsByContact(int contactId) throws SQLException {
        List<FinancialTransaction> transactions = new ArrayList<>();
        String sql = """
            SELECT t.*, a.account_name 
            FROM financial_transactions t
            JOIN financial_accounts a ON t.account_id = a.account_id
            WHERE t.related_contact_id = ? 
            AND t.related_purchase_id IS NULL 
            AND t.related_sale_id IS NULL
            ORDER BY t.transaction_date
            """;
        
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, contactId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                     FinancialAccount acc = new FinancialAccount();
                     acc.setAccountId(rs.getInt("account_id"));
                     acc.setAccountName(rs.getString("account_name"));

                     FinancialTransaction trans = new FinancialTransaction(
                         rs.getInt("transaction_id"),
                         FormatUtils.parseDateFromDatabase(rs.getString("transaction_date")),
                         acc,
                         rs.getString("transaction_type"),
                         rs.getString("description"),
                         rs.getDouble("amount"),
                         null, null, null,
                         rs.getString("notes")
                     );
                     transactions.add(trans);
                }
            }
        }
        return transactions;
    }
    
    /**
     * الحصول على جميع قيود دفتر الأستاذ العام مع إمكانية الفلترة
     */
    public List<LedgerEntry> getGeneralLedgerEntries(LocalDate fromDate, LocalDate toDate) throws SQLException {
        List<LedgerEntry> entries = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder(
            "SELECT gl.entry_date, gl.transaction_ref, gl.description, fa.account_name, gl.debit, gl.credit " +
            "FROM general_ledger gl " +
            "JOIN financial_accounts fa ON gl.account_id = fa.account_id " +
            "WHERE 1=1"
        );
    
        List<Object> params = new ArrayList<>();
    
        if (fromDate != null) {
            sql.append(" AND gl.entry_date >= ?");
            params.add(FormatUtils.formatDateForDatabase(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND gl.entry_date <= ?");
            params.add(FormatUtils.formatDateForDatabase(toDate));
        }
        
        sql.append(" ORDER BY gl.entry_date, gl.entry_id");
    
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
    
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new LedgerEntry(
                        FormatUtils.parseDateFromDatabase(rs.getString("entry_date")),
                        rs.getString("description") + " (الحساب: " + rs.getString("account_name") + ")",
                        rs.getString("transaction_ref"),
                        rs.getDouble("debit"),
                        rs.getDouble("credit")
                    ));
                }
            }
        }
        return entries;
    }
}