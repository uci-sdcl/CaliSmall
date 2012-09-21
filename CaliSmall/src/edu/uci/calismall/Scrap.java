/**
 * Scrap.java
 * Created on Aug 11, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
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
import android.util.FloatMath;
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
	 * The direct parent of this scrap.
	 */
	protected Scrap parent;
	private Canvas snapshotCanvas;
	private Bitmap snapshot;
	private boolean topLevelForEdit;
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
		this.boundaries.set(scrapArea);
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
		// this.outerBorder = new Stroke(new Path(copy.outerBorder.getPath()),
		// copy.outerBorder);
		this.outerBorder = copy.outerBorder;
		if (deepCopy) {
			this.scraps = new ArrayList<Scrap>(copy.scraps.size());
			this.strokes = new ArrayList<Stroke>(copy.strokes.size());
			// this.boundaries.set(copy.getBoundaries());
			copyContent(copy);
		} else {
			this.scraps = new ArrayList<Scrap>(copy.scraps);
			this.strokes = new ArrayList<Stroke>(copy.strokes);
			// this.boundaries.set(copy.boundaries);
			this.outerBorder.getPath().close();
			// all strokes are now in this scrap
			for (Scrap scrap : scraps) {
				scrap.parent = this;
			}
		}
		setBoundaries(copy.getBorder());
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
			this.strokes.add(new Stroke(stroke).setBoundaries());
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
		setBoundaries();
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
	 * Removes the link to this scrap from the parent scrap and marks all
	 * strokes and children scraps as <tt>toBeDeleted</tt>, so that the drawing
	 * thread can remove them from the lists.
	 */
	public void erase() {
		if (parent != null) {
			parent.removeChild(this);
			parent = null;
		}
		toBeDeleted = true;
		mustBeDrawn(false);
		outerBorder.toBeDeleted = true;
		for (Stroke stroke : strokes) {
			stroke.toBeDeleted = true;
		}
		for (Scrap scrap : scraps) {
			scrap.erase();
		}
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
			changeDrawingStatus(false);
		}
		translate(dx, dy);
	}

	private void changeDrawingStatus(boolean mustBeDrawn) {
		mustBeDrawn(mustBeDrawn);
		outerBorder.mustBeDrawn(mustBeDrawn);
		for (Stroke stroke : strokes) {
			stroke.mustBeDrawn(mustBeDrawn);
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
		if (snapshot == null) {
			if (hasToBeDrawn()) {
				drawShadedRegion(canvas, SCRAP_REGION_COLOR);
				drawBorder(canvas, scaleFactor);
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
	public void setBoundaries() {
		// Path testPath = new Path(outerBorder.getPath());
		outerBorder.getPath().close();
		outerBorder.getPath().setFillType(FillType.WINDING);
		outerBorder.setBoundaries();
		super.setBoundaries(outerBorder.getPath());
	}

	/**
	 * Prepares this scrap for editing.
	 * 
	 * <p>
	 * Must be called before starting an edit operation on the scrap.
	 */
	public void startEditing() {
		topLevelForEdit = true;
	}

	/**
	 * Applies the transformations set for this scrap to all of its contents.
	 */
	public void applyTransform() {
		snapshot = null;
		if (topLevelForEdit) {
			// only translate the root scrap
			matrix.postTranslate(-snapOffsetX, -snapOffsetY);
			topLevelForEdit = false;
		}
		// update boundaries according to new position
		outerBorder.transform(matrix);
		outerBorder.setBoundaries();
		outerBorder.mustBeDrawn(true);
		for (Stroke stroke : strokes) {
			stroke.transform(matrix);
			stroke.setBoundaries();
			stroke.mustBeDrawn(true);
		}
		mustBeDrawn(true);
		for (Scrap scrap : scraps) {
			scrap.applyTransform();
		}
		matrix.reset();
		setBoundaries();
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
	 * Returns the smallest scrap child of this scrap that contains the argument
	 * <tt>element</tt>, or this scrap itself.
	 * 
	 * @param element
	 *            the element that should be within this scrap
	 * @return the smallest scrap child of this scrap containing the argument
	 *         <tt>element</tt>, or this scrap
	 */
	public Scrap getSmallestTouched(CaliSmallElement element) {
		List<Scrap> allScraps = getScraps();
		for (int i = allScraps.size() - 1; i > -1; i--) {
			Scrap test = allScraps.get(i);
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
		 * @param scaleFactor
		 *            the scale factor currently applied to the canvas
		 */
		public Temp(Stroke selectionBorder, float scaleFactor) {
			super(selectionBorder, new ArrayList<Scrap>(),
					new ArrayList<Stroke>(), new Region());
			setBoundaries();
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
			dashInterval = CaliSmall.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
		}

		private void findSelected() {
			List<CaliSmallElement> candidates = Stroke.SPACE_OCCUPATION_LIST
					.findIntersectionCandidates(this);
			Log.d(CaliSmall.TAG, "candidates found: " + candidates.size());
			for (CaliSmallElement element : candidates) {
				Stroke stroke = (Stroke) element;
				Region strokeBoundaries = stroke.getBoundaries();
				if (strokeBoundaries.isEmpty()) {
					// porkaround for empty regions... thx android
					strokeBoundaries.set(
							(int) FloatMath.floor(stroke.topLeftPoint.x),
							(int) FloatMath.floor(stroke.topLeftPoint.y),
							(int) FloatMath.ceil(stroke.topLeftPoint.x
									+ stroke.width),
							(int) FloatMath.ceil(stroke.topLeftPoint.y
									+ stroke.height));
				}
				if (!strokeBoundaries.op(boundaries, Op.DIFFERENCE)) {
					// stroke is contained within selection
					strokes.add(stroke);
				}
			}
			candidates = SPACE_OCCUPATION_LIST.findIntersectionCandidates(this);
			final float size = width + height;
			for (CaliSmallElement candidate : candidates) {
				Scrap scrap = (Scrap) candidate;
				if (scrap.parent == null
						|| scrap.parent.width + scrap.parent.height > size) {
					// only include scraps that have no parent or whose parent
					// is larger than this scrap
					if (!scrap.getBoundaries().op(boundaries, Op.DIFFERENCE)) {
						scraps.add(scrap);
						scrap.parent = this;
					}
				}
			}
			Log.d(CaliSmall.TAG, String.format(
					"*** new temp scrap - strokes: %d scraps: %d",
					strokes.size(), scraps.size()));
			Log.d(CaliSmall.TAG, "*** border - " + outerBorder);
			StringBuilder builder = new StringBuilder("content:\n");
			String newline = "";
			for (Stroke stroke : strokes) {
				builder.append(newline);
				builder.append(stroke);
				newline = "\n";
			}
			Log.d(CaliSmall.TAG, builder.toString());
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.uci.calismall.Scrap#deselect()
		 */
		@Override
		public void deselect() {
			selected = false;
			toBeDestroyed = true;
			mustBeDrawn(false);
		}

		private void highlight(Canvas canvas, float scaleFactor) {
			for (Stroke stroke : strokes) {
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
			for (Scrap scrap : getScraps()) {
				scrap.highlightBorder(canvas, scaleFactor);
			}
		}

		public void draw(CaliSmall.CaliView parent, Canvas canvas,
				float scaleFactor) {
			if (hasToBeDrawn() && !toBeDestroyed) {
				highlight(canvas, scaleFactor);
				drawShadedRegion(canvas, TEMP_SCRAP_REGION_COLOR);
				drawBorder(canvas, scaleFactor);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.uci.calismall.CaliSmallElement#contains(edu.uci.calismall.
	 * CaliSmallElement)
	 */
	@Override
	public boolean contains(CaliSmallElement element) {
		Region elementBoundaries = element.getBoundaries();
		Region boundaries = getBoundaries();
		if (boundaries.op(elementBoundaries, Op.INTERSECT)) {
			// elements intersect
			if (!element.getBoundaries().op(getBoundaries(), Op.DIFFERENCE)) {
				return true;
			}
		}
		return false;
	}

}
