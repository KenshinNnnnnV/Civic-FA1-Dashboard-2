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
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small ELM327/Vgate transport used by the dashboard.
 *
 * It tries a paired Bluetooth Classic SPP adapter first. If that is not available,
 * it scans for a BLE adapter and automatically selects writable/notifiable UART-like
 * characteristics. The BLE path is intentionally generic because Vgate iCar Pro is
 * sold in several radio variants.
 */
public class ObdManager {

    public enum State {
        SIMULATOR,
        SEARCHING,
        ADAPTER_FOUND,
        CONNECTING,
        ECU_CONNECTED,
        ERROR
    }

    public static class Telemetry {
        public float rpm = Float.NaN;
        public float speed = Float.NaN;
        public float coolant = Float.NaN;
        public float throttle = Float.NaN;
        public float load = Float.NaN;
        public float intake = Float.NaN;
        public float voltage = Float.NaN;
        public float maf = Float.NaN;
        public float fuel = Float.NaN;
        public String protocol = "AUTO";
    }

    public interface Listener {
        void onObdState(State state, String detail, String transport);
        void onTelemetry(Telemetry telemetry);
    }

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final BluetoothAdapter adapter;

    private volatile boolean stopped = false;
    private BluetoothSocket classicSocket;
    private InputStream classicIn;
    private OutputStream classicOut;

    private BluetoothGatt gatt;
    private Socket wifiSocket;
    private InputStream wifiIn;
    private OutputStream wifiOut;
    private BluetoothGattCharacteristic bleWrite;
    private BluetoothGattCharacteristic bleNotify;
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;

    private final StringBuilder rx = new StringBuilder();
    private String currentCommand = null;
    private int initIndex = 0;
    private int pollIndex = 0;
    private boolean elmReady = false;
    private boolean commandInFlight = false;
    private String transportName = "SIMULATOR";

    private final String[] initCommands = new String[] {
            "ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100"
    };

    private final String[] pollCommands = new String[] {
            "010C", // RPM
            "010D", // vehicle speed
            "0105", // coolant
            "0111", // throttle position
            "0104", // calculated engine load
            "010F", // intake air temperature
            "0110", // MAF
            "012F", // fuel level (if supported)
            "ATRV"  // adapter voltage
    };

    private final Telemetry telemetry = new Telemetry();

    public ObdManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void startAutoConnect() {
        stopped = false;
        closeConnections(false);
        if (!prepareBluetooth("AUTO")) return;

        state(State.SEARCHING, "Looking for Vgate / iCar / OBD adapter", "AUTO");
        BluetoothDevice classicCandidate = findBondedCandidate();
        if (classicCandidate != null) {
            connectClassic(classicCandidate, true);
        } else {
            startBleScan();
        }
    }

    public void startBleConnect() {
        stopped = false;
        closeConnections(true);
        if (!prepareBluetooth("BLE")) return;
        startBleScan();
    }

    public void startClassicConnect() {
        stopped = false;
        closeConnections(true);
        if (!prepareBluetooth("BLUETOOTH CLASSIC")) return;
        BluetoothDevice classicCandidate = findBondedCandidate();
        if (classicCandidate == null) {
            state(State.ERROR, "Pair Vgate / iCar Pro in Android Bluetooth settings first", "BLUETOOTH CLASSIC");
            return;
        }
        connectClassic(classicCandidate, false);
    }

    public void startWifiConnect() {
        startWifiConnect("192.168.0.10", 35000);
    }

    public void startWifiConnect(String host, int port) {
        stopped = false;
        closeConnections(true);
        transportName = "WIFI";
        state(State.SEARCHING, "Looking for ELM327 at " + host + ":" + port, "WIFI");
        io.execute(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 4500);
                socket.setTcpNoDelay(true);
                wifiSocket = socket;
                wifiIn = socket.getInputStream();
                wifiOut = socket.getOutputStream();
                state(State.ADAPTER_FOUND, host + ":" + port, "WIFI");
                state(State.CONNECTING, "Opening ELM327 Wi-Fi link", "WIFI");
                resetElmSession();
                startWifiReader();
                main.postDelayed(this::sendNextCommand, 250);
            } catch (Exception e) {
                closeWifi();
                state(State.ERROR, "Wi-Fi OBD not found at " + host + ":" + port, "WIFI");
            }
        });
    }

    private boolean prepareBluetooth(String transport) {
        if (adapter == null) {
            state(State.ERROR, "Bluetooth not available", transport);
            return false;
        }
        try {
            if (!adapter.isEnabled()) {
                state(State.ERROR, "Turn Bluetooth on", transport);
                return false;
            }
        } catch (SecurityException e) {
            state(State.ERROR, "Bluetooth permission required", transport);
            return false;
        }
        return true;
    }

    public void stop() {
        stopped = true;
        closeConnections(true);
        state(State.SIMULATOR, "Live adapter disconnected", "SIMULATOR");
    }

    private BluetoothDevice findBondedCandidate() {
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null) return null;
            for (BluetoothDevice d : bonded) {
                String n = safeName(d);
                if (isCandidateName(n)) return d;
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private boolean isCandidateName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        return n.contains("vgate") || n.contains("icar") || n.contains("vlink") ||
                n.contains("obd") || n.contains("elm") || n.contains("veepeak") ||
                n.contains("v-link") || n.contains("vlink") || n.contains("ios-vlink") || n.contains("android-vlink");
    }

    private String safeName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null ? d.getAddress() : n;
        } catch (SecurityException e) {
            return "Bluetooth adapter";
        }
    }

    private void connectClassic(BluetoothDevice device, boolean fallbackToBle) {
        final String name = safeName(device);
        state(State.ADAPTER_FOUND, name, "BLUETOOTH CLASSIC");
        state(State.CONNECTING, "Opening ELM327 serial link", "BLUETOOTH CLASSIC");
        io.execute(() -> {
            try {
                classicSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                adapter.cancelDiscovery();
                classicSocket.connect();
                classicIn = classicSocket.getInputStream();
                classicOut = classicSocket.getOutputStream();
                transportName = "BLUETOOTH CLASSIC";
                resetElmSession();
                startClassicReader();
                main.postDelayed(this::sendNextCommand, 250);
            } catch (Exception e) {
                closeClassic();
                if (fallbackToBle) main.post(() -> startBleScan());
                else state(State.ERROR, "Could not open paired BT 3.0 adapter", "BLUETOOTH CLASSIC");
            }
        });
    }

    private void startClassicReader() {
        io.execute(() -> {
            byte[] buf = new byte[512];
            try {
                while (!stopped && classicIn != null) {
                    int n = classicIn.read(buf);
                    if (n < 0) break;
                    if (n > 0) onBytes(buf, n);
                }
            } catch (Exception e) {
                if (!stopped) state(State.ERROR, "Classic Bluetooth link lost", transportName);
            }
        });
    }

    private void startWifiReader() {
        io.execute(() -> {
            byte[] buf = new byte[512];
            try {
                while (!stopped && wifiIn != null) {
                    int n = wifiIn.read(buf);
                    if (n < 0) break;
                    if (n > 0) onBytes(buf, n);
                }
            } catch (Exception e) {
                if (!stopped) state(State.ERROR, "Wi-Fi OBD link lost", "WIFI");
            }
        });
    }

    private void startBleScan() {
        if (stopped) return;
        try {
            bleScanner = adapter.getBluetoothLeScanner();
            if (bleScanner == null) {
                state(State.ERROR, "BLE scanner unavailable", "BLE");
                return;
            }
            transportName = "BLE";
            state(State.SEARCHING, "Scanning for Vgate iCar Pro", "BLE");
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice d = result.getDevice();
                    String name = safeName(d);
                    if (isCandidateName(name)) {
                        stopBleScan();
                        state(State.ADAPTER_FOUND, name, "BLE");
                        connectBle(d);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    state(State.ERROR, "BLE scan error " + errorCode, "BLE");
                }
            };
            bleScanner.startScan(scanCallback);
            main.postDelayed(() -> {
                if (!elmReady && gatt == null) {
                    stopBleScan();
                    state(State.ERROR, "No compatible OBD adapter found", "AUTO");
                }
            }, 9000);
        } catch (SecurityException e) {
            state(State.ERROR, "Bluetooth scan permission required", "BLE");
        }
    }

    private void stopBleScan() {
        try {
            if (bleScanner != null && scanCallback != null) bleScanner.stopScan(scanCallback);
        } catch (SecurityException ignored) {
        }
        scanCallback = null;
    }

    private void connectBle(BluetoothDevice device) {
        state(State.CONNECTING, "Opening BLE link", "BLE");
        try {
            gatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            g.discoverServices();
                        } catch (SecurityException e) {
                            state(State.ERROR, "BLE permission required", "BLE");
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !stopped) {
                        state(State.ERROR, "BLE adapter disconnected", "BLE");
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt g, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS || !findBleUart(g)) {
                        state(State.ERROR, "Compatible BLE serial service not found", "BLE");
                        return;
                    }
                    resetElmSession();
                    enableBleNotifications(g);
                    main.postDelayed(ObdManager.this::sendNextCommand, 350);
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0) onBytes(data, data.length);
                }
            });
        } catch (SecurityException e) {
            state(State.ERROR, "Bluetooth connect permission required", "BLE");
        }
    }

    private boolean findBleUart(BluetoothGatt g) {
        BluetoothGattCharacteristic firstWrite = null;
        BluetoothGattCharacteristic firstNotify = null;
        for (BluetoothGattService s : g.getServices()) {
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                int props = c.getProperties();
                if (firstWrite == null && ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) {
                    firstWrite = c;
                }
                if (firstNotify == null && ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) {
                    firstNotify = c;
                }
            }
        }
        bleWrite = firstWrite;
        bleNotify = firstNotify != null ? firstNotify : firstWrite;
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
        } catch (SecurityException ignored) {
        }
    }

    private synchronized void resetElmSession() {
        rx.setLength(0);
        initIndex = 0;
        pollIndex = 0;
        elmReady = false;
        commandInFlight = false;
        currentCommand = null;
    }

    private void sendNextCommand() {
        if (stopped || commandInFlight) return;
        String cmd;
        if (initIndex < initCommands.length) {
            cmd = initCommands[initIndex++];
        } else {
            if (!elmReady) {
                elmReady = true;
                state(State.ECU_CONNECTED, "Honda ECU responding", transportName);
            }
            cmd = pollCommands[pollIndex++ % pollCommands.length];
        }
        sendCommand(cmd);
    }

    private void sendCommand(String cmd) {
        currentCommand = cmd;
        commandInFlight = true;
        byte[] data = (cmd + "\r").getBytes(StandardCharsets.US_ASCII);
        if (classicOut != null) {
            io.execute(() -> {
                try {
                    classicOut.write(data);
                    classicOut.flush();
                } catch (Exception e) {
                    commandInFlight = false;
                    state(State.ERROR, "OBD write failed", transportName);
                }
            });
            return;
        }
        if (wifiOut != null) {
            io.execute(() -> {
                try {
                    wifiOut.write(data);
                    wifiOut.flush();
                } catch (Exception e) {
                    commandInFlight = false;
                    state(State.ERROR, "Wi-Fi OBD write failed", "WIFI");
                }
            });
            return;
        }
        if (gatt != null && bleWrite != null) {
            try {
                bleWrite.setValue(data);
                int writeType = (bleWrite.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                        ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                bleWrite.setWriteType(writeType);
                gatt.writeCharacteristic(bleWrite);
            } catch (SecurityException e) {
                commandInFlight = false;
                state(State.ERROR, "BLE write permission required", "BLE");
            }
            return;
        }
        commandInFlight = false;
    }

    private synchronized void onBytes(byte[] data, int n) {
        rx.append(new String(data, 0, n, StandardCharsets.US_ASCII));
        int prompt;
        while ((prompt = rx.indexOf(">")) >= 0) {
            String response = rx.substring(0, prompt);
            rx.delete(0, prompt + 1);
            String cmd = currentCommand;
            commandInFlight = false;
            parseResponse(cmd, response);
            main.postDelayed(this::sendNextCommand, elmReady ? 85 : 140);
        }
    }

    private void parseResponse(String cmd, String raw) {
        if (cmd == null) return;
        String upper = raw.toUpperCase(Locale.US).replace("SEARCHING...", "");
        if (cmd.startsWith("AT")) {
            if ("ATRV".equals(cmd)) {
                String compact = upper.replace(" ", "").replace("\r", "").replace("\n", "");
                int v = compact.indexOf('V');
                if (v > 0) {
                    int start = v - 1;
                    while (start >= 0 && (Character.isDigit(compact.charAt(start)) || compact.charAt(start) == '.')) start--;
                    try { telemetry.voltage = Float.parseFloat(compact.substring(start + 1, v)); } catch (Exception ignored) {}
                }
                publishTelemetry();
            }
            return;
        }

        String hex = upper.replaceAll("[^0-9A-F]", "");
        String pid = cmd.length() >= 4 ? cmd.substring(2, 4) : "";
        String marker = "41" + pid;
        int idx = hex.indexOf(marker);
        if (idx < 0) return;
        int pos = idx + marker.length();
        try {
            int a = readByte(hex, pos);
            int b = readByte(hex, pos + 2);
            switch (pid) {
                case "0C": telemetry.rpm = ((a * 256f) + b) / 4f; break;
                case "0D": telemetry.speed = a; break;
                case "05": telemetry.coolant = a - 40f; break;
                case "11": telemetry.throttle = a * 100f / 255f; break;
                case "04": telemetry.load = a * 100f / 255f; break;
                case "0F": telemetry.intake = a - 40f; break;
                case "10": telemetry.maf = ((a * 256f) + b) / 100f; break;
                case "2F": telemetry.fuel = a * 100f / 255f; break;
            }
            publishTelemetry();
        } catch (Exception ignored) {
        }
    }

    private int readByte(String hex, int pos) {
        if (pos + 2 > hex.length()) return 0;
        return Integer.parseInt(hex.substring(pos, pos + 2), 16);
    }

    private void publishTelemetry() {
        Telemetry copy = new Telemetry();
        copy.rpm = telemetry.rpm;
        copy.speed = telemetry.speed;
        copy.coolant = telemetry.coolant;
        copy.throttle = telemetry.throttle;
        copy.load = telemetry.load;
        copy.intake = telemetry.intake;
        copy.voltage = telemetry.voltage;
        copy.maf = telemetry.maf;
        copy.fuel = telemetry.fuel;
        copy.protocol = telemetry.protocol;
        main.post(() -> listener.onTelemetry(copy));
    }

    private void state(State state, String detail, String transport) {
        main.post(() -> listener.onObdState(state, detail, transport));
    }

    private void closeConnections(boolean stopScan) {
        if (stopScan) stopBleScan();
        closeClassic();
        closeWifi();
        try {
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
        } catch (SecurityException ignored) {
        }
        gatt = null;
        bleWrite = null;
        bleNotify = null;
        commandInFlight = false;
        elmReady = false;
    }

    private void closeWifi() {
        try { if (wifiIn != null) wifiIn.close(); } catch (Exception ignored) {}
        try { if (wifiOut != null) wifiOut.close(); } catch (Exception ignored) {}
        try { if (wifiSocket != null) wifiSocket.close(); } catch (Exception ignored) {}
        wifiIn = null;
        wifiOut = null;
        wifiSocket = null;
    }

    private void closeClassic() {
        try { if (classicIn != null) classicIn.close(); } catch (Exception ignored) {}
        try { if (classicOut != null) classicOut.close(); } catch (Exception ignored) {}
        try { if (classicSocket != null) classicSocket.close(); } catch (Exception ignored) {}
        classicIn = null;
        classicOut = null;
        classicSocket = null;
    }
}
