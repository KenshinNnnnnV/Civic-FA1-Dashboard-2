package com.civicfa1.dashboard;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

final class Ui {
    static final int BG = Color.rgb(2, 8, 13);
    static final int PANEL = Color.rgb(4, 18, 28);
    static final int PANEL_2 = Color.rgb(7, 27, 38);
    static final int WHITE = Color.rgb(235, 248, 255);
    static final int MUTED = Color.rgb(160, 192, 205);
    static final int DIM = Color.rgb(72, 92, 104);
    static final int CONNECT = Color.rgb(42, 255, 145);
    static final int SPORT = Color.rgb(255, 48, 48);
    static final int DIAG = Color.rgb(47, 255, 125);
    static final int CYAN = Color.rgb(29, 208, 255);
    static final int GREEN = Color.rgb(31, 242, 127);
    static final int YELLOW = Color.rgb(255, 220, 20);
    static final int RED = Color.rgb(255, 67, 76);
    static final int ORANGE = Color.rgb(255, 159, 30);

    static Paint text(float sp, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(color);
        p.setTextSize(sp);
        p.setTypeface(TypeFaceHolder.get(bold));
        return p;
    }

    private static final class TypeFaceHolder {
        static final Typeface NORMAL = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        static final Typeface BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        static Typeface get(boolean bold) { return bold ? BOLD : NORMAL; }
    }

    private Ui() {}
}
