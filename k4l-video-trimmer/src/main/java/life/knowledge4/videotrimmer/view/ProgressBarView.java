/*
 * MIT License
 *
 * Copyright (c) 2016 Knowledge, education for life.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package life.knowledge4.videotrimmer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import life.knowledge4.videotrimmer.R;
import life.knowledge4.videotrimmer.interfaces.OnProgressVideoListener;
import life.knowledge4.videotrimmer.interfaces.OnRangeSeekBarListener;

public class ProgressBarView extends View implements OnRangeSeekBarListener, OnProgressVideoListener {

    private int mProgressHeight;
    private int mViewWidth;

    private final Paint mBackgroundColor = new Paint();
    private final Paint mProgressColor = new Paint();

    private Rect mBackgroundRect;
    private Rect mProgressRect;

    public ProgressBarView(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgressBarView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int lineProgress = ContextCompat.getColor(getContext(), R.color.progress_color);
        int lineBackground = ContextCompat.getColor(getContext(), R.color.background_progress_color);

        mProgressHeight = getContext().getResources().getDimensionPixelOffset(R.dimen.progress_video_line_height);

        mBackgroundColor.setAntiAlias(true);
        mBackgroundColor.setColor(lineBackground);

        mProgressColor.setAntiAlias(true);
        mProgressColor.setColor(lineProgress);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int minW = getPaddingLeft() + getPaddingRight() + getSuggestedMinimumWidth();
        mViewWidth = resolveSizeAndState(minW, widthMeasureSpec, 1);

        int minH = getPaddingBottom() + getPaddingTop() + mProgressHeight;
        int viewHeight = resolveSizeAndState(minH, heightMeasureSpec, 1);

        setMeasuredDimension(mViewWidth, viewHeight);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        drawLineBackground(canvas);
        drawLineProgress(canvas);
    }

    private void drawLineBackground(@NonNull Canvas canvas) {
        if (mBackgroundRect != null) {
            canvas.drawRect(mBackgroundRect, mBackgroundColor);
        }
    }

    private void drawLineProgress(@NonNull Canvas canvas) {
        if (mProgressRect != null) {
            canvas.drawRect(mProgressRect, mProgressColor);
        }
    }

    @Override
    public void onCreate(RangeSeekBarView rangeSeekBarView, int index, float value) {
        updateBackgroundRect(index, value);
    }

    @Override
    public void onSeek(RangeSeekBarView rangeSeekBarView, int index, float value) {
        updateBackgroundRect(index, value);
    }

    @Override
    public void onSeekStart(RangeSeekBarView rangeSeekBarView, int index, float value) {
        updateBackgroundRect(index, value);
    }

    @Override
    public void onSeekStop(RangeSeekBarView rangeSeekBarView, int index, float value) {
        updateBackgroundRect(index, value);
    }

    private void updateBackgroundRect(int index, float value) {

        if (mBackgroundRect == null) {
            mBackgroundRect = new Rect(0, 0, mViewWidth, mProgressHeight);
        }

        int newValue = (int) ((mViewWidth * value) / 100);
        if (index == 0) {
            mBackgroundRect = new Rect(newValue, mBackgroundRect.top, mBackgroundRect.right, mBackgroundRect.bottom);
        } else {
            mBackgroundRect = new Rect(mBackgroundRect.left, mBackgroundRect.top, newValue, mBackgroundRect.bottom);
        }

        updateProgress(0, 0, 0.0f);
    }

    @Override
    public void updateProgress(int time, int max, float scale) {

        if (scale == 0) {
            mProgressRect = new Rect(0, mBackgroundRect.top, 0, mBackgroundRect.bottom);
        } else {
            int newValue = (int) ((mViewWidth * scale) / 100);
            mProgressRect = new Rect(mBackgroundRect.left, mBackgroundRect.top, newValue, mBackgroundRect.bottom);
        }

        invalidate();
    }
}
