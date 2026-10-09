package com.cuvar.app;

/** Address confidence for one browser window. A hidden toolbar may keep a verified fullscreen host. */
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
        return missing && host == null && missingSince >= 0
                && elapsedMs - missingSince >= UNKNOWN_GRACE_MS;
    }
}
