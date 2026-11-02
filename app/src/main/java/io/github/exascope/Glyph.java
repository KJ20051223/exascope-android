package io.github.exascope;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Small original vector icon set; no downloaded assets or runtime font requests. */
final class Glyph extends View {
    static final int SEARCH = 0, SETTINGS = 1, ARROW = 2, BOOK = 3;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    Glyph(Context context, int kind, int color) {
        super(context); this.kind = kind;
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save(); canvas.translate(getWidth() / 2f, getHeight() / 2f);
        float scale = Math.min(getWidth(), getHeight()) / 28f;
        canvas.scale(scale, scale);
        if (kind == SEARCH) {
            canvas.drawCircle(-2, -2, 7, paint); canvas.drawLine(3, 3, 9, 9, paint);
        } else if (kind == SETTINGS) {
            canvas.drawLine(-9,-6,9,-6,paint); canvas.drawLine(-9,6,9,6,paint);
            canvas.drawCircle(-3,-6,2.5f,paint); canvas.drawCircle(4,6,2.5f,paint);
        } else if (kind == ARROW) {
            canvas.drawLine(-8,0,8,0,paint); canvas.drawLine(-8,0,-2,-6,paint); canvas.drawLine(-8,0,-2,6,paint);
        } else {
            canvas.drawRoundRect(-8,-9,8,9,2,2,paint); canvas.drawLine(-4,-3,4,-3,paint);
            canvas.drawLine(-4,2,4,2,paint); canvas.drawLine(-4,6,1,6,paint);
        }
        canvas.restore();
    }
}
