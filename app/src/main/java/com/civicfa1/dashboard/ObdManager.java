package com.civicfa1.dashboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session-oriented ELM327 transport for Civic FA1 Dashboard.
 *
 * Important invariants:
 *  - a transport connection is NOT the same as an ECU connection;
 *  - ECU_CONNECTED is emitted only after a complete 41 00 A B C D response;
 *  - every connection attempt owns its own sockets/GATT/scanner and generation id;
 *  - callbacks from old sessions are ignored;
 *  - ELM commands are strictly serialized and a timeout without '>' aborts the session;
 *  - normal mode never fabricates telemetry.
 */
public final class ObdManager {

    public enum Transport { AUTO, BLUETOOTH, BLE, WIFI }

    public enum State {
        DISCONNECTED,
        PERMISSION_REQUIRED,
        SEARCHING,
        ADAPTER_FOUND,
        CONNECTING,
        INITIALIZING,
        ECU_CONNECTING,
        ECU_CONNECTED,
        ERROR
    }

    public enum MonitorState { UNSUPPORTED, INCOMPLETE, COMPLETE }
    public enum DtcStatus { NOT_READ, ERROR, NO_CODES, HAS_CODES }

    public static final class DeviceInfo {
        public final String name;
        public final String address;
        public final Transport transport;
        public final boolean paired;
        public final int rssi;

        DeviceInfo(String name, String address, Transport transport, boolean paired) {
            this(name, address, transport, paired, Integer.MIN_VALUE);
        }

        DeviceInfo(String name, String address, Transport transport, boolean paired, int rssi) {
            this.name = name == null || name.trim().isEmpty() ? "Unknown adapter" : name;
            this.address = address == null ? "" : address;
            this.transport = transport == null ? Transport.AUTO : transport;
            this.paired = paired;
            this.rssi = rssi;
        }

        public String key() { return transport.name() + ":" + address; }
    }

    public static final class Readiness {
        public boolean available;
        public boolean milOn;
        public int dtcCount;
        public boolean compressionIgnition;
        public final Map<String, MonitorState> monitors = new LinkedHashMap<>();

        Readiness copy() {
            Readiness r = new Readiness();
            r.available = available;
            r.milOn = milOn;
            r.dtcCount = dtcCount;
            r.compressionIgnition = compressionIgnition;
            r.monitors.putAll(monitors);
            return r;
        }
    }

    public static final class DtcResult {
        public DtcStatus status = DtcStatus.NOT_READ;
        public final List<String> codes = new ArrayList<>();
        public String detail = "Not read";

        DtcResult copy() {
            DtcResult d = new DtcResult();
            d.status = status;
            d.codes.addAll(codes);
            d.detail = detail;
            return d;
        }
    }

    /**
     * Telemetry carries value + freshness timestamp for every sensor. A NaN value means no
     * currently valid data. supportedPids is populated after the capability bitmap is read.
     */
    public static final class Telemetry {
        public float rpm = Float.NaN;            public long rpmAt;
        public float speed = Float.NaN;          public long speedAt;
        public float coolant = Float.NaN;        public long coolantAt;
        public float intake = Float.NaN;         public long intakeAt;
        public float throttle = Float.NaN;       public long throttleAt;
        public float load = Float.NaN;           public long loadAt;
        public float maf = Float.NaN;            public long mafAt;
        public float fuel = Float.NaN;           public long fuelAt;
        /** ECU module voltage from PID 0142 only. */
        public float voltage = Float.NaN;        public long voltageAt;
        /** ELM adapter supply voltage from ATRV, intentionally separate. */
        public float adapterVoltage = Float.NaN; public long adapterVoltageAt;
        public float map = Float.NaN;            public long mapAt;
        public float timing = Float.NaN;         public long timingAt;
        public float fuelRate = Float.NaN;       public long fuelRateAt;
        public float shortFuelTrim = Float.NaN;  public long shortFuelTrimAt;
        public float longFuelTrim = Float.NaN;   public long longFuelTrimAt;
        public String protocol = "";
        public String adapterVersion = "";
        public boolean capabilitiesKnown;
        public final Set<Integer> supportedPids = new HashSet<>();

        public boolean supports(int pid) { return capabilitiesKnown && supportedPids.contains(pid); }

        public Telemetry copyFresh(long now) {
            Telemetry t = new Telemetry();
            t.protocol = protocol;
            t.adapterVersion = adapterVersion;
            t.capabilitiesKnown = capabilitiesKnown;
            t.supportedPids.addAll(supportedPids);
            // Fast sensors expire quickly; slow sensors are allowed to live longer.
            t.rpm = fresh(rpm, rpmAt, now, 1600); t.rpmAt = rpmAt;
            t.speed = fresh(speed, speedAt, now, 1800); t.speedAt = speedAt;
            t.throttle = fresh(throttle, throttleAt, now, 1800); t.throttleAt = throttleAt;
            t.load = fresh(load, loadAt, now, 1800); t.loadAt = loadAt;
            t.maf = fresh(maf, mafAt, now, 3000); t.mafAt = mafAt;
            t.map = fresh(map, mapAt, now, 3000); t.mapAt = mapAt;
            t.timing = fresh(timing, timingAt, now, 3000); t.timingAt = timingAt;
            t.shortFuelTrim = fresh(shortFuelTrim, shortFuelTrimAt, now, 4000); t.shortFuelTrimAt = shortFuelTrimAt;
            t.longFuelTrim = fresh(longFuelTrim, longFuelTrimAt, now, 6000); t.longFuelTrimAt = longFuelTrimAt;
            t.coolant = fresh(coolant, coolantAt, now, 7000); t.coolantAt = coolantAt;
            t.intake = fresh(intake, intakeAt, now, 7000); t.intakeAt = intakeAt;
            t.voltage = fresh(voltage, voltageAt, now, 7000); t.voltageAt = voltageAt;
            t.adapterVoltage = fresh(adapterVoltage, adapterVoltageAt, now, 7000); t.adapterVoltageAt = adapterVoltageAt;
            t.fuelRate = fresh(fuelRate, fuelRateAt, now, 7000); t.fuelRateAt = fuelRateAt;
            t.fuel = fresh(fuel, fuelAt, now, 15000); t.fuelAt = fuelAt;
            return t;
        }

        private static float fresh(float value, long at, long now, long ttl) {
            return !Float.isNaN(value) && at > 0 && now - at <= ttl ? value : Float.NaN;
        }

        public void clearLiveValues() {
            rpm = speed = coolant = intake = throttle = load = maf = fuel = voltage = adapterVoltage =
                    map = timing = fuelRate = shortFuelTrim = longFuelTrim = Float.NaN;
            rpmAt = speedAt = coolantAt = intakeAt = throttleAt = loadAt = mafAt = fuelAt = voltageAt =
                    adapterVoltageAt = mapAt = timingAt = fuelRateAt = shortFuelTrimAt = longFuelTrimAt = 0L;
        }
    }

    public interface Listener {
        boolean hasBluetoothPermission();
        void onState(State state, String detail, Transport transport, String adapterName);
        void onDevices(List<DeviceInfo> devices);
        void onTelemetry(Telemetry telemetry);
        void onReadiness(Readiness readiness);
        void onDtc(DtcResult result);
    }

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF1 = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID FFE0 = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID FFE1 = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID NUS_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_WRITE = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_NOTIFY = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    // Vgate iCar Pro / VLink BLE profile observed on iCar Pro BLE 4.0 hardware.
    private static final UUID VGATE_SERVICE_18F0 = UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb");
    private static final UUID VGATE_NOTIFY_2AF0 = UUID.fromString("00002af0-0000-1000-8000-00805f9b34fb");
    private static final UUID VGATE_WRITE_2AF1 = UUID.fromString("00002af1-0000-1000-8000-00805f9b34fb");
    private static final UUID VGATE_VENDOR_SERVICE = UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2");
    private static final UUID VGATE_VENDOR_CHAR = UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f");

    private static final long BLE_SUBSCRIBE_TIMEOUT_MS = 4500L;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean sportPriority;
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final BluetoothAdapter bluetoothAdapter;
    private final SharedPreferences prefs;
    private final AtomicLong generation = new AtomicLong(0);
    private volatile Session current;
    private volatile State state = State.DISCONNECTED;
    private volatile Transport activeTransport = Transport.AUTO;
    private volatile String adapterName = "";

    private final DebugLog debugLog = new DebugLog(300);

    private static final class Session {
        final long id;
        final Transport transport;
        volatile boolean cancelled;
        volatile boolean verified;
        volatile boolean classicConnecting;
        BluetoothSocket btSocket;
        Socket wifiSocket;
        InputStream in;
        OutputStream out;
        BluetoothGatt gatt;
        BluetoothGattCharacteristic bleWrite;
        BluetoothGattCharacteristic bleNotify;
        BluetoothLeScanner scanner;
        ScanCallback scanCallback;
        BroadcastReceiver discoveryReceiver;
        boolean discoveryRegistered;
        boolean bleSubscribed;
        final Object bleRxLock = new Object();
        final StringBuilder bleRx = new StringBuilder();
        final Telemetry telemetry = new Telemetry();
        final Readiness readiness = new Readiness();
        final DtcResult dtc = new DtcResult();
        final Map<Integer, Long> nextDue = new HashMap<>();
        int consecutiveTransportFailures;
        int bleScanToken;
        String adapterName = "";
        String address = "";
        String wifiHost = "";
        int wifiPort;

        Session(long id, Transport transport) { this.id = id; this.transport = transport; }
    }

    public ObdManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.prefs = this.context.getSharedPreferences("civic_dashboard", Context.MODE_PRIVATE);
        this.debugLog.enabled = prefs.getBoolean("obd_debug", false);
    }

    public void setConnectionTimeoutMs(int ms) {
        int safe = Math.max(3000, Math.min(15000, ms));
        prefs.edit().putInt("connection_timeout_ms", safe).apply();
    }

    private int connectionTimeoutMs() {
        return Math.max(3000, Math.min(15000, prefs.getInt("connection_timeout_ms", 5000)));
    }

    public State getState() { return state; }
    public Transport getActiveTransport() { return activeTransport; }
    public String getAdapterName() { return adapterName; }

    // ---------------- Device discovery ----------------

    /** Returns paired Classic devices only; call scanClassicDevices() for active discovery. */
    public void refreshDevices() {
        List<DeviceInfo> result = pairedClassicDevices();
        postDevices(result);
    }

    public void scanClassicDevices() {
        if (!ensureBluetoothReady(Transport.BLUETOOTH)) return;
        final Session s = beginSession(Transport.BLUETOOTH);
        final Map<String, DeviceInfo> found = new LinkedHashMap<>();
        for (DeviceInfo d : pairedClassicDevices()) found.put(d.key(), d);
        postDevices(new ArrayList<>(found.values()));
        postState(s, State.SEARCHING, "Scanning Bluetooth devices", Transport.BLUETOOTH, "");

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (!isCurrent(s)) return;
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice d;
                    if (Build.VERSION.SDK_INT >= 33) d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    else d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d == null) return;
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    DeviceInfo info = new DeviceInfo(safeName(d), safeAddress(d), Transport.BLUETOOTH, isBonded(d), rssi);
                    found.put(info.key(), info);
                    postDevices(new ArrayList<>(found.values()));
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                    if (isCurrent(s)) postState(s, State.DISCONNECTED, "Bluetooth scan complete — choose a device", Transport.BLUETOOTH, "");
                    unregisterDiscoveryReceiver(s);
                }
            }
        };
        s.discoveryReceiver = receiver;
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(BluetoothDevice.ACTION_FOUND);
            f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(receiver, f);
            s.discoveryRegistered = true;
            bluetoothAdapter.cancelDiscovery();
            if (!bluetoothAdapter.startDiscovery()) {
                unregisterDiscoveryReceiver(s);
                postState(s, State.ERROR, "Bluetooth discovery could not start", Transport.BLUETOOTH, "");
            }
        } catch (SecurityException e) {
            unregisterDiscoveryReceiver(s);
            postState(s, State.PERMISSION_REQUIRED, "Bluetooth scan permission required", Transport.BLUETOOTH, "");
        }
    }

    private List<DeviceInfo> pairedClassicDevices() {
        List<DeviceInfo> result = new ArrayList<>();
        if (bluetoothAdapter == null || !listener.hasBluetoothPermission()) return result;
        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            if (bonded != null) for (BluetoothDevice d : bonded) {
                result.add(new DeviceInfo(safeName(d), safeAddress(d), Transport.BLUETOOTH, true));
            }
        } catch (SecurityException ignored) { }
        result.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.US)));
        return result;
    }

    /** BLE scan only discovers devices; it never auto-connects by name. */
    public void connectBleScan() { scanBleDevices(); }

    public void scanBleDevices() {
        if (!ensureBluetoothReady(Transport.BLE)) return;
        final Session s = beginSession(Transport.BLE);
        final Map<String, DeviceInfo> found = new LinkedHashMap<>();
        startBleScanAttempt(s, found, 0);
    }

    private void startBleScanAttempt(Session s, Map<String, DeviceInfo> found, int attempt) {
        if (!isCurrent(s) || s.cancelled) return;
        try {
            try { bluetoothAdapter.cancelDiscovery(); } catch (SecurityException ignored) { }
            s.scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (s.scanner == null) {
                postState(s, State.ERROR, "BLE scanner unavailable", Transport.BLE, "");
                return;
            }
            final int token = ++s.bleScanToken;
            postState(s, State.SEARCHING, attempt == 0 ? "Scanning BLE devices" : "Retrying BLE scan", Transport.BLE, "");
            s.scanCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    if (!isCurrent(s) || token != s.bleScanToken || result == null) return;
                    BluetoothDevice d = result.getDevice();
                    if (d == null) return;
                    DeviceInfo info = new DeviceInfo(safeName(d), safeAddress(d), Transport.BLE, isBonded(d), result.getRssi());
                    found.put(info.key(), info);
                    postDevices(new ArrayList<>(found.values()));
                }

                @Override public void onBatchScanResults(List<ScanResult> results) {
                    if (!isCurrent(s) || token != s.bleScanToken || results == null) return;
                    for (ScanResult result : results) onScanResult(0, result);
                }

                @Override public void onScanFailed(int errorCode) {
                    if (!isCurrent(s) || token != s.bleScanToken) return;
                    stopBleScan(s);
                    debugCore("BLE scan failed code=" + errorCode + " attempt=" + attempt);
                    // Error 2 is common on vendor head units when the BLE scanner registration
                    // is temporarily stuck. Retry the same session after a short cooldown.
                    if (errorCode == ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED && attempt < 2) {
                        main.postDelayed(() -> startBleScanAttempt(s, found, attempt + 1), 900L * (attempt + 1));
                    } else {
                        postState(s, State.ERROR, "BLE scan error " + errorCode, Transport.BLE, "");
                    }
                }
            };
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            s.scanner.startScan(null, settings, s.scanCallback);
            main.postDelayed(() -> {
                if (!isCurrent(s) || s.cancelled || token != s.bleScanToken) return;
                stopBleScan(s);
                postState(s, State.DISCONNECTED, found.isEmpty() ?
                        "BLE scan complete — no devices found" : "BLE scan complete — choose a device",
                        Transport.BLE, "");
            }, 12000L);
        } catch (SecurityException e) {
            postState(s, State.PERMISSION_REQUIRED, "Bluetooth scan permission required", Transport.BLE, "");
        } catch (Exception e) {
            postState(s, State.ERROR, "BLE scan could not start: " + cleanError(e), Transport.BLE, "");
        }
    }

    // ---------------- Connection entry points ----------------

    /**
     * AUTO is deliberately conservative: it uses only an adapter that previously completed a
     * real ECU handshake. Without one, the UI must ask the user to select an adapter.
     */
    public void connectAuto() {
        String transportName = prefs.getString("verified_transport", "");
        if (transportName.isEmpty()) {
            postState(null, State.DISCONNECTED, "No verified adapter saved — choose an adapter", Transport.AUTO, "");
            refreshDevices();
            return;
        }
        try {
            Transport t = Transport.valueOf(transportName);
            if (t == Transport.BLUETOOTH || t == Transport.BLE) {
                String address = prefs.getString("verified_address", "");
                String name = prefs.getString("verified_name", "Saved adapter");
                if (address.isEmpty()) {
                    postState(null, State.DISCONNECTED, "Saved adapter is incomplete — choose again", Transport.AUTO, "");
                    return;
                }
                connect(new DeviceInfo(name, address, t, t == Transport.BLUETOOTH));
            } else if (t == Transport.WIFI) {
                connectWifi(prefs.getString("verified_wifi_host", "192.168.0.10"), prefs.getInt("verified_wifi_port", 35000));
            } else {
                postState(null, State.DISCONNECTED, "Choose an adapter", Transport.AUTO, "");
            }
        } catch (IllegalArgumentException e) {
            postState(null, State.DISCONNECTED, "Choose an adapter", Transport.AUTO, "");
        }
    }

    public void connect(DeviceInfo device) {
        if (device == null) return;
        if (device.transport == Transport.BLUETOOTH) {
            if (!ensureBluetoothReady(Transport.BLUETOOTH)) return;
            Session s = beginSession(Transport.BLUETOOTH);
            s.adapterName = device.name;
            s.address = device.address;
            io.execute(() -> connectClassicBlocking(s, device));
        } else if (device.transport == Transport.BLE) {
            if (!ensureBluetoothReady(Transport.BLE)) return;
            Session s = beginSession(Transport.BLE);
            s.adapterName = device.name;
            s.address = device.address;
            connectBleByAddress(s, device);
        }
    }

    public void connectBluetoothByAddress(String address) {
        if (address == null || address.trim().isEmpty() || bluetoothAdapter == null) return;
        if (!ensureBluetoothReady(Transport.BLUETOOTH)) return;
        try {
            BluetoothDevice d = bluetoothAdapter.getRemoteDevice(address);
            connect(new DeviceInfo(safeName(d), address, Transport.BLUETOOTH, isBonded(d)));
        } catch (Exception e) {
            postState(null, State.ERROR, "Bluetooth device unavailable", Transport.BLUETOOTH, "");
        }
    }

    public void connectWifi(String host, int port) {
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535) {
            postState(null, State.ERROR, "Invalid Wi-Fi endpoint", Transport.WIFI, "");
            return;
        }
        Session s = beginSession(Transport.WIFI);
        s.adapterName = "Wi-Fi ELM327";
        s.wifiHost = host.trim();
        s.wifiPort = port;
        postState(s, State.SEARCHING, "Opening " + s.wifiHost + ":" + port, Transport.WIFI, s.adapterName);
        io.execute(() -> {
            try {
                Socket socket = new Socket();
                s.wifiSocket = socket; // publish before blocking connect so cancellation can close it
                socket.connect(new InetSocketAddress(s.wifiHost, s.wifiPort), connectionTimeoutMs());
                if (!isCurrent(s)) return;
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(1000);
                s.in = socket.getInputStream();
                s.out = socket.getOutputStream();
                postState(s, State.ADAPTER_FOUND, s.wifiHost + ":" + s.wifiPort, Transport.WIFI, s.adapterName);
                runElmSession(s);
            } catch (Exception e) {
                if (isCurrent(s) && !s.cancelled) failSession(s, "Wi-Fi adapter not reachable");
            } finally {
                closeSessionResources(s);
            }
        });
    }

    /** Higher-rate polling profile while the Sport dashboard is visible. */
    public void setSportPriority(boolean enabled) {
        sportPriority = enabled;
    }

    public void disconnect() {
        Session old = current;
        if (old != null) cancelSession(old);
        current = null;
        clearPublicData();
        postState(null, State.DISCONNECTED, "Tap OBD Connection to connect", Transport.AUTO, "");
    }

    /** Called when Activity goes to background; keeps preferences but closes all hardware links. */
    public void suspend() {
        Session old = current;
        if (old != null) cancelSession(old);
        current = null;
        clearPublicData();
        postState(null, State.DISCONNECTED, "Paused", Transport.AUTO, "");
    }

    public void shutdown() {
        suspend();
        io.shutdownNow();
    }

    // ---------------- Classic Bluetooth ----------------

    private void connectClassicBlocking(Session s, DeviceInfo info) {
        postState(s, State.ADAPTER_FOUND, info.name, Transport.BLUETOOTH, info.name);
        postState(s, State.CONNECTING, "Opening Bluetooth serial link", Transport.BLUETOOTH, info.name);
        try {
            BluetoothDevice d = bluetoothAdapter.getRemoteDevice(info.address);
            try { bluetoothAdapter.cancelDiscovery(); } catch (SecurityException ignored) { }
            unregisterDiscoveryReceiver(s);

            Exception secureFailure = null;
            try {
                BluetoothSocket socket = d.createRfcommSocketToServiceRecord(SPP_UUID);
                s.btSocket = socket; // store before connect() so timeout/disconnect can cancel it
                s.classicConnecting = true;
                scheduleClassicConnectTimeout(s, socket);
                socket.connect();
                s.classicConnecting = false;
            } catch (Exception e) {
                secureFailure = e;
                closeQuietly(s.btSocket);
                s.btSocket = null;
            }
            if (!isCurrent(s) || s.cancelled) return;
            if (s.btSocket == null || !s.btSocket.isConnected()) {
                BluetoothSocket socket = d.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                s.btSocket = socket;
                s.classicConnecting = true;
                scheduleClassicConnectTimeout(s, socket);
                socket.connect();
                s.classicConnecting = false;
            }
            if (!isCurrent(s) || s.cancelled) return;
            s.in = s.btSocket.getInputStream();
            s.out = s.btSocket.getOutputStream();
            runElmSession(s);
        } catch (Exception e) {
            if (isCurrent(s) && !s.cancelled) {
                String message = "Could not open Bluetooth serial link";
                if (e.getMessage() != null && !e.getMessage().isEmpty()) message += ": " + e.getMessage();
                failSession(s, message);
            }
        } finally {
            closeSessionResources(s);
        }
    }

    private void scheduleClassicConnectTimeout(Session s, BluetoothSocket socket) {
        main.postDelayed(() -> {
            if (!isCurrent(s) || s.cancelled || !s.classicConnecting || s.btSocket != socket) return;
            debug("Classic connect timeout");
            closeQuietly(socket);
        }, connectionTimeoutMs());
    }

    // ---------------- BLE ----------------

    private void connectBleByAddress(Session s, DeviceInfo info) {
        postState(s, State.ADAPTER_FOUND, info.name, Transport.BLE, info.name);
        postState(s, State.CONNECTING, "Opening BLE UART service", Transport.BLE, info.name);
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(info.address);
            BluetoothGattCallback cb = new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
                    if (!isCurrent(s)) { safeCloseGatt(g); return; }
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failSession(s, "BLE connection error " + status);
                        return;
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            try { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH); } catch (Exception ignored) { }
                            if (!g.discoverServices()) failSession(s, "BLE service discovery could not start");
                        } catch (SecurityException e) {
                            failSession(s, "Bluetooth permission required");
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !s.cancelled) {
                        failSession(s, "BLE adapter disconnected");
                    }
                }

                @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
                    if (!isCurrent(s)) return;
                    logGattLayout(g);
                    if (status != BluetoothGatt.GATT_SUCCESS || !selectBleUart(s, g)) {
                        failSession(s, "Supported BLE UART profile not found — open View Log");
                        return;
                    }
                    if (!startBleSubscription(s, g)) {
                        failSession(s, "Could not enable BLE notifications");
                        return;
                    }
                    main.postDelayed(() -> {
                        if (isCurrent(s) && !s.cancelled && !s.bleSubscribed) {
                            failSession(s, "BLE notification subscription timed out");
                        }
                    }, BLE_SUBSCRIBE_TIMEOUT_MS);
                }

                @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
                    if (!isCurrent(s) || s.cancelled) return;
                    if (!CCCD_UUID.equals(descriptor.getUuid())) return;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failSession(s, "BLE notification subscription failed: " + status);
                        return;
                    }
                    s.bleSubscribed = true;
                    debug("BLE CCCD subscribed");
                    io.execute(() -> {
                        try { runElmSession(s); }
                        finally { closeSessionResources(s); }
                    });
                }

                @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
                    if (characteristic == null) return;
                    appendBleRx(s, characteristic.getValue());
                }

                @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic, byte[] value) {
                    appendBleRx(s, value);
                }
            };
            if (Build.VERSION.SDK_INT >= 23) s.gatt = device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE);
            else s.gatt = device.connectGatt(context, false, cb);
            if (s.gatt == null) failSession(s, "Could not create BLE GATT connection");
        } catch (SecurityException e) {
            failSession(s, "Bluetooth permission required");
        } catch (IllegalArgumentException e) {
            failSession(s, "BLE device address is invalid");
        }
    }

    private void appendBleRx(Session s, byte[] data) {
        if (!isCurrent(s) || data == null || data.length == 0) return;
        synchronized (s.bleRxLock) {
            s.bleRx.append(new String(data, StandardCharsets.US_ASCII));
            s.bleRxLock.notifyAll();
        }
    }

    /** Select only known UART layouts and keep write/notify inside the same service. */
    private boolean selectBleUart(Session s, BluetoothGatt g) {
        // Vgate iCar Pro BLE 4.0 / VLink profile: 18F0 service, 2AF0 notify, 2AF1 write.
        if (selectBlePair(s, g.getService(VGATE_SERVICE_18F0), VGATE_WRITE_2AF1, VGATE_NOTIFY_2AF0)) {
            debugCore("BLE profile selected: Vgate 18F0/2AF0/2AF1");
            return true;
        }
        // Some Vgate firmware exposes a single vendor characteristic with write + notify/indicate.
        if (selectSingleBleCharacteristic(s, g.getService(VGATE_VENDOR_SERVICE), VGATE_VENDOR_CHAR)) {
            debugCore("BLE profile selected: Vgate vendor single characteristic");
            return true;
        }
        if (selectBlePair(s, g.getService(FFF0), FFF1, FFF2)) { debugCore("BLE profile selected: FFF0"); return true; }
        if (selectSingleBleCharacteristic(s, g.getService(FFE0), FFE1)) { debugCore("BLE profile selected: FFE0/FFE1"); return true; }
        BluetoothGattService nus = g.getService(NUS_SERVICE);
        if (nus != null) {
            BluetoothGattCharacteristic w = nus.getCharacteristic(NUS_WRITE);
            BluetoothGattCharacteristic n = nus.getCharacteristic(NUS_NOTIFY);
            if (isWritable(w) && isNotifiableWithCccd(n)) {
                s.bleWrite = w; s.bleNotify = n; debugCore("BLE profile selected: Nordic UART"); return true;
            }
        }
        return false;
    }

    private void logGattLayout(BluetoothGatt g) {
        if (g == null) return;
        try {
            for (BluetoothGattService service : g.getServices()) {
                debugCore("GATT service " + service.getUuid());
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    boolean hasCccd = c.getDescriptor(CCCD_UUID) != null;
                    debugCore("  char " + c.getUuid() + " props=0x" + Integer.toHexString(c.getProperties()) + " cccd=" + hasCccd);
                }
            }
        } catch (Exception e) {
            debugCore("GATT layout read failed: " + e.getClass().getSimpleName());
        }
    }

    private boolean selectBlePair(Session s, BluetoothGattService service, UUID aId, UUID bId) {
        if (service == null) return false;
        BluetoothGattCharacteristic a = service.getCharacteristic(aId);
        BluetoothGattCharacteristic b = service.getCharacteristic(bId);
        if (isWritable(a) && isNotifiableWithCccd(b)) { s.bleWrite = a; s.bleNotify = b; return true; }
        if (isWritable(b) && isNotifiableWithCccd(a)) { s.bleWrite = b; s.bleNotify = a; return true; }
        return false;
    }

    private boolean selectSingleBleCharacteristic(Session s, BluetoothGattService service, UUID id) {
        if (service == null) return false;
        BluetoothGattCharacteristic c = service.getCharacteristic(id);
        if (isWritable(c) && isNotifiableWithCccd(c)) { s.bleWrite = c; s.bleNotify = c; return true; }
        return false;
    }

    private boolean isWritable(BluetoothGattCharacteristic c) {
        if (c == null) return false;
        int p = c.getProperties();
        return (p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
    }

    private boolean isNotifiableWithCccd(BluetoothGattCharacteristic c) {
        if (c == null || c.getDescriptor(CCCD_UUID) == null) return false;
        int p = c.getProperties();
        return (p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
    }

    private boolean startBleSubscription(Session s, BluetoothGatt g) {
        try {
            if (!g.setCharacteristicNotification(s.bleNotify, true)) return false;
            BluetoothGattDescriptor cccd = s.bleNotify.getDescriptor(CCCD_UUID);
            if (cccd == null) return false;
            boolean indicate = (s.bleNotify.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 &&
                    (s.bleNotify.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0;
            cccd.setValue(indicate ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            return g.writeDescriptor(cccd);
        } catch (SecurityException e) {
            return false;
        }
    }

    // ---------------- ELM session ----------------

    private void runElmSession(Session s) {
        if (!isCurrent(s) || s.cancelled) return;
        try {
            postState(s, State.INITIALIZING, "Initializing ELM327", s.transport, s.adapterName);
            resetSessionData(s);
            String atz = command(s, "ATZ", 2800, 1);
            if (!looksLikeElm(atz)) throw new IllegalStateException("ELM327 adapter did not answer ATZ");
            command(s, "ATE0", 1300, 1);
            command(s, "ATL0", 1300, 1);
            command(s, "ATS0", 1300, 1);
            command(s, "ATH0", 1300, 1);
            command(s, "ATAT1", 1300, 1);
            if (prefs.getBoolean("auto_detect_protocol", true)) {
                command(s, "ATSP0", 1800, 1);
            }
            s.telemetry.adapterVersion = compactAtText(command(s, "ATI", 1600, 1));

            postState(s, State.ECU_CONNECTING, "Waiting for ECU response", s.transport, s.adapterName);
            ObdProtocol.ParsedResponse handshake = null;
            long deadline = System.currentTimeMillis() + 12000L;
            while (isCurrent(s) && !s.cancelled && System.currentTimeMillis() < deadline) {
                String raw = command(s, "0100", 2000, 0);
                ObdProtocol.ParsedResponse parsed = ObdProtocol.parse(raw, "0100");
                byte[] p = ObdProtocol.mode01Payload(parsed, 0x00, 4);
                if (p != null && p.length >= 4) { handshake = parsed; break; }
                sleep(250);
            }
            if (handshake == null) throw new IllegalStateException("Adapter connected, ECU did not answer with valid 41 00 bitmap");

            discoverSupportedPids(s, handshake);
            s.telemetry.protocol = compactAtText(command(s, "ATDP", 1600, 1));
            s.verified = true;
            saveVerifiedAdapter(s);
            postState(s, State.ECU_CONNECTED, "Live ECU data", s.transport, s.adapterName);
            postTelemetry(s);
            schedulerLoop(s);
        } catch (Exception e) {
            if (isCurrent(s) && !s.cancelled) failSession(s, cleanError(e));
        }
    }

    private void discoverSupportedPids(Session s, ObdProtocol.ParsedResponse first) throws Exception {
        Set<Integer> supported = s.telemetry.supportedPids;
        supported.clear();
        int base = 0x00;
        ObdProtocol.ParsedResponse parsed = first;
        while (base <= 0xC0 && isCurrent(s) && !s.cancelled) {
            byte[] payload = ObdProtocol.mode01Payload(parsed, base, 4);
            if (payload == null || payload.length < 4) throw new IllegalStateException("Invalid supported-PID bitmap at " + hex2(base));
            supported.addAll(ObdProtocol.decodeSupportedPids(base, payload));
            int continuationPid = base + 0x20;
            if (!supported.contains(continuationPid) || continuationPid > 0xC0) break;
            base = continuationPid;
            String cmd = "01" + hex2(base);
            parsed = ObdProtocol.parse(command(s, cmd, 1800, 1), cmd);
        }
        s.telemetry.capabilitiesKnown = true;
        debug("Supported PIDs: " + supported);
    }

    private static final class PollDef {
        final int pid;
        final long normalIntervalMs;
        final long sportIntervalMs;
        PollDef(int pid, long normalIntervalMs, long sportIntervalMs) {
            this.pid = pid;
            this.normalIntervalMs = normalIntervalMs;
            this.sportIntervalMs = sportIntervalMs;
        }
        long interval(boolean sport) { return sport ? sportIntervalMs : normalIntervalMs; }
    }

    private static final PollDef[] POLLS = new PollDef[]{
            new PollDef(0x0C, 140, 90),   // RPM
            new PollDef(0x0D, 220, 140),  // speed
            new PollDef(0x11, 200, 120),  // throttle
            new PollDef(0x04, 260, 160),  // load
            new PollDef(0x10, 650, 450),  // MAF
            new PollDef(0x0B, 700, 450),  // MAP
            new PollDef(0x0E, 750, 500),  // timing
            new PollDef(0x06, 1200, 900), // STFT
            new PollDef(0x07, 1600, 1300),// LTFT
            new PollDef(0x05, 1600, 1200),// coolant
            new PollDef(0x0F, 1900, 1300),// intake
            new PollDef(0x42, 2200, 1500),// module voltage
            new PollDef(0x5E, 2500, 1800),// fuel rate
            new PollDef(0x2F, 6500, 5000) // fuel level
    };

    private void schedulerLoop(Session s) throws Exception {
        long now = System.currentTimeMillis();
        for (PollDef p : POLLS) s.nextDue.put(p.pid, now);
        long readinessDue = now + 1200;
        long dtcDue = now + 2500;
        long adapterVoltageDue = now + 2500;
        long telemetryPublishDue = now;

        while (isCurrent(s) && !s.cancelled) {
            now = System.currentTimeMillis();
            PollDef due = null;
            long earliest = Long.MAX_VALUE;
            // Fair scheduler: service the oldest due PID instead of always preferring array order.
            for (PollDef p : POLLS) {
                if (!s.telemetry.supportedPids.contains(p.pid)) continue;
                long at = s.nextDue.getOrDefault(p.pid, now);
                if (at < earliest) { earliest = at; due = p; }
            }
            if (due != null && earliest <= now) {
                pollPid(s, due.pid);
                long mult = prefs.getBoolean("low_power_mode", false) ? 2L : 1L;
                long afterPoll = System.currentTimeMillis();
                s.nextDue.put(due.pid, afterPoll + due.interval(sportPriority) * mult);
                // Up to ~30 Hz telemetry delivery when the adapter and ECU can sustain it.
                if (afterPoll >= telemetryPublishDue) {
                    postTelemetry(s);
                    telemetryPublishDue = afterPoll + 33L;
                }
                continue;
            }
            if (now >= readinessDue && s.telemetry.supportedPids.contains(0x01)) {
                pollReadiness(s);
                readinessDue = System.currentTimeMillis() + (prefs.getBoolean("low_power_mode", false) ? 20000L : 10000L);
                continue;
            }
            if (now >= dtcDue) {
                pollDtcs(s);
                dtcDue = System.currentTimeMillis() + (prefs.getBoolean("low_power_mode", false) ? 120000L : 60000L);
                continue;
            }
            if (now >= adapterVoltageDue) {
                pollAdapterVoltage(s);
                adapterVoltageDue = System.currentTimeMillis() + (prefs.getBoolean("low_power_mode", false) ? 10000L : 5000L);
            }
            if (now >= telemetryPublishDue) {
                postTelemetry(s); // expires stale fields without flooding the main thread
                telemetryPublishDue = now + 33L;
            }
            long sleep = earliest == Long.MAX_VALUE ? 40L : Math.max(8L, Math.min(40L, earliest - now));
            sleep(sleep);
        }
    }

    private void pollPid(Session s, int pid) throws Exception {
        if (!s.telemetry.supportedPids.contains(pid)) return;
        String cmd = "01" + hex2(pid);
        String raw = command(s, cmd, 1100, 0);
        ObdProtocol.ParsedResponse parsed = ObdProtocol.parse(raw, cmd);
        if (parsed.status == ObdProtocol.TextStatus.NO_DATA) return; // temporary; freshness TTL handles display
        if (parsed.status == ObdProtocol.TextStatus.STOPPED || parsed.status == ObdProtocol.TextStatus.UNABLE_TO_CONNECT) {
            if (++s.consecutiveTransportFailures >= 2) throw new IllegalStateException("ECU communication lost");
            return;
        }
        byte[] data = ObdProtocol.mode01Payload(parsed, pid, minPayloadBytes(pid));
        if (data == null) return;
        s.consecutiveTransportFailures = 0;
        long at = System.currentTimeMillis();
        switch (pid) {
            case 0x0C: s.telemetry.rpm = ((data[0]&0xff)*256f + (data[1]&0xff))/4f; s.telemetry.rpmAt = at; break;
            case 0x0D: s.telemetry.speed = data[0]&0xff; s.telemetry.speedAt = at; break;
            case 0x05: s.telemetry.coolant = (data[0]&0xff)-40f; s.telemetry.coolantAt = at; break;
            case 0x11: s.telemetry.throttle = (data[0]&0xff)*100f/255f; s.telemetry.throttleAt = at; break;
            case 0x04: s.telemetry.load = (data[0]&0xff)*100f/255f; s.telemetry.loadAt = at; break;
            case 0x0F: s.telemetry.intake = (data[0]&0xff)-40f; s.telemetry.intakeAt = at; break;
            case 0x10: s.telemetry.maf = ((data[0]&0xff)*256f + (data[1]&0xff))/100f; s.telemetry.mafAt = at; break;
            case 0x0B: s.telemetry.map = data[0]&0xff; s.telemetry.mapAt = at; break;
            case 0x0E: s.telemetry.timing = (data[0]&0xff)/2f - 64f; s.telemetry.timingAt = at; break;
            case 0x2F: s.telemetry.fuel = (data[0]&0xff)*100f/255f; s.telemetry.fuelAt = at; break;
            case 0x42: s.telemetry.voltage = ((data[0]&0xff)*256f + (data[1]&0xff))/1000f; s.telemetry.voltageAt = at; break;
            case 0x5E: s.telemetry.fuelRate = ((data[0]&0xff)*256f + (data[1]&0xff))/20f; s.telemetry.fuelRateAt = at; break;
            case 0x06: s.telemetry.shortFuelTrim = (data[0]&0xff)*100f/128f - 100f; s.telemetry.shortFuelTrimAt = at; break;
            case 0x07: s.telemetry.longFuelTrim = (data[0]&0xff)*100f/128f - 100f; s.telemetry.longFuelTrimAt = at; break;
        }
        sanityCheck(s.telemetry);
        debug("PID " + hex2(pid) + " <= " + summarize(raw));
    }

    private int minPayloadBytes(int pid) {
        switch (pid) {
            case 0x0C: case 0x10: case 0x42: case 0x5E: return 2;
            default: return 1;
        }
    }

    private void sanityCheck(Telemetry t) {
        if (!Float.isNaN(t.rpm) && (t.rpm < 0 || t.rpm > 10000)) t.rpm = Float.NaN;
        if (!Float.isNaN(t.speed) && (t.speed < 0 || t.speed > 255)) t.speed = Float.NaN;
        if (!Float.isNaN(t.coolant) && (t.coolant < -40 || t.coolant > 215)) t.coolant = Float.NaN;
        if (!Float.isNaN(t.intake) && (t.intake < -40 || t.intake > 215)) t.intake = Float.NaN;
        if (!Float.isNaN(t.voltage) && (t.voltage < 0 || t.voltage > 30)) t.voltage = Float.NaN;
    }

    private void pollAdapterVoltage(Session s) {
        try {
            String raw = command(s, "ATRV", 1200, 0);
            Float v = parseAdapterVoltage(raw);
            if (v != null) { s.telemetry.adapterVoltage = v; s.telemetry.adapterVoltageAt = System.currentTimeMillis(); }
        } catch (Exception ignored) { }
    }

    private Float parseAdapterVoltage(String raw) {
        if (raw == null) return null;
        String[] lines = raw.replace('>', '\n').replace('\r', '\n').split("\\n+");
        for (String line : lines) {
            String s = line.trim().toUpperCase(Locale.US);
            if (s.equals("ATRV") || !s.endsWith("V")) continue;
            try {
                float v = Float.parseFloat(s.substring(0, s.length()-1).trim());
                if (v >= 5f && v <= 20f) return v;
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private void pollReadiness(Session s) {
        try {
            String raw = command(s, "0101", 1300, 0);
            byte[] d = ObdProtocol.mode01Payload(ObdProtocol.parse(raw, "0101"), 0x01, 4);
            if (d == null || d.length < 4) return;
            parseReadiness(s.readiness, d);
            postReadiness(s);
        } catch (Exception e) {
            debug("Readiness error: " + e.getMessage());
        }
    }

    /** PID 01 readiness: B support/incomplete for continuous, C support, D incomplete. */
    static void parseReadiness(Readiness r, byte[] d) {
        int a = d[0]&0xff, b = d[1]&0xff, c = d[2]&0xff, dd = d[3]&0xff;
        r.available = true;
        r.milOn = (a & 0x80) != 0;
        r.dtcCount = a & 0x7f;
        r.compressionIgnition = (b & 0x08) != 0;
        r.monitors.clear();
        putMonitor(r, "Misfire", (b & 0x01) != 0, (b & 0x10) != 0);
        putMonitor(r, "Fuel System", (b & 0x02) != 0, (b & 0x20) != 0);
        putMonitor(r, "Components", (b & 0x04) != 0, (b & 0x40) != 0);
        if (!r.compressionIgnition) {
            putMonitor(r, "Catalyst", (c & 0x01) != 0, (dd & 0x01) != 0);
            putMonitor(r, "Heated Catalyst", (c & 0x02) != 0, (dd & 0x02) != 0);
            putMonitor(r, "Evaporative System", (c & 0x04) != 0, (dd & 0x04) != 0);
            putMonitor(r, "Secondary Air", (c & 0x08) != 0, (dd & 0x08) != 0);
            putMonitor(r, "A/C Refrigerant", (c & 0x10) != 0, (dd & 0x10) != 0);
            putMonitor(r, "O2 Sensor", (c & 0x20) != 0, (dd & 0x20) != 0);
            putMonitor(r, "O2 Sensor Heater", (c & 0x40) != 0, (dd & 0x40) != 0);
            putMonitor(r, "EGR/VVT", (c & 0x80) != 0, (dd & 0x80) != 0);
        } else {
            putMonitor(r, "NMHC Catalyst", (c & 0x01) != 0, (dd & 0x01) != 0);
            putMonitor(r, "NOx/SCR", (c & 0x02) != 0, (dd & 0x02) != 0);
            putMonitor(r, "Boost Pressure", (c & 0x08) != 0, (dd & 0x08) != 0);
            putMonitor(r, "Exhaust Gas Sensor", (c & 0x20) != 0, (dd & 0x20) != 0);
            putMonitor(r, "PM Filter", (c & 0x40) != 0, (dd & 0x40) != 0);
            putMonitor(r, "EGR/VVT", (c & 0x80) != 0, (dd & 0x80) != 0);
        }
    }

    private static void putMonitor(Readiness r, String name, boolean supported, boolean incomplete) {
        r.monitors.put(name, !supported ? MonitorState.UNSUPPORTED : incomplete ? MonitorState.INCOMPLETE : MonitorState.COMPLETE);
    }

    private void pollDtcs(Session s) {
        DtcResult target = s.dtc;
        try {
            String raw = command(s, "03", 1800, 0);
            ObdProtocol.DtcParseResult parsed = ObdProtocol.parseMode03(ObdProtocol.parse(raw, "03"));
            target.codes.clear();
            if (!parsed.validMode03Response) {
                target.status = DtcStatus.ERROR;
                target.detail = "Stored DTC response was not valid";
            } else if (parsed.codes.isEmpty()) {
                target.status = DtcStatus.NO_CODES;
                target.detail = "Stored DTC read successfully";
            } else {
                target.status = DtcStatus.HAS_CODES;
                target.codes.addAll(parsed.codes);
                target.detail = parsed.codes.size() + " stored code(s)";
            }
        } catch (Exception e) {
            target.status = DtcStatus.ERROR;
            target.detail = "Stored DTC read failed";
            target.codes.clear();
        }
        postDtc(s);
    }

    // ---------------- Strict command queue ----------------

    private String command(Session s, String cmd, long timeoutMs, int retries) throws Exception {
        int attempts = 0;
        while (true) {
            if (!isCurrent(s) || s.cancelled) throw new InterruptedException("Disconnected");
            try {
                String response = s.transport == Transport.BLE ? bleCommandOnce(s, cmd, timeoutMs) : streamCommandOnce(s, cmd, timeoutMs);
                debug("> " + cmd + " | < " + summarize(response));
                return response;
            } catch (CommandTimeoutException e) {
                if (attempts++ >= retries) throw e;
                debug("Timeout; resynchronizing before retry " + cmd);
                resync(s);
            }
        }
    }

    private String streamCommandOnce(Session s, String cmd, long timeoutMs) throws Exception {
        if (s.out == null || s.in == null) throw new IllegalStateException("OBD link is not open");
        drainInput(s.in);
        s.out.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        s.out.flush();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (isCurrent(s) && !s.cancelled && System.currentTimeMillis() < deadline) {
            int available = s.in.available();
            if (available > 0) {
                byte[] buffer = new byte[Math.min(available, 512)];
                int n = s.in.read(buffer);
                if (n < 0) throw new IllegalStateException("OBD stream closed");
                if (n > 0) {
                    bytes.write(buffer, 0, n);
                    String text = bytes.toString(StandardCharsets.US_ASCII.name());
                    if (text.indexOf('>') >= 0) return text;
                }
            } else sleep(10);
        }
        throw new CommandTimeoutException("Timeout waiting for prompt after " + cmd);
    }

    private String bleCommandOnce(Session s, String cmd, long timeoutMs) throws Exception {
        if (s.gatt == null || s.bleWrite == null || !s.bleSubscribed) throw new IllegalStateException("BLE link is not ready");
        synchronized (s.bleRxLock) { s.bleRx.setLength(0); }
        byte[] data = (cmd + "\r").getBytes(StandardCharsets.US_ASCII);
        try {
            s.bleWrite.setValue(data);
            int props = s.bleWrite.getProperties();
            if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                s.bleWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            else s.bleWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (!s.gatt.writeCharacteristic(s.bleWrite)) throw new IllegalStateException("BLE write failed");
        } catch (SecurityException e) {
            throw new IllegalStateException("Bluetooth permission required");
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (s.bleRxLock) {
            while (isCurrent(s) && !s.cancelled && System.currentTimeMillis() < deadline) {
                if (s.bleRx.indexOf(">") >= 0) return s.bleRx.toString();
                try { s.bleRxLock.wait(40L); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
            }
        }
        throw new CommandTimeoutException("Timeout waiting for prompt after " + cmd);
    }

    private void resync(Session s) throws Exception {
        if (s.transport == Transport.BLE) {
            synchronized (s.bleRxLock) { s.bleRx.setLength(0); }
        } else if (s.in != null) {
            drainInput(s.in);
        }
        sleep(120);
    }

    private static final class CommandTimeoutException extends Exception {
        CommandTimeoutException(String message) { super(message); }
    }

    // ---------------- Session/lifecycle ----------------

    private Session beginSession(Transport transport) {
        Session old = current;
        if (old != null) cancelSession(old);
        Session s = new Session(generation.incrementAndGet(), transport);
        current = s;
        activeTransport = transport;
        return s;
    }

    private boolean belongsToCurrent(Session s) { return s != null && current == s && generation.get() == s.id; }
    private boolean isCurrent(Session s) { return belongsToCurrent(s) && !s.cancelled; }

    private void cancelSession(Session s) {
        if (s == null || s.cancelled) return;
        s.cancelled = true;
        try { if (bluetoothAdapter != null) bluetoothAdapter.cancelDiscovery(); } catch (SecurityException ignored) { }
        stopBleScan(s);
        unregisterDiscoveryReceiver(s);
        closeSessionResources(s);
        synchronized (s.bleRxLock) { s.bleRxLock.notifyAll(); }
    }

    private void closeSessionResources(Session s) {
        if (s == null) return;
        stopBleScan(s);
        unregisterDiscoveryReceiver(s);
        closeQuietly(s.in); s.in = null;
        closeQuietly(s.out); s.out = null;
        closeQuietly(s.btSocket); s.btSocket = null;
        closeQuietly(s.wifiSocket); s.wifiSocket = null;
        safeCloseGatt(s.gatt); s.gatt = null;
        s.bleSubscribed = false;
        s.bleWrite = null;
        s.bleNotify = null;
    }

    private void failSession(Session s, String message) {
        if (!isCurrent(s)) return;
        debug("ERROR: " + message);
        s.telemetry.clearLiveValues();
        s.readiness.available = false;
        s.dtc.status = DtcStatus.ERROR;
        s.dtc.detail = message;
        postTelemetry(s);
        postReadiness(s);
        postDtc(s);
        postState(s, State.ERROR, message, s.transport, s.adapterName);
        cancelSession(s);
    }

    private void clearPublicData() {
        Session temp = new Session(-1, Transport.AUTO);
        temp.telemetry.clearLiveValues();
        temp.readiness.available = false;
        temp.dtc.status = DtcStatus.NOT_READ;
        main.post(() -> {
            listener.onTelemetry(temp.telemetry.copyFresh(System.currentTimeMillis()));
            listener.onReadiness(temp.readiness.copy());
            listener.onDtc(temp.dtc.copy());
        });
    }

    private void resetSessionData(Session s) {
        s.telemetry.clearLiveValues();
        s.telemetry.supportedPids.clear();
        s.telemetry.capabilitiesKnown = false;
        s.telemetry.protocol = "";
        s.telemetry.adapterVersion = "";
        s.readiness.available = false;
        s.readiness.monitors.clear();
        s.dtc.status = DtcStatus.NOT_READ;
        s.dtc.codes.clear();
        s.dtc.detail = "Not read";
        postTelemetry(s);
        postReadiness(s);
        postDtc(s);
    }

    private void saveVerifiedAdapter(Session s) {
        SharedPreferences.Editor e = prefs.edit()
                .putString("verified_transport", s.transport.name())
                .putString("verified_name", s.adapterName == null ? "" : s.adapterName);
        if (s.transport == Transport.BLUETOOTH || s.transport == Transport.BLE) e.putString("verified_address", s.address);
        if (s.transport == Transport.WIFI) e.putString("verified_wifi_host", s.wifiHost).putInt("verified_wifi_port", s.wifiPort);
        e.apply();
    }

    // ---------------- Debug logging ----------------

    public void setDebugLoggingEnabled(boolean enabled) {
        debugLog.enabled = enabled;
        prefs.edit().putBoolean("obd_debug", enabled).apply();
    }

    public String getDebugLogText() { return debugLog.dump(); }

    public File exportDebugLog() throws Exception {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File file = new File(base, "civic-obd-debug.log");
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(debugLog.dump().getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private void debug(String line) { debugLog.add(line); }
    private void debugCore(String line) { debugLog.addAlways(line); }

    private static final class DebugLog {
        final int maxLines;
        boolean enabled;
        final ArrayList<String> lines = new ArrayList<>();
        DebugLog(int maxLines) { this.maxLines = maxLines; }
        synchronized void add(String s) {
            if (!enabled) return;
            addAlways(s);
        }
        synchronized void addAlways(String s) {
            lines.add(System.currentTimeMillis() + " " + s);
            while (lines.size() > maxLines) lines.remove(0);
        }
        synchronized String dump() {
            StringBuilder b = new StringBuilder();
            for (String s : lines) b.append(s).append('\n');
            return b.toString();
        }
    }

    // ---------------- Utilities ----------------

    private boolean ensureBluetoothReady(Transport transport) {
        if (!listener.hasBluetoothPermission()) {
            postState(null, State.PERMISSION_REQUIRED, "Bluetooth permission required", transport, "");
            return false;
        }
        if (bluetoothAdapter == null) {
            postState(null, State.ERROR, "Bluetooth is not available on this device", transport, "");
            return false;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                postState(null, State.ERROR, "Turn Bluetooth on", transport, "");
                return false;
            }
        } catch (SecurityException e) {
            postState(null, State.PERMISSION_REQUIRED, "Bluetooth permission required", transport, "");
            return false;
        }
        return true;
    }

    private void stopBleScan(Session s) {
        if (s == null) return;
        try { if (s.scanner != null && s.scanCallback != null) s.scanner.stopScan(s.scanCallback); }
        catch (SecurityException ignored) { }
        s.scanCallback = null;
        s.scanner = null;
    }

    private void unregisterDiscoveryReceiver(Session s) {
        if (s == null || !s.discoveryRegistered || s.discoveryReceiver == null) return;
        try { context.unregisterReceiver(s.discoveryReceiver); } catch (Exception ignored) { }
        s.discoveryRegistered = false;
        s.discoveryReceiver = null;
    }

    private boolean isBonded(BluetoothDevice d) {
        try { return d.getBondState() == BluetoothDevice.BOND_BONDED; }
        catch (SecurityException e) { return false; }
    }

    private String safeName(BluetoothDevice d) {
        try { String n = d.getName(); return n == null ? "Bluetooth adapter" : n; }
        catch (SecurityException e) { return "Bluetooth adapter"; }
    }

    private String safeAddress(BluetoothDevice d) {
        try { return d.getAddress(); } catch (SecurityException e) { return ""; }
    }

    private void drainInput(InputStream input) {
        try {
            while (input != null && input.available() > 0) {
                byte[] b = new byte[Math.min(256, input.available())];
                if (input.read(b) <= 0) break;
            }
        } catch (Exception ignored) { }
    }

    private boolean looksLikeElm(String s) {
        if (s == null || s.indexOf('>') < 0) return false;
        String n = s.toUpperCase(Locale.US);
        return n.contains("ELM") || n.contains("VLINK") || n.contains("OBD") || n.contains("OK") || n.contains("> ") || n.endsWith(">");
    }

    private String compactAtText(String response) {
        if (response == null) return "";
        String s = response.replace(">", " ").replace('\r', ' ').replace('\n', ' ').trim();
        return s.replaceAll("\\s+", " ");
    }

    private String cleanError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return "OBD connection lost";
        if (m.contains("41 00")) return "Adapter connected, but ECU did not return a valid supported-PID bitmap";
        return m;
    }

    private String summarize(String s) {
        if (s == null) return "";
        String n = s.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return n.length() > 160 ? n.substring(0, 160) + "…" : n;
    }

    private String hex2(int value) { return String.format(Locale.US, "%02X", value & 0xff); }
    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    private void closeQuietly(Object o) {
        if (o == null) return;
        try {
            if (o instanceof InputStream) ((InputStream)o).close();
            else if (o instanceof OutputStream) ((OutputStream)o).close();
            else if (o instanceof BluetoothSocket) ((BluetoothSocket)o).close();
            else if (o instanceof Socket) ((Socket)o).close();
        } catch (Exception ignored) { }
    }

    private void safeCloseGatt(BluetoothGatt g) {
        if (g == null) return;
        try { g.disconnect(); } catch (Exception ignored) { }
        try { g.close(); } catch (Exception ignored) { }
    }

    private void postState(Session s, State next, String detail, Transport transport, String name) {
        if (s != null && !belongsToCurrent(s)) return;
        debugCore("STATE " + state + " -> " + next + " [" + transport + "] " + (detail == null ? "" : detail));
        state = next;
        activeTransport = transport;
        final String emittedName = name == null ? "" : name;
        adapterName = emittedName;
        main.post(() -> {
            if (s == null || belongsToCurrent(s) || next == State.DISCONNECTED) {
                listener.onState(next, detail, transport, emittedName);
            }
        });
    }

    private void postDevices(List<DeviceInfo> devices) {
        List<DeviceInfo> copy = new ArrayList<>(devices);
        main.post(() -> listener.onDevices(copy));
    }

    private void postTelemetry(Session s) {
        if (!belongsToCurrent(s)) return;
        Telemetry copy = s.telemetry.copyFresh(System.currentTimeMillis());
        main.post(() -> { if (belongsToCurrent(s)) listener.onTelemetry(copy); });
    }

    private void postReadiness(Session s) {
        if (!belongsToCurrent(s)) return;
        Readiness copy = s.readiness.copy();
        main.post(() -> { if (belongsToCurrent(s)) listener.onReadiness(copy); });
    }

    private void postDtc(Session s) {
        if (!belongsToCurrent(s)) return;
        DtcResult copy = s.dtc.copy();
        main.post(() -> { if (belongsToCurrent(s)) listener.onDtc(copy); });
    }
}
