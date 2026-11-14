package com.candy.tools;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 横向电池条（3.7 观感）：
 *  - 轨道跟随主题（@color/tile + 1dp 描边），不做纯白/纯灰
 *  - 未插电：主题主色填充，按真实电量
 *  - 插电 + 普通充电：主题主色，长度做 12% → 当前电量 的生长动画
 *  - 插电 + 旁路：固定长度，绿色填充 + 流光（深浅色主题都保持绿色）
 *  - 文字按填充是否覆盖自动切换黑白，保证深色主题下也能看清
 */
public class BatteryBarView extends View {

    private static final int COLOR_BYPASS = 0xFF22C55E;
    private static final int COLOR_ON_LIGHT_FILL = 0xFF0B1220;
    private static final int COLOR_ON_DARK_FILL = 0xFFFFFFFF;

    private float level;
    private boolean charging;
    private boolean bypass;
    private String busyText;
    private float blink;

    private final ValueAnimator blinkAnimator = ValueAnimator.ofFloat(0.4f, 1f);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final RectF fill = new RectF();
    private final RectF cap = new RectF();

    private final int colorTrack;
    private final int colorBorder;
    private final int colorAccent;
    private final int colorTextPrimary;
    private final int colorTextSecondary;

    private float animPhase;
    private ValueAnimator animator;

    public BatteryBarView(Context context) {
        this(context, null);
    }

    public BatteryBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        colorTrack = context.getResources().getColor(R.color.tile);
        colorBorder = context.getResources().getColor(R.color.stroke_neon);
        colorAccent = context.getResources().getColor(R.color.neon);
        colorTextPrimary = context.getResources().getColor(R.color.text_primary);
        colorTextSecondary = context.getResources().getColor(R.color.text_secondary);
    }

    public void setLevel(float percent) {
        level = Math.max(0f, Math.min(1f, percent));
        invalidate();
    }

    public void setState(boolean isCharging, boolean isBypass) {
        boolean changed = charging != isCharging || bypass != isBypass;
        charging = isCharging;
        bypass = isBypass;
        if (changed) {
            updateAnimator();
        }
        invalidate();
    }

    /** 切换过程中的提示文字，传入 null 结束。 */
    public void setBusyText(String text) {
        busyText = text;
        blinkAnimator.removeAllUpdateListeners();
        if (text != null) {
            blinkAnimator.setDuration(700);
            blinkAnimator.setRepeatMode(ValueAnimator.REVERSE);
            blinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
            blinkAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    blink = (float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            blinkAnimator.start();
        } else {
            blinkAnimator.cancel();
            blink = 1f;
        }
        invalidate();
    }

    private void updateAnimator() {
        if (charging) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(1800);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        animPhase = (float) animation.getAnimatedValue();
                        invalidate();
                    }
                });
            }
            if (!animator.isRunning()) {
                animator.start();
            }
        } else {
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
            animPhase = 0f;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        blinkAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float capW = Math.max(dp(4), h * 0.055f);
        float radius = h / 2f;

        body.set(0, 0, w - capW - dp(6), h);
        cap.set(body.right + dp(3), h * 0.32f, w - dp(1), h * 0.68f);

        // 正极
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colorTrack);
        canvas.drawRoundRect(cap, dp(2), dp(2), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(colorBorder);
        canvas.drawRoundRect(cap, dp(2), dp(2), paint);

        // 轨道
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colorTrack);
        canvas.drawRoundRect(body, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(colorBorder);
        canvas.drawRoundRect(body, radius, radius, paint);

        // 填充
        float shown = level;
        if (charging && !bypass) {
            // 普通充电：长度反复生长，模拟充电过程
            shown = 0.12f + Math.max(0f, level - 0.12f)
                    * (0.5f - 0.5f * (float) Math.cos(animPhase * Math.PI * 2));
        }
        float inset = dp(4);
        float right = inset + (body.width() - inset * 2) * shown;
        int fillColor = bypass ? COLOR_BYPASS : colorAccent;
        boolean hasFill = right > inset + dp(6);
        if (hasFill) {
            fill.set(inset, inset, right, h - inset);
            float fr = fill.height() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            canvas.drawRoundRect(fill, fr, fr, paint);

            if (charging) {
                float sweep = Math.max(dp(40), fill.width() * 0.35f);
                float x = fill.left - sweep + (fill.width() + sweep) * animPhase;
                Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
                shine.setShader(new LinearGradient(x, 0, x + sweep, 0,
                        new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                canvas.save();
                canvas.clipRect(fill);
                canvas.drawRoundRect(fill, fr, fr, shine);
                canvas.restore();
            }
        }

        // 百分比：落在填充上时自动取反色，避免深色主题看不清
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(16));
        paint.setFakeBoldText(true);
        String percent = Math.round(level * 100) + "%";
        float textX = dp(16);
        float ty = body.centerY() - (paint.ascent() + paint.descent()) / 2f;
        boolean textOnFill = hasFill && (textX + paint.measureText(percent) + dp(6) < right);
        paint.setColor(textOnFill ? contrastOn(fillColor) : colorTextPrimary);
        canvas.drawText(percent, textX, ty, paint);

        paint.setFakeBoldText(false);
    }

    /** 根据填充色亮度选择黑或白文字。 */
    private int contrastOn(int color) {
        double luminance = 0.2126 * Color.red(color) / 255.0
                + 0.7152 * Color.green(color) / 255.0
                + 0.0722 * Color.blue(color) / 255.0;
        return luminance > 0.55 ? COLOR_ON_LIGHT_FILL : COLOR_ON_DARK_FILL;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
