package com.civicfa1.dashboard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Random;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DashboardView extends View implements ObdManager.Listener {

    private enum Mode { STREET, SPORT, DIAGNOSTICS }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final MainActivity activity;
    private final ObdManager obdManager;

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
    private float fuelEconomy = 6.2f;
    private float avgSpeed = 79f;
    private float maf = 2.1f;
    private float tripSeconds = 3 * 3600f + 14 * 60f;
    private long lastTickMs = 0L;

    private float targetRpm = 3250f;
    private float targetSpeed = 72f;
    private float targetThrottle = 24f;
    private float targetLoad = 32f;

    private boolean simulationRunning = false;
    private boolean liveConnected = false;
    private ObdManager.State obdState = ObdManager.State.SIMULATOR;
    private String obdDetail = "TEST MODE";
    private String obdTransport = "SIMULATOR";
    private String overlayTitle = null;
    private String overlayValue = null;
    private String overlayBody = null;
    private boolean obdSelectorOpen = false;

    private static final int WHITE = Color.rgb(242, 246, 249);
    private static final int MUTED = Color.rgb(170, 183, 192);
    private static final int GREEN = Color.rgb(33, 241, 150);
    private static final int PANEL = Color.rgb(7, 16, 18);

    private final Runnable simulationTick = new Runnable() {
        @Override
        public void run() {
            if (!simulationRunning) return;

            long now = System.currentTimeMillis();
            if (lastTickMs == 0L) lastTickMs = now;
            float dtSec = Math.max(0.02f, Math.min(0.5f, (now - lastTickMs) / 1000f));
            lastTickMs = now;

            if (!liveConnected) {
                if (Math.abs(rpm - targetRpm) < 100f) {
                    targetRpm = 900f + random.nextInt(5200);
                    targetSpeed = Math.max(0f, Math.min(135f,
                            (targetRpm - 900f) / 42f + random.nextInt(15)));
                    float rpmDemand = Math.max(0f, Math.min(1f, (targetRpm - 900f) / 5200f));
                    targetThrottle = 8f + rpmDemand * 72f + random.nextInt(8);
                    targetLoad = 18f + rpmDemand * 68f + random.nextInt(8);
                }

                rpm += (targetRpm - rpm) * 0.045f;
                speed += (targetSpeed - speed) * 0.035f;
                throttle += (targetThrottle - throttle) * 0.045f;
                load += (targetLoad - load) * 0.04f;
                coolant += ((89f + (rpm > 4500f ? 3f : 0f)) - coolant) * 0.008f;
                voltage += ((13.9f + (rpm / 8000f) * 0.35f) - voltage) * 0.02f;
                intake += ((30f + (rpm / 8000f) * 7f) - intake) * 0.012f;
                maf = Math.max(1.0f, rpm / 1550f);
            }

            updateTripValues(dtSec);
            invalidate();
            handler.postDelayed(this, 90);
        }
    };

    public DashboardView(MainActivity activity) {
        super(activity);
        this.activity = activity;
        this.obdManager = new ObdManager(activity, this);
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
            lastTickMs = System.currentTimeMillis();
            handler.postDelayed(simulationTick, 120);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        simulationRunning = false;
        handler.removeCallbacks(simulationTick);
        obdManager.stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Bitmap bg = mode == Mode.STREET ? streetBg : mode == Mode.SPORT ? sportBg : diagnosticsBg;
        c.drawBitmap(bg, null, new RectF(0, 0, getWidth(), getHeight()), p);
        drawTopStatus(c);

        if (mode == Mode.STREET) {
            drawStreetLiveValues(c);
        } else if (mode == Mode.SPORT) {
            drawSportLiveValues(c);
        } else {
            drawDiagnosticsLiveValues(c);
        }

        drawUnifiedModeTabs(c);
        if (obdSelectorOpen) drawObdSelector(c);
        if (overlayTitle != null) drawOverlay(c);
    }

    private float sx(float x) { return x * getWidth() / 1280f; }
    private float sy(float y) { return y * getHeight() / 720f; }

    private void cover(Canvas c, float l, float t, float r, float b, float radius) {
        p.setShader(null);
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(246, 4, 12, 15));
        c.drawRoundRect(new RectF(sx(l), sy(t), sx(r), sy(b)), sx(radius), sx(radius), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sx(1.2f));
        p.setColor(Color.argb(120, 60, 215, 214));
        c.drawRoundRect(new RectF(sx(l), sy(t), sx(r), sy(b)), sx(radius), sx(radius), p);
        p.setStyle(Paint.Style.FILL);
    }

    private void softMask(Canvas c, float l, float t, float r, float b, float radius) {
        p.setShader(null);
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(238, 4, 11, 14));
        c.drawRoundRect(new RectF(sx(l), sy(t), sx(r), sy(b)), sx(radius), sx(radius), p);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold) {
        p.setShader(null);
        p.clearShadowLayer();
        p.setColor(color);
        p.setTextAlign(align);
        p.setTextSize(sx(size));
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, sx(x), sy(y), p);
    }

    private void liveText(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextAlign(align);
        p.setTextSize(sx(size));
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        p.setShadowLayer(sx(6f), 0f, 0f, Color.argb(185, 30, 245, 190));
        c.drawText(s, sx(x), sy(y), p);
        p.clearShadowLayer();
    }

    private void unitText(Canvas c, String s, float x, float y, float size) {
        p.setShader(null);
        p.clearShadowLayer();
        p.setColor(Color.rgb(205, 221, 228));
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(sx(size));
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.drawText(s, sx(x), sy(y), p);
    }

    private void progress(Canvas c, float l, float y, float r, float value, float min, float max) {
        float ratio = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(35, 57, 68));
        c.drawRoundRect(new RectF(sx(l), sy(y), sx(r), sy(y + 8)), sx(4), sx(4), p);
        p.setColor(GREEN);
        p.setShadowLayer(sx(5f), 0, 0, Color.argb(140, 32, 255, 191));
        c.drawRoundRect(new RectF(sx(l), sy(y), sx(l + (r - l) * ratio), sy(y + 8)), sx(4), sx(4), p);
        p.clearShadowLayer();
    }

    private void drawStreetLiveValues(Canvas c) {
        // v0.3.2: backgrounds contain EMPTY data fields.
        // One live value is drawn in each block, in the exact place where the old baked number used to be.
        liveText(c, String.valueOf(Math.round(speed)), 640, 315, 88, WHITE, Paint.Align.CENTER);
        text(c, "km/h", 640, 350, 24, MUTED, Paint.Align.CENTER, false);

        liveText(c, String.valueOf(Math.round(rpm)), 640, 458, 29, WHITE, Paint.Align.CENTER);
        text(c, "RPM", 640, 476, 11, MUTED, Paint.Align.CENTER, false);

        drawMetric(c, 205, 410, coolant, "°C", 54, 50, 130, 55, 438, 355);
        drawMetric(c, 1060, 410, voltage, "V", 54, 10, 16, 923, 438, 1228);
        drawMetric(c, 190, 560, fuel, "%", 31, 0, 100, 55, 568, 408);

        liveText(c, String.format(Locale.US, "%.1f", trip), 628, 558, 31, WHITE, Paint.Align.CENTER);
        unitText(c, "km", 705, 558, 15);
        String tripTime = String.format(Locale.US, "%02d:%02d h", (int)(tripSeconds / 3600f), ((int)(tripSeconds / 60f)) % 60);
        text(c, String.format(Locale.US, "%.1f L/100km   |   %s   |   Ø %.0f km/h", fuelEconomy, tripTime, avgSpeed),
                640, 590, 14, MUTED, Paint.Align.CENTER, false);

        drawMetric(c, 1005, 560, load, "%", 31, 0, 100, 875, 568, 1227);
    }

    private void drawSportLiveValues(Canvas c) {
        // Dynamic tachometer and shift-light bar. They are driven by the SAME RPM value
        // that is shown in the centre, so there is no fake second data source.
        drawSportTachometerActivity(c);

        liveText(c, String.valueOf(Math.round(rpm)), 640, 305, 68, WHITE, Paint.Align.CENTER);
        text(c, "RPM", 640, 337, 22, MUTED, Paint.Align.CENTER, false);

        // Remove the old bright green speed pedestal without changing the layout geometry.
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(238, 5, 17, 19));
        c.drawRoundRect(new RectF(sx(548), sy(374), sx(735), sy(455)), sx(7), sx(7), p);
        liveText(c, String.valueOf(Math.round(speed)), 625, 435, 55, WHITE, Paint.Align.CENTER);
        unitText(c, "km/h", 700, 435, 18);

        drawMetric(c, 205, 405, coolant, "°C", 52, 50, 130, 55, 420, 359);
        drawMetric(c, 1070, 405, voltage, "V", 52, 10, 16, 912, 420, 1225);
        drawMetric(c, 205, 530, throttle, "%", 31, 0, 100, 55, 542, 405);
        drawMetric(c, 625, 530, load, "%", 31, 0, 100, 468, 542, 800);
        drawMetric(c, 1025, 530, intake, "°C", 31, -20, 80, 871, 542, 1225);
    }


    /**
     * Makes the Sport tachometer itself "live" without changing the screen design.
     * The illuminated arc and the upper shift-light row are both derived from rpm.
     */
    private void drawSportTachometerActivity(Canvas c) {
        float clampedRpm = Math.max(0f, Math.min(8000f, rpm));
        float ratio = clampedRpm / 8000f;

        // ---- Active tachometer ring ----
        // Approximate the existing Sport dial geometry in the 1280x720 design canvas.
        RectF dial = new RectF(sx(438), sy(116), sx(842), sy(520));
        float startAngle = 145f;
        float totalSweep = 250f;
        float activeSweep = totalSweep * ratio;

        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sx(9f));

        // A subtle dark pass over the full track makes the old baked illumination read as "off".
        p.clearShadowLayer();
        p.setColor(Color.argb(145, 4, 14, 17));
        c.drawArc(dial, startAngle, totalSweep, false, p);

        // Paint active RPM in three design-matching zones.
        drawRpmArcZone(c, dial, startAngle, activeSweep, 0f, 0.625f,
                Color.rgb(26, 242, 145));              // 0-5000 green
        drawRpmArcZone(c, dial, startAngle, activeSweep, 0.625f, 0.775f,
                Color.rgb(245, 224, 54));              // 5000-6200 yellow
        drawRpmArcZone(c, dial, startAngle, activeSweep, 0.775f, 1.0f,
                Color.rgb(255, 54, 50));               // 6200-8000 red

        p.clearShadowLayer();
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);

        // ---- Upper sequential shift-light bar ----
        final int segmentCount = 14;
        final float left = 366f;
        final float right = 914f;
        final float gap = 7f;
        final float segW = (right - left - gap * (segmentCount - 1)) / segmentCount;
        final float top = 82f;
        final float bottom = 96f;

        // Lights intentionally start waking up near 1800 RPM rather than at idle.
        float shiftRatio = Math.max(0f, Math.min(1f, (clampedRpm - 1800f) / 5000f));
        int lit = Math.round(shiftRatio * segmentCount);

        for (int i = 0; i < segmentCount; i++) {
            float l = left + i * (segW + gap);
            RectF seg = new RectF(sx(l), sy(top), sx(l + segW), sy(bottom));

            if (i < lit) {
                int color;
                if (i < 8) color = Color.rgb(24, 244, 148);
                else if (i < 11) color = Color.rgb(248, 226, 57);
                else color = Color.rgb(255, 65, 54);
                p.setColor(color);
                p.setShadowLayer(sx(7f), 0f, 0f, color);
            } else {
                // Opaque enough to hide the static "on" pixels baked into the old image.
                p.clearShadowLayer();
                p.setColor(Color.argb(225, 7, 18, 21));
            }
            c.drawRoundRect(seg, sx(3f), sx(3f), p);
        }
        p.clearShadowLayer();
    }

    private void drawRpmArcZone(Canvas c, RectF dial, float startAngle, float activeSweep,
                                float zoneStartRatio, float zoneEndRatio, int color) {
        float totalSweep = 250f;
        float zoneStart = totalSweep * zoneStartRatio;
        float zoneEnd = totalSweep * zoneEndRatio;
        float visibleEnd = Math.min(activeSweep, zoneEnd);
        if (visibleEnd <= zoneStart) return;

        p.setColor(color);
        p.setShadowLayer(sx(10f), 0f, 0f, color);
        c.drawArc(dial, startAngle + zoneStart, visibleEnd - zoneStart, false, p);
        p.clearShadowLayer();
    }

    private void drawDiagnosticsLiveValues(Canvas c) {
        // v0.4.0 restores the cleaner v0.3.3 diagnostics composition.
        // Only the numeric fields and live sensor table are dynamic.
        int stateColor = obdState == ObdManager.State.ERROR ? Color.rgb(255, 88, 74) : GREEN;
        String connection = obdState == ObdManager.State.ECU_CONNECTED ? "CONNECTED" :
                obdState == ObdManager.State.SEARCHING ? "SEARCHING" :
                obdState == ObdManager.State.ADAPTER_FOUND ? "ADAPTER FOUND" :
                obdState == ObdManager.State.CONNECTING ? "CONNECTING" :
                obdState == ObdManager.State.ERROR ? "ERROR" : "SIMULATOR";
        text(c, connection, 325, 145, 24, stateColor, Paint.Align.LEFT, true);
        text(c, obdTransport, 325, 174, 13, MUTED, Paint.Align.LEFT, false);
        text(c, obdDetail, 325, 198, 12.5f, MUTED, Paint.Align.LEFT, false);

        text(c, liveConnected ? "LIVE ECU" : "SIMULATED", 738, 145, 24, liveConnected ? GREEN : MUTED, Paint.Align.LEFT, true);
        text(c, "PGM-FI (Honda)", 738, 174, 13, MUTED, Paint.Align.LEFT, false);
        text(c, liveConnected ? "ELM327 / Vgate live data" : "Local simulator", 738, 198, 12.5f, MUTED, Paint.Align.LEFT, false);
        drawMetric(c, 150, 317, voltage, "V", 29, 10, 16, 50, 333, 246);
        drawMetric(c, 395, 317, coolant, "°C", 29, 50, 130, 297, 333, 496);
        drawMetric(c, 635, 317, intake, "°C", 29, -20, 80, 544, 333, 744);
        drawMetric(c, 875, 317, throttle, "%", 29, 0, 100, 786, 333, 985);
        drawMetric(c, 1120, 317, load, "%", 29, 0, 100, 1033, 333, 1230);

        float y = 432;
        drawSensorRow(c, "RPM", String.format(Locale.US, "%.0f rpm", rpm), y); y += 21;
        drawSensorRow(c, "Vehicle Speed", String.format(Locale.US, "%.0f km/h", speed), y); y += 21;
        drawSensorRow(c, "Throttle", String.format(Locale.US, "%.0f %%", throttle), y); y += 21;
        drawSensorRow(c, "Engine Load", String.format(Locale.US, "%.0f %%", load), y); y += 21;
        drawSensorRow(c, "Coolant", String.format(Locale.US, "%.0f °C", coolant), y); y += 21;
        drawSensorRow(c, "Voltage", String.format(Locale.US, "%.2f V", voltage), y); y += 21;
        drawSensorRow(c, "Intake Temp", String.format(Locale.US, "%.0f °C", intake), y); y += 21;
        drawSensorRow(c, "MAF", String.format(Locale.US, "%.1f g/s", maf), y);
    }

    private void drawSensorRow(Canvas c, String label, String value, float y) {
        text(c, label, 905, y, 12, MUTED, Paint.Align.LEFT, false);
        liveText(c, value, 1212, y, 12.5f, WHITE, Paint.Align.RIGHT);
    }

    private void drawMetric(Canvas c, float x, float y, float raw, String unit, float size,
                            float min, float max, float barL, float barY, float barR) {
        String value;
        if ("V".equals(unit)) value = String.format(Locale.US, "%.1f", raw);
        else if ("km".equals(unit)) value = String.format(Locale.US, "%.1f", raw);
        else value = String.format(Locale.US, "%.0f", raw);

        // IMPORTANT: no cover/softMask here. The background is already clean.
        // This is the single live number for this card.
        liveText(c, value, x, y, size, WHITE, Paint.Align.CENTER);

        float valueWidth = Math.max(60f, size * Math.max(1.25f, value.length() * 0.58f));
        unitText(c, unit, x + valueWidth * 0.55f, y - 2, size * 0.32f);
        progress(c, barL, barY, barR, raw, min, max);
    }

    private void updateTripValues(float dtSec) {
        trip += speed * dtSec / 3600f;
        tripSeconds += dtSec;
        float hours = Math.max(0.001f, tripSeconds / 3600f);
        avgSpeed = Math.max(0f, trip / hours);

        if (maf > 0.1f && speed > 4f) {
            // Approximate gasoline consumption from MAF using stoichiometric AFR 14.7 and fuel density 745 g/L.
            float litersPerHour = maf * 3600f / (14.7f * 745f);
            float instant = litersPerHour * 100f / speed;
            if (instant > 0.5f && instant < 50f) fuelEconomy += (instant - fuelEconomy) * 0.025f;
        } else if (!liveConnected) {
            float demo = 5.2f + throttle * 0.045f + load * 0.018f;
            fuelEconomy += (demo - fuelEconomy) * 0.02f;
        }
    }

    private void drawTopStatus(Canvas c) {
        int color = GREEN;
        String label = "OBD: SIMULATOR";
        String sub = "TEST MODE - TAP TO CONNECT";
        if (obdState == ObdManager.State.SEARCHING) { label = "OBD: SEARCHING"; color = Color.rgb(255, 214, 74); sub = obdDetail; }
        else if (obdState == ObdManager.State.ADAPTER_FOUND) { label = "OBD: FOUND"; color = Color.rgb(78, 220, 255); sub = obdDetail; }
        else if (obdState == ObdManager.State.CONNECTING) { label = "OBD: CONNECTING"; color = Color.rgb(255, 214, 74); sub = obdDetail; }
        else if (obdState == ObdManager.State.ECU_CONNECTED) { label = "OBD: CONNECTED"; color = GREEN; sub = obdTransport + " • LIVE ECU"; }
        else if (obdState == ObdManager.State.ERROR) { label = "OBD: ERROR"; color = Color.rgb(255, 88, 74); sub = obdDetail; }

        p.setColor(color);
        c.drawCircle(sx(904), sy(24), sx(7), p);
        text(c, label, 930, 30, 17, color, Paint.Align.LEFT, true);
        text(c, sub, 930, 50, 10.5f, MUTED, Paint.Align.LEFT, false);
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        text(c, time, 1165, 32, 20, WHITE, Paint.Align.CENTER, true);
    }

    private void startObdConnection() {
        obdSelectorOpen = true;
        invalidate();
    }

    private void connectSelectedObd(int option) {
        obdSelectorOpen = false;
        if (option != 3 && !activity.hasObdBluetoothPermissions()) {
            showDetail("OBD CONNECTION", "PERMISSION REQUIRED", "Allow Bluetooth permission, then tap OBD again.");
            return;
        }
        if (option == 0) obdManager.startAutoConnect();
        else if (option == 1) obdManager.startBleConnect();
        else if (option == 2) obdManager.startClassicConnect();
        else if (option == 3) obdManager.startWifiConnect();
        invalidate();
    }

    public void onBluetoothPermissionResult(boolean granted) {
        if (granted) {
            obdDetail = "Bluetooth permission ready - tap OBD to connect";
        } else {
            obdState = ObdManager.State.ERROR;
            obdDetail = "Bluetooth permission denied";
        }
        invalidate();
    }

    @Override
    public void onObdState(ObdManager.State state, String detail, String transport) {
        obdState = state;
        obdDetail = detail == null ? "" : detail;
        obdTransport = transport == null ? "AUTO" : transport;
        liveConnected = state == ObdManager.State.ECU_CONNECTED;
        invalidate();
    }

    @Override
    public void onTelemetry(ObdManager.Telemetry t) {
        if (!Float.isNaN(t.rpm)) rpm = t.rpm;
        if (!Float.isNaN(t.speed)) speed = t.speed;
        if (!Float.isNaN(t.coolant)) coolant = t.coolant;
        if (!Float.isNaN(t.throttle)) throttle = t.throttle;
        if (!Float.isNaN(t.load)) load = t.load;
        if (!Float.isNaN(t.intake)) intake = t.intake;
        if (!Float.isNaN(t.voltage)) voltage = t.voltage;
        if (!Float.isNaN(t.maf)) maf = t.maf;
        if (!Float.isNaN(t.fuel)) fuel = t.fuel;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;

        float x = event.getX() * 1280f / getWidth();
        float y = event.getY() * 720f / getHeight();

        if (obdSelectorOpen) {
            if (in(x, y, 315, 245, 965, 315)) connectSelectedObd(0);
            else if (in(x, y, 315, 325, 965, 395)) connectSelectedObd(1);
            else if (in(x, y, 315, 405, 965, 475)) connectSelectedObd(2);
            else if (in(x, y, 315, 485, 965, 555)) connectSelectedObd(3);
            else { obdSelectorOpen = false; invalidate(); }
            return true;
        }

        if (overlayTitle != null) {
            overlayTitle = null;
            overlayValue = null;
            overlayBody = null;
            invalidate();
            return true;
        }

        if (y < 70f && x > 880f && x < 1140f) {
            startObdConnection();
            return true;
        }

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
            if (in(x, y, 200, 80, 615, 245)) { startObdConnection(); }
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

    private void drawUnifiedModeTabs(Canvas c) {
        // Equal 1/3-width hit/visual zones in every mode. The veil suppresses
        // different baked active-tab glows without moving the layout.
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(112, 2, 8, 13));
        c.drawRect(0, sy(625), getWidth(), getHeight(), p);

        float[][] tabs = new float[][] {{6, 631, 420, 710}, {432, 631, 848, 710}, {860, 631, 1274, 710}};
        int active = mode == Mode.STREET ? 0 : mode == Mode.SPORT ? 1 : 2;
        int color = active == 0 ? Color.rgb(30, 240, 145) : active == 1 ? Color.rgb(255, 72, 72) : Color.rgb(50, 220, 235);
        RectF r = new RectF(sx(tabs[active][0]), sy(tabs[active][1]), sx(tabs[active][2]), sy(tabs[active][3]));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sx(2.2f));
        p.setColor(color);
        p.setShadowLayer(sx(7f), 0f, sy(2f), Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)));
        c.drawRoundRect(r, sx(7f), sx(7f), p);
        p.clearShadowLayer();
        p.setStrokeWidth(sx(3f));
        c.drawLine(sx(tabs[active][0] + 28), sy(706), sx(tabs[active][2] - 28), sy(706), p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawObdSelector(Canvas c) {
        p.clearShadowLayer();
        p.setColor(Color.argb(190, 0, 0, 0));
        c.drawRect(0, 0, getWidth(), getHeight(), p);

        RectF panel = new RectF(sx(280), sy(150), sx(1000), sy(590));
        p.setColor(Color.argb(252, 5, 16, 20));
        c.drawRoundRect(panel, sx(18), sx(18), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sx(2));
        p.setColor(Color.rgb(45, 225, 200));
        c.drawRoundRect(panel, sx(18), sx(18), p);
        p.setStyle(Paint.Style.FILL);

        text(c, "OBD CONNECTION", 640, 195, 27, WHITE, Paint.Align.CENTER, true);
        text(c, "Choose adapter / transport", 640, 220, 14, MUTED, Paint.Align.CENTER, false);
        drawObdOption(c, 245, "AUTO", "Try paired BT 3.0, then BLE 4.0");
        drawObdOption(c, 325, "BLE 4.0", "Recommended for Vgate iCar Pro BLE 4.0 DUAL");
        drawObdOption(c, 405, "BT 3.0 / CLASSIC", "Use a paired Bluetooth ELM327 adapter");
        drawObdOption(c, 485, "WIFI ELM327", "Default 192.168.0.10 : 35000");
        text(c, "Tap outside to close", 640, 575, 13, MUTED, Paint.Align.CENTER, false);
    }

    private void drawObdOption(Canvas c, float top, String title, String subtitle) {
        RectF r = new RectF(sx(315), sy(top), sx(965), sy(top + 70));
        p.setColor(Color.argb(235, 10, 28, 32));
        c.drawRoundRect(r, sx(11), sx(11), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sx(1.2f));
        p.setColor(Color.argb(145, 65, 220, 215));
        c.drawRoundRect(r, sx(11), sx(11), p);
        p.setStyle(Paint.Style.FILL);
        text(c, title, 345, top + 29, 19, WHITE, Paint.Align.LEFT, true);
        text(c, subtitle, 345, top + 52, 12.5f, MUTED, Paint.Align.LEFT, false);
        text(c, ">", 930, top + 42, 26, Color.rgb(80, 235, 210), Paint.Align.CENTER, true);
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
