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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
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
public class Scrap extends CaliSmallElement implements JSONSerializable<Scrap> {

    /**
     * The paint object in use when drawing the bitmap snapshot of this scrap.
     */
    protected static final Paint SNAPSHOT_PAINT = new Paint();
    private static final int SCRAP_REGION_COLOR = 0x44d0e4f0;
    private static final int DESELECTED_BORDER_COLOR = 0xff6a899c;
    private static final int SELECTED_BORDER_COLOR = 0xffabb5fa;
    private static final int ABS_BORDER_THICKNESS = 3;
    private static final float ABS_SHRINK_BORDER_MARGIN = 20;
    private static final float ABS_SHRINK_BORDER_RADIUS = 10;
    private static final Paint PAINT = new Paint(), BORDER_PAINT = new Paint();
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

    /**
     * IDs of all scraps children of this scrap. Only used when creating a Scrap
     * from a JSON file.
     */
    protected List<String> scrapIDs;

    /**
     * The enclosing border of this scrap.
     */
    protected Stroke outerBorder;
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
     * The current rotation applied to the scrap.
     */
    protected float rotation = 0;
    /**
     * Whether this scrap is the one on which the user is operating a
     * transformation.
     */
    protected boolean topLevelForEdit;
    private boolean contentChanged = true;
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
        PAINT.setAntiAlias(true);
        PAINT.setDither(true);
        PAINT.setStrokeJoin(Paint.Join.ROUND);
        PAINT.setStrokeCap(Paint.Cap.ROUND);
        PAINT.setColor(TempScrap.REGION_COLOR);
        PAINT.setStyle(Style.FILL);
    }

    /**
     * Creates an empty scrap.
     * 
     * <p>
     * Used when deserializing data from JSON.
     * 
     * @param parentView
     *            the view within which this stroke lies
     */
    public Scrap(CaliView parentView) {
        super(parentView);
        snapshotMatrix = new Matrix();
        matrix = new Matrix();
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
        super(outerBorder.parentView);
        snapshotMatrix = new Matrix();
        matrix = new Matrix();
        rollbackMatrix = new Matrix();
        if (outerBorder instanceof RoundRectStroke)
            this.outerBorder = new RoundRectStroke(
                    (RoundRectStroke) outerBorder);
        else
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
        super(copy.parentView);
        snapshotMatrix = new Matrix();
        matrix = new Matrix();
        rollbackMatrix = new Matrix();
        if (copy.outerBorder instanceof RoundRectStroke)
            this.outerBorder = new RoundRectStroke(
                    (RoundRectStroke) copy.outerBorder);
        else
            this.outerBorder = new Stroke(copy.outerBorder);
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
        if (stroke != null && !strokes.contains(stroke)) {
            strokes.add(stroke);
            stroke.parent = this;
            stroke.previousParent = null;
            // refresh the snapshot the next time!
            contentChanged = true;
        }
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
                Scrap scrap = parentView.getScrapList().getById(id);
                add(scrap);
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
        if (scrap != null && scrap.id != id && !scraps.contains(scrap)) {
            Utils.debug("adding " + scrap.id + " to " + id);
            scraps.add(scrap);
            scrap.parent = this;
            scrap.previousParent = null;
            // refresh the snapshot the next time!
            contentChanged = true;
        }
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
        List<Stroke> allStrokes = new ArrayList<Stroke>();
        for (Iterator<Stroke> iterator = strokes.iterator(); iterator.hasNext();) {
            Stroke next = iterator.next();
            if (next.hasToBeDeleted()) {
                iterator.remove();
            } else {
                allStrokes.add(next);
            }
        }
        for (Scrap scrap : getAllScraps()) {
            for (Iterator<Stroke> iterator = scrap.strokes.iterator(); iterator
                    .hasNext();) {
                Stroke next = iterator.next();
                if (next.hasToBeDeleted()) {
                    iterator.remove();
                } else {
                    allStrokes.add(next);
                }
            }
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
        area.set(area.left, area.top, area.right, area.bottom);
        outerBorder = new RoundRectStroke(outerBorder, area, radius, margin);
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
        PAINT.setStyle(Style.FILL);
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
        BORDER_PAINT.setColor(DESELECTED_BORDER_COLOR);
        BORDER_PAINT.setStrokeWidth((ABS_BORDER_THICKNESS / scaleFactor) / 2);
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
    protected void drawHighlightedBorder(Canvas canvas, float scaleFactor) {
        BORDER_PAINT.setColor(SELECTED_BORDER_COLOR);
        BORDER_PAINT.setStrokeWidth(2 * (ABS_BORDER_THICKNESS / scaleFactor));
        canvas.drawPath(outerBorder.getPath(), BORDER_PAINT);
        drawBorder(canvas, scaleFactor);
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
        rotation += angle;
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
     * Deselects this scrap, meaning that it won't be drawn as highlighted
     * anymore. The bitmap snapshot created to be shown while editing this scrap
     * is dropped and will only be recreated if the scrap is selected and edited
     * once again.
     * 
     * @return a stroke that must be added to the lists of strokes kept by
     *         {@link CaliView}, is not <code>null</code> only for temp scraps
     */
    public Stroke deselect() {
        // free up some space
        snapshot = null;
        return null;
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
        delete();
        mustBeDrawnVectorially(false);
        outerBorder.delete();
        for (Stroke stroke : getAllStrokes()) {
            stroke.delete();
            if (stroke.parent != null) {
                Scrap parentScrap = (Scrap) stroke.parent;
                parentScrap.remove(stroke);
            }
        }
        for (Scrap scrap : getAllScraps()) {
            scrap.delete();
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
     * Drawing a scrap means drawing the shaded region that highlights its
     * content and, optionally, the border sorrounding the scrap.
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
     * @param drawBorder
     *            whether the scrap's border must be drawn
     */
    public void draw(CaliView parent, Canvas canvas, float scaleFactor,
            boolean drawBorder) {
        if (hasToBeDrawnVectorially() || (topLevelForEdit && snapshot == null)) {
            drawShadedRegion(canvas);
            drawBorder(canvas, scaleFactor);
            for (int i = 0; i < scraps.size(); i++) {
                Scrap scrap = scraps.get(i);
                scrap.draw(parent, canvas, scaleFactor, drawBorder);
            }
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
     */
    public void startEditing(float scaleFactor) {
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
        parentView.forceRedraw();
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
        for (Stroke stroke : getAllStrokes()) {
            stroke.transform(matrix);
            stroke.mustBeDrawnVectorially(true);
        }
        mustBeDrawnVectorially(true);
        for (Scrap scrap : getAllScraps()) {
            scrap.transformationEnded(matrix);
        }
        snapshotMatrix.reset();
        matrix.reset();
        setBoundaries();
        contentChanged = forceSnapshotRedraw;
        parentView.setHighlighted(this);
        parentView.forceRedraw();
    }

    /**
     * Called by the top level scraps on all its children after a transformation
     * is done.
     * 
     * @param matrix
     *            the matrix to be applied
     */
    public void transformationEnded(Matrix matrix) {
        outerBorder.transform(matrix);
        mustBeDrawnVectorially(true);
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

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.CaliSmallElement#updateSpaceOccupation()
     */
    @Override
    public void updateSpaceOccupation() {
        parentView.getScrapList().update(this);
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

    /**
     * Forces a redraw of the bitmap snapshot for this scrap.
     */
    public void forceBitmapRedraw() {
        contentChanged = true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#toJSON()
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("b", outerBorder.toJSON());
        JSONArray array = new JSONArray();
        if (!strokes.isEmpty()) {
            for (int i = 0; i < strokes.size(); i++) {
                array.put(strokes.get(i).id);
            }
            json.put("str", array);
        }
        array = new JSONArray();
        if (!scraps.isEmpty()) {
            for (int i = 0; i < scraps.size(); i++) {
                array.put(scraps.get(i).id);
            }
            json.put("scr", array);
        }
        return json;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#fromJSON(org.json.JSONObject)
     */
    @Override
    public Scrap fromJSON(JSONObject jsonData) throws JSONException {
        try {
            jsonData.getBoolean("i");
            // an ImageStroke, update its boundaries once image is loaded
            return new ImageScrap(parentView).fromJSON(jsonData);
        } catch (JSONException e) {
            // not an ImageStroke
            id = jsonData.getLong("id");
            JSONObject border = jsonData.getJSONObject("b");
            if (border.has("r")) {
                outerBorder = new RoundRectStroke(parentView).fromJSON(border);
            } else
                outerBorder = new Stroke(parentView).fromJSON(border);
            try {
                JSONArray array = jsonData.getJSONArray("str");
                for (int i = 0; i < array.length(); i++) {
                    Stroke stroke = parentView.getStrokeList().getById(
                            array.getString(i));
                    add(stroke);
                }
            } catch (JSONException e1) { /* it's ok, no strokes */
            }
            try {
                JSONArray array = jsonData.getJSONArray("scr");
                scrapIDs = new ArrayList<String>(array.length());
                for (int i = 0; i < array.length(); i++) {
                    scrapIDs.add(array.getString(i));
                }
            } catch (JSONException e1) { /* it's ok, no scraps */
            }
            setBoundaries();
        }
        return this;
    }

}
