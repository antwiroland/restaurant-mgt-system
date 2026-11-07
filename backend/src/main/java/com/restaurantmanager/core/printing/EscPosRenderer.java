package com.restaurantmanager.core.printing;

import com.restaurantmanager.core.billing.BillingService;
import com.restaurantmanager.core.order.dto.OrderItemModifierResponse;
import com.restaurantmanager.core.order.dto.OrderItemResponse;
import com.restaurantmanager.core.order.dto.OrderResponse;
import com.restaurantmanager.core.payment.dto.ReceiptResponse;
import com.restaurantmanager.core.profile.RestaurantProfileService;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders customer receipts and kitchen tickets as ESC/POS byte streams for 58 mm (32 columns)
 * and 80 mm (48 columns) thermal printers. Text is folded to ASCII so it prints on any code page.
 */
public final class EscPosRenderer {
    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final int columns;
    private final ZoneId zone;

    private EscPosRenderer(int paperWidthMm, ZoneId zone) {
        this.columns = paperWidthMm <= 58 ? 32 : 48;
        this.zone = zone;
        raw(ESC, '@'); // initialise
    }

    public static byte[] receipt(OrderResponse order, ReceiptResponse receipt, RestaurantProfileService.ProfileView profile,
                                 int paperWidthMm, ZoneId zone) {
        EscPosRenderer r = new EscPosRenderer(paperWidthMm, zone);
        String currency = profile.currency() == null ? "GHS" : profile.currency();
        r.align(1).bold(true).size(true).line(profile.name()).size(false).bold(false);
        if (profile.address() != null) r.line(profile.address());
        if (profile.phone() != null) r.line("Tel " + profile.phone());
        r.align(0).rule();
        r.pair(receipt != null ? "Receipt " + receipt.receiptNumber() : "Bill (not paid)", "");
        r.pair("Order #" + shortId(order), r.time(order.createdAt()));
        r.line(where(order));
        r.rule();
        for (OrderItemResponse item : order.items()) {
            BigDecimal line = item.price().multiply(BigDecimal.valueOf(item.quantity()));
            r.pair(item.quantity() + " x " + item.name(), money(line));
            for (OrderItemModifierResponse modifier : item.modifiers()) {
                r.line("   + " + modifier.optionName());
            }
        }
        r.rule();
        r.pair("Subtotal", money(order.subtotal()));
        if (positive(order.discountTotal())) r.pair("Discounts", "-" + money(order.discountTotal()));
        if (positive(order.deliveryFee())) r.pair("Delivery", money(order.deliveryFee()));
        if (positive(order.serviceCharge())) r.pair("Service charge", money(order.serviceCharge()));
        if (order.taxLines() != null) {
            for (BillingService.TaxLine tax : order.taxLines()) {
                r.pair(tax.name() + (order.taxInclusive() ? " (incl.)" : ""), money(tax.amount()));
            }
        }
        if (positive(order.tipAmount())) r.pair("Tip", money(order.tipAmount()));
        r.bold(true).pair("TOTAL " + currency, money(order.total())).bold(false);
        if (receipt != null) {
            r.pair("Paid (" + receipt.paymentMethod().name().replace('_', ' ').toLowerCase(Locale.ROOT) + ")", money(receipt.amountPaid()));
            if (receipt.paidAt() != null) r.pair("Paid at", r.time(receipt.paidAt()));
            if (positive(receipt.refundedAmount())) r.pair("Refunded", money(receipt.refundedAmount()));
        }
        r.rule().align(1);
        r.line(profile.receiptFooter() != null ? profile.receiptFooter() : "Thank you!");
        r.feed(4).cut();
        return r.out.toByteArray();
    }

    public static byte[] kitchenTicket(OrderResponse order, int paperWidthMm, ZoneId zone) {
        EscPosRenderer r = new EscPosRenderer(paperWidthMm, zone);
        r.align(1).bold(true).size(true).line(where(order).toUpperCase(Locale.ROOT)).size(false).bold(false);
        r.line("#" + shortId(order) + "  " + r.time(order.createdAt()));
        r.align(0).rule();
        for (OrderItemResponse item : order.items()) {
            r.bold(true).size(true).line(item.quantity() + " x " + item.name()).size(false).bold(false);
            for (OrderItemModifierResponse modifier : item.modifiers()) {
                r.line("   " + modifier.groupName() + ": " + modifier.optionName());
            }
            if (item.notes() != null && !item.notes().isBlank()) {
                r.bold(true).line("   ! " + item.notes()).bold(false);
            }
        }
        if (order.notes() != null && !order.notes().isBlank()) {
            r.rule().bold(true).line("NOTE: " + order.notes()).bold(false);
        }
        r.feed(4).cut();
        return r.out.toByteArray();
    }

    public static byte[] testPage(String printerName, int paperWidthMm) {
        EscPosRenderer r = new EscPosRenderer(paperWidthMm, ZoneId.systemDefault());
        r.align(1).bold(true).size(true).line("Printer test").size(false).bold(false);
        r.line(printerName).line(paperWidthMm + " mm paper").rule();
        r.align(0).pair("Left", "Right").pair("1234567890", "GHS 0.00");
        r.feed(4).cut();
        return r.out.toByteArray();
    }

    /* ---------------------------------------------------------------- primitives */

    private static String where(OrderResponse order) {
        return switch (order.type()) {
            case DINE_IN -> "Table " + (order.tableNumber() == null ? "-" : order.tableNumber());
            case PICKUP -> "Pickup" + (order.pickupCode() == null ? "" : " " + order.pickupCode());
            case DELIVERY -> "Delivery";
        };
    }

    private static String shortId(OrderResponse order) {
        return order.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String time(java.time.Instant instant) {
        return instant == null ? "" : TIME.format(instant.atZone(zone));
    }

    private EscPosRenderer raw(int... bytes) {
        for (int b : bytes) out.write(b);
        return this;
    }

    private EscPosRenderer align(int mode) {
        return raw(ESC, 'a', mode);
    }

    private EscPosRenderer bold(boolean on) {
        return raw(ESC, 'E', on ? 1 : 0);
    }

    private EscPosRenderer size(boolean doubled) {
        return raw(GS, '!', doubled ? 0x11 : 0x00);
    }

    private EscPosRenderer feed(int lines) {
        return raw(ESC, 'd', lines);
    }

    private EscPosRenderer cut() {
        return raw(GS, 'V', 1);
    }

    private EscPosRenderer line(String text) {
        for (String chunk : wrap(ascii(text), columns)) {
            out.writeBytes(chunk.getBytes(StandardCharsets.US_ASCII));
            out.write('\n');
        }
        return this;
    }

    private EscPosRenderer rule() {
        return line("-".repeat(columns));
    }

    /** Left text and right-aligned value on one line (left side wraps if needed). */
    private EscPosRenderer pair(String left, String right) {
        String l = ascii(left);
        String rt = ascii(right);
        int room = columns - rt.length() - 1;
        if (room < 8) {
            line(l);
            return line(" ".repeat(Math.max(0, columns - rt.length())) + rt);
        }
        List<String> parts = wrap(l, room);
        for (int i = 0; i < parts.size() - 1; i++) line(parts.get(i));
        String last = parts.isEmpty() ? "" : parts.get(parts.size() - 1);
        return line(last + " ".repeat(Math.max(1, columns - last.length() - rt.length())) + rt);
    }

    static String ascii(String text) {
        if (text == null) return "";
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        folded = folded.replace('×', 'x').replace('–', '-').replace('—', '-')
                .replace('’', '\'').replace('“', '"').replace('”', '"').replace("₵", "GHS");
        return folded.replaceAll("[^\\x20-\\x7E]", "?");
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new java.util.ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > width) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (current.length() + word.length() + (current.isEmpty() ? 0 : 1) > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
}
