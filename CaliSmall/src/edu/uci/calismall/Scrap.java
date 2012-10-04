/**
 * Scrap.java
 * Created on Aug 11, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Path.FillType;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.Log;
import android.view.View;

/**
 * A Calico scrap.
 * 
 * <p>
 * Scraps are collections of {@link Stroke}'s that can be moved, scaled, copied,
 * rotated together.
 * 
 * <p>
 * Scraps keep a list of all {@link Stroke}'s that make part of them, and they
 * also store a list of all scraps inside of them. To speed up the drawing
 * process, strokes and children scraps are always kept inside the lists stored
 * in the parent {@link CaliSmall} object.
 * 
 * @author Michele Bonazza
 */
public class Scrap extends CaliSmallElement {

	/**
	 * A list containing all created scraps sorted by their position in the
	 * canvas.
	 */
	static final SpaceOccupationList SPACE_OCCUPATION_LIST = new SpaceOccupationList();
	private static final int SCRAP_REGION_COLOR = 0x44d0e4f0;
	private static final int TEMP_SCRAP_REGION_COLOR = 0x55bcdbbc;
	private static final int HIGHLIGHTED_STROKE_COLOR = 0xffb2b2ff;
	private static final int DESELECTED_BORDER_COLOR = 0xff6a899c;
	private static final int SELECTED_BORDER_COLOR = 0xffabb5fa;
	private static final float HIGHLIGHTED_STROKE_WIDTH_MUL = 2.5f;
	private static final float ABS_SHRINK_BORDER_MARGIN = 20;
	private static final float ABS_SHRINK_BORDER_RADIUS = 10;
	private static final Paint PAINT = new Paint(), BORDER_PAINT = new Paint(),
			TEMP_BORDER_PAINT = new Paint(), SNAPSHOT_PAINT = new Paint();
	/**
	 * The transformation matrix in use when modifying a scrap through bubble
	 * menu.
	 */
	protected final Matrix matrix;

	/**
	 * The transformation matrix in use when modifying a scrap through bubble
	 * menu that is applied to the scrap's outer border.
	 */
	protected final Matrix borderMatrix;

	/**
	 * All scraps children (but not grand-children) of this scrap.
	 */
	protected final List<Scrap> scraps;
	/**
	 * All strokes belonging to this scrap.
	 */
	protected final List<Stroke> strokes;
	/**
	 * The enclosing border of this scrap.
	 */
	protected final Stroke outerBorder;
	/**
	 * Whether this scrap is currently selected.
	 */
	protected boolean selected;
	/**
	 * The bitmap to which strokes are temporarily painted when editing the
	 * scrap.
	 */
	protected Bitmap snapshot;
	/**
	 * The color with which to fill the area of this scrap.
	 */
	protected int regionColor = SCRAP_REGION_COLOR;
	private boolean topLevelForEdit, contentChanged = true;
	private float snapOffsetX, snapOffsetY;

	static {
		BORDER_PAINT.setAntiAlias(true);
		BORDER_PAINT.setDither(true);
		BORDER_PAINT.setStrokeJoin(Paint.Join.ROUND);
		BORDER_PAINT.setStrokeCap(Paint.Cap.ROUND);
		BORDER_PAINT.setColor(SELECTED_BORDER_COLOR);
		BORDER_PAINT.setStyle(Style.STROKE);
		SNAPSHOT_PAINT.setAntiAlias(true);
		SNAPSHOT_PAINT.setDither(true);
		SNAPSHOT_PAINT.setStrokeJoin(Paint.Join.ROUND);
		SNAPSHOT_PAINT.setStrokeCap(Paint.Cap.ROUND);
		SNAPSHOT_PAINT.setColor(SELECTED_BORDER_COLOR);
		SNAPSHOT_PAINT.setStyle(Style.STROKE);
		TEMP_BORDER_PAINT.setColor(Color.BLACK);
		TEMP_BORDER_PAINT.setStyle(Style.STROKE);
		PAINT.setAntiAlias(true);
		PAINT.setDither(true);
		PAINT.setStrokeJoin(Paint.Join.ROUND);
		PAINT.setStrokeCap(Paint.Cap.ROUND);
		PAINT.setColor(TEMP_SCRAP_REGION_COLOR);
		PAINT.setStyle(Style.FILL);
	}

	/**
	 * Creates a new scrap that is enclosed within the argument <tt>border</tt>.
	 * 
	 * @param outerBorder
	 *            the border drawn by the user to identify this scrap
	 * @param scraps
	 *            all scraps that make part of this scrap
	 * @param strokes
	 *            all strokes belonging to this scrap
	 */
	public Scrap(Stroke outerBorder, List<Scrap> scraps, List<Stroke> strokes) {
		this.outerBorder = new Stroke(outerBorder);
		this.scraps = scraps;
		this.strokes = strokes;
		outerBorder.getPath().close();
		outerBorder.getPath().setFillType(FillType.WINDING);
		setBoundaries();
		matrix = new Matrix();
		borderMatrix = new Matrix();
	}

	/**
	 * Creates a new scrap copying content from the argument scrap.
	 * 
	 * @param copy
	 *            the scrap to be copied
	 * @param deepCopy
	 *            whether content should be copied using new objects, if
	 *            <code>false</code> the same objects inside the copy are used
	 *            by this scrap
	 */
	public Scrap(Scrap copy, boolean deepCopy) {
		this.outerBorder = new Stroke(copy.outerBorder);
		if (deepCopy) {
			this.scraps = new ArrayList<Scrap>(copy.scraps.size());
			this.strokes = new ArrayList<Stroke>(copy.strokes.size());
			copyContent(copy);
		} else {
			this.scraps = new ArrayList<Scrap>(copy.scraps);
			this.strokes = new ArrayList<Stroke>(copy.strokes);
			this.outerBorder.getPath().close();
			outerBorder.getPath().setFillType(FillType.WINDING);
		}
		// all strokes are now in this scrap
		for (Stroke stroke : strokes) {
			stroke.parent = this;
			stroke.previousParent = this;
		}
		for (Scrap scrap : scraps) {
			scrap.parent = this;
			scrap.previousParent = this;
		}
		setBoundaries();
		matrix = new Matrix();
		borderMatrix = new Matrix();
	}

	/**
	 * Adds the argument <tt>stroke</tt> to this scrap.
	 * 
	 * @param stroke
	 *            the stroke to be added to this scrap
	 */
	public void add(Stroke stroke) {
		strokes.add(stroke);
		stroke.parent = this;
		// refresh the snapshot the next time!
		contentChanged = true;
	}

	/**
	 * Adds all of the strokes in the argument list to this scrap.
	 * 
	 * @param strokes
	 *            a list of strokes to be added to this scrap
	 */
	public void addAll(List<Stroke> strokes) {
		this.strokes.addAll(strokes);
		for (Stroke stroke : strokes)
			stroke.parent = this;
		// refresh the snapshot the next time!
		contentChanged = true;
	}

	/**
	 * Adds the argument <tt>scrap</tt> as a child of this scrap.
	 * 
	 * @param scrap
	 *            the child scrap to be added to this scrap
	 */
	public void add(Scrap scrap) {
		scraps.add(scrap);
		scrap.parent = this;
		// refresh the snapshot the next time!
		contentChanged = true;
	}

	/**
	 * Removes the argument <tt>stroke</tt> from this scrap.
	 * 
	 * @param stroke
	 *            the stroke to be removed from this scrap
	 */
	public void remove(Stroke stroke) {
		strokes.remove(stroke);
		stroke.parent = stroke.previousParent;
		// refresh the snapshot the next time!
		contentChanged = true;
	}

	/**
	 * Removes all the elements in the argument list from this scrap.
	 * 
	 * @param <T>
	 *            the type of elements to be removed from this scrap
	 * 
	 * @param elements
	 *            the strokes to be removed from this scrap
	 * @param type
	 *            the type of elements to be removed
	 */
	@SuppressWarnings("unchecked")
	public <T extends CaliSmallElement> void removeAll(List<T> elements,
			Class<T> type) {
		// TODO refactor so that the unchecked cast is not needed
		List<T> content;
		if (type.equals(Stroke.class)) {
			content = (List<T>) strokes;
		} else {
			content = (List<T>) scraps;
		}
		content.removeAll(elements);
		for (CaliSmallElement element : elements)
			element.parent = element.previousParent;
		// refresh the snapshot the next time!
		contentChanged = true;
	}

	/**
	 * Removes the argument <tt>scrap</tt> from the list of children of this
	 * scrap.
	 * 
	 * @param scrap
	 *            the child scrap to be removed from the list of children of
	 *            this scrap
	 */
	public void remove(Scrap scrap) {
		scraps.remove(scrap);
		scrap.parent = scrap.previousParent;
		// refresh the snapshot the next time!
		contentChanged = true;
	}

	/**
	 * Creates fresh copies of all scraps and strokes within <tt>copy</tt> to
	 * this scrap.
	 * 
	 * @param copy
	 *            the scrap to be copied
	 */
	public void copyContent(Scrap copy) {
		for (Scrap scrap : copy.scraps) {
			Scrap newCopy = new Scrap(scrap, true);
			scraps.add(newCopy);
		}
		for (Stroke stroke : copy.strokes) {
			strokes.add(new Stroke(stroke).setBoundaries());
		}
	}

	/**
	 * Tests whether the argument point is within the area of this scrap.
	 * 
	 * @param point
	 *            the point to be tested
	 * @return <code>true</code> if the point is within this scrap's area
	 */
	public boolean contains(PointF point) {
		return boundaries.contains(Math.round(point.x), Math.round(point.y));
	}

	/**
	 * Returns the smallest <tt>Rect</tt> enclosing the whole scrap.
	 * 
	 * @return a rect enclosing this scrap
	 */
	public Rect getBounds() {
		return outerBorder.getBoundaries().getBounds();
	}

	/**
	 * Returns the outer border containing this scrap.
	 * 
	 * @return the outer border
	 */
	public Path getBorder() {
		return outerBorder.getPath();
	}

	/**
	 * Returns the list of all scraps contained within this scrap, including all
	 * descendent scraps.
	 * 
	 * @return all scraps that are descendents of this scrap
	 */
	public List<Scrap> getAllScraps() {
		List<Scrap> allScraps = new ArrayList<Scrap>(scraps);
		for (Scrap scrap : scraps) {
			allScraps.addAll(scrap.getAllScraps());
		}
		return allScraps;
	}

	/**
	 * Returns all strokes that are part of this scrap, including strokes that
	 * are part of scraps that are children of this scrap.
	 * 
	 * @return all strokes children of this scrap
	 */
	public List<Stroke> getAllStrokes() {
		List<Stroke> allStrokes = new ArrayList<Stroke>(strokes);
		for (Scrap scrap : getAllScraps()) {
			allStrokes.addAll(scrap.strokes);
		}
		return allStrokes;
	}

	/**
	 * Returns a list of all strokes whose parent is this scrap.
	 * 
	 * <p>
	 * The list is not defensively copied, so editing the return object will
	 * alter this scrap's internal status.
	 * 
	 * @return this scrap's children strokes
	 */
	public List<Stroke> getStrokes() {
		return strokes;
	}

	/**
	 * Returns a list of all scraps whose parent is this scrap.
	 * 
	 * <p>
	 * The list is not defensively copied, so editing the return object will
	 * alter this scrap's internal status.
	 * 
	 * @return this scrap's children scraps
	 */
	public List<Scrap> getScraps() {
		return scraps;
	}

	/**
	 * Replaces the outer border with the smallest {@link RoundRectShape} that
	 * contains all strokes within this scrap.
	 * 
	 * @param scaleFactor
	 *            the current scale factor applied to the canvas
	 */
	public void shrinkBorder(float scaleFactor) {
		Path collage = new Path();
		if (strokes.isEmpty() && scraps.isEmpty()) {
			collage.addPath(outerBorder.getPath());
		} else {
			for (Stroke stroke : strokes) {
				collage.addPath(stroke.getPath());
			}
			for (Scrap scrap : scraps) {
				collage.addPath(scrap.getBorder());
			}
		}
		RectF area = new RectF();
		collage.computeBounds(area, true);
		collage.reset();
		final float radius = ABS_SHRINK_BORDER_RADIUS / scaleFactor;
		final float margin = ABS_SHRINK_BORDER_MARGIN / scaleFactor;
		area.set(area.left - margin, area.top - margin, area.right + margin,
				area.bottom + margin);
		collage.addRoundRect(area, radius, radius, Direction.CW);
		outerBorder.replacePath(collage, Arrays.asList(new PointF(area.left,
				area.top), new PointF(area.right, area.bottom)));
		setBoundaries();
		contentChanged = true;
	}

	/**
	 * Draws the internal region of this scrap.
	 * 
	 * @param canvas
	 *            the canvas onto which the scrap is to be drawn
	 */
	protected void drawShadedRegion(Canvas canvas) {
		PAINT.setColor(regionColor);
		canvas.drawPath(outerBorder.getPath(), PAINT);
	}

	/**
	 * Draws the border of this scrap.
	 * 
	 * @param canvas
	 *            the canvas onto which the scrap is to be drawn
	 * @param scaleFactor
	 *            the current scale factor applied to the canvas
	 */
	protected void drawBorder(Canvas canvas, float scaleFactor) {
		if (selected) {
			highlightBorder(canvas, scaleFactor);
		}
		BORDER_PAINT.setColor(DESELECTED_BORDER_COLOR);
		BORDER_PAINT
				.setStrokeWidth((CaliSmall.ABS_STROKE_WIDTH / scaleFactor) / 2);
		canvas.drawPath(outerBorder.getPath(), BORDER_PAINT);
	}

	/**
	 * Highlights this scrap's outer border.
	 * 
	 * @param canvas
	 *            the canvas on which the highlighted border is to be drawn
	 * @param scaleFactor
	 *            the current scale factor applied to the canvas
	 */
	protected void highlightBorder(Canvas canvas, float scaleFactor) {
		BORDER_PAINT.setColor(SELECTED_BORDER_COLOR);
		BORDER_PAINT
				.setStrokeWidth(2 * (CaliSmall.ABS_STROKE_WIDTH / scaleFactor));
		canvas.drawPath(outerBorder.getPath(), BORDER_PAINT);
	}

	/**
	 * Returns whether this scrap is empty (i.e. contains no strokes or other
	 * scraps).
	 * 
	 * @return <code>true</code> if this scrap does not contain any stroke or
	 *         other scrap
	 */
	public boolean isEmpty() {
		return scraps.isEmpty() && strokes.isEmpty();
	}

	/**
	 * Returns whether this scrap is currently selected.
	 * 
	 * @return <code>true</code> if this scrap is now selected (and therefore
	 *         highlighted)
	 */
	public boolean isSelected() {
		return selected;
	}

	/**
	 * Resizes this scrap by the argument values.
	 * 
	 * @param dx
	 *            the scale value along the X-axis
	 * @param dy
	 *            the scale value along the Y-axis
	 * @param centerOffset
	 *            the distance between the center of the scrap and the topmost
	 *            left corner of the scraps' bounds
	 */
	public void scale(float dx, float dy, PointF centerOffset) {
		final float scaleX = 1 + dx;
		final float scaleY = 1 + dy;
		matrix.preTranslate(-centerOffset.x, -centerOffset.y);
		matrix.preScale(scaleX, scaleY);
		matrix.preTranslate(centerOffset.x, centerOffset.y);
		borderMatrix.preTranslate(-centerOffset.x, -centerOffset.y);
		borderMatrix.preScale(scaleX, scaleY);
		borderMatrix.preTranslate(centerOffset.x, centerOffset.y);
		outerBorder.transform(borderMatrix);
		setBoundaries();
		borderMatrix.reset();
	}

	/**
	 * Sets whether this scrap is the selected one.
	 */
	public void select() {
		this.selected = true;
	}

	/**
	 * Deselects this scrap, returning all strokes that now belong to the canvas
	 * once again.
	 */
	public void deselect() {
		this.selected = false;
		// free up some space
		snapshot = null;
	}

	/**
	 * Removes the link to this scrap from the parent scrap and marks all
	 * strokes and children scraps as <tt>toBeDeleted</tt>, so that the drawing
	 * thread can remove them from the lists.
	 */
	public void erase() {
		if (parent != null) {
			Scrap parentScrap = (Scrap) parent;
			parentScrap.remove(this);
			parent = null;
		}
		toBeDeleted = true;
		mustBeDrawnVectorially(false);
		outerBorder.toBeDeleted = true;
		for (Stroke stroke : getAllStrokes()) {
			stroke.toBeDeleted = true;
			if (stroke.parent != null) {
				Scrap parentScrap = (Scrap) stroke.parent;
				parentScrap.remove(stroke);
			}
		}
		for (Scrap scrap : getAllScraps()) {
			scrap.toBeDeleted = true;
			if (scrap.parent != null) {
				Scrap parentScrap = (Scrap) scrap.parent;
				parentScrap.remove(scrap);
			}
		}
	}

	private void changeDrawingStatus(boolean mustBeDrawn) {
		mustBeDrawnVectorially(mustBeDrawn);
		outerBorder.mustBeDrawnVectorially(mustBeDrawn);
		for (Stroke stroke : strokes) {
			stroke.mustBeDrawnVectorially(mustBeDrawn);
		}
		for (Scrap scrap : scraps) {
			scrap.changeDrawingStatus(mustBeDrawn);
		}
	}

	/**
	 * Translates this scrap by the argument values.
	 * 
	 * @param dx
	 *            the X-offset
	 * @param dy
	 *            the Y-offset
	 */
	public void translate(float dx, float dy) {
		matrix.postTranslate(dx, dy);
		borderMatrix.postTranslate(dx, dy);
		outerBorder.transform(borderMatrix);
		setBoundaries();
		borderMatrix.reset();
	}

	/**
	 * Draws this scrap to the argument <tt>canvas</tt>.
	 * 
	 * <p>
	 * Drawing a scrap means drawing:
	 * <ol>
	 * <li>the border delimiting the scrap</li>
	 * <li>the shaded region that highlights the content of the scrap</li>
	 * <li>all {@link Scrap}'s within this scrap</li>
	 * </ol>.
	 * 
	 * <p>
	 * Strokes are <i>always</i> taken by the list in the parent
	 * {@link CaliSmall} object, <b>except</b> for the outer borders, which are
	 * always kept inside scraps.
	 * 
	 * @param parent
	 *            the main {@link View} of the application
	 * @param canvas
	 *            the canvas onto which this scrap must be drawn
	 * @param scaleFactor
	 *            the current scale factor applied to the canvas
	 */
	public void draw(CaliSmall.CaliView parent, Canvas canvas, float scaleFactor) {
		if (hasToBeDrawnVectorially() || (topLevelForEdit && snapshot == null)) {
			drawShadedRegion(canvas);
			drawBorder(canvas, scaleFactor);
		} else if (topLevelForEdit) {
			canvas.drawBitmap(snapshot, matrix, null);
		}
	}

	/**
	 * Draws this scrap onto the argument <tt>bitmap</tt>.
	 * 
	 * Used to take snapshots of a scrap when editing it.
	 * 
	 * @param canvas
	 *            the canvas onto which this scrap has to be drawn
	 * @param bitmap
	 *            the bitmap onto which this scrap has to be drawn
	 * @param scaleFactor
	 *            the scale factor that is currently applied to the canvas
	 */
	public void drawOnBitmap(Canvas canvas, Bitmap bitmap, float scaleFactor) {
		drawShadedRegion(canvas);
		drawBorder(canvas, scaleFactor);
		for (Stroke stroke : strokes) {
			SNAPSHOT_PAINT.setColor(stroke.getColor());
			SNAPSHOT_PAINT.setStrokeWidth(stroke.getStrokeWidth());
			SNAPSHOT_PAINT.setStyle(stroke.getStyle());
			canvas.drawPath(stroke.getPath(), SNAPSHOT_PAINT);
		}
		for (Scrap scrap : scraps) {
			scrap.drawOnBitmap(canvas, bitmap, scaleFactor);
		}
	}

	/**
	 * Updates the scrap area according to the outer border.
	 */
	public void setBoundaries() {
		outerBorder.setBoundaries();
		super.setBoundaries(outerBorder.getPath());
	}

	/**
	 * Updates the parent of this scrap to the argument <tt>parent</tt>.
	 * 
	 * @param parent
	 *            this scrap's new parent
	 */
	public void setParent(CaliSmallElement parent) {
		previousParent = this.parent;
		this.parent = parent;
	}

	/**
	 * Prepares this scrap for editing.
	 * 
	 * <p>
	 * Must be called before starting an edit operation on the scrap.
	 * 
	 * @param scaleFactor
	 *            the current scale factor
	 */
	public void startEditing(float scaleFactor) {
		topLevelForEdit = true;
		setBoundaries();
		Rect size = getBounds();
		snapOffsetX = size.left;
		snapOffsetY = size.top;
		if (contentChanged || snapshot == null) {
			// create a new bitmap as large as the scrap and move it
			Bitmap snapshot = Bitmap.createBitmap(size.width(), size.height(),
					Config.ARGB_8888);
			Canvas snapshotCanvas = new Canvas(snapshot);
			snapshotCanvas.translate(-snapOffsetX, -snapOffsetY);
			drawOnBitmap(snapshotCanvas, snapshot, scaleFactor);
			this.snapshot = snapshot;
			contentChanged = false;
		}
		matrix.postTranslate(snapOffsetX, snapOffsetY);
		// use the bitmap snapshot until applyTransform() is called
		changeDrawingStatus(false);
	}

	/**
	 * Applies the transformations set for this scrap to all of its contents.
	 */
	public void applyTransform() {
		matrix.postTranslate(-snapOffsetX, -snapOffsetY);
		topLevelForEdit = false;
		outerBorder.mustBeDrawnVectorially(true);
		for (Stroke stroke : getAllStrokes()) {
			// Log.d(CaliSmall.TAG, "applying transformation to " + stroke);
			stroke.transform(matrix);
			stroke.mustBeDrawnVectorially(true);
		}
		mustBeDrawnVectorially(true);
		for (Scrap scrap : getAllScraps()) {
			scrap.outerBorder.transform(matrix);
			scrap.mustBeDrawnVectorially(true);
			scrap.setBoundaries();
		}
		matrix.reset();
		setBoundaries();
	}

	public void realignAfterScale(PointF originalCenter) {
		Rect newBounds = getBounds();
		Log.d(CaliSmall.TAG, "rect " + newBounds.toString() + ", center: ("
				+ newBounds.centerX() + "," + newBounds.centerY() + ")");
		Log.d(CaliSmall.TAG, "original center: (" + originalCenter.x + ", "
				+ originalCenter.y + ")");
		Log.d(CaliSmall.TAG,
				String.format("translating: (%.4f, %.4f)", originalCenter.x
						- newBounds.centerX(),
						originalCenter.y - newBounds.centerY()));
		matrix.postTranslate(originalCenter.x - newBounds.centerX(),
				originalCenter.y - newBounds.centerY());
		outerBorder.transform(matrix);
		for (Stroke stroke : getAllStrokes()) {
			// Log.d(CaliSmall.TAG, "applying transformation to " + stroke);
			stroke.transform(matrix);
		}
		mustBeDrawnVectorially(true);
		for (Scrap scrap : getAllScraps()) {
			scrap.outerBorder.transform(matrix);
			scrap.setBoundaries();
		}
		matrix.reset();
		setBoundaries();
		contentChanged = true;
	}

	/**
	 * Returns the smallest scrap child of this scrap that contains the argument
	 * <tt>touchPoint</tt>, or this scrap itself.
	 * 
	 * @param touchPoint
	 *            the test point
	 * @return the smallest scrap child of this scrap containing the argument
	 *         <tt>touchPoint</tt>, or this scrap
	 */
	public Scrap getSmallestTouched(PointF touchPoint) {
		List<Scrap> allScraps = getAllScraps();
		for (int i = allScraps.size() - 1; i > -1; i--) {
			Scrap test = allScraps.get(i);
			if (test.contains(touchPoint)) {
				return test;
			}
		}
		// this method is only called when touchPoint is within this scrap
		return this;
	}

	/**
	 * Returns the smallest scrap child of this scrap that contains the argument
	 * <tt>element</tt>, or this scrap itself.
	 * 
	 * @param element
	 *            the element that should be within this scrap
	 * @return the smallest scrap child of this scrap containing the argument
	 *         <tt>element</tt>, or this scrap
	 */
	public Scrap getSmallestTouched(CaliSmallElement element) {
		List<Scrap> allScraps = getAllScraps();
		Collections.sort(allScraps);
		for (Scrap test : allScraps) {
			if (test.contains(element)) {
				return test;
			}
		}
		// this method is only called when touchPoint is within this scrap
		return this;
	}

	/**
	 * A special scrap that is created whenever users close a path in the
	 * landing zone.
	 * 
	 * <p>
	 * A <tt>Temp</tt> gets promoted to a standard <tt>Scrap</tt> when the user
	 * presses the <tt>scrap</tt> button on the {@link BubbleMenu}.
	 * 
	 * @author Michele Bonazza
	 */
	public static class Temp extends Scrap {

		private static final Paint HIGHLIGHT_PAINT = new Paint();
		private float dashInterval, pathPhase;

		static {
			HIGHLIGHT_PAINT.setAntiAlias(true);
			HIGHLIGHT_PAINT.setDither(true);
			HIGHLIGHT_PAINT.setStrokeJoin(Paint.Join.ROUND);
			HIGHLIGHT_PAINT.setStrokeCap(Paint.Cap.ROUND);
			HIGHLIGHT_PAINT.setColor(HIGHLIGHTED_STROKE_COLOR);
			HIGHLIGHT_PAINT.setStyle(Style.STROKE);
		}

		/**
		 * Creates a new temporary selection scrap.
		 * 
		 * @param selectionBorder
		 *            the border enclosing the temporary scrap
		 * @param scaleFactor
		 *            the scale factor currently applied to the canvas
		 */
		public Temp(Stroke selectionBorder, float scaleFactor) {
			super(selectionBorder, new ArrayList<Scrap>(),
					new ArrayList<Stroke>());
			regionColor = TEMP_SCRAP_REGION_COLOR;
			dashInterval = CaliSmall.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
			findSelected();
		}

		/**
		 * Copy constructor for temporary selections.
		 * 
		 * @param copy
		 *            the selection to be copied
		 * @param scaleFactor
		 *            the scale factor currently applied to the canvas
		 */
		public Temp(Scrap copy, float scaleFactor) {
			super(copy, true);
			regionColor = TEMP_SCRAP_REGION_COLOR;
			dashInterval = CaliSmall.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
		}

		private void findSelected() {
			// Log.d(CaliSmall.TAG, "*** new temp scrap, border - " +
			// outerBorder
			// + ", points: " + outerBorder.listPoints());
			List<CaliSmallElement> candidates = SPACE_OCCUPATION_LIST
					.findIntersectionCandidates(this);
			final float size = getRectSize();
			for (CaliSmallElement candidate : candidates) {
				Scrap scrap = (Scrap) candidate;
				if (scrap.parent == null || scrap.parent.getRectSize() > size) {
					// only include scraps that have no parent or whose parent
					// is larger than this scrap
					if (outerBorder.contains(scrap.outerBorder)) {
						scraps.add(scrap);
						scrap.previousParent = scrap.parent;
						scrap.parent = this;
					}
				}
			}
			candidates = Stroke.SPACE_OCCUPATION_LIST
					.findIntersectionCandidates(this);
			// Log.d(CaliSmall.TAG, "candidates found: " + candidates.size());
			for (CaliSmallElement element : candidates) {
				if (outerBorder.contains(element)) {
					Stroke stroke = (Stroke) element;
					if (stroke.parent == null
							|| stroke.parent.getRectSize() > size) {
						strokes.add(stroke);
						stroke.previousParent = stroke.parent;
						stroke.parent = this;
					}
				}
			}
			// Log.d(CaliSmall.TAG, String.format("*** strokes: %d scraps: %d",
			// strokes.size(), scraps.size()));
			// StringBuilder builder = new StringBuilder("content:\n");
			// String newline = "";
			// for (Stroke stroke : strokes) {
			// builder.append(newline);
			// builder.append(stroke);
			// newline = "\n";
			// }
			// Log.d(CaliSmall.TAG, builder.toString());
		}

		private void highlight(Canvas canvas, float scaleFactor) {
			for (Stroke stroke : strokes) {
				if (stroke.parent == this) {
					if (stroke.getStyle() == Style.FILL) {
						HIGHLIGHT_PAINT.setStrokeWidth(stroke.getStrokeWidth()
								* HIGHLIGHTED_STROKE_WIDTH_MUL / scaleFactor);
						PointF startPoint = stroke.getStartPoint();
						HIGHLIGHT_PAINT.setStyle(Style.FILL);
						canvas.drawCircle(startPoint.x, startPoint.y,
								stroke.getStrokeWidth(), HIGHLIGHT_PAINT);
						HIGHLIGHT_PAINT.setStyle(Style.STROKE);
					} else {
						HIGHLIGHT_PAINT.setStrokeWidth(stroke.getStrokeWidth()
								* HIGHLIGHTED_STROKE_WIDTH_MUL);
						canvas.drawPath(stroke.getPath(), HIGHLIGHT_PAINT);
					}
				}
			}
			for (Scrap scrap : getAllScraps()) {
				scrap.highlightBorder(canvas, scaleFactor);
			}
		}

		public void draw(CaliSmall.CaliView parent, Canvas canvas,
				float scaleFactor) {
			if (selected) {
				if (hasToBeDrawnVectorially() || snapshot == null) {
					highlight(canvas, scaleFactor);
					drawShadedRegion(canvas);
					drawBorder(canvas, scaleFactor);
				} else {
					canvas.drawBitmap(snapshot, matrix, null);
				}
			}
		}

		protected void drawBorder(Canvas canvas, float scaleFactor) {
			TEMP_BORDER_PAINT.setPathEffect(new DashPathEffect(new float[] {
					dashInterval, dashInterval }, pathPhase));
			pathPhase += 1 / scaleFactor;
			canvas.drawPath(outerBorder.getPath(), TEMP_BORDER_PAINT);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.uci.calismall.Scrap#deselect()
		 */
		@Override
		public void deselect() {
			super.deselect();
			// remove link to child scraps, rollback
			for (Scrap scrap : scraps) {
				scrap.parent = scrap.previousParent;
			}
			for (Stroke stroke : strokes) {
				stroke.parent = stroke.previousParent;
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#updateSpaceOccupation()
	 */
	@Override
	public void updateSpaceOccupation() {
		SPACE_OCCUPATION_LIST.update(this);
		previousTopLeftPoint.x = topLeftPoint.x;
		previousTopLeftPoint.y = topLeftPoint.y;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#getPointsForInclusionTests()
	 */
	@Override
	public List<PointF> getPointsForInclusionTests() {
		return outerBorder.getPointsForInclusionTests();
	}

}
