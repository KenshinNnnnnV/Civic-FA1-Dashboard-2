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
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production-oriented ELM327 transport for Civic FA1 Dashboard.
 *
 * Design goals:
 *  - never invent telemetry; fields remain NaN until an ECU response is parsed;
 *  - on Android, prefer paired Bluetooth Classic SPP because Vgate iCar Pro BLE Dual
 *    exposes an Android-Vlink classic endpoint and this is the path used by many Android OBD apps;
 *  - provide BLE and Wi-Fi fallbacks;
 *  - do not report ECU_CONNECTED until a real Mode 01 response is received;
 *  - serialise ELM commands, use prompt/timeouts, and recover cleanly from a lost link.
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

    public static final class DeviceInfo {
        public final String name;
        public final String address;
        public final Transport transport;
        public final boolean paired;

        DeviceInfo(String name, String address, Transport transport, boolean paired) {
            this.name = name == null || name.trim().isEmpty() ? "Unknown adapter" : name;
            this.address = address == null ? "" : address;
            this.transport = transport;
            this.paired = paired;
        }

        public String key() {
            return transport.name() + ":" + address;
        }
    }

    public static final class Readiness {
        public boolean available;
        public int milOn;
        public int dtcCount;
        public final Map<String, Boolean> monitors = new HashMap<>();
    }

    public static final class Telemetry {
        public float rpm = Float.NaN;
        public float speed = Float.NaN;
        public float coolant = Float.NaN;
        public float intake = Float.NaN;
        public float throttle = Float.NaN;
        public float load = Float.NaN;
        public float maf = Float.NaN;
        public float fuel = Float.NaN;
        public float voltage = Float.NaN;
        public float map = Float.NaN;
        public float timing = Float.NaN;
        public float fuelRate = Float.NaN;
        public String protocol = "";
        public String adapterVersion = "";
        public long updatedAtMs = 0L;

        public Telemetry copy() {
            Telemetry t = new Telemetry();
            t.rpm = rpm;
            t.speed = speed;
            t.coolant = coolant;
            t.intake = intake;
            t.throttle = throttle;
            t.load = load;
            t.maf = maf;
            t.fuel = fuel;
            t.voltage = voltage;
            t.map = map;
            t.timing = timing;
            t.fuelRate = fuelRate;
            t.protocol = protocol;
            t.adapterVersion = adapterVersion;
            t.updatedAtMs = updatedAtMs;
            return t;
        }

        public void clearLiveValues() {
            rpm = speed = coolant = intake = throttle = load = maf = fuel = voltage = map = timing = fuelRate = Float.NaN;
            updatedAtMs = 0L;
        }
    }

    public interface Listener {
        boolean hasBluetoothPermission();
        void onState(State state, String detail, Transport transport, String adapterName);
        void onDevices(List<DeviceInfo> devices);
        void onTelemetry(Telemetry telemetry);
        void onReadiness(Readiness readiness);
        void onDtc(List<String> codes);
    }

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    private static final UUID FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID FFE0 = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF1 = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID FFE1 = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final BluetoothAdapter bluetoothAdapter;
    private final AtomicBoolean cancel = new AtomicBoolean(false);

    private volatile State state = State.DISCONNECTED;
    private volatile Transport activeTransport = Transport.AUTO;
    private volatile String adapterName = "";

    private BluetoothSocket btSocket;
    private Socket wifiSocket;
    private InputStream in;
    private OutputStream out;

    private BluetoothGatt bleGatt;
    private BluetoothGattCharacteristic bleWrite;
    private BluetoothGattCharacteristic bleNotify;
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleScanCallback;
    private final Object bleRxLock = new Object();
    private final StringBuilder bleRx = new StringBuilder();
    private volatile boolean bleReady = false;

    private final Telemetry telemetry = new Telemetry();
    private final Readiness readiness = new Readiness();
    private final Set<Integer> unsupportedPids = new HashSet<>();
    private final Map<Integer, Integer> noDataCounts = new HashMap<>();

    public ObdManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public State getState() { return state; }
    public Transport getActiveTransport() { return activeTransport; }
    public String getAdapterName() { return adapterName; }

    public void refreshDevices() {
        List<DeviceInfo> result = new ArrayList<>();
        if (bluetoothAdapter != null && listener.hasBluetoothPermission()) {
            try {
                Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        result.add(new DeviceInfo(safeName(d), safeAddress(d), Transport.BLUETOOTH, true));
                    }
                }
            } catch (SecurityException ignored) { }
        }
        Collections.sort(result, Comparator.comparingInt((DeviceInfo a) -> obdNameScore(a.name)));
        postDevices(result);
    }

    public void connectAuto() {
        disconnectInternal(false);
        if (!ensureBluetoothReady(Transport.AUTO)) return;
        io.execute(() -> {
            DeviceInfo candidate = bestPairedCandidate();
            if (candidate != null) {
                connectClassicBlocking(candidate, true);
            } else {
                postState(State.SEARCHING, "No paired OBD adapter. Scanning BLE…", Transport.BLE, "");
                startBleScan(null, true, true);
            }
        });
    }

    public void connect(DeviceInfo device) {
        if (device == null) return;
        disconnectInternal(false);
        if (device.transport == Transport.BLUETOOTH) {
            if (!ensureBluetoothReady(Transport.BLUETOOTH)) return;
            io.execute(() -> connectClassicBlocking(device, false));
        } else if (device.transport == Transport.BLE) {
            if (!ensureBluetoothReady(Transport.BLE)) return;
            startBleScan(device.address, false, true);
        }
    }

    public void connectBluetoothByAddress(String address) {
        if (bluetoothAdapter == null || address == null) return;
        if (!ensureBluetoothReady(Transport.BLUETOOTH)) return;
        try {
            BluetoothDevice d = bluetoothAdapter.getRemoteDevice(address);
            connect(new DeviceInfo(safeName(d), address, Transport.BLUETOOTH, true));
        } catch (Exception e) {
            postState(State.ERROR, "Bluetooth device unavailable", Transport.BLUETOOTH, "");
        }
    }

    public void connectBleScan() {
        disconnectInternal(false);
        if (!ensureBluetoothReady(Transport.BLE)) return;
        startBleScan(null, false, false);
    }

    public void connectWifi(String host, int port) {
        disconnectInternal(false);
        activeTransport = Transport.WIFI;
        cancel.set(false);
        postState(State.SEARCHING, "Opening " + host + ":" + port, Transport.WIFI, "Wi-Fi ELM327");
        io.execute(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 5000);
                s.setTcpNoDelay(true);
                s.setSoTimeout(1000);
                wifiSocket = s;
                in = s.getInputStream();
                out = s.getOutputStream();
                adapterName = "Wi-Fi ELM327";
                postState(State.ADAPTER_FOUND, host + ":" + port, Transport.WIFI, adapterName);
                runElmSession();
            } catch (Exception e) {
                closeSockets();
                postState(State.ERROR, "Wi-Fi adapter not reachable", Transport.WIFI, "");
            }
        });
    }

    public void disconnect() {
        disconnectInternal(true);
    }

    public void shutdown() {
        disconnectInternal(false);
        io.shutdownNow();
    }

    private boolean ensureBluetoothReady(Transport transport) {
        if (!listener.hasBluetoothPermission()) {
            postState(State.PERMISSION_REQUIRED, "Bluetooth permission required", transport, "");
            return false;
        }
        if (bluetoothAdapter == null) {
            postState(State.ERROR, "Bluetooth is not available on this device", transport, "");
            return false;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                postState(State.ERROR, "Turn Bluetooth on", transport, "");
                return false;
            }
        } catch (SecurityException e) {
            postState(State.PERMISSION_REQUIRED, "Bluetooth permission required", transport, "");
            return false;
        }
        return true;
    }

    private DeviceInfo bestPairedCandidate() {
        if (bluetoothAdapter == null) return null;
        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return null;
            BluetoothDevice best = null;
            int bestScore = Integer.MAX_VALUE;
            for (BluetoothDevice d : bonded) {
                int score = obdNameScore(safeName(d));
                if (score < bestScore) {
                    bestScore = score;
                    best = d;
                }
            }
            if (best == null || bestScore >= 100) return null;
            return new DeviceInfo(safeName(best), safeAddress(best), Transport.BLUETOOTH, true);
        } catch (SecurityException e) {
            return null;
        }
    }

    private int obdNameScore(String name) {
        if (name == null) return 1000;
        String n = name.toLowerCase(Locale.US);
        if (n.contains("android-vlink")) return 0;
        if (n.contains("vlink")) return 1;
        if (n.contains("vgate")) return 2;
        if (n.contains("icar")) return 3;
        if (n.contains("obd")) return 4;
        if (n.contains("elm")) return 5;
        return 100;
    }

    private void connectClassicBlocking(DeviceInfo info, boolean autoFallbackBle) {
        activeTransport = Transport.BLUETOOTH;
        adapterName = info.name;
        cancel.set(false);
        postState(State.ADAPTER_FOUND, info.name, Transport.BLUETOOTH, adapterName);
        postState(State.CONNECTING, "Opening Bluetooth serial link", Transport.BLUETOOTH, adapterName);
        BluetoothSocket socket = null;
        try {
            BluetoothDevice d = bluetoothAdapter.getRemoteDevice(info.address);
            try { bluetoothAdapter.cancelDiscovery(); } catch (SecurityException ignored) { }

            try {
                socket = d.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
            } catch (Exception first) {
                closeQuietly(socket);
                socket = d.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
            }

            btSocket = socket;
            in = socket.getInputStream();
            out = socket.getOutputStream();
            runElmSession();
        } catch (Exception e) {
            closeSockets();
            if (autoFallbackBle && !cancel.get()) {
                postState(State.SEARCHING, "Bluetooth link failed. Trying BLE…", Transport.BLE, "");
                main.post(() -> startBleScan(null, true, true));
            } else if (!cancel.get()) {
                postState(State.ERROR, "Could not connect to " + info.name, Transport.BLUETOOTH, info.name);
            }
        }
    }

    private void runElmSession() {
        try {
            postState(State.INITIALIZING, "Initializing ELM327", activeTransport, adapterName);
            resetTelemetry();

            String atz = command("ATZ", 2500);
            if (!looksLikeElm(atz)) {
                throw new IllegalStateException("Adapter did not answer ATZ");
            }
            command("ATE0", 1200);
            command("ATL0", 1200);
            command("ATS0", 1200);
            command("ATH0", 1200);
            command("ATAT1", 1200);
            command("ATSP0", 1500);
            String ati = command("ATI", 1500);
            telemetry.adapterVersion = compactAtText(ati);

            postState(State.ECU_CONNECTING, "Waiting for ECU response", activeTransport, adapterName);
            boolean ecuOk = false;
            long deadline = System.currentTimeMillis() + 12000L;
            while (!cancel.get() && System.currentTimeMillis() < deadline) {
                String r = command("0100", 1800);
                if (findMode01Payload(r, 0x00) != null) {
                    ecuOk = true;
                    break;
                }
                sleep(300);
            }
            if (!ecuOk) {
                throw new IllegalStateException("Adapter connected, ECU did not answer");
            }

            telemetry.protocol = compactAtText(command("ATDP", 1500));
            postState(State.ECU_CONNECTED, "Live ECU data", activeTransport, adapterName);

            long lastReadiness = 0L;
            long lastDtc = 0L;
            while (!cancel.get()) {
                pollPid(0x0C); // rpm
                pollPid(0x0D); // speed
                pollPid(0x05); // coolant
                pollPid(0x11); // throttle
                pollPid(0x04); // load
                pollPid(0x0F); // intake
                pollPid(0x10); // MAF
                pollPid(0x0B); // MAP
                pollPid(0x0E); // timing
                pollPid(0x2F); // fuel level
                pollPid(0x5E); // engine fuel rate, if supported
                pollVoltage();

                long now = System.currentTimeMillis();
                telemetry.updatedAtMs = now;
                postTelemetry();

                if (now - lastReadiness > 5000L) {
                    pollReadiness();
                    lastReadiness = now;
                }
                if (now - lastDtc > 15000L) {
                    pollDtcs();
                    lastDtc = now;
                }
                sleep(80);
            }
        } catch (Exception e) {
            if (!cancel.get()) {
                resetTelemetry();
                postTelemetry();
                postState(State.ERROR, cleanError(e), activeTransport, adapterName);
            }
        } finally {
            closeSockets();
        }
    }

    private String cleanError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return "OBD connection lost";
        if (m.contains("ATZ")) return "ELM327 adapter did not respond";
        if (m.contains("ECU did not answer")) return "Adapter found, but ECU did not respond";
        return m;
    }

    private void pollPid(int pid) throws Exception {
        if (unsupportedPids.contains(pid)) return;
        String cmd = String.format(Locale.US, "01%02X", pid);
        String response = command(cmd, 900);
        if (response == null) return;
        String normalized = normalize(response);
        if (normalized.contains("NODATA") || normalized.contains("STOPPED") || normalized.contains("UNABLETOCONNECT")) {
            int c = noDataCounts.containsKey(pid) ? noDataCounts.get(pid) + 1 : 1;
            noDataCounts.put(pid, c);
            if (c >= 3) unsupportedPids.add(pid);
            return;
        }
        byte[] data = findMode01Payload(response, pid);
        if (data == null) return;
        noDataCounts.put(pid, 0);
        switch (pid) {
            case 0x0C:
                if (data.length >= 2) telemetry.rpm = ((data[0] & 0xff) * 256f + (data[1] & 0xff)) / 4f;
                break;
            case 0x0D:
                if (data.length >= 1) telemetry.speed = data[0] & 0xff;
                break;
            case 0x05:
                if (data.length >= 1) telemetry.coolant = (data[0] & 0xff) - 40f;
                break;
            case 0x11:
                if (data.length >= 1) telemetry.throttle = (data[0] & 0xff) * 100f / 255f;
                break;
            case 0x04:
                if (data.length >= 1) telemetry.load = (data[0] & 0xff) * 100f / 255f;
                break;
            case 0x0F:
                if (data.length >= 1) telemetry.intake = (data[0] & 0xff) - 40f;
                break;
            case 0x10:
                if (data.length >= 2) telemetry.maf = ((data[0] & 0xff) * 256f + (data[1] & 0xff)) / 100f;
                break;
            case 0x0B:
                if (data.length >= 1) telemetry.map = data[0] & 0xff;
                break;
            case 0x0E:
                if (data.length >= 1) telemetry.timing = (data[0] & 0xff) / 2f - 64f;
                break;
            case 0x2F:
                if (data.length >= 1) telemetry.fuel = (data[0] & 0xff) * 100f / 255f;
                break;
            case 0x5E:
                if (data.length >= 2) telemetry.fuelRate = ((data[0] & 0xff) * 256f + (data[1] & 0xff)) / 20f;
                break;
        }
    }

    private void pollVoltage() throws Exception {
        String r = command("ATRV", 900);
        if (r == null) return;
        String s = r.toUpperCase(Locale.US).replace("V", " ").replace(">", " ").trim();
        String[] parts = s.split("[^0-9.]+");
        for (String p : parts) {
            if (p.contains(".")) {
                try {
                    float v = Float.parseFloat(p);
                    if (v >= 5f && v <= 20f) {
                        telemetry.voltage = v;
                        return;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
    }

    private void pollReadiness() {
        try {
            String r = command("0101", 1000);
            byte[] d = findMode01Payload(r, 0x01);
            if (d == null || d.length < 4) return;
            int a = d[0] & 0xff;
            int b = d[1] & 0xff;
            int c = d[2] & 0xff;
            int e = d[3] & 0xff;
            readiness.available = true;
            readiness.milOn = (a & 0x80) != 0 ? 1 : 0;
            readiness.dtcCount = a & 0x7f;
            readiness.monitors.clear();
            readiness.monitors.put("Misfire", (b & 0x10) == 0);
            readiness.monitors.put("Fuel System", (b & 0x20) == 0);
            readiness.monitors.put("Components", (b & 0x40) == 0);
            readiness.monitors.put("Catalyst", (c & 0x01) == 0);
            readiness.monitors.put("Heated Catalyst", (c & 0x02) == 0);
            readiness.monitors.put("Evaporative System", (c & 0x04) == 0);
            readiness.monitors.put("Secondary Air", (c & 0x08) == 0);
            readiness.monitors.put("O2 Sensor", (c & 0x20) == 0);
            readiness.monitors.put("O2 Sensor Heater", (c & 0x40) == 0);
            readiness.monitors.put("EGR/VVT", (c & 0x80) == 0);
            postReadiness();
        } catch (Exception ignored) { }
    }

    private void pollDtcs() {
        try {
            String r = command("03", 1600);
            List<String> codes = parseMode03(r);
            postDtc(codes);
        } catch (Exception ignored) { }
    }

    private List<String> parseMode03(String response) {
        String hex = extractHex(response);
        int idx = hex.indexOf("43");
        if (idx < 0) return new ArrayList<>();
        hex = hex.substring(idx + 2);
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 4 <= hex.length(); i += 4) {
            int a, b;
            try {
                a = Integer.parseInt(hex.substring(i, i + 2), 16);
                b = Integer.parseInt(hex.substring(i + 2, i + 4), 16);
            } catch (NumberFormatException e) { break; }
            if (a == 0 && b == 0) continue;
            char system = "PCBU".charAt((a >> 6) & 0x03);
            int d1 = (a >> 4) & 0x03;
            int d2 = a & 0x0f;
            out.add(String.format(Locale.US, "%c%d%X%02X", system, d1, d2, b));
        }
        return out;
    }

    private String command(String command, long timeoutMs) throws Exception {
        if (cancel.get()) throw new InterruptedException("Disconnected");
        if (activeTransport == Transport.BLE) return bleCommand(command, timeoutMs);
        if (out == null || in == null) throw new IllegalStateException("OBD link is not open");

        drainInput();
        out.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!cancel.get() && System.currentTimeMillis() < deadline) {
            int available = in.available();
            if (available > 0) {
                byte[] buffer = new byte[Math.min(available, 512)];
                int n = in.read(buffer);
                if (n > 0) {
                    bytes.write(buffer, 0, n);
                    String s = bytes.toString(StandardCharsets.US_ASCII.name());
                    if (s.indexOf('>') >= 0) return s;
                }
            } else {
                sleep(12);
            }
        }
        String partial = bytes.toString(StandardCharsets.US_ASCII.name());
        if (!partial.isEmpty()) return partial;
        throw new IllegalStateException("Timeout waiting for " + command);
    }

    private void drainInput() {
        if (in == null) return;
        try {
            while (in.available() > 0) {
                byte[] b = new byte[Math.min(256, in.available())];
                if (in.read(b) <= 0) break;
            }
        } catch (Exception ignored) { }
    }

    private boolean looksLikeElm(String s) {
        if (s == null) return false;
        String n = s.toUpperCase(Locale.US);
        return n.contains("ELM") || n.contains("VLINK") || n.contains("OBD") || n.contains("OK") || n.contains(">");
    }

    private byte[] findMode01Payload(String response, int pid) {
        String hex = extractHex(response);
        String header = String.format(Locale.US, "41%02X", pid);
        int idx = hex.indexOf(header);
        if (idx < 0) return null;
        String tail = hex.substring(idx + 4);
        int bytes = tail.length() / 2;
        byte[] result = new byte[Math.min(bytes, 8)];
        for (int i = 0; i < result.length; i++) {
            try { result[i] = (byte) Integer.parseInt(tail.substring(i * 2, i * 2 + 2), 16); }
            catch (NumberFormatException e) { return null; }
        }
        return result;
    }

    private String extractHex(String response) {
        if (response == null) return "";
        String upper = response.toUpperCase(Locale.US);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F')) out.append(ch);
        }
        return out.toString();
    }

    private String normalize(String response) {
        return response == null ? "" : response.toUpperCase(Locale.US).replace(" ", "").replace("\r", "").replace("\n", "");
    }

    private String compactAtText(String response) {
        if (response == null) return "";
        String s = response.replace(">", " ").replace("\r", " ").replace("\n", " ").trim();
        return s.replaceAll("\\s+", " ");
    }

    // ---------------- BLE ----------------

    private void startBleScan(String requestedAddress, boolean autoFallback, boolean connectFirstMatch) {
        if (!ensureBluetoothReady(Transport.BLE)) return;
        cancel.set(false);
        activeTransport = Transport.BLE;
        try {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bleScanner == null) {
                postState(State.ERROR, "BLE scanner unavailable", Transport.BLE, "");
                return;
            }
            postState(State.SEARCHING, "Scanning BLE adapters", Transport.BLE, "");
            List<DeviceInfo> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            bleScanCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice d = result.getDevice();
                    String address = safeAddress(d);
                    if (!seen.add(address)) return;
                    DeviceInfo info = new DeviceInfo(safeName(d), address, Transport.BLE, false);
                    found.add(info);
                    postDevices(new ArrayList<>(found));
                    boolean addressMatch = requestedAddress != null && requestedAddress.equalsIgnoreCase(address);
                    boolean goodName = obdNameScore(info.name) < 100;
                    if (addressMatch || (connectFirstMatch && requestedAddress == null && goodName)) {
                        stopBleScan();
                        connectBleDevice(d, info.name);
                    }
                }
                @Override public void onScanFailed(int errorCode) {
                    postState(State.ERROR, "BLE scan error " + errorCode, Transport.BLE, "");
                }
            };
            bleScanner.startScan(bleScanCallback);
            main.postDelayed(() -> {
                if (state == State.SEARCHING && activeTransport == Transport.BLE) {
                    stopBleScan();
                    if (connectFirstMatch) {
                        postState(State.ERROR, autoFallback ? "No compatible OBD adapter found" : "BLE adapter not found", Transport.BLE, "");
                    } else {
                        postState(State.DISCONNECTED, "BLE scan complete — select a device", Transport.BLE, "");
                    }
                }
            }, 10000L);
        } catch (SecurityException e) {
            postState(State.PERMISSION_REQUIRED, "Bluetooth scan permission required", Transport.BLE, "");
        }
    }

    private void stopBleScan() {
        try {
            if (bleScanner != null && bleScanCallback != null) bleScanner.stopScan(bleScanCallback);
        } catch (SecurityException ignored) { }
        bleScanCallback = null;
    }

    private void connectBleDevice(BluetoothDevice device, String name) {
        adapterName = name;
        postState(State.ADAPTER_FOUND, name, Transport.BLE, name);
        postState(State.CONNECTING, "Opening BLE serial service", Transport.BLE, name);
        try {
            bleGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try { g.discoverServices(); }
                        catch (SecurityException e) { postState(State.PERMISSION_REQUIRED, "Bluetooth permission required", Transport.BLE, name); }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !cancel.get()) {
                        postState(State.ERROR, "BLE adapter disconnected", Transport.BLE, name);
                    }
                }

                @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS || !selectBleUart(g)) {
                        postState(State.ERROR, "BLE serial service not recognized", Transport.BLE, name);
                        return;
                    }
                    enableBleNotifications(g);
                    bleReady = true;
                    io.execute(ObdManager.this::runElmSession);
                }

                @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
                    byte[] data = characteristic.getValue();
                    if (data == null) return;
                    synchronized (bleRxLock) {
                        bleRx.append(new String(data, StandardCharsets.US_ASCII));
                        bleRxLock.notifyAll();
                    }
                }
            });
        } catch (SecurityException e) {
            postState(State.PERMISSION_REQUIRED, "Bluetooth connect permission required", Transport.BLE, name);
        }
    }

    private boolean selectBleUart(BluetoothGatt g) {
        BluetoothGattService fff0 = g.getService(FFF0);
        if (fff0 != null) {
            BluetoothGattCharacteristic c1 = fff0.getCharacteristic(FFF1);
            BluetoothGattCharacteristic c2 = fff0.getCharacteristic(FFF2);
            if (assignBleChars(c1, c2)) return true;
        }
        BluetoothGattService ffe0 = g.getService(FFE0);
        if (ffe0 != null) {
            BluetoothGattCharacteristic c = ffe0.getCharacteristic(FFE1);
            if (c != null) {
                bleWrite = c;
                bleNotify = c;
                return true;
            }
        }
        BluetoothGattCharacteristic firstWrite = null;
        BluetoothGattCharacteristic firstNotify = null;
        for (BluetoothGattService s : g.getServices()) {
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                int p = c.getProperties();
                if (firstWrite == null && ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) firstWrite = c;
                if (firstNotify == null && ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) firstNotify = c;
            }
        }
        bleWrite = firstWrite;
        bleNotify = firstNotify != null ? firstNotify : firstWrite;
        return bleWrite != null && bleNotify != null;
    }

    private boolean assignBleChars(BluetoothGattCharacteristic a, BluetoothGattCharacteristic b) {
        BluetoothGattCharacteristic[] chars = new BluetoothGattCharacteristic[]{a, b};
        for (BluetoothGattCharacteristic c : chars) {
            if (c == null) continue;
            int p = c.getProperties();
            if (bleWrite == null && ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) bleWrite = c;
            if (bleNotify == null && ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) bleNotify = c;
        }
        if (bleNotify == null) bleNotify = bleWrite;
        return bleWrite != null && bleNotify != null;
    }

    private void enableBleNotifications(BluetoothGatt g) {
        try {
            g.setCharacteristicNotification(bleNotify, true);
            BluetoothGattDescriptor d = bleNotify.getDescriptor(CCCD_UUID);
            if (d != null) {
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(d);
            }
        } catch (SecurityException ignored) { }
    }

    private String bleCommand(String command, long timeoutMs) throws Exception {
        if (bleGatt == null || bleWrite == null || !bleReady) throw new IllegalStateException("BLE link is not ready");
        synchronized (bleRxLock) { bleRx.setLength(0); }
        byte[] data = (command + "\r").getBytes(StandardCharsets.US_ASCII);
        try {
            bleWrite.setValue(data);
            if ((bleWrite.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                bleWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                bleWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
            if (!bleGatt.writeCharacteristic(bleWrite)) throw new IllegalStateException("BLE write failed");
        } catch (SecurityException e) {
            throw new IllegalStateException("Bluetooth permission required");
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (bleRxLock) {
            while (!cancel.get() && System.currentTimeMillis() < deadline) {
                if (bleRx.indexOf(">") >= 0) return bleRx.toString();
                try { bleRxLock.wait(50L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (bleRx.length() > 0) return bleRx.toString();
        }
        throw new IllegalStateException("Timeout waiting for " + command);
    }

    private void disconnectInternal(boolean notify) {
        cancel.set(true);
        stopBleScan();
        closeSockets();
        closeBle();
        resetTelemetry();
        postTelemetry();
        readiness.available = false;
        readiness.monitors.clear();
        postReadiness();
        postDtc(new ArrayList<>());
        if (notify) postState(State.DISCONNECTED, "Tap OBD Connection to connect", Transport.AUTO, "");
    }

    private void resetTelemetry() {
        unsupportedPids.clear();
        noDataCounts.clear();
        telemetry.clearLiveValues();
        telemetry.protocol = "";
        telemetry.adapterVersion = "";
    }

    private void closeSockets() {
        closeQuietly(in); in = null;
        closeQuietly(out); out = null;
        closeQuietly(btSocket); btSocket = null;
        closeQuietly(wifiSocket); wifiSocket = null;
    }

    private void closeBle() {
        bleReady = false;
        bleWrite = null;
        bleNotify = null;
        if (bleGatt != null) {
            try { bleGatt.disconnect(); bleGatt.close(); } catch (Exception ignored) { }
            bleGatt = null;
        }
    }

    private void closeQuietly(Object o) {
        if (o == null) return;
        try {
            if (o instanceof InputStream) ((InputStream) o).close();
            else if (o instanceof OutputStream) ((OutputStream) o).close();
            else if (o instanceof BluetoothSocket) ((BluetoothSocket) o).close();
            else if (o instanceof Socket) ((Socket) o).close();
        } catch (Exception ignored) { }
    }

    private String safeName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null ? "Bluetooth adapter" : n;
        } catch (SecurityException e) { return "Bluetooth adapter"; }
    }

    private String safeAddress(BluetoothDevice d) {
        try { return d.getAddress(); }
        catch (SecurityException e) { return ""; }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void postState(State s, String detail, Transport transport, String name) {
        state = s;
        activeTransport = transport;
        if (name != null && !name.isEmpty()) adapterName = name;
        main.post(() -> listener.onState(s, detail, transport, adapterName));
    }

    private void postDevices(List<DeviceInfo> devices) {
        main.post(() -> listener.onDevices(devices));
    }

    private void postTelemetry() {
        Telemetry copy = telemetry.copy();
        main.post(() -> listener.onTelemetry(copy));
    }

    private void postReadiness() {
        Readiness r = new Readiness();
        r.available = readiness.available;
        r.milOn = readiness.milOn;
        r.dtcCount = readiness.dtcCount;
        r.monitors.putAll(readiness.monitors);
        main.post(() -> listener.onReadiness(r));
    }

    private void postDtc(List<String> codes) {
        List<String> copy = new ArrayList<>(codes);
        main.post(() -> listener.onDtc(copy));
    }
}
