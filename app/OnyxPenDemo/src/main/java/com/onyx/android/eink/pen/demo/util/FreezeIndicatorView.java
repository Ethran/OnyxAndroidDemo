package com.onyx.android.eink.pen.demo.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * A diagnostic overlay that draws a continuously updating millisecond timer and a
 * black rectangle that bounces horizontally across the top of the screen.
 *
 * As long as the screen keeps refreshing, the timer counts up and the rectangle
 * moves. When a demo freezes the screen (e.g. while pausing/resuming raw drawing
 * or during an EPD refresh), both visibly stop, making the freeze easy to spot.
 */
public class FreezeIndicatorView extends View {

    private static final long START = SystemClock.elapsedRealtime();
    private static final float TEXT_SIZE_PX = 72f;
    private static final float BAR_HEIGHT_PX = 60f;
    private static final float BAR_WIDTH_PX = 120f;
    private static final float SPEED_PX_PER_MS = 0.8f;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FreezeIndicatorView(Context context) {
        super(context);
        // Transparent + non-interactive so it never hides or blocks the buttons underneath.
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(TEXT_SIZE_PX);
        textPaint.setFakeBoldText(true);
        barPaint.setColor(Color.BLACK);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        // Let all touches fall through to the views below.
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long elapsed = SystemClock.elapsedRealtime() - START;
        canvas.drawText(elapsed + " ms", 16f, TEXT_SIZE_PX, textPaint);

        int width = getWidth();
        float travel = width - BAR_WIDTH_PX;
        if (travel > 0) {
            // ping-pong position based on elapsed time
            float cycle = travel * 2f;
            float pos = (elapsed * SPEED_PX_PER_MS) % cycle;
            float x = pos <= travel ? pos : cycle - pos;
            float top = getHeight() - BAR_HEIGHT_PX;
            canvas.drawRect(x, top, x + BAR_WIDTH_PX, top + BAR_HEIGHT_PX, barPaint);
        }

        // keep animating
        postInvalidateOnAnimation();
    }

    /**
     * Attach a freeze indicator strip to the top of the given activity's content view.
     */
    public static void attach(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        FreezeIndicatorView view = new FreezeIndicatorView(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (TEXT_SIZE_PX + BAR_HEIGHT_PX + 24f),
                Gravity.TOP);
        content.addView(view, params);
    }
}
