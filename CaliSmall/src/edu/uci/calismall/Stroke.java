/**
 * Stroke.java Created on Jul 22, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import edu.uci.calismall.Scrap.Temp;

/**
 * Contains a {@link Path} and all of the style attributes to be set to the
 * {@link Paint} object used to draw it on canvas.
 * 
 * <p>
 * Every time {@link View#onDraw(android.graphics.Canvas)} is called all strokes
 * are rendered according to their styles by updating the {@link Paint} object
 * according to what's in this object.
 * 
 * @author Michele Bonazza
 */
class Stroke extends CaliSmallElement implements JSONSerializable<Stroke> {

    /**
     * A list containing all created strokes sorted by their position in the
     * canvas.
     */
    static final SpaceOccupationList SPACE_OCCUPATION_LIST = new SpaceOccupationList();
    private static final int DEFAULT_COLOR = Color.BLACK;
    private static final Paint.Style DEFAULT_STYLE = Paint.Style.STROKE;
    /**
     * The paint object that is used to draw ghost strokes.
     * 
     * <p>
     * Whenever a new {@link Temp} is created, its outer border is drawn to the
     * canvas as a "ghost" stroke, whose aim is to let users get their stroke
     * back if they hit the landing zone accidentally.
     */
    private static final Paint GHOST_PAINT = new Paint();
    private static final int GHOST_PAINT_OPACITY_PERCENTAGE = 25;
    private static final int GHOST_PAINT_START_OPACITY = GHOST_PAINT_OPACITY_PERCENTAGE * 255 / 100;
    /**
     * The amount of time that every ghost stroke is displayed before
     * disappearing.
     */
    private static final long GHOST_TIME = 2 * 100;
    /**
     * The amount of time it takes for the fadeout animation to complete.
     */
    private static final long GHOST_FADEOUT_TIME = 3000;

    /**
     * The list of points that this stroke contains.
     */
    protected final List<PointF> points;
    /**
     * The path that is created when drawing this stroke.
     */
    protected final Path path;
    /**
     * The style used to draw this stroke.
     */
    protected Paint.Style style = DEFAULT_STYLE;
    /**
     * The stroke width used to draw this stroke.
     */
    protected float strokeWidth = CaliSmall.ABS_MEDIUM_STROKE_WIDTH;
    /**
     * The color of this stroke.
     */
    protected int color = DEFAULT_COLOR;
    private int ghostOpacity = GHOST_PAINT_START_OPACITY;
    private final float[] matrixValues;
    private boolean isDot;
    private long ghostUntil;
    private BubbleMenu.Button ghostRevive;

    static {
        GHOST_PAINT.setAntiAlias(true);
        GHOST_PAINT.setDither(true);
        GHOST_PAINT.setStrokeJoin(Paint.Join.ROUND);
        GHOST_PAINT.setStrokeCap(Paint.Cap.ROUND);
        GHOST_PAINT.setStyle(Style.STROKE);
    }

    /**
     * Creates an empty stroke.
     * 
     * <p>
     * Used when deserializing data from JSON.
     */
    Stroke() {
        path = new Path();
        points = new ArrayList<PointF>();
        matrixValues = new float[9];
    }

    /**
     * Creates a new stroke, called when the user lifts her finger after drawing
     * a line or when the style for the current Path changes.
     * 
     * @param path
     *            the path currently being drawn
     * @param copyStyleFrom
     *            the stroke in use from which style is to be copied, if
     *            <code>null</code> all default values are used
     */
    Stroke(Path path, Stroke copyStyleFrom) {
        this.path = path;
        points = new ArrayList<PointF>();
        matrixValues = new float[9];
        if (copyStyleFrom != null) {
            this.strokeWidth = copyStyleFrom.getStrokeWidth();
            this.color = copyStyleFrom.getColor();
        }
    }

    /**
     * Copy constructor for stroke.
     * 
     * <p>
     * Paths within the <tt>copy</tt> stroke will be copied into new paths
     * associated with this stroke.
     * 
     * @param copy
     *            the stroke to be copied
     */
    Stroke(Stroke copy) {
        this(new Path(copy.path), copy);
        for (PointF point : copy.points) {
            points.add(new PointF(point.x, point.y));
        }
        if (copy.isDot)
            turnIntoDot();
    }

    /**
     * Adds a point to this stroke if it's at least <tt>touchTolerance</tt> far
     * from the last point of this stroke.
     * 
     * <p>
     * If <tt>newPoint</tt> is too close to the previous point it's not added
     * and this method returns <code>false</code>.
     * 
     * <p>
     * This method assumes that {@link #setStart(PointF)} has been called once
     * for this <tt>Stroke</tt>.
     * 
     * @param newPoint
     *            the new point to be added to this stroke
     * @param touchTolerance
     *            the distance from the last point in this <tt>Stroke</tt> under
     *            which <tt>newPoint</tt> is to be ignored
     * @return <code>true</code> if the point has been added to this stroke
     */
    public boolean addAndDrawPoint(PointF newPoint, float touchTolerance) {
        boolean added = false;
        final PointF last = points.get(points.size() - 1);
        final float dx = Math.abs(newPoint.x - last.x);
        final float dy = Math.abs(newPoint.y - last.y);
        if (dx >= touchTolerance || dy >= touchTolerance) {
            path.quadTo(last.x, last.y, (newPoint.x + last.x) / 2,
                    (newPoint.y + last.y) / 2);
            points.add(newPoint);
            setBoundaries();
            added = true;
        }
        return added;
    }

    /**
     * Returns the vector path for this stroke.
     * 
     * @return the path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns whether this stroke is empty, i.e. if it only contains an empty
     * path.
     * 
     * @return <code>true</code> if the underlying path is empty
     * @see Path
     */
    public boolean isEmpty() {
        return path.isEmpty();
    }

    /**
     * Sets the style of this stroke to the argument <tt>style</tt>.
     * 
     * @param style
     *            the style for this stroke
     * @return a reference to this object, so calls can be chained
     */
    public Stroke setStyle(Paint.Style style) {
        this.style = style;
        return this;
    }

    /**
     * Returns the style for this stroke.
     * 
     * @return the style to be applied to a {@link Paint} in order to display
     *         this stroke
     */
    public Paint.Style getStyle() {
        return style;
    }

    /**
     * Sets the first point of this stroke.
     * 
     * <p>
     * This method <b>must</b> be called first before any call to
     * {@link #addAndDrawPoint(PointF, float)} can be performed.
     * 
     * @param startPoint
     *            the first point of this stroke, adjusted for the current zoom
     *            level and panning
     * @return a reference to this object, so calls can be chained
     */
    public Stroke setStart(PointF startPoint) {
        path.moveTo(startPoint.x, startPoint.y);
        // this is here so that the path is not empty and press and hold relies
        // on that. DO NOT DELETE!
        path.lineTo(startPoint.x, startPoint.y);
        points.add(startPoint);
        setBoundaries();
        return this;
    }

    /**
     * Returns the first point in this stroke.
     * 
     * @return the point from which the user started to draw this stroke or
     *         <code>null</code> if this stroke is empty (i.e. it contains no
     *         points)
     */
    public PointF getStartPoint() {
        if (points.isEmpty())
            return null;
        return points.get(0);
    }

    /**
     * Returns the last point in this stroke.
     * 
     * @return the point that was added last while drawing this stroke
     */
    public PointF getEndPoint() {
        if (points.isEmpty())
            return null;
        return points.get(points.size() - 1);
    }

    /**
     * Turns this stroke into a dot centered on the first point of this stroke,
     * having a width of half the stroke's width.
     */
    public void turnIntoDot() {
        isDot = true;
        PointF center = points.get(0);
        path.reset();
        points.clear();
        points.add(center);
        style = Paint.Style.FILL;
        path.addCircle(center.x, center.y, strokeWidth / 2, Direction.CW);
        setBoundaries();
    }

    /**
     * Returns the width of this stroke.
     * 
     * @return the argument to be passed to {@link Paint#setStrokeWidth(float)}
     *         to display this stroke
     */
    public float getStrokeWidth() {
        return strokeWidth;
    }

    /**
     * Sets the width of this stroke.
     * 
     * @param strokeWidth
     *            the new width for this stroke
     * @return a reference to this object, so calls can be chained
     */
    public Stroke setStrokeWidth(float strokeWidth) {
        this.strokeWidth = strokeWidth;
        return this;
    }

    /**
     * Returns the color of this stroke.
     * 
     * @return the color to be passed to {@link Paint#setColor(int)} to display
     *         this stroke
     */
    public int getColor() {
        return color;
    }

    /**
     * Sets the color for this stroke.
     * 
     * @param color
     *            the color for this stroke
     * @return a reference to this object, so calls can be chained
     */
    public Stroke setColor(int color) {
        this.color = color;
        return this;
    }

    /**
     * Marks this stroke as a <i>ghost</i> stroke or revives a stroke that was
     * previously marked as ghost.
     * 
     * <p>
     * Ghost strokes are strokes that were the outer border of a temporary scrap
     * that was deselected without the user having done anything with the temp
     * scrap. They are displayed with a low opacity and can be restored to
     * regular strokes by pressing a button that pops up as soon as the temp
     * scrap is deselected.
     * 
     * <p>
     * If users interact with the originary temp scrap, ghost strokes are
     * immediately deleted to avoid polluting the screen for no reason: the
     * purpose of ghost strokes is to let users recover from accidentally
     * hitting the landing zone and creating a selection, whereas a regular
     * stroke was meant to be drawn.
     * 
     * <p>
     * After some time, ghost strokes disappear and cannot be recovered anymore.
     * 
     * @param isGhost
     *            whether this stroke is to be set as a ghost
     * @param screenBounds
     *            the current screen bounds
     * @param resources
     *            the resources to load the ghost revive bitmap from
     * @param buttonSize
     *            the size for the ghost revive button
     * 
     * @return this stroke
     */
    public Stroke setGhost(boolean isGhost, RectF screenBounds,
            Resources resources, float buttonSize) {
        if (isGhost) {
            ghostUntil = System.currentTimeMillis() + GHOST_TIME
                    + GHOST_FADEOUT_TIME;
            ghostRevive = new BubbleMenu.Button(new BitmapDrawable(resources,
                    BitmapFactory.decodeResource(resources,
                            R.drawable.ghost_revive)));
            PointF topLeft = getMostTopLeftPoint();
            float left = Math.max(screenBounds.left, topLeft.x - buttonSize);
            float top = Math.max(screenBounds.top, topLeft.y - buttonSize);
            Rect position = new Rect((int) left, (int) top,
                    (int) (left + buttonSize), (int) (top + buttonSize));
            RectF hitArea = new RectF(position);
            hitArea.inset(-buttonSize * 0.5f, -buttonSize * 0.5f);
            ghostRevive.setPosition(position, hitArea);
        } else {
            ghostUntil = -1;
        }
        return this;
    }

    /**
     * Returns whether this stroke is currently marked as a ghost stroke.
     * 
     * <p>
     * Ghost strokes are strokes that were the outer border of a temporary scrap
     * that was deselected without the user having done anything with the temp
     * scrap. They are displayed with a low opacity and can be restored to
     * regular strokes by pressing a button that pops up as soon as the temp
     * scrap is deselected.
     * 
     * <p>
     * If users interact with the originary temp scrap, ghost strokes are
     * immediately deleted to avoid polluting the screen for no reason: the
     * purpose of ghost strokes is to let users recover from accidentally
     * hitting the landing zone and creating a selection, whereas a regular
     * stroke was meant to be drawn.
     * 
     * <p>
     * After some time, ghost strokes disappear and cannot be recovered anymore.
     * At that time, this method starts returning <code>false</code>.
     * 
     * @return <code>true</code> if this stroke is currently marked as a ghost
     */
    public boolean isGhost() {
        return ghostUntil > -1;
    }

    /**
     * If the ghost button associated with this stroke must be triggered when
     * the argument point is touched it returns a reference to this stroke,
     * otherwise the method returns <code>null</code>.
     * 
     * @param touchPoint
     *            the touched point
     * @return this stroke if its ghost revive button should be triggered,
     *         <code>null</code> otherwise
     */
    Stroke ghostButtonTouched(PointF touchPoint) {
        return ghostRevive != null && ghostRevive.contains(touchPoint) ? this
                : null;
    }

    private PointF getMostTopLeftPoint() {
        float min = Float.MAX_VALUE;
        PointF topLeft = null;
        for (PointF point : points) {
            float value = 1.7f * point.x + point.y;
            if (value < min) {
                min = value;
                topLeft = point;
            }
        }
        return topLeft;
    }

    /**
     * Applies the argument transformation matrix to this stroke.
     * 
     * @param matrix
     *            the matrix containing deltas from the current stroke position
     */
    public void transform(Matrix matrix) {
        path.transform(matrix);
        matrix.getValues(matrixValues);
        // variable names are the same used by Skia library
        final float tx = matrixValues[Matrix.MTRANS_X];
        final float ty = matrixValues[Matrix.MTRANS_Y];
        final float mx = matrixValues[Matrix.MSCALE_X];
        final float my = matrixValues[Matrix.MSCALE_Y];
        final float kx = matrixValues[Matrix.MSKEW_X];
        final float ky = matrixValues[Matrix.MSKEW_Y];
        /*
         * if rotation: skia messes up with the matrix, so sx and sy actually
         * store cosV, rx and ry store -sinV and sinV
         */
        for (PointF point : points) {
            final float originalY = point.y;
            point.y = point.x * ky + (point.y * my) + ty;
            point.x = point.x * mx + (originalY * kx) + tx;
        }
        setBoundaries();
    }

    /**
     * Computes the boundaries for this stroke.
     * 
     * <p>
     * To be called after <tt>onDown()</tt>, when the stroke is complete.
     * 
     * @return a reference to this object, so calls can be chained
     */
    public Stroke setBoundaries() {
        super.setBoundaries(path);
        return this;
    }

    /**
     * Returns a list of all points that are part of this stroke.
     * 
     * @return a comma separated list of points that make part of this stroke
     */
    public String listPoints() {
        StringBuilder builder = new StringBuilder();
        String comma = "";
        for (PointF point : points) {
            builder.append(comma);
            builder.append(CaliSmall.pointToString(point));
            comma = ", ";
        }
        return builder.toString();
    }

    /**
     * Resets this stroke, clearing all points.
     * 
     * <p>
     * After a call to this method, {@link #setStart(PointF)} must be called
     * again to use this stroke.
     */
    public void reset() {
        path.reset();
        points.clear();
        setBoundaries();
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.CaliSmallElement#updateSpaceOccupation()
     */
    @Override
    protected void updateSpaceOccupation() {
        SPACE_OCCUPATION_LIST.update(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.CaliSmallElement#contains(android.graphics.PointF)
     */
    @Override
    public boolean contains(PointF point) {
        if (point == null)
            return false;
        return boundaries.contains(Math.round(point.x), Math.round(point.y));
    }

    /**
     * Draws this stroke on the argument canvas.
     * 
     * @param canvas
     *            the canvas on which to draw the stroke
     * @param paint
     *            the paint with which this stroke must be drawn
     */
    public void draw(Canvas canvas, Paint paint) {
        if (ghostUntil > 0) {
            drawGhost(canvas);
        } else {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(style);
            canvas.drawPath(path, paint);
        }
    }

    private void drawGhost(Canvas canvas) {
        if (ghostRevive != null) {
            GHOST_PAINT.setColor(color);
            GHOST_PAINT.setAlpha(ghostOpacity);
            GHOST_PAINT.setStrokeWidth(strokeWidth);
            GHOST_PAINT.setStyle(style);
            canvas.drawPath(path, GHOST_PAINT);
            long now = System.currentTimeMillis();
            ghostRevive.draw(canvas, ghostOpacity);
            if (now > ghostUntil) {
                toBeDeleted = true;
                ghostRevive = null;
            } else if (now > ghostUntil - GHOST_FADEOUT_TIME) {
                // update alpha to show fadeout
                ghostOpacity = (int) Math
                        .floor((((double) GHOST_PAINT_START_OPACITY / GHOST_FADEOUT_TIME) * (ghostUntil - now)));
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.CaliSmallElement#getPointsForInclusionTests()
     */
    @Override
    public List<PointF> getPointsForInclusionTests() {
        return points;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#toJSON()
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id.toString());
        json.put("color", color);
        json.put("width", strokeWidth);
        json.put("style", style.name());
        json.put("points", pointsToList());
        return json;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#fromJSON(edu.uci.calismall.
     * JSONSerializable, org.json.JSONObject)
     */
    @Override
    public Stroke fromJSON(JSONObject jsonData) throws JSONException {
        try {
            jsonData.getBoolean("rect");
            // a RectStroke
            return new RectStroke().fromJSON(jsonData);
        } catch (JSONException e) {
            // not a RectStroke
            id = UUID.fromString(jsonData.getString("id"));
            color = jsonData.getInt("color");
            strokeWidth = (float) jsonData.getDouble("width");
            style = Style.valueOf(jsonData.getString("style"));
            for (PointF point : parsePoints(jsonData))
                addAndDrawPoint(point, -1f);
            setBoundaries();
            return this;
        }
    }

    /**
     * Parses points from a JSON file and adds them to this stroke.
     * 
     * @param jsonData
     *            the data coming from file
     * @return a list containing all points in this stroke's path
     * @throws JSONException
     *             in case something is wrong with the file format
     */
    protected List<PointF> parsePoints(JSONObject jsonData)
            throws JSONException {
        List<PointF> newPoints = new ArrayList<PointF>();
        JSONArray array = jsonData.getJSONArray("points");
        if (array.length() > 0) {
            setStart(new PointF((float) array.getJSONArray(0).getDouble(0),
                    (float) array.getJSONArray(0).getDouble(1)));
        }
        for (int i = 1; i < array.length(); i++) {
            JSONArray point = array.getJSONArray(i);
            newPoints.add(new PointF((float) point.getDouble(0), (float) point
                    .getDouble(1)));
        }
        if (array.length() == 1)
            turnIntoDot();
        return newPoints;
    }

    private JSONArray pointsToList() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < points.size(); i++) {
            PointF point = points.get(i);
            array.put(new JSONArray(Arrays.asList(point.x, point.y)));
        }
        return array;
    }

}
