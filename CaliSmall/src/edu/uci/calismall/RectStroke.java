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

import android.graphics.Paint;
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
    
    /**
     * Creates a clone of the argument <tt>RectStroke</tt>, copying its content.
     * 
     * @param clone
     *            the stroke to be cloned
     */
    RectStroke(RectStroke clone) {
        super(clone.parentView);
        if (clone.points.size() > 0) {
            PointF start = clone.points.get(0);
            setStart(new PointF(start.x, start.y));
        }
        for (int i = 1; i < clone.points.size(); i++) {
            PointF add = clone.points.get(i);
            points.add(new PointF(add.x, add.y));
        }
        createPath();
        setBoundaries();
    }

    private void createPath() {
        path.lineTo(points.get(1).x, points.get(1).y);
        path.lineTo(points.get(2).x, points.get(2).y);
        path.lineTo(points.get(3).x, points.get(3).y);
        path.lineTo(points.get(0).x, points.get(0).y);
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
    public RectStroke fromJSON(JSONObject jsonData) throws JSONException {
        id = jsonData.getLong("id");
        color = jsonData.getInt("c");
        strokeWidth = (float) jsonData.getDouble("w");
        style = Paint.Style.valueOf(jsonData.getString("s"));
        for (PointF point : parsePoints(jsonData))
            points.add(point);
        createPath();
        setBoundaries();
        return this;
    }

}
