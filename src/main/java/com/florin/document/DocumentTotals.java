package com.florin.document;

import com.florin.tax.SupplyType;
import com.florin.tax.TaxRegime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class DocumentTotals {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO_HUNDRED = BigDecimal.valueOf(200);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private DocumentTotals() {}

    public static BigDecimal lineTotal(BillingItem item) {
        BigDecimal quantity = orZero(item.getQuantity());
        BigDecimal kept = ONE_HUNDRED.subtract(orZero(item.getDiscount()));
        return quantity.multiply(orZero(item.getUnitPrice()))
                .multiply(kept)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static void apply(Document document) {
        compute(document);
        settle(document);
    }

    static void compute(Document document) {
        BigDecimal subTotal = BigDecimal.ZERO.setScale(2);
        Map<BigDecimal, BigDecimal> taxable = new TreeMap<>();
        for (BillingItem item : document.getBillingItems()) {
            BigDecimal line = lineTotal(item);
            subTotal = subTotal.add(line);
            taxable.merge(rate(document, item), line, BigDecimal::add);
        }
        boolean gst = document.getTaxRegime() == TaxRegime.GST_INDIA && document.getSupplyType() != null;
        BigDecimal salesTax = BigDecimal.ZERO.setScale(2);
        document.getTaxSummaries().clear();
        for (Map.Entry<BigDecimal, BigDecimal> rate : taxable.entrySet()) {
            TaxSummary summary = summary(rate.getKey(), rate.getValue(), gst ? document.getSupplyType() : null);
            document.getTaxSummaries().add(summary);
            salesTax = salesTax.add(summary.getTax());
        }
        if (gst && document.isReverseCharge()) {
            salesTax = BigDecimal.ZERO.setScale(2);
        }
        document.setSubTotal(subTotal);
        document.setSalesTax(salesTax);
        document.setTotal(subTotal.add(salesTax));
    }

    static BigDecimal rate(Document document, BillingItem item) {
        return document.getSupplyType() == SupplyType.EXPORT_LUT ? BigDecimal.ZERO : orZero(item.getTaxRate());
    }

    static TaxSummary summary(BigDecimal rate, BigDecimal taxable, SupplyType supply) {
        BigDecimal scaled = rate.setScale(4);
        if (supply == SupplyType.INTRA) {
            BigDecimal half = taxable.multiply(rate).divide(TWO_HUNDRED, 2, RoundingMode.HALF_UP);
            return new TaxSummary(scaled, taxable, half.add(half), half, half, ZERO);
        }
        BigDecimal tax = taxable.multiply(rate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        return supply == null
                ? new TaxSummary(scaled, taxable, tax)
                : new TaxSummary(scaled, taxable, tax, ZERO, ZERO, tax);
    }

    public static BigDecimal singleRate(Document document) {
        List<TaxSummary> summaries = document.getTaxSummaries();
        if (summaries.size() > 1) {
            return null;
        }
        return summaries.isEmpty() ? BigDecimal.ZERO : summaries.get(0).getRate();
    }

    public static void settle(Document document) {
        BigDecimal total = orZero(document.getTotal());
        if (document.getDocumentType() == DocumentType.CREDIT_NOTE) {
            document.setTotalAmountReceived(ZERO);
            document.setTotalTds(ZERO);
            document.setTotalCredited(ZERO);
            document.setTotalRefunded(ZERO);
            document.setStatus(DocumentStatus.NOT_PAID);
            return;
        }
        document.setTotalCredited(credited(document));
        document.setTotalRefunded(refunded(document));
        if (document.getDocumentType() == DocumentType.RECEIPT) {
            document.setTotalAmountReceived(total);
            document.setTotalTds(ZERO);
            document.setStatus(DocumentStatus.PAID);
            return;
        }
        BigDecimal received = received(document);
        BigDecimal withheld = withheld(document);
        document.setTotalAmountReceived(received);
        document.setTotalTds(withheld);
        boolean free = document.getDocumentType() == DocumentType.INVOICE
                && !document.getBillingItems().isEmpty()
                && total.signum() == 0;
        document.setStatus(free
                ? DocumentStatus.PAID
                : status(
                        total,
                        received.add(withheld).subtract(document.getTotalRefunded()),
                        document.getTotalCredited()));
    }

    public static BigDecimal balanceDue(Document document) {
        if (document.getDocumentType() == DocumentType.CREDIT_NOTE) {
            return ZERO;
        }
        return orZero(document.getTotal())
                .subtract(orZero(document.getTotalCredited()))
                .subtract(orZero(document.getTotalAmountReceived()))
                .subtract(orZero(document.getTotalTds()))
                .add(orZero(document.getTotalRefunded()))
                .max(BigDecimal.ZERO);
    }

    public static BigDecimal refundDue(Document document) {
        BigDecimal refunded = orZero(document.getTotalRefunded());
        BigDecimal credited = orZero(document.getTotalCredited());
        return switch (document.getDocumentType()) {
            case INVOICE -> {
                BigDecimal received = orZero(document.getTotalAmountReceived());
                BigDecimal over = received.add(orZero(document.getTotalTds()))
                        .subtract(refunded)
                        .add(credited)
                        .subtract(orZero(document.getTotal()))
                        .max(BigDecimal.ZERO);
                yield over.min(received.subtract(refunded)).max(ZERO);
            }
            case RECEIPT -> credited.subtract(refunded).max(ZERO);
            default -> ZERO;
        };
    }

    public static BigDecimal refunded(Document document) {
        return document.getRefunds().stream()
                .map(Refund::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    public static BigDecimal credited(Document document) {
        return document.getCreditNotes().stream()
                .filter(note -> note.state() == DocumentState.ISSUED)
                .map(note -> orZero(note.getTotal()))
                .reduce(ZERO, BigDecimal::add);
    }

    public static BigDecimal creditable(Document document) {
        return orZero(document.getTotal()).subtract(credited(document)).max(BigDecimal.ZERO);
    }

    public static BigDecimal received(Document document) {
        return document.getPayments().stream()
                .map(Payment::getAmountPaid)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public static BigDecimal withheld(Document document) {
        return document.getPayments().stream()
                .map(Payment::getTdsAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public static boolean isOverdue(Document document, Instant startOfToday) {
        return document.getDocumentType() == DocumentType.INVOICE
                && document.state() == DocumentState.ISSUED
                && document.getStatus() != DocumentStatus.PAID
                && orZero(document.getTotal()).signum() > 0
                && document.getDueDate() != null
                && document.getDueDate().isBefore(startOfToday);
    }

    public static DocumentStatus status(BigDecimal total, BigDecimal settled) {
        return status(total, settled, BigDecimal.ZERO);
    }

    public static DocumentStatus status(BigDecimal total, BigDecimal paid, BigDecimal credited) {
        if (total.signum() > 0 && paid.add(credited).compareTo(total) >= 0) {
            return DocumentStatus.PAID;
        }
        return paid.signum() > 0 ? DocumentStatus.NOT_FULLY_PAID : DocumentStatus.NOT_PAID;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
