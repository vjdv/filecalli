package net.vjdv.filecalli.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public interface Utils {

    static String toRFC7231(long time) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(time).atOffset(ZoneOffset.UTC));
    }

}
