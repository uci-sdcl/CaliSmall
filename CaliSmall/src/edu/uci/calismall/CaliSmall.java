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
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * A small version of Calico for Android devices.
 * 
 * <p>
 * This version is still a standalone app that does no communication with Calico
 * server(s).
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
				if (timer < SCREEN_REFRESH_TIME) {
					try {
						Thread.sleep(SCREEN_REFRESH_TIME - timer);
					} catch (InterruptedException e) {
						// don't care, it'll be less fluid, big deal
						Log.d(TAG, "interrupted!");
					}
				}
			}
		}
	}

	private static class LongPressAction implements Runnable {

		private final CaliSmall parent;
		private boolean completed;
		private Stroke selected;

		private LongPressAction(CaliSmall parent) {
			this.parent = parent;
		}

		@Override
		public void run() {
			if (completed) {
				if (!parent.view.hasMovedEnough()) {
					parent.view.longPress(selected);
					selected = null;
				}
			} else {
				if (!parent.view.hasMovedEnough()) {
					PointF center = parent.stroke.getStartPoint();
					parent.circleBounds = new RectF(center.x
							- parent.circleBoundsSize, center.y
							- parent.circleBoundsSize, center.x
							+ parent.circleBoundsSize, center.y
							+ parent.circleBoundsSize);
					selected = parent.view.getSelectedStroke();
					if (selected != null) {
						parent.view.longPressed = true;
						parent.view.mustShowLongPressCircle = true;
					}
				}
			}
		}

		/**
		 * Resets the status for the long press action.
		 * 
		 * @param completed
		 *            whether the user kept the finger/stylus down for the
		 *            circle to be completely drawn on canvas, and therefore the
		 *            action should be performed.
		 */
		public void reset(boolean completed) {
			parent.view.longPressed = false;
			parent.view.mustShowLongPressCircle = false;
			parent.circleSweepAngle = 0;
			this.completed = completed;
			if (completed)
				parent.view.longPressListener.post(this);
		}
	}

	/**
	 * The {@link SurfaceView} on which the canvas is drawn.
	 * 
	 * <p>
	 * Drawing takes place within the {@link CaliView#drawView(Canvas)} method,
	 * which is called roughly every {@link CaliSmall#SCREEN_REFRESH_TIME}
	 * milliseconds by the <tt>Worker</tt> thread that is spawn by
	 * {@link CaliView#surfaceCreated(SurfaceHolder)} (which in turn is called
	 * by the Android Runtime when the app is moved to the foreground).
	 * 
	 * <p>
	 * All data structures accessed by the drawing thread are only edited by the
	 * drawing thread itself to prevent conflicts (and therefore locking). When
	 * objects must be added to/removed from the view, they must be put to other
	 * data structures whose only purpose is to communicate with the drawing
	 * thread.
	 * 
	 * <p>
	 * Input (touch) events are handles by the
	 * {@link CaliView#onTouchEvent(MotionEvent)} method, which is called by the
	 * Android Event Dispatcher Thread (or whatever they call it for Android,
	 * it's the equivalente of Java's good ol' EDT).
	 * 
	 * <p>
	 * To simplify the code, and to avoid conflicts, all drawing operations are
	 * performed by the drawing thread (no <tt>postXYZ()</tt> calls), and all
	 * object creation/editing and border inclusion/intersection computation is
	 * done by the EDT.
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
		private final Handler longPressListener = new Handler();
		private final LongPressAction longPressAction;
		private Thread worker;
		private Scrap selected, previousSelection, newSelection, toBeRemoved,
				tempScrap;
		private List<Stroke> newStrokes;
		private List<Scrap> newScraps;
		private boolean zoomingOrPanning, running, mustShowLandingZone,
				strokeAdded, mustClearCanvas, bubbleMenuShown,
				mustShowBubbleMenu, tempScrapCreated, redirectingToBubbleMenu,
				longPressed, mustShowLongPressCircle, skipEvents;
		private PointF landingZoneCenter;
		private int mActivePointerId = INVALID_POINTER_ID, screenWidth,
				screenHeight;

		private CaliView(CaliSmall c) {
			super(c);
			longPressAction = new LongPressAction(c);
			getHolder().addCallback(this);
			strokes = new ArrayList<Stroke>();
			scraps = new ArrayList<Scrap>();
			newStrokes = new ArrayList<Stroke>();
			newScraps = new ArrayList<Scrap>();
			scaleDetector = new ScaleGestureDetector(c, new ScaleListener());
			pathMeasure = new PathMeasure();
			landingZoneCenter = new PointF();
		}

		/**
		 * Draws this view to the argument {@link Canvas}.
		 * 
		 * @param canvas
		 *            the canvas onto which this view is to be drawn
		 */
		public void drawView(Canvas canvas) {
			if (canvas != null) {
				canvas.drawColor(Color.WHITE);
				canvas.concat(matrix);
				if (mustShowLongPressCircle) {
					drawLongPressAnimation(canvas);
				}
				if (mustClearCanvas) {
					clearCanvas();
				} else {
					// canvas.drawPath(canvasBounds, borderPaint);
					maybeDrawLandingZone(canvas);
					deleteSelected();
					maybeCreateBubbleMenu();
					drawTempScrap(canvas);
					addNewStrokesAndScraps();
					drawScraps(canvas);
					drawStrokes(canvas);
					if (bubbleMenuShown)
						bubbleMenu.draw(canvas);
					drawNewStroke(canvas);
				}
			}
		}

		private void drawLongPressAnimation(Canvas canvas) {
			circleSweepAngle += ABS_CIRCLE_SWEEP_INCREMENT;
			canvas.drawArc(circleBounds, CIRCLE_SWEEP_START, circleSweepAngle,
					false, circlePaint);
			if (circleSweepAngle > 360) {
				longPressAction.reset(true);
			}
		}

		private void clearCanvas() {
			strokes.clear();
			scraps.clear();
			Stroke.SPACE_OCCUPATION_LIST.clear();
			Scrap.SPACE_OCCUPATION_LIST.clear();
			setSelected(null);
			stroke = null;
			createNewStroke();
			mustClearCanvas = false;
		}

		private void drawScraps(Canvas canvas) {
			for (Scrap scrap : scraps) {
				scrap.draw(this, canvas, scaleFactor);
			}
		}

		private void drawStrokes(Canvas canvas) {
			for (Stroke stroke : strokes) {
				if (stroke.hasToBeDrawnVectorially())
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
					Stroke border = getActiveStroke();
					Stroke.SPACE_OCCUPATION_LIST.remove(border);
					createNewStroke();
					changeTempScrap(new Scrap.Temp(border, scaleFactor));
					tempScrapCreated = false;
				}
				bubbleMenuShown = true;
			}
		}

		private Stroke getActiveStroke() {
			int index = -1;
			for (index = strokes.size() - 1; index > -1; index--) {
				if (strokes.get(index).equals(activeStroke)) {
					break;
				}
			}
			if (index > -1) {
				return strokes.remove(index);
			}
			return null;
		}

		private void deleteSelected() {
			if (toBeRemoved != null) {
				toBeRemoved.erase();
				mustShowBubbleMenu = false;
				toBeRemoved = null;
			}
			CaliSmallElement.deleteMarkedFromList(strokes,
					Stroke.SPACE_OCCUPATION_LIST);
			CaliSmallElement.deleteMarkedFromList(scraps,
					Scrap.SPACE_OCCUPATION_LIST);
		}

		private void longPress(Stroke selected) {
			skipEvents = true;
			if (selected != null) {
				if (selected.parent != null) {
					((Scrap) selected.parent).remove(selected);
				}
				stroke.toBeDeleted = true;
				activeStroke = selected;
				tempScrapCreated = true;
				mustShowBubbleMenu = true;
			}
		}

		private void addNewStrokesAndScraps() {
			if (!newStrokes.isEmpty()) {
				strokes.addAll(newStrokes);
				Stroke.SPACE_OCCUPATION_LIST.addAll(newStrokes);
				newStrokes.clear();
			}
			if (!newScraps.isEmpty()) {
				scraps.addAll(newScraps);
				Scrap.SPACE_OCCUPATION_LIST.addAll(newScraps);
				newScraps.clear();
			}
			if (newSelection != null) {
				setSelected(newSelection);
				previousSelection = newSelection;
				newSelection = null;
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
				bubbleMenu.setBounds(selected.getBorder(), scaleFactor,
						screenBounds);
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
		 * @param addContent
		 *            whether all of the strokes inside of the argument scrap
		 *            should be added to the view as well, should be
		 *            <code>true</code> only for newly created scrap copies
		 */
		public void addScrap(Scrap scrap, boolean addContent) {
			newSelection = scrap;
			if (!(scrap instanceof Scrap.Temp))
				newScraps.add(scrap);
			if (addContent) {
				newStrokes.addAll(scrap.getAllStrokes());
				newScraps.addAll(scrap.getAllScraps());
			}
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
			if (zoomingOrPanning) {
				handleZoomingPanningEvent(event, action);
			} else {
				if (redirectingToBubbleMenu) {
					if (onTouchBubbleMenuShown(action,
							adjustForZoom(event.getX(), event.getY()))) {
						// action has been handled by the bubble menu
						return true;
					} else {
						redirectingToBubbleMenu = false;
						mustShowBubbleMenu = false;
						bubbleMenuShown = false;
						createNewStroke();
					}
				}
				handleDrawingEvent(event, action);
			}
			return true;
		}

		private void handleDrawingEvent(final MotionEvent event,
				final int action) {
			if (skipEvents) {
				if (action == MotionEvent.ACTION_UP) {
					onUp(event);
				}
			} else {
				// events in the switch block are in chronological order
				switch (action) {
				case MotionEvent.ACTION_DOWN:
					// first touch with one finger
					onDown(event);
					scaleDetector.onTouchEvent(event);
					break;
				case MotionEvent.ACTION_MOVE:
					onMove(event);
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					// first touch with second finger
					onPointerDown(event);
					break;
				case MotionEvent.ACTION_CANCEL:
					mustShowLandingZone = false;
					createNewStroke();
					break;
				case MotionEvent.ACTION_UP:
					// last finger lifted
					onUp(event);
					break;
				default:
					Log.d(TAG, "default: " + actionToString(action));
				}
			}
		}

		private void handleZoomingPanningEvent(final MotionEvent event,
				final int action) {
			scaleDetector.onTouchEvent(event);
			// events in the switch block are in chronological order
			switch (action) {
			case MotionEvent.ACTION_POINTER_UP:
				// first finger lifted (only when pinching)
				onPointerUp(event);
				break;
			case MotionEvent.ACTION_UP:
				// last finger lifted
				onUp(event);
				// delete stroke if one was accidentally created
				stroke.getPath().reset();
				zoomingOrPanning = false;
				break;
			// move actions are handled by scaleDetector
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
			longPressed = false;
			if (bubbleMenuShown) {
				if (onTouchBubbleMenuShown(MotionEvent.ACTION_DOWN, adjusted)) {
					// a button was touched, redirect actions to bubble menu
					redirectingToBubbleMenu = true;
					return;
				}
			}
			longPressListener.postDelayed(longPressAction, LONG_PRESS_DURATION);
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
				setSelected(getSelectedScrap(stroke));
			}
		}

		private void onUp(MotionEvent event) {
			mustShowLandingZone = false;
			longPressListener.removeCallbacks(longPressAction);
			skipEvents = false;
			if (zoomingOrPanning) {
				setSelected(previousSelection);
			} else {
				final int pointerIndex = event
						.findPointerIndex(mActivePointerId);
				mActivePointerId = INVALID_POINTER_ID;
				if (longPressAction.completed) {
					stroke.reset();
					createNewStroke();
					longPressAction.completed = false;
					return;
				} else if (longPressed) {
					// animation has been shown, but the user didn't mean to
					// long press, so she took her finger/stylus away
					longPressAction.reset(false);
				}
				final PointF adjusted = adjustForZoom(event.getX(pointerIndex),
						event.getY(pointerIndex));
				if (isInLandingZone(adjusted)) {
					bubbleMenu.setBounds(stroke.getPath(), scaleFactor,
							screenBounds);
					mustShowBubbleMenu = true;
					tempScrapCreated = true;
				} else {
					Scrap newSelection;
					if (!hasMovedEnough()) {
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
							stroke.reset();
						}
					} else {
						newSelection = getSelectedScrap(stroke);
					}
					setSelected(newSelection);
					if (selected != null && !stroke.isEmpty()) {
						selected.add(stroke);
					}
					createNewStroke();
				}
			}
			previousSelection = selected;
			// Log.d(TAG, "selected = " + selected + ", old selected = "
			// + previousSelection);
		}

		private void createNewStroke() {
			if (stroke == null || !stroke.getPath().isEmpty()) {
				// otherwise don't create useless strokes
				stroke = new Stroke(new Path(), stroke);
				activeStroke = stroke;
				strokeAdded = false;
			}
		}

		/**
		 * Returns the smallest scrap that contains the argument
		 * <tt>element</tt>, if any exists.
		 * 
		 * @param element
		 *            the element that must be completely contained within the
		 *            scrap that this method shall return
		 * @return the smallest scrap that completely contains the argument
		 *         element, if any exists, <code>null</code>
		 */
		public Scrap getSelectedScrap(CaliSmallElement element) {
			// TODO move it to the specific canvas object
			List<CaliSmallElement> candidates = Scrap.SPACE_OCCUPATION_LIST
					.findIntersectionCandidates(element);
			// sort elements by their size (smallest first)
			Collections.sort(candidates);
			for (CaliSmallElement candidate : candidates) {
				if (candidate.contains(element)) {
					return ((Scrap) (candidate)).getSmallestTouched(element);
				}
			}
			return null;
		}

		private Scrap getSelectedScrap(PointF adjusted) {
			// TODO move it to the specific canvas object
			List<CaliSmallElement> candidates = Scrap.SPACE_OCCUPATION_LIST
					.findContainerCandidates(adjusted);
			Collections.sort(candidates);
			for (CaliSmallElement candidate : candidates) {
				if (candidate.contains(adjusted)) {
					return ((Scrap) (candidate)).getSmallestTouched(adjusted);
				}
			}
			return null;
		}

		private Stroke getSelectedStroke() {
			List<CaliSmallElement> candidates = Stroke.SPACE_OCCUPATION_LIST
					.findIntersectionCandidates(stroke);
			// sort elements by their size (smallest first)
			Collections.sort(candidates);
			for (CaliSmallElement candidate : candidates) {
				if (candidate.contains(stroke.getStartPoint())) {
					return (Stroke) candidate;
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

		private void onPointerDown(MotionEvent event) {
			longPressListener.removeCallbacks(longPressAction);
			bubbleMenuShown = false;
			mustShowLandingZone = false;
			stroke.reset();
			setSelected(null);
			scaleDetector.onTouchEvent(event);
			zoomingOrPanning = true;
		}

		private boolean isInLandingZone(PointF lastPoint) {
			return Math.pow(lastPoint.x - landingZoneCenter.x, 2)
					+ Math.pow(lastPoint.y - landingZoneCenter.y, 2) <= Math
						.pow(landingZoneRadius, 2);
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
			canvasBounds.reset();
			canvasBounds.addRect(new RectF(0, 0, screenWidth, screenHeight),
					Direction.CCW);
			updateBounds();
			if (selected != null) {
				bubbleMenu.setBounds(selected.getBorder(), scaleFactor,
						screenBounds);
			} else {
				bubbleMenu.setBounds(scaleFactor, screenBounds);
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
			enforceBoundConstraints();
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

		private void enforceBoundConstraints() {
			if (mCanvasOffsetX > 0) {
				mTranslateX -= mCanvasOffsetX;
				mCanvasOffsetX = 0;
			} else {
				final float minOffsetX = (1 - scaleFactor) * view.screenWidth;
				if (mCanvasOffsetX * scaleFactor < minOffsetX) {
					float difference = mCanvasOffsetX * scaleFactor
							- minOffsetX;
					mCanvasOffsetX -= mTranslateX;
					mTranslateX -= difference;
					mCanvasOffsetX += mTranslateX;
				}
			}
			if (mCanvasOffsetY > 0) {
				mTranslateY -= mCanvasOffsetY;
				mCanvasOffsetY = 0;
			} else {
				final float minOffsetY = (1 - scaleFactor) * view.screenHeight;
				if (mCanvasOffsetY * scaleFactor < minOffsetY) {
					float difference = mCanvasOffsetY * scaleFactor
							- minOffsetY;
					mCanvasOffsetY -= mTranslateY;
					mTranslateY -= difference;
					mCanvasOffsetY += mTranslateY;
				}
			}
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
			circlePaint.setStrokeWidth(stroke.getStrokeWidth() * 2);
			landingZoneRadius = ABS_LANDING_ZONE_RADIUS / scaleFactor;
			minPathLengthForLandingZone = ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE
					/ scaleFactor;
			landingZonePathOffset = ABS_LANDING_ZONE_PATH_OFFSET / scaleFactor;
			touchThreshold = ABS_TOUCH_THRESHOLD / scaleFactor;
			touchTolerance = ABS_TOUCH_TOLERANCE / scaleFactor;
			circleBoundsSize = ABS_CIRCLE_BOUNDS_HALF_SIZE / scaleFactor;
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
	public static final int ABS_STROKE_WIDTH = 3;

	/**
	 * The amount of time (in milliseconds) before the long pressure animation
	 * is shown.
	 */
	public static final long LONG_PRESS_DURATION = 300;

	/**
	 * Time in milliseconds between two consecutive screen refreshes (i.e. two
	 * consecutive calls to {@link CaliView#drawView(Canvas)}). To get the FPS
	 * that this value sets, just divide 1000 by the value (so a
	 * <tt>SCREEN_REFRESH_TIME</tt> of <tt>20</tt> translates to 50 FPS).
	 */
	public static final long SCREEN_REFRESH_TIME = 20;

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
	static final float ABS_TOUCH_THRESHOLD = 8;
	/**
	 * The amount of pixels that a touch needs to cover before it is considered
	 * a move action (to be rescaled by scaleFactor).
	 */
	static final float ABS_TOUCH_TOLERANCE = 2;

	/**
	 * Absolute half the size of the rectangle enclosing the circle displayed on
	 * long presses (to be rescaled by scaleFactor).
	 */
	static final float ABS_CIRCLE_BOUNDS_HALF_SIZE = 75;

	/**
	 * Absolute length of the increment when drawing the long press circle
	 * between {@link CaliView#draw(Canvas)} calls. This determines the speed at
	 * which the circle is animated.
	 */
	static final float ABS_CIRCLE_SWEEP_INCREMENT = 25;
	/**
	 * The starting point for the sweep animation for long presses. 0 is the
	 * rightmost point, -90 is the topmost point.
	 */
	static final float CIRCLE_SWEEP_START = -90;
	private static final float MIN_ZOOM = 1f;
	private static final float MAX_ZOOM = 4f;
	private static final int COLOR_MENU_ID = Menu.FIRST;
	private static final int CLEAR_MENU_ID = Menu.FIRST + 1;
	private static final int LOG_MENU_ID = Menu.FIRST + 2;
	private BubbleMenu bubbleMenu;
	private CaliView view;
	private Matrix matrix;
	private RectF screenBounds, circleBounds;
	private Path canvasBounds;
	private Stroke stroke, activeStroke;
	private Paint paint, borderPaint, landingZonePaint, textPaint, circlePaint;
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
			touchTolerance = ABS_TOUCH_TOLERANCE,
			circleBoundsSize = ABS_CIRCLE_BOUNDS_HALF_SIZE, circleSweepAngle;

	/**
	 * Returns a string that represents the symbolic name of the specified
	 * action such as "ACTION_DOWN", "ACTION_POINTER_DOWN(3)" or an equivalent
	 * numeric constant such as "35" if unknown.
	 * 
	 * @author Google
	 * @param action
	 *            The action.
	 * @return The symbolic name of the specified action.
	 */
	public static String actionToString(int action) {
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		view = new CaliView(this);
		matrix = new Matrix();
		screenBounds = new RectF();
		setContentView(view);
		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setDither(true);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
		borderPaint = new Paint();
		borderPaint.setAntiAlias(true);
		borderPaint.setDither(true);
		borderPaint.setStrokeJoin(Paint.Join.ROUND);
		borderPaint.setStrokeCap(Paint.Cap.ROUND);
		borderPaint.setStyle(Style.STROKE);
		borderPaint.setStrokeWidth(10);
		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setDither(true);
		textPaint.setStrokeJoin(Paint.Join.ROUND);
		textPaint.setStrokeCap(Paint.Cap.ROUND);
		textPaint.setStyle(Style.STROKE);
		textPaint.setTextSize(80);
		textPaint.setColor(Color.RED);
		circlePaint = new Paint();
		circlePaint.setAntiAlias(true);
		circlePaint.setDither(true);
		circlePaint.setStrokeJoin(Paint.Join.ROUND);
		circlePaint.setStrokeCap(Paint.Cap.ROUND);
		circlePaint.setStyle(Style.STROKE);
		circlePaint.setStrokeWidth(ABS_STROKE_WIDTH * 2);
		circlePaint.setColor(Color.RED);
		canvasBounds = new Path();
		landingZonePaint = new Paint();
		landingZonePaint.setColor(Color.BLACK);
		landingZonePaint.setPathEffect(new DashPathEffect(new float[] {
				ABS_LANDING_ZONE_INTERVAL, ABS_LANDING_ZONE_INTERVAL },
				(float) 1.0));
		landingZonePaint.setStyle(Style.STROKE);
		view.createNewStroke();
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
		screenBounds.set(min.x, min.y, max.x, max.y);
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
		menu.add(0, LOG_MENU_ID, 0, "Print Log").setShortcut('5', 'l');
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case COLOR_MENU_ID:
			AmbilWarnaDialog dialog = new AmbilWarnaDialog(this,
					stroke.getColor(), new OnAmbilWarnaListener() {
						@Override
						public void onOk(AmbilWarnaDialog dialog, int color) {
							view.createNewStroke();
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
			return true;
		case LOG_MENU_ID:
			Log.d(TAG, "{{{SCRAPS}}}\n" + Scrap.SPACE_OCCUPATION_LIST);
			Log.d(TAG, "{{{STROKES}}}\n" + Stroke.SPACE_OCCUPATION_LIST);
			StringBuilder builder = new StringBuilder("{{{PARENTING}}}\n");
			String newLine = "";
			for (Scrap scrap : view.scraps) {
				builder.append(newLine);
				builder.append(scrap.getID());
				builder.append(" ");
				builder.append("parent: ");
				builder.append(scrap.getParent() == null ? "null" : scrap
						.getParent().getID());
				builder.append(" previous: ");
				builder.append(scrap.previousParent == null ? "null"
						: scrap.previousParent.getID());
				newLine = "\n";
			}
			Log.d(TAG, builder.toString());
			builder = new StringBuilder("{{{POINTS}}}\n");
			newLine = "";
			for (Stroke stroke : view.strokes) {
				builder.append(newLine);
				builder.append(stroke.getID());
				builder.append(" ");
				builder.append(stroke.listPoints());
				builder.append(" ");
				builder.append("parent: ");
				builder.append(stroke.getParent() == null ? "null" : stroke
						.getParent().getID());
				builder.append(" previous: ");
				builder.append(stroke.previousParent == null ? "null"
						: stroke.previousParent.getID());
				newLine = "\n";
			}
			builder.append(newLine);
			builder.append("{{{SCRAPS CONTENT}}}\n");
			for (Scrap scrap : view.scraps) {
				builder.append("============================\n");
				builder.append(scrap.getID());
				builder.append(Scrap.getContentToLog(scrap));
				builder.append("\n============================");
			}
			Log.d(TAG, builder.toString());
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
