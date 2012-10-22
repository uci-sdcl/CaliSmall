/**
 * GenericTouchHandler.java Created on Oct 19, 2012 Copyright 2012 Michele
 * Bonazza <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Groups functionalities that are common to many touch handlers.
 * 
 * @author Michele Bonazza
 * 
 */
public abstract class GenericTouchHandler extends
        ScaleGestureDetector.SimpleOnScaleGestureListener implements
        TouchHandler {

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#processTouchEvent(int,
     * android.graphics.PointF)
     */
    @Override
    public boolean processTouchEvent(int action, PointF touched,
            MotionEvent event) {
        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#onDown()
     */
    @Override
    public boolean onDown(PointF touchPoint) {
        return false;

    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#onPointerDown()
     */
    @Override
    public boolean onPointerDown(PointF touchPoint) {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#onMove()
     */
    @Override
    public boolean onMove(PointF touchPoint) {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#onPointerUp()
     */
    @Override
    public boolean onPointerUp(PointF touchPoint) {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#onUp()
     */
    @Override
    public boolean onUp(PointF touchPoint) {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#onCancel()
     */
    @Override
    public boolean onCancel() {
        return false;
    }

}
