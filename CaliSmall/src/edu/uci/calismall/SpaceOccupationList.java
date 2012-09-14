/**
 * SpaceOccupationList.java
 * Created on Sep 13, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A list of {@link CaliSmallElement}'s sorted by their position along the two
 * (X, Y) axes.
 * 
 * @author Michele Bonazza
 * @param <T>
 *            the actual subtype of {@link CaliSmallElement} that this list is
 *            going to contain
 */
public class SpaceOccupationList<T extends CaliSmallElement> {

	private final List<T> sortedByX;
	private final List<T> sortedByY;
	private final Comparator<T> xComparator;
	private final Comparator<T> yComparator;

	/**
	 * Creates a new list.
	 */
	public SpaceOccupationList() {
		sortedByX = new ArrayList<T>();
		sortedByY = new ArrayList<T>();
		xComparator = new CaliSmallElement.XComparator<T>();
		yComparator = new CaliSmallElement.YComparator<T>();
	}

	/**
	 * Adds an element to this list, keeping the list sorted.
	 * 
	 * @param element
	 *            the element to be added to the list
	 */
	public void add(T element) {
		insertSorted(element, sortedByX, xComparator);
		insertSorted(element, sortedByY, yComparator);
	}

	private void insertSorted(T element, List<T> list, Comparator<T> comparator) {
		int position = Collections.binarySearch(list, element, comparator);
		if (position < 0) {
			position = -(position + 1);
		}
		if (!list.get(position).equals(element))
			list.add(position, element);
		// T swap;
		// for (int i = position; i < list.size(); i++) {
		// swap = list.get(i);
		// list.set(position, element);
		// element = swap;
		// }
		// list.add(element);
	}

	/**
	 * Removes an element from this list, if present, and keeps the list sorted.
	 * 
	 * @param element
	 *            the element to be removed from the list
	 */
	public void remove(T element) {
		removeSorted(element, sortedByX, xComparator);
		removeSorted(element, sortedByY, yComparator);
	}

	private void removeSorted(T element, List<T> list, Comparator<T> comparator) {
		int position = Collections.binarySearch(list, element, comparator);
		if (position < 0) {
			// the element is not on the list
			return;
		}
		T whatsThere = list.get(position);
		if (element.equals(whatsThere))
			list.remove(position);
	}

	/**
	 * Updates this list to mirror the change in position that the argument
	 * <tt>element</tt> underwent.
	 * 
	 * @param element
	 *            the element whose position changed
	 */
	public void update(T element) {

	}
}
