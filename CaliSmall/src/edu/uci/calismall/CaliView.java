/**
 * CaliView.java Created on Oct 19, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener;
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
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import edu.uci.calismall.Scrap.Temp;

/**
 * The {@link SurfaceView} on which the canvas is drawn.
 * 
 * <p>
 * Drawing takes place within the {@link CaliView#drawView(Canvas)} method,
 * which is called roughly every {@link Painter#SCREEN_REFRESH_TIME}
 * milliseconds by the <tt>Worker</tt> thread that is spawn by
 * {@link CaliView#surfaceCreated(SurfaceHolder)} (which in turn is called by
 * the Android Runtime when the app is moved to the foreground).
 * 
 * <p>
 * All data structures accessed by the drawing thread are only edited by the
 * drawing thread itself to prevent conflicts (and therefore locking). When
 * objects must be added to/removed from the view, they must be put to other
 * data structures whose only purpose is to communicate with the drawing thread.
 * 
 * <p>
 * Input (touch) events are handles by the
 * {@link CaliView#onTouchEvent(MotionEvent)} method, which is called by the
 * Android Event Dispatcher Thread (or whatever they call it for Android, it's
 * the equivalente of Java's good ol' EDT).
 * 
 * <p>
 * To simplify code, and to avoid conflicts, all drawing operations are
 * performed by the drawing thread (no <tt>postXYZ()</tt> calls), and all object
 * creation/editing and border inclusion/intersection computation is done by the
 * EDT.
 * 
 * @author Michele Bonazza
 */
public class CaliView extends SurfaceView implements SurfaceHolder.Callback,
        JSONSerializable<CaliView> {
    private static final int INVALID_POINTER_ID = -1;
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
    /**
     * The current instance of the bubble menu.
     */
    BubbleMenu bubbleMenu;

    // a list of strokes kept in chronological order (oldest first)
    private final List<Stroke> strokes;
    // a list of scraps kept in chronological order (oldest first)
    private final List<Scrap> scraps;
    /**
     * A list containing all created scraps sorted by their position in the
     * canvas.
     */
    private final SpaceOccupationList<Stroke> allStrokes;
    /**
     * A list containing all created strokes sorted by their position in the
     * canvas.
     */
    private final SpaceOccupationList<Scrap> allScraps;
    private final Handler longPressListener = new Handler();
    private final CaliSmall parent;
    private final List<Stroke> newStrokes;
    private final List<Scrap> newScraps;
    private LongPressAction longPressAction;
    private Thread worker;
    private Painter painter;
    private Scrap selected, previousSelection, newSelection, toBeRemoved,
            tempScrap;
    private List<TouchHandler> handlers;
    private ScaleListener scaleListener;
    private TouchHandler redirectTo;
    private GhostStrokeHandler ghostHandler;
    private DrawingHandler drawingHandler;
    private PathMeasure pathMeasure;
    private boolean running, mustShowLandingZone, strokeAdded, mustClearCanvas,
            tempScrapCreated, longPressed, mustShowLongPressCircle,
            didSomething;
    private PointF landingZoneCenter;
    private int currentPointerID = INVALID_POINTER_ID, screenWidth,
            screenHeight;

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

    /**
     * Creates a new view for the argument CaliSmall instance.
     * 
     * @param c
     *            the new CaliSmall instance
     */
    public CaliView(CaliSmall c) {
        super(c);
        parent = c;
        strokes = new ArrayList<Stroke>();
        scraps = new ArrayList<Scrap>();
        allStrokes = new SpaceOccupationList<Stroke>();
        allScraps = new SpaceOccupationList<Scrap>();
        newStrokes = new ArrayList<Stroke>();
        newScraps = new ArrayList<Scrap>();
        reset();
        getHolder().addCallback(this);
    }

    @SuppressWarnings("serial")
    private void reset() {
        strokes.clear();
        scraps.clear();
        allStrokes.clear();
        allScraps.clear();
        bubbleMenu = new BubbleMenu(this);
        ghostHandler = new GhostStrokeHandler(this);
        longPressAction = new LongPressAction(this);
        drawingHandler = new DrawingHandler(this);
        scaleListener = new ScaleListener(this);
        scaleDetector = new ScaleGestureDetector(parent, scaleListener);
        // stupid Arrays.asList signature...
        handlers = new ArrayList<TouchHandler>() {
            {
                // order DOES matter! calls are chained
                add(ghostHandler);
                add(bubbleMenu);
                add(scaleListener);
                add(drawingHandler);
            }
        };
        newStrokes.clear();
        newScraps.clear();
        pathMeasure = new PathMeasure();
        landingZoneCenter = new PointF();
        selected = null;
        previousSelection = null;
        newSelection = null;
        toBeRemoved = null;
        tempScrap = null;
        mustShowLandingZone = false;
        strokeAdded = false;
        mustClearCanvas = false;
        tempScrapCreated = false;
        longPressed = false;
        mustShowLongPressCircle = false;
        matrix = new Matrix();
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
        int height = screenHeight;
        int width = screenWidth;
        setBounds(width, height);
        stroke = null;
        createNewStroke();
        scaleListener.onScaleEnd(scaleDetector);
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
                maybeDrawLandingZone(canvas);
                deleteElements();
                maybeCreateBubbleMenu();
                addNewStrokesAndScraps();
                drawItems(canvas);
            }
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

    /**
     * Requests to clear (delete all objects from) this view and to empty every
     * list.
     */
    public void clear() {
        mustClearCanvas = true;
    }

    private void clearCanvas() {
        strokes.clear();
        scraps.clear();
        allStrokes.clear();
        allScraps.clear();
        setSelected(null);
        stroke = null;
        createNewStroke();
        mustClearCanvas = false;
    }

    private void drawItems(Canvas canvas) {
        if (tempScrap != null) {
            tempScrap.draw(this, canvas, scaleFactor);
        }
        for (Scrap scrap : scraps) {
            scrap.draw(this, canvas, scaleFactor);
        }
        for (Stroke stroke : strokes) {
            if (stroke.hasToBeDrawnVectorially())
                stroke.draw(canvas, PAINT);
        }
        if (bubbleMenu.isVisible())
            bubbleMenu.draw(canvas);
        if (!strokeAdded) {
            stroke.draw(canvas, PAINT);
            strokes.add(stroke);
            allStrokes.add(stroke);
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
        if (!bubbleMenu.isVisible() && bubbleMenu.isDrawable()) {
            if (tempScrapCreated) {
                Stroke border = getActiveStroke();
                allStrokes.remove(border);
                createNewStroke();
                changeTempScrap(new Scrap.Temp(border, scaleFactor));
                tempScrapCreated = false;
            }
            bubbleMenu.setDrawable(false);
            bubbleMenu.setVisible(true);
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
            if (toBeRemoved == tempScrap) {
                tempScrap = null;
                bubbleMenu.setVisible(false);
            }
            toBeRemoved = null;
        }
        CaliSmallElement.deleteMarkedFromList(strokes, allStrokes);
        CaliSmallElement.deleteMarkedFromList(scraps, allScraps);
        ghostHandler.deleteOldStrokes();
    }

    private void longPress(Stroke selected) {
        if (selected != null) {
            if (selected.parent != null) {
                ((Scrap) selected.parent).remove(selected);
            }
            stroke.delete();
            activeStroke = selected;
            tempScrapCreated = true;
            bubbleMenu.setDrawable(true);
        }
    }

    private void addNewStrokesAndScraps() {
        if (!newStrokes.isEmpty()) {
            strokes.addAll(newStrokes);
            allStrokes.addAll(newStrokes);
            newStrokes.clear();
        }
        if (!newScraps.isEmpty()) {
            scraps.addAll(newScraps);
            allScraps.addAll(newScraps);
            newScraps.clear();
        }
        if (newSelection != null) {
            setSelected(newSelection);
            previousSelection = newSelection;
            newSelection = null;
        }
    }

    /**
     * Sets the argument scrap as the selected one, unselecting the scrap that
     * was previously selected (if any was).
     * 
     * @param selected
     *            the scrap that should appear selected or <code>null</code> if
     *            there shouldn't be any scrap selected
     */
    public void setSelected(Scrap selected) {
        if (this.selected != null && selected != this.selected) {
            Stroke outerBorder = this.selected.deselect();
            if (outerBorder != null) {
                newStrokes.add(outerBorder);
                ghostHandler.addGhost(outerBorder);
            }
        }
        if (selected != null) {
            bubbleMenu.setBounds(selected.getBorder(), scaleFactor,
                    screenBounds);
            bubbleMenu.setDrawable(true);
            selected.select();
        } else {
            bubbleMenu.setVisible(false);
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
     *            whether all of the strokes inside of the argument scrap should
     *            be added to the view as well, should be <code>true</code> only
     *            for newly created scrap copies
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
     * Returns a new color picker whose initial color is set to math the current
     * stroke's.
     * 
     * @return a newly created color picker dialog ready to be shown
     */
    public AmbilWarnaDialog getColorPicker() {
        return new AmbilWarnaDialog(parent, stroke.getColor(),
                new OnAmbilWarnaListener() {
                    @Override
                    public void onOk(AmbilWarnaDialog dialog, int color) {
                        createNewStroke();
                        stroke.setColor(color);
                    }

                    @Override
                    public void onCancel(AmbilWarnaDialog dialog) {
                    }
                });
    }

    /**
     * Returns a reference to the scrap that is currently selected.
     * 
     * @return a reference to the selected scrap, or <code>null</code> if no
     *         scrap is selected
     */
    public Scrap getSelection() {
        return selected;
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
        try {
            PointF touchPoint = getTouchPoint(action, event);
            boolean redirected = false;
            if (redirectTo != null) {
                redirected = redirectTo.processTouchEvent(action, touchPoint,
                        event);
            }
            if (redirected) {
                if (redirectTo.done()) {
                    redirectTo = null;
                }
                return true;
            } else {
                for (TouchHandler handler : handlers) {
                    if (handler.processTouchEvent(action, touchPoint, event)) {
                        redirectTo = handler;
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // you gave me no choice, Android... :'(
            Log.e(VIEW_LOG_TAG, "Fatal exception", e);
        }
        return false;
    }

    private PointF getTouchPoint(int action, MotionEvent event) {
        int previousPointerID = INVALID_POINTER_ID;
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // first touch with one finger
            previousPointerID = 0;
            currentPointerID = event.findPointerIndex(0);
            break;
        case MotionEvent.ACTION_MOVE:
            previousPointerID = event.findPointerIndex(currentPointerID);
            break;
        case MotionEvent.ACTION_POINTER_UP:
            // second (or third, or fourth...) finger lifted
            final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            final int pointerId = event.getPointerId(pointerIndex);
            if (pointerId == currentPointerID) {
                // This was our active pointer going up. Choose a new
                // active pointer and adjust accordingly.
                final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                currentPointerID = event.getPointerId(newPointerIndex);
            }
            break;
        case MotionEvent.ACTION_UP:
            // last finger lifted
            previousPointerID = event.findPointerIndex(currentPointerID);
            currentPointerID = INVALID_POINTER_ID;
            break;
        // all other events don't change our pointer
        }
        return previousPointerID != INVALID_POINTER_ID ? adjustForZoom(
                event.getX(previousPointerID), event.getY(previousPointerID))
                : null;
    }

    private void createNewStroke() {
        if (stroke == null || !stroke.getPath().isEmpty()) {
            // otherwise don't create useless strokes
            stroke = new Stroke(this, new Path(), stroke);
            activeStroke = stroke;
            strokeAdded = false;
        }
    }

    /**
     * Returns <code>true</code> if the content of this view changed since this
     * method was last called (therefore this call has also the same effect as
     * {@link #resetChangeCounter()}).
     * 
     * @return <code>true</code> if something changed in this view since last
     *         call
     */
    public boolean hasChanged() {
        // FIXME this method is a lame replacement for a proper history
        boolean returnMe = didSomething;
        didSomething = false;
        return returnMe;
    }

    /**
     * Resets the change counter, meaning that the {@link #hasChanged()} will
     * return <code>true</code> only if changes in this view took place after
     * this method call.
     */
    public void resetChangeCounter() {
        didSomething = false;
    }

    /**
     * Returns the smallest scrap that contains the argument <tt>element</tt>,
     * if any exists.
     * 
     * @param element
     *            the element that must be completely contained within the scrap
     *            that this method shall return
     * @return the smallest scrap that completely contains the argument element,
     *         if any exists, <code>null</code>
     */
    public Scrap getSelectedScrap(CaliSmallElement element) {
        // TODO move it to the specific canvas object
        List<CaliSmallElement> candidates = allScraps
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
        List<CaliSmallElement> candidates = allScraps
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
        List<CaliSmallElement> candidates = allStrokes
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

    /**
     * Sets the previous selection.
     * 
     * <p>
     * Previous selections represent the scrap that was selected when the last
     * sequence of action (meaning anything taking place between a
     * {@link MotionEvent#ACTION_DOWN} and a {@link MotionEvent#ACTION_UP})
     * started.
     * 
     * @param previousSelection
     *            the previousSelection to set
     */
    public void setPreviousSelection(Scrap previousSelection) {
        this.previousSelection = previousSelection;
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
        return (dx * dx + dy * dy) < largestSide * largestSide;
    }

    /**
     * Returns whether the view is currently being drawn to a canvas.
     * 
     * @return <code>true</code> if this view is currently being drawn
     */
    public boolean isRunning() {
        return running;
    }

    private boolean intersectsBounds(Scrap selection) {
        RectF selectionBounds = new RectF();
        selectionBounds.set(selection.getBounds());
        return selectionBounds.intersect(screenBounds);
    }

    private boolean isInLandingZone(PointF lastPoint) {
        return Math.pow(lastPoint.x - landingZoneCenter.x, 2)
                + Math.pow(lastPoint.y - landingZoneCenter.y, 2) <= Math.pow(
                landingZoneRadius, 2);
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
        }
    }

    /**
     * Returns a reference to the list of all strokes lying within this view.
     * 
     * @return a reference to the list of all strokes
     */
    public SpaceOccupationList<Stroke> getStrokeList() {
        return allStrokes;
    }

    /**
     * Returns a reference to the list of all scraps lying within this view.
     * 
     * @return a reference to the list of all scraps
     */
    public SpaceOccupationList<Scrap> getScrapList() {
        return allScraps;
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
    private PointF adjustForZoom(float x, float y) {
        return new PointF(x / scaleFactor - canvasOffsetX, y / scaleFactor
                - canvasOffsetY);
    }

    private void updateBounds() {
        final PointF max = adjustForZoom(screenWidth, screenHeight);
        final PointF min = adjustForZoom(0, 0);
        screenBounds.set(min.x, min.y, max.x, max.y);
    }

    /**
     * Returns a reference to the painter that is currently responsible for
     * drawing this view.
     * 
     * @return a reference to the current painter
     */
    public Painter getPainter() {
        return painter;
    }

    /**
     * Takes a snapshot of this view and saves it to the argument file.
     * 
     * @param tmpImage
     *            the file to which the snapshot will be saved
     * @param signalCondition
     *            the condition to be {@link Condition#signal()}'ed after the
     *            snapshot has been taken
     */
    public void createSnapshot(File tmpImage, Condition signalCondition) {
        FileOutputStream tmp;
        try {
            tmp = new FileOutputStream(tmpImage);
            Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight,
                    Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawView(canvas);
            signalCondition.signalAll();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, tmp);
            tmp.flush();
            tmp.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Logs information about what's currently being shown on screen.
     */
    public void printLog() {
        Utils.debug("{{{SCRAPS}}}\n" + allScraps);
        Utils.debug("{{{STROKES}}}\n" + allStrokes);
        StringBuilder builder = new StringBuilder("{{{PARENTING}}}\n");
        String newLine = "";
        for (Scrap scrap : scraps) {
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
        Utils.debug(builder.toString());
        builder = new StringBuilder("{{{POINTS}}}\n");
        newLine = "";
        for (Stroke stroke : strokes) {
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
        for (Scrap scrap : scraps) {
            builder.append("============================\n");
            builder.append(scrap.getID());
            builder.append(Scrap.getContentToLog(scrap));
            builder.append("\n============================");
        }
        Utils.debug(builder.toString());
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
            painter = new Painter(holder, this);
            worker = new Thread(painter);
            running = true;
            worker.start();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view
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

    /**
     * Handles zooming and panning events.
     * 
     * @author Michele Bonazza
     * 
     */
    public class ScaleListener extends GenericTouchHandler {

        private boolean wasBubbleMenuShown;

        /**
         * Creates a new listener for zooming and panning events.
         * 
         * @param parent
         *            the parent view
         */
        public ScaleListener(CaliView parent) {
            super("ScaleListener", parent);
        }

        public boolean onPointerDown(PointF adjusted, MotionEvent event) {
            longPressListener.removeCallbacks(longPressAction);
            mustShowLandingZone = false;
            stroke.reset();
            scaleDetector.onTouchEvent(event);
            return true;
        }

        /*
         * (non-Javadoc)
         * 
         * @see edu.uci.calismall.GenericTouchHandler#processTouchEvent(int,
         * android.graphics.PointF, android.view.MotionEvent)
         */
        @Override
        public boolean processTouchEvent(int action, PointF touched,
                MotionEvent event) {
            switch (action) {
            case MotionEvent.ACTION_DOWN:
                actionCompleted = false;
                break;
            case MotionEvent.ACTION_MOVE:
                return scaleDetector.onTouchEvent(event);
            case MotionEvent.ACTION_POINTER_DOWN:
                return onPointerDown(touched, event);
            case MotionEvent.ACTION_POINTER_UP:
                return scaleDetector.onTouchEvent(event);
            case MotionEvent.ACTION_UP:
                actionCompleted = true;
                return true;
            }
            return false;
        }

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
            if (previousSelection != null) {
                wasBubbleMenuShown = true;
                previousSelection.outerBorder.setGhost(false, null, null, -1f);
                previousSelection.outerBorder.delete();
            } else
                wasBubbleMenuShown = false;
            bubbleMenu.setVisible(false);
            ghostHandler.setGhostAnimationOnPause(true);
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
                final float minOffsetX = (1 - scaleFactor) * screenWidth;
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
                final float minOffsetY = (1 - scaleFactor) * screenHeight;
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
            LANDING_ZONE_PAINT.setPathEffect(new DashPathEffect(new float[] {
                    newInterval, newInterval }, (float) 1.0));
            updateBounds();
            bubbleMenu.setBounds(scaleFactor, screenBounds);
            if (wasBubbleMenuShown) {
                previousSelection.outerBorder.restore();
                if (intersectsBounds(previousSelection))
                    setSelected(previousSelection);
            }
            ghostHandler.setGhostAnimationOnPause(false);
        }
    }

    private class DrawingHandler extends GenericTouchHandler {

        private DrawingHandler(CaliView parent) {
            super("DrawingHandler", parent);
        }

        public boolean onDown(PointF adjusted) {
            if (!didSomething)
                didSomething = true;
            actionStart = -System.currentTimeMillis();
            mustShowLandingZone = false;
            longPressed = false;
            longPressListener.postDelayed(longPressAction, LONG_PRESS_DURATION);
            stroke.setStart(adjusted);
            setSelected(getSelectedScrap(adjusted));
            return true;
        }

        public boolean onMove(PointF adjusted) {
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
            return true;
        }

        public boolean onUp(PointF adjusted) {
            mustShowLandingZone = false;
            actionCompleted = true;
            longPressListener.removeCallbacks(longPressAction);
            if (longPressAction.completed) {
                stroke.reset();
                createNewStroke();
                longPressAction.completed = false;
                landingZoneCenter.set(-1, -1);
                return true;
            } else if (longPressed) {
                // animation has been shown, but the user didn't mean to
                // long press, so she took her finger/stylus away
                longPressAction.reset(false);
            }
            if (isInLandingZone(adjusted)) {
                bubbleMenu.setDrawable(true);
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
            previousSelection = selected;
            landingZoneCenter.set(-1, -1);
            return true;
        }
    }

    private static class LongPressAction implements Runnable {

        private final CaliView parent;
        private boolean completed;
        private Stroke selected;

        private LongPressAction(CaliView parent) {
            this.parent = parent;
        }

        @Override
        public void run() {
            if (completed) {
                if (!parent.hasMovedEnough()) {
                    parent.longPress(selected);
                    selected = null;
                }
            } else {
                if (!parent.hasMovedEnough()) {
                    PointF center = parent.stroke.getStartPoint();
                    if (center != null) {
                        parent.longPressCircleBounds = new RectF(center.x
                                - parent.landingZoneCircleBoundsSize, center.y
                                - parent.landingZoneCircleBoundsSize, center.x
                                + parent.landingZoneCircleBoundsSize, center.y
                                + parent.landingZoneCircleBoundsSize);
                        parent.stroke.setBoundaries();
                        selected = parent.getClosestStroke();
                        if (selected != null) {
                            parent.longPressed = true;
                            parent.mustShowLongPressCircle = true;
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
        void reset(boolean completed) {
            parent.longPressed = false;
            parent.mustShowLongPressCircle = false;
            parent.landingZoneCircleSweepAngle = 0;
            this.completed = completed;
            if (completed)
                parent.longPressListener.post(this);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#toJSON()
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        List<JSONObject> jsonStrokes = new ArrayList<JSONObject>(strokes.size());
        for (int i = 0; i < strokes.size(); i++) {
            Stroke stroke = strokes.get(i);
            if (!stroke.isEmpty() && !stroke.isGhost())
                jsonStrokes.add(stroke.toJSON());
        }
        List<JSONObject> jsonScraps = new ArrayList<JSONObject>(scraps.size());
        for (int i = 0; i < scraps.size(); i++) {
            jsonScraps.add(scraps.get(i).toJSON());
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
    public CaliView fromJSON(JSONObject jsonData) throws JSONException {
        reset();
        JSONArray array = jsonData.getJSONArray("strokes");
        for (int i = 0; i < array.length(); i++) {
            Stroke stroke = new Stroke(this);
            stroke.fromJSON(array.getJSONObject(i));
            strokes.add(stroke);
        }
        allStrokes.addAll(strokes);
        array = jsonData.getJSONArray("scraps");
        for (int i = 0; i < array.length(); i++) {
            Scrap scrap = new Scrap(this);
            scrap.fromJSON(array.getJSONObject(i));
            scraps.add(scrap);
        }
        allScraps.addAll(scraps);
        for (Scrap scrap : scraps) {
            scrap.addChildrenFromJSON();
        }
        createNewStroke();
        return this;
    }

}
