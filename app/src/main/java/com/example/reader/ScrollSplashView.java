package com.example.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class ScrollSplashView extends View {

    private final Paint scrollPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float scrollTop;
    private float scrollBottom;

    public ScrollSplashView(Context context) {
        super(context);
        init();
    }

    public ScrollSplashView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        titlePaint.setColor(Color.parseColor("#5B3A1E"));
        titlePaint.setTextSize(56f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        subPaint.setColor(Color.parseColor("#7A5A34"));
        subPaint.setTextSize(22f);
        subPaint.setTextAlign(Paint.Align.CENTER);

        rodPaint.setColor(Color.parseColor("#6B3E1C"));
        rodPaint.setStrokeWidth(24f);
        rodPaint.setStrokeCap(Paint.Cap.ROUND);

        linePaint.setColor(Color.parseColor("#8A6A42"));
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float cx = w / 2f;
        scrollTop = h * 0.30f;
        scrollBottom = h * 0.72f;
        scrollPaint.setShader(new LinearGradient(cx, scrollTop, cx, scrollBottom,
                Color.parseColor("#F3E3BF"), Color.parseColor("#E6D0A0"),
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float cx = w / 2f;
        float top = scrollTop;
        float bottom = scrollBottom;
        float paperW = w * 0.62f;
        float left = cx - paperW / 2f;
        float right = cx + paperW / 2f;

        Path paper = new Path();
        paper.moveTo(left, top);
        paper.quadTo(cx - paperW * 0.28f, top + (bottom - top) * 0.12f,
                left, bottom);
        paper.lineTo(right, bottom);
        paper.quadTo(cx + paperW * 0.28f, top + (bottom - top) * 0.12f,
                right, top);
        paper.close();
        canvas.drawPath(paper, scrollPaint);

        drawScrollLines(canvas, left, right, top, bottom, paperW);

        float rodY = top - 34f;
        canvas.drawRoundRect(new RectF(left - 26f, rodY - 12f, right + 26f, rodY + 12f), 12f, 12f, rodPaint);
        float rodY2 = bottom + 34f;
        canvas.drawRoundRect(new RectF(left - 26f, rodY2 - 12f, right + 26f, rodY2 + 12f), 12f, 12f, rodPaint);

        canvas.drawText("Книжник", cx, top + 72f, titlePaint);
        canvas.drawText("электронная библиотека", cx, top + 112f, subPaint);
    }

    private void drawScrollLines(Canvas canvas, float left, float right,
            float top, float bottom, float paperW) {
        int lines = 7;
        float startX = left + paperW * 0.12f;
        float endX = right - paperW * 0.12f;
        float spacing = (bottom - top - 140f) / (lines - 1);
        for (int i = 0; i < lines; i++) {
            float y = top + 150f + i * spacing;
            Path line = new Path();
            line.moveTo(startX, y);
            line.quadTo((startX + endX) / 2f, y + 6f, endX, y);
            canvas.drawPath(line, linePaint);
        }
    }
}
