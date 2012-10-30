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

    /**
     * The view for which this handler was created.
     */
    protected final CaliView parentView;

    /**
     * The value returned by {@link #done()}: subclasses should change the value
     * of this field to <code>true</code> whenever their management for a
     * sequence of touch events is completed.
     * 
     * <p>
     * Every time this handler is passed a new {@link MotionEvent#ACTION_DOWN},
     * the value of this field is reset to <code>false</code>.
     */
    protected boolean actionCompleted;

    private final String name;

    /**
     * Creates a new handler with the argument name.
     * 
     * @param name
     *            the name that identifies this handler
     * @param parent
     *            the parent view
     */
    public GenericTouchHandler(String name, CaliView parent) {
        this.name = name;
        parentView = parent;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#processTouchEvent(int,
     * android.graphics.PointF)
     */
    @Override
    public boolean processTouchEvent(int action, PointF touched,
            MotionEvent event) {
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            actionCompleted = false;
            return onDown(touched);
        case MotionEvent.ACTION_MOVE:
            return onMove(touched);
        case MotionEvent.ACTION_POINTER_DOWN:
            return onPointerDown(touched, event);
        case MotionEvent.ACTION_POINTER_UP:
            return onPointerUp(touched, event);
        case MotionEvent.ACTION_UP:
            return onUp(touched);
        case MotionEvent.ACTION_CANCEL:
            return onCancel();
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.TouchHandler#done()
     */
    @Override
    public boolean done() {
        return actionCompleted;
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
    public boolean onPointerDown(PointF touchPoint, MotionEvent event) {
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
    public boolean onPointerUp(PointF touchPoint, MotionEvent event) {
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

    public String toString() {
        return name;
    }

}
