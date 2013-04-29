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

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Contains static methods that are used all over the code base.
 * 
 * @author Michele Bonazza
 * 
 */
public final class Utils {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    /**
     * Logs the argument message as a debug string.
     * 
     * <p>
     * This method splits the message into several in case the message is larger
     * than the largest String that LogCat can handle for a single print call.
     * 
     * @param message
     *            the message to be logged
     */
    public static void debug(String message) {
        int offset = 0;
        while (offset < message.length()) {
            Log.d(CaliSmall.TAG,
                    message.substring(
                            offset,
                            Math.min(offset + MAX_MESSAGE_LENGTH,
                                    message.length())));
            offset += MAX_MESSAGE_LENGTH;
        }
    }

    /**
     * Returns the last element in the argument <tt>array</tt>.
     * 
     * <p>
     * The last element is the one at index <tt>array.length - 1</tt>, or
     * <code>null</code> if <tt>array</tt> is empty or <code>null</code>.
     * 
     * @param <T>
     *            the type of objects stored into the array
     * @param array
     *            the array from which to retrieve the last element
     * @return the last element in the array or <code>null</code> if
     *         <tt>array</tt> is empty or <code>null</code>
     */
    public static <T> T getLast(T[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[array.length - 1];
    }

    /**
     * Returns the last element in the argument <tt>list</tt>.
     * 
     * <p>
     * The last element is the one at index <tt>list.size() - 1</tt>, or
     * <code>null</code> if <tt>list</tt> is empty or <code>null</code>.
     * 
     * @param <T>
     *            the type of objects stored into the list
     * @param list
     *            the list from which to retrieve the last element
     * @return the last element in the list or <code>null</code> if
     *         <tt>list</tt> is empty or <code>null</code>
     */
    public static <T> T getLast(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    /**
     * Returns what <tt>PointF.toString()</tt> should have returned, but Android
     * developers were too lazy to implement.
     * 
     * @param point
     *            the point of which a String representation must be returned
     * @return a String containing the point's coordinates enclosed within
     *         parentheses
     */
    public static String pointToString(PointF point) {
        return new StringBuilder("(").append(point.x).append(",")
                .append(point.y).append(")").toString();
    }

    /**
     * Returns what <tt>Point.toString()</tt> should have returned (without the
     * initial <tt>"Point"</tt> that the <tt>toString()</tt> default
     * implementation returns).
     * 
     * @param point
     *            the point of which a String representation must be returned
     * @return a String containing the point's coordinates enclosed within
     *         parentheses
     */
    public static String pointToString(Point point) {
        return new StringBuilder("(").append(point.x).append(",")
                .append(point.y).append(")").toString();
    }

    /**
     * Applies the transformations stored in the array of float values to the
     * argument list of points.
     * 
     * <p>
     * The float array can be obtained starting from a {@link Matrix} object by
     * calling <blockquote>
     * 
     * <pre>
     * Matrix myMatrix;
     * float[] matrixValues = new float[9];
     * myMatrix.getValues(matrixValues);
     * </pre>
     * 
     * </blockquote>
     * 
     * @param matrixValues
     *            the values to apply to all points in the list
     * @param points
     *            a list of points to which the transformations in the array
     *            will be applied
     */
    public static void applyMatrix(float[] matrixValues, List<PointF> points) {
        // variable names are the same used by Skia library
        final float tx = matrixValues[Matrix.MTRANS_X];
        final float ty = matrixValues[Matrix.MTRANS_Y];
        final float mx = matrixValues[Matrix.MSCALE_X];
        final float my = matrixValues[Matrix.MSCALE_Y];
        final float kx = matrixValues[Matrix.MSKEW_X];
        final float ky = matrixValues[Matrix.MSKEW_Y];
        /*
         * if rotation: skia messes up with the matrix, so sx and sy actually
         * store cosV, rx and ry store -sinV and sinV
         */
        for (PointF point : points) {
            final float originalY = point.y;
            point.y = point.x * ky + (point.y * my) + ty;
            point.x = point.x * mx + (originalY * kx) + tx;
        }
    }
    
    /**
     * Encodes the argument <tt>matrix</tt> into a JSON array.
     * 
     * <p>
     * Values are cast to <tt>double</tt> because of JSON lack for a primitive
     * <tt>float</tt> value.
     * 
     * @param matrix
     *            the matrix to be encoded
     * @return the matrix encoded into a JSON array, or <code>null</code> if
     *         <tt>matrix</tt> is <code>null</code> or inconsistent (i.e. it
     *         doesn't contain <tt>float</tt>'s)
     */
    public static JSONArray matrixToJson(Matrix matrix) {
        if (matrix == null)
            return null;
        JSONArray array = new JSONArray();
        float[] values = new float[9];
        matrix.getValues(values);
        for (float value : values) {
            try {
                array.put(value);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
        return array;
    }

    /**
     * Decodes a matrix encoded using {@link #matrixToJson(Matrix)} from JSON
     * format to a {@link Matrix} object.
     * 
     * @param array
     *            the encoded matrix
     * @return a matrix containing values from the JSON string (probably not
     *         100% equal to the original because of the
     *         <tt>float --&gt; double --&gt; float</tt> conversion) or
     *         <code>null</code> if <tt>array</tt> is <code>null</code> or
     *         doesn't contain a matrix
     */
    public static Matrix jsonToMatrix(JSONArray array) {
        if (array == null)
            return null;
        float[] values = new float[9];
        Matrix matrix = new Matrix();
        for (int i = 0; i < array.length(); i++) {
            try {
                values[i] = (float) array.getDouble(i);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
        matrix.setValues(values);
        return matrix;
    }

    /**
     * Returns the geometric distance between the two argument points.
     * 
     * @param point1
     *            the first point
     * @param point2
     *            the second point
     * @return the distance between the two points
     * @throws NullPointerException
     *             if any of the argument points is <code>null</code>
     */
    public static float getDistance(PointF point1, PointF point2) {
        if (point1.x == point2.x)
            return Math.abs(point2.y - point1.y);
        if (point1.y == point2.y)
            return Math.abs(point2.x - point1.x);
        return FloatMath.sqrt((float) (Math.pow(point2.x - point1.x, 2) + Math
                .pow(point2.y - point1.y, 2)));
    }

    /**
     * Returns a string that represents the symbolic name of the specified
     * action such as "ACTION_DOWN", "ACTION_POINTER_DOWN(3)" or an equivalent
     * numeric constant such as "35" if unknown. By Google.
     * 
     * @param action
     *            The action.
     * @return The symbolic name of the specified action.
     */
    public static String actionToString(int action) {
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            return "ACTION_DOWN";
        case MotionEvent.ACTION_UP:
            return "ACTION_UP";
        case MotionEvent.ACTION_CANCEL:
            return "ACTION_CANCEL";
        case MotionEvent.ACTION_OUTSIDE:
            return "ACTION_OUTSIDE";
        case MotionEvent.ACTION_MOVE:
            return "ACTION_MOVE";
        case MotionEvent.ACTION_HOVER_MOVE:
            return "ACTION_HOVER_MOVE";
        case MotionEvent.ACTION_SCROLL:
            return "ACTION_SCROLL";
        case MotionEvent.ACTION_HOVER_ENTER:
            return "ACTION_HOVER_ENTER";
        case MotionEvent.ACTION_HOVER_EXIT:
            return "ACTION_HOVER_EXIT";
        }
        int index = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_POINTER_DOWN:
            return "ACTION_POINTER_DOWN(" + index + ")";
        case MotionEvent.ACTION_POINTER_UP:
            return "ACTION_POINTER_UP(" + index + ")";
        default:
            return Integer.toString(action);
        }
    }
}
