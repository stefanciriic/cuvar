package com.cuvar.app;

/** Address confidence for one browser window. Unknown navigation fails closed after a short grace period. */
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
        return missing && missingSince >= 0 && elapsedMs - missingSince >= UNKNOWN_GRACE_MS;
    }

    /** Clears a previously trusted host once the address stays unavailable long enough. */
    void clearIfUnresolved(long elapsedMs) {
        if (unresolved(elapsedMs)) host = null;
    }
}
