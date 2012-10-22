/**
 * RectStroke.java Created on Oct 12, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

/**
 * A rounded rect stroke.
 * 
 * <p>
 * <tt>RectStroke</tt>'s are used whenever a {@link Scrap}'s border have been
 * "rectified" by the {@link BubbleMenu}'s <i>shrinkWrapped</i> button.
 * 
 * @author Michele Bonazza
 * 
 */
public class RectStroke extends Stroke {

    /**
     * Empty constructor used when creating objects from deserialization.
     * 
     * @param parentView
     *            the view that will store this stroke
     */
    public RectStroke(CaliView parentView) {
        super(parentView);
    }

    /**
     * Creates a new rect stroke using the same style as the argument
     * <tt>Stroke</tt>, whose outer borders will be aligned to the argument
     * <tt>borders</tt> and having its corners rounded by the argument
     * <tt>radius</tt>.
     * 
     * @param copyStyleFrom
     *            the stroke from which style will be copied
     * @param borders
     *            the outer borders for this stroke
     * @param radius
     *            the radius (as would be used in
     *            {@link Path#addRoundRect(RectF, float, float, android.graphics.Path.Direction)}
     *            ) for the corners
     */
    public RectStroke(Stroke copyStyleFrom, RectF borders, float radius) {
        super(copyStyleFrom.parentView, new Path(), copyStyleFrom);
        // points are arranged starting from top-left corner clockwise
        setStart(new PointF(borders.left, borders.top + radius));
        points.add(new PointF(borders.left, borders.top));
        points.add(new PointF(borders.left + radius, borders.top));
        // top right corner
        points.add(new PointF(borders.right - radius, borders.top));
        points.add(new PointF(borders.right, borders.top));
        points.add(new PointF(borders.right, borders.top + radius));
        // bottom right corner
        points.add(new PointF(borders.right, borders.bottom - radius));
        points.add(new PointF(borders.right, borders.bottom));
        points.add(new PointF(borders.right - radius, borders.bottom));
        // bottom left corner
        points.add(new PointF(borders.left + radius, borders.bottom));
        points.add(new PointF(borders.left, borders.bottom));
        points.add(new PointF(borders.left, borders.bottom - radius));
        createPath();
    }

    private void createPath() {
        // 4 as in, the sides of a rectangle...
        for (int i = 0; i < 4; i++) {
            PointF firstAnchor = points.get(i * 3);
            PointF corner = points.get(i * 3 + 1);
            PointF secondAnchor = points.get(i * 3 + 2);
            path.lineTo(firstAnchor.x, firstAnchor.y);
            path.quadTo(corner.x, corner.y, secondAnchor.x, secondAnchor.y);
        }
        path.close();
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Stroke#toJSON()
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = super.toJSON();
        json.put("rect", true);
        return json;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Stroke#fromJSON(org.json.JSONObject)
     */
    @Override
    public Stroke fromJSON(JSONObject jsonData) throws JSONException {
        id = UUID.fromString(jsonData.getString("id"));
        color = jsonData.getInt("color");
        strokeWidth = (float) jsonData.getDouble("width");
        style = Style.valueOf(jsonData.getString("style"));
        for (PointF point : parsePoints(jsonData))
            points.add(point);
        createPath();
        setBoundaries();
        return this;
    }
}
