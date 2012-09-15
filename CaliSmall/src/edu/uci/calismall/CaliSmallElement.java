/**
 * CalicoElement.java
 * Created on Sep 13, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.Comparator;
import java.util.UUID;

import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;

/**
 * A graphical item used in CaliSmall.
 * 
 * <p>
 * <tt>CaliSmallElement</tt>'s all take some space in the main {@link Canvas};
 * to speed up intersection/inclusion tests a
 * <tt>({@link PointF},float, float)</tt> triplet is kept for every
 * <tt>CaliSmallElement</tt>: the first element represents the coordinates of
 * the top-left corner of the {@link RectF} enclosing the element the two
 * <tt>float</tt>s are the rectangle width and height. This way a binary search
 * can be performed to filter potential candidates (a more refined test is to be
 * performed afterwards using {@link Region}'s).
 * 
 * <p>
 * Every <tt>CaliSmallElement</tt> <tt>CaliSmallElement</tt>'s need not be
 * primitive, i.e. a <tt>CaliSmallElement</tt> may contain another one (as for
 * {@link Stroke}'s within {@link Scrap}'s).
 * 
 * @author Michele Bonazza
 * 
 */
public abstract class CaliSmallElement {

	/**
	 * A comparator to sort elements by their position along the X coordinate.
	 * 
	 * @param <T>
	 *            the actual type of element to be sorted
	 * @author Michele Bonazza
	 */
	public static class XComparator<T extends CaliSmallElement> implements
			Comparator<T> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(T lhs, T rhs) {
			// if (lhs.id.equals(rhs.id))
			// return 0;
			int whichFirst = Float.compare(lhs.topLeftPoint.x,
					rhs.topLeftPoint.x);
			if (whichFirst == 0) {
				return lhs.width < rhs.width ? -1 : 1;
			}
			return whichFirst;
		}

	}

	/**
	 * A comparator to find elements whose position along the X coordinate has
	 * just changed.
	 * 
	 * @param <T>
	 *            the actual type of element to be found
	 * @author Michele Bonazza
	 */
	public static class XFinderComparator<T extends CaliSmallElement>
			implements Comparator<T> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(T lhs, T rhs) {
			// if (lhs.id.equals(rhs.id))
			// return 0;
			int whichFirst = Float.compare(lhs.previousTopLeftPoint.x,
					rhs.previousTopLeftPoint.x);
			if (whichFirst == 0) {
				return lhs.width < rhs.width ? -1 : 1;
			}
			return whichFirst;
		}

	}

	private final UUID id = UUID.randomUUID();

	/**
	 * The top-left corner of the {@link RectF} enclosing this element.
	 */
	protected PointF topLeftPoint = new PointF();
	/**
	 * The top-left corner of the {@link RectF} that was enclosing this element
	 * before the last call to {@link #setArea(RectF)}.
	 */
	protected PointF previousTopLeftPoint = topLeftPoint;
	/**
	 * The width of the {@link RectF} enclosing this element.
	 */
	private float width;
	/**
	 * The height of the {@link RectF} enclosing this element.
	 */
	private float height;

	/**
	 * Updates the information about the area occupied by this element
	 * extracting it by the argument <tt>enclosingRect</tt>.
	 * 
	 * <p>
	 * After this method is called, a call to {@link #updateSpaceOccupation()}
	 * is performed to update the list of space occupations of all elements of
	 * this kind.
	 * 
	 * @param enclosingRect
	 *            the rectangle enclosing this element
	 */
	protected void setArea(RectF enclosingRect) {
		previousTopLeftPoint.x = topLeftPoint.x;
		previousTopLeftPoint.y = topLeftPoint.y;
		topLeftPoint.x = enclosingRect.left;
		topLeftPoint.y = enclosingRect.top;
		width = enclosingRect.width();
		height = enclosingRect.height();
		updateSpaceOccupation();
	}

	/**
	 * Tests whether this element's X occupation intersect that of the argument
	 * element.
	 * 
	 * @param other
	 *            the element to be tested against this element for
	 *            one-dimensional intersection
	 * @return <code>true</code> if the topmost side of the two rectangles
	 *         enclosing the two elements overlap
	 */
	public boolean intersectsX(CaliSmallElement other) {
		return (topLeftPoint.x < other.topLeftPoint.x && topLeftPoint.x + width > other.topLeftPoint.x)
				|| (topLeftPoint.x > other.topLeftPoint.x && other.topLeftPoint.x
						+ other.width > topLeftPoint.x);
	}

	/**
	 * Tests whether this element's Y occupation intersect that of the argument
	 * element.
	 * 
	 * @param other
	 *            the element to be tested against this element for
	 *            one-dimensional intersection
	 * @return <code>true</code> if the left side of the two rectangles
	 *         enclosing the two elements overlap
	 */
	public boolean intersectsY(CaliSmallElement other) {
		return (topLeftPoint.y < other.topLeftPoint.y && topLeftPoint.y
				+ height > other.topLeftPoint.y)
				|| (topLeftPoint.y > other.topLeftPoint.y && other.topLeftPoint.y
						+ other.height > topLeftPoint.y);
	}

	/**
	 * Updates the space occupation list containing this type of
	 * <tt>CaliSmallElement</tt>'s to mirror the changes that this element
	 * underwent.
	 */
	protected abstract void updateSpaceOccupation();

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof CaliSmallElement))
			return false;
		return id.equals(((CaliSmallElement) o).id);
	}

	/**
	 * Returns the identifier for this element.
	 * 
	 * <p>
	 * This id is <b>not</b> the same as the id that is stored in Calico server,
	 * it's just a local id.
	 * 
	 * @return the id for this element
	 */
	public UUID getID() {
		return id;
	}

	/**
	 * Returns the X-coordinate of the top left point of the rectangle enclosing
	 * this element.
	 * 
	 * @return the X position of the top left point
	 */
	public float getXPos() {
		return topLeftPoint.x;
	}

	/**
	 * Returns the Y-coordinate of the top left point of the rectangle enclosing
	 * this element.
	 * 
	 * @return the Y position of the top left point
	 */
	public float getYPos() {
		return topLeftPoint.y;
	}

	/**
	 * Returns the X-coordinate of the top left point of the rectangle that was
	 * enclosing this element before the last call to {@link #setArea(RectF)}.
	 * 
	 * @return the X position of the top left point
	 */
	public float getPrevXPos() {
		return previousTopLeftPoint.x;
	}

	/**
	 * Returns the Y-coordinate of the top left point of the rectangle that was
	 * enclosing this element before the last call to {@link #setArea(RectF)}.
	 * 
	 * @return the Y position of the top left point
	 */
	public float getPrevYPos() {
		return previousTopLeftPoint.y;
	}

	/**
	 * Returns the width of the rectangle enclosing this element.
	 * 
	 * @return the enclosing rectangle's width
	 */
	public float getWidth() {
		return width;
	}

	/**
	 * Returns the height of the rectangle enclosing this element.
	 * 
	 * @return the enclosing rectangle's height
	 */
	public float getHeight() {
		return height;
	}

	public String toString() {
		return getClass().getSimpleName() + " " + id.toString();
	}
}
