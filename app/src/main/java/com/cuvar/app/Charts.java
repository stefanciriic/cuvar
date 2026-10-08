package com.cuvar.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/** Grafikoni za statistiku, crtani ručno na Canvas-u (krug po aplikacijama i kolone po danima). */
final class Charts {

    private Charts() {
    }

    /** Boje redom za 1., 2., 3. … aplikaciju; poslednja je za „Ostalo“. Boje se čitaju pri crtanju zbog teme. */
    static int color(int i, boolean other) {
        if (other) return Ui.dark ? 0xFF5A6273 : 0xFFBDB6A8;
        int[] c = Ui.dark
                ? new int[]{Ui.ACCENT, 0xFF3FB8B0, 0xFF6C9BF2, 0xFFB08CF0, 0xFFE8B33A}
                : new int[]{Ui.ACCENT, 0xFF22857F, 0xFF3B6FD8, 0xFF8452C8, 0xFFC98F0B};
        return c[i % c.length];
    }

    /** Zelena za „manje nego ranije“. */
    static int good() {
        return Ui.dark ? 0xFF5CC08A : 0xFF2F855A;
    }

    /** Jedan deo kruga ili kolone. */
    static final class Part {
        final long ms;
        final int color;

        Part(long ms, int color) {
            this.ms = ms;
            this.color = color;
        }
    }

    /** Krug podeljen po aplikacijama, sa ukupnim vremenom u sredini. */
    static final class Donut extends View {
        private final List<Part> parts;
        private final String center;
        private final String caption;
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        Donut(Context c, List<Part> parts, String center, String caption) {
            super(c);
            this.parts = parts;
            this.center = center;
            this.caption = caption;
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.BUTT);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int size = Ui.dp(getContext(), 190);
            setMeasuredDimension(resolveSize(size, w), size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float stroke = Ui.dp(getContext(), 24);
            float d = Math.min(getWidth(), getHeight()) - stroke;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            box.set(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2);
            arc.setStrokeWidth(stroke);

            long total = 0;
            for (Part p : parts) total += p.ms;
            if (total <= 0) {
                arc.setColor(Ui.LINE);
                canvas.drawArc(box, 0, 360, false, arc);
            } else {
                float gap = parts.size() > 1 ? 1.5f : 0f;
                float angle = -90f;
                for (Part p : parts) {
                    float sweep = 360f * p.ms / total;
                    if (sweep - gap > 0.3f) {
                        arc.setColor(p.color);
                        canvas.drawArc(box, angle + gap / 2, sweep - gap, false, arc);
                    }
                    angle += sweep;
                }
            }

            text.setColor(Ui.INK);
            text.setTextSize(sp(22));
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            canvas.drawText(center, cx, cy + sp(4), text);
            text.setColor(Ui.MUTED);
            text.setTextSize(sp(12));
            text.setTypeface(Typeface.DEFAULT);
            canvas.drawText(caption, cx, cy + sp(22), text);
        }

        private float sp(float v) {
            return v * getResources().getDisplayMetrics().scaledDensity;
        }
    }

    /** Kolone po danima, svaka podeljena po aplikacijama, sa isprekidanom linijom proseka. */
    static final class Columns extends View {
        private final List<List<Part>> days;
        private final List<String> labels;
        private final int highlight;
        private final long avg;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF r = new RectF();

        /** highlight je indeks današnjeg dana (ili -1), avg je prosek po danu (0 = bez linije). */
        Columns(Context c, List<List<Part>> days, List<String> labels, int highlight, long avg) {
            super(c);
            this.days = days;
            this.labels = labels;
            this.highlight = highlight;
            this.avg = avg;
            text.setTextAlign(Paint.Align.CENTER);
            line.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize(Ui.dp(getContext(), 300), w), Ui.dp(getContext(), 200));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int n = days.size();
            if (n == 0) return;
            float density = getResources().getDisplayMetrics().density;
            float scaled = getResources().getDisplayMetrics().scaledDensity;
            float top = 22 * density;
            float bottom = getHeight() - 24 * density;
            float h = bottom - top;
            float slot = getWidth() / (float) n;
            float col = Math.min(slot * 0.62f, 28 * density);
            float radius = Math.min(col / 2f, 6 * density);

            long max = Math.max(1L, avg);
            for (List<Part> day : days) {
                long t = 0;
                for (Part p : day) t += p.ms;
                max = Math.max(max, t);
            }

            for (int i = 0; i < n; i++) {
                float cx = slot * i + slot / 2f;
                long t = 0;
                for (Part p : days.get(i)) t += p.ms;
                float colH = h * t / max;
                if (colH < 3 * density) {
                    // dan bez korišćenja: samo tanka crtica na dnu
                    fill.setColor(Ui.LINE);
                    r.set(cx - col / 2, bottom - 3 * density, cx + col / 2, bottom);
                    canvas.drawRoundRect(r, 1.5f * density, 1.5f * density, fill);
                } else {
                    canvas.save();
                    clip.reset();
                    r.set(cx - col / 2, bottom - colH, cx + col / 2, bottom);
                    clip.addRoundRect(r, new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
                    canvas.clipPath(clip);
                    float y = bottom;
                    for (Part p : days.get(i)) {
                        float ph = h * p.ms / max;
                        fill.setColor(p.color);
                        canvas.drawRect(cx - col / 2, y - ph, cx + col / 2, y, fill);
                        y -= ph;
                    }
                    canvas.restore();
                }

                boolean today = i == highlight;
                text.setColor(today ? Ui.INK : Ui.MUTED);
                text.setTextSize((n > 10 ? 10 : 12) * scaled);
                text.setTypeface(today ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
                canvas.drawText(labels.get(i), cx, getHeight() - 6 * density, text);
            }

            if (avg > 0) {
                float y = bottom - h * avg / max;
                line.setColor(Ui.MUTED);
                line.setStrokeWidth(1.5f * density);
                line.setPathEffect(new DashPathEffect(new float[]{6 * density, 5 * density}, 0));
                canvas.drawLine(0, y, getWidth(), y, line);
                text.setColor(Ui.MUTED);
                text.setTextSize(11 * scaled);
                text.setTypeface(Typeface.DEFAULT);
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText("prosek " + Ui.fmt(avg), getWidth(), y - 5 * density, text);
                text.setTextAlign(Paint.Align.CENTER);
            }
        }
    }
}
