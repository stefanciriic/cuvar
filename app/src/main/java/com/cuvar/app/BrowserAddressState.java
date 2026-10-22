package com.cuvar.app;

/**
 * Address confidence for one browser window. A toolbar hidden by scrolling or fullscreen keeps the host
 * last verified in that window, because the browser shows the toolbar again when another page loads.
 * A window whose address was never read fails closed after a short grace period.
 */
final class BrowserAddressState {
    static final long UNKNOWN_GRACE_MS = 3000L;
    /** Browser screens that never show a web page (history, bookmarks, downloads, settings). */
    private static final java.util.regex.Pattern INTERNAL_SCREEN = java.util.regex.Pattern.compile(
            "(?i)^(History|Bookmarks?|Downloads?|Settings|Preferences|ManageSpace|RecentTabs)\\w*Activity$");
    /** Browser's own pages (chrome://history, about:blank...), never a website. */
    private static final java.util.regex.Pattern INTERNAL_ADDRESS = java.util.regex.Pattern.compile(
            "(?i)^\\s*(chrome|chrome-native|chrome-search|edge|brave|vivaldi|kiwi|samsung|internet-native|about):.*");
    private String host;
    private long missingSince = -1;
    private boolean missing;

    void readable(String value) {
        host = value;
        missing = false;
        missingSince = -1;
    }

    /** A browser screen without an address bar that cannot show a website, e.g. Chrome history. */
    void internalScreen() {
        host = null;
        missing = false;
        missingSince = -1;
    }

    static boolean isInternalScreen(String activity) {
        if (activity == null) return false;
        String simple = activity.substring(activity.lastIndexOf('.') + 1);
        return INTERNAL_SCREEN.matcher(simple).matches();
    }

    static boolean isInternalAddress(String text) {
        return text != null && INTERNAL_ADDRESS.matcher(text).matches();
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
