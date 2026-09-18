package com.civicfa1.dashboard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Civic FA1 Dashboard v0.8.1 locked-reference UI.
 *
 * Three fixed 1280x720 modes: CONNECT / SPORT / DIAGNOSTICS.
 * The three approved 1280x720 disconnected-state references are the locked visual shell. Runtime code replaces
 * only changing values/statuses with native overlays while preserving the approved geometry, atmosphere,
 * navigation, typography hierarchy and disconnected appearance 1:1. There is no generated/demo telemetry in normal mode.
 */
public final class DashboardView extends View implements ObdManager.Listener {

    private enum Mode { CONNECT, SPORT, DIAGNOSTICS }
    private enum Icon { OBD, CHIP, RADIO, BLUETOOTH, GEAR, LINK, SEARCH, LIST, LOG, CLOCK, PULSE, BATTERY, TEMP, AIR, THROTTLE, ENGINE, FUEL, ROAD, FLAG, WARNING, CHECK, O2, SNOW, LEAF, INFO, WIFI }

    private enum SensorKey {
        COOLANT("COOLANT TEMP", "°C", 50f, 130f, 0x05, 0, Icon.TEMP),
        INTAKE("INTAKE TEMP", "°C", -20f, 80f, 0x0F, 0, Icon.AIR),
        MODULE_VOLTAGE("MODULE VOLTAGE", "V", 10f, 16f, 0x42, 1, Icon.BATTERY),
        ADAPTER_VOLTAGE("ADAPTER VOLTAGE", "V", 10f, 16f, -2, 1, Icon.BATTERY),
        THROTTLE("THROTTLE", "%", 0f, 100f, 0x11, 0, Icon.THROTTLE),
        LOAD("ENGINE LOAD", "%", 0f, 100f, 0x04, 0, Icon.ENGINE),
        FUEL("FUEL LEVEL", "%", 0f, 100f, 0x2F, 0, Icon.FUEL),
        MAF("MAF", "g/s", 0f, 150f, 0x10, 1, Icon.PULSE),
        MAP("MAP", "kPa", 0f, 110f, 0x0B, 0, Icon.PULSE),
        STFT("SHORT FUEL TRIM", "%", -25f, 25f, 0x06, 1, Icon.FUEL),
        LTFT("LONG FUEL TRIM", "%", -25f, 25f, 0x07, 1, Icon.FUEL),
        TIMING("IGNITION TIMING", "°", -20f, 50f, 0x0E, 1, Icon.PULSE),
        FUEL_RATE("FUEL RATE", "L/h", 0f, 30f, 0x5E, 1, Icon.FUEL);

        final String title;
        final String unit;
        final float min;
        final float max;
        final int pid;
        final int decimals;
        final Icon icon;
        SensorKey(String title, String unit, float min, float max, int pid, int decimals, Icon icon) {
            this.title = title; this.unit = unit; this.min = min; this.max = max;
            this.pid = pid; this.decimals = decimals; this.icon = icon;
        }
    }

    private static final class SensorReading {
        final SensorKey key;
        final float value;
        final SensorFreshness.State state;
        SensorReading(SensorKey key, float value, SensorFreshness.State state) {
            this.key = key; this.value = value; this.state = state;
        }
        String display() {
            if (state == SensorFreshness.State.UNSUPPORTED) return "N/A";
            if (state != SensorFreshness.State.VALID || Float.isNaN(value)) return "--";
            return key.decimals == 0 ? Integer.toString(Math.round(value)) : String.format(Locale.US, "%.1f", value);
        }
    }

    private static final float DW = 1280f;
    private static final float DH = 720f;
    private static final float HEADER_H = 60f;
    private static final float NAV_TOP = 632f;
    private static final float NAV_BOTTOM = 716f;

    private static final int BG = Color.rgb(2, 9, 14);
    private static final int PANEL = Color.rgb(3, 16, 20);
    private static final int PANEL_2 = Color.rgb(5, 22, 27);
    private static final int BORDER = Color.rgb(52, 76, 85);
    private static final int WHITE = Color.rgb(244, 247, 249);
    private static final int MUTED = Color.rgb(188, 199, 207);
    private static final int DIM = Color.rgb(102, 120, 129);
    private static final int GREEN = Color.rgb(24, 240, 145);
    private static final int CYAN = Color.rgb(31, 224, 201);
    private static final int RED = Color.rgb(255, 55, 76);
    private static final int YELLOW = Color.rgb(255, 214, 40);
    private static final int ORANGE = Color.rgb(255, 146, 28);

    private final MainActivity activity;
    private final ObdManager obd;
    private final android.content.SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final RectF canvasBounds = new RectF(0, 0, DW, DH);
    private final Typeface regular = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Typeface italicBold = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
    private final Typeface wide = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface wideBold = Typeface.create("sans-serif", Typeface.BOLD);
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.US);

    private final Bitmap connectArtwork;
    private final Bitmap sportArtwork;
    private final Bitmap diagnosticsArtwork;

    private Mode mode = Mode.CONNECT;
    private ObdManager.State obdState = ObdManager.State.DISCONNECTED;
    private ObdManager.Transport activeTransport = ObdManager.Transport.AUTO;
    private ObdManager.Transport selectedTransport = ObdManager.Transport.BLE;
    private boolean autoTransport = true;
    private String obdDetail = "Choose an adapter";
    private String adapterName = "";
    private String selectedDeviceKey = "";
    private String connectedAddress = "";
    private long connectedAtMs;
    private long lastConnectedAtMs;
    private int deviceScroll;

    private ObdManager.Telemetry telemetry = new ObdManager.Telemetry();
    private ObdManager.Readiness readiness = new ObdManager.Readiness();
    private ObdManager.DtcResult dtc = new ObdManager.DtcResult();
    private List<ObdManager.DeviceInfo> devices = new ArrayList<>();

    private boolean autoReconnect;
    private boolean autoDetectProtocol;
    private boolean lowPowerMode;
    private String wifiHost;
    private int wifiPort;
    private int connectionTimeoutMs;

    private final SensorKey[] sportSlots = new SensorKey[5];
    private int sensorPickerSlot = -1;
    private float diagLiveScroll;
    private float touchDownY;
    private float lastTouchY;
    private boolean diagDragging;

    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private boolean firstHostStart = true;

    // v0.8.4 performance: coalesce frequent ELM telemetry callbacks into UI frames.
    private static final long UI_FRAME_MS = 33L; // ~30 FPS on UIS8581A.
    private static final float TACH_MAX_RPM = 8000f;
    private static final float TACH_GREEN_END_RPM = 2500f;
    private static final float TACH_YELLOW_END_RPM = 4500f;
    private ObdManager.Telemetry pendingTelemetry;
    private boolean telemetryFrameScheduled;
    private float displayedRpm = Float.NaN;
    private float targetRpm = Float.NaN;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            invalidate();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable telemetryFrame = new Runnable() {
        @Override public void run() {
            telemetryFrameScheduled = false;
            if (pendingTelemetry != null) {
                telemetry = pendingTelemetry;
                pendingTelemetry = null;
                targetRpm = telemetry.rpm;
                if (Float.isNaN(displayedRpm) && !Float.isNaN(targetRpm)) displayedRpm = targetRpm;
            }

            boolean animating = false;
            if (mode == Mode.SPORT && obdState == ObdManager.State.ECU_CONNECTED && !Float.isNaN(targetRpm)) {
                if (Float.isNaN(displayedRpm)) displayedRpm = targetRpm;
                float delta = targetRpm - displayedRpm;
                if (Math.abs(delta) > 2f) {
                    // Visual damping only. The center number remains the latest real ECU value.
                    displayedRpm += delta * 0.34f;
                    animating = true;
                } else {
                    displayedRpm = targetRpm;
                }
            } else {
                displayedRpm = targetRpm;
            }

            invalidate();
            if (pendingTelemetry != null || animating) scheduleTelemetryFrame();
        }
    };

    private void scheduleTelemetryFrame() {
        if (telemetryFrameScheduled) return;
        telemetryFrameScheduled = true;
        handler.postDelayed(telemetryFrame, UI_FRAME_MS);
    }

    // Geometry measured directly from the three approved 1280x720 reference images.
    // The reference bitmap itself is the immutable visual shell; these RectF values are interaction
    // and dynamic-value zones only. They intentionally match the approved artwork rather than the
    // earlier v0.7 programmatic approximation.
    private final RectF[] connectCards = {
            new RectF(23, 176, 435, 308), new RectF(445, 176, 838, 308), new RectF(847, 176, 1256, 308),
            new RectF(23, 314, 435, 494), new RectF(445, 314, 809, 494), new RectF(816, 314, 1256, 494),
            new RectF(23, 500, 435, 649), new RectF(445, 500, 838, 649), new RectF(847, 500, 1256, 649)
    };

    private final RectF sportLeftBig = new RectF(30, 302, 381, 458);
    private final RectF sportRightBig = new RectF(899, 302, 1249, 458);
    private final RectF sportBottomLeft = new RectF(30, 470, 429, 586);
    private final RectF sportBottomMid = new RectF(442, 470, 837, 586);
    private final RectF sportBottomRight = new RectF(850, 470, 1249, 586);

    public DashboardView(MainActivity activity) {
        super(activity);
        this.activity = activity;
        this.obd = new ObdManager(activity, this);
        this.prefs = activity.getSharedPreferences("civic_dashboard", 0);

        autoReconnect = prefs.getBoolean("auto_reconnect", true);
        autoDetectProtocol = prefs.getBoolean("auto_detect_protocol", true);
        lowPowerMode = prefs.getBoolean("low_power_mode", false);
        wifiHost = prefs.getString("wifi_host", "192.168.0.10");
        wifiPort = prefs.getInt("wifi_port", 35000);
        connectionTimeoutMs = prefs.getInt("connection_timeout_ms", 5000);

        selectedTransport = parseTransport(prefs.getString("ui_selected_transport", ObdManager.Transport.BLE.name()), ObdManager.Transport.BLE);
        autoTransport = prefs.getBoolean("ui_auto_transport", true);
        if (selectedTransport == ObdManager.Transport.AUTO) {
            selectedTransport = ObdManager.Transport.BLE;
            autoTransport = true;
        }
        connectedAddress = prefs.getString("verified_address", "");

        sportSlots[0] = loadSlot(0, SensorKey.COOLANT);
        sportSlots[1] = loadSlot(1, SensorKey.MODULE_VOLTAGE);
        sportSlots[2] = loadSlot(2, SensorKey.THROTTLE);
        sportSlots[3] = loadSlot(3, SensorKey.LOAD);
        sportSlots[4] = loadSlot(4, SensorKey.INTAKE);

        connectArtwork = BitmapFactory.decodeResource(getResources(), R.drawable.background_connect);
        sportArtwork = BitmapFactory.decodeResource(getResources(), R.drawable.background_sport);
        diagnosticsArtwork = BitmapFactory.decodeResource(getResources(), R.drawable.background_diagnostics);

        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(regular);
        glow.setTypeface(italicBold);
        setFocusable(true);
    }

    private SensorKey loadSlot(int slot, SensorKey fallback) {
        String s = prefs.getString("sport_slot_" + slot, fallback.name());
        try { return SensorKey.valueOf(s); } catch (Exception ignored) { return fallback; }
    }

    private boolean sportUsesDefaultSlots() {
        return sportSlots[0] == SensorKey.COOLANT &&
                sportSlots[1] == SensorKey.MODULE_VOLTAGE &&
                sportSlots[2] == SensorKey.THROTTLE &&
                sportSlots[3] == SensorKey.LOAD &&
                sportSlots[4] == SensorKey.INTAKE;
    }

    private ObdManager.Transport parseTransport(String s, ObdManager.Transport fallback) {
        try { return ObdManager.Transport.valueOf(s); } catch (Exception ignored) { return fallback; }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(clockTick);
        super.onDetachedFromWindow();
    }

    public void onHostStart() {
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        obd.refreshDevices();
        if (autoReconnect && activity.hasObdBluetoothPermissions() && obdState == ObdManager.State.DISCONNECTED) {
            handler.postDelayed(obd::connectAuto, firstHostStart ? 900L : 1400L);
        }
        firstHostStart = false;
    }

    public void onHostStop() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(telemetryFrame);
        telemetryFrameScheduled = false;
        pendingTelemetry = null;
        obd.suspend();
    }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        pendingTelemetry = null;
        telemetryFrameScheduled = false;
        obd.shutdown();
        recycle(connectArtwork);
        recycle(sportArtwork);
        recycle(diagnosticsArtwork);
    }

    private void recycle(Bitmap b) {
        if (b != null && !b.isRecycled()) b.recycle();
    }

    public void onBluetoothPermissionResult(boolean granted) {
        if (granted) {
            obd.refreshDevices();
            if (autoReconnect) obd.connectAuto();
        } else {
            obdState = ObdManager.State.PERMISSION_REQUIRED;
            obdDetail = "Bluetooth permission required";
            invalidate();
        }
    }

    @Override public boolean hasBluetoothPermission() {
        return activity.hasObdBluetoothPermissions();
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

        // v0.8.1: render the locked approved reference shell 1:1. We no longer reconstruct the visual
        // language with generic Canvas panels; only the values/statuses that must change at runtime
        // are painted on top of clean, locally matched patches.
        drawBackground(canvas);
        drawReferenceHeaderOverlay(canvas);
        switch (mode) {
            case CONNECT: drawReferenceConnectOverlay(canvas); break;
            case SPORT: drawReferenceSportOverlay(canvas); break;
            case DIAGNOSTICS: drawReferenceDiagnosticsOverlay(canvas); break;
        }

        if (sensorPickerSlot >= 0) drawSensorPicker(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas c) {
        fill.setColor(Color.BLACK);
        c.drawRect(0, 0, DW, DH, fill);
        Bitmap b = mode == Mode.CONNECT ? connectArtwork : mode == Mode.SPORT ? sportArtwork : diagnosticsArtwork;
        if (b != null && !b.isRecycled()) {
            fill.setAlpha(255);
            c.drawBitmap(b, null, canvasBounds, fill);
        }
    }

    private int modeAccent() {
        return mode == Mode.SPORT ? RED : mode == Mode.DIAGNOSTICS ? CYAN : GREEN;
    }

    private void drawHeader(Canvas c) {
        fill.setColor(Color.argb(252, 1, 9, 12));
        c.drawRect(0, 0, DW, HEADER_H, fill);
        line(c, 0, HEADER_H - 1, DW, HEADER_H - 1, Color.argb(95, 87, 121, 136), 1f);

        drawHondaMark(c, 26, 8);
        label(c, "CIVIC FA1", 92, 29, 25, WHITE, Paint.Align.LEFT, true, true);
        label(c, "i-VTEC · 1.8L R18A", 94, 48, 11, MUTED, Paint.Align.LEFT, false, true);

        String title = mode == Mode.CONNECT ? "CONNECTION MODE" : mode == Mode.SPORT ? "SPORT MODE" : "DIAGNOSTICS MODE";
        String sub = mode == Mode.CONNECT ? "OBD SETUP & LINK" : mode == Mode.SPORT ? "HIGHER STANDARDS" : "SYSTEM HEALTH MONITOR";
        int accent = modeAccent();
        line(c, 402, 25, 493, 25, Color.argb(155, 190, 204, 211), 1.1f);
        line(c, 788, 25, 879, 25, Color.argb(155, 190, 204, 211), 1.1f);
        glowLabel(c, title, 640, 31, 27, accent, Paint.Align.CENTER, true, 2.3f);
        label(c, sub, 640, 51, 11, MUTED, Paint.Align.CENTER, false, true);

        int sc = stateColor();
        circle(c, 913, 21, 6.5f, sc);
        label(c, "OBD: " + stateTitle(), 934, 24, 15, sc, Paint.Align.LEFT, true, false);
        label(c, stateSubtitle(), 934, 43, 9.5f, MUTED, Paint.Align.LEFT, false, false);
        line(c, 1110, 8, 1110, 51, Color.argb(80, 140, 155, 164), 1f);
        label(c, clockFormat.format(new Date()), 1161, 31, 20, WHITE, Paint.Align.CENTER, true, false);
        drawIcon(c, Icon.WIFI, 1234, 24, WHITE, 0.70f);
    }

    private int stateColor() {
        if (obdState == ObdManager.State.ECU_CONNECTED) return GREEN;
        if (obdState == ObdManager.State.ERROR) return RED;
        if (obdState == ObdManager.State.DISCONNECTED) return RED;
        return CYAN;
    }

    private String stateTitle() {
        switch (obdState) {
            case ECU_CONNECTED: return "CONNECTED";
            case SEARCHING: return "SEARCHING";
            case ADAPTER_FOUND: return "ADAPTER FOUND";
            case CONNECTING: return "CONNECTING";
            case INITIALIZING: return "INITIALIZING";
            case ECU_CONNECTING: return "ECU HANDSHAKE";
            case PERMISSION_REQUIRED: return "PERMISSION";
            case ERROR: return "ERROR";
            default: return "DISCONNECTED";
        }
    }

    private String stateSubtitle() {
        if (obdState == ObdManager.State.ECU_CONNECTED) {
            String p = telemetry.protocol == null || telemetry.protocol.isEmpty() ? transportLabel(activeTransport) : telemetry.protocol;
            return truncate(p, 26);
        }
        if (obdState == ObdManager.State.DISCONNECTED) {
            return mode == Mode.DIAGNOSTICS ? "ALL SYSTEMS UNAVAILABLE" : "ALL SYSTEMS OFFLINE";
        }
        return truncate(obdDetail == null || obdDetail.isEmpty() ? "No ECU data" : obdDetail, 33);
    }


    // -----------------------------------------------------------------------------------------
    // v0.8.1 LOCKED-REFERENCE OVERLAYS
    // -----------------------------------------------------------------------------------------

    private void refPatch(Canvas c, float l, float t, float r, float b, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawRect(l, t, r, b, fill);
    }

    private int refPanelColor() {
        if (mode == Mode.CONNECT) return Color.rgb(0, 16, 19);
        if (mode == Mode.DIAGNOSTICS) return Color.rgb(2, 17, 18);
        return Color.rgb(4, 13, 17);
    }
    private int refPanelColor2() {
        if (mode == Mode.CONNECT) return Color.rgb(3, 17, 21);
        if (mode == Mode.DIAGNOSTICS) return Color.rgb(1, 15, 17);
        return Color.rgb(4, 15, 18);
    }
    private int refHeaderColor() {
        if (mode == Mode.CONNECT) return Color.rgb(0, 10, 15);
        if (mode == Mode.DIAGNOSTICS) return Color.rgb(0, 9, 13);
        return Color.rgb(1, 8, 11);
    }
    private int refTachColor() { return Color.rgb(1, 5, 8); }

    private void drawReferenceHeaderOverlay(Canvas c) {
        // The disconnected-state reference already contains the exact approved OBD status. Keep it
        // untouched while disconnected and replace only the clock. Other states replace the status
        // area with live text using the same geometry.
        if (obdState != ObdManager.State.DISCONNECTED) {
            refPatch(c, 907, 7, 1117, 57, refHeaderColor());
            int sc = stateColor();
            circle(c, 920, 29, 6.5f, sc);
            label(c, "OBD:", 946, 31, 17, WHITE, Paint.Align.LEFT, true, false);
            label(c, stateTitle(), 990, 31, 17, sc, Paint.Align.LEFT, true, false);
            label(c, stateSubtitle(), 946, 49, 10, MUTED, Paint.Align.LEFT, false, false);
        }

        refPatch(c, 1132, 8, 1210, 53, refHeaderColor());
        label(c, clockFormat.format(new Date()), 1170, 36, 22, WHITE, Paint.Align.CENTER, false, false);
    }

    private void drawReferenceSportOverlay(Canvas c) {
        // v0.8.4: the tach arc itself shows RPM. No separate needle/marker and no always-on colors.
        drawDynamicRpmArc(c);
        normalizeSportNavigation(c);

        // Preserve approved disconnected card/value typography while keeping the tach dark.
        if (obdState == ObdManager.State.DISCONNECTED && sportUsesDefaultSlots()) return;

        // Center RPM remains the latest truthful ECU sample; smoothing affects only the arc.
        refPatch(c, 523, 252, 758, 323, refTachColor());
        String rpm = valueText(telemetry.rpm, 0x0C, 0);
        glowLabel(c, rpm, 640, 313, 66, WHITE, Paint.Align.CENTER, true, 1.8f);

        // Integrated speed pedestal.
        fill.setColor(Color.rgb(5, 11, 14));
        path.reset();
        path.moveTo(506, 383); path.lineTo(774, 383); path.lineTo(862, 455);
        path.lineTo(418, 455); path.close();
        c.drawPath(path, fill);
        line(c, 520, 384, 760, 384, RED, 2.0f);
        line(c, 438, 455, 842, 455, Color.argb(150, 255, 45, 66), 1.5f);
        glowLabel(c, valueText(telemetry.speed, 0x0D, 0), 643, 447, 70, WHITE, Paint.Align.CENTER, true, 1.8f);
        label(c, "km/h", 710, 442, 23, MUTED, Paint.Align.LEFT, false, false);

        SensorKey[] defaults = {SensorKey.COOLANT, SensorKey.MODULE_VOLTAGE, SensorKey.THROTTLE, SensorKey.LOAD, SensorKey.INTAKE};
        RectF[] slots = {sportLeftBig, sportRightBig, sportBottomLeft, sportBottomMid, sportBottomRight};
        for (int i = 0; i < slots.length; i++) {
            if (sportSlots[i] == defaults[i]) {
                drawReferenceSportDefaultValue(c, slots[i], sportSlots[i], i);
            } else {
                drawReferenceSportCustomCard(c, slots[i], sportSlots[i], i < 2);
            }
        }
    }

    private void drawDynamicRpmArc(Canvas c) {
        // background_sport.png carries a neutral/dark tach scale. Runtime paints only the live part.
        final float cx = 640f, cy = 255f, radius = 202f;
        final float startAngle = 145f, totalSweep = 250f;

        float rpm = obdState == ObdManager.State.ECU_CONNECTED && !Float.isNaN(displayedRpm)
                ? clamp(displayedRpm, 0f, TACH_MAX_RPM) : 0f;
        drawRpmArcSegment(c, cx, cy, radius, startAngle, totalSweep, 0f,
                Math.min(rpm, TACH_GREEN_END_RPM), GREEN);
        drawRpmArcSegment(c, cx, cy, radius, startAngle, totalSweep, TACH_GREEN_END_RPM,
                Math.min(rpm, TACH_YELLOW_END_RPM), YELLOW);
        drawRpmArcSegment(c, cx, cy, radius, startAngle, totalSweep, TACH_YELLOW_END_RPM,
                rpm, RED);
    }

    private void drawRpmArcSegment(Canvas c, float cx, float cy, float radius, float startAngle,
                                   float totalSweep, float fromRpm, float toRpm, int color) {
        float from = clamp(fromRpm, 0f, TACH_MAX_RPM);
        float to = clamp(toRpm, 0f, TACH_MAX_RPM);
        if (to <= from) return;
        float a = startAngle + totalSweep * (from / TACH_MAX_RPM);
        float sweep = totalSweep * ((to - from) / TACH_MAX_RPM);
        int glowColor = Color.argb(72, Color.red(color), Color.green(color), Color.blue(color));
        arc(c, cx, cy, radius, a, sweep, glowColor, 15f);
        arc(c, cx, cy, radius, a, sweep, color, 7.5f);
    }

    private void normalizeSportNavigation(Canvas c) {
        // Keep all three SPORT-screen tabs at the same physical height. The baked active SPORT glow
        // bleeds upward above the common top edge; suppress only that excess glow, not the tab body.
        fill.setColor(Color.rgb(2, 9, 14));
        c.drawRect(406, 594, 874, 603, fill);
        // Redraw a restrained active top edge at the same y-position used by the neighboring tabs.
        line(c, 422, 604, 858, 604, Color.argb(190, 255, 55, 76), 1.5f);
    }

    private void drawReferenceSportDefaultValue(Canvas c, RectF r, SensorKey key, int slot) {
        SensorReading reading = sensorReading(key);
        boolean large = slot < 2;
        if (large) {
            // Erase only the example number/unit and example green bar fill.
            if (slot == 0) refPatch(c, 122, 343, 294, 407, refPanelColor());
            else refPatch(c, 995, 342, 1194, 408, refPanelColor());
            float x = slot == 0 ? 133 : 1005;
            float valueBaseline = 400;
            glowLabel(c, reading.display(), x, valueBaseline, 58, WHITE, Paint.Align.LEFT, true, 1.4f);
            if (reading.state != SensorFreshness.State.UNSUPPORTED) {
                label(c, key.unit, slot == 0 ? 247 : 1154, 387, 26, MUTED, Paint.Align.LEFT, false, false);
            }
            float l = slot == 0 ? 49 : 918;
            float rr = slot == 0 ? 360 : 1228;
            refPatch(c, l, 411, rr, 424, Color.rgb(38, 50, 62));
            drawReferenceProgress(c, l, 411, rr, 424, reading);
        } else {
            float l = slot == 2 ? 143 : slot == 3 ? 560 : 967;
            float rr = slot == 2 ? 260 : slot == 3 ? 675 : 1090;
            refPatch(c, l, 501, rr, 539, refPanelColor());
            glowLabel(c, reading.display(), l, 534, 34, WHITE, Paint.Align.LEFT, true, 1.2f);
            if (reading.state != SensorFreshness.State.UNSUPPORTED) {
                label(c, key.unit, l + 62, 531, 17, MUTED, Paint.Align.LEFT, false, false);
            }
            float barL = slot == 2 ? 50 : slot == 3 ? 467 : 870;
            float barR = slot == 2 ? 402 : slot == 3 ? 809 : 1224;
            refPatch(c, barL, 540, barR, 552, Color.rgb(38, 50, 62));
            drawReferenceProgress(c, barL, 540, barR, 552, reading);
        }
    }

    private void drawReferenceSportCustomCard(Canvas c, RectF r, SensorKey key, boolean large) {
        // Custom cards are intentionally rebuilt only inside the card content area. The original
        // approved card border/background remains untouched.
        refPatch(c, r.left + 12, r.top + 10, r.right - 12, r.bottom - 10, refPanelColor());
        SensorReading reading = sensorReading(key);
        drawIcon(c, key.icon, r.left + 44, r.top + (large ? 45 : 37), GREEN, large ? .75f : .62f);
        label(c, key.title, r.left + (large ? 105 : 90), r.top + 34, large ? 17 : 15, MUTED, Paint.Align.LEFT, false, false);
        label(c, "›", r.right - 24, r.top + 34, 26, MUTED, Paint.Align.CENTER, false, false);
        if (large) {
            glowLabel(c, reading.display(), r.left + 106, r.top + 101, 55, WHITE, Paint.Align.LEFT, true, 1.4f);
            if (reading.state != SensorFreshness.State.UNSUPPORTED) label(c, key.unit, r.left + 235, r.top + 95, 23, MUTED, Paint.Align.LEFT, false, false);
            refPatch(c, r.left + 20, r.bottom - 46, r.right - 20, r.bottom - 34, Color.rgb(38,50,62));
            drawReferenceProgress(c, r.left + 20, r.bottom - 46, r.right - 20, r.bottom - 34, reading);
            scaleLabels(c, r, key, r.bottom - 13);
        } else {
            glowLabel(c, reading.display(), r.left + 90, r.top + 65, 34, WHITE, Paint.Align.LEFT, true, 1.3f);
            if (reading.state != SensorFreshness.State.UNSUPPORTED) label(c, key.unit, r.left + 160, r.top + 62, 17, MUTED, Paint.Align.LEFT, false, false);
            refPatch(c, r.left + 20, r.bottom - 45, r.right - 20, r.bottom - 33, Color.rgb(38,50,62));
            drawReferenceProgress(c, r.left + 20, r.bottom - 45, r.right - 20, r.bottom - 33, reading);
            scaleLabels(c, r, key, r.bottom - 12);
        }
    }

    private void drawReferenceProgress(Canvas c, float l, float t, float r, float b, SensorReading reading) {
        if (reading.state != SensorFreshness.State.VALID || Float.isNaN(reading.value)) return;
        float f = clamp((reading.value - reading.key.min) / Math.max(.001f, reading.key.max - reading.key.min), 0f, 1f);
        fill.setColor(GREEN);
        c.drawRoundRect(l, t, l + (r - l) * f, b, (b - t) / 2f, (b - t) / 2f, fill);
    }

    private void drawReferenceConnectOverlay(Canvas c) {
        // OBD ADAPTER value column.
        refPatch(c, 214, 211, 423, 303, refPanelColor());
        boolean disconnected = obdState == ObdManager.State.DISCONNECTED;
        String device = disconnected ? "Not detected" : nonEmpty(adapterName, selectedDeviceName());
        label(c, truncate(device, 28), 220, 226, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : transportLabel(activeTransport), 220, 245, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "Disconnected" : stateTitle(), 220, 264, 12, stateColor(), Paint.Align.LEFT, true, false);
        label(c, disconnected ? "--" : nonEmpty(telemetry.adapterVersion, "N/A"), 220, 283, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : nonEmpty(currentDeviceAddress(), "N/A"), 220, 301, 12, WHITE, Paint.Align.LEFT, false, false);

        // ECU LINK value column.
        refPatch(c, 648, 211, 827, 303, refPanelColor());
        label(c, disconnected ? "--" : nonEmpty(telemetry.protocol, "N/A"), 652, 226, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, obdState == ObdManager.State.ECU_CONNECTED ? "Connected" : (disconnected ? "Unavailable" : "Offline"), 652, 245, 12,
                obdState == ObdManager.State.ECU_CONNECTED ? GREEN : (disconnected ? RED : MUTED), Paint.Align.LEFT, true, false);
        label(c, disconnected ? "--" : "N/A", 652, 264, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : "N/A", 652, 283, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : "N/A", 652, 301, 12, WHITE, Paint.Align.LEFT, false, false);

        drawReferenceConnectionType(c);
        drawReferenceSettings(c);
        drawReferenceConnectionStatus(c);
        drawReferenceRecentDevices(c);
        drawReferenceTransportOptions(c);
        drawReferenceConnectionDiagnostics(c);
    }

    private void drawReferenceConnectionType(Canvas c) {
        // Remove the baked BLE example state and redraw the three choices from the same geometry.
        refPatch(c, 854, 211, 1247, 303, refPanelColor());
        float gap = 10f;
        RectF[] boxes = {
                new RectF(858, 214, 979, 300),
                new RectF(990, 214, 1111, 300),
                new RectF(1122, 214, 1243, 300)
        };
        ObdManager.Transport[] ts = {ObdManager.Transport.BLE, ObdManager.Transport.BLUETOOTH, ObdManager.Transport.WIFI};
        String[] names = {"BLE 4.0", "BT 3.0", "Wi-Fi"};
        Icon[] icons = {Icon.BLUETOOTH, Icon.BLUETOOTH, Icon.WIFI};
        for (int i=0;i<3;i++) {
            boolean active = selectedTransport == ts[i];
            fill.setColor(active ? Color.rgb(4, 55, 38) : Color.rgb(5, 20, 25));
            c.drawRoundRect(boxes[i], 6, 6, fill);
            stroke.setColor(active ? GREEN : Color.rgb(57, 83, 96));
            stroke.setStrokeWidth(active ? 1.7f : 1f);
            c.drawRoundRect(boxes[i], 6, 6, stroke);
            drawIcon(c, icons[i], boxes[i].centerX(), boxes[i].top + 24, active ? GREEN : Color.rgb(181, 204, 226), .55f);
            label(c, names[i], boxes[i].centerX(), boxes[i].top + 57, 13, WHITE, Paint.Align.CENTER, false, false);
            strokeCircle(c, boxes[i].centerX(), boxes[i].bottom - 15, 7, active ? GREEN : Color.rgb(154,181,198), 1.5f);
            if (active) circle(c, boxes[i].centerX(), boxes[i].bottom - 15, 3.2f, GREEN);
        }
    }

    private void drawReferenceSettings(Canvas c) {
        // Dynamic values/toggles only; labels and panel title remain from the approved artwork.
        refPatch(c, 642, 348, 798, 475, refPanelColor());
        drawToggle(c, 770, 360, autoReconnect);
        label(c, wifiHost, 778, 384, 11, MUTED, Paint.Align.RIGHT, false, false);
        label(c, Integer.toString(wifiPort), 778, 405, 11, MUTED, Paint.Align.RIGHT, false, false);
        label(c, connectionTimeoutMs + " ms", 778, 425, 11, MUTED, Paint.Align.RIGHT, false, false);
        drawToggle(c, 770, 444, autoDetectProtocol);
        drawToggle(c, 770, 464, lowPowerMode);
    }

    private void drawReferenceConnectionStatus(Canvas c) {
        refPatch(c, 1125, 320, 1244, 346, refPanelColor());
        String panelState = obdState == ObdManager.State.ECU_CONNECTED ? "Connected" :
                (obdState == ObdManager.State.DISCONNECTED ? "Not Connected" : stateTitle());
        label(c, panelState, 1238, 339, 12, stateColor(), Paint.Align.RIGHT, true, false);
        refPatch(c, 827, 350, 1244, 488, refPanelColor());
        float cx=901, cy=402;
        int sc = stateColor();
        strokeCircle(c, cx, cy, 34, sc, 2.2f);
        if (obdState == ObdManager.State.ECU_CONNECTED) {
            drawCheck(c, cx, cy, GREEN, .78f);
        } else if (obdState == ObdManager.State.DISCONNECTED || obdState == ObdManager.State.ERROR) {
            line(c, cx - 10, cy - 10, cx + 10, cy + 10, sc, 4f);
            line(c, cx + 10, cy - 10, cx - 10, cy + 10, sc, 4f);
        } else {
            drawIcon(c, Icon.LINK, cx, cy, sc, .65f);
        }
        label(c, obdState == ObdManager.State.ECU_CONNECTED ? "Connection Healthy" : "Not Connected",
                cx, 454, 13, sc, Paint.Align.CENTER, true, false);
        String centerDetail = obdState == ObdManager.State.ECU_CONNECTED ? "Stable communication" :
                (obdState == ObdManager.State.DISCONNECTED ? "Connect to OBD adapter" : truncate(obdDetail, 23));
        label(c, centerDetail, cx, 472, 10, MUTED, Paint.Align.CENTER, false, false);
        if (obdState == ObdManager.State.DISCONNECTED)
            label(c, "to start communication", cx, 485, 10, MUTED, Paint.Align.CENTER, false, false);

        float x=997, vr=1238;
        String session = connectedAtMs > 0 ? formatDuration(System.currentTimeMillis()-connectedAtMs) : "--";
        smallInfo(c, "Session Time:", session, x, 362, vr, WHITE);
        smallInfo(c, "Last Handshake:", lastConnectedAtMs > 0 ? clockFormat.format(new Date(lastConnectedAtMs)) : "--", x, 384, vr, WHITE);
        smallInfo(c, "Transport:", obdState == ObdManager.State.DISCONNECTED ? "--" : transportLabel(activeTransport), x, 406, vr, WHITE);
        smallInfo(c, "Protocol:", obdState == ObdManager.State.DISCONNECTED ? "--" : nonEmpty(telemetry.protocol, "N/A"), x, 428, vr, WHITE);
        smallInfo(c, "Signal Quality:", obdState == ObdManager.State.DISCONNECTED ? "--" : "N/A", x, 450, vr, MUTED);
        smallInfo(c, "Errors:", obdState == ObdManager.State.DISCONNECTED ? "--" : (obdState == ObdManager.State.ERROR ? "1+" : "0"), x, 472, vr, obdState == ObdManager.State.ERROR ? RED : WHITE);
    }

    private void drawReferenceRecentDevices(Canvas c) {
        refPatch(c, 31, 536, 424, 643, refPanelColor());
        List<ObdManager.DeviceInfo> list = visibleDevicePool();
        int start = Math.min(deviceScroll, Math.max(0, list.size()-4));
        if (list.isEmpty()) {
            label(c, "No devices discovered yet", 51, 563, 11, MUTED, Paint.Align.LEFT, false, false);
            label(c, "Use Scan above", 51, 586, 10, DIM, Paint.Align.LEFT, false, false);
            return;
        }
        for (int row=0; row<4 && start+row<list.size(); row++) {
            ObdManager.DeviceInfo d = list.get(start+row);
            float y = 552 + row*26;
            boolean selected = d.key().equals(selectedDeviceKey);
            if (selected) {
                fill.setColor(Color.rgb(3, 57, 40));
                c.drawRoundRect(34, y-15, 422, y+7, 4,4,fill);
                stroke.setColor(GREEN); stroke.setStrokeWidth(1f); c.drawRoundRect(34, y-15, 422, y+7, 4,4,stroke);
            }
            circle(c, 53, y-4, 5, selected ? GREEN : Color.rgb(194,210,231));
            label(c, truncate(d.name, 24), 80, y, 11.5f, WHITE, Paint.Align.LEFT, false, false);
            label(c, truncate(d.address, 20), 354, y, 10.5f, MUTED, Paint.Align.RIGHT, false, false);
        }
    }

    private void drawReferenceTransportOptions(Canvas c) {
        refPatch(c, 455, 536, 827, 643, refPanelColor());
        String[] names={"Auto (Recommended)","BLE 4.0","Bluetooth 3.0","Wi-Fi (TCP)","Wi-Fi (UDP)"};
        String[] notes={"Verified adapter only","Low power, stable","Wider compatibility","TCP endpoint","UDP not used"};
        ObdManager.Transport[] types={ObdManager.Transport.AUTO,ObdManager.Transport.BLE,ObdManager.Transport.BLUETOOTH,ObdManager.Transport.WIFI,ObdManager.Transport.WIFI};
        for(int i=0;i<5;i++){
            float y=551+i*21.5f;
            boolean active = i == 0 ? autoTransport : (!autoTransport && selectedTransport==types[i] && (types[i]!=ObdManager.Transport.WIFI || i==3));
            strokeCircle(c,474,y-3,7,active?GREEN:Color.rgb(173,196,220),1.5f);
            if(active) circle(c,474,y-3,3.2f,GREEN);
            label(c,names[i],501,y,11.5f,active?GREEN:WHITE,Paint.Align.LEFT,active,false);
            label(c,notes[i],814,y,10,MUTED,Paint.Align.RIGHT,false,false);
        }
    }

    private void drawReferenceConnectionDiagnostics(Canvas c) {
        refPatch(c, 858, 536, 1244, 643, refPanelColor());
        String[] names={"Adapter detected","Transport initialized","Protocol handshake","ECU responding","Data stream active","Total connect time"};
        boolean adapter = obdState != ObdManager.State.DISCONNECTED && obdState != ObdManager.State.SEARCHING;
        boolean transport = obdState == ObdManager.State.INITIALIZING || obdState == ObdManager.State.ECU_CONNECTING || obdState == ObdManager.State.ECU_CONNECTED;
        boolean handshake = obdState == ObdManager.State.ECU_CONNECTING || obdState == ObdManager.State.ECU_CONNECTED;
        boolean ecu = obdState == ObdManager.State.ECU_CONNECTED;
        boolean data = ecu && hasAnyFreshTelemetry();
        boolean[] ok={adapter,transport,handshake,ecu,data,ecu};
        for(int i=0;i<6;i++){
            float y=552+i*18.5f;
            strokeCircle(c,877,y-3,6,ok[i]?GREEN:Color.rgb(132,160,176),1.5f);
            if(ok[i]) drawCheck(c,877,y-3,GREEN,.28f);
            label(c,names[i],900,y,11,WHITE,Paint.Align.LEFT,false,false);
            String right = i==5 ? (connectedAtMs>0?formatDuration(System.currentTimeMillis()-connectedAtMs):"--") : (ok[i]?"OK":"Pending");
            label(c,right,1230,y,10.5f,ok[i]?GREEN:MUTED,Paint.Align.RIGHT,true,false);
        }
    }

    private void drawReferenceDiagnosticsOverlay(Canvas c) {
        // The locked disconnected diagnostics reference already contains truthful -- / N/A /
        // WAITING FOR ECU states. Preserve it pixel-for-pixel until the connection state changes.
        if (obdState == ObdManager.State.DISCONNECTED) return;

        SensorKey[] keys={SensorKey.MODULE_VOLTAGE,SensorKey.COOLANT,SensorKey.INTAKE,SensorKey.THROTTLE,SensorKey.LOAD,SensorKey.FUEL};
        RectF[] cards={
                new RectF(28,226,218,321),new RectF(228,226,441,321),new RectF(452,226,640,321),
                new RectF(650,226,851,321),new RectF(862,226,1060,321),new RectF(1072,226,1255,321)
        };
        for(int i=0;i<6;i++) drawReferenceDiagnosticTopValue(c,cards[i],keys[i]);

        drawReferenceDtcBody(c);
        drawReferenceReadinessBody(c);
        drawReferenceLiveBody(c);
        drawReferenceFreezeBody(c);
        drawReferenceEmissionsBody(c);
        drawReferenceEcuBody(c);
    }

    private void drawReferenceDiagnosticTopValue(Canvas c, RectF r, SensorKey key) {
        SensorReading reading=sensorReading(key);
        refPatch(c,r.left+63,r.top+22,r.right-8,r.top+65,refPanelColor());
        glowLabel(c,reading.display(),r.left+69,r.top+55,30,WHITE,Paint.Align.LEFT,true,1.1f);
        if(reading.state!=SensorFreshness.State.UNSUPPORTED) label(c,key.unit,r.left+126,r.top+54,14,MUTED,Paint.Align.LEFT,false,false);
        refPatch(c,r.left+14,r.bottom-35,r.right-14,r.bottom-26,Color.rgb(38,50,62));
        drawReferenceProgress(c,r.left+14,r.bottom-35,r.right-14,r.bottom-26,reading);
    }

    private void drawReferenceDtcBody(Canvas c) {
        refPatch(c,44,382,386,511,refPanelColor());
        float cx=73,cy=416;
        switch(dtc.status){
            case NO_CODES:
                strokeCircle(c,cx,cy,23,GREEN,2.4f); drawCheck(c,cx,cy,GREEN,.62f);
                label(c,"NO FAULT CODES",115,420,16,GREEN,Paint.Align.LEFT,true,false);
                label(c,"Mode 03 completed. No stored DTC.",115,451,11,MUTED,Paint.Align.LEFT,false,false);
                break;
            case HAS_CODES:
                strokeCircle(c,cx,cy,23,RED,2.4f); drawIcon(c,Icon.WARNING,cx,cy,RED,.42f);
                label(c,dtc.codes.size()+" STORED DTC",115,416,16,RED,Paint.Align.LEFT,true,false);
                for(int i=0;i<Math.min(4,dtc.codes.size());i++) label(c,dtc.codes.get(i),115,443+i*18,12,WHITE,Paint.Align.LEFT,true,false);
                break;
            case ERROR:
                strokeCircle(c,cx,cy,23,RED,2.4f); drawIcon(c,Icon.WARNING,cx,cy,RED,.42f);
                label(c,"READ ERROR",115,416,16,RED,Paint.Align.LEFT,true,false);
                label(c,truncate(dtc.detail,35),115,447,11,MUTED,Paint.Align.LEFT,false,false);
                break;
            default:
                strokeCircle(c,cx,cy,23,CYAN,2f);
                label(c,"...",cx,cy+5,15,CYAN,Paint.Align.CENTER,true,false);
                label(c,"WAITING FOR ECU",115,416,16,CYAN,Paint.Align.LEFT,true,false);
                label(c,"Unable to read fault codes.",115,442,11,MUTED,Paint.Align.LEFT,false,false);
                label(c,"Check OBD connection.",115,462,11,MUTED,Paint.Align.LEFT,false,false);
        }
    }

    private void drawReferenceReadinessBody(Canvas c) {
        refPatch(c,430,383,842,511,refPanelColor());
        if(!readiness.available){
            label(c,"WAITING FOR ECU",480,414,15,CYAN,Paint.Align.LEFT,true,false);
            String[] waiting={"Misfire Monitor","Fuel System","Comprehensive Components","Catalyst Monitor","Heated Catalyst","Evaporative System","Secondary Air System","Oxygen Sensor","EGR System"};
            for(int i=0;i<waiting.length;i++){
                int col=i>=4?1:0;
                int row=i>=4?i-4:i;
                float x=438+col*220, y=438+row*22;
                circle(c,x,y-4,7,Color.rgb(120,140,150));
                label(c,"--",x,y,9,Color.rgb(40,55,62),Paint.Align.CENTER,true,false);
                label(c,waiting[i],x+20,y,10.5f,MUTED,Paint.Align.LEFT,false,false);
                label(c,"N/A",x+195,y,10.5f,MUTED,Paint.Align.RIGHT,false,false);
            }
            return;
        }
        label(c,readinessSummary(),480,378,15,GREEN,Paint.Align.LEFT,true,false);
        String[] wanted={"Misfire","Fuel System","Components","Catalyst","Heated Catalyst","Evaporative System","Secondary Air","O2 Sensor","EGR"};
        int shown=0;
        for(String name:wanted){
            ObdManager.MonitorState ms=findMonitorState(name);
            if(ms==null || ms==ObdManager.MonitorState.UNSUPPORTED) continue;
            int col=shown>=4?1:0;
            int row=shown>=4?shown-4:shown;
            float x=438+col*220, y=406+row*23;
            int colr=ms==ObdManager.MonitorState.COMPLETE?GREEN:ORANGE;
            circle(c,x,y-4,7,colr);
            if(ms==ObdManager.MonitorState.COMPLETE) drawCheck(c,x,y-4,Color.rgb(0,60,40),.27f);
            else label(c,"!",x,y,10,Color.rgb(0,60,40),Paint.Align.CENTER,true,false);
            label(c,name,x+20,y,11.5f,WHITE,Paint.Align.LEFT,false,false);
            shown++;
            if(shown>=8) break;
        }
    }

    private void drawReferenceLiveBody(Canvas c) {
        refPatch(c,876,360,1245,519,refPanelColor());
        List<SensorKey> keys=liveSensorKeys();
        float y=375-diagLiveScroll;
        int rows=0;
        for(SensorKey k:keys){
            if(y>=369 && y<=514){
                SensorReading reading=sensorReading(k);
                label(c,k.title,880,y,10.5f,MUTED,Paint.Align.LEFT,false,false);
                label(c,reading.display(),1158,y,10.5f,WHITE,Paint.Align.RIGHT,true,false);
                label(c,reading.state==SensorFreshness.State.UNSUPPORTED?"":k.unit,1175,y,10,MUTED,Paint.Align.LEFT,false,false);
                line(c,880,y+5,1238,y+5,Color.argb(45,100,130,140),1f);
            }
            y+=18.5f; rows++;
            if(rows>20) break;
        }
    }

    private void drawReferenceFreezeBody(Canvas c) {
        refPatch(c,96,563,412,628,refPanelColor());
        label(c,"NO FREEZE FRAME DATA",101,579,13,CYAN,Paint.Align.LEFT,true,false);
        label(c,"No freeze frame data available.",101,603,10.5f,MUTED,Paint.Align.LEFT,false,false);
        label(c,obdState == ObdManager.State.ECU_CONNECTED ? "(Mode 02 not read)" : "(Waiting for ECU)",101,620,10.5f,MUTED,Paint.Align.LEFT,false,false);
    }

    private void drawReferenceEmissionsBody(Canvas c) {
        refPatch(c,690,559,812,626,refPanelColor());
        int col = readiness.available ? GREEN : MUTED;
        label(c,readiness.available ? (readiness.milOn?"ON":"OFF") : "N/A",805,570,11,col,Paint.Align.RIGHT,true,false);
        label(c,readiness.available ? Integer.toString(readiness.dtcCount) : "N/A",805,589,11,col,Paint.Align.RIGHT,true,false);
        label(c,readiness.available ? readinessSummaryShort() : "N/A",805,608,11,col,Paint.Align.RIGHT,true,false);
        label(c,readiness.available && !readiness.milOn ? "READY" : "N/A",805,626,11,col,Paint.Align.RIGHT,true,false);
    }

    private void drawReferenceEcuBody(Canvas c) {
        refPatch(c,1057,557,1243,633,refPanelColor());
        label(c,obdState==ObdManager.State.ECU_CONNECTED?"PGM-FI (Honda)":"N/A",1237,568,10.5f,WHITE,Paint.Align.RIGHT,false,false);
        label(c,"N/A",1237,586,10.5f,MUTED,Paint.Align.RIGHT,false,false);
        label(c,nonEmpty(telemetry.protocol,"N/A"),1237,604,10.5f,WHITE,Paint.Align.RIGHT,false,false);
        label(c,"N/A",1237,622,10.5f,MUTED,Paint.Align.RIGHT,false,false);
    }

    private String formatDuration(long ms) {
        long total=Math.max(0,ms/1000L), min=total/60L, sec=total%60L;
        long hr=min/60L; min%=60L;
        return hr>0 ? String.format(Locale.US,"%02d:%02d:%02d",hr,min,sec) : String.format(Locale.US,"%02d:%02d",min,sec);
    }

    private ObdManager.MonitorState findMonitorState(String needle) {
        if(readiness.monitors==null) return null;
        String n=needle.toLowerCase(Locale.US);
        for(Map.Entry<String, ObdManager.MonitorState> e:readiness.monitors.entrySet()){
            if(e.getKey()!=null && e.getKey().toLowerCase(Locale.US).contains(n)) return e.getValue();
        }
        return null;
    }

    private String readinessSummaryShort() {
        if(!readiness.available) return "N/A";
        int supported=0,complete=0;
        if(readiness.monitors!=null){
            for(ObdManager.MonitorState m:readiness.monitors.values()){
                if(m!=ObdManager.MonitorState.UNSUPPORTED){supported++; if(m==ObdManager.MonitorState.COMPLETE)complete++;}
            }
        }
        return complete+" / "+supported;
    }

    // -----------------------------------------------------------------------------------------
    // CONNECT MODE
    // -----------------------------------------------------------------------------------------

    private void drawConnect(Canvas c) {
        drawObdAdapterCard(c, connectCards[0]);
        drawEcuLinkCard(c, connectCards[1]);
        drawConnectionTypeCard(c, connectCards[2]);
        drawDeviceActionsCard(c, connectCards[3]);
        drawSettingsCard(c, connectCards[4]);
        drawConnectionStatusCard(c, connectCards[5]);
        drawRecentDevicesCard(c, connectCards[6]);
        drawTransportOptionsCard(c, connectCards[7]);
        drawConnectionDiagnosticsCard(c, connectCards[8]);
    }

    private void drawObdAdapterCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.OBD, r.left + 44, r.top + 43, GREEN, 1.0f);
        title(c, "OBD ADAPTER", r.left + 92, r.top + 29);
        String name = obdState == ObdManager.State.DISCONNECTED ? "--" : nonEmpty(adapterName, selectedDeviceName());
        infoRow(c, "Device:", name, r.left + 92, r.top + 56, 185);
        infoRow(c, "Transport:", transportLabel(obdState == ObdManager.State.DISCONNECTED ? selectedTransport : activeTransport), r.left + 92, r.top + 80, 185);
        infoRow(c, "Status:", stateTitle(), r.left + 92, r.top + 104, 185, obdState == ObdManager.State.ECU_CONNECTED ? GREEN : stateColor());
        infoRow(c, "Firmware:", nonEmpty(telemetry.adapterVersion, "N/A"), r.left + 92, r.top + 128, 185);
        infoRow(c, "Address:", nonEmpty(currentDeviceAddress(), "N/A"), r.left + 92, r.top + 152, 185);
    }

    private void drawEcuLinkCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.CHIP, r.left + 45, r.top + 44, GREEN, 1.0f);
        title(c, "ECU LINK", r.left + 94, r.top + 29);
        infoRow(c, "Protocol:", nonEmpty(telemetry.protocol, "N/A"), r.left + 94, r.top + 56, 178);
        infoRow(c, "ECU Status:", obdState == ObdManager.State.ECU_CONNECTED ? "Connected" : "Offline", r.left + 94, r.top + 80, 178, obdState == ObdManager.State.ECU_CONNECTED ? GREEN : MUTED);
        infoRow(c, "Response Time:", "N/A", r.left + 94, r.top + 104, 178);
        infoRow(c, "VIN:", "N/A", r.left + 94, r.top + 128, 178);
        infoRow(c, "ECU Type:", "N/A", r.left + 94, r.top + 152, 178);
    }

    private void drawConnectionTypeCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.RADIO, r.left + 30, r.top + 29, GREEN, .72f);
        title(c, "CONNECTION TYPE", r.left + 67, r.top + 29);
        float gap = 10, left = r.left + 14, top = r.top + 58, h = 96;
        float w = (r.width() - 28 - gap * 2) / 3f;
        transportChoice(c, new RectF(left, top, left + w, top + h), ObdManager.Transport.BLE, "BLE 4.0", Icon.BLUETOOTH);
        transportChoice(c, new RectF(left + w + gap, top, left + w * 2 + gap, top + h), ObdManager.Transport.BLUETOOTH, "BT 3.0", Icon.BLUETOOTH);
        transportChoice(c, new RectF(left + (w + gap) * 2, top, r.right - 14, top + h), ObdManager.Transport.WIFI, "Wi-Fi", Icon.WIFI);
    }

    private void transportChoice(Canvas c, RectF r, ObdManager.Transport t, String name, Icon icon) {
        boolean active = selectedTransport == t;
        fill.setColor(active ? Color.argb(92, 0, 120, 71) : Color.argb(165, 5, 22, 29));
        c.drawRoundRect(r, 7, 7, fill);
        stroke.setColor(active ? GREEN : Color.rgb(72, 100, 113)); stroke.setStrokeWidth(active ? 1.8f : 1f);
        c.drawRoundRect(r, 7, 7, stroke);
        drawIcon(c, icon, r.centerX(), r.top + 32, active ? GREEN : Color.rgb(192, 210, 224), .62f);
        label(c, name, r.centerX(), r.top + 67, 15, WHITE, Paint.Align.CENTER, true, false);
        strokeCircle(c, r.centerX(), r.bottom - 14, 7, active ? GREEN : Color.rgb(141, 169, 187), active ? 2.4f : 1.4f);
        if (active) circle(c, r.centerX(), r.bottom - 14, 3.2f, GREEN);
    }

    private void drawDeviceActionsCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.GEAR, r.left + 30, r.top + 29, GREEN, .68f);
        title(c, "DEVICE ACTIONS", r.left + 67, r.top + 29);
        float l = r.left + 14, t = r.top + 54, gap = 8;
        float w = (r.width() - 28 - gap) / 2f;
        actionButton(c, new RectF(l, t, l + w, t + 43), "Scan", Icon.SEARCH, false);
        actionButton(c, new RectF(l + w + gap, t, r.right - 14, t + 43), "Select", Icon.LIST, false);
        actionButton(c, new RectF(l, t + 50, l + w, t + 93), "Connect", Icon.LINK, true);
        actionButton(c, new RectF(l + w + gap, t + 50, r.right - 14, t + 93), "Disconnect", Icon.LINK, false);
        actionButton(c, new RectF(l, t + 100, l + w, t + 143), "Retry", Icon.CLOCK, false);
        actionButton(c, new RectF(l + w + gap, t + 100, r.right - 14, t + 143), "View Log", Icon.LOG, false);
    }

    private void actionButton(Canvas c, RectF r, String name, Icon icon, boolean primary) {
        fill.setColor(primary ? Color.argb(84, 0, 135, 75) : Color.argb(160, 7, 25, 32));
        c.drawRoundRect(r, 6, 6, fill);
        stroke.setColor(primary ? GREEN : Color.rgb(72, 101, 114)); stroke.setStrokeWidth(primary ? 1.8f : 1f);
        c.drawRoundRect(r, 6, 6, stroke);
        drawIcon(c, icon, r.left + 28, r.centerY(), primary ? GREEN : Color.rgb(210, 225, 234), .52f);
        label(c, name, r.left + 58, r.centerY() + 5, 14, WHITE, Paint.Align.LEFT, false, false);
    }

    private void drawSettingsCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.GEAR, r.left + 30, r.top + 29, GREEN, .68f);
        title(c, "SETTINGS", r.left + 67, r.top + 29);
        float y = r.top + 58;
        settingToggle(c, r, y, "Auto reconnect", autoReconnect); y += 26;
        settingValue(c, r, y, "Wi-Fi Host", wifiHost); y += 26;
        settingValue(c, r, y, "Wi-Fi Port", Integer.toString(wifiPort)); y += 26;
        settingValue(c, r, y, "Connection Timeout", connectionTimeoutMs + " ms"); y += 26;
        settingToggle(c, r, y, "Auto-detect Protocol", autoDetectProtocol); y += 26;
        settingToggle(c, r, y, "Low Power Mode", lowPowerMode);
    }

    private void settingToggle(Canvas c, RectF r, float y, String name, boolean on) {
        label(c, name, r.left + 24, y + 4, 12.5f, MUTED, Paint.Align.LEFT, false, false);
        drawToggle(c, r.right - 45, y, on);
        line(c, r.left + 24, y + 13, r.right - 18, y + 13, Color.argb(80, 102, 132, 145), 1f);
    }

    private void settingValue(Canvas c, RectF r, float y, String name, String value) {
        label(c, name, r.left + 24, y + 4, 12.5f, MUTED, Paint.Align.LEFT, false, false);
        fill.setColor(Color.argb(160, 12, 31, 39));
        c.drawRoundRect(r.right - 177, y - 12, r.right - 22, y + 9, 4, 4, fill);
        stroke.setColor(Color.rgb(56, 85, 98)); stroke.setStrokeWidth(1f);
        c.drawRoundRect(r.right - 177, y - 12, r.right - 22, y + 9, 4, 4, stroke);
        label(c, truncate(value, 18), r.right - 167, y + 4, 12.5f, Color.rgb(195, 216, 229), Paint.Align.LEFT, false, false);
        line(c, r.left + 24, y + 13, r.right - 18, y + 13, Color.argb(80, 102, 132, 145), 1f);
    }

    private void drawConnectionStatusCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.PULSE, r.left + 30, r.top + 29, GREEN, .68f);
        title(c, "CONNECTION STATUS", r.left + 67, r.top + 29);
        int sc = stateColor();
        circle(c, r.right - 126, r.top + 27, 6, sc);
        label(c, stateTitle(), r.right - 18, r.top + 31, 13, sc, Paint.Align.RIGHT, true, false);
        float cx = r.left + 92, cy = r.top + 104;
        strokeCircle(c, cx, cy, 40, Color.argb(130, 26, 208, 126), 2.5f);
        strokeCircle(c, cx, cy, 31, sc, 3f);
        if (obdState == ObdManager.State.ECU_CONNECTED) drawCheck(c, cx, cy, sc, 1.15f);
        else drawIcon(c, Icon.LINK, cx, cy, sc, .7f);
        label(c, obdState == ObdManager.State.ECU_CONNECTED ? "Healthy link" : "Not connected", cx, cy + 58, 12.5f, sc, Paint.Align.CENTER, true, false);

        float lx = r.left + 178, y = r.top + 63;
        infoRow(c, "Session:", sessionTime(), lx, y, 112); y += 23;
        infoRow(c, "Transport:", transportLabel(activeTransport), lx, y, 112); y += 23;
        infoRow(c, "Protocol:", nonEmpty(telemetry.protocol, "N/A"), lx, y, 112); y += 23;
        infoRow(c, "Last Link:", lastConnectedAtMs > 0 ? clockFormat.format(new Date(lastConnectedAtMs)) : "--", lx, y, 112); y += 23;
        infoRow(c, "Detail:", truncate(obdDetail, 18), lx, y, 112);
    }

    private String sessionTime() {
        if (obdState != ObdManager.State.ECU_CONNECTED || connectedAtMs <= 0) return "--";
        long total = Math.max(0, (System.currentTimeMillis() - connectedAtMs) / 1000L);
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
    }

    private void drawRecentDevicesCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.CLOCK, r.left + 30, r.top + 27, GREEN, .65f);
        title(c, "RECENT DEVICES", r.left + 67, r.top + 28);
        List<ObdManager.DeviceInfo> list = visibleDevicePool();
        float y = r.top + 56;
        if (list.isEmpty()) {
            label(c, "No devices discovered yet", r.left + 24, y + 20, 13, MUTED, Paint.Align.LEFT, false, false);
            label(c, "Use Scan above", r.left + 24, y + 43, 11.5f, DIM, Paint.Align.LEFT, false, false);
            return;
        }
        int start = Math.min(deviceScroll, Math.max(0, list.size() - 4));
        for (int row = 0; row < 4 && start + row < list.size(); row++) {
            ObdManager.DeviceInfo d = list.get(start + row);
            boolean selected = d.key().equals(selectedDeviceKey) || (selectedDeviceKey.isEmpty() && d.address.equals(connectedAddress));
            RectF rr = new RectF(r.left + 14, y + row * 27, r.right - 14, y + row * 27 + 23);
            if (selected) {
                fill.setColor(Color.argb(80, 0, 155, 83)); c.drawRoundRect(rr, 4, 4, fill);
                stroke.setColor(GREEN); stroke.setStrokeWidth(1f); c.drawRoundRect(rr, 4, 4, stroke);
            }
            circle(c, rr.left + 13, rr.centerY(), 4.2f, selected ? GREEN : Color.rgb(167, 192, 207));
            label(c, truncate(d.name, 23), rr.left + 28, rr.centerY() + 4, 11.5f, selected ? WHITE : MUTED, Paint.Align.LEFT, false, false);
            label(c, truncate(d.address, 19), rr.right - 74, rr.centerY() + 4, 10.5f, Color.rgb(156, 181, 197), Paint.Align.RIGHT, false, false);
            label(c, d.transport == ObdManager.Transport.BLE ? "BLE" : "BT", rr.right - 10, rr.centerY() + 4, 10, selected ? GREEN : DIM, Paint.Align.RIGHT, true, false);
        }
    }

    private void drawTransportOptionsCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.RADIO, r.left + 30, r.top + 27, GREEN, .6f);
        title(c, "TRANSPORT OPTIONS", r.left + 67, r.top + 28);
        transportOptionRow(c, r, r.top + 58, ObdManager.Transport.AUTO, "AUTO", "Verified adapter only");
        transportOptionRow(c, r, r.top + 85, ObdManager.Transport.BLE, "BLE 4.0", "Low power, direct GATT");
        transportOptionRow(c, r, r.top + 112, ObdManager.Transport.BLUETOOTH, "Bluetooth 3.0", "Classic SPP fallback");
        transportOptionRow(c, r, r.top + 139, ObdManager.Transport.WIFI, "Wi-Fi", "TCP endpoint");
    }

    private void transportOptionRow(Canvas c, RectF r, float y, ObdManager.Transport transport, String name, String detail) {
        boolean selected = selectedTransport == transport;
        strokeCircle(c, r.left + 30, y - 4, 7, selected ? GREEN : Color.rgb(160, 185, 201), 1.6f);
        if (selected) circle(c, r.left + 30, y - 4, 3, GREEN);
        label(c, name, r.left + 54, y, 12.5f, selected ? GREEN : WHITE, Paint.Align.LEFT, selected, false);
        label(c, detail, r.right - 18, y, 10.5f, MUTED, Paint.Align.RIGHT, false, false);
    }

    private void drawConnectionDiagnosticsCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.PULSE, r.left + 30, r.top + 27, GREEN, .65f);
        title(c, "CONNECTION DIAGNOSTICS", r.left + 67, r.top + 28);
        int rank = stateRank(obdState);
        diagStep(c, r, r.top + 58, "Adapter detected", rank >= 2);
        diagStep(c, r, r.top + 84, "Transport initialized", rank >= 4);
        diagStep(c, r, r.top + 110, "Protocol handshake", rank >= 5);
        diagStep(c, r, r.top + 136, "ECU responding", rank >= 7);
        boolean dataActive = obdState == ObdManager.State.ECU_CONNECTED && hasAnyFreshTelemetry();
        diagStep(c, r, r.top + 162, "Data stream active", dataActive);
    }

    private int stateRank(ObdManager.State s) {
        switch (s) {
            case SEARCHING: return 1;
            case ADAPTER_FOUND: return 2;
            case CONNECTING: return 3;
            case INITIALIZING: return 5;
            case ECU_CONNECTING: return 6;
            case ECU_CONNECTED: return 7;
            default: return 0;
        }
    }

    private void diagStep(Canvas c, RectF r, float y, String name, boolean ok) {
        strokeCircle(c, r.left + 31, y - 4, 7, ok ? GREEN : Color.rgb(94, 119, 132), 1.5f);
        if (ok) drawCheck(c, r.left + 31, y - 4, GREEN, .34f);
        label(c, name, r.left + 55, y, 12, ok ? MUTED : DIM, Paint.Align.LEFT, false, false);
        label(c, ok ? "OK" : "--", r.right - 20, y, 11.5f, ok ? GREEN : DIM, Paint.Align.RIGHT, true, false);
    }

    // -----------------------------------------------------------------------------------------
    // SPORT MODE
    // -----------------------------------------------------------------------------------------

    private void drawSport(Canvas c) {
        drawTachometer(c);
        drawSportMetric(c, sportLeftBig, sportSlots[0], true);
        drawSportMetric(c, sportRightBig, sportSlots[1], true);
        drawSportMetric(c, sportBottomLeft, sportSlots[2], false);
        drawSportMetric(c, sportBottomMid, sportSlots[3], false);
        drawSportMetric(c, sportBottomRight, sportSlots[4], false);
    }

    private void drawShiftLights(Canvas c) {
        float left = 335, top = 78, gap = 6, w = 36, h = 12;
        float rpm = freshRaw(telemetry.rpm);
        int lit = Float.isNaN(rpm) ? 0 : Math.max(0, Math.min(16, (int) (rpm / 500f)));
        for (int i = 0; i < 16; i++) {
            float l = left + i * (w + gap);
            int color = i < 10 ? GREEN : i < 13 ? YELLOW : RED;
            fill.setColor(i < lit ? color : Color.rgb(16, 26, 31));
            c.drawRoundRect(l, top, l + w, top + h, 3, 3, fill);
            stroke.setColor(i < lit ? Color.argb(210, Color.red(color), Color.green(color), Color.blue(color)) : Color.rgb(67, 82, 89));
            stroke.setStrokeWidth(1f); c.drawRoundRect(l, top, l + w, top + h, 3, 3, stroke);
        }
    }

    private void drawTachometer(Canvas c) {
        final float cx = 640, cy = 255, radius = 205;

        // Dynamic Sport tachometer renderer v0.9.2
        fill.setColor(Color.argb(244, 2, 10, 13));
        c.drawCircle(cx, cy, radius, fill);
        strokeCircle(c, cx, cy, radius, Color.rgb(170, 184, 190), 1.8f);

        float rpm = freshRaw(telemetry.rpm);
        float value = Float.isNaN(rpm) ? 0f : Math.max(0f, Math.min(8000f, rpm));

        float start = 140f;
        float sweep = 260f;
        float ring = radius - 18f;

        // inactive ring
        arc(c, cx, cy, ring, start, sweep, Color.rgb(22, 35, 41), 18f);

        float progress = value / 8000f;
        if (progress > 0) {
            float greenPart = Math.min(value, 4000f) / 8000f * sweep;
            float yellowPart = Math.max(0, Math.min(value - 4000f, 1500f)) / 8000f * sweep;
            float redPart = Math.max(0, value - 5500f) / 8000f * sweep;

            if (greenPart > 0) arc(c, cx, cy, ring, start, greenPart, GREEN, 18f);
            if (yellowPart > 0) arc(c, cx, cy, ring, start + 4000f / 8000f * sweep, yellowPart, YELLOW, 18f);
            if (redPart > 0) arc(c, cx, cy, ring, start + 5500f / 8000f * sweep, redPart, RED, 18f);
        }

        // OEM style scale
        for (int i = 0; i <= 40; i++) {
            float a = start + sweep * i / 40f;
            float len = i % 5 == 0 ? 18 : 9;
            int color = i >= 35 ? RED : i >= 27 ? YELLOW : WHITE;
            radialLine(c, cx, cy, radius - 30, radius - 30 - len, a, color, i % 5 == 0 ? 2f : 1f);
        }

        for (int i = 0; i <= 8; i++) {
            float a = start + sweep * i / 8f;
            polarLabel(c, Integer.toString(i), cx, cy, radius - 62, a, 20, i >= 6 ? RED : WHITE);
        }

        label(c, "i-VTEC", cx, cy - 36, 16, MUTED, Paint.Align.CENTER, true, false);
        label(c, "x1000 RPM", cx, cy - 16, 11, DIM, Paint.Align.CENTER, false, false);

        glowLabel(c, valueText(telemetry.rpm, 0x0C, 0), cx, cy + 49, 64, WHITE, Paint.Align.CENTER, true, 2f);
        label(c, "RPM", cx, cy + 80, 21, MUTED, Paint.Align.CENTER, true, false);

        path.reset();
        path.moveTo(cx - 154, cy + 136);
        path.lineTo(cx - 108, cy + 98);
        path.lineTo(cx + 108, cy + 98);
        path.lineTo(cx + 154, cy + 136);
        path.lineTo(cx + 123, cy + 181);
        path.lineTo(cx - 123, cy + 181);
        path.close();
        fill.setColor(Color.argb(245, 3, 13, 17));
        c.drawPath(path, fill);
        stroke.setColor(Color.rgb(82, 102, 111));
        stroke.setStrokeWidth(1.2f);
        c.drawPath(path, stroke);
        line(c, cx - 103, cy + 105, cx + 103, cy + 105, RED, 2f);
        glowLabel(c, valueText(telemetry.speed, 0x0D, 0), cx, cy + 158, 50, WHITE, Paint.Align.CENTER, true, 2.5f);
        label(c, "km/h", cx + 78, cy + 158, 18, WHITE, Paint.Align.LEFT, true, false);
    }

    private void drawSportMetric(Canvas c, RectF r, SensorKey key, boolean large) {
        SensorReading reading = sensorReading(key);
        panel(c, r, GREEN, false);
        drawIcon(c, key.icon, r.left + (large ? 48 : 42), r.top + (large ? 43 : 39), GREEN, large ? .78f : .65f);
        label(c, key.title, r.left + (large ? 110 : 92), r.top + 35, large ? 17 : 15, MUTED, Paint.Align.LEFT, false, false);
        label(c, "›", r.right - 25, r.top + 35, 28, Color.rgb(205, 222, 231), Paint.Align.CENTER, false, false);
        if (large) {
            glowLabel(c, reading.display(), r.left + 112, r.top + 105, 55, WHITE, Paint.Align.LEFT, true, 2.2f);
            label(c, reading.state == SensorFreshness.State.UNSUPPORTED ? "" : key.unit, r.left + 245, r.top + 99, 22, WHITE, Paint.Align.LEFT, true, false);
            progress(c, r.left + 24, r.bottom - 38, r.right - 24, r.bottom - 30, reading, GREEN);
            scaleLabels(c, r, key, r.bottom - 12);
        } else {
            glowLabel(c, reading.display(), r.left + 92, r.top + 71, 32, WHITE, Paint.Align.LEFT, true, 1.8f);
            label(c, reading.state == SensorFreshness.State.UNSUPPORTED ? "" : key.unit, r.left + 174, r.top + 69, 17, WHITE, Paint.Align.LEFT, true, false);
            progress(c, r.left + 24, r.bottom - 38, r.right - 24, r.bottom - 30, reading, GREEN);
            scaleLabels(c, r, key, r.bottom - 12);
        }
    }

    // -----------------------------------------------------------------------------------------
    // DIAGNOSTICS MODE
    // -----------------------------------------------------------------------------------------

    private void drawDiagnostics(Canvas c) {
        float gap = 8, left = 28, right = 1254;
        float w = (right - left - gap * 5) / 6f;
        SensorKey[] top = {SensorKey.MODULE_VOLTAGE, SensorKey.COOLANT, SensorKey.INTAKE, SensorKey.THROTTLE, SensorKey.LOAD, SensorKey.FUEL};
        for (int i = 0; i < 6; i++) {
            RectF r = new RectF(left + i * (w + gap), 222, left + i * (w + gap) + w, 320);
            drawDiagnosticMetric(c, r, top[i]);
        }

        RectF dtcR = new RectF(28, 330, 396, 520);
        RectF readyR = new RectF(406, 330, 850, 520);
        RectF liveR = new RectF(860, 330, 1254, 520);
        drawDtcCard(c, dtcR);
        drawReadinessCard(c, readyR);
        drawLiveSensorCard(c, liveR);

        drawFreezeFrameCard(c, new RectF(28, 530, 396, 626));
        drawEmissionsCard(c, new RectF(406, 530, 850, 626));
        drawEcuInfoCard(c, new RectF(860, 530, 1254, 626));
    }

    private void drawDiagnosticMetric(Canvas c, RectF r, SensorKey key) {
        SensorReading reading = sensorReading(key);
        panel(c, r, GREEN, false);
        drawIcon(c, key.icon, r.left + 30, r.top + 32, GREEN, .58f);
        label(c, key.title, r.left + 63, r.top + 26, 10.5f, MUTED, Paint.Align.LEFT, false, false);
        glowLabel(c, reading.display(), r.left + 63, r.top + 61, 28, WHITE, Paint.Align.LEFT, true, 1.4f);
        label(c, reading.state == SensorFreshness.State.UNSUPPORTED ? "" : key.unit, r.left + 128, r.top + 60, 14, WHITE, Paint.Align.LEFT, false, false);
        progress(c, r.left + 18, r.bottom - 28, r.right - 18, r.bottom - 21, reading, GREEN);
        scaleLabels(c, r, key, r.bottom - 7);
    }

    private void drawDtcCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.WARNING, r.left + 35, r.top + 34, WHITE, .7f);
        title(c, "FAULT CODES (DTC)", r.left + 76, r.top + 31);
        label(c, "›", r.right - 24, r.top + 31, 26, Color.rgb(206, 222, 230), Paint.Align.CENTER, false, false);
        float cx = r.left + 62, cy = r.top + 100;
        switch (dtc.status) {
            case NO_CODES:
                strokeCircle(c, cx, cy, 29, GREEN, 2.5f); drawCheck(c, cx, cy, GREEN, .8f);
                label(c, "NO FAULT CODES", r.left + 111, r.top + 94, 16, GREEN, Paint.Align.LEFT, true, false);
                label(c, "Mode 03 read completed successfully.", r.left + 111, r.top + 120, 11, MUTED, Paint.Align.LEFT, false, false);
                break;
            case HAS_CODES:
                strokeCircle(c, cx, cy, 29, RED, 2.5f); drawIcon(c, Icon.WARNING, cx, cy, RED, .55f);
                label(c, dtc.codes.size() + " STORED DTC", r.left + 111, r.top + 88, 16, RED, Paint.Align.LEFT, true, false);
                for (int i = 0; i < Math.min(3, dtc.codes.size()); i++) label(c, dtc.codes.get(i), r.left + 111, r.top + 112 + i * 19, 13, WHITE, Paint.Align.LEFT, true, false);
                break;
            case ERROR:
                strokeCircle(c, cx, cy, 29, RED, 2.5f); drawIcon(c, Icon.WARNING, cx, cy, RED, .55f);
                label(c, "READ ERROR", r.left + 111, r.top + 94, 16, RED, Paint.Align.LEFT, true, false);
                label(c, truncate(dtc.detail, 34), r.left + 111, r.top + 120, 11, MUTED, Paint.Align.LEFT, false, false);
                break;
            default:
                strokeCircle(c, cx, cy, 29, DIM, 2f); drawIcon(c, Icon.INFO, cx, cy, MUTED, .55f);
                label(c, "NOT READ", r.left + 111, r.top + 94, 16, MUTED, Paint.Align.LEFT, true, false);
                label(c, "Connect ECU to read stored DTC.", r.left + 111, r.top + 120, 11, DIM, Paint.Align.LEFT, false, false);
        }
    }

    private void drawReadinessCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.CHECK, r.left + 34, r.top + 33, GREEN, .66f);
        title(c, "READINESS MONITORS", r.left + 74, r.top + 31);
        String summary = readinessSummary();
        label(c, summary, r.right - 22, r.top + 31, 13, readiness.available ? GREEN : MUTED, Paint.Align.RIGHT, true, false);
        if (!readiness.available) {
            label(c, "N/A — waiting for ECU", r.left + 34, r.top + 92, 16, MUTED, Paint.Align.LEFT, true, false);
            return;
        }
        String[] wanted = {"Misfire", "Fuel System", "Components", "Catalyst", "Heated Catalyst", "Evaporative System", "Secondary Air", "O2 Sensor"};
        float x1 = r.left + 34, x2 = r.left + 240, y = r.top + 68;
        int col = 0, row = 0;
        for (String n : wanted) {
            ObdManager.MonitorState state = readiness.monitors.get(n);
            float x = col == 0 ? x1 : x2;
            drawMonitorState(c, x, y + row * 27, n, state);
            col++;
            if (col == 2) { col = 0; row++; }
        }
    }

    private void drawMonitorState(Canvas c, float x, float y, String name, ObdManager.MonitorState state) {
        int color = state == ObdManager.MonitorState.COMPLETE ? GREEN : state == ObdManager.MonitorState.INCOMPLETE ? ORANGE : DIM;
        strokeCircle(c, x, y - 4, 7, color, 1.5f);
        if (state == ObdManager.MonitorState.COMPLETE) drawCheck(c, x, y - 4, color, .33f);
        else if (state == ObdManager.MonitorState.INCOMPLETE) label(c, "!", x, y, 10, color, Paint.Align.CENTER, true, false);
        else label(c, "–", x, y, 11, color, Paint.Align.CENTER, true, false);
        label(c, name, x + 18, y, 10.5f, state == ObdManager.MonitorState.UNSUPPORTED ? DIM : MUTED, Paint.Align.LEFT, false, false);
    }

    private String readinessSummary() {
        if (!readiness.available) return "N/A";
        int supported = 0, complete = 0;
        for (ObdManager.MonitorState s : readiness.monitors.values()) {
            if (s != ObdManager.MonitorState.UNSUPPORTED) { supported++; if (s == ObdManager.MonitorState.COMPLETE) complete++; }
        }
        return supported == 0 ? "N/A" : complete + " / " + supported + " COMPLETE";
    }

    private void drawLiveSensorCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.PULSE, r.left + 31, r.top + 31, GREEN, .63f);
        title(c, "LIVE SENSOR DATA", r.left + 72, r.top + 31);
        label(c, "›", r.right - 22, r.top + 31, 26, Color.rgb(206, 222, 230), Paint.Align.CENTER, false, false);

        List<SensorKey> keys = liveSensorKeys();
        float y0 = r.top + 58 - diagLiveScroll;
        c.save();
        c.clipRect(r.left + 12, r.top + 44, r.right - 12, r.bottom - 10);
        for (int i = 0; i < keys.size(); i++) {
            float y = y0 + i * 20;
            if (y < r.top + 46 || y > r.bottom - 4) continue;
            SensorReading sr = sensorReading(keys.get(i));
            label(c, keys.get(i).title, r.left + 22, y, 10.5f, MUTED, Paint.Align.LEFT, false, false);
            label(c, sr.display(), r.right - 58, y, 10.5f, WHITE, Paint.Align.RIGHT, sr.state == SensorFreshness.State.VALID, false);
            label(c, sr.state == SensorFreshness.State.UNSUPPORTED ? "" : keys.get(i).unit, r.right - 20, y, 10, MUTED, Paint.Align.RIGHT, false, false);
            line(c, r.left + 22, y + 6, r.right - 20, y + 6, Color.argb(48, 105, 131, 143), 1f);
        }
        c.restore();
    }

    private List<SensorKey> liveSensorKeys() {
        List<SensorKey> out = new ArrayList<>();
        SensorKey[] all = SensorKey.values();
        for (SensorKey key : all) {
            SensorReading sr = sensorReading(key);
            if (sr.state != SensorFreshness.State.UNSUPPORTED || !telemetry.capabilitiesKnown || key == SensorKey.ADAPTER_VOLTAGE) out.add(key);
        }
        return out;
    }

    private void drawFreezeFrameCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.SNOW, r.left + 30, r.top + 29, GREEN, .62f);
        title(c, "FREEZE FRAME STATUS", r.left + 70, r.top + 29);
        label(c, "NO FREEZE FRAME DATA", r.left + 70, r.top + 62, 13, MUTED, Paint.Align.LEFT, true, false);
        label(c, "Mode 02 freeze frame is not read until available.", r.left + 70, r.top + 86, 10.5f, DIM, Paint.Align.LEFT, false, false);
    }

    private void drawEmissionsCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.LEAF, r.left + 30, r.top + 29, GREEN, .62f);
        title(c, "EMISSIONS / SYSTEM STATUS", r.left + 70, r.top + 29);
        String mil = readiness.available ? (readiness.milOn ? "ON" : "OFF") : "N/A";
        String count = readiness.available ? Integer.toString(readiness.dtcCount) : "N/A";
        String monitor = readinessSummary();
        String ready = emissionsReady();
        smallInfo(c, "MIL Status", mil, r.left + 70, r.top + 56, r.right - 22, readiness.milOn ? RED : GREEN);
        smallInfo(c, "DTC Count", count, r.left + 70, r.top + 76, r.right - 22, WHITE);
        smallInfo(c, "Monitors", monitor, r.left + 70, r.top + 96, r.right - 22, readiness.available ? GREEN : MUTED);
        smallInfo(c, "Readiness", ready, r.left + 70, r.top + 116, r.right - 22, "READY".equals(ready) ? GREEN : MUTED);
    }

    private String emissionsReady() {
        if (!readiness.available) return "N/A";
        boolean any = false;
        for (ObdManager.MonitorState s : readiness.monitors.values()) {
            if (s == ObdManager.MonitorState.UNSUPPORTED) continue;
            any = true;
            if (s != ObdManager.MonitorState.COMPLETE) return "NOT READY";
        }
        return any ? "READY" : "N/A";
    }

    private void drawEcuInfoCard(Canvas c, RectF r) {
        panel(c, r, GREEN, false);
        drawIcon(c, Icon.CHIP, r.left + 30, r.top + 29, GREEN, .62f);
        title(c, "ECU INFORMATION", r.left + 70, r.top + 29);
        smallInfo(c, "ECU Identity", "N/A", r.left + 70, r.top + 54, r.right - 22, MUTED);
        smallInfo(c, "Calibration ID", "N/A", r.left + 70, r.top + 73, r.right - 22, MUTED);
        smallInfo(c, "Protocol", nonEmpty(telemetry.protocol, "N/A"), r.left + 70, r.top + 92, r.right - 22, WHITE);
        smallInfo(c, "Module Voltage", sensorReading(SensorKey.MODULE_VOLTAGE).display() + " " + SensorKey.MODULE_VOLTAGE.unit, r.left + 70, r.top + 111, r.right - 22, WHITE);
    }

    // -----------------------------------------------------------------------------------------
    // SPORT SENSOR PICKER
    // -----------------------------------------------------------------------------------------

    private void drawSensorPicker(Canvas c) {
        fill.setColor(Color.argb(190, 0, 0, 0)); c.drawRect(0, 0, DW, DH, fill);
        RectF r = new RectF(190, 105, 1090, 610);
        fill.setColor(Color.rgb(4, 18, 24)); c.drawRoundRect(r, 14, 14, fill);
        stroke.setColor(GREEN); stroke.setStrokeWidth(1.5f); c.drawRoundRect(r, 14, 14, stroke);
        label(c, "SELECT SENSOR", r.left + 34, r.top + 45, 24, WHITE, Paint.Align.LEFT, true, true);
        label(c, "Sport widget " + (sensorPickerSlot + 1), r.left + 34, r.top + 70, 12, MUTED, Paint.Align.LEFT, false, false);
        label(c, "×", r.right - 34, r.top + 47, 34, WHITE, Paint.Align.CENTER, false, false);

        SensorKey[] keys = SensorKey.values();
        float l = r.left + 30, top = r.top + 96, gapX = 12, gapY = 12;
        float w = (r.width() - 60 - gapX * 2) / 3f;
        float h = 58;
        for (int i = 0; i < keys.length; i++) {
            int col = i % 3, row = i / 3;
            float x = l + col * (w + gapX), y = top + row * (h + gapY);
            RectF rr = new RectF(x, y, x + w, y + h);
            boolean selected = sportSlots[sensorPickerSlot] == keys[i];
            fill.setColor(selected ? Color.argb(85, 0, 145, 84) : Color.rgb(7, 28, 35)); c.drawRoundRect(rr, 7, 7, fill);
            stroke.setColor(selected ? GREEN : Color.rgb(64, 91, 103)); stroke.setStrokeWidth(selected ? 1.6f : 1f); c.drawRoundRect(rr, 7, 7, stroke);
            drawIcon(c, keys[i].icon, rr.left + 28, rr.centerY(), selected ? GREEN : Color.rgb(190, 210, 220), .48f);
            label(c, keys[i].title, rr.left + 52, rr.centerY() - 3, 11.5f, selected ? WHITE : MUTED, Paint.Align.LEFT, true, false);
            label(c, sensorReading(keys[i]).state == SensorFreshness.State.UNSUPPORTED ? "N/A" : keys[i].unit, rr.left + 52, rr.centerY() + 17, 10, selected ? GREEN : DIM, Paint.Align.LEFT, false, false);
        }
    }

    // -----------------------------------------------------------------------------------------
    // BOTTOM NAVIGATION
    // -----------------------------------------------------------------------------------------

    private void drawBottomNavigation(Canvas c) {
        final float top = NAV_TOP + 2f, bottom = NAV_BOTTOM - 2f;
        final float third = DW / 3f;
        Mode[] modes = {Mode.CONNECT, Mode.SPORT, Mode.DIAGNOSTICS};
        String[] names = {"CONNECT", "SPORT", "DIAGNOSTICS"};
        String[] subs = {"OBD SETUP & LINK", "HIGHER STANDARDS", "KNOW YOUR CAR"};
        Icon[] icons = {Icon.LINK, Icon.FLAG, Icon.GEAR};
        for (int i = 0; i < 3; i++) {
            float l = i * third;
            float r = (i + 1) * third;
            boolean active = mode == modes[i];
            int accent = modes[i] == Mode.SPORT ? RED : modes[i] == Mode.DIAGNOSTICS ? CYAN : GREEN;
            float slant = 18f;
            path.reset();
            if (i == 0) {
                path.moveTo(l + 2, top); path.lineTo(r - 3, top); path.lineTo(r - slant, bottom); path.lineTo(l + 2, bottom);
            } else if (i == 1) {
                path.moveTo(l + slant, top); path.lineTo(r - slant, top); path.lineTo(r - 2, bottom); path.lineTo(l + 2, bottom);
            } else {
                path.moveTo(l + slant, top); path.lineTo(r - 2, top); path.lineTo(r - 2, bottom); path.lineTo(l + 2, bottom);
            }
            path.close();
            fill.setColor(active ? Color.argb(78, Color.red(accent), Color.green(accent), Color.blue(accent)) : Color.argb(248, 3, 14, 18));
            c.drawPath(path, fill);
            stroke.setColor(active ? accent : Color.rgb(54, 75, 84));
            stroke.setStrokeWidth(active ? 1.7f : 1f); c.drawPath(path, stroke);

            float iconX = l + 132;
            drawIcon(c, icons[i], iconX, 671, active ? accent : Color.rgb(202, 216, 229), .82f);
            label(c, names[i], l + 184, 666, 19, active ? WHITE : Color.rgb(219, 229, 236), Paint.Align.LEFT, true, false);
            label(c, subs[i], l + 184, 690, 10.5f, active ? accent : MUTED, Paint.Align.LEFT, false, true);
        }
    }

    // -----------------------------------------------------------------------------------------
    // SENSOR MODEL HELPERS
    // -----------------------------------------------------------------------------------------

    private SensorReading sensorReading(SensorKey key) {
        float v = rawSensor(key);
        long at = sensorTimestamp(key);
        boolean independent = key == SensorKey.ADAPTER_VOLTAGE;
        boolean supported = independent || key.pid < 0 || telemetry.supports(key.pid);
        SensorFreshness.State state = SensorFreshness.classify(
                obdState == ObdManager.State.ECU_CONNECTED,
                telemetry.capabilitiesKnown,
                supported,
                independent,
                v,
                at,
                System.currentTimeMillis(),
                sensorTtlMs(key));
        return new SensorReading(key, state == SensorFreshness.State.VALID ? v : Float.NaN, state);
    }

    private long sensorTimestamp(SensorKey key) {
        switch (key) {
            case COOLANT: return telemetry.coolantAt;
            case INTAKE: return telemetry.intakeAt;
            case MODULE_VOLTAGE: return telemetry.voltageAt;
            case ADAPTER_VOLTAGE: return telemetry.adapterVoltageAt;
            case THROTTLE: return telemetry.throttleAt;
            case LOAD: return telemetry.loadAt;
            case FUEL: return telemetry.fuelAt;
            case MAF: return telemetry.mafAt;
            case MAP: return telemetry.mapAt;
            case STFT: return telemetry.shortFuelTrimAt;
            case LTFT: return telemetry.longFuelTrimAt;
            case TIMING: return telemetry.timingAt;
            case FUEL_RATE: return telemetry.fuelRateAt;
            default: return 0L;
        }
    }

    private long sensorTtlMs(SensorKey key) {
        switch (key) {
            case THROTTLE:
            case LOAD: return 1800L;
            case MAF:
            case MAP:
            case TIMING: return 3000L;
            case STFT: return 4000L;
            case LTFT: return 6000L;
            case FUEL: return 15000L;
            default: return 7000L;
        }
    }

    private float rawSensor(SensorKey key) {
        switch (key) {
            case COOLANT: return telemetry.coolant;
            case INTAKE: return telemetry.intake;
            case MODULE_VOLTAGE: return telemetry.voltage;
            case ADAPTER_VOLTAGE: return telemetry.adapterVoltage;
            case THROTTLE: return telemetry.throttle;
            case LOAD: return telemetry.load;
            case FUEL: return telemetry.fuel;
            case MAF: return telemetry.maf;
            case MAP: return telemetry.map;
            case STFT: return telemetry.shortFuelTrim;
            case LTFT: return telemetry.longFuelTrim;
            case TIMING: return telemetry.timing;
            case FUEL_RATE: return telemetry.fuelRate;
            default: return Float.NaN;
        }
    }

    private float freshRaw(float f) { return Float.isNaN(f) ? Float.NaN : f; }

    private String valueText(float value, int pid, int decimals) {
        if (obdState != ObdManager.State.ECU_CONNECTED) return "--";
        if (telemetry.capabilitiesKnown && !telemetry.supports(pid)) return "N/A";
        if (Float.isNaN(value)) return "--";
        return decimals == 0 ? Integer.toString(Math.round(value)) : String.format(Locale.US, "%.1f", value);
    }

    private boolean hasAnyFreshTelemetry() {
        return !Float.isNaN(telemetry.rpm) || !Float.isNaN(telemetry.speed) || !Float.isNaN(telemetry.coolant) || !Float.isNaN(telemetry.throttle);
    }

    // -----------------------------------------------------------------------------------------
    // TOUCH
    // -----------------------------------------------------------------------------------------

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = (e.getX() - viewOffsetX) / viewScale;
        float y = (e.getY() - viewOffsetY) / viewScale;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownY = lastTouchY = y;
            diagDragging = false;
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (mode == Mode.DIAGNOSTICS && x >= 860 && x <= 1254 && y >= 330 && y <= 520) {
                float dy = y - lastTouchY;
                if (Math.abs(y - touchDownY) > 4) diagDragging = true;
                diagLiveScroll = clamp(diagLiveScroll - dy, 0, Math.max(0, liveSensorKeys().size() * 20f - 130f));
                lastTouchY = y;
                invalidate();
            }
            return true;
        }
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        if (x < 0 || y < 0 || x > DW || y > DH) return true;

        if (sensorPickerSlot >= 0) {
            handleSensorPickerTouch(x, y);
            return true;
        }

        // One safe touch zone for all modes; it does not overlap CONNECT cards.
        final float navTop = 654f;
        if (y >= navTop && y <= 716f) {
            int idx = Math.max(0, Math.min(2, (int) (x / (DW / 3f))));
            mode = idx == 0 ? Mode.CONNECT : idx == 1 ? Mode.SPORT : Mode.DIAGNOSTICS;
            invalidate(); return true;
        }

        if (mode == Mode.CONNECT) handleConnectTouch(x, y);
        else if (mode == Mode.SPORT) handleSportTouch(x, y);
        return true;
    }

    private void handleSportTouch(float x, float y) {
        RectF[] slots = {sportLeftBig, sportRightBig, sportBottomLeft, sportBottomMid, sportBottomRight};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].contains(x, y)) { sensorPickerSlot = i; invalidate(); return; }
        }
    }

    private void handleSensorPickerTouch(float x, float y) {
        RectF r = new RectF(190, 105, 1090, 610);
        if (!r.contains(x, y) || (x > r.right - 75 && y < r.top + 80)) { sensorPickerSlot = -1; invalidate(); return; }
        SensorKey[] keys = SensorKey.values();
        float l = r.left + 30, top = r.top + 96, gapX = 12, gapY = 12;
        float w = (r.width() - 60 - gapX * 2) / 3f, h = 68;
        for (int i = 0; i < keys.length; i++) {
            int col = i % 3, row = i / 3;
            RectF rr = new RectF(l + col * (w + gapX), top + row * (h + gapY), l + col * (w + gapX) + w, top + row * (h + gapY) + h);
            if (rr.contains(x, y)) {
                sportSlots[sensorPickerSlot] = keys[i];
                prefs.edit().putString("sport_slot_" + sensorPickerSlot, keys[i].name()).apply();
                sensorPickerSlot = -1;
                invalidate(); return;
            }
        }
    }

    private void handleConnectTouch(float x, float y) {
        // Top transport selector: exact approved-reference boxes.
        RectF ble = new RectF(858, 214, 979, 300);
        RectF bt = new RectF(990, 214, 1111, 300);
        RectF wifi = new RectF(1122, 214, 1243, 300);
        if (ble.contains(x, y)) { setSelectedTransport(ObdManager.Transport.BLE); return; }
        if (bt.contains(x, y)) { setSelectedTransport(ObdManager.Transport.BLUETOOTH); return; }
        if (wifi.contains(x, y)) { setSelectedTransport(ObdManager.Transport.WIFI); return; }

        // Device actions — 2 columns x 3 rows from the approved reference.
        RectF scan = new RectF(36, 351, 224, 388);
        RectF select = new RectF(233, 351, 423, 388);
        RectF connect = new RectF(36, 394, 224, 432);
        RectF disconnect = new RectF(233, 394, 423, 432);
        RectF retry = new RectF(36, 438, 224, 476);
        RectF log = new RectF(233, 438, 423, 476);
        if (scan.contains(x,y)) { scanSelectedTransport(); return; }
        if (select.contains(x,y)) { selectNextDevice(); return; }
        if (connect.contains(x,y)) { connectSelected(); return; }
        if (disconnect.contains(x,y)) { obd.disconnect(); return; }
        if (retry.contains(x,y)) { connectSelected(); return; }
        if (log.contains(x,y)) { obd.setDebugLoggingEnabled(true); activity.showObdLog(obd.getDebugLogText(), this::exportLog); return; }

        // SETTINGS rows.
        if (x >= 454 && x <= 800) {
            if (y >= 349 && y <= 369) { autoReconnect = !autoReconnect; prefs.edit().putBoolean("auto_reconnect", autoReconnect).apply(); invalidate(); return; }
            if (y >= 371 && y <= 392) { activity.promptWifiEndpoint(wifiHost, wifiPort, (host, port) -> { wifiHost = host; wifiPort = port; prefs.edit().putString("wifi_host", host).putInt("wifi_port", port).apply(); invalidate(); }); return; }
            if (y >= 393 && y <= 413) { activity.promptWifiEndpoint(wifiHost, wifiPort, (host, port) -> { wifiHost = host; wifiPort = port; prefs.edit().putString("wifi_host", host).putInt("wifi_port", port).apply(); invalidate(); }); return; }
            if (y >= 414 && y <= 435) { activity.promptConnectionTimeout(connectionTimeoutMs, ms -> { connectionTimeoutMs = ms; prefs.edit().putInt("connection_timeout_ms", ms).apply(); obd.setConnectionTimeoutMs(ms); invalidate(); }); return; }
            if (y >= 436 && y <= 454) { autoDetectProtocol = !autoDetectProtocol; prefs.edit().putBoolean("auto_detect_protocol", autoDetectProtocol).apply(); invalidate(); return; }
            if (y >= 455 && y <= 476) { lowPowerMode = !lowPowerMode; prefs.edit().putBoolean("low_power_mode", lowPowerMode).apply(); invalidate(); return; }
        }

        // Recent devices.
        List<ObdManager.DeviceInfo> list = visibleDevicePool();
        int first = Math.min(deviceScroll, Math.max(0, list.size() - 4));
        for (int row=0; row<4 && first+row<list.size(); row++) {
            RectF rr = new RectF(34, 535 + row*26, 422, 558 + row*26);
            if (rr.contains(x,y)) {
                ObdManager.DeviceInfo info=list.get(first+row);
                selectedDeviceKey=info.key();
                selectedTransport=info.transport;
                autoTransport=false;
                prefs.edit().putString("ui_selected_transport", selectedTransport.name()).putBoolean("ui_auto_transport", false).apply();
                invalidate();
                return;
            }
        }

        // Transport options. Wi-Fi TCP/UDP both use the same current TCP transport implementation.
        if (x >= 455 && x <= 827 && y >= 536 && y <= 646) {
            int row = Math.max(0, Math.min(4, (int)((y - 538) / 21.5f)));
            if (row == 0) {
                autoTransport = true;
                prefs.edit().putBoolean("ui_auto_transport", true).apply();
                invalidate();
            } else {
                autoTransport = false;
                prefs.edit().putBoolean("ui_auto_transport", false).apply();
                if (row == 1) setSelectedTransport(ObdManager.Transport.BLE);
                else if (row == 2) setSelectedTransport(ObdManager.Transport.BLUETOOTH);
                else setSelectedTransport(ObdManager.Transport.WIFI);
            }
        }
    }

    private void setSelectedTransport(ObdManager.Transport t) {
        selectedTransport = t;
        prefs.edit().putString("ui_selected_transport", t.name()).apply();
        selectedDeviceKey = "";
        if ((t == ObdManager.Transport.BLE || t == ObdManager.Transport.BLUETOOTH) && !hasBluetoothPermission()) activity.requestObdBluetoothPermissions();
        if (t == ObdManager.Transport.BLUETOOTH) obd.refreshDevices();
        invalidate();
    }

    private void scanSelectedTransport() {
        if (selectedTransport == ObdManager.Transport.BLE) {
            if (!hasBluetoothPermission()) activity.requestObdBluetoothPermissions(); else obd.scanBleDevices();
        } else if (selectedTransport == ObdManager.Transport.BLUETOOTH) {
            if (!hasBluetoothPermission()) activity.requestObdBluetoothPermissions(); else obd.scanClassicDevices();
        } else if (selectedTransport == ObdManager.Transport.WIFI) {
            Toast.makeText(activity, "Wi-Fi uses the configured host/port; no LAN discovery is performed.", Toast.LENGTH_SHORT).show();
        } else {
            obd.connectAuto();
        }
    }

    private void selectNextDevice() {
        List<ObdManager.DeviceInfo> filtered = filteredDevices(selectedTransport);
        if (filtered.isEmpty()) { Toast.makeText(activity, "Scan for an adapter first", Toast.LENGTH_SHORT).show(); return; }
        int idx = -1;
        for (int i = 0; i < filtered.size(); i++) if (filtered.get(i).key().equals(selectedDeviceKey)) idx = i;
        idx = (idx + 1) % filtered.size();
        selectedDeviceKey = filtered.get(idx).key();
        deviceScroll = Math.max(0, idx - 2);
        invalidate();
    }

    private void connectSelected() {
        if (autoTransport) { obd.connectAuto(); return; }
        if (selectedTransport == ObdManager.Transport.WIFI) { obd.connectWifi(wifiHost, wifiPort); return; }
        if (!hasBluetoothPermission()) { activity.requestObdBluetoothPermissions(); return; }
        for (ObdManager.DeviceInfo d : devices) {
            if (d.key().equals(selectedDeviceKey)) { connectedAddress = d.address; obd.connect(d); return; }
        }
        List<ObdManager.DeviceInfo> filtered = filteredDevices(selectedTransport);
        if (filtered.size() == 1) { selectedDeviceKey = filtered.get(0).key(); connectedAddress = filtered.get(0).address; obd.connect(filtered.get(0)); return; }
        Toast.makeText(activity, "Select an adapter from Recent Devices", Toast.LENGTH_SHORT).show();
    }

    private void exportLog() {
        try {
            File f = obd.exportDebugLog();
            Toast.makeText(activity, "OBD log exported: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Could not export OBD log", Toast.LENGTH_SHORT).show();
        }
    }

    private List<ObdManager.DeviceInfo> filteredDevices(ObdManager.Transport t) {
        List<ObdManager.DeviceInfo> out = new ArrayList<>();
        if (t == ObdManager.Transport.AUTO) return visibleDevicePool();
        for (ObdManager.DeviceInfo d : devices) if (d.transport == t) out.add(d);
        return out;
    }

    private List<ObdManager.DeviceInfo> visibleDevicePool() {
        List<ObdManager.DeviceInfo> out = new ArrayList<>();
        Map<String, ObdManager.DeviceInfo> dedupe = new LinkedHashMap<>();
        for (ObdManager.DeviceInfo d : devices) dedupe.put(d.key(), d);
        out.addAll(dedupe.values());
        return out;
    }

    private String selectedDeviceName() {
        for (ObdManager.DeviceInfo d : devices) if (d.key().equals(selectedDeviceKey)) return d.name;
        return adapterName;
    }

    private String currentDeviceAddress() {
        for (ObdManager.DeviceInfo d : devices) if (d.key().equals(selectedDeviceKey)) return d.address;
        return connectedAddress;
    }

    // -----------------------------------------------------------------------------------------
    // OBD LISTENER
    // -----------------------------------------------------------------------------------------

    @Override public void onState(ObdManager.State state, String detail, ObdManager.Transport transport, String adapterName) {
        ObdManager.State previous = obdState;
        obdState = state;
        obdDetail = detail == null ? "" : detail;
        activeTransport = transport == null ? ObdManager.Transport.AUTO : transport;
        this.adapterName = adapterName == null ? "" : adapterName;
        if (state == ObdManager.State.ECU_CONNECTED && previous != ObdManager.State.ECU_CONNECTED) {
            connectedAtMs = System.currentTimeMillis(); lastConnectedAtMs = connectedAtMs;
            prefs.edit().putLong("last_connected_at", lastConnectedAtMs).apply();
        } else if (state != ObdManager.State.ECU_CONNECTED && previous == ObdManager.State.ECU_CONNECTED) {
            connectedAtMs = 0;
        }
        if (state != ObdManager.State.ECU_CONNECTED) {
            targetRpm = Float.NaN;
            displayedRpm = Float.NaN;
        }
        invalidate();
    }

    @Override public void onDevices(List<ObdManager.DeviceInfo> devices) {
        this.devices = devices == null ? new ArrayList<>() : new ArrayList<>(devices);
        if (selectedDeviceKey.isEmpty()) {
            String verified = prefs.getString("verified_address", "");
            String vt = prefs.getString("verified_transport", "");
            for (ObdManager.DeviceInfo d : this.devices) {
                if (!verified.isEmpty() && verified.equals(d.address) && vt.equals(d.transport.name())) { selectedDeviceKey = d.key(); connectedAddress = d.address; break; }
            }
        }
        invalidate();
    }

    @Override public void onTelemetry(ObdManager.Telemetry telemetry) {
        // Keep only the newest snapshot. ELM packet timing no longer drives full-screen redraw timing.
        pendingTelemetry = telemetry == null ? new ObdManager.Telemetry() : telemetry;
        scheduleTelemetryFrame();
    }

    @Override public void onReadiness(ObdManager.Readiness readiness) {
        this.readiness = readiness == null ? new ObdManager.Readiness() : readiness;
        invalidate();
    }

    @Override public void onDtc(ObdManager.DtcResult result) {
        this.dtc = result == null ? new ObdManager.DtcResult() : result;
        invalidate();
    }

    // -----------------------------------------------------------------------------------------
    // DRAWING PRIMITIVES
    // -----------------------------------------------------------------------------------------

    private void panel(Canvas c, RectF r, int accent, boolean active) {
        fill.setColor(Color.argb(active ? 248 : 242, 2, 15, 19)); c.drawRoundRect(r, 6, 6, fill);
        stroke.setColor(active ? accent : BORDER); stroke.setStrokeWidth(active ? 1.6f : 1f); c.drawRoundRect(r, 6, 6, stroke);
    }

    private void title(Canvas c, String s, float x, float y) { label(c, s, x, y, 15.5f, WHITE, Paint.Align.LEFT, true, false); }

    private void infoRow(Canvas c, String name, String value, float x, float y, float offset) { infoRow(c, name, value, x, y, offset, WHITE); }
    private void infoRow(Canvas c, String name, String value, float x, float y, float offset, int valueColor) {
        label(c, name, x, y, 12.5f, MUTED, Paint.Align.LEFT, false, false);
        label(c, truncate(value, 26), x + offset, y, 12.5f, valueColor, Paint.Align.LEFT, true, false);
    }

    private void smallInfo(Canvas c, String name, String value, float x, float y, float right, int color) {
        label(c, name, x, y, 10.5f, MUTED, Paint.Align.LEFT, false, false);
        label(c, truncate(value, 26), right, y, 10.5f, color, Paint.Align.RIGHT, true, false);
        line(c, x, y + 5, right, y + 5, Color.argb(48, 105, 131, 143), 1f);
    }

    private void label(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean isBold, boolean isWide) {
        text.clearShadowLayer(); text.setStyle(Paint.Style.FILL); text.setColor(color); text.setTextAlign(align); text.setTextSize(size);
        text.setTypeface(isWide ? (isBold ? wideBold : wide) : (isBold ? bold : regular));
        c.drawText(s == null ? "" : s, x, y, text);
    }

    private void glowLabel(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean isBold, float shadow) {
        glow.setStyle(Paint.Style.FILL); glow.setColor(color); glow.setTextAlign(align); glow.setTextSize(size); glow.setTypeface(isBold ? italicBold : regular);
        glow.setShadowLayer(shadow, 0, 0, color); c.drawText(s == null ? "" : s, x, y, glow); glow.clearShadowLayer();
    }

    private void progress(Canvas c, float l, float t, float r, float b, SensorReading reading, int accent) {
        fill.setColor(Color.rgb(37, 55, 65)); c.drawRoundRect(l, t, r, b, 4, 4, fill);
        if (reading.state != SensorFreshness.State.VALID || Float.isNaN(reading.value)) return;
        float f = (reading.value - reading.key.min) / Math.max(.001f, reading.key.max - reading.key.min);
        f = clamp(f, 0f, 1f);
        fill.setColor(accent); c.drawRoundRect(l, t, l + (r - l) * f, b, 4, 4, fill);
    }

    private void scaleLabels(Canvas c, RectF r, SensorKey k, float y) {
        label(c, fmtScale(k.min), r.left + 18, y, 10.5f, MUTED, Paint.Align.LEFT, false, false);
        label(c, fmtScale((k.min + k.max) * .5f), r.centerX(), y, 10.5f, MUTED, Paint.Align.CENTER, false, false);
        label(c, fmtScale(k.max), r.right - 18, y, 10.5f, MUTED, Paint.Align.RIGHT, false, false);
    }

    private String fmtScale(float v) { return Math.abs(v - Math.round(v)) < .01f ? Integer.toString(Math.round(v)) : String.format(Locale.US, "%.1f", v); }

    private void drawToggle(Canvas c, float cx, float cy, boolean on) {
        fill.setColor(on ? GREEN : Color.rgb(58, 77, 86)); c.drawRoundRect(cx - 22, cy - 10, cx + 22, cy + 10, 10, 10, fill);
        circle(c, cx + (on ? 11 : -11), cy, 8, WHITE);
    }

    private void drawHondaMark(Canvas c, float x, float y) {
        // Lightweight vector approximation of the Honda badge; avoids the square "H" placeholder.
        tmp.set(x, y, x + 50, y + 42);
        stroke.setColor(WHITE); stroke.setStrokeWidth(2.2f); c.drawRoundRect(tmp, 11, 11, stroke);
        path.reset();
        path.moveTo(x + 12, y + 8);
        path.lineTo(x + 16, y + 33);
        path.lineTo(x + 22, y + 33);
        path.lineTo(x + 25, y + 22);
        path.lineTo(x + 28, y + 33);
        path.lineTo(x + 34, y + 33);
        path.lineTo(x + 38, y + 8);
        stroke.setColor(WHITE); stroke.setStrokeWidth(3f); stroke.setStyle(Paint.Style.STROKE);
        c.drawPath(path, stroke);
        line(c, x + 20, y + 19, x + 30, y + 19, WHITE, 2.5f);
    }

    private void drawIcon(Canvas c, Icon icon, float x, float y, int color, float s) {
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND); stroke.setColor(color); stroke.setStrokeWidth(Math.max(1.4f, 3f * s));
        fill.setStyle(Paint.Style.FILL); fill.setColor(color);
        switch (icon) {
            case BATTERY:
                c.drawRect(x - 19*s, y - 12*s, x + 19*s, y + 12*s, stroke); c.drawRect(x - 7*s, y - 16*s, x + 7*s, y - 12*s, fill); line(c,x-8*s,y,x+8*s,y,color,2*s); line(c,x,y-8*s,x,y+8*s,color,2*s); break;
            case TEMP:
                c.drawCircle(x, y + 10*s, 7*s, stroke); line(c,x,y+4*s,x,y-19*s,color,4*s); line(c,x+4*s,y-15*s,x+10*s,y-15*s,color,2*s); break;
            case AIR:
                line(c,x-20*s,y-10*s,x+4*s,y-10*s,color,3*s); line(c,x-20*s,y,x+10*s,y,color,3*s); line(c,x-20*s,y+10*s,x+4*s,y+10*s,color,3*s); arc(c,x+5*s,y,8*s,-90,180,color,2.5f*s); break;
            case THROTTLE:
                line(c,x-11*s,y+18*s,x+11*s,y-18*s,color,4*s); c.drawRect(x-16*s,y+7*s,x-5*s,y+14*s,stroke); c.drawRect(x+5*s,y-14*s,x+16*s,y-7*s,stroke); break;
            case ENGINE:
                tmp.set(x-18*s,y-10*s,x+17*s,y+12*s); c.drawRoundRect(tmp,4*s,4*s,stroke); line(c,x-23*s,y-3*s,x-18*s,y-3*s,color,3*s); line(c,x+17*s,y,x+23*s,y,color,3*s); line(c,x-9*s,y-15*s,x+7*s,y-15*s,color,3*s); break;
            case FUEL:
                c.drawRect(x-16*s,y-18*s,x+2*s,y+18*s,stroke); line(c,x-12*s,y-11*s,x-2*s,y-11*s,color,2*s); path.reset(); path.moveTo(x+3*s,y-13*s); path.lineTo(x+12*s,y-6*s); path.lineTo(x+12*s,y+14*s); path.lineTo(x+17*s,y+18*s); c.drawPath(path,stroke); break;
            case OBD:
                tmp.set(x-27*s,y-17*s,x+27*s,y+17*s); c.drawRoundRect(tmp,5*s,5*s,stroke); for(int i=0;i<4;i++){c.drawCircle(x-15*s+i*10*s,y-7*s,2.4f*s,stroke);c.drawCircle(x-15*s+i*10*s,y+7*s,2.4f*s,stroke);} break;
            case CHIP:
                c.drawRect(x-16*s,y-16*s,x+16*s,y+16*s,stroke); c.drawRect(x-9*s,y-9*s,x+9*s,y+9*s,stroke); for(int i=-1;i<=1;i++){line(c,x-25*s,y+i*10*s,x-17*s,y+i*10*s,color,2*s);line(c,x+17*s,y+i*10*s,x+25*s,y+i*10*s,color,2*s);line(c,x+i*10*s,y-25*s,x+i*10*s,y-17*s,color,2*s);line(c,x+i*10*s,y+17*s,x+i*10*s,y+25*s,color,2*s);} break;
            case GEAR:
                strokeCircle(c,x,y,13*s,color,3*s); strokeCircle(c,x,y,4*s,color,2*s); for(int i=0;i<8;i++){double a=Math.toRadians(i*45);line(c,x+(float)Math.cos(a)*13*s,y+(float)Math.sin(a)*13*s,x+(float)Math.cos(a)*19*s,y+(float)Math.sin(a)*19*s,color,3*s);} break;
            case LINK:
                arc(c,x-7*s,y,11*s,45,180,color,3*s); arc(c,x+7*s,y,11*s,225,180,color,3*s); line(c,x-8*s,y+8*s,x+8*s,y-8*s,color,3*s); break;
            case SEARCH:
                strokeCircle(c,x-4*s,y-4*s,10*s,color,3*s); line(c,x+4*s,y+4*s,x+14*s,y+14*s,color,3*s); break;
            case LIST:
                for(int i=0;i<3;i++){circle(c,x-14*s,y-10*s+i*10*s,2*s,color);line(c,x-7*s,y-10*s+i*10*s,x+16*s,y-10*s+i*10*s,color,2*s);} break;
            case LOG:
                c.drawRect(x-12*s,y-17*s,x+12*s,y+17*s,stroke); for(int i=0;i<3;i++)line(c,x-7*s,y-8*s+i*8*s,x+7*s,y-8*s+i*8*s,color,2*s); break;
            case CLOCK:
                strokeCircle(c,x,y,15*s,color,2.5f*s);line(c,x,y,x,y-9*s,color,2.5f*s);line(c,x,y,x+7*s,y+4*s,color,2.5f*s); break;
            case PULSE:
                path.reset();path.moveTo(x-20*s,y);path.lineTo(x-10*s,y);path.lineTo(x-5*s,y-10*s);path.lineTo(x+2*s,y+11*s);path.lineTo(x+8*s,y-5*s);path.lineTo(x+12*s,y);path.lineTo(x+20*s,y);c.drawPath(path,stroke);break;
            case RADIO:
                strokeCircle(c,x,y,5*s,color,2*s);arc(c,x,y,13*s,210,120,color,2*s);arc(c,x,y,20*s,210,120,color,2*s);break;
            case BLUETOOTH:
                path.reset();
                path.moveTo(x, y-20*s); path.lineTo(x+11*s, y-9*s); path.lineTo(x-8*s, y+10*s);
                path.moveTo(x, y+20*s); path.lineTo(x+11*s, y+9*s); path.lineTo(x-8*s, y-10*s);
                line(c,x,y-20*s,x,y+20*s,color,2.6f*s); c.drawPath(path,stroke); break;
            case FLAG:
                line(c,x-17*s,y-19*s,x-17*s,y+21*s,color,3*s); for(int rr=0;rr<3;rr++)for(int cc=0;cc<3;cc++)if((rr+cc)%2==0)c.drawRect(x-14*s+cc*9*s,y-17*s+rr*9*s,x-6*s+cc*9*s,y-9*s+rr*9*s,fill);break;
            case WARNING:
                path.reset();path.moveTo(x,y-21*s);path.lineTo(x+20*s,y+18*s);path.lineTo(x-20*s,y+18*s);path.close();c.drawPath(path,stroke);line(c,x,y-8*s,x,y+5*s,color,3*s);circle(c,x,y+12*s,2*s,color);break;
            case CHECK:
                c.drawRect(x-14*s,y-17*s,x+14*s,y+17*s,stroke);drawCheck(c,x,y,color,.5f*s);break;
            case O2:
                strokeCircle(c,x-7*s,y,13*s,color,3*s);label(c,"2",x+8*s,y+11*s,15*s,color,Paint.Align.CENTER,true,false);break;
            case SNOW:
                for(int i=0;i<3;i++){double a=Math.toRadians(i*60);float dx=(float)Math.cos(a)*20*s,dy=(float)Math.sin(a)*20*s;line(c,x-dx,y-dy,x+dx,y+dy,color,2*s);}break;
            case LEAF:
                tmp.set(x-18*s,y-14*s,x+17*s,y+13*s);c.drawOval(tmp,stroke);line(c,x-12*s,y+12*s,x+13*s,y-10*s,color,2*s);break;
            case INFO:
                strokeCircle(c,x,y,18*s,color,2.5f*s);label(c,"i",x,y+8*s,22*s,color,Paint.Align.CENTER,true,false);break;
            case WIFI:
                arc(c,x,y+5*s,18*s,210,120,color,2.5f*s);arc(c,x,y+5*s,11*s,210,120,color,2.5f*s);circle(c,x,y+11*s,2.5f*s,color);break;
            case ROAD:
                line(c,x-16*s,y+20*s,x-7*s,y-20*s,color,4*s);line(c,x+16*s,y+20*s,x+7*s,y-20*s,color,4*s);break;
        }
    }

    private void drawCheck(Canvas c, float x, float y, int color, float scale) {
        line(c, x - 12*scale, y, x - 3*scale, y + 9*scale, color, 3*scale);
        line(c, x - 3*scale, y + 9*scale, x + 14*scale, y - 10*scale, color, 3*scale);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
        stroke.setColor(color); stroke.setStrokeWidth(width); stroke.setStyle(Paint.Style.STROKE); c.drawLine(x1,y1,x2,y2,stroke);
    }
    private void circle(Canvas c, float x, float y, float r, int color) { fill.setColor(color); fill.setStyle(Paint.Style.FILL); c.drawCircle(x,y,r,fill); }
    private void strokeCircle(Canvas c, float x,float y,float r,int color,float width){stroke.setColor(color);stroke.setStrokeWidth(width);stroke.setStyle(Paint.Style.STROKE);c.drawCircle(x,y,r,stroke);}
    private void arc(Canvas c,float cx,float cy,float radius,float start,float sweep,int color,float width){tmp.set(cx-radius,cy-radius,cx+radius,cy+radius);stroke.setColor(color);stroke.setStrokeWidth(width);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeCap(Paint.Cap.BUTT);c.drawArc(tmp,start,sweep,false,stroke);stroke.setStrokeCap(Paint.Cap.ROUND);}
    private void radialLine(Canvas c,float cx,float cy,float r1,float r2,float angle,int color,float width){double a=Math.toRadians(angle);line(c,cx+(float)Math.cos(a)*r1,cy+(float)Math.sin(a)*r1,cx+(float)Math.cos(a)*r2,cy+(float)Math.sin(a)*r2,color,width);}
    private void polarLabel(Canvas c,String s,float cx,float cy,float r,float angle,float size,int color){double a=Math.toRadians(angle);label(c,s,cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r+size*.35f,size,color,Paint.Align.CENTER,true,false);}

    private String transportLabel(ObdManager.Transport t) {
        if (t == null) return "AUTO";
        switch (t) { case BLE: return "BLE 4.0"; case BLUETOOTH: return "BT 3.0"; case WIFI: return "Wi-Fi"; default: return "AUTO"; }
    }
    private String nonEmpty(String s,String fallback){return s==null||s.trim().isEmpty()?fallback:s;}
    private String truncate(String s,int max){if(s==null)return "";return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}
    private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
