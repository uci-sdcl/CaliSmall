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
 * @author Michele Bonazza
 */
public interface JSONSerializable {

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
     * @throws JSONException
     *             if anything goes wrong while parsing data from JSON
     */
    void fromJSON(JSONObject jsonData) throws JSONException;
}
