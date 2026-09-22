package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Independent native panel. It owns its own rendering/state and never draws over another panel. */
class NeonPanelView extends View {
    protected final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), stroke=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Path path=new Path();
    protected int accent=Ui.CYAN;
    protected String title="";
    private boolean selected;

    NeonPanelView(Context c) { super(c); setClickable(false); }
    NeonPanelView(Context c, String title, int accent) { this(c); this.title=title; this.accent=accent; }

    public void setAccent(int c) { if(accent!=c){accent=c; invalidate();} }
    public void setSelectedState(boolean v) { if(selected!=v){selected=v; invalidate();} }
    public void setTitle(String t){ t=t==null?"":t; if(!title.equals(t)){title=t;invalidate();} }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight(), cut=Math.min(16f, w*.06f);
        path.reset(); path.moveTo(cut,1); path.lineTo(w-cut,1); path.lineTo(w-1,cut); path.lineTo(w-1,h-cut); path.lineTo(w-cut,h-1); path.lineTo(cut,h-1); path.lineTo(1,h-cut); path.lineTo(1,cut); path.close();
        fill.setStyle(Paint.Style.FILL); fill.setColor(Ui.PANEL); fill.setAlpha(isPressed()?245:230); c.drawPath(path, fill);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(selected?3f:1.6f); stroke.setColor(accent); stroke.setAlpha(selected||isPressed()?255:205); c.drawPath(path, stroke);
        if(!title.isEmpty()){
            text.setColor(Ui.WHITE); text.setTextSize(Math.max(14f,h*.15f)); text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.BOLD));
            c.drawText(title, Math.max(14f,w*.04f), Math.max(22f,h*.20f), text);
        }
    }
}
