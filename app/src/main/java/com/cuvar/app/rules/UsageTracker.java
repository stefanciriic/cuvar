package com.cuvar.app.rules;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Measures the previous observed context at each event boundary using a monotonic clock. */
public final class UsageTracker {
    public static final class Interval {
        public final long fromMs;
        public final long toMs;
        public final Set<String> keys;

        public Interval(long fromMs, long toMs, Set<String> keys) {
            this.fromMs = fromMs;
            this.toMs = toMs;
            this.keys = keys;
        }
    }

    private final long maxGapMs;
    private long lastElapsed = -1;
    private Set<String> keys = Collections.emptySet();

    public UsageTracker(long maxGapMs) {
        this.maxGapMs = maxGapMs;
    }

    public Interval checkpoint(long elapsedMs, long epochMs) {
        return transition(elapsedMs, epochMs, keys);
    }

    /** Epoch time labels the interval; elapsed time alone determines its duration. Null kad nema šta da se upiše. */
    public Interval transition(long elapsedMs, long epochMs, Collection<String> nextKeys) {
        long duration = lastElapsed < 0 ? 0 : elapsedMs - lastElapsed;
        Set<String> previous = keys;
        lastElapsed = elapsedMs;
        keys = Collections.unmodifiableSet(new LinkedHashSet<>(nextKeys));
        // A long unobserved gap can include sleep or a suspended service. Do not invent usage.
        if (duration <= 0 || duration > maxGapMs || previous.isEmpty()) return null;
        return new Interval(epochMs - duration, epochMs, previous);
    }
}
