package com.civicfa1.dashboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Native component dashboard root. No full-screen dashboard bitmap is used. */
public final class DashboardRootView extends RefLayout implements ObdManager.Listener {
    enum Mode { CONNECT, SPORT, DIAGNOSTICS }

    private final MainActivity activity;
    private final ObdManager obd;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HeaderView header;
    private final ConnectScreenView connectScreen;
    private final SportScreenView sportScreen;
    private final DiagnosticsScreenView diagnosticsScreen;
    private final ModeTabView connectTab,sportTab,diagnosticsTab;
    private final List<ObdManager.DeviceInfo> devices=new ArrayList<>();
    private ObdManager.DeviceInfo selectedDevice;
    private ObdManager.Transport selectedTransport=ObdManager.Transport.BLE;
    private ObdManager.State obdState=ObdManager.State.DISCONNECTED;
    private ObdManager.Transport activeTransport=ObdManager.Transport.AUTO;
    private ObdManager.Telemetry telemetry=new ObdManager.Telemetry();
    private ObdManager.Readiness readiness=new ObdManager.Readiness();
    private ObdManager.DtcResult dtc=new ObdManager.DtcResult();
    private String stateDetail="Tap Connect to connect";
    private String adapterName="";
    private Mode mode=Mode.CONNECT;
    private boolean hostVisible;
    private boolean autoReconnect;
    private boolean lowPower;
    private long connectedAt;

    private final Runnable freshnessTick=new Runnable(){@Override public void run(){
        if(!hostVisible)return;
        updateDynamicUi();
        if(obdState==ObdManager.State.ECU_CONNECTED) main.postDelayed(this,500L);
    }};

    public DashboardRootView(MainActivity activity){
        super(activity);this.activity=activity;setBackgroundColor(Ui.BG);prefs=activity.getSharedPreferences("civic_dashboard",Context.MODE_PRIVATE);obd=new ObdManager(activity,this);
        autoReconnect=prefs.getBoolean("auto_reconnect",true);lowPower=prefs.getBoolean("low_power_mode",false);selectedTransport=parseTransport(prefs.getString("ui_selected_transport",ObdManager.Transport.BLE.name()));
        header=new HeaderView(activity);connectScreen=new ConnectScreenView(activity);sportScreen=new SportScreenView(activity);diagnosticsScreen=new DiagnosticsScreenView(activity);
        addRef(connectScreen,0,0,1280,720);addRef(sportScreen,0,0,1280,720);addRef(diagnosticsScreen,0,0,1280,720);addRef(header,0,0,1280,105);
        connectTab=new ModeTabView(activity,"CONNECT","OBD SETUP & LINK","LINK",Ui.CONNECT);sportTab=new ModeTabView(activity,"SPORT","HIGHER STANDARDS","FLAG",Ui.SPORT);diagnosticsTab=new ModeTabView(activity,"DIAGNOSTICS","KNOW YOUR CAR","GEAR",Ui.DIAG);
        addRef(connectTab,15,615,405,90);addRef(sportTab,437,615,405,90);addRef(diagnosticsTab,859,615,405,90);
        connectTab.setOnClickListener(v->showMode(Mode.CONNECT));sportTab.setOnClickListener(v->showMode(Mode.SPORT));diagnosticsTab.setOnClickListener(v->showMode(Mode.DIAGNOSTICS));
        connectScreen.setPrefs(autoReconnect,lowPower);connectScreen.setSelectedTransport(selectedTransport);wireConnectActions();sportScreen.setPicker(this::showSensorPicker);
        restoreSportSlots();showMode(Mode.CONNECT);updateDynamicUi();
    }

    private void wireConnectActions(){connectScreen.setActions(new ConnectScreenView.Actions(){
        @Override public void scan(){scanSelected();}
        @Override public void connect(){connectSelected();}
        @Override public void disconnect(){obd.disconnect();}
        @Override public void retry(){connectSelected();}
        @Override public void showLog(){activity.showObdLog(obd.getDebugLogText(),()->{try{File f=obd.exportDebugLog();Toast.makeText(activity,"Log: "+f.getAbsolutePath(),Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(activity,"Log export failed",Toast.LENGTH_SHORT).show();}});}
        @Override public void selectTransport(ObdManager.Transport t){selectedTransport=t;prefs.edit().putString("ui_selected_transport",t.name()).apply();selectedDevice=null;connectScreen.setDevices(devices,"");if(t==ObdManager.Transport.BLUETOOTH)obd.refreshDevices();}
        @Override public void selectDevice(ObdManager.DeviceInfo d){selectedDevice=d;if(d!=null){selectedTransport=d.transport;prefs.edit().putString("ui_selected_transport",selectedTransport.name()).apply();connectScreen.setSelectedTransport(selectedTransport);connectScreen.setDevices(devices,d.key());}}
        @Override public void setAutoReconnect(boolean v){autoReconnect=v;prefs.edit().putBoolean("auto_reconnect",v).apply();}
        @Override public void setLowPower(boolean v){lowPower=v;prefs.edit().putBoolean("low_power_mode",v).apply();}
        @Override public void editTimeout(){activity.promptConnectionTimeout(prefs.getInt("connection_timeout_ms",5000),ms->{prefs.edit().putInt("connection_timeout_ms",ms).apply();obd.setConnectionTimeoutMs(ms);});}
        @Override public void editWifi(){activity.promptWifiEndpoint(prefs.getString("verified_wifi_host","192.168.0.10"),prefs.getInt("verified_wifi_port",35000),(host,port)->prefs.edit().putString("verified_wifi_host",host).putInt("verified_wifi_port",port).apply());}
    });}

    private void showMode(Mode m){mode=m;connectScreen.setVisibility(m==Mode.CONNECT?VISIBLE:GONE);sportScreen.setVisibility(m==Mode.SPORT?VISIBLE:GONE);diagnosticsScreen.setVisibility(m==Mode.DIAGNOSTICS?VISIBLE:GONE);connectTab.setActive(m==Mode.CONNECT);sportTab.setActive(m==Mode.SPORT);diagnosticsTab.setActive(m==Mode.DIAGNOSTICS);obd.setSportPriority(m==Mode.SPORT);if(m==Mode.CONNECT)header.setMode("CONNECT MODE","OBD SETUP & LINK",Ui.CONNECT);else if(m==Mode.SPORT)header.setMode("SPORT MODE","HIGHER STANDARDS",Ui.SPORT);else header.setMode("DIAGNOSTICS MODE","KNOW YOUR CAR",Ui.DIAG);updateDynamicUi();}

    private void scanSelected(){if(selectedTransport==ObdManager.Transport.WIFI){activity.promptWifiEndpoint(prefs.getString("verified_wifi_host","192.168.0.10"),prefs.getInt("verified_wifi_port",35000),(h,p)->{prefs.edit().putString("verified_wifi_host",h).putInt("verified_wifi_port",p).apply();obd.connectWifi(h,p);});return;}if(!activity.hasObdBluetoothPermissions()){activity.requestObdBluetoothPermissions();return;}if(selectedTransport==ObdManager.Transport.BLE)obd.scanBleDevices();else obd.scanClassicDevices();}
    private void connectSelected(){if(selectedTransport==ObdManager.Transport.WIFI){obd.connectWifi(prefs.getString("verified_wifi_host","192.168.0.10"),prefs.getInt("verified_wifi_port",35000));return;}if(!activity.hasObdBluetoothPermissions()){activity.requestObdBluetoothPermissions();return;}if(selectedDevice!=null&&selectedDevice.transport==selectedTransport){obd.connect(selectedDevice);return;}String vt=prefs.getString("verified_transport","");String va=prefs.getString("verified_address","");if(vt.equals(selectedTransport.name())&&!va.isEmpty()){obd.connectAuto();return;}scanSelected();Toast.makeText(activity,"Select an adapter after the scan",Toast.LENGTH_SHORT).show();}

    private void showSensorPicker(int slot,SensorType current){SensorType[] all=SensorType.values();String[] names=new String[all.length];for(int i=0;i<all.length;i++)names[i]=all[i].label+(all[i].supported(telemetry)?"":"  (N/A)");new AlertDialog.Builder(activity).setTitle("Sport widget "+(slot+1)).setSingleChoiceItems(names,current.ordinal(),(d,which)->{SensorType s=all[which];sportScreen.setSensor(slot,s);prefs.edit().putString("sport_slot_"+slot,s.name()).apply();d.dismiss();updateDynamicUi();}).setNegativeButton("Cancel",null).show();}
    private void restoreSportSlots(){SensorType[] defaults={SensorType.COOLANT,SensorType.MODULE_VOLTAGE,SensorType.THROTTLE,SensorType.LOAD,SensorType.INTAKE};for(int i=0;i<5;i++){try{sportScreen.setSensor(i,SensorType.valueOf(prefs.getString("sport_slot_"+i,defaults[i].name())));}catch(Exception e){sportScreen.setSensor(i,defaults[i]);}}}

    public void onHostStart(){hostVisible=true;main.removeCallbacks(freshnessTick);main.post(freshnessTick);if(autoReconnect&&obdState==ObdManager.State.DISCONNECTED)main.postDelayed(()->{if(hostVisible&&obdState==ObdManager.State.DISCONNECTED){String vt=prefs.getString("verified_transport","");if((vt.equals("BLE")||vt.equals("BLUETOOTH"))&&!activity.hasObdBluetoothPermissions())return;obd.connectAuto();}},900L);}
    public void onHostStop(){hostVisible=false;main.removeCallbacks(freshnessTick);/* Intentionally keep the verified OBD session alive while HOME/minimized. */}
    public void destroy(){hostVisible=false;main.removeCallbacksAndMessages(null);obd.shutdown();}
    public void onBluetoothPermissionResult(boolean granted){if(granted)scanSelected();else onState(ObdManager.State.PERMISSION_REQUIRED,"Bluetooth permission required",selectedTransport,"");}

    @Override public boolean hasBluetoothPermission(){return activity.hasObdBluetoothPermissions();}
    @Override public void onState(ObdManager.State state,String detail,ObdManager.Transport transport,String adapter){ObdManager.State previous=obdState;obdState=state==null?ObdManager.State.DISCONNECTED:state;stateDetail=detail==null?"":detail;activeTransport=transport==null?ObdManager.Transport.AUTO:transport;adapterName=adapter==null?"":adapter;if(obdState==ObdManager.State.ECU_CONNECTED&&previous!=ObdManager.State.ECU_CONNECTED)connectedAt=System.currentTimeMillis();if(obdState!=ObdManager.State.ECU_CONNECTED&&previous==ObdManager.State.ECU_CONNECTED)connectedAt=0;if(obdState!=ObdManager.State.ECU_CONNECTED){telemetry.clearLiveValues();}
        if(hostVisible&&obdState==ObdManager.State.ECU_CONNECTED){main.removeCallbacks(freshnessTick);main.postDelayed(freshnessTick,500L);}
        main.post(this::updateDynamicUi);}
    @Override public void onDevices(List<ObdManager.DeviceInfo> list){devices.clear();if(list!=null)devices.addAll(list);if(selectedDevice!=null){for(ObdManager.DeviceInfo d:devices)if(d.key().equals(selectedDevice.key())){selectedDevice=d;break;}}String key=selectedDevice==null?"":selectedDevice.key();main.post(()->connectScreen.setDevices(devices,key));}
    @Override public void onTelemetry(ObdManager.Telemetry t){telemetry=t==null?new ObdManager.Telemetry():t;main.post(this::updateDynamicUi);}
    @Override public void onReadiness(ObdManager.Readiness r){readiness=r==null?new ObdManager.Readiness():r;main.post(this::updateDynamicUi);}
    @Override public void onDtc(ObdManager.DtcResult d){dtc=d==null?new ObdManager.DtcResult():d;main.post(this::updateDynamicUi);}

    private void updateDynamicUi(){
        header.setObd(obdState,stateDetail);
        if(mode==Mode.CONNECT) connectScreen.update(obdState,stateDetail,activeTransport,adapterName,telemetry,connectedAt);
        else if(mode==Mode.SPORT) sportScreen.update(obdState,telemetry);
        else diagnosticsScreen.update(obdState,telemetry,readiness,dtc);
    }
    private static ObdManager.Transport parseTransport(String s){try{ObdManager.Transport t=ObdManager.Transport.valueOf(s);return t==ObdManager.Transport.AUTO?ObdManager.Transport.BLE:t;}catch(Exception e){return ObdManager.Transport.BLE;}}
}
