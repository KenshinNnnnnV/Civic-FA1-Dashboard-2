package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class HeaderView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String mode="CONNECT MODE", sub="OBD SETUP & LINK", obd="OBD: DISCONNECTED", detail="ALL SYSTEMS OFFLINE";
    private int accent=Ui.CONNECT, statusColor=Ui.RED;

    HeaderView(Context c){super(c);}
    void setMode(String m,String s,int a){if(!mode.equals(m)||!sub.equals(s)||accent!=a){mode=m;sub=s;accent=a;invalidate();}}
    void setObd(ObdManager.State state,String d){String n;
        if(state==ObdManager.State.ECU_CONNECTED)n="OBD: CONNECTED"; else if(state==ObdManager.State.SEARCHING)n="OBD: SEARCHING"; else if(state==ObdManager.State.CONNECTING||state==ObdManager.State.INITIALIZING||state==ObdManager.State.ECU_CONNECTING)n="OBD: CONNECTING"; else n="OBD: DISCONNECTED";
        int sc=state==ObdManager.State.ECU_CONNECTED?Ui.GREEN:(state==ObdManager.State.ERROR?Ui.RED:state==ObdManager.State.DISCONNECTED?Ui.RED:Ui.YELLOW);
        String nd=(d==null||d.isEmpty())?(state==ObdManager.State.ECU_CONNECTED?"ALL SYSTEMS NORMAL":"ALL SYSTEMS OFFLINE"):d.toUpperCase();
        if(!obd.equals(n)||!detail.equals(nd)||statusColor!=sc){obd=n;detail=nd;statusColor=sc;invalidate();}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();c.drawColor(Ui.BG);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);p.setColor(accent);c.drawLine(0,h-2,w,h-2,p);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));p.setColor(Ui.WHITE);p.setTextSize(h*.32f);c.drawText("HONDA  |  CIVIC FA1",24,h*.42f,p);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.NORMAL));p.setColor(Ui.MUTED);p.setTextSize(h*.18f);c.drawText("i-VTEC 1.8L R18A",24,h*.69f,p);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));p.setTextSize(h*.34f);p.setColor(accent);p.setTextAlign(Paint.Align.CENTER);c.drawText(mode,w*.54f,h*.43f,p);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.NORMAL));p.setTextSize(h*.18f);c.drawText(sub,w*.54f,h*.69f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));p.setTextSize(h*.22f);p.setColor(statusColor);c.drawText(obd,w*.75f,h*.40f,p);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.NORMAL));p.setTextSize(h*.14f);p.setColor(Ui.MUTED);String shortDetail=detail.length()>30?detail.substring(0,30):detail;c.drawText(shortDetail,w*.75f,h*.66f,p);
    }
}
