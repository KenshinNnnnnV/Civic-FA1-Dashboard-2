package com.civicfa1.dashboard;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView rpmValue, speedValue, coolantValue, voltageValue;
    private int rpm = 850;
    private int direction = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUi();
        setContentView(buildDashboard());
        startDemoAnimation();
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private View buildDashboard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(36, 24, 36, 24);
        root.setBackgroundColor(Color.rgb(5, 7, 10));

        TextView title = text("CIVIC FA1  •  DASHBOARD TEST", 24, Color.rgb(205,214,224));
        root.addView(title, new LinearLayout.LayoutParams(-1, 56));

        TextView status = text("TEST MODE  •  OBD DISCONNECTED", 16, Color.rgb(0,230,170));
        root.addView(status, new LinearLayout.LayoutParams(-1, 44));

        LinearLayout gauges = new LinearLayout(this);
        gauges.setOrientation(LinearLayout.HORIZONTAL);
        gauges.setGravity(Gravity.CENTER);
        root.addView(gauges, new LinearLayout.LayoutParams(-1, 0, 1f));

        rpmValue = addGauge(gauges, "RPM", "850", "rpm", 72);
        speedValue = addGauge(gauges, "SPEED", "0", "km/h", 62);
        coolantValue = addGauge(gauges, "COOLANT", "88", "°C", 62);
        voltageValue = addGauge(gauges, "VOLTAGE", "14.1", "V", 62);

        TextView footer = text("Android compatibility test • 1280×720 layout", 14, Color.rgb(110,122,136));
        root.addView(footer, new LinearLayout.LayoutParams(-1, 42));

        return root;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView addGauge(LinearLayout parent, String label, String initial, String unit, int valueSize) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(12,12,12,12);

        box.addView(text(label, 18, Color.rgb(125,140,156)), new LinearLayout.LayoutParams(-1,48));

        TextView value = text(initial, valueSize, Color.WHITE);
        box.addView(value, new LinearLayout.LayoutParams(-1,0,1f));

        box.addView(text(unit, 18, Color.rgb(0,230,170)), new LinearLayout.LayoutParams(-1,44));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-1,1f);
        p.setMargins(8,8,8,8);
        parent.addView(box,p);
        return value;
    }

    private void startDemoAnimation() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                rpm += direction * 250;
                if (rpm >= 4200) direction = -1;
                if (rpm <= 850) direction = 1;

                int speed = Math.max(0, (rpm - 850) / 35);
                int coolant = 88 + ((rpm / 500) % 4);
                double voltage = 13.8 + ((rpm % 700) / 1000.0);

                rpmValue.setText(String.valueOf(rpm));
                speedValue.setText(String.valueOf(speed));
                coolantValue.setText(String.valueOf(coolant));
                voltageValue.setText(String.format(Locale.US, "%.1f", voltage));
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
    }
}
