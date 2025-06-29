package accounting.model;

import java.time.LocalDate;

/**
 * يمثل سطراً واحداً في كشف حساب (لعميل أو مورد).
 * هذا الكلاس يساعد على توحيد عرض أنواع مختلفة من الحركات (بيع، شراء، دفعات) في جدول واحد.
 */
public class LedgerEntry {

    private final LocalDate date;
    private final String description;
    private final String reference; // رقم الفاتورة أو الإيصال
    private final double debit;     // مدين (مبلغ له)
    private final double credit;    // دائن (مبلغ عليه)
    private double balance;   // الرصيد بعد الحركة

    public LedgerEntry(LocalDate date, String description, String reference, double debit, double credit) {
        this.date = date;
        this.description = description;
        this.reference = reference;
        this.debit = debit;
        this.credit = credit;
        this.balance = 0; // سيتم حسابه لاحقاً
    }

    // Getters
    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public double getDebit() {
        return debit;
    }

    public double getCredit() {
        return credit;
    }

    public double getBalance() {
        return balance;
    }

    // Setter for the running balance
    public void setBalance(double balance) {
        this.balance = balance;
    }
}