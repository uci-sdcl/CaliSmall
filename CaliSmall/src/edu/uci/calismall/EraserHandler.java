/**
 * EraserHandler.java Created on Nov 16, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.List;

import android.graphics.PointF;
import android.graphics.RectF;

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
    private boolean enabled;
    private float halfEraserSize;

    /**
     * Creates a new handler for touch events when eraser mode is on.
     * 
     * @param parent
     *            the parent view
     */
    public EraserHandler(CaliView parent) {
        super("EraserHandler", parent);
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
        halfEraserSize = ABS_HALF_ERASER_SIZE / parentView.getScaleFactor();
        checkForIntersections(touchPoint);
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
        checkForIntersections(touchPoint);
        return true;
    }

    private void checkForIntersections(PointF touchPoint) {
        final RectF testRect = new RectF(touchPoint.x - halfEraserSize,
                touchPoint.y - halfEraserSize, touchPoint.x + halfEraserSize,
                touchPoint.y + halfEraserSize);
        List<Stroke> candidates = parentView.getIntersectingStrokes(testRect);
        if (!candidates.isEmpty()) {
            for (Stroke stroke : candidates) {
                if (stroke.rawIntersects(testRect)) {
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

}
