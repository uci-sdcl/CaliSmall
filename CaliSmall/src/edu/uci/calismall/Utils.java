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
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
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
     * Logs the argument message as a debug string.
     * 
     * <p>
     * This method splits the message into several in case the message is larger
     * than the largest String that LogCat can handle for a single print call.
     * 
     * <p>
     * The argument <tt>message</tt> can be a format string, in which case
     * {@link String#format(String, Object...)} is called with the same
     * parameters passed to this method.
     * 
     * <p>
     * Messages logged with this method will have {@link CaliSmall#TAG} as tag.
     * 
     * @param message
     *            the message to be logged
     * @param formatParms
     *            zero or more arguments to fill the format string with, in case
     *            <tt>message</tt> is a format string
     */
    public static void debug(String message, Object... formatParms) {
        int offset = 0;
        if (formatParms != null && formatParms.length > 0) {
            swapNullsWithStrings(formatParms);
            message = String.format(message, formatParms);
        }
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
     * Logs the output of {@link #listToString(String, List)} in case it's not
     * <code>null</code>.
     * 
     * @param listName
     *            the name of the list to be logged
     * @param list
     *            the list to be logged
     */
    public static void debugList(String listName, List<?> list) {
        String out = listToString(listName, list);
        if (out != null)
            debug(out);
    }

    /**
     * Iterates through all elements in the argument <tt>array</tt>, switching
     * each <code>null</code> item with a String containing "<tt>null</tt>" as
     * text.
     * 
     * @param array
     *            a non-<code>null</code> array that may contain
     *            <code>null</code> items
     */
    public static void swapNullsWithStrings(final Object[] array) {
        if (array != null) {
            for (int i = 0; i < array.length; i++) {
                if (array[i] == null)
                    array[i] = "null";
            }
        }
    }

    /**
     * Returns a string structured as such: <blockquote>
     * 
     * <pre>
     * listName: item1, item2, item3, ..., itemN
     * </pre>
     * 
     * </blockquote> where each <tt>item</tt> is the string returned by calling
     * {@link Object#toString()} on elements in the list.
     * 
     * <p>
     * If <tt>list</tt> is empty, <code>null</code> is returned.
     * 
     * @param listName
     *            the name of the argument list
     * @param list
     *            the list to be examined
     * @return a String as reported above or <code>null</code> if <tt>list</tt>
     *         is empty or any of the arguments are <code>null</code>
     */
    public static String listToString(String listName, List<?> list) {
        if (isAnyNull(listName, list))
            return null;
        return list.isEmpty() ? null : listName + ": " + join(", ", list);
    }

    /**
     * Returns a String that is empty in case the argument <tt>string</tt> is
     * <code>null</code>, the unmodified <tt>string</tt> otherwise.
     * 
     * @param string
     *            the string to be checked against <code>null</code>
     * @return the empty String if <tt>string</tt> is <code>null</code>, the
     *         argument <tt>string</tt> unmodified otherwise
     */
    public static String nonNull(final String string) {
        return string == null ? "" : string;
    }

    /**
     * Returns a String containing "<tt>null</tt>" as text if <tt>object</tt> is
     * <code>null</code>, the value returned by {@link Object#toString()} called
     * on <tt>object</tt> if it's not.
     * 
     * @param object
     *            the object to check
     * @return the value returned by <tt>toString()</tt> called on the object if
     *         it's not <code>null</code>, a <tt>"null"</tt> String otherwise
     */
    public static String nonNullString(Object object) {
        if (object == null)
            return "null";
        return object.toString();
    }

    /**
     * An equivalent of Python's <code>str.join()</code> function on lists: it
     * returns a String which is the concatenation of the strings in the
     * argument array. The separator between elements is the string providing
     * this method. The separator is appended after the first element and it is
     * not after the last element.
     * 
     * @param toJoin
     *            the separator
     * @param list
     *            a list of <code>Object</code>s on which
     *            {@link Object#toString()} will be called
     * @return the concatenation of String representations of the objects in the
     *         list
     */
    public static String join(String toJoin, Object[] list) {
        if (list == null || list.length == 0)
            return "";
        StringBuilder builder = new StringBuilder();
        String delimiter = nonNull(toJoin);
        int i = 0;
        for (; i < (list.length - 1); i++) {
            if (list[i] != null)
                builder.append(list[i]);
            builder.append(delimiter);
        }
        builder.append(getLast(list));
        return builder.toString();
    }

    /**
     * An equivalent of Python's <code>str.join()</code> function on lists: it
     * returns a String which is the concatenation of the strings in the
     * argument list. The separator between elements is the string providing
     * this method. The separator is appended after the first element and it is
     * not after the last element.
     * 
     * @param toJoin
     *            the separator
     * @param list
     *            a list of <code>Object</code>s on which
     *            {@link Object#toString()} will be called
     * @return the concatenation of String representations of the objects in the
     *         list
     */
    public static String join(String toJoin, List<?> list) {
        if (list == null || list.isEmpty())
            return "";
        StringBuilder builder = new StringBuilder();
        String delimiter = nonNull(toJoin);
        int i = 0;
        for (; i < list.size() - 1; i++) {
            if (list.get(i) != null)
                builder.append(list.get(i));
            builder.append(delimiter);
        }
        builder.append(getLast(list));
        return builder.toString();
    }

    /**
     * An equivalent of Python's <code>str.join()</code> function on lists: it
     * returns a String which is the concatenation of the strings in the
     * argument list. The separator between elements is the string providing
     * this method. The separator is appended after the first element and it is
     * not after the last element.
     * 
     * @param toJoin
     *            the separator
     * @param set
     *            a set of <code>Object</code>s on which
     *            {@link Object#toString()} will be called
     * @return the concatenation of String representations of the objects in the
     *         set
     */
    public static String join(String toJoin, Set<?> set) {
        return join(toJoin, set.toArray());
    }

    /**
     * Checks whether <b>any</b> of the provided objects is <code>null</code>.
     * 
     * @param objects
     *            a number of objects of any kind of which a check against
     *            <code>null</code> values is performed.
     * @return <code>true</code> if at least one of the arguments is
     *         <code>null</code>.
     */
    public static boolean isAnyNull(Object... objects) {
        for (Object o : objects) {
            if (o == null)
                return true;
        }
        return false;
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
     * This method works the same way that
     * {@link Matrix#mapPoints(float[], float[])} does, except it accepts a list
     * of points and executes in-place on those.
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
        return (float) Math.sqrt(((Math.pow(point2.x - point1.x, 2) + Math.pow(
                point2.y - point1.y, 2))));
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
