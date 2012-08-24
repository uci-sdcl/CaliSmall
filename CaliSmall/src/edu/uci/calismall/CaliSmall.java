/**
 * CaliSmall.java
 * Created on July 11, 2012
 * Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
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
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
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
		private final Thread worker;
		private final PathMeasure pathMeasure;
		private float lastX, lastY;
		private final List<Stroke> strokes;
		private final List<Scrap> scraps;
		private Scrap selected, newScrap, toBeRemoved, tempSelStrokes;
		private boolean zooming, drawing, moved, running, mustShowLandingZone,
				strokeAdded, clearStrokes, bubbleMenuShown, mustShowBubbleMenu,
				tempSelectionCreated;
		private PointF landingZoneCenter;
		private int mActivePointerId = INVALID_POINTER_ID, screenWidth,
				screenHeight;

		private CaliView(Context c) {
			super(c);
			getHolder().addCallback(this);
			worker = new Thread(new Worker(getHolder(), this));
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
				if (clearStrokes) {
					strokes.clear();
					scraps.clear();
					clearStrokes = false;
				} else {
					maybeDrawLandingZone(canvas);
					maybeCreateBubbleMenu();
					deleteSelectedStrokes();
					if (bubbleMenuShown) {
						selected.draw(this, canvas, scaleFactor);
					}
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

		private void drawScraps(Canvas canvas) {
			for (Scrap scrap : scraps) {
				// only draw top-level scraps
				if (!scrap.isInScrap())
					scrap.draw(this, canvas, scaleFactor);
			}
		}

		private void drawStrokes(Canvas canvas) {
			for (Stroke stroke : strokes) {
				if (!stroke.isInScrap())
					drawStroke(stroke, canvas);
			}
		}

		private void drawNewStroke(Canvas canvas) {
			if (!strokeAdded) {
				if (!stroke.isInScrap()) {
					drawStroke(stroke, canvas);
					strokes.add(stroke);
					strokeAdded = true;
				}
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
				if (tempSelectionCreated) {
					selected = new Scrap.Temp(
							strokes.remove(strokes.size() - 1), scraps,
							strokes, scaleFactor);
					tempSelectionCreated = false;
				}
				bubbleMenuShown = true;
			}
		}

		private void deleteSelectedStrokes() {
			if (toBeRemoved != null) {
				scraps.removeAll(toBeRemoved.erase());
				scraps.remove(toBeRemoved);
				strokes.removeAll(toBeRemoved.getStrokes());
				toBeRemoved = null;
			}
		}

		private void maybeCreateScrap() {
			if (newScrap != null) {
				scraps.removeAll(newScrap.getScraps());
				strokes.removeAll(newScrap.getStrokes());
				scraps.add(newScrap);
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
			if (this.selected != null)
				this.selected.deselect();
			if (selected != null) {
				bubbleMenu.setBounds(selected.getBorder(), scaleFactor, bounds);
				mustShowBubbleMenu = true;
				selected.select();
			} else {
				bubbleMenuShown = false;
				if (!stroke.isEmpty()) {
					// create a new stroke
					stroke = new Stroke(new Path(), stroke);
					strokeAdded = false;
				}
			}
			this.selected = selected;
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
			PointF adjusted = adjustForZoom(event.getX(), event.getY());
			if (bubbleMenuShown) {
				onTouchBubbleMenuShown(action, adjusted);
			} else {
				if (!drawing)
					scaleDetector.onTouchEvent(event);
				switch (action) {
				case MotionEvent.ACTION_DOWN:
					// first touch with one finger
					onDown(event);
					break;
				case MotionEvent.ACTION_MOVE:
					// move only if using 1 finger
					if (!zooming)
						onMove(event);
					break;
				case MotionEvent.ACTION_UP:
					// last finger lifted
					onUp(event);
					break;
				case MotionEvent.ACTION_POINTER_UP:
					// first finger lifted (only when pinching)
					onPointerUp(event);
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					// first touch with second finger
					if (!drawing)
						zooming = true;
				}
			}
			return true;
		}

		private void onTouchBubbleMenuShown(int action, PointF touchPoint) {
			if (bubbleMenu.touched(touchPoint)) {
				if (!bubbleMenu.onTouch(action, touchPoint, selected)) {
					setSelected(null);
				}
			} else {
				Scrap newSelection = getSelectedScrap(touchPoint);
				if (action == MotionEvent.ACTION_UP) {
					setSelected(newSelection == selected ? null : newSelection);
				}
			}
		}

		private void onDown(MotionEvent event) {
			PointF adjusted = adjustForZoom(event.getX(), event.getY());
			mustShowLandingZone = false;
			lastX = adjusted.x;
			lastY = adjusted.y;
			stroke.setStart(adjusted);
			stroke.getPath().moveTo(lastX, lastY);
			mActivePointerId = event.getPointerId(0);
		}

		private void onMove(MotionEvent event) {
			drawing = true;
			if (!moved)
				moved = true;
			if (!mustShowLandingZone) {
				mustShowLandingZone = mustShowLandingZone();
				if (mustShowLandingZone) {
					final float[] position = new float[2];
					pathMeasure
							.getPosTan(landingZonePathOffset, position, null);
					landingZoneCenter = new PointF(position[0], position[1]);
				}
			}
			final int pointerIndex = event.findPointerIndex(mActivePointerId);
			final PointF adjusted = adjustForZoom(event.getX(pointerIndex),
					event.getY(pointerIndex));
			final float x = adjusted.x;
			final float y = adjusted.y;
			final float dx = Math.abs(x - lastX);
			final float dy = Math.abs(y - lastY);
			if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
				stroke.getPath().quadTo(lastX, lastY, (x + lastX) / 2,
						(y + lastY) / 2);
				lastX = x;
				lastY = y;
			}
		}

		private void onUp(MotionEvent event) {
			drawing = false;
			if (!zooming) {
				final int pointerIndex = event
						.findPointerIndex(mActivePointerId);
				final PointF adjusted = adjustForZoom(event.getX(pointerIndex),
						event.getY(pointerIndex));
				if (!moved) {
					// a single touch inside a scrap selects it
					Scrap selected = getSelectedScrap(adjusted);
					if (selected != null) {
						setSelected(selected);
						mActivePointerId = INVALID_POINTER_ID;
						return;
					}
					// draw a point (a small circle)
					stroke.setStyle(Paint.Style.FILL);
					stroke.getPath().addCircle(adjusted.x, adjusted.y,
							stroke.getStrokeWidth() / 2, Direction.CW);
				}
				// last stroke ended, compute its boundaries
				stroke.setBoundaries();
				if (isInLandingZone(adjusted) && isWideEnoughForBubbleMenu()) {
					bubbleMenu.setBounds(stroke.getPath(), scaleFactor, bounds);
					mustShowBubbleMenu = true;
					tempSelectionCreated = true;
				} else {
					// create a new stroke
					stroke = new Stroke(new Path(), stroke);
					strokeAdded = false;
				}
			}
			mustShowLandingZone = false;
			zooming = false;
			moved = false;
			mActivePointerId = INVALID_POINTER_ID;
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
	static final float ABS_LANDING_ZONE_PATH_OFFSET = 50;
	/**
	 * The amount of pixels that a touch needs to cover before it is considered
	 * a move action.
	 */
	static final float TOUCH_TOLERANCE = 2;
	private static final float MIN_ZOOM = 0.25f;
	private static final float MAX_ZOOM = 3f;
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
			landingZonePathOffset = ABS_LANDING_ZONE_PATH_OFFSET;

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
			view.clearStrokes = true;
			view.strokeAdded = false;
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
