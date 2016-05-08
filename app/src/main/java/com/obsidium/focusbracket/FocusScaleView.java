package com.obsidium.focusbracket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class FocusScaleView extends View
{
    private static final int BORDER_WIDTH = 2;

    private final Paint m_paint = new Paint();

    private int         m_max;
    private int         m_min;
    private int         m_cur;

    public FocusScaleView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        m_paint.setAntiAlias(false);
        m_paint.setStrokeWidth(BORDER_WIDTH);
    }

    public void setMaxPosition(int max)
    {
        if (max > 0)
            m_max = max;
        invalidate();
    }

    public void setMinPosition(int min)
    {
        if (min < m_max)
            m_min = min;
        invalidate();
    }

    public void setCurPosition(int pos)
    {
        if (pos >= 0 && pos <= m_max)
            m_cur = pos;
        invalidate();
    }

    public void onDraw(Canvas canvas)
    {
        canvas.drawARGB(0, 0, 0, 0);
        if (m_max != 0)
        {
            final float w = getWidth();
            final float h = getHeight();
            // Draw frame
            m_paint.setARGB(100, 255, 255, 255);
            m_paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(0, 0, w, h, m_paint);
            // Draw bar
            final float w2 = w - BORDER_WIDTH * 2;
            if (m_cur > m_min)
                m_paint.setARGB(150, 0, 255, 0);
            else
                m_paint.setARGB(150, 255, 0, 0);
            m_paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH + (float)m_cur / (float)m_max * w2, h - BORDER_WIDTH, m_paint);
        }
    }
}
