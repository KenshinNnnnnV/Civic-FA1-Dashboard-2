package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

final class TransportButtonView extends NeonPanelView {
    private final String name; private boolean selected;
    TransportButtonView(Context c,String name,int accent){super(c,"",accent);this.name=name;setClickable(true);setFocusable(true);}
    void setSelectedTransport(boolean s){if(selected!=s){selected=s;setSelectedState(s);invalidate();}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);Paint p=text;p.setTextAlign(Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));p.setColor(selected?accent:Ui.WHITE);p.setTextSize(getHeight()*.25f);c.drawText(name,getWidth()/2f,getHeight()*.58f,p);p.setTextAlign(Paint.Align.LEFT);}
}
