package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

final class DeviceListView extends NeonPanelView {
    interface Listener { void onDeviceSelected(ObdManager.DeviceInfo device); }
    private final List<ObdManager.DeviceInfo> devices=new ArrayList<>();
    private String selectedKey=""; private Listener listener;
    DeviceListView(Context c,int accent){super(c,"RECENT DEVICES",accent);setClickable(true);}
    void setListener(Listener l){listener=l;}
    void setDevices(List<ObdManager.DeviceInfo> list){devices.clear();if(list!=null)devices.addAll(list);invalidate();}
    void setSelectedKey(String key){key=key==null?"":key;if(!selectedKey.equals(key)){selectedKey=key;invalidate();}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);Paint p=text;float top=getHeight()*.27f;float row=(getHeight()-top-6)/Math.max(1,Math.min(4,devices.size()==0?4:devices.size()));p.setTextSize(Math.max(11f,getHeight()*.09f));for(int i=0;i<Math.min(4,devices.size());i++){ObdManager.DeviceInfo d=devices.get(i);float y=top+i*row;boolean sel=d.key().equals(selectedKey);fill.setStyle(Paint.Style.FILL);fill.setColor(sel?accent:Ui.PANEL_2);fill.setAlpha(sel?55:110);c.drawRect(5,y,getWidth()-5,y+row-2,fill);p.setColor(sel?Ui.WHITE:Ui.MUTED);p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,sel?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));c.drawText((sel?"●  ":"○  ")+d.name,14,y+row*.55f,p);p.setTextAlign(Paint.Align.RIGHT);p.setColor(Ui.MUTED);c.drawText(d.address,getWidth()-12,y+row*.55f,p);p.setTextAlign(Paint.Align.LEFT);}if(devices.isEmpty()){p.setColor(Ui.DIM);c.drawText("No devices yet — Scan to discover adapters",14,top+row*.55f,p);}}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float top=getHeight()*.27f;float row=(getHeight()-top-6)/Math.max(1,Math.min(4,devices.size()==0?4:devices.size()));int idx=(int)((e.getY()-top)/row);if(idx>=0&&idx<Math.min(4,devices.size())){ObdManager.DeviceInfo d=devices.get(idx);selectedKey=d.key();invalidate();if(listener!=null)listener.onDeviceSelected(d);}return true;}
}
