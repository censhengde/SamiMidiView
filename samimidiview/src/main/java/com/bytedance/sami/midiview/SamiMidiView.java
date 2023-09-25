package com.bytedance.sami.midiview;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

/**
 * Author：Shengde·Cen on 2022/11/2 15:41
 *
 * explain：
 */
@Keep
public class SamiMidiView extends BaseMidiView implements AnimatorUpdateListener {

    private static final String TAG = SamiMidiView.class.getSimpleName();

    private final Paint baseLinePaint = new Paint();
    final Paint hitPaint = new Paint();
    final Paint hitChunkPaint = new Paint();
    final Paint mScoreTextPaint = new Paint();
    private final Paint midiBarPaint = new Paint();

    final ValueAnimator hitChunkAnima = new ValueAnimator();

    Bitmap scoreIcon;
    boolean scoreVisible;
    final Rect scoreBounds = new Rect();
    private final Runnable hideScoreTask = () -> {
        setScoreVisible(false);
    };


    public SamiMidiView(Context context) {
        this(context, null);
    }

    public SamiMidiView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SamiMidiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        hitChunk.setIconResource(R.drawable.ic_ktv_midi_triangle_arrow);
        baseLinePaint.setDither(true);
        baseLinePaint.setAntiAlias(true);
        baseLinePaint.setColor(Color.WHITE);
        //
        hitPaint.setDither(true);
        hitPaint.setAntiAlias(true);
        hitPaint.setColor(getResources().getColor(R.color.orange_FBA85B));

        hitChunkPaint.setDither(true);
        hitChunkPaint.setAntiAlias(true);

        hitChunkAnima.setTarget(this);
        hitChunkAnima.addUpdateListener(this);
        hitChunkAnima.setDuration(3000);
        hitChunkAnima.setInterpolator(new LinearInterpolator());
        // score
        mScoreTextPaint.setDither(true);
        mScoreTextPaint.setAntiAlias(true);
        mScoreTextPaint.setTextSize(sp2px(14));
        mScoreTextPaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Freehand-Regular.ttf"));
        mScoreTextPaint.setColor(getResources().getColor(R.color.orange_FBA85B));
        scoreIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_ktv_score_bottom_icon);

        midiBarPaint.setDither(true);
        midiBarPaint.setAntiAlias(true);
        midiBarPaint.setColor(Color.parseColor("#33FFFFFF"));
    }


    @Override
    protected void onUserPitchHit(@NonNull MidiBar midiBar, long timeMs, int userPitch) {
        super.onUserPitchHit(midiBar, timeMs, userPitch);
        final int finalPitchValue = midiBar.pitch;
        layoutHitChunk(finalPitchValue);
        post(() -> {
            hitChunkAnima.cancel();
            hitChunkAnima.setIntValues(finalPitchValue, minPitch);
            hitChunkAnima.start();
        });
    }

    @Override
    protected void onSentenceChanged(int score, int sentenceIndex) {
        if (!isHitChunkActive) {
            return;
        }
        removeCallbacks(hideScoreTask);
        setScoreVisible(true);
        // Auto hide the score after 2s later.
        postDelayed(hideScoreTask, 2000);
    }

    public void setScoreVisible(final boolean visible) {
        if (visible != scoreVisible) {
            scoreVisible = visible;
            invalidate();
        }
    }

    protected void drawHitChunk(@NonNull Canvas canvas) {
        if (hitChunk != null && hitChunk.icon != null) {
            canvas.save();
            canvas.drawBitmap(hitChunk.icon, hitChunk.left, hitChunk.top, hitChunkPaint);
            canvas.restore();
        }
    }

    protected void drawScore(@NonNull Canvas canvas) {
        if (scoreIcon == null) {
            return;
        }

        if (!scoreVisible || !isHitChunkActive) {
            return;
        }
        final String text = String.valueOf(score);
        if (TextUtils.isEmpty(text)) {
            return;
        }
        mScoreTextPaint.getTextBounds(text, 0, text.length(), scoreBounds);
        final float textWidth = scoreBounds.width();
        final float textHeight = scoreBounds.height();
        final float x = baseLineX - dp2px(10) - textWidth;
        final float y = dp2px(30);
        canvas.drawText(text, x, y, mScoreTextPaint);
        // draw bottom icon
        final float iconY = y + textHeight - dp2px(4);
        final float iconO = x + textWidth / 2;
        final float iconX = iconO - scoreIcon.getWidth() / 2f;
        canvas.drawBitmap(scoreIcon, iconX, iconY, mScoreTextPaint);
        scoreBounds.left = 0;
        scoreBounds.top = 0;
        scoreBounds.right = 0;
        scoreBounds.bottom = 0;
    }

    @Override
    protected void drawBaseLine(@NonNull Canvas canvas) {
        canvas.drawRect(baseLineX, getPaddingTop(), baseLineX + 1, getHeight() - getPaddingBottom(),
                baseLinePaint);
    }


    @Override
    protected void onDrawEachMidiBar(@NonNull Canvas canvas, @NonNull MidiBar midiBar, @NonNull View ofView) {
        canvas.drawRoundRect(midiBar.layout.left, midiBar.layout.top, midiBar.layout.right - 4, midiBar.layout.bottom,
                10f,
                10f, midiBarPaint);
        // draw hit midi ranges
        if (midiBar.isHit()) {
            for (RectF part : midiBar.getHitParts()) {
                final float hitRight = part.right - getScrollX();
                if (hitRight > baseLineX) {
                    continue;
                }
                onDrawEachHitPart(canvas, part, this);
            }
        }
    }

    protected void onDrawEachHitPart(@NonNull Canvas canvas, @NonNull RectF hitPart, @NonNull View ofView) {
        canvas.drawRoundRect(hitPart.left, hitPart.top, hitPart.right,
                hitPart.bottom, 10f, 10f, hitPaint);
    }


    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        final int pitchValue = (int) animation.getAnimatedValue();
        layoutHitChunk(pitchValue);
        invalidate();
    }
}
