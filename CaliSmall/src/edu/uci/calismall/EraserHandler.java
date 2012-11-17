/**
 * EraserHandler.java Created on Nov 16, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.List;

import android.graphics.PointF;

/**
 * A handler for touch events generated while CaliSmall is in eraser mode.
 * 
 * @author Michele Bonazza
 * 
 */
public class EraserHandler extends GenericTouchHandler {

    private boolean enabled;

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
        List<Stroke> candidates = parentView.getIntersectingStrokes(touchPoint);
        if (!candidates.isEmpty()) {
            for (Stroke stroke : candidates) {
                if (stroke.bezierContains(touchPoint)) {
                    stroke.delete();
                    CaliSmallElement parent = stroke.getParent();
                    if (parent instanceof Scrap) {
                        ((Scrap) parent).forceBitmapRedraw();
                    }
                    parentView.forceRedraw();
                    return true;
                }
            }
        }
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
        List<Stroke> candidates = parentView.getIntersectingStrokes(touchPoint);
        if (!candidates.isEmpty()) {
            for (Stroke stroke : candidates) {
                if (stroke.bezierContains(touchPoint)) {
                    stroke.delete();
                    CaliSmallElement parent = stroke.getParent();
                    if (parent instanceof Scrap) {
                        ((Scrap) parent).forceBitmapRedraw();
                    }
                    parentView.forceRedraw();
                    return true;
                }
            }
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.GenericTouchHandler#onUp(android.graphics.PointF)
     */
    @Override
    public boolean onUp(PointF touchPoint) {
        return enabled;
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
