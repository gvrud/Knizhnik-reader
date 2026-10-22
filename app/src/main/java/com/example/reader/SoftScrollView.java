package com.example.reader;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

public class SoftScrollView extends ScrollView {

    public interface OnHorizontalSwipeListener {
        void onSwipeLeft();

        void onSwipeRight();
    }

    private OnHorizontalSwipeListener swipeListener;
    private float downX;
    private float downY;

    public SoftScrollView(Context context) {
        super(context);
    }

    public SoftScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SoftScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnHorizontalSwipeListener(OnHorizontalSwipeListener l) {
        this.swipeListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
        } else if (action == MotionEvent.ACTION_UP) {
            float dx = ev.getX() - downX;
            float dy = ev.getY() - downY;
            if (swipeListener != null
                    && Math.abs(dx) > Math.abs(dy) * 1.5f
                    && Math.abs(dx) > 80) {
                if (dx < 0) {
                    swipeListener.onSwipeLeft();
                } else {
                    swipeListener.onSwipeRight();
                }
                return true;
            }
        }
        return super.onTouchEvent(ev);
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