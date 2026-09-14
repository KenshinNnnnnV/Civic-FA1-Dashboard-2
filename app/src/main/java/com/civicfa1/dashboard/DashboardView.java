package com.civicfa1.dashboard;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Civic FA1 Dashboard v0.5.0
 *
 * The UI is rendered as real widgets on a fixed 1280x720 design canvas. Background JPGs
 * contribute only city/car artwork; all gauges, cards, labels and live values are drawn by
 * the app. This avoids baked values, opaque patches and mode-to-mode scaling jumps.
 */
public final class DashboardView extends View implements ObdManager.Listener {

    private enum Mode { STREET, SPORT, DIAGNOSTICS }

    private static final float DW = 1280f;
    private static final float DH = 720f;

    private static final int BG = Color.rgb(2, 10, 17);
    private static final int PANEL = Color.rgb(5, 24, 37);
    private static final int PANEL2 = Color.rgb(7, 31, 46);
    private static final int BORDER = Color.rgb(50, 178, 229);
    private static final int CYAN = Color.rgb(38, 240, 219);
    private static final int GREEN = Color.rgb(43, 244, 155);
    private static final int RED = Color.rgb(255, 72, 87);
    private static final int ORANGE = Color.rgb(255, 158, 62);
    private static final int BLUE = Color.rgb(24, 121, 226);
    private static final int WHITE = Color.rgb(242, 248, 253);
    private static final int MUTED = Color.rgb(177, 199, 216);
    private static final int DIM = Color.rgb(91, 125, 147);

    private final MainActivity activity;
    private final ObdManager obd;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final Bitmap streetArtwork;
    private final Bitmap sportArtwork;
    private final Bitmap diagnosticsArtwork;

    private Mode mode = Mode.STREET;
    private ObdManager.State obdState = ObdManager.State.DISCONNECTED;
    private ObdManager.Transport obdTransport = ObdManager.Transport.AUTO;
    private String obdDetail = "Tap to connect";
    private String adapterName = "";
    private ObdManager.Telemetry telemetry = new ObdManager.Telemetry();
    private ObdManager.Readiness readiness = new ObdManager.Readiness();
    private List<String> dtcs = new ArrayList<>();
    private List<ObdManager.DeviceInfo> devices = new ArrayList<>();

    private boolean connectionPanelOpen = false;
    private ObdManager.Transport selectorTransport = ObdManager.Transport.BLUETOOTH;
    private int selectedDeviceIndex = -1;
    private boolean autoReconnect;
    private boolean firstAttach = true;

    private float viewScale = 1f;
    private float viewOffsetX = 0f;
    private float viewOffsetY = 0f;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            invalidate();
            handler.postDelayed(this, 1000L);
        }
    };

    public DashboardView(MainActivity activity) {
        super(activity);
        this.activity = activity;
        this.obd = new ObdManager(activity, this);
        this.prefs = activity.getSharedPreferences("civic_dashboard", 0);
        this.autoReconnect = prefs.getBoolean("auto_reconnect", true);
        this.streetArtwork = BitmapFactory.decodeResource(getResources(), R.drawable.mode_street);
        this.sportArtwork = BitmapFactory.decodeResource(getResources(), R.drawable.mode_sport);
        this.diagnosticsArtwork = BitmapFactory.decodeResource(getResources(), R.drawable.mode_diagnostics);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        obd.refreshDevices();
        if (firstAttach) {
            firstAttach = false;
            handler.postDelayed(() -> {
                if (autoReconnect && hasBluetoothPermission() && obdState == ObdManager.State.DISCONNECTED) {
                    connectLastOrAuto();
                }
            }, 900L);
        }
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(clockTick);
        obd.shutdown();
        super.onDetachedFromWindow();
    }

    public void onBluetoothPermissionResult(boolean granted) {
        if (granted) {
            obd.refreshDevices();
            if (autoReconnect && obdState == ObdManager.State.PERMISSION_REQUIRED) connectLastOrAuto();
        } else {
            obdState = ObdManager.State.PERMISSION_REQUIRED;
            obdDetail = "Bluetooth permission required";
            invalidate();
        }
    }

    @Override public boolean hasBluetoothPermission() {
        return activity.hasObdBluetoothPermissions();
    }

    private void connectLastOrAuto() {
        String address = prefs.getString("last_address", "");
        String t = prefs.getString("last_transport", "BLUETOOTH");
        if (!address.isEmpty() && "BLUETOOTH".equals(t)) {
            obd.connectBluetoothByAddress(address);
        } else {
            obd.connectAuto();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        float s = Math.min(getWidth() / DW, getHeight() / DH);
        viewScale = s;
        viewOffsetX = (getWidth() - DW * s) * 0.5f;
        viewOffsetY = (getHeight() - DH * s) * 0.5f;
        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(s, s);

        drawBackground(canvas);
        drawHeader(canvas);
        if (mode == Mode.STREET) drawStreet(canvas);
        else if (mode == Mode.SPORT) drawSport(canvas);
        else drawDiagnostics(canvas);
        drawModeTabs(canvas);
        if (connectionPanelOpen) drawConnectionPanel(canvas);

        canvas.restore();
    }

    // ---------------- Background / header ----------------

    private void drawBackground(Canvas c) {
        fillRect(c, 0, 0, DW, DH, BG, 255);
        Bitmap art = mode == Mode.STREET ? streetArtwork : mode == Mode.SPORT ? sportArtwork : diagnosticsArtwork;
        if (art == null) return;
        p.setAlpha(mode == Mode.SPORT ? 210 : 190);
        if (mode == Mode.DIAGNOSTICS) {
            // Only the city/car strip is borrowed from artwork. All UI below is native Canvas.
            Rect src = new Rect(0, 76, art.getWidth(), Math.min(330, art.getHeight()));
            RectF dst = new RectF(0, 76, 1280, 330);
            c.drawBitmap(art, src, dst, p);
        } else {
            // Left city and right car only; the centre is fully redrawn as a real gauge.
            Rect srcLeft = new Rect(0, 76, 390, 330);
            RectF dstLeft = new RectF(0, 76, 390, 330);
            c.drawBitmap(art, srcLeft, dstLeft, p);
            Rect srcRight = new Rect(880, 76, 1280, 330);
            RectF dstRight = new RectF(880, 76, 1280, 330);
            c.drawBitmap(art, srcRight, dstRight, p);
        }
        p.setAlpha(255);
        LinearGradient shade = new LinearGradient(0, 75, 0, 620,
                new int[]{Color.argb(35, 0, 0, 0), Color.argb(160, 0, 6, 12)}, null, Shader.TileMode.CLAMP);
        p.setShader(shade);
        c.drawRect(0, 75, 1280, 620, p);
        p.setShader(null);
    }

    private void drawHeader(Canvas c) {
        fillRect(c, 0, 0, 1280, 76, Color.rgb(2, 12, 22), 248);
        strokeLine(c, 0, 75, 1280, 75, Color.argb(170, 78, 181, 230), 1.2f);
        int accent = mode == Mode.SPORT ? RED : (mode == Mode.DIAGNOSTICS ? CYAN : GREEN);

        // Brand mark
        strokeRound(c, 24, 15, 76, 61, 9, WHITE, 3f, 240);
        text(c, "H", 50, 51, 34, WHITE, Paint.Align.CENTER, true, false);
        text(c, "CIVIC FA1", 92, 40, 28, WHITE, Paint.Align.LEFT, true, true);
        text(c, "i-VTEC · 1.8L R18A", 94, 60, 13, MUTED, Paint.Align.LEFT, false, false);

        String title = mode == Mode.STREET ? "STREET MODE" : mode == Mode.SPORT ? "SPORT MODE" : "DIAGNOSTICS MODE";
        String subtitle = mode == Mode.STREET ? "COMFORT EVERYDAY" : mode == Mode.SPORT ? "MORE RESPONSE" : "SYSTEM HEALTH MONITOR";
        glowLine(c, 380, 37, 468, 37, accent, 4f, 7f);
        glowLine(c, 812, 37, 900, 37, accent, 4f, 7f);
        glowText(c, title, 640, 42, 31, accent, Paint.Align.CENTER, true, 5f);
        text(c, subtitle, 640, 63, 13, WHITE, Paint.Align.CENTER, false, true);

        boolean connected = obdState == ObdManager.State.ECU_CONNECTED;
        int statusColor = connected ? GREEN : (obdState == ObdManager.State.ERROR ? RED : CYAN);
        fillCircle(c, 955, 31, 9, statusColor, 255);
        String status = connected ? "OBD: CONNECTED" : obdState == ObdManager.State.ERROR ? "OBD: ERROR" :
                obdState == ObdManager.State.DISCONNECTED ? "OBD: DISCONNECTED" : "OBD: " + stateShort(obdState);
        text(c, status, 976, 30, 17, statusColor, Paint.Align.LEFT, true, false);
        String sub = connected ? nonEmpty(adapterName, transportLabel(obdTransport)) : truncate(obdDetail, 31);
        text(c, sub, 976, 52, 11, MUTED, Paint.Align.LEFT, false, false);

        text(c, new SimpleDateFormat("HH:mm", Locale.US).format(new Date()), 1168, 40, 24, WHITE, Paint.Align.CENTER, true, false);
        drawWifi(c, 1244, 31, WHITE);
    }

    private String stateShort(ObdManager.State s) {
        switch (s) {
            case SEARCHING: return "SEARCHING";
            case ADAPTER_FOUND: return "FOUND";
            case CONNECTING: return "CONNECTING";
            case INITIALIZING: return "INITIALIZING";
            case ECU_CONNECTING: return "ECU";
            case PERMISSION_REQUIRED: return "PERMISSION";
            default: return "OFFLINE";
        }
    }

    // ---------------- Street ----------------

    private void drawStreet(Canvas c) {
        drawSpeedDial(c, 640, 310, 225, GREEN, value(telemetry.speed), 220f);
        drawRpmHalfDial(c, 640, 545, 182, GREEN, value(telemetry.rpm));

        drawMetricCard(c, new RectF(26, 308, 368, 455), "COOLANT TEMP", MetricIcon.THERMOMETER,
                value(telemetry.coolant), "°C", 50, 130, GREEN, telemetry.coolant);
        drawMetricCard(c, new RectF(26, 468, 395, 603), "FUEL LEVEL", MetricIcon.FUEL,
                value(telemetry.fuel), "%", 0, 100, GREEN, telemetry.fuel);
        drawMetricCard(c, new RectF(912, 308, 1254, 455), "INTAKE TEMP", MetricIcon.AIR,
                value(telemetry.intake), "°C", -20, 80, CYAN, telemetry.intake);
        drawMetricCard(c, new RectF(885, 468, 1254, 603), "BATTERY VOLTAGE", MetricIcon.BATTERY,
                value1(telemetry.voltage), "V", 10, 16, CYAN, telemetry.voltage);
    }

    private void drawSpeedDial(Canvas c, float cx, float cy, float radius, int accent, String display, float max) {
        // dark dial body
        fillCircle(c, cx, cy, radius, Color.rgb(2, 12, 20), 245);
        strokeCircle(c, cx, cy, radius, Color.rgb(128, 188, 224), 2.4f, 220);
        strokeCircle(c, cx, cy, radius - 10, Color.argb(120, 70, 130, 170), 1.1f, 255);
        float value = finite(telemetry.speed) ? telemetry.speed : 0f;
        float start = 145f, sweep = 250f;
        drawArcTrack(c, cx, cy, radius - 10, start, sweep, Color.rgb(34, 52, 71), 13f);
        drawArcTrack(c, cx, cy, radius - 10, start, sweep * clamp(value / max), accent, 13f);
        for (int i = 0; i <= 44; i++) {
            float a = start + sweep * i / 44f;
            float len = i % 4 == 0 ? 13 : 7;
            radialLine(c, cx, cy, radius - 24, radius - 24 - len, a, WHITE, i % 4 == 0 ? 2.5f : 1f, i % 4 == 0 ? 240 : 150);
        }
        for (int v = 0; v <= 220; v += 20) {
            float a = start + sweep * v / max;
            polarText(c, Integer.toString(v), cx, cy, radius - 57, a, 18, WHITE);
        }
        glowText(c, display, cx, cy + 35, 82, WHITE, Paint.Align.CENTER, true, 5f);
        text(c, "km/h", cx, cy + 77, 25, WHITE, Paint.Align.CENTER, true, false);
    }

    private void drawRpmHalfDial(Canvas c, float cx, float cy, float radius, int accent, String display) {
        RectF r = new RectF(cx - radius, cy - radius * 0.72f, cx + radius, cy + radius * 0.72f);
        fillRound(c, cx - radius - 12, cy - 78, cx + radius + 12, cy + 76, 20, Color.rgb(2, 14, 22), 250);
        drawArc(c, r, 195, 150, Color.rgb(45, 64, 82), 10f, 255);
        float rpm = finite(telemetry.rpm) ? telemetry.rpm : 0f;
        drawArc(c, r, 195, 150 * clamp(rpm / 8000f), accent, 10f, 255);
        if (rpm > 6500) drawArc(c, r, 195 + 150 * 0.81f, 150 * 0.19f, RED, 10f, 255);
        for (int v = 0; v <= 8; v++) {
            float a = 195 + 150f * v / 8f;
            polarText(c, Integer.toString(v), cx, cy + 8, radius - 25, a, 16, v >= 7 ? RED : WHITE);
        }
        glowText(c, display, cx, cy + 30, 33, WHITE, Paint.Align.CENTER, true, 4f);
        text(c, "RPM", cx, cy + 52, 12, MUTED, Paint.Align.CENTER, true, false);
    }

    // ---------------- Sport ----------------

    private void drawSport(Canvas c) {
        drawSportTach(c);
        drawMetricCard(c, new RectF(26, 306, 368, 455), "THROTTLE POSITION", MetricIcon.THROTTLE,
                value(telemetry.throttle), "%", 0, 100, RED, telemetry.throttle);
        drawMetricCard(c, new RectF(26, 468, 395, 603), "ENGINE LOAD", MetricIcon.ENGINE,
                value(telemetry.load), "%", 0, 100, RED, telemetry.load);
        drawMetricCard(c, new RectF(912, 306, 1254, 455), "INTAKE TEMP", MetricIcon.AIR,
                value(telemetry.intake), "°C", -20, 80, CYAN, telemetry.intake);
        drawMetricCard(c, new RectF(885, 468, 1254, 603), "COOLANT TEMP", MetricIcon.THERMOMETER,
                value(telemetry.coolant), "°C", 50, 130, CYAN, telemetry.coolant);
    }

    private void drawSportTach(Canvas c) {
        float cx = 640, cy = 332, radius = 226;
        fillCircle(c, cx, cy, radius, Color.rgb(3, 9, 17), 250);
        strokeCircle(c, cx, cy, radius, Color.rgb(164, 197, 223), 2.2f, 230);
        float start = 145, sweep = 250;
        float rpm = finite(telemetry.rpm) ? telemetry.rpm : 0f;
        drawArcTrack(c, cx, cy, radius - 10, start, sweep, Color.rgb(35, 47, 64), 14);
        float ratio = clamp(rpm / 8000f);
        float greenEnd = Math.min(ratio, 0.62f);
        if (greenEnd > 0) drawArcTrack(c, cx, cy, radius - 10, start, sweep * greenEnd, RED, 14);
        if (ratio > 0.62f) drawArcTrack(c, cx, cy, radius - 10, start + sweep * 0.62f, sweep * (Math.min(ratio, 0.78f) - 0.62f), ORANGE, 14);
        if (ratio > 0.78f) drawArcTrack(c, cx, cy, radius - 10, start + sweep * 0.78f, sweep * (ratio - 0.78f), Color.rgb(255, 38, 49), 14);
        for (int i = 0; i <= 40; i++) {
            float a = start + sweep * i / 40f;
            radialLine(c, cx, cy, radius - 25, radius - 25 - (i % 5 == 0 ? 14 : 7), a, WHITE, i % 5 == 0 ? 2.5f : 1f, i % 5 == 0 ? 240 : 140);
        }
        for (int v = 0; v <= 8; v++) {
            float a = start + sweep * v / 8f;
            polarText(c, Integer.toString(v), cx, cy, radius - 59, a, 22, WHITE);
        }
        text(c, "i-VTEC", cx, cy - 45, 23, WHITE, Paint.Align.CENTER, true, false);
        text(c, "x1000 RPM", cx, cy - 20, 14, MUTED, Paint.Align.CENTER, false, false);
        glowText(c, value(telemetry.rpm), cx, cy + 54, 72, WHITE, Paint.Align.CENTER, true, 6f);
        text(c, "RPM", cx, cy + 91, 25, WHITE, Paint.Align.CENTER, true, false);

        // speed pedestal: native widget, no background image patch
        fillRound(c, 505, 500, 775, 604, 18, Color.rgb(9, 19, 31), 250);
        strokeRound(c, 505, 500, 775, 604, 18, Color.rgb(103, 166, 209), 2f, 230);
        glowLine(c, 545, 590, 735, 590, RED, 3f, 8f);
        glowText(c, value(telemetry.speed), 625, 570, 62, WHITE, Paint.Align.CENTER, true, 5f);
        text(c, "km/h", 704, 570, 18, WHITE, Paint.Align.LEFT, true, false);
    }

    // ---------------- Diagnostics ----------------

    private void drawDiagnostics(Canvas c) {
        // Header scene ends at 270; native widgets start below it.
        drawInfoCard(c, new RectF(18, 258, 390, 365), "OBD CONNECTION", MetricIcon.OBD,
                obdState == ObdManager.State.ECU_CONNECTED ? "Connected" : stateFriendly(),
                obdState == ObdManager.State.ECU_CONNECTED ? GREEN : (obdState == ObdManager.State.ERROR ? RED : CYAN),
                nonEmpty(adapterName, transportLabel(obdTransport)),
                telemetry.protocol.isEmpty() ? obdDetail : "Protocol: " + telemetry.protocol);

        drawInfoCard(c, new RectF(400, 258, 735, 365), "ECU STATUS", MetricIcon.CHIP,
                obdState == ObdManager.State.ECU_CONNECTED ? "PGM-FI (Honda)" : "Offline",
                obdState == ObdManager.State.ECU_CONNECTED ? WHITE : MUTED,
                obdState == ObdManager.State.ECU_CONNECTED ? (dtcs.isEmpty() ? "No fault codes" : dtcs.size() + " DTC") : "Waiting for ECU",
                obdState == ObdManager.State.ECU_CONNECTED ? "Live OBD-II data" : "Connect adapter first");

        drawConnectionSummary(c, new RectF(745, 258, 1018, 365));

        float y = 375;
        float h = 98;
        float gap = 6;
        float w = (1248f - 5 * gap) / 6f;
        float x = 16;
        drawMetricCard(c, new RectF(x, y, x+w, y+h), "BATTERY VOLTAGE", MetricIcon.BATTERY, value1(telemetry.voltage), "V", 10, 16, CYAN, telemetry.voltage); x += w+gap;
        drawMetricCard(c, new RectF(x, y, x+w, y+h), "COOLANT TEMP", MetricIcon.THERMOMETER, value(telemetry.coolant), "°C", 50, 130, CYAN, telemetry.coolant); x += w+gap;
        drawMetricCard(c, new RectF(x, y, x+w, y+h), "INTAKE TEMP", MetricIcon.AIR, value(telemetry.intake), "°C", -20, 80, CYAN, telemetry.intake); x += w+gap;
        drawMetricCard(c, new RectF(x, y, x+w, y+h), "THROTTLE POSITION", MetricIcon.THROTTLE, value(telemetry.throttle), "%", 0, 100, CYAN, telemetry.throttle); x += w+gap;
        drawMetricCard(c, new RectF(x, y, x+w, y+h), "ENGINE LOAD", MetricIcon.ENGINE, value(telemetry.load), "%", 0, 100, CYAN, telemetry.load); x += w+gap;
        drawMetricCard(c, new RectF(x, y, x+w, y+h), "FUEL LEVEL", MetricIcon.FUEL, value(telemetry.fuel), "%", 0, 100, CYAN, telemetry.fuel);

        drawDtcPanel(c, new RectF(16, 483, 320, 614));
        drawReadinessPanel(c, new RectF(330, 483, 795, 614));
        drawLivePanel(c, new RectF(805, 483, 1264, 614));
    }

    private void drawConnectionSummary(Canvas c, RectF r) {
        drawPanel(c, r, CYAN, false);
        text(c, "Connection type", r.left + 18, r.top + 28, 13, MUTED, Paint.Align.LEFT, false, false);
        text(c, transportLabel(selectorTransport), r.right - 18, r.top + 28, 14, WHITE, Paint.Align.RIGHT, true, false);
        strokeLine(c, r.left + 14, r.top + 41, r.right - 14, r.top + 41, Color.argb(90, 85, 175, 214), 1);
        text(c, "Auto reconnect", r.left + 18, r.top + 66, 13, MUTED, Paint.Align.LEFT, false, false);
        drawToggle(c, r.right - 45, r.top + 61, autoReconnect);
        strokeLine(c, r.left + 14, r.top + 78, r.right - 14, r.top + 78, Color.argb(90, 85, 175, 214), 1);
        text(c, "Tap for adapter settings", r.left + 18, r.top + 99, 12, CYAN, Paint.Align.LEFT, true, false);
    }

    private void drawDtcPanel(Canvas c, RectF r) {
        drawPanel(c, r, CYAN, false);
        drawIcon(c, MetricIcon.ENGINE, r.left + 28, r.top + 30, CYAN, 0.85f);
        text(c, "FAULT CODES (DTC)", r.left + 60, r.top + 29, 14, WHITE, Paint.Align.LEFT, true, false);
        if (obdState != ObdManager.State.ECU_CONNECTED) {
            drawCheckCircle(c, r.left + 52, r.top + 82, DIM, false);
            text(c, "Not available", r.left + 92, r.top + 80, 17, MUTED, Paint.Align.LEFT, true, false);
            text(c, "Connect ECU to read DTC", r.left + 92, r.top + 103, 11, DIM, Paint.Align.LEFT, false, false);
        } else if (dtcs.isEmpty()) {
            drawCheckCircle(c, r.left + 52, r.top + 82, GREEN, true);
            text(c, "No fault codes", r.left + 92, r.top + 80, 18, GREEN, Paint.Align.LEFT, true, false);
            text(c, "ECU reports no stored DTC", r.left + 92, r.top + 103, 11, MUTED, Paint.Align.LEFT, false, false);
        } else {
            drawCheckCircle(c, r.left + 52, r.top + 82, RED, false);
            text(c, dtcs.size() + " fault code(s)", r.left + 92, r.top + 78, 18, RED, Paint.Align.LEFT, true, false);
            text(c, join(dtcs, 3), r.left + 92, r.top + 103, 11, WHITE, Paint.Align.LEFT, false, false);
        }
    }

    private void drawReadinessPanel(Canvas c, RectF r) {
        drawPanel(c, r, CYAN, false);
        drawIcon(c, MetricIcon.CLIPBOARD, r.left + 27, r.top + 29, CYAN, 0.8f);
        text(c, "READINESS MONITORS", r.left + 58, r.top + 28, 14, WHITE, Paint.Align.LEFT, true, false);
        if (!readiness.available || obdState != ObdManager.State.ECU_CONNECTED) {
            text(c, "N/A — waiting for ECU", r.left + 35, r.top + 75, 17, MUTED, Paint.Align.LEFT, true, false);
            return;
        }
        String[] names = new String[]{"Misfire", "Fuel System", "Components", "Catalyst", "Heated Catalyst", "Evaporative System", "Secondary Air", "O2 Sensor", "O2 Sensor Heater", "EGR/VVT"};
        float startX = r.left + 26, startY = r.top + 58;
        for (int i = 0; i < names.length; i++) {
            int col = i / 4;
            int row = i % 4;
            if (col >= 3) break;
            float xx = startX + col * 145;
            float yy = startY + row * 20;
            Boolean ready = findMonitor(readiness.monitors, names[i]);
            fillCircle(c, xx, yy - 4, 6, Boolean.TRUE.equals(ready) ? GREEN : ORANGE, 255);
            if (Boolean.TRUE.equals(ready)) text(c, "✓", xx, yy, 9, BG, Paint.Align.CENTER, true, false);
            text(c, names[i], xx + 13, yy, 10.5f, WHITE, Paint.Align.LEFT, false, false);
        }
    }

    private Boolean findMonitor(Map<String, Boolean> map, String name) {
        if (map.containsKey(name)) return map.get(name);
        if ("EGR/VVT".equals(name)) return map.get("EGR/VVT");
        if ("Secondary Air".equals(name)) return map.get("Secondary Air");
        return null;
    }

    private void drawLivePanel(Canvas c, RectF r) {
        drawPanel(c, r, CYAN, false);
        drawIcon(c, MetricIcon.BARS, r.left + 28, r.top + 29, CYAN, 0.8f);
        text(c, "LIVE SENSOR DATA", r.left + 58, r.top + 28, 14, WHITE, Paint.Align.LEFT, true, false);
        float y = r.top + 57;
        sensorRow(c, r.left + 28, r.left + 230, y, "Vehicle Speed", unitValue(telemetry.speed, "km/h", 0));
        sensorRow(c, r.left + 28, r.left + 230, y + 20, "RPM", unitValue(telemetry.rpm, "rpm", 0));
        sensorRow(c, r.left + 28, r.left + 230, y + 40, "Coolant Temp", unitValue(telemetry.coolant, "°C", 0));
        sensorRow(c, r.left + 28, r.left + 230, y + 60, "Intake Temp", unitValue(telemetry.intake, "°C", 0));
        sensorRow(c, r.left + 255, r.right - 24, y, "Throttle Position", unitValue(telemetry.throttle, "%", 0));
        sensorRow(c, r.left + 255, r.right - 24, y + 20, "Engine Load", unitValue(telemetry.load, "%", 0));
        sensorRow(c, r.left + 255, r.right - 24, y + 40, "MAF", unitValue(telemetry.maf, "g/s", 1));
        sensorRow(c, r.left + 255, r.right - 24, y + 60, "Fuel Level", unitValue(telemetry.fuel, "%", 0));
    }

    private void sensorRow(Canvas c, float labelX, float valueX, float y, String label, String value) {
        text(c, label, labelX, y, 10.5f, MUTED, Paint.Align.LEFT, false, false);
        text(c, value, valueX, y, 10.5f, WHITE, Paint.Align.RIGHT, true, false);
    }

    // ---------------- Reusable widgets ----------------

    private enum MetricIcon { THERMOMETER, BATTERY, FUEL, AIR, ENGINE, THROTTLE, OBD, CHIP, BARS, CLIPBOARD }

    private void drawMetricCard(Canvas c, RectF r, String title, MetricIcon icon,
                                String display, String unit, float min, float max, int accent, float raw) {
        drawPanel(c, r, accent, false);
        drawIcon(c, icon, r.left + 34, r.top + 37, accent, r.height() < 110 ? 0.7f : 1f);
        text(c, title, r.left + 72, r.top + 29, r.height() < 110 ? 10.8f : 14f, WHITE, Paint.Align.LEFT, true, false);
        float valueY = r.height() < 110 ? r.top + 59 : r.top + 82;
        float valueX = r.centerX() + 12;
        glowText(c, display, valueX, valueY, r.height() < 110 ? 27f : 39f, WHITE, Paint.Align.CENTER, true, 3.5f);
        float textWidth = measure(display, r.height() < 110 ? 27f : 39f, true);
        text(c, unit, valueX + textWidth * 0.5f + 9, valueY, r.height() < 110 ? 11f : 15f, WHITE, Paint.Align.LEFT, true, false);
        float barY = r.bottom - (r.height() < 110 ? 20 : 25);
        drawBar(c, r.left + 18, barY, r.right - 18, barY + 8, raw, min, max, accent);
        text(c, formatTick(min), r.left + 18, r.bottom - 4, 9.5f, MUTED, Paint.Align.LEFT, false, false);
        text(c, formatTick((min + max) * 0.5f), r.centerX(), r.bottom - 4, 9.5f, MUTED, Paint.Align.CENTER, false, false);
        text(c, formatTick(max), r.right - 18, r.bottom - 4, 9.5f, MUTED, Paint.Align.RIGHT, false, false);
        text(c, "›", r.right - 18, r.top + 31, 25, WHITE, Paint.Align.CENTER, false, false);
    }

    private void drawInfoCard(Canvas c, RectF r, String title, MetricIcon icon, String line1, int line1Color, String line2, String line3) {
        drawPanel(c, r, CYAN, false);
        drawIcon(c, icon, r.left + 38, r.top + 45, CYAN, 1f);
        text(c, title, r.left + 82, r.top + 27, 14, WHITE, Paint.Align.LEFT, true, false);
        text(c, line1, r.left + 82, r.top + 55, 19, line1Color, Paint.Align.LEFT, true, false);
        text(c, truncate(line2, 33), r.left + 82, r.top + 79, 11.5f, WHITE, Paint.Align.LEFT, false, false);
        text(c, truncate(line3, 38), r.left + 82, r.top + 98, 10.5f, MUTED, Paint.Align.LEFT, false, false);
        text(c, "›", r.right - 20, r.top + 32, 26, WHITE, Paint.Align.CENTER, false, false);
    }

    private void drawPanel(Canvas c, RectF r, int accent, boolean active) {
        fillRound(c, r.left, r.top, r.right, r.bottom, 8, PANEL, 250);
        strokeRound(c, r.left, r.top, r.right, r.bottom, 8, Color.argb(215, Color.red(BORDER), Color.green(BORDER), Color.blue(BORDER)), 1.4f, 255);
        if (active) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f);
            p.setColor(accent);
            p.setShadowLayer(10f, 0, 0, accent);
            c.drawRoundRect(r, 8, 8, p);
            p.clearShadowLayer();
        }
    }

    private void drawBar(Canvas c, float l, float t, float r, float b, float raw, float min, float max, int accent) {
        fillRound(c, l, t, r, b, 4, Color.rgb(23, 73, 122), 255);
        if (!finite(raw)) return;
        float ratio = clamp((raw - min) / (max - min));
        if (ratio <= 0) return;
        LinearGradient g = new LinearGradient(l, t, r, t, CYAN, accent, Shader.TileMode.CLAMP);
        p.setShader(g);
        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(5f, 0, 0, accent);
        c.drawRoundRect(new RectF(l, t, l + (r - l) * ratio, b), 4, 4, p);
        p.clearShadowLayer();
        p.setShader(null);
    }

    // ---------------- Bottom nav ----------------

    private void drawModeTabs(Canvas c) {
        float y1 = 624, y2 = 710, margin = 16, gap = 8;
        float w = (1280 - margin * 2 - gap * 2) / 3f;
        for (int i = 0; i < 3; i++) {
            float l = margin + i * (w + gap), r = l + w;
            Mode m = i == 0 ? Mode.STREET : i == 1 ? Mode.SPORT : Mode.DIAGNOSTICS;
            boolean active = mode == m;
            int accent = m == Mode.STREET ? GREEN : m == Mode.SPORT ? RED : CYAN;
            drawPanel(c, new RectF(l, y1, r, y2), accent, active);
            if (active) {
                LinearGradient g = new LinearGradient(l, y1, r, y2, Color.argb(85, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT, Shader.TileMode.CLAMP);
                p.setShader(g); p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(l, y1, r, y2), 8, 8, p); p.setShader(null);
            }
            if (m == Mode.STREET) drawRoadIcon(c, l + 85, 666, active ? WHITE : MUTED);
            else if (m == Mode.SPORT) drawFlagIcon(c, l + 85, 666, active ? WHITE : MUTED);
            else drawGearIcon(c, l + 85, 666, active ? WHITE : MUTED);
            String t = m == Mode.STREET ? "STREET" : m == Mode.SPORT ? "SPORT" : "DIAGNOSTICS";
            String sub = m == Mode.STREET ? "COMFORT EVERYDAY" : m == Mode.SPORT ? "MORE RESPONSE" : "KNOW YOUR CAR";
            text(c, t, l + 150, 658, 21, active ? WHITE : MUTED, Paint.Align.LEFT, true, false);
            text(c, sub, l + 150, 684, 11, active ? WHITE : DIM, Paint.Align.LEFT, false, true);
        }
    }

    // ---------------- Connection selector ----------------

    private void drawConnectionPanel(Canvas c) {
        fillRect(c, 0, 0, 1280, 720, Color.BLACK, 150);
        RectF r = new RectF(300, 112, 980, 610);
        fillRound(c, r.left, r.top, r.right, r.bottom, 14, Color.rgb(4, 20, 32), 252);
        strokeRound(c, r.left, r.top, r.right, r.bottom, 14, CYAN, 1.5f, 230);
        text(c, "OBD CONNECTION", r.left + 30, r.top + 42, 24, WHITE, Paint.Align.LEFT, true, false);
        text(c, "×", r.right - 35, r.top + 42, 30, MUTED, Paint.Align.CENTER, false, false);
        text(c, "Choose transport", r.left + 30, r.top + 76, 12, MUTED, Paint.Align.LEFT, false, false);

        ObdManager.Transport[] transports = new ObdManager.Transport[]{ObdManager.Transport.AUTO, ObdManager.Transport.BLUETOOTH, ObdManager.Transport.BLE, ObdManager.Transport.WIFI};
        String[] labels = new String[]{"AUTO", "BT 3.0", "BLE 4.0", "WI-FI"};
        for (int i = 0; i < transports.length; i++) {
            float l = r.left + 30 + i * 150;
            boolean sel = selectorTransport == transports[i];
            fillRound(c, l, r.top + 92, l + 134, r.top + 130, 7, sel ? Color.rgb(13, 83, 128) : PANEL2, 255);
            strokeRound(c, l, r.top + 92, l + 134, r.top + 130, 7, sel ? CYAN : BORDER, 1.1f, 220);
            text(c, labels[i], l + 67, r.top + 117, 13, WHITE, Paint.Align.CENTER, true, false);
        }

        if (selectorTransport == ObdManager.Transport.WIFI) {
            text(c, "Wi-Fi ELM327", r.left + 30, r.top + 178, 17, WHITE, Paint.Align.LEFT, true, false);
            text(c, "Default endpoint: 192.168.0.10 : 35000", r.left + 30, r.top + 207, 13, MUTED, Paint.Align.LEFT, false, false);
            drawActionButton(c, r.left + 30, r.bottom - 80, r.right - 30, r.bottom - 30, "CONNECT WI-FI", CYAN);
        } else if (selectorTransport == ObdManager.Transport.AUTO) {
            text(c, "AUTO prioritizes paired Android-Vlink / Vgate / VLink / OBD devices.", r.left + 30, r.top + 174, 13, MUTED, Paint.Align.LEFT, false, false);
            text(c, "If no paired Classic Bluetooth adapter is found, BLE scan is attempted.", r.left + 30, r.top + 198, 13, MUTED, Paint.Align.LEFT, false, false);
            drawActionButton(c, r.left + 30, r.bottom - 80, r.right - 30, r.bottom - 30, "AUTO CONNECT", GREEN);
        } else {
            text(c, selectorTransport == ObdManager.Transport.BLUETOOTH ? "Paired Bluetooth devices" : "BLE devices", r.left + 30, r.top + 165, 15, WHITE, Paint.Align.LEFT, true, false);
            if (selectorTransport == ObdManager.Transport.BLUETOOTH) {
                text(c, "For Vgate iCar Pro on Android, pair Android-Vlink first (PIN 1234).", r.left + 30, r.top + 188, 11, MUTED, Paint.Align.LEFT, false, false);
            }
            int shown = 0;
            for (int i = 0; i < devices.size() && shown < 5; i++) {
                ObdManager.DeviceInfo d = devices.get(i);
                if (d.transport != selectorTransport) continue;
                float yy = r.top + 215 + shown * 48;
                boolean selected = selectedDeviceIndex == i;
                fillRound(c, r.left + 30, yy, r.right - 30, yy + 40, 6, selected ? Color.rgb(15, 68, 98) : PANEL2, 255);
                strokeRound(c, r.left + 30, yy, r.right - 30, yy + 40, 6, selected ? CYAN : Color.argb(100, 80, 160, 200), 1, 255);
                text(c, d.name, r.left + 48, yy + 18, 13, WHITE, Paint.Align.LEFT, true, false);
                text(c, d.address, r.left + 48, yy + 34, 9.5f, DIM, Paint.Align.LEFT, false, false);
                shown++;
            }
            if (shown == 0) text(c, "No devices yet. Tap SCAN / REFRESH.", r.left + 30, r.top + 238, 13, MUTED, Paint.Align.LEFT, false, false);
            drawActionButton(c, r.left + 30, r.bottom - 80, r.left + 260, r.bottom - 30, selectorTransport == ObdManager.Transport.BLE ? "SCAN BLE" : "REFRESH", CYAN);
            drawActionButton(c, r.left + 275, r.bottom - 80, r.right - 30, r.bottom - 30, "CONNECT SELECTED", GREEN);
        }
        text(c, "Status: " + stateFriendly() + " — " + truncate(obdDetail, 60), r.left + 30, r.bottom - 102, 11, obdState == ObdManager.State.ERROR ? RED : MUTED, Paint.Align.LEFT, false, false);
    }

    private void drawActionButton(Canvas c, float l, float t, float r, float b, String label, int accent) {
        fillRound(c, l, t, r, b, 7, Color.rgb(8, 52, 79), 255);
        strokeRound(c, l, t, r, b, 7, accent, 1.2f, 240);
        text(c, label, (l+r)*.5f, (t+b)*.5f + 5, 13, WHITE, Paint.Align.CENTER, true, false);
    }

    // ---------------- Touch ----------------

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = (event.getX() - viewOffsetX) / viewScale;
        float y = (event.getY() - viewOffsetY) / viewScale;
        if (x < 0 || y < 0 || x > DW || y > DH) return true;

        if (connectionPanelOpen) {
            handleConnectionPanelTouch(x, y);
            invalidate();
            return true;
        }

        if (y >= 620) {
            if (x < 426) mode = Mode.STREET;
            else if (x < 853) mode = Mode.SPORT;
            else mode = Mode.DIAGNOSTICS;
            invalidate();
            return true;
        }

        if (mode == Mode.DIAGNOSTICS && ((x >= 18 && x <= 390 && y >= 258 && y <= 365) || (x >= 745 && x <= 1018 && y >= 258 && y <= 365))) {
            connectionPanelOpen = true;
            selectorTransport = ObdManager.Transport.BLUETOOTH;
            selectedDeviceIndex = -1;
            obd.refreshDevices();
            invalidate();
            return true;
        }
        if (x >= 930 && y <= 70) {
            connectionPanelOpen = true;
            obd.refreshDevices();
            invalidate();
        }
        return true;
    }

    private void handleConnectionPanelTouch(float x, float y) {
        RectF r = new RectF(300, 112, 980, 610);
        if (x < r.left || x > r.right || y < r.top || y > r.bottom || (x > r.right - 70 && y < r.top + 70)) {
            connectionPanelOpen = false;
            return;
        }
        if (y >= r.top + 92 && y <= r.top + 130) {
            int idx = (int)((x - (r.left + 30)) / 150f);
            if (idx >= 0 && idx < 4) {
                selectorTransport = new ObdManager.Transport[]{ObdManager.Transport.AUTO, ObdManager.Transport.BLUETOOTH, ObdManager.Transport.BLE, ObdManager.Transport.WIFI}[idx];
                selectedDeviceIndex = -1;
                if (selectorTransport == ObdManager.Transport.BLUETOOTH) obd.refreshDevices();
                if (selectorTransport == ObdManager.Transport.BLE) obd.connectBleScan();
            }
            return;
        }

        if (selectorTransport == ObdManager.Transport.BLUETOOTH || selectorTransport == ObdManager.Transport.BLE) {
            int shown = 0;
            for (int i = 0; i < devices.size() && shown < 5; i++) {
                if (devices.get(i).transport != selectorTransport) continue;
                float yy = r.top + 215 + shown * 48;
                if (y >= yy && y <= yy + 40) {
                    selectedDeviceIndex = i;
                    return;
                }
                shown++;
            }
            if (y >= r.bottom - 80 && y <= r.bottom - 30) {
                if (x <= r.left + 260) {
                    if (selectorTransport == ObdManager.Transport.BLE) obd.connectBleScan(); else obd.refreshDevices();
                } else if (selectedDeviceIndex >= 0 && selectedDeviceIndex < devices.size()) {
                    ObdManager.DeviceInfo d = devices.get(selectedDeviceIndex);
                    prefs.edit().putString("last_address", d.address).putString("last_transport", d.transport.name()).apply();
                    obd.connect(d);
                }
            }
        } else if (selectorTransport == ObdManager.Transport.AUTO && y >= r.bottom - 80) {
            obd.connectAuto();
        } else if (selectorTransport == ObdManager.Transport.WIFI && y >= r.bottom - 80) {
            prefs.edit().putString("last_transport", "WIFI").apply();
            obd.connectWifi("192.168.0.10", 35000);
        }
    }

    // ---------------- Listener ----------------

    @Override public void onState(ObdManager.State state, String detail, ObdManager.Transport transport, String adapterName) {
        this.obdState = state;
        this.obdDetail = detail == null ? "" : detail;
        this.obdTransport = transport;
        this.adapterName = adapterName == null ? "" : adapterName;
        if (state == ObdManager.State.ECU_CONNECTED) {
            prefs.edit().putString("last_transport", transport.name()).apply();
        }
        invalidate();
    }

    @Override public void onDevices(List<ObdManager.DeviceInfo> devices) {
        // Preserve both previously discovered BLE devices and paired BT devices.
        this.devices = new ArrayList<>(devices);
        invalidate();
    }

    @Override public void onTelemetry(ObdManager.Telemetry telemetry) {
        this.telemetry = telemetry == null ? new ObdManager.Telemetry() : telemetry;
        invalidate();
    }

    @Override public void onReadiness(ObdManager.Readiness readiness) {
        this.readiness = readiness == null ? new ObdManager.Readiness() : readiness;
        invalidate();
    }

    @Override public void onDtc(List<String> codes) {
        this.dtcs = codes == null ? new ArrayList<>() : new ArrayList<>(codes);
        invalidate();
    }

    // ---------------- Drawing primitives ----------------

    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold, boolean wideSpacing) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextAlign(align); p.setTextSize(size);
        p.setTypeface(Typeface.create(wideSpacing ? "sans-serif" : "sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s == null ? "" : s, x, y, p);
    }

    private void glowText(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold, float glow) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextAlign(align); p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD_ITALIC : Typeface.NORMAL));
        p.setShadowLayer(glow, 0, 0, color); c.drawText(s == null ? "" : s, x, y, p); p.clearShadowLayer();
    }

    private float measure(String s, float size, boolean bold) {
        p.setTextSize(size); p.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD_ITALIC : Typeface.NORMAL));
        return p.measureText(s == null ? "" : s);
    }

    private void fillRect(Canvas c, float l, float t, float r, float b, int color, int alpha) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setAlpha(alpha); c.drawRect(l, t, r, b, p); p.setAlpha(255);
    }
    private void fillRound(Canvas c, float l, float t, float r, float b, float radius, int color, int alpha) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setAlpha(alpha); c.drawRoundRect(new RectF(l,t,r,b), radius, radius, p); p.setAlpha(255);
    }
    private void strokeRound(Canvas c, float l, float t, float r, float b, float radius, int color, float width, int alpha) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); p.setAlpha(alpha); c.drawRoundRect(new RectF(l,t,r,b), radius, radius, p); p.setAlpha(255); p.setStyle(Paint.Style.FILL);
    }
    private void fillCircle(Canvas c, float x, float y, float radius, int color, int alpha) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setAlpha(alpha); c.drawCircle(x,y,radius,p); p.setAlpha(255);
    }
    private void strokeCircle(Canvas c, float x, float y, float radius, int color, float width, int alpha) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); p.setAlpha(alpha); c.drawCircle(x,y,radius,p); p.setAlpha(255); p.setStyle(Paint.Style.FILL);
    }
    private void strokeLine(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); c.drawLine(x1,y1,x2,y2,p); p.setStyle(Paint.Style.FILL);
    }
    private void glowLine(Canvas c, float x1, float y1, float x2, float y2, int color, float width, float glow) {
        p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); p.setStrokeCap(Paint.Cap.ROUND); p.setShadowLayer(glow,0,0,color); c.drawLine(x1,y1,x2,y2,p); p.clearShadowLayer(); p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
    }
    private void drawArc(Canvas c, RectF r, float start, float sweep, int color, float width, int alpha) {
        p.setShader(null); p.clearShadowLayer(); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(width); p.setColor(color); p.setAlpha(alpha); c.drawArc(r,start,sweep,false,p); p.setAlpha(255); p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
    }
    private void drawArcTrack(Canvas c, float cx, float cy, float radius, float start, float sweep, int color, float width) {
        RectF r = new RectF(cx-radius,cy-radius,cx+radius,cy+radius); drawArc(c,r,start,sweep,color,width,255);
    }
    private void radialLine(Canvas c, float cx, float cy, float r1, float r2, float angle, int color, float width, int alpha) {
        double a = Math.toRadians(angle); p.setAlpha(alpha); strokeLine(c, cx+(float)Math.cos(a)*r1, cy+(float)Math.sin(a)*r1, cx+(float)Math.cos(a)*r2, cy+(float)Math.sin(a)*r2, color, width); p.setAlpha(255);
    }
    private void polarText(Canvas c, String s, float cx, float cy, float radius, float angle, float size, int color) {
        double a = Math.toRadians(angle); float x = cx+(float)Math.cos(a)*radius; float y = cy+(float)Math.sin(a)*radius + size*.35f; text(c,s,x,y,size,color,Paint.Align.CENTER,true,false);
    }

    private void drawWifi(Canvas c, float x, float y, int color) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.7f); p.setColor(color); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(x-15,y-8,x+15,y+14),215,110,false,p); c.drawArc(new RectF(x-10,y-3,x+10,y+12),215,110,false,p); fillCircle(c,x,y+9,2.5f,color,255); p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
    }

    private void drawToggle(Canvas c, float cx, float cy, boolean on) {
        fillRound(c,cx-24,cy-11,cx+24,cy+11,11,on?Color.rgb(20,181,107):Color.rgb(58,76,90),255); fillCircle(c,cx+(on?13:-13),cy,8.5f,WHITE,255);
    }

    private void drawCheckCircle(Canvas c, float x, float y, int color, boolean checked) {
        strokeCircle(c,x,y,24,color,3,255); if (checked) { strokeLine(c,x-11,y,x-3,y+8,color,3); strokeLine(c,x-3,y+8,x+13,y-9,color,3); }
    }

    private void drawIcon(Canvas c, MetricIcon icon, float x, float y, int color, float scale) {
        p.setColor(color); p.setStrokeWidth(3f*scale); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.clearShadowLayer();
        switch(icon) {
            case BATTERY:
                c.drawRect(x-20*scale,y-15*scale,x+20*scale,y+15*scale,p); strokeLine(c,x-9*scale,y-18*scale,x-3*scale,y-18*scale,color,3*scale); strokeLine(c,x+5*scale,y-18*scale,x+11*scale,y-18*scale,color,3*scale); strokeLine(c,x-10*scale,y,x-2*scale,y,color,2*scale); strokeLine(c,x-6*scale,y-4*scale,x-6*scale,y+4*scale,color,2*scale); strokeLine(c,x+6*scale,y,x+14*scale,y,color,2*scale); break;
            case THERMOMETER:
                c.drawCircle(x-8*scale,y+12*scale,7*scale,p); strokeLine(c,x-8*scale,y-20*scale,x-8*scale,y+7*scale,color,4*scale); strokeLine(c,x-4*scale,y-15*scale,x+3*scale,y-15*scale,color,2*scale); break;
            case FUEL:
                c.drawRect(x-18*scale,y-18*scale,x+5*scale,y+18*scale,p); strokeLine(c,x-13*scale,y-10*scale,x+1*scale,y-10*scale,color,2*scale); path.reset(); path.moveTo(x+5*scale,y-10*scale); path.lineTo(x+14*scale,y-4*scale); path.lineTo(x+14*scale,y+12*scale); c.drawPath(path,p); break;
            case AIR:
                for(int i=-1;i<=1;i++){ float yy=y+i*9*scale; strokeLine(c,x-21*scale,yy,x+5*scale,yy,color,3*scale); c.drawArc(new RectF(x-2*scale,yy-7*scale,x+17*scale,yy+7*scale),-80,170,false,p);} break;
            case ENGINE:
                c.drawRoundRect(new RectF(x-20*scale,y-12*scale,x+17*scale,y+14*scale),4*scale,4*scale,p); strokeLine(c,x-10*scale,y-17*scale,x+3*scale,y-17*scale,color,3*scale); strokeLine(c,x+17*scale,y-5*scale,x+24*scale,y-5*scale,color,3*scale); break;
            case THROTTLE:
                c.rotate(-35,x,y); c.drawRect(x-6*scale,y-21*scale,x+6*scale,y+17*scale,p); c.drawCircle(x,y-23*scale,5*scale,p); c.rotate(35,x,y); break;
            case OBD:
                c.drawRoundRect(new RectF(x-25*scale,y-16*scale,x+25*scale,y+16*scale),5*scale,5*scale,p); for(int i=0;i<5;i++) fillCircle(c,x-15*scale+i*7*scale,y-6*scale,1.7f*scale,color,255); for(int i=0;i<4;i++) fillCircle(c,x-11*scale+i*7*scale,y+6*scale,1.7f*scale,color,255); break;
            case CHIP:
                c.drawRect(x-15*scale,y-15*scale,x+15*scale,y+15*scale,p); for(int i=-1;i<=1;i++){strokeLine(c,x-24*scale,y+i*10*scale,x-16*scale,y+i*10*scale,color,2*scale);strokeLine(c,x+16*scale,y+i*10*scale,x+24*scale,y+i*10*scale,color,2*scale);} break;
            case BARS:
                for(int i=0;i<4;i++) fillRect(c,x-18*scale+i*10*scale,y+15*scale-(i+1)*9*scale,x-13*scale+i*10*scale,y+15*scale,color,255); break;
            case CLIPBOARD:
                c.drawRect(x-18*scale,y-18*scale,x+18*scale,y+20*scale,p); c.drawRoundRect(new RectF(x-8*scale,y-23*scale,x+8*scale,y-14*scale),3*scale,3*scale,p); break;
        }
        p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
    }

    private void drawRoadIcon(Canvas c,float x,float y,int color){strokeLine(c,x-18,y+22,x-8,y-22,color,5);strokeLine(c,x+18,y+22,x+8,y-22,color,5);strokeLine(c,x,y+20,x,y+10,color,3);strokeLine(c,x,y+2,x,y-7,color,3);strokeLine(c,x,y-14,x,y-22,color,3);}
    private void drawFlagIcon(Canvas c,float x,float y,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);for(int rr=0;rr<3;rr++)for(int cc=0;cc<3;cc++)if((rr+cc)%2==0)c.drawRect(x-20+cc*13,y-20+rr*13,x-8+cc*13,y-8+rr*13,p);strokeLine(c,x-23,y-23,x-23,y+24,color,3);p.setColor(color);}
    private void drawGearIcon(Canvas c,float x,float y,int color){strokeCircle(c,x,y,20,color,5,255);strokeCircle(c,x,y,7,color,4,255);for(int i=0;i<8;i++){double a=Math.toRadians(i*45);strokeLine(c,x+(float)Math.cos(a)*20,y+(float)Math.sin(a)*20,x+(float)Math.cos(a)*27,y+(float)Math.sin(a)*27,color,5);}}

    // ---------------- Formatting ----------------

    private boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }
    private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private String value(float v) { return finite(v) ? String.format(Locale.US, "%.0f", v) : "--"; }
    private String value1(float v) { return finite(v) ? String.format(Locale.US, "%.1f", v) : "--"; }
    private String unitValue(float v, String unit, int decimals) { if(!finite(v)) return "--"; return String.format(Locale.US, decimals==0?"%.0f %s":"%.1f %s",v,unit); }
    private String formatTick(float v) { return Math.abs(v - Math.round(v)) < .01 ? Integer.toString(Math.round(v)) : String.format(Locale.US,"%.1f",v); }
    private String transportLabel(ObdManager.Transport t) { if(t==null)return "AUTO"; switch(t){case BLUETOOTH:return "BT 3.0";case BLE:return "BLE 4.0";case WIFI:return "WI-FI";default:return "AUTO";} }
    private String nonEmpty(String s,String fallback){return s==null||s.trim().isEmpty()?fallback:s;}
    private String truncate(String s,int max){if(s==null)return "";return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}
    private String join(List<String> items,int max){StringBuilder b=new StringBuilder();for(int i=0;i<items.size()&&i<max;i++){if(i>0)b.append("  ");b.append(items.get(i));}return b.toString();}
    private String stateFriendly(){switch(obdState){case ECU_CONNECTED:return "Connected";case SEARCHING:return "Searching";case ADAPTER_FOUND:return "Adapter found";case CONNECTING:return "Connecting";case INITIALIZING:return "Initializing";case ECU_CONNECTING:return "ECU handshake";case PERMISSION_REQUIRED:return "Permission required";case ERROR:return "Error";default:return "Disconnected";}}
}
