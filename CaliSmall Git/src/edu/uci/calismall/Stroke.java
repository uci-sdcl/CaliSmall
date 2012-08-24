/**
 * Stroke.java
 * Created on Jul 22, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package edu.uci.calismall;

import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

/**
 * Contains a {@link Path} and all of the style attributes to be set to the
 * {@link Paint} object used to draw it on canvas.
 * 
 * <p>
 * Every time {@link View#onDraw(android.graphics.Canvas)} is called all strokes
 * are rendered according to their styles by updating the {@link Paint} object
 * according to what's in this object.
 * 
 * @author Michele Bonazza
 */
class Stroke implements Parcelable {

	private static final int DEFAULT_COLOR = Color.BLACK;
	private static final Paint.Style DEFAULT_STYLE = Paint.Style.STROKE;
	private final Path path;
	private final float[] matrixValues;
	private float startX, startY;
	private final Region boundaries;
	private Paint.Style style = DEFAULT_STYLE;
	private float strokeWidth = CaliSmall.ABS_STROKE_WIDTH;
	private int color = DEFAULT_COLOR;
	private boolean inScrap, locked;
	private MaskFilter maskFilter;

	/**
	 * Creates a new stroke, called when the user lifts her finger after drawing
	 * a line or when the style for the current Path changes.
	 * 
	 * @param path
	 *            the path currently being drawn
	 * @param previousStroke
	 *            the stroke in use from which style is to be copied, if
	 *            <code>null</code> all default values are used
	 */
	Stroke(Path path, Stroke previousStroke) {
		this.path = path;
		this.boundaries = new Region();
		matrixValues = new float[9];
		if (previousStroke != null) {
			this.strokeWidth = previousStroke.getStrokeWidth();
			this.color = previousStroke.getColor();
			this.maskFilter = previousStroke.getMaskFilter();
		}
	}

	/**
	 * Copy constructor for stroke.
	 * 
	 * <p>
	 * Paths within the <tt>copy</tt> stroke will be copied into new paths
	 * associated with this stroke.
	 * 
	 * @param copy
	 *            the stroke to be copied
	 */
	Stroke(Stroke copy) {
		this(new Path(copy.getPath()), copy);
	}

	/**
	 * Returns the vector path for this stroke.
	 * 
	 * @return the path
	 */
	public Path getPath() {
		return path;
	}

	/**
	 * Returns whether this stroke is empty, i.e. if it only contains an empty
	 * path.
	 * 
	 * @return <code>true</code> if the underlying path is empty
	 * @see Path
	 */
	public boolean isEmpty() {
		return path.isEmpty();
	}

	/**
	 * Sets the style of this stroke to the argument <tt>style</tt>.
	 * 
	 * @param style
	 *            the style for this stroke
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setStyle(Paint.Style style) {
		this.style = style;
		return this;
	}

	/**
	 * Returns the style for this stroke.
	 * 
	 * @return the style to be applied to a {@link Paint} in order to display
	 *         this stroke
	 */
	public Paint.Style getStyle() {
		return style;
	}

	/**
	 * Locks this stroke, making calls to {@link #setInScrap(boolean)}
	 * ineffective unless preceeded by a call to {@link #unlock()}.
	 */
	public void lock() {
		this.locked = true;
	}

	/**
	 * Unlocks this stroke, making calls to {@link #setInScrap(boolean)}
	 * effective again.
	 */
	public void unlock() {
		this.locked = false;
	}

	/**
	 * Sets the first point of this stroke.
	 * 
	 * @param startPoint
	 *            the first point of this stroke
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setStart(PointF startPoint) {
		this.startX = startPoint.x;
		this.startY = startPoint.y;
		return this;
	}

	/**
	 * Returns the first point in this stroke.
	 * 
	 * @return the point from which the user started to draw this stroke
	 */
	public PointF getStartPoint() {
		return new PointF(startX, startY);
	}

	/**
	 * Returns the width of this stroke.
	 * 
	 * @return the argument to be passed to {@link Paint#setStrokeWidth(float)}
	 *         to display this stroke
	 */
	public float getStrokeWidth() {
		return strokeWidth;
	}

	/**
	 * Sets the width of this stroke.
	 * 
	 * @param strokeWidth
	 *            the new width for this stroke
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setStrokeWidth(float strokeWidth) {
		this.strokeWidth = strokeWidth;
		return this;
	}

	/**
	 * Returns the color of this stroke.
	 * 
	 * @return the color to be passed to {@link Paint#setColor(int)} to display
	 *         this stroke
	 */
	public int getColor() {
		return color;
	}

	/**
	 * Sets the color for this stroke.
	 * 
	 * @param color
	 *            the color for this stroke
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setColor(int color) {
		this.color = color;
		return this;
	}

	/**
	 * Applies the argument transformation matrix to this stroke.
	 * 
	 * @param matrix
	 *            the matrix containing deltas from the current stroke position
	 */
	public void transform(Matrix matrix) {
		matrix.getValues(matrixValues);
		startX += matrixValues[2];
		startY += matrixValues[5];
		path.transform(matrix);
	}

	/**
	 * Returns the mask filter applied to this stroke.
	 * 
	 * @return the maskFilter to be passed to
	 *         {@link Paint#setMaskFilter(MaskFilter)} to display this stroke
	 */
	public MaskFilter getMaskFilter() {
		return maskFilter;
	}

	/**
	 * Sets the mask filter for this stroke.
	 * 
	 * @param maskFilter
	 *            the maskFilter to set to this stroke
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setMaskFilter(MaskFilter maskFilter) {
		this.maskFilter = maskFilter;
		return this;
	}

	/**
	 * Returns a copy of this stroke's boundaries.
	 * 
	 * <p>
	 * {@link android.graphics.Region.Op} can be performed on the returned
	 * object, as it doesn't affect the <tt>Stroke</tt>'s internal state.
	 * 
	 * @return a copy of the boundaries computed for this stroke
	 */
	public Region getBoundaries() {
		return new Region(boundaries);
	}

	/**
	 * Computes the boundaries for this stroke.
	 * 
	 * <p>
	 * To be called after <tt>onDown()</tt>, when the stroke is complete.
	 * 
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setBoundaries() {
		RectF rect = new RectF();
		path.computeBounds(rect, true);
		boundaries.setPath(
				path,
				new Region(new Rect(Math.round(rect.left),
						Math.round(rect.top), Math.round(rect.right), Math
								.round(rect.bottom))));
		return this;
	}

	/**
	 * Resets this stroke replacing its path with the argument <tt>path</tt>.
	 * 
	 * @param path
	 *            the new path that replaces this stroke's
	 */
	public void setPath(Path path) {
		this.path.reset();
		this.path.addPath(path);
		this.path.setFillType(path.getFillType());
		setBoundaries();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.os.Parcelable#describeContents()
	 */
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
	 */
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub

	}

	/**
	 * Returns whether this stroke is part of a scrap.
	 * 
	 * <p>
	 * If a stroke is part of a scrap its drawing is handled by {@link Scrap}
	 * <tt>draw*</tt> methods, so that overlay effects can be correctly
	 * displayed.
	 * 
	 * @return <code>true</code> if this stroke is within a scrap
	 */
	public boolean isInScrap() {
		return inScrap;
	}

	/**
	 * Sets whether this stroke is part of a scrap.
	 * 
	 * <p>
	 * If a stroke is part of a scrap its drawing is handled by {@link Scrap}
	 * <tt>draw*</tt> methods, so that overlay effects can be correctly
	 * displayed.
	 * 
	 * <p>
	 * If {@link #lock()} has been called on this stroke, calls to this method
	 * won't alter the internal state of this object unless {@link #unlock()} is
	 * called.
	 * 
	 * @param inScrap
	 *            <code>true</code> if this stroke is now part of a scrap,
	 *            <code>false</code> if it's been removed from a scrap
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setInScrap(boolean inScrap) {
		if (!locked) {
			this.inScrap = inScrap;
		}
		return this;
	}
}
