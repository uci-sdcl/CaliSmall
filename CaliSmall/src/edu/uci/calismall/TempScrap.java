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
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;

/**
 * A special scrap that is created whenever users close a path in the landing
 * zone.
 * 
 * <p>
 * A <tt>Temp</tt> gets promoted to a standard <tt>Scrap</tt> when the user
 * presses the <tt>scrap</tt> button on the {@link BubbleMenu}.
 * 
 * @author Michele Bonazza
 */
public class TempScrap extends Scrap {

    /**
     * The color in use to fill temporary scraps.
     */
    static final int REGION_COLOR = 0x55bcdbbc;
    private static final int HIGHLIGHTED_STROKE_COLOR = 0xffb2b2ff;
    private static final float HIGHLIGHTED_STROKE_WIDTH_MUL = 2.5f;
    private static final Paint HIGHLIGHT_PAINT = new Paint();
    private static final Paint TEMP_BORDER_PAINT = new Paint();
    private float dashInterval, pathPhase;
    private boolean dontTurnIntoGhost;

    static {
        HIGHLIGHT_PAINT.setAntiAlias(true);
        HIGHLIGHT_PAINT.setDither(true);
        HIGHLIGHT_PAINT.setStrokeJoin(Paint.Join.ROUND);
        HIGHLIGHT_PAINT.setStrokeCap(Paint.Cap.ROUND);
        HIGHLIGHT_PAINT.setColor(HIGHLIGHTED_STROKE_COLOR);
        HIGHLIGHT_PAINT.setStyle(Style.STROKE);
        TEMP_BORDER_PAINT.setColor(Color.BLACK);
        TEMP_BORDER_PAINT.setStyle(Style.STROKE);
    }

    /**
     * Creates a new temporary selection scrap.
     * 
     * @param selectionBorder
     *            the border enclosing the temporary scrap
     * @param scaleFactor
     *            the scale factor currently applied to the canvas
     */
    public TempScrap(Stroke selectionBorder, float scaleFactor) {
        super(selectionBorder, new ArrayList<Scrap>(), new ArrayList<Stroke>());
        regionColor = REGION_COLOR;
        dashInterval = CaliView.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
        outerBorder.mustBeDrawnVectorially(false);
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
    public TempScrap(Scrap copy, float scaleFactor) {
        super(copy, true);
        for (Stroke stroke : strokes) {
            // temp scraps are handled differently from regular ones
            stroke.previousParent = null;
        }
        regionColor = REGION_COLOR;
        dashInterval = CaliView.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
    }

    private void findSelected() {
        List<CaliSmallElement> candidates = parentView.getScrapList()
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
        candidates = parentView.getStrokeList()
                .findIntersectionCandidates(this);
        for (CaliSmallElement element : candidates) {
            Stroke stroke = (Stroke) element;
            if (!stroke.addedToSelection && !stroke.isGhost()) {
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
            highlightStroke(stroke, canvas, scaleFactor);
        }
        for (Scrap scrap : getAllScraps()) {
            scrap.drawHighlightedBorder(canvas, scaleFactor);
        }
    }

    private void
            highlightStroke(Stroke stroke, Canvas canvas, float scaleFactor) {
        if (stroke.getStyle() == Style.FILL) {
            PointF startPoint = stroke.getStartPoint();
            if (startPoint != null) {
                HIGHLIGHT_PAINT.setStrokeWidth(stroke.getStrokeWidth()
                        * HIGHLIGHTED_STROKE_WIDTH_MUL / scaleFactor);
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

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.calismall.Scrap#drawHighlightedBorder(android.graphics.Canvas ,
     * float)
     */
    @Override
    protected void drawHighlightedBorder(Canvas canvas, float scaleFactor) {
        // don't highlight, it's ugly!
        drawBorder(canvas, scaleFactor);
    }

    public void draw(CaliView parent, Canvas canvas, float scaleFactor,
            boolean drawBorder) {
        if (hasToBeDrawnVectorially() || snapshot == null) {
            highlight(canvas, scaleFactor);
            drawShadedRegion(canvas);
            if (drawBorder)
                drawBorder(canvas, scaleFactor);
        } else {
            canvas.drawBitmap(snapshot, snapshotMatrix, null);
        }
    }

    public void drawOnBitmap(Canvas canvas, Bitmap bitmap, float scaleFactor) {
        highlight(canvas, scaleFactor);
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

    protected void drawBorder(Canvas canvas, float scaleFactor) {
        dashInterval = CaliView.ABS_LANDING_ZONE_INTERVAL / scaleFactor;
        TEMP_BORDER_PAINT.setPathEffect(new DashPathEffect(new float[] {
                dashInterval, dashInterval }, pathPhase));
        pathPhase += 1 / scaleFactor;
        canvas.drawPath(outerBorder.getPath(), TEMP_BORDER_PAINT);
    }

    /**
     * Prevents this temp scrap's outer border from becoming a ghost upon
     * deselection.
     * 
     * @param doPause
     *            whether the effect should be paused
     */
    public void setGhostEffect(boolean doPause) {
        dontTurnIntoGhost = !doPause;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#deselect()
     */
    @Override
    public Stroke deselect() {
        super.deselect();
        // boolean mustUpdate = false;
        // remove link to child scraps, rollback
        for (Scrap scrap : scraps) {
            if (scrap.previousParent != null)
                ((Scrap) scrap.previousParent).add(scrap);
        }
        // RectF drawableArea = parentView.getDrawableArea();
        // final List<Stroke> toBeDeleted = new ArrayList<Stroke>(
        // strokes.size());
        for (Stroke stroke : strokes) {
            if (stroke.previousParent != null)
                ((Scrap) stroke.previousParent).add(stroke);
            // // delete strokes outside of the drawable area
            // if (stroke.filterOutOfBoundsPoints(drawableArea))
            // mustUpdate = true;
            // if (stroke.isEmpty())
            // toBeDeleted.add(stroke);
        }
        // for (Stroke stroke : toBeDeleted)
        // stroke.delete();
        // if (outerBorder.filterOutOfBoundsPoints(drawableArea))
        // mustUpdate = true;
        // if (mustUpdate)
        // parentView.forceRedraw();
        if (dontTurnIntoGhost)
            return null;
        return outerBorder;
    }

}