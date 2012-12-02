/**
 * CalicoElement.java Created on Sep 13, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.FloatMath;

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
public abstract class CaliSmallElement implements Comparable<CaliSmallElement> {

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
            if (lhs == null)
                return rhs == null ? 0 : 1;
            if (rhs == null)
                return -1;
            if (lhs.id == rhs.id)
                return 0;
            int whichFirst = Float.compare(lhs.topLeftPoint.x,
                    rhs.topLeftPoint.x);
            if (whichFirst == 0) {
                return lhs.width < rhs.width ? -1 : 1;
            }
            return whichFirst;
        }

    }

    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    /**
     * The region representing the area of this element.
     */
    protected final Region boundaries = new Region();

    /**
     * The ID for this element.
     */
    protected long id = ID_GENERATOR.incrementAndGet();

    /**
     * The direct parent of this element.
     */
    protected CaliSmallElement parent;

    /**
     * The previous direct parent of this element.
     */
    protected CaliSmallElement previousParent;

    /**
     * The rectangle enclosing this element.
     */
    protected RectF bounds;

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
     * Whether this element has already been added to the list of elements
     * within a {@link TempScrap} and should therefore be ignored when testing
     * for elements within the newly created {@link TempScrap}.
     */
    protected boolean addedToSelection;
    /**
     * The view within which this element was created.
     */
    protected final CaliView parentView;
    /**
     * The width of the {@link RectF} enclosing this element.
     */
    protected float width;
    /**
     * The height of the {@link RectF} enclosing this element.
     */
    protected float height;

    private boolean mustBeDrawn = true;
    /**
     * Whether this element must be deleted. Elements are only removed from
     * lists by the drawing thread, to avoid
     * {@link ConcurrentModificationException}'s while drawing.
     */
    private boolean toBeDeleted;

    private boolean committedToBg;

    /**
     * Creates a new element.
     * 
     * @param parentView
     *            the view within which this element lies.
     */
    protected CaliSmallElement(CaliView parentView) {
        this.parentView = parentView;
        this.bounds = new RectF();
    }

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
        previousTopLeftPoint.set(topLeftPoint.x, topLeftPoint.y);
        topLeftPoint.set(enclosingRect.left, enclosingRect.top);
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
    boolean intersectsX(CaliSmallElement other) {
        return (topLeftPoint.x <= other.topLeftPoint.x && topLeftPoint.x
                + width >= other.topLeftPoint.x)
                || (topLeftPoint.x >= other.topLeftPoint.x && other.topLeftPoint.x
                        + other.width >= topLeftPoint.x);
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
    boolean intersectsY(CaliSmallElement other) {
        return (topLeftPoint.y <= other.topLeftPoint.y && topLeftPoint.y
                + height >= other.topLeftPoint.y)
                || (topLeftPoint.y >= other.topLeftPoint.y && other.topLeftPoint.y
                        + other.height >= topLeftPoint.y);
    }

    /**
     * Updates the space occupation list containing this type of
     * <tt>CaliSmallElement</tt>'s to mirror the changes that this element
     * underwent.
     */
    protected abstract void updateSpaceOccupation();

    /**
     * Returns whether this element is smaller (-1) or bigger (1) than the
     * argument element. If the two elements have the same ID, 0 is returned; if
     * they're the same size but have different IDs, this method returns -1.
     */
    @Override
    public int compareTo(CaliSmallElement another) {
        if (another == null)
            return -1;
        if (id == another.id)
            return 0;
        return Float.compare(width + height, another.width + another.height);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public final boolean equals(Object o) {
        if (o == null || !(o instanceof CaliSmallElement))
            return false;
        return id == ((CaliSmallElement) o).id;
    }

    /**
     * Resets the selection status of all the elements in the argument list.
     * 
     * @param elements
     *            a list of elements whose selection status should be set to
     *            <code>false</code>
     */
    public static void resetSelectionStatus(
            List<? extends CaliSmallElement> elements) {
        for (CaliSmallElement element : elements) {
            element.addedToSelection = false;
        }
    }

    /**
     * Sets the selection status of all elements in the argument list to
     * <code>true</code>.
     * 
     * @param elements
     *            a list of elements whose selection status should be set to
     *            <code>true</code>
     */
    public static void setAllAddedToSelection(
            List<? extends CaliSmallElement> elements) {
        for (CaliSmallElement element : elements) {
            element.addedToSelection = true;
        }
    }

    /**
     * Marks this element as <i>committed</i> to the background image or unmarks
     * it.
     * 
     * <p>
     * An element is <i>committed</i> when the committer thread has added it to
     * the bitmap image that is drawn on the background. As soon as an element
     * is marked committed, Android's drawing thread is free to stop drawing it
     * on the foreground, which is reserved to active elements like strokes,
     * selections, ghost strokes and the bubble menu.
     * 
     * @param isCommitted
     *            whether this element can be discarded by the drawing thread
     */
    public void setCommitted(boolean isCommitted) {
        committedToBg = isCommitted;
    }

    /**
     * Returns whether this element has been <i>committed</i> to the background
     * image.
     * 
     * <p>
     * An element is <i>committed</i> when the committer thread has added it to
     * the bitmap image that is drawn on the background. As soon as an element
     * is marked committed, Android's drawing thread is free to stop drawing it
     * on the foreground, which is reserved to active elements like strokes,
     * selections, ghost strokes and the bubble menu.
     * 
     * @return <code>true</code> if the committer thread has committed this
     *         element to the background
     */
    public boolean isCommitted() {
        return committedToBg;
    }

    /**
     * Tests whether the argument point is within the area of this element.
     * 
     * @param point
     *            the point to be tested, may be <code>null</code>
     * @return <code>true</code> if the point is within this element's area,
     *         <code>false</code> if <tt>point</tt> is <code>null</code> or
     *         outside of this element's area
     */
    abstract boolean contains(PointF point);

    /**
     * Marks this element as <i>toBeDeleted</i>, so that calls to
     * {@link #deleteMarkedFromList(List, SpaceOccupationList)} will remove it
     * from lists.
     */
    public void delete() {
        toBeDeleted = true;
    }

    /**
     * Unmarks this element as <i>toBeDeleted</i>, so that calls to
     * {@link #deleteMarkedFromList(List, SpaceOccupationList)} won't remove it
     * from lists.
     */
    public void restore() {
        toBeDeleted = false;
    }

    /**
     * Tests whether the argument element is completely contained in this
     * element.
     * 
     * @param element
     *            the element to be tested
     * @return <code>true</code> if the argument element is completely within
     *         this element's area
     */
    boolean contains(CaliSmallElement element) {
        boolean outliers = false;
        for (PointF point : element.getPointsForInclusionTests()) {
            if (!boundaries.contains(Math.round(point.x), Math.round(point.y))) {
                outliers = true;
                break;
            }
        }
        return !outliers;
    }

    /**
     * Returns whether this element must be deleted.
     * 
     * <p>
     * Only the drawing thread can delete elements, otherwise
     * {@link ConcurrentModificationException}'s may be thrown.
     * 
     * @return <code>true</code> if this element must be deleted
     */
    boolean hasToBeDeleted() {
        return toBeDeleted;
    }

    /**
     * Returns whether this element must be drawn as vector data.
     * 
     * <p>
     * While being edited (moved, scaled, rotated) elements should not be drawn
     * using their vector data format, but a "snapshot" bitmap should be
     * preferred to speed up the drawing of the Canvas.
     * 
     * @return <code>true</code> if this element shall be drawn using its vector
     *         data format
     */
    boolean hasToBeDrawnVectorially() {
        return mustBeDrawn;
    }

    /**
     * Sets whether this element must be drawn.
     * 
     * <p>
     * While being edited (moved, scaled, rotated) elements should not be drawn
     * using their vector data format, but a "snapshot" bitmap should be
     * preferred to speed up the drawing of the Canvas.
     * 
     * @param mustBeDrawn
     *            <code>true</code> if this element shall be drawn using its
     *            vector data format
     */
    void mustBeDrawnVectorially(boolean mustBeDrawn) {
        this.mustBeDrawn = mustBeDrawn;
    }

    /**
     * Removes all elements marked for deletion from the argument lists.
     * 
     * <p>
     * An element is marked for deletion when {@link #hasToBeDeleted()} returns
     * <code>true</code>. This method removes the elements from both lists.
     * 
     * @param <T>
     *            the type of elements that the two lists store
     * @param deleteList
     *            the list containing all elements of type <tt>T</tt>, including
     *            elements that are not to be deleted
     * @param spaceOccupationList
     *            the space occupation list from which elements are also to be
     *            deleted
     */
    public static <T extends CaliSmallElement> void deleteMarkedFromList(
            List<T> deleteList, SpaceOccupationList<T> spaceOccupationList) {
        List<T> elementsToRemove = new ArrayList<T>();
        for (Iterator<T> iterator = deleteList.iterator(); iterator.hasNext();) {
            T next = iterator.next();
            if (next.toBeDeleted) {
                iterator.remove();
                elementsToRemove.add(next);
            }
        }
        spaceOccupationList.removeAll(elementsToRemove);
    }

    /**
     * Returns the region enclosing this element.
     * 
     * @return a new copy of the region enclosing this element
     */
    Region getBoundaries() {
        return new Region(boundaries);
    }

    /**
     * Updates the boundaries for this element using the argument {@link Path}
     * as perimeter for the area of this element.
     * 
     * @param path
     *            the perimeter of the region enclosing this element
     */
    protected void setBoundaries(Path path) {
        path.computeBounds(bounds, true);
        setArea(bounds);
        Rect intRegion = new Rect((int) FloatMath.floor(bounds.left),
                (int) FloatMath.floor(bounds.top),
                (int) FloatMath.ceil(bounds.right),
                (int) FloatMath.ceil(bounds.bottom));
        boundaries.setPath(path, new Region(intRegion));
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
    long getID() {
        return id;
    }

    /**
     * Returns the parent element for this element
     * 
     * @return the parent, may be <code>null</code>
     */
    CaliSmallElement getParent() {
        return parent;
    }

    /**
     * Returns the previous parent of this element.
     * 
     * @return the previous parent of this element
     */
    CaliSmallElement getPreviousParent() {
        return previousParent;
    }

    /**
     * Sets the parent of this element to the argument <tt>parent</tt> element,
     * also updating the <tt>previousParent</tt> field with the current parent.
     * 
     * @param parent
     *            the parent to set, may be <code>null</code>
     */
    void setParent(CaliSmallElement parent) {
        previousParent = this.parent;
        this.parent = parent;
    }

    /**
     * Sets the previous parent to the argument element.
     * 
     * <p>
     * Used by temp scraps.
     * 
     * @param previousParent
     *            the previous parent to set
     */
    void setPreviousParent(CaliSmallElement previousParent) {
        this.previousParent = previousParent;
    }

    /**
     * Returns a list of all points that must be tested when performing
     * inclusion tests.
     * 
     * <p>
     * This method <b>is not required</b> to make defensive copies, so altering
     * the returned may alter the internal state of the object.
     * 
     * @return a list containing all points of this element that must be
     *         included within some area for this element to be contained within
     *         said area
     */
    public abstract List<PointF> getPointsForInclusionTests();

    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName());
        builder.append(" ").append(id).append(" ")
                .append(Utils.pointToString(topLeftPoint)).append(" [")
                .append(Math.round(width)).append("x")
                .append(Math.round(height)).append("] ");
        return builder.toString();
    }
}
