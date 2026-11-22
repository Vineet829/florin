package com.florin.document;

public final class DocumentLedger {

    public static final String COUNTED = "d.issued_at is not null and d.voided_at is null";

    public static final String OWED =
            "(d.total - d.total_credited - d.total_amount_received - d.total_tds + d.total_refunded)";

    public static final String BILLED = "(case when d.document_type = 'Credit Note' then -d.total else d.total end)";

    public static final String BILLED_DOCUMENT =
            COUNTED + " and d.document_type in ('Invoice', 'Receipt', 'Credit Note')";

    public static final String OWED_INVOICE = COUNTED + " and d.document_type = 'Invoice' and " + OWED + " > 0";

    private DocumentLedger() {}
}
