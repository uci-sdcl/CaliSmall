/*******************************************************************************
* Copyright (c) 2013, Regents of the University of California
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without modification, are permitted provided
* that the following conditions are met:
*
* Redistributions of source code must retain the above copyright notice, this list of conditions
* and the following disclaimer.
*
* Redistributions in binary form must reproduce the above copyright notice, this list of conditions
* and the following disclaimer in the documentation and/or other materials provided with the
* distribution.
*
* None of the name of the Regents of the University of California, or the names of its
* contributors may be used to endorse or promote products derived from this software without specific
* prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
* PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
* TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
* ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
******************************************************************************/
package edu.uci.calismall;

import java.util.List;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * A handler for touch events generated while CaliSmall is in eraser mode.
 * 
 * @author Michele Bonazza
 * 
 */
public class EraserHandler extends GenericTouchHandler {

    /**
     * Absolute size of half the side of a square built around the touch point
     * to test for intersections when erasing. To be rescaled by the scale
     * factor retrieved using {@link CaliView#getScaleFactor()}.
     */
    public static final float ABS_HALF_ERASER_SIZE = 10f;
    private static final Paint ERASER_PAINT = new Paint();
    private static final float ABS_STROKE_WIDTH = 2.5f;

    static {
        ERASER_PAINT.setAntiAlias(true);
        ERASER_PAINT.setStyle(Style.STROKE);
        ERASER_PAINT.setStrokeWidth(ABS_STROKE_WIDTH);
    }

    private final RectF eraserArea;
    private boolean enabled, drawIt;
    private float halfEraserSize;

    /**
     * Creates a new handler for touch events when eraser mode is on.
     * 
     * @param parent
     *            the parent view
     */
    public EraserHandler(CaliView parent) {
        super("EraserHandler", parent);
        eraserArea = new RectF();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.calismall.GenericTouchHandler#onDown(android.graphics.PointF)
     */
    @Override
    public boolean onDown(PointF touchPoint) {
        if (!enabled)
            return false;
        drawIt = true;
        halfEraserSize = ABS_HALF_ERASER_SIZE / parentView.getScaleFactor();
        ERASER_PAINT.setStrokeWidth(ABS_STROKE_WIDTH
                / parentView.getScaleFactor());
        eraserArea.set(touchPoint.x - halfEraserSize, touchPoint.y
                - halfEraserSize, touchPoint.x + halfEraserSize, touchPoint.y
                + halfEraserSize);
        checkForIntersections(touchPoint);
        return true;
    }

    @Override
    public boolean onUp(PointF touchPoint) {
        drawIt = false;
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.calismall.GenericTouchHandler#onMove(android.graphics.PointF)
     */
    @Override
    public boolean onMove(PointF touchPoint) {
        if (!enabled)
            return false;
        eraserArea.set(touchPoint.x - halfEraserSize, touchPoint.y
                - halfEraserSize, touchPoint.x + halfEraserSize, touchPoint.y
                + halfEraserSize);
        checkForIntersections(touchPoint);
        return true;
    }

    /**
     * Draws a small square representing the eraser's touch area to the argument
     * <tt>canvas</tt>.
     * 
     * @param canvas
     *            the canvas onto which the eraser must be drawn
     */
    public void draw(Canvas canvas) {
        if (drawIt) {
            canvas.drawRect(eraserArea, ERASER_PAINT);
        }
    }

    @Override
    public boolean onPointerDown(PointF touchPoint, MotionEvent event) {
        drawIt = false;
        return false;
    }

    private void checkForIntersections(PointF touchPoint) {
        List<Stroke> candidates = parentView.getIntersectingStrokes(eraserArea);
        if (!candidates.isEmpty()) {
            for (Stroke stroke : candidates) {
                if (stroke.rawIntersects(eraserArea)) {
                    // if (stroke.bezierContains(touchPoint)) {
                    stroke.delete();
                    CaliSmallElement parent = stroke.getParent();
                    if (parent instanceof Scrap) {
                        ((Scrap) parent).forceBitmapRedraw();
                    }
                    parentView.forceRedraw();
                    return;
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.GenericTouchHandler#onCancel()
     */
    @Override
    public boolean onCancel() {
        return enabled;
    }

    /**
     * Enables this handler if it is disabled, disables this handler if it's
     * enabled.
     */
    public void toggleEnabled() {
        enabled = !enabled;
        if (enabled)
            parentView.setSelected(null);
    }

    /**
     * Returns whether the eraser is currently enabled.
     * 
     * @return <code>true</code> if the eraser is currently enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

}
