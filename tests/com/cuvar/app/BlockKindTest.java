package com.cuvar.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Koja blokada se prikazuje: tvrde se ne otključavaju, otključavanje jedne stavke ne otvara druge. */
public final class BlockKindTest {
    private static int checks;

    public static void main(String[] args) {
        int[] hard = {BlockKind.SCHEDULE, BlockKind.DAY, BlockKind.NIGHT, BlockKind.TIME, BlockKind.SITE_TIME,
                BlockKind.OPENS, BlockKind.BREAK, BlockKind.FOCUS};
        int[] soft = {BlockKind.LOCK, BlockKind.SITE, BlockKind.CODE, BlockKind.BROWSER, BlockKind.PAUSE,
                BlockKind.CLONER, BlockKind.INAPP, BlockKind.ADDRESS};
        for (int k : hard) check(BlockKind.hard(k), "Tvrda blokada " + k);
        for (int k : soft) check(!BlockKind.hard(k), "Otključava se " + k);

        Set<Integer> all = new HashSet<>();
        for (int k : hard) all.add(k);
        for (int k : soft) all.add(k);
        check(all.size() == 16, "Svaka vrsta ima svoj broj");

        BlockKind.Block lock = new BlockKind.Block("app:x", BlockKind.LOCK, null, true);
        BlockKind.Block site = new BlockKind.Block("site:y.com", BlockKind.SITE, null, true);
        BlockKind.Block night = new BlockKind.Block("app:x", BlockKind.NIGHT, null, false);

        check(BlockKind.pick(Collections.emptyList(), k -> false) == null, "Ništa ne blokira");
        check(BlockKind.pick(Arrays.asList(lock, site), k -> false) == lock, "Prva po redu");
        check(BlockKind.pick(Arrays.asList(lock), k -> true) == null, "Otključana stavka se otvara");
        check(BlockKind.pick(Arrays.asList(lock, site), "app:x"::equals) == site,
                "Otključan pregledač ne otvara blokiran sajt");
        check(BlockKind.pick(Arrays.asList(night, lock), k -> true) == night, "Noć se ne otključava ni šifrom");
        System.out.println("BlockKindTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
