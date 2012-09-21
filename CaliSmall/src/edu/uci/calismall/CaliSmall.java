/**
 * CaliSmall.java
 * Created on July 11, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * A simple proof of concept for Calico on Android devices.
 * 
 * @author Michele Bonazza
 */
public class CaliSmall extends Activity {

	private class Worker implements Runnable {

		private final SurfaceHolder holder;
		private final CaliView view;

		private Worker(SurfaceHolder holder, CaliView view) {
			this.holder = holder;
			this.view = view;
		}

		public void run() {
			Canvas canvas = null;
			long timer;
			while (view.running) {
				timer = -System.currentTimeMillis();
				try {
					canvas = holder.lockCanvas();
					if (canvas != null)
						view.drawView(canvas);
				} catch (IllegalArgumentException e) {
					// activity sent to bg, don't care
				} finally {
					try {
						holder.unlockCanvasAndPost(canvas);
					} catch (IllegalArgumentException e) {
						// app has been minimized, don't care
					}
				}
				timer += System.currentTimeMillis();
				if (timer < 20) {
					try {
						// draw canvas at ~50fps
						Thread.sleep(20 - timer);
					} catch (InterruptedException e) {
						// don't care, it'll be less fluid, big deal
						Log.d(TAG, "interrupted!");
					}
				}
			}
		}
	}

	/**
	 * The {@link SurfaceView} on which the canvas is drawn.
	 * 
	 * @author Michele Bonazza
	 */
	public class CaliView extends SurfaceView implements SurfaceHolder.Callback {

		private static final int INVALID_POINTER_ID = -1;
		private final PathMeasure pathMeasure;
		// a list of strokes kept in chronological order (oldest first)
		private final List<Stroke> strokes;
		// a list of scraps kept in chronological order (oldest first)
		private final List<Scrap> scraps;

		private Thread worker;
		private Scrap selected, previousSelection, newScrap, toBeRemoved,
				tempScrap, tempSelStrokes;
		private boolean zooming, running, mustShowLandingZone, strokeAdded,
				mustClearCanvas, bubbleMenuShown, mustShowBubbleMenu,
				tempScrapCreated, redirectingToBubbleMenu;
		private PointF landingZoneCenter;
		private int mActivePointerId = INVALID_POINTER_ID, screenWidth,
				screenHeight;

		private CaliView(Context c) {
			super(c);
			getHolder().addCallback(this);
			strokes = new ArrayList<Stroke>();
			scraps = new ArrayList<Scrap>();
			scaleDetector = new ScaleGestureDetector(c, new ScaleListener());
			pathMeasure = new PathMeasure();
			landingZoneCenter = new PointF();
		}

		private void drawView(Canvas canvas) {
			if (canvas != null) {
				canvas.drawColor(Color.WHITE);
				canvas.concat(matrix);
				if (mustClearCanvas) {
					clearCanvas();
				} else {
					maybeDrawLandingZone(canvas);
					maybeCreateBubbleMenu();
					deleteSelected();
					drawTempScrap(canvas);
					maybeCreateScrap();
					addTmpSelectionStrokes();
					drawScraps(canvas);
					drawStrokes(canvas);
					if (bubbleMenuShown)
						bubbleMenu.draw(canvas);
					drawNewStroke(canvas);
				}
			}
		}

		private void clearCanvas() {
			strokes.clear();
			scraps.clear();
			Stroke.SPACE_OCCUPATION_LIST.clear();
			Scrap.SPACE_OCCUPATION_LIST.clear();
			mustClearCanvas = false;
		}

		private void drawScraps(Canvas canvas) {
			for (Scrap scrap : scraps) {
				if (scrap.hasToBeDrawn())
					scrap.draw(this, canvas, scaleFactor);
			}
		}

		private void drawStrokes(Canvas canvas) {
			for (Stroke stroke : strokes) {
				if (stroke.hasToBeDrawn())
					drawStroke(stroke, canvas);
			}
		}

		private void drawTempScrap(Canvas canvas) {
			if (tempScrap != null) {
				tempScrap.draw(this, canvas, scaleFactor);
			}
		}

		private void drawNewStroke(Canvas canvas) {
			if (!strokeAdded) {
				drawStroke(stroke, canvas);
				strokes.add(stroke);
				Stroke.SPACE_OCCUPATION_LIST.add(stroke);
				strokeAdded = true;
			}
		}

		private void maybeDrawLandingZone(Canvas canvas) {
			if (mustShowLandingZone) {
				canvas.drawCircle(landingZoneCenter.x, landingZoneCenter.y,
						landingZoneRadius, landingZonePaint);
			}
		}

		private void maybeCreateBubbleMenu() {
			if (mustShowBubbleMenu) {
				mustShowBubbleMenu = false;
				if (tempScrapCreated) {
					Stroke border = strokes.remove(strokes.size() - 1);
					Stroke.SPACE_OCCUPATION_LIST.remove(border);
					createNewStroke();
					changeTempScrap(new Scrap.Temp(border, scaleFactor));
					tempScrapCreated = false;
				}
				bubbleMenuShown = true;
			}
		}

		private void deleteSelected() {
			if (toBeRemoved != null) {
				toBeRemoved.erase();
				CaliSmallElement.deleteMarkedFromList(strokes,
						Stroke.SPACE_OCCUPATION_LIST);
				mustShowBubbleMenu = false;
				toBeRemoved = null;
			}
		}

		private void maybeCreateScrap() {
			if (newScrap != null) {
				scraps.add(newScrap);
				Scrap.SPACE_OCCUPATION_LIST.add(newScrap);
				Log.d(TAG, "new scrap! Here's the new list");
				Log.d(TAG, Scrap.SPACE_OCCUPATION_LIST.toString());
				setSelected(newScrap);
				previousSelection = newScrap;
				newScrap = null;
			}
		}

		private void addTmpSelectionStrokes() {
			if (tempSelStrokes != null) {
				strokes.addAll(tempSelStrokes.getStrokes());
				scraps.addAll(tempSelStrokes.getScraps());
				tempSelStrokes = null;
			}
		}

		/**
		 * Sets the argument scrap as the selected one, unselecting the scrap
		 * that was previously selected (if any was).
		 * 
		 * @param selected
		 *            the scrap that should appear selected or <code>null</code>
		 *            if there shouldn't be any scrap selected
		 */
		public void setSelected(Scrap selected) {
			if (this.selected != null && selected != this.selected)
				this.selected.deselect();
			if (selected != null) {
				bubbleMenu.setBounds(selected.getBorder(), scaleFactor, bounds);
				mustShowBubbleMenu = true;
				selected.select();
			} else {
				bubbleMenuShown = false;
				redirectingToBubbleMenu = false;
			}
			this.selected = selected;
		}

		/**
		 * Changes the current selected scrap to the argument temp scrap.
		 * 
		 * @param newTempScrap
		 *            the new temporary scrap, cannot be <code>null</code>
		 */
		public void changeTempScrap(Scrap newTempScrap) {
			if (newTempScrap != null) {
				tempScrap = newTempScrap;
				setSelected(tempScrap);
				previousSelection = tempScrap;
			}
		}

		/**
		 * Adds the argument scrap to the canvas.
		 * 
		 * @param scrap
		 *            the scrap to be added
		 */
		public void addScrap(Scrap scrap) {
			newScrap = scrap;
		}

		/**
		 * Adds all strokes in the argument scrap to the canvas.
		 * 
		 * <p>
		 * Called when copying a temp selection.
		 * 
		 * @param toBeAdded
		 *            the temporary selection whose strokes must be added to the
		 *            canvas
		 */
		public void addStrokes(Scrap toBeAdded) {
			this.tempSelStrokes = toBeAdded;
		}

		/**
		 * Removes (stops drawing) the argument scrap.
		 * 
		 * @param scrap
		 *            the scrap to be deleted
		 */
		public void removeScrap(Scrap scrap) {
			toBeRemoved = scrap;
		}

		/**
		 * Draws the argument stroke on the argument canvas.
		 * 
		 * @param stroke
		 *            the stroke to be drawn
		 * @param canvas
		 *            the canvas on which to draw the stroke
		 */
		public void drawStroke(Stroke stroke, Canvas canvas) {
			paint.setColor(stroke.getColor());
			paint.setStrokeWidth(stroke.getStrokeWidth());
			paint.setStyle(stroke.getStyle());
			canvas.drawPath(stroke.getPath(), paint);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			final int action = event.getAction() & MotionEvent.ACTION_MASK;
			// Log.d(TAG, actionToString(action));
			if (zooming) {
				scaleDetector.onTouchEvent(event);
				switch (action) {
				case MotionEvent.ACTION_UP:
					// last finger lifted
					onUp(event);
					// delete stroke if one was accidentally created
					stroke.getPath().reset();
					zooming = false;
					break;
				case MotionEvent.ACTION_POINTER_UP:
					// first finger lifted (only when pinching)
					onPointerUp(event);
					break;
				}
			} else {
				if (redirectingToBubbleMenu) {
					if (onTouchBubbleMenuShown(action,
							adjustForZoom(event.getX(), event.getY()))) {
						// action has been handled by the bubble menu
						Log.d(TAG, "handled by bubble menu");
						return true;
					} else {
						Log.d(TAG, "deselect bubble menu NOW");
						redirectingToBubbleMenu = false;
						mustShowBubbleMenu = false;
						bubbleMenuShown = false;
					}
				}
				switch (action) {
				case MotionEvent.ACTION_DOWN:
					// first touch with one finger
					onDown(event);
					scaleDetector.onTouchEvent(event);
					break;
				case MotionEvent.ACTION_MOVE:
					onMove(event);
					break;
				case MotionEvent.ACTION_UP:
					// last finger lifted
					onUp(event);
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					// first touch with second finger
					bubbleMenuShown = false;
					setSelected(null);
					scaleDetector.onTouchEvent(event);
					zooming = true;
					break;
				default:
					Log.d(TAG, "default: " + actionToString(action));
				}
			}
			return true;
		}

		/**
		 * Returns a string that represents the symbolic name of the specified
		 * action such as "ACTION_DOWN", "ACTION_POINTER_DOWN(3)" or an
		 * equivalent numeric constant such as "35" if unknown.
		 * 
		 * @param action
		 *            The action.
		 * @return The symbolic name of the specified action.
		 * @hide
		 */
		public String actionToString(int action) {
			switch (action) {
			case MotionEvent.ACTION_DOWN:
				return "ACTION_DOWN";
			case MotionEvent.ACTION_UP:
				return "ACTION_UP";
			case MotionEvent.ACTION_CANCEL:
				return "ACTION_CANCEL";
			case MotionEvent.ACTION_OUTSIDE:
				return "ACTION_OUTSIDE";
			case MotionEvent.ACTION_MOVE:
				return "ACTION_MOVE";
			case MotionEvent.ACTION_HOVER_MOVE:
				return "ACTION_HOVER_MOVE";
			case MotionEvent.ACTION_SCROLL:
				return "ACTION_SCROLL";
			case MotionEvent.ACTION_HOVER_ENTER:
				return "ACTION_HOVER_ENTER";
			case MotionEvent.ACTION_HOVER_EXIT:
				return "ACTION_HOVER_EXIT";
			}
			int index = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			switch (action & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_POINTER_DOWN:
				return "ACTION_POINTER_DOWN(" + index + ")";
			case MotionEvent.ACTION_POINTER_UP:
				return "ACTION_POINTER_UP(" + index + ")";
			default:
				return Integer.toString(action);
			}
		}

		/**
		 * Checks whether the argument <tt>touchPoint</tt> is within any of the
		 * buttons in the bubble menu, and if so it forwards the action to the
		 * bubble menu. Returns whether the bubble menu should keep being
		 * displayed.
		 */
		private boolean onTouchBubbleMenuShown(int action, PointF touchPoint) {
			if (bubbleMenu.buttonTouched(touchPoint)) {
				return bubbleMenu.onTouch(action, touchPoint, selected);
			}
			return false;
		}

		private void onDown(MotionEvent event) {
			PointF adjusted = adjustForZoom(event.getX(), event.getY());
			mustShowLandingZone = false;
			mActivePointerId = event.findPointerIndex(0);
			if (bubbleMenuShown) {
				if (onTouchBubbleMenuShown(MotionEvent.ACTION_DOWN, adjusted)) {
					// a button was touched, redirect actions to bubble menu
					redirectingToBubbleMenu = true;
					return;
				}
			}
			stroke.setStart(adjusted);
			setSelected(getSelectedScrap(adjusted));
		}

		private void onMove(MotionEvent event) {
			final int pointerIndex = event.findPointerIndex(mActivePointerId);
			final PointF adjusted = adjustForZoom(event.getX(pointerIndex),
					event.getY(pointerIndex));
			if (!mustShowLandingZone) {
				mustShowLandingZone = mustShowLandingZone();
				if (mustShowLandingZone) {
					final float[] position = new float[2];
					pathMeasure
							.getPosTan(landingZonePathOffset, position, null);
					landingZoneCenter = new PointF(position[0], position[1]);
				}
			}
			if (stroke.addPoint(adjusted, touchTolerance)) {
				setSelected(getSelectedScrap());
			}
		}

		private void onUp(MotionEvent event) {
			mustShowLandingZone = false;
			if (!zooming) {
				final int pointerIndex = event
						.findPointerIndex(mActivePointerId);
				mActivePointerId = INVALID_POINTER_ID;
				final PointF adjusted = adjustForZoom(event.getX(pointerIndex),
						event.getY(pointerIndex));
				// last stroke ended, compute its boundaries
				stroke.setBoundaries();
				if (isInLandingZone(adjusted) && isWideEnoughForBubbleMenu()) {
					bubbleMenu.setBounds(stroke.getPath(), scaleFactor, bounds);
					mustShowBubbleMenu = true;
					tempScrapCreated = true;
				} else {
					Scrap newSelection;
					if (!hasMovedEnough()) {
						Log.d(TAG, "tap detected");
						PointF center = stroke.getStartPoint();
						newSelection = getSelectedScrap(center);
						if (newSelection == previousSelection) {
							// draw a point (a small circle)
							stroke.setStyle(Paint.Style.FILL);
							stroke.getPath().addCircle(center.x, center.y,
									stroke.getStrokeWidth() / 2, Direction.CW);
							stroke.setBoundaries();
						} else {
							// a single tap selects the scrap w/o being
							// drawn
							stroke.getPath().reset();
						}
					} else {
						newSelection = getSelectedScrap();
					}
					setSelected(newSelection);
					createNewStroke();
				}
			} else {
				setSelected(previousSelection);
			}
			previousSelection = selected;
			Log.d(TAG, "selected = " + selected + ", old selected = "
					+ previousSelection);
		}

		private void createNewStroke() {
			if (!stroke.getPath().isEmpty()) {
				// otherwise don't create useless strokes
				stroke = new Stroke(new Path(), stroke);
				strokeAdded = false;
			}
		}

		private Scrap getSelectedScrap() {
			List<CaliSmallElement> candidates = Scrap.SPACE_OCCUPATION_LIST
					.findIntersectionCandidates(stroke);
			// sort elements by their size (smallest first)
			Collections.sort(candidates);
			for (CaliSmallElement element : candidates) {
				if (element.contains(stroke)) {
					return Scrap.class.cast(element).getSmallestTouched(stroke);
				}
			}
			return null;
		}

		private Scrap getSelectedScrap(PointF adjusted) {
			for (int i = scraps.size() - 1; i > -1; i--) {
				// reverse loop because older scraps are behind
				Scrap scrap = scraps.get(i);
				if (scrap.contains(adjusted)) {
					return scrap.getSmallestTouched(adjusted);
				}
			}
			return null;
		}

		private void onPointerUp(MotionEvent event) {
			final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			final int pointerId = event.getPointerId(pointerIndex);
			if (pointerId == mActivePointerId) {
				// This was our active pointer going up. Choose a new
				// active pointer and adjust accordingly.
				final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
				mActivePointerId = event.getPointerId(newPointerIndex);
			}
		}

		private boolean isInLandingZone(PointF lastPoint) {
			return Math.pow(lastPoint.x - landingZoneCenter.x, 2)
					+ Math.pow(lastPoint.y - landingZoneCenter.y, 2) <= Math
						.pow(landingZoneRadius, 2);
		}

		private boolean isWideEnoughForBubbleMenu() {
			RectF rect = new RectF();
			stroke.getPath().computeBounds(rect, true);
			final float minSize = (BubbleMenu.ABS_B_SIZE / scaleFactor) * 2;
			return rect.height() >= minSize;
		}

		private boolean hasMovedEnough() {
			return stroke.getWidth() + stroke.getHeight() > touchThreshold;
		}

		private boolean mustShowLandingZone() {
			pathMeasure.setPath(stroke.getPath(), false);
			return pathMeasure.getLength() > minPathLengthForLandingZone;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.
		 * SurfaceHolder, int, int, int)
		 */
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			screenWidth = width;
			screenHeight = height;
			updateBounds();
			if (selected != null) {
				bubbleMenu.setBounds(selected.getBorder(), scaleFactor, bounds);
			} else {
				bubbleMenu.setBounds(scaleFactor, bounds);
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.
		 * SurfaceHolder)
		 */
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			if (!running) {
				worker = new Thread(new Worker(getHolder(), this));
				running = true;
				worker.start();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view
		 * .SurfaceHolder)
		 */
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			running = false;
			boolean exited = false;
			while (!exited) {
				try {
					worker.join();
					exited = true;
				} catch (InterruptedException e) {
					// retry
				}
			}
		}
	}

	private class ScaleListener extends
			ScaleGestureDetector.SimpleOnScaleGestureListener {

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.view.ScaleGestureDetector.SimpleOnScaleGestureListener#
		 * onScaleBegin(android.view.ScaleGestureDetector)
		 */
		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			scaledPreviousScaleCenterX = detector.getFocusX() / scaleFactor;
			scaledPreviousScaleCenterY = detector.getFocusY() / scaleFactor;
			return true;
		}

		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			dScaleCenterX = detector.getFocusX();
			dScaleCenterY = detector.getFocusY();
			// don't let the canvas get too small or too large
			final float newScale = scaleFactor * detector.getScaleFactor();
			if (newScale > MAX_ZOOM || newScale < MIN_ZOOM) {
				scaleFactor = Math.max(MIN_ZOOM,
						Math.min(scaleFactor, MAX_ZOOM));
				dScaleFactor = 1.f;
			} else {
				scaleFactor *= detector.getScaleFactor();
				dScaleFactor = detector.getScaleFactor();
			}
			final float scaledCenterX = dScaleCenterX / scaleFactor;
			final float scaledCenterY = dScaleCenterY / scaleFactor;
			mTranslateX = scaledCenterX - scaledPreviousScaleCenterX;
			mTranslateY = scaledCenterY - scaledPreviousScaleCenterY;
			mCanvasOffsetX += mTranslateX;
			mCanvasOffsetY += mTranslateY;
			// translate, move origin to (x,y) to center zooming
			matrix.preTranslate(mTranslateX + dScaleCenterX, mTranslateY
					+ dScaleCenterY);
			// scale and move origin back to (0,0)
			matrix.postScale(dScaleFactor, dScaleFactor);
			matrix.preTranslate(-dScaleCenterX, -dScaleCenterY);
			scaledPreviousScaleCenterX = scaledCenterX;
			scaledPreviousScaleCenterY = scaledCenterY;
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.view.ScaleGestureDetector.SimpleOnScaleGestureListener#onScaleEnd
		 * (android.view.ScaleGestureDetector)
		 */
		@Override
		public void onScaleEnd(ScaleGestureDetector detector) {
			// rescale paint brush and connect circle
			stroke.setStrokeWidth(ABS_STROKE_WIDTH / scaleFactor);
			landingZoneRadius = ABS_LANDING_ZONE_RADIUS / scaleFactor;
			minPathLengthForLandingZone = ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE
					/ scaleFactor;
			landingZonePathOffset = ABS_LANDING_ZONE_PATH_OFFSET / scaleFactor;
			touchThreshold = ABS_TOUCH_THRESHOLD / scaleFactor;
			touchTolerance = ABS_TOUCH_TOLERANCE / scaleFactor;
			final float newInterval = ABS_LANDING_ZONE_INTERVAL / scaleFactor;
			landingZonePaint.setPathEffect(new DashPathEffect(new float[] {
					newInterval, newInterval }, (float) 1.0));
			updateBounds();
		}
	}

	/**
	 * Tag used for the whole application in LogCat files.
	 */
	public static final String TAG = "CaliSmall";

	/**
	 * Absolute stroke width (to be rescaled by scaleFactor).
	 */
	public static final int ABS_STROKE_WIDTH = 6;

	/**
	 * A list containing all created scraps sorted by their position in the
	 * canvas.
	 */
	static final SpaceOccupationList SPACE_OCCUPATION_LIST = new SpaceOccupationList();

	/**
	 * Absolute radius for landing zones (to be rescaled by scaleFactor).
	 */
	static final float ABS_LANDING_ZONE_RADIUS = 25;
	/**
	 * The interval between dashes in landing zones (to be rescaled by
	 * scaleFactor).
	 */
	static final float ABS_LANDING_ZONE_INTERVAL = 5;
	/**
	 * The length that a Path must reach before a landing zone is shown (to be
	 * rescaled by scaleFactor).
	 */
	static final float ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE = 160;
	/**
	 * Where to put the center of the landing zone on a path (to be rescaled by
	 * scaleFactor).
	 */
	static final float ABS_LANDING_ZONE_PATH_OFFSET = 40;
	/**
	 * The length over which a Path is no longer considered as a potential tap,
	 * but is viewed as a stroke instead (to be rescaled by scaleFactor).
	 */
	static final float ABS_TOUCH_THRESHOLD = 6;
	/**
	 * The amount of pixels that a touch needs to cover before it is considered
	 * a move action (to be rescaled by scaleFactor).
	 */
	static final float ABS_TOUCH_TOLERANCE = 2;
	private static final float MIN_ZOOM = 1f;
	private static final float MAX_ZOOM = 4f;
	private static final int COLOR_MENU_ID = Menu.FIRST;
	private static final int CLEAR_MENU_ID = Menu.FIRST + 1;
	private BubbleMenu bubbleMenu;
	private CaliView view;
	private Matrix matrix;
	private RectF bounds;
	private Stroke stroke;
	private Paint paint, landingZonePaint;
	private ScaleGestureDetector scaleDetector;
	// variables starting with 'd' are in display-coordinates
	private float scaleFactor = 1.f, dScaleFactor = 1.f, dScaleCenterX,
			dScaleCenterY, scaledPreviousScaleCenterX,
			scaledPreviousScaleCenterY, mTranslateX, mTranslateY,
			mCanvasOffsetX, mCanvasOffsetY,
			landingZoneRadius = ABS_LANDING_ZONE_RADIUS,
			minPathLengthForLandingZone = ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE,
			landingZonePathOffset = ABS_LANDING_ZONE_PATH_OFFSET,
			touchThreshold = ABS_TOUCH_THRESHOLD,
			touchTolerance = ABS_TOUCH_TOLERANCE;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		view = new CaliView(this);
		matrix = new Matrix();
		bounds = new RectF();
		setContentView(view);
		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setDither(true);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
		landingZonePaint = new Paint();
		landingZonePaint.setColor(Color.BLACK);
		landingZonePaint.setPathEffect(new DashPathEffect(new float[] {
				ABS_LANDING_ZONE_INTERVAL, ABS_LANDING_ZONE_INTERVAL },
				(float) 1.0));
		landingZonePaint.setStyle(Style.STROKE);
		stroke = new Stroke(new Path(), null);
		bubbleMenu = new BubbleMenu(this);
	}

	/**
	 * Returns the current view.
	 * 
	 * @return the view
	 */
	public CaliView getView() {
		return view;
	}

	/**
	 * Returns a point with (x, y) coordinates set to be displayed on the
	 * current canvas.
	 * 
	 * @param x
	 *            the X-axis value
	 * @param y
	 *            the Y-axis value
	 * @return a new point to which the current scale factor and offset have
	 *         been applied
	 */
	public PointF adjustForZoom(float x, float y) {
		return new PointF(x / scaleFactor - mCanvasOffsetX, y / scaleFactor
				- mCanvasOffsetY);
	}

	private void updateBounds() {
		final PointF max = adjustForZoom(view.screenWidth, view.screenHeight);
		final PointF min = adjustForZoom(0, 0);
		bounds.set(min.x, min.y, max.x, max.y);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, COLOR_MENU_ID, 0, "Color").setShortcut('3', 'c');
		menu.add(0, CLEAR_MENU_ID, 0, "Clear").setShortcut('4', 'x');
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		view.setSelected(null);
		switch (item.getItemId()) {
		case COLOR_MENU_ID:
			AmbilWarnaDialog dialog = new AmbilWarnaDialog(this,
					stroke.getColor(), new OnAmbilWarnaListener() {
						@Override
						public void onOk(AmbilWarnaDialog dialog, int color) {
							if (!stroke.isEmpty()) {
								stroke = new Stroke(new Path(), stroke);
								view.strokeAdded = false;
							}
							stroke.setColor(color);
						}

						@Override
						public void onCancel(AmbilWarnaDialog dialog) {
						}
					});

			dialog.show();
			return true;
		case CLEAR_MENU_ID:
			view.mustClearCanvas = true;
			view.strokeAdded = false;
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Returns what <tt>PointF.toString()</tt> should have returned, but Android
	 * developers were too lazy to implement.
	 * 
	 * @param point
	 *            the point of which a String representation must be returned
	 * @return a String containing the point's coordinates enclosed within
	 *         parentheses
	 */
	public static String pointToString(PointF point) {
		return new StringBuilder("(").append(point.x).append(",")
				.append(point.y).append(")").toString();
	}

	/**
	 * Returns what <tt>Point.toString()</tt> should have returned (without the
	 * initial <tt>"Point"</tt> that the <tt>toString()</tt> default
	 * implementation returns).
	 * 
	 * @param point
	 *            the point of which a String representation must be returned
	 * @return a String containing the point's coordinates enclosed within
	 *         parentheses
	 */
	public static String pointToString(Point point) {
		return new StringBuilder("(").append(point.x).append(",")
				.append(point.y).append(")").toString();
	}

}
