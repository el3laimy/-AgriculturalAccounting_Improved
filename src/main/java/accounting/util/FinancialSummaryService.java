package accounting.util;

import accounting.model.BalanceSheet;
import accounting.model.Contact;
import accounting.model.FinancialAccount;
import accounting.model.FinancialTransaction;
import accounting.model.IncomeStatement;
import accounting.model.PurchaseRecord;
import accounting.model.SaleRecord;
import accounting.model.TrialBalanceEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FinancialSummaryService {

    // <<< تم إضافة هذا السطر لتصحيح الخطأ
    private static final Logger LOGGER = Logger.getLogger(FinancialSummaryService.class.getName());

    private final ImprovedDataManager dataManager;
    private final CropDataService cropDataService;
    private final FinancialAccountDataService accountDataService;
    private final ContactDataService contactDataService;
    private final PurchaseDataService purchaseDataService;
    private final SaleDataService saleDataService;
    private final FinancialTransactionDataService transactionService;

    public FinancialSummaryService() {
        // <<< تم تحديث هذا القسم لضمان تهيئة كل الخدمات
        this.dataManager = ImprovedDataManager.getInstance();
        this.cropDataService = new CropDataService();
        this.accountDataService = new FinancialAccountDataService();
        this.contactDataService = new ContactDataService();
        this.purchaseDataService = new PurchaseDataService();
        this.saleDataService = new SaleDataService();
        this.transactionService = new FinancialTransactionDataService();
    }

    public double getTotalInventoryValue() throws SQLException {
        return cropDataService.getInventoryValuePerCrop().values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }
    
    public Map<String, Double> getInventoryDistribution() throws SQLException {
        return cropDataService.getInventoryValuePerCrop();
    }

    public double getTotalCashBalances() throws SQLException {
        return accountDataService.getAllAccounts().stream()
                .mapToDouble(FinancialAccount::getCurrentBalance)
                .sum();
    }
    
    public List<FinancialAccount> getAccountBalances() throws SQLException {
        return accountDataService.getAllAccounts();
    }


    public double getTotalAccountsReceivable() throws SQLException {
        double totalReceivables = 0.0;
        List<Contact> customers = contactDataService.getAllContacts().stream()
                .filter(Contact::isCustomer).toList();

        for (Contact customer : customers) {
            double balance = calculateContactBalance(customer);
            if (balance > 0) {
                totalReceivables += balance;
            }
        }
        return totalReceivables;
    }

    public double getTotalAccountsPayable() throws SQLException {
        double totalPayables = 0.0;
        List<Contact> suppliers = contactDataService.getAllContacts().stream()
                .filter(Contact::isSupplier).toList();

        for (Contact supplier : suppliers) {
            double balance = calculateContactBalance(supplier);
            if (balance < 0) {
                totalPayables += balance;
            }
        }
        return Math.abs(totalPayables);
    }
    
    public double getNetProfit(LocalDate fromDate, LocalDate toDate) throws SQLException {
        LocalDate start = (fromDate != null) ? fromDate : LocalDate.of(1900, 1, 1);
        LocalDate end = (toDate != null) ? toDate : LocalDate.now();

        Map<String, Double> salesStats = saleDataService.getSalesStatistics(start, end);
        PurchaseDataService.PurchaseStatistics purchaseStats = purchaseDataService.getPurchaseStatistics(start, end, null, null);

        double totalRevenue = salesStats.getOrDefault("total_revenue", 0.0);
        double totalExpenses = purchaseStats.getTotalCost();

        return totalRevenue - totalExpenses;
    }
    
    public double getNetProfitForCurrentYear() throws SQLException {
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfYear = today.withDayOfYear(1);
        return getNetProfit(firstDayOfYear, today);
    }


    private double calculateContactBalance(Contact contact) throws SQLException {
        double balance = 0.0;
        int contactId = contact.getContactId();

        if (contact.isCustomer()) {
            List<SaleRecord> sales = saleDataService.getSales(null, null, null, contactId, 0, 0);
            balance += sales.stream().mapToDouble(SaleRecord::getTotalSaleAmount).sum();
        }

        if (contact.isSupplier()) {
            List<PurchaseRecord> purchases = purchaseDataService.getPurchases(null, null, null, contactId, 0, 0);
            balance -= purchases.stream().mapToDouble(PurchaseRecord::getTotalCost).sum();
        }

        double totalPayments = transactionService.getTransactionsByContact(contactId)
                .stream()
                .mapToDouble(FinancialTransaction::getAmount)
                .sum();
        
        balance -= totalPayments;

        return balance;
    }

    public static class EquityStatement {
        public final double beginningEquity;
        public final double periodNetProfit;
        public final double capitalAdditions;
        public final double ownerWithdrawals;
        public final double endingEquity;

        public EquityStatement(double beginningEquity, double periodNetProfit, double capitalAdditions, double ownerWithdrawals) {
            this.beginningEquity = beginningEquity;
            this.periodNetProfit = periodNetProfit;
            this.capitalAdditions = capitalAdditions;
            this.ownerWithdrawals = ownerWithdrawals;
            this.endingEquity = beginningEquity + periodNetProfit + capitalAdditions - ownerWithdrawals;
        }
    }

    public EquityStatement generateEquityStatement(LocalDate startDate, LocalDate endDate) throws SQLException {
        double openingEquity = accountDataService.getAllAccounts().stream()
                .mapToDouble(FinancialAccount::getOpeningBalance)
                .sum();

        double historicalNetProfit = getNetProfitToDate(startDate.minusDays(1));
        double beginningEquity = openingEquity + historicalNetProfit;

        double periodNetProfit = getNetProfit(startDate, endDate);

        double capitalAdditions = transactionService.getTransactions(startDate, endDate, null, "إضافة رأس مال", false)
                .stream().mapToDouble(FinancialTransaction::getAbsoluteAmount).sum();

        double ownerWithdrawals = transactionService.getTransactions(startDate, endDate, null, "مسحوبات شخصية", true)
                .stream().mapToDouble(FinancialTransaction::getAbsoluteAmount).sum();

        return new EquityStatement(beginningEquity, periodNetProfit, capitalAdditions, ownerWithdrawals);
    }

    private double getNetProfitToDate(LocalDate toDate) throws SQLException {
        if (toDate == null) return 0;
        Map<String, Double> salesStats = saleDataService.getSalesStatistics(null, toDate);
        PurchaseDataService.PurchaseStatistics purchaseStats = purchaseDataService.getPurchaseStatistics(null, toDate, null, null);
        
        double totalRevenue = salesStats.getOrDefault("total_revenue", 0.0);
        double totalExpenses = purchaseStats.getTotalCost();
        
        double otherExpenses = transactionService.getTransactions(null, toDate, null, null, true)
                .stream()
                .filter(t -> t.getAmount() < 0 && !t.getTransactionType().equals("مسحوبات شخصية"))
                .mapToDouble(FinancialTransaction::getAmount)
                .sum();

        return totalRevenue + totalExpenses + otherExpenses;
    }

    public List<TrialBalanceEntry> getTrialBalance(LocalDate toDate) throws SQLException {
        List<TrialBalanceEntry> entries = new ArrayList<>();
        String sql = """
            SELECT
                fa.account_id,
                fa.account_name,
                SUM(gl.debit) as total_debit,
                SUM(gl.credit) as total_credit
            FROM general_ledger gl
            JOIN financial_accounts fa ON gl.account_id = fa.account_id
            WHERE gl.entry_date <= ?
            GROUP BY fa.account_id, fa.account_name
            ORDER BY fa.account_id
        """;

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, FormatUtils.formatDateForDatabase(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new TrialBalanceEntry(
                        rs.getInt("account_id"),
                        rs.getString("account_name"),
                        rs.getDouble("total_debit"),
                        rs.getDouble("total_credit")
                    ));
                }
            }
        }
        return entries;
    }

    public IncomeStatement getIncomeStatement(LocalDate fromDate, LocalDate toDate) throws SQLException {
        Map<String, Double> revenueDetails = new HashMap<>();
        Map<String, Double> expenseDetails = new HashMap<>();

        String sql = """
            SELECT
                fa.account_name,
                fa.account_type,
                SUM(gl.credit) - SUM(gl.debit) as balance
            FROM general_ledger gl
            JOIN financial_accounts fa ON gl.account_id = fa.account_id
            WHERE gl.entry_date BETWEEN ? AND ?
              AND (fa.account_type = 'REVENUE' OR fa.account_type = 'EXPENSE')
            GROUP BY fa.account_id, fa.account_name, fa.account_type
            HAVING balance != 0
        """;

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, FormatUtils.formatDateForDatabase(fromDate));
            stmt.setString(2, FormatUtils.formatDateForDatabase(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String accountType = rs.getString("account_type");
                    String accountName = rs.getString("account_name");
                    double balance = rs.getDouble("balance");

                    if ("REVENUE".equals(accountType)) {
                        revenueDetails.put(accountName, Math.abs(balance));
                    } else if ("EXPENSE".equals(accountType)) {
                        expenseDetails.put(accountName, Math.abs(balance));
                    }
                }
            }
        }
        return new IncomeStatement(revenueDetails, expenseDetails);
    }
    
    public BalanceSheet getBalanceSheet(LocalDate toDate) throws SQLException {
        Map<String, Double> assets = new HashMap<>();
        Map<String, Double> liabilities = new HashMap<>();
        Map<String, Double> equity = new HashMap<>();

        String sql = """
            SELECT
                fa.account_name,
                fa.account_type,
                SUM(gl.debit) - SUM(gl.credit) as balance
            FROM general_ledger gl
            JOIN financial_accounts fa ON gl.account_id = fa.account_id
            WHERE gl.entry_date <= ?
              AND fa.account_type NOT IN ('REVENUE', 'EXPENSE')
            GROUP BY fa.account_id, fa.account_name, fa.account_type
            HAVING balance != 0
        """;

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, FormatUtils.formatDateForDatabase(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String accountType = rs.getString("account_type");
                    String accountName = rs.getString("account_name");
                    double balance = rs.getDouble("balance");

                    switch (accountType) {
                        case "CASH", "BANK", "CURRENT_ASSET", "ACCOUNTS_RECEIVABLE":
                            assets.put(accountName, balance);
                            break;
                        case "LIABILITY", "ACCOUNTS_PAYABLE":
                            liabilities.put(accountName, -balance);
                            break;
                        case "EQUITY":
                            equity.put(accountName, -balance);
                            break;
                    }
                }
            }
        }
        return new BalanceSheet(assets, liabilities, equity);
    }

    public void performPeriodClose(LocalDate closeDate) throws SQLException {
        dataManager.executeTransaction(conn -> {
            
            String sql = """
                SELECT
                    fa.account_id,
                    fa.account_type,
                    SUM(gl.debit) - SUM(gl.credit) as balance
                FROM general_ledger gl
                JOIN financial_accounts fa ON gl.account_id = fa.account_id
                WHERE gl.entry_date <= ?
                  AND (fa.account_type = 'REVENUE' OR fa.account_type = 'EXPENSE')
                GROUP BY fa.account_id, fa.account_name, fa.account_type
                HAVING balance != 0
            """;
            
            List<Map<String, Object>> temporaryAccounts = new ArrayList<>();
            double netIncome = 0;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, FormatUtils.formatDateForDatabase(closeDate));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> accountInfo = new HashMap<>();
                        accountInfo.put("id", rs.getInt("account_id"));
                        accountInfo.put("type", rs.getString("account_type"));
                        accountInfo.put("balance", rs.getDouble("balance"));
                        temporaryAccounts.add(accountInfo);
                        
                        netIncome -= rs.getDouble("balance");
                    }
                }
            }

            if (!temporaryAccounts.isEmpty()) {
                String transactionRef = "CLOSE-" + closeDate.toString();
                int retainedEarningsAccountId = 30103;

                for (Map<String, Object> account : temporaryAccounts) {
                    int accountId = (int) account.get("id");
                    double balance = (double) account.get("balance");
                    
                    if ("REVENUE".equals(account.get("type"))) {
                        dataManager.addLedgerEntry(conn, transactionRef, closeDate, accountId, Math.abs(balance), 0.0, "إغلاق حساب إيرادات للفترة");
                    } else {
                        dataManager.addLedgerEntry(conn, transactionRef, closeDate, accountId, 0.0, balance, "إغلاق حساب مصروفات للفترة");
                    }
                }

                String description = "ترحيل صافي الربح للفترة المنتهية في " + closeDate.toString();
                if (netIncome >= 0) {
                    dataManager.addLedgerEntry(conn, transactionRef, closeDate, retainedEarningsAccountId, 0.0, netIncome, description);
                } else {
                    dataManager.addLedgerEntry(conn, transactionRef, closeDate, retainedEarningsAccountId, Math.abs(netIncome), 0.0, description);
                }
            }
            
            LOGGER.info("تم إغلاق الفترة المالية بنجاح لتاريخ: " + closeDate);
            return null;
        });
    }
}