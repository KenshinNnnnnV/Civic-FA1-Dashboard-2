package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class SensorCardView extends NeonPanelView {
    private SensorType sensor;
    private float value=Float.NaN;
    private boolean valid, unsupported;
    private final RectF bar=new RectF(), fillBar=new RectF();
    private final Paint valuePaint=new Paint(Paint.ANTI_ALIAS_FLAG), barPaint=new Paint(Paint.ANTI_ALIAS_FLAG);

    SensorCardView(Context c, SensorType sensor, int accent){super(c,"",accent);this.sensor=sensor;setClickable(true);setFocusable(true);}
    SensorType getSensor(){return sensor;}
    void setSensor(SensorType s){if(sensor!=s){sensor=s;invalidate();}}
    void setReading(float v, boolean isValid, boolean isUnsupported){
        if(Float.compare(value,v)!=0||valid!=isValid||unsupported!=isUnsupported){value=v;valid=isValid;unsupported=isUnsupported;invalidate();}}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();
        valuePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));valuePaint.setColor(Ui.WHITE);valuePaint.setTextSize(h*.16f);c.drawText(sensor.label,w*.19f,h*.27f,valuePaint);
        valuePaint.setTextSize(h*.29f);String v=unsupported?"N/A":(valid?sensor.format(value):"--");c.drawText(v,w*.19f,h*.57f,valuePaint);
        valuePaint.setTextSize(h*.14f);valuePaint.setColor(Ui.MUTED);c.drawText(unsupported?"":sensor.unit,w*.64f,h*.57f,valuePaint);
        float left=w*.10f,right=w*.90f,top=h*.70f,bottom=h*.80f;bar.set(left,top,right,bottom);
        barPaint.setStyle(Paint.Style.STROKE);barPaint.setStrokeWidth(1.5f);barPaint.setColor(Ui.MUTED);c.drawRoundRect(bar,h*.05f,h*.05f,barPaint);
        if(valid&&!unsupported&&!Float.isNaN(value)){
            float f=(value-sensor.min)/(sensor.max-sensor.min);f=Math.max(0f,Math.min(1f,f));barPaint.setStyle(Paint.Style.FILL);barPaint.setColor(accent);fillBar.set(left+2,top+2,left+2+(right-left-4)*f,bottom-2);c.drawRoundRect(fillBar,h*.04f,h*.04f,barPaint);
        }
        valuePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.NORMAL));valuePaint.setTextSize(h*.11f);valuePaint.setColor(Ui.MUTED);c.drawText(sensor.format(sensor.min),left,h*.94f,valuePaint);valuePaint.setTextAlign(Paint.Align.RIGHT);c.drawText(sensor.format(sensor.max),right,h*.94f,valuePaint);valuePaint.setTextAlign(Paint.Align.LEFT);
    }
}
