package accounting.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * نموذج بيانات سجل الشراء المحسن
 */
public class PurchaseRecord {
    private int purchaseId;
    private Crop crop;
    private Contact supplier;
    private double quantityKg;
    private String pricingUnit;
    private double specificFactor;
    private double unitPrice;
    private double totalCost;
    private LocalDate purchaseDate;
    private String invoiceNumber;

    // Constructors
    public PurchaseRecord() {}

    public PurchaseRecord(int purchaseId, Crop crop, Contact supplier, double quantityKg,
                         String pricingUnit, double specificFactor, double unitPrice,
                         double totalCost, LocalDate purchaseDate, String invoiceNumber) {
        this.purchaseId = purchaseId;
        this.crop = crop;
        this.supplier = supplier;
        this.quantityKg = quantityKg;
        this.pricingUnit = pricingUnit;
        this.specificFactor = specificFactor;
        this.unitPrice = unitPrice;
        this.totalCost = totalCost;
        this.purchaseDate = purchaseDate;
        this.invoiceNumber = invoiceNumber;
    }

    // Getters and Setters
    public int getPurchaseId() { return purchaseId; }
    public void setPurchaseId(int purchaseId) { this.purchaseId = purchaseId; }

    public Crop getCrop() { return crop; }
    public void setCrop(Crop crop) { this.crop = crop; }

    public Contact getSupplier() { return supplier; }
    public void setSupplier(Contact supplier) { this.supplier = supplier; }

    public double getQuantityKg() { return quantityKg; }
    public void setQuantityKg(double quantityKg) { this.quantityKg = quantityKg; }

    public String getPricingUnit() { return pricingUnit; }
    public void setPricingUnit(String pricingUnit) { this.pricingUnit = pricingUnit; }

    public double getSpecificFactor() { return specificFactor; }
    public void setSpecificFactor(double specificFactor) { this.specificFactor = specificFactor; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    /**
     * يحسب الكمية بوحدة التسعير
     * @return الكمية بوحدة التسعير
     */
    public double getQuantityInPricingUnit() {
        if (specificFactor > 0) {
            return quantityKg / specificFactor;
        }
        return quantityKg;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PurchaseRecord that = (PurchaseRecord) obj;
        return purchaseId == that.purchaseId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(purchaseId);
    }
}

