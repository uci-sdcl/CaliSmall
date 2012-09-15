/**
 * Scrap.java
 * Created on Aug 11, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
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
import android.graphics.Region;
import android.graphics.Region.Op;
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
 * Once a {@link Stroke} is added to a <tt>Scrap</tt> it is removed from the
 * list of strokes in the canvas, to avoid duplicate <tt>draw()</tt> calls. A
 * <tt>Scrap</tt> is therefore "responsible" for drawing all of the strokes it
 * contains. This also applies to other scraps contained within this scrap.
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
	 * All scraps children of this scrap.
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
	 * The area of this scrap.
	 */
	protected final Region scrapArea;
	/**
	 * Whether this scrap is currently selected.
	 */
	protected boolean selected;
	/**
	 * The direct parent of this scrap.
	 */
	protected Scrap parent;
	private boolean isInScrap, locked;
	private Canvas snapshotCanvas;
	private Bitmap snapshot;
	private float snapOffsetX, snapOffsetY;

	static {
		BORDER_PAINT.setAntiAlias(true);
		BORDER_PAINT.setDither(true);
		BORDER_PAINT.setStrokeJoin(Paint.Join.ROUND);
		BORDER_PAINT.setStrokeCap(Paint.Cap.ROUND);
		BORDER_PAINT.setColor(SELECTED_BORDER_COLOR);
		BORDER_PAINT.setStyle(Style.STROKE);
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
	 * @param scrapArea
	 *            the area of this scrap
	 */
	public Scrap(Stroke outerBorder, List<Scrap> scraps, List<Stroke> strokes,
			Region scrapArea) {
		this.outerBorder = new Stroke(new Path(outerBorder.getPath()),
				outerBorder);
		this.scraps = scraps;
		this.strokes = strokes;
		this.scrapArea = scrapArea;
		matrix = new Matrix();
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
		this.outerBorder = new Stroke(new Path(copy.outerBorder.getPath()),
				copy.outerBorder);
		if (deepCopy) {
			this.scraps = new ArrayList<Scrap>(copy.scraps.size());
			this.strokes = new ArrayList<Stroke>(copy.strokes.size());
			this.scrapArea = new Region(copy.scrapArea);
			copyContent(copy);
		} else {
			this.scraps = new ArrayList<Scrap>(copy.scraps);
			this.strokes = new ArrayList<Stroke>(copy.strokes);
			this.scrapArea = copy.scrapArea;
			this.outerBorder.getPath().close();
			// all strokes are now in this scrap
			this.outerBorder.lock();
			for (Stroke stroke : strokes) {
				stroke.lock();
			}
			for (Scrap scrap : scraps) {
				scrap.parent = this;
				scrap.lock();
			}
		}
		matrix = new Matrix();
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
			this.scraps.add(newCopy);
		}
		for (Stroke stroke : copy.strokes) {
			this.strokes.add(new Stroke(stroke).setInScrap(true)
					.setBoundaries());
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
		return scrapArea.contains(Math.round(point.x), Math.round(point.y));
	}

	/**
	 * Returns the region enclosing this scrap.
	 * 
	 * @return a new copy of the region enclosing this scrap
	 */
	public Region getBoundaries() {
		return new Region(scrapArea);
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
	 * Returns the list of scraps contained within this scrap.
	 * 
	 * @return all scraps that are children of this scrap
	 */
	public List<Scrap> getScraps() {
		List<Scrap> allScraps = new ArrayList<Scrap>(scraps);
		for (Scrap scrap : scraps) {
			allScraps.addAll(scrap.getScraps());
		}
		return allScraps;
	}

	/**
	 * Returns all strokes that are part of this scrap.
	 * 
	 * @return all strokes children of this scrap
	 */
	public List<Stroke> getStrokes() {
		List<Stroke> allStrokes = new ArrayList<Stroke>(strokes);
		for (Scrap scrap : scraps) {
			allStrokes.addAll(scrap.getStrokes());
		}
		return allStrokes;
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
		outerBorder.setPath(collage);
		computeArea();
	}

	/**
	 * Draws the internal region of this scrap.
	 * 
	 * @param canvas
	 *            the canvas onto which the scrap is to be drawn
	 * @param color
	 *            the color with which the scrap shall be filled
	 */
	protected void drawShadedRegion(Canvas canvas, int color) {
		PAINT.setColor(color);
		canvas.drawPath(outerBorder.getPath(), PAINT);
	}

	/**
	 * Returns whether this scrap is inside another scrap.
	 * 
	 * @return <code>true</code> if this scrap has a parent
	 */
	protected boolean isInScrap() {
		return isInScrap;
	}

	/**
	 * Locks this scrap, making calls to {@link #setInScrap(boolean)}
	 * ineffective unless preceeded by a call to {@link #unlock()}.
	 */
	private void lock() {
		locked = true;
	}

	/**
	 * Unlocks this scrap, making calls to {@link #setInScrap(boolean)}
	 * effective again.
	 */
	private void unlock() {
		locked = false;
	}

	/**
	 * Sets whether this scrap is part of another scrap.
	 * 
	 * <p>
	 * If {@link #lock()} has been called on this scrap, calls to this method
	 * won't alter the internal state of this object unless {@link #unlock()} is
	 * called.
	 * 
	 * @param inScrap
	 *            <code>true</code> if this scrap is part of a larger scrap,
	 *            <code>false</code> if it's been removed from a scrap
	 */
	public void setInScrap(boolean inScrap) {
		if (!locked) {
			this.isInScrap = inScrap;
		}
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
	}

	/**
	 * Recursively removes all links to the descendants of this scrap and
	 * returns a list of scraps that must be removed from canvas.
	 * 
	 * @return a list containing all scraps that should be removed from the
	 *         canvas
	 */
	public List<Scrap> erase() {
		unlock();
		if (parent != null) {
			parent.removeChild(this);
			parent = null;
		}
		SPACE_OCCUPATION_LIST.remove(this);
		return Collections.emptyList();
	}

	/**
	 * Removes the argument <tt>scrap</tt> (and all of its children) from this
	 * scrap.
	 * 
	 * @param scrap
	 *            the scrap to be removed
	 */
	public void removeChild(Scrap scrap) {
		if (scrap != null) {
			scraps.remove(scrap);
		}
	}

	/**
	 * Returns the parent of this scrap if it has any, <code>null</code>
	 * otherwise.
	 * 
	 * @return the parent of this scrap or <code>null</code> if this is a
	 *         top-level scrap
	 */
	public Scrap getParent() {
		return parent;
	}

	/**
	 * Moves this scrap to the new position.
	 * 
	 * @param dx
	 *            the X-axis translation
	 * @param dy
	 *            the Y-axis translation
	 * @param scaleFactor
	 *            the scale factor that is currently applied to the canvas
	 */
	public void moveBy(float dx, float dy, float scaleFactor) {
		if (snapshot == null) {
			outerBorder.setBoundaries();
			Rect size = getBounds();
			snapshot = Bitmap.createBitmap(size.width(), size.height(),
					Config.ARGB_8888);
			snapshotCanvas = new Canvas(snapshot);
			snapOffsetX = size.left;
			snapOffsetY = size.top;
			matrix.postTranslate(snapOffsetX, snapOffsetY);
			drawOnBitmap(snapshotCanvas, snapshot, scaleFactor);
		}
		translate(dx, dy);
		// if (!matrix.isIdentity()) {
		// outerBorder.transform(matrix);
		// for (Stroke stroke : strokes) {
		// stroke.transform(matrix);
		// }
		// for (Scrap scrap : scraps) {
		// scrap.moveBy(dx, dy, scaleFactor);
		// }
		// }
		// matrix.reset();
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
		for (Scrap scrap : scraps) {
			scrap.translate(dx, dy);
		}
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
	 * <li>all {@link Stroke}'s within this scrap</li>
	 * </ol>
	 * 
	 * @param parent
	 *            the main {@link View} of the application
	 * @param canvas
	 *            the canvas onto which this scrap must be drawn
	 * @param scaleFactor
	 *            the current scale factor applied to the canvas
	 */
	public void draw(CaliSmall.CaliView parent, Canvas canvas, float scaleFactor) {
		if (snapshot == null) {
			drawShadedRegion(canvas, SCRAP_REGION_COLOR);
			drawBorder(canvas, scaleFactor);
			for (Scrap scrap : scraps) {
				scrap.draw(parent, canvas, scaleFactor);
			}
			for (Stroke stroke : strokes) {
				parent.drawStroke(stroke, canvas);
			}
		} else {
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
		drawShadedRegion(canvas, SCRAP_REGION_COLOR);
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
	protected void computeArea() {
		Path testPath = new Path(outerBorder.getPath());
		testPath.close();
		testPath.setFillType(FillType.WINDING);
		RectF rect = new RectF();
		testPath.computeBounds(rect, true);
		setArea(rect);
		scrapArea.setPath(
				testPath,
				new Region(new Rect(Math.round(rect.left),
						Math.round(rect.top), Math.round(rect.right), Math
								.round(rect.bottom))));
	}

	/**
	 * Applies the transformations set for this scrap to all of its contents.
	 */
	public void applyTransform() {
		snapshot = null;
		if (!isInScrap) {
			// only translate the root scrap
			matrix.postTranslate(-snapOffsetX, -snapOffsetY);
		}
		// update boundaries according to new position
		if (!matrix.isIdentity()) {
			outerBorder.transform(matrix);
			outerBorder.setBoundaries();
			for (Stroke stroke : strokes) {
				stroke.transform(matrix);
				stroke.setBoundaries();
			}
			for (Scrap scrap : scraps) {
				scrap.applyTransform();
			}
			matrix.reset();
		}
		computeArea();
		// outerBorder.setBoundaries();
		// for (Stroke stroke : strokes) {
		// stroke.setBoundaries();
		// }
		// for (Scrap scrap : scraps) {
		// scrap.applyTransform();
		// }
		// computeArea();
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
		List<Scrap> allScraps = getScraps();
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
		private boolean toBeDestroyed;

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
		 * @param canvasScraps
		 *            all scraps currently drawn to the canvas
		 * @param canvasStrokes
		 *            all strokes currently not belonging to any other scrap
		 * @param scaleFactor
		 *            the scale factor currently applied to the canvas
		 */
		public Temp(Stroke selectionBorder, List<Scrap> canvasScraps,
				List<Stroke> canvasStrokes, float scaleFactor) {
			super(selectionBorder, new ArrayList<Scrap>(),
					new ArrayList<Stroke>(), new Region());
			computeArea();
			dashInterval = CaliSmall.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
			findSelected(canvasStrokes, canvasScraps);
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
			for (Stroke stroke : copy.strokes) {
				// back to the canvas
				stroke.setInScrap(false);
			}
			dashInterval = CaliSmall.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
		}

		private void findSelected(List<Stroke> canvasStrokes,
				List<Scrap> canvasScraps) {
			List<CaliSmallElement> candidates = Stroke.SPACE_OCCUPATION_LIST
					.findIntersectionCandidates(this);
			Log.d(CaliSmall.TAG, "found " + candidates.size()
					+ " candidates...");
			for (CaliSmallElement element : candidates) {
				// for (Stroke stroke : canvasStrokes) {
				Stroke stroke = (Stroke) element;
				if (!stroke.isInScrap()) {
					Region boundaries = stroke.getBoundaries();
					if (boundaries.op(scrapArea, Op.INTERSECT)) {
						// stroke intersects selection
						if (!stroke.getBoundaries().op(boundaries,
								Op.DIFFERENCE)) {
							// stroke is contained within selection
							strokes.add(stroke);
							stroke.setInScrap(true);
						}
					}
				}
			}
			for (Scrap scrap : canvasScraps) {
				if (!scrap.isInScrap()) {
					Region boundaries = scrap.getBoundaries();
					if (boundaries.op(scrapArea, Op.INTERSECT)) {
						if (!scrap.getBoundaries()
								.op(boundaries, Op.DIFFERENCE)) {
							scraps.add(scrap);
							scrap.parent = this;
							scrap.setInScrap(true);
						}
					}
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.uci.calismall.Scrap#erase()
		 */
		@Override
		public List<Scrap> erase() {
			return scraps;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.uci.calismall.Scrap#deselect()
		 */
		@Override
		public void deselect() {
			selected = false;
			for (Stroke stroke : strokes) {
				stroke.setInScrap(false);
			}
			for (Scrap scrap : scraps) {
				scrap.setInScrap(false);
			}
			outerBorder.setInScrap(false);
			toBeDestroyed = true;
			SPACE_OCCUPATION_LIST.remove(this);
			SPACE_OCCUPATION_LIST.remove(outerBorder);
		}

		private void highlight(Canvas canvas, float scaleFactor) {
			for (Stroke stroke : strokes) {
				if (stroke.isInScrap()) {
					if (stroke.getStyle() == Style.FILL) {
						HIGHLIGHT_PAINT.setStrokeWidth(stroke.getStrokeWidth()
								* HIGHLIGHTED_STROKE_WIDTH_MUL);
						PointF startPoint = stroke.getStartPoint();
						canvas.drawCircle(startPoint.x, startPoint.y,
								stroke.getStrokeWidth(), HIGHLIGHT_PAINT);
					} else {
						HIGHLIGHT_PAINT.setStrokeWidth(stroke.getStrokeWidth()
								* HIGHLIGHTED_STROKE_WIDTH_MUL);
						canvas.drawPath(stroke.getPath(), HIGHLIGHT_PAINT);
					}
				}
			}
			for (Scrap scrap : getScraps()) {
				scrap.highlightBorder(canvas, scaleFactor);
			}
		}

		public void draw(CaliSmall.CaliView parent, Canvas canvas,
				float scaleFactor) {
			if (!toBeDestroyed) {
				highlight(canvas, scaleFactor);
				drawShadedRegion(canvas, TEMP_SCRAP_REGION_COLOR);
				drawBorder(canvas, scaleFactor);
				for (Scrap scrap : scraps) {
					scrap.draw(parent, canvas, scaleFactor);
				}
				for (Stroke stroke : strokes) {
					parent.drawStroke(stroke, canvas);
				}
			}
		}

		protected void drawBorder(Canvas canvas, float scaleFactor) {
			TEMP_BORDER_PAINT.setPathEffect(new DashPathEffect(new float[] {
					dashInterval, dashInterval }, pathPhase));
			pathPhase += 1 / scaleFactor;
			canvas.drawPath(outerBorder.getPath(), TEMP_BORDER_PAINT);
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

}
