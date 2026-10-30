package com.cuvar.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/** Owns the accessibility overlay window and pauses media while it is visible. */
final class GuardOverlayWindow {
    private final WindowManager windows;
    private final AudioManager audio;
    private AudioFocusRequest silence;
    /** Da li je muzika svirala pri poslednjoj proveri, i koja je aplikacija bila napred kad je muzika krenula. */
    private boolean musicPlaying;
    private String musicOwner;
    private boolean musicSeen;

    GuardOverlayWindow(Context context) {
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Beleži da li muzika svira dok nema ekrana blokade. Muzika koja je krenula dok je napred bila neka druga
     * aplikacija (npr. Spotify u pozadini) ne pripada zaključanoj aplikaciji, pa je ekran blokade ne prekida,
     * ni kad limit istekne dok se aplikacija koristi uz tu muziku.
     */
    void noteMusic(String front) {
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

    void show(View view, boolean catchBack, String front) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                catchBack ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windows.addView(view, params);
        if (!MusicOwner.belongsToOther(musicPlaying, musicOwner, front)) silenceMedia();
    }

    void hide(View view) {
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
