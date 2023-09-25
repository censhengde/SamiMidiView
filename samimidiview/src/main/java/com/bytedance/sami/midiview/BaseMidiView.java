package com.bytedance.sami.midiview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;
import androidx.annotation.DrawableRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Author：Shengde·Cen on 2022/11/2 15:41
 *
 * explain：
 */
@Keep
public abstract class BaseMidiView extends FrameLayout {

    private static final String TAG = "SurfacePitchView3";

    // MidiBar 集合
    List<MidiBar> midiBarList = Collections.emptyList();


    // 基线x坐标，单位：px；
    float baseLineX = 450;

    // 当前MidiBar 集合中最大音高值
    int maxPitch = 127;

    // 当前MidiBar 集合中最小音高值
    int minPitch = 0;

    // MidiBar 高度。
    float midiBarH = 10;

    float pitchUnitHeight;

    private float speed = 0.25f;
    volatile long progress = 0;
    private int visibleStartIndex;
    boolean isHitChunkActive;


    HitChunk hitChunk;

    int score;
    int sentenceIndex;

    private final MidiView midiView;


    public BaseMidiView(Context context) {
        this(context, null);
    }

    public BaseMidiView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseMidiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        midiView = new MidiView(context);
        final LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(midiView, lp);
        initView();
    }

    private void initView() {
        baseLineX = dp2px(82);
        hitChunk = new HitChunk(getContext());
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getBaseLineX() {
        return baseLineX;
    }

    public void setBaseLineX(float baseLineX) {
        this.baseLineX = baseLineX;
        if (isAttachedToWindow()) {
            requestLayout();
        }
    }

    public HitChunk getHitChunk() {
        return hitChunk;
    }

    public void setHitChunk(HitChunk hitChunk) {
        this.hitChunk = hitChunk;
    }

    public int getMaxPitch() {
        return maxPitch;
    }

    public int getMinPitch() {
        return minPitch;
    }

    public void setProgress(long progress) {
        if (midiBarList == null || midiBarList.isEmpty() || progress < 0) {
            return;
        }
        // seek event
        final int offset = (int) ((progress) * speed);
        if (progress < this.progress) {
            reset();
            midiView.setScrollX(offset);
        }
        final long deltaTime = Math.abs(progress - this.progress);
        this.progress = progress;
        midiView.scrollMidiBar(offset, deltaTime);
    }


    private void reset() {
        visibleStartIndex = 0;
        score = 0;
        isHitChunkActive = false;
    }

    public void setStandardPitch(List<MidiBar> pitchList) {
        if (pitchList != null && !pitchList.isEmpty()) {
            Log.i(TAG, "===> setStandardPitch: size:" + pitchList.size());
            maxPitch = minPitch = pitchList.get(0).pitch;
            for (MidiBar pitch : pitchList) {
                maxPitch = Math.max(maxPitch, pitch.pitch);
                minPitch = Math.min(minPitch, pitch.pitch);
            }
            minPitch = Math.max(minPitch - 5, 0);
            maxPitch = Math.min(maxPitch + 5, 127);
            this.midiBarList = pitchList;
            midiView.requestLayout();
        }
    }

    public void hit(long timeMs, int userPitch) {
        if (!isHitChunkActive || midiBarList == null || midiBarList.isEmpty()) {
            return;
        }
        MidiBar hitMidiBar = null;
        // 查找命中的MidiBar
        for (int i = visibleStartIndex; i < midiBarList.size(); i++) {
            final MidiBar midiBar = midiBarList.get(i);
            final boolean hit = isHit(midiBar, timeMs, userPitch);
            if (hit) {
                hitMidiBar = midiBar;
                break;
            }

            final float left = baseLineX + (midiBar.startTime - progress) * speed;
            // out of view bounds,ignore
            if (left > getMeasuredWidth() - getPaddingEnd()) {
                break;
            }
        }

        if (hitMidiBar != null) {
            onUserPitchHit(hitMidiBar, timeMs, userPitch);
        }

    }


    /**
     * 用户歌唱音高是否命中标准音高。
     *
     * @param midiBar 标准音高条
     * @param timeMs 当前进度。
     * @param userPitch 用户歌唱音高值。
     * @return
     */
    protected boolean isHit(MidiBar midiBar, long timeMs, int userPitch) {
        return timeMs >= midiBar.startTime && timeMs <= midiBar.getEndTime()
                && Math.abs(midiBar.pitch - userPitch) <= 2;
    }

    /**
     * 用户歌唱音高已经命中标准音高。
     *
     * @param midiBar
     * @param timeMs
     * @param userPitch
     */
    protected void onUserPitchHit(@NonNull MidiBar midiBar, long timeMs, int userPitch) {
        final long cutoffDuration = Math.min(midiBar.getEndTime(), timeMs + 120) - timeMs;
        final RectF part = new RectF();
        part.left = baseLineX + timeMs * speed;
        part.top = midiBar.layout.top;
        part.right = part.left + (cutoffDuration * speed);
        part.bottom = midiBar.layout.bottom;
        midiBar.addHitPart(part);
    }

    protected final float dp2px(final float dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return (dp * scale + 0.5f);
    }

    public final float sp2px(final float sp) {
        final float fontScale = getResources().getDisplayMetrics().scaledDensity;
        return (sp * fontScale + 0.5f);
    }

    protected void onSentenceChanged(final int score, int sentenceIndex) {

    }

    public void setScore(final int score, final int sentenceIndex) {
        final boolean sentenceChanged = this.sentenceIndex != sentenceIndex;
        this.sentenceIndex = sentenceIndex;
        if (sentenceChanged) {
            this.score = score;
            onSentenceChanged(score, sentenceIndex);
        }
    }


    protected void layoutHitChunk(float pitchValue) {
        final float left = baseLineX - hitChunk.width();
        final float pitchHeight = (pitchValue - minPitch) * pitchUnitHeight;
        final float top = (getHeight() - getPaddingBottom() - hitChunk.height()) - pitchHeight;
        hitChunk.offsetTo(left, top);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawBaseLine(canvas);
        drawScore(canvas);
        drawHitChunk(canvas);
    }

    protected abstract void drawHitChunk(@NonNull Canvas canvas);

    protected abstract void drawScore(@NonNull Canvas canvas);


    protected abstract void drawBaseLine(@NonNull Canvas canvas);

    public static class HitChunk extends RectF {


        Bitmap icon;
        private final Context context;

        public HitChunk(Context context) {
            this.context = context;
            right = left + icon.getWidth();
            bottom = top + icon.getHeight();
        }

        public Bitmap getIcon() {
            return icon;
        }

        public void setIcon(Bitmap icon) {
            this.icon = icon;
        }

        public void setIconResource(@DrawableRes int resId) {
            icon = BitmapFactory.decodeResource(context.getResources(), resId);
        }

    }

    private class MidiView extends View {

        private final Scroller scroller;


        public MidiView(Context context) {
            this(context, null);
        }

        public MidiView(Context context, @Nullable AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public MidiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            scroller = new Scroller(context, new LinearInterpolator());
            midiBarH = dp2px(5);
        }

        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            final float totalHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - hitChunk.height();
            final int totalPitch = maxPitch - minPitch;
            if (totalPitch > 0) {
                pitchUnitHeight = totalHeight / totalPitch;
            }
            //
            layoutHitChunk(minPitch);
            layoutMidiBar();
        }


        private void layoutMidiBar() {
            if (midiBarList == null || midiBarList.isEmpty()) {
                return;
            }
            for (MidiBar midiBar : midiBarList) {
                midiBar.layout.left = baseLineX + midiBar.startTime * speed;
                midiBar.layout.right = midiBar.layout.left + (midiBar.duration * speed);
                midiBar.layout.top =
                        (getHeight() - getPaddingBottom() - (hitChunk.height() * 0.5f)) - ((midiBar.pitch - minPitch)
                                * pitchUnitHeight) - (midiBarH * 0.5f);
                midiBar.layout.bottom = midiBar.layout.top + midiBarH;
            }
        }

        public void scrollMidiBar(int offset, long deltaTime) {
            final int dx = offset - getScrollX();
            scroller.startScroll(getScrollX(), 0, dx, 0, (int) deltaTime);
            invalidate();
        }

        @Override
        public void computeScroll() {
            boolean isNotFinished = scroller.computeScrollOffset();
            if (isNotFinished) {
                scrollTo(scroller.getCurrX(), scroller.getCurrY());
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawMidiBar(canvas);
        }

        protected void drawMidiBar(@NonNull Canvas canvas) {
            if (midiBarList == null || midiBarList.isEmpty()) {
                return;
            }
            canvas.save();
            for (int i = visibleStartIndex; i < midiBarList.size(); i++) {

                final MidiBar pitch = midiBarList.get(i);
                final float left = pitch.layout.left - getScrollX();
                // 当前MidiBar还没滚动到可视范围内，忽略；out of view bounds ,ignore
                if (left > getMeasuredWidth() - getPaddingEnd()) {
                    break;
                }
                // first midi bar is visible
                if (i == 0 && left < getWidth() - getPaddingEnd()) {
                    isHitChunkActive = true;
                }
                final float right = left + pitch.layout.width();
                if (i == midiBarList.size() - 1 && right < getPaddingStart()) {
                    isHitChunkActive = false;
                }
                // 当前MidiBar已经滚出可视范围内，忽略不画，继续下一个；out of view bounds ,ignore
                if (right < getPaddingStart()) {
                    pitch.clearHitParts();
                    continue;
                }
                visibleStartIndex = i;
                onDrawEachMidiBar(canvas, pitch, this);
            }
            canvas.restore();

        }

    }


    protected abstract void onDrawEachMidiBar(@NonNull Canvas canvas, @NonNull MidiBar midiBar, @NonNull View ofView);


    public static class MidiBar {

        public long startTime;
        public long duration;
        public int pitch;
        // maybe multiple be hit
        private List<RectF> hitParts;

        // MidiBar 布局信息（坐标）
        @NonNull
        public final RectF layout = new RectF();

        public MidiBar(long startTime, long duration, int pitch) {
            this.startTime = startTime;
            this.duration = duration;
            this.pitch = pitch;
        }


        public long getEndTime() {
            return startTime + duration;
        }

        @NonNull
        public List<RectF> getHitParts() {
            return hitParts == null ? Collections.emptyList() : hitParts;
        }

        public void addHitPart(RectF hitPart) {
            if (hitParts == null) {
                hitParts = new ArrayList<>();
            }
            hitParts.add(hitPart);
        }

        // it is  hit ?
        public boolean isHit() {
            return hitParts != null && !hitParts.isEmpty();
        }

        public void clearHitParts() {
            if (hitParts != null) {
                hitParts.clear();
                hitParts = null;
            }
        }

    }

}
