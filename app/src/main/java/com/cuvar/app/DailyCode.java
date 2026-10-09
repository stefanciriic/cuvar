package com.cuvar.app;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Dnevna šifra od 6 cifara. Računa se iz tajnog ključa (nasumičnog, samo na ovom telefonu) i dana,
 * bez interneta. Nova šifra kreće svakog dana u 17:00 i važi do 17:00 sledećeg dana.
 */
final class DailyCode {
    static final int CHANGE_HOUR = 17;
    /** Od ovog sata šifra više ne važi i počinje noćna blokada. */
    static final int LOCK_HOUR = 22;
    /** Noćna blokada traje do ovog sata ujutru. */
    static final int NIGHT_END_HOUR = 6;

    private DailyCode() {
    }

    /** Dan šifre ("yyyyMMdd"): pre 17:00 još važi jučerašnja. */
    static String dayKey(long millis, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone, Locale.US);
        c.setTimeInMillis(millis);
        if (c.get(Calendar.HOUR_OF_DAY) < CHANGE_HOUR) c.add(Calendar.DAY_OF_MONTH, -1);
        return String.format(Locale.US, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String code(byte[] key, String dayKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] h = mac.doFinal(("cuvar-dan:" + dayKey).getBytes(StandardCharsets.UTF_8));
            int o = h[h.length - 1] & 0x0f;
            int n = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format(Locale.US, "%06d", n % 1000000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
