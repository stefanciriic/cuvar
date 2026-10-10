package com.cuvar.app;

/**
 * Address confidence for one browser window. A toolbar hidden by scrolling or fullscreen keeps the host
 * last verified in that window, because the browser shows the toolbar again when another page loads.
 * A window whose address was never read fails closed after a short grace period.
 */
final class BrowserAddressState {
    static final long UNKNOWN_GRACE_MS = 3000L;
    private String host;
    private long missingSince = -1;
    private boolean missing;

    void readable(String value) {
        host = value;
        missing = false;
        missingSince = -1;
    }

    void editing() {
        missing = false;
        missingSince = -1;
    }

    void missing(long elapsedMs) {
        missing = true;
        if (missingSince < 0) missingSince = elapsedMs;
    }

    String host() {
        return host;
    }

    boolean unresolved(long elapsedMs) {
        return missing && host == null && missingSince >= 0 && elapsedMs - missingSince >= UNKNOWN_GRACE_MS;
    }
}
