package com.smart.seriesblock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Plain-JDK tests for Series (no Android SDK needed). Run: ./test.sh */
public class SeriesTest {
    static int fail;

    static void eq(Object got, Object want, String what) {
        boolean ok = Objects.equals(got, want);
        if (!ok) fail++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + " -> " + got + (ok ? "" : "   (want " + want + ")"));
    }

    static List<String> texts(List<Series> rules) {
        List<String> out = new ArrayList<>();
        for (Series r : rules) out.add(r.text());
        return out;
    }

    static String hit(String number, List<Series> rules) {
        Series r = Series.match(number, rules);
        return r == null ? null : r.text();
    }

    public static void main(String[] a) {
        // Parsing
        eq(texts(Series.parse("+91140\n+911401234500 100\n0140 1234 50; +91 14012 345\n+91\n+9\n+\n+1\n"
                        + "01401234500, 100\n+9114012345001\n+911401234500 0\n,\n")),
                Arrays.asList("+91140", "+911401234500 100", "+91140123450", "+9114012345", "+1"),
                "parse: prefixes, series, spaces, duplicates, bad lines");
        eq(texts(Series.parse("+91 14012 34500 25")), Arrays.asList("+911401234500 25"),
                "parse: spaced first number and length");
        eq(texts(Series.parse("+91 14012 34500")), Arrays.asList("+911401234500"),
                "parse: spaced full number is a 1-number prefix");
        eq(texts(Series.parse("")), Arrays.asList(), "parse: empty");

        // Prefix rules
        List<Series> p = Series.parse("+91140\n+1");
        eq(hit("+911409876543", p), "+91140", "prefix: E.164 number");
        eq(hit("01409876543", p), "+91140", "prefix: 0 form");
        eq(hit("1409876543", p), "+91140", "prefix: 10 digits");
        eq(hit("911409876543", p), "+91140", "prefix: 12 digits");
        eq(hit("+91 14098 76543", p), "+91140", "prefix: spaces");
        eq(hit("+911600123456", p), null, "prefix: other series");
        eq(hit("+14155550100", p), "+1", "prefix: other country");
        eq(hit("1909", p), null, "prefix: short code");
        eq(hit("", p), null, "prefix: empty number");
        eq(hit(null, p), null, "prefix: hidden number");

        // Series with a length, also across a prefix boundary (...599 -> ...600)
        List<Series> s = Series.parse("+911401234590 20");
        eq(hit("+911401234589", s), null, "series: one before the first");
        eq(hit("+911401234590", s), "+911401234590 20", "series: first");
        eq(hit("01401234600", s), "+911401234590 20", "series: across the boundary");
        eq(hit("+911401234609", s), "+911401234590 20", "series: last");
        eq(hit("+911401234610", s), null, "series: one after the last");
        eq(hit("+9114012345900", s), null, "series: longer number");

        // Descriptions
        eq(Series.parse("+91140").get(0).describe(), "+91140…  (10,000,000 numbers, 3-digit prefix)",
                "describe: prefix");
        eq(Series.parse("+9114012345").get(0).describe(), "+9114012345…  (100 numbers, 8-digit prefix)",
                "describe: 100 prefix");
        eq(Series.parse("+911401234500").get(0).describe(), "+911401234500  (1 number)", "describe: full number");
        eq(Series.parse("+911401234590 20").get(0).describe(), "+911401234590 … +911401234609  (20 numbers)",
                "describe: series");
        eq(Series.parse("+1").get(0).describe(), "+1…  (every number that starts with it)", "describe: other country");

        System.out.println(fail == 0 ? "ALL PASS" : fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
