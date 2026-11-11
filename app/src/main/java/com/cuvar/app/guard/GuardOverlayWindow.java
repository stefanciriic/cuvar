package com.cuvar.app.guard;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.cuvar.app.rules.QuickCover;
import com.cuvar.app.ui.Safe;
import com.cuvar.app.ui.Ui;

/** Owns the accessibility overlay window and pauses media while it is visible. */
public final class GuardOverlayWindow {
    private final WindowManager windows;
    private final AudioManager audio;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Zavesa pre blokade (vidi QuickCover): prazna pozadina dok ekran blokade ne bude nacrtan. */
    private View shield;
    private AudioFocusRequest silence;
    /** Da li je muzika svirala pri poslednjoj proveri, i koja je aplikacija bila napred kad je muzika krenula. */
    private boolean musicPlaying;
    private String musicOwner;
    private boolean musicSeen;

    public GuardOverlayWindow(Context context) {
        this.context = context;
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Beleži da li muzika svira dok nema ekrana blokade. Muzika koja je krenula dok je napred bila neka druga
     * aplikacija (npr. Spotify u pozadini) ne pripada zaključanoj aplikaciji, pa je ekran blokade ne prekida,
     * ni kad limit istekne dok se aplikacija koristi uz tu muziku.
     */
    public void noteMusic(String front) {
        boolean playing;
        try {
            playing = audio != null && audio.isMusicActive();
        } catch (Throwable error) {
            playing = false;
        }
        musicOwner = MusicOwner.next(!musicSeen, musicPlaying, playing, musicOwner, front);
        musicPlaying = playing;
        musicSeen = true;
    }

    /** bounds: samo taj deo ekrana (prozor jedne aplikacije u podeljenom ekranu), ili null za ceo ekran. */
    public void show(View view, boolean catchBack, String front, android.graphics.Rect bounds) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                catchBack ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        place(params, bounds);
        keepClearOfBars(view);
        windows.addView(view, params);
        if (!MusicOwner.belongsToOther(musicPlaying, musicOwner, front)) silenceMedia();
    }

    /** Pozadina ide ispod statusne trake i kamere, a sadržaj (dugmad, tekst) ostaje ispod njih. */
    private static void keepClearOfBars(View view) {
        final int left = view.getPaddingLeft(), top = view.getPaddingTop();
        final int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
                } else {
                    v.setPadding(left + insets.getSystemWindowInsetLeft(), top + insets.getSystemWindowInsetTop(),
                            right + insets.getSystemWindowInsetRight(), bottom + insets.getSystemWindowInsetBottom());
                }
            } catch (Throwable error) {
                GuardDiagnostics.report("overlayInsets", error);
            }
            return insets;
        });
    }

    /**
     * Odmah prekriva ceo ekran pozadinom ekrana blokade, bez sadržaja. Ne prima dodir ni fokus, pa ni greška
     * ne može da zaključa telefon; skida se sa uncover (najkasnije posle QuickCover.MAX_MS).
     */
    public void cover() {
        if (shield != null) return;
        try {
            View v = new View(context);
            v.setBackgroundColor(Ui.NIGHT);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.OPAQUE);
            params.gravity = Gravity.TOP | Gravity.START;
            params.windowAnimations = 0; // bez animacije: zavesa mora da se vidi odmah
            place(params, null);
            windows.addView(v, params);
            shield = v;
            handler.postDelayed(Safe.task("coverTimeout", () -> {
                if (shield == v) uncover();
            }), QuickCover.MAX_MS + 100L);
        } catch (Throwable error) {
            GuardDiagnostics.report("cover", error);
            shield = null;
        }
    }

    public boolean covered() {
        return shield != null;
    }

    public void uncover() {
        View v = shield;
        if (v == null) return;
        shield = null;
        try {
            windows.removeView(v);
        } catch (Throwable error) {
            GuardDiagnostics.report("uncover", error);
        }
    }

    /**
     * Skida zavesu tek kad je ekran blokade nacrtan; inače bi se između njih na trenutak videla aplikacija.
     * Ako crtanje ne stigne, zavesa se ipak skida posle kratkog vremena.
     */
    public void uncoverAfterDraw(View view) {
        final View s = shield;
        if (s == null || s.getTag() != null) return;
        s.setTag(Boolean.TRUE); // skidanje je već zakazano
        final Runnable lift = Safe.task("uncoverDrawn", () -> {
            if (shield == s) uncover();
        });
        try {
            final ViewTreeObserver.OnDrawListener[] drawn = new ViewTreeObserver.OnDrawListener[1];
            drawn[0] = () -> handler.post(Safe.task("uncoverDraw", () -> {
                try {
                    view.getViewTreeObserver().removeOnDrawListener(drawn[0]);
                } catch (Throwable ignored) {
                    // prozor je već skinut
                }
                // Još dva kadra da sistem prikaže nacrtano.
                handler.postDelayed(lift, 40L);
            }));
            view.getViewTreeObserver().addOnDrawListener(drawn[0]);
            view.invalidate();
        } catch (Throwable error) {
            GuardDiagnostics.report("uncoverAfterDraw", error);
        }
        handler.postDelayed(lift, 500L);
    }

    /** Pomera blokadu na nove granice prozora (pomerena granica podeljenog ekrana). */
    public void move(View view, android.graphics.Rect bounds) {
        try {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
            place(params, bounds);
            windows.updateViewLayout(view, params);
        } catch (Throwable error) {
            GuardDiagnostics.report("moveOverlay", error);
        }
    }

    private static void place(WindowManager.LayoutParams params, android.graphics.Rect bounds) {
        // Blokada ide preko celog ekrana, i preko statusne trake i izreza kamere (inače se tu vidi aplikacija).
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            // Podrazumevano sistem skraćuje prozor za statusnu i donju traku.
            params.setFitInsetsTypes(0);
            params.setFitInsetsSides(0);
        }
        if (bounds == null) return;
        // Granice prozora su u koordinatama celog ekrana, pa se blokada tako i postavlja.
        params.x = bounds.left;
        params.y = bounds.top;
        params.width = bounds.width();
        params.height = bounds.height();
    }

    public void hide(View view) {
        try {
            windows.removeView(view);
        } catch (Throwable error) {
            GuardDiagnostics.report("hideOverlay", error);
        } finally {
            releaseMedia();
        }
    }

    private void silenceMedia() {
        if (audio == null) return;
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
        } catch (Throwable error) {
            GuardDiagnostics.report("mediaPause", error);
        }
        try {
            if (silence == null) {
                silence = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(change -> { })
                        .build();
                audio.requestAudioFocus(silence);
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("audioFocus", error);
            silence = null;
        }
    }

    private void releaseMedia() {
        if (audio == null || silence == null) return;
        try {
            audio.abandonAudioFocusRequest(silence);
        } catch (Throwable error) {
            GuardDiagnostics.report("releaseAudioFocus", error);
        }
        silence = null;
    }
}
