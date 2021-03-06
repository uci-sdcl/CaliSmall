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
