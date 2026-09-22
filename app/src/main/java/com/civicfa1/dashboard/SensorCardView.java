package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

final class SensorCardView extends NeonPanelView {
    private SensorType sensor;
    private float value=Float.NaN;
    private boolean valid, unsupported;
    private final RectF bar=new RectF(), fillBar=new RectF();
    private final Paint valuePaint=new Paint(Paint.ANTI_ALIAS_FLAG), barPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath=new Path();

    SensorCardView(Context c, SensorType sensor, int accent){super(c,"",accent);this.sensor=sensor;setClickable(true);setFocusable(true);}
    SensorType getSensor(){return sensor;}
    void setSensor(SensorType s){if(sensor!=s){sensor=s;invalidate();}}
    void setReading(float v, boolean isValid, boolean isUnsupported){
        if(Float.compare(value,v)!=0||valid!=isValid||unsupported!=isUnsupported){value=v;valid=isValid;unsupported=isUnsupported;invalidate();}}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();
        // The icon is derived from the current SensorType instead of being a static card asset.
        // Selecting another sensor therefore changes every visible part of the widget together.
        drawSensorIcon(c,w*.105f,h*.38f,Math.min(w,h)*.13f);
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

    private void drawSensorIcon(Canvas c,float cx,float cy,float s){
        iconPaint.setStyle(Paint.Style.STROKE);iconPaint.setStrokeCap(Paint.Cap.ROUND);iconPaint.setStrokeJoin(Paint.Join.ROUND);iconPaint.setStrokeWidth(Math.max(1.5f,s*.16f));
        iconPaint.setColor(unsupported?Ui.DIM:accent);
        switch(sensor){
            case COOLANT:
                c.drawLine(cx,cy-s,cx,cy+s*.28f,iconPaint);c.drawCircle(cx,cy+s*.52f,s*.28f,iconPaint);
                for(int i=-1;i<=1;i++)c.drawLine(cx-s*.78f,cy+s*(.95f+i*.18f),cx+s*.78f,cy+s*(.95f+i*.18f),iconPaint);
                break;
            case INTAKE: case MAF:
                for(int i=-1;i<=1;i++)c.drawLine(cx-s*.8f,cy+i*s*.45f,cx+s*.72f,cy+i*s*.45f,iconPaint);
                c.drawLine(cx+s*.20f,cy-s*.63f,cx+s*.80f,cy,iconPaint);c.drawLine(cx+s*.80f,cy,cx+s*.20f,cy+s*.63f,iconPaint);
                break;
            case THROTTLE:
                c.drawCircle(cx,cy,s*.78f,iconPaint);c.drawLine(cx-s*.54f,cy+s*.54f,cx+s*.54f,cy-s*.54f,iconPaint);c.drawCircle(cx,cy,s*.12f,iconPaint);
                break;
            case MODULE_VOLTAGE: case ADAPTER_VOLTAGE:
                iconPath.reset();iconPath.moveTo(cx-s*.9f,cy-s*.48f);iconPath.lineTo(cx+s*.9f,cy-s*.48f);iconPath.lineTo(cx+s*.9f,cy+s*.58f);iconPath.lineTo(cx-s*.9f,cy+s*.58f);iconPath.close();c.drawPath(iconPath,iconPaint);
                c.drawLine(cx-s*.42f,cy,cx-s*.02f,cy,iconPaint);c.drawLine(cx+s*.36f,cy-s*.19f,cx+s*.36f,cy+s*.19f,iconPaint);c.drawLine(cx+s*.17f,cy,cx+s*.55f,cy,iconPaint);
                break;
            case SHORT_FUEL_TRIM: case LONG_FUEL_TRIM: case FUEL_RATE: case FUEL_LEVEL:
                c.drawRect(cx-s*.48f,cy-s*.76f,cx+s*.32f,cy+s*.76f,iconPaint);c.drawLine(cx+s*.32f,cy-s*.48f,cx+s*.68f,cy-s*.18f,iconPaint);c.drawLine(cx+s*.68f,cy-s*.18f,cx+s*.68f,cy+s*.55f,iconPaint);c.drawLine(cx-s*.25f,cy-s*.38f,cx+s*.12f,cy-s*.38f,iconPaint);
                break;
            case TIMING:
                iconPath.reset();iconPath.moveTo(cx+s*.14f,cy-s*.94f);iconPath.lineTo(cx-s*.58f,cy+s*.05f);iconPath.lineTo(cx-s*.08f,cy+s*.05f);iconPath.lineTo(cx-s*.27f,cy+s*.93f);iconPath.lineTo(cx+s*.62f,cy-s*.23f);iconPath.lineTo(cx+s*.12f,cy-s*.23f);iconPath.close();c.drawPath(iconPath,iconPaint);
                break;
            case RPM: case SPEED: case LOAD: case MAP:
            default:
                c.drawArc(cx-s*.82f,cy-s*.82f,cx+s*.82f,cy+s*.82f,205f,130f,false,iconPaint);c.drawLine(cx,cy,cx+s*.48f,cy-s*.38f,iconPaint);c.drawCircle(cx,cy,s*.10f,iconPaint);
                break;
        }
        iconPaint.setStrokeCap(Paint.Cap.BUTT);
    }
}
