/**
 * Stroke.java
 * Created on Jul 22, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
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
class Stroke extends CaliSmallElement implements Parcelable {

	/**
	 * A list containing all created strokes sorted by their position in the
	 * canvas.
	 */
	static final SpaceOccupationList SPACE_OCCUPATION_LIST = new SpaceOccupationList();
	private static final int DEFAULT_COLOR = Color.BLACK;
	private static final Paint.Style DEFAULT_STYLE = Paint.Style.STROKE;
	private final Path path;
	private final float[] matrixValues;
	private float startX, startY;
	private Paint.Style style = DEFAULT_STYLE;
	private float strokeWidth = CaliSmall.ABS_STROKE_WIDTH;
	private int color = DEFAULT_COLOR;
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
	 * Computes the boundaries for this stroke.
	 * 
	 * <p>
	 * To be called after <tt>onDown()</tt>, when the stroke is complete.
	 * 
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setBoundaries() {
		super.setBoundaries(path);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#updateSpaceOccupation()
	 */
	@Override
	protected void updateSpaceOccupation() {
		SPACE_OCCUPATION_LIST.update(this);
		previousTopLeftPoint.x = topLeftPoint.x;
		previousTopLeftPoint.y = topLeftPoint.y;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#contains(android.graphics.PointF)
	 */
	@Override
	public boolean contains(PointF point) {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#contains(edu.uci.calismall.
	 * CaliSmallElement)
	 */
	@Override
	public boolean contains(CaliSmallElement element) {
		return false;
	}
}
