package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class ActionButtonView extends NeonPanelView {
    private String label;
    ActionButtonView(Context c,String label,int accent){super(c,"",accent);this.label=label;setClickable(true);setFocusable(true);}
    void setLabel(String s){if(!label.equals(s)){label=s;invalidate();}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);Paint p=text;p.setTextAlign(Paint.Align.CENTER);p.setColor(isEnabled()?Ui.WHITE:Ui.DIM);p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));p.setTextSize(Math.max(13f,getHeight()*.30f));c.drawText(label,getWidth()/2f,getHeight()*.60f,p);p.setTextAlign(Paint.Align.LEFT);}
}
