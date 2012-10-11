/**
 * Scrap.java Created on Aug 11, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import android.graphics.drawable.shapes.RoundRectShape;
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
public class Scrap extends CaliSmallElement implements JSONSerializable {

    /**
     * The kind of transformation that is applied to a scrap (and all its
     * content).
     * 
     * @author Michele Bonazza
     */
    public static enum Transformation {
        /**
         * A translation along the (x, y) axes.
         */
        TRANSLATION, /**
         * A resize transformation that scales all points of this
         * scrap (and those of all its children).
         */
        RESIZE
    }

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
     * menu that is applied to the bitmap snapshot representing this scrap.
     */
    protected final Matrix snapshotMatrix;

    /**
     * The transformation matrix in use when modifying a scrap through bubble
     * menu that is applied to the content of the scrap.
     */
    protected final Matrix matrix;

    /**
     * The transformation matrix that keeps track of how to roll back from the
     * last transformation matrix that has been applied to the scrap's border.
     * This is kept to handle delta-like actions, since
     * {@link Path#transform(Matrix)} only works with incremental updates.
     */
    protected final Matrix rollbackMatrix;

    /**
     * All scraps children (but not grand-children) of this scrap.
     */
    protected final List<Scrap> scraps;
    /**
     * All strokes belonging to this scrap.
     */
    protected final List<Stroke> strokes;

    private List<String> scrapIDs;

    /**
     * The enclosing border of this scrap.
     */
    protected Stroke outerBorder;
    /**
     * Whether this scrap is currently selected.
     */
    protected boolean selected;
    /**
     * The bitmap to which strokes are temporarily painted when editing the
     * scrap.
     */
    protected Bitmap snapshot;
    /**
     * The color with which to fill the area of this scrap.
     */
    protected int regionColor = SCRAP_REGION_COLOR;

    /**
     * The transformation matrix in use when modifying a scrap through bubble
     * menu that is applied to the content of the topmost scrap.
     */
    protected Matrix contentMatrix;
    private boolean topLevelForEdit, contentChanged = true;
    private float snapOffsetX, snapOffsetY;

    static {
        BORDER_PAINT.setAntiAlias(true);
        BORDER_PAINT.setDither(true);
        BORDER_PAINT.setStrokeJoin(Paint.Join.ROUND);
        BORDER_PAINT.setStrokeCap(Paint.Cap.ROUND);
        BORDER_PAINT.setColor(SELECTED_BORDER_COLOR);
        BORDER_PAINT.setStyle(Style.STROKE);
        SNAPSHOT_PAINT.setAntiAlias(true);
        SNAPSHOT_PAINT.setDither(true);
        SNAPSHOT_PAINT.setStrokeJoin(Paint.Join.ROUND);
        SNAPSHOT_PAINT.setStrokeCap(Paint.Cap.ROUND);
        SNAPSHOT_PAINT.setColor(SELECTED_BORDER_COLOR);
        SNAPSHOT_PAINT.setStyle(Style.STROKE);
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
     * Creates an empty scrap.
     * 
     * <p>
     * Used when deserializing data from JSON.
     */
    public Scrap() {
        snapshotMatrix = new Matrix();
        matrix = new Matrix();
        contentMatrix = new Matrix();
        rollbackMatrix = new Matrix();
        strokes = new ArrayList<Stroke>();
        scraps = new ArrayList<Scrap>();
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
     */
    public Scrap(Stroke outerBorder, List<Scrap> scraps, List<Stroke> strokes) {
        snapshotMatrix = new Matrix();
        matrix = new Matrix();
        contentMatrix = new Matrix();
        rollbackMatrix = new Matrix();
        this.outerBorder = new Stroke(outerBorder);
        this.scraps = scraps;
        this.strokes = strokes;
        outerBorder.getPath().close();
        outerBorder.getPath().setFillType(FillType.WINDING);
        setBoundaries();
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
        snapshotMatrix = new Matrix();
        matrix = new Matrix();
        contentMatrix = new Matrix();
        rollbackMatrix = new Matrix();
        outerBorder = new Stroke(copy.outerBorder);
        if (deepCopy) {
            this.scraps = new ArrayList<Scrap>(copy.scraps.size());
            this.strokes = new ArrayList<Stroke>(copy.strokes.size());
            copyContent(copy);
        } else {
            this.scraps = new ArrayList<Scrap>(copy.scraps);
            this.strokes = new ArrayList<Stroke>(copy.strokes);
            this.outerBorder.getPath().close();
            outerBorder.getPath().setFillType(FillType.WINDING);
            // all strokes are now in this scrap
            for (Stroke stroke : strokes) {
                stroke.parent = this;
                stroke.previousParent = null;
            }
            for (Scrap scrap : scraps) {
                scrap.parent = this;
                scrap.previousParent = null;
            }
        }
        setBoundaries();
    }

    /**
     * Adds the argument <tt>stroke</tt> to this scrap.
     * 
     * @param stroke
     *            the stroke to be added to this scrap
     */
    public void add(Stroke stroke) {
        strokes.add(stroke);
        stroke.parent = this;
        stroke.previousParent = null;
        // refresh the snapshot the next time!
        contentChanged = true;
    }

    /**
     * Adds all of the strokes in the argument list to this scrap.
     * 
     * @param strokes
     *            a list of strokes to be added to this scrap
     */
    public void addAll(List<Stroke> strokes) {
        this.strokes.addAll(strokes);
        for (Stroke stroke : strokes) {
            stroke.parent = this;
            stroke.previousParent = null;
        }
        // refresh the snapshot the next time!
        contentChanged = true;
    }

    /**
     * Adds all scraps that are children of this scrap according to the data
     * stored by JSON.
     * 
     * <p>
     * After adding all children scraps, this method clears the list of Strings
     * parsed by JSON to save space.
     */
    public void addChildrenFromJSON() {
        if (scrapIDs != null) {
            for (String id : scrapIDs) {
                scraps.add((Scrap) Scrap.SPACE_OCCUPATION_LIST.getById(id));
            }
            scrapIDs = null;
        }
    }

    /**
     * Adds the argument <tt>scrap</tt> as a child of this scrap.
     * 
     * @param scrap
     *            the child scrap to be added to this scrap
     */
    public void add(Scrap scrap) {
        scraps.add(scrap);
        scrap.parent = this;
        scrap.previousParent = null;
        // refresh the snapshot the next time!
        contentChanged = true;
    }

    /**
     * Removes the argument <tt>stroke</tt> from this scrap.
     * 
     * @param stroke
     *            the stroke to be removed from this scrap
     */
    public void remove(Stroke stroke) {
        stroke.parent = null;
        if (strokes.remove(stroke)) {
            stroke.previousParent = this;
            // refresh the snapshot the next time!
            contentChanged = true;
        }
    }

    /**
     * Removes the argument <tt>scrap</tt> from the list of children of this
     * scrap.
     * 
     * @param scrap
     *            the child scrap to be removed from the list of children of
     *            this scrap
     */
    public void remove(Scrap scrap) {
        scrap.parent = null;
        if (scraps.remove(scrap)) {
            scrap.previousParent = this;
            // refresh the snapshot the next time!
            contentChanged = true;
        }
    }

    /**
     * Removes all the elements in the argument list from this scrap.
     * 
     * @param <T>
     *            the type of elements to be removed from this scrap
     * 
     * @param elements
     *            the strokes to be removed from this scrap
     * @param type
     *            the type of elements to be removed
     */
    @SuppressWarnings("unchecked")
    public <T extends CaliSmallElement> void removeAll(List<T> elements,
            Class<T> type) {
        // TODO refactor so that the unchecked cast is not needed
        if (type.equals(Stroke.class)) {
            for (Stroke stroke : (List<Stroke>) elements) {
                remove(stroke);
            }
        } else {
            for (Scrap scrap : (List<Scrap>) elements) {
                remove(scrap);
            }
        }
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
            add(newCopy);
        }
        for (Stroke stroke : copy.strokes) {
            Stroke newStroke = new Stroke(stroke);
            add(newStroke.setBoundaries());
        }
    }

    /**
     * Tests whether the argument point is within the area of this scrap.
     * 
     * @param point
     *            the point to be tested
     * @return <code>true</code> if the point is not <code>null</code> and is
     *         within this scrap's area
     */
    public boolean contains(PointF point) {
        if (point == null)
            return false;
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
     * Returns the list of all scraps contained within this scrap, including all
     * descendent scraps.
     * 
     * @return all scraps that are descendents of this scrap
     */
    public List<Scrap> getAllScraps() {
        List<Scrap> allScraps = new ArrayList<Scrap>(scraps);
        for (Scrap scrap : scraps) {
            allScraps.addAll(scrap.getAllScraps());
        }
        return allScraps;
    }

    /**
     * Returns all strokes that are part of this scrap, including strokes that
     * are part of scraps that are children of this scrap.
     * 
     * @return all strokes children of this scrap
     */
    public List<Stroke> getAllStrokes() {
        List<Stroke> allStrokes = new ArrayList<Stroke>(strokes);
        for (Scrap scrap : getAllScraps()) {
            allStrokes.addAll(scrap.strokes);
        }
        return allStrokes;
    }

    /**
     * Returns a list of all strokes whose parent is this scrap.
     * 
     * <p>
     * The list is not defensively copied, so editing the return object will
     * alter this scrap's internal status.
     * 
     * @return this scrap's children strokes
     */
    public List<Stroke> getStrokes() {
        return strokes;
    }

    /**
     * Returns a list of all scraps whose parent is this scrap.
     * 
     * <p>
     * The list is not defensively copied, so editing the return object will
     * alter this scrap's internal status.
     * 
     * @return this scrap's children scraps
     */
    public List<Scrap> getScraps() {
        return scraps;
    }

    /**
     * Replaces the outer border with the smallest {@link RoundRectShape} that
     * contains all strokes within this scrap.
     * 
     * @param scaleFactor
     *            the current scale factor applied to the canvas
     */
    public void setRect(float scaleFactor) {
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
        outerBorder.setRoundRect(collage, area, radius);
        setBoundaries();
        contentChanged = true;
    }

    /**
     * Draws the internal region of this scrap.
     * 
     * @param canvas
     *            the canvas onto which the scrap is to be drawn
     */
    protected void drawShadedRegion(Canvas canvas) {
        PAINT.setColor(regionColor);
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
     * Returns a description of the content of the argument <tt>scrap</tt>, with
     * one element in each line, grouped by type (scraps vs. strokes).
     * 
     * @param scrap
     *            the scrap of which the representation has to be created
     * @return a String containing the representation for the argument scrap,
     *         ready to be logged
     */
    public static String getContentToLog(Scrap scrap) {
        StringBuilder builder = new StringBuilder(" content:\n***BORDER***\n");
        builder.append(scrap.outerBorder);
        builder.append("\n***SCRAPS***\n");
        String newLine = "\n";
        for (Scrap child : scrap.scraps) {
            builder.append(child);
            builder.append(newLine);
        }
        builder.append("***STROKES***\n");
        newLine = "";
        for (Stroke stroke : scrap.strokes) {
            builder.append(newLine);
            builder.append(stroke);
            newLine = "\n";
        }
        return builder.toString();
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
     * Rotates this scrap by the argument values.
     * 
     * @param angle
     *            the rotation angle, in degrees
     * @param rotationPivot
     *            the point which is used as pivot when rotating the scrap
     */
    public void rotate(float angle, PointF rotationPivot) {
        snapshotMatrix.postConcat(rollbackMatrix);
        snapshotMatrix.preTranslate(-rotationPivot.x, -rotationPivot.y);
        snapshotMatrix.postRotate(angle, rotationPivot.x, rotationPivot.y);
        snapshotMatrix.preTranslate(rotationPivot.x, rotationPivot.y);
        matrix.setRotate(angle, rotationPivot.x, rotationPivot.y);
        outerBorder.transform(rollbackMatrix);
        outerBorder.transform(matrix);
        setBoundaries();
        rollbackMatrix.setRotate(-angle, rotationPivot.x, rotationPivot.y);
    }

    /**
     * Resizes this scrap by the argument values.
     * 
     * @param scaleX
     *            the scale value along the X-axis
     * @param scaleY
     *            the scale value along the Y-axis
     * @param scalePivot
     *            the point which is used as pivot when scaling the scrap
     * @param centerOffset
     */
    public void scale(float scaleX, float scaleY, PointF scalePivot,
            PointF centerOffset) {
        snapshotMatrix.postConcat(rollbackMatrix);
        snapshotMatrix.preScale(scaleX, scaleY);
        matrix.setScale(scaleX, scaleY, scalePivot.x, scalePivot.y);
        outerBorder.transform(rollbackMatrix);
        outerBorder.transform(matrix);
        rollbackMatrix.setScale(1 / scaleX, 1 / scaleY, scalePivot.x,
                scalePivot.y);
        setBoundaries();
    }

    /**
     * Sets whether this scrap is the selected one.
     */
    public void select() {
        this.selected = true;
    }

    /**
     * Deselects this scrap, meaning that it won't be drawn as highlighted
     * anymore. The bitmap snapshot created to be shown while editing this scrap
     * is dropped and will only be recreated if the scrap is selected and edited
     * once again.
     */
    public void deselect() {
        this.selected = false;
        // free up some space
        snapshot = null;
    }

    /**
     * Removes the link to this scrap from the parent scrap and marks all
     * strokes and children scraps as <tt>toBeDeleted</tt>, so that the drawing
     * thread can remove them from {@link CaliSmall} lists.
     */
    public void erase() {
        if (parent != null) {
            Scrap parentScrap = (Scrap) parent;
            parentScrap.remove(this);
            parent = null;
        }
        toBeDeleted = true;
        mustBeDrawnVectorially(false);
        outerBorder.toBeDeleted = true;
        for (Stroke stroke : getAllStrokes()) {
            stroke.toBeDeleted = true;
            if (stroke.parent != null) {
                Scrap parentScrap = (Scrap) stroke.parent;
                parentScrap.remove(stroke);
            }
        }
        for (Scrap scrap : getAllScraps()) {
            scrap.toBeDeleted = true;
            if (scrap.parent != null) {
                Scrap parentScrap = (Scrap) scrap.parent;
                parentScrap.remove(scrap);
            }
        }
    }

    private void changeDrawingStatus(boolean mustBeDrawn) {
        mustBeDrawnVectorially(mustBeDrawn);
        outerBorder.mustBeDrawnVectorially(mustBeDrawn);
        for (Stroke stroke : strokes) {
            stroke.mustBeDrawnVectorially(mustBeDrawn);
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
        snapshotMatrix.postConcat(rollbackMatrix);
        snapshotMatrix.postTranslate(dx, dy);
        matrix.setTranslate(dx, dy);
        outerBorder.transform(rollbackMatrix);
        outerBorder.transform(matrix);
        setBoundaries();
        rollbackMatrix.setTranslate(-dx, -dy);
    }

    /**
     * Draws this scrap to the argument <tt>canvas</tt>.
     * 
     * <p>
     * Drawing a scrap means drawing:
     * <ol>
     * <li>the border delimiting the scrap</li>
     * <li>the shaded region that highlights the content of the scrap</li>
     * </ol>.
     * 
     * <p>
     * The content of a scrap is drawn using the objects in {@link CaliSmall}'s
     * lists, and never from the lists kept within the scrap object. The only
     * exception is the scrap's outer border, which is never stored by the
     * parent {@link CaliSmall} object, as it's treated in a different way from
     * how regular {@link Stroke}'s are.
     * 
     * @param parent
     *            the main {@link View} of the application
     * @param canvas
     *            the canvas onto which this scrap must be drawn
     * @param scaleFactor
     *            the current scale factor applied to the canvas
     */
    public void
            draw(CaliSmall.CaliView parent, Canvas canvas, float scaleFactor) {
        if (hasToBeDrawnVectorially() || (topLevelForEdit && snapshot == null)) {
            drawShadedRegion(canvas);
            drawBorder(canvas, scaleFactor);
        } else if (topLevelForEdit) {
            canvas.drawBitmap(snapshot, snapshotMatrix, null);
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
        drawShadedRegion(canvas);
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
        outerBorder.setBoundaries();
        super.setBoundaries(outerBorder.getPath());
    }

    /**
     * Updates the parent of this scrap to the argument <tt>parent</tt>.
     * 
     * @param parent
     *            this scrap's new parent
     */
    public void setParent(CaliSmallElement parent) {
        previousParent = this.parent;
        this.parent = parent;
    }

    /**
     * Prepares this scrap for editing.
     * 
     * <p>
     * Must be called before starting an edit operation on the scrap.
     * 
     * @param scaleFactor
     *            the current scale factor
     * @param transformationType
     *            the type of transformation that is about to be applied to this
     *            scrap
     */
    public void startEditing(float scaleFactor,
            Transformation transformationType) {
        topLevelForEdit = true;
        rollbackMatrix.reset();
        setBoundaries();
        Rect size = getBounds();
        snapOffsetX = size.left;
        snapOffsetY = size.top;
        if (contentChanged || snapshot == null) {
            // create a new bitmap as large as the scrap
            Bitmap snapshot = Bitmap.createBitmap(size.width(), size.height(),
                    Config.ARGB_8888);
            // move the bitmap over the scrap
            Canvas snapshotCanvas = new Canvas(snapshot);
            snapshotCanvas.translate(-snapOffsetX, -snapOffsetY);
            // draw scrap content to the bitmap
            drawOnBitmap(snapshotCanvas, snapshot, scaleFactor);
            this.snapshot = snapshot;
            contentChanged = false;
        }
        snapshotMatrix.postTranslate(snapOffsetX, snapOffsetY);
        // use the bitmap snapshot until applyTransform() is called
        changeDrawingStatus(false);
        switch (transformationType) {
        case TRANSLATION:
            contentMatrix = snapshotMatrix;
            break;
        case RESIZE:
            contentMatrix = new Matrix();
        }
    }

    /**
     * Applies the transformations set for this scrap to all of its contents.
     * 
     * @param forceSnapshotRedraw
     *            whether the bitmap snapshot for this scrap should be redrawn
     *            (i.e. whether the transformation altered the way this scrap
     *            looks)
     */
    public void applyTransform(boolean forceSnapshotRedraw) {
        topLevelForEdit = false;
        outerBorder.mustBeDrawnVectorially(true);
        outerBorder.fixRadius(matrix);
        for (Stroke stroke : getAllStrokes()) {
            stroke.transform(matrix);
            stroke.mustBeDrawnVectorially(true);
        }
        mustBeDrawnVectorially(true);
        for (Scrap scrap : getAllScraps()) {
            scrap.outerBorder.transform(matrix);
            scrap.mustBeDrawnVectorially(true);
            scrap.setBoundaries();
        }
        snapshotMatrix.reset();
        matrix.reset();
        setBoundaries();
        contentChanged = forceSnapshotRedraw;
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
        List<Scrap> allScraps = getAllScraps();
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
        List<Scrap> allScraps = getAllScraps();
        Collections.sort(allScraps);
        for (Scrap test : allScraps) {
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
                    new ArrayList<Stroke>());
            regionColor = TEMP_SCRAP_REGION_COLOR;
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
            for (Stroke stroke : strokes) {
                // temp scraps are handled differently from regular ones
                stroke.previousParent = null;
            }
            regionColor = TEMP_SCRAP_REGION_COLOR;
            dashInterval = CaliSmall.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
        }

        private void findSelected() {
            List<CaliSmallElement> candidates = SPACE_OCCUPATION_LIST
                    .findIntersectionCandidates(this);
            Collections.sort(candidates);
            List<Scrap> allScrapsInSelection = new ArrayList<Scrap>();
            List<Stroke> allStrokesInSelection = new ArrayList<Stroke>();
            // iterate from largest to smallest
            for (int i = candidates.size() - 1; i > -1; i--) {
                Scrap scrap = (Scrap) candidates.get(i);
                if (!scrap.addedToSelection) {
                    if (outerBorder.contains(scrap.outerBorder)) {
                        scraps.add(scrap);
                        allScrapsInSelection.add(scrap);
                        if (scrap.parent != null) {
                            ((Scrap) scrap.parent).remove(scrap);
                        } else {
                            scrap.previousParent = null;
                        }
                        scrap.parent = this;
                        List<Scrap> newScraps = scrap.getAllScraps();
                        List<Stroke> newStrokes = scrap.getAllStrokes();
                        CaliSmallElement.setAllAddedToSelection(newScraps);
                        CaliSmallElement.setAllAddedToSelection(newStrokes);
                        allScrapsInSelection.addAll(newScraps);
                        allStrokesInSelection.addAll(newStrokes);
                    }
                }
            }
            CaliSmallElement.resetSelectionStatus(allScrapsInSelection);
            candidates = Stroke.SPACE_OCCUPATION_LIST
                    .findIntersectionCandidates(this);
            for (CaliSmallElement element : candidates) {
                Stroke stroke = (Stroke) element;
                if (!stroke.addedToSelection) {
                    if (outerBorder.contains(stroke)) {
                        strokes.add(stroke);
                        allStrokesInSelection.add(stroke);
                        stroke.addedToSelection = true;
                        if (stroke.parent != null) {
                            ((Scrap) stroke.parent).remove(stroke);
                        } else {
                            stroke.previousParent = null;
                        }
                        stroke.parent = this;
                    }
                }
            }
            CaliSmallElement.resetSelectionStatus(allStrokesInSelection);
        }

        private void highlight(Canvas canvas, float scaleFactor) {
            for (Stroke stroke : strokes) {
                if (stroke.parent == this) {
                    if (stroke.getStyle() == Style.FILL) {
                        PointF startPoint = stroke.getStartPoint();
                        if (startPoint != null) {
                            HIGHLIGHT_PAINT.setStrokeWidth(stroke
                                    .getStrokeWidth()
                                    * HIGHLIGHTED_STROKE_WIDTH_MUL
                                    / scaleFactor);
                            HIGHLIGHT_PAINT.setStyle(Style.FILL);
                            canvas.drawCircle(startPoint.x, startPoint.y,
                                    stroke.getStrokeWidth(), HIGHLIGHT_PAINT);
                        }
                        HIGHLIGHT_PAINT.setStyle(Style.STROKE);
                    } else {
                        HIGHLIGHT_PAINT.setStrokeWidth(stroke.getStrokeWidth()
                                * HIGHLIGHTED_STROKE_WIDTH_MUL);
                        canvas.drawPath(stroke.getPath(), HIGHLIGHT_PAINT);
                    }
                }
            }
            for (Scrap scrap : getAllScraps()) {
                scrap.highlightBorder(canvas, scaleFactor);
            }
        }

        public void draw(CaliSmall.CaliView parent, Canvas canvas,
                float scaleFactor) {
            if (selected) {
                if (hasToBeDrawnVectorially() || snapshot == null) {
                    highlight(canvas, scaleFactor);
                    drawShadedRegion(canvas);
                    drawBorder(canvas, scaleFactor);
                } else {
                    canvas.drawBitmap(snapshot, snapshotMatrix, null);
                }
            }
        }

        protected void drawBorder(Canvas canvas, float scaleFactor) {
            TEMP_BORDER_PAINT.setPathEffect(new DashPathEffect(new float[] {
                    dashInterval, dashInterval }, pathPhase));
            pathPhase += 1 / scaleFactor;
            canvas.drawPath(outerBorder.getPath(), TEMP_BORDER_PAINT);
        }

        /*
         * (non-Javadoc)
         * 
         * @see edu.uci.calismall.Scrap#deselect()
         */
        @Override
        public void deselect() {
            super.deselect();
            // remove link to child scraps, rollback
            for (Scrap scrap : scraps) {
                if (scrap.previousParent != null)
                    ((Scrap) scrap.previousParent).add(scrap);
            }
            for (Stroke stroke : strokes) {
                if (stroke.previousParent != null)
                    ((Scrap) stroke.previousParent).add(stroke);
            }
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
     * @see edu.uci.calismall.CaliSmallElement#getPointsForInclusionTests()
     */
    @Override
    public List<PointF> getPointsForInclusionTests() {
        return outerBorder.getPointsForInclusionTests();
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
        json.put("border", outerBorder.toJSON());
        JSONArray array = new JSONArray();
        if (!strokes.isEmpty()) {
            for (Stroke stroke : strokes) {
                array.put(stroke.id.toString());
            }
            json.put("strokes", array);
        }
        if (!scraps.isEmpty()) {
            for (Scrap scrap : scraps) {
                array.put(scrap.id.toString());
            }
            json.put("scraps", array);
        }
        return json;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#fromJSON(org.json.JSONObject)
     */
    @Override
    public void fromJSON(JSONObject jsonData) throws JSONException {
        id = UUID.fromString(jsonData.getString("id"));
        outerBorder = new Stroke();
        outerBorder.fromJSON(jsonData.getJSONObject("border"));
        try {
            JSONArray array = jsonData.getJSONArray("strokes");
            for (int i = 0; i < array.length(); i++) {
                Stroke stroke = (Stroke) Stroke.SPACE_OCCUPATION_LIST
                        .getById(array.getString(i));
                if (stroke != null)
                    strokes.add(stroke);
            }
        } catch (JSONException e) { /* it's ok, no strokes */}
        try {
            JSONArray array = jsonData.getJSONArray("scraps");
            scrapIDs = new ArrayList<String>(array.length());
            for (int i = 0; i < array.length(); i++) {
                scrapIDs.add(array.getString(i));
            }
        } catch (JSONException e) { /* it's ok, no scraps */}
        setBoundaries();
    }

}
