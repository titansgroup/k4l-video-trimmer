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
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import life.knowledge4.videotrimmer.R;
import life.knowledge4.videotrimmer.interfaces.OnRangeSeekBarListener;

public class RangeSeekBarView extends View {

    private static final String TAG = RangeSeekBarView.class.getSimpleName();

    private int mHeightTimeLine;
    private List<Thumb> mThumbs;
    private List<OnRangeSeekBarListener> mListeners;
    private float mMaxWidth;
    private float mThumbWidth;
    private float mThumbHeight;
    private int mViewWidth;
    private float mPixelRangeMin;
    private float mPixelRangeMax;
    private float mScaleRangeMax;
    private boolean mFirstRun;

    private final Paint mShadow = new Paint();
    private final Paint mLine = new Paint();

    public RangeSeekBarView(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RangeSeekBarView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mThumbs = Thumb.initThumbs(getResources());
        mThumbWidth = Thumb.getWidthBitmap(mThumbs);
        mThumbHeight = Thumb.getHeightBitmap(mThumbs);

        mScaleRangeMax = 100;
        mHeightTimeLine = getContext().getResources().getDimensionPixelOffset(R.dimen.frames_video_height);

        setFocusable(true);
        setFocusableInTouchMode(true);

        mFirstRun = true;

        int shadowColor = ContextCompat.getColor(getContext(), R.color.shadow_color);
        mShadow.setAntiAlias(true);
        mShadow.setColor(shadowColor);
        mShadow.setAlpha(177);

        int lineColor = ContextCompat.getColor(getContext(), R.color.line_color);
        mLine.setAntiAlias(true);
        mLine.setColor(lineColor);
        mLine.setAlpha(200);
    }

    public void initMaxWidth() {
        mMaxWidth = mThumbs.get(1).getPos() - mThumbs.get(0).getPos();

        onSeekStop(this, 0, mThumbs.get(0).getVal());
        onSeekStop(this, 1, mThumbs.get(1).getVal());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int minW = getPaddingLeft() + getPaddingRight() + getSuggestedMinimumWidth();
        mViewWidth = resolveSizeAndState(minW, widthMeasureSpec, 1);

        int minH = getPaddingBottom() + getPaddingTop() + (int) mThumbHeight + mHeightTimeLine;
        int viewHeight = resolveSizeAndState(minH, heightMeasureSpec, 1);

        setMeasuredDimension(mViewWidth, viewHeight);

        mPixelRangeMin = 0;
        mPixelRangeMax = mViewWidth - mThumbWidth;

        if (mFirstRun) {
            for (int i = 0; i < mThumbs.size(); i++) {
                Thumb th = mThumbs.get(i);
                th.setVal(mScaleRangeMax * i);
                th.setPos(mPixelRangeMax * i);
            }
            // Fire listener callback
            onCreate(this, currentThumb, getThumbValue(currentThumb));
            mFirstRun = false;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        drawShadow(canvas);
        drawThumbs(canvas);
    }

    private int currentThumb = 0;

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        final Thumb mThumb;
        final Thumb mThumb2;
        final float coordinate = ev.getX();
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                // Remember where we started
                currentThumb = getClosestThumb(coordinate);

                if (currentThumb == -1) {
                    return false;
                }

                mThumb = mThumbs.get(currentThumb);
                mThumb.setLastTouchX(coordinate);
                onSeekStart(this, currentThumb, mThumb.getVal());
                return true;
            }
            case MotionEvent.ACTION_UP: {

                if (currentThumb == -1) {
                    return false;
                }

                mThumb = mThumbs.get(currentThumb);
                onSeekStop(this, currentThumb, mThumb.getVal());
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                mThumb = mThumbs.get(currentThumb);
                mThumb2 = mThumbs.get(currentThumb == 0 ? 1 : 0);
                // Calculate the distance moved
                final float dx = coordinate - mThumb.getLastTouchX();
                final float newX = mThumb.getPos() + dx;
                if (currentThumb == 0) {

                    if ((newX + mThumb.getWidthBitmap()) >= mThumb2.getPos()) {
                        mThumb.setPos(mThumb2.getPos() - mThumb.getWidthBitmap());
                    } else if (newX <= mPixelRangeMin) {
                        mThumb.setPos(mPixelRangeMin);
                    } else {
                        //Check if thumb is not out of max width
                        checkPositionThumb(mThumb, mThumb2, dx, true);
                        // Move the object
                        mThumb.setPos(mThumb.getPos() + dx);

                        // Remember this touch position for the next move event
                        mThumb.setLastTouchX(coordinate);
                    }

                } else {
                    if (newX <= mThumb2.getPos() + mThumb2.getWidthBitmap()) {
                        mThumb.setPos(mThumb2.getPos() + mThumb.getWidthBitmap());
                    } else if (newX >= mPixelRangeMax) {
                        mThumb.setPos(mPixelRangeMax);
                    } else {
                        //Check if thumb is not out of max width
                        checkPositionThumb(mThumb2, mThumb, dx, false);
                        // Move the object
                        mThumb.setPos(mThumb.getPos() + dx);
                        // Remember this touch position for the next move event
                        mThumb.setLastTouchX(coordinate);
                    }
                }

                setThumbPos(currentThumb, mThumb.getPos());

                // Invalidate to request a redraw
                invalidate();
                return true;
            }
        }
        return false;
    }

    private void checkPositionThumb(@NonNull Thumb mThumbLeft, @NonNull Thumb mThumbRight, float dx, boolean isLeftMove) {
        if (isLeftMove && dx < 0) {
            if ((mThumbRight.getPos() - (mThumbLeft.getPos() + dx)) > mMaxWidth) {
                mThumbRight.setPos(mThumbLeft.getPos() + dx + mMaxWidth);
                setThumbPos(1, mThumbRight.getPos());
            }
        } else if (!isLeftMove && dx > 0) {
            if (((mThumbRight.getPos() + dx) - mThumbLeft.getPos()) > mMaxWidth) {
                mThumbLeft.setPos(mThumbRight.getPos() + dx - mMaxWidth);
                setThumbPos(0, mThumbLeft.getPos());
            }
        }
    }

    private int getUnstuckFrom(int index) {
        int unstuck = 0;
        float lastVal = mThumbs.get(index).getVal();
        for (int i = index - 1; i >= 0; i--) {
            Thumb th = mThumbs.get(i);
            if (th.getVal() != lastVal)
                return i + 1;
        }
        return unstuck;
    }

    private float pixelToScale(int index, float pixelValue) {
        float scale = (pixelValue * 100) / mPixelRangeMax;
        if (index == 0) {
            float pxThumb = (scale * mThumbWidth) / 100;
            return scale + (pxThumb * 100) / mPixelRangeMax;
        } else {
            float pxThumb = ((100 - scale) * mThumbWidth) / 100;
            return scale - (pxThumb * 100) / mPixelRangeMax;
        }
    }

    private float scaleToPixel(int index, float scaleValue) {
        float px = (scaleValue * mPixelRangeMax) / 100;
        if (index == 0) {
            float pxThumb = (scaleValue * mThumbWidth) / 100;
            return px - pxThumb;
        } else {
            float pxThumb = ((100 - scaleValue) * mThumbWidth) / 100;
            return px + pxThumb;
        }
    }

    private void calculateThumbValue(int index) {
        if (index < mThumbs.size() && !mThumbs.isEmpty()) {
            Thumb th = mThumbs.get(index);
            th.setVal(pixelToScale(index, th.getPos()));
            onSeek(this, index, th.getVal());
        }
    }

    private void calculateThumbPos(int index) {
        if (index < mThumbs.size() && !mThumbs.isEmpty()) {
            Thumb th = mThumbs.get(index);
            th.setPos(scaleToPixel(index, th.getVal()));
        }
    }

    private float getThumbValue(int index) {
        return mThumbs.get(index).getVal();
    }

    public void setThumbValue(int index, float value) {
        mThumbs.get(index).setVal(value);
        calculateThumbPos(index);
        // Tell the view we want a complete redraw
        invalidate();
    }

    private void setThumbPos(int index, float pos) {
        mThumbs.get(index).setPos(pos);
        calculateThumbValue(index);
        // Tell the view we want a complete redraw
        invalidate();
    }

    private int getClosestThumb(float coordinate) {
        int closest = -1;
        if (!mThumbs.isEmpty()) {
            for (int i = 0; i < mThumbs.size(); i++) {
                // Find thumb closest to x coordinate
                final float tcoordinate = mThumbs.get(i).getPos() + mThumbWidth;
                if (coordinate >= mThumbs.get(i).getPos() && coordinate <= tcoordinate) {
                    closest = mThumbs.get(i).getIndex();
                }
            }
        }
        return closest;
    }

    private void drawShadow(@NonNull Canvas canvas) {
        if (!mThumbs.isEmpty()) {

            for (Thumb th : mThumbs) {
                if (th.getIndex() == 0) {
                    final float x = th.getPos() + getPaddingLeft();
                    if (x > mPixelRangeMin) {
                        Rect mRect = new Rect((int) mThumbWidth, 0, (int) (x + mThumbWidth), mHeightTimeLine);
                        canvas.drawRect(mRect, mShadow);
                    }
                } else {
                    final float x = th.getPos() - getPaddingRight();
                    if (x < mPixelRangeMax) {
                        Rect mRect = new Rect((int) x, 0, (int) (mViewWidth - mThumbWidth), mHeightTimeLine);
                        canvas.drawRect(mRect, mShadow);
                    }
                }
            }
        }
    }

    private void drawThumbs(@NonNull Canvas canvas) {

        if (!mThumbs.isEmpty()) {
            for (Thumb th : mThumbs) {
                if (th.getIndex() == 0) {
                    canvas.drawBitmap(th.getBitmap(), th.getPos() + getPaddingLeft(), getPaddingTop() + mHeightTimeLine, null);
                } else {
                    canvas.drawBitmap(th.getBitmap(), th.getPos() - getPaddingRight(), getPaddingTop() + mHeightTimeLine, null);
                }
            }
        }
    }

    public void addOnRangeSeekBarListener(OnRangeSeekBarListener listener) {

        if (mListeners == null) {
            mListeners = new ArrayList<>();
        }

        mListeners.add(listener);
    }

    private void onCreate(RangeSeekBarView rangeSeekBarView, int index, float value) {
        if (mListeners == null)
            return;

        for (OnRangeSeekBarListener item : mListeners) {
            item.onCreate(rangeSeekBarView, index, value);
        }
    }

    private void onSeek(RangeSeekBarView rangeSeekBarView, int index, float value) {
        if (mListeners == null)
            return;

        for (OnRangeSeekBarListener item : mListeners) {
            item.onSeek(rangeSeekBarView, index, value);
        }
    }

    private void onSeekStart(RangeSeekBarView rangeSeekBarView, int index, float value) {
        if (mListeners == null)
            return;

        for (OnRangeSeekBarListener item : mListeners) {
            item.onSeekStart(rangeSeekBarView, index, value);
        }
    }

    private void onSeekStop(RangeSeekBarView rangeSeekBarView, int index, float value) {
        if (mListeners == null)
            return;

        for (OnRangeSeekBarListener item : mListeners) {
            item.onSeekStop(rangeSeekBarView, index, value);
        }
    }

    public List<Thumb> getThumbs() {
        return mThumbs;
    }
}
