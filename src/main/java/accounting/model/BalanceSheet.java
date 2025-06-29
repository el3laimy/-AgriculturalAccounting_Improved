package accounting.model;

import java.util.Map;

public class BalanceSheet {

    private final Map<String, Double> assets;
    private final Map<String, Double> liabilities;
    private final Map<String, Double> equity;

    private final double totalAssets;
    private final double totalLiabilities;
    private final double totalEquity;
    private final double totalLiabilitiesAndEquity;

    public BalanceSheet(Map<String, Double> assets, Map<String, Double> liabilities, Map<String, Double> equity) {
        this.assets = assets;
        this.liabilities = liabilities;
        this.equity = equity;

        this.totalAssets = assets.values().stream().mapToDouble(Double::doubleValue).sum();
        this.totalLiabilities = liabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        this.totalEquity = equity.values().stream().mapToDouble(Double::doubleValue).sum();
        this.totalLiabilitiesAndEquity = this.totalLiabilities + this.totalEquity;
    }

    // Getters
    public Map<String, Double> getAssets() { return assets; }
    public Map<String, Double> getLiabilities() { return liabilities; }
    public Map<String, Double> getEquity() { return equity; }
    public double getTotalAssets() { return totalAssets; }
    public double getTotalLiabilities() { return totalLiabilities; }
    public double getTotalEquity() { return totalEquity; }
    public double getTotalLiabilitiesAndEquity() { return totalLiabilitiesAndEquity; }
}