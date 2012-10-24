/**
 * CaliSmall.java Created on July 11, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
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
                        view.drawView(canvas, false);
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
                    if (center != null) {
                        parent.longPressCircleBounds = new RectF(center.x
                                - parent.landingZoneCircleBoundsSize, center.y
                                - parent.landingZoneCircleBoundsSize, center.x
                                + parent.landingZoneCircleBoundsSize, center.y
                                + parent.landingZoneCircleBoundsSize);
                        parent.stroke.setBoundaries();
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

    private final class ProgressBar extends AsyncTask<InputStream, Void, Void> {

        private final CaliSmall parent;
        private ProgressDialog dialog;
        private InputStream toBeLoaded;

        private ProgressBar(CaliSmall parent) {
            this.parent = parent;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(InputStream... params) {
            toBeLoaded = params[0];
            load(toBeLoaded);
            restartAutoSaving();
            return null;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            if (loadDialog != null) {
                loadDialog.hide();
                loadDialog.dismiss();
            }
            Resources res = getResources();
            dialog = ProgressDialog.show(parent,
                    res.getString(R.string.load_dialog_progress),
                    res.getString(R.string.load_dialog_progress_message), true);
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Void result) {
            dialog.dismiss();
            setTitle(String.format("CaliSmall - (%d/%d) %s",
                    currentFileListIndex + 1, fileList.size(),
                    parent.chosenFile));
        }

    }

    private final class AutoSaveTask extends TimerTask {

        private final boolean saveBackupFiles;
        private final CaliSmall parent;
        private long lastRun;

        private AutoSaveTask(CaliSmall parent, boolean saveBackupFiles) {
            this.saveBackupFiles = saveBackupFiles;
            this.parent = parent;
            lastRun = System.currentTimeMillis();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.TimerTask#run()
         */
        @Override
        public void run() {
            if (saveBackupFiles) {
                File file = new File(parent.getApplicationContext()
                        .getExternalFilesDir(null), chosenFile + FILE_EXTENSION);
                if (file.lastModified() > lastRun) {
                    // time to save a new backup!
                    save("~" + chosenFile);
                }
                lastRun = System.currentTimeMillis();
            } else {
                if (parent.view.didSomething) {
                    save(chosenFile);
                    // FIXME this is a very lame replacement for a proper
                    // history
                    parent.view.didSomething = false;
                }
            }
        }
    }

    /**
     * The {@link SurfaceView} on which the canvas is drawn.
     * 
     * <p>
     * Drawing takes place within the {@link CaliView#drawView(Canvas, boolean)}
     * method, which is called roughly every
     * {@link CaliSmall#SCREEN_REFRESH_TIME} milliseconds by the <tt>Worker</tt>
     * thread that is spawn by {@link CaliView#surfaceCreated(SurfaceHolder)}
     * (which in turn is called by the Android Runtime when the app is moved to
     * the foreground).
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
        // a list of strokes kept in chronological order (oldest first)
        private final List<Stroke> strokes;
        // a list of scraps kept in chronological order (oldest first)
        private final List<Scrap> scraps;
        private final Handler longPressListener = new Handler();
        private LongPressAction longPressAction;
        private Thread worker;
        private Scrap selected, previousSelection, newSelection, toBeRemoved,
                tempScrap;
        private final List<Stroke> newStrokes;
        private final List<Stroke> ghosts;
        private final List<Scrap> newScraps;
        private final ScaleListener scaleListener;
        private Stroke latestGhost;
        private PathMeasure pathMeasure;
        private boolean zoomingOrPanning, running, mustShowLandingZone,
                strokeAdded, mustClearCanvas, bubbleMenuShown,
                mustShowBubbleMenu, tempScrapCreated, redirectingToBubbleMenu,
                longPressed, mustShowLongPressCircle, skipEvents, didSomething;
        private PointF landingZoneCenter;
        private Stroke redirectedGhost;
        private int mActivePointerId = INVALID_POINTER_ID, screenWidth,
                screenHeight;

        private CaliView(CaliSmall c) {
            super(c);
            longPressAction = new LongPressAction(c);
            strokes = new ArrayList<Stroke>();
            scraps = new ArrayList<Scrap>();
            newStrokes = new ArrayList<Stroke>();
            ghosts = new ArrayList<Stroke>();
            newScraps = new ArrayList<Scrap>();
            scaleListener = new ScaleListener();
            scaleDetector = new ScaleGestureDetector(c, scaleListener);
            reset(c);
            getHolder().addCallback(this);
        }

        private void reset(CaliSmall c) {
            longPressAction = new LongPressAction(c);
            newStrokes.clear();
            newScraps.clear();
            ghosts.clear();
            pathMeasure = new PathMeasure();
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
         * @param dontWait
         *            must be <code>true</code> if the thread that calls this
         *            method shouldn't wait for files to be opened (i.e. only
         *            when it is called by a method that is saving the canvas
         *            content)
         */
        public void drawView(Canvas canvas, boolean dontWait) {
            if (canvas != null) {
                while (wantToOpenFile && !dontWait) {
                    waitForFileOpen();
                }
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
                    deleteElements();
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
                    landingZoneCircleSweepAngle, false, LONG_PRESS_CIRCLE_PAINT);
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
                    stroke.draw(canvas, PAINT);
            }
        }

        private void drawTempScrap(Canvas canvas) {
            if (tempScrap != null) {
                tempScrap.draw(this, canvas, scaleFactor);
            }
        }

        private void drawNewStroke(Canvas canvas) {
            if (!strokeAdded) {
                stroke.draw(canvas, PAINT);
                strokes.add(stroke);
                Stroke.SPACE_OCCUPATION_LIST.add(stroke);
                strokeAdded = true;
            }
        }

        private void maybeDrawLandingZone(Canvas canvas) {
            if (mustShowLandingZone) {
                canvas.drawCircle(landingZoneCenter.x, landingZoneCenter.y,
                        landingZoneRadius, LANDING_ZONE_PAINT);
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

        private void deleteElements() {
            if (toBeRemoved != null) {
                toBeRemoved.erase();
                mustShowBubbleMenu = false;
                toBeRemoved = null;
            }
            CaliSmallElement.deleteMarkedFromList(strokes,
                    Stroke.SPACE_OCCUPATION_LIST);
            CaliSmallElement.deleteMarkedFromList(scraps,
                    Scrap.SPACE_OCCUPATION_LIST);
            for (Iterator<Stroke> iterator = ghosts.iterator(); iterator
                    .hasNext();) {
                Stroke next = iterator.next();
                if (next.hasToBeDeleted() || !next.isGhost()) {
                    iterator.remove();
                }
            }
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
            if (this.selected != null && selected != this.selected) {
                Stroke outerBorder = this.selected.deselect();
                if (outerBorder != null) {
                    outerBorder.setGhost(true, screenBounds, getResources(),
                            bubbleMenu.getButtonSize());
                    newStrokes.add(outerBorder);
                    ghosts.add(outerBorder);
                }
            }
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

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int action = event.getAction() & MotionEvent.ACTION_MASK;
            // Log.d(TAG, actionToString(action));
            if (zoomingOrPanning) {
                handleZoomingPanningEvent(event, action);
            } else {
                if (redirectedGhost != null) {
                    skipEvents = true;
                } else if (redirectingToBubbleMenu) {
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
            if (!didSomething)
                didSomething = true;
            actionStart = -System.currentTimeMillis();
            PointF adjusted = adjustForZoom(event.getX(), event.getY());
            mustShowLandingZone = false;
            mActivePointerId = event.findPointerIndex(0);
            longPressed = false;
            for (Stroke ghost : ghosts) {
                Stroke touched = ghost.ghostButtonTouched(adjusted);
                if (touched != null) {
                    redirectedGhost = ghost;
                    return;
                }
            }
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
            int pointerIndex = event.findPointerIndex(mActivePointerId);
            if (pointerIndex == -1)
                pointerIndex = 0;
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
            mustShowLandingZone = false;
            longPressListener.removeCallbacks(longPressAction);
            skipEvents = false;
            if (!zoomingOrPanning) {
                int pointerIndex = event.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0)
                    pointerIndex = 0;
                mActivePointerId = INVALID_POINTER_ID;
                if (redirectedGhost != null) {
                    redirectedGhost.setGhost(false, null, null, 0f);
                    redirectedGhost = null;
                    return;
                }
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
            if (!ghosts.isEmpty()) {
                latestGhost = ghosts.remove(ghosts.size() - 1);
                latestGhost.setGhost(false, null, null, 0f);
                latestGhost.toBeDeleted = true;
            }
            bubbleMenuShown = false;
            mustShowLandingZone = false;
            stroke.reset();
            redirectingToBubbleMenu = false;
            scaleDetector.onTouchEvent(event);
            zoomingOrPanning = true;
        }

        private boolean isInLandingZone(PointF lastPoint) {
            return Math.pow(lastPoint.x - landingZoneCenter.x, 2)
                    + Math.pow(lastPoint.y - landingZoneCenter.y, 2) <= Math
                        .pow(landingZoneRadius, 2);
        }

        private boolean hasMovedEnough() {
            return stroke.getRectSize() > touchThreshold;
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
            setBounds(width, height);
        }

        private void setBounds(int width, int height) {
            screenWidth = width;
            screenHeight = height;
            updateBounds();
            if (selected != null) {
                bubbleMenu.setBounds(selected.getBorder(), scaleFactor,
                        screenBounds);
            } else {
                bubbleMenu.setBounds(scaleFactor, screenBounds);
                scaledLandingZoneAbsRadius = Math.max(width, height)
                        * LANDING_ZONE_RADIUS_TO_WIDTH_RATIO;
                landingZoneRadius = scaledLandingZoneAbsRadius / scaleFactor;
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
            LONG_PRESS_CIRCLE_PAINT.setStrokeWidth(stroke.getStrokeWidth() * 2);
            // landingZoneRadius = ABS_LANDING_ZONE_RADIUS / scaleFactor;
            landingZoneRadius = scaledLandingZoneAbsRadius / scaleFactor;
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
            LANDING_ZONE_PAINT.setPathEffect(new DashPathEffect(new float[] {
                    newInterval, newInterval }, (float) 1.0));
            updateBounds();
            if (view.latestGhost != null)
                view.latestGhost.toBeDeleted = false;
            view.setSelected(view.previousSelection);
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
     * consecutive calls to {@link CaliView#drawView(Canvas, boolean)}). To get
     * the FPS that this value sets, just divide 1000 by the value (so a
     * <tt>SCREEN_REFRESH_TIME</tt> of <tt>20</tt> translates to 50 FPS).
     */
    public static final long SCREEN_REFRESH_TIME = 20;
    /**
     * Absolute radius for landing zones (to be rescaled by {@link #scaleFactor}
     * ).
     */
    static final float ABS_LANDING_ZONE_RADIUS = 25;

    /**
     * The ratio used to make the landing zone the same physical size regardless
     * of the device that is currently being used.
     */
    static final float LANDING_ZONE_RADIUS_TO_WIDTH_RATIO = 22f / 1280;
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
    /**
     * The paint object that is used to draw all strokes with.
     * 
     * <p>
     * Every {@link Stroke} stores values for how this <tt>Paint</tt> object
     * should be modified before actually drawing it, including the stroke
     * width, color and fill type. These are set in the
     * {@link Stroke#draw(Canvas, Paint)} method for every stroke to be drawn.
     */
    static final Paint PAINT = new Paint();
    /**
     * The paint object that is used to draw landing zones with.
     * 
     * <p>
     * The dotted effect is obtained by constantly updating the length of the
     * segments according to the zoom level.
     */
    static final Paint LANDING_ZONE_PAINT = new Paint();
    /**
     * The paint object that is used to draw the circle animation whenever users
     * press-and-hold in the proximity of a stroke.
     */
    static final Paint LONG_PRESS_CIRCLE_PAINT = new Paint();

    private static final String FILE_EXTENSION = ".csf";
    private static final String LIST_FILE_NAME = ".file_list";
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
    private static final long AUTO_SAVE_TIME = 20 * 1000,
            AUTO_BACKUP_TIME = 3 * 60 * 1000;
    private static final long MIN_FILE_SIZE_FOR_PROGRESSBAR = 100 * 1024;

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
     * loaded a sketch file.
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
    float scaleFactor;
    /**
     * The current scale factor, in display coordinates.
     * 
     * <p>
     * This is the instantaneous version of {@link #scaleFactor}, in that this
     * value is updated for every scale event, whereas {@link #scaleFactor}
     * stores the current zoom level, which is the combination of all
     * <tt>dScaleFactor</tt> applied thus far.
     */
    float dScaleFactor;
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
    float landingZoneRadius;

    /**
     * The abstract radius (to be rescaled by {@link #scaleFactor}) of landing
     * zones for this device, meaning that it's rescaled to look the same
     * physical size across devices.
     */
    float scaledLandingZoneAbsRadius = ABS_LANDING_ZONE_RADIUS;
    /**
     * The minimum length that a {@link Stroke} must reach before the landing
     * zone is displayed over it (letting that <tt>Stroke</tt> be used to create
     * a {@link Temp}). The value is squared to speed up comparison with
     * Euclidean distances.
     */
    float minPathLengthForLandingZone;
    /**
     * The offset at which the landing zone is shown, starting from the first
     * point of the {@link Stroke}.
     */
    float landingZonePathOffset;
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
    float touchThreshold;
    /**
     * The minimum amount of pixels between two touch events (when events are
     * closer to each other than this value only the first is considered).
     */
    float touchTolerance;
    /**
     * The width/height of the rectangle enclosing the landing zone circle that
     * is shown on long presses.
     */
    float landingZoneCircleBoundsSize;
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
    float maxStrokeDistanceForLongPress;
    private List<String> fileList = new ArrayList<String>();
    private String chosenFile, autoSaveName, tmpSnapshotName;
    private int currentFileListIndex = -1;
    private EditText input;
    private AlertDialog saveDialog, loadDialog, deleteDialog;
    private TimerTask autoSaver, autoBackupSaver;
    private Timer autoSaverTimer;
    private boolean userPickedANewName;

    static {
        PAINT.setAntiAlias(true);
        PAINT.setDither(true);
        PAINT.setStrokeJoin(Paint.Join.ROUND);
        PAINT.setStrokeCap(Paint.Cap.ROUND);
        LONG_PRESS_CIRCLE_PAINT.setAntiAlias(true);
        LONG_PRESS_CIRCLE_PAINT.setDither(true);
        LONG_PRESS_CIRCLE_PAINT.setStrokeJoin(Paint.Join.ROUND);
        LONG_PRESS_CIRCLE_PAINT.setStrokeCap(Paint.Cap.ROUND);
        LONG_PRESS_CIRCLE_PAINT.setStyle(Style.STROKE);
        LONG_PRESS_CIRCLE_PAINT.setStrokeWidth(ABS_STROKE_WIDTH * 2);
        LONG_PRESS_CIRCLE_PAINT.setColor(Color.RED);
        LANDING_ZONE_PAINT.setColor(Color.BLACK);
        LANDING_ZONE_PAINT.setPathEffect(new DashPathEffect(new float[] {
                ABS_LANDING_ZONE_INTERVAL, ABS_LANDING_ZONE_INTERVAL },
                (float) 1.0));
        LANDING_ZONE_PAINT.setStyle(Style.STROKE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new CaliView(this);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        bubbleMenu = new BubbleMenu(this);
        reset();
        setContentView(view);
        autoSaveName = getResources().getString(R.string.unnamed_files);
        final EditText input = new EditText(this);
        this.input = input;
        input.setSingleLine();
        input.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String name = input.getText().toString();
                    userPickedANewName = !name.startsWith(autoSaveName)
                            && !name.startsWith("~" + autoSaveName);
                    save(name);
                    setTitle(String.format("CaliSmall - (%d/%d) %s",
                            currentFileListIndex + 1, fileList.size(),
                            chosenFile));
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
		                            .setPositiveButton(android.R.string.ok,
		                                    new DialogInterface.OnClickListener() {
		                                        public void onClick(DialogInterface dialog, int whichButton) {
		                                            String name = input.getText().toString();
//		                                            userPickedANewName = !name.startsWith(autoSaveName)
//		                                                    && !name.startsWith("~" + autoSaveName);
		                                            userPickedANewName = !name.equals(chosenFile);
		                                            save(name);
		                                            setTitle(String.format("CaliSmall - (%d/%d) %s",
		                                                    currentFileListIndex + 1, fileList.size(), chosenFile));
		                                            Toast.makeText(
		                                                    getApplicationContext(),
		                                                    String.format(
		                                                            getResources().getString(R.string.file_saved),
		                                                            chosenFile), Toast.LENGTH_SHORT).show();
		                                        }
		                            })
		                            .setNegativeButton(android.R.string.cancel,
		                                    new DialogInterface.OnClickListener() {
		                                        public void onClick(DialogInterface dialog, int whichButton) {
		                                            // Canceled.
		                                        }
		                            }).create();
        deleteDialog = new AlertDialog.Builder(this)
        .setTitle(R.string.delete_dialog_title)
        .setPositiveButton(android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        delete();
                    }
        })
        .setNegativeButton(android.R.string.no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
        }).create();
		// @formatter:on
        final Intent intent = getIntent();
        if (intent != null) {
            final android.net.Uri data = intent.getData();
            if (data != null) {
                if (chosenFile != null)
                    save(chosenFile);
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            /**
                             * The load call must be delayed because if the app
                             * was started through an intent the drawing thread
                             * is not started until this method ends, causing a
                             * deadlock on the load function
                             */
                            loadAndMaybeShowProgressBar(getContentResolver()
                                    .openInputStream(data));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }, SCREEN_REFRESH_TIME * 2);
            }
        }
        if (chosenFile == null) {
            newSketch();
        }
    }

    private void restartAutoSaving() {
        if (autoSaverTimer != null) {
            autoSaverTimer.cancel();
        }
        autoSaverTimer = new Timer();
        autoSaver = new AutoSaveTask(this, false);
        autoBackupSaver = new AutoSaveTask(this, true);
        autoSaverTimer.scheduleAtFixedRate(autoSaver, AUTO_SAVE_TIME,
                AUTO_SAVE_TIME);
        autoSaverTimer.schedule(autoBackupSaver, AUTO_BACKUP_TIME,
                AUTO_BACKUP_TIME);
    }

    private List<String> initFileList() {
        List<String> files = new ArrayList<String>();
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            File listFileProject = new File(getApplicationContext()
                    .getExternalFilesDir(null), LIST_FILE_NAME);
            try {
                if (!listFileProject.exists())
                    listFileProject.createNewFile();
                BufferedReader reader = new BufferedReader(new FileReader(
                        listFileProject));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    String fileName = line.endsWith(FILE_EXTENSION) ? line
                            .substring(0, line.lastIndexOf(FILE_EXTENSION))
                            : line;
                    files.add(fileName);
                }
            } catch (FileNotFoundException e) {
                try {
                    listFileProject.createNewFile();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return files;
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
        Log.d(TAG, "file list: " + fileList.toString());
    }

    private void save(final String input) {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            try {
                lock.lock();
                File path = getApplicationContext().getExternalFilesDir(null);
                File newFile = new File(path, input + FILE_EXTENSION);
                if (userPickedANewName) {
                    // delete the "Unnamed Sketch" file
                    // if (chosenFile.startsWith(autoSaveName)) {
                    new File(path, chosenFile + FILE_EXTENSION).delete();
                    new File(path, "~" + chosenFile + FILE_EXTENSION).delete();
                    fileList.remove(currentFileListIndex);
                    // }
                    chosenFile = input;
                    fileList.add(currentFileListIndex, input);
                    userPickedANewName = false;
                    restartAutoSaving();
                }
                updateFileList();
                String json = toJSON().toString();
                FileWriter writer = new FileWriter(newFile);
                writer.write(json);
                writer.flush();
                writer.close();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.save_dialog_fail_title)
                    .setMessage(R.string.save_dialog_fail_message)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    saveInternal(input);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
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

    private void updateFileList() {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            File listFileProject = new File(getApplicationContext()
                    .getExternalFilesDir(null), LIST_FILE_NAME);
            try {
                PrintStream ps = new PrintStream(listFileProject);
                for (String file : fileList) {
                    ps.println(file);
                }
                ps.flush();
                ps.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void load(File toBeLoaded) {
        try {
            load(new FileInputStream(toBeLoaded));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load(InputStream toBeLoaded) {
        try {
            runOnUiThread(new Runnable() {
                public void run() {
                    invalidateOptionsMenu();
                }
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    toBeLoaded));
            String in, newLine = "";
            StringBuilder builder = new StringBuilder();
            while ((in = reader.readLine()) != null) {
                builder.append(newLine);
                builder.append(in);
                newLine = "\n";
            }
            reader.close();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.activity_cali_small, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.color:
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
        case R.id.log:
            printLog();
            return true;
        case R.id.save:
            saveButtonClicked();
            return true;
        case R.id.save_as:
            if (chosenFile.startsWith(autoSaveName)) {
                input.setText("");
            }
            saveDialog.show();
            return true;
        case R.id.open:
            showLoadDialog();
            return true;
        case R.id.open_next:
            loadNext();
            return true;
        case R.id.open_previous:
            loadPrevious();
            return true;
        case R.id.create_new:
            newSketch();
            return true;
        case R.id.share:
            share();
            return true;
        case R.id.share_snapshot:
            shareSnapshot();
            return true;
        case R.id.delete:
            deleteDialog.setMessage(String.format(
                    getResources().getString(R.string.delete_dialog_message),
                    chosenFile));
            deleteDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem previous = menu.findItem(R.id.open_previous);
        previous.setEnabled(currentFileListIndex > 0);
        previous.getIcon().setAlpha(currentFileListIndex > 0 ? 255 : 85);
        MenuItem next = menu.findItem(R.id.open_next);
        next.setEnabled(currentFileListIndex < fileList.size() - 1);
        Drawable nextIcon = next.getIcon();
        if (nextIcon != null)
            nextIcon.setAlpha(currentFileListIndex < fileList.size() - 1 ? 255
                    : 85);
        return super.onPrepareOptionsMenu(menu);
    }

    private void newSketch() {
        userPickedANewName = false;
        if (chosenFile != null)
            save(chosenFile);
        chosenFile = generateAutoSaveName();
        save(chosenFile);
        fileList.add(++currentFileListIndex, chosenFile);
        invalidateOptionsMenu();
        view.mustClearCanvas = true;
        input.setText("");
        setTitle(String.format("CaliSmall - (%d/%d) %s",
                currentFileListIndex + 1, fileList.size(), getResources()
                        .getString(R.string.unnamed_files)));
    }

    private void delete() {
        String fileName = chosenFile;
        File path = getApplicationContext().getExternalFilesDir(null);
        File newFile = new File(path, fileName + FILE_EXTENSION);
        if (newFile.exists()) {
            newFile.delete();
            newFile = new File(path, "~" + fileName + FILE_EXTENSION);
            if (newFile.exists())
                newFile.delete();
            fileList.remove(currentFileListIndex);
            updateFileList();
        }
        fileList = initFileList();
        if (fileList.isEmpty()) {
            currentFileListIndex--;
            newSketch();
        } else {
            loadPrevious();
        }
        Toast.makeText(
                getApplicationContext(),
                String.format(getResources().getString(R.string.delete_done),
                        fileName), Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    private void saveButtonClicked() {
        if (chosenFile.startsWith(autoSaveName)) {
            input.setText("");
            saveDialog.show();
        } else {
            restartAutoSaving();
            save(chosenFile);
            Toast.makeText(
                    getApplicationContext(),
                    String.format(
                            getResources().getString(R.string.file_saved),
                            chosenFile), Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        save(chosenFile);
        File path = getApplicationContext().getExternalFilesDir(null);
        File newFile = new File(path, chosenFile + FILE_EXTENSION);
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(newFile));
        intent.putExtra(Intent.EXTRA_SUBJECT, R.string.share_message_subject);
        intent.putExtra(Intent.EXTRA_TEXT,
                getResources().getString(R.string.share_message_text));
        startActivity(Intent.createChooser(intent,
                getResources().getString(R.string.share_message)));
    }

    private void shareSnapshot() {
        File path = getApplicationContext().getExternalFilesDir(null);
        File tmpImage = new File(path, chosenFile + ".png");
        tmpSnapshotName = chosenFile + ".png";
        FileOutputStream tmp;
        try {
            tmp = new FileOutputStream(tmpImage);
            lock.lock();
            wantToOpenFile = true;
            while (!drawingThreadSleeping)
                drawingThreadWaiting.await(SCREEN_REFRESH_TIME,
                        TimeUnit.MILLISECONDS);
            Bitmap bitmap = Bitmap.createBitmap(view.screenWidth,
                    view.screenHeight, Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            wantToOpenFile = false;
            view.drawView(canvas, true);
            fileOpened.signalAll();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, tmp);
            tmp.flush();
            tmp.close();
            Intent intent = new Intent(android.content.Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tmpImage));
            intent.putExtra(Intent.EXTRA_SUBJECT,
                    R.string.share_message_subject);
            intent.putExtra(Intent.EXTRA_TEXT,
                    getResources().getString(R.string.share_message_text));
            startActivityForResult(
                    Intent.createChooser(intent,
                            getResources().getString(R.string.share_message)),
                    1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onActivityResult(int, int,
     * android.content.Intent)
     */
    @Override
    protected void
            onActivityResult(int requestCode, int resultCode, Intent data) {
        File path = getApplicationContext().getExternalFilesDir(null);
        File tmpImage = new File(path, tmpSnapshotName);
        if (tmpImage.exists())
            tmpImage.delete();
    }

    private String generateAutoSaveName() {
        return autoSaveName + " created on "
                + DATE_FORMATTER.format(new Date());
    }

    private void showLoadDialog() {
        loadDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.load_dialog_message)
                .setItems(fileList.toArray(new String[fileList.size()]),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                currentFileListIndex = which;
                                loadAndMaybeShowProgressBar(fileList.get(which));
                            }
                        }).create();
        loadDialog.show();
    }

    private void loadAndMaybeShowProgressBar(String file) {
        save(chosenFile);
        chosenFile = file;
        input.setText(chosenFile);
        input.setSelection(chosenFile.length());
        final File toBeLoaded = new File(getApplicationContext()
                .getExternalFilesDir(null), file + FILE_EXTENSION);
        if (toBeLoaded.length() > MIN_FILE_SIZE_FOR_PROGRESSBAR) {
            try {
                new ProgressBar(this).execute(new FileInputStream(toBeLoaded));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            load(toBeLoaded);
            String fileName = toBeLoaded.getName();
            if (fileName.endsWith(FILE_EXTENSION)) {
                fileName = fileName.substring(0,
                        fileName.lastIndexOf(FILE_EXTENSION));
            }
            setTitle(String.format("CaliSmall - (%d/%d) %s",
                    currentFileListIndex + 1, fileList.size(), fileName));
            restartAutoSaving();
        }
    }

    private void loadAndMaybeShowProgressBar(InputStream input) {
        view.didSomething = false;
        new ProgressBar(this).execute(input);
    }

    private void loadNext() {
        userPickedANewName = false;
        currentFileListIndex = currentFileListIndex == fileList.size() - 1 ? currentFileListIndex
                : ++currentFileListIndex;
        loadAndMaybeShowProgressBar(fileList.get(currentFileListIndex));
    }

    private void loadPrevious() {
        userPickedANewName = false;
        currentFileListIndex = currentFileListIndex > 0 ? --currentFileListIndex
                : currentFileListIndex;
        loadAndMaybeShowProgressBar(fileList.get(currentFileListIndex));
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
        for (int i = 0; i < view.strokes.size(); i++) {
            Stroke stroke = view.strokes.get(i);
            if (!stroke.isEmpty())
                strokes.add(stroke.toJSON());
        }
        List<JSONObject> scraps = new ArrayList<JSONObject>(view.scraps.size());
        for (int i = 0; i < view.scraps.size(); i++) {
            scraps.add(view.scraps.get(i).toJSON());
        }
        json.put("strokes", new JSONArray(strokes));
        json.put("scraps", new JSONArray(scraps));
        return json;
    }

    private void reset() {
        matrix = new Matrix();
        Stroke.SPACE_OCCUPATION_LIST.clear();
        Scrap.SPACE_OCCUPATION_LIST.clear();
        screenBounds = new RectF();
        longPressCircleBounds = new RectF();
        activeStroke = null;
        actionStart = 0;
        scaleFactor = 1.f;
        dScaleFactor = 1.f;
        dCenterX = 0;
        dCenterY = 0;
        previousScaledCenterX = 0;
        previousScaledCenterY = 0;
        translateX = 0;
        translateY = 0;
        canvasOffsetX = 0;
        canvasOffsetY = 0;
        landingZoneCircleSweepAngle = 0;
        int height = view.screenHeight;
        int width = view.screenWidth;
        view.reset(this);
        view.setBounds(width, height);
        stroke = null;
        view.createNewStroke();
        view.scaleListener.onScaleEnd(scaleDetector);
        fileList = initFileList();
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#fromJSON(org.json.JSONObject)
     */
    @Override
    public CaliSmall fromJSON(JSONObject jsonData) throws JSONException {
        reset();
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
