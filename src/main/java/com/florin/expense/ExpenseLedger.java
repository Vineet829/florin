package com.florin.expense;

public final class ExpenseLedger {

    public static final String SPENT = "(e.amount + case when e.tax_reclaimable then 0 else e.tax_amount end)";

    public static final String PAID = "(e.amount + e.tax_amount)";

    private ExpenseLedger() {}
}
