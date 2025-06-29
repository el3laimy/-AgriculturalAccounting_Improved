package accounting.util;

import accounting.model.Contact;
import accounting.model.Crop;
import accounting.model.FinancialAccount;
import accounting.model.SaleRecord;
import accounting.model.SaleReturn;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * خدمات البيانات المحسنة للمبيعات
 */
public class SaleDataService {

    private static final Logger LOGGER = Logger.getLogger(SaleDataService.class.getName());
    private final ImprovedDataManager dataManager;
    private final CropDataService cropDataService;

    public SaleDataService() {
        this.dataManager = ImprovedDataManager.getInstance();
        this.cropDataService = new CropDataService();
    }

    public int addSale(SaleRecord sale, FinancialAccount paymentAccount, double amountReceived) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            
            double finalAmountReceived = amountReceived;
            String paymentStatus;
            if (finalAmountReceived <= 0) {
                paymentStatus = "PENDING";
            } else if (finalAmountReceived >= sale.getTotalSaleAmount()) {
                paymentStatus = "PAID";
                finalAmountReceived = sale.getTotalSaleAmount();
            } else {
                paymentStatus = "PARTIAL";
            }

            String sql = "INSERT INTO sales(crop_id, customer_id, sale_date, quantity_sold_kg, selling_pricing_unit, specific_selling_factor, selling_unit_price, total_sale_amount, sale_invoice_number, amount_paid, payment_status) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            int saleId;
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, sale.getCrop().getCropId());
                pstmt.setInt(2, sale.getCustomer().getContactId());
                pstmt.setString(3, FormatUtils.formatDateForDatabase(sale.getSaleDate()));
                pstmt.setDouble(4, sale.getQuantitySoldKg());
                pstmt.setString(5, sale.getSellingPricingUnit());
                pstmt.setDouble(6, sale.getSpecificSellingFactor());
                pstmt.setDouble(7, sale.getSellingUnitPrice());
                pstmt.setDouble(8, sale.getTotalSaleAmount());
                pstmt.setString(9, sale.getSaleInvoiceNumber());
                pstmt.setDouble(10, finalAmountReceived);
                pstmt.setString(11, paymentStatus);
                
                pstmt.executeUpdate();

                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        saleId = generatedKeys.getInt(1);
                        sale.setSaleId(saleId); // تحديث الـ ID في الكائن لاستخدامه لاحقاً
                    } else {
                        throw new SQLException("Creating sale failed, no ID obtained.");
                    }
                }
            }

            // --- *** بداية التعديل الجوهري لتطبيق القيد المزدوج *** ---

            String transactionRef = "SAL-" + saleId;
            String saleDescription = "بيع فاتورة رقم: " + sale.getSaleInvoiceNumber();

            // **القيد الأول: إثبات الإيرادات والمستحقات**
            // الطرف المدين: إما النقدية/البنك أو الذمم المدينة
            if (finalAmountReceived >= sale.getTotalSaleAmount()) {
                // حالة البيع النقدي الكامل: الطرف المدين هو حساب النقدية/البنك
                dataManager.addLedgerEntry(conn, transactionRef, sale.getSaleDate(), paymentAccount.getAccountId(), sale.getTotalSaleAmount(), 0.0, saleDescription);
            } else {
                // حالة البيع الآجل أو الدفع الجزئي
                if (finalAmountReceived > 0) {
                    // قيد مدين بالجزء المحصل في حساب النقدية/البنك
                    dataManager.addLedgerEntry(conn, transactionRef, sale.getSaleDate(), paymentAccount.getAccountId(), finalAmountReceived, 0.0, "تحصيل جزء من " + saleDescription);
                }
                // قيد مدين بالجزء المتبقي في حساب الذمم المدينة
                int accountsReceivableId = 10104; // ID حساب الذمم المدينة (العملاء) من شجرة الحسابات
                dataManager.addLedgerEntry(conn, transactionRef, sale.getSaleDate(), accountsReceivableId, sale.getTotalSaleAmount() - finalAmountReceived, 0.0, saleDescription);
            }
            // الطرف الدائن: إيرادات المبيعات
            int salesRevenueAccountId = 40101; // ID حساب إيرادات المبيعات من شجرة الحسابات
            dataManager.addLedgerEntry(conn, transactionRef, sale.getSaleDate(), salesRevenueAccountId, 0.0, sale.getTotalSaleAmount(), saleDescription);


            // **القيد الثاني: إثبات تكلفة البضاعة المباعة وتخفيض المخزون**
            CropDataService.CropStatistics stats = cropDataService.getCropStatistics(sale.getCrop().getCropId(), null, null);
            double unitCost = (stats != null) ? stats.getAverageCost() : 0;
            double costOfGoodsSold = unitCost * sale.getQuantitySoldKg();
            
            if(costOfGoodsSold > 0) {
                String cogsDescription = "تكلفة بضاعة مباعة للفاتورة " + sale.getSaleInvoiceNumber();
                // الطرف المدين: زيادة مصروف تكلفة البضاعة المباعة
                int cogsAccountId = 50101; // ID حساب تكلفة البضاعة المباعة من شجرة الحسابات
                dataManager.addLedgerEntry(conn, transactionRef, sale.getSaleDate(), cogsAccountId, costOfGoodsSold, 0.0, cogsDescription);
                
                // الطرف الدائن: تخفيض المخزون
                int inventoryAccountId = 10103; // ID حساب المخزون من شجرة الحسابات
                dataManager.addLedgerEntry(conn, transactionRef, sale.getSaleDate(), inventoryAccountId, 0.0, costOfGoodsSold, cogsDescription);
            }
            
            // --- *** نهاية التعديل الجوهري *** ---

            // تحديث كمية المخزون (عملية إدارية)
            dataManager.updateInventory(sale.getCrop().getCropId(), -sale.getQuantitySoldKg(), unitCost, "OUT", "SALE", saleId, conn);
            
            // تسجيل عملية التدقيق
            dataManager.logAuditEntry("sales", saleId, "INSERT", null, sale.getSaleInvoiceNumber(), "SYSTEM", conn);
            
            return saleId;
        });
    }

    public List<SaleRecord> getSales(LocalDate fromDate, LocalDate toDate, Integer cropId, Integer customerId, int limit, int offset) throws SQLException {
        StringBuilder queryBuilder = new StringBuilder("""
            SELECT s.*, c.crop_name, ct.name as customer_name
            FROM sales s
            JOIN crops c ON s.crop_id = c.crop_id
            JOIN contacts ct ON s.customer_id = ct.contact_id
            WHERE 1=1
            """);
        
        List<Object> parameters = new ArrayList<>();
        
        if (fromDate != null) {
            queryBuilder.append(" AND s.sale_date >= ?");
            parameters.add(FormatUtils.formatDateForDatabase(fromDate));
        }
        if (toDate != null) {
            queryBuilder.append(" AND s.sale_date <= ?");
            parameters.add(FormatUtils.formatDateForDatabase(toDate));
        }
        if (cropId != null) {
            queryBuilder.append(" AND s.crop_id = ?");
            parameters.add(cropId);
        }
        if (customerId != null) {
            queryBuilder.append(" AND s.customer_id = ?");
            parameters.add(customerId);
        }
        queryBuilder.append(" ORDER BY s.sale_date DESC, s.sale_id DESC");
        
        if (limit > 0) {
            queryBuilder.append(" LIMIT ? OFFSET ?");
            parameters.add(limit);
            parameters.add(offset);
        }
        
        List<SaleRecord> sales = new ArrayList<>();
        Connection conn = null;
        try {
            conn = dataManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
                for (int i = 0; i < parameters.size(); i++) {
                    stmt.setObject(i + 1, parameters.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        sales.add(mapResultSetToSale(rs));
                    }
                }
            }
        } finally {
            dataManager.returnConnection(conn);
        }
        return sales;
    }

    private SaleRecord mapResultSetToSale(ResultSet rs) throws SQLException {
        Crop crop = new Crop();
        crop.setCropId(rs.getInt("crop_id"));
        crop.setCropName(rs.getString("crop_name"));
        
        Contact customer = new Contact();
        customer.setContactId(rs.getInt("customer_id"));
        customer.setName(rs.getString("customer_name"));

        return new SaleRecord(
            rs.getInt("sale_id"),
            customer,
            crop,
            rs.getDouble("quantity_sold_kg"),
            rs.getString("selling_pricing_unit"),
            rs.getDouble("specific_selling_factor"),
            rs.getDouble("selling_unit_price"),
            rs.getDouble("total_sale_amount"),
            FormatUtils.parseDateFromDatabase(rs.getString("sale_date")),
            rs.getString("sale_invoice_number")
        );
    }

    public Map<String, Double> getSalesStatistics(LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = "SELECT SUM(total_sale_amount) as total_revenue, COUNT(*) as sales_count FROM sales WHERE sale_date BETWEEN ? AND ?";
        Map<String, Double> stats = new HashMap<>();
        stats.put("total_revenue", 0.0);
        stats.put("sales_count", 0.0);

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, FormatUtils.formatDateForDatabase(fromDate));
            stmt.setString(2, FormatUtils.formatDateForDatabase(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_revenue", rs.getDouble("total_revenue"));
                    stats.put("sales_count", rs.getDouble("sales_count"));
                }
            }
        }
        return stats;
    }

    public Map<String, Number> getMonthlySalesForChart(int year) throws SQLException {
        String sql = "SELECT strftime('%m', sale_date) as month, SUM(total_sale_amount) as monthly_total " +
                     "FROM sales WHERE strftime('%Y', sale_date) = ? " +
                     "GROUP BY month ORDER BY month";
        
        Map<String, Number> monthlySales = new LinkedHashMap<>();
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, String.valueOf(year));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    monthlySales.put(rs.getString("month"), rs.getDouble("monthly_total"));
                }
            }
        }
        return monthlySales;
    }
    /**
     * تسجيل مرتجع مبيعات جديد وتحديث المخزون والحسابات.
     */
    public int addSaleReturn(SaleReturn saleReturn) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            
            // 1. إضافة سجل المرتجع إلى قاعدة البيانات
            String sql = "INSERT INTO sale_returns (original_sale_id, return_date, crop_id, quantity_kg, return_reason, refund_amount) VALUES (?, ?, ?, ?, ?, ?)";
            int returnId;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, saleReturn.getOriginalSale().getSaleId());
                stmt.setString(2, FormatUtils.formatDateForDatabase(saleReturn.getReturnDate()));
                stmt.setInt(3, saleReturn.getOriginalSale().getCrop().getCropId());
                stmt.setDouble(4, saleReturn.getQuantityKg());
                stmt.setString(5, saleReturn.getReturnReason());
                stmt.setDouble(6, saleReturn.getRefundAmount());
                
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        returnId = rs.getInt(1);
                    } else {
                        throw new SQLException("Creating sale return failed, no ID obtained.");
                    }
                }
            }

            // 2. تسجيل القيود المحاسبية المزدوجة
            String transactionRef = "SAL-RTN-" + returnId;
            String description = "مرتجع مبيعات من فاتورة رقم: " + saleReturn.getOriginalSale().getSaleInvoiceNumber();

            // **القيد الأول: عكس الإيراد وتخفيض مستحقات العميل**
            // الطرف المدين: حساب مرتجعات المبيعات (حساب جديد يجب إنشاؤه من نوع REVENUE)
            int salesReturnAccountId = 40102; // مثال: افترض أن ID حساب مرتجعات المبيعات هو 40102
            dataManager.addLedgerEntry(conn, transactionRef, saleReturn.getReturnDate(), salesReturnAccountId, saleReturn.getRefundAmount(), 0.0, description);

            // الطرف الدائن: تخفيض الذمم المدينة للعميل
            int accountsReceivableId = 10104; // ID حساب الذمم المدينة
            dataManager.addLedgerEntry(conn, transactionRef, saleReturn.getReturnDate(), accountsReceivableId, 0.0, saleReturn.getRefundAmount(), description);

            // **القيد الثاني: إعادة البضاعة للمخزون وعكس التكلفة**
            double originalUnitCost = saleReturn.getOriginalSale().getTotalSaleAmount() / saleReturn.getOriginalSale().getQuantitySoldKg(); // يجب استخدام التكلفة وليس سعر البيع
            double costOfReturnedGoods = originalUnitCost * saleReturn.getQuantityKg();
            
            if (costOfReturnedGoods > 0) {
                String cogsDescription = "عكس تكلفة بضاعة مرتجعة للفاتورة " + saleReturn.getOriginalSale().getSaleInvoiceNumber();
                // الطرف المدين: زيادة المخزون مرة أخرى
                int inventoryAccountId = 10103; // ID حساب المخزون
                dataManager.addLedgerEntry(conn, transactionRef, saleReturn.getReturnDate(), inventoryAccountId, costOfReturnedGoods, 0.0, cogsDescription);
                
                // الطرف الدائن: تخفيض مصروف تكلفة البضاعة المباعة
                int cogsAccountId = 50101; // ID حساب تكلفة البضاعة المباعة
                dataManager.addLedgerEntry(conn, transactionRef, saleReturn.getReturnDate(), cogsAccountId, 0.0, costOfReturnedGoods, cogsDescription);
            }

            // 3. تحديث كمية المخزون (عملية إدارية)
            dataManager.updateInventory(
                saleReturn.getOriginalSale().getCrop().getCropId(), 
                saleReturn.getQuantityKg(), // الكمية بالموجب لأنها تعود للمخزون
                originalUnitCost, 
                "IN", 
                "SALE_RETURN", 
                returnId, 
                conn
            );

            dataManager.logAuditEntry("sale_returns", returnId, "INSERT", null, description, "SYSTEM", conn);
            
            return returnId;
        });
    }
}