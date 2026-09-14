package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Random;

public class DashboardView extends View {

    private enum Mode { STREET, SPORT, DIAGNOSTICS }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private final Bitmap streetBg;
    private final Bitmap sportBg;
    private final Bitmap diagnosticsBg;

    private Mode mode = Mode.STREET;

    private float rpm = 3250f;
    private float speed = 72f;
    private float coolant = 89f;
    private float voltage = 14.1f;
    private float throttle = 24f;
    private float load = 32f;
    private float intake = 31f;
    private float fuel = 68f;
    private float trip = 248.7f;

    private float targetRpm = 3250f;
    private float targetSpeed = 72f;
    private float targetThrottle = 24f;
    private float targetLoad = 32f;

    private boolean simulationRunning = false;
    private String overlayTitle = null;
    private String overlayValue = null;
    private String overlayBody = null;

    private static final int WHITE = Color.rgb(242, 246, 249);
    private static final int MUTED = Color.rgb(170, 183, 192);
    private static final int GREEN = Color.rgb(33, 241, 150);
    private static final int PANEL = Color.rgb(7, 16, 18);

    private final Runnable simulationTick = new Runnable() {
        @Override
        public void run() {
            if (!simulationRunning) return;

            if (Math.abs(rpm - targetRpm) < 100f) {
                targetRpm = 900f + random.nextInt(5200);
                targetSpeed = Math.max(0f, Math.min(135f,
                        (targetRpm - 900f) / 42f + random.nextInt(15)));
                targetThrottle = 8f + random.nextInt(66);
                targetLoad = 20f + random.nextInt(66);
            }

            rpm += (targetRpm - rpm) * 0.045f;
            speed += (targetSpeed - speed) * 0.035f;
            throttle += (targetThrottle - throttle) * 0.045f;
            load += (targetLoad - load) * 0.04f;

            coolant += ((89f + (rpm > 4500f ? 3f : 0f)) - coolant) * 0.008f;
            voltage += ((13.9f + (rpm / 8000f) * 0.35f) - voltage) * 0.02f;
            intake += ((30f + (rpm / 8000f) * 7f) - intake) * 0.012f;
            trip += speed / 9000f;

            invalidate();
            handler.postDelayed(this, 90);
        }
    };

    public DashboardView(Context context) {
        super(context);
        streetBg = BitmapFactory.decodeResource(getResources(), R.drawable.mode_street);
        sportBg = BitmapFactory.decodeResource(getResources(), R.drawable.mode_sport);
        diagnosticsBg = BitmapFactory.decodeResource(getResources(), R.drawable.mode_diagnostics);
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
        Bitmap bg = mode == Mode.STREET ? streetBg : mode == Mode.SPORT ? sportBg : diagnosticsBg;
        c.drawBitmap(bg, null, new RectF(0, 0, getWidth(), getHeight()), p);

        if (mode == Mode.STREET) {
            drawStreetLiveValues(c);
        } else if (mode == Mode.SPORT) {
            drawSportLiveValues(c);
        } else {
            drawDiagnosticsLiveValues(c);
        }

        if (overlayTitle != null) drawOverlay(c);
    }

    private float sx(float x) { return x * getWidth() / 1280f; }
    private float sy(float y) { return y * getHeight() / 720f; }

    private void cover(Canvas c, float l, float t, float r, float b, float radius) {
        p.setColor(Color.argb(235, 5, 13, 15));
        c.drawRoundRect(new RectF(sx(l), sy(t), sx(r), sy(b)), sx(radius), sx(radius), p);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold) {
        p.setShader(null);
        p.setColor(color);
        p.setTextAlign(align);
        p.setTextSize(sx(size));
        p.setTypeface(android.graphics.Typeface.create("sans-serif", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        c.drawText(s, sx(x), sy(y), p);
    }

    private void drawStreetLiveValues(Canvas c) {
        cover(c, 495, 215, 785, 365, 18);
        text(c, String.valueOf(Math.round(speed)), 640, 315, 92, WHITE, Paint.Align.CENTER, true);
        text(c, "km/h", 640, 354, 25, MUTED, Paint.Align.CENTER, false);

        cover(c, 565, 405, 715, 474, 12);
        text(c, String.valueOf(Math.round(rpm)), 640, 451, 30, WHITE, Paint.Align.CENTER, true);
        text(c, "RPM", 640, 474, 12, MUTED, Paint.Align.CENTER, false);

        metricValue(c, 192, 408, String.format(Locale.US, "%.0f", coolant), "°C", 58);
        metricValue(c, 1024, 408, String.format(Locale.US, "%.1f", voltage), "V", 58);
        metricValue(c, 180, 558, String.format(Locale.US, "%.0f", fuel), "%", 34);
        metricValue(c, 630, 558, String.format(Locale.US, "%.1f", trip), "km", 32);
        metricValue(c, 1018, 558, String.format(Locale.US, "%.0f", load), "%", 34);
    }

    private void drawSportLiveValues(Canvas c) {
        cover(c, 500, 220, 780, 375, 18);
        text(c, String.valueOf(Math.round(rpm)), 640, 315, 70, WHITE, Paint.Align.CENTER, true);
        text(c, "RPM", 640, 356, 24, MUTED, Paint.Align.CENTER, false);

        cover(c, 560, 393, 720, 468, 14);
        text(c, String.valueOf(Math.round(speed)), 640, 445, 58, WHITE, Paint.Align.CENTER, true);
        text(c, "km/h", 706, 444, 20, MUTED, Paint.Align.LEFT, false);

        metricValue(c, 195, 398, String.format(Locale.US, "%.0f", coolant), "°C", 56);
        metricValue(c, 1025, 398, String.format(Locale.US, "%.1f", voltage), "V", 56);
        metricValue(c, 181, 548, String.format(Locale.US, "%.0f", throttle), "%", 32);
        metricValue(c, 628, 548, String.format(Locale.US, "%.0f", load), "%", 32);
        metricValue(c, 1015, 548, String.format(Locale.US, "%.0f", intake), "°C", 32);
    }

    private void drawDiagnosticsLiveValues(Canvas c) {
        metricValue(c, 145, 312, String.format(Locale.US, "%.1f", voltage), "V", 30);
        metricValue(c, 380, 312, String.format(Locale.US, "%.0f", coolant), "°C", 30);
        metricValue(c, 600, 312, String.format(Locale.US, "%.0f", intake), "°C", 30);
        metricValue(c, 835, 312, String.format(Locale.US, "%.0f", throttle), "%", 30);
        metricValue(c, 1068, 312, String.format(Locale.US, "%.0f", load), "%", 30);

        cover(c, 886, 418, 1230, 617, 10);
        float y = 442;
        drawSensorRow(c, "RPM", String.format(Locale.US, "%.0f rpm", rpm), y); y += 22;
        drawSensorRow(c, "Vehicle Speed", String.format(Locale.US, "%.0f km/h", speed), y); y += 22;
        drawSensorRow(c, "Throttle", String.format(Locale.US, "%.0f %%", throttle), y); y += 22;
        drawSensorRow(c, "Engine Load", String.format(Locale.US, "%.0f %%", load), y); y += 22;
        drawSensorRow(c, "Coolant", String.format(Locale.US, "%.0f °C", coolant), y); y += 22;
        drawSensorRow(c, "Voltage", String.format(Locale.US, "%.2f V", voltage), y); y += 22;
        drawSensorRow(c, "Intake Temp", String.format(Locale.US, "%.0f °C", intake), y); y += 22;
        drawSensorRow(c, "MAF", String.format(Locale.US, "%.1f g/s", Math.max(1.0f, rpm / 1550f)), y);
    }

    private void drawSensorRow(Canvas c, String label, String value, float y) {
        text(c, label, 898, y, 13, MUTED, Paint.Align.LEFT, false);
        text(c, value, 1210, y, 13, WHITE, Paint.Align.RIGHT, false);
    }

    private void metricValue(Canvas c, float x, float y, String value, String unit, float size) {
        float width = size * (value.length() * 0.62f + 1.4f);
        cover(c, x - width * 0.55f, y - size * 0.75f, x + width * 0.65f, y + size * 0.22f, 8);
        text(c, value, x, y, size, WHITE, Paint.Align.CENTER, true);
        text(c, unit, x + width * 0.38f, y - 2, size * 0.34f, MUTED, Paint.Align.LEFT, false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;

        if (overlayTitle != null) {
            overlayTitle = null;
            overlayValue = null;
            overlayBody = null;
            invalidate();
            return true;
        }

        float x = event.getX() * 1280f / getWidth();
        float y = event.getY() * 720f / getHeight();

        if (y >= 625f) {
            if (x < 426f) mode = Mode.STREET;
            else if (x < 853f) mode = Mode.SPORT;
            else mode = Mode.DIAGNOSTICS;
            invalidate();
            return true;
        }

        if (mode == Mode.STREET) {
            if (in(x, y, 30, 300, 380, 505)) showDetail("COOLANT TEMP", String.format(Locale.US, "%.0f °C", coolant), "Simulated temperature. Real ECU data will replace this after OBD connection.");
            else if (in(x, y, 900, 300, 1250, 505)) showDetail("VOLTAGE", String.format(Locale.US, "%.2f V", voltage), "Simulated charging voltage. Live reading will be enabled with the OBD adapter.");
            else if (in(x, y, 25, 505, 430, 625)) showDetail("FUEL LEVEL", String.format(Locale.US, "%.0f %%", fuel), "Visual demo value. Fuel PID support depends on the vehicle ECU.");
            else if (in(x, y, 430, 505, 850, 625)) showDetail("TRIP A", String.format(Locale.US, "%.1f km", trip), "Trip distance calculated by the simulator in this build.");
            else if (in(x, y, 850, 505, 1255, 625)) showDetail("ENGINE LOAD", String.format(Locale.US, "%.0f %%", load), "Calculated engine load. OBD PID 0104 in live mode.");
        } else if (mode == Mode.SPORT) {
            if (in(x, y, 20, 300, 390, 500)) showDetail("COOLANT TEMP", String.format(Locale.US, "%.0f °C", coolant), "Performance monitoring value. Simulator active.");
            else if (in(x, y, 890, 300, 1255, 500)) showDetail("VOLTAGE", String.format(Locale.US, "%.2f V", voltage), "Charging system value. Simulator active.");
            else if (in(x, y, 20, 500, 430, 625)) showDetail("THROTTLE", String.format(Locale.US, "%.0f %%", throttle), "Throttle position. OBD PID 0111 in live mode.");
            else if (in(x, y, 430, 500, 850, 625)) showDetail("ENGINE LOAD", String.format(Locale.US, "%.0f %%", load), "Calculated engine load. OBD PID 0104 in live mode.");
            else if (in(x, y, 850, 500, 1255, 625)) showDetail("INTAKE TEMP", String.format(Locale.US, "%.0f °C", intake), "Intake air temperature. OBD PID 010F in live mode.");
        } else {
            if (in(x, y, 200, 80, 615, 245)) showDetail("OBD CONNECTION", "SIMULATOR", "Adapter is not connected. Live OBD transport will be added after the exact adapter type is confirmed.");
            else if (in(x, y, 620, 80, 1035, 245)) showDetail("ECU STATUS", "SIMULATED", "Honda PGM-FI profile is shown for UI testing. ECU has not been queried.");
            else if (in(x, y, 25, 390, 370, 625)) showDetail("FAULT CODES (DTC)", "NO TEST FAULTS", "This build does not claim real DTC status. Reading and clearing DTCs will be enabled in live OBD mode.");
            else if (in(x, y, 375, 390, 850, 625)) showDetail("READINESS MONITORS", "SIMULATED", "Monitor states are visual test data only until the vehicle ECU is connected.");
            else if (in(x, y, 850, 390, 1255, 625)) showDetail("LIVE SENSOR DATA", "SIMULATOR", "These values are generated locally for UI testing. No ECU is currently queried.");
        }

        return true;
    }

    private boolean in(float x, float y, float l, float t, float r, float b) {
        return x >= l && x <= r && y >= t && y <= b;
    }

    private void showDetail(String title, String value, String body) {
        overlayTitle = title;
        overlayValue = value;
        overlayBody = body;
        invalidate();
    }

    private void drawOverlay(Canvas c) {
        p.setColor(Color.argb(185, 0, 0, 0));
        c.drawRect(0, 0, getWidth(), getHeight(), p);

        RectF card = new RectF(sx(280), sy(185), sx(1000), sy(535));
        p.setColor(Color.argb(248, 6, 18, 20));
        c.drawRoundRect(card, sx(22), sx(22), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sx(2));
        p.setColor(GREEN);
        c.drawRoundRect(card, sx(22), sx(22), p);
        p.setStyle(Paint.Style.FILL);

        text(c, overlayTitle, 640, 245, 28, GREEN, Paint.Align.CENTER, true);
        text(c, overlayValue, 640, 330, 54, WHITE, Paint.Align.CENTER, true);

        drawWrapped(c, overlayBody, 640, 390, 18, MUTED, 620);
        text(c, "Tap anywhere to close", 640, 500, 15, MUTED, Paint.Align.CENTER, false);
    }

    private void drawWrapped(Canvas c, String text, float cx, float y, float size, int color, float maxWidth) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float currentY = y;
        p.setTextSize(sx(size));
        p.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (p.measureText(test) > sx(maxWidth)) {
                text(c, line.toString(), cx, currentY, size, color, Paint.Align.CENTER, false);
                currentY += size * 1.5f;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) text(c, line.toString(), cx, currentY, size, color, Paint.Align.CENTER, false);
    }
}
