package com.alibaba.fastjson2.util;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderImplDate;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRules;
import java.util.Date;
import java.util.TimeZone;

import static com.alibaba.fastjson2.util.DateUtils.DateTimeFormatPattern.*;
import static com.alibaba.fastjson2.util.JDKUtils.*;
import static java.time.ZoneOffset.UTC;

public class DateUtils {
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();
    public static final String SHANGHAI_ZONE_ID_NAME = "Asia/Shanghai";
    public static final ZoneId SHANGHAI_ZONE_ID
            = SHANGHAI_ZONE_ID_NAME.equals(DEFAULT_ZONE_ID.getId())
            ? DEFAULT_ZONE_ID
            : ZoneId.of(SHANGHAI_ZONE_ID_NAME);
    public static final ZoneRules SHANGHAI_ZONE_RULES = SHANGHAI_ZONE_ID.getRules();

    static DateTimeFormatter DATE_TIME_FORMATTER_34;
    static DateTimeFormatter DATE_TIME_FORMATTER_COOKIE;
    static DateTimeFormatter DATE_TIME_FORMATTER_COOKIE_LOCAL;
    static DateTimeFormatter DATE_TIME_FORMATTER_RFC_2822;

    static final int LOCAL_EPOCH_DAY;
    static {
        final long timeMillis = System.currentTimeMillis();
        ZoneId zoneId = DEFAULT_ZONE_ID;
        final int SECONDS_PER_DAY = 60 * 60 * 24;

        long epochSecond = Math.floorDiv(timeMillis, 1000L);
        int offsetTotalSeconds;
        if (zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES) {
            offsetTotalSeconds = getShanghaiZoneOffsetTotalSeconds(epochSecond);
        } else {
            Instant instant = Instant.ofEpochMilli(timeMillis);
            offsetTotalSeconds = zoneId.getRules().getOffset(instant).getTotalSeconds();
        }

        long localSecond = epochSecond + offsetTotalSeconds;
        LOCAL_EPOCH_DAY = (int) Math.floorDiv(localSecond, (long) SECONDS_PER_DAY);
    }

    static class CacheDate8 {
        static final String[] CACHE = new String[1024];
    }

    static class CacheDate10 {
        static final String[] CACHE = new String[1024];
    }

    public static Date parseDateYMDHMS19(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }

        long millis = parseMillisYMDHMS19(str, DEFAULT_ZONE_ID);
        return new Date(millis);
    }

    public static Date parseDate(String str, String format) {
        return parseDate(str, format, DEFAULT_ZONE_ID);
    }

    public static Date parseDate(String str, String format, ZoneId zoneId) {
        if (str == null || str.isEmpty() || "null".equals(str)) {
            return null;
        }

        if (format == null || format.isEmpty()) {
            return parseDate(str);
        }

        switch (format) {
            case "yyyy-MM-dd'T'HH:mm:ss": {
                long millis = parseMillis19(str, zoneId, DATE_TIME_FORMAT_19_DASH_T);
                return new Date(millis);
            }
            case "yyyy-MM-dd HH:mm:ss": {
                long millis = parseMillisYMDHMS19(str, zoneId);
                return new Date(millis);
            }
            case "yyyy/MM/dd HH:mm:ss": {
                long millis = parseMillis19(str, zoneId, DATE_TIME_FORMAT_19_SLASH);
                return new Date(millis);
            }
            case "dd.MM.yyyy HH:mm:ss": {
                long millis = parseMillis19(str, zoneId, DATE_TIME_FORMAT_19_DOT);
                return new Date(millis);
            }
            case "yyyy-MM-dd": {
                long millis = parseMillis10(str, zoneId, DATE_FORMAT_10_DASH);
                return new Date(millis);
            }
            case "yyyy/MM/dd": {
                long millis = parseMillis10(str, zoneId, DATE_FORMAT_10_SLASH);
                return new Date(millis);
            }
            case "yyyyMMdd": {
                DateTimeFormatter formatter;
                formatter = DateTimeFormatter.ofPattern(format);
                LocalDate ldt = LocalDate.parse(str, formatter);
                long millis = millis(
                        zoneId,
                        ldt.getYear(),
                        ldt.getMonthValue(),
                        ldt.getDayOfMonth(),
                        0,
                        0,
                        0,
                        0
                );
                return new Date(millis);
            }
            case "yyyyMMddHHmmssSSSZ": {
                long millis = parseMillis(str, DEFAULT_ZONE_ID);
                return new Date(millis);
            }
            case "iso8601":
                return parseDate(str);
            default:
                break;
        }

        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }

        DateTimeFormatter formatter;
        formatter = DateTimeFormatter.ofPattern(format);
        LocalDateTime ldt = LocalDateTime.parse(str, formatter);
        long millis = millis(ldt, zoneId);
        return new Date(millis);
    }

    public static Date parseDate(String str) {
        long millis = parseMillis(str, DEFAULT_ZONE_ID);
        if (millis == 0) {
            return null;
        }
        return new Date(millis);
    }

    public static Date parseDate(String str, ZoneId zoneId) {
        long millis = parseMillis(str, zoneId);
        if (millis == 0) {
            return null;
        }
        return new Date(millis);
    }

    public static long parseMillis(String str) {
        return parseMillis(str, DEFAULT_ZONE_ID);
    }

    public static long parseMillis(String str, ZoneId zoneId) {
        if (str == null) {
            return 0;
        }

        final int len = str.length();
        if (len == 0 || (len == 4 && "null".equals(str))) {
            return 0;
        }

        char c10;
        long millis;
        char c0 = str.charAt(0);
        if (c0 == '"' && str.charAt(len - 1) == '"') {
            try (JSONReader jsonReader = JSONReader.of(str)) {
                Date date = (Date) ObjectReaderImplDate.INSTANCE.readObject(
                        jsonReader,
                        null,
                        null,
                        0
                );
                millis = date.getTime();
            }
        } else if (len == 19) {
            millis = DateUtils.parseMillis19(str, zoneId);
        } else if (len > 19
                // ISO Date with offset example '2011-12-03+01:00'
                || (len == 16 && ((c10 = str.charAt(10)) == '+' || c10 == '-'))
        ) {
            ZonedDateTime zdt = parseZonedDateTime(str, zoneId);
            millis = zdt.toInstant().toEpochMilli();
        } else if ((c0 == '-' || c0 >= '0' && c0 <= '9') && IOUtils.isNumber(str)) {
            millis = Long.parseLong(str);
            if (len == 8 && millis >= 19700101 && millis <= 21000101) {
                int year = (int) millis / 10000;
                int month = ((int) millis % 10000) / 100;
                int dom = (int) millis % 100;

                if (month >= 1 && month <= 12) {
                    int max = 31;
                    switch (month) {
                        case 2:
                            boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                            max = leapYear ? 29 : 28;
                            break;
                        case 4:
                        case 6:
                        case 9:
                        case 11:
                            max = 30;
                            break;
                    }
                    if (dom <= max) {
                        LocalDateTime ldt = LocalDateTime.of(year, month, dom, 0, 0, 0);
                        ZonedDateTime zdt = ZonedDateTime.ofLocal(ldt, zoneId, null);
                        long seconds = zdt.toEpochSecond();
                        millis = seconds * 1000L;
                    }
                }
            }
        } else {
            char last = str.charAt(len - 1);
            if (last == 'Z') {
                zoneId = UTC;
            }
            LocalDateTime ldt = DateUtils.parseLocalDateTime(str, 0, len);
            if (ldt == null && "0000-00-00".equals(str)) {
                ldt = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            }
            ZonedDateTime zdt = ZonedDateTime.ofLocal(ldt, zoneId, null);
            long seconds = zdt.toEpochSecond();
            int nanos = ldt.getNano();
            if (seconds < 0 && nanos > 0) {
                millis = (seconds + 1) * 1000 + nanos / 1000_000 - 1000;
            } else {
                millis = seconds * 1000L + nanos / 1000_000;
            }
        }
        return millis;
    }

    public static LocalDateTime parseLocalDateTime(String str) {
        if (str == null) {
            return null;
        }
        return parseLocalDateTime(str, 0, str.length());
    }

    public static LocalDateTime parseLocalDateTime(String str, int off, int len) {
        if (str == null || len == 0) {
            return null;
        }

        switch (len) {
            case 4:
                if (str.regionMatches(0, "null", off, len)) {
                    return null;
                }
                String input = str.substring(off, len);
                throw new DateTimeParseException("illegal input " + input, input, 0);
            case 8: {
                LocalDate localDate = parseLocalDate8(str, off);
                if (localDate == null) {
                    return null;
                }
                return LocalDateTime.of(localDate, LocalTime.MIN);
            }
            case 9: {
                LocalDate localDate = parseLocalDate9(str, off);
                if (localDate == null) {
                    return null;
                }
                return LocalDateTime.of(localDate, LocalTime.MIN);
            }
            case 10: {
                LocalDate localDate = parseLocalDate10(str, off);
                if (localDate == null) {
                    return null;
                }
                return LocalDateTime.of(localDate, LocalTime.MIN);
            }
            case 11: {
                LocalDate localDate = parseLocalDate11(str, off);
                return LocalDateTime.of(localDate, LocalTime.MIN);
            }
            case 12:
                return parseLocalDateTime12(str, off);
            case 14:
                return parseLocalDateTime14(str, off);
            case 16:
                return parseLocalDateTime16(str, off);
            case 17:
                return parseLocalDateTime17(str, off);
            case 18:
                return parseLocalDateTime18(str, off);
            case 19:
                return parseLocalDateTime19(str, off);
            case 20:
                return parseLocalDateTime20(str, off);
            default:
                return parseLocalDateTimeX(str, off, len);
        }
    }

    public static LocalDate parseLocalDate(String str) {
        if (str == null) {
            return null;
        }
        return parseLocalDate(str, 0, str.length());
    }

    public static LocalDate parseLocalDate(String str, int off, int len) {
        if (str == null || len == 0) {
            return null;
        }

        if (off + len > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        switch (len) {
            case 8:
                return parseLocalDate8(str, off);
            case 9:
                return parseLocalDate9(str, off);
            case 10:
                return parseLocalDate10(str, off);
            case 11:
                return parseLocalDate11(str, off);
            default:
                String input = str.substring(off, off + len);
                if ("null".equals(input)) {
                    return null;
                }
                throw new DateTimeParseException("illegal input " + input, input, 0);
        }
    }

    /**
     * yyyy-m-d
     * yyyyMMdd
     */
    public static LocalDate parseLocalDate8(String str, int off) {
        if (off + 8 > str.length()) {
            throw new DateTimeParseException("illegal input", str.substring(off), 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);

        char y0, y1, y2, y3, m0, m1, d0, d1;
        if (c4 == '-' && c6 == '-') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = '0';
            d1 = c7;
        } else {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c4;
            m1 = c5;

            d0 = c6;
            d1 = c7;
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 8);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 8);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 8);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            return null;
        }

        return LocalDate.of(year, month, dom);
    }

    /**
     * yyyy-MM-d
     * yyyy-M-dd
     */
    public static LocalDate parseLocalDate9(String str, int off) {
        if (off + 9 > str.length()) {
            throw new DateTimeParseException("illegal input", str.substring(off), 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);

        char y0, y1, y2, y3, m0, m1, d0, d1;
        if (c4 == '-' && c7 == '-') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = '0';
            d1 = c8;
        } else if (c4 == '-' && c6 == '-') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = c7;
            d1 = c8;
        } else if (c4 == '/' && c7 == '/') { // tw : yyyy/mm/d
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = '0';
            d1 = c8;
        } else if (c4 == '/' && c6 == '/') { // tw : yyyy/m/dd
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = c7;
            d1 = c8;
        } else if (c1 == '.' && c4 == '.') {
            d0 = '0';
            d1 = c0;

            m0 = c2;
            m1 = c3;

            y0 = c5;
            y1 = c6;
            y2 = c7;
            y3 = c8;
        } else if (c2 == '.' && c4 == '.') {
            d0 = c0;
            d1 = c1;

            m0 = '0';
            m1 = c3;

            y0 = c5;
            y1 = c6;
            y2 = c7;
            y3 = c8;
        } else if (c1 == '-' && c4 == '-') {
            d0 = '0';
            d1 = c0;

            m0 = c2;
            m1 = c3;

            y0 = c5;
            y1 = c6;
            y2 = c7;
            y3 = c8;
        } else if (c2 == '-' && c4 == '-') {
            d0 = c0;
            d1 = c1;

            m0 = '0';
            m1 = c3;

            y0 = c5;
            y1 = c6;
            y2 = c7;
            y3 = c8;
        } else if (c4 == '年' && c6 == '月' && c8 == '日') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = '0';
            d1 = c7;
        } else if (c4 == '년' && c6 == '월' && c8 == '일') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = '0';
            d1 = c7;
        } else {
            String input = str.substring(off, off + 9);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 9);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 9);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 9);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            return null;
        }

        return LocalDate.of(year, month, dom);
    }

    /**
     * yyyy-MM-dd
     * yyyy/MM/dd
     * dd.MM.yyyy
     * yyyy年M月dd日
     * yyyy年MM月d日
     * yyyy MMM d
     */
    public static LocalDate parseLocalDate10(String str, int off) {
        if (off + 10 > str.length()) {
            throw new DateTimeParseException("illegal input", str.substring(off), 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);
        char c9 = str.charAt(off + 9);

        char y0, y1, y2, y3, m0, m1, d0, d1;
        if (c4 == '-' && c7 == '-') {
            // yyyy-MM-dd
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;
        } else if (c4 == '/' && c7 == '/') {
            // tw : yyyy/mm/dd
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;
        } else if (c2 == '.' && c5 == '.') {
            // dd.MM.yyyy
            d0 = c0;
            d1 = c1;

            m0 = c3;
            m1 = c4;

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;
        } else if (c2 == '-' && c5 == '-') {
            // dd-MM-yyyy
            d0 = c0;
            d1 = c1;

            m0 = c3;
            m1 = c4;

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;
        } else if (c4 == '年' && c6 == '月' && c9 == '日') {
            // yyyy年M月dd日
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = c7;
            d1 = c8;
        } else if (c4 == '년' && c6 == '월' && c9 == '일') {
            // yyyy년M월dd일
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = c7;
            d1 = c8;
        } else if (c4 == '年' && c7 == '月' && c9 == '日') {
            // // yyyy年MM月d日
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = '0';
            d1 = c8;
        } else if (c4 == '년' && c7 == '월' && c9 == '일') {
            // yyyy년MM월d일
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = '0';
            d1 = c8;
        } else if (c1 == ' ' && c5 == ' ') {
            // yyyy MMM d
            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                String input = str.substring(off, off + 16);
                throw new DateTimeParseException("illegal input " + input, input, 0);
            }

            d0 = '0';
            d1 = c0;
        } else {
            throw new DateTimeParseException("illegal input", str.substring(off, off + 10), 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str.substring(off, off + 10), 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str.substring(off, off + 10), 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str.substring(off, off + 10), 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            return null;
        }

        return LocalDate.of(year, month, dom);
    }

    /**
     * yyyy年MM月dd日
     * yyyy년MM월dd일
     */
    public static LocalDate parseLocalDate11(String str, int off) {
        if (off + 11 > str.length()) {
            throw new DateTimeParseException("illegal input", str.substring(off), 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);
        char c9 = str.charAt(off + 9);
        char c10 = str.charAt(off + 10);

        char y0, y1, y2, y3, m0, m1, d0, d1;
        if (c4 == '年' && c7 == '月' && c10 == '日') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;
        } else if (c4 == '년' && c7 == '월' && c10 == '일') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;
        } else if (c2 == ' ' && c6 == ' ') {
            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                String input = str.substring(off, off + 11);
                throw new DateTimeParseException("illegal input " + input, input, 0);
            }

            d0 = c0;
            d1 = c1;
        } else {
            String input = str.substring(off, off + 11);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 11);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 11);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 11);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            return null;
        }

        return LocalDate.of(year, month, dom);
    }

    /**
     * yyyyMMddHHmm
     *
     * @return
     */
    public static LocalDateTime parseLocalDateTime12(String str, int off) {
        if (off + 12 > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char y0 = str.charAt(off + 0);
        char y1 = str.charAt(off + 1);
        char y2 = str.charAt(off + 2);
        char y3 = str.charAt(off + 3);
        char m0 = str.charAt(off + 4);
        char m1 = str.charAt(off + 5);
        char d0 = str.charAt(off + 6);
        char d1 = str.charAt(off + 7);
        char h0 = str.charAt(off + 8);
        char h1 = str.charAt(off + 9);
        char i0 = str.charAt(off + 10);
        char i1 = str.charAt(off + 11);

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 12);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 12);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 12);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            String input = str.substring(off, off + 12);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            String input = str.substring(off, off + 12);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        if (year == 0 && month == 0 && dom == 0 && hour == 0 && minute == 0) {
            return null;
        }

        return LocalDateTime.of(year, month, dom, hour, minute, 0);
    }

    /**
     * yyyyMMddHHmmss
     */
    public static LocalDateTime parseLocalDateTime14(String str, int off) {
        if (off + 14 > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char y0 = str.charAt(off + 0);
        char y1 = str.charAt(off + 1);
        char y2 = str.charAt(off + 2);
        char y3 = str.charAt(off + 3);
        char m0 = str.charAt(off + 4);
        char m1 = str.charAt(off + 5);
        char d0 = str.charAt(off + 6);
        char d1 = str.charAt(off + 7);
        char h0 = str.charAt(off + 8);
        char h1 = str.charAt(off + 9);
        char i0 = str.charAt(off + 10);
        char i1 = str.charAt(off + 11);
        char s0 = str.charAt(off + 12);
        char s1 = str.charAt(off + 13);

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 14);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 14);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 14);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            String input = str.substring(off, off + 14);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            String input = str.substring(off, off + 14);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            String input = str.substring(off, off + 14);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        return LocalDateTime.of(year, month, dom, hour, minute, second);
    }

    /**
     * yyyy-MM-ddTHH:mm
     * yyyy-MM-dd HH:mm
     * yyyyMMddTHHmmssZ
     * yyyy-MM-ddTH:m:s
     * yyyy-MM-dd H:m:s
     */
    public static LocalDateTime parseLocalDateTime16(String str, int off) {
        if (off + 16 > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);
        char c9 = str.charAt(off + 9);
        char c10 = str.charAt(off + 10);
        char c11 = str.charAt(off + 11);
        char c12 = str.charAt(off + 12);
        char c13 = str.charAt(off + 13);
        char c14 = str.charAt(off + 14);
        char c15 = str.charAt(off + 15);

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0 = '0', s1 = '0';
        if (c4 == '-' && c7 == '-' && (c10 == 'T' || c10 == ' ') && c13 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;
        } else if (c8 == 'T' && c15 == 'Z') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;
            m0 = c4;
            m1 = c5;
            d0 = c6;
            d1 = c7;
            h0 = c9;
            h1 = c10;
            i0 = c11;
            i1 = c12;
            s0 = c13;
            s1 = c14;
        } else if (c4 == '-' && c7 == '-' && (c10 == 'T' || c10 == ' ') && c12 == ':' && c14 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = '0';
            h1 = c11;

            i0 = '0';
            i1 = c13;

            s0 = '0';
            s1 = c15;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c13 == ':') {
            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                String input = str.substring(off, off + 16);
                throw new DateTimeParseException("illegal input " + input, input, 0);
            }

            d0 = '0';
            d1 = c0;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = '0';
            s1 = '0';
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c12 == ':' && c14 == ':') {
            // d MMM yyyy H:m:ss
            // 6 DEC 2020 2:3:14
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = '0';
            h1 = c11;

            i0 = '0';
            i1 = c13;

            s0 = '0';
            s1 = c15;
        } else {
            String input = str.substring(off, off + 16);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 16);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 16);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 16);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            String input = str.substring(off, off + 16);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            String input = str.substring(off, off + 16);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            String input = str.substring(off, off + 16);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        return LocalDateTime.of(year, month, dom, hour, minute, second);
    }

    /**
     * yyyy-MM-ddTHH:mmZ
     * yyyy-MM-dd HH:mmZ
     * yyyy-M-dTHH:mm:ss
     * yyyy-M-d HH:mm:ss
     */
    public static LocalDateTime parseLocalDateTime17(String str, int off) {
        if (off + 17 > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);
        char c9 = str.charAt(off + 9);
        char c10 = str.charAt(off + 10);
        char c11 = str.charAt(off + 11);
        char c12 = str.charAt(off + 12);
        char c13 = str.charAt(off + 13);
        char c14 = str.charAt(off + 14);
        char c15 = str.charAt(off + 15);
        char c16 = str.charAt(off + 16);

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1;
        if (c4 == '-' && c7 == '-' && (c10 == 'T' || c10 == ' ') && c13 == ':' && c16 == 'Z') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = '0';
            s1 = '0';
        } else if (c4 == '-' && c6 == '-' && (c8 == ' ' || c8 == 'T') && c11 == ':' && c14 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = '0';
            d1 = c7;

            h0 = c9;
            h1 = c10;

            i0 = c12;
            i1 = c13;

            s0 = c15;
            s1 = c16;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c14 == ':') {
            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                String input = str.substring(off, off + 17);
                throw new DateTimeParseException("illegal input " + input, input, 0);
            }

            d0 = c0;
            d1 = c1;

            h0 = c12;
            h1 = c13;

            i0 = c15;
            i1 = c16;

            s0 = '0';
            s1 = '0';
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c12 == ':' && c14 == ':') {
            // d MMM yyyy H:m:ss
            // 6 DEC 2020 1:3:14
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = '0';
            h1 = c11;

            i0 = '0';
            i1 = c13;

            s0 = c15;
            s1 = c16;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c12 == ':' && c15 == ':') {
            // d MMM yyyy H:mm:s
            // 6 DEC 2020 1:13:4
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = '0';
            h1 = c11;

            i0 = c13;
            i1 = c14;

            s0 = '0';
            s1 = c16;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c13 == ':' && c15 == ':') {
            // d MMM yyyy HH:m:s
            // 6 DEC 2020 11:3:4
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = c11;
            h1 = c12;

            i0 = '0';
            i1 = c14;

            s0 = '0';
            s1 = c16;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c13 == ':' && c15 == ':') {
            // dd MMM yyyy H:m:s
            // 16 DEC 2020 1:3:4
            d0 = c0;
            d1 = c1;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            h0 = '0';
            h1 = c12;

            i0 = '0';
            i1 = c14;

            s0 = '0';
            s1 = c16;
        } else {
            String input = str.substring(off, off + 17);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 17);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 17);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 17);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            String input = str.substring(off, off + 17);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            String input = str.substring(off, off + 17);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            String input = str.substring(off, off + 17);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        return LocalDateTime.of(year, month, dom, hour, minute, second);
    }

    /**
     * yyyy-M-ddTHH:mm:ss
     * yyyy-M-dd HH:mm:ss
     * yyyy-MM-dTHH:mm:ss
     * yyyy-MM-d HH:mm:ss
     * yyyy-MM-ddTH:mm:ss
     * yyyy-MM-dd H:mm:ss
     * yyyy-MM-ddTHH:m:ss
     * yyyy-MM-dd HH:m:ss
     * yyyy-MM-ddTHH:mm:s
     * yyyy-MM-dd HH:mm:s
     */
    public static LocalDateTime parseLocalDateTime18(String str, int off) {
        if (off + 18 > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);
        char c9 = str.charAt(off + 9);
        char c10 = str.charAt(off + 10);
        char c11 = str.charAt(off + 11);
        char c12 = str.charAt(off + 12);
        char c13 = str.charAt(off + 13);
        char c14 = str.charAt(off + 14);
        char c15 = str.charAt(off + 15);
        char c16 = str.charAt(off + 16);
        char c17 = str.charAt(off + 17);

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1;
        if (c4 == '-' && c6 == '-' && (c9 == ' ' || c9 == 'T') && c12 == ':' && c15 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = '0';
            m1 = c5;

            d0 = c7;
            d1 = c8;

            h0 = c10;
            h1 = c11;

            i0 = c13;
            i1 = c14;

            s0 = c16;
            s1 = c17;
        } else if (c4 == '-' && c7 == '-' && (c9 == ' ' || c9 == 'T') && c12 == ':' && c15 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = '0';
            d1 = c8;

            h0 = c10;
            h1 = c11;

            i0 = c13;
            i1 = c14;

            s0 = c16;
            s1 = c17;
        } else if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c12 == ':' && c15 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = '0';
            h1 = c11;

            i0 = c13;
            i1 = c14;

            s0 = c16;
            s1 = c17;
        } else if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c15 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = '0';
            i1 = c14;

            s0 = c16;
            s1 = c17;
        } else if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = '0';
            s1 = c17;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c12 == ':' && c15 == ':') {
            // d MMM yyyy H:mm:ss
            // 6 DEC 2020 2:13:14
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = '0';
            h1 = c11;

            i0 = c13;
            i1 = c14;

            s0 = c16;
            s1 = c17;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c13 == ':' && c15 == ':') {
            // d MMM yyyy HH:m:ss
            // 6 DEC 2020 12:3:14
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = c11;
            h1 = c12;

            i0 = '0';
            i1 = c14;

            s0 = c16;
            s1 = c17;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c13 == ':' && c16 == ':') {
            // d MMM yyyy HH:m:ss
            // 6 DEC 2020 12:3:14
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = '0';
            s1 = c17;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c14 == ':' && c16 == ':') {
            // dd MMM yyyy HH:m:s
            // 16 DEC 2020 12:3:4
            d0 = c0;
            d1 = c1;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            h0 = c12;
            h1 = c13;

            i0 = '0';
            i1 = c15;

            s0 = '0';
            s1 = c17;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c13 == ':' && c16 == ':') {
            // dd MMM yyyy H:mm:s
            // 16 DEC 2020 1:13:4
            d0 = c0;
            d1 = c1;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            h0 = '0';
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = '0';
            s1 = c17;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c13 == ':' && c15 == ':') {
            // dd MMM yyyy H:mm:s
            // 16 DEC 2020 1:13:4
            d0 = c0;
            d1 = c1;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            h0 = '0';
            h1 = c12;

            i0 = '0';
            i1 = c14;

            s0 = c16;
            s1 = c17;
        } else {
            String input = str.substring(off, off + 18);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            String input = str.substring(off, off + 18);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            String input = str.substring(off, off + 18);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            String input = str.substring(off, off + 18);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            String input = str.substring(off, off + 18);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            String input = str.substring(off, off + 18);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            String input = str.substring(off, off + 18);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        return LocalDateTime.of(year, month, dom, hour, minute, second);
    }

    /**
     * yyyy-MM-ddTHH:mm:ss
     * yyyy-MM-dd HH:mm:ss
     * yyyy/MM/ddTHH:mm:ss
     * yyyy/MM/dd HH:mm:ss
     */
    public static LocalDateTime parseLocalDateTime19(String str, int off) {
        if (off + 19 > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);
        char c9 = str.charAt(off + 9);
        char c10 = str.charAt(off + 10);
        char c11 = str.charAt(off + 11);
        char c12 = str.charAt(off + 12);
        char c13 = str.charAt(off + 13);
        char c14 = str.charAt(off + 14);
        char c15 = str.charAt(off + 15);
        char c16 = str.charAt(off + 16);
        char c17 = str.charAt(off + 17);
        char c18 = str.charAt(off + 18);

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1;
        if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c4 == '/' && c7 == '/' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c2 == '/' && c5 == '/' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':') {
            d0 = c0;
            d1 = c1;

            m0 = c3;
            m1 = c4;

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c13 == ':' && c16 == ':') {
            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = '0';
            d1 = c0;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else {
            String input = str.substring(off, off + 19);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        LocalDateTime ldt = localDateTime(y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1);
        if (ldt == null) {
            String input = str.substring(off, off + 19);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        return ldt;
    }

    public static LocalDateTime parseLocalDateTime20(String str, int off) {
        if (off + 19 > str.length()) {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char c0 = str.charAt(off + 0);
        char c1 = str.charAt(off + 1);
        char c2 = str.charAt(off + 2);
        char c3 = str.charAt(off + 3);
        char c4 = str.charAt(off + 4);
        char c5 = str.charAt(off + 5);
        char c6 = str.charAt(off + 6);
        char c7 = str.charAt(off + 7);
        char c8 = str.charAt(off + 8);
        char c9 = str.charAt(off + 9);
        char c10 = str.charAt(off + 10);
        char c11 = str.charAt(off + 11);
        char c12 = str.charAt(off + 12);
        char c13 = str.charAt(off + 13);
        char c14 = str.charAt(off + 14);
        char c15 = str.charAt(off + 15);
        char c16 = str.charAt(off + 16);
        char c17 = str.charAt(off + 17);
        char c18 = str.charAt(off + 18);
        char c19 = str.charAt(off + 19);

        if (c2 != ' ' || c6 != ' ' || c11 != ' ' || c14 != ':' || c17 != ':') {
            String input = str.substring(off);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char y0 = c7;
        char y1 = c8;
        char y2 = c9;
        char y3 = c10;

        char m0, m1;

        int month = DateUtils.month(c3, c4, c5);
        if (month > 0) {
            m0 = (char) ('0' + month / 10);
            m1 = (char) ('0' + (month % 10));
        } else {
            m0 = '0';
            m1 = '0';
        }

        char d0 = c0;
        char d1 = c1;

        char h0 = c12;
        char h1 = c13;

        char i0 = c15;
        char i1 = c16;

        char s0 = c18;
        char s1 = c19;

        LocalDateTime ldt = localDateTime(
                y0, y1, y2, y3,
                m0, m1,
                d0, d1,
                h0, h1,
                i0, i1,
                s0, s1
        );
        if (ldt == null) {
            String input = str.substring(off, off + 20);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        return ldt;
    }

    static LocalDateTime parseLocalDateTimeX(String str, int offset, int len) {
        if (str == null || len == 0) {
            return null;
        }

        if (len < 21 || len > 29) {
            String input = str.substring(offset, len);
            throw new DateTimeParseException("illegal input " + input, input, 0);
        }

        char c0 = str.charAt(offset + 0);
        char c1 = str.charAt(offset + 1);
        char c2 = str.charAt(offset + 2);
        char c3 = str.charAt(offset + 3);
        char c4 = str.charAt(offset + 4);
        char c5 = str.charAt(offset + 5);
        char c6 = str.charAt(offset + 6);
        char c7 = str.charAt(offset + 7);
        char c8 = str.charAt(offset + 8);
        char c9 = str.charAt(offset + 9);
        char c10 = str.charAt(offset + 10);
        char c11 = str.charAt(offset + 11);
        char c12 = str.charAt(offset + 12);
        char c13 = str.charAt(offset + 13);
        char c14 = str.charAt(offset + 14);
        char c15 = str.charAt(offset + 15);
        char c16 = str.charAt(offset + 16);
        char c17 = str.charAt(offset + 17);
        char c18 = str.charAt(offset + 18);
        char c19 = str.charAt(offset + 19);
        char c20, c21 = '0', c22 = '0', c23 = '0', c24 = '0', c25 = '0', c26 = '0', c27 = '0', c28 = '0';
        switch (len) {
            case 21:
                c20 = str.charAt(offset + 20);
                break;
            case 22:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                break;
            case 23:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                c22 = str.charAt(offset + 22);
                break;
            case 24:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                c22 = str.charAt(offset + 22);
                c23 = str.charAt(offset + 23);
                break;
            case 25:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                c22 = str.charAt(offset + 22);
                c23 = str.charAt(offset + 23);
                c24 = str.charAt(offset + 24);
                break;
            case 26:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                c22 = str.charAt(offset + 22);
                c23 = str.charAt(offset + 23);
                c24 = str.charAt(offset + 24);
                c25 = str.charAt(offset + 25);
                break;
            case 27:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                c22 = str.charAt(offset + 22);
                c23 = str.charAt(offset + 23);
                c24 = str.charAt(offset + 24);
                c25 = str.charAt(offset + 25);
                c26 = str.charAt(offset + 26);
                break;
            case 28:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                c22 = str.charAt(offset + 22);
                c23 = str.charAt(offset + 23);
                c24 = str.charAt(offset + 24);
                c25 = str.charAt(offset + 25);
                c26 = str.charAt(offset + 26);
                c27 = str.charAt(offset + 27);
                break;
            default:
                c20 = str.charAt(offset + 20);
                c21 = str.charAt(offset + 21);
                c22 = str.charAt(offset + 22);
                c23 = str.charAt(offset + 23);
                c24 = str.charAt(offset + 24);
                c25 = str.charAt(offset + 25);
                c26 = str.charAt(offset + 26);
                c27 = str.charAt(offset + 27);
                c28 = str.charAt(offset + 28);
                break;
        }

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1, S0, S1, S2, S3, S4, S5, S6, S7, S8;
        if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':' && c19 == '.') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = c23;
            S4 = c24;
            S5 = c25;
            S6 = c26;
            S7 = c27;
            S8 = c28;
        } else {
            String input = str.substring(offset, len);
            throw new DateTimeParseException("illegal input " + input, input, offset);
        }

        LocalDateTime ldt = localDateTime(y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1, S0, S1, S2, S3, S4, S5, S6, S7, S8);
        if (ldt == null) {
            String input = str.substring(offset, len);
            throw new DateTimeParseException("illegal input " + input, input, offset);
        }
        return ldt;
    }

    /**
     *
     * ISO Date with offset, example '2011-12-03+01:00'
     */
    public static ZonedDateTime parseZonedDateTime16(String str) {
        if (str.length() != 16) {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        char c0 = str.charAt(0);
        char c1 = str.charAt(1);
        char c2 = str.charAt(2);
        char c3 = str.charAt(3);
        char c4 = str.charAt(4);
        char c5 = str.charAt(5);
        char c6 = str.charAt(6);
        char c7 = str.charAt(7);
        char c8 = str.charAt(8);
        char c9 = str.charAt(9);
        char c10 = str.charAt(10);
        char c11 = str.charAt(11);
        char c12 = str.charAt(12);
        char c13 = str.charAt(13);
        char c14 = str.charAt(14);
        char c15 = str.charAt(15);

        char y0, y1, y2, y3, m0, m1, d0, d1;
        if (c4 == '-' && c7 == '-' && (c10 == '+' || c10 == '-') && c13 == ':') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        ZoneId zoneId;
        String zoneIdStr = str.substring(10, 16);
        zoneId = getZoneId(zoneIdStr, DEFAULT_ZONE_ID);

        LocalDateTime ldt = LocalDateTime.of(LocalDate.of(year, month, dom), LocalTime.MIN);
        return ZonedDateTime.of(ldt, zoneId);
    }

    public static ZonedDateTime parseZonedDateTime(String str) {
        return parseZonedDateTime(str, null);
    }

    public static ZonedDateTime parseZonedDateTime(String str, ZoneId defaultZoneId) {
        if (str == null) {
            return null;
        }

        final int len = str.length();
        if (len == 0) {
            return null;
        }

        if (len == 16) {
            return parseZonedDateTime16(str);
        }

        if (len < 19) {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        String zoneIdStr = null;

        char c0 = str.charAt(0);
        char c1 = str.charAt(1);
        char c2 = str.charAt(2);
        char c3 = str.charAt(3);
        char c4 = str.charAt(4);
        char c5 = str.charAt(5);
        char c6 = str.charAt(6);
        char c7 = str.charAt(7);
        char c8 = str.charAt(8);
        char c9 = str.charAt(9);
        char c10 = str.charAt(10);
        char c11 = str.charAt(11);
        char c12 = str.charAt(12);
        char c13 = str.charAt(13);
        char c14 = str.charAt(14);
        char c15 = str.charAt(15);
        char c16 = str.charAt(16);
        char c17 = str.charAt(17);
        char c18 = str.charAt(18);
        char c19 = len == 19 ? ' ' : str.charAt(19);

        char c20, c21 = '0', c22 = '0', c23 = '0', c24 = '0', c25 = '0', c26 = '0', c27 = '0', c28 = '0', c29 = '\0';
        switch (len) {
            case 19:
            case 20:
                c20 = '\0';
                break;
            case 21:
                c20 = str.charAt(20);
                break;
            case 22:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                break;
            case 23:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                break;
            case 24:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                c23 = str.charAt(23);
                break;
            case 25:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                c23 = str.charAt(23);
                c24 = str.charAt(24);
                break;
            case 26:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                c23 = str.charAt(23);
                c24 = str.charAt(24);
                c25 = str.charAt(25);
                break;
            case 27:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                c23 = str.charAt(23);
                c24 = str.charAt(24);
                c25 = str.charAt(25);
                c26 = str.charAt(26);
                break;
            case 28:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                c23 = str.charAt(23);
                c24 = str.charAt(24);
                c25 = str.charAt(25);
                c26 = str.charAt(26);
                c27 = str.charAt(27);
                break;
            case 29:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                c23 = str.charAt(23);
                c24 = str.charAt(24);
                c25 = str.charAt(25);
                c26 = str.charAt(26);
                c27 = str.charAt(27);
                c28 = str.charAt(28);
                break;
            default:
                c20 = str.charAt(20);
                c21 = str.charAt(21);
                c22 = str.charAt(22);
                c23 = str.charAt(23);
                c24 = str.charAt(24);
                c25 = str.charAt(25);
                c26 = str.charAt(26);
                c27 = str.charAt(27);
                c28 = str.charAt(28);
                c29 = str.charAt(29);
                break;
        }

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1, S0, S1, S2, S3, S4, S5, S6, S7, S8;
        int zoneIdBegin;
        boolean isTimeZone = false, pm = false;
        if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':'
                && (c19 == '[' || c19 == 'Z' || c19 == '+' || c19 == '-' || c19 == ' ')
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 19;
        } else if (c4 == '-' && c7 == '-' && (c10 == ' ' && c11 == ' ') && c14 == ':' && c17 == ':' && len == 20) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c12;
            h1 = c13;

            i0 = c15;
            i1 = c16;

            s0 = c18;
            s1 = c19;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 20;
        } else if (len == 20 && c2 == ' ' && c6 == ' ' && c11 == ' ' && c14 == ':' && c17 == ':') {
            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = c0;
            d1 = c1;

            h0 = c12;
            h1 = c13;

            i0 = c15;
            i1 = c16;

            s0 = c18;
            s1 = c19;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 20;
            isTimeZone = false;
        } else if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 21 || c21 == '[' || c21 == '+' || c21 == '-' || c21 == 'Z')) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 21;
            isTimeZone = c21 == '|';
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T')
                && c13 == ':' && c16 == ':'
                && c19 == '.' && (len == 22 || c22 == '[' || c22 == '+' || c22 == '-' || c22 == 'Z')) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 22;
            isTimeZone = c22 == '|';
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T')
                && c13 == ':' && c16 == 'Z'
                && c17 == '[' && c21 == ']'
                && len == 22) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = '0';
            s1 = '0';

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            isTimeZone = true;
            zoneIdBegin = 17;
        } else if (len == 22
                && c3 == ' ' && c5 == ',' && c6 == ' ' && c11 == ' '
                && c13 == ':' && c16 == ':'
                && c19 == ' ' && (c20 == 'A' || c20 == 'P') && c21 == 'M'
        ) {
            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            int month = DateUtils.month(c0, c1, c2);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = '0';
            d1 = c4;

            h0 = '0';
            h1 = c12;
            pm = c20 == 'P';

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 22;
            isTimeZone = false;
        } else if (len == 23
                && c3 == ' ' && c5 == ',' && c6 == ' ' && c11 == ' '
                && c14 == ':' && c17 == ':' && c20 == ' '
                && (c21 == 'A' || c21 == 'P') && c22 == 'M'
        ) {
            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            int month = DateUtils.month(c0, c1, c2);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = '0';
            d1 = c4;

            h0 = c12;
            h1 = c13;
            pm = c21 == 'P';

            i0 = c15;
            i1 = c16;

            s0 = c18;
            s1 = c19;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 23;
            isTimeZone = false;
        } else if (len == 23
                && c3 == ' ' && c6 == ',' && c7 == ' ' && c12 == ' '
                && c14 == ':' && c17 == ':'
                && c20 == ' ' && (c21 == 'A' || c21 == 'P') && c22 == 'M'
        ) {
            y0 = c8;
            y1 = c9;
            y2 = c10;
            y3 = c11;

            int month = DateUtils.month(c0, c1, c2);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = c4;
            d1 = c5;

            h0 = '0';
            h1 = c13;
            pm = c21 == 'P';

            i0 = c15;
            i1 = c16;

            s0 = c18;
            s1 = c19;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 23;
            isTimeZone = false;
        } else if (len == 24
                && c3 == ' ' && c6 == ',' && c7 == ' ' && c12 == ' '
                && c15 == ':' && c18 == ':'
                && c21 == ' ' && (c22 == 'A' || c22 == 'P') && c23 == 'M'
        ) {
            y0 = c8;
            y1 = c9;
            y2 = c10;
            y3 = c11;

            int month = DateUtils.month(c0, c1, c2);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = c4;
            d1 = c5;

            h0 = c13;
            h1 = c14;
            pm = c22 == 'P';

            i0 = c16;
            i1 = c17;

            s0 = c19;
            s1 = c20;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 24;
            isTimeZone = false;
        } else if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 23 || c23 == '[' || c23 == '|' || c23 == '+' || c23 == '-' || c23 == 'Z')) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 23;
            isTimeZone = c23 == '|';
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T')
                && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 24 || c24 == '[' || c24 == '|' || c24 == '+' || c24 == '-' || c24 == 'Z')) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = c23;
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 24;
            isTimeZone = c24 == '|';
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T')
                && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 25 || c25 == '[' || c25 == '|' || c25 == '+' || c25 == '-' || c25 == 'Z')
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = c23;
            S4 = c24;
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 25;
            isTimeZone = c25 == '|';
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T')
                && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 26 || c26 == '[' || c26 == '|' || c26 == '+' || c26 == '-' || c26 == 'Z')
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = c23;
            S4 = c24;
            S5 = c25;
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 26;
            isTimeZone = c26 == '|';
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T')
                && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 27 || c27 == '[' || c27 == '|' || c27 == '+' || c27 == '-' || c27 == 'Z')
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = c23;
            S4 = c24;
            S5 = c25;
            S6 = c26;
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 27;
            isTimeZone = c27 == '|';
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 28 || c28 == '[' || c28 == '|' || c28 == '+' || c28 == '-' || c28 == 'Z')
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = c23;
            S4 = c24;
            S5 = c25;
            S6 = c26;
            S7 = c27;
            S8 = '0';
            zoneIdBegin = 28;
            isTimeZone = c28 == '|';
        } else if (len == 28 && c3 == ' ' && c7 == ' ' && c10 == ' ' && c13 == ':' && c16 == ':' && c19 == ' ' && c23 == ' ') {
            int month = DateUtils.month(c4, c5, c6);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            y0 = c24;
            y1 = c25;
            y2 = c26;
            y3 = c27;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 19;
            zoneIdStr = str.substring(20, 23);
            isTimeZone = false;
        } else if (len == 28 && c3 == ',' && c4 == ' ' && c6 == ' ' && c10 == ' ' && c15 == ' '
                && c18 == ':' && c21 == ':' && c24 == ' ') {
            // RFC 1123
            y0 = c11;
            y1 = c12;
            y2 = c13;
            y3 = c14;

            int month = DateUtils.month(c7, c8, c9);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = '0';
            d1 = c5;

            h0 = c16;
            h1 = c17;

            i0 = c19;
            i1 = c20;

            s0 = c22;
            s1 = c23;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 24;
            isTimeZone = true;
        } else if (len == 29
                && c3 == ',' && c4 == ' ' && c7 == ' ' && c11 == ' ' && c16 == ' '
                && c19 == ':' && c22 == ':' && c25 == ' '
        ) {
            // RFC 1123
            y0 = c12;
            y1 = c13;
            y2 = c14;
            y3 = c15;

            int month = DateUtils.month(c8, c9, c10);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                m0 = '0';
                m1 = '0';
            }

            d0 = c5;
            d1 = c6;

            h0 = c17;
            h1 = c18;

            i0 = c20;
            i1 = c21;

            s0 = c23;
            s1 = c24;

            S0 = '0';
            S1 = '0';
            S2 = '0';
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 25;
            isTimeZone = true;
        } else if (c4 == '-' && c7 == '-'
                && (c10 == ' ' || c10 == 'T')
                && c13 == ':' && c16 == ':' && c19 == '.'
                && (len == 29 || c29 == '[' || c29 == '|' || c29 == '+' || c29 == '-' || c29 == 'Z')
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;

            S0 = c20;
            S1 = c21;
            S2 = c22;
            S3 = c23;
            S4 = c24;
            S5 = c25;
            S6 = c26;
            S7 = c27;
            S8 = c28;
            zoneIdBegin = 29;
            isTimeZone = c29 == '|';
        } else if (len == 22 && (c17 == '+' || c17 == '-')) {
            // yyyyMMddHHmmssSSSZ
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c4;
            m1 = c5;

            d0 = c6;
            d1 = c7;

            h0 = c8;
            h1 = c9;

            i0 = c10;
            i1 = c11;

            s0 = c12;
            s1 = c13;

            S0 = c14;
            S1 = c15;
            S2 = c16;
            S3 = '0';
            S4 = '0';
            S5 = '0';
            S6 = '0';
            S7 = '0';
            S8 = '0';
            zoneIdBegin = 17;
            isTimeZone = false;
        } else if ((len == 32 && c6 == ',' && c7 == ' ' && c10 == '-' && c14 == '-' && c19 == ' ' && c22 == ':' && c25 == ':' && str.charAt(28) == ' ')
                || (len == 33 && c7 == ',' && c8 == ' ' && c11 == '-' && c15 == '-' && c20 == ' ' && c23 == ':' && c26 == ':' && str.charAt(29) == ' ')
                || (len == 34 && c8 == ',' && c9 == ' ' && c12 == '-' && c16 == '-' && c21 == ' ' && c24 == ':' && c27 == ':' && str.charAt(30) == ' ')
                || (len == 35 && c9 == ',' && c10 == ' ' && c13 == '-' && c17 == '-' && c22 == ' ' && c25 == ':' && c28 == ':' && str.charAt(31) == ' ')
        ) {
            return parseZonedDateTimeCookie(str);
        } else if (len == 34) {
            DateTimeFormatter formatter = DATE_TIME_FORMATTER_34;
            if (formatter == null) {
                formatter = DATE_TIME_FORMATTER_34 = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss O yyyy");
            }
            return ZonedDateTime.parse(str, formatter);
        } else if (len == 31 && str.charAt(3) == ',') {
            DateTimeFormatter formatter;
            formatter = DATE_TIME_FORMATTER_RFC_2822;
            if (formatter == null) {
                formatter = DATE_TIME_FORMATTER_RFC_2822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z");
            }
            return ZonedDateTime.parse(str, formatter);
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        if (pm) {
            int hourValue = DateUtils.hourAfterNoon(h0, h1);
            h0 = (char) (hourValue >> 16);
            h1 = (char) ((short) hourValue);
        }

        LocalDateTime ldt = localDateTime(
                y0, y1, y2, y3,
                m0, m1,
                d0, d1,
                h0, h1,
                i0, i1,
                s0, s1,
                S0, S1, S2, S3, S4, S5, S6, S7, S8
        );
        if (ldt == null) {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        ZoneId zoneId;
        if (isTimeZone) {
            String tzStr = str.substring(zoneIdBegin, len);
            switch (tzStr) {
                case "UTC":
                case "[UTC]":
                    zoneId = UTC;
                    break;
                default:
                    TimeZone timeZone = TimeZone.getTimeZone(tzStr);
                    zoneId = timeZone.toZoneId();
                    break;
            }
            // String tzStr = new String(chars, this.offset + zoneIdBegin, len - zoneIdBegin);
        } else if (zoneIdBegin == len) {
            zoneId = DEFAULT_ZONE_ID;
        } else {
            char first = str.charAt(zoneIdBegin);
            if (first == 'Z') {
                zoneId = UTC;
            } else {
                if (zoneIdStr == null) {
                    if (first == '+' || first == '-') {
                        zoneIdStr = str.substring(zoneIdBegin, len);
                        //                    zoneIdStr = new String(chars, zoneIdBegin, len - zoneIdBegin);
                    } else if (first == ' ') {
                        zoneIdStr = str.substring(zoneIdBegin + 1, len);
                    } else { // '[
                        if (zoneIdBegin < len) {
                            zoneIdStr = str.substring(zoneIdBegin + 1, len - 1);
                        } else {
                            zoneIdStr = null;
                        }
                    }
                }
                zoneId = getZoneId(zoneIdStr, defaultZoneId);
            }
        }

        return ZonedDateTime.ofLocal(ldt, zoneId, null);
    }

    private static ZonedDateTime parseZonedDateTimeCookie(String str) {
        if (str.endsWith(" CST")) {
            DateTimeFormatter formatter = DATE_TIME_FORMATTER_COOKIE_LOCAL;
            if (formatter == null) {
                formatter = DateTimeFormatter.ofPattern("EEEE, dd-MMM-yyyy HH:mm:ss");
                DATE_TIME_FORMATTER_COOKIE_LOCAL = formatter;
            }
            String strLocalDateTime = str.substring(0, str.length() - 4);
            LocalDateTime ldt = LocalDateTime.parse(strLocalDateTime, formatter);
            return ZonedDateTime.of(ldt, DateUtils.SHANGHAI_ZONE_ID);
        }

        DateTimeFormatter formatter = DATE_TIME_FORMATTER_COOKIE;
        if (formatter == null) {
            formatter = DateTimeFormatter.ofPattern("EEEE, dd-MMM-yyyy HH:mm:ss zzz");
            DATE_TIME_FORMATTER_COOKIE = formatter;
        }
        return ZonedDateTime.parse(str, formatter);
    }

    public static ZoneId getZoneId(String zoneIdStr, ZoneId defaultZoneId) {
        if (zoneIdStr == null) {
            return defaultZoneId != null ? defaultZoneId : DEFAULT_ZONE_ID;
        }

        ZoneId zoneId;
        int p0, p1;
        if ("000".equals(zoneIdStr)) {
            zoneId = ZoneOffset.UTC;
        } else if ("CST".equals(zoneIdStr)) {
            zoneId = SHANGHAI_ZONE_ID;
        } else if ((p0 = zoneIdStr.indexOf('[')) > 0 && (p1 = zoneIdStr.indexOf(']', p0)) > 0) {
            String str = zoneIdStr.substring(p0 + 1, p1);
            zoneId = ZoneId.of(str);
        } else {
            zoneId = ZoneId.of(zoneIdStr);
        }
        return zoneId;
    }

    public static long parseMillisYMDHMS19(String str, ZoneId zoneId) {
        if (str == null) {
            return 0;
        }

        char c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18;
        if (JVM_VERSION == 8) {
            char[] chars = JDKUtils.getCharArray(str);
            if (chars.length != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = chars[0];
            c1 = chars[1];
            c2 = chars[2];
            c3 = chars[3];
            c4 = chars[4];
            c5 = chars[5];
            c6 = chars[6];
            c7 = chars[7];
            c8 = chars[8];
            c9 = chars[9];
            c10 = chars[10];
            c11 = chars[11];
            c12 = chars[12];
            c13 = chars[13];
            c14 = chars[14];
            c15 = chars[15];
            c16 = chars[16];
            c17 = chars[17];
            c18 = chars[18];
        } else if (STRING_CODER != null
                && STRING_CODER.applyAsInt(str) == 0
                && STRING_VALUE != null
        ) {
            byte[] bytes = JDKUtils.STRING_VALUE.apply(str);
            if (bytes.length != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = (char) bytes[0];
            c1 = (char) bytes[1];
            c2 = (char) bytes[2];
            c3 = (char) bytes[3];
            c4 = (char) bytes[4];
            c5 = (char) bytes[5];
            c6 = (char) bytes[6];
            c7 = (char) bytes[7];
            c8 = (char) bytes[8];
            c9 = (char) bytes[9];
            c10 = (char) bytes[10];
            c11 = (char) bytes[11];
            c12 = (char) bytes[12];
            c13 = (char) bytes[13];
            c14 = (char) bytes[14];
            c15 = (char) bytes[15];
            c16 = (char) bytes[16];
            c17 = (char) bytes[17];
            c18 = (char) bytes[18];
        } else {
            if (str.length() != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = str.charAt(0);
            c1 = str.charAt(1);
            c2 = str.charAt(2);
            c3 = str.charAt(3);
            c4 = str.charAt(4);
            c5 = str.charAt(5);
            c6 = str.charAt(6);
            c7 = str.charAt(7);
            c8 = str.charAt(8);
            c9 = str.charAt(9);
            c10 = str.charAt(10);
            c11 = str.charAt(11);
            c12 = str.charAt(12);
            c13 = str.charAt(13);
            c14 = str.charAt(14);
            c15 = str.charAt(15);
            c16 = str.charAt(16);
            c17 = str.charAt(17);
            c18 = str.charAt(18);
        }

        if (c4 != '-' || c7 != '-' || c10 != ' ' || c13 != ':' || c16 != ':') {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        char y0 = c0;
        char y1 = c1;
        char y2 = c2;
        char y3 = c3;

        char m0 = c5;
        char m1 = c6;

        char d0 = c8;
        char d1 = c9;

        char h0 = c11;
        char h1 = c12;

        char i0 = c14;
        char i1 = c15;

        char s0 = c17;
        char s1 = c18;

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');

            if ((month == 0 && year != 0) || month > 12) {
                throw new DateTimeParseException("illegal input", str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');

            int max = 31;
            switch (month) {
                case 2:
                    boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                    max = leapYear ? 29 : 28;
                    break;
                case 4:
                case 6:
                case 9:
                case 11:
                    max = 30;
                    break;
            }

            if ((dom == 0 && year != 0) || dom > max) {
                throw new DateTimeParseException("illegal input", str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            year = 1970;
            month = 1;
            dom = 1;
        }

        long utcSeconds;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long total = (365 * year)
                    + ((year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400)
                    + ((367 * month - 362) / 12)
                    + (dom - 1);

            if (month > 2) {
                total--;
                boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                if (!leapYear) {
                    total--;
                }
            }

            long epochDay = total - DAYS_0000_TO_1970;
            utcSeconds = epochDay * 86400
                    + hour * 3600
                    + minute * 60
                    + second;
        }

        int zoneOffsetTotalSeconds;

        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }
        boolean shanghai = zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES;
        long SECONDS_1991_09_15_02 = 684900000; // utcMillis(1991, 9, 15, 2, 0, 0);
        if (shanghai && utcSeconds >= SECONDS_1991_09_15_02) {
            final int OFFSET_0800_TOTAL_SECONDS = 28800;
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (zoneId == ZoneOffset.UTC || "UTC".equals(zoneId.getId())) {
            zoneOffsetTotalSeconds = 0;
        } else {
            LocalDate localDate = LocalDate.of(year, month, dom);
            LocalTime localTime = LocalTime.of(hour, minute, second, 0);
            LocalDateTime ldt = LocalDateTime.of(localDate, localTime);
            ZoneOffset offset = zoneId.getRules().getOffset(ldt);
            zoneOffsetTotalSeconds = offset.getTotalSeconds();
        }

        return (utcSeconds - zoneOffsetTotalSeconds) * 1000L;
    }

    static long parseMillis19(
            String str,
            ZoneId zoneId,
            DateTimeFormatPattern pattern
    ) {
        if (str == null || "null".equals(str)) {
            return 0;
        }

        if (pattern.length != 19) {
            throw new UnsupportedOperationException();
        }

        char c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18;
        if (JVM_VERSION == 8) {
            char[] chars = JDKUtils.getCharArray(str);
            if (chars.length != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }
            c0 = chars[0];
            c1 = chars[1];
            c2 = chars[2];
            c3 = chars[3];
            c4 = chars[4];
            c5 = chars[5];
            c6 = chars[6];
            c7 = chars[7];
            c8 = chars[8];
            c9 = chars[9];
            c10 = chars[10];
            c11 = chars[11];
            c12 = chars[12];
            c13 = chars[13];
            c14 = chars[14];
            c15 = chars[15];
            c16 = chars[16];
            c17 = chars[17];
            c18 = chars[18];
        } else if (STRING_CODER != null && STRING_VALUE != null && STRING_CODER.applyAsInt(str) == 0) {
            byte[] bytes = JDKUtils.STRING_VALUE.apply(str);
            if (bytes.length != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = (char) bytes[0];
            c1 = (char) bytes[1];
            c2 = (char) bytes[2];
            c3 = (char) bytes[3];
            c4 = (char) bytes[4];
            c5 = (char) bytes[5];
            c6 = (char) bytes[6];
            c7 = (char) bytes[7];
            c8 = (char) bytes[8];
            c9 = (char) bytes[9];
            c10 = (char) bytes[10];
            c11 = (char) bytes[11];
            c12 = (char) bytes[12];
            c13 = (char) bytes[13];
            c14 = (char) bytes[14];
            c15 = (char) bytes[15];
            c16 = (char) bytes[16];
            c17 = (char) bytes[17];
            c18 = (char) bytes[18];
        } else {
            if (str.length() != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = str.charAt(0);
            c1 = str.charAt(1);
            c2 = str.charAt(2);
            c3 = str.charAt(3);
            c4 = str.charAt(4);
            c5 = str.charAt(5);
            c6 = str.charAt(6);
            c7 = str.charAt(7);
            c8 = str.charAt(8);
            c9 = str.charAt(9);
            c10 = str.charAt(10);
            c11 = str.charAt(11);
            c12 = str.charAt(12);
            c13 = str.charAt(13);
            c14 = str.charAt(14);
            c15 = str.charAt(15);
            c16 = str.charAt(16);
            c17 = str.charAt(17);
            c18 = str.charAt(18);
        }

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1;
        switch (pattern) {
            case DATE_TIME_FORMAT_19_DASH:
                if (c4 != '-' || c7 != '-' || c10 != ' ' || c13 != ':' || c16 != ':') {
                    throw new DateTimeParseException("illegal input", str, 0);
                }
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;

                m0 = c5;
                m1 = c6;

                d0 = c8;
                d1 = c9;

                h0 = c11;
                h1 = c12;

                i0 = c14;
                i1 = c15;

                s0 = c17;
                s1 = c18;
                break;
            case DATE_TIME_FORMAT_19_DASH_T:
                if (c4 != '-' || c7 != '-' || c10 != 'T' || c13 != ':' || c16 != ':') {
                    throw new DateTimeParseException("illegal input", str, 0);
                }
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;

                m0 = c5;
                m1 = c6;

                d0 = c8;
                d1 = c9;

                h0 = c11;
                h1 = c12;

                i0 = c14;
                i1 = c15;

                s0 = c17;
                s1 = c18;
                break;
            case DATE_TIME_FORMAT_19_SLASH:
                if (c4 != '/' || c7 != '/' || c10 != ' ' || c13 != ':' || c16 != ':') {
                    throw new DateTimeParseException("illegal input", str, 0);
                }
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;

                m0 = c5;
                m1 = c6;

                d0 = c8;
                d1 = c9;

                h0 = c11;
                h1 = c12;

                i0 = c14;
                i1 = c15;

                s0 = c17;
                s1 = c18;
                break;
            case DATE_TIME_FORMAT_19_DOT:
                if (c2 != '.' || c5 != '.' || c10 != ' ' || c13 != ':' || c16 != ':') {
                    throw new DateTimeParseException("illegal input", str, 0);
                }
                d0 = c0;
                d1 = c1;

                m0 = c3;
                m1 = c4;

                y0 = c6;
                y1 = c7;
                y2 = c8;
                y3 = c9;

                h0 = c11;
                h1 = c12;

                i0 = c14;
                i1 = c15;

                s0 = c17;
                s1 = c18;
                break;
            default:
                throw new DateTimeParseException("illegal input", str, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');

            if ((month == 0 && year != 0) || month > 12) {
                throw new DateTimeParseException("illegal input", str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');

            int max = 31;
            switch (month) {
                case 2:
                    boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                    max = leapYear ? 29 : 28;
                    break;
                case 4:
                case 6:
                case 9:
                case 11:
                    max = 30;
                    break;
            }

            if ((dom == 0 && year != 0) || dom > max) {
                throw new DateTimeParseException("illegal input", str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            year = 1970;
            month = 1;
            dom = 1;
        }

        long utcSeconds;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long total = (365 * year)
                    + ((year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400)
                    + ((367 * month - 362) / 12)
                    + (dom - 1);

            if (month > 2) {
                total--;
                boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                if (!leapYear) {
                    total--;
                }
            }

            long epochDay = total - DAYS_0000_TO_1970;
            utcSeconds = epochDay * 86400
                    + hour * 3600
                    + minute * 60
                    + second;
        }

        int zoneOffsetTotalSeconds;

        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }
        boolean shanghai = zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES;
        long SECONDS_1991_09_15_02 = 684900000; // utcMillis(1991, 9, 15, 2, 0, 0);
        if (shanghai && utcSeconds >= SECONDS_1991_09_15_02) {
            final int OFFSET_0800_TOTAL_SECONDS = 28800;
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (zoneId == ZoneOffset.UTC || "UTC".equals(zoneId.getId())) {
            zoneOffsetTotalSeconds = 0;
        } else {
            LocalDate localDate = LocalDate.of(year, month, dom);
            LocalTime localTime = LocalTime.of(hour, minute, second, 0);
            LocalDateTime ldt = LocalDateTime.of(localDate, localTime);
            ZoneOffset offset = zoneId.getRules().getOffset(ldt);
            zoneOffsetTotalSeconds = offset.getTotalSeconds();
        }

        return (utcSeconds - zoneOffsetTotalSeconds) * 1000L;
    }

    static long parseMillis10(
            String str,
            ZoneId zoneId,
            DateTimeFormatPattern pattern
    ) {
        if (str == null || "null".equals(str)) {
            return 0;
        }

        if (pattern.length != 10) {
            throw new UnsupportedOperationException();
        }

        char c0, c1, c2, c3, c4, c5, c6, c7, c8, c9;
        if (JVM_VERSION == 8) {
            char[] chars = JDKUtils.getCharArray(str);
            if (chars.length != 10) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }
            c0 = chars[0];
            c1 = chars[1];
            c2 = chars[2];
            c3 = chars[3];
            c4 = chars[4];
            c5 = chars[5];
            c6 = chars[6];
            c7 = chars[7];
            c8 = chars[8];
            c9 = chars[9];
        } else if (STRING_CODER != null && STRING_VALUE != null && STRING_CODER.applyAsInt(str) == 0) {
            byte[] bytes = JDKUtils.STRING_VALUE.apply(str);
            if (bytes.length != 10) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = (char) bytes[0];
            c1 = (char) bytes[1];
            c2 = (char) bytes[2];
            c3 = (char) bytes[3];
            c4 = (char) bytes[4];
            c5 = (char) bytes[5];
            c6 = (char) bytes[6];
            c7 = (char) bytes[7];
            c8 = (char) bytes[8];
            c9 = (char) bytes[9];
        } else {
            if (str.length() != 10) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = str.charAt(0);
            c1 = str.charAt(1);
            c2 = str.charAt(2);
            c3 = str.charAt(3);
            c4 = str.charAt(4);
            c5 = str.charAt(5);
            c6 = str.charAt(6);
            c7 = str.charAt(7);
            c8 = str.charAt(8);
            c9 = str.charAt(9);
        }

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1;
        switch (pattern) {
            case DATE_FORMAT_10_DASH:
                if (c4 != '-' || c7 != '-') {
                    throw new DateTimeParseException("illegal input", str, 0);
                }
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;

                m0 = c5;
                m1 = c6;

                d0 = c8;
                d1 = c9;
                break;
            case DATE_FORMAT_10_SLASH:
                if (c4 != '/' || c7 != '/') {
                    throw new DateTimeParseException("illegal input", str, 0);
                }
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;

                m0 = c5;
                m1 = c6;

                d0 = c8;
                d1 = c9;
                break;
            default:
                throw new DateTimeParseException("illegal input", str, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');

            if ((month == 0 && year != 0) || month > 12) {
                throw new DateTimeParseException("illegal input", str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');

            int max = 31;
            switch (month) {
                case 2:
                    boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                    max = leapYear ? 29 : 28;
                    break;
                case 4:
                case 6:
                case 9:
                case 11:
                    max = 30;
                    break;
            }

            if ((dom == 0 && year != 0) || dom > max) {
                throw new DateTimeParseException("illegal input", str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input", str, 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            year = 1970;
            month = 1;
            dom = 1;
        }

        long utcSeconds;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long total = (365 * year)
                    + ((year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400)
                    + ((367 * month - 362) / 12)
                    + (dom - 1);

            if (month > 2) {
                total--;
                boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                if (!leapYear) {
                    total--;
                }
            }

            long epochDay = total - DAYS_0000_TO_1970;
            utcSeconds = epochDay * 86400;
        }

        int zoneOffsetTotalSeconds;

        boolean shanghai = zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES;
        long SECONDS_1991_09_15_02 = 684900000; // utcMillis(1991, 9, 15, 2, 0, 0);
        if (shanghai && utcSeconds >= SECONDS_1991_09_15_02) {
            final int OFFSET_0800_TOTAL_SECONDS = 28800;
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (zoneId == ZoneOffset.UTC || "UTC".equals(zoneId.getId())) {
            zoneOffsetTotalSeconds = 0;
        } else {
            LocalDate localDate = LocalDate.of(year, month, dom);
            LocalDateTime ldt = LocalDateTime.of(localDate, LocalTime.MIN);
            ZoneOffset offset = zoneId.getRules().getOffset(ldt);
            zoneOffsetTotalSeconds = offset.getTotalSeconds();
        }

        return (utcSeconds - zoneOffsetTotalSeconds) * 1000L;
    }

    public static long parseMillis19(String str, ZoneId zoneId) {
        if (str == null) {
            throw new NullPointerException();
        }

        char c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18;
        if (JVM_VERSION == 8) {
            char[] chars = JDKUtils.getCharArray(str);
            if (chars.length != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = chars[0];
            c1 = chars[1];
            c2 = chars[2];
            c3 = chars[3];
            c4 = chars[4];
            c5 = chars[5];
            c6 = chars[6];
            c7 = chars[7];
            c8 = chars[8];
            c9 = chars[9];
            c10 = chars[10];
            c11 = chars[11];
            c12 = chars[12];
            c13 = chars[13];
            c14 = chars[14];
            c15 = chars[15];
            c16 = chars[16];
            c17 = chars[17];
            c18 = chars[18];
        } else if (STRING_CODER != null && STRING_VALUE != null && STRING_CODER.applyAsInt(str) == 0) {
            byte[] chars = JDKUtils.STRING_VALUE.apply(str);
            if (chars.length != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = (char) chars[0];
            c1 = (char) chars[1];
            c2 = (char) chars[2];
            c3 = (char) chars[3];
            c4 = (char) chars[4];
            c5 = (char) chars[5];
            c6 = (char) chars[6];
            c7 = (char) chars[7];
            c8 = (char) chars[8];
            c9 = (char) chars[9];
            c10 = (char) chars[10];
            c11 = (char) chars[11];
            c12 = (char) chars[12];
            c13 = (char) chars[13];
            c14 = (char) chars[14];
            c15 = (char) chars[15];
            c16 = (char) chars[16];
            c17 = (char) chars[17];
            c18 = (char) chars[18];
        } else {
            if (str.length() != 19) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            c0 = str.charAt(0);
            c1 = str.charAt(1);
            c2 = str.charAt(2);
            c3 = str.charAt(3);
            c4 = str.charAt(4);
            c5 = str.charAt(5);
            c6 = str.charAt(6);
            c7 = str.charAt(7);
            c8 = str.charAt(8);
            c9 = str.charAt(9);
            c10 = str.charAt(10);
            c11 = str.charAt(11);
            c12 = str.charAt(12);
            c13 = str.charAt(13);
            c14 = str.charAt(14);
            c15 = str.charAt(15);
            c16 = str.charAt(16);
            c17 = str.charAt(17);
            c18 = str.charAt(18);
        }

        char y0, y1, y2, y3, m0, m1, d0, d1, h0, h1, i0, i1, s0, s1;
        if (c4 == '-' && c7 == '-' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':') {
            // yyyy-MM-dd HH:mm:ss
            // yyyy-MM-dd'T'HH:mm:ss
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c4 == '/' && c7 == '/' && (c10 == ' ' || c10 == 'T') && c13 == ':' && c16 == ':') {
            // yyyy/MM/dd HH:mm:ss
            // yyyy/MM/dd'T'HH:mm:ss
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;

            m0 = c5;
            m1 = c6;

            d0 = c8;
            d1 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c2 == '/' && c5 == '/' && c10 == ' ' && c13 == ':' && c16 == ':') {
            // dd/MM/yyyy HH:mm:ss
            d0 = c0;
            d1 = c1;

            m0 = c3;
            m1 = c4;

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c2 == '.' && c5 == '.' && c10 == ' ' && c13 == ':' && c16 == ':') {
            // dd.MM.yyyy HH:mm:ss
            d0 = c0;
            d1 = c1;

            m0 = c3;
            m1 = c4;

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c1 == ' ' && c5 == ' ' && c10 == ' ' && c13 == ':' && c16 == ':') {
            // d MMM yyyy HH:mm:ss
            // 6 DEC 2020 12:13:14
            d0 = '0';
            d1 = c0;

            int month = DateUtils.month(c2, c3, c4);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;

            h0 = c11;
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c13 == ':' && c16 == ':') {
            // dd MMM yyyy H:mm:ss
            // 16 DEC 2020 2:13:14
            d0 = c0;
            d1 = c1;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            h0 = '0';
            h1 = c12;

            i0 = c14;
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c14 == ':' && c16 == ':') {
            // dd MMM yyyy HH:m:ss
            // 16 DEC 2020 12:3:14
            d0 = c0;
            d1 = c1;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            h0 = c12;
            h1 = c13;

            i0 = '0';
            i1 = c15;

            s0 = c17;
            s1 = c18;
        } else if (c2 == ' ' && c6 == ' ' && c11 == ' ' && c14 == ':' && c17 == ':') {
            // dd MMM yyyy HH:m:ss
            // 16 DEC 2020 12:3:14
            d0 = c0;
            d1 = c1;

            int month = DateUtils.month(c3, c4, c5);
            if (month > 0) {
                m0 = (char) ('0' + month / 10);
                m1 = (char) ('0' + (month % 10));
            } else {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }

            y0 = c7;
            y1 = c8;
            y2 = c9;
            y3 = c10;

            h0 = c12;
            h1 = c13;

            i0 = c15;
            i1 = c16;

            s0 = '0';
            s1 = c18;
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');

            if ((month == 0 && year != 0) || month > 12) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');

            int max = 31;
            switch (month) {
                case 2:
                    boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                    max = leapYear ? 29 : 28;
                    break;
                case 4:
                case 6:
                case 9:
                case 11:
                    max = 30;
                    break;
            }

            if ((dom == 0 && year != 0) || dom > max) {
                throw new DateTimeParseException("illegal input " + str, str, 0);
            }
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            throw new DateTimeParseException("illegal input " + str, str, 0);
        }

        if (year == 0 && month == 0 && dom == 0) {
            year = 1970;
            month = 1;
            dom = 1;
        }

        long utcSeconds;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long total = (365 * year)
                    + ((year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400)
                    + ((367 * month - 362) / 12)
                    + (dom - 1);

            if (month > 2) {
                total--;
                boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                if (!leapYear) {
                    total--;
                }
            }

            long epochDay = total - DAYS_0000_TO_1970;
            utcSeconds = epochDay * 86400
                    + hour * 3600
                    + minute * 60
                    + second;
        }

        int zoneOffsetTotalSeconds;

        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }
        boolean shanghai = zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES;
        long SECONDS_1991_09_15_02 = 684900000; // utcMillis(1991, 9, 15, 2, 0, 0);
        if (shanghai && utcSeconds >= SECONDS_1991_09_15_02) {
            final int OFFSET_0800_TOTAL_SECONDS = 28800;
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (zoneId == ZoneOffset.UTC || "UTC".equals(zoneId.getId())) {
            zoneOffsetTotalSeconds = 0;
        } else {
            LocalDate localDate = LocalDate.of(year, month, dom);
            LocalTime localTime = LocalTime.of(hour, minute, second, 0);
            LocalDateTime ldt = LocalDateTime.of(localDate, localTime);
            ZoneOffset offset = zoneId.getRules().getOffset(ldt);
            zoneOffsetTotalSeconds = offset.getTotalSeconds();
        }

        return (utcSeconds - zoneOffsetTotalSeconds) * 1000L;
    }

    public static LocalDateTime localDateTime(
            char y0,
            char y1,
            char y2,
            char y3,
            char m0,
            char m1,
            char d0,
            char d1,
            char h0,
            char h1,
            char i0,
            char i1,
            char s0,
            char s1
    ) {
        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            return null;
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            return null;
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            return null;
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            return null;
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            return null;
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            return null;
        }

        LocalDateTime ldt = LocalDateTime.of(year, month, dom, hour, minute, second, 0);
        return ldt;
    }

    public static LocalDateTime localDateTime(
            char y0,
            char y1,
            char y2,
            char y3,
            char m0,
            char m1,
            char d0,
            char d1,
            char h0,
            char h1,
            char i0,
            char i1,
            char s0,
            char s1,
            char S0,
            char S1,
            char S2,
            char S3,
            char S4,
            char S5,
            char S6,
            char S7,
            char S8) {
        int year;
        if (y0 >= '0' && y0 <= '9'
                && y1 >= '0' && y1 <= '9'
                && y2 >= '0' && y2 <= '9'
                && y3 >= '0' && y3 <= '9'
        ) {
            year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
        } else {
            return null;
        }

        int month;
        if (m0 >= '0' && m0 <= '9'
                && m1 >= '0' && m1 <= '9'
        ) {
            month = (m0 - '0') * 10 + (m1 - '0');
        } else {
            return null;
        }

        int dom;
        if (d0 >= '0' && d0 <= '9'
                && d1 >= '0' && d1 <= '9'
        ) {
            dom = (d0 - '0') * 10 + (d1 - '0');
        } else {
            return null;
        }

        int hour;
        if (h0 >= '0' && h0 <= '9'
                && h1 >= '0' && h1 <= '9'
        ) {
            hour = (h0 - '0') * 10 + (h1 - '0');
        } else {
            return null;
        }

        int minute;
        if (i0 >= '0' && i0 <= '9'
                && i1 >= '0' && i1 <= '9'
        ) {
            minute = (i0 - '0') * 10 + (i1 - '0');
        } else {
            return null;
        }

        int second;
        if (s0 >= '0' && s0 <= '9'
                && s1 >= '0' && s1 <= '9'
        ) {
            second = (s0 - '0') * 10 + (s1 - '0');
        } else {
            return null;
        }

        int nanos;
        if (S0 >= '0' && S0 <= '9'
                && S1 >= '0' && S1 <= '9'
                && S2 >= '0' && S2 <= '9'
                && S3 >= '0' && S3 <= '9'
                && S4 >= '0' && S4 <= '9'
                && S5 >= '0' && S5 <= '9'
                && S6 >= '0' && S6 <= '9'
                && S7 >= '0' && S7 <= '9'
                && S8 >= '0' && S8 <= '9'
        ) {
            nanos = (S0 - '0') * 1000_000_00
                    + (S1 - '0') * 1000_000_0
                    + (S2 - '0') * 1000_000
                    + (S3 - '0') * 1000_00
                    + (S4 - '0') * 1000_0
                    + (S5 - '0') * 1000
                    + (S6 - '0') * 100
                    + (S7 - '0') * 10
                    + (S8 - '0');
        } else {
            return null;
        }

        LocalDateTime ldt = LocalDateTime.of(year, month, dom, hour, minute, second, nanos);
        return ldt;
    }

    public static long millis(LocalDateTime ldt) {
        return millis(
                null,
                ldt.getYear(),
                ldt.getMonthValue(),
                ldt.getDayOfMonth(),
                ldt.getHour(),
                ldt.getMinute(),
                ldt.getSecond(),
                ldt.getNano()
        );
    }

    public static long millis(LocalDateTime ldt, ZoneId zoneId) {
        return millis(
                zoneId,
                ldt.getYear(),
                ldt.getMonthValue(),
                ldt.getDayOfMonth(),
                ldt.getHour(),
                ldt.getMinute(),
                ldt.getSecond(),
                ldt.getNano()
        );
    }

    public static long millis(
            ZoneId zoneId,
            int year,
            int month,
            int dom,
            int hour,
            int minute,
            int second,
            int nanoOfSecond
    ) {
        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }

        long utcSeconds;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long total = (365 * year)
                    + ((year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400)
                    + ((367 * month - 362) / 12)
                    + (dom - 1);

            if (month > 2) {
                total--;
                boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
                if (!leapYear) {
                    total--;
                }
            }

            long epochDay = total - DAYS_0000_TO_1970;
            utcSeconds = epochDay * 86400
                    + hour * 3600
                    + minute * 60
                    + second;
        }

        int zoneOffsetTotalSeconds;

        boolean shanghai = zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES;
        long SECONDS_1991_09_15_02 = 684900000; // utcMillis(1991, 9, 15, 2, 0, 0);
        if (shanghai && utcSeconds >= SECONDS_1991_09_15_02) {
            final int OFFSET_0800_TOTAL_SECONDS = 28800;
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (zoneId == ZoneOffset.UTC || "UTC".equals(zoneId.getId())) {
            zoneOffsetTotalSeconds = 0;
        } else {
            LocalDate localDate = LocalDate.of(year, month, dom);
            LocalTime localTime = LocalTime.of(hour, minute, second, nanoOfSecond);
            LocalDateTime ldt = LocalDateTime.of(localDate, localTime);
            ZoneOffset offset = zoneId.getRules().getOffset(ldt);
            zoneOffsetTotalSeconds = offset.getTotalSeconds();
        }

        long millis = (utcSeconds - zoneOffsetTotalSeconds) * 1000L;
        if (nanoOfSecond != 0) {
            millis += nanoOfSecond / 1_000_000;
        }
        return millis;
    }

    public static long utcSeconds(
            int year,
            int month,
            int dom,
            int hour,
            int minute,
            int second
    ) {
        final int DAYS_PER_CYCLE = 146097;
        final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

        long total = (365 * year)
                + ((year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400)
                + ((367 * month - 362) / 12)
                + (dom - 1);

        if (month > 2) {
            total--;
            boolean leapYear = (year & 3) == 0 && ((year % 100) != 0 || (year % 400) == 0);
            if (!leapYear) {
                total--;
            }
        }

        long epochDay = total - DAYS_0000_TO_1970;
        return epochDay * 86400
                + hour * 3600
                + minute * 60
                + second;
    }

    public static String formatYMDHMS19(Date date) {
        return formatYMDHMS19(date, DEFAULT_ZONE_ID);
    }

    public static String formatYMDHMS19(Date date, ZoneId zoneId) {
        if (date == null) {
            return null;
        }

        long timeMillis = date.getTime();

        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }

        final int SECONDS_PER_DAY = 60 * 60 * 24;

        long epochSecond = Math.floorDiv(timeMillis, 1000L);
        int offsetTotalSeconds;

        final long SECONDS_1991_09_15_02 = 684900000;
        boolean shanghai = zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES;
        if (shanghai && epochSecond > SECONDS_1991_09_15_02) {
            final int OFFSET_0800_TOTAL_SECONDS = 28800;
            offsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else {
            Instant instant = Instant.ofEpochMilli(timeMillis);
            offsetTotalSeconds = zoneId.getRules().getOffset(instant).getTotalSeconds();
        }

        long localSecond = epochSecond + offsetTotalSeconds;
        long localEpochDay = Math.floorDiv(localSecond, (long) SECONDS_PER_DAY);
        int secsOfDay = (int) Math.floorMod(localSecond, (long) SECONDS_PER_DAY);
        int year, month, dayOfMonth;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long zeroDay = localEpochDay + DAYS_0000_TO_1970;
            // find the march-based year
            zeroDay -= 60;  // adjust to 0000-03-01 so leap day is at end of four year cycle
            long adjust = 0;
            if (zeroDay < 0) {
                // adjust negative years to positive for calculation
                long adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
                adjust = adjustCycles * 400;
                zeroDay += -adjustCycles * DAYS_PER_CYCLE;
            }
            long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
            long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            if (doyEst < 0) {
                // fix estimate
                yearEst--;
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            }
            yearEst += adjust;  // reset any negative year
            int marchDoy0 = (int) doyEst;

            // convert march-based values back to january-based
            int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
            month = (marchMonth0 + 2) % 12 + 1;
            dayOfMonth = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
            yearEst += marchMonth0 / 10;

            // check year now we are certain it is correct
            if (yearEst < Year.MIN_VALUE || yearEst > Year.MAX_VALUE) {
                throw new DateTimeException("Invalid year " + yearEst);
            }

            year = (int) yearEst;
        }

        int hour, minute, second;
        {
            final int MINUTES_PER_HOUR = 60;
            final int SECONDS_PER_MINUTE = 60;
            final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;

            long secondOfDay = secsOfDay;
            if (secondOfDay < 0 || secondOfDay > 86399) {
                throw new DateTimeException("Invalid secondOfDay " + secondOfDay);
            }
            int hours = (int) (secondOfDay / SECONDS_PER_HOUR);
            secondOfDay -= hours * SECONDS_PER_HOUR;
            int minutes = (int) (secondOfDay / SECONDS_PER_MINUTE);
            secondOfDay -= minutes * SECONDS_PER_MINUTE;

            hour = hours;
            minute = minutes;
            second = (int) secondOfDay;
        }

        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';
        int h0 = hour / 10 + '0';
        int h1 = hour % 10 + '0';
        int i0 = minute / 10 + '0';
        int i1 = minute % 10 + '0';
        int s0 = second / 10 + '0';
        int s1 = second % 10 + '0';

        if (STRING_CREATOR_JDK11 != null) {
            byte[] bytes = new byte[19];
            bytes[0] = (byte) y0;
            bytes[1] = (byte) y1;
            bytes[2] = (byte) y2;
            bytes[3] = (byte) y3;
            bytes[4] = '-';
            bytes[5] = (byte) m0;
            bytes[6] = (byte) m1;
            bytes[7] = '-';
            bytes[8] = (byte) d0;
            bytes[9] = (byte) d1;
            bytes[10] = ' ';
            bytes[11] = (byte) h0;
            bytes[12] = (byte) h1;
            bytes[13] = ':';
            bytes[14] = (byte) i0;
            bytes[15] = (byte) i1;
            bytes[16] = ':';
            bytes[17] = (byte) s0;
            bytes[18] = (byte) s1;

            return STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        }

        char[] chars = new char[19];
        chars[0] = (char) y0;
        chars[1] = (char) y1;
        chars[2] = (char) y2;
        chars[3] = (char) y3;
        chars[4] = '-';
        chars[5] = (char) m0;
        chars[6] = (char) m1;
        chars[7] = '-';
        chars[8] = (char) d0;
        chars[9] = (char) d1;
        chars[10] = ' ';
        chars[11] = (char) h0;
        chars[12] = (char) h1;
        chars[13] = ':';
        chars[14] = (char) i0;
        chars[15] = (char) i1;
        chars[16] = ':';
        chars[17] = (char) s0;
        chars[18] = (char) s1;

        if (STRING_CREATOR_JDK8 != null) {
            return STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
        }

        return new String(chars);
    }

    public static String formatYMD8(Date date) {
        if (date == null) {
            return null;
        }

        return formatYMD8(date.getTime(), DEFAULT_ZONE_ID);
    }

    public static String formatYMD8(long timeMillis, ZoneId zoneId) {
        final int SECONDS_PER_DAY = 60 * 60 * 24;

        long epochSecond = Math.floorDiv(timeMillis, 1000L);
        int offsetTotalSeconds;

        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }
        if (zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES) {
            offsetTotalSeconds = getShanghaiZoneOffsetTotalSeconds(epochSecond);
        } else {
            Instant instant = Instant.ofEpochMilli(timeMillis);
            offsetTotalSeconds = zoneId.getRules().getOffset(instant).getTotalSeconds();
        }

        long localSecond = epochSecond + offsetTotalSeconds;
        long localEpochDay = Math.floorDiv(localSecond, (long) SECONDS_PER_DAY);

        int off = (int) (localEpochDay - LOCAL_EPOCH_DAY + 128);

        final String[] cache = CacheDate8.CACHE;
        if (off >= 0 && off < cache.length) {
            String str = cache[off];
            if (str != null) {
                return str;
            }
        }

        int year, month, dayOfMonth;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long zeroDay = localEpochDay + DAYS_0000_TO_1970;
            // find the march-based year
            zeroDay -= 60;  // adjust to 0000-03-01 so leap day is at end of four year cycle
            long adjust = 0;
            if (zeroDay < 0) {
                // adjust negative years to positive for calculation
                long adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
                adjust = adjustCycles * 400;
                zeroDay += -adjustCycles * DAYS_PER_CYCLE;
            }
            long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
            long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            if (doyEst < 0) {
                // fix estimate
                yearEst--;
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            }
            yearEst += adjust;  // reset any negative year
            int marchDoy0 = (int) doyEst;

            // convert march-based values back to january-based
            int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
            month = (marchMonth0 + 2) % 12 + 1;
            dayOfMonth = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
            yearEst += marchMonth0 / 10;

            // check year now we are certain it is correct
            if (yearEst < Year.MIN_VALUE || yearEst > Year.MAX_VALUE) {
                throw new DateTimeException("Invalid year " + yearEst);
            }

            year = (int) yearEst;
        }
        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';

        String str;
        if (STRING_CREATOR_JDK11 != null) {
            byte[] bytes = new byte[8];
            bytes[0] = (byte) y0;
            bytes[1] = (byte) y1;
            bytes[2] = (byte) y2;
            bytes[3] = (byte) y3;
            bytes[4] = (byte) m0;
            bytes[5] = (byte) m1;
            bytes[6] = (byte) d0;
            bytes[7] = (byte) d1;

            str = STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        } else {
            char[] chars = new char[8];
            chars[0] = (char) y0;
            chars[1] = (char) y1;
            chars[2] = (char) y2;
            chars[3] = (char) y3;
            chars[4] = (char) m0;
            chars[5] = (char) m1;
            chars[6] = (char) d0;
            chars[7] = (char) d1;

            if (STRING_CREATOR_JDK8 != null) {
                str = STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
            } else {
                str = new String(chars);
            }
        }

        if (off >= 0 && off < cache.length) {
            cache[off] = str;
        }

        return str;
    }

    public static String formatYMD10(LocalDate date) {
        if (date == null) {
            return null;
        }

        int year = date.getYear();
        int month = date.getMonthValue();
        int dayOfMonth = date.getDayOfMonth();

        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';

        final char separator = '-';

        String str;
        if (STRING_CREATOR_JDK11 != null) {
            byte[] bytes = new byte[10];
            bytes[0] = (byte) y0;
            bytes[1] = (byte) y1;
            bytes[2] = (byte) y2;
            bytes[3] = (byte) y3;
            bytes[4] = separator;
            bytes[5] = (byte) m0;
            bytes[6] = (byte) m1;
            bytes[7] = separator;
            bytes[8] = (byte) d0;
            bytes[9] = (byte) d1;

            str = STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        } else {
            char[] chars = new char[10];
            chars[0] = (char) y0;
            chars[1] = (char) y1;
            chars[2] = (char) y2;
            chars[3] = (char) y3;
            chars[4] = separator;
            chars[5] = (char) m0;
            chars[6] = (char) m1;
            chars[7] = separator;
            chars[8] = (char) d0;
            chars[9] = (char) d1;

            if (STRING_CREATOR_JDK8 != null) {
                str = STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
            } else {
                str = new String(chars);
            }
        }

        return str;
    }

    public static String formatYMD10(Date date) {
        if (date == null) {
            return null;
        }

        return formatYMD10(date.getTime(), DEFAULT_ZONE_ID);
    }

    public static String formatYMD8(LocalDate date) {
        if (date == null) {
            return null;
        }

        int year = date.getYear();
        int month = date.getMonthValue();
        int dayOfMonth = date.getDayOfMonth();

        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';

        String str;
        if (STRING_CREATOR_JDK11 != null) {
            byte[] bytes = new byte[8];
            bytes[0] = (byte) y0;
            bytes[1] = (byte) y1;
            bytes[2] = (byte) y2;
            bytes[3] = (byte) y3;
            bytes[4] = (byte) m0;
            bytes[5] = (byte) m1;
            bytes[6] = (byte) d0;
            bytes[7] = (byte) d1;

            str = STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        } else {
            char[] chars = new char[8];
            chars[0] = (char) y0;
            chars[1] = (char) y1;
            chars[2] = (char) y2;
            chars[3] = (char) y3;
            chars[4] = (char) m0;
            chars[5] = (char) m1;
            chars[6] = (char) d0;
            chars[7] = (char) d1;

            if (STRING_CREATOR_JDK8 != null) {
                str = STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
            } else {
                str = new String(chars);
            }
        }

        return str;
    }

    public static String formatYMD10(long timeMillis, ZoneId zoneId) {
        if (zoneId == null) {
            zoneId = DEFAULT_ZONE_ID;
        }

        final int SECONDS_PER_DAY = 60 * 60 * 24;

        long epochSecond = Math.floorDiv(timeMillis, 1000L);
        int offsetTotalSeconds;
        if (zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES) {
            offsetTotalSeconds = getShanghaiZoneOffsetTotalSeconds(epochSecond);
        } else {
            Instant instant = Instant.ofEpochMilli(timeMillis);
            offsetTotalSeconds = zoneId.getRules().getOffset(instant).getTotalSeconds();
        }

        long localSecond = epochSecond + offsetTotalSeconds;
        long localEpochDay = Math.floorDiv(localSecond, (long) SECONDS_PER_DAY);

        int off = (int) (localEpochDay - LOCAL_EPOCH_DAY + 128);
        final String[] cache = CacheDate10.CACHE;
        if (off >= 0 && off < cache.length) {
            String str = cache[off];
            if (str != null) {
                return str;
            }
        }

        int year, month, dayOfMonth;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long zeroDay = localEpochDay + DAYS_0000_TO_1970;
            // find the march-based year
            zeroDay -= 60;  // adjust to 0000-03-01 so leap day is at end of four year cycle
            long adjust = 0;
            if (zeroDay < 0) {
                // adjust negative years to positive for calculation
                long adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
                adjust = adjustCycles * 400;
                zeroDay += -adjustCycles * DAYS_PER_CYCLE;
            }
            long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
            long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            if (doyEst < 0) {
                // fix estimate
                yearEst--;
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            }
            yearEst += adjust;  // reset any negative year
            int marchDoy0 = (int) doyEst;

            // convert march-based values back to january-based
            int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
            month = (marchMonth0 + 2) % 12 + 1;
            dayOfMonth = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
            yearEst += marchMonth0 / 10;

            // check year now we are certain it is correct
            if (yearEst < Year.MIN_VALUE || yearEst > Year.MAX_VALUE) {
                throw new DateTimeException("Invalid year " + yearEst);
            }

            year = (int) yearEst;
        }
        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';

        final char separator = '-';

        String str;
        if (STRING_CREATOR_JDK11 != null) {
            byte[] bytes = new byte[10];
            bytes[0] = (byte) y0;
            bytes[1] = (byte) y1;
            bytes[2] = (byte) y2;
            bytes[3] = (byte) y3;
            bytes[4] = separator;
            bytes[5] = (byte) m0;
            bytes[6] = (byte) m1;
            bytes[7] = separator;
            bytes[8] = (byte) d0;
            bytes[9] = (byte) d1;

            str = STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        } else {
            char[] chars = new char[10];
            chars[0] = (char) y0;
            chars[1] = (char) y1;
            chars[2] = (char) y2;
            chars[3] = (char) y3;
            chars[4] = separator;
            chars[5] = (char) m0;
            chars[6] = (char) m1;
            chars[7] = separator;
            chars[8] = (char) d0;
            chars[9] = (char) d1;

            if (STRING_CREATOR_JDK8 != null) {
                str = STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
            } else {
                str = new String(chars);
            }
        }

        if (off >= 0 && off < cache.length) {
            cache[off] = str;
        }

        return str;
    }

    public static String format(Date date, String format) {
        if (date == null) {
            return null;
        }

        if (format == null) {
            return format(date);
        }

        switch (format) {
            case "yyyy-MM-dd HH:mm:ss": {
                return format(date.getTime(), DATE_TIME_FORMAT_19_DASH);
            }
            case "yyyy-MM-ddTHH:mm:ss":
            case "yyyy-MM-dd'T'HH:mm:ss": {
                return format(date.getTime(), DATE_TIME_FORMAT_19_DASH_T);
            }
            case "dd.MM.yyyy HH:mm:ss": {
                return format(date.getTime(), DATE_TIME_FORMAT_19_DOT);
            }
            case "yyyyMMdd":
                return formatYMD8(date.getTime(), DEFAULT_ZONE_ID);
            case "yyyy-MM-dd":
                return formatYMD10(date.getTime(), DEFAULT_ZONE_ID);
            case "yyyy/MM/dd":
                return format(date.getTime(), DATE_FORMAT_10_SLASH);
            case "dd.MM.yyyy":
                return format(date.getTime(), DATE_FORMAT_10_DOT);
            default:
                break;
        }

        long epochMilli = date.getTime();
        if (format == null) {
            toString(epochMilli, false, DEFAULT_ZONE_ID);
        }

        Instant instant = Instant.ofEpochMilli(epochMilli);
        ZoneId zoneId = DEFAULT_ZONE_ID;
        ZonedDateTime zdt = instant.atZone(zoneId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return formatter.format(zdt);
    }

    public static String formatYMDHMS19(ZonedDateTime zdt) {
        if (zdt == null) {
            return null;
        }

        int year = zdt.getYear();
        int month = zdt.getMonthValue();
        int dayOfMonth = zdt.getDayOfMonth();
        int hour = zdt.getHour();
        int minute = zdt.getMinute();
        int second = zdt.getSecond();
        return format(year, month, dayOfMonth, hour, minute, second, DATE_TIME_FORMAT_19_DASH);
    }

    public static String format(ZonedDateTime zdt, String format) {
        if (zdt == null) {
            return null;
        }

        int year = zdt.getYear();
        int month = zdt.getMonthValue();
        int dayOfMonth = zdt.getDayOfMonth();

        switch (format) {
            case "yyyy-MM-dd HH:mm:ss": {
                int hour = zdt.getHour();
                int minute = zdt.getMinute();
                int second = zdt.getSecond();
                return format(year, month, dayOfMonth, hour, minute, second, DATE_TIME_FORMAT_19_DASH);
            }
            case "yyyy-MM-ddTHH:mm:ss":
            case "yyyy-MM-dd'T'HH:mm:ss": {
                int hour = zdt.getHour();
                int minute = zdt.getMinute();
                int second = zdt.getSecond();
                return format(year, month, dayOfMonth, hour, minute, second, DATE_TIME_FORMAT_19_DASH_T);
            }
            case "yyyy-MM-dd":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_DASH);
            case "yyyy/MM/dd":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_SLASH);
            case "dd.MM.yyyy":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_DOT);
            default:
                break;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return formatter.format(zdt);
    }

    public static String formatYMDHMS19(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }

        int year = ldt.getYear();
        int month = ldt.getMonthValue();
        int dayOfMonth = ldt.getDayOfMonth();
        int hour = ldt.getHour();
        int minute = ldt.getMinute();
        int second = ldt.getSecond();
        return format(year, month, dayOfMonth, hour, minute, second, DATE_TIME_FORMAT_19_DASH);
    }

    public static String format(LocalDateTime ldt, String format) {
        if (ldt == null) {
            return null;
        }

        int year = ldt.getYear();
        int month = ldt.getMonthValue();
        int dayOfMonth = ldt.getDayOfMonth();

        switch (format) {
            case "yyyy-MM-dd HH:mm:ss": {
                int hour = ldt.getHour();
                int minute = ldt.getMinute();
                int second = ldt.getSecond();
                return format(year, month, dayOfMonth, hour, minute, second, DATE_TIME_FORMAT_19_DASH);
            }
            case "yyyy-MM-ddTHH:mm:ss":
            case "yyyy-MM-dd'T'HH:mm:ss": {
                int hour = ldt.getHour();
                int minute = ldt.getMinute();
                int second = ldt.getSecond();
                return format(year, month, dayOfMonth, hour, minute, second, DATE_TIME_FORMAT_19_DASH_T);
            }
            case "yyyy-MM-dd":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_DASH);
            case "yyyy/MM/dd":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_SLASH);
            case "dd.MM.yyyy":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_DOT);
            default:
                break;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return formatter.format(ldt);
    }

    public static String formatYMDHMS19(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }

        int year = localDate.getYear();
        int month = localDate.getMonthValue();
        int dayOfMonth = localDate.getDayOfMonth();
        return format(year, month, dayOfMonth, 0, 0, 0, DATE_TIME_FORMAT_19_DASH);
    }

    public static String format(LocalDate localDate, String format) {
        if (localDate == null) {
            return null;
        }

        int year = localDate.getYear();
        int month = localDate.getMonthValue();
        int dayOfMonth = localDate.getDayOfMonth();

        switch (format) {
            case "yyyy-MM-dd HH:mm:ss": {
                return format(year, month, dayOfMonth, 0, 0, 0, DATE_TIME_FORMAT_19_DASH);
            }
            case "yyyy-MM-ddTHH:mm:ss":
            case "yyyy-MM-dd'T'HH:mm:ss": {
                return format(year, month, dayOfMonth, 0, 0, 0, DATE_TIME_FORMAT_19_DASH_T);
            }
            case "yyyy-MM-dd":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_DASH);
            case "yyyy/MM/dd":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_SLASH);
            case "dd.MM.yyyy":
                return format(year, month, dayOfMonth, DATE_FORMAT_10_DOT);
            default:
                break;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return formatter.format(localDate);
    }

    public static String format(int year, int month, int dayOfMonth) {
        return format(year, month, dayOfMonth, DATE_FORMAT_10_DASH);
    }

    public static String format(
            int year,
            int month,
            int dayOfMonth,
            DateTimeFormatPattern pattern
    ) {
        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';

        if (STRING_CREATOR_JDK11 != null) {
            byte[] bytes = new byte[10];
            if (pattern == DATE_FORMAT_10_DOT) {
                bytes[0] = (byte) d0;
                bytes[1] = (byte) d1;
                bytes[2] = '.';
                bytes[3] = (byte) m0;
                bytes[4] = (byte) m1;
                bytes[5] = '.';
                bytes[6] = (byte) y0;
                bytes[7] = (byte) y1;
                bytes[8] = (byte) y2;
                bytes[9] = (byte) y3;
            } else {
                byte separator = (byte) (pattern == DATE_FORMAT_10_DASH ? '-' : '/');
                bytes[0] = (byte) y0;
                bytes[1] = (byte) y1;
                bytes[2] = (byte) y2;
                bytes[3] = (byte) y3;
                bytes[4] = separator;
                bytes[5] = (byte) m0;
                bytes[6] = (byte) m1;
                bytes[7] = separator;
                bytes[8] = (byte) d0;
                bytes[9] = (byte) d1;
            }

            return STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        }

        char[] chars = new char[10];
        if (pattern == DATE_FORMAT_10_DOT) {
            chars[0] = (char) d0;
            chars[1] = (char) d1;
            chars[2] = '.';
            chars[3] = (char) m0;
            chars[4] = (char) m1;
            chars[5] = '.';
            chars[6] = (char) y0;
            chars[7] = (char) y1;
            chars[8] = (char) y2;
            chars[9] = (char) y3;
        } else {
            char separator = (pattern == DATE_FORMAT_10_DASH ? '-' : '/');
            chars[0] = (char) y0;
            chars[1] = (char) y1;
            chars[2] = (char) y2;
            chars[3] = (char) y3;
            chars[4] = separator;
            chars[5] = (char) m0;
            chars[6] = (char) m1;
            chars[7] = separator;
            chars[8] = (char) d0;
            chars[9] = (char) d1;
        }

        if (STRING_CREATOR_JDK8 != null) {
            return STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
        }

        return new String(chars);
    }

    public static String format(long timeMillis) {
        return format(timeMillis, DateTimeFormatPattern.DATE_TIME_FORMAT_19_DASH);
    }

    public static String format(Date date) {
        if (date == null) {
            return null;
        }
        return format(date.getTime(), DateTimeFormatPattern.DATE_TIME_FORMAT_19_DASH);
    }

    public static String format(long timeMillis, DateTimeFormatPattern pattern) {
        ZoneId zoneId = DEFAULT_ZONE_ID;
        final int SECONDS_PER_DAY = 60 * 60 * 24;

        long epochSecond = Math.floorDiv(timeMillis, 1000L);
        int offsetTotalSeconds;
        if (zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES) {
            offsetTotalSeconds = getShanghaiZoneOffsetTotalSeconds(epochSecond);
        } else {
            Instant instant = Instant.ofEpochMilli(timeMillis);
            offsetTotalSeconds = zoneId.getRules().getOffset(instant).getTotalSeconds();
        }

        long localSecond = epochSecond + offsetTotalSeconds;
        long localEpochDay = Math.floorDiv(localSecond, (long) SECONDS_PER_DAY);
        int secsOfDay = (int) Math.floorMod(localSecond, (long) SECONDS_PER_DAY);
        int year, month, dayOfMonth;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long zeroDay = localEpochDay + DAYS_0000_TO_1970;
            // find the march-based year
            zeroDay -= 60;  // adjust to 0000-03-01 so leap day is at end of four year cycle
            long adjust = 0;
            if (zeroDay < 0) {
                // adjust negative years to positive for calculation
                long adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
                adjust = adjustCycles * 400;
                zeroDay += -adjustCycles * DAYS_PER_CYCLE;
            }
            long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
            long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            if (doyEst < 0) {
                // fix estimate
                yearEst--;
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            }
            yearEst += adjust;  // reset any negative year
            int marchDoy0 = (int) doyEst;

            // convert march-based values back to january-based
            int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
            month = (marchMonth0 + 2) % 12 + 1;
            dayOfMonth = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
            yearEst += marchMonth0 / 10;

            // check year now we are certain it is correct
            if (yearEst < Year.MIN_VALUE || yearEst > Year.MAX_VALUE) {
                throw new DateTimeException("Invalid year " + yearEst);
            }

            year = (int) yearEst;
        }

        if (pattern == DATE_FORMAT_10_DASH || pattern == DATE_FORMAT_10_SLASH || pattern == DATE_FORMAT_10_DOT) {
            return format(year, month, dayOfMonth, pattern);
        }

        int hour, minute, second;
        {
            final int MINUTES_PER_HOUR = 60;
            final int SECONDS_PER_MINUTE = 60;
            final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;

            long secondOfDay = secsOfDay;
            if (secondOfDay < 0 || secondOfDay > 86399) {
                throw new DateTimeException("Invalid secondOfDay " + secondOfDay);
            }
            int hours = (int) (secondOfDay / SECONDS_PER_HOUR);
            secondOfDay -= hours * SECONDS_PER_HOUR;
            int minutes = (int) (secondOfDay / SECONDS_PER_MINUTE);
            secondOfDay -= minutes * SECONDS_PER_MINUTE;

            hour = hours;
            minute = minutes;
            second = (int) secondOfDay;
        }

        return format(year, month, dayOfMonth, hour, minute, second, pattern);
    }

    public static String format(
            int year,
            int month,
            int dayOfMonth,
            int hour,
            int minute,
            int second
    ) {
        return format(year, month, dayOfMonth, hour, minute, second, DATE_TIME_FORMAT_19_DASH);
    }

    static String format(
            int year,
            int month,
            int dayOfMonth,
            int hour,
            int minute,
            int second,
            DateTimeFormatPattern pattern
    ) {
        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';
        int h0 = hour / 10 + '0';
        int h1 = hour % 10 + '0';
        int i0 = minute / 10 + '0';
        int i1 = minute % 10 + '0';
        int s0 = second / 10 + '0';
        int s1 = second % 10 + '0';

        if (STRING_CREATOR_JDK11 != null) {
            byte[] bytes = new byte[19];
            if (pattern == DATE_TIME_FORMAT_19_DOT) {
                bytes[0] = (byte) d0;
                bytes[1] = (byte) d1;
                bytes[2] = '.';
                bytes[3] = (byte) m0;
                bytes[4] = (byte) m1;
                bytes[5] = '.';
                bytes[6] = (byte) y0;
                bytes[7] = (byte) y1;
                bytes[8] = (byte) y2;
                bytes[9] = (byte) y3;
                bytes[10] = (byte) ' ';
            } else {
                char separator = pattern == DATE_TIME_FORMAT_19_DASH ? ' ' : 'T';
                char dateSeparator = pattern == DATE_TIME_FORMAT_19_SLASH ? '/' : '-';
                bytes[0] = (byte) y0;
                bytes[1] = (byte) y1;
                bytes[2] = (byte) y2;
                bytes[3] = (byte) y3;
                bytes[4] = (byte) dateSeparator;
                bytes[5] = (byte) m0;
                bytes[6] = (byte) m1;
                bytes[7] = (byte) dateSeparator;
                bytes[8] = (byte) d0;
                bytes[9] = (byte) d1;
                bytes[10] = (byte) separator;
            }
            bytes[11] = (byte) h0;
            bytes[12] = (byte) h1;
            bytes[13] = ':';
            bytes[14] = (byte) i0;
            bytes[15] = (byte) i1;
            bytes[16] = ':';
            bytes[17] = (byte) s0;
            bytes[18] = (byte) s1;

            return STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        }

        char[] chars = new char[19];
        if (pattern == DATE_TIME_FORMAT_19_DOT) {
            chars[0] = (char) d0;
            chars[1] = (char) d1;
            chars[2] = '.';
            chars[3] = (char) m0;
            chars[4] = (char) m1;
            chars[5] = '.';
            chars[6] = (char) y0;
            chars[7] = (char) y1;
            chars[8] = (char) y2;
            chars[9] = (char) y3;
            chars[10] = ' ';
        } else {
            char separator = pattern == DATE_TIME_FORMAT_19_DASH ? ' ' : 'T';
            char dateSeparator = pattern == DATE_TIME_FORMAT_19_SLASH ? '/' : '-';
            chars[0] = (char) y0;
            chars[1] = (char) y1;
            chars[2] = (char) y2;
            chars[3] = (char) y3;
            chars[4] = dateSeparator;
            chars[5] = (char) m0;
            chars[6] = (char) m1;
            chars[7] = dateSeparator;
            chars[8] = (char) d0;
            chars[9] = (char) d1;
            chars[10] = separator;
        }
        chars[11] = (char) h0;
        chars[12] = (char) h1;
        chars[13] = ':';
        chars[14] = (char) i0;
        chars[15] = (char) i1;
        chars[16] = ':';
        chars[17] = (char) s0;
        chars[18] = (char) s1;

        if (STRING_CREATOR_JDK8 != null) {
            return STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
        }

        return new String(chars);
    }

    public static String toString(Date date) {
        return toString(date.getTime(), false, DEFAULT_ZONE_ID);
    }

    public static String toString(long timeMillis, boolean timeZone, ZoneId zoneId) {
        final int SECONDS_PER_DAY = 60 * 60 * 24;

        long epochSecond = Math.floorDiv(timeMillis, 1000L);
        int offsetTotalSeconds;
        if (zoneId == SHANGHAI_ZONE_ID || zoneId.getRules() == SHANGHAI_ZONE_RULES) {
            offsetTotalSeconds = getShanghaiZoneOffsetTotalSeconds(epochSecond);
        } else {
            Instant instant = Instant.ofEpochMilli(timeMillis);
            offsetTotalSeconds = zoneId.getRules().getOffset(instant).getTotalSeconds();
        }

        long localSecond = epochSecond + offsetTotalSeconds;
        long localEpochDay = Math.floorDiv(localSecond, (long) SECONDS_PER_DAY);
        int secsOfDay = (int) Math.floorMod(localSecond, (long) SECONDS_PER_DAY);
        int year, month, dayOfMonth;
        {
            final int DAYS_PER_CYCLE = 146097;
            final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

            long zeroDay = localEpochDay + DAYS_0000_TO_1970;
            // find the march-based year
            zeroDay -= 60;  // adjust to 0000-03-01 so leap day is at end of four year cycle
            long adjust = 0;
            if (zeroDay < 0) {
                // adjust negative years to positive for calculation
                long adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
                adjust = adjustCycles * 400;
                zeroDay += -adjustCycles * DAYS_PER_CYCLE;
            }
            long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
            long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            if (doyEst < 0) {
                // fix estimate
                yearEst--;
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
            }
            yearEst += adjust;  // reset any negative year
            int marchDoy0 = (int) doyEst;

            // convert march-based values back to january-based
            int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
            month = (marchMonth0 + 2) % 12 + 1;
            dayOfMonth = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
            yearEst += marchMonth0 / 10;

            // check year now we are certain it is correct
            if (yearEst < Year.MIN_VALUE || yearEst > Year.MAX_VALUE) {
                throw new DateTimeException("Invalid year " + yearEst);
            }

            year = (int) yearEst;
        }

        int hour, minute, second;
        {
            final int MINUTES_PER_HOUR = 60;
            final int SECONDS_PER_MINUTE = 60;
            final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;

            long secondOfDay = secsOfDay;
            if (secondOfDay < 0 || secondOfDay > 86399) {
                throw new DateTimeException("Invalid secondOfDay " + secondOfDay);
            }
            int hours = (int) (secondOfDay / SECONDS_PER_HOUR);
            secondOfDay -= hours * SECONDS_PER_HOUR;
            int minutes = (int) (secondOfDay / SECONDS_PER_MINUTE);
            secondOfDay -= minutes * SECONDS_PER_MINUTE;

            hour = hours;
            minute = minutes;
            second = (int) secondOfDay;
        }

        int millis = (int) Math.floorMod(timeMillis, 1000L);

        int millislen;
        if (millis == 0) {
            millislen = 0;
        } else if (millis < 10) {
            millislen = 4;
        } else {
            if (millis % 100 == 0) {
                millislen = 2;
            } else if (millis % 10 == 0) {
                millislen = 3;
            } else {
                millislen = 4;
            }
        }

        int zonelen;
        if (timeZone) {
            zonelen = offsetTotalSeconds == 0 ? 1 : 6;
        } else {
            zonelen = 0;
        }

        int len = 19 + millislen + zonelen;
        int y0 = year / 1000 + '0';
        int y1 = (year / 100) % 10 + '0';
        int y2 = (year / 10) % 10 + '0';
        int y3 = year % 10 + '0';
        int m0 = month / 10 + '0';
        int m1 = month % 10 + '0';
        int d0 = dayOfMonth / 10 + '0';
        int d1 = dayOfMonth % 10 + '0';
        int h0 = hour / 10 + '0';
        int h1 = hour % 10 + '0';
        int i0 = minute / 10 + '0';
        int i1 = minute % 10 + '0';
        int s0 = second / 10 + '0';
        int s1 = second % 10 + '0';

        if (STRING_CREATOR_JDK8 != null) {
            char[] chars = new char[len];
            chars[0] = (char) y0;
            chars[1] = (char) y1;
            chars[2] = (char) y2;
            chars[3] = (char) y3;
            chars[4] = '-';
            chars[5] = (char) m0;
            chars[6] = (char) m1;
            chars[7] = '-';
            chars[8] = (char) d0;
            chars[9] = (char) d1;
            chars[10] = ' ';
            chars[11] = (char) h0;
            chars[12] = (char) h1;
            chars[13] = ':';
            chars[14] = (char) i0;
            chars[15] = (char) i1;
            chars[16] = ':';
            chars[17] = (char) s0;
            chars[18] = (char) s1;
            if (millislen > 0) {
                chars[19] = '.';
                for (int i = 20; i < len; ++i) {
                    chars[i] = '0';
                }
                if (millis < 10) {
                    IOUtils.getChars(millis, 19 + millislen, chars);
                } else {
                    if (millis % 100 == 0) {
                        IOUtils.getChars(millis / 100, 19 + millislen, chars);
                    } else if (millis % 10 == 0) {
                        IOUtils.getChars(millis / 10, 19 + millislen, chars);
                    } else {
                        IOUtils.getChars(millis, 19 + millislen, chars);
                    }
                }
            }
            if (timeZone) {
                int timeZoneOffset = offsetTotalSeconds / 3600;
                if (offsetTotalSeconds == 0) {
                    chars[19 + millislen] = 'Z';
                } else {
                    int offsetAbs = Math.abs(timeZoneOffset);

                    if (timeZoneOffset >= 0) {
                        chars[19 + millislen] = '+';
                    } else {
                        chars[19 + millislen] = '-';
                    }
                    chars[19 + millislen + 1] = '0';
                    IOUtils.getChars(offsetAbs, 19 + millislen + 3, chars);
                    chars[19 + millislen + 3] = ':';
                    chars[19 + millislen + 4] = '0';
                    int offsetMinutes = (offsetTotalSeconds - timeZoneOffset * 3600) / 60;
                    if (offsetMinutes < 0) {
                        offsetMinutes = -offsetMinutes;
                    }
                    IOUtils.getChars(offsetMinutes, 19 + millislen + zonelen, chars);
                }
            }

            return STRING_CREATOR_JDK8.apply(chars, Boolean.TRUE);
        }

        byte[] bytes = new byte[len];
        bytes[0] = (byte) y0;
        bytes[1] = (byte) y1;
        bytes[2] = (byte) y2;
        bytes[3] = (byte) y3;
        bytes[4] = '-';
        bytes[5] = (byte) m0;
        bytes[6] = (byte) m1;
        bytes[7] = '-';
        bytes[8] = (byte) d0;
        bytes[9] = (byte) d1;
        bytes[10] = ' ';
        bytes[11] = (byte) h0;
        bytes[12] = (byte) h1;
        bytes[13] = ':';
        bytes[14] = (byte) i0;
        bytes[15] = (byte) i1;
        bytes[16] = ':';
        bytes[17] = (byte) s0;
        bytes[18] = (byte) s1;
        if (millislen > 0) {
            bytes[19] = '.';
            for (int i = 20; i < len; ++i) {
                bytes[i] = '0';
            }
            if (millis < 10) {
                IOUtils.getChars(millis, 19 + millislen, bytes);
            } else {
                if (millis % 100 == 0) {
                    IOUtils.getChars(millis / 100, 19 + millislen, bytes);
                } else if (millis % 10 == 0) {
                    IOUtils.getChars(millis / 10, 19 + millislen, bytes);
                } else {
                    IOUtils.getChars(millis, 19 + millislen, bytes);
                }
            }
        }
        if (timeZone) {
            int timeZoneOffset = offsetTotalSeconds / 3600;
            if (offsetTotalSeconds == 0) {
                bytes[19 + millislen] = 'Z';
            } else {
                int offsetAbs = Math.abs(timeZoneOffset);

                if (timeZoneOffset >= 0) {
                    bytes[19 + millislen] = '+';
                } else {
                    bytes[19 + millislen] = '-';
                }
                bytes[19 + millislen + 1] = '0';
                IOUtils.getChars(offsetAbs, 19 + millislen + 3, bytes);
                bytes[19 + millislen + 3] = ':';
                bytes[19 + millislen + 4] = '0';
                int offsetMinutes = (offsetTotalSeconds - timeZoneOffset * 3600) / 60;
                if (offsetMinutes < 0) {
                    offsetMinutes = -offsetMinutes;
                }
                IOUtils.getChars(offsetMinutes, 19 + millislen + zonelen, bytes);
            }
        }

        if (STRING_CREATOR_JDK11 != null) {
            return STRING_CREATOR_JDK11.apply(bytes, LATIN1);
        }

        return new String(bytes, 0, bytes.length, StandardCharsets.ISO_8859_1);
    }

    public static int month(char c0, char c1, char c2) {
        switch (c0) {
            case 'J':
                // Jan
                if (c1 == 'a' && c2 == 'n') {
                    return 1;
                }
                if (c1 == 'u') {
                    if (c2 == 'n') {
                        return 6;
                    }
                    if (c2 == 'l') {
                        return 7;
                    }
                }
                break;
            case 'F':
                if (c1 == 'e' && c2 == 'b') {
                    return 2;
                }
                break;
            case 'M':
                if (c1 == 'a') {
                    if (c2 == 'r') {
                        return 3;
                    }
                    if (c2 == 'y') {
                        return 5;
                    }
                }
                break;
            case 'A':
                if (c1 == 'p' && c2 == 'r') {
                    return 4;
                }
                if (c1 == 'u' && c2 == 'g') {
                    return 8;
                }
                break;
            case 'S':
                if (c1 == 'e' && c2 == 'p') {
                    return 9;
                }
                break;
            case 'O':
                if (c1 == 'c' && c2 == 't') {
                    return 10;
                }
                break;
            case 'N':
                if (c1 == 'o' && c2 == 'v') {
                    return 11;
                }
                break;
            case 'D':
                if (c1 == 'e' && c2 == 'c') {
                    return 12;
                }
                break;
            default:
                break;
        }

        return 0;
    }

    public static int hourAfterNoon(char h0, char h1) {
        if (h0 == '0') {
            switch (h1) {
                case '0':
                    h0 = '1';
                    h1 = '2';
                    break;
                case '1':
                    h0 = '1';
                    h1 = '3';
                    break;
                case '2':
                    h0 = '1';
                    h1 = '4';
                    break;
                case '3':
                    h0 = '1';
                    h1 = '5';
                    break;
                case '4':
                    h0 = '1';
                    h1 = '6';
                    break;
                case '5':
                    h0 = '1';
                    h1 = '7';
                    break;
                case '6':
                    h0 = '1';
                    h1 = '8';
                    break;
                case '7':
                    h0 = '1';
                    h1 = '9';
                    break;
                case '8':
                    h0 = '2';
                    h1 = '0';
                    break;
                case '9':
                    h0 = '2';
                    h1 = '1';
                    break;
                default:
                    break;
            }
        } else if (h0 == '1') {
            switch (h1) {
                case '0':
                    h0 = '2';
                    h1 = '2';
                    break;
                case '1':
                    h0 = '2';
                    h1 = '3';
                    break;
                case '2':
                    h0 = '2';
                    h1 = '4';
                    break;
                default:
                    break;
            }
        }

        return h0 << 16 | h1;
    }

    public static int getShanghaiZoneOffsetTotalSeconds(long seconds) {
        long SECONDS_1991_09_15_02 = 684900000; // utcMillis(1991, 9, 15, 2, 0, 0);
        long SECONDS_1991_04_14_03 = 671598000; // utcMillis(1991, 4, 14, 3, 0, 0);
        long SECONDS_1990_09_16_02 = 653450400; // utcMillis(1990, 9, 16, 2, 0, 0);
        long SECONDS_1990_04_15_03 = 640148400; // utcMillis(1990, 4, 15, 3, 0, 0);
        long SECONDS_1989_09_17_02 = 622000800; // utcMillis(1989, 9, 17, 2, 0, 0);
        long SECONDS_1989_04_16_03 = 608698800; // utcMillis(1989, 4, 16, 3, 0, 0);
        long SECONDS_1988_09_11_02 = 589946400; // utcMillis(1988, 9, 11, 2, 0, 0);
        long SECONDS_1988_04_17_03 = 577249200; // utcMillis(1988, 4, 17, 3, 0, 0);
        long SECONDS_1987_09_13_02 = 558496800; // utcMillis(1987, 9, 13, 2, 0, 0);
        long SECONDS_1987_04_12_03 = 545194800; // utcMillis(1987, 4, 12, 3, 0, 0);
        long SECONDS_1986_09_14_02 = 527047200; // utcMillis(1986, 9, 14, 2, 0, 0);
        long SECONDS_1986_05_04_03 = 515559600; // utcMillis(1986, 5, 4, 3, 0, 0);
        long SECONDS_1949_05_28_00 = -649987200; // utcMillis(1949, 5, 28, 0, 0, 0);
        long SECONDS_1949_05_01_01 = -652316400; // utcMillis(1949, 5, 1, 1, 0, 0);
        long SECONDS_1948_10_01_00 = -670636800; // utcMillis(1948, 10, 1, 0, 0, 0);
        long SECONDS_1948_05_01_01 = -683852400; // utcMillis(1948, 5, 1, 1, 0, 0);
        long SECONDS_1947_11_01_00 = -699580800; // utcMillis(1947, 11, 1, 0, 0, 0);
        long SECONDS_1947_04_15_01 = -716857200; // utcMillis(1947, 4, 15, 1, 0, 0);
        long SECONDS_1946_10_01_00 = -733795200; // utcMillis(1946, 10, 1, 0, 0, 0);
        long SECONDS_1946_05_15_01 = -745801200; // utcMillis(1946, 5, 15, 1, 0, 0);
        long SECONDS_1945_09_02_00 = -767836800; // utcMillis(1945, 9, 2, 0, 0, 0);
        long SECONDS_1942_01_31_01 = -881017200; // utcMillis(1942, 1, 31, 1, 0, 0);
        long SECONDS_1941_11_02_00 = -888796800; // utcMillis(1941, 11, 2, 0, 0, 0);
        long SECONDS_1941_03_15_01 = -908838000; // utcMillis(1941, 3, 15, 1, 0, 0);
        long SECONDS_1940_10_13_00 = -922060800; // utcMillis(1940, 10, 13, 0, 0, 0);
        long SECONDS_1940_06_01_01 = -933634800L; //utcMillis(1940, 6, 1, 1, 0, 0);
        long SECONDS_1919_10_01_00 = -1585872000L; // utcMillis(1919, 10, 1, 0, 0, 0);
        long SECONDS_1919_04_13_01 = -1600642800L; // utcMillis(1919, 4, 13, 1, 0, 0);
        long SECONDS_1901_01_01_00 = -2177452800L; // utcMillis(1901, 1, 1, 0, 0, 0);

        final int OFFSET_0900_TOTAL_SECONDS = 32400;
        final int OFFSET_0800_TOTAL_SECONDS = 28800;
        final int OFFSET_0543_TOTAL_SECONDS = 29143;

        int zoneOffsetTotalSeconds;
        if (seconds >= SECONDS_1991_09_15_02) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1991_04_14_03) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1990_09_16_02) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1990_04_15_03) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1989_09_17_02) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1989_04_16_03) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1988_09_11_02) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1988_04_17_03) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1987_09_13_02) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1987_04_12_03) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1986_09_14_02) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1986_05_04_03) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1949_05_28_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1949_05_01_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1948_10_01_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1948_05_01_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1947_11_01_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1947_04_15_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1946_10_01_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1946_05_15_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1945_09_02_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1942_01_31_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1941_11_02_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1941_03_15_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1940_10_13_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1940_06_01_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1919_10_01_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1919_04_13_01) {
            zoneOffsetTotalSeconds = OFFSET_0900_TOTAL_SECONDS;
        } else if (seconds >= SECONDS_1901_01_01_00) {
            zoneOffsetTotalSeconds = OFFSET_0800_TOTAL_SECONDS;
        } else {
            zoneOffsetTotalSeconds = OFFSET_0543_TOTAL_SECONDS;
        }

        return zoneOffsetTotalSeconds;
    }

    public enum DateTimeFormatPattern {
        DATE_FORMAT_10_DASH("yyyy-MM-dd", 10),
        DATE_FORMAT_10_SLASH("yyyy/MM/dd", 10),
        DATE_FORMAT_10_DOT("dd.MM.yyyy", 10),
        DATE_TIME_FORMAT_19_DASH("yyyy-MM-dd HH:mm:ss", 19),
        DATE_TIME_FORMAT_19_DASH_T("yyyy-MM-dd'T'HH:mm:ss", 19),
        DATE_TIME_FORMAT_19_SLASH("yyyy/MM/dd HH:mm:ss", 19),
        DATE_TIME_FORMAT_19_DOT("dd.MM.yyyy HH:mm:ss", 19);

        public final String pattern;
        public final int length;

        DateTimeFormatPattern(String pattern, int length) {
            this.pattern = pattern;
            this.length = length;
        }
    }
}
