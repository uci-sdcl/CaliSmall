/**
 * JSONSerializable.java Created on Oct 10, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An object that can be serialized to JSON format.
 * 
 * <p>
 * The type information is used to let deserialization of subtypes happen: if
 * the deserialization results in an object of a subtype, that object can be
 * created and return by the {@link #fromJSON(JSONObject)} method. Otherwise,
 * <tt>this</tt> is going to be returned.
 * 
 * @author Michele Bonazza
 * @param <T>
 *            the type of objects that are returned as an output of the
 *            deserialization
 */
public interface JSONSerializable<T> {

    /**
     * Serializes this object to JSON format.
     * 
     * @return the newly created <tt>JSONObject</tt> containing all data that is
     *         ready to be stored on disk/sent through the network
     * @throws JSONException
     *             if anything goes wrong while serializing this object
     */
    JSONObject toJSON() throws JSONException;

    /**
     * Populates all fields in the argument <tt>newObject</tt> with data
     * retrieved from <tt>jsonData</tt>.
     * 
     * @param jsonData
     *            an object parsed from a JSON string
     * @return the object that was populated with all data coming from the JSON
     *         representation
     * @throws JSONException
     *             if anything goes wrong while parsing data from JSON
     */
    T fromJSON(JSONObject jsonData) throws JSONException;
}
