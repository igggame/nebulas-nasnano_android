package io.nebulas.wallet.android.view.swip

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.support.v4.view.ViewCompat
import android.view.View
import android.view.animation.AnimationUtils

/**
 * Created by Heinoc on 2018/7/4.
 */

/**
 * Custom progress bar that shows a cycle of colors as widening circles that
 * overdraw each other. When finished, the bar is cleared from the inside out as
 * the main cycle continues. Before running, this can also indicate how close
 * the user is to triggering something (e.g. how far they need to pull down to
 * trigger a refresh).
 */
internal class SwipeProgressBar(private val mParent: View) {

    private val mPaint = Paint()
    private val mClipRect = RectF()
    private var mTriggerPercentage: Float = 0.toFloat()
    private var mStartTime: Long = 0
    private var mFinishTime: Long = 0
    private var mRunning: Boolean = false

    // Colors used when rendering the animation,
    private var mColor1: Int = 0
    private var mColor2: Int = 0
    private var mColor3: Int = 0
    private var mColor4: Int = 0

    private val mBounds = Rect()

    /**
     * @return Return whether the progress animation is currently running.
     */
    val isRunning: Boolean
        get() = mRunning || mFinishTime > 0

    init {
        mColor1 = COLOR1
        mColor2 = COLOR2
        mColor3 = COLOR3
        mColor4 = COLOR4
    }

    /**
     * Set the four colors used in the progress animation. The first color will
     * also be the color of the bar that grows in response to a user swipe
     * gesture.
     *
     * @param color1 Integer representation of a color.
     * @param color2 Integer representation of a color.
     * @param color3 Integer representation of a color.
     * @param color4 Integer representation of a color.
     */
    fun setColorScheme(color1: Int, color2: Int, color3: Int, color4: Int) {
        mColor1 = color1
        mColor2 = color2
        mColor3 = color3
        mColor4 = color4
    }

    /**
     * Update the progress the user has made toward triggering the swipe
     * gesture. and use this value to update the percentage of the trigger that
     * is shown.
     */
    fun setTriggerPercentage(triggerPercentage: Float) {
        mTriggerPercentage = triggerPercentage
        mStartTime = 0
        ViewCompat.postInvalidateOnAnimation(mParent)
    }

    /**
     * Start showing the progress animation.
     */
    fun start() {
        if (!mRunning) {
            mTriggerPercentage = 0f
            mStartTime = AnimationUtils.currentAnimationTimeMillis()
            mRunning = true
            mParent.postInvalidate()
        }
    }

    /**
     * Stop showing the progress animation.
     */
    fun stop() {
        if (mRunning) {
            mTriggerPercentage = 0f
            mFinishTime = AnimationUtils.currentAnimationTimeMillis()
            mRunning = false
            mParent.postInvalidate()
        }
    }

    fun draw(canvas: Canvas) {
        val width = mBounds.width()
        val height = mBounds.height()
        val cx = width / 2
        val cy = height / 2
        var drawTriggerWhileFinishing = false
        var restoreCount = canvas.save()
        canvas.clipRect(mBounds)

        if (mRunning || mFinishTime > 0) {
            val now = AnimationUtils.currentAnimationTimeMillis()
            val elapsed = (now - mStartTime) % ANIMATION_DURATION_MS
            val iterations = (now - mStartTime) / ANIMATION_DURATION_MS
            val rawProgress = elapsed / (ANIMATION_DURATION_MS / 100f)

            // If we're not running anymore, that means we're running through
            // the finish animation.
            if (!mRunning) {
                // If the finish animation is done, don't draw anything, and
                // don't repost.
                if (now - mFinishTime >= FINISH_ANIMATION_DURATION_MS) {
                    mFinishTime = 0
                    return
                }

                // Otherwise, use a 0 opacity alpha layer to clear the animation
                // from the inside out. This layer will prevent the circles from
                // drawing within its bounds.
                val finishElapsed = (now - mFinishTime) % FINISH_ANIMATION_DURATION_MS
                val finishProgress = finishElapsed / (FINISH_ANIMATION_DURATION_MS / 100f)
                val pct = finishProgress / 100f
                // Radius of the circle is half of the screen.
                val clearRadius = width / 2 * INTERPOLATOR.getInterpolation(pct)
                mClipRect.set(cx - clearRadius, 0f, cx + clearRadius, height.toFloat())
                canvas.saveLayerAlpha(mClipRect, 0, 0)
                // Only draw the trigger if there is a space in the center of
                // this refreshing view that needs to be filled in by the
                // trigger. If the progress view is just still animating, let it
                // continue animating.
                drawTriggerWhileFinishing = true
            }

            // First fill in with the last color that would have finished drawing.
            if (iterations == 0L) {
                canvas.drawColor(mColor1)
            } else {
                if (rawProgress >= 0 && rawProgress < 25) {
                    canvas.drawColor(mColor4)
                } else if (rawProgress >= 25 && rawProgress < 50) {
                    canvas.drawColor(mColor1)
                } else if (rawProgress >= 50 && rawProgress < 75) {
                    canvas.drawColor(mColor2)
                } else {
                    canvas.drawColor(mColor3)
                }
            }

            // Then draw up to 4 overlapping concentric circles of varying radii, based on how far
            // along we are in the cycle.
            // progress 0-50 draw mColor2
            // progress 25-75 draw mColor3
            // progress 50-100 draw mColor4
            // progress 75 (wrap to 25) draw mColor1
            if (rawProgress >= 0 && rawProgress <= 25) {
                val pct = (rawProgress + 25) * 2 / 100f
                drawCircle(canvas, cx.toFloat(), cy.toFloat(), mColor1, pct)
            }
            if (rawProgress >= 0 && rawProgress <= 50) {
                val pct = rawProgress * 2 / 100f
                drawCircle(canvas, cx.toFloat(), cy.toFloat(), mColor2, pct)
            }
            if (rawProgress >= 25 && rawProgress <= 75) {
                val pct = (rawProgress - 25) * 2 / 100f
                drawCircle(canvas, cx.toFloat(), cy.toFloat(), mColor3, pct)
            }
            if (rawProgress >= 50 && rawProgress <= 100) {
                val pct = (rawProgress - 50) * 2 / 100f
                drawCircle(canvas, cx.toFloat(), cy.toFloat(), mColor4, pct)
            }
            if (rawProgress >= 75 && rawProgress <= 100) {
                val pct = (rawProgress - 75) * 2 / 100f
                drawCircle(canvas, cx.toFloat(), cy.toFloat(), mColor1, pct)
            }
            if (mTriggerPercentage > 0 && drawTriggerWhileFinishing) {
                // There is some portion of trigger to draw. Restore the canvas,
                // then draw the trigger. Otherwise, the trigger does not appear
                // until after the bar has finished animating and appears to
                // just jump in at a larger width than expected.
                canvas.restoreToCount(restoreCount)
                restoreCount = canvas.save()
                canvas.clipRect(mBounds)
                drawTrigger(canvas, cx, cy)
            }
            // Keep running until we finish out the last cycle.
            ViewCompat.postInvalidateOnAnimation(mParent)
        } else {
            // Otherwise if we're in the middle of a trigger, draw that.
            if (mTriggerPercentage > 0 && mTriggerPercentage <= 1.0) {
                drawTrigger(canvas, cx, cy)
            }
        }
        canvas.restoreToCount(restoreCount)
    }

    private fun drawTrigger(canvas: Canvas, cx: Int, cy: Int) {
        mPaint.color = mColor1
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), cx * mTriggerPercentage, mPaint)
    }

    /**
     * Draws a circle centered in the view.
     *
     * @param canvas the canvas to draw on
     * @param cx the center x coordinate
     * @param cy the center y coordinate
     * @param color the color to draw
     * @param pct the percentage of the view that the circle should cover
     */
    private fun drawCircle(canvas: Canvas, cx: Float, cy: Float, color: Int, pct: Float) {
        mPaint.color = color
        canvas.save()
        canvas.translate(cx, cy)
        val radiusScale = INTERPOLATOR.getInterpolation(pct)
        canvas.scale(radiusScale, radiusScale)
        canvas.drawCircle(0f, 0f, cx, mPaint)
        canvas.restore()
    }

    /**
     * Set the drawing bounds of this SwipeProgressBar.
     */
    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        mBounds.left = left
        mBounds.top = top
        mBounds.right = right
        mBounds.bottom = bottom
    }

    companion object {

        // Default progress animation colors are grays.
        private val COLOR1 = -0x4d000000
        private val COLOR2 = -0x80000000
        private val COLOR3 = 0x4d000000
        private val COLOR4 = 0x1a000000

        // The duration of the animation cycle.
        private val ANIMATION_DURATION_MS = 2000

        // The duration of the animation to clear the bar.
        private val FINISH_ANIMATION_DURATION_MS = 1000

        // Interpolator for varying the speed of the animation.
        private val INTERPOLATOR = BakedBezierInterpolator.instance
    }
}