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

import android.util.Log;

/**
 * A list of {@link CaliSmallElement}'s sorted by their position along the two
 * (X, Y) axes.
 * 
 * @author Michele Bonazza
 */
public class SpaceOccupationList {

	private final List<CaliSmallElement> sortedByX;
	private final Comparator<CaliSmallElement> comparator;
	private final Comparator<CaliSmallElement> finderComparator;

	/**
	 * Creates a new list.
	 */
	public SpaceOccupationList() {
		sortedByX = new ArrayList<CaliSmallElement>();
		comparator = new CaliSmallElement.XComparator<CaliSmallElement>();
		finderComparator = new CaliSmallElement.XFinderComparator<CaliSmallElement>();
	}

	/**
	 * Adds an element to this list, keeping the list sorted.
	 * 
	 * @param element
	 *            the element to be added to the list
	 */
	public void add(CaliSmallElement element) {
		int position = Collections.binarySearch(sortedByX, element, comparator);
		if (position < 0) {
			position = -(position + 1);
		}
		if (position == sortedByX.size()
				|| !sortedByX.get(position).equals(element)) {
			sortedByX.add(position, element);
			Log.d(CaliSmall.TAG, "added element " + element + ", new size is "
					+ sortedByX.size());
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
	 * @return a (potentially empty) list containing all candidates for
	 *         intersection with the argument <tt>element</tt>
	 */
	public List<CaliSmallElement> findIntersectionCandidates(
			CaliSmallElement element) {
		List<CaliSmallElement> candidates = new ArrayList<CaliSmallElement>();
		int position = Collections.binarySearch(sortedByX, element, comparator);
		Log.d(CaliSmall.TAG, "test element is in position " + position + " x: "
				+ element.getXPos() + " width: " + element.getWidth());
		if (position < 0) {
			position = -(position + 1);
		}
		// look within the element's neighborhood
		findIntersectingLeftNeighbors(element, position, candidates);
		findIntersectingRightNeighbors(element, ++position, candidates);
		return candidates;
	}

	/**
	 * Removes all elements from this list.
	 */
	public void clear() {
		sortedByX.clear();
	}

	/**
	 * Removes all of the argument elements from this list.
	 * 
	 * @param toBeRemoved
	 *            a list of elements that have been erased
	 */
	public void removeAll(List<? extends CaliSmallElement> toBeRemoved) {
		for (CaliSmallElement element : toBeRemoved) {
			remove(element);
		}
	}

	private void findIntersectingLeftNeighbors(CaliSmallElement element,
			int position, List<CaliSmallElement> candidates) {
		for (int i = position; i > -1; i--) {
			CaliSmallElement candidate = sortedByX.get(i);
			Log.d(CaliSmall.TAG,
					String.format("%s <-- %s ?", candidate, element));
			if (candidate.getID().equals(element.getID())) {
				// don't add the element itself
				Log.d(CaliSmall.TAG, "same!");
				continue;
			}
			if (!candidate.intersectsX(element)) {
				// it's pointless to keep going left
				Log.d(CaliSmall.TAG, "no intersection");
				break;
			}
			if (candidate.intersectsY(element)) {
				candidates.add(candidate);
				Log.d(CaliSmall.TAG, "*** intersection ***");
			}
		}
	}

	private void findIntersectingRightNeighbors(CaliSmallElement element,
			int position, List<CaliSmallElement> candidates) {
		for (int i = position; i < sortedByX.size(); i++) {
			CaliSmallElement candidate = sortedByX.get(i);
			Log.d(CaliSmall.TAG,
					String.format("%s --> %s ?", element, candidate));
			if (candidate.getID().equals(element.getID())) {
				// don't add the element itself
				Log.d(CaliSmall.TAG, "same!");
				continue;
			}
			if (!candidate.intersectsX(element)) {
				// it's pointless to keep going right
				Log.d(CaliSmall.TAG, "no intersection");
				break;
			}
			if (candidate.intersectsY(element)) {
				candidates.add(candidate);
				Log.d(CaliSmall.TAG, "*** intersection ***");
			}
		}
	}

	/**
	 * Removes an element from this list, if present, and keeps the list sorted.
	 * 
	 * @param element
	 *            the element to be removed from the list
	 */
	public void remove(CaliSmallElement element) {
		int position = Collections.binarySearch(sortedByX, element, comparator);
		if (position < 0) {
			// the element is not on the list
			return;
		}
		CaliSmallElement whatsThere = sortedByX.get(position);
		if (element.equals(whatsThere))
			sortedByX.remove(position);
	}

	/**
	 * Updates this list to mirror the change in position that the argument
	 * <tt>element</tt> underwent.
	 * 
	 * @param element
	 *            the element whose position changed
	 */
	public void update(CaliSmallElement element) {
		int position = Collections.binarySearch(sortedByX, element,
				finderComparator);
		if (position < 0) {
			// element's not on the list
			add(element);
		} else {
			if (Collections.binarySearch(sortedByX, element, comparator) != position) {
				remove(element);
				add(element);
			}
		}
	}
}
