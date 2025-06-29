package accounting.util;

import java.sql.*;
import java.time.LocalDate; // <<< تمت إضافة هذا السطر
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * مدير قاعدة البيانات المحسن مع تجميع الاتصالات وإدارة المعاملات
 */
public class ImprovedDataManager {
    
    private static final Logger LOGGER = Logger.getLogger(ImprovedDataManager.class.getName());
    private static final String DATABASE_URL = "jdbc:sqlite:agricultural_accounting.db";
    private static final int MAX_POOL_SIZE = 10;
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    
    private static ImprovedDataManager instance;
    private final List<Connection> connectionPool;
    private final Object poolLock = new Object();
    
    private ImprovedDataManager() {
        connectionPool = new ArrayList<>();
        initializeDatabase();
        createConnectionPool();
    }
    
    /**
     * الحصول على مثيل وحيد من مدير قاعدة البيانات
     */
    public static synchronized ImprovedDataManager getInstance() {
        if (instance == null) {
            instance = new ImprovedDataManager();
        }
        return instance;
    }
    
    /**
     * تهيئة قاعدة البيانات وإنشاء الجداول
     */
    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            createTables(conn);
            createIndexes(conn);
            createDefaultAccounts(conn);
            LOGGER.info("تم تهيئة قاعدة البيانات بنجاح");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "خطأ في تهيئة قاعدة البيانات", e);
            throw new RuntimeException("فشل في تهيئة قاعدة البيانات", e);
        }
    }
    
    /**
     * إنشاء جداول قاعدة البيانات المحسنة
     */
    private void createTables(Connection conn) throws SQLException {
        String[] createTableQueries = {
            // جدول المحاصيل المحسن
            """
            CREATE TABLE IF NOT EXISTS crops (
                crop_id INTEGER PRIMARY KEY AUTOINCREMENT,
                crop_name TEXT NOT NULL UNIQUE,
                allowed_pricing_units TEXT NOT NULL,
                conversion_factors TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_active BOOLEAN DEFAULT 1
            )
            """,
            
            // جدول جهات التعامل المحسن
            """
            CREATE TABLE IF NOT EXISTS contacts (
                contact_id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                address TEXT,
                email TEXT,
                tax_number TEXT,
                is_supplier BOOLEAN DEFAULT 0,
                is_customer BOOLEAN DEFAULT 0,
                credit_limit REAL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_active BOOLEAN DEFAULT 1
            )
            """,
            
            // جدول الحسابات المالية المحسن
            """
            CREATE TABLE IF NOT EXISTS financial_accounts (
                account_id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_name TEXT NOT NULL UNIQUE,
                account_type TEXT NOT NULL CHECK (account_type IN ('CASH', 'BANK')),
                account_number TEXT,
                bank_name TEXT,
                opening_balance REAL DEFAULT 0,
                opening_balance_date DATE,
                current_balance REAL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_active BOOLEAN DEFAULT 1
            )
            """,
            
            // جدول المشتريات المحسن
            """
            CREATE TABLE IF NOT EXISTS purchases (
            		purchase_id INTEGER PRIMARY KEY AUTOINCREMENT,
            		crop_id INTEGER NOT NULL,
            		supplier_id INTEGER NOT NULL,
            		purchase_date DATE NOT NULL,
            		quantity_kg REAL NOT NULL CHECK (quantity_kg > 0),
            		pricing_unit TEXT NOT NULL,
            		specific_factor REAL NOT NULL CHECK (specific_factor > 0),
            		unit_price REAL NOT NULL CHECK (unit_price > 0),
            		total_cost REAL NOT NULL CHECK (total_cost > 0),
            		amount_paid REAL DEFAULT 0, -- << تم إضافة هذا الحقل
            		payment_status TEXT DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING', 'PARTIAL', 'PAID')), -- << تم تحديث هذا الحقل
            		invoice_number TEXT,
            		notes TEXT,
            		created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            		updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            		FOREIGN KEY (crop_id) REFERENCES crops (crop_id),
            		FOREIGN KEY (supplier_id) REFERENCES contacts (contact_id)
            )
            """,
            
            // جدول المبيعات المحسن
            """
            CREATE TABLE IF NOT EXISTS sales (
            		sale_id INTEGER PRIMARY KEY AUTOINCREMENT,
            		customer_id INTEGER NOT NULL,
            		crop_id INTEGER NOT NULL,
            		sale_date DATE NOT NULL,
            		quantity_sold_kg REAL NOT NULL CHECK (quantity_sold_kg > 0),
            		selling_pricing_unit TEXT NOT NULL,
            		specific_selling_factor REAL NOT NULL CHECK (specific_selling_factor > 0),
            		selling_unit_price REAL NOT NULL CHECK (selling_unit_price > 0),
            		total_sale_amount REAL NOT NULL CHECK (total_sale_amount > 0),
            		amount_paid REAL DEFAULT 0, -- << تم إضافة هذا الحقل
            		payment_status TEXT DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING', 'PARTIAL', 'PAID')), -- << تم تحديث هذا الحقل
            		sale_invoice_number TEXT,
            		notes TEXT,
            		created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            		updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            		FOREIGN KEY (customer_id) REFERENCES contacts (contact_id),
            		FOREIGN KEY (crop_id) REFERENCES crops (crop_id)
            )
            """,
            
            // جدول الحركات المالية المحسن
            """
            CREATE TABLE IF NOT EXISTS financial_transactions (
                transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_id INTEGER NOT NULL,
                transaction_date DATE NOT NULL,
                transaction_type TEXT NOT NULL,
                description TEXT NOT NULL,
                amount REAL NOT NULL,
                related_contact_id INTEGER,
                related_purchase_id INTEGER,
                related_sale_id INTEGER,
                reference_number TEXT,
                notes TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (account_id) REFERENCES financial_accounts (account_id),
                FOREIGN KEY (related_contact_id) REFERENCES contacts (contact_id),
                FOREIGN KEY (related_purchase_id) REFERENCES purchases (purchase_id),
                FOREIGN KEY (related_sale_id) REFERENCES sales (sale_id)
            )
            """,
            
            // جدول المخزون المحسن
            """
            CREATE TABLE IF NOT EXISTS inventory (
                inventory_id INTEGER PRIMARY KEY AUTOINCREMENT,
                crop_id INTEGER NOT NULL,
                current_stock_kg REAL DEFAULT 0 CHECK (current_stock_kg >= 0),
                reserved_stock_kg REAL DEFAULT 0 CHECK (reserved_stock_kg >= 0),
                average_cost_per_kg REAL DEFAULT 0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (crop_id) REFERENCES crops (crop_id),
                UNIQUE(crop_id)
            )
            """,
            """
			CREATE TABLE IF NOT EXISTS inventory_adjustments (
			    adjustment_id INTEGER PRIMARY KEY AUTOINCREMENT,
			    crop_id INTEGER NOT NULL,
			    adjustment_date DATE NOT NULL,
			    adjustment_type TEXT NOT NULL CHECK (adjustment_type IN ('DAMAGE', 'SHORTAGE', 'SURPLUS')),
			    quantity_kg REAL NOT NULL,
			    reason TEXT,
			    cost REAL NOT NULL,
			    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
			    FOREIGN KEY (crop_id) REFERENCES crops (crop_id)
			)
			""",
            
            // جدول حركات المخزون
            """
            CREATE TABLE IF NOT EXISTS inventory_movements (
                movement_id INTEGER PRIMARY KEY AUTOINCREMENT,
                crop_id INTEGER NOT NULL,
                movement_type TEXT NOT NULL CHECK (movement_type IN ('IN', 'OUT', 'ADJUSTMENT')),
                quantity_kg REAL NOT NULL,
                unit_cost REAL,
                reference_type TEXT,
                reference_id INTEGER,
                movement_date DATE NOT NULL,
                notes TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (crop_id) REFERENCES crops (crop_id)
            )
            """,
            """
	            CREATE TABLE IF NOT EXISTS purchase_returns (
	            return_id INTEGER PRIMARY KEY AUTOINCREMENT,
	            original_purchase_id INTEGER NOT NULL,
	            return_date DATE NOT NULL,
	            crop_id INTEGER NOT NULL,
	           quantity_kg REAL NOT NULL,
	    return_reason TEXT,
	    returned_cost REAL NOT NULL,
	    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	    FOREIGN KEY (original_purchase_id) REFERENCES purchases (purchase_id),
	    FOREIGN KEY (crop_id) REFERENCES crops (crop_id)
            )
            """,
            """
			CREATE TABLE IF NOT EXISTS sale_returns (
			    return_id INTEGER PRIMARY KEY AUTOINCREMENT,
			    original_sale_id INTEGER NOT NULL,
			    return_date DATE NOT NULL,
			    crop_id INTEGER NOT NULL,
			    quantity_kg REAL NOT NULL,
			    return_reason TEXT,
			    refund_amount REAL NOT NULL,
			    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
			    FOREIGN KEY (original_sale_id) REFERENCES sales (sale_id),
			    FOREIGN KEY (crop_id) REFERENCES crops (crop_id)
			)
			""",
            
            // جدول سجل التدقيق
            """
            CREATE TABLE IF NOT EXISTS audit_log (
                log_id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name TEXT NOT NULL,
                record_id INTEGER NOT NULL,
                operation TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
                old_values TEXT,
                new_values TEXT,
                user_name TEXT,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS general_ledger (
                entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
                transaction_ref TEXT NOT NULL,
                entry_date DATE NOT NULL,
                account_id INTEGER NOT NULL,
                debit REAL DEFAULT 0,
                credit REAL DEFAULT 0,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (account_id) REFERENCES financial_accounts (account_id)
            )
            """
            
        };
        
        for (String query : createTableQueries) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(query);
            }
        }
    }
    
    /**
     * إنشاء المؤشرات لتحسين الأداء
     */
    private void createIndexes(Connection conn) throws SQLException {
        String[] indexQueries = {
            "CREATE INDEX IF NOT EXISTS idx_purchases_date ON purchases (purchase_date)",
            "CREATE INDEX IF NOT EXISTS idx_purchases_crop ON purchases (crop_id)",
            "CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON purchases (supplier_id)",
            "CREATE INDEX IF NOT EXISTS idx_sales_date ON sales (sale_date)",
            "CREATE INDEX IF NOT EXISTS idx_sales_crop ON sales (crop_id)",
            "CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales (customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_transactions_date ON financial_transactions (transaction_date)",
            "CREATE INDEX IF NOT EXISTS idx_transactions_account ON financial_transactions (account_id)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_date ON inventory_movements (movement_date)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_crop ON inventory_movements (crop_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_table_record ON audit_log (table_name, record_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log (timestamp)"
        };
        
        for (String query : indexQueries) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(query);
            }
        }
    }
    
    /**
     * إنشاء تجميع الاتصالات
     */
    private void createConnectionPool() {
        synchronized (poolLock) {
            try {
                for (int i = 0; i < MAX_POOL_SIZE; i++) {
                    Connection conn = DriverManager.getConnection(DATABASE_URL);
                    conn.setAutoCommit(true);
                    connectionPool.add(conn);
                }
                LOGGER.info("تم إنشاء تجميع الاتصالات بنجاح: " + MAX_POOL_SIZE + " اتصال");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "خطأ في إنشاء تجميع الاتصالات", e);
                throw new RuntimeException("فشل في إنشاء تجميع الاتصالات", e);
            }
        }
    }
    
    /**
     * الحصول على اتصال من التجميع
     */
    public Connection getConnection() throws SQLException {
        synchronized (poolLock) {
            if (connectionPool.isEmpty()) {
                // إنشاء اتصال جديد إذا كان التجميع فارغ
                Connection conn = DriverManager.getConnection(DATABASE_URL);
                conn.setAutoCommit(true);
                return conn;
            }
            
            Connection conn = connectionPool.remove(connectionPool.size() - 1);
            
            // التحقق من صحة الاتصال
            if (conn.isClosed() || !conn.isValid(CONNECTION_TIMEOUT)) {
                conn = DriverManager.getConnection(DATABASE_URL);
                conn.setAutoCommit(true);
            }
            
            return conn;
        }
    }
    
    /**
     * إرجاع الاتصال إلى التجميع
     */
    public void returnConnection(Connection conn) {
        if (conn != null) {
            synchronized (poolLock) {
                try {
                    if (!conn.isClosed() && connectionPool.size() < MAX_POOL_SIZE) {
                        conn.setAutoCommit(true);
                        connectionPool.add(conn);
                    } else {
                        conn.close();
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "خطأ في إرجاع الاتصال إلى التجميع", e);
                    try {
                        conn.close();
                    } catch (SQLException ex) {
                        LOGGER.log(Level.WARNING, "خطأ في إغلاق الاتصال", ex);
                    }
                }
            }
        }
    }
    
    /**
     * تنفيذ معاملة قاعدة بيانات مع إدارة تلقائية للمعاملات
     */
    public <T> T executeTransaction(DatabaseTransaction<T> transaction) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            T result = transaction.execute(conn);
            conn.commit();
            return result;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "خطأ في التراجع عن المعاملة", rollbackEx);
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "خطأ في إعادة تعيين AutoCommit", e);
                }
                returnConnection(conn);
            }
        }
    }
    
    /**
     * واجهة للمعاملات
     */
    @FunctionalInterface
    public interface DatabaseTransaction<T> {
        T execute(Connection conn) throws SQLException;
    }
    
    /**
     * تنظيف الموارد وإغلاق جميع الاتصالات
     */
    public void shutdown() {
        synchronized (poolLock) {
            for (Connection conn : connectionPool) {
                try {
                    if (!conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "خطأ في إغلاق الاتصال", e);
                }
            }
            connectionPool.clear();
        }
        LOGGER.info("تم إغلاق مدير قاعدة البيانات");
    }
    
    /**
     * تسجيل عملية في سجل التدقيق
     * تم تعديلها لتقبل اتصال موجود لمنع قفل قاعدة البيانات
     */
    public void logAuditEntry(String tableName, int recordId, String operation, 
                             String oldValues, String newValues, String userName, Connection... existingConnection) throws SQLException {
        String query = """
            INSERT INTO audit_log (table_name, record_id, operation, old_values, new_values, user_name)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        Connection conn = null;
        try {
            // إذا تم توفير اتصال، استخدمه. وإلا، احصل على اتصال جديد.
            conn = (existingConnection.length > 0) ? existingConnection[0] : getConnection();
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, tableName);
                stmt.setInt(2, recordId);
                stmt.setString(3, operation);
                stmt.setString(4, oldValues);
                stmt.setString(5, newValues);
                stmt.setString(6, userName);
                stmt.executeUpdate();
            }
        } finally {
            // لا تغلق الاتصال إذا كان قد تم توفيره من الخارج
            if (existingConnection.length == 0 && conn != null) {
                returnConnection(conn);
            }
        }
    }
    
    /**
     * تحديث رصيد المخزون - تستخدم اتصالاً موجوداً
     */
    public void updateInventory(int cropId, double quantityChange, double unitCost, 
                               String movementType, String referenceType, int referenceId, Connection conn) throws SQLException {
        // ... (الكود الداخلي لهذه الدالة يبقى كما هو)
        // This is the private worker method, we just need to make it public and accept a connection
        String updateInventoryQuery = "INSERT OR REPLACE INTO inventory (crop_id, current_stock_kg, average_cost_per_kg, last_updated) VALUES (?, COALESCE((SELECT current_stock_kg FROM inventory WHERE crop_id = ?), 0) + ?, CASE WHEN ? > 0 THEN (COALESCE((SELECT current_stock_kg * average_cost_per_kg FROM inventory WHERE crop_id = ?), 0) + (? * ?)) / (COALESCE((SELECT current_stock_kg FROM inventory WHERE crop_id = ?), 0) + ?) ELSE COALESCE((SELECT average_cost_per_kg FROM inventory WHERE crop_id = ?), 0) END, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement stmt = conn.prepareStatement(updateInventoryQuery)) {
            stmt.setInt(1, cropId);
            stmt.setInt(2, cropId);
            stmt.setDouble(3, quantityChange);
            stmt.setDouble(4, quantityChange);
            stmt.setInt(5, cropId);
            stmt.setDouble(6, quantityChange);
            stmt.setDouble(7, unitCost);
            stmt.setInt(8, cropId);
            stmt.setDouble(9, quantityChange);
            stmt.setInt(10, cropId);
            stmt.executeUpdate();
        }
        
        String insertMovementQuery = "INSERT INTO inventory_movements (crop_id, movement_type, quantity_kg, unit_cost, reference_type, reference_id, movement_date) VALUES (?, ?, ?, ?, ?, ?, DATE('now'))";
        
        try (PreparedStatement stmt = conn.prepareStatement(insertMovementQuery)) {
            stmt.setInt(1, cropId);
            stmt.setString(2, movementType);
            stmt.setDouble(3, quantityChange);
            stmt.setDouble(4, unitCost);
            stmt.setString(5, referenceType);
            stmt.setInt(6, referenceId);
            stmt.executeUpdate();
        }
    }
    
     /**
     * تحديث رصيد الحساب المالي - تستخدم اتصالاً موجوداً
     */
    public void updateAccountBalance(int accountId, double amount, Connection conn) throws SQLException {
        String query = "UPDATE financial_accounts SET current_balance = current_balance + ?, updated_at = CURRENT_TIMESTAMP WHERE account_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, accountId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("لم يتم العثور على الحساب المالي رقم: " + accountId);
            }
        }
    }
    public void addFinancialTransaction(Connection conn, int accountId, LocalDate date, 
            String type, String description, double amount, 
            int contactId, Integer purchaseId, Integer saleId, 
            String referenceNumber) throws SQLException {
    	String query = """
    			INSERT INTO financial_transactions (account_id, transaction_date, transaction_type, 
                   description, amount, related_contact_id, 
                   related_purchase_id, related_sale_id, reference_number)
    			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    			""";

    	try (PreparedStatement stmt = conn.prepareStatement(query)) {
    		stmt.setInt(1, accountId);
    		stmt.setString(2, FormatUtils.formatDateForDatabase(date));
    		stmt.setString(3, type);
    		stmt.setString(4, description);
    		stmt.setDouble(5, amount);
    		stmt.setInt(6, contactId);

    			if (purchaseId != null) {
    				stmt.setInt(7, purchaseId);
    			} else {
    				stmt.setNull(7, Types.INTEGER);
    			}

    			if (saleId != null) {
    				stmt.setInt(8, saleId);
    			} else {
    				stmt.setNull(8, Types.INTEGER);
    			}

    			stmt.setString(9, referenceNumber);

    			stmt.executeUpdate();
    	}
    }
    public void addLedgerEntry(Connection conn, String transactionRef, LocalDate entryDate,
            int accountId, double debit, double credit, String description) throws SQLException {
    	String sql = "INSERT INTO general_ledger (transaction_ref, entry_date, account_id, debit, credit, description) VALUES (?, ?, ?, ?, ?, ?)";
    	try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    		stmt.setString(1, transactionRef);
    		stmt.setString(2, FormatUtils.formatDateForDatabase(entryDate));
    		stmt.setInt(3, accountId);
    		stmt.setDouble(4, debit);
    		stmt.setDouble(5, credit);
    		stmt.setString(6, description);
    		stmt.executeUpdate();
    	}
}
    /**
     * إنشاء الحسابات الافتراضية في شجرة الحسابات إذا لم تكن موجودة.
     * تستخدم INSERT OR IGNORE لتجنب الأخطاء عند إعادة التشغيل.
     * @param conn اتصال قاعدة البيانات.
     * @throws SQLException
     */
    private void createDefaultAccounts(Connection conn) throws SQLException {
        String sql = "INSERT OR IGNORE INTO financial_accounts (account_id, account_name, account_type, is_active, opening_balance, opening_balance_date) VALUES (?, ?, ?, 1, 0.0, ?)";
        
        // تاريخ اليوم كرصيد افتتاحي
        String today = FormatUtils.formatDateForDatabase(LocalDate.now());

        // تعريف شجرة الحسابات الأساسية
        // ملاحظة: قمنا بتوسيع enum AccountType ليشمل أنواعاً أكثر دقة
        Object[][] accounts = {
            // الأصول
            {1, "الأصول", "HEADER", today},
            {101, "الأصول المتداولة", "HEADER", today},
            {10101, "النقدية بالصندوق", "CASH", today},
            {10102, "حساب بنك مصر", "BANK", today},
            {10103, "المخزون", "CURRENT_ASSET", today},
            {10104, "الذمم المدينة (العملاء)", "ACCOUNTS_RECEIVABLE", today},
            // الخصوم
            {2, "الخصوم", "HEADER", today},
            {201, "الخصوم المتداولة", "HEADER", today},
            {20101, "الذمم الدائنة (الموردين)", "ACCOUNTS_PAYABLE", today},
            // حقوق الملكية
            {3, "حقوق الملكية", "HEADER", today},
            {30101, "رأس المال", "EQUITY", today},
            {30103, "الأرباح المحتجزة", "EQUITY", today},
            {30102, "المسحوبات الشخصية", "EQUITY", today},
            // الإيرادات
            {4, "الإيرادات", "HEADER", today},
            {40101, "إيرادات المبيعات", "REVENUE", today},
            // المصروفات
            {5, "المصروفات", "HEADER", today},
            {50101, "تكلفة البضاعة المباعة", "EXPENSE", today},
            {50102, "مصروفات إدارية", "EXPENSE", today}
        };

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Object[] account : accounts) {
                stmt.setInt(1, (Integer) account[0]);
                stmt.setString(2, (String) account[1]);
                stmt.setString(3, (String) account[2]);
                stmt.setString(4, (String) account[3]);
                stmt.addBatch();
            }
            stmt.executeBatch();
            LOGGER.info("تم التحقق من/إنشاء الحسابات الافتراضية بنجاح.");
        }
    }
    
}


