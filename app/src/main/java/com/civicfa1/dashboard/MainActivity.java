package com.civicfa1.dashboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int BT_PERMISSION_REQUEST = 704;
    private DashboardView dashboardView;
    private boolean permissionDialogShown;

    public interface WifiEndpointCallback {
        void onEndpoint(String host, int port);
    }

    public interface IntValueCallback {
        void onValue(int value);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUi();
        dashboardView = new DashboardView(this);
        setContentView(dashboardView);
        // Bluetooth permission is requested only when the user scans/connects a Bluetooth transport.
    }

    public boolean hasObdBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestObdBluetoothPermissions() {
        if (hasObdBluetoothPermissions()) {
            if (dashboardView != null) dashboardView.onBluetoothPermissionResult(true);
            return;
        }
        showBluetoothPermissionExplanation();
    }

    private void showBluetoothPermissionExplanation() {
        if (permissionDialogShown || isFinishing()) return;
        permissionDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle("OBD Bluetooth access")
                .setMessage("Bluetooth permission is used only to scan and connect your OBD-II adapter. Live vehicle data remains unavailable until you connect an adapter.")
                .setNegativeButton("Not now", (d, which) -> permissionDialogShown = false)
                .setPositiveButton("Continue", (d, which) -> {
                    permissionDialogShown = false;
                    requestBluetoothPermissionsNow();
                })
                .setOnCancelListener(d -> permissionDialogShown = false)
                .show();
    }

    private void requestBluetoothPermissionsNow() {
        if (hasObdBluetoothPermissions()) return;
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        requestPermissions(permissions.toArray(new String[0]), BT_PERMISSION_REQUEST);
    }


    public void promptConnectionTimeout(int currentMs, IntValueCallback callback) {
        EditText value = new EditText(this);
        value.setSingleLine(true);
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setText(Integer.toString(currentMs));
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        value.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(this)
                .setTitle("Connection timeout (ms)")
                .setMessage("Allowed range: 3000–15000 ms")
                .setView(value)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    int ms;
                    try { ms = Integer.parseInt(value.getText().toString().trim()); }
                    catch (NumberFormatException e) { ms = -1; }
                    if (ms < 3000 || ms > 15000) {
                        Toast.makeText(this, "Enter 3000–15000 ms", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (callback != null) callback.onValue(ms);
                })
                .show();
    }

    public void showObdLog(String logText, Runnable exportAction) {
        String text = logText == null || logText.trim().isEmpty() ?
                "Debug logging is currently empty. Enable logging in the project preferences when detailed transport traces are required." : logText;
        new AlertDialog.Builder(this)
                .setTitle("OBD debug log")
                .setMessage(text)
                .setNegativeButton("Close", null)
                .setNeutralButton("Export", (d, which) -> { if (exportAction != null) exportAction.run(); })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BT_PERMISSION_REQUEST && dashboardView != null) {
            dashboardView.onBluetoothPermissionResult(hasObdBluetoothPermissions());
        }
    }

    public void promptWifiEndpoint(String currentHost, int currentPort, WifiEndpointCallback callback) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        EditText host = new EditText(this);
        host.setHint("Host / IP");
        host.setSingleLine(true);
        host.setText(currentHost == null ? "" : currentHost);
        layout.addView(host);

        EditText port = new EditText(this);
        port.setHint("Port");
        port.setSingleLine(true);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(Integer.toString(currentPort));
        layout.addView(port);

        new AlertDialog.Builder(this)
                .setTitle("Wi-Fi ELM327 endpoint")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    String h = host.getText().toString().trim();
                    int p;
                    try { p = Integer.parseInt(port.getText().toString().trim()); }
                    catch (NumberFormatException e) { p = -1; }
                    if (h.isEmpty() || p < 1 || p > 65535) {
                        Toast.makeText(this, "Enter a valid host and port", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (callback != null) callback.onEndpoint(h, p);
                })
                .show();
    }

    @Override protected void onStart() {
        super.onStart();
        if (dashboardView != null) dashboardView.onHostStart();
    }

    @Override protected void onStop() {
        // v0.9.4: minimizing must NOT close the dashboard or tear down the OBD session.
        // DashboardView only pauses visual frame callbacks while the Activity is hidden;
        // the existing ELM327 session may stay alive for instant resume.
        if (dashboardView != null) dashboardView.onHostStop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (dashboardView != null) dashboardView.destroy();
        super.onDestroy();
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
    }
}
