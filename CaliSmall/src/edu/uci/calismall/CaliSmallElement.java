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
		public int compare(CaliSmallElement lhs, CaliSmallElement rhs) {
			int whichFirst = Float.compare(lhs.topLeftPoint.x,
					rhs.topLeftPoint.x);
			if (whichFirst == 0) {
				return lhs.width < rhs.width ? -1 : 1;
			}
			return whichFirst;
		}

	}

	/**
	 * A comparator to sort elements by their position along the Y coordinate.
	 * 
	 * @param <T>
	 *            the actual type of element to be sorted
	 * @author Michele Bonazza
	 */
	public static class YComparator<T extends CaliSmallElement> implements
			Comparator<T> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(CaliSmallElement lhs, CaliSmallElement rhs) {
			int whichFirst = Float.compare(lhs.topLeftPoint.y,
					rhs.topLeftPoint.y);
			if (whichFirst == 0) {
				return lhs.height < rhs.height ? -1 : 1;
			}
			return whichFirst;
		}

	}

	private final UUID id = UUID.randomUUID();

	/**
	 * The top-left corner of the {@link RectF} enclosing this element.
	 */
	private PointF topLeftPoint = new PointF();
	/**
	 * The top-left corner of the {@link RectF} that was enclosing this element
	 * before the last call to {@link #setArea(RectF)}.
	 */
	private PointF previousTopLeftPoint = topLeftPoint;
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
	 * This method also stores the previous values associated with this element
	 * so that they can be retrieved in the {@link SpaceOccupationList} and
	 * updates the argument <tt>list</tt> to mirror the changes that happened to
	 * this element
	 * 
	 * @param <T>
	 *            the actual type of elements within the argument <tt>list</tt>
	 * 
	 * @param enclosingRect
	 *            the rectangle enclosing this element
	 * @param list
	 *            the list containing all elements of type <tt>T</tt>
	 */
	@SuppressWarnings("unchecked")
	protected <T extends CaliSmallElement> void setArea(RectF enclosingRect,
			SpaceOccupationList<T> list) {
		previousTopLeftPoint.x = topLeftPoint.x;
		previousTopLeftPoint.y = topLeftPoint.y;
		topLeftPoint.x = enclosingRect.left;
		topLeftPoint.y = enclosingRect.top;
		width = enclosingRect.width();
		height = enclosingRect.height();
		list.update((T) this);
	}

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
	 * enclosing this element before the last call to
	 * {@link #setArea(RectF, SpaceOccupationList)}.
	 * 
	 * @return the X position of the top left point
	 */
	public float getPrevXPos() {
		return previousTopLeftPoint.x;
	}

	/**
	 * Returns the Y-coordinate of the top left point of the rectangle that was
	 * enclosing this element before the last call to
	 * {@link #setArea(RectF, SpaceOccupationList)}.
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
}
