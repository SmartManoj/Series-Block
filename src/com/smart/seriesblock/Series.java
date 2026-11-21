package com.smart.seriesblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One block rule: a prefix ("+91140"), or a first number and a length ("+911401234500 100").
 * No Android classes, so it can be tested on a PC. The home country is India (+91).
 */
final class Series {
    /** Length of a full Indian number in E.164 form: "+91" and 10 digits. */
    static final int HOME_LEN = 13;

    /** E.164 form: the prefix, or the first number of the series. */
    final String start;
    /** 0 = prefix rule; otherwise how many numbers, counted up from start. */
    final long length;

    private Series(String start, long length) {
        this.start = start;
        this.length = length;
    }

    /** Puts a number or a prefix in "+<country><digits>" form. Returns null when it has no digits. */
    static String e164(String raw) {
        if (raw == null) return null;
        boolean plus = raw.trim().startsWith("+");
        String d = raw.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return null;
        if (plus) return "+" + d;
        if (d.startsWith("00")) return "+" + d.substring(2);
        if (d.startsWith("0")) return "+91" + d.substring(1);
        if (d.length() == 12 && d.startsWith("91")) return "+" + d; // 91XXXXXXXXXX without the plus
        return "+91" + d;
    }

    /**
     * One rule on each line (";" also separates rules). A rule is a prefix, or a full +91 number
     * and then a length of 1 to 6 digits ("+911401234500 100" or "01401234500, 100").
     * Dropped: "+", "+9" and "+91" (they would block every Indian number), +91 prefixes longer
     * than a full number, and a length of 0.
     */
    static List<Series> parse(String text) {
        List<Series> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("[\\r\\n;]+")) {
            Series s = parseLine(line.trim());
            if (s == null) continue;
            boolean dup = false;
            for (Series o : out) dup |= o.text().equals(s.text());
            if (!dup) out.add(s);
        }
        return out;
    }

    private static Series parseLine(String line) {
        if (line.isEmpty()) return null;
        String[] tok = line.split("[\\s,]+");
        if (tok.length >= 2 && tok[tok.length - 1].matches("\\d{1,6}")) {
            String last = tok[tok.length - 1];
            String first = e164(line.substring(0, line.lastIndexOf(last)));
            if (first != null && first.startsWith("+91") && first.length() == HOME_LEN) {
                long n = Long.parseLong(last);
                return n > 0 ? new Series(first, n) : null;
            }
        }
        String p = e164(line);
        if (p == null || "+91".startsWith(p)) return null;
        if (p.startsWith("+91") && p.length() > HOME_LEN) return null;
        return new Series(p, 0);
    }

    /** number must be in E.164 form. */
    boolean matches(String number) {
        if (number == null) return false;
        if (length == 0) return number.startsWith(start);
        if (number.length() != start.length() || !number.startsWith("+91")) return false;
        long v = Long.parseLong(number.substring(1)), s = Long.parseLong(start.substring(1));
        return v >= s && v - s < length;
    }

    /** Returns the first rule that the number matches, or null. */
    static Series match(String number, List<Series> rules) {
        String n = e164(number);
        if (n == null) return null;
        for (Series r : rules) if (r.matches(n)) return r;
        return null;
    }

    /** The saved form: "+91140" or "+911401234500 100". */
    String text() {
        return length == 0 ? start : start + " " + length;
    }

    /** What the rule blocks, with the number of numbers. */
    String describe() {
        if (length == 0) {
            if (!start.startsWith("+91")) return start + "…  (every number that starts with it)";
            long count = 1;
            for (int i = start.length(); i < HOME_LEN; i++) count *= 10;
            return count == 1 ? start + "  (1 number)" : start + "…  (" + count(count) + ")";
        }
        if (length == 1) return start + "  (1 number)";
        long s = Long.parseLong(start.substring(1));
        long end = Math.min(s + length - 1, 919999999999L);
        return start + " … +" + end + "  (" + count(end - s + 1) + ")";
    }

    private static String count(long n) {
        return String.format(Locale.US, "%,d number%s", n, n == 1 ? "" : "s");
    }
}
