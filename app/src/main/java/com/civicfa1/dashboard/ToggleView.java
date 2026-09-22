package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class ToggleView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rail=new RectF();
    private final String label;private boolean checked;private int accent;
    ToggleView(Context c,String label,int accent,boolean checked){super(c);this.label=label;this.accent=accent;this.checked=checked;setClickable(true);setFocusable(true);setOnClickListener(v->setChecked(!this.checked));}
    boolean isChecked(){return checked;} void setChecked(boolean v){if(checked!=v){checked=v;invalidate();}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();text.setColor(Ui.WHITE);text.setTextSize(h*.40f);text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.NORMAL));c.drawText(label,2,h*.64f,text);float rw=h*1.25f,rh=h*.46f,l=w-rw-2,t=(h-rh)/2;rail.set(l,t,l+rw,t+rh);p.setStyle(Paint.Style.FILL);p.setColor(checked?accent:Ui.DIM);c.drawRoundRect(rail,rh/2,rh/2,p);p.setColor(Ui.WHITE);float r=rh*.40f;float cx=checked?rail.right-rh/2:rail.left+rh/2;c.drawCircle(cx,h/2f,r,p);}
}
