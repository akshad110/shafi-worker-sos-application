package com.example.shafi.core;

import com.example.shafi.data.Dtos;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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

    public static String shiftDay(Dtos.ShiftItem item) {
        if (item == null) return "";
        if (item.shiftDate != null && item.shiftDate.length() >= 10) return item.shiftDate.substring(0, 10);
        if (item.job != null && item.job.shiftDate != null && item.job.shiftDate.length() >= 10) {
            return item.job.shiftDate.substring(0, 10);
        }
        if (item.startAt != null && item.startAt.length() >= 10) return item.startAt.substring(0, 10);
        return "";
    }

    public static String prettyStamp(String iso) {
        if (iso == null || iso.length() < 19) return prettyDate(iso);
        try {
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(iso.substring(0, 19));
            if (date == null) return iso;
            return new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US).format(date);
        } catch (ParseException error) {
            return prettyDate(iso);
        }
    }

    public static String prettyDate(String iso) {
        Date date = parseDay(iso);
        if (date == null) return iso == null ? "" : iso;
        return new SimpleDateFormat("EEE, d MMM", Locale.US).format(date);
    }

    public static String weekday(String iso) {
        Date date = parseDay(iso);
        if (date == null) return "";
        return new SimpleDateFormat("EEE", Locale.US).format(date).toUpperCase(Locale.US);
    }

    public static int dayNumber(String iso) {
        Date date = parseDay(iso);
        if (date == null) return 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_MONTH);
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static String plusDays(String iso, int days) {
        Date date = parseDay(iso);
        if (date == null) date = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private static Date parseDay(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.substring(0, 10));
        } catch (ParseException error) {
            return null;
        }
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

    public static String clock() {
        return new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
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
