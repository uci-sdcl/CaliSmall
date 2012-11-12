/**
 * GhostStrokeHandler.java Created on Oct 22, 2012 Copyright 2012 Michele
 * Bonazza <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;

/**
 * Intercepts touch events that may involve ghost stroke "revive" buttons.
 * 
 * <p>
 * Whenever a temporary scrap is deselected, its outer border is displayed using
 * a solid line (as opposed to the dotted one in use for temporary scraps) and a
 * small pencil is shown on its top-leftmost point. Both the line and the pencil
 * start fading; if the pencil button is clicked before it's completed fading
 * out, the stroke is "revived", meaning that it's drawn with its original solid
 * color. This handler checks whether a touch event involves any of these ghost
 * strokes currently displayed.
 * 
 * @author Michele Bonazza
 * 
 */
public class GhostStrokeHandler extends GenericTouchHandler {

    private final List<Stroke> ghosts;
    private Stroke touchedGhost;

    /**
     * Creates a new handler that takes care of touch events whenever ghost
     * strokes are being displayed.
     * 
     * @param parent
     *            the parent view
     */
    public GhostStrokeHandler(CaliView parent) {
        super("GhostStrokeHandler", parent);
        ghosts = new ArrayList<Stroke>();
    }

    /**
     * Adds the argument stroke to the list of ghost strokes.
     * 
     * @param ghost
     *            the stroke to be marked as a ghost
     */
    public void addGhost(Stroke ghost) {
        ghosts.add(ghost);
        ghost.setGhost(true, parentView.screenBounds,
                parentView.getResources(),
                parentView.bubbleMenu.getButtonSize());
    }

    /**
     * Draws all ghost strokes to the argument <tt>canvas</tt>, using the
     * argument <tt>paint</tt>.
     * 
     * @param canvas
     *            the canvas onto which ghost strokes will be drawn
     * @param paint
     *            the pain with which to draw ghost strokes
     */
    public void drawGhosts(Canvas canvas, Paint paint) {
        for (int i = 0; i < ghosts.size(); i++) {
            Stroke ghost = ghosts.get(i);
            if (ghost.isGhost()) {
                ghost.draw(canvas, paint);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.calismall.GenericTouchHandler#onDown(android.graphics.PointF)
     */
    @Override
    public boolean onDown(PointF touchPoint) {
        for (Iterator<Stroke> iterator = ghosts.iterator(); iterator.hasNext();) {
            Stroke next = iterator.next();
            if (next.ghostButtonTouched(touchPoint) != null) {
                touchedGhost = next;
                return true;
            }
        }
        return false;
    }

    /**
     * Sets whether ghost fade out animations must be executed, and whether all
     * ghost revive buttons for all ghost strokes must be drawn.
     * 
     * @param isPaused
     *            <code>true</code> if ghost animations should be paused and
     *            revive buttons not be drawn
     */
    public void setGhostAnimationOnPause(boolean isPaused) {
        if (isPaused) {
            for (Iterator<Stroke> iterator = ghosts.iterator(); iterator
                    .hasNext();) {
                Stroke next = iterator.next();
                if (next.isGhost())
                    next.setGhostAnimationOnPause(isPaused, null, -1f);
            }
        } else {
            for (Iterator<Stroke> iterator = ghosts.iterator(); iterator
                    .hasNext();) {
                Stroke next = iterator.next();
                if (next.isGhost())
                    next.setGhostAnimationOnPause(isPaused,
                            parentView.screenBounds,
                            parentView.bubbleMenu.getButtonSize());
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.calismall.GenericTouchHandler#onMove(android.graphics.PointF)
     */
    @Override
    public boolean onMove(PointF touchPoint) {
        return touchedGhost != null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.GenericTouchHandler#onUp(android.graphics.PointF)
     */
    @Override
    public boolean onUp(PointF touchPoint) {
        if (touchedGhost != null) {
            touchedGhost.setGhost(false, null, null, 0);
            touchedGhost.mustBeDrawnVectorially(true);
            parentView.addStroke(touchedGhost);
            touchedGhost = null;
            return true;
        }
        return false;
    }

    /**
     * Deletes all ghost strokes marked as to be deleted (because they faded
     * out).
     */
    public void deleteOldStrokes() {
        for (Iterator<Stroke> iterator = ghosts.iterator(); iterator.hasNext();) {
            Stroke next = iterator.next();
            if (next.hasToBeDeleted() || !next.isGhost()) {
                iterator.remove();
            }
        }
    }
}
