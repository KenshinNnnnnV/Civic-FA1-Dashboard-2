package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class TachometerView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc=new RectF();
    private float rpm=Float.NaN,speed=Float.NaN; private boolean rpmValid,speedValid;
    TachometerView(Context c){super(c);}
    void setData(float r,boolean rv,float s,boolean sv){if(Float.compare(rpm,r)!=0||rpmValid!=rv||Float.compare(speed,s)!=0||speedValid!=sv){rpm=r;rpmValid=rv;speed=s;speedValid=sv;invalidate();}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();float cx=w/2f,cy=h*.47f,r=Math.min(w,h)*.40f;arc.set(cx-r,cy-r,cx+r,cy+r);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeWidth(Math.max(12f,r*.10f));p.setColor(android.graphics.Color.rgb(35,43,48));c.drawArc(arc,135,270,false,p);
        if(rpmValid&&!Float.isNaN(rpm)){
            float clamped=Math.max(0,Math.min(8000,rpm));int segments=Math.max(1,(int)Math.ceil(clamped/250f));for(int i=0;i<segments;i++){float from=i*250f,to=Math.min(clamped,(i+1)*250f);float start=135f+(from/8000f)*270f,sweep=((to-from)/8000f)*270f;float mid=(from+to)/2f;p.setColor(mid<=4000?Ui.GREEN:(mid<=6500?Ui.YELLOW:Ui.RED));c.drawArc(arc,start,sweep+0.4f,false,p);}}
        p.setStrokeWidth(2f);p.setColor(Ui.MUTED);c.drawArc(arc,135,270,false,p);
        text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));text.setColor(Ui.WHITE);text.setTextSize(r*.16f);
        for(int i=0;i<=8;i++){double a=Math.toRadians(135+(i/8f)*270);float tx=cx+(float)Math.cos(a)*r*.78f,ty=cy+(float)Math.sin(a)*r*.78f+text.getTextSize()*.35f;c.drawText(Integer.toString(i),tx,ty,text);}
        text.setTextSize(r*.12f);text.setColor(Ui.MUTED);c.drawText("i-VTEC  x1000 RPM",cx,cy-r*.10f,text);
        text.setTextSize(r*.32f);text.setColor(Ui.WHITE);c.drawText(rpmValid&&!Float.isNaN(rpm)?String.format(java.util.Locale.US,"%.0f",rpm):"--",cx,cy+r*.18f,text);
        text.setTextSize(r*.13f);text.setColor(Ui.CYAN);c.drawText("RPM",cx,cy+r*.34f,text);
        text.setTextSize(r*.12f);text.setColor(Ui.MUTED);c.drawText("SPEED",cx,cy+r*.54f,text);text.setTextSize(r*.20f);text.setColor(Ui.WHITE);c.drawText(speedValid&&!Float.isNaN(speed)?String.format(java.util.Locale.US,"%.0f km/h",speed):"-- km/h",cx,cy+r*.72f,text);
    }
}
