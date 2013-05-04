/*******************************************************************************
* Copyright (c) 2013, Regents of the University of California
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without modification, are permitted provided
* that the following conditions are met:
*
* Redistributions of source code must retain the above copyright notice, this list of conditions
* and the following disclaimer.
*
* Redistributions in binary form must reproduce the above copyright notice, this list of conditions
* and the following disclaimer in the documentation and/or other materials provided with the
* distribution.
*
* None of the name of the Regents of the University of California, or the names of its
* contributors may be used to endorse or promote products derived from this software without specific
* prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
* PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
* TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
* ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
******************************************************************************/
package edu.uci.calismall;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Condition;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
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
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

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
     * The interval between dashes in landing zones (to be rescaled by
     * {@link #scaleFactor}).
     */
    public static final float ABS_LANDING_ZONE_INTERVAL = 5;
    /**
     * The length that a {@link Stroke} must reach before a landing zone is
     * shown (to be rescaled by {@link #scaleFactor}).
     */
    public static final float ABS_MIN_PATH_LENGTH_FOR_LANDING_ZONE = 340;
    /**
     * Where to put the center of the landing zone on a path (to be rescaled by
     * {@link #scaleFactor}).
     */
    public static final float ABS_LANDING_ZONE_PATH_OFFSET = 70;
    /**
     * The length over which a Path is no longer considered as a potential tap,
     * but is viewed as a stroke instead (to be rescaled by {@link #scaleFactor}
     * ).
     */
    public static final float ABS_TOUCH_THRESHOLD = 8;
    /**
     * The amount of pixels that a touch needs to cover before it is considered
     * a move action (to be rescaled by {@link #scaleFactor}).
     */
    public static final float ABS_TOUCH_TOLERANCE = 4;

    /**
     * Absolute half the size of the rectangle enclosing the circle displayed on
     * long presses (to be rescaled by {@link #scaleFactor}).
     */
    public static final float ABS_CIRCLE_BOUNDS_HALF_SIZE = 75;

    /**
     * How long should the long press animation last, that is how long it should
     * take for the circle to be drawn completely in milliseconds.
     */
    public static final long LONG_PRESS_ANIMATION_DURATION = 350;

    /**
     * Absolute length of the increment when drawing the long press circle
     * between {@link CaliView#draw(Canvas)} calls. This determines the speed at
     * which the circle is animated.
     */
    public static final float ABS_CIRCLE_SWEEP_INCREMENT = 25;
    /**
     * The starting point for the sweep animation for long presses. 0 is the
     * rightmost point, -90 is the topmost point.
     */
    public static final float CIRCLE_SWEEP_START = -90;
    /**
     * Absolute distance that the first and last point in a {@link Stroke} may
     * have to be considered a valid target for long-presses scrap recognition
     * (to be rescaled by {@link #scaleFactor}). The value is squared to speed
     * up comparison with Euclidean distances.
     */
    public static final float ABS_MAX_STROKE_DISTANCE_FOR_LONG_PRESS = 200 * 200;
    /**
     * The minimum zoom level that users can reach.
     */
    public static final float MIN_ZOOM = 0.5f;
    /**
     * The maximum zoom level that users can reach
     */
    public static final float MAX_ZOOM = 4f;
    /**
     * The ratio used to make the landing zone the same physical size regardless
     * of the device that is currently being used.
     */
    public static final float LANDING_ZONE_RADIUS_TO_WIDTH_RATIO = 30f / 1280;
    /**
     * The paint object that is used to draw all strokes with.
     * 
     * <p>
     * Every {@link Stroke} stores values for how this <tt>Paint</tt> object
     * should be modified before actually drawing it, including the stroke
     * width, color and fill type. These are set in the
     * {@link Stroke#draw(Canvas, Paint)} method for every stroke to be drawn.
     */
    public static final Paint PAINT = new Paint();
    /**
     * The paint object that is used to draw landing zones with.
     * 
     * <p>
     * The dotted effect is obtained by constantly updating the length of the
     * segments according to the zoom level.
     */
    public static final Paint LANDING_ZONE_PAINT = new Paint();
    /**
     * The paint object that is used to draw the circle animation whenever users
     * press-and-hold in the proximity of a stroke.
     */
    public static final Paint LONG_PRESS_CIRCLE_PAINT = new Paint();
    /**
     * The paint object that is used to draw the drawable portion of the canvas
     * in white when the view is also showing a portion outside of the drawable
     * area.
     */
    public static final Paint DRAWABLE_PORTION_PAINT = new Paint();
    /**
     * The paint object that is used to draw the shadow of the drawable portion
     * of the canvas.
     */
    public static final Paint DRAWABLE_SHADOW_PAINT = new Paint();
    /**
     * The offset applied to the drawable area's shadow, which is the same
     * applied to the blur effect of said shadow.
     */
    public static final int DRAWABLE_SHADOW_OFFSET = 4;
    /**
     * The amount of time that passes between two consecutive executions of the
     * {@link Committer}.
     */
    public static final long COMMITTER_REFRESH_PERIOD = 500;
    /**
     * The stroke thickness used by default.
     */
    public static final int DEFAULT_STROKE_THICKNESS = 2;
    /**
     * The stroke thickness in use when drawing the long press animation.
     */
    public static final int LONG_PRESS_CIRCLE_THICKNESS = 3;
    private static final int INVALID_POINTER_ID = -1;
    /**
     * The portion of the canvas that represents paper, so it's colored white
     * and users can draw on top of it.
     */
    RectF drawableCanvas;
    /**
     * The area where the shadow of the drawable area will be drawn to (to
     * simulate a piece of paper).
     */
    RectF drawableCanvasShadow;
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
     * A reference to the current stroke used by the drawing thread.
     * 
     * <p>
     * Points are added to the current {@link #stroke}, but its path is drawn
     * using this object instead, so that the Event thread can be decoupled from
     * the drawing thread(s). In other words, the Event thread is free to change
     * the value of the {@link #stroke} field without risking to cause a
     * {@link ConcurrentModificationException}, as it's not being directly used
     * by the drawing thread (or by the committer thread).
     */
    Stroke activeStroke;
    /**
     * The stroke used to select items.
     * 
     * <p>
     * This stroke will most of the times be the same as {@link #activeStroke},
     * but keeping a separate reference helps in dealing with corner cases.
     */
    Stroke selectionStroke;
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
     * The abstract radius (to be rescaled by {@link #scaleFactor}) of landing
     * zones for this device, meaning that it's rescaled to look the same
     * physical size across devices.
     */
    float scaledLandingZoneAbsRadius;
    /**
     * The radius of the circle that represents the landing zone.
     */
    float landingZoneRadius;
    /**
     * The minimum length that a {@link Stroke} must reach before the landing
     * zone is displayed over it (letting that <tt>Stroke</tt> be used to create
     * a {@link TempScrap}). The value is squared to speed up comparison with
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
     * The stroke width chosen by the user.
     */
    float currentAbsStrokeWidth = DEFAULT_STROKE_THICKNESS;
    /**
     * Whether stroke size should be rescaled according to the zoom level.
     */
    boolean scaleStrokeWithZoom = true;
    /**
     * Whether a full redraw of the screen must be performed.
     */
    boolean zooming;
    /**
     * Whether a redraw has been requested.
     */
    boolean forceSingleRedraw;
    /**
     * The current instance of the bubble menu.
     */
    BubbleMenu bubbleMenu;
    /**
     * Whether the view should display the "outside" of the canvas, meaning its
     * non-drawable portion (in gray).
     */
    boolean zoomOutOfBounds;

    // a list of strokes kept in chronological order (oldest first)
    private final List<Stroke> strokes;
    // a list of scraps kept in chronological order (oldest first)
    private final List<Scrap> scraps;
    private final List<Stroke> foregroundStrokes;
    private final List<Stroke> newStrokes;
    private final List<Scrap> newScraps;
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
    private Canvas backgroundCanvas;
    private Bitmap background, snapshot;
    private LongPressAction longPressAction;
    private Thread worker;
    private Timer committerTimer;
    private Painter painter;
    private Scrap selected, highlighted, previousSelection, newSelection,
            toBeRemoved, tempScrap;
    private TouchHandler[] handlers;
    private ScaleListener scaleListener;
    private TouchHandler redirectTo;
    private GhostStrokeHandler ghostHandler;
    private EraserHandler eraserHandler;
    private DrawingHandler drawingHandler;
    private PathMeasure pathMeasure;
    private boolean running, mustShowLandingZone, mustClearCanvas, longPressed,
            mustShowLongPressCircle, didSomething, forcedRedraw,
            foregroundRefresh, mustFixCanvasSize;
    private PointF landingZoneCenter;
    private int currentPointerID = INVALID_POINTER_ID, screenWidth,
            screenHeight;
    private long lastLongPressAnimationRefresh;

    static {
        PAINT.setAntiAlias(true);
        PAINT.setDither(true);
        PAINT.setStrokeJoin(Paint.Join.ROUND);
        PAINT.setStrokeCap(Paint.Cap.ROUND);
        DRAWABLE_PORTION_PAINT.setColor(Color.WHITE);
        DRAWABLE_SHADOW_PAINT.setColor(Color.BLACK);
        DRAWABLE_SHADOW_PAINT.setMaskFilter(new BlurMaskFilter(
                DRAWABLE_SHADOW_OFFSET, Blur.OUTER));
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
    public CaliView(Context c) {
        super(c);
        if (!(c instanceof CaliSmall))
            throw new IllegalArgumentException(
                    "CaliView can only be placed within a CaliSmall Activity");
        parent = (CaliSmall) c;
        strokes = new ArrayList<Stroke>();
        scraps = new ArrayList<Scrap>();
        foregroundStrokes = new ArrayList<Stroke>();
        allStrokes = new SpaceOccupationList<Stroke>();
        allScraps = new SpaceOccupationList<Scrap>();
        newStrokes = new ArrayList<Stroke>();
        newScraps = new ArrayList<Scrap>();
        eraserHandler = new EraserHandler(this);
        bubbleMenu = new BubbleMenu(this);
        ghostHandler = new GhostStrokeHandler(this);
        longPressAction = new LongPressAction(this);
        drawingHandler = new DrawingHandler(this);
        scaleListener = new ScaleListener(this);
        scaleDetector = new ScaleGestureDetector(parent, scaleListener);
        // order DOES matter! calls are chained, see onTouchEvent
        handlers = new TouchHandler[] { bubbleMenu, scaleListener,
                eraserHandler, ghostHandler, drawingHandler };
        reset();
        committerTimer = new Timer();
        getHolder().addCallback(this);
    }

    /**
     * Resets the state of this view.
     */
    public void reset() {
        strokes.clear();
        scraps.clear();
        foregroundStrokes.clear();
        allStrokes.clear();
        allScraps.clear();
        backgroundCanvas = new Canvas();
        newStrokes.clear();
        newScraps.clear();
        pathMeasure = new PathMeasure();
        landingZoneCenter = new PointF();
        selected = null;
        previousSelection = null;
        if (bubbleMenu != null) {
            bubbleMenu.setVisible(false);
        }
        newSelection = null;
        toBeRemoved = null;
        tempScrap = null;
        mustShowLandingZone = false;
        mustClearCanvas = false;
        longPressed = false;
        mustShowLongPressCircle = false;
        didSomething = false;
        matrix = new Matrix();
        screenBounds = new RectF();
        longPressCircleBounds = new RectF();
        zoomOutOfBounds = false;
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
        setBounds(screenWidth, screenHeight);
        if (stroke != null)
            stroke.reset();
        createNewStroke();
        activeStroke = stroke;
        selectionStroke = null;
        scaleListener.onScaleEnd(scaleDetector);
    }

    /**
     * Draws this view to the argument {@link Canvas}.
     * 
     * <p>
     * The current implementation simulates having a background bitmap on top of
     * which foreground elements are drawn (current stroke, selections). The way
     * it's done it's a little too fragile, changing the order of redrawing
     * operations can trigger strobo-like effects, not cool (or very cool,
     * depending on your taste). PorterModes should be used instead. Room for
     * improvement!
     * 
     * @param canvas
     *            the canvas onto which this view is to be drawn
     */
    public void drawView(Canvas canvas) {
        if (zooming || forceSingleRedraw) {
            forceSingleRedraw = false;
            if (!forcedRedraw) {
                snapshot = takeSnapshot();
            }
            forcedRedraw = true;
            if (zooming) {
                // draw the raw bitmap so it performs better
                canvas.concat(matrix);
                drawDrawableArea(canvas);
                canvas.drawBitmap(snapshot, 0, 0, PAINT);
            } else {
                redrawEverything(canvas);
            }
            return;
        } else if (forcedRedraw) {
            forcedRedraw = false;
            // force a full redraw by Android Drawing Thread
            updateBackground();
        }
        drawBackground(canvas);
        drawForeground(canvas);
    }

    private void redrawEverything(Canvas canvas) {
        canvas.concat(matrix);
        drawDrawableArea(canvas);
        for (Scrap scrap : scraps) {
            if (scrap.getParent() == null)
                scrap.draw(this, canvas, scaleFactor, true);
        }
        for (Stroke stroke : strokes) {
            if (!stroke.hasToBeDeleted() && stroke.hasToBeDrawnVectorially())
                stroke.draw(canvas, PAINT);
        }
        if (bubbleMenu.isVisible())
            bubbleMenu.draw(canvas);
    }

    private void drawBackground(Canvas canvas) {
        if (background != null) {
            canvas.drawBitmap(background, 0, 0, PAINT);
        }
    }

    private void drawForeground(Canvas canvas) {
        canvas.concat(matrix);
        if (mustShowLongPressCircle) {
            drawLongPressAnimation(canvas);
        }
        if (mustClearCanvas) {
            clearCanvas();
        } else if (foregroundRefresh) {
            forceSingleRedraw = true;
            foregroundRefresh = false;
        } else {
            deleteElements();
            boolean tempScrapCreated = createTempScrap();
            addNewStrokesAndScraps();
            drawSelected(canvas);
            drawHighlighted(canvas);
            drawForegroundStrokes(canvas);
            if (bubbleMenu.isVisible()) {
                bubbleMenu.draw(canvas);
            }
            activeStroke.draw(canvas, PAINT);
            ghostHandler.drawGhosts(canvas, PAINT);
            drawLandingZone(canvas);
            if (activeStroke != stroke) {
                // that is, after DrawingHandler.onUp() has been called
                if (!tempScrapCreated) {
                    // selection strokes are temporary, don't mind them
                    strokes.add(activeStroke);
                    allStrokes.add(activeStroke);
                }
                foregroundStrokes.add(activeStroke);
                activeStroke = stroke;
            }
        }
    }

    /**
     * Draws the selected scrap on the argument canvas, if a scrap is selected
     * and it's <u>not</u> being drawn from vector data. This way the bitmap
     * stored inside the scrap object is used, and that is what is drawn to the
     * foreground.
     * 
     * @param canvas
     *            the canvas onto which the selected scrap must be drawn
     */
    private void drawSelected(Canvas canvas) {
        Scrap drawMe = selected;
        if (drawMe != null && !drawMe.hasToBeDrawnVectorially()) {
            drawMe.draw(this, canvas, scaleFactor, true);
        }
    }

    private void drawHighlighted(Canvas canvas) {
        Scrap drawMe = highlighted;
        if (drawMe != null && drawMe.hasToBeDrawnVectorially()) {
            drawMe.draw(this, canvas, scaleFactor, false);
            for (Stroke stroke : drawMe.getAllStrokes()) {
                stroke.draw(canvas, PAINT);
            }
            drawMe.drawHighlightedBorder(canvas, scaleFactor);
        }
    }

    private void drawForegroundStrokes(Canvas canvas) {
        if (!foregroundStrokes.isEmpty()) {
            for (Iterator<Stroke> iterator = foregroundStrokes.iterator(); iterator
                    .hasNext();) {
                Stroke stroke = iterator.next();
                stroke.draw(canvas, PAINT);
                if (stroke.isCommitted())
                    iterator.remove();
            }
        }
    }

    private void updateBackground() {
        background = Bitmap.createBitmap(screenWidth, screenHeight,
                Config.ARGB_8888);
        backgroundCanvas = new Canvas(background);
        backgroundCanvas.concat(matrix);
        drawDrawableArea(backgroundCanvas);
        for (Scrap scrap : scraps) {
            if (scrap.hasToBeDrawnVectorially()) {
                scrap.draw(this, backgroundCanvas, scaleFactor, true);
            }
        }
        for (Stroke stroke : strokes) {
            if (!stroke.hasToBeDeleted() && !stroke.isGhost()
                    && stroke.hasToBeDrawnVectorially())
                stroke.draw(backgroundCanvas, PAINT);
        }
    }

    private void drawLongPressAnimation(Canvas canvas) {
        final long deltaT = System.currentTimeMillis()
                - lastLongPressAnimationRefresh;
        landingZoneCircleSweepAngle += (360 * deltaT / LONG_PRESS_ANIMATION_DURATION);
        canvas.drawArc(longPressCircleBounds, CIRCLE_SWEEP_START,
                landingZoneCircleSweepAngle, false, LONG_PRESS_CIRCLE_PAINT);
        if (landingZoneCircleSweepAngle >= 360) {
            longPressAction.reset(true);
        }
        lastLongPressAnimationRefresh = System.currentTimeMillis();
    }

    /**
     * Requests to clear (delete all objects from) this view and to empty every
     * list.
     */
    public void clear() {
        mustClearCanvas = true;
    }

    /**
     * Sets zooming and panning so that the drawable portion of the current
     * canvas fits the width of the screen.
     */
    public void fitZoom() {
        final float dX = drawableCanvas.width() - screenWidth;
        final float dY = drawableCanvas.height() - screenHeight;
        canvasOffsetX = 0;
        canvasOffsetY = 0;
        scaleFactor = 1f;
        if (dX != 0 || dY != 0) {
            if (dY < dX) {
                scaleFactor = screenWidth / drawableCanvas.width();
                canvasOffsetY = screenHeight / 2;
            } else {
                scaleFactor = screenHeight / drawableCanvas.height();
                canvasOffsetX = screenWidth / 2;
            }
            if (scaleFactor >= 1) {
                canvasOffsetX = 0;
                canvasOffsetY = 0;
            }
        }
        matrix.reset();
        matrix.preTranslate(canvasOffsetX, canvasOffsetY);
        matrix.postScale(scaleFactor, scaleFactor);
        scaleListener.onScaleEnd(scaleDetector);
        forceSingleRedraw = true;
    }

    /**
     * Forces the drawing thread to perform a single redraw of the whole canvas
     * using vector data (skipping the background bitmap redraw).
     * 
     * <p>
     * This method is to be called whenever a full refresh of the screen must be
     * performed because content is about to be edited. A full redraw of the
     * screen takes more time than the bitmap background + vector foreground
     * method, so this method should be called only when the background contains
     * stale data that must be refreshed.
     */
    public void forceRedraw() {
        forceSingleRedraw = true;
    }

    private void drawDrawableArea(Canvas canvas) {
        if (zoomOutOfBounds) {
            canvas.drawColor(Color.GRAY);
            canvas.drawRect(drawableCanvasShadow, DRAWABLE_SHADOW_PAINT);
            canvas.drawRect(drawableCanvas, DRAWABLE_PORTION_PAINT);
        } else {
            canvas.drawColor(Color.WHITE);
        }
    }

    private void clearCanvas() {
        strokes.clear();
        scraps.clear();
        allStrokes.clear();
        allScraps.clear();
        setSelected(null);
        stroke = null;
        createNewStroke();
        forceSingleRedraw = true;
        mustClearCanvas = false;
    }

    private void drawLandingZone(Canvas canvas) {
        if (mustShowLandingZone) {
            canvas.drawCircle(landingZoneCenter.x, landingZoneCenter.y,
                    landingZoneRadius, LANDING_ZONE_PAINT);
        }
    }

    private boolean createTempScrap() {
        if (selectionStroke != null) {
            selectionStroke.delete();
            changeTempScrap(new TempScrap(selectionStroke, scaleFactor));
            bubbleMenu.setVisible(true);
            selectionStroke = null;
            return true;
        }
        return false;
    }

    private void deleteElements() {
        if (toBeRemoved != null) {
            toBeRemoved.erase();
            if (toBeRemoved == tempScrap) {
                tempScrap = null;
                bubbleMenu.setVisible(false);
            }
            toBeRemoved = null;
            // remove deleted elements from the background
            forceSingleRedraw = true;
        }
        CaliSmallElement.deleteMarkedFromList(strokes, allStrokes);
        CaliSmallElement.deleteMarkedFromList(scraps, allScraps);
        ghostHandler.deleteOldStrokes();
        removeMarkedForDeletion();
    }

    private void removeMarkedForDeletion() {
        if (!strokes.isEmpty()) {
            for (Iterator<Stroke> iterator = strokes.iterator(); iterator
                    .hasNext();) {
                Stroke stroke = iterator.next();
                if (stroke.hasToBeDeleted())
                    iterator.remove();
            }
        }
        if (!foregroundStrokes.isEmpty()) {
            for (Iterator<Stroke> iterator = foregroundStrokes.iterator(); iterator
                    .hasNext();) {
                Stroke stroke = iterator.next();
                if (stroke.hasToBeDeleted())
                    iterator.remove();
            }
        }
        if (!scraps.isEmpty()) {
            for (Iterator<Scrap> iterator = scraps.iterator(); iterator
                    .hasNext();) {
                Scrap scrap = iterator.next();
                if (scrap.hasToBeDeleted()) {
                    iterator.remove();
                }
            }
        }
    }

    private void longPress(Stroke selected) {
        if (selected != null) {
            if (selected.parent != null) {
                ((Scrap) selected.parent).remove(selected);
            }
            activeStroke = selected;
            selectionStroke = selected;
            forceSingleRedraw = true;
        }
    }

    private void addNewStrokesAndScraps() {
        if (!newStrokes.isEmpty()) {
            strokes.addAll(newStrokes);
            allStrokes.addAll(newStrokes);
            foregroundStrokes.addAll(newStrokes);
            newStrokes.clear();
        }
        if (!newScraps.isEmpty()) {
            scraps.addAll(newScraps);
            allScraps.addAll(newScraps);
            newScraps.clear();
            forceSingleRedraw = true;
        }
        if (newSelection != null) {
            setSelected(newSelection);
            previousSelection = newSelection;
            newSelection = null;
        }
    }
    
    /**
     * Displays an error dialog informing the user that there's not enough
     * memory to load the image that she tried to import to the sketch.
     */
    public void showOutOfMemoryError() {
        parent.showOutOfMemoryError();
    }

    /**
     * Sets the argument scrap as the selected one, deselecting the scrap that
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
                ghostHandler.addGhost(outerBorder);
            }
        }
        highlighted = selected;
        if (selected != null) {
            bubbleMenu.setBounds(selected, scaleFactor, screenBounds);
            bubbleMenu.setVisible(true);
        } else {
            tempScrap = null;
            bubbleMenu.setVisible(false);
        }
        this.selected = selected;
    }

    /**
     * Sets the drawable portion of the canvas, the one with a white background.
     * 
     * <p>
     * The argument rect is used only temporarily and only the first time this
     * method is called, as the view is not already been laid out and hence
     * {@link #surfaceChanged(SurfaceHolder, int, int, int)} has not been
     * called. The first call will thus result in a larger canvas being
     * displayed (since the display size computed from the {@link Activity} will
     * always be larger than the view size because of the action bar), but as
     * soon as the view gets notified of the availability of its {@link Surface}
     * the actual size is used instead. This should happen within milliseconds,
     * so users will never notice the issue.
     * 
     * <p>
     * After the view has been laid out, the actual display size will be used
     * and the argument rectangle ignored.
     * 
     * @param drawable
     *            the size of the drawable portion of the canvas
     */
    public void setDrawableCanvas(RectF drawable) {
        if (screenWidth == 0) {
            mustFixCanvasSize = true;
            drawableCanvas = drawable;
        } else {
            drawableCanvas = new RectF(0, 0, screenWidth, screenHeight);
        }
        drawableCanvasShadow = new RectF(drawableCanvas);
        drawableCanvasShadow.left += DRAWABLE_SHADOW_OFFSET;
        drawableCanvasShadow.top += DRAWABLE_SHADOW_OFFSET;
    }

    private void setDrawableCanvasInternal(RectF drawable) {
        drawableCanvas = drawable;
        drawableCanvasShadow = new RectF(drawable);
        drawableCanvasShadow.left += DRAWABLE_SHADOW_OFFSET;
        drawableCanvasShadow.top += DRAWABLE_SHADOW_OFFSET;
        mustFixCanvasSize = false;
    }

    /**
     * Enables/disables the eraser mode.
     */
    public void toggleEraserMode() {
        eraserHandler.toggleEnabled();
    }

    /**
     * Sets the argument scrap as highlighted, meaning that its border will be
     * drawn on the foreground using a blueish background.
     * 
     * @param highlighted
     *            the scrap to highlight
     */
    public void setHighlighted(Scrap highlighted) {
        this.highlighted = highlighted;
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
        if (!(scrap instanceof TempScrap))
            newScraps.add(scrap);
        if (addContent) {
            newStrokes.addAll(scrap.getAllStrokes());
            newScraps.addAll(scrap.getAllScraps());
        }
    }

    /**
     * Adds the argument stroke to the list of new strokes that must be added to
     * the canvas.
     * 
     * <p>
     * Since this method is only called by
     * {@link GhostStrokeHandler#onUp(PointF)} when a ghost stroke is to be
     * submitted to the canvas, if the argument <tt>stroke</tt> is the same as
     * {@link #selectionStroke}, this last field is reset. So, the message is:
     * if you use this method passing it the {@link #selectionStroke}, be aware
     * that you're altering the selection.
     * 
     * @param stroke
     *            the new stroke to be added
     */
    public void addStroke(Stroke stroke) {
        if (stroke != null) {
            newStrokes.add(stroke);
            if (stroke == selectionStroke)
                selectionStroke = null;
        }
    }

    /**
     * Returns a rectangle containing the current sketch's canvas width and
     * height.
     * 
     * @return the area onto which strokes can be drawn
     */
    public RectF getDrawableArea() {
        return new RectF(drawableCanvas);
    }

    /**
     * Called whenever a new combination of color, thickness and value for the
     * "scale with zoom" flag has been chosen by the user.
     * 
     * @param color
     *            the new color
     * @param scaleWithZoom
     *            whether stroke thickness should change together with the zoom
     *            value
     * @param strokeThickness
     *            the new stroke thickness
     */
    public void styleChanged(int color, boolean scaleWithZoom,
            float strokeThickness) {
        stroke.setColor(color);
        scaleStrokeWithZoom = scaleWithZoom;	
        currentAbsStrokeWidth = strokeThickness;
        if (scaleWithZoom) {
            stroke.setStrokeWidth(currentAbsStrokeWidth / scaleFactor);
        } else {
            stroke.setStrokeWidth(currentAbsStrokeWidth);
        }
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
        // Log.d("MotionEvent", Utils.actionToString(action));
        try {
            PointF touchPoint = getTouchPoint(action, event);
            boolean redirected = false;
            if (redirectTo != null) {
                // keep redirecting to the enabled TouchHandler
                redirected = redirectTo.processTouchEvent(action, touchPoint,
                        event);
            }
            if (redirected) {
                if (redirectTo.done()) {
                    redirectTo = null;
                }
                return true;
            } else {
                // new action, or an event was unknown to last TouchHandler
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

    private void checkDrawableArea() {
        zoomOutOfBounds = drawableCanvas.height() < screenHeight
                || drawableCanvas.width() < screenWidth;
        if (canvasOffsetX > 0) {
            zoomOutOfBounds = true;
        } else {
            final float minOffsetX = (1 - scaleFactor) * drawableCanvas.width();
            if (canvasOffsetX * scaleFactor < minOffsetX) {
                zoomOutOfBounds = true;
            }
        }
        if (canvasOffsetY > 0) {
            zoomOutOfBounds = true;
        } else {
            final float minOffsetY = (1 - scaleFactor)
                    * drawableCanvas.height();
            if (canvasOffsetY * scaleFactor < minOffsetY) {
                zoomOutOfBounds = true;
            }
        }
    }

    private void createNewStroke() {
        if (stroke == null || !stroke.isEmpty()) {
            // otherwise don't create useless strokes
            stroke = new Stroke(this, new Path(), stroke);
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
     * Returns if this view contains at least as many points in its strokes as
     * the argument quantity.
     * 
     * @param howManyPoints
     *            the reference number of points
     * @return <code>true</code> if this view contains at least
     *         <tt>howManyPoints</tt>
     */
    public boolean areThereMoreThanThisPoints(int howManyPoints) {
        int pointsSoFar = 0;
        for (Stroke stroke : strokes) {
            pointsSoFar += stroke.sizeInNumberOfPoints();
            if (pointsSoFar > howManyPoints)
                return true;
        }
        return false;
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
     * Returns the current size of this view.
     * 
     * @return the size of the view as it's been laid out
     */
    public PointF getSize() {
        return new PointF(screenWidth, screenHeight);
    }

    /**
     * Returns the current size of the drawable portion of this view.
     * 
     * @return the size of the portion of the view that can be drawn
     */
    public PointF getDrawableSize() {
        return new PointF(drawableCanvas.width(), drawableCanvas.height());
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

    /**
     * Returns all strokes that may contain the rectangle.
     * 
     * @param test
     *            the test rectangle
     * @return a sorted list (from smallest to largest) of strokes whose areas
     *         include the argument point
     */
    public List<Stroke> getIntersectingStrokes(RectF test) {
        List<Stroke> candidates = new ArrayList<Stroke>();
        for (int i = 0; i < strokes.size(); i++) {
            Stroke stroke = strokes.get(i);
            if (stroke.isPointWithinBounds(test))
                candidates.add(stroke);
        }
        return candidates;
    }

    private Stroke getClosestStroke() {
        List<CaliSmallElement> candidates = allStrokes
                .findIntersectionCandidates(activeStroke);
        // sort elements by their size (smallest first)
        Collections.sort(candidates);
        for (CaliSmallElement candidate : candidates) {
            if (candidate.contains(stroke.getStartPoint())
                    && isClosedEnough(candidate)) {
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

    private boolean isClosedEnough(CaliSmallElement candidate) {
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
        if (selection == null)
            return false;
        RectF selectionBounds = new RectF();
        selectionBounds.set(selection.getBounds());
        return selectionBounds.intersect(screenBounds);
    }

    private boolean isInLandingZone(PointF lastPoint) {
        return Math.pow(lastPoint.x - landingZoneCenter.x, 2)
                + Math.pow(lastPoint.y - landingZoneCenter.y, 2) <= Math.pow(
                landingZoneRadius, 2);
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
        if (mustFixCanvasSize) {
            // this will only be called after CaliSmall.newSketch() is called,
            // see setDrawableCanvas' javadoc for details
            setDrawableCanvasInternal(new RectF(0, 0, screenWidth, screenHeight));
        }
        checkDrawableArea();
        if (!intersectsBounds(selected)) {
            setSelected(null);
        }
        background = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        backgroundCanvas = new Canvas(background);
        forceSingleRedraw = true;
    }

    private void setBounds(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        updateBounds();
        if (selected != null) {
            bubbleMenu.setBounds(selected, scaleFactor, screenBounds);
        } else {
            bubbleMenu.setBounds(scaleFactor, screenBounds);
            scaledLandingZoneAbsRadius = Math.max(width, height)
                    * LANDING_ZONE_RADIUS_TO_WIDTH_RATIO;
            landingZoneRadius = scaledLandingZoneAbsRadius / scaleFactor;
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
     * Returns the current canvas' scale factor.
     * 
     * @return the scale factor currently in use
     */
    public float getScaleFactor() {
        return scaleFactor;
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
        FileOutputStream tmp = null;
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
        } finally {
            try {
                if (tmp != null)
                    tmp.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Saves a JPEG thumbnail of this canvas to the argument file.
     * 
     * @param dst
     *            the file to which the thumbnail will be saved
     */
    public void createThumbnail(File dst) {
        if (background != null) {
            FileOutputStream tmp = null;
            try {
                tmp = new FileOutputStream(dst);
                Bitmap bitmap = takeSnapshot();
                Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 4,
                        bitmap.getHeight() / 4, false).compress(
                        CompressFormat.JPEG, 70, tmp);
                tmp.flush();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (tmp != null)
                        tmp.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Bitmap takeSnapshot() {
        Bitmap bitmap = Bitmap.createBitmap((int) drawableCanvas.width(),
                (int) drawableCanvas.height(), Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        for (int i = 0; i < scraps.size(); i++) {
            Scrap scrap = scraps.get(i);
            scrap.draw(this, canvas, 1, true);
        }
        for (int i = 0; i < strokes.size(); i++) {
            Stroke stroke = strokes.get(i);
            if (!stroke.hasToBeDeleted() && stroke.hasToBeDrawnVectorially())
                stroke.draw(canvas, PAINT);
        }
        return bitmap;
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
            committerTimer.scheduleAtFixedRate(new Committer(this), 0,
                    COMMITTER_REFRESH_PERIOD);
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
        committerTimer.cancel();
        committerTimer = new Timer();
        parent.pauseAutoSaving();
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
     * Periodically draws strokes that are in the foreground to the foreground,
     * marking them as <i>committed</i>, so that the drawing thread can ignore
     * them and stop drawing them.
     * 
     * @author Michele Bonazza
     * 
     */
    private class Committer extends TimerTask {

        private CaliView parentView;

        private Committer(CaliView parentView) {
            this.parentView = parentView;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.TimerTask#run()
         */
        @Override
        public void run() {
            if (!foregroundStrokes.isEmpty()) {
                for (int i = 0; i < foregroundStrokes.size(); i++) {
                    Stroke stroke = foregroundStrokes.get(i);
                    if (!stroke.isCommitted() && !stroke.isGhost()
                            && !stroke.hasToBeDeleted()
                            && stroke.hasToBeDrawnVectorially()) {
                        stroke.draw(backgroundCanvas, PAINT);
                        stroke.setCommitted(true);
                    }
                }
                for (int i = 0; i < scraps.size(); i++) {
                    Scrap scrap = scraps.get(i);
                    if (!scrap.isCommitted() && scrap.hasToBeDrawnVectorially()) {
                        scrap.draw(parentView, backgroundCanvas, scaleFactor,
                                true);
                        scrap.setCommitted(true);
                    }
                }
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
                scaleDetector.onTouchEvent(event);
                break;
            case MotionEvent.ACTION_MOVE:
                return scaleDetector.onTouchEvent(event);
            case MotionEvent.ACTION_POINTER_DOWN:
                return onPointerDown(touched, event);
            case MotionEvent.ACTION_POINTER_UP:
                return scaleDetector.onTouchEvent(event);
            case MotionEvent.ACTION_CANCEL:
                return scaleDetector.onTouchEvent(event);
            case MotionEvent.ACTION_UP:
                actionCompleted = true;
                scaleDetector.onTouchEvent(event);
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
            bubbleMenu.setVisible(false);
            ghostHandler.setGhostAnimationOnPause(true);
            if (previousSelection != null) {
                // prevent the border from turning into a ghost
                previousSelection.outerBorder.setGhost(false, null, null, -1f);
            }
            zooming = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            dCenterX = detector.getFocusX();
            dCenterY = detector.getFocusY();
            // don't let the canvas get too small or too large
            final float newScale = scaleFactor * detector.getScaleFactor();
            if (newScale > MAX_ZOOM || newScale < MIN_ZOOM) {
                dScaleFactor = 1.f;
            } else if (newScale < 1) {
                zoomOutOfBounds = true;
                scaleFactor *= detector.getScaleFactor();
                dScaleFactor = detector.getScaleFactor();
            } else {
                zoomOutOfBounds = false;
                scaleFactor *= detector.getScaleFactor();
                dScaleFactor = detector.getScaleFactor();
            }
            final float scaledCenterX = dCenterX / scaleFactor;
            final float scaledCenterY = dCenterY / scaleFactor;
            translateX = scaledCenterX - previousScaledCenterX;
            translateY = scaledCenterY - previousScaledCenterY;
            canvasOffsetX += translateX;
            canvasOffsetY += translateY;
            checkDrawableArea();
            // translate, move origin to (x,y) to center zooming
            matrix.preTranslate(translateX + dCenterX, translateY + dCenterY);
            // scale and move origin back to (0,0)
            matrix.postScale(dScaleFactor, dScaleFactor);
            matrix.preTranslate(-dCenterX, -dCenterY);
            previousScaledCenterX = scaledCenterX;
            previousScaledCenterY = scaledCenterY;
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
            if (scaleStrokeWithZoom) {
                // rescale paint brush and connect circle
                stroke.setStrokeWidth(currentAbsStrokeWidth / scaleFactor);
            }
            LONG_PRESS_CIRCLE_PAINT.setStrokeWidth(LONG_PRESS_CIRCLE_THICKNESS
                    / scaleFactor * 2);
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
            bubbleMenu.setBounds(scaleFactor, screenBounds);
            ghostHandler.setGhostAnimationOnPause(false);
            if (previousSelection != null) {
                if (intersectsBounds(previousSelection)) {
                    setSelected(previousSelection);
                }
            } else {
                highlighted = null;
            }
            zooming = false;
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
            selectionStroke = null;
            longPressListener.postDelayed(longPressAction, LONG_PRESS_DURATION);
            stroke.setStart(adjusted);
            setSelected(getSelectedScrap(adjusted));
            return true;
        }

        public boolean onMove(PointF adjusted) {
            if (!longPressed && !longPressAction.completed) {
                if (!mustShowLandingZone) {
                    // a landing zone is forever... don't check once it's there!
                    mustShowLandingZone = mustShowLandingZone();
                    if (mustShowLandingZone) {
                        final float[] position = new float[2];
                        pathMeasure.getPosTan(landingZonePathOffset, position,
                                null);
                        landingZoneCenter = new PointF(position[0], position[1]);
                    }
                }
                if (stroke.addAndDrawPoint(adjusted, touchTolerance)) {
                    setSelected(getSelectedScrap(stroke));
                }
            }
            return true;
        }

        public boolean onUp(PointF adjusted) {
            // I LIED! (see onMove())
            mustShowLandingZone = false;
            actionCompleted = true;
            longPressListener.removeCallbacks(longPressAction);
            if (longPressAction.completed) {
                stroke.reset();
                createNewStroke();
                longPressAction.completed = false;
                landingZoneCenter.set(Float.MIN_VALUE, Float.MIN_VALUE);
                return true;
            }
            if (longPressed) {
                // animation has been shown, but the user didn't mean to
                // long press, so she took her finger/stylus away
                longPressAction.reset(false);
            }
            if (!stroke.isEmpty()) {
                if (isInLandingZone(adjusted)) {
                    selectionStroke = stroke;
                } else {
                    Scrap newSelection;
                    if (stroke.isTap(touchThreshold)) {
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
                    stroke.filterOutOfBoundsPoints(new RectF(drawableCanvas));
                }
                createNewStroke();
            }
            previousSelection = selected;
            landingZoneCenter.set(Float.MIN_VALUE, Float.MIN_VALUE);
            return true;
        }

        /*
         * (non-Javadoc)
         * 
         * @see edu.uci.calismall.GenericTouchHandler#onCancel()
         */
        @Override
        public boolean onCancel() {
            onUp(new PointF(Float.MAX_VALUE, Float.MAX_VALUE));
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
                if (parent.stroke.isTap(parent.touchThreshold)) {
                    parent.selectionStroke = selected;
                    parent.longPress(selected);
                    selected = null;
                }
            } else {
                if (parent.stroke.isTap(parent.touchThreshold)) {
                    PointF center = parent.activeStroke.getStartPoint();
                    if (center != null) {
                        parent.longPressCircleBounds = new RectF(center.x
                                - parent.landingZoneCircleBoundsSize, center.y
                                - parent.landingZoneCircleBoundsSize, center.x
                                + parent.landingZoneCircleBoundsSize, center.y
                                + parent.landingZoneCircleBoundsSize);
                        parent.activeStroke.setBoundaries();
                        selected = parent.getClosestStroke();
                        if (selected != null) {
                            parent.longPressed = true;
                            parent.lastLongPressAnimationRefresh = System
                                    .currentTimeMillis();
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
        json.put("x", drawableCanvas.width());
        json.put("y", drawableCanvas.height());
        json.put("str", new JSONArray(jsonStrokes));
        json.put("scr", new JSONArray(jsonScraps));
        return json;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#fromJSON(org.json.JSONObject)
     */
    @Override
    public CaliView fromJSON(JSONObject jsonData) throws JSONException {
        Stroke restore = activeStroke;
        reset();
        try {
            drawableCanvas = new RectF(0, 0, (float) jsonData.getDouble("x"),
                    (float) jsonData.getDouble("y"));
        } catch (JSONException e) {
            // old format, assume it's in portrait mode
            Utils.debug("old format, no size given");
            drawableCanvas = new RectF(0, 0,
                    Math.max(screenHeight, screenWidth), Math.min(screenHeight,
                            screenWidth));
        }
        setDrawableCanvasInternal(drawableCanvas);
        zoomOutOfBounds = drawableCanvas.height() < screenHeight
                || drawableCanvas.width() < screenWidth;
        JSONArray array = jsonData.getJSONArray("str");
        for (int i = 0; i < array.length(); i++) {
            Stroke stroke = new Stroke(this);
            stroke.fromJSON(array.getJSONObject(i));
            strokes.add(stroke);
        }
        allStrokes.addAll(strokes);
        array = jsonData.getJSONArray("scr");
        for (int i = 0; i < array.length(); i++) {
            Scrap scrap = new Scrap(this);
            scrap.fromJSON(array.getJSONObject(i));
            scraps.add(scrap);
        }
        allScraps.addAll(scraps);
        for (Scrap scrap : scraps) {
            scrap.addChildrenFromJSON();
        }
        // this lets the new stroke be added to fg
        activeStroke = restore;
        // let android flip the buffer twice... weird, I know..
        foregroundRefresh = true;
        return this;
    }

}
