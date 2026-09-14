package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class DashboardView extends View {

    private enum Mode { STREET, SPORT, DIAGNOSTICS }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private Mode mode = Mode.SPORT;

    private float rpm = 850f;
    private float speed = 0f;
    private float coolant = 88f;
    private float voltage = 14.1f;
    private float throttle = 12f;
    private float load = 28f;
    private float intake = 30f;
    private float fuel = 68f;
    private float trip = 248.7f;

    private float targetRpm = 2600f;
    private float targetSpeed = 54f;
    private float targetThrottle = 26f;
    private float targetLoad = 40f;

    private final int BG = Color.rgb(5, 8, 10);
    private final int PANEL = Color.rgb(12, 17, 20);
    private final int BORDER = Color.rgb(55, 68, 74);
    private final int TEXT = Color.rgb(236, 241, 244);
    private final int MUTED = Color.rgb(150, 164, 174);
    private final int GREEN = Color.rgb(46, 235, 133);
    private final int YELLOW = Color.rgb(246, 211, 70);
    private final int RED = Color.rgb(245, 72, 72);

    private boolean simulationRunning = false;

    private final Runnable simulationTick = new Runnable() {
        @Override
        public void run() {
            if (!simulationRunning) return;

            if (Math.abs(rpm - targetRpm) < 120) {
                targetRpm = 900 + random.nextInt(5300);
                targetSpeed = Math.max(0, Math.min(130,
                        (targetRpm - 900) / 38f + random.nextInt(10)));
                targetThrottle = 8 + random.nextInt(65);
                targetLoad = 20 + random.nextInt(65);
            }

            rpm += (targetRpm - rpm) * 0.07f;
            speed += (targetSpeed - speed) * 0.055f;
            throttle += (targetThrottle - throttle) * 0.06f;
            load += (targetLoad - load) * 0.05f;

            coolant += ((89f + (rpm > 4200 ? 3f : 0f)) - coolant) * 0.01f;
            voltage += ((13.9f + (rpm / 8000f) * 0.4f) - voltage) * 0.03f;
            intake += ((30f + (rpm / 8000f) * 8f) - intake) * 0.015f;
            trip += speed / 7200f;

            invalidate();
            handler.postDelayed(this, 80);
        }
    };

    public DashboardView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setColor(BORDER);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!simulationRunning) {
            simulationRunning = true;
            handler.postDelayed(simulationTick, 120);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        simulationRunning = false;
        handler.removeCallbacks(simulationTick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        float w = getWidth();
        float h = getHeight();

        c.drawColor(BG);
        drawBackgroundGlow(c, w, h);
        drawHeader(c, w, h);

        switch (mode) {
            case STREET:
                drawStreet(c, w, h);
                break;
            case SPORT:
                drawSport(c, w, h);
                break;
            case DIAGNOSTICS:
                drawDiagnostics(c, w, h);
                break;
        }

        drawBottomTabs(c, w, h);
    }

    private void drawBackgroundGlow(Canvas c, float w, float h) {
        p.setShader(new RadialGradient(
                w * 0.62f, h * 0.30f, w * 0.62f,
                new int[]{Color.argb(35, 35, 180, 110), Color.argb(0, 0, 0, 0)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        p.setColor(Color.argb(22, 255, 255, 255));
        p.setStrokeWidth(1f);
        for (int i = 0; i < 9; i++) {
            c.drawLine(w * 0.70f + i * 22, 76, w - 20, 76 + i * 10, p);
        }
    }

    private void drawHeader(Canvas c, float w, float h) {
        p.setColor(Color.rgb(9, 13, 16));
        c.drawRect(0, 0, w, 78, p);
        p.setColor(BORDER);
        c.drawRect(0, 77, w, 79, p);

        text(c, "CIVIC FA1", 34, 40, 28, TEXT, Paint.Align.LEFT, true);
        text(c, "i-VTEC • 1.8L R18A", 34, 64, 13, MUTED, Paint.Align.LEFT, false);

        String title = mode == Mode.STREET ? "STREET MODE" :
                mode == Mode.SPORT ? "SPORT MODE" : "DIAGNOSTICS MODE";

        String subtitle = mode == Mode.STREET ? "COMFORT EVERYDAY" :
                mode == Mode.SPORT ? "HIGHER STANDARDS" : "SYSTEM HEALTH MONITOR";

        text(c, title, w * 0.5f, 36, 29, GREEN, Paint.Align.CENTER, true);
        text(c, subtitle, w * 0.5f, 61, 12, MUTED, Paint.Align.CENTER, false);

        p.setColor(YELLOW);
        c.drawCircle(w - 355, 30, 7, p);
        text(c, "OBD: SIMULATOR",
                w - 335, 36, 18, TEXT, Paint.Align.LEFT, true);
        text(c, "TEST DATA ACTIVE",
                w - 335, 60, 12, MUTED, Paint.Align.LEFT, false);

        String clock = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        text(c, clock, w - 62, 38, 22, TEXT, Paint.Align.RIGHT, false);
    }

    private void drawStreet(Canvas c, float w, float h) {
        float navTop = h - 100f;
        float contentTop = 92f;

        RectF center = new RectF(w * 0.30f, contentTop + 10f, w * 0.70f, 355f);
        drawArcGauge(c, center, speed, 0, 160, true);

        text(c, String.valueOf(Math.round(speed)), center.centerX(), center.centerY() + 25f,
                104, TEXT, Paint.Align.CENTER, true);
        text(c, "km/h", center.centerX(), center.centerY() + 62f,
                26, MUTED, Paint.Align.CENTER, false);

        drawLargeMetric(c, 35, 350, w * 0.28f, 505, "COOLANT",
                String.format(Locale.US, "%.0f", coolant), "°C", 50, 130, coolant);

        drawLargeMetric(c, w * 0.72f, 350, w - 35, 505, "VOLTAGE",
                String.format(Locale.US, "%.1f", voltage), "V", 10, 16, voltage);

        drawSmallMetric(c, 35, 520, w * 0.34f, navTop - 10, "FUEL LEVEL",
                String.format(Locale.US, "%.0f", fuel), "%", fuel / 100f);

        drawSmallMetric(c, w * 0.35f, 520, w * 0.66f, navTop - 10, "TRIP A",
                String.format(Locale.US, "%.1f", trip), "km", 0.68f);

        drawSmallMetric(c, w * 0.67f, 520, w - 35, navTop - 10, "LOAD",
                String.format(Locale.US, "%.0f", load), "%", load / 100f);
    }

    private void drawSport(Canvas c, float w, float h) {
        float navTop = h - 100f;

        drawShiftLights(c, w, 91);

        RectF gauge = new RectF(w * 0.30f, 112, w * 0.70f, 360);
        drawArcGauge(c, gauge, rpm, 0, 8000, false);

        text(c, String.valueOf(Math.round(rpm)), gauge.centerX(), gauge.centerY() + 18,
                84, TEXT, Paint.Align.CENTER, true);
        text(c, "RPM", gauge.centerX(), gauge.centerY() + 54,
                24, MUTED, Paint.Align.CENTER, false);

        text(c, String.valueOf(Math.round(speed)), gauge.centerX(), 405,
                68, TEXT, Paint.Align.CENTER, true);
        text(c, "km/h", gauge.centerX() + 84, 396,
                22, MUTED, Paint.Align.LEFT, false);

        drawLargeMetric(c, 35, 315, w * 0.28f, 500, "COOLANT",
                String.format(Locale.US, "%.0f", coolant), "°C", 50, 130, coolant);

        drawLargeMetric(c, w * 0.72f, 315, w - 35, 500, "VOLTAGE",
                String.format(Locale.US, "%.1f", voltage), "V", 10, 16, voltage);

        drawSmallMetric(c, 35, 515, w * 0.34f, navTop - 10, "THROTTLE",
                String.format(Locale.US, "%.0f", throttle), "%", throttle / 100f);

        drawSmallMetric(c, w * 0.35f, 515, w * 0.66f, navTop - 10, "LOAD",
                String.format(Locale.US, "%.0f", load), "%", load / 100f);

        drawSmallMetric(c, w * 0.67f, 515, w - 35, navTop - 10, "INTAKE",
                String.format(Locale.US, "%.0f", intake), "°C", Math.min(1f, intake / 80f));
    }

    private void drawDiagnostics(Canvas c, float w, float h) {
        float navTop = h - 100f;

        drawStatusCard(c, 35, 100, w * 0.49f, 220, "OBD CONNECTION", "SIMULATOR",
                "Adapter: not connected\nLive data: disabled");

        drawStatusCard(c, w * 0.51f, 100, w - 35, 220, "ECU STATUS", "TEST MODE",
                "ECU profile: Honda PGM-FI\nValues: simulated");

        float gap = 12;
        float x = 35;
        float width = (w - 70 - gap * 4) / 5f;
        drawSmallMetric(c, x, 235, x + width, 350, "BATTERY",
                String.format(Locale.US, "%.1f", voltage), "V", voltage / 16f);
        x += width + gap;
        drawSmallMetric(c, x, 235, x + width, 350, "COOLANT",
                String.format(Locale.US, "%.0f", coolant), "°C", coolant / 130f);
        x += width + gap;
        drawSmallMetric(c, x, 235, x + width, 350, "INTAKE",
                String.format(Locale.US, "%.0f", intake), "°C", intake / 80f);
        x += width + gap;
        drawSmallMetric(c, x, 235, x + width, 350, "THROTTLE",
                String.format(Locale.US, "%.0f", throttle), "%", throttle / 100f);
        x += width + gap;
        drawSmallMetric(c, x, 235, x + width, 350, "ENGINE LOAD",
                String.format(Locale.US, "%.0f", load), "%", load / 100f);

        panel(c, 35, 365, w * 0.30f, navTop - 10);
        text(c, "FAULT CODES (DTC)", 60, 395, 18, TEXT, Paint.Align.LEFT, true);
        stroke.setColor(GREEN);
        stroke.setStrokeWidth(4);
        c.drawCircle(85, 465, 26, stroke);
        c.drawLine(73, 465, 83, 475, stroke);
        c.drawLine(83, 475, 100, 452, stroke);
        text(c, "NO TEST FAULTS", 125, 472, 22, GREEN, Paint.Align.LEFT, true);
        text(c, "Simulator only — ECU not queried.", 60, 515, 14, MUTED, Paint.Align.LEFT, false);

        panel(c, w * 0.31f, 365, w * 0.66f, navTop - 10);
        text(c, "READINESS MONITORS", w * 0.33f, 395, 18, TEXT, Paint.Align.LEFT, true);
        text(c, "SIMULATED", w * 0.33f, 424, 19, GREEN, Paint.Align.LEFT, true);
        String[] checks = {"Misfire Monitor", "Fuel System", "Catalyst Monitor",
                "Oxygen Sensor", "Evaporative System", "EGR System"};
        for (int i = 0; i < checks.length; i++) {
            float cx = w * 0.33f + (i % 2) * 175;
            float cy = 463 + (i / 2) * 31;
            p.setColor(GREEN);
            c.drawCircle(cx, cy, 7, p);
            text(c, "✓", cx, cy + 5, 13, BG, Paint.Align.CENTER, true);
            text(c, checks[i], cx + 17, cy + 5, 12, TEXT, Paint.Align.LEFT, false);
        }

        panel(c, w * 0.67f, 365, w - 35, navTop - 10);
        text(c, "LIVE SENSOR DATA", w * 0.69f, 395, 18, TEXT, Paint.Align.LEFT, true);
        String[][] rows = {
                {"RPM", String.format(Locale.US, "%.0f rpm", rpm)},
                {"Vehicle Speed", String.format(Locale.US, "%.0f km/h", speed)},
                {"Throttle", String.format(Locale.US, "%.0f %%", throttle)},
                {"Engine Load", String.format(Locale.US, "%.0f %%", load)},
                {"Coolant", String.format(Locale.US, "%.0f °C", coolant)},
                {"Voltage", String.format(Locale.US, "%.1f V", voltage)}
        };
        for (int i = 0; i < rows.length; i++) {
            float y = 430 + i * 26;
            text(c, rows[i][0], w * 0.69f, y, 12, MUTED, Paint.Align.LEFT, false);
            text(c, rows[i][1], w - 58, y, 12, TEXT, Paint.Align.RIGHT, true);
            p.setColor(Color.rgb(40, 50, 55));
            c.drawRect(w * 0.69f, y + 7, w - 58, y + 8, p);
        }
    }

    private void drawStatusCard(Canvas c, float l, float t, float r, float b,
                                String title, String value, String details) {
        panel(c, l, t, r, b);
        text(c, title, l + 24, t + 28, 16, MUTED, Paint.Align.LEFT, false);
        text(c, value, l + 24, t + 59, 24, GREEN, Paint.Align.LEFT, true);

        String[] lines = details.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            text(c, lines[i], l + 24, t + 84 + i * 19, 12, TEXT, Paint.Align.LEFT, false);
        }
    }

    private void drawShiftLights(Canvas c, float w, float y) {
        int count = 18;
        float segW = 31;
        float gap = 7;
        float total = count * segW + (count - 1) * gap;
        float start = (w - total) / 2f;
        int active = Math.min(count, Math.max(0, (int) (rpm / 8000f * count)));

        for (int i = 0; i < count; i++) {
            if (i < active) {
                if (i < 11) p.setColor(GREEN);
                else if (i < 15) p.setColor(YELLOW);
                else p.setColor(RED);
            } else {
                p.setColor(Color.rgb(42, 49, 53));
            }
            RectF rr = new RectF(start + i * (segW + gap), y, start + i * (segW + gap) + segW, y + 14);
            c.drawRoundRect(rr, 4, 4, p);
        }
    }

    private void drawArcGauge(Canvas c, RectF rect, float value, float min, float max, boolean street) {
        float start = 150;
        float sweep = 240;
        float frac = Math.max(0, Math.min(1, (value - min) / (max - min)));

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(street ? 18 : 20);
        stroke.setColor(Color.rgb(44, 54, 58));
        c.drawArc(rect, start, sweep, false, stroke);

        stroke.setStrokeWidth(street ? 20 : 22);
        stroke.setColor(GREEN);
        c.drawArc(rect, start, sweep * Math.min(frac, 0.72f), false, stroke);

        if (frac > 0.72f) {
            stroke.setColor(YELLOW);
            c.drawArc(rect, start + sweep * 0.72f, sweep * Math.min(frac - 0.72f, 0.16f), false, stroke);
        }
        if (frac > 0.88f) {
            stroke.setColor(RED);
            c.drawArc(rect, start + sweep * 0.88f, sweep * (frac - 0.88f), false, stroke);
        }

        stroke.setStrokeWidth(2f);
        stroke.setColor(BORDER);
        RectF outer = new RectF(rect.left - 12, rect.top - 12, rect.right + 12, rect.bottom + 12);
        c.drawArc(outer, start, sweep, false, stroke);

        int ticks = street ? 8 : 9;
        for (int i = 0; i < ticks; i++) {
            float a = (float) Math.toRadians(start + (sweep / (ticks - 1)) * i);
            float cx = rect.centerX();
            float cy = rect.centerY();
            float rx1 = rect.width() / 2f - 26;
            float ry1 = rect.height() / 2f - 26;
            float rx2 = rect.width() / 2f - 10;
            float ry2 = rect.height() / 2f - 10;

            float x1 = cx + (float) Math.cos(a) * rx1;
            float y1 = cy + (float) Math.sin(a) * ry1;
            float x2 = cx + (float) Math.cos(a) * rx2;
            float y2 = cy + (float) Math.sin(a) * ry2;

            p.setColor(TEXT);
            p.setStrokeWidth(i % 2 == 0 ? 4f : 2f);
            c.drawLine(x1, y1, x2, y2, p);
        }
    }

    private void drawLargeMetric(Canvas c, float l, float t, float r, float b,
                                 String title, String value, String unit,
                                 float min, float max, float current) {
        panel(c, l, t, r, b);
        text(c, title, l + 28, t + 34, 18, MUTED, Paint.Align.LEFT, true);
        text(c, value, l + 28, t + 91, 52, TEXT, Paint.Align.LEFT, true);
        text(c, unit, r - 38, t + 84, 24, TEXT, Paint.Align.RIGHT, false);

        float frac = Math.max(0, Math.min(1, (current - min) / (max - min)));
        progress(c, l + 28, b - 42, r - 28, b - 32, frac);
        text(c, String.format(Locale.US, "%.0f", min), l + 28, b - 11, 12, MUTED, Paint.Align.LEFT, false);
        text(c, String.format(Locale.US, "%.0f", max), r - 28, b - 11, 12, MUTED, Paint.Align.RIGHT, false);
    }

    private void drawSmallMetric(Canvas c, float l, float t, float r, float b,
                                 String title, String value, String unit, float frac) {
        panel(c, l, t, r, b);
        float boxH = b - t;
        float titleY = t + Math.min(28f, boxH * 0.30f);
        float valueY = t + Math.min(62f, boxH * 0.68f);
        float unitY = valueY - 3f;
        float valueSize = boxH < 100f ? 32f : 36f;

        text(c, title, l + 24, titleY, 15, MUTED, Paint.Align.LEFT, false);
        text(c, value, l + 24, valueY, valueSize, TEXT, Paint.Align.LEFT, true);
        text(c, unit, r - 28, unitY, 18, TEXT, Paint.Align.RIGHT, false);
        progress(c, l + 24, b - 20, r - 24, b - 12, frac);
    }

    private void drawBottomTabs(Canvas c, float w, float h) {
        float y = h - 100;
        float tabW = w / 3f;

        drawTab(c, 0, y, tabW, h, "STREET", mode == Mode.STREET);
        drawTab(c, tabW, y, tabW * 2, h, "SPORT", mode == Mode.SPORT);
        drawTab(c, tabW * 2, y, w, h, "DIAGNOSTICS", mode == Mode.DIAGNOSTICS);
    }

    private void drawTab(Canvas c, float l, float t, float r, float b, String name, boolean selected) {
        p.setColor(selected ? Color.rgb(10, 52, 35) : Color.rgb(11, 15, 18));
        c.drawRect(l, t, r, b, p);

        stroke.setStrokeWidth(selected ? 3f : 1.4f);
        stroke.setColor(selected ? GREEN : BORDER);
        c.drawRect(l + 1, t + 1, r - 1, b - 1, stroke);

        int color = selected ? GREEN : TEXT;
        text(c, name, (l + r) / 2f, t + 45, 24, color, Paint.Align.CENTER, true);

        String sub = name.equals("STREET") ? "COMFORT EVERYDAY" :
                name.equals("SPORT") ? "MORE RESPONSE" : "KNOW YOUR CAR";
        text(c, sub, (l + r) / 2f, t + 70, 12, selected ? GREEN : MUTED, Paint.Align.CENTER, false);
    }

    private void panel(Canvas c, float l, float t, float r, float b) {
        RectF rr = new RectF(l, t, r, b);
        p.setColor(PANEL);
        p.setShadowLayer(12, 0, 4, Color.argb(110, 0, 0, 0));
        c.drawRoundRect(rr, 15, 15, p);
        p.clearShadowLayer();

        stroke.setColor(BORDER);
        stroke.setStrokeWidth(1.5f);
        c.drawRoundRect(rr, 15, 15, stroke);
    }

    private void progress(Canvas c, float l, float t, float r, float b, float frac) {
        frac = Math.max(0, Math.min(1, frac));
        RectF bg = new RectF(l, t, r, b);
        p.setColor(Color.rgb(43, 52, 57));
        c.drawRoundRect(bg, 6, 6, p);

        RectF fg = new RectF(l, t, l + (r - l) * frac, b);
        p.setColor(GREEN);
        c.drawRoundRect(fg, 6, 6, p);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color,
                      Paint.Align align, boolean bold) {
        p.setShader(null);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(align);
        p.setTypeface(bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                : Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        c.drawText(s, x, y, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;

        float h = getHeight();
        float w = getWidth();

        if (e.getY() >= h - 100) {
            if (e.getX() < w / 3f) mode = Mode.STREET;
            else if (e.getX() < w * 2f / 3f) mode = Mode.SPORT;
            else mode = Mode.DIAGNOSTICS;

            invalidate();
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
