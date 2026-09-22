package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class ModeTabView extends View {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), stroke=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p=new Path();
    private final String title, subtitle, symbol;
    private final int activeColor;
    private boolean active;

    ModeTabView(Context c,String title,String subtitle,String symbol,int activeColor){super(c);this.title=title;this.subtitle=subtitle;this.symbol=symbol;this.activeColor=activeColor;setClickable(true);setFocusable(true);}
    void setActive(boolean a){if(active!=a){active=a;invalidate();}}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cut=w*.07f;
        p.reset();p.moveTo(cut,2);p.lineTo(w-cut,2);p.lineTo(w-2,h-2);p.lineTo(2,h-2);p.close();
        fill.setColor(active?darken(activeColor):Ui.PANEL);fill.setStyle(Paint.Style.FILL);c.drawPath(p,fill);
        stroke.setColor(active?activeColor:Ui.MUTED);stroke.setStrokeWidth(active?3.2f:1.8f);stroke.setStyle(Paint.Style.STROKE);c.drawPath(p,stroke);
        text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));text.setTextSize(h*.31f);text.setColor(active?Ui.WHITE:Ui.MUTED);c.drawText(symbol,w*.12f,h*.55f,text);
        text.setTextSize(h*.25f);text.setColor(active?Ui.WHITE:Ui.WHITE);c.drawText(title,w*.30f,h*.46f,text);
        text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.NORMAL));text.setTextSize(h*.16f);text.setColor(active?activeColor:Ui.MUTED);c.drawText(subtitle,w*.30f,h*.70f,text);
    }
    private static int darken(int c){return android.graphics.Color.rgb(android.graphics.Color.red(c)/5,android.graphics.Color.green(c)/5,android.graphics.Color.blue(c)/5);}
}
