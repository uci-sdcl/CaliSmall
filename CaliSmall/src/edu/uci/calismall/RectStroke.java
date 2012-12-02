/**
 * RectStroke.java Created on Dec 1, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import android.graphics.PointF;
import android.graphics.RectF;

/**
 * A rectangular stroke.
 * 
 * <p>
 * <tt>RectStroke</tt>s are used to wrap images.
 * 
 * @author Michele Bonazza
 * 
 */
public class RectStroke extends Stroke {

    /**
     * Creates a new empty rect stroke, used when loading sketches from file.
     * 
     * @param parentView
     *            the current view
     */
    RectStroke(CaliView parentView) {
        super(parentView);
    }

    /**
     * Creates a new rect stroke whose outer borders will be aligned to the
     * argument <tt>borders</tt>.
     * 
     * @param parentView
     *            the current view
     * @param borders
     *            the outer borders for this stroke
     */
    public RectStroke(CaliView parentView, RectF borders) {
        super(parentView);
        // points are arranged starting from top-left corner clockwise
        setStart(new PointF(borders.left, borders.top));
        points.add(new PointF(borders.right, borders.top));
        points.add(new PointF(borders.right, borders.bottom));
        points.add(new PointF(borders.left, borders.bottom));
        createPath();
    }

    private void createPath() {
        path.lineTo(points.get(1).x, points.get(1).y);
        path.lineTo(points.get(2).x, points.get(2).y);
        path.lineTo(points.get(3).x, points.get(3).y);
        path.lineTo(points.get(0).x, points.get(0).y);
        path.close();
    }

}
