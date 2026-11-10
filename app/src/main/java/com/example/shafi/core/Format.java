package com.example.shafi.core;

import java.util.Calendar;
import java.util.Locale;

public final class Format {

    private Format() {
    }

    public static String money(long minor, String currency) {
        double major = minor / 100.0;
        String amount = String.format(Locale.US, "%,.0f", major);
        if (currency == null || currency.isEmpty() || "INR".equals(currency)) {
            return "₹" + amount;
        }
        return currency + " " + amount;
    }

    public static String payUnit(String unit) {
        if ("PER_HOUR".equals(unit)) return "/ hour";
        if ("PER_DAY".equals(unit)) return "/ day";
        return "/ shift";
    }

    public static String shift(String date, String start, String end) {
        String when = date == null ? "" : date;
        if (start != null && end != null) {
            when = when + " · " + start + " – " + end;
        }
        return when.trim();
    }

    public static String status(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.toLowerCase(Locale.US).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    public static String block(String label, String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return label + "\n" + value.trim() + "\n\n";
    }

    public static String bullets(String label, java.util.List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(label).append('\n');
        for (String value : values) builder.append("• ").append(value).append('\n');
        return builder.append('\n').toString();
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "HS";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.US);
        return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.US);
    }
}
