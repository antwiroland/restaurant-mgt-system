package com.restaurantmanager.core.notification;

public final class PhoneNumbers {
    private PhoneNumbers() {
    }

    /**
     * Normalises a phone number to international digits without "+", e.g.
     * "024 412 3456" -> "233244123456", "+233244123456" -> "233244123456".
     */
    public static String toInternational(String phone, String defaultCountryCode) {
        if (phone == null) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        if (digits.startsWith("00")) {
            return digits.substring(2);
        }
        if (digits.startsWith("0")) {
            return defaultCountryCode + digits.substring(1);
        }
        return digits;
    }
}
