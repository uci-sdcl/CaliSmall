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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.graphics.PointF;

/**
 * A list of {@link CaliSmallElement}'s sorted by their position along the two
 * (X, Y) axes.
 * 
 * @author Michele Bonazza
 * @param <T>
 *            the type of elements in this list
 */
public class SpaceOccupationList<T extends CaliSmallElement> {
    /**
     * Tag used for messages about intersection tests in LogCat files.
     */
    public static final String INTERSECTION = "intersections";
    /**
     * Tag used for messages about space occupation of elements in LogCat files.
     */
    public static final String SPACE_OCCUPATION = "space";
    /**
     * The list of elements kept by this <tt>SpaceOccupationList</tt>.
     */
    protected List<T> list;

    private final Map<String, T> idMap;

    /**
     * Creates a new list.
     */
    public SpaceOccupationList() {
        list = new ArrayList<T>();
        idMap = new HashMap<String, T>();
    }

    /**
     * Appends an element to this list.
     * 
     * @param element
     *            the element to be added to the list
     */
    public void add(T element) {
        list.add(element);
        idMap.put(String.valueOf(element.id), element);
    }

    /**
     * Appends all of the elements in the argument list to this list.
     * 
     * @param elements
     *            the list of elements that must be added to this list
     */
    public void addAll(List<T> elements) {
        if (elements == null || elements.isEmpty())
            return;
        list.addAll(elements);
        for (T element : elements) {
            idMap.put(String.valueOf(element.id), element);
        }
    }

    /**
     * Finds all elements whose boundaries rectangle intersects with that of the
     * argument <tt>element</tt>.
     * 
     * <p>
     * The fact that an element is returned by this method does <b>not</b> imply
     * that the two elements actually overlap: they could be shaped in such a
     * way that the enclosing rectangles intersect, but their actual contours do
     * not.
     * 
     * @param element
     *            the element of which all intersecting elements shall be found
     * @return a (potentially empty) list containing all elements that can
     *         potentially intersect the argument <tt>element</tt>; the list is
     *         sorted by the X position of the elements
     */
    public List<CaliSmallElement> findIntersectionCandidates(
            CaliSmallElement element) {
        List<CaliSmallElement> candidates = new ArrayList<CaliSmallElement>();
        for (CaliSmallElement candidate : list) {
            if (candidate.getID() == element.getID()) {
                // don't add the element itself
                continue;
            }
            if (!candidate.intersectsX(element)) {
                continue;
            }
            if (candidate.intersectsY(element)) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * Returns the element having the argument <tt>id</tt>, if any is stored
     * within this map.
     * 
     * @param id
     *            the ID to be searched
     * @return the element or <code>null</code> if no element has the specified
     *         id, or <code>null</code> was provided as argument
     */
    public T getById(String id) {
        if (id == null)
            return null;
        return idMap.get(id);
    }

    /**
     * Finds all elements whose area contain the argument <tt>point</tt>.
     * 
     * @param point
     *            the point to be tested
     * @return a (potentially empty) list containing all elements that <b>do
     *         contain</b> the argument point
     */
    public List<CaliSmallElement> findContainerCandidates(PointF point) {
        List<CaliSmallElement> candidates = new ArrayList<CaliSmallElement>();
        for (CaliSmallElement candidate : list) {
            if (candidate.contains(point))
                candidates.add(candidate);
        }
        return candidates;
    }

    /**
     * Removes all elements from this list.
     */
    public void clear() {
        list.clear();
    }

    /**
     * Removes all of the argument elements from this list.
     * 
     * @param toBeRemoved
     *            a list of elements that have been erased
     */
    public void removeAll(List<? extends CaliSmallElement> toBeRemoved) {
        for (CaliSmallElement element : toBeRemoved) {
            idMap.remove(element.id);
        }
        list.removeAll(toBeRemoved);
    }

    /**
     * Removes an element from this list, if present.
     * 
     * @param element
     *            the element to be removed from the list
     * @return <code>true</code> if the element was found and removed
     */
    public boolean remove(CaliSmallElement element) {
        idMap.remove(element.id);
        return list.remove(element);
    }

    /**
     * Returns the number of elements in this list.
     * 
     * @return the size of this list
     */
    public int size() {
        return list.size();
    }

    /**
     * Updates this list to mirror the change in position that the argument
     * <tt>element</tt> underwent.
     * 
     * @param element
     *            the element whose position changed
     */
    public void update(CaliSmallElement element) {
        // do nothing, we don't care about the list being sorted
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("size is ");
        builder.append(list.size());
        builder.append("\n");
        String newLine = "";
        for (int i = 0; i < list.size(); i++) {
            builder.append(newLine);
            builder.append("[");
            builder.append(i);
            builder.append("] ");
            builder.append(list.get(i));
            newLine = "\n";
        }
        return builder.toString();
    }
}
