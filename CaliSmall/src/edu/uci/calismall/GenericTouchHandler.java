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
