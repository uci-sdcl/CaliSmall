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
public class RoundRectStroke extends Stroke {

    /**
     * Empty constructor used when creating objects from deserialization.
     * 
     * @param parentView
     *            the view that will store this stroke
     */
    public RoundRectStroke(CaliView parentView) {
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
    public RoundRectStroke(Stroke copyStyleFrom, RectF borders, float radius) {
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
        json.put("r", true);
        return json;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Stroke#fromJSON(org.json.JSONObject)
     */
    @Override
    public Stroke fromJSON(JSONObject jsonData) throws JSONException {
        try {
            id = jsonData.getLong("id");
        } catch (JSONException e) {
            Utils.debug("old format!");
        }
        color = jsonData.getInt("c");
        strokeWidth = (float) jsonData.getDouble("w");
        style = Style.valueOf(jsonData.getString("s"));
        for (PointF point : parsePoints(jsonData))
            points.add(point);
        createPath();
        setBoundaries();
        return this;
    }
}
