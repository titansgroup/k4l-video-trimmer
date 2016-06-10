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
package life.knowledge4.videotrimmer;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import life.knowledge4.videotrimmer.interfaces.OnProgressVideoListener;
import life.knowledge4.videotrimmer.interfaces.OnRangeSeekBarListener;
import life.knowledge4.videotrimmer.interfaces.OnTrimVideoListener;
import life.knowledge4.videotrimmer.utils.BackgroundExecutor;
import life.knowledge4.videotrimmer.utils.TrimVideoUtils;
import life.knowledge4.videotrimmer.utils.UiThreadExecutor;
import life.knowledge4.videotrimmer.view.ProgressBarView;
import life.knowledge4.videotrimmer.view.RangeSeekBarView;
import life.knowledge4.videotrimmer.view.TimeLineView;

public class K4LVideoTrimmer extends FrameLayout implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        SeekBar.OnSeekBarChangeListener, OnRangeSeekBarListener, OnProgressVideoListener {

    private static final String TAG = K4LVideoTrimmer.class.getSimpleName();
    private static final int MIN_TIME_FRAME = 1000;

    private SeekBar mHolderTopView;
    private RangeSeekBarView mRangeSeekBarView;
    private RelativeLayout mLinearVideo;
    private VideoView mVideoView;
    private ImageView mPlayView;
    private TextView mTextSize;
    private TextView mTextTimeFrame;
    private TextView mTextTime;
    private TimeLineView mTimeLineView;

    private Uri mSrc;
    private String mFinalPath;

    private int mMaxDuration;
    private List<OnProgressVideoListener> mListeners;
    private OnTrimVideoListener mOnTrimVideoListener;

    private int mDuration = 0;
    private int mTimeVideo = 0;
    private int mStartPosition = 0;
    private int mEndPosition = 0;
    private long mOriginSizeFile;
    private boolean mResetSeekBar = true;

    @NonNull
    private final MessageHandler mMessageHandler = new MessageHandler(this);
    private static final int SHOW_PROGRESS = 2;

    private GestureDetector mGestureDetector;

    @NonNull
    private final GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mVideoView.isPlaying()) {
                mPlayView.setVisibility(View.VISIBLE);
                mMessageHandler.removeMessages(SHOW_PROGRESS);
                mVideoView.pause();
            } else {
                mPlayView.setVisibility(View.GONE);

                if (mResetSeekBar) {
                    mResetSeekBar = false;
                    mVideoView.seekTo(mStartPosition);
                }

                mMessageHandler.sendEmptyMessage(SHOW_PROGRESS);
                mVideoView.start();
            }
            return true;
        }
    };

    @NonNull
    private final View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, @NonNull MotionEvent event) {
            mGestureDetector.onTouchEvent(event);
            return true;
        }
    };

    public K4LVideoTrimmer(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public K4LVideoTrimmer(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {

        LayoutInflater.from(context).inflate(R.layout.view_time_line, this, true);

        mHolderTopView = ((SeekBar) findViewById(R.id.handlerTop));
        ProgressBarView progressVideoView = ((ProgressBarView) findViewById(R.id.timeVideoView));
        mRangeSeekBarView = ((RangeSeekBarView) findViewById(R.id.timeLineBar));
        mLinearVideo = ((RelativeLayout) findViewById(R.id.layout_surface_view));
        mVideoView = ((VideoView) findViewById(R.id.video_loader));
        mPlayView = ((ImageView) findViewById(R.id.icon_video_play));
        mTextSize = ((TextView) findViewById(R.id.textSize));
        mTextTimeFrame = ((TextView) findViewById(R.id.textTimeSelection));
        mTextTime = ((TextView) findViewById(R.id.textTime));
        mTimeLineView = ((TimeLineView) findViewById(R.id.timeLineView));
        View viewButtonCancel = findViewById(R.id.btCancel);
        View viewButtonSave = findViewById(R.id.btSave);

        if (viewButtonCancel != null) {
            viewButtonCancel.setOnClickListener(new OnClickListener() {
                                                    @Override
                                                    public void onClick(View view) {
                                                        mOnTrimVideoListener.cancelAction();
                                                    }
                                                }
            );
        }

        if (viewButtonSave != null) {
            viewButtonSave.setOnClickListener(new OnClickListener() {
                                                  @Override
                                                  public void onClick(View view) {
                                                      if (mStartPosition <= 0 && mEndPosition >= mDuration) {
                                                          mOnTrimVideoListener.getResult(mSrc);
                                                      } else {
                                                          mPlayView.setVisibility(View.VISIBLE);
                                                          mVideoView.pause();

                                                          MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                                                          mediaMetadataRetriever.setDataSource(getContext(), mSrc);
                                                          long METADATA_KEY_DURATION = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

                                                          File file = new File(mSrc.getPath());

                                                          if (mTimeVideo < MIN_TIME_FRAME) {

                                                              if ((METADATA_KEY_DURATION - mEndPosition) > (MIN_TIME_FRAME - mTimeVideo)) {
                                                                  mEndPosition += (MIN_TIME_FRAME - mTimeVideo);
                                                              } else if (mStartPosition > (MIN_TIME_FRAME - mTimeVideo)) {
                                                                  mStartPosition -= (MIN_TIME_FRAME - mTimeVideo);
                                                              }
                                                          }

                                                          startTrimVideo(file, mFinalPath, mStartPosition, mEndPosition, mOnTrimVideoListener);
                                                      }
                                                  }
                                              }
            );
        }

        mListeners = new ArrayList<>();
        mListeners.add(this);
        mListeners.add(progressVideoView);

        mHolderTopView.setMax(1000);
        mHolderTopView.setSecondaryProgress(0);

        mRangeSeekBarView.addOnRangeSeekBarListener(this);
        mRangeSeekBarView.addOnRangeSeekBarListener(progressVideoView);

        int marge = mRangeSeekBarView.getThumbs().get(0).getWidthBitmap();
        int widthSeek = mHolderTopView.getThumb().getMinimumWidth() / 2;

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mHolderTopView.getLayoutParams();
        lp.setMargins(marge - widthSeek, 0, marge - widthSeek, 0);
        mHolderTopView.setLayoutParams(lp);

        lp = (RelativeLayout.LayoutParams) mTimeLineView.getLayoutParams();
        lp.setMargins(marge, 0, marge, 0);
        mTimeLineView.setLayoutParams(lp);

        lp = (RelativeLayout.LayoutParams) progressVideoView.getLayoutParams();
        lp.setMargins(marge, 0, marge, 0);
        progressVideoView.setLayoutParams(lp);

        mHolderTopView.setOnSeekBarChangeListener(this);

        mVideoView.setOnPreparedListener(this);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setOnErrorListener(this);

        mGestureDetector = new GestureDetector(getContext(), mGestureListener);
        mVideoView.setOnTouchListener(mTouchListener);

        setDefaultDestinationPath();
    }

    @SuppressWarnings("unused")
    public void setVideoURI(final Uri videoURI) {
        mSrc = videoURI;

        getSizeFile();

        mVideoView.setVideoURI(mSrc);
        mVideoView.requestFocus();

        mTimeLineView.setVideo(mSrc);
    }

    @SuppressWarnings("unused")
    public void setDestinationPath(final String finalPath) {
        mFinalPath = finalPath;
        Log.d(TAG, "Setting custom path " + mFinalPath);
    }

    private void setDefaultDestinationPath() {
        File folder = Environment.getExternalStorageDirectory();
        mFinalPath = folder.getPath() + File.separator;
        Log.d(TAG, "Setting default path " + mFinalPath);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        int duration = (int) ((mDuration * progress) / 1000L);

        if (fromUser) {
            if (duration < mStartPosition) {
                setProgressBarPosition(mStartPosition);
                duration = mStartPosition;
            } else if (duration > mEndPosition) {
                setProgressBarPosition(mEndPosition);
                duration = mEndPosition;
            }
            setTimeVideo(duration);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mMessageHandler.removeMessages(SHOW_PROGRESS);
        mVideoView.pause();
        mPlayView.setVisibility(View.VISIBLE);
        updateProgress(false);
    }

    @Override
    public void onStopTrackingTouch(@NonNull SeekBar seekBar) {
        mMessageHandler.removeMessages(SHOW_PROGRESS);
        mVideoView.pause();
        mPlayView.setVisibility(View.VISIBLE);

        int duration = (int) ((mDuration * seekBar.getProgress()) / 1000L);
        mVideoView.seekTo(duration);
        setTimeVideo(duration);
        updateProgress(false);
    }

    @Override
    public void onPrepared(@NonNull MediaPlayer mp) {
        // Adjust the size of the video
        // so it fits on the screen
        int videoWidth = mp.getVideoWidth();
        int videoHeight = mp.getVideoHeight();
        float videoProportion = (float) videoWidth / (float) videoHeight;
        int screenWidth = mLinearVideo.getWidth();
        int screenHeight = mLinearVideo.getHeight();
        float screenProportion = (float) screenWidth / (float) screenHeight;
        ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();

        if (videoProportion > screenProportion) {
            lp.width = screenWidth;
            lp.height = (int) ((float) screenWidth / videoProportion);
        } else {
            lp.width = (int) (videoProportion * (float) screenHeight);
            lp.height = screenHeight;
        }
        mVideoView.setLayoutParams(lp);

        mPlayView.setVisibility(View.VISIBLE);

        mDuration = mVideoView.getDuration();
        setSeekBarPosition();

        setTimeFrames();
        setTimeVideo(0);
    }

    private void setSeekBarPosition() {

        if (mDuration >= mMaxDuration) {
            mStartPosition = mDuration / 2 - mMaxDuration / 2;
            mEndPosition = mDuration / 2 + mMaxDuration / 2;

            mRangeSeekBarView.setThumbValue(0, (mStartPosition * 100) / mDuration);
            mRangeSeekBarView.setThumbValue(1, (mEndPosition * 100) / mDuration);

        } else {
            mStartPosition = 0;
            mEndPosition = mDuration;
        }

        setProgressBarPosition(mStartPosition);
        mVideoView.seekTo(mStartPosition);

        mTimeVideo = mDuration;
        mRangeSeekBarView.initMaxWidth();
    }

    private void startTrimVideo(@NonNull final File file, @NonNull final String dst, final int startVideo, final int endVideo, @NonNull final OnTrimVideoListener callback) {
        BackgroundExecutor.execute(new BackgroundExecutor.Task("", 0L, "") {
                                       @Override
                                       public void execute() {
                                           try {
                                               TrimVideoUtils.startTrim(file, dst, startVideo, endVideo, callback);
                                           } catch (final Throwable e) {
                                               Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                                           }
                                       }
                                   }
        );
    }

    private void setTimeFrames() {
        String seconds = getContext().getString(R.string.short_seconds);
        mTextTimeFrame.setText(String.format("%s %s - %s %s", stringForTime(mStartPosition), seconds, stringForTime(mEndPosition), seconds));
    }

    private void setTimeVideo(int position) {
        String seconds = getContext().getString(R.string.short_seconds);
        mTextTime.setText(String.format("%s %s", stringForTime(position), seconds));
    }

    @Override
    public void onCreate(RangeSeekBarView rangeSeekBarView, int index, float value) {

    }

    @Override
    public void onSeek(RangeSeekBarView rangeSeekBarView, int index, float value) {
        // 0 is Left selector
        // 1 is right selector
        switch (index) {
            case 0: {
                mStartPosition = (int) ((mDuration * value) / 100L);
                mVideoView.seekTo(mStartPosition);
                break;
            }
            case 1: {
                mEndPosition = (int) ((mDuration * value) / 100L);
                break;
            }
        }
        setProgressBarPosition(mStartPosition);

        setTimeFrames();
        mTimeVideo = mEndPosition - mStartPosition;
    }

    @Override
    public void onSeekStart(RangeSeekBarView rangeSeekBarView, int index, float value) {

    }

    @Override
    public void onSeekStop(RangeSeekBarView rangeSeekBarView, int index, float value) {
        mMessageHandler.removeMessages(SHOW_PROGRESS);
        mVideoView.pause();
        mPlayView.setVisibility(View.VISIBLE);
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        Formatter mFormatter = new Formatter();
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private void getSizeFile() {

        if (mOriginSizeFile == 0) {
            File file = new File(mSrc.getPath());

            mOriginSizeFile = file.length();
            long fileSizeInKB = mOriginSizeFile / 1024;

            if (fileSizeInKB > 1000) {
                long fileSizeInMB = fileSizeInKB / 1024;
                mTextSize.setText(String.format("%s %s", fileSizeInMB, getContext().getString(R.string.megabyte)));
            } else {
                mTextSize.setText(String.format("%s %s", fileSizeInKB, getContext().getString(R.string.kilobyte)));
            }
        }
    }

    @SuppressWarnings("unused")
    public void setOnTrimVideoListener(OnTrimVideoListener onTrimVideoListener) {
        mOnTrimVideoListener = onTrimVideoListener;
    }

    @SuppressWarnings("unused")
    public void setMaxDuration(int maxDuration) {
        mMaxDuration = maxDuration * 1000;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mVideoView.seekTo(0);
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    private static class MessageHandler extends Handler {

        @NonNull
        private final WeakReference<K4LVideoTrimmer> mView;

        MessageHandler(K4LVideoTrimmer view) {
            mView = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            K4LVideoTrimmer view = mView.get();
            if (view == null || view.mVideoView == null) {
                return;
            }

            view.updateProgress(true);
            if (view.mVideoView.isPlaying()) {
                sendEmptyMessageDelayed(0, 10);
            }
        }
    }

    private void updateProgress(boolean all) {
        if (mDuration == 0) return;

        int position = mVideoView.getCurrentPosition();
        if (all) {
            for (OnProgressVideoListener item : mListeners) {
                item.updateProgress(position, mDuration, ((position * 100) / mDuration));
            }
        } else {
            mListeners.get(1).updateProgress(position, mDuration, ((position * 100) / mDuration));
        }
    }

    @Override
    public void updateProgress(int time, int max, float scale) {
        if (mVideoView == null) {
            return;
        }

        if (time >= mEndPosition) {
            mMessageHandler.removeMessages(SHOW_PROGRESS);
            mVideoView.pause();
            mPlayView.setVisibility(View.VISIBLE);
            mResetSeekBar = true;
            return;
        }

        if (mHolderTopView != null) {
            // use long to avoid overflow
            setProgressBarPosition(time);
        }
        setTimeVideo(time);
    }


    private void setProgressBarPosition(int position) {
        if (mDuration > 0) {
            long pos = 1000L * position / mDuration;
            mHolderTopView.setProgress((int) pos);
        }
    }

    public void destroy() {
        BackgroundExecutor.cancelAll("", true);
        UiThreadExecutor.cancelAll("");
    }
}
