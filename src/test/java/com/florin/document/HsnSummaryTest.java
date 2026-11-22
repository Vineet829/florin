package com.florin.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.tax.SupplyType;
import com.florin.tax.TaxRegime;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class HsnSummaryTest {

    private static Document gst(SupplyType supply, BillingItem... lines) {
        Document document = new Document();
        document.setDocumentType(DocumentType.INVOICE);
        document.setTaxRegime(TaxRegime.GST_INDIA);
        document.setSupplyType(supply);
        document.getBillingItems().addAll(List.of(lines));
        DocumentTotals.apply(document);
        return document;
    }

    private static BillingItem line(String code, String price, String rate) {
        BillingItem item = new BillingItem();
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(BigDecimal.ONE);
        item.setDiscount(BigDecimal.ZERO);
        item.setTaxRate(new BigDecimal(rate));
        item.setHsnSac(code);
        return item;
    }

    private static String row(HsnSummary.Row row) {
        return String.join(" ", String.valueOf(row.hsnSac()), row.rate().stripTrailingZeros().toPlainString(),
                row.taxable().toPlainString(), row.cgst().toPlainString(), row.sgst().toPlainString(),
                row.igst().toPlainString(), row.tax().toPlainString());
    }

    @Test
    void intraState() {
        Document document = gst(
                SupplyType.INTRA,
                line("998391", "10.05", "18"),
                line(null, "20", "5"),
                line("998314", "10.05", "18"));

        assertThat(document.getTaxSummaries().get(1).getCgst()).isEqualByComparingTo("1.81");
        assertThat(HsnSummary.of(document)).map(HsnSummaryTest::row).containsExactly(
                "998314 18 10.05 0.91 0.91 0.00 1.82",
                "998391 18 10.05 0.90 0.90 0.00 1.80",
                "null 5 20.00 0.50 0.50 0.00 1.00");
    }

    @Test
    void interState() {
        Document document = gst(
                SupplyType.INTER,
                line("998314", "0.05", "18"),
                line("998314", "100", "5"),
                line("4901", "0.03", "18"),
                line("998314", "0.05", "18"));

        assertThat(HsnSummary.of(document)).map(HsnSummaryTest::row).containsExactly(
                "4901 18 0.03 0.00 0.00 0.01 0.01",
                "998314 5 100.00 0.00 0.00 5.00 5.00",
                "998314 18 0.10 0.00 0.00 0.01 0.01");
    }

    @Test
    void reverseChargeAndExport() {
        Document reverse = gst(SupplyType.INTER, line("998314", "1000", "18"));
        reverse.setReverseCharge(true);
        DocumentTotals.apply(reverse);
        assertThat(reverse.getTotal()).isEqualByComparingTo("1000.00");
        assertThat(HsnSummary.of(reverse)).map(HsnSummaryTest::row)
                .containsExactly("998314 18 1000.00 0.00 0.00 180.00 180.00");

        Document export = gst(SupplyType.EXPORT_LUT, line("998314", "1000", "0"));
        assertThat(HsnSummary.of(export)).map(HsnSummaryTest::row)
                .containsExactly("998314 0 1000.00 0.00 0.00 0.00 0.00");
        Document draft = gst(SupplyType.EXPORT_LUT, line("998314", "1000", "18"));
        assertThat(HsnSummary.of(draft)).map(HsnSummaryTest::row)
                .containsExactly("998314 0 1000.00 0.00 0.00 0.00 0.00");
    }
}
