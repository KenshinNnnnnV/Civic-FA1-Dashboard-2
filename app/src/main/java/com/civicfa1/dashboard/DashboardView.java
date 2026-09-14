package com.civicfa1.dashboard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * v0.4.1
 * - No simulator/random telemetry.
 * - When ECU is not connected, live fields show -- / N/A.
 * - When ECU is connected, every displayed value comes from ObdManager only.
 * - Street/Sport/Diagnostics use one fixed 1280x720 geometry.
 */
public class DashboardView extends View implements ObdManager.Listener {

    private enum Mode { STREET, SPORT, DIAGNOSTICS }

    private final MainActivity activity;
    private final ObdManager obdManager;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Bitmap streetBg;
    private final Bitmap sportBg;
    private final Bitmap diagnosticsBg;

    private Mode mode = Mode.STREET;
    private boolean obdSelectorOpen = false;

    private ObdManager.State obdState = ObdManager.State.SIMULATOR;
    private String obdDetail = "Not connected";
    private String obdTransport = "OFFLINE";
    private boolean liveConnected = false;

    private float rpm = Float.NaN;
    private float speed = Float.NaN;
    private float coolant = Float.NaN;
    private float voltage = Float.NaN;
    private float throttle = Float.NaN;
    private float load = Float.NaN;
    private float intake = Float.NaN;
    private float fuel = Float.NaN;
    private float maf = Float.NaN;

    private static final int WHITE = Color.rgb(245, 248, 250);
    private static final int MUTED = Color.rgb(184, 197, 208);
    private static final int GREEN = Color.rgb(35, 240, 158);
    private static final int RED = Color.rgb(255, 78, 88);
    private static final int CYAN = Color.rgb(57, 229, 226);
    private static final int PANEL = Color.rgb(5, 18, 27);
    private static final int TRACK = Color.rgb(22, 80, 132);

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
    protected void onDetachedFromWindow() {
        obdManager.stop();
        super.onDetachedFromWindow();
    }

    private float sx(float x) { return x * getWidth() / 1280f; }
    private float sy(float y) { return y * getHeight() / 720f; }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Bitmap bg = mode == Mode.STREET ? streetBg : mode == Mode.SPORT ? sportBg : diagnosticsBg;
        c.drawBitmap(bg, null, new RectF(0, 0, getWidth(), getHeight()), p);

        drawTopStatus(c);
        if (mode == Mode.STREET) drawStreet(c);
        else if (mode == Mode.SPORT) drawSport(c);
        else drawDiagnostics(c);

        if (obdSelectorOpen) drawObdSelector(c);
    }

    private void panelFill(Canvas c, float l, float t, float r, float b, float radius) {
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(PANEL);
        c.drawRoundRect(new RectF(sx(l), sy(t), sx(r), sy(b)), sx(radius), sx(radius), p);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold) {
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextAlign(align);
        p.setTextSize(sx(size));
        p.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, sx(x), sy(y), p);
    }

    private void liveText(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextAlign(align);
        p.setTextSize(sx(size));
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        if (!"--".equals(s) && !"N/A".equals(s)) {
            int glow = mode == Mode.SPORT ? Color.argb(145, 255, 70, 78) : Color.argb(135, 25, 238, 180);
            p.setShadowLayer(sx(4.5f), 0, 0, glow);
        } else p.clearShadowLayer();
        c.drawText(s, sx(x), sy(y), p);
        p.clearShadowLayer();
    }

    private String whole(float v) { return Float.isNaN(v) ? "--" : String.valueOf(Math.round(v)); }
    private String one(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.1f", v); }
    private String two(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.2f", v); }

    private void drawTopStatus(Canvas c) {
        // Cover the baked sample status from the reference image and render real state.
        panelFill(c, 900, 2, 1188, 72, 0);

        String label = "OBD: DISCONNECTED";
        String sub = "Tap to connect";
        int color = Color.rgb(235, 91, 91);
        if (obdState == ObdManager.State.SEARCHING) {
            label = "OBD: SEARCHING"; sub = obdDetail; color = Color.rgb(90, 205, 255);
        } else if (obdState == ObdManager.State.ADAPTER_FOUND) {
            label = "OBD: FOUND"; sub = obdDetail; color = Color.rgb(90, 225, 255);
        } else if (obdState == ObdManager.State.CONNECTING) {
            label = "OBD: CONNECTING"; sub = obdDetail; color = Color.rgb(255, 208, 76);
        } else if (obdState == ObdManager.State.ECU_CONNECTED) {
            label = "OBD: CONNECTED"; sub = obdTransport; color = GREEN;
        } else if (obdState == ObdManager.State.ERROR) {
            label = "OBD: ERROR"; sub = obdDetail; color = RED;
        }

        p.setColor(color);
        c.drawCircle(sx(920), sy(29), sx(7), p);
        text(c, label, 942, 32, 16, color, Paint.Align.LEFT, true);
        text(c, sub, 942, 52, 10, MUTED, Paint.Align.LEFT, false);
        String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        text(c, now, 1170, 34, 20, WHITE, Paint.Align.CENTER, true);
    }

    // ---------- STREET ----------
    private void drawStreet(Canvas c) {
        // Main speed field
        panelFill(c, 516, 246, 765, 390, 40);
        liveText(c, whole(speed), 640, 340, 82, WHITE, Paint.Align.CENTER);
        text(c, "km/h", 640, 382, 27, WHITE, Paint.Align.CENTER, true);

        // Lower RPM field
        panelFill(c, 568, 469, 711, 556, 20);
        liveText(c, whole(rpm), 640, 522, 38, WHITE, Paint.Align.CENTER);
        text(c, "RPM", 640, 548, 13, MUTED, Paint.Align.CENTER, true);

        drawMetricCard(c, 28, 310, 373, 445, 205, 384, coolant, "°C", 50, 130, false);
        drawMetricCard(c, 907, 310, 1257, 445, 1085, 384, intake, "°C", -20, 80, false);
        drawMetricCard(c, 28, 460, 401, 596, 205, 535, fuel, "%", 0, 100, false);
        drawMetricCard(c, 877, 460, 1257, 596, 1070, 535, voltage, "V", 10, 16, true);
    }

    // ---------- SPORT ----------
    private void drawSport(Canvas c) {
        // Central RPM
        panelFill(c, 500, 245, 782, 410, 55);
        liveText(c, whole(rpm), 640, 344, 78, WHITE, Paint.Align.CENTER);
        text(c, "RPM", 640, 390, 29, WHITE, Paint.Align.CENTER, true);

        // Central speed
        panelFill(c, 548, 446, 734, 560, 8);
        liveText(c, whole(speed), 630, 526, 66, WHITE, Paint.Align.CENTER);
        text(c, "km/h", 704, 528, 17, WHITE, Paint.Align.LEFT, true);

        drawMetricCard(c, 27, 310, 372, 445, 205, 384, throttle, "%", 0, 100, false);
        drawMetricCard(c, 27, 460, 401, 595, 205, 535, load, "%", 0, 100, false);
        drawMetricCard(c, 907, 310, 1257, 445, 1082, 384, intake, "°C", -20, 80, false);
        drawMetricCard(c, 907, 460, 1257, 595, 1082, 535, coolant, "°C", 50, 130, false);

        drawSportActiveArc(c);
    }

    private void drawSportActiveArc(Canvas c) {
        if (Float.isNaN(rpm)) return;
        float ratio = Math.max(0f, Math.min(1f, rpm / 8000f));
        RectF dial = new RectF(sx(405), sy(89), sx(875), sy(559));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sx(5.5f));
        p.setColor(RED);
        p.setShadowLayer(sx(7), 0, 0, Color.argb(155, 255, 55, 65));
        c.drawArc(dial, 145, 250 * ratio, false, p);
        p.clearShadowLayer();
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    // ---------- DIAGNOSTICS ----------
    private void drawDiagnostics(Canvas c) {
        // OBD connection card: wipe sample values but keep card/title/icon.
        panelFill(c, 119, 263, 420, 337, 0);
        String state = liveConnected ? "Connected" : stateShort();
        int col = liveConnected ? GREEN : (obdState == ObdManager.State.ERROR ? RED : MUTED);
        text(c, state, 125, 294, 18, col, Paint.Align.LEFT, true);
        text(c, liveConnected ? obdTransport : obdDetail, 125, 318, 12, WHITE, Paint.Align.LEFT, false);
        text(c, liveConnected ? "LIVE ECU DATA" : "No ECU data", 125, 334, 11, MUTED, Paint.Align.LEFT, false);

        // ECU status card
        panelFill(c, 520, 263, 828, 337, 0);
        text(c, liveConnected ? "ECU: RESPONDING" : "ECU: OFFLINE", 524, 292, 15, liveConnected ? GREEN : MUTED, Paint.Align.LEFT, true);
        text(c, liveConnected ? "PGM-FI / OBD-II" : "Waiting for connection", 524, 316, 12, WHITE, Paint.Align.LEFT, false);

        // Connection settings card sample text replacement.
        panelFill(c, 876, 254, 1248, 337, 0);
        text(c, "Connection type", 888, 278, 14, WHITE, Paint.Align.LEFT, false);
        text(c, obdTransport, 1132, 278, 14, WHITE, Paint.Align.LEFT, true);
        text(c, "Tap to choose adapter", 888, 307, 13, MUTED, Paint.Align.LEFT, false);
        text(c, liveConnected ? "Connected" : "Scan / Connect", 1132, 307, 13, liveConnected ? GREEN : CYAN, Paint.Align.LEFT, true);

        // 6 metric cards
        drawDiagMetric(c, 18, 342, 218, 438, 118, 393, voltage, "V", 10, 16, true);
        drawDiagMetric(c, 224, 342, 428, 438, 326, 393, coolant, "°C", 50, 130, false);
        drawDiagMetric(c, 434, 342, 637, 438, 536, 393, intake, "°C", -20, 80, false);
        drawDiagMetric(c, 643, 342, 846, 438, 745, 393, throttle, "%", 0, 100, false);
        drawDiagMetric(c, 852, 342, 1055, 438, 954, 393, load, "%", 0, 100, false);
        drawDiagMetric(c, 1061, 342, 1264, 438, 1162, 393, fuel, "%", 0, 100, false);

        // Fault codes/readiness: do not make claims without ECU data.
        panelFill(c, 95, 514, 356, 602, 0);
        text(c, liveConnected ? "ECU CONNECTED" : "NOT AVAILABLE", 95, 545, 18, liveConnected ? GREEN : MUTED, Paint.Align.LEFT, true);
        text(c, liveConnected ? "DTC reading planned next" : "Connect ECU to read DTC", 95, 570, 12, WHITE, Paint.Align.LEFT, false);

        panelFill(c, 440, 505, 773, 603, 0);
        text(c, liveConnected ? "READINESS: ECU DATA" : "READINESS: N/A", 445, 536, 16, liveConnected ? GREEN : MUTED, Paint.Align.LEFT, true);
        text(c, liveConnected ? "Monitor decoding follows in next build" : "Connect ECU first", 445, 562, 12, WHITE, Paint.Align.LEFT, false);

        // Live sensor table
        panelFill(c, 835, 487, 1258, 607, 0);
        float y = 513;
        sensorRow(c, "Vehicle Speed", Float.isNaN(speed) ? "--" : whole(speed) + " km/h", y); y += 19;
        sensorRow(c, "RPM", Float.isNaN(rpm) ? "--" : whole(rpm) + " rpm", y); y += 19;
        sensorRow(c, "Coolant Temp", Float.isNaN(coolant) ? "--" : whole(coolant) + " °C", y); y += 19;
        sensorRow(c, "Intake Temp", Float.isNaN(intake) ? "--" : whole(intake) + " °C", y); y += 19;
        sensorRow(c, "Throttle", Float.isNaN(throttle) ? "--" : whole(throttle) + " %", y); y += 19;
        sensorRow(c, "Engine Load", Float.isNaN(load) ? "--" : whole(load) + " %", y);
    }

    private String stateShort() {
        switch (obdState) {
            case SEARCHING: return "Searching";
            case ADAPTER_FOUND: return "Adapter found";
            case CONNECTING: return "Connecting";
            case ERROR: return "Error";
            default: return "Disconnected";
        }
    }

    private void sensorRow(Canvas c, String label, String value, float y) {
        text(c, label, 842, y, 11.5f, MUTED, Paint.Align.LEFT, false);
        text(c, value, 1240, y, 11.5f, WHITE, Paint.Align.RIGHT, true);
    }

    private void drawMetricCard(Canvas c, float l, float t, float r, float b,
                                float cx, float cy, float value, String unit,
                                float min, float max, boolean oneDecimal) {
        // Repaint only the live-value + bar portion with the same panel tone.
        panelFill(c, l + 18, t + 45, r - 18, b - 10, 3);
        String s = oneDecimal ? one(value) : whole(value);
        liveText(c, s, cx, cy, 38, WHITE, Paint.Align.CENTER);
        if (!"--".equals(s)) text(c, unit, cx + 52, cy, 15, WHITE, Paint.Align.LEFT, true);
        progress(c, l + 24, b - 45, r - 24, value, min, max, mode == Mode.SPORT && (unit.equals("%")) ? RED : GREEN);
        text(c, formatScale(min), l + 22, b - 13, 12, WHITE, Paint.Align.LEFT, false);
        text(c, formatScale((min + max) / 2f), (l + r) / 2f, b - 13, 12, WHITE, Paint.Align.CENTER, false);
        text(c, formatScale(max), r - 22, b - 13, 12, WHITE, Paint.Align.RIGHT, false);
    }

    private void drawDiagMetric(Canvas c, float l, float t, float r, float b,
                                float cx, float cy, float value, String unit,
                                float min, float max, boolean oneDecimal) {
        panelFill(c, l + 10, t + 28, r - 10, b - 8, 2);
        String s = oneDecimal ? one(value) : whole(value);
        liveText(c, s, cx, cy, 25, WHITE, Paint.Align.CENTER);
        if (!"--".equals(s)) text(c, unit, cx + 34, cy, 10.5f, WHITE, Paint.Align.LEFT, true);
        progress(c, l + 14, b - 28, r - 14, value, min, max, GREEN);
        text(c, formatScale(min), l + 14, b - 7, 9.5f, WHITE, Paint.Align.LEFT, false);
        text(c, formatScale((min + max) / 2f), (l + r) / 2f, b - 7, 9.5f, WHITE, Paint.Align.CENTER, false);
        text(c, formatScale(max), r - 14, b - 7, 9.5f, WHITE, Paint.Align.RIGHT, false);
    }

    private String formatScale(float v) {
        if (Math.abs(v - Math.round(v)) < 0.01f) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%.1f", v);
    }

    private void progress(Canvas c, float l, float y, float r, float value, float min, float max, int activeColor) {
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setColor(TRACK);
        c.drawRoundRect(new RectF(sx(l), sy(y), sx(r), sy(y + 8)), sx(4), sx(4), p);
        if (Float.isNaN(value) || max <= min) return;
        float ratio = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
        p.setColor(activeColor);
        p.setShadowLayer(sx(4), 0, 0, Color.argb(120, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor)));
        c.drawRoundRect(new RectF(sx(l), sy(y), sx(l + (r - l) * ratio), sy(y + 8)), sx(4), sx(4), p);
        p.clearShadowLayer();
    }

    // ---------- OBD ----------
    private void clearTelemetry() {
        rpm = speed = coolant = voltage = throttle = load = intake = fuel = maf = Float.NaN;
    }

    @Override
    public void onObdState(ObdManager.State state, String detail, String transport) {
        obdState = state;
        obdDetail = detail == null ? "" : detail;
        obdTransport = transport == null ? "AUTO" : transport;
        liveConnected = state == ObdManager.State.ECU_CONNECTED;
        if (!liveConnected) clearTelemetry();
        invalidate();
    }

    @Override
    public void onTelemetry(ObdManager.Telemetry t) {
        // Only accept telemetry after a verified ECU connection.
        if (!liveConnected) return;
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

    public void onBluetoothPermissionResult(boolean granted) {
        if (!granted) {
            obdState = ObdManager.State.ERROR;
            obdDetail = "Bluetooth permission denied";
            invalidate();
        }
    }

    private void openObdSelector() {
        obdSelectorOpen = true;
        invalidate();
    }

    private void connectSelected(int option) {
        obdSelectorOpen = false;
        if (option == 4) {
            obdManager.stop();
            clearTelemetry();
            invalidate();
            return;
        }
        if (option != 3 && !activity.hasObdBluetoothPermissions()) {
            obdState = ObdManager.State.ERROR;
            obdDetail = "Bluetooth permission required";
            invalidate();
            return;
        }
        if (option == 0) obdManager.startAutoConnect();
        else if (option == 1) obdManager.startBleConnect();
        else if (option == 2) obdManager.startClassicConnect();
        else if (option == 3) obdManager.startWifiConnect();
    }

    private void drawObdSelector(Canvas c) {
        p.setColor(Color.argb(205, 0, 0, 0));
        c.drawRect(0, 0, getWidth(), getHeight(), p);
        RectF panel = new RectF(sx(278), sy(120), sx(1002), sy(610));
        p.setColor(Color.rgb(5, 18, 27));
        c.drawRoundRect(panel, sx(18), sx(18), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sx(2));
        p.setColor(CYAN);
        c.drawRoundRect(panel, sx(18), sx(18), p);
        p.setStyle(Paint.Style.FILL);

        text(c, "OBD CONNECTION", 640, 164, 27, WHITE, Paint.Align.CENTER, true);
        text(c, "Vgate iCar Pro BLE 4.0 DUAL", 640, 190, 14, MUTED, Paint.Align.CENTER, false);
        obdOption(c, 220, "AUTO", "BT Classic → BLE");
        obdOption(c, 295, "BLE 4.0", "Recommended for your Vgate adapter");
        obdOption(c, 370, "BT 3.0 / CLASSIC", "Use paired Bluetooth adapter");
        obdOption(c, 445, "WI-FI ELM327", "192.168.0.10 : 35000");
        obdOption(c, 520, "DISCONNECT", "Stop live OBD and clear readings");
    }

    private void obdOption(Canvas c, float top, String title, String sub) {
        RectF r = new RectF(sx(318), sy(top), sx(962), sy(top + 61));
        p.setColor(Color.rgb(9, 29, 39));
        c.drawRoundRect(r, sx(9), sx(9), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sx(1));
        p.setColor(Color.rgb(42, 120, 150));
        c.drawRoundRect(r, sx(9), sx(9), p);
        p.setStyle(Paint.Style.FILL);
        text(c, title, 345, top + 25, 18, WHITE, Paint.Align.LEFT, true);
        text(c, sub, 345, top + 47, 12, MUTED, Paint.Align.LEFT, false);
        text(c, ">", 930, top + 37, 23, CYAN, Paint.Align.CENTER, true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float x = e.getX() * 1280f / getWidth();
        float y = e.getY() * 720f / getHeight();

        if (obdSelectorOpen) {
            if (in(x,y,318,220,962,281)) connectSelected(0);
            else if (in(x,y,318,295,962,356)) connectSelected(1);
            else if (in(x,y,318,370,962,431)) connectSelected(2);
            else if (in(x,y,318,445,962,506)) connectSelected(3);
            else if (in(x,y,318,520,962,581)) connectSelected(4);
            else { obdSelectorOpen = false; invalidate(); }
            return true;
        }

        if (y < 78 && x > 885 && x < 1160) {
            openObdSelector(); return true;
        }
        if (mode == Mode.DIAGNOSTICS && in(x,y,850,250,1260,345)) {
            openObdSelector(); return true;
        }
        if (y >= 610) {
            if (x < 426) mode = Mode.STREET;
            else if (x < 853) mode = Mode.SPORT;
            else mode = Mode.DIAGNOSTICS;
            invalidate();
            return true;
        }
        return true;
    }

    private boolean in(float x, float y, float l, float t, float r, float b) {
        return x >= l && x <= r && y >= t && y <= b;
    }
}
