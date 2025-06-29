package accounting.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * نموذج بيانات سجل البيع المحسن
 */
public class SaleRecord {
    private int saleId;
    private Contact customer;
    private Crop crop;
    private double quantitySoldKg;
    private String sellingPricingUnit;
    private double specificSellingFactor;
    private double sellingUnitPrice;
    private double totalSaleAmount;
    private LocalDate saleDate;
    private String saleInvoiceNumber;

    // Constructors
    public SaleRecord() {}

    public SaleRecord(int saleId, Contact customer, Crop crop, double quantitySoldKg,
                     String sellingPricingUnit, double specificSellingFactor,
                     double sellingUnitPrice, double totalSaleAmount,
                     LocalDate saleDate, String saleInvoiceNumber) {
        this.saleId = saleId;
        this.customer = customer;
        this.crop = crop;
        this.quantitySoldKg = quantitySoldKg;
        this.sellingPricingUnit = sellingPricingUnit;
        this.specificSellingFactor = specificSellingFactor;
        this.sellingUnitPrice = sellingUnitPrice;
        this.totalSaleAmount = totalSaleAmount;
        this.saleDate = saleDate;
        this.saleInvoiceNumber = saleInvoiceNumber;
    }

    // Getters and Setters
    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }

    public Contact getCustomer() { return customer; }
    public void setCustomer(Contact customer) { this.customer = customer; }

    public Crop getCrop() { return crop; }
    public void setCrop(Crop crop) { this.crop = crop; }

    public double getQuantitySoldKg() { return quantitySoldKg; }
    public void setQuantitySoldKg(double quantitySoldKg) { this.quantitySoldKg = quantitySoldKg; }

    public String getSellingPricingUnit() { return sellingPricingUnit; }
    public void setSellingPricingUnit(String sellingPricingUnit) { this.sellingPricingUnit = sellingPricingUnit; }

    public double getSpecificSellingFactor() { return specificSellingFactor; }
    public void setSpecificSellingFactor(double specificSellingFactor) { this.specificSellingFactor = specificSellingFactor; }

    public double getSellingUnitPrice() { return sellingUnitPrice; }
    public void setSellingUnitPrice(double sellingUnitPrice) { this.sellingUnitPrice = sellingUnitPrice; }

    public double getTotalSaleAmount() { return totalSaleAmount; }
    public void setTotalSaleAmount(double totalSaleAmount) { this.totalSaleAmount = totalSaleAmount; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public String getSaleInvoiceNumber() { return saleInvoiceNumber; }
    public void setSaleInvoiceNumber(String saleInvoiceNumber) { this.saleInvoiceNumber = saleInvoiceNumber; }

    /**
     * يحسب الكمية بوحدة التسعير
     * @return الكمية بوحدة التسعير
     */
    public double getQuantityInSellingUnit() {
        if (specificSellingFactor > 0) {
            return quantitySoldKg / specificSellingFactor;
        }
        return quantitySoldKg;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SaleRecord that = (SaleRecord) obj;
        return saleId == that.saleId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(saleId);
    }
}

