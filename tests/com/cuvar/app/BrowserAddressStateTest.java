package com.cuvar.app;

public final class BrowserAddressStateTest {
    private static int checks;

    public static void main(String[] args) {
        BrowserAddressState s = new BrowserAddressState();
        s.missing(100);
        check(!s.unresolved(3099), "wait briefly for toolbar to render");
        check(s.unresolved(3100), "missing address becomes explicit after grace");
        s.editing();
        check(!s.unresolved(10000), "typing an address is not a missing toolbar");
        s.readable("example.com");
        s.missing(11000);
        check(!s.unresolved(13000) && "example.com".equals(s.host()), "briefly missing toolbar keeps verified host");
        check(!s.unresolved(600000) && "example.com".equals(s.host()),
                "toolbar hidden while reading a long page keeps the verified host and does not lock");
        s.readable("other.org");
        check("other.org".equals(s.host()) && !s.unresolved(600000), "toolbar shown again replaces the host");
        s.missing(700000);
        check(!s.unresolved(900000) && "other.org".equals(s.host()), "hiding again keeps the new host");
        s.readable(null);
        check(s.host() == null && !s.unresolved(900000), "empty new tab clears old host");
        s.missing(910000);
        check(!s.unresolved(912999), "unverified window waits for the toolbar");
        check(s.unresolved(913000), "unverified window does not inherit old host and fails closed");
        BrowserAddressState h = new BrowserAddressState();
        h.missing(0);
        h.internalScreen();
        check(h.host() == null && !h.unresolved(600000), "Chrome history screen without address bar does not lock");
        check(BrowserAddressState.isInternalScreen("org.chromium.chrome.browser.history.HistoryActivity"), "history activity");
        check(BrowserAddressState.isInternalScreen("org.chromium.chrome.browser.bookmarks.BookmarkActivity"), "bookmarks");
        check(BrowserAddressState.isInternalScreen("org.chromium.chrome.browser.download.DownloadActivity"), "downloads");
        check(!BrowserAddressState.isInternalScreen("org.chromium.chrome.browser.ChromeTabbedActivity"), "tabs show websites");
        check(!BrowserAddressState.isInternalScreen("org.chromium.chrome.browser.customtabs.CustomTabActivity"), "custom tabs show websites");
        check(!BrowserAddressState.isInternalScreen(null), "unknown screen fails closed");
        check(BrowserAddressState.isInternalAddress("chrome://history"), "chrome history page");
        check(BrowserAddressState.isInternalAddress("about:blank"), "about page");
        check(!BrowserAddressState.isInternalAddress("youtube.com"), "website");
        check(!BrowserAddressState.isInternalAddress("https://www.youtube.com/watch?v=1"), "website with scheme");
        check(!BrowserAddressState.isInternalAddress("view-source:https://youtube.com"), "view-source still shows the site");
        System.out.println("Prošlo: " + checks + " provera BrowserAddressState.");
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
