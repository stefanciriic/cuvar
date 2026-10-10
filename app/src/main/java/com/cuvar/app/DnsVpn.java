package com.cuvar.app;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lokalni VPN samo za DNS: kroz njega ide jedino pitanje „koja je adresa ovog sajta“, sav ostali saobraćaj ide
 * direktno, pa ne usporava internet. Blokirani sajt dobije odgovor „ne postoji“ u svakoj aplikaciji i pregledaču,
 * i u pregledaču unutar Instagrama ili Messengera. Ništa ne napušta telefon osim samog pitanja ka DNS-u mreže.
 */
public class DnsVpn extends VpnService {

    static final String ACTION_STOP = "com.cuvar.app.DNS_STOP";
    private static final String TUN_ADDR = "10.111.222.2";
    private static final String DNS_ADDR = "10.111.222.1";
    private static final byte[] DNS_IP = {10, 111, (byte) 222, 1};

    /** Servisi za „bezbedni DNS“ u pregledačima; bez njih bi Chrome mogao da zaobiđe blokadu. */
    private static final String[] DOH = {"dns.google", "dns.google.com", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
            "chrome.cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com", "dns.quad9.net", "dns9.quad9.net",
            "dns10.quad9.net", "dns11.quad9.net", "doh.opendns.com", "doh.familyshield.opendns.com", "dns.nextdns.io",
            "doh.cleanbrowsing.org", "dns.adguard.com", "dns.adguard-dns.com", "dns-unfiltered.adguard.com", "doh.mullvad.net",
            "dns.mullvad.net", "doh.dns.sb", "dns.controld.com", "freedns.controld.com", "doh.xfinity.com", "dns.brahma.world",
            "use-application-dns.net"};

    static volatile boolean running;

    private ParcelFileDescriptor tun;
    private Thread loop;
    private ExecutorService pool;
    private final Map<String, Long> decided = new HashMap<>(); // ime -> do kada važi odluka (+ blokirano / - pušteno)

    static void start(Context c) {
        try {
            c.startService(new Intent(c, DnsVpn.class));
        } catch (Throwable error) {
            GuardDiagnostics.report("dnsStart", error);
        }
    }

    static void stop(Context c) {
        try {
            c.startService(new Intent(c, DnsVpn.class).setAction(ACTION_STOP));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (loop == null || !loop.isAlive()) open();
        return START_STICKY;
    }

    private synchronized void open() {
        try {
            Builder b = new Builder()
                    .setSession(getString(R.string.app_name))
                    .addAddress(TUN_ADDR, 32)
                    .addRoute(DNS_ADDR, 32)
                    .addDnsServer(DNS_ADDR)
                    .setBlocking(true)
                    .setMtu(1500);
            try {
                b.addDisallowedApplication(getPackageName()); // Čuvarova pitanja ka DNS-u mreže idu direktno
            } catch (Throwable ignored) {
            }
            tun = b.establish();
        } catch (Throwable error) {
            GuardDiagnostics.report("dnsEstablish", error);
            tun = null;
        }
        if (tun == null) {
            running = false;
            stopSelf();
            return;
        }
        pool = Executors.newFixedThreadPool(4);
        running = true;
        final ParcelFileDescriptor fd = tun;
        loop = new Thread(() -> run(fd), "cuvar-dns");
        loop.start();
    }

    private void run(ParcelFileDescriptor fd) {
        byte[] buf = new byte[32767];
        try (FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
            while (running) {
                int n = in.read(buf);
                if (n <= 0) continue;
                final byte[] pkt = java.util.Arrays.copyOf(buf, n);
                handle(pkt, out);
            }
        } catch (Throwable error) {
            if (running) GuardDiagnostics.report("dnsLoop", error);
        }
    }

    /** IPv4 + UDP na port 53 našeg DNS-a; sve ostalo (npr. pokušaj šifrovanog DNS-a na 853) se odbacuje. */
    private void handle(byte[] pkt, FileOutputStream out) {
        if (pkt.length < 28 || (pkt[0] >> 4) != 4 || pkt[9] != 17) return;
        int ihl = (pkt[0] & 0x0f) * 4;
        if (pkt.length < ihl + 8 + 12) return;
        int dport = ((pkt[ihl + 2] & 0xff) << 8) | (pkt[ihl + 3] & 0xff);
        if (dport != 53) return;
        final byte[] query = java.util.Arrays.copyOfRange(pkt, ihl + 8, pkt.length);
        String name = questionName(query);
        if (name != null && blocked(name)) {
            byte[] reply = nxdomain(query);
            if (reply != null) write(out, ipUdp(pkt, ihl, reply));
            return;
        }
        if (pool == null) return;
        pool.execute(() -> {
            byte[] reply = forward(query);
            if (reply != null) write(out, ipUdp(pkt, ihl, reply));
        });
    }

    private boolean blocked(String name) {
        long now = SystemClock.elapsedRealtime();
        synchronized (decided) {
            Long until = decided.get(name);
            if (until != null && Math.abs(until) > now) return until > 0;
        }
        boolean b = false;
        for (String d : DOH) b |= name.equals(d);
        if (!b) {
            try {
                b = Store.get(this).dnsBlocked(Store.hostOf(name));
            } catch (Throwable error) {
                b = false;
            }
        }
        synchronized (decided) {
            if (decided.size() > 500) decided.clear();
            decided.put(name, b ? now + 5000L : -(now + 5000L));
        }
        return b;
    }

    private static String questionName(byte[] q) {
        if (q.length < 13 || ((q[4] << 8) | (q[5] & 0xff)) < 1) return null;
        StringBuilder sb = new StringBuilder();
        int i = 12;
        while (i < q.length) {
            int len = q[i] & 0xff;
            if (len == 0) break;
            if ((len & 0xc0) != 0 || i + 1 + len > q.length) return null;
            if (sb.length() > 0) sb.append('.');
            for (int k = 0; k < len; k++) sb.append((char) (q[i + 1 + k] & 0xff));
            i += 1 + len;
        }
        return sb.length() == 0 ? null : sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Odgovor „ime ne postoji“ (zaglavlje i pitanje iz upita, bez ostalih zapisa). */
    private static byte[] nxdomain(byte[] q) {
        int i = 12;
        while (i < q.length && (q[i] & 0xff) != 0) i += 1 + (q[i] & 0xff);
        int end = i + 1 + 4; // nula na kraju imena, tip i klasa
        if (end > q.length) return null;
        byte[] r = java.util.Arrays.copyOf(q, end);
        r[2] = (byte) (0x80 | (q[2] & 0x79));
        r[3] = (byte) 0x83;
        r[4] = 0;
        r[5] = 1;
        for (int k = 6; k < 12; k++) r[k] = 0;
        return r;
    }

    private byte[] forward(byte[] query) {
        for (InetAddress server : upstream()) {
            try (DatagramSocket s = new DatagramSocket()) {
                protect(s);
                s.setSoTimeout(3000);
                s.send(new DatagramPacket(query, query.length, server, 53));
                byte[] buf = new byte[4096];
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                return java.util.Arrays.copyOf(buf, p.getLength());
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private List<InetAddress> cachedUpstream;
    private long upstreamAt;

    /** DNS serveri prave mreže (Wi-Fi ili mobilni), pa javni kao rezerva. */
    private List<InetAddress> upstream() {
        long now = SystemClock.elapsedRealtime();
        if (cachedUpstream != null && now - upstreamAt < 30000L) return cachedUpstream;
        List<InetAddress> out = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null || nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (InetAddress a : lp.getDnsServers()) {
                    if (a instanceof Inet4Address && !out.contains(a)) out.add(0, a);
                    else if (!out.contains(a)) out.add(a);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            out.add(InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
            out.add(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}));
        } catch (Throwable ignored) {
        }
        cachedUpstream = out;
        upstreamAt = now;
        return out;
    }

    /** Da li je na mreži uključen privatni DNS sa imenom servera (tada telefon zaobilazi ovaj VPN). */
    static boolean privateDnsStrict(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null || nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp != null && android.os.Build.VERSION.SDK_INT >= 28 && lp.getPrivateDnsServerName() != null) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** IPv4/UDP paket nazad aplikaciji: zamenjene adrese i portovi iz upita. */
    private static byte[] ipUdp(byte[] req, int ihl, byte[] payload) {
        int total = 28 + payload.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        p[2] = (byte) (total >> 8);
        p[3] = (byte) total;
        p[6] = 0x40;
        p[8] = 64;
        p[9] = 17;
        System.arraycopy(req, 16, p, 12, 4);
        System.arraycopy(req, 12, p, 16, 4);
        int sum = 0;
        for (int i = 0; i < 20; i += 2) sum += ((p[i] & 0xff) << 8) | (p[i + 1] & 0xff);
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        sum = ~sum & 0xffff;
        p[10] = (byte) (sum >> 8);
        p[11] = (byte) sum;
        p[20] = req[ihl + 2];
        p[21] = req[ihl + 3];
        p[22] = req[ihl];
        p[23] = req[ihl + 1];
        int ulen = 8 + payload.length;
        p[24] = (byte) (ulen >> 8);
        p[25] = (byte) ulen;
        System.arraycopy(payload, 0, p, 28, payload.length);
        return p;
    }

    private void write(FileOutputStream out, byte[] p) {
        synchronized (this) {
            try {
                out.write(p);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onRevoke() {
        shutdown(); // drugi VPN je preuzeo mesto ili je isključen u podešavanjima; Čuvar to beleži
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private synchronized void shutdown() {
        running = false;
        if (pool != null) pool.shutdownNow();
        pool = null;
        if (tun != null) {
            try {
                tun.close();
            } catch (Throwable ignored) {
            }
        }
        tun = null;
        if (loop != null) loop.interrupt();
        loop = null;
    }
}
