package com.cuvar.app;

import java.util.Locale;

/** Svakodnevni period po lokalnom vremenu telefona; kraj nije uključen. */
final class DailySchedule {
    static boolean contains(int start, int end, int minute) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static String label(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
}
