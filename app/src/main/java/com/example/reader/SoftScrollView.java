package com.example.reader;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

public class SoftScrollView extends ScrollView {

    public SoftScrollView(Context context) {
        super(context);
    }

    public SoftScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SoftScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void fling(int velocityY) {
        int max = 1500;
        int v = velocityY;
        if (v > max) {
            v = max;
        } else if (v < -max) {
            v = -max;
        }
        super.fling(v);
    }
}