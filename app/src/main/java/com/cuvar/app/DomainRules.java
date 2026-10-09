package com.cuvar.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Pravila roditeljskog domena važe i kada poddomen ima sopstveno pravilo. */
final class DomainRules {
    private DomainRules() { }

    static List<String> matching(String host, Collection<String> domains) {
        List<String> matches = new ArrayList<>();
        if (host == null) return matches;
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) matches.add(domain);
        }
        matches.sort((a, b) -> {
            int length = Integer.compare(b.length(), a.length());
            return length != 0 ? length : a.compareTo(b);
        });
        return matches;
    }
}
