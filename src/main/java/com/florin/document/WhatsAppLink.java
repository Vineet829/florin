package com.florin.document;

import com.florin.business.Business;
import com.florin.tax.TaxRegime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class WhatsAppLink {

    private WhatsAppLink() {}

    static String of(String phone, boolean indian, String text) {
        String number = number(phone, indian);
        String message = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        return number == null ? "https://wa.me/?text=" + message : "https://wa.me/" + number + "?text=" + message;
    }

    static String number(String phone, boolean indian) {
        if (phone == null) {
            return null;
        }
        String written = phone.trim();
        String digits = written.replaceAll("\\D", "");
        if (written.startsWith("+")) {
            return international(digits);
        }
        if (digits.startsWith("00")) {
            return international(digits.substring(2));
        }
        return indian && digits.length() == 10 ? "91" + digits : null;
    }

    static boolean inIndia(Business business, TaxRegime regime) {
        return "India".equalsIgnoreCase(Objects.requireNonNullElse(business.getCountry(), "").trim())
                || regime == TaxRegime.GST_INDIA;
    }

    private static String international(String digits) {
        return digits.length() >= 8 && digits.length() <= 15 ? digits : null;
    }
}
