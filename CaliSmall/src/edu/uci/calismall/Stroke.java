/**
 * Stroke.java
 * Created on Jul 22, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.List;

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
	private final List<PointF> points;
	private final float[] matrixValues;
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
		points = new ArrayList<PointF>();
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
		this(new Path(copy.path), copy);
		for (PointF point : copy.points) {
			points.add(new PointF(point.x, point.y));
		}
	}

	/**
	 * Adds a point to this stroke if it's at least <tt>touchTolerance</tt> far
	 * from the last point of this stroke.
	 * 
	 * <p>
	 * If <tt>newPoint</tt> is too close to the previous point it's not added
	 * and this method returns <code>false</code>.
	 * 
	 * <p>
	 * This method assumes that {@link #setStart(PointF)} has been called once
	 * for this <tt>Stroke</tt>.
	 * 
	 * @param newPoint
	 *            the new point to be added to this stroke
	 * @param touchTolerance
	 *            the distance from the last point in this <tt>Stroke</tt> under
	 *            which <tt>newPoint</tt> is to be ignored
	 * @return <code>true</code> if the point has been added to this stroke
	 */
	public boolean addPoint(PointF newPoint, float touchTolerance) {
		boolean added = false;
		final PointF last = points.get(points.size() - 1);
		final float dx = Math.abs(newPoint.x - last.x);
		final float dy = Math.abs(newPoint.y - last.y);
		if (dx >= touchTolerance || dy >= touchTolerance) {
			path.quadTo(last.x, last.y, (newPoint.x + last.x) / 2,
					(newPoint.y + last.y) / 2);
			points.add(newPoint);
			setBoundaries();
			added = true;
		}
		return added;
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
	 * <p>
	 * This method <b>must</b> be called first before any call to
	 * {@link #addPoint(PointF, float)} can be performed.
	 * 
	 * @param startPoint
	 *            the first point of this stroke, adjusted for the current zoom
	 *            level and panning
	 * @return a reference to this object, so calls can be chained
	 */
	public Stroke setStart(PointF startPoint) {
		path.moveTo(startPoint.x, startPoint.y);
		path.lineTo(startPoint.x, startPoint.y);
		points.add(startPoint);
		setBoundaries();
		return this;
	}

	/**
	 * Returns the first point in this stroke.
	 * 
	 * @return the point from which the user started to draw this stroke
	 */
	public PointF getStartPoint() {
		if (points.isEmpty())
			return new PointF();
		return points.get(0);
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
		path.transform(matrix);
		matrix.getValues(matrixValues);
		// variable names are the same used by Skia library
		final float tx = matrixValues[Matrix.MTRANS_X];
		final float ty = matrixValues[Matrix.MTRANS_Y];
		final float mx = matrixValues[Matrix.MSCALE_X];
		final float my = matrixValues[Matrix.MSCALE_Y];
		final float kx = matrixValues[Matrix.MSKEW_X];
		final float ky = matrixValues[Matrix.MSKEW_Y];
		/*
		 * if rotation: skia messes up with the matrix, so sx and sy actually
		 * store cosV, rx and ry store -sinV and sinV
		 */
		for (PointF point : points) {
			final float originalY = point.y;
			point.y = point.x * ky + (point.y * my) + ty;
			point.x = point.x * mx + (originalY * kx) + tx;
		}
		setBoundaries();
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
	 * Returns a list of all points that are part of this stroke.
	 * 
	 * @return a comma separated list of points that make part of this stroke
	 */
	public String listPoints() {
		StringBuilder builder = new StringBuilder();
		String comma = "";
		for (PointF point : points) {
			builder.append(comma);
			builder.append(CaliSmall.pointToString(point));
			comma = ", ";
		}
		return builder.toString();
	}

	/**
	 * Resets this stroke, clearing all points.
	 * 
	 * <p>
	 * After a call to this method, {@link #setStart(PointF)} must be called
	 * again to use this stroke.
	 */
	public void reset() {
		path.reset();
		points.clear();
		setBoundaries();
	}

	/**
	 * Resets this stroke replacing its path with the argument <tt>path</tt> and
	 * replacing the current points with the argument list of points.
	 * 
	 * @param newPath
	 *            the new path that replaces this stroke's
	 * @param newPoints
	 *            a list of points that belong to the <tt>newPath</tt>
	 */
	public void replacePath(Path newPath, List<PointF> newPoints) {
		this.path.reset();
		this.path.addPath(newPath);
		this.path.setFillType(newPath.getFillType());
		points.clear();
		points.addAll(newPoints);
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
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#contains(android.graphics.PointF)
	 */
	@Override
	public boolean contains(PointF point) {
		return boundaries.contains(Math.round(point.x), Math.round(point.y));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#getPointsForInclusionTests()
	 */
	@Override
	public List<PointF> getPointsForInclusionTests() {
		return points;
	}
}
