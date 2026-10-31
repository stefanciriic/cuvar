package com.cuvar.app.guard;

import com.cuvar.app.GuardService;
import com.cuvar.app.R;
import com.cuvar.app.web.BrowserMonitor;
import com.cuvar.app.web.Hosts;
import com.cuvar.app.web.WebAddress;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Vrsta aplikacije koja je napred: bez pravila, kopija, pregledač u aplikaciji, WebAPK, izuzeta, podešavanja. */
public final class AppKinds {
    private final GuardService svc;

    public AppKinds(GuardService svc) {
        this.svc = svc;
    }

    public final Map<String, String> webApkHosts = new HashMap<>();

    /** Navigacija: Čuvar je nikad ne prati, ne čita i ne blokira, osim ako sama ima pravilo. */
    public static final Set<String> HANDS_OFF = new HashSet<>(java.util.Arrays.asList(
            "com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.waze"));

    public Set<String> launcherApps;
    private long launcherAppsAt;
    public Set<String> exempt;      // aplikacije koje ne troše ukupni limit i ne blokiraju se zbog njega, ni uz pravilo

    /** Aplikacije koje otvaraju linkove u svom pregledaču. */
    public static final String[] IN_APP_APPS = {"com.instagram.android", "com.instagram.lite", "com.facebook.katana",
            "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.twitter.android", "com.snapchat.android", "com.linkedin.android", "com.reddit.frontpage",
            "com.google.android.googlequicksearchbox", "com.google.android.gm", "org.telegram.messenger", "com.pinterest"};

    /** Sistemski ekrani koje zaštita od isključivanja mora da vidi. */
    public static final String[] SETTINGS_APPS = {"com.android.settings", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.android.vending", "com.miui.securitycenter", "com.samsung.android.lool", "com.samsung.accessibility",
            "com.google.android.marvin.talkback", "com.huawei.systemmanager", "com.coloros.safecenter", "com.oplus.safecenter"};

    public Map<String, String> clones;
    private long clonesAt;
    private long exemptAt;

    /** Aplikacija sa pravilom, ili pregledač koji Čuvar ne prati dok postoje pravila za sajtove. */
    public boolean guarded(String p) {
        return svc.store.appGuarded(p) || unknownBrowser(p) || cloner(p);
    }

    /**
     * Sajt „instaliran kao aplikacija“ iz pregledača (WebAPK, npr. YouTube na početnom ekranu iz Chrome-a):
     * host njegove početne adrese, "" kad se adresa ne zna, null kad nije takva aplikacija.
     */
    public String webApkHost(String p) {
        if (p == null || p.equals(svc.getPackageName()) || BrowserMonitor.isBrowser(p)) return null;
        if (webApkHosts.containsKey(p)) return webApkHosts.get(p);
        String host = null;
        try {
            android.os.Bundle meta = svc.getPackageManager()
                    .getApplicationInfo(p, PackageManager.GET_META_DATA).metaData;
            if (meta != null) {
                Object url = meta.get("org.chromium.webapk.shell_apk.startUrl");
                if (url == null) url = meta.get("org.chromium.webapk.shell_apk.scope");
                if (url != null) {
                    host = Hosts.hostOf(WebAddress.real(url.toString()));
                    if (host == null) host = "";
                }
            }
        } catch (Throwable ignored) {
            // nema podataka o aplikaciji: nije WebAPK
        }
        if (host == null && (p.startsWith("org.chromium.webapk.") || p.contains(".webapk."))) host = "";
        webApkHosts.put(p, host);
        return host;
    }

    /** Aplikacija za kloniranje drugih aplikacija; zaključana dok postoje pravila za aplikacije. */
    public boolean cloner(String p) {
        return Clones.isCloner(p) && !svc.store.guardedApps().isEmpty();
    }

    /**
     * Kopija aplikacije sa drugim imenom paketa (npr. com.instagram.android2 ili „Instagram 2“ iz App Cloner-a)
     * vodi se kao original, da se pravila ne zaobiđu kloniranjem. Vraća paket originala ili isti paket.
     */
    public String cloneOf(String p) {
        long now = SystemClock.elapsedRealtime();
        if (clones == null || now - clonesAt > 60000L) {
            clones = new HashMap<>();
            clonesAt = now;
        }
        String known = clones.get(p);
        if (known != null) return known;
        String out = p;
        Set<String> guarded = svc.store.guardedApps();
        if (!guarded.contains(p) && !exempt().contains(p) && !BrowserMonitor.isBrowser(p)) {
            String label = svc.appLabel(p);
            for (String g : guarded) {
                if (Clones.isPackageCopy(p, g) || Clones.isLabelCopy(label, g, svc.appLabel(g))) {
                    out = g;
                    break;
                }
            }
        }
        clones.put(p, out);
        return out;
    }

    /** Da li je napred pregledač unutar aplikacije (link otvoren iz Instagrama, Facebooka, Messengera...). */
    public boolean inAppBrowser(String p) {
        if (BrowserMonitor.isBrowser(p) || p.equals(svc.getPackageName())) return false;
        String a = svc.activityOf.get(p);
        return BrowserMonitor.isInAppBrowserActivity(a);
    }

    /**
     * Pregledač u kome Čuvar ne može da pročita adresu (npr. Opera, DuckDuckGo). Dok postoje pravila za sajtove,
     * on je zaključan kao aplikacija, inače bi se blokirani sajtovi samo otvorili u njemu.
     */
    public boolean unknownBrowser(String p) {
        return svc.browserMonitor.isUnknownBrowser(p, svc.store.hasSiteRules());
    }

    /** Promene sadržaja trebaju za adresu u pregledaču i, dok je zaštita uključena, za ekrane podešavanja. */
    /**
     * Aplikacija na koju se ne odnosi nijedno pravilo ni provera: nije pregledač, nema pravilo, nije kopija ni
     * aplikacija za kloniranje, nije ekran podešavanja pod zaštitom i ne traje fokus.
     */
    public boolean quiet(String raw) {
        if (handsOff(raw)) return true;
        if (raw.equals(svc.getPackageName()) || BrowserMonitor.isBrowser(raw) || svc.store.focus.focusActive()) return false;
        if (settingsLike(raw) && svc.store.protectNow()) return false;
        if (inAppBrowser(raw)) return false;
        if (webApkHost(raw) != null && svc.store.hasSiteRules()) return false;
        String p = cloneOf(raw);
        return p.equals(raw) && !guarded(p);
    }

    public boolean handsOff(String p) {
        return p != null && HANDS_OFF.contains(p) && (svc.store == null || !svc.store.appGuarded(p));
    }

    /** Sve aplikacije sa ikonom, za praćenje bez „Pristupa korišćenju“; osvežava se na 5 minuta. */
    public Set<String> launcherApps() {
        long now = SystemClock.elapsedRealtime();
        if (launcherApps == null || now - launcherAppsAt > 5 * 60000L) {
            Set<String> out = new HashSet<>();
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (android.content.pm.ResolveInfo r : svc.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL)) {
                    if (r.activityInfo != null) out.add(r.activityInfo.packageName);
                }
            } catch (Throwable error) {
                GuardDiagnostics.report("launcherApps", error);
            }
            launcherApps = out;
            launcherAppsAt = now;
        }
        return launcherApps;
    }

    public static boolean settingsLike(String p) {
        return SystemScreens.settingsLike(p);
    }

    /** Da li sistemski ekran prikazuje Čuvara (podaci o aplikaciji, Pristupačnost, brisanje). */
    public boolean guardsSelf(AccessibilityNodeInfo root, String p) {
        if (p.equals(svc.getPackageName()) || exempt().contains(p)) return false;
        if (!settingsLike(p)) return false;
        // Korisnik je iz Čuvara otvorio „Pristup korišćenju“ da ga uključi: taj ekran se ne zatvara dva minuta.
        if (SystemClock.elapsedRealtime() < GuardService.usageSetupUntil && !svc.usageOk) return false;
        boolean installer = SystemScreens.installer(p);
        try {
            String label = svc.getString(R.string.app_name);
            if (!mentions(root, label) && !mentions(root, "Чувар")) return false;
            if (!installer) return true;
            for (String w : SystemScreens.UNINSTALL) {
                List<AccessibilityNodeInfo> n = root.findAccessibilityNodeInfosByText(w);
                if (n != null && !n.isEmpty()) return true;
            }
        } catch (Throwable ignored) {
            // prozor se promenio dok se čitao: smatra se da teksta nema
        }
        return false;
    }

    /** Da li se na ekranu pominje naziv (ali ne „čuvar ekrana“, koji je podešavanje ekrana). */
    private static boolean mentions(AccessibilityNodeInfo root, String name) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(name);
        if (nodes == null) return false;
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence t = n == null ? null : n.getText();
            if (t == null) t = n == null ? null : n.getContentDescription();
            if (t == null) continue;
            if (SystemScreens.namesGuard(t.toString())) return true;
        }
        return false;
    }

    /** Početni ekran, Čuvar, pozivi i poruke: ne troše ukupni limit i nikad se zbog njega ne blokiraju, ni uz pravilo. */
    public Set<String> exempt() {
        long now = SystemClock.elapsedRealtime();
        if (exempt == null || now - exemptAt > 60000L) {
            exempt = exemptApps(svc);
            exemptAt = now;
        }
        return exempt;
    }

    /** Poznate aplikacije za telefon i SMS, kad Android ne kaže koja je podrazumevana: hitni pozivi moraju da rade. */
    private static final String[] FALLBACK_DIALERS = {"com.google.android.dialer", "com.android.dialer",
            "com.samsung.android.dialer", "com.android.incallui", "com.samsung.android.incallui", "com.android.server.telecom"};
    private static final String[] FALLBACK_SMS = {"com.google.android.apps.messaging", "com.android.mms",
            "com.samsung.android.messaging", "com.android.messaging"};

    public static Set<String> exemptApps(Context c) {
        Set<String> out = new HashSet<>();
        out.add(c.getPackageName());
        boolean dialerKnown = false, smsKnown = false;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r != null && r.activityInfo != null) out.add(r.activityInfo.packageName);
        } catch (Throwable error) {
            GuardDiagnostics.report("exemptHome", error);
        }
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            String dialer = tm == null ? null : tm.getDefaultDialerPackage();
            if (dialer != null) {
                out.add(dialer);
                dialerKnown = true;
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("exemptDialer", error);
        }
        try {
            String sms = Telephony.Sms.getDefaultSmsPackage(c);
            if (sms != null) {
                out.add(sms);
                smsKnown = true;
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("exemptSms", error);
        }
        if (!dialerKnown) Collections.addAll(out, FALLBACK_DIALERS);
        if (!smsKnown) Collections.addAll(out, FALLBACK_SMS);
        return out;
    }
}
