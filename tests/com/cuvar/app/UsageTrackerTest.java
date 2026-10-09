package com.cuvar.app;

import java.util.Arrays;
import java.util.Collections;

public final class UsageTrackerTest {
    private static int checks;

    public static void main(String[] args) {
        UsageTracker t = new UsageTracker(15000);
        check(t.transition(0, 100000, Arrays.asList("a")) == null, "startup has no usage");
        UsageTracker.Interval a = t.transition(4000, 104000, Arrays.asList("b"));
        check(a.keys.equals(Collections.singleton("a")) && a.toMs - a.fromMs == 4000,
                "switch settles four seconds to app A");
        UsageTracker.Interval b = t.transition(5000, 105000, Collections.emptySet());
        check(b.keys.equals(Collections.singleton("b")) && b.toMs - b.fromMs == 1000,
                "screen off or overlay settles one second to app B");
        check(t.transition(9000, 109000, Arrays.asList("b", "site:example.com", "site:example.com")) == null,
                "blocked or screen-off time is not counted");
        UsageTracker.Interval resumed = t.transition(10000, 110000, Arrays.asList("b"));
        check(resumed.keys.size() == 2 && resumed.toMs - resumed.fromMs == 1000,
                "resume counts only active interval and deduplicates visible domains");
        check(t.transition(50000, 150000, Arrays.asList("b")) == null, "suspended gap is discarded");
        check(t.transition(50000, 150000, Arrays.asList("b")) == null, "duplicate boundary is not counted");
        UsageTracker.Interval midnight = t.transition(52000, 86401000, Collections.emptySet());
        check(midnight.fromMs == 86399000 && midnight.toMs == 86401000,
                "epoch endpoints preserve interval across midnight for Store to split");
        t.transition(53000, 86402000, Arrays.asList("a"));
        UsageTracker.Interval beforeCheck = t.checkpoint(54000, 86403000);
        UsageTracker.Interval duringCheck = t.transition(54200, 86403200, Arrays.asList("a"));
        check(beforeCheck.toMs - beforeCheck.fromMs + duringCheck.toMs - duringCheck.fromMs == 1200,
                "frequent checks do not discard time spent checking the same app");
        System.out.println("Prošlo: " + checks + " provera UsageTracker.");
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
