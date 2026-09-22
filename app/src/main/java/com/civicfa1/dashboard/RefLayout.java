package com.civicfa1.dashboard;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Fixed logical 1280x720 coordinate system scaled uniformly to the actual head-unit surface. */
public class RefLayout extends ViewGroup {
    public static final int REF_W = 1280;
    public static final int REF_H = 720;

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public final float x, y, w, h;
        public LayoutParams(float x, float y, float w, float h) {
            super(0, 0);
            this.x=x; this.y=y; this.w=w; this.h=h;
        }
    }

    public RefLayout(Context context) { super(context); setClipChildren(false); setClipToPadding(false); }

    public void addRef(View child, float x, float y, float w, float h) {
        addView(child, new LayoutParams(x,y,w,h));
    }

    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams; }
    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(0,0,REF_W,REF_H); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        float s = Math.min(width/(float)REF_W, height/(float)REF_H);
        for (int i=0;i<getChildCount();i++) {
            View v=getChildAt(i);
            LayoutParams lp=(LayoutParams)v.getLayoutParams();
            int cw=Math.max(1, Math.round(lp.w*s));
            int ch=Math.max(1, Math.round(lp.h*s));
            v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float s = Math.min((r-l)/(float)REF_W, (b-t)/(float)REF_H);
        float ox=((r-l)-REF_W*s)/2f;
        float oy=((b-t)-REF_H*s)/2f;
        for (int i=0;i<getChildCount();i++) {
            View v=getChildAt(i);
            LayoutParams lp=(LayoutParams)v.getLayoutParams();
            int left=Math.round(ox+lp.x*s), top=Math.round(oy+lp.y*s);
            v.layout(left, top, left+v.getMeasuredWidth(), top+v.getMeasuredHeight());
        }
    }
}
