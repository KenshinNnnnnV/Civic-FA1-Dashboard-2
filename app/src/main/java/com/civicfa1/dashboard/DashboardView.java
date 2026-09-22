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
 * Civic FA1 Dashboard v0.9.5 clean-runtime UI + performance.
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

    /** Geometry for one configurable SPORT widget.  The PNG owns the card; this owns live glyphs. */
    private static final class SportWidgetLayout {
        final RectF touchBounds;
        final RectF progressTrack;
        final float iconX, iconY, iconScale;
        final float titleX, titleY, titleMaxWidth;
        final float valueRight, valueBaseline, valueSize;
        final float unitX, unitY;
        final float scaleLeftX, scaleMidX, scaleRightX, scaleBaseline;

        SportWidgetLayout(RectF touchBounds, RectF progressTrack,
                          float iconX, float iconY, float iconScale,
                          float titleX, float titleY, float titleMaxWidth,
                          float valueRight, float valueBaseline, float valueSize,
                          float unitX, float unitY,
                          float scaleLeftX, float scaleMidX, float scaleRightX, float scaleBaseline) {
            this.touchBounds = touchBounds;
            this.progressTrack = progressTrack;
            this.iconX = iconX; this.iconY = iconY; this.iconScale = iconScale;
            this.titleX = titleX; this.titleY = titleY; this.titleMaxWidth = titleMaxWidth;
            this.valueRight = valueRight; this.valueBaseline = valueBaseline; this.valueSize = valueSize;
            this.unitX = unitX; this.unitY = unitY;
            this.scaleLeftX = scaleLeftX; this.scaleMidX = scaleMidX;
            this.scaleRightX = scaleRightX; this.scaleBaseline = scaleBaseline;
        }
    }

    private static final float DW = 1280f;
    private static final float DH = 720f;
    private static final int WHITE = Color.rgb(244, 247, 249);
    private static final int MUTED = Color.rgb(188, 199, 207);
    private static final int DIM = Color.rgb(102, 120, 129);
    private static final int GREEN = Color.rgb(24, 240, 145);
    private static final int CYAN = Color.rgb(31, 224, 201);
    private static final int RED = Color.rgb(255, 55, 76);
    private static final int YELLOW = Color.rgb(255, 214, 40);
    private static final int ORANGE = Color.rgb(255, 146, 28);
    // SPORT reference uses a cool blue/cyan fill for temperature and voltage tracks.
    private static final int SPORT_BLUE = Color.rgb(0, 185, 255);

    private final MainActivity activity;
    private final ObdManager obd;
    private final android.content.SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    // Dedicated annular clip for the live SPORT RPM fill.  It is deliberately separate from
    // `path`, which is reused by icon rendering later in the frame.
    private final Path tachArcClip = new Path();
    private final RectF tmp = new RectF();
    private final RectF canvasBounds = new RectF(0, 0, DW, DH);
    private final Typeface regular = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Typeface italicBold = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
    private final Typeface wide = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface wideBold = Typeface.create("sans-serif", Typeface.BOLD);
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.US);

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

    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private boolean firstHostStart = true;
    private boolean hostVisible;

    // Coalesce frequent ELM telemetry callbacks into UI frames.
    private static final long UI_FRAME_MS = 16L; // target ~60 FPS; only while visible/animating.
    private static final float TACH_MAX_RPM = 8000f;
    private ObdManager.Telemetry pendingTelemetry;
    private boolean telemetryFrameScheduled;
    private float displayedRpm = Float.NaN;
    private float targetRpm = Float.NaN;
    private String clockText = "--:--";
    private String dateText = "---";

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
                    displayedRpm += delta * 0.22f;
                    animating = true;
                } else {
                    displayedRpm = targetRpm;
                }
            } else {
                displayedRpm = targetRpm;
            }

            if (hostVisible) postInvalidateOnAnimation();
            if (pendingTelemetry != null || animating) scheduleTelemetryFrame();
        }
    };

    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            if (!hostVisible) return;
            clockText = clockFormat.format(new Date());
            dateText = dateFormat.format(new Date()).toUpperCase(Locale.US);
            postInvalidateOnAnimation();
            handler.postDelayed(this, 30_000L);
        }
    };

    private void scheduleTelemetryFrame() {
        if (!hostVisible || telemetryFrameScheduled) return;
        telemetryFrameScheduled = true;
        handler.postDelayed(telemetryFrame, UI_FRAME_MS);
    }

    // Geometry measured directly from the three approved 1280x720 reference images.
    // The reference bitmap itself is the immutable visual shell; these RectF values are interaction
    // and dynamic-value zones only. They intentionally match the approved artwork rather than the
    // earlier v0.7 programmatic approximation.
    // Slot order preserves the existing user preference keys: coolant, voltage, throttle, load, intake.
    private final SportWidgetLayout[] sportWidgets = {
            new SportWidgetLayout(new RectF(12, 130, 390, 308), new RectF(70, 242, 321, 261),
                    99, 190, .95f, 156, 173, 171, 265, 222, 48, 276, 218, 72, 195, 320, 290),
            new SportWidgetLayout(new RectF(890, 130, 1252, 308), new RectF(950, 242, 1203, 261),
                    984, 190, .95f, 1056, 173, 130, 1174, 222, 48, 1186, 218, 950, 1076, 1202, 290),
            new SportWidgetLayout(new RectF(26, 340, 385, 516), new RectF(78, 449, 330, 468),
                    105, 397, .95f, 170, 382, 128, 263, 429, 44, 279, 427, 78, 204, 330, 499),
            new SportWidgetLayout(new RectF(407, 449, 861, 585), new RectF(477, 534, 797, 550),
                    525, 500, .88f, 590, 488, 150, 673, 523, 38, 684, 521, 478, 637, 797, 575),
            new SportWidgetLayout(new RectF(894, 340, 1230, 516), new RectF(945, 449, 1196, 468),
                    990, 397, .95f, 1055, 382, 150, 1165, 429, 44, 1155, 427, 946, 1071, 1196, 499)
    };
    private final RectF[] connectionTypeBoxes = {
            new RectF(860, 207, 982, 292),
            new RectF(990, 207, 1114, 292),
            new RectF(1122, 207, 1246, 292)
    };
    // Measured from the clean DIAGNOSTICS shell. These are runtime-only zones; all card artwork stays in PNG.
    private final RectF[] diagnosticsMetricCards = {
            new RectF(286, 140, 478, 240), new RectF(489, 140, 689, 240), new RectF(699, 140, 885, 240),
            new RectF(286, 248, 478, 349), new RectF(489, 248, 689, 349), new RectF(699, 248, 885, 349),
            new RectF(286, 357, 478, 457), new RectF(489, 357, 689, 457), new RectF(699, 357, 885, 457),
            new RectF(286, 464, 478, 571), new RectF(489, 464, 689, 571), new RectF(699, 464, 885, 571)
    };

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

    private ObdManager.Transport parseTransport(String s, ObdManager.Transport fallback) {
        try { return ObdManager.Transport.valueOf(s); } catch (Exception ignored) { return fallback; }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public void onHostStart() {
        hostVisible = true;
        obd.setSportPriority(mode == Mode.SPORT);
        obd.refreshDevices();
        handler.removeCallbacks(clockTicker);
        clockText = clockFormat.format(new Date());
        dateText = dateFormat.format(new Date()).toUpperCase(Locale.US);
        handler.post(clockTicker);
        if (pendingTelemetry != null) scheduleTelemetryFrame();
        if (autoReconnect && activity.hasObdBluetoothPermissions() && obdState == ObdManager.State.DISCONNECTED) {
            handler.postDelayed(obd::connectAuto, firstHostStart ? 900L : 1400L);
        }
        firstHostStart = false;
    }

    public void onHostStop() {
        hostVisible = false;
        handler.removeCallbacks(telemetryFrame);
        handler.removeCallbacks(clockTicker);
        telemetryFrameScheduled = false;
        // Keep the task and active OBD link alive while minimized. Do not call obd.suspend().
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

        // The bitmap is the locked visual shell. Runtime paints only changing glyphs and state markers
        // directly onto its reserved areas; it never reconstructs cards with generic Canvas panels.
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
    // CURRENT RUNTIME OVERLAYS
    // -----------------------------------------------------------------------------------------

    private void drawReferenceHeaderOverlay(Canvas c) {
        if (mode == Mode.DIAGNOSTICS) {
            drawReferenceDiagnosticsHeader(c);
            return;
        }
        if (mode == Mode.SPORT) {
            drawReferenceSportHeader(c);
            return;
        }
        // The clean PNG reserves these fields; only live glyphs are painted here.
        int sc = stateColor();
        circle(c, 943, 23, 6.5f, sc);
        label(c, "OBD:", 958, 28, 14, WHITE, Paint.Align.LEFT, true, false);
        label(c, stateTitle(), 998, 28, 14, sc, Paint.Align.LEFT, true, false);
        label(c, truncate(stateSubtitle(), 25), 958, 46, 9.5f, MUTED, Paint.Align.LEFT, false, false);
        label(c, clockText, 1173, 33, 19, WHITE, Paint.Align.CENTER, true, false);
    }

    private void drawReferenceDiagnosticsHeader(Canvas c) {
        // The DIAGNOSTICS shell owns the header frame and divider. Draw only state glyphs and clock text.
        int sc = stateColor();
        drawIcon(c, Icon.ENGINE, 958, 61, sc, .56f);
        label(c, "OBD:", 998, 54, 14, WHITE, Paint.Align.LEFT, true, false);
        label(c, stateTitle(), 1038, 54, 14, sc, Paint.Align.LEFT, true, false);
        String subtitle = obdState == ObdManager.State.ECU_CONNECTED ? "ALL SYSTEMS NORMAL" : stateSubtitle();
        label(c, truncate(subtitle, 25), 998, 75, 9.5f, MUTED, Paint.Align.LEFT, false, false);
        label(c, clockText, 1207, 59, 19, WHITE, Paint.Align.CENTER, true, false);
        label(c, dateText, 1207, 78, 10, MUTED, Paint.Align.CENTER, false, false);
    }

    private void drawReferenceSportHeader(Canvas c) {
        // The Sport shell already owns the engine icon, "OBD:" label, divider and date.
        // Render just the live state word in the blank field after the static label.
        label(c, stateTitle(), 1026, 61, 14, stateColor(), Paint.Align.LEFT, true, false);
    }

    private void drawReferenceSportOverlay(Canvas c) {
        // The tach arc itself shows RPM. No separate needle/marker and no always-on colors.
        drawDynamicRpmArc(c);

        // Center RPM remains the latest truthful ECU sample; smoothing affects only the arc.
        String rpm = valueText(telemetry.rpm, 0x0C, 0);
        glowLabel(c, rpm, 640, 313, 66, WHITE, Paint.Align.CENTER, true, 1.8f);
        label(c, "RPM", 640, 347, 22, CYAN, Paint.Align.CENTER, true, false);

        // "SPEED" and "km/h" are part of the approved shell; only the number is dynamic.
        glowLabel(c, valueText(telemetry.speed, 0x0D, 0), 640, 440, 56, WHITE, Paint.Align.CENTER, true, 1.8f);

        for (int i = 0; i < sportWidgets.length; i++) {
            drawReferenceSportRuntimeCard(c, i, sportSlots[i]);
        }
    }

    private void drawDynamicRpmArc(Canvas c) {
        // The arc is the RPM indicator. No needle and no second set of 0..8 numerals.
        // The static SPORT shell owns the complete neutral scale; this draws one uniform green
        // fill only after the ECU has supplied an RPM sample.
        // Measured from the neutral tick band in background_sport.png.  The old center at y=270
        // belonged to the gauge interior, which explains the green wedge floating near “1”.
        final float cx = 640f, cy = 320f;
        final float startAngle = 145f, totalSweep = 250f;
        float rpm = obdState == ObdManager.State.ECU_CONNECTED && !Float.isNaN(displayedRpm)
                ? clamp(displayedRpm, 0f, TACH_MAX_RPM) : 0f;
        if (rpm <= 0f) return;

        // The clip is an annulus that exactly encloses the existing tick/scale band.  It makes
        // the runtime arc physically incapable of bleeding onto the numerals, bezel, cards or
        // center gauge, including its antialiased edges and the two arc endpoints.
        tachArcClip.reset();
        tachArcClip.setFillType(Path.FillType.EVEN_ODD);
        tachArcClip.addOval(new RectF(cx - 232f, cy - 225f, cx + 232f, cy + 225f), Path.Direction.CW);
        tachArcClip.addOval(new RectF(cx - 187f, cy - 180f, cx + 187f, cy + 180f), Path.Direction.CCW);

        float sweep = totalSweep * (rpm / TACH_MAX_RPM);
        c.save();
        c.clipPath(tachArcClip);
        // Two same-hue passes fill the full approved scale band evenly from 0 to the live RPM.
        // The annular clip contains both passes strictly inside the tachometer scale.
        ellipseArc(c, cx, cy, 210f, 202f, startAngle, sweep, Color.argb(70, 24, 240, 145), 31f, Paint.Cap.BUTT);
        ellipseArc(c, cx, cy, 210f, 202f, startAngle, sweep, Color.argb(208, 24, 240, 145), 21f, Paint.Cap.BUTT);
        c.restore();
    }

    private void ellipseArc(Canvas c, float cx, float cy, float rx, float ry,
                            float start, float sweep, int color, float width, Paint.Cap cap) {
        tmp.set(cx - rx, cy - ry, cx + rx, cy + ry);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        stroke.setStrokeCap(cap);
        c.drawArc(tmp, start, sweep, false, stroke);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    private void drawReferenceSportRuntimeCard(Canvas c, int slot, SensorKey key) {
        // A configured widget owns its entire semantic payload.  The PNG contributes only the
        // shell; changing a slot therefore cannot leave an old icon, title or scale behind.
        SportWidgetLayout layout = sportWidgets[slot];
        SensorReading reading = sensorReading(key);
        drawIcon(c, key.icon, layout.iconX, layout.iconY, CYAN, layout.iconScale);
        fitSportTitle(c, key.title, layout.titleX, layout.titleY, layout.titleMaxWidth, slot == 3 ? 19f : 22f);
        label(c, key.unit, layout.unitX, layout.unitY, slot == 3 ? 18f : 23f,
                CYAN, Paint.Align.LEFT, true, false);
        drawSportScaleLabels(c, key, layout);

        int valueColor = reading.state == SensorFreshness.State.VALID ? WHITE : MUTED;
        if (reading.state == SensorFreshness.State.VALID) {
            glowLabel(c, reading.display(), layout.valueRight, layout.valueBaseline, layout.valueSize,
                    valueColor, Paint.Align.RIGHT, true, 1.3f);
        } else {
            label(c, reading.display(), layout.valueRight, layout.valueBaseline, layout.valueSize,
                    valueColor, Paint.Align.RIGHT, true, false);
        }
        drawReferenceSportProgress(c, layout.progressTrack, reading, sportScaleMin(key), sportScaleMax(key), slot);
    }

    private void drawReferenceSportProgress(Canvas c, RectF track, SensorReading reading, float min, float max, int slot) {
        // A disconnected shell stays completely neutral, even if a stale value is still cached.
        // The color is live feedback, not part of the static PNG artwork.
        if (obdState != ObdManager.State.ECU_CONNECTED
                || reading.state != SensorFreshness.State.VALID || Float.isNaN(reading.value)) return;
        float fraction = clamp((reading.value - min) / Math.max(.001f, max - min), 0f, 1f);
        if (fraction <= 0f) return;
        float left = track.left + 3f, right = track.right - 3f, top = track.top + 3f, bottom = track.bottom - 3f;
        fill.setColor(sportProgressColor(slot));
        c.drawRoundRect(left, top, left + (right - left) * fraction, bottom, (bottom - top) / 2f, (bottom - top) / 2f, fill);
    }

    private int sportProgressColor(int slot) {
        // These are active data fills inside the existing PNG tracks, not replacement widgets.
        switch (slot) {
            case 2: return GREEN;   // throttle
            case 3: return YELLOW;  // engine load
            default: return SPORT_BLUE; // coolant, voltage and intake temperature
        }
    }

    private void fitSportTitle(Canvas c, String title, float x, float y, float maxWidth, float preferredSize) {
        text.clearShadowLayer();
        text.setStyle(Paint.Style.FILL);
        text.setColor(CYAN);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(bold);
        float size = preferredSize;
        while (size > 11f) {
            text.setTextSize(size);
            if (text.measureText(title) <= maxWidth) break;
            size -= .5f;
        }
        c.drawText(title, x, y, text);
    }

    private void drawSportScaleLabels(Canvas c, SensorKey key, SportWidgetLayout layout) {
        float min = sportScaleMin(key);
        float max = sportScaleMax(key);
        float mid = (min + max) * .5f;
        label(c, sportScaleText(min), layout.scaleLeftX, layout.scaleBaseline, 17f, MUTED, Paint.Align.CENTER, false, false);
        label(c, sportScaleText(mid), layout.scaleMidX, layout.scaleBaseline, 17f, MUTED, Paint.Align.CENTER, false, false);
        label(c, sportScaleText(max), layout.scaleRightX, layout.scaleBaseline, 17f, MUTED, Paint.Align.CENTER, false, false);
    }

    private String sportScaleText(float value) {
        if (Math.abs(value - Math.round(value)) < .01f) return Integer.toString(Math.round(value));
        return String.format(Locale.US, "%.1f", value);
    }

    private float sportScaleMin(SensorKey key) {
        switch (key) {
            case INTAKE:
            case TIMING: return -20f;
            case STFT:
            case LTFT: return -25f;
            default: return key.min;
        }
    }

    private float sportScaleMax(SensorKey key) {
        switch (key) {
            case INTAKE: return 60f;
            case MAP: return 110f;
            default: return key.max;
        }
    }
    private void drawReferenceConnectOverlay(Canvas c) {
        // Clean PNG value fields are intentionally blank; paint live text directly.
        boolean disconnected = obdState == ObdManager.State.DISCONNECTED;
        String device = disconnected ? "Not detected" : nonEmpty(adapterName, selectedDeviceName());
        label(c, truncate(device, 28), 220, 219, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : transportLabel(activeTransport), 220, 238, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "Disconnected" : stateTitle(), 220, 257, 12, stateColor(), Paint.Align.LEFT, true, false);
        label(c, disconnected ? "--" : nonEmpty(telemetry.adapterVersion, "N/A"), 220, 276, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : nonEmpty(currentDeviceAddress(), "N/A"), 220, 294, 12, WHITE, Paint.Align.LEFT, false, false);

        label(c, disconnected ? "--" : nonEmpty(telemetry.protocol, "N/A"), 652, 219, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, obdState == ObdManager.State.ECU_CONNECTED ? "Connected" : (disconnected ? "Unavailable" : "Offline"), 652, 238, 12,
                obdState == ObdManager.State.ECU_CONNECTED ? GREEN : (disconnected ? RED : MUTED), Paint.Align.LEFT, true, false);
        label(c, disconnected ? "--" : "N/A", 652, 257, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : "N/A", 652, 276, 12, WHITE, Paint.Align.LEFT, false, false);
        label(c, disconnected ? "--" : "N/A", 652, 294, 12, WHITE, Paint.Align.LEFT, false, false);

        drawReferenceConnectionType(c);
        drawReferenceSettings(c);
        drawReferenceConnectionStatus(c);
        drawReferenceRecentDevices(c);
        drawReferenceTransportOptions(c);
        drawReferenceConnectionDiagnostics(c);
    }

    private void drawReferenceConnectionType(Canvas c) {
        // The three button bodies/icons/labels are static artwork. Runtime only marks selection.
        ObdManager.Transport[] ts = {ObdManager.Transport.BLE, ObdManager.Transport.BLUETOOTH, ObdManager.Transport.WIFI};
        for (int i=0;i<3;i++) {
            boolean active = selectedTransport == ts[i];
            if (active) {
                stroke.setColor(GREEN);
                stroke.setStrokeWidth(2.2f);
                c.drawRoundRect(connectionTypeBoxes[i], 6, 6, stroke);
            }
            strokeCircle(c, connectionTypeBoxes[i].centerX(), connectionTypeBoxes[i].bottom - 15, 7,
                    active ? GREEN : Color.rgb(154,181,198), 1.5f);
            if (active) circle(c, connectionTypeBoxes[i].centerX(), connectionTypeBoxes[i].bottom - 15, 3.2f, GREEN);
        }
    }

    private void drawReferenceSettings(Canvas c) {
        // Rails, labels and fields are static PNG. Runtime draws values and knobs only.
        drawToggleIndicator(c, 770, 353, autoReconnect);
        label(c, wifiHost, 778, 377, 11, MUTED, Paint.Align.RIGHT, false, false);
        label(c, Integer.toString(wifiPort), 778, 398, 11, MUTED, Paint.Align.RIGHT, false, false);
        label(c, connectionTimeoutMs + " ms", 778, 418, 11, MUTED, Paint.Align.RIGHT, false, false);
        drawToggleIndicator(c, 770, 437, autoDetectProtocol);
        drawToggleIndicator(c, 770, 456, lowPowerMode);
    }

    private void drawReferenceConnectionStatus(Canvas c) {
        String panelState = obdState == ObdManager.State.ECU_CONNECTED ? "Connected" :
                (obdState == ObdManager.State.DISCONNECTED ? "Not Connected" : stateTitle());
        label(c, panelState, 1243, 332, 12, stateColor(), Paint.Align.RIGHT, true, false);
        float cx=906, cy=393;
        int sc = stateColor();
        // The neutral status ring belongs to the PNG shell. Runtime adds only a state-colored outline and glyph.
        strokeCircle(c, cx, cy, 31, sc, 2.2f);
        if (obdState == ObdManager.State.ECU_CONNECTED) {
            drawCheck(c, cx, cy, GREEN, .78f);
        } else if (obdState == ObdManager.State.DISCONNECTED || obdState == ObdManager.State.ERROR) {
            line(c, cx - 10, cy - 10, cx + 10, cy + 10, sc, 4f);
            line(c, cx + 10, cy - 10, cx - 10, cy + 10, sc, 4f);
        } else {
            drawIcon(c, Icon.LINK, cx, cy, sc, .65f);
        }
        label(c, obdState == ObdManager.State.ECU_CONNECTED ? "Connection Healthy" : "Not Connected",
                cx, 449, 13, sc, Paint.Align.CENTER, true, false);
        String centerDetail = obdState == ObdManager.State.ECU_CONNECTED ? "Stable communication" :
                (obdState == ObdManager.State.DISCONNECTED ? "Connect to OBD adapter" : truncate(obdDetail, 23));
        label(c, centerDetail, cx, 465, 10, MUTED, Paint.Align.CENTER, false, false);
        if (obdState == ObdManager.State.DISCONNECTED)
            label(c, "to start communication", cx, 478, 10, MUTED, Paint.Align.CENTER, false, false);

        // The PNG owns the labels, divider lines and row positions at the right of this card.
        // Only values belong to runtime; repeating labels was the source of the double text on
        // the head-unit photo.
        float vr=1243;
        String session = connectedAtMs > 0 ? formatDuration(System.currentTimeMillis()-connectedAtMs) : "--";
        label(c, session, vr, 354, 10.5f, WHITE, Paint.Align.RIGHT, true, false);
        label(c, lastConnectedAtMs > 0 ? clockFormat.format(new Date(lastConnectedAtMs)) : "--", vr, 375, 10.5f, WHITE, Paint.Align.RIGHT, true, false);
        label(c, obdState == ObdManager.State.DISCONNECTED ? "--" : transportLabel(activeTransport), vr, 396, 10.5f, WHITE, Paint.Align.RIGHT, true, false);
        label(c, obdState == ObdManager.State.DISCONNECTED ? "--" : nonEmpty(telemetry.protocol, "N/A"), vr, 417, 10.5f, WHITE, Paint.Align.RIGHT, true, false);
        label(c, obdState == ObdManager.State.DISCONNECTED ? "--" : "N/A", vr, 438, 10.5f, MUTED, Paint.Align.RIGHT, true, false);
        label(c, obdState == ObdManager.State.DISCONNECTED ? "-- / --" : "N/A", vr, 459, 10.5f, MUTED, Paint.Align.RIGHT, true, false);
        label(c, obdState == ObdManager.State.DISCONNECTED ? "--" : (obdState == ObdManager.State.ERROR ? "1+" : "0"),
                vr, 478, 10.5f, obdState == ObdManager.State.ERROR ? RED : WHITE, Paint.Align.RIGHT, true, false);
    }

    private void drawReferenceRecentDevices(Canvas c) {
        List<ObdManager.DeviceInfo> list = visibleDevicePool();
        int start = Math.min(deviceScroll, Math.max(0, list.size()-4));
        if (list.isEmpty()) {
            label(c, "No devices discovered yet", 79, 540, 11, MUTED, Paint.Align.LEFT, false, false);
            label(c, "Use Scan above", 79, 565, 10, DIM, Paint.Align.LEFT, false, false);
            return;
        }
        for (int row=0; row<4 && start+row<list.size(); row++) {
            ObdManager.DeviceInfo d = list.get(start+row);
            float y = 540 + row*26;
            boolean selected = d.key().equals(selectedDeviceKey);
            if (selected) {
                stroke.setColor(GREEN); stroke.setStrokeWidth(1f); c.drawRoundRect(34, y-15, 422, y+7, 4,4,stroke);
            }
            circle(c, 53, y-4, 5, selected ? GREEN : Color.rgb(194,210,231));
            label(c, truncate(d.name, 24), 80, y, 11.5f, WHITE, Paint.Align.LEFT, false, false);
            label(c, truncate(d.address, 20), 354, y, 10.5f, MUTED, Paint.Align.RIGHT, false, false);
        }
    }

    private void drawReferenceTransportOptions(Canvas c) {
        ObdManager.Transport[] types={ObdManager.Transport.AUTO,ObdManager.Transport.BLE,ObdManager.Transport.BLUETOOTH,ObdManager.Transport.WIFI,ObdManager.Transport.WIFI};
        for(int i=0;i<5;i++){
            float y=540+i*21.5f;
            boolean active = i == 0 ? autoTransport : (!autoTransport && selectedTransport==types[i] && (types[i]!=ObdManager.Transport.WIFI || i==3));
            strokeCircle(c,474,y-3,7,active?GREEN:Color.rgb(173,196,220),1.5f);
            if(active) circle(c,474,y-3,3.2f,GREEN);
        }
    }

    private void drawReferenceConnectionDiagnostics(Canvas c) {
        boolean adapter = obdState != ObdManager.State.DISCONNECTED && obdState != ObdManager.State.SEARCHING;
        boolean transport = obdState == ObdManager.State.INITIALIZING || obdState == ObdManager.State.ECU_CONNECTING || obdState == ObdManager.State.ECU_CONNECTED;
        boolean handshake = obdState == ObdManager.State.ECU_CONNECTING || obdState == ObdManager.State.ECU_CONNECTED;
        boolean ecu = obdState == ObdManager.State.ECU_CONNECTED;
        boolean data = ecu && hasAnyFreshTelemetry();
        boolean[] ok={adapter,transport,handshake,ecu,data,ecu};
        for(int i=0;i<6;i++){
            float y=540+i*18.5f;
            strokeCircle(c,877,y-3,6,ok[i]?GREEN:Color.rgb(132,160,176),1.5f);
            if(ok[i]) drawCheck(c,877,y-3,GREEN,.28f);
            String right = i==5 ? (connectedAtMs>0?formatDuration(System.currentTimeMillis()-connectedAtMs):"--") : (ok[i]?"OK":"Pending");
            label(c,right,1230,y,10.5f,ok[i]?GREEN:MUTED,Paint.Align.RIGHT,true,false);
        }
    }

    private void drawReferenceDiagnosticsOverlay(Canvas c) {
        // All labels, icons, card bodies and tracks are part of the approved PNG. This method paints
        // only live glyphs, scale labels and progress fill directly in their reserved blank areas.
        drawReferenceDiagnosticPidMetric(c, diagnosticsMetricCards[0], telemetry.rpm, 0x0C, 0, "rpm", 0f, 8000f, "0", "8000");
        drawReferenceDiagnosticPidMetric(c, diagnosticsMetricCards[1], telemetry.speed, 0x0D, 0, "km/h", 0f, 240f, "0", "240");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[2], SensorKey.COOLANT, -40f, 150f, "-40", "150");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[3], SensorKey.INTAKE, -40f, 120f, "-40", "120");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[4], SensorKey.THROTTLE, 0f, 100f, "0", "100");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[5], SensorKey.MAP, 0f, 255f, "0", "255");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[6], SensorKey.MAF, 0f, 50f, "0", "50");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[7], SensorKey.STFT, -25f, 25f, "-25", "25");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[8], SensorKey.LTFT, -25f, 25f, "-25", "25");
        drawReferenceDiagnosticUnavailableMetric(c, diagnosticsMetricCards[9], "V", 0f, 1f, "0.0", "1.0");
        drawReferenceDiagnosticUnavailableMetric(c, diagnosticsMetricCards[10], "V", 0f, 1f, "0.0", "1.0");
        drawReferenceDiagnosticSensorMetric(c, diagnosticsMetricCards[11], SensorKey.MODULE_VOLTAGE, 9f, 16f, "9.0", "16.0");
        drawReferenceDiagnosticsStatus(c);
        drawReferenceVehicleScanState(c);
    }

    private void drawReferenceDiagnosticPidMetric(Canvas c, RectF r, float value, int pid, int decimals,
                                                   String unit, float min, float max, String minLabel, String maxLabel) {
        boolean valid = obdState == ObdManager.State.ECU_CONNECTED && !Float.isNaN(value)
                && (!telemetry.capabilitiesKnown || telemetry.supports(pid));
        drawReferenceDiagnosticMetric(c, r, valueText(value, pid, decimals), unit, value, valid, min, max, minLabel, maxLabel);
    }

    private void drawReferenceDiagnosticSensorMetric(Canvas c, RectF r, SensorKey key,
                                                      float min, float max, String minLabel, String maxLabel) {
        SensorReading reading = sensorReading(key);
        drawReferenceDiagnosticMetric(c, r, reading.display(), key.unit, reading.value,
                reading.state == SensorFreshness.State.VALID, min, max, minLabel, maxLabel);
    }

    private void drawReferenceDiagnosticUnavailableMetric(Canvas c, RectF r, String unit,
                                                           float min, float max, String minLabel, String maxLabel) {
        String value = obdState == ObdManager.State.ECU_CONNECTED ? "N/A" : "--";
        drawReferenceDiagnosticMetric(c, r, value, unit, Float.NaN, false, min, max, minLabel, maxLabel);
    }

    private void drawReferenceDiagnosticMetric(Canvas c, RectF r, String value, String unit, float rawValue,
                                                boolean valid, float min, float max, String minLabel, String maxLabel) {
        final float valueX = r.left + 76f;
        final float valueY = r.top + 55f;
        final float valueSize = 31f;
        int valueColor = valid ? GREEN : MUTED;
        if (valid) glowLabel(c, value, valueX, valueY, valueSize, valueColor, Paint.Align.LEFT, true, 1.1f);
        else label(c, value, valueX, valueY, valueSize, valueColor, Paint.Align.LEFT, true, false);
        if (!"N/A".equals(value)) {
            float unitX = valueX + dynamicValueWidth(value, valueSize) + 7f;
            label(c, unit, unitX, valueY - 1f, 14f, MUTED, Paint.Align.LEFT, false, false);
        }
        if (valid) {
            float fraction = clamp((rawValue - min) / Math.max(.001f, max - min), 0f, 1f);
            float left = r.left + 13f, right = r.right - 13f, top = r.top + 65f, bottom = top + 9f;
            fill.setColor(GREEN);
            c.drawRoundRect(left, top, left + (right - left) * fraction, bottom, 4.5f, 4.5f, fill);
        }
        label(c, minLabel, r.left + 12f, r.bottom - 8f, 10.5f, MUTED, Paint.Align.LEFT, false, false);
        label(c, maxLabel, r.right - 12f, r.bottom - 8f, 10.5f, MUTED, Paint.Align.RIGHT, false, false);
    }

    private float dynamicValueWidth(String value, float size) {
        glow.setTextSize(size);
        glow.setTypeface(italicBold);
        return glow.measureText(value == null ? "" : value);
    }

    private void drawReferenceDiagnosticsStatus(Canvas c) {
        boolean connected = obdState == ObdManager.State.ECU_CONNECTED;
        int statusColor = connected ? GREEN : (obdState == ObdManager.State.ERROR ? RED : MUTED);
        label(c, connected ? "CONNECTED" : stateTitle(), 986, 190, 17, statusColor, Paint.Align.LEFT, true, false);
        label(c, connected ? nonEmpty(telemetry.protocol, "N/A") : "N/A", 986, 244, 15, connected ? GREEN : MUTED, Paint.Align.LEFT, true, false);

        int count = dtc.codes == null ? 0 : dtc.codes.size();
        String codes = dtc.status == ObdManager.DtcStatus.NO_CODES ? "0 CODES"
                : dtc.status == ObdManager.DtcStatus.HAS_CODES ? count + " CODES" : "N/A";
        int codesColor = dtc.status == ObdManager.DtcStatus.HAS_CODES ? RED : (dtc.status == ObdManager.DtcStatus.NO_CODES ? GREEN : MUTED);
        label(c, codes, 986, 298, 16, codesColor, Paint.Align.LEFT, true, false);

        boolean readinessComplete = isReadinessComplete();
        String readinessLabel = readiness.available ? (readinessComplete ? "COMPLETE" : "NOT READY") : "N/A";
        int readinessColor = readinessComplete ? GREEN : MUTED;
        label(c, readinessLabel, 1176, 292, 15, readinessColor, Paint.Align.CENTER, true, false);
        label(c, readiness.available ? readinessSummaryShort() + " MONITORS" : "WAITING FOR ECU", 1176, 313, 9.5f,
                readinessColor, Paint.Align.CENTER, false, false);
    }

    private void drawReferenceVehicleScanState(Canvas c) {
        String state = obdState == ObdManager.State.ECU_CONNECTED ? "N/A" : "--";
        int color = obdState == ObdManager.State.ECU_CONNECTED ? MUTED : DIM;
        label(c, state, 916, 403, 12, color, Paint.Align.LEFT, true, false);
        label(c, state, 1244, 393, 12, color, Paint.Align.RIGHT, true, false);
        label(c, state, 916, 570, 12, color, Paint.Align.LEFT, true, false);
        label(c, state, 1112, 570, 12, color, Paint.Align.CENTER, true, false);
        label(c, state, 1244, 559, 12, color, Paint.Align.RIGHT, true, false);
    }

    private String formatDuration(long ms) {
        long total=Math.max(0,ms/1000L), min=total/60L, sec=total%60L;
        long hr=min/60L; min%=60L;
        return hr>0 ? String.format(Locale.US,"%02d:%02d:%02d",hr,min,sec) : String.format(Locale.US,"%02d:%02d",min,sec);
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

    private boolean isReadinessComplete() {
        if (!readiness.available || readiness.monitors == null) return false;
        boolean hasSupportedMonitor = false;
        for (ObdManager.MonitorState monitor : readiness.monitors.values()) {
            if (monitor == ObdManager.MonitorState.UNSUPPORTED) continue;
            hasSupportedMonitor = true;
            if (monitor != ObdManager.MonitorState.COMPLETE) return false;
        }
        return hasSupportedMonitor && !readiness.milOn;
    }

    // -----------------------------------------------------------------------------------------
    // SPORT SENSOR PICKER
    // -----------------------------------------------------------------------------------------

    private void drawSensorPicker(Canvas c) {
        fill.setColor(Color.argb(190, 0, 0, 0));
        c.drawRect(0, 0, DW, DH, fill);

        final float left = 174f, topY = 102f, right = 1106f, bottom = 610f;
        final float contentTop = topY + 92f;
        final float side = 30f, gapX = 12f, gapY = 10f, cardH = 60f;
        RectF r = new RectF(left, topY, right, bottom);
        fill.setColor(Color.rgb(4, 18, 24));
        c.drawRoundRect(r, 14, 14, fill);
        stroke.setColor(GREEN);
        stroke.setStrokeWidth(1.5f);
        c.drawRoundRect(r, 14, 14, stroke);
        label(c, "SELECT SENSOR", r.left + 34, r.top + 43, 24, WHITE, Paint.Align.LEFT, true, true);
        label(c, "Sport widget " + (sensorPickerSlot + 1), r.left + 34, r.top + 67, 12, MUTED, Paint.Align.LEFT, false, false);
        label(c, "×", r.right - 34, r.top + 45, 32, WHITE, Paint.Align.CENTER, false, false);

        SensorKey[] keys = SensorKey.values();
        float cardW = (r.width() - side * 2f - gapX * 2f) / 3f;

        c.save();
        // Hard clip guarantees that no future sensor card can bleed outside the modal frame.
        c.clipRect(r.left + 2f, contentTop - 2f, r.right - 2f, r.bottom - 12f);
        for (int i = 0; i < keys.length; i++) {
            int col = i % 3, row = i / 3;
            float x = r.left + side + col * (cardW + gapX);
            float y = contentTop + row * (cardH + gapY);
            RectF rr = new RectF(x, y, x + cardW, y + cardH);
            boolean selected = sportSlots[sensorPickerSlot] == keys[i];
            fill.setColor(selected ? Color.argb(85, 0, 145, 84) : Color.rgb(7, 28, 35));
            c.drawRoundRect(rr, 7, 7, fill);
            stroke.setColor(selected ? GREEN : Color.rgb(64, 91, 103));
            stroke.setStrokeWidth(selected ? 1.6f : 1f);
            c.drawRoundRect(rr, 7, 7, stroke);
            drawIcon(c, keys[i].icon, rr.left + 28, rr.centerY(), selected ? GREEN : Color.rgb(190, 210, 220), .46f);
            label(c, keys[i].title, rr.left + 52, rr.centerY() - 3, 11.2f, selected ? WHITE : MUTED, Paint.Align.LEFT, true, false);
            label(c, sensorReading(keys[i]).state == SensorFreshness.State.UNSUPPORTED ? "N/A" : keys[i].unit,
                    rr.left + 52, rr.centerY() + 16, 9.8f, selected ? GREEN : DIM, Paint.Align.LEFT, false, false);
        }
        c.restore();
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
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_MOVE) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        if (x < 0 || y < 0 || x > DW || y > DH) return true;

        if (sensorPickerSlot >= 0) {
            handleSensorPickerTouch(x, y);
            return true;
        }

        // These are invisible hit zones aligned to the selected tab artwork in each current shell.
        final float navTop = mode == Mode.CONNECT ? 642f : mode == Mode.SPORT ? 607f : 602f;
        final float navBottom = mode == Mode.CONNECT ? 703f : mode == Mode.SPORT ? 687f : 686f;
        if (y >= navTop && y <= navBottom) {
            int idx;
            if (mode == Mode.CONNECT) idx = x <= 420f ? 0 : x <= 860f ? 1 : 2;
            else if (mode == Mode.DIAGNOSTICS) idx = x <= 446f ? 0 : x <= 849f ? 1 : 2;
            else idx = x <= 410f ? 0 : x <= 839f ? 1 : 2;
            mode = idx == 0 ? Mode.CONNECT : idx == 1 ? Mode.SPORT : Mode.DIAGNOSTICS;
            obd.setSportPriority(mode == Mode.SPORT);
            postInvalidateOnAnimation(); return true;
        }

        if (mode == Mode.CONNECT) handleConnectTouch(x, y);
        else if (mode == Mode.SPORT) handleSportTouch(x, y);
        return true;
    }

    private void handleSportTouch(float x, float y) {
        for (int i = 0; i < sportWidgets.length; i++) {
            if (sportWidgets[i].touchBounds.contains(x, y)) { sensorPickerSlot = i; invalidate(); return; }
        }
    }

    private void handleSensorPickerTouch(float x, float y) {
        final float left = 174f, topY = 102f, right = 1106f, bottom = 610f;
        final float contentTop = topY + 92f;
        final float side = 30f, gapX = 12f, gapY = 10f, cardH = 60f;
        RectF r = new RectF(left, topY, right, bottom);
        if (!r.contains(x, y) || (x > r.right - 75 && y < r.top + 80)) {
            sensorPickerSlot = -1;
            invalidate();
            return;
        }

        SensorKey[] keys = SensorKey.values();
        float cardW = (r.width() - side * 2f - gapX * 2f) / 3f;
        for (int i = 0; i < keys.length; i++) {
            int col = i % 3, row = i / 3;
            float cardX = r.left + side + col * (cardW + gapX);
            float cardY = contentTop + row * (cardH + gapY);
            RectF rr = new RectF(cardX, cardY, cardX + cardW, cardY + cardH);
            if (rr.contains(x, y)) {
                sportSlots[sensorPickerSlot] = keys[i];
                prefs.edit().putString("sport_slot_" + sensorPickerSlot, keys[i].name()).apply();
                sensorPickerSlot = -1;
                invalidate();
                return;
            }
        }
    }

    private void handleConnectTouch(float x, float y) {
        // Top transport selector: exact approved-reference boxes.
        RectF ble = new RectF(860, 207, 982, 292);
        RectF bt = new RectF(990, 207, 1114, 292);
        RectF wifi = new RectF(1122, 207, 1246, 292);
        if (ble.contains(x, y)) { setSelectedTransport(ObdManager.Transport.BLE); return; }
        if (bt.contains(x, y)) { setSelectedTransport(ObdManager.Transport.BLUETOOTH); return; }
        if (wifi.contains(x, y)) { setSelectedTransport(ObdManager.Transport.WIFI); return; }

        // Device actions — 2 columns x 3 rows from the approved reference.
        RectF scan = new RectF(33, 342, 221, 377);
        RectF select = new RectF(229, 342, 421, 377);
        RectF connect = new RectF(33, 384, 221, 419);
        RectF disconnect = new RectF(229, 384, 421, 419);
        RectF retry = new RectF(33, 427, 221, 463);
        RectF log = new RectF(229, 427, 421, 463);
        if (scan.contains(x,y)) { scanSelectedTransport(); return; }
        if (select.contains(x,y)) { selectNextDevice(); return; }
        if (connect.contains(x,y)) { connectSelected(); return; }
        if (disconnect.contains(x,y)) { obd.disconnect(); return; }
        if (retry.contains(x,y)) { connectSelected(); return; }
        if (log.contains(x,y)) { obd.setDebugLoggingEnabled(true); activity.showObdLog(obd.getDebugLogText(), this::exportLog); return; }

        // SETTINGS rows.
        if (x >= 454 && x <= 800) {
            if (y >= 342 && y <= 362) { autoReconnect = !autoReconnect; prefs.edit().putBoolean("auto_reconnect", autoReconnect).apply(); invalidate(); return; }
            if (y >= 364 && y <= 385) { activity.promptWifiEndpoint(wifiHost, wifiPort, (host, port) -> { wifiHost = host; wifiPort = port; prefs.edit().putString("wifi_host", host).putInt("wifi_port", port).apply(); invalidate(); }); return; }
            if (y >= 386 && y <= 406) { activity.promptWifiEndpoint(wifiHost, wifiPort, (host, port) -> { wifiHost = host; wifiPort = port; prefs.edit().putString("wifi_host", host).putInt("wifi_port", port).apply(); invalidate(); }); return; }
            if (y >= 407 && y <= 428) { activity.promptConnectionTimeout(connectionTimeoutMs, ms -> { connectionTimeoutMs = ms; prefs.edit().putInt("connection_timeout_ms", ms).apply(); obd.setConnectionTimeoutMs(ms); invalidate(); }); return; }
            if (y >= 429 && y <= 447) { autoDetectProtocol = !autoDetectProtocol; prefs.edit().putBoolean("auto_detect_protocol", autoDetectProtocol).apply(); invalidate(); return; }
            if (y >= 448 && y <= 469) { lowPowerMode = !lowPowerMode; prefs.edit().putBoolean("low_power_mode", lowPowerMode).apply(); invalidate(); return; }
        }

        // Recent devices.
        List<ObdManager.DeviceInfo> list = visibleDevicePool();
        int first = Math.min(deviceScroll, Math.max(0, list.size() - 4));
        for (int row=0; row<4 && first+row<list.size(); row++) {
            RectF rr = new RectF(34, 522 + row*26, 422, 547 + row*26);
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
        if (x >= 455 && x <= 827 && y >= 522 && y <= 631) {
            int row = Math.max(0, Math.min(4, (int)((y - 522) / 21.5f)));
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
        if (hostVisible) postInvalidateOnAnimation();
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
        if (hostVisible) postInvalidateOnAnimation();
    }

    @Override public void onTelemetry(ObdManager.Telemetry telemetry) {
        // Keep only the newest snapshot. ELM packet timing no longer drives full-screen redraw timing.
        pendingTelemetry = telemetry == null ? new ObdManager.Telemetry() : telemetry;
        if (hostVisible) scheduleTelemetryFrame();
    }

    @Override public void onReadiness(ObdManager.Readiness readiness) {
        this.readiness = readiness == null ? new ObdManager.Readiness() : readiness;
        if (hostVisible) postInvalidateOnAnimation();
    }

    @Override public void onDtc(ObdManager.DtcResult result) {
        this.dtc = result == null ? new ObdManager.DtcResult() : result;
        if (hostVisible) postInvalidateOnAnimation();
    }

    // -----------------------------------------------------------------------------------------
    // DRAWING PRIMITIVES
    // -----------------------------------------------------------------------------------------

    private void label(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean isBold, boolean isWide) {
        text.clearShadowLayer(); text.setStyle(Paint.Style.FILL); text.setColor(color); text.setTextAlign(align); text.setTextSize(size);
        text.setTypeface(isWide ? (isBold ? wideBold : wide) : (isBold ? bold : regular));
        c.drawText(s == null ? "" : s, x, y, text);
    }

    private void glowLabel(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean isBold, float shadow) {
        glow.setStyle(Paint.Style.FILL); glow.setColor(color); glow.setTextAlign(align); glow.setTextSize(size); glow.setTypeface(isBold ? italicBold : regular);
        glow.setShadowLayer(shadow, 0, 0, color); c.drawText(s == null ? "" : s, x, y, glow); glow.clearShadowLayer();
    }

    private void drawToggleIndicator(Canvas c, float cx, float cy, boolean on) {
        // The rail belongs to the static PNG shell. Only the stateful knob is runtime content.
        circle(c, cx + (on ? 11 : -11), cy, 8, on ? GREEN : WHITE);
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

    private String transportLabel(ObdManager.Transport t) {
        if (t == null) return "AUTO";
        switch (t) { case BLE: return "BLE 4.0"; case BLUETOOTH: return "BT 3.0"; case WIFI: return "Wi-Fi"; default: return "AUTO"; }
    }
    private String nonEmpty(String s,String fallback){return s==null||s.trim().isEmpty()?fallback:s;}
    private String truncate(String s,int max){if(s==null)return "";return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}
    private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
