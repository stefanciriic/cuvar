package com.cuvar.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DomainRulesTest {
    public static void main(String[] args) {
        List<String> rules = Arrays.asList("facebook.com", "m.facebook.com", "video.m.facebook.com", "other.com");
        expect(DomainRules.matching("video.m.facebook.com", rules),
                "video.m.facebook.com", "m.facebook.com", "facebook.com");
        expect(DomainRules.matching("a.video.m.facebook.com", rules),
                "video.m.facebook.com", "m.facebook.com", "facebook.com");
        expect(DomainRules.matching("facebook.com", rules), "facebook.com");
        expect(DomainRules.matching("notfacebook.com", rules));
        expect(DomainRules.matching("facebook.com.evil.example", rules));
        expect(DomainRules.matching(null, rules));
        expect(DomainRules.matching("facebook.com", Collections.emptyList()));

        // Dodavanje blažeg poddomena ne sme da skloni blokadu niti potrošeni limit roditelja.
        List<String> before = DomainRules.matching("m.facebook.com", Arrays.asList("facebook.com"));
        List<String> after = DomainRules.matching("m.facebook.com", rules);
        if (!after.containsAll(before)) throw new AssertionError("Roditeljsko pravilo je izgubljeno");
        System.out.println("DomainRulesTest: prošlo 8 provera.");
    }

    private static void expect(List<String> actual, String... expected) {
        if (!actual.equals(Arrays.asList(expected))) throw new AssertionError(actual + " != " + Arrays.toString(expected));
    }
}
