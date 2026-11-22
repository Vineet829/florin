package com.florin.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UpiPaymentTest {

    @Test
    void buildsUpiPaymentLink() {
        UpiPayment upi = new UpiPayment("apte.co@okhdfcbank", "Apte & Co", new BigDecimal("1500"), "Invoice INV-0042");

        assertThat(upi.uri()).isEqualTo(
                "upi://pay?pa=apte.co@okhdfcbank&pn=Apte%20%26%20Co&am=1500.00&cu=INR&tn=Invoice%20INV-0042");
        assertThat(upi.amountIncluded()).isTrue();
    }

    @Test
    void encodesAndTrimsNames() {
        assertThat(new UpiPayment("x@upi", "Zoë Café", new BigDecimal("10.5"), "Invoice INV/7#1").uri())
                .isEqualTo("upi://pay?pa=x@upi&pn=Zo%C3%AB%20Caf%C3%A9&am=10.50&cu=INR&tn=Invoice%20INV%2F7%231");
        String name = "A".repeat(49) + "éB";
        assertThat(new UpiPayment("x@upi", name, BigDecimal.ONE, null).uri())
                .isEqualTo("upi://pay?pa=x@upi&pn=" + "A".repeat(49) + "%C3%A9&am=1.00&cu=INR");
    }

    @Test
    void omitsAmountOverLakh() {
        UpiPayment atTheLimit = new UpiPayment("x@upi", "Apte", new BigDecimal("100000.00"), "Invoice INV-1");
        UpiPayment over = new UpiPayment("x@upi", "Apte", new BigDecimal("100000.01"), "Invoice INV-1");

        assertThat(atTheLimit.amountIncluded()).isTrue();
        assertThat(atTheLimit.uri()).contains("&am=100000.00&");
        assertThat(over.amountIncluded()).isFalse();
        assertThat(over.uri()).isEqualTo("upi://pay?pa=x@upi&pn=Apte&cu=INR&tn=Invoice%20INV-1");
    }
}
