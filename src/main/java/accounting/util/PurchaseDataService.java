package accounting.util;

import accounting.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * خدمات البيانات المحسنة للمشتريات
 */
public class PurchaseDataService {

    private static final Logger LOGGER = Logger.getLogger(PurchaseDataService.class.getName());
    private final ImprovedDataManager dataManager;

    public PurchaseDataService() {
        this.dataManager = ImprovedDataManager.getInstance();
    }

    /**
     * إضافة شراء جديد مع تحديث المخزون وتوليد قيود القيد المزدوج المحاسبية.
     */
    public int addPurchase(PurchaseRecord purchase, FinancialAccount paymentAccount, double amountPaid) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            
            double finalAmountPaid = amountPaid;
            String paymentStatus;
            if (finalAmountPaid <= 0) {
                paymentStatus = "PENDING";
            } else if (finalAmountPaid >= purchase.getTotalCost()) {
                paymentStatus = "PAID";
                finalAmountPaid = purchase.getTotalCost(); 
            } else {
                paymentStatus = "PARTIAL";
            }
            
            String insertQuery = """
                INSERT INTO purchases (crop_id, supplier_id, purchase_date, quantity_kg,
                                     pricing_unit, specific_factor, unit_price, total_cost,
                                     invoice_number, amount_paid, payment_status, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            int purchaseId;
            try (PreparedStatement stmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, purchase.getCrop().getCropId());
                stmt.setInt(2, purchase.getSupplier().getContactId());
                stmt.setString(3, FormatUtils.formatDateForDatabase(purchase.getPurchaseDate()));
                stmt.setDouble(4, purchase.getQuantityKg());
                stmt.setString(5, purchase.getPricingUnit());
                stmt.setDouble(6, purchase.getSpecificFactor());
                stmt.setDouble(7, purchase.getUnitPrice());
                stmt.setDouble(8, purchase.getTotalCost());
                stmt.setString(9, purchase.getInvoiceNumber());
                stmt.setDouble(10, finalAmountPaid);
                stmt.setString(11, paymentStatus);
                stmt.setString(12, ""); // Notes

                stmt.executeUpdate();

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        purchaseId = generatedKeys.getInt(1);
                        purchase.setPurchaseId(purchaseId);
                    } else {
                        throw new SQLException("فشل في الحصول على معرف الشراء الجديد");
                    }
                }
            }
            
            // --- تطبيق القيد المزدوج ---
            String transactionRef = "PUR-" + purchaseId;
            String description = "شراء فاتورة رقم: " + purchase.getInvoiceNumber();

            // القيد المدين: زيادة قيمة المخزون (نفترض أن ID حساب المخزون هو 4)
            int inventoryAccountId = 4; // يجب استبدال هذا برقم الحساب الفعلي للمخزون
            dataManager.addLedgerEntry(conn, transactionRef, purchase.getPurchaseDate(),
                inventoryAccountId, purchase.getTotalCost(), 0.0, description);

            // القيد الدائن: الطرف الآخر للمعاملة
            if (finalAmountPaid >= purchase.getTotalCost()) {
                // حالة الدفع الكلي
                dataManager.addLedgerEntry(conn, transactionRef, purchase.getPurchaseDate(),
                    paymentAccount.getAccountId(), 0.0, purchase.getTotalCost(), description);
            } else {
                // حالة الشراء الآجل أو الدفع الجزئي
                if (finalAmountPaid > 0) {
                    dataManager.addLedgerEntry(conn, transactionRef, purchase.getPurchaseDate(),
                        paymentAccount.getAccountId(), 0.0, finalAmountPaid, "دفع جزء من " + description);
                }
                // قيد دائن بالمبلغ المتبقي لحساب المورد (نفترض أن ID حساب الذمم الدائنة هو 5)
                int accountsPayableId = 5; // يجب استبدال هذا برقم الحساب الفعلي للذمم الدائنة
                dataManager.addLedgerEntry(conn, transactionRef, purchase.getPurchaseDate(),
                    accountsPayableId, 0.0, purchase.getTotalCost() - finalAmountPaid, description);
            }
            
            // تحديث كمية المخزون
            double unitCost = purchase.getQuantityKg() > 0 ? purchase.getTotalCost() / purchase.getQuantityKg() : 0;
            dataManager.updateInventory(purchase.getCrop().getCropId(), purchase.getQuantityKg(), unitCost, "IN", "PURCHASE", purchaseId, conn);
            
            // تحديث رصيد الحساب (إذا تم الدفع)
            if (finalAmountPaid > 0 && paymentAccount != null) {
                dataManager.updateAccountBalance(paymentAccount.getAccountId(), -finalAmountPaid, conn);
            }
            
            dataManager.logAuditEntry("purchases", purchaseId, "INSERT", null, purchase.getInvoiceNumber(), "SYSTEM", conn);
            
            return purchaseId;
        });
    }

    /**
     * تحديث شراء موجود
     */
    public boolean updatePurchase(PurchaseRecord purchase) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            PurchaseRecord oldPurchase = getPurchaseById(purchase.getPurchaseId());
            if (oldPurchase == null) throw new SQLException("لم يتم العثور على سجل الشراء");
            
            String updateQuery = """
                UPDATE purchases 
                SET crop_id = ?, supplier_id = ?, purchase_date = ?, quantity_kg = ?, 
                    pricing_unit = ?, specific_factor = ?, unit_price = ?, total_cost = ?, 
                    invoice_number = ?, updated_at = CURRENT_TIMESTAMP
                WHERE purchase_id = ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                stmt.setInt(1, purchase.getCrop().getCropId());
                stmt.setInt(2, purchase.getSupplier().getContactId());
                stmt.setString(3, FormatUtils.formatDateForDatabase(purchase.getPurchaseDate()));
                stmt.setDouble(4, purchase.getQuantityKg());
                stmt.setString(5, purchase.getPricingUnit());
                stmt.setDouble(6, purchase.getSpecificFactor());
                stmt.setDouble(7, purchase.getUnitPrice());
                stmt.setDouble(8, purchase.getTotalCost());
                stmt.setString(9, purchase.getInvoiceNumber());
                stmt.setInt(10, purchase.getPurchaseId());
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    if (!oldPurchase.getCrop().equals(purchase.getCrop()) || oldPurchase.getQuantityKg() != purchase.getQuantityKg() || oldPurchase.getTotalCost() != purchase.getTotalCost()) {
                        double oldUnitCost = oldPurchase.getQuantityKg() > 0 ? oldPurchase.getTotalCost() / oldPurchase.getQuantityKg() : 0;
                        dataManager.updateInventory(oldPurchase.getCrop().getCropId(), -oldPurchase.getQuantityKg(), oldUnitCost, "ADJUSTMENT", "PURCHASE_UPDATE", purchase.getPurchaseId(), conn);
                        double newUnitCost = purchase.getQuantityKg() > 0 ? purchase.getTotalCost() / purchase.getQuantityKg() : 0;
                        dataManager.updateInventory(purchase.getCrop().getCropId(), purchase.getQuantityKg(), newUnitCost, "ADJUSTMENT", "PURCHASE_UPDATE", purchase.getPurchaseId(), conn);
                    }
                    dataManager.logAuditEntry("purchases", purchase.getPurchaseId(), "UPDATE", oldPurchase.getInvoiceNumber(), purchase.getInvoiceNumber(), "SYSTEM", conn);
                }
                return rowsAffected > 0;
            }
        });
    }

    /**
     * حذف شراء
     */
    public boolean deletePurchase(int purchaseId) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            PurchaseRecord purchase = getPurchaseById(purchaseId);
            if (purchase == null) throw new SQLException("لم يتم العثور على سجل الشراء");
            
            String deleteQuery = "DELETE FROM purchases WHERE purchase_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteQuery)) {
                stmt.setInt(1, purchaseId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    double unitCost = purchase.getQuantityKg() > 0 ? purchase.getTotalCost() / purchase.getQuantityKg() : 0;
                    dataManager.updateInventory(purchase.getCrop().getCropId(), -purchase.getQuantityKg(), unitCost, "OUT", "PURCHASE_DELETE", purchaseId, conn);
                    dataManager.logAuditEntry("purchases", purchaseId, "DELETE", purchase.getInvoiceNumber(), null, "SYSTEM", conn);
                }
                return rowsAffected > 0;
            }
        });
    }

    /**
     * الحصول على شراء بالمعرف
     */
    public PurchaseRecord getPurchaseById(int purchaseId) throws SQLException {
        String query = """
            SELECT p.*, c.crop_name, ct.name as supplier_name
            FROM purchases p
            JOIN crops c ON p.crop_id = c.crop_id
            JOIN contacts ct ON p.supplier_id = ct.contact_id
            WHERE p.purchase_id = ?
            """;
        
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, purchaseId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPurchase(rs);
                }
            }
        }
        return null;
    }

    /**
     * الحصول على المشتريات مع الفلترة
     */
    public List<PurchaseRecord> getPurchases(LocalDate fromDate, LocalDate toDate, Integer cropId, Integer supplierId, int limit, int offset) throws SQLException {
        StringBuilder queryBuilder = new StringBuilder("SELECT p.*, c.crop_name, ct.name as supplier_name FROM purchases p JOIN crops c ON p.crop_id = c.crop_id JOIN contacts ct ON p.supplier_id = ct.contact_id WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        
        if (fromDate != null) {
            queryBuilder.append(" AND p.purchase_date >= ?");
            parameters.add(FormatUtils.formatDateForDatabase(fromDate));
        }
        if (toDate != null) {
            queryBuilder.append(" AND p.purchase_date <= ?");
            parameters.add(FormatUtils.formatDateForDatabase(toDate));
        }
        if (cropId != null) {
            queryBuilder.append(" AND p.crop_id = ?");
            parameters.add(cropId);
        }
        if (supplierId != null) {
            queryBuilder.append(" AND p.supplier_id = ?");
            parameters.add(supplierId);
        }
        queryBuilder.append(" ORDER BY p.purchase_date DESC, p.purchase_id DESC");
        
        if (limit > 0) {
            queryBuilder.append(" LIMIT ? OFFSET ?");
            parameters.add(limit);
            parameters.add(offset);
        }
        
        List<PurchaseRecord> purchases = new ArrayList<>();
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapResultSetToPurchase(rs));
                }
            }
        }
        return purchases;
    }

    /**
     * الحصول على إحصائيات المشتريات
     */
    public PurchaseStatistics getPurchaseStatistics(LocalDate fromDate, LocalDate toDate, Integer cropId, Integer supplierId) throws SQLException {
        StringBuilder queryBuilder = new StringBuilder("""
            SELECT 
                COUNT(*) as total_records,
                SUM(quantity_kg) as total_quantity,
                SUM(total_cost) as total_cost,
                AVG(unit_price) as average_unit_price,
                MIN(purchase_date) as first_purchase_date,
                MAX(purchase_date) as last_purchase_date
            FROM purchases p
            WHERE 1=1
            """);
        
        List<Object> parameters = new ArrayList<>();
        
        if (fromDate != null) { queryBuilder.append(" AND p.purchase_date >= ?"); parameters.add(FormatUtils.formatDateForDatabase(fromDate)); }
        if (toDate != null) { queryBuilder.append(" AND p.purchase_date <= ?"); parameters.add(FormatUtils.formatDateForDatabase(toDate)); }
        if (cropId != null) { queryBuilder.append(" AND p.crop_id = ?"); parameters.add(cropId); }
        if (supplierId != null) { queryBuilder.append(" AND p.supplier_id = ?"); parameters.add(supplierId); }
        
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PurchaseStatistics(
                        rs.getInt("total_records"),
                        rs.getDouble("total_quantity"),
                        rs.getDouble("total_cost"),
                        rs.getDouble("average_unit_price"),
                        FormatUtils.parseDateFromDatabase(rs.getString("first_purchase_date")),
                        FormatUtils.parseDateFromDatabase(rs.getString("last_purchase_date"))
                    );
                }
            }
        }
        return new PurchaseStatistics(0, 0.0, 0.0, 0.0, null, null);
    }
    
    private PurchaseRecord mapResultSetToPurchase(ResultSet rs) throws SQLException {
        PurchaseRecord purchase = new PurchaseRecord();
        purchase.setPurchaseId(rs.getInt("purchase_id"));
        
        Crop crop = new Crop();
        crop.setCropId(rs.getInt("crop_id"));
        crop.setCropName(rs.getString("crop_name"));
        purchase.setCrop(crop);
        
        Contact supplier = new Contact();
        supplier.setContactId(rs.getInt("supplier_id"));
        supplier.setName(rs.getString("supplier_name"));
        purchase.setSupplier(supplier);
        
        purchase.setPurchaseDate(FormatUtils.parseDateFromDatabase(rs.getString("purchase_date")));
        purchase.setQuantityKg(rs.getDouble("quantity_kg"));
        purchase.setPricingUnit(rs.getString("pricing_unit"));
        purchase.setSpecificFactor(rs.getDouble("specific_factor"));
        purchase.setUnitPrice(rs.getDouble("unit_price"));
        purchase.setTotalCost(rs.getDouble("total_cost"));
        purchase.setInvoiceNumber(rs.getString("invoice_number"));
        
        return purchase;
    }

    /**
     * فئة إحصائيات المشتريات
     */
    public static class PurchaseStatistics {
        private final int totalRecords;
        private final double totalQuantity;
        private final double totalCost;
        private final double averageUnitPrice;
        private final LocalDate firstPurchaseDate;
        private final LocalDate lastPurchaseDate;
        
        public PurchaseStatistics(int totalRecords, double totalQuantity, double totalCost, double averageUnitPrice, LocalDate firstPurchaseDate, LocalDate lastPurchaseDate) {
            this.totalRecords = totalRecords;
            this.totalQuantity = totalQuantity;
            this.totalCost = totalCost;
            this.averageUnitPrice = averageUnitPrice;
            this.firstPurchaseDate = firstPurchaseDate;
            this.lastPurchaseDate = lastPurchaseDate;
        }
        
        public int getTotalRecords() { return totalRecords; }
        public double getTotalQuantity() { return totalQuantity; }
        public double getTotalCost() { return totalCost; }
        public double getAverageUnitPrice() { return averageUnitPrice; }
        public LocalDate getFirstPurchaseDate() { return firstPurchaseDate; }
        public LocalDate getLastPurchaseDate() { return lastPurchaseDate; }
        
        public double getAverageCostPerKg() {
            return totalQuantity > 0 ? totalCost / totalQuantity : 0;
        }
    }
    /**
     * تسجيل مرتجع شراء جديد وتحديث المخزون والحسابات.
     */
    public int addPurchaseReturn(PurchaseReturn purchaseReturn) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            
            // 1. إضافة سجل المرتجع
            String sql = "INSERT INTO purchase_returns (original_purchase_id, return_date, crop_id, quantity_kg, return_reason, returned_cost) VALUES (?, ?, ?, ?, ?, ?)";
            int returnId;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, purchaseReturn.getOriginalPurchase().getPurchaseId());
                stmt.setString(2, FormatUtils.formatDateForDatabase(purchaseReturn.getReturnDate()));
                stmt.setInt(3, purchaseReturn.getOriginalPurchase().getCrop().getCropId());
                stmt.setDouble(4, purchaseReturn.getQuantityKg());
                stmt.setString(5, purchaseReturn.getReturnReason());
                stmt.setDouble(6, purchaseReturn.getReturnedCost());
                
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        returnId = rs.getInt(1);
                    } else {
                        throw new SQLException("Creating purchase return failed, no ID obtained.");
                    }
                }
            }

            // 2. تسجيل القيد المزدوج
            String transactionRef = "PUR-RTN-" + returnId;
            String description = "مرتجع شراء للفاتورة رقم: " + purchaseReturn.getOriginalPurchase().getInvoiceNumber();

            // الطرف المدين: تخفيض الذمم الدائنة للمورد
            int accountsPayableId = 20101; // ID حساب الذمم الدائنة
            dataManager.addLedgerEntry(conn, transactionRef, purchaseReturn.getReturnDate(), accountsPayableId, purchaseReturn.getReturnedCost(), 0.0, description);

            // الطرف الدائن: تخفيض قيمة المخزون
            int inventoryAccountId = 10103; // ID حساب المخزون
            dataManager.addLedgerEntry(conn, transactionRef, purchaseReturn.getReturnDate(), inventoryAccountId, 0.0, purchaseReturn.getReturnedCost(), description);

            // 3. تحديث كمية المخزون (عملية إدارية)
            // ملاحظة: نستخدم التكلفة الأصلية للمحصول عند إرجاعه
            double originalUnitCost = purchaseReturn.getOriginalPurchase().getTotalCost() / purchaseReturn.getOriginalPurchase().getQuantityKg();
            dataManager.updateInventory(
                purchaseReturn.getOriginalPurchase().getCrop().getCropId(), 
                -purchaseReturn.getQuantityKg(), // الكمية بالسالب لأنها تخرج من المخزون
                originalUnitCost, 
                "OUT", 
                "PURCHASE_RETURN", 
                returnId, 
                conn
            );

            dataManager.logAuditEntry("purchase_returns", returnId, "INSERT", null, description, "SYSTEM", conn);
            
            return returnId;
        });
    }
}