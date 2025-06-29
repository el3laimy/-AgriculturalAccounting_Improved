package accounting.util;

import accounting.model.Crop;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import accounting.model.InventoryAdjustment;

/**
 * خدمات البيانات المحسنة للمحاصيل
 */
public class CropDataService {
    
    private static final Logger LOGGER = Logger.getLogger(CropDataService.class.getName());
    private final ImprovedDataManager dataManager;
    
    public CropDataService() {
        this.dataManager = ImprovedDataManager.getInstance();
    }
    /**
     * الحصول على قيمة المخزون لكل المحاصيل لعرضها في لوحة التحكم
     */
    public Map<String, Double> getInventoryValuePerCrop() throws SQLException {
        String sql = """
            SELECT c.crop_name, (i.current_stock_kg * i.average_cost_per_kg) as inventory_value
            FROM inventory i JOIN crops c ON i.crop_id = c.crop_id
            WHERE i.current_stock_kg > 0
            """;
        
        Map<String, Double> inventoryValues = new HashMap<>();
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while(rs.next()) {
                inventoryValues.put(rs.getString("crop_name"), rs.getDouble("inventory_value"));
            }
        }
        return inventoryValues;
    }
    
    /**
     * إضافة محصول جديد
     */
    public int addCrop(Crop crop) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            String query = """
                INSERT INTO crops (crop_name, allowed_pricing_units, conversion_factors)
                VALUES (?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, crop.getCropName());
                stmt.setString(2, convertListToJson(crop.getAllowedPricingUnits()));
                stmt.setString(3, convertMapToJson(crop.getConversionFactors()));
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("فشل في إضافة المحصول");
                }
                
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int cropId = generatedKeys.getInt(1);
                        
                        dataManager.logAuditEntry("crops", cropId, "INSERT", null, crop.getCropName(), "SYSTEM", conn);
                        
                        createInventoryRecord(conn, cropId);
                        return cropId;
                    } else {
                        throw new SQLException("فشل في الحصول على معرف المحصول الجديد");
                    }
                }
            }
        });
    }
    
    /**
     * تحديث محصول موجود
     */
    public boolean updateCrop(Crop crop) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            String oldValues = getCropAsJson(conn, crop.getCropId());
            
            String query = """
                UPDATE crops 
                SET crop_name = ?, allowed_pricing_units = ?, conversion_factors = ?, updated_at = CURRENT_TIMESTAMP
                WHERE crop_id = ? AND is_active = 1
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, crop.getCropName());
                stmt.setString(2, convertListToJson(crop.getAllowedPricingUnits()));
                stmt.setString(3, convertMapToJson(crop.getConversionFactors()));
                stmt.setInt(4, crop.getCropId());
                
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    dataManager.logAuditEntry("crops", crop.getCropId(), "UPDATE", 
                        oldValues, crop.getCropName(), "SYSTEM", conn);
                }
                
                return rowsAffected > 0;
            }
        });
    }
    
    /**
     * حذف محصول (حذف ناعم)
     */
    public boolean deleteCrop(int cropId) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            if (hasCropTransactions(conn, cropId)) {
                throw new SQLException("لا يمكن حذف المحصول لوجود معاملات مرتبطة به");
            }
            
            String oldValues = getCropAsJson(conn, cropId);
            
            String query = "UPDATE crops SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE crop_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, cropId);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    dataManager.logAuditEntry("crops", cropId, "DELETE", 
                        oldValues, null, "SYSTEM", conn);
                }
                
                return rowsAffected > 0;
            }
        });
    }
    
    /**
     * الحصول على محصول بالمعرف
     */
    public Crop getCropById(int cropId) throws SQLException {
        String query = """
            SELECT crop_id, crop_name, allowed_pricing_units, conversion_factors
            FROM crops 
            WHERE crop_id = ? AND is_active = 1
            """;
        
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, cropId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCrop(rs);
                }
            }
        }
        
        return null;
    }
    
    /**
     * الحصول على جميع المحاصيل النشطة
     */
    public List<Crop> getAllActiveCrops() throws SQLException {
        String query = """
            SELECT crop_id, crop_name, allowed_pricing_units, conversion_factors
            FROM crops 
            WHERE is_active = 1
            ORDER BY crop_name
            """;
        
        List<Crop> crops = new ArrayList<>();
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                crops.add(mapResultSetToCrop(rs));
            }
        }
        
        return crops;
    }
    
    /**
     * الحصول على إحصائيات المحصول
     */
    public CropStatistics getCropStatistics(int cropId, LocalDate fromDate, LocalDate toDate) throws SQLException {
        String query = """
            SELECT 
                c.crop_name,
                COALESCE(i.current_stock_kg, 0) as current_stock,
                COALESCE(i.average_cost_per_kg, 0) as average_cost,
                COALESCE(purchase_stats.total_purchased, 0) as total_purchased,
                COALESCE(purchase_stats.total_purchase_cost, 0) as total_purchase_cost,
                COALESCE(sale_stats.total_sold, 0) as total_sold,
                COALESCE(sale_stats.total_sale_revenue, 0) as total_sale_revenue
            FROM crops c
            LEFT JOIN inventory i ON c.crop_id = i.crop_id
            LEFT JOIN (
                SELECT crop_id, SUM(quantity_kg) as total_purchased, SUM(total_cost) as total_purchase_cost
                FROM purchases 
                WHERE crop_id = ? AND (? IS NULL OR purchase_date >= ?) AND (? IS NULL OR purchase_date <= ?)
            ) purchase_stats ON c.crop_id = purchase_stats.crop_id
            LEFT JOIN (
                SELECT crop_id, SUM(quantity_sold_kg) as total_sold, SUM(total_sale_amount) as total_sale_revenue
                FROM sales 
                WHERE crop_id = ? AND (? IS NULL OR sale_date >= ?) AND (? IS NULL OR sale_date <= ?)
            ) sale_stats ON c.crop_id = sale_stats.crop_id
            WHERE c.crop_id = ? AND c.is_active = 1
            """;
        
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            String fromDateStr = (fromDate != null) ? FormatUtils.formatDateForDatabase(fromDate) : null;
            String toDateStr = (toDate != null) ? FormatUtils.formatDateForDatabase(toDate) : null;

            stmt.setInt(1, cropId);
            stmt.setString(2, fromDateStr);
            stmt.setString(3, fromDateStr);
            stmt.setString(4, toDateStr);
            stmt.setString(5, toDateStr);

            stmt.setInt(6, cropId);
            stmt.setString(7, fromDateStr);
            stmt.setString(8, fromDateStr);
            stmt.setString(9, toDateStr);
            stmt.setString(10, toDateStr);

            stmt.setInt(11, cropId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CropStatistics(
                        rs.getString("crop_name"),
                        rs.getDouble("current_stock"),
                        rs.getDouble("average_cost"),
                        rs.getDouble("total_purchased"),
                        rs.getDouble("total_purchase_cost"),
                        rs.getDouble("total_sold"),
                        rs.getDouble("total_sale_revenue")
                    );
                }
            }
        }
        return null;
    }
    
    private Crop mapResultSetToCrop(ResultSet rs) throws SQLException {
        Crop crop = new Crop();
        crop.setCropId(rs.getInt("crop_id"));
        crop.setCropName(rs.getString("crop_name"));
        crop.setAllowedPricingUnits(convertJsonToList(rs.getString("allowed_pricing_units")));
        crop.setConversionFactors(convertJsonToMap(rs.getString("conversion_factors")));
        return crop;
    }
    
    private void createInventoryRecord(Connection conn, int cropId) throws SQLException {
        String query = "INSERT INTO inventory (crop_id, current_stock_kg, average_cost_per_kg) VALUES (?, 0, 0)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, cropId);
            stmt.executeUpdate();
        }
    }
    
    private boolean hasCropTransactions(Connection conn, int cropId) throws SQLException {
        String query = "SELECT 1 FROM purchases WHERE crop_id = ? UNION SELECT 1 FROM sales WHERE crop_id = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, cropId);
            stmt.setInt(2, cropId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private String getCropAsJson(Connection conn, int cropId) throws SQLException {
        String query = "SELECT crop_name FROM crops WHERE crop_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, cropId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("crop_name");
                }
            }
        }
        return null;
    }
    
    private String convertListToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return list.stream()
                   .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                   .collect(Collectors.joining(",", "[", "]"));
    }
    
    private String convertMapToJson(Map<String, List<Double>> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return map.entrySet().stream()
                .map(entry -> {
                    String values = entry.getValue().stream()
                                         .map(String::valueOf)
                                         .collect(Collectors.joining(","));
                    return "\"" + entry.getKey().replace("\"", "\\\"") + "\":[" + values + "]";
                })
                .collect(Collectors.joining(",", "{", "}"));
    }
    
    private List<String> convertJsonToList(String json) {
        if (json == null || json.trim().length() <= 2) {
            return new ArrayList<>();
        }
        String content = json.substring(1, json.length() - 1);
        if (content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(content.split(","))
                     .map(s -> s.replace("\"", "").trim())
                     .collect(Collectors.toList());
    }

    private Map<String, List<Double>> convertJsonToMap(String json) {
        Map<String, List<Double>> result = new HashMap<>();
        if (json == null || json.trim().length() <= 2) {
            return result;
        }
        String content = json.substring(1, json.length() - 1).trim();
        String[] entries = content.split("],");

        for (String entry : entries) {
            try {
                String[] parts = entry.split(":\\[");
                if (parts.length < 2) continue;
                String key = parts[0].replace("\"", "").trim();
                String valuePart = parts[1].replace("]", "").trim();
                if (!valuePart.isEmpty()) {
                    List<Double> values = Arrays.stream(valuePart.split(","))
                                                .map(String::trim)
                                                .map(Double::parseDouble)
                                                .collect(Collectors.toList());
                    result.put(key, values);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error parsing conversion factor entry: " + entry, e);
            }
        }
        return result;
    }
    /**
     * البحث عن محصول بالاسم (بما في ذلك المحذوفة)
     * @return كائن المحصول إذا وجد، وإلا null
     */
    public Crop findCropByName(String name) throws SQLException {
        String query = "SELECT * FROM crops WHERE crop_name = ?";
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCrop(rs);
                }
            }
        }
        return null;
    }
    /**
     * إعادة تفعيل محصول محذوف
     */
    public boolean reactivateCrop(int cropId) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            String query = "UPDATE crops SET is_active = 1, updated_at = CURRENT_TIMESTAMP WHERE crop_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, cropId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    dataManager.logAuditEntry("crops", cropId, "REACTIVATE", null, "Reactivated", "SYSTEM", conn);
                }
                return rowsAffected > 0;
            }
        });
    }
    /**
     * الحصول على إحصائيات المخزون لجميع المحاصيل النشطة.
     * @return قائمة بإحصائيات المحاصيل.
     * @throws SQLException في حالة حدوث خطأ في قاعدة البيانات.
     */
    public List<CropStatistics> getAllCropStatistics() throws SQLException {
        List<CropStatistics> allStats = new ArrayList<>();
        List<Crop> activeCrops = getAllActiveCrops();

        for (Crop crop : activeCrops) {
            // نستدعي الدالة الموجودة للحصول على إحصائيات كل محصول على حدة
            // نمرر null للتواريخ لجلب الإحصائيات الكاملة
            CropStatistics stats = getCropStatistics(crop.getCropId(), null, null);
            if (stats != null) {
                allStats.add(stats);
            }
        }
        return allStats;
    }

    
    /**
     * فئة إحصائيات المحصول
     */
    public static class CropStatistics {
        private final String cropName;
        private final double currentStock;
        private final double averageCost;
        private final double totalPurchased;
        private final double totalPurchaseCost;
        private final double totalSold;
        private final double totalSaleRevenue;
        
        public CropStatistics(String cropName, double currentStock, double averageCost,
                             double totalPurchased, double totalPurchaseCost,
                             double totalSold, double totalSaleRevenue) {
            this.cropName = cropName;
            this.currentStock = currentStock;
            this.averageCost = averageCost;
            this.totalPurchased = totalPurchased;
            this.totalPurchaseCost = totalPurchaseCost;
            this.totalSold = totalSold;
            this.totalSaleRevenue = totalSaleRevenue;
        }
        
        // Getters
        public String getCropName() { return cropName; }
        public double getCurrentStock() { return currentStock; }
        public double getAverageCost() { return averageCost; }
        public double getTotalPurchased() { return totalPurchased; }
        public double getTotalPurchaseCost() { return totalPurchaseCost; }
        public double getTotalSold() { return totalSold; }
        public double getTotalSaleRevenue() { return totalSaleRevenue; }
        
        public double getGrossProfit() {
            return totalSaleRevenue - (totalSold * averageCost);
        }
        
        public double getProfitMargin() {
            return totalSaleRevenue > 0 ? (getGrossProfit() / totalSaleRevenue) * 100 : 0;
        }
        
        public double getInventoryValue() {
            return currentStock * averageCost;
        }
        
        public double getAveragePurchasePrice() {
            if (totalPurchased == 0) return 0;
            return totalPurchaseCost / totalPurchased;
        }

        public double getAverageSellingPrice() {
            if (totalSold == 0) return 0;
            return totalSaleRevenue / totalSold;
        }

        public double getTurnoverRate() {
            if (currentStock == 0) return 0;
            return totalSold / currentStock;
        }
    }
    /**
     * تسجيل تسوية مخزون جديدة، وتحديث كمية المخزون ودفتر الأستاذ.
     */
    public int addInventoryAdjustment(InventoryAdjustment adjustment) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            
            // 1. حساب تكلفة الكمية المعدلة بناءً على متوسط التكلفة الحالي للمخزون
            CropStatistics stats = getCropStatistics(adjustment.getCrop().getCropId(), null, null);
            double unitCost = (stats != null) ? stats.getAverageCost() : 0;
            double totalCost = unitCost * adjustment.getQuantityKg();
            adjustment.setCost(totalCost);

            // 2. إضافة سجل التسوية
            String sql = "INSERT INTO inventory_adjustments (crop_id, adjustment_date, adjustment_type, quantity_kg, reason, cost) VALUES (?, ?, ?, ?, ?, ?)";
            int adjustmentId;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, adjustment.getCrop().getCropId());
                stmt.setString(2, FormatUtils.formatDateForDatabase(adjustment.getAdjustmentDate()));
                stmt.setString(3, adjustment.getAdjustmentType().name());
                stmt.setDouble(4, adjustment.getQuantityKg());
                stmt.setString(5, adjustment.getReason());
                stmt.setDouble(6, totalCost);
                
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        adjustmentId = rs.getInt(1);
                    } else {
                        throw new SQLException("Creating inventory adjustment failed, no ID obtained.");
                    }
                }
            }

            // 3. تسجيل القيد المزدوج
            String transactionRef = "INV-ADJ-" + adjustmentId;
            String description = "تسوية مخزون: " + adjustment.getAdjustmentType().getArabicName() + " لـ " + adjustment.getCrop().getCropName();
            double quantityForUpdate = adjustment.getQuantityKg();

            int debitAccountId;
            int creditAccountId;
            int inventoryAccountId = 10103; // ID حساب المخزون

            if (adjustment.getAdjustmentType() == InventoryAdjustment.AdjustmentType.SURPLUS) {
                // حالة الزيادة
                debitAccountId = inventoryAccountId; // مدين: المخزون
                creditAccountId = 40103; // مثال: ID حساب "أرباح فروقات المخزون" (يجب إنشاؤه)
            } else {
                // حالة التلف أو العجز
                creditAccountId = inventoryAccountId; // دائن: المخزون
                debitAccountId = 50103; // مثال: ID حساب "خسائر المخزون" (يجب إنشاؤه)
                quantityForUpdate = -quantityForUpdate; // الكمية بالسالب لأنها تنقص
            }

            dataManager.addLedgerEntry(conn, transactionRef, adjustment.getAdjustmentDate(), debitAccountId, totalCost, 0.0, description);
            dataManager.addLedgerEntry(conn, transactionRef, adjustment.getAdjustmentDate(), creditAccountId, 0.0, totalCost, description);

            // 4. تحديث كمية المخزون
            dataManager.updateInventory(adjustment.getCrop().getCropId(), quantityForUpdate, unitCost, "ADJUSTMENT", "INV_ADJUST", adjustmentId, conn);

            dataManager.logAuditEntry("inventory_adjustments", adjustmentId, "INSERT", null, description, "SYSTEM", conn);
            
            return adjustmentId;
        });
    }
}