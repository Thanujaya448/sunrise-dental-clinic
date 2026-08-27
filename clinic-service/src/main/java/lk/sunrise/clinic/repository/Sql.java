package lk.sunrise.clinic.repository;

import java.time.LocalDate;

/**
 * Defensive conversions for values read out of a JDBC Map.
 *
 * MySQL Connector/J returns BOOLEAN columns as Boolean, Long or Integer
 * depending on driver settings, and DATE as java.sql.Date or LocalDate.
 * Reading them through these helpers means a driver upgrade cannot produce a
 * ClassCastException at runtime.
 */
public final class Sql {

    private Sql() { }

    public static boolean asBoolean(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    public static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v));
    }

    public static int asInt(Object v) {
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }

    public static LocalDate asLocalDate(Object v) {
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(v));
    }

    public static java.time.LocalTime asLocalTime(Object v) {
        if (v instanceof java.time.LocalTime t) {
            return t;
        }
        if (v instanceof java.sql.Time t) {
            return t.toLocalTime();
        }
        return java.time.LocalTime.parse(String.valueOf(v));
    }
}
