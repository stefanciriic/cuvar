package com.cuvar.app.rules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Uklanjanje iz pravila ne važi odmah: zahtev se potvrđuje tek sutradan od 08:00 do 22:00 (početak noćne
 * blokade). Nepotvrđen zahtev posle toga propada i pravilo ostaje.
 */
public final class RemovalWindow {
    public static final int ASK_HOUR = 8;
    public static final int ASK_END_HOUR = DailyCode.LOCK_HOUR;

    public static final int WAITING = 0, ASK = 1, EXPIRED = 2;

    private RemovalWindow() {
    }

    /** Stanje zahteva iz dana pravila requestDay ("yyyyMMdd", menja se u 06:00) u trenutku now. */
    public static int state(String requestDay, LocalDateTime now) {
        LocalDate day;
        try {
            day = LocalDate.parse(requestDay, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (RuntimeException e) {
            return EXPIRED;
        }
        LocalDateTime start = day.plusDays(1).atTime(ASK_HOUR, 0);
        LocalDateTime end = day.plusDays(1).atTime(ASK_END_HOUR, 0);
        if (now.isBefore(start)) return WAITING;
        return now.isBefore(end) ? ASK : EXPIRED;
    }

    /** Da li pitanje stiže danas (zahtev posle ponoći, a pre 06:00), a ne sutra po kalendaru. */
    public static boolean asksToday(String requestDay, LocalDate today) {
        try {
            return LocalDate.parse(requestDay, DateTimeFormatter.BASIC_ISO_DATE).plusDays(1).equals(today);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
