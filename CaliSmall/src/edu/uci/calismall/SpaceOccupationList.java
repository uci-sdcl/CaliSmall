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
import java.util.Iterator;
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

	/**
	 * Creates a new list.
	 */
	public SpaceOccupationList() {
		sortedByX = new ArrayList<CaliSmallElement>();
		comparator = new CaliSmallElement.XComparator<CaliSmallElement>();
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
		if (position == sortedByX.size()) {
			sortedByX.add(element);
		} else {
			sortedByX.add(position, element);
		}
		Log.d(CaliSmall.TAG, toString());
		Log.d(CaliSmall.TAG, "added element " + element + " at [" + position
				+ "]");
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
		if (!sortedByX.isEmpty()) {
			Log.d(CaliSmall.TAG, "finding intersections. ### test is "
					+ element + " ###");
			Log.d(CaliSmall.TAG, toString());
			int position = Collections.binarySearch(sortedByX, element,
					comparator);
			if (position < 0) {
				position = -(position + 1);
			}
			// look within the element's neighborhood
			findIntersectingLeftNeighbors(element, position, candidates);
			findIntersectingRightNeighbors(element, ++position, candidates);
		}
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
		Collections.sort(toBeRemoved, comparator);
		Iterator<? extends CaliSmallElement> removeIterator = toBeRemoved
				.iterator();
		if (!removeIterator.hasNext())
			return;
		CaliSmallElement nextToBeRemoved = removeIterator.next();
		for (Iterator<CaliSmallElement> iterator = sortedByX.iterator(); iterator
				.hasNext();) {
			CaliSmallElement next = iterator.next();
			while (comparator.compare(next, nextToBeRemoved) > 0
					&& removeIterator.hasNext()) {
				nextToBeRemoved = removeIterator.next();
			}
			if (next.equals(nextToBeRemoved)) {
				iterator.remove();
			}
			if (!removeIterator.hasNext()
					&& comparator.compare(next, nextToBeRemoved) > 0) {
				return;
			}
		}
	}

	private void findIntersectingLeftNeighbors(CaliSmallElement element,
			int position, List<CaliSmallElement> candidates) {
		if (position == sortedByX.size())
			position = sortedByX.size() - 1;
		for (int i = position; i > -1; i--) {
			CaliSmallElement candidate = sortedByX.get(i);
			String testResult = String.format("[%d] <-- [test] ?", i);
			if (candidate.getID().equals(element.getID())) {
				// don't add the element itself
				testResult += "same!";
				Log.d(CaliSmall.TAG, testResult);
				continue;
			}
			if (!candidate.intersectsX(element)) {
				// it's pointless to keep going left
				testResult += " no intersection";
				Log.d(CaliSmall.TAG, testResult);
				break;
			}
			if (candidate.intersectsY(element)) {
				candidates.add(candidate);
				testResult += " *** intersection ***";
				Log.d(CaliSmall.TAG, testResult);
			}
		}
	}

	private void findIntersectingRightNeighbors(CaliSmallElement element,
			int position, List<CaliSmallElement> candidates) {
		for (int i = position; i < sortedByX.size(); i++) {
			CaliSmallElement candidate = sortedByX.get(i);
			String testResult = String.format("[test] --> [%d] ?", i);
			if (candidate.getID().equals(element.getID())) {
				// don't add the element itself
				testResult += "same!";
				Log.d(CaliSmall.TAG, testResult);
				continue;
			}
			if (!candidate.intersectsX(element)) {
				// it's pointless to keep going right
				testResult += " no intersection";
				Log.d(CaliSmall.TAG, testResult);
				break;
			}
			if (candidate.intersectsY(element)) {
				candidates.add(candidate);
				testResult += " *** intersection ***";
				Log.d(CaliSmall.TAG, testResult);
			}
		}
	}

	/**
	 * Removes an element from this list, if present, keeping the list sorted.
	 * 
	 * @param element
	 *            the element to be removed from the list
	 * @return <code>true</code> if the element was found and removed
	 */
	public boolean remove(CaliSmallElement element) {
		boolean removed = false, found = false;
		int position;
		for (position = 0; position < sortedByX.size(); position++) {
			if (sortedByX.get(position).equals(element)) {
				found = true;
				break;
			}
		}
		if (found) {
			removed = sortedByX.remove(position) != null;
		}
		if (removed)
			Log.d(CaliSmall.TAG, "Removed element at position [" + position
					+ "], updated list follows");
		else
			Log.d(CaliSmall.TAG, "request to remove " + element
					+ ", but it's not on the list!");
		Log.d(CaliSmall.TAG, toString());
		return removed;
	}

	/**
	 * Updates this list to mirror the change in position that the argument
	 * <tt>element</tt> underwent.
	 * 
	 * @param element
	 *            the element whose position changed
	 */
	public void update(CaliSmallElement element) {
		if (sortedByX.size() > 1) {
			boolean sorted = true;
			int position = -1;
			CaliSmallElement last = sortedByX.get(0);
			for (int i = 0; i < sortedByX.size(); i++) {
				CaliSmallElement next = sortedByX.get(i);
				if (position > -1) {
					sorted = sorted && comparator.compare(last, next) <= 0;
					break;
				}
				if (next.equals(element)) {
					// found it, check if it changed position
					position = i;
					sorted = comparator.compare(last, next) <= 0;
				}
				last = next;
			}
			if (!sorted) {
				Log.d(CaliSmall.TAG, "updating position of " + element);
				sortedByX.remove(position);
				add(element);
			} else {
				Log.d(CaliSmall.TAG, "item " + element + "not in the list!");
			}
		}
	}

	public String toString() {
		StringBuilder builder = new StringBuilder("size is ");
		builder.append(sortedByX.size());
		builder.append("\n");
		String newLine = "";
		for (int i = 0; i < sortedByX.size(); i++) {
			builder.append(newLine);
			builder.append("[");
			builder.append(i);
			builder.append("] ");
			builder.append(sortedByX.get(i));
			newLine = "\n";
		}
		return builder.toString();
	}
}
