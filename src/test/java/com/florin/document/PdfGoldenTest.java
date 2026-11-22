package com.florin.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.business.Business;
import com.florin.settings.BusinessSettings;
import com.florin.settings.PdfTemplate;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfGoldenTest {

    private static final Path GOLDEN = Path.of("src/test/resources/pdf-golden");
    private static final Pattern STYLE = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);
    private static final Pattern CSS_BREAK = Pattern.compile("\\s*([{};])\\s*");

    @TempDir
    Path uploads;

    private PdfService service;
    private Business seller;

    @BeforeEach
    void setUp() throws IOException {
        service = PdfFixtures.service(uploads);
        seller = PdfFixtures.seller(uploads);
    }

    private Document kept(Document document) {
        document.setSeller(SellerSnapshot.of(seller));
        return document;
    }

    static String normalized(String html) {
        String flat = html.replaceAll(">\\s+<", "><").replaceAll("\\s+", " ").trim();
        String styled = STYLE.matcher(flat).replaceAll(style -> Matcher.quoteReplacement("<style>\n"
                + CSS_BREAK.matcher(style.group(1).trim())
                        .replaceAll(mark -> mark.group(1).equals("{") ? " {\n" : mark.group(1) + "\n")
                + "</style>"));
        return styled.replace("><", ">\n<") + "\n";
    }

    private static void assertGolden(String name, String html) throws IOException {
        Path file = GOLDEN.resolve(name + ".html");
        String actual = normalized(html);
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN);
            Files.writeString(file, actual);
            return;
        }
        assertThat(file).as("%s is missing: run the test with -Dgolden.update=true", file).exists();
        assertThat(actual).as("%s changed: review it, then rewrite with -Dgolden.update=true", file)
                .isEqualTo(Files.readString(file));
    }

    @Test
    void partPaidInvoice() throws IOException {
        assertGolden("invoice", service.html(kept(PdfFixtures.invoice()), seller, PdfFixtures.settings()));
    }

    @Test
    void dollarQuotation() throws IOException {
        assertGolden("quotation", service.html(kept(PdfFixtures.quotation()), seller, PdfFixtures.settings()));
    }

    @Test
    void rupeeReceipt() throws IOException {
        assertGolden("receipt", service.html(kept(PdfFixtures.receipt()), seller, PdfFixtures.settings()));
    }

    @Test
    void twoRates() throws IOException {
        assertGolden("two-rate-invoice", service.html(kept(PdfFixtures.twoRates()), seller, PdfFixtures.settings()));
    }

    @Test
    void creditNote() throws IOException {
        assertGolden("credit-note", service.html(kept(PdfFixtures.creditNote()), seller, PdfFixtures.settings()));
    }

    @Test
    void creditedInvoice() throws IOException {
        assertGolden(
                "credited-invoice",
                service.html(kept(PdfFixtures.creditedInvoice()), seller, PdfFixtures.settings()));
    }

    @Test
    void gstInvoice() throws IOException {
        assertGolden("gst-invoice", service.html(kept(PdfFixtures.gstInvoice()), seller, PdfFixtures.settings()));
    }

    @Test
    void gstExport() throws IOException {
        assertGolden("gst-export", service.html(kept(PdfFixtures.gstExport()), seller, PdfFixtures.settings()));
    }

    @Test
    void gstExportKeepingRates() throws IOException {
        Document export = PdfFixtures.gstExport();
        export.getBillingItems().get(0).setTaxRate(new BigDecimal("18"));
        DocumentTotals.apply(export);
        assertGolden("gst-export", service.html(kept(export), seller, PdfFixtures.settings()));
    }

    @Test
    void draftInvoice() throws IOException {
        assertGolden("draft-invoice", service.html(PdfFixtures.draft(), seller, PdfFixtures.settings()));
    }

    @Test
    void voidInvoice() throws IOException {
        assertGolden("void-invoice", service.html(kept(PdfFixtures.voided()), seller, PdfFixtures.settings()));
    }

    @Test
    void paymentReceipt() throws IOException {
        Document invoice = kept(PdfFixtures.invoice());
        assertGolden(
                "payment-receipt",
                service.receiptHtml(
                        invoice,
                        invoice.getPayments().get(0),
                        "INV-0042-P1",
                        new BigDecimal("44550.00"),
                        seller,
                        PdfTemplate.CLASSIC));
    }

    @Test
    void twoCurrencyStatement() throws IOException {
        assertGolden(
                "statement",
                service.statementHtml(
                        seller,
                        PdfFixtures.customer(),
                        LocalDate.of(2026, 10, 3),
                        PdfFixtures.accounts(),
                        PdfTemplate.CLASSIC));
    }

    @Test
    void modernInvoice() throws IOException {
        assertGolden("modern-invoice", service.html(kept(PdfFixtures.invoice()), seller, in(PdfTemplate.MODERN)));
    }

    @Test
    void compactInvoice() throws IOException {
        assertGolden("compact-invoice", service.html(kept(PdfFixtures.invoice()), seller, in(PdfTemplate.COMPACT)));
    }

    private static BusinessSettings in(PdfTemplate template) {
        BusinessSettings settings = PdfFixtures.settings();
        settings.setPdfTemplate(template);
        return settings;
    }
}
