package com.florin.demo;

import com.florin.document.PaymentMethod;
import com.florin.document.RecurringFrequency;
import java.math.BigDecimal;
import java.util.List;

final class DemoData {

    record Item(String name, String description, BigDecimal price, boolean countable, String sac) {}

    record Client(
            String name, String email, String phone, String address, String city, String gstin, String stateCode) {}

    record Seller(
            String name,
            String email,
            String phone,
            String address,
            String city,
            String state,
            String postalCode,
            String gstin) {}

    record SampleExpense(
            int daysAgo,
            String vendor,
            String category,
            BigDecimal amount,
            BigDecimal tax,
            boolean taxReclaimable,
            PaymentMethod method) {}

    record SampleSchedule(String item, int quantity, String client, RecurringFrequency frequency, int daysAhead) {}

    static final Seller SELLER = new Seller(
            "Apte Design Studio", "hello@aptedesign.example", "+91 20 1457 3186", "Office 9, Paud Road, Kothrud",
            "Pune", "Maharashtra", "411038", "27AFQRA4826M1ZY");

    static final String ACCOUNT_NUMBER = "041120005317";
    static final String IFSC = "EXMP0000411";
    static final String UPI_ID = "aptedesign@example";

    static final List<Item> ITEMS = List.of(
            item("Brand strategy workshop", "Half-day session with the leadership team", "25000", false, "998311"),
            item("Logo and identity system", "Logo, colour, type and usage guide", "45000", false, "998391"),
            item("Website design (per page)", "Responsive layouts for one page", "4000", true, "998314"),
            item("Front-end build (per page)", "Accessible, production-ready code", "3500", true, "998314"),
            item("Copywriting (per 500 words)", null, "1500", true, "998361"),
            item("Product photography (half day)", "Studio, lighting and retouching", "12000", false, "998382"),
            item("Motion graphics (per 15 seconds)", null, "6000", true, "999613"),
            item("Social media kit", "Templates for posts, stories and covers", "10000", false, "998391"),
            item("Hosting and maintenance (monthly)", "Updates, backups and uptime checks", "1500", true, "998315"),
            item("Consulting (hourly)", null, "2000", true, "998311"));

    static final List<Client> CLIENTS = List.of(
            new Client("Kale & Deshpande Architects", "accounts@kaledeshpande.example", "+91 20 1462 3307",
                    "12 Prabhat Road, Erandwane", "Pune", "27AAXRK5207D1ZH", null),
            new Client("Shinde Constructions", "office@shindeconstructions.example", "+91 20 1475 9021",
                    "Office 3, Survey No. 41, Baner", "Pune", "27BQWNS3816H1Z9", null),
            new Client("Mehta Bakers", "orders@mehtabakers.example", "020 1438 6610",
                    "Shop 7, Sadashiv Peth", "Pune", null, "27"),
            new Client("Bhide Nursing Home", "billing@bhidenursinghome.example", "+91 253 145 2280",
                    "Plot 18, College Road", "Nashik", "27AFEMB6094L1ZD", null),
            new Client("Jadhav Roadlines", "accounts@jadhavroadlines.example", "+91 2522 148 336",
                    "Gala 9, Mankoli Naka", "Bhiwandi", "27CKPUJ1478Q2ZW", null),
            new Client("Pai Electricals", "sales@paielectricals.example", "+91 80 1473 0582",
                    "41 SJP Road", "Bengaluru", "29AJTSP9352E1ZB", null),
            new Client("Iyer Dance Academy", "classes@iyerdance.example", "022 1456 7713",
                    "2nd floor, Bhandarkar Road, Matunga", "Mumbai", null, "27"),
            new Client("Casa Fernandes Homestay", "stay@casafernandes.example", "+91 832 126 4408",
                    "H. No. 112, Badem", "Assagao", "30DHLWF2763K1ZY", null),
            new Client("Gokhale Dental Clinic", "clinic@gokhaledental.example", "+91 20 1449 2261",
                    "Shop 4, Karve Road, Kothrud", "Pune", null, "27"),
            new Client("Deshmukh & Associates", "office@deshmukhassociates.example", "+91 22 1427 9054",
                    "Office 305, 14 Bank Street, Fort", "Mumbai", "27AAGYD4410R1Z3", null),
            new Client("Gadre Mess", "orders@gadremess.example", "020 1483 5596",
                    "1184 Narayan Peth", "Pune", null, "27"),
            new Client("Nisarg Organic Farms", "farm@nisargorganic.example", "+91 253 139 6627",
                    "Gat No. 212, Dindori Road", "Nashik", "27EVBNN7125C1ZU", null));

    private static final String SOFTWARE = "Software and subscriptions";

    static final List<SampleExpense> EXPENSES = List.of(
            expense(3, "Jain Stationery Mart", "Office", "860.00", "0", false, PaymentMethod.CASH),
            expense(9, "Kumar Infotech", SOFTWARE, "18600.00", "3348.00", true, PaymentMethod.CREDIT_CARD),
            expense(15, "Hotel Mayur Residency", "Travel", "4800.00", "240.00", false, PaymentMethod.CREDIT_CARD),
            expense(16, "Patel Tours & Travels", "Travel", "6400.00", "320.00", false, PaymentMethod.UPI),
            expense(24, "Rohit Kulkarni", "Contractors", "30000.00", "0", false, PaymentMethod.BANK_TRANSFER),
            expense(38, "Bhosale Printers", "Marketing", "2750.00", "0", false, PaymentMethod.UPI),
            expense(52, "Kumar Infotech", SOFTWARE, "4500.00", "810.00", true, PaymentMethod.CREDIT_CARD),
            expense(67, "Sneha Pillai", "Contractors", "15000.00", "0", false, PaymentMethod.BANK_TRANSFER),
            expense(81, "Sathe Business Centre", "Office", "3500.00", "630.00", true, PaymentMethod.BANK_TRANSFER),
            expense(121, "Joshi Advertising", "Marketing", "8000.00", "1440.00", true, PaymentMethod.BANK_TRANSFER),
            expense(148, "Prakash Web Hosting", SOFTWARE, "6000.00", "1080.00", true, PaymentMethod.CREDIT_CARD),
            expense(184, "Pune City Cabs", "Travel", "640.00", "0", false, PaymentMethod.UPI),
            expense(205, "Vijay Computers", SOFTWARE, "3200.00", "576.00", true, PaymentMethod.UPI),
            expense(310, "Gupta Furniture House", "Office", "14500.00", "2610.00", true, PaymentMethod.CREDIT_CARD));

    static final List<String> EXPENSE_VENDORS = EXPENSES.stream().map(SampleExpense::vendor).toList();

    static final List<SampleSchedule> SCHEDULES = List.of(
            new SampleSchedule(
                    "Hosting and maintenance (monthly)", 1, "Mehta Bakers", RecurringFrequency.MONTHLY, 5),
            new SampleSchedule("Consulting (hourly)", 10, "Shinde Constructions", RecurringFrequency.MONTHLY, 12),
            new SampleSchedule("Brand strategy workshop", 1, "Bhide Nursing Home", RecurringFrequency.QUARTERLY, 26));

    static final String NOTES = "Thank you for working with us. It's a pleasure to be part of your project.";
    static final String TERMS =
            "Payment is due within 14 days of the issue date. Please pay by UPI or bank transfer.";
    static final String EMAIL_MESSAGE = "Hi {client},\n\nPlease find {document} attached. Let me know if you have any"
            + " questions.\n\nThanks,\n{business}";

    private static Item item(String name, String description, String price, boolean countable, String sac) {
        return new Item(name, description, new BigDecimal(price), countable, sac);
    }

    private static SampleExpense expense(
            int daysAgo,
            String vendor,
            String category,
            String amount,
            String tax,
            boolean taxReclaimable,
            PaymentMethod method) {
        return new SampleExpense(
                daysAgo, vendor, category, new BigDecimal(amount), new BigDecimal(tax), taxReclaimable, method);
    }

    private DemoData() {}
}
