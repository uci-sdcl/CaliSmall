/**
 * Utils.java Created on Oct 19, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.List;

import android.util.Log;

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
}
