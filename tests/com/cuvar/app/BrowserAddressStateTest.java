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
        s.clearIfUnresolved(14000);
        check(s.unresolved(14000) && s.host() == null, "stale fullscreen host is cleared after the grace period");
        s.readable(null);
        check(s.host() == null && !s.unresolved(15000), "empty new tab clears old host");
        s.missing(32000);
        check(s.unresolved(35000), "unverified next document does not inherit old host");
        System.out.println("Prošlo: " + checks + " provera BrowserAddressState.");
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
