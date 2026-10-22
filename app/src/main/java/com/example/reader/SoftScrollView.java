package com.example.reader;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

public class SoftScrollView extends ScrollView {

    public interface OnHorizontalSwipeListener {
        void onSwipeLeft();

        void onSwipeRight();
    }

    private OnHorizontalSwipeListener swipeListener;
    private float downX;
    private float downY;
    private boolean horizontalDrag;
    private final int touchSlop;

    public SoftScrollView(Context context) {
        this(context, null);
    }

    public SoftScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SoftScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnHorizontalSwipeListener(OnHorizontalSwipeListener l) {
        this.swipeListener = l;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            horizontalDrag = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX;
            float dy = ev.getY() - downY;
            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > touchSlop) {
                horizontalDrag = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (horizontalDrag) {
            int action = ev.getAction();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                horizontalDrag = false;
                float dx = ev.getX() - downX;
                if (swipeListener != null) {
                    if (dx < 0) {
                        swipeListener.onSwipeLeft();
                    } else {
                        swipeListener.onSwipeRight();
                    }
                }
                return true;
            }
            return true;
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