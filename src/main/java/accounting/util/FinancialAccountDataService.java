package accounting.util;

import accounting.model.FinancialAccount;
// تم حذف FinancialTransaction لأنه لم يعد مطلوباً بشكل مباشر في هذا الكلاس
// import accounting.model.FinancialTransaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * خدمات البيانات المحسنة للحسابات المالية
 * (تم تعديله لحل مشكلة StackOverflowError)
 */
public class FinancialAccountDataService {

    private final ImprovedDataManager dataManager;
    // تم حذف الاعتمادية المسببة للحلقة
    // private final FinancialTransactionDataService transactionDataService;


    public FinancialAccountDataService() {
        this.dataManager = ImprovedDataManager.getInstance();
        // تم حذف السطر الذي ينشئ كائن جديد ويسبب الحلقة
        // this.transactionDataService = new FinancialTransactionDataService();
    }

    /**
     * إضافة حساب مالي جديد إلى قاعدة البيانات.
     * @param account الكائن الذي يحتوي على بيانات الحساب.
     * @throws SQLException في حال حدوث خطأ في قاعدة البيانات.
     */
    public void addAccount(FinancialAccount account) throws SQLException {
        dataManager.executeTransaction(conn -> {
            String sql = "INSERT INTO financial_accounts (account_name, account_type, opening_balance, opening_balance_date, current_balance) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, account.getAccountName());
                stmt.setString(2, account.getAccountType().name());
                stmt.setDouble(3, account.getOpeningBalance());
                stmt.setString(4, FormatUtils.formatDateForDatabase(account.getOpeningBalanceDate()));
                stmt.setDouble(5, account.getOpeningBalance()); // الرصيد الحالي هو نفسه الافتتاحي عند الإنشاء
                
                stmt.executeUpdate();
                
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int accountId = rs.getInt(1);
                        dataManager.logAuditEntry("financial_accounts", accountId, "INSERT", null, account.getAccountName(), "SYSTEM", conn);
                    }
                }
            }
            return null;
        });
    }

    /**
     * تحديث بيانات حساب مالي موجود.
     * @param account الكائن الذي يحتوي على البيانات المحدثة.
     * @throws SQLException في حال حدوث خطأ.
     */
    public void updateAccount(FinancialAccount account) throws SQLException {
        dataManager.executeTransaction(conn -> {
            String sql = "UPDATE financial_accounts SET account_name = ?, account_type = ?, opening_balance = ?, opening_balance_date = ? WHERE account_id = ?";
             try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, account.getAccountName());
                stmt.setString(2, account.getAccountType().name());
                stmt.setDouble(3, account.getOpeningBalance());
                stmt.setString(4, FormatUtils.formatDateForDatabase(account.getOpeningBalanceDate()));
                stmt.setInt(5, account.getAccountId());
                
                stmt.executeUpdate();
                dataManager.logAuditEntry("financial_accounts", account.getAccountId(), "UPDATE", null, account.getAccountName(), "SYSTEM", conn);
            }
            return null;
        });
    }
    
    /**
     * حذف ناعم لحساب مالي.
     * @param accountId معرف الحساب المراد حذفه.
     * @throws SQLException في حال حدوث خطأ.
     */
    public void deleteAccount(int accountId) throws SQLException {
        dataManager.executeTransaction(conn -> {
            String sql = "UPDATE financial_accounts SET is_active = 0 WHERE account_id = ?";
             try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, accountId);
                stmt.executeUpdate();
                dataManager.logAuditEntry("financial_accounts", accountId, "DELETE", null, null, "SYSTEM", conn);
             }
            return null;
        });
    }

    /**
     * جلب جميع الحسابات المالية النشطة.
     * @return قائمة بالحسابات المالية.
     * @throws SQLException في حال حدوث خطأ.
     */
    public List<FinancialAccount> getAllAccounts() throws SQLException {
        String sql = "SELECT * FROM financial_accounts WHERE is_active = 1 ORDER BY account_name";
        List<FinancialAccount> accounts = new ArrayList<>();
        try (Connection conn = dataManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                accounts.add(mapResultSetToAccount(rs));
            }
        }
        return accounts;
    }
    
    /**
     * تحويل صف من قاعدة البيانات إلى كائن حساب مالي.
     * @param rs كائن ResultSet يحتوي على بيانات الصف.
     * @return كائن FinancialAccount.
     * @throws SQLException في حال حدوث خطأ.
     */
    private FinancialAccount mapResultSetToAccount(ResultSet rs) throws SQLException {
         FinancialAccount account = new FinancialAccount(
            rs.getInt("account_id"),
            rs.getString("account_name"),
            FinancialAccount.AccountType.valueOf(rs.getString("account_type")),
            rs.getDouble("opening_balance"),
            FormatUtils.parseDateFromDatabase(rs.getString("opening_balance_date"))
        );
        // قراءة وتعبئة الرصيد الحالي من قاعدة البيانات
        account.setCurrentBalance(rs.getDouble("current_balance"));
        return account;
    }
}