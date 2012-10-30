/**
 * TouchHandler.java Created on Oct 19, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import android.graphics.PointF;
import android.view.MotionEvent;

/**
 * Manages touch events coming from the user.
 * 
 * @author Michele Bonazza
 */
public interface TouchHandler {

    /**
     * Processes the event labeled with the argument <tt>action</tt> which took
     * place at the argument point.
     * 
     * @param action
     *            the result of calling {@link MotionEvent#getAction()} on the
     *            {@link MotionEvent} that generated this call
     * @param touchPoint
     *            the point in canvas coordinates at which the action took place
     * @param event
     *            the event to be processed
     * @return <code>true</code> if this event was handled by this handler
     */
    boolean processTouchEvent(int action, PointF touchPoint, MotionEvent event);

    /**
     * Called when an {@link MotionEvent#ACTION_DOWN} is detected, that is
     * whenever the first touch event is detected for an action.
     * 
     * @param touchPoint
     *            the point in canvas coordinates touched by the user
     * 
     * @return <code>true</code> if the event should be "swallowed" by this
     *         method, <code>false</code> if it should be forwarded to other
     *         {@link TouchHandler}'
     */
    boolean onDown(PointF touchPoint);

    /**
     * Called when an {@link MotionEvent#ACTION_POINTER_DOWN} is detected, that
     * is whenever the second and any following touch event is detected for an
     * action.
     * 
     * @param touchPoint
     *            the point in canvas coordinates touched by the user
     * @param event
     *            the event that originated this call
     * 
     * @return <code>true</code> if the event should be "swallowed" by this
     *         method, <code>false</code> if it should be forwarded to other
     *         {@link TouchHandler}'
     */
    boolean onPointerDown(PointF touchPoint, MotionEvent event);

    /**
     * Called when an {@link MotionEvent#ACTION_MOVE} is detected, that is
     * whenever any of the previously detected touch pointers is dragged along
     * the screen.
     * 
     * @param touchPoint
     *            the point in canvas coordinates touched by the user
     * 
     * @return <code>true</code> if the event should be "swallowed" by this
     *         method, <code>false</code> if it should be forwarded to other
     *         {@link TouchHandler}'
     */
    boolean onMove(PointF touchPoint);

    /**
     * Called when an {@link MotionEvent#ACTION_POINTER_UP} is detected, that is
     * whenever one of the touch pointer is destroyed because of the user no
     * longer touching the screen.
     * 
     * @param touchPoint
     *            the point in canvas coordinates touched by the user
     * 
     * @param event
     *            the event that originated this call
     * 
     * @return <code>true</code> if the event should be "swallowed" by this
     *         method, <code>false</code> if it should be forwarded to other
     *         {@link TouchHandler}'
     */
    boolean onPointerUp(PointF touchPoint, MotionEvent event);

    /**
     * Called when an {@link MotionEvent#ACTION_DOWN} is detected, that is
     * whenever the last pointer is destroyed because the user lifted all her
     * fingers/styluses from the screen.
     * 
     * @param touchPoint
     *            the point in canvas coordinates touched by the user
     * 
     * @return <code>true</code> if the event should be "swallowed" by this
     *         method, <code>false</code> if it should be forwarded to other
     *         {@link TouchHandler}'
     */
    boolean onUp(PointF touchPoint);

    /**
     * Called when an {@link MotionEvent#ACTION_DOWN} is detected, that is
     * whenever users press some button that cancels the current action.
     * 
     * @return <code>true</code> if the event should be "swallowed" by this
     *         method, <code>false</code> if it should be forwarded to other
     *         {@link TouchHandler}'
     */
    boolean onCancel();

    /**
     * Returns whether this handler has completed handling an action.
     * 
     * @return <code>true</code> if this handler has completed its job,
     *         <code>false</code> if touch events must still be redirected to
     *         this handler
     */
    boolean done();
}
