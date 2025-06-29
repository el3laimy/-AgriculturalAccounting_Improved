package accounting.model;

import java.util.Map;

public class IncomeStatement {

    private final double totalRevenue;
    private final double totalExpenses;
    private final double netIncome;
    private final Map<String, Double> revenueDetails;
    private final Map<String, Double> expenseDetails;

    public IncomeStatement(Map<String, Double> revenueDetails, Map<String, Double> expenseDetails) {
        this.revenueDetails = revenueDetails;
        this.expenseDetails = expenseDetails;
        this.totalRevenue = revenueDetails.values().stream().mapToDouble(Double::doubleValue).sum();
        this.totalExpenses = expenseDetails.values().stream().mapToDouble(Double::doubleValue).sum();
        this.netIncome = this.totalRevenue - this.totalExpenses;
    }

    // Getters
    public double getTotalRevenue() { return totalRevenue; }
    public double getTotalExpenses() { return totalExpenses; }
    public double getNetIncome() { return netIncome; }
    public Map<String, Double> getRevenueDetails() { return revenueDetails; }
    public Map<String, Double> getExpenseDetails() { return expenseDetails; }
}