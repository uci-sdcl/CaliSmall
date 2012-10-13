/**
 * CaliSmall.java Created on July 11, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import edu.uci.calismall.Scrap.Temp;

/**
 * A small version of Calico for Android devices.
 * 
 * <p>
 * This version is still a standalone app that does no communication with Calico
 * server(s).
 * 
 * @author Michele Bonazza
 */
public class CaliSmall extends Activity implements JSONSerializable<CaliSmall> {

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
                Log.d(TAG, "long press");
                if (!parent.view.hasMovedEnough()) {
                    parent.view.longPress(selected);
                    selected = null;
                }
            } else {
                if (!parent.view.hasMovedEnough()) {
                    PointF center = parent.stroke.getStartPoint();
                    if (center != null) {
                        parent.longPressCircleBounds = new RectF(center.x
                                - parent.landingZoneCircleBoundsSize, center.y
                                - parent.landingZoneCircleBoundsSize, center.x
                                + parent.landingZoneCircleBoundsSize, center.y
                                + parent.landingZoneCircleBoundsSize);
                        selected = parent.view.getClosestStroke();
                        if (selected != null) {
                            parent.view.longPressed = true;
                            parent.view.mustShowLongPressCircle = true;
                        }
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
            parent.landingZoneCircleSweepAngle = 0;
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
        private LongPressAction longPressAction;
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

        private void reset(CaliSmall c) {
            longPressAction = new LongPressAction(c);
            newStrokes = new ArrayList<Stroke>();
            newScraps = new ArrayList<Scrap>();
            scaleDetector = new ScaleGestureDetector(c, new ScaleListener());
            landingZoneCenter = new PointF();
            strokes.clear();
            scraps.clear();
            selected = null;
            previousSelection = null;
            newSelection = null;
            toBeRemoved = null;
            tempScrap = null;
            zoomingOrPanning = false;
            mustShowLandingZone = false;
            strokeAdded = false;
            mustClearCanvas = false;
            bubbleMenuShown = false;
            tempScrapCreated = false;
            redirectingToBubbleMenu = false;
            longPressed = false;
            mustShowLongPressCircle = false;
            skipEvents = false;
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
                while (wantToOpenFile) {
                    waitForFileOpen();
                }
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

        private void waitForFileOpen() {
            try {
                lock.lock();
                drawingThreadSleeping = true;
                drawingThreadWaiting.signalAll();
                fileOpened.await();
                drawingThreadSleeping = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }

        private void drawLongPressAnimation(Canvas canvas) {
            landingZoneCircleSweepAngle += ABS_CIRCLE_SWEEP_INCREMENT;
            canvas.drawArc(longPressCircleBounds, CIRCLE_SWEEP_START,
                    landingZoneCircleSweepAngle, false, longPressCirclePaint);
            if (landingZoneCircleSweepAngle >= 360) {
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
                    stroke.draw(canvas, paint);
            }
        }

        private void drawTempScrap(Canvas canvas) {
            if (tempScrap != null) {
                tempScrap.draw(this, canvas, scaleFactor);
            }
        }

        private void drawNewStroke(Canvas canvas) {
            if (!strokeAdded) {
                stroke.draw(canvas, paint);
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
            actionStart = -System.currentTimeMillis();
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
            if (stroke.addAndDrawPoint(adjusted, touchTolerance)) {
                setSelected(getSelectedScrap(stroke));
            }
        }

        private void onUp(MotionEvent event) {
            long time = actionStart + System.currentTimeMillis();
            Log.d(TAG, "time: " + time);
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
                    landingZoneCenter.set(-1, -1);
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
                            stroke.turnIntoDot();
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
            landingZoneCenter.set(-1, -1);
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
            if (adjusted == null)
                return null;
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

        private Stroke getClosestStroke() {
            List<CaliSmallElement> candidates = Stroke.SPACE_OCCUPATION_LIST
                    .findIntersectionCandidates(stroke);
            // sort elements by their size (smallest first)
            Collections.sort(candidates);
            for (CaliSmallElement candidate : candidates) {
                if (candidate.contains(stroke.getStartPoint())
                        && isCloseEnough(candidate)) {
                    return (Stroke) candidate;
                }
            }
            return null;
        }

        private boolean isCloseEnough(CaliSmallElement candidate) {
            if (candidate == null || !(candidate instanceof Stroke))
                return false;
            Stroke test = (Stroke) candidate;
            PointF first = test.getStartPoint();
            PointF last = test.getEndPoint();
            float dx = last.x - first.x;
            float dy = last.y - first.y;
            float largestSide = test.width >= test.height ? test.height
                    : test.width;
            // return (dx * dx + dy * dy) < maxStrokeDistanceForLongPress;
            return (dx * dx + dy * dy) < largestSide * largestSide;
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
            if (actionStart + System.currentTimeMillis() < LANDING_ZONE_TIME_THRESHOLD)
                return false;
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
            previousScaledCenterX = detector.getFocusX() / scaleFactor;
            previousScaledCenterY = detector.getFocusY() / scaleFactor;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            dCenterX = detector.getFocusX();
            dCenterY = detector.getFocusY();
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
            final float scaledCenterX = dCenterX / scaleFactor;
            final float scaledCenterY = dCenterY / scaleFactor;
            translateX = scaledCenterX - previousScaledCenterX;
            translateY = scaledCenterY - previousScaledCenterY;
            canvasOffsetX += translateX;
            canvasOffsetY += translateY;
            enforceBoundConstraints();
            // translate, move origin to (x,y) to center zooming
            matrix.preTranslate(translateX + dCenterX, translateY + dCenterY);
            // scale and move origin back to (0,0)
            matrix.postScale(dScaleFactor, dScaleFactor);
            matrix.preTranslate(-dCenterX, -dCenterY);
            previousScaledCenterX = scaledCenterX;
            previousScaledCenterY = scaledCenterY;
            return true;
        }

        private void enforceBoundConstraints() {
            if (canvasOffsetX > 0) {
                translateX -= canvasOffsetX;
                canvasOffsetX = 0;
            } else {
                final float minOffsetX = (1 - scaleFactor) * view.screenWidth;
                if (canvasOffsetX * scaleFactor < minOffsetX) {
                    float difference = canvasOffsetX * scaleFactor - minOffsetX;
                    canvasOffsetX -= translateX;
                    translateX -= difference;
                    canvasOffsetX += translateX;
                }
            }
            if (canvasOffsetY > 0) {
                translateY -= canvasOffsetY;
                canvasOffsetY = 0;
            } else {
                final float minOffsetY = (1 - scaleFactor) * view.screenHeight;
                if (canvasOffsetY * scaleFactor < minOffsetY) {
                    float difference = canvasOffsetY * scaleFactor - minOffsetY;
                    canvasOffsetY -= translateY;
                    translateY -= difference;
                    canvasOffsetY += translateY;
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
            longPressCirclePaint.setStrokeWidth(stroke.getStrokeWidth() * 2);
            landingZoneRadius = ABS_LANDING_ZONE_RADIUS / scaleFactor;
            minPathLengthForLandingZone = ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE
                    / scaleFactor;
            landingZonePathOffset = ABS_LANDING_ZONE_PATH_OFFSET / scaleFactor;
            touchThreshold = ABS_TOUCH_THRESHOLD / scaleFactor;
            touchTolerance = ABS_TOUCH_TOLERANCE / scaleFactor;
            landingZoneCircleBoundsSize = ABS_CIRCLE_BOUNDS_HALF_SIZE
                    / scaleFactor;
            maxStrokeDistanceForLongPress = ABS_MAX_STROKE_DISTANCE_FOR_LONG_PRESS
                    / scaleFactor;
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
     * Absolute {@link Stroke} width (to be rescaled by {@link #scaleFactor}).
     */
    public static final int ABS_STROKE_WIDTH = 3;

    /**
     * The amount of time (in milliseconds) before the long pressure animation
     * is shown.
     */
    public static final long LONG_PRESS_DURATION = 300;

    /**
     * Time in milliseconds after which the landing zone can be shown.
     */
    public static final long LANDING_ZONE_TIME_THRESHOLD = 500;

    /**
     * Time in milliseconds between two consecutive screen refreshes (i.e. two
     * consecutive calls to {@link CaliView#drawView(Canvas)}). To get the FPS
     * that this value sets, just divide 1000 by the value (so a
     * <tt>SCREEN_REFRESH_TIME</tt> of <tt>20</tt> translates to 50 FPS).
     */
    public static final long SCREEN_REFRESH_TIME = 20;

    /**
     * Absolute radius for landing zones (to be rescaled by {@link #scaleFactor}
     * ).
     */
    static final float ABS_LANDING_ZONE_RADIUS = 25;
    /**
     * The interval between dashes in landing zones (to be rescaled by
     * {@link #scaleFactor}).
     */
    static final float ABS_LANDING_ZONE_INTERVAL = 5;
    /**
     * The length that a {@link Stroke} must reach before a landing zone is
     * shown (to be rescaled by {@link #scaleFactor}).
     */
    static final float ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE = 160;
    /**
     * Where to put the center of the landing zone on a path (to be rescaled by
     * {@link #scaleFactor}).
     */
    static final float ABS_LANDING_ZONE_PATH_OFFSET = 70;
    /**
     * The length over which a Path is no longer considered as a potential tap,
     * but is viewed as a stroke instead (to be rescaled by {@link #scaleFactor}
     * ).
     */
    static final float ABS_TOUCH_THRESHOLD = 8;
    /**
     * The amount of pixels that a touch needs to cover before it is considered
     * a move action (to be rescaled by {@link #scaleFactor}).
     */
    static final float ABS_TOUCH_TOLERANCE = 2;

    /**
     * Absolute half the size of the rectangle enclosing the circle displayed on
     * long presses (to be rescaled by {@link #scaleFactor}).
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

    /**
     * Absolute distance that the first and last point in a {@link Stroke} may
     * have to be considered a valid target for long-presses scrap recognition
     * (to be rescaled by {@link #scaleFactor}). The value is squared to speed
     * up comparison with Euclidean distances.
     */
    static final float ABS_MAX_STROKE_DISTANCE_FOR_LONG_PRESS = 200 * 200;
    /**
     * The minimum zoom level that users can reach.
     */
    static final float MIN_ZOOM = 1f;
    /**
     * The maximum zoom level that users can reach
     */
    static final float MAX_ZOOM = 4f;

    /*************************************************************************
     * DISCLAIMER FOR THE READER
     * 
     * A good bunch of all the following package-protected fields should really
     * be private if we were in Javadom. As it turns out, whenever private
     * fields are accessed from within other nested classes (such as CaliView,
     * just to name one), you hit a performance slowdown of allegedly 7x (see
     * http://developer.android.com/guide/practices/performance.html for
     * reference). Since we don't care about shielding access from other classes
     * in the package that much, we can just use package-protected. It is also
     * quite good from a Javadoc point of view. Just don't abuse accessing these
     * variables from outside of this file, don't be that guy!
     ************************************************************************/

    /**
     * A lock that is shared between the drawing thread and the Event Dispatch
     * Thread used when opening files.
     */
    Lock lock = new ReentrantLock();
    /**
     * Whether a file dialog should be displayed after the user has clicked on
     * the menu.
     */
    boolean wantToOpenFile = false;
    /**
     * Whether the drawing thread has received the request for a file to be
     * opened and it set itselft to sleep.
     */
    boolean drawingThreadSleeping = false;
    /**
     * Condition that is signalled by the drawing thread just before setting
     * itself to sleep.
     */
    Condition drawingThreadWaiting = lock.newCondition();
    /**
     * Condition that is signalled by the file opening thread just after having
     * loaded a project file.
     */
    Condition fileOpened = lock.newCondition();
    /**
     * Lock used while loading files to prevent the drawing thread from
     * encountering {@link ConcurrentModificationException}'s.
     */
    Object loadingLock = new Object();
    /**
     * The bubble menu that is drawn whenever users select scraps.
     */
    BubbleMenu bubbleMenu;
    /**
     * The current view.
     */
    CaliView view;
    /**
     * A rectangle representing the screen boundaries translated to the current
     * metrics (i.e. accounting for the current zoom level).
     */
    RectF screenBounds;
    /**
     * The boundaries of the rectangle enclosing the circle that is shown on
     * press-and-hold actions.
     */
    RectF longPressCircleBounds;
    /**
     * The current stroke.
     * 
     * <p>
     * A stroke object is always available to be updated with the points that
     * the user is touching. Every time a stroke ends (i.e. a
     * {@link MotionEvent#ACTION_UP} is detected) a new empty one is created,
     * ready to be filled with new points coming from the user drawing on the
     * screen.
     */
    Stroke stroke;
    /**
     * A reference to the current stroke.
     * 
     * <p>
     * Usually the current stroke is the last in the list of strokes generated
     * thus far. Whenever new temporary scraps are created things get more
     * complicated, so a reference to the last drawing stroke (as opposed to
     * scrap border) in use is kept to simplify the code.
     */
    Stroke activeStroke;
    /**
     * The transformation matrix that is applied to the canvas, which stores all
     * zooming and panning actions.
     */
    Matrix matrix;
    /**
     * The paint object that is used to draw all strokes with.
     * 
     * <p>
     * Every {@link Stroke} stores values for how this <tt>Paint</tt> object
     * should be modified before actually drawing it, including the stroke
     * width, color and fill type. These are set in the
     * {@link Stroke#draw(Canvas, Paint)} method for every stroke to be drawn.
     */
    Paint paint;
    /**
     * The paint object that is used to draw landing zones with.
     * 
     * <p>
     * The dotted effect is obtained by constantly updating the length of the
     * segments according to the zoom level.
     */
    Paint landingZonePaint;
    /**
     * The paint object that is used to draw the circle animation whenever users
     * press-and-hold in the proximity of a stroke.
     */
    Paint longPressCirclePaint;
    /**
     * The detector that handles all multi-touch events.
     */
    ScaleGestureDetector scaleDetector;
    /**
     * The instant at which the drawing of the current stroke started (in
     * milliseconds after the Epoch).
     */
    long actionStart;
    /**
     * The current scale factor.
     * 
     * <p>
     * All quantities must be divided by this scale factor in order to be scaled
     * accordingly to the current zoom level.
     */
    float scaleFactor = 1.f;
    /**
     * The current scale factor, in display coordinates.
     * 
     * <p>
     * This is the instantaneous version of {@link #scaleFactor}, in that this
     * value is updated for every scale event, whereas {@link #scaleFactor}
     * stores the current zoom level, which is the combination of all
     * <tt>dScaleFactor</tt> applied thus far.
     */
    float dScaleFactor = 1.f;
    /**
     * The X-center of a 2-fingers gesture, in display coordinates.
     * 
     * <p>
     * Display coordinates don't take into account the current offset and zoom
     * applied to the canvas.
     */
    float dCenterX;
    /**
     * The Y-center of a 2-fingers gesture, in display coordinates.
     * 
     * <p>
     * Display coordinates don't take into account the current offset and zoom
     * applied to the canvas.
     */
    float dCenterY;
    /**
     * The X-center of the last 2-fingers gesture.
     */
    float previousScaledCenterX;
    /**
     * The Y-center of the last 2-fingers gesture.
     */
    float previousScaledCenterY;
    /**
     * The delta X between the last center of the 2-fingers gesture and the
     * current one.
     * 
     * <p>
     * This is the instantaneous version of {@link #canvasOffsetX}, in that this
     * value is updated for every panning event, whereas {@link #canvasOffsetX}
     * is the sum of all <tt>translateX</tt> applied thus far.
     */
    float translateX;
    /**
     * The delta Y between the last center of the 2-fingers gesture and the
     * current one.
     * 
     * <p>
     * This is the instantaneous version of {@link #canvasOffsetY}, in that this
     * value is updated for every panning event, whereas {@link #canvasOffsetY}
     * is the sum of all <tt>translateY</tt> applied thus far.
     */
    float translateY;
    /**
     * The X-offset currently applied to the canvas, which is a result of all
     * the panning actions executed by the user so far.
     */
    float canvasOffsetX;
    /**
     * The Y-offset currently applied to the canvas, which is a result of all
     * the panning actions executed by the user so far.
     */
    float canvasOffsetY;
    /**
     * The radius of the circle that represents the landing zone.
     */
    float landingZoneRadius = ABS_LANDING_ZONE_RADIUS;
    /**
     * The minimum length that a {@link Stroke} must reach before the landing
     * zone is displayed over it (letting that <tt>Stroke</tt> be used to create
     * a {@link Temp}). The value is squared to speed up comparison with
     * Euclidean distances.
     */
    float minPathLengthForLandingZone = ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE;
    /**
     * The offset at which the landing zone is shown, starting from the first
     * point of the {@link Stroke}.
     */
    float landingZonePathOffset = ABS_LANDING_ZONE_PATH_OFFSET;
    /**
     * The maximum size that the area enclosed within a {@link Stroke} can reach
     * while still being considered a tap.
     * 
     * <p>
     * Devices with high pixel density have a tendency to raise a lot of
     * {@link MotionEvent}'s, therefore no assumption can be made over when
     * {@link MotionEvent#ACTION_MOVE} are generated: in other words, a tap is
     * not only a sequence of {@link MotionEvent#ACTION_DOWN} followed by a
     * {@link MotionEvent#ACTION_UP} with no {@link MotionEvent#ACTION_MOVE} in
     * between, it's simply a stroke whose size falls within this threshold.
     */
    float touchThreshold = ABS_TOUCH_THRESHOLD;
    /**
     * The minimum amount of pixels between two touch events (when events are
     * closer to each other than this value only the first is considered).
     */
    float touchTolerance = ABS_TOUCH_TOLERANCE;
    /**
     * The width/height of the rectangle enclosing the landing zone circle that
     * is shown on long presses.
     */
    float landingZoneCircleBoundsSize = ABS_CIRCLE_BOUNDS_HALF_SIZE;
    /**
     * The last angle that was used to draw the landing zone circle.
     * 
     * <p>
     * This value is incremented for every draw event to draw the landing zone
     * animation.
     */
    float landingZoneCircleSweepAngle;

    /**
     * The maximum distance that the first and last point in a {@link Stroke}
     * may have to be considered a valid target for long-presses scrap
     * recognition. The value is squared to speed up comparison with Euclidean
     * distances.
     */
    float maxStrokeDistanceForLongPress = ABS_MAX_STROKE_DISTANCE_FOR_LONG_PRESS;
    private static final int COLOR_MENU_ID = Menu.FIRST;
    private static final int CLEAR_MENU_ID = Menu.FIRST + 1;
    private static final int LOG_MENU_ID = Menu.FIRST + 2;
    private static final int SAVE_MENU_ID = Menu.FIRST + 3;
    private static final int LOAD_MENU_ID = Menu.FIRST + 4;
    private static final int LOAD_NEXT_MENU_ID = Menu.FIRST + 5;
    private static final int LOAD_PREVIOUS_MENU_ID = Menu.FIRST + 6;
    private static final String FILE_EXTENSION = ".csf";
    private String[] fileList;
    private String chosenFile;
    private EditText input;
    private FilenameFilter fileNameFilter;
    private AlertDialog saveDialog, loadDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new CaliView(this);
        matrix = new Matrix();
        screenBounds = new RectF();
        setContentView(view);
        initPaintObjects();
        view.createNewStroke();
        bubbleMenu = new BubbleMenu(this);
        fileNameFilter = new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                File test = new File(dir, filename);
                return (filename.endsWith(FILE_EXTENSION) && test.canRead());
            }
        };
        final EditText input = new EditText(this);
        this.input = input;
        input.setSingleLine();
        input.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    save(input.getText());
                    saveDialog.dismiss();
                }
                return true;
            }
        });
        // @formatter:off
        saveDialog = new AlertDialog.Builder(this)
		                            .setTitle(R.string.save_dialog_title)
		                            .setMessage(R.string.save_dialog_message)
		                            .setView(input)
		                            .setPositiveButton(R.string.ok,
		                                    new DialogInterface.OnClickListener() {
		                                        public void onClick(DialogInterface dialog, int whichButton) {
		                                            save(input.getText());
		                                        }
		                            })
		                            .setNegativeButton(R.string.cancel,
		                                    new DialogInterface.OnClickListener() {
		                                        public void onClick(DialogInterface dialog, int whichButton) {
		                                            // Canceled.
		                                        }
		                            }).create();
		// @formatter:on
    }

    private void initPaintObjects() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        longPressCirclePaint = new Paint();
        longPressCirclePaint.setAntiAlias(true);
        longPressCirclePaint.setDither(true);
        longPressCirclePaint.setStrokeJoin(Paint.Join.ROUND);
        longPressCirclePaint.setStrokeCap(Paint.Cap.ROUND);
        longPressCirclePaint.setStyle(Style.STROKE);
        longPressCirclePaint.setStrokeWidth(ABS_STROKE_WIDTH * 2);
        longPressCirclePaint.setColor(Color.RED);
        landingZonePaint = new Paint();
        landingZonePaint.setColor(Color.BLACK);
        landingZonePaint.setPathEffect(new DashPathEffect(new float[] {
                ABS_LANDING_ZONE_INTERVAL, ABS_LANDING_ZONE_INTERVAL },
                (float) 1.0));
        landingZonePaint.setStyle(Style.STROKE);
    }

    private void initFileList() {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            fileList = getApplicationContext().getExternalFilesDir(null).list(
                    fileNameFilter);
        }
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
        return new PointF(x / scaleFactor - canvasOffsetX, y / scaleFactor
                - canvasOffsetY);
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
        menu.add(0, CLEAR_MENU_ID, 0, "Clear").setShortcut('5', 'x');
        menu.add(0, LOG_MENU_ID, 0, "Print Log").setShortcut('9', 'l');
        menu.add(0, SAVE_MENU_ID, 0, "Save").setShortcut('5', 's');
        menu.add(0, LOAD_MENU_ID, 0, "Open").setShortcut('2', 'o');
        menu.add(0, LOAD_NEXT_MENU_ID, 0, "Open Next").setShortcut('6', 'n');
        menu.add(0, LOAD_PREVIOUS_MENU_ID, 0, "Open Previous").setShortcut('4',
                'p');
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    private void printLog() {
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
    }

    private void save(final Editable input) {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            try {
                String json = toJSON().toString();
                String fileName = input.toString();
                chosenFile = fileName;
                FileWriter writer = new FileWriter(new File(
                        getApplicationContext().getExternalFilesDir(null),
                        fileName.endsWith(FILE_EXTENSION) ? fileName : fileName
                                + FILE_EXTENSION));
                writer.write(json);
                writer.flush();
                writer.close();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.save_dialog_fail_title)
                    .setMessage(R.string.save_dialog_fail_message)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    saveInternal(input.toString());
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    Toast.makeText(
                                            getApplicationContext(),
                                            R.string.save_dialog_file_not_saved,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }).create();
        }
    }

    private void load(String file) {
        try {
            chosenFile = file;
            input.setText(file.endsWith(FILE_EXTENSION) ? file.substring(0,
                    file.lastIndexOf(FILE_EXTENSION)) : file);
            BufferedReader reader = new BufferedReader(new FileReader(new File(
                    getApplicationContext().getExternalFilesDir(null), file)));
            String in, newLine = "";
            StringBuilder builder = new StringBuilder();
            while ((in = reader.readLine()) != null) {
                builder.append(newLine);
                builder.append(in);
                newLine = "\n";
            }
            JSONObject json = new JSONObject(builder.toString());
            syncAndLoad(json);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveInternal(String fileName) {

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
            printLog();
            return true;
        case SAVE_MENU_ID:
            saveDialog.show();
            return true;
        case LOAD_MENU_ID:
            showLoadDialog();
            return true;
        case LOAD_NEXT_MENU_ID:
            loadNext();
            return true;
        case LOAD_PREVIOUS_MENU_ID:
            loadPrevious();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLoadDialog() {
        initFileList();
        loadDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.load_dialog_message)
                .setItems(fileList, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        load(fileList[which]);
                    }
                }).create();
        loadDialog.show();
    }

    private void loadNext() {
        initFileList();
        if (fileList != null && fileList.length > 0) {
            int fileIndex = Arrays.binarySearch(fileList, chosenFile);
            fileIndex++;
            fileIndex = fileIndex % fileList.length;
            if (fileIndex < 0)
                fileIndex += fileList.length;
            load(fileList[fileIndex]);
        }
    }

    private void loadPrevious() {
        initFileList();
        if (fileList != null && fileList.length > 0) {
            int fileIndex = Arrays.binarySearch(fileList, chosenFile);
            fileIndex--;
            fileIndex = fileIndex % fileList.length;
            if (fileIndex < 0)
                fileIndex += fileList.length;
            load(fileList[fileIndex]);
        }
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

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#toJSON()
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        List<JSONObject> strokes = new ArrayList<JSONObject>(
                view.strokes.size());
        for (Stroke stroke : view.strokes) {
            if (!stroke.isEmpty())
                strokes.add(stroke.toJSON());
        }
        List<JSONObject> scraps = new ArrayList<JSONObject>(view.scraps.size());
        for (Scrap scrap : view.scraps) {
            scraps.add(scrap.toJSON());
        }
        json.put("strokes", new JSONArray(strokes));
        json.put("scraps", new JSONArray(scraps));
        return json;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#fromJSON(org.json.JSONObject)
     */
    @Override
    public CaliSmall fromJSON(JSONObject jsonData) throws JSONException {
        matrix = new Matrix();
        initPaintObjects();
        view.reset(this);
        Stroke.SPACE_OCCUPATION_LIST.clear();
        Scrap.SPACE_OCCUPATION_LIST.clear();
        JSONArray array = jsonData.getJSONArray("strokes");
        for (int i = 0; i < array.length(); i++) {
            Stroke stroke = new Stroke();
            stroke.fromJSON(array.getJSONObject(i));
            view.strokes.add(stroke);
        }
        Stroke.SPACE_OCCUPATION_LIST.addAll(view.strokes);
        array = jsonData.getJSONArray("scraps");
        for (int i = 0; i < array.length(); i++) {
            Scrap scrap = new Scrap();
            scrap.fromJSON(array.getJSONObject(i));
            view.scraps.add(scrap);
        }
        Scrap.SPACE_OCCUPATION_LIST.addAll(view.scraps);
        for (Scrap scrap : view.scraps) {
            scrap.addChildrenFromJSON();
        }
        view.createNewStroke();
        return this;
    }

    private void syncAndLoad(JSONObject jsonData) throws JSONException {
        try {
            lock.lock();
            wantToOpenFile = true;
            while (!drawingThreadSleeping)
                drawingThreadWaiting.await(SCREEN_REFRESH_TIME,
                        TimeUnit.MILLISECONDS);
            fromJSON(jsonData);
            wantToOpenFile = false;
            fileOpened.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

}
