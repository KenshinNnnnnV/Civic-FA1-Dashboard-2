package com.civicfa1.dashboard;

import android.content.Context;
import android.view.View;
import java.util.Collections;
import java.util.List;

final class ConnectScreenView extends RefLayout {
    interface Actions {
        void scan(); void connect(); void disconnect(); void retry(); void showLog();
        void selectTransport(ObdManager.Transport t); void selectDevice(ObdManager.DeviceInfo d);
        void setAutoReconnect(boolean v); void setLowPower(boolean v); void editTimeout(); void editWifi();
    }
    private final int accent=Ui.CONNECT;
    final InfoPanelView adapter, ecu, status, diagnostics, transportInfo;
    final DeviceListView devices;
    final ToggleView autoReconnect, lowPower;
    final TransportButtonView ble, classicBt, wifi;
    final ActionButtonView scan, connect, disconnect, retry, viewLog, select;
    private ObdManager.Transport selectedTransport=ObdManager.Transport.BLE;
    private Actions actions;

    ConnectScreenView(Context c){super(c);setBackgroundColor(Ui.BG);
        adapter=new InfoPanelView(c,"OBD ADAPTER",accent);ecu=new InfoPanelView(c,"ECU LINK",accent);status=new InfoPanelView(c,"CONNECTION STATUS",accent);diagnostics=new InfoPanelView(c,"CONNECTION DIAGNOSTICS",accent);transportInfo=new InfoPanelView(c,"TRANSPORT OPTIONS",accent);
        addRef(adapter,20,150,410,145);addRef(ecu,440,150,400,145);addRef(status,850,310,410,175);addRef(diagnostics,850,495,410,115);addRef(transportInfo,440,495,400,115);
        InfoPanelView connBox=new InfoPanelView(c,"CONNECTION TYPE",accent);addRef(connBox,850,150,410,145);
        ble=new TransportButtonView(c,"BLE 4.0",accent);classicBt=new TransportButtonView(c,"BT 3.0",accent);wifi=new TransportButtonView(c,"Wi-Fi",accent);addRef(ble,865,205,120,70);addRef(classicBt,995,205,120,70);addRef(wifi,1125,205,120,70);
        InfoPanelView actionsBox=new InfoPanelView(c,"DEVICE ACTIONS",accent);addRef(actionsBox,20,305,410,175);
        scan=new ActionButtonView(c,"SCAN",accent);select=new ActionButtonView(c,"SELECT",accent);connect=new ActionButtonView(c,"CONNECT",accent);disconnect=new ActionButtonView(c,"DISCONNECT",accent);retry=new ActionButtonView(c,"RETRY",accent);viewLog=new ActionButtonView(c,"VIEW LOG",accent);
        addRef(scan,35,350,185,34);addRef(select,230,350,185,34);addRef(connect,35,392,185,34);addRef(disconnect,230,392,185,34);addRef(retry,35,434,185,34);addRef(viewLog,230,434,185,34);
        InfoPanelView settingsBox=new InfoPanelView(c,"SETTINGS",accent);addRef(settingsBox,440,305,400,175);autoReconnect=new ToggleView(c,"Auto reconnect",accent,true);lowPower=new ToggleView(c,"Low power mode",accent,false);addRef(autoReconnect,462,349,350,34);addRef(lowPower,462,394,350,34);ActionButtonView timeout=new ActionButtonView(c,"CONNECTION TIMEOUT",accent);ActionButtonView wifiEdit=new ActionButtonView(c,"WI-FI ENDPOINT",accent);addRef(timeout,462,436,170,32);addRef(wifiEdit,642,436,170,32);
        devices=new DeviceListView(c,accent);addRef(devices,20,490,410,120);
        ble.setOnClickListener(v->{selectedTransport=ObdManager.Transport.BLE;syncTransport();if(actions!=null)actions.selectTransport(selectedTransport);});classicBt.setOnClickListener(v->{selectedTransport=ObdManager.Transport.BLUETOOTH;syncTransport();if(actions!=null)actions.selectTransport(selectedTransport);});wifi.setOnClickListener(v->{selectedTransport=ObdManager.Transport.WIFI;syncTransport();if(actions!=null)actions.selectTransport(selectedTransport);});
        scan.setOnClickListener(v->{if(actions!=null)actions.scan();});select.setOnClickListener(v->{if(actions!=null)actions.scan();});connect.setOnClickListener(v->{if(actions!=null)actions.connect();});disconnect.setOnClickListener(v->{if(actions!=null)actions.disconnect();});retry.setOnClickListener(v->{if(actions!=null)actions.retry();});viewLog.setOnClickListener(v->{if(actions!=null)actions.showLog();});timeout.setOnClickListener(v->{if(actions!=null)actions.editTimeout();});wifiEdit.setOnClickListener(v->{if(actions!=null)actions.editWifi();});
        autoReconnect.setOnClickListener(v->{autoReconnect.setChecked(!autoReconnect.isChecked());if(actions!=null)actions.setAutoReconnect(autoReconnect.isChecked());});lowPower.setOnClickListener(v->{lowPower.setChecked(!lowPower.isChecked());if(actions!=null)actions.setLowPower(lowPower.isChecked());});devices.setListener(d->{if(actions!=null)actions.selectDevice(d);});syncTransport();
    }
    void setActions(Actions a){actions=a;}
    void setPrefs(boolean auto,boolean low){autoReconnect.setChecked(auto);lowPower.setChecked(low);}
    void setSelectedTransport(ObdManager.Transport t){selectedTransport=t==null?ObdManager.Transport.BLE:t;syncTransport();}
    private void syncTransport(){ble.setSelectedTransport(selectedTransport==ObdManager.Transport.BLE);classicBt.setSelectedTransport(selectedTransport==ObdManager.Transport.BLUETOOTH);wifi.setSelectedTransport(selectedTransport==ObdManager.Transport.WIFI);}
    void setDevices(List<ObdManager.DeviceInfo> list,String selectedKey){devices.setDevices(list==null? Collections.emptyList():list);devices.setSelectedKey(selectedKey);}
    void update(ObdManager.State state,String detail,ObdManager.Transport active,String adapterName,ObdManager.Telemetry t,long connectedMs){
        boolean ecuConnected=state==ObdManager.State.ECU_CONNECTED;String device=(adapterName==null||adapterName.isEmpty())?"--":adapterName;
        adapter.setLines("Device:  "+device,"Transport:  "+label(active),"Status:  "+state.name().replace('_',' '),"Firmware:  "+empty(t.adapterVersion),"Address:  --");
        ecu.setLines("Protocol:  "+empty(t.protocol),"ECU Status:  "+(ecuConnected?"CONNECTED":"--"),"Supported PIDs:  "+(t.capabilitiesKnown?t.supportedPids.size():0),"Response:  "+(ecuConnected?"LIVE":"--"),"VIN:  --");
        status.setLines("State:  "+state.name().replace('_',' '),"Detail:  "+shorten(detail,34),"Transport:  "+label(active),"Session:  "+(connectedMs>0?formatElapsed(connectedMs):"--"),"Signal / packets:  --");
        diagnostics.setLines("Adapter detected:  "+yes(state.ordinal()>=ObdManager.State.ADAPTER_FOUND.ordinal()),"ECU responding:  "+yes(ecuConnected),"Data stream active:  "+yes(ecuConnected));
        transportInfo.setLines("Selected:  "+label(selectedTransport),"Active:  "+label(active),"AUTO uses saved verified adapter only");
    }
    private static String yes(boolean v){return v?"READY":"PENDING";}private static String empty(String s){return s==null||s.trim().isEmpty()?"--":s;}private static String label(ObdManager.Transport t){if(t==null)return"--";if(t==ObdManager.Transport.BLUETOOTH)return"BT 3.0";if(t==ObdManager.Transport.BLE)return"BLE 4.0";return t.name();}private static String shorten(String s,int n){if(s==null||s.isEmpty())return"--";return s.length()>n?s.substring(0,n):s;}private static String formatElapsed(long ms){long sec=Math.max(0,(System.currentTimeMillis()-ms)/1000);return String.format(java.util.Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
}
