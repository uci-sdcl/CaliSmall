/**
 * SpaceOccupationList.java
 * Created on Sep 13, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of {@link CaliSmallElement}'s sorted by their position along the two
 * (X, Y) axes.
 * 
 * @author Michele Bonazza
 */
public class SpaceOccupationList {
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
	protected List<CaliSmallElement> list;

	/**
	 * Creates a new list.
	 */
	public SpaceOccupationList() {
		list = new ArrayList<CaliSmallElement>();
	}

	/**
	 * Appends an element to this list.
	 * 
	 * @param element
	 *            the element to be added to the list
	 */
	public void add(CaliSmallElement element) {
		list.add(element);
	}

	/**
	 * Appends all of the elements in the argument list to this list.
	 * 
	 * @param elements
	 *            the list of elements that must be added to this list
	 */
	public void addAll(List<? extends CaliSmallElement> elements) {
		if (elements == null || elements.isEmpty())
			return;
		list.addAll(elements);
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
			if (candidate.getID().equals(element.getID())) {
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
		return list.remove(element);
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
