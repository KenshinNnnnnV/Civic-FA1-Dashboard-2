package com.civicfa1.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Arrays;

final class InfoPanelView extends NeonPanelView {
    private String[] lines=new String[0];
    InfoPanelView(Context c,String title,int accent){super(c,title,accent);}
    void setLines(String... values){String[] v=values==null?new String[0]:values; if(!Arrays.equals(lines,v)){lines=v;invalidate();}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);Paint p=text;p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.NORMAL));p.setTextSize(Math.max(11f,getHeight()*.105f));p.setColor(Ui.MUTED);float y=getHeight()*.34f;float step=Math.max(16f,getHeight()*.13f);for(String line:lines){if(line!=null&&!line.isEmpty())c.drawText(line,getWidth()*.06f,y,p);y+=step;if(y>getHeight()*.94f)break;}}
}
