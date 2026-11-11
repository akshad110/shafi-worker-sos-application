package com.example.shafi.core;

import com.example.shafi.data.Dtos;

import java.util.List;
import java.util.Map;

public final class PayRules {
    public final boolean recorded;
    public final long amountMinor;
    public final String currency;
    public final String card;
    public final String detail;
    public final String mark;

    private PayRules(boolean recorded, long amountMinor, String currency, String card, String detail, String mark) {
        this.recorded = recorded;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.card = card;
        this.detail = detail;
        this.mark = mark;
    }

    public static PayRules of(Dtos.ShiftItem item) {
        String status = item.status == null ? "" : item.status;
        String mark = resolveMark(item, status);
        long full = item.compensationMinor > 0 ? item.compensationMinor : (item.amount > 0 ? Math.round(item.amount * 100) : (item.job == null ? 0 : item.job.compensationMinor));
        String currency = item.currency != null && !item.currency.isEmpty()
                ? item.currency
                : (item.job != null && item.job.currency != null && !item.job.currency.isEmpty() ? item.job.currency : "INR");
        boolean waiting = ("UPCOMING".equals(status) || "ACTIVE".equals(status)) && "UNMARKED".equals(mark);
        if (item.payment != null && "NOT_RECORDED".equals(item.payment.status)) waiting = true;
        if (!waiting && item.payment != null && item.payment.note != null && !item.payment.note.isEmpty()) {
            boolean recorded = item.payment.recorded || !"NOT_RECORDED".equals(item.payment.status);
            return build(recorded, item.payment.amountMinor, currency, mark, item.payment.note, status);
        }
        if (waiting) {
            return build(false, 0, currency, mark, "Payment is recorded after the hospital marks attendance.", status);
        }
        boolean hospitalStored = item.payment != null && item.payment.id != null && !item.payment.id.isEmpty();
        long stored = hospitalStored ? item.payment.amountMinor : -1;
        if ("ABSENT".equals(mark) || "CANCELLED".equals(status)) {
            long amount = hospitalStored ? stored : 0;
            return build(true, amount, currency, "ABSENT", "Absent, so no payment. " + Format.money(amount, currency) + ".", status);
        }
        if ("HALF".equals(mark)) {
            long amount = hospitalStored ? stored : Math.round(full / 2.0);
            return build(true, amount, currency, mark, storedNote("Half day, so half the shift pay is recorded.", hospitalStored), status);
        }
        if ("LATE".equals(mark)) {
            long amount = hospitalStored ? stored : Math.round(full * 0.75);
            return build(true, amount, currency, mark, storedNote("Late, so 75% of the shift pay is recorded.", hospitalStored), status);
        }
        if ("PRESENT".equals(mark)) {
            long amount = hospitalStored ? stored : full;
            return build(true, amount, currency, mark, storedNote("Present, so the full shift pay is recorded.", hospitalStored), status);
        }
        return build(false, 0, currency, mark, "This shift is complete. Attendance is not marked yet, so payment is not recorded.", status);
    }

    private static String storedNote(String base, boolean hospitalStored) {
        return hospitalStored ? base + " The hospital stored this amount." : base;
    }

    private static PayRules build(boolean recorded, long amountMinor, String currency, String mark, String note, String status) {
        String card = recorded ? "Payment recorded · " + Format.money(amountMinor, currency) : "Payment not recorded";
        String detail = "COMPLETED".equals(status) ? "Completed. " + note : note;
        return new PayRules(recorded, amountMinor, currency, card, detail, label(mark));
    }

    private static String resolveMark(Dtos.ShiftItem item, String status) {
        if (item.attendanceMark != null && !item.attendanceMark.isEmpty() && !"UNMARKED".equals(item.attendanceMark)) {
            return item.attendanceMark;
        }
        String fromAttendance = readAttendance(item.attendance);
        if (!fromAttendance.isEmpty()) return fromAttendance;
        if ("CANCELLED".equals(status)) return "ABSENT";
        return "UNMARKED";
    }

    private static String readAttendance(Object raw) {
        if (raw instanceof String) return fromText((String) raw);
        if (raw instanceof Map) return fromMap((Map<?, ?>) raw);
        if (raw instanceof List && !((List<?>) raw).isEmpty()) {
            Object row = ((List<?>) raw).get(0);
            if (row instanceof String) return fromText((String) row);
            if (row instanceof Map) return fromMap((Map<?, ?>) row);
        }
        return "";
    }

    private static String fromMap(Map<?, ?> raw) {
        Object value = raw.get("status");
        if (value == null) value = raw.get("mark");
        if (value == null) value = raw.get("value");
        return value == null ? "" : fromText(String.valueOf(value));
    }

    private static String fromText(String value) {
        String text = value == null ? "" : value.toLowerCase();
        if (text.contains("late")) return "LATE";
        if (text.contains("half")) return "HALF";
        if (text.contains("absent") || "a".equals(text)) return "ABSENT";
        if (text.contains("present") || "p".equals(text)) return "PRESENT";
        return "";
    }

    private static String label(String mark) {
        if ("PRESENT".equals(mark)) return "Present";
        if ("LATE".equals(mark)) return "Late";
        if ("HALF".equals(mark)) return "Half day";
        if ("ABSENT".equals(mark)) return "Absent";
        return "Not marked";
    }
}
