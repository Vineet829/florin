package com.florin.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.business.Business;
import com.florin.config.AppProperties;
import com.florin.customer.Customer;
import com.florin.settings.BusinessSettings;
import com.florin.settings.PdfTemplate;
import com.florin.tax.SupplyType;
import com.florin.tax.TaxRegime;
import com.florin.upload.TestImages;
import com.florin.upload.UploadService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class PdfServiceTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    private static final BusinessSettings SETTINGS = new BusinessSettings();

    @TempDir
    Path uploads;

    private PdfService service;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("a", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Cookie("jwt", "/", "None", true),
                new AppProperties.Site("Florin", "http://localhost:3000", "http://localhost:8080", "from@florin.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload(uploads.toString()));
        service = new PdfService(engine, new UploadService(properties));
    }

    private static Business seller(String name) {
        Business business = new Business();
        business.setName(name);
        business.setEmail("apte@x.com");
        business.setCity("Pune");
        business.setCountry("India");
        return business;
    }

    private static BillingItem item(String name, String price, int quantity, String discount) {
        BillingItem item = new BillingItem();
        item.setItemName(name);
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(BigDecimal.valueOf(quantity));
        item.setTaxRate(new BigDecimal("18"));
        item.setDiscount(new BigDecimal(discount));
        return item;
    }

    private static Document sampleDocument(DocumentType type) {
        Document document = new Document();
        document.setDocumentType(type);
        document.setStatus(DocumentStatus.NOT_FULLY_PAID);
        CustomerSnapshot customer = new CustomerSnapshot();
        customer.setName("Beta LLC");
        customer.setEmail("beta@x.com");
        customer.setAccountNo("CUS-123456");
        customer.setVatTinNo("0");
        document.setCustomerSnapshot(customer);
        document.setDocumentNumber("2026-September-ABC123");
        document.setCreatedAt(Instant.parse("2026-09-30T06:00:00Z"));
        document.setIssuedAt(Instant.parse("2026-09-30T06:00:00Z"));
        document.setIssueDate(LocalDate.of(2026, 9, 30));
        document.setDueDate(Instant.parse("2026-10-15T06:30:00Z"));
        document.setCurrency("USD");
        document.getTaxSummaries().add(
                new TaxSummary(new BigDecimal("18.0000"), new BigDecimal("1225.00"), new BigDecimal("220.50")));
        document.setSubTotal(new BigDecimal("1225.00"));
        document.setSalesTax(new BigDecimal("220.50"));
        document.setTotal(new BigDecimal("1445.50"));
        document.setTotalAmountReceived(new BigDecimal("445.50"));
        document.setAdditionalInfo("Thanks for your business.");
        document.getBillingItems().add(item("Design sprint", "1000", 1, "0"));
        document.getBillingItems().add(item("Prototype & <review>", "125", 2, "10"));
        Payment payment = new Payment();
        payment.setAmountPaid(new BigDecimal("445.50"));
        document.getPayments().add(payment);
        return document;
    }

    @Test
    void invoiceHtml() {
        String html = service.html(sampleDocument(DocumentType.INVOICE), seller("Apte Analytics"), SETTINGS);

        assertThat(html)
                .contains("Prototype &amp; &lt;review&gt;")
                .contains("225.00")
                .contains("Tax (18%)")
                .contains("USD 1,445.50")
                .contains("Amount paid")
                .contains("Balance due")
                .contains("USD 1,000.00")
                .contains("Partially paid")
                .contains("Sep 30, 2026")
                .contains("Oct 15, 2026")
                .contains("Account no. CUS-123456")
                .contains("Pune, India")
                .contains("Thanks for your business.")
                .doesNotContain("VAT / TIN");
    }

    @Test
    void unionTerritory() {
        Document document = sampleDocument(DocumentType.INVOICE);
        document.getBillingItems().forEach(item -> item.setTaxRate(new BigDecimal("18")));
        document.setTaxRegime(TaxRegime.GST_INDIA);
        document.setSellerStateCode("04");
        document.setPlaceOfSupply("04");
        document.setSupplyType(SupplyType.INTRA);
        DocumentTotals.apply(document);

        String html = service.html(document, seller("Apte Analytics"), SETTINGS);

        assertThat(html).contains(
                "UTGST 9%",
                "<th class=\"num\">UTGST</th>",
                "Place of supply: 04-Chandigarh",
                "State: Chandigarh (04)",
                "Tax Invoice");
    }

    @Test
    void quotationHtml() {
        String html = service.html(sampleDocument(DocumentType.QUOTATION), seller("Apte Analytics"), SETTINGS);

        assertThat(html).contains("Valid until").contains("USD 1,445.50");
        assertThat(html).doesNotContain("Amount paid").doesNotContain("Balance due").doesNotContain("Partially paid");
    }

    @Test
    void receiptHtml() {
        Document document = sampleDocument(DocumentType.INVOICE);
        Payment payment = document.getPayments().get(0);
        payment.setDatePaid(LocalDate.of(2026, 9, 12));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setAdditionalInfo("Deposit & <thanks>");

        String html = service.receiptHtml(
                document,
                payment,
                "2026-September-ABC123-P1",
                new BigDecimal("445.50"),
                seller("Apte Analytics"),
                PdfTemplate.CLASSIC);

        assertThat(html)
                .contains("Payment receipt 2026-September-ABC123-P1")
                .contains("Sep 12, 2026")
                .contains("Invoice 2026-September-ABC123")
                .contains("USD 445.50")
                .contains("Paid by Bank Transfer")
                .contains("Deposit &amp; &lt;thanks&gt;")
                .contains("USD 1,445.50")
                .contains("Balance after this payment")
                .contains("USD 1,000.00");
    }

    private static Customer client() {
        Customer customer = new Customer();
        customer.setName("Beta & <Sons>");
        customer.setEmail("beta@x.com");
        customer.setAccountNo("CUS-123456");
        customer.setCity("Mumbai");
        return customer;
    }

    private static Statements.Account account(String currency, String overdue) {
        return new Statements.Account(
                currency,
                List.of(
                        new Statements.Line(LocalDate.of(2026, 8, 1), "Invoice INV-1", new BigDecimal("1200.00"),
                                null, new BigDecimal("1200.00")),
                        new Statements.Line(LocalDate.of(2026, 9, 2), "Payment for INV-1 (Cash)", null,
                                new BigDecimal("200.00"), new BigDecimal("1000.00"))),
                new BigDecimal("1200.00"),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                new BigDecimal(overdue));
    }

    @Test
    void statementHtml() {
        String html = service.statementHtml(
                seller("Apte Analytics"),
                client(),
                LocalDate.of(2026, 9, 30),
                List.of(account("USD", "1000.00")),
                PdfTemplate.CLASSIC);

        assertThat(html)
                .contains("Statement for Beta &amp; &lt;Sons&gt;")
                .contains("Account no. CUS-123456")
                .contains("Sep 30, 2026")
                .contains("Aug 1, 2026")
                .contains("Payment for INV-1 (Cash)")
                .contains("1,200.00")
                .contains("USD 200.00")
                .contains("Overdue")
                .contains("USD 1,000.00")
                .contains("Amount due");
    }

    @Test
    void statementCurrencies() {
        String settled = service.statementHtml(
                seller("Apte Analytics"),
                client(),
                LocalDate.of(2026, 9, 30),
                List.of(account("USD", "0")),
                PdfTemplate.CLASSIC);
        String mixed = service.statementHtml(
                seller("Apte Analytics"),
                client(),
                LocalDate.of(2026, 9, 30),
                List.of(account("USD", "0"), account("EUR", "0")),
                PdfTemplate.CLASSIC);
        String empty = service.statementHtml(
                seller("Apte Analytics"), client(), LocalDate.of(2026, 9, 30), List.of(), PdfTemplate.CLASSIC);

        assertThat(settled).doesNotContain("Overdue");
        assertThat(mixed).contains("<div class=\"label\">EUR</div>");
        assertThat(empty).contains("No invoices yet.");
    }

    @Test
    void embedsOnlyLocalLogos() throws Exception {
        Files.write(uploads.resolve("brand.png"), PNG);
        Files.write(uploads.resolve("brand.webp"), PNG);
        Business local = seller("Apte Analytics");
        local.setLogo("/uploads/brand.png");
        Business remote = seller("Apte Analytics");
        remote.setLogo("https://example.com/brand.png");
        Business webp = seller("Apte Analytics");
        webp.setLogo("/uploads/brand.webp");

        assertThat(service.html(sampleDocument(DocumentType.INVOICE), local, SETTINGS)).contains("data:image/png;base64,");
        assertThat(service.html(sampleDocument(DocumentType.INVOICE), remote, SETTINGS)).doesNotContain("example.com");
        assertThat(service.html(sampleDocument(DocumentType.INVOICE), webp, SETTINGS)).doesNotContain("class=\"logo\"");
        assertThat(new String(service.render(sampleDocument(DocumentType.INVOICE), local, SETTINGS), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void controlCharacters() throws Exception {
        String verticalTab = Character.toString(0x0B);
        String nul = Character.toString(0x00);
        Document document = sampleDocument(DocumentType.INVOICE);
        document.getCustomerSnapshot().setName("Beta" + verticalTab + "LLC" + nul);
        document.setAdditionalInfo("Thank you" + verticalTab + "for your business");

        String text = PdfText.of(service.render(document, seller("Apte Analytics"), SETTINGS));

        assertThat(text).contains("Beta LLC").contains("Thank you for your business");
        assertThat(PdfService.printable("a" + verticalTab + "b" + nul + "c")).isEqualTo("a bc");
    }

    @Test
    void largeLogo() throws Exception {
        Files.write(uploads.resolve("huge.png"), TestImages.pngHeader(12000, 12000));
        Business business = seller("Apte Analytics");
        business.setLogo("/uploads/huge.png");

        assertThat(service.html(sampleDocument(DocumentType.INVOICE), business, SETTINGS)).doesNotContain("class=\"logo\"");
        assertThat(new String(service.render(sampleDocument(DocumentType.INVOICE), business, SETTINGS), 0, 5))
                .isEqualTo("%PDF-");
    }

    @Test
    void senderShowsBusinessDetails() {
        Business business = seller("Apte Analytics");
        business.setLegalName("Apte Analytics Private Limited");
        business.setState("Maharashtra");
        business.setPostalCode("411001");
        business.setTaxId("27AAPFU0939F1ZV");

        assertThat(PdfService.senderName(business)).isEqualTo("Apte Analytics");
        assertThat(PdfService.senderLines(business)).containsExactly(
                "Apte Analytics Private Limited", "apte@x.com", "Pune, Maharashtra 411001, India", "Tax ID 27AAPFU0939F1ZV");

        business.setLegalName("apte analytics");
        assertThat(PdfService.senderLines(business)).doesNotContain("apte analytics");
    }

    @Test
    void settingsApply() {
        BusinessSettings settings = new BusinessSettings();
        settings.setTaxLabel("VAT");
        settings.setTimeZone("Asia/Kolkata");
        Document document = sampleDocument(DocumentType.INVOICE);
        document.setTaxLabel("GST");
        document.setDueDate(Instant.parse("2026-10-15T20:00:00Z"));

        String html = service.html(document, seller("Apte Analytics"), settings);

        assertThat(html).contains("GST (18%)").contains("Oct 16, 2026").doesNotContain("VAT (18%)");
    }

    @Test
    void keptSeller() {
        Document document = sampleDocument(DocumentType.INVOICE);
        document.setSeller(SellerSnapshot.of(seller("Apte Analytics")));
        Payment payment = document.getPayments().get(0);
        payment.setDatePaid(LocalDate.of(2026, 9, 12));
        payment.setPaymentMethod(PaymentMethod.CASH);

        assertThat(service.html(document, seller("Borealis Studio"), SETTINGS))
                .contains("Apte Analytics")
                .doesNotContain("Borealis Studio");
        assertThat(service.receiptHtml(
                        document, payment, "INV-1-P1", BigDecimal.TEN, seller("Borealis Studio"), PdfTemplate.CLASSIC))
                .contains("Apte Analytics")
                .doesNotContain("Borealis Studio");
    }

    @Test
    void issueDate() {
        Document document = sampleDocument(DocumentType.INVOICE);
        document.setIssueDate(LocalDate.of(2026, 9, 1));

        assertThat(service.html(document, seller("Apte Analytics"), SETTINGS)).contains("Sep 1, 2026");
    }
}
