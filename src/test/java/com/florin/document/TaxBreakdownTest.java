package com.florin.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.tax.SupplyType;
import com.florin.tax.TaxRegime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaxBreakdownTest {

    private static Document gst(SupplyType supply, boolean reverseCharge) {
        Document document = new Document();
        document.setDocumentType(DocumentType.INVOICE);
        document.setTaxRegime(TaxRegime.GST_INDIA);
        document.setSupplyType(supply);
        document.setReverseCharge(reverseCharge);
        document.getBillingItems().add(line("101", "5"));
        document.getBillingItems().add(line("1000", "18"));
        return document;
    }

    private static BillingItem line(String price, String rate) {
        BillingItem item = new BillingItem();
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(BigDecimal.ONE);
        item.setDiscount(BigDecimal.ZERO);
        item.setTaxRate(new BigDecimal(rate));
        return item;
    }

    private static TaxSummary at(Document document, int index) {
        return document.getTaxSummaries().get(index);
    }

    @Test
    void intraState() {
        Document document = gst(SupplyType.INTRA, false);
        DocumentTotals.apply(document);

        assertThat(at(document, 0).getCgst()).isEqualByComparingTo("2.53");
        assertThat(at(document, 0).getSgst()).isEqualByComparingTo("2.53");
        assertThat(at(document, 0).getTax()).isEqualByComparingTo("5.06");
        assertThat(at(document, 0).getIgst()).isEqualByComparingTo("0");
        assertThat(at(document, 1).getCgst()).isEqualByComparingTo("90.00");
        assertThat(document.getSalesTax()).isEqualByComparingTo("185.06");
        assertThat(document.getTotal()).isEqualByComparingTo("1286.06");
    }

    @Test
    void interState() {
        for (SupplyType supply : new SupplyType[] {SupplyType.INTER, SupplyType.EXPORT_IGST}) {
            Document document = gst(supply, false);
            DocumentTotals.apply(document);

            assertThat(at(document, 0).getIgst()).isEqualByComparingTo("5.05");
            assertThat(at(document, 0).getCgst()).isEqualByComparingTo("0");
            assertThat(document.getTotal()).isEqualByComparingTo("1286.05");
        }
    }

    @Test
    void reverseCharge() {
        Document document = gst(SupplyType.INTER, true);
        DocumentTotals.apply(document);

        assertThat(at(document, 1).getIgst()).isEqualByComparingTo("180.00");
        assertThat(document.getSalesTax()).isEqualByComparingTo("0");
        assertThat(document.getTotal()).isEqualByComparingTo("1101.00");
    }

    @Test
    void underLut() {
        Document document = gst(SupplyType.EXPORT_LUT, false);
        GstSupply.zeroRatedExport(document);
        DocumentTotals.apply(document);

        assertThat(document.getBillingItems()).allSatisfy(item -> assertThat(item.getTaxRate()).isZero());
        assertThat(document.getSalesTax()).isEqualByComparingTo("0");
    }

    @Test
    void lutDraft() {
        Document document = gst(SupplyType.EXPORT_LUT, false);
        DocumentTotals.apply(document);

        assertThat(document.getSalesTax()).isEqualByComparingTo("0");
        assertThat(document.getTaxSummaries()).hasSize(1);
        assertThat(at(document, 0).getRate()).isEqualByComparingTo("0");
        assertThat(at(document, 0).getTaxable()).isEqualByComparingTo("1101.00");
        assertThat(document.getBillingItems().get(0).getTaxRate()).isEqualByComparingTo("5");
        assertThat(document.getBillingItems().get(1).getTaxRate()).isEqualByComparingTo("18");
    }

    @Test
    void standardDocumentNotSplit() {
        Document document = gst(SupplyType.INTRA, false);
        document.setTaxRegime(TaxRegime.STANDARD);
        DocumentTotals.apply(document);

        assertThat(at(document, 0).getTax()).isEqualByComparingTo("5.05");
        assertThat(at(document, 0).getCgst()).isEqualByComparingTo("0");
    }
}
