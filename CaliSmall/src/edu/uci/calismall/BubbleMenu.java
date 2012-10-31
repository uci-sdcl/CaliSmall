/**
 * BubbleMenu.java Created on Aug 8, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.util.FloatMath;
import android.view.MotionEvent;
import edu.uci.calismall.Scrap.Transformation;

/**
 * A pop-up menu shown when the user "closes" a path creating a scrap selection.
 * 
 * @author Michele Bonazza
 */
public class BubbleMenu extends GenericTouchHandler {

    // TODO decouple this class from the CaliSmall object

    private interface ButtonListener {
        boolean touched(int action, PointF touchPoint, Scrap selected);
    }

    private abstract class ClickableButtonListener implements ButtonListener {

        private final Button parent;
        private boolean moved = false;

        private ClickableButtonListener(Button parent) {
            this.parent = parent;
        }

        /**
         * Returns <code>true</code> only when the user has clicked this button.
         * 
         * <p>
         * A button is considered clicked when no
         * {@link MotionEvent#ACTION_MOVE} events are detected by this listener
         * or when they are detected but all of the <tt>touchPoint</tt>'s passed
         * as argument since the first call to this method are within the area
         * of the button this listener is attached to.
         * 
         * 
         * <p>
         * This implementation is designed for overriding: if not overridden
         * this method <b>always returns <code>true</code> unless no
         * {@link MotionEvent#ACTION_MOVE} events are detected and a
         * {@link MotionEvent#ACTION_UP} is detected</b>.
         * 
         * <p>
         * Subclasses should override this method like this: <blockquote>
         * 
         * <pre>
         * &#064;Override
         * public boolean touched(int action, PointF touchPoint, Scrap selected) {
         *     return super.touched(action, touchPoint, selected)
         *             || myMethod(action, touchPoint, selected);
         * }
         * </pre>
         * 
         * </blockquote>
         * 
         * so that <tt>myMethod()</tt> (which is the method that actually
         * performs the action associated with the button this listener is
         * attached to) is called only when a click has been detected.
         * 
         * <p>
         * This way the overridden implementation always returns
         * <code>true</code> as long as the action is not "over" (i.e. until a
         * {@link MotionEvent#ACTION_UP} event is detected), and returns
         * <tt>myMethod()</tt>'s return value only when a
         * {@link MotionEvent#ACTION_UP} event is detected and the user's
         * finger/stylus never left the area of the button this listener is
         * attached to.
         * 
         * <p>
         * If the user touches the button and then drags the finger/stylus
         * outside of the button area, the overridden method should always
         * return <code>true</code>, so that the selection doesn't change and
         * the bubble menu is always shown; however, the associated action
         * should <b>not</b> be performed. Overriding this method like shown
         * above guarantees this behavior.
         */
        @Override
        public boolean touched(int action, PointF touchPoint, Scrap selected) {
            if (action == MotionEvent.ACTION_MOVE
                    && !parent.contains(Math.round(touchPoint.x),
                            Math.round(touchPoint.y)))
                moved = true;
            boolean returnMe = moved || action != MotionEvent.ACTION_UP;
            if (action == MotionEvent.ACTION_UP) {
                // reset
                moved = false;
                touched = null;
            }
            return returnMe;
        }
    }

    /**
     * A clickable button in the bubble menu.
     * 
     * @author Michele Bonazza
     */
    public static class Button {

        private final BitmapDrawable scaledButton;
        private final Rect position;
        private final RectF hitArea;
        private ButtonListener listener;

        /**
         * Creates a new button.
         * 
         * @param bitmap
         *            the bitmap that will be drawn
         */
        Button(BitmapDrawable bitmap) {
            scaledButton = bitmap;
            position = new Rect();
            hitArea = new RectF();
        }

        /**
         * Draws this button on the argument <tt>canvas</tt>.
         * 
         * @param canvas
         *            the canvas onto which this button will be drawn
         * @param alpha
         *            the alpha to set to the button
         */
        void draw(Canvas canvas, int alpha) {
            scaledButton.setBounds(position);
            if (alpha > -1) {
                scaledButton.setAlpha(alpha);
            }
            scaledButton.draw(canvas);
        }

        /**
         * Returns whether this button contains the argument point.
         * 
         * @param point
         *            the point to be tested
         * @return <code>true</code> if the <tt>(x, y)</tt> point is within this
         *         button's coordinates
         */
        boolean contains(PointF point) {
            return hitArea.contains(Math.round(point.x), Math.round(point.y));
        }

        private boolean contains(int x, int y) {
            return hitArea.contains(x, y);
        }

        /**
         * Sets this button's position.
         * 
         * <p>
         * Not to be used from within <tt>BubbleMenu</tt> class.
         * 
         * @param position
         *            this button's position
         * @param hitAreaEnlargement
         *            the amount of pixels that shall be added twice to each
         *            dimension to increase the hit area: the new hit area will
         *            be <tt>position.width() + 2 * hitAreaEnlargement</tt>
         *            wide, and
         *            <tt>position.height() + 2 * hitAreaEnlargement</tt> high.
         */
        void setPosition(Rect position, float hitAreaEnlargement) {
            if (position != null)
                this.position.set(position);
            hitArea.set(this.position.left - hitAreaEnlargement,
                    this.position.top - hitAreaEnlargement, this.position.right
                            + hitAreaEnlargement, this.position.bottom
                            + hitAreaEnlargement);
        }

        public String toString() {
            return "button " + position + ", hit area: " + hitArea;
        }
    }

    /**
     * The ratio used to make buttons have the same physical size regardless of
     * the actual pixel density of the device.
     */
    public static final float BUTTON_SIZE_TO_SCREEN_WIDTH_RATIO = 48f / 1280;
    private static final int BUTTONS_PER_SIDE = 4;
    private static final float PADDING_TO_BUTTON_SIZE_RATIO = 0.5f;
    private static final int MINIMUM_SIDE_LENGTH_FOR_SCALE = 4;
    private final Button[] buttons;
    private final Button shrink, scrap, erase, move, copy, rotate, resize;
    private final Button topLeft, topRight, bottomRight;
    private final RectF sel, bounds;
    private final CaliView view;
    private PointF initialDistanceToPivot, pivot, referencePoint;
    private Button touched;
    private float buttonDisplaySize, bSize, padding, minSize, scaleFactor,
            maxXScale, maxYScale, compensationForRotateButtonPos;
    private Scrap highlighted;
    private boolean topLeftPinned, visible, drawable;

    /**
     * Initializes a new BubbleMenu that will pick image files from the
     * resources associated to the argument <tt>parent</tt> main activity class.
     * 
     * @param parent
     *            the running instance of CaliSmall
     */
    BubbleMenu(CaliView parent) {
        super("BubbleMenu", parent);
        view = parent;
        sel = new RectF();
        bounds = new RectF();
        Resources resources = parent.getResources();
        shrink = createButton(resources, R.drawable.shrinkwrapped);
        scrap = createButton(resources, R.drawable.scrap);
        erase = createButton(resources, R.drawable.scrap_erase);
        move = createButton(resources, R.drawable.scrap_move);
        copy = createButton(resources, R.drawable.scrap_copy);
        rotate = createButton(resources, R.drawable.scrap_rotate);
        resize = createButton(resources, R.drawable.scrap_resize);
        buttons = new Button[] { shrink, scrap, erase, move, copy, rotate,
                resize };
        topLeft = shrink;
        topRight = move;
        bottomRight = resize;
        attachListeners();
    }

    private void attachListeners() {
        shrink.listener = new ClickableButtonListener(shrink) {

            @Override
            public boolean
                    touched(int action, PointF touchPoint, Scrap selected) {
                return super.touched(action, touchPoint, selected)
                        || shrinkWrapped(action, touchPoint, selected);
            }
        };
        scrap.listener = new ClickableButtonListener(scrap) {

            @Override
            public boolean
                    touched(int action, PointF touchPoint, Scrap selected) {
                return super.touched(action, touchPoint, selected)
                        || scrap(action, touchPoint, selected);
            }
        };
        erase.listener = new ClickableButtonListener(erase) {

            @Override
            public boolean
                    touched(int action, PointF touchPoint, Scrap selected) {
                return super.touched(action, touchPoint, selected)
                        || scrapErase(action, touchPoint, selected);
            }
        };
        move.listener = new ButtonListener() {

            @Override
            public boolean
                    touched(int action, PointF touchPoint, Scrap selected) {
                return scrapMove(action, touchPoint, selected);
            }
        };
        copy.listener = new ButtonListener() {

            @Override
            public boolean
                    touched(int action, PointF touchPoint, Scrap selected) {
                return scrapCopy(action, touchPoint, selected);
            }
        };
        rotate.listener = new ButtonListener() {

            @Override
            public boolean
                    touched(int action, PointF touchPoint, Scrap selected) {
                return scrapRotate(action, touchPoint, selected);
            }
        };
        resize.listener = new ButtonListener() {

            @Override
            public boolean
                    touched(int action, PointF touchPoint, Scrap selected) {
                return scrapResize(action, touchPoint, selected);
            }
        };
    }

    private Button createButton(Resources resources, int buttonID) {
        return new Button(new BitmapDrawable(resources,
                BitmapFactory.decodeResource(resources, buttonID)));
    }

    private boolean scrap(int action, PointF touchPoint, Scrap selected) {
        if (selected instanceof Scrap.Temp) {
            // FIXME control should be moved outside
            Scrap newScrap = new Scrap(selected, false);
            view.addScrap(newScrap, false);
            ((Scrap.Temp) selected).setGhostEffect(false);
            touched = null;
        }
        return true;
    }

    private boolean scrapCopy(int action, PointF touchPoint, Scrap selected) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (selected instanceof Scrap.Temp) {
                selected = new Scrap.Temp(selected, scaleFactor);
                view.addScrap(selected, true);
                view.changeTempScrap(selected);
            } else {
                selected = new Scrap(selected, true);
                view.addScrap(selected, true);
                view.setSelected(selected);
            }
        }
        return scrapMove(action, touchPoint, selected);
    }

    private boolean scrapErase(int action, PointF touchPoint, Scrap selected) {
        selected.outerBorder.delete();
        view.removeScrap(selected);
        view.setSelected(null);
        return false;
    }

    private boolean scrapMove(int action, PointF touchPoint, Scrap selected) {
        if (action == MotionEvent.ACTION_DOWN) {
            selected.startEditing(scaleFactor, Transformation.TRANSLATION);
            referencePoint = touchPoint;
        }
        selected.translate(touchPoint.x - referencePoint.x, touchPoint.y
                - referencePoint.y);
        updateMenu(selected);
        if (!(selected instanceof Scrap.Temp)) {
            // FIXME soooo not OOP!
            updateHighlighted(selected);
        }
        if (action == MotionEvent.ACTION_UP) {
            selected.applyTransform(false);
            fixParenting(selected);
            touched = null;
        }
        return true;
    }

    private boolean scrapResize(int action, PointF touchPoint, Scrap selected) {
        if (action == MotionEvent.ACTION_DOWN) {
            selected.startEditing(scaleFactor, Transformation.RESIZE);
            Rect bounds = selected.getBounds();
            pivot = new PointF(bounds.left, bounds.top);
            initialDistanceToPivot = calculateDistanceToPivot(touchPoint,
                    selected);
            maxXScale = MINIMUM_SIDE_LENGTH_FOR_SCALE / selected.width;
            maxYScale = MINIMUM_SIDE_LENGTH_FOR_SCALE / selected.height;
            topLeftPinned = true;
        }
        // avoid reaching 0, as a scale factor of 0 is a point of non-return
        // minimum size should be a 2x2 area
        float scaleX = (touchPoint.x - pivot.x) / initialDistanceToPivot.x;
        if (touchPoint.x <= pivot.x
                || (scaleX < 1 && selected.width <= MINIMUM_SIDE_LENGTH_FOR_SCALE)) {
            scaleX = maxXScale;
        }
        float scaleY = (touchPoint.y - pivot.y) / initialDistanceToPivot.y;
        if (touchPoint.y <= pivot.y
                || (scaleY < 1 && selected.height <= MINIMUM_SIDE_LENGTH_FOR_SCALE)) {
            scaleY = maxYScale;
        }
        selected.scale(scaleX, scaleY, pivot, initialDistanceToPivot);
        updateMenu(selected);
        if (!(selected instanceof Scrap.Temp)) {
            // FIXME soooo not OOP!
            updateHighlighted(selected);
        }
        if (action == MotionEvent.ACTION_UP) {
            selected.applyTransform(true);
            fixParenting(selected);
            touched = null;
            topLeftPinned = false;
        }
        return true;
    }

    private boolean scrapRotate(int action, PointF touchPoint, Scrap selected) {
        if (action == MotionEvent.ACTION_DOWN) {
            selected.startEditing(scaleFactor, Transformation.RESIZE);
            Rect bounds = selected.getBounds();
            pivot = new PointF(bounds.centerX(), bounds.centerY());
            compensationForRotateButtonPos = (float) Math.toDegrees(Math.atan2(
                    touchPoint.y - pivot.y, touchPoint.x - pivot.x));
        }
        float rotation = (float) Math.toDegrees(Math.atan2(touchPoint.y
                - pivot.y, touchPoint.x - pivot.x))
                - compensationForRotateButtonPos;
        selected.rotate(rotation, pivot);
        updateMenu(selected);
        if (!(selected instanceof Scrap.Temp)) {
            // FIXME soooo not OOP!
            updateHighlighted(selected);
        }
        if (action == MotionEvent.ACTION_UP) {
            selected.applyTransform(true);
            fixParenting(selected);
            touched = null;
        }
        return true;
    }

    private boolean
            shrinkWrapped(int action, PointF touchPoint, Scrap selected) {
        selected.setRect(scaleFactor);
        touched = null;
        setBounds(selected.getBorder(), scaleFactor, bounds);
        scrap(action, touchPoint, selected);
        return true;
    }

    private PointF calculateDistanceToPivot(PointF touchPoint, Scrap selected) {
        Rect bounds = selected.getBounds();
        return new PointF((touchPoint.x - bounds.left),
                (touchPoint.y - bounds.top));
    }

    private void updateHighlighted(Scrap selected) {
        Scrap toBeHighlighted = view.getSelectedScrap(selected);
        if (toBeHighlighted != highlighted) {
            if (toBeHighlighted != null)
                toBeHighlighted.select();
            if (highlighted != null) {
                highlighted.deselect();
            }
            highlighted = toBeHighlighted;
        }
    }

    private void fixParenting(Scrap selected) {
        if (selected instanceof Scrap.Temp) {
            // FIXME sooo not OOP!
            fixParenting((Scrap.Temp) selected);
        } else {
            if (selected.parent != highlighted) {
                // parent must be changed
                if (selected.parent != null) {
                    ((Scrap) selected.parent).remove(selected);
                }
                if (highlighted != null) {
                    highlighted.add(selected);
                } else {
                    selected.setParent(null);
                }
                selected.setPreviousParent(null);
            }
            if (highlighted != null) {
                highlighted.deselect();
            }
            highlighted = null;
        }

    }

    private void fixParenting(Scrap.Temp tempScrap) {
        for (Stroke stroke : tempScrap.getStrokes()) {
            CaliSmallElement newParent = view.getSelectedScrap(stroke);
            CaliSmallElement previousParent = stroke.getPreviousParent();
            if (newParent != previousParent) {
                if (previousParent != null)
                    ((Scrap) previousParent).remove(stroke);
                if (newParent != null)
                    ((Scrap) newParent).add(stroke);
                stroke.setPreviousParent(null);
            }
        }
        for (Scrap scrap : tempScrap.getScraps()) {
            CaliSmallElement newParent = view.getSelectedScrap(scrap);
            CaliSmallElement previousParent = scrap.getPreviousParent();
            if (newParent != scrap.getParent()) {
                if (previousParent != null && previousParent != tempScrap) {
                    Scrap previous = (Scrap) previousParent;
                    previous.remove(scrap);
                }
                if (newParent != null)
                    ((Scrap) newParent).add(scrap);
                scrap.setPreviousParent(null);
            }
        }
    }

    /**
     * Calls <tt>touched()</tt> on the listener set for the button that contains
     * <tt>touchPoint</tt> if any does and returns whether the bubble menu
     * should keep being displayed.
     * 
     * <p>
     * If <tt>touchPoint</tt> is outside this method does nothing and returns
     * <code>false</code>.
     * 
     * @param action
     *            the kind of action that was performed by the user when
     *            touching the canvas
     * @param touchPoint
     *            the point that was touched by the user
     * @param selection
     *            the current selected scrap
     * @return whether the bubble menu should still be displayed on the argument
     *         <tt>selection</tt> after this method returns
     */
    boolean onTouch(int action, PointF touchPoint, Scrap selection) {
        // if not clicking on buttons hide menu when the touch action is over
        boolean keepShowingMenu = action != MotionEvent.ACTION_UP;
        if (touched != null) {
            keepShowingMenu = touched.listener.touched(action, touchPoint,
                    selection);
        }
        if (!keepShowingMenu)
            touched = null;
        return keepShowingMenu;
    }

    /**
     * Tests whether the argument point is within any of the buttons on this
     * menu.
     * 
     * @param touchPoint
     *            the point to be tested
     * @param selection
     *            the current selection
     * @return <code>true</code> if <tt>touchPoint</tt> is within any of the
     *         buttons
     */
    boolean buttonTouched(PointF touchPoint, Scrap selection) {
        if (selection == null)
            return false;
        if (touched != null)
            return true;
        int x = Math.round(touchPoint.x);
        int y = Math.round(touchPoint.y);
        for (Button button : buttons) {
            if (button.contains(x, y)) {
                touched = button;
                return true;
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.calismall.GenericTouchHandler#onDown(android.graphics.PointF)
     */
    @Override
    public boolean onDown(PointF touchPoint) {
        Scrap previousSelection = view.getSelection();
        view.setPreviousSelection(previousSelection);
        if (buttonTouched(touchPoint, previousSelection))
            return onTouch(MotionEvent.ACTION_DOWN, touchPoint,
                    view.getSelection());
        else
            actionCompleted = true;
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.calismall.GenericTouchHandler#onMove(android.graphics.PointF)
     */
    @Override
    public boolean onMove(PointF touchPoint) {
        onTouch(MotionEvent.ACTION_MOVE, touchPoint, view.getSelection());
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.GenericTouchHandler#onUp(android.graphics.PointF)
     */
    @Override
    public boolean onUp(PointF touchPoint) {
        if (!onTouch(MotionEvent.ACTION_UP, touchPoint, view.getSelection())) {
            actionCompleted = true;
            reset();
        }
        return true;
    }

    private void reset() {
        for (Button button : buttons) {
            button.position.set(new Rect());
        }
    }

    /**
     * Draws the menu on the argument canvas.
     * 
     * @param canvas
     *            the canvas to which the menu must be drawn.
     */
    void draw(Canvas canvas) {
        for (Button button : buttons) {
            button.draw(canvas, -1);
        }
    }

    /**
     * Updates the position of buttons in this menu according to the argument
     * <tt>selectionPath</tt> and current <tt>scaleFactor</tt> applied to the
     * canvas.
     * 
     * @param selectionPath
     *            the path drawn by the user to select elements in the canvas
     * @param scaleFactor
     *            the current (cumulative) scale factor applied to the canvas
     * @param bounds
     *            the portion of the canvas currently displayed
     */
    void setBounds(Path selectionPath, float scaleFactor, RectF bounds) {
        this.scaleFactor = scaleFactor;
        this.bounds.set(bounds);
        bSize = buttonDisplaySize / scaleFactor;
        padding = bSize * PADDING_TO_BUTTON_SIZE_RATIO;
        minSize = bSize * BUTTONS_PER_SIDE + (BUTTONS_PER_SIDE - 1) * padding;
        updatePositionAndSize(selectionPath);
    }

    /**
     * Updates the display bounds for bubble menus.
     * 
     * @param scaleFactor
     *            the current (cumulative) scale factor applied to the canvas
     * @param bounds
     *            the portion of the canvas currently displayed
     */
    public void setBounds(float scaleFactor, RectF bounds) {
        buttonDisplaySize = Math.max(bounds.width(), bounds.height())
                * BUTTON_SIZE_TO_SCREEN_WIDTH_RATIO;
        setBounds(null, scaleFactor, bounds);
    }

    private void updatePositionAndSize(Path outerBorder) {
        if (outerBorder != null)
            outerBorder.computeBounds(sel, true);
        updatePivotButtonsPosition();
        applySizeConstraints();
        applySpaceContraints();
        applySizeConstraintsFixedPivots();
        updateNonPivotButtonsPosition();
        setHitAreas();
    }

    private void updatePivotButtonsPosition() {
        // use floor() and ceil() according to what's closest to the screen
        // margins
        topRight.position.left = (int) FloatMath.floor(sel.right);
        topRight.position.top = (int) FloatMath.ceil(sel.top - bSize);
        topRight.position.right = (int) FloatMath.floor(topRight.position.left
                + bSize);
        topRight.position.bottom = (int) FloatMath.ceil(topRight.position.top
                + bSize);
        topLeft.position.left = (int) FloatMath.floor(sel.left - bSize);
        topLeft.position.top = topRight.position.top;
        topLeft.position.right = (int) FloatMath.floor(topLeft.position.left
                + bSize);
        topLeft.position.bottom = topRight.position.bottom;
        bottomRight.position.left = topRight.position.left;
        bottomRight.position.top = (int) FloatMath.floor(sel.bottom);
        bottomRight.position.right = topRight.position.right;
        bottomRight.position.bottom = (int) FloatMath
                .floor(bottomRight.position.top + bSize);
    }

    private void updateNonPivotButtonsPosition() {
        scrap.position.left = topLeft.position.left;
        scrap.position.right = topLeft.position.right;
        scrap.position.top = (int) FloatMath.floor(topRight.position.bottom
                + padding);
        scrap.position.bottom = (int) FloatMath.floor(scrap.position.top
                + bSize);
        copy.position.left = topRight.position.left;
        copy.position.right = topRight.position.right;
        copy.position.top = scrap.position.top;
        copy.position.bottom = scrap.position.bottom;
        rotate.position.left = topRight.position.left;
        rotate.position.right = topRight.position.right;
        rotate.position.bottom = (int) FloatMath.floor(bottomRight.position.top
                - padding);
        rotate.position.top = (int) FloatMath.floor(rotate.position.bottom
                - bSize);
        // TODO add scrap_palette here
        erase.position.left = topLeft.position.left;
        erase.position.right = topLeft.position.right;
        erase.position.top = bottomRight.position.top;
        erase.position.bottom = bottomRight.position.bottom;
        // TODO add scrap_list here
        // TODO add scrap_drop here
    }

    /**
     * Returns the current size for buttons.
     * 
     * @return the current button size
     */
    float getButtonSize() {
        return bSize;
    }

    private void applySizeConstraints() {
        final int curWidth = topRight.position.right - topLeft.position.left;
        final int curHeight = bottomRight.position.bottom
                - topRight.position.top;
        if (curWidth < minSize) {
            // set the bubble menu to its minimum size, center according to
            // selection
            fixTooNarrow();
        } else if (curWidth > bounds.width()) {
            fixTooWide();
        }
        if (curHeight < minSize) {
            fixTooShort();
        } else if (curHeight > bounds.height()) {
            fixTooHigh();
        }
    }

    private void fixTooNarrow() {
        if (!topLeftPinned) {
            topLeft.position.left = (int) FloatMath.ceil(sel.centerX()
                    - minSize / 2);
            topLeft.position.right = (int) FloatMath.ceil(topLeft.position.left
                    + bSize);
        }
        topRight.position.right = (int) FloatMath.floor(topLeft.position.left
                + minSize);
        topRight.position.left = (int) FloatMath.ceil(topRight.position.right
                - bSize);
        bottomRight.position.left = topRight.position.left;
        bottomRight.position.right = topRight.position.right;
    }

    private void fixTooWide() {
        topLeft.position.left = (int) Math.max(FloatMath.floor(bounds.left),
                topLeft.position.left);
        topLeft.position.right = (int) FloatMath.ceil(topLeft.position.left
                + bSize);
        topRight.position.right = (int) Math.min(FloatMath.floor(bounds.right),
                topRight.position.right);
        topRight.position.left = (int) FloatMath.ceil(topRight.position.right
                - bSize);
        bottomRight.position.left = topRight.position.left;
        bottomRight.position.right = topRight.position.right;
    }

    private void fixTooShort() {
        if (!topLeftPinned) {
            topLeft.position.top = (int) FloatMath.ceil(sel.centerY() - minSize
                    / 2);
            topLeft.position.bottom = (int) FloatMath.ceil(topLeft.position.top
                    + bSize);
        }
        bottomRight.position.bottom = (int) FloatMath
                .floor(topLeft.position.top + minSize);
        bottomRight.position.top = (int) FloatMath
                .floor(bottomRight.position.bottom - bSize);
        topRight.position.top = topLeft.position.top;
        topRight.position.bottom = topLeft.position.bottom;
    }

    private void fixTooHigh() {
        topLeft.position.top = (int) Math.max(FloatMath.ceil(bounds.top),
                topLeft.position.top);
        topLeft.position.bottom = (int) FloatMath.ceil(topLeft.position.top
                + bSize);
        bottomRight.position.bottom = (int) Math.min(
                FloatMath.floor(bounds.bottom), bottomRight.position.bottom);
        bottomRight.position.top = (int) FloatMath
                .floor(bottomRight.position.bottom - bSize);
        topRight.position.top = topLeft.position.top;
        topRight.position.bottom = topLeft.position.bottom;
    }

    private void applySpaceContraints() {
        if (topLeft.position.left <= bounds.left) {
            // make it appear on screen
            topLeft.position.left = (int) FloatMath.ceil(bounds.left);
            topLeft.position.right = (int) FloatMath.ceil(topLeft.position.left
                    + bSize);
        }
        if (topRight.position.right >= bounds.right) {
            topRight.position.right = (int) FloatMath.floor(bounds.right);
            topRight.position.left = (int) FloatMath
                    .floor(topRight.position.right - bSize);
            bottomRight.position.right = topRight.position.right;
            bottomRight.position.left = topRight.position.left;
        }
        if (bottomRight.position.bottom >= bounds.bottom) {
            bottomRight.position.bottom = (int) FloatMath.floor(bounds.bottom);
            bottomRight.position.top = (int) FloatMath
                    .floor(bottomRight.position.bottom - bSize);
        }
        if (topRight.position.top <= bounds.top) {
            topRight.position.top = (int) FloatMath.ceil(bounds.top);
            topRight.position.bottom = (int) FloatMath
                    .ceil(topRight.position.top + bSize);
            topLeft.position.top = topRight.position.top;
            topLeft.position.bottom = topRight.position.bottom;
        }
    }

    private void applySizeConstraintsFixedPivots() {
        final int curWidth = topRight.position.right - topLeft.position.left;
        final int curHeight = bottomRight.position.bottom
                - topRight.position.top;
        if (curWidth < minSize) {
            // decide which point is to be used as pivot according to which one
            // is closer to the screen margins
            if (Math.abs(topLeft.position.left - bounds.left) < Math
                    .abs(bounds.right - topRight.position.right)) {
                // scrap is near the left border of the screen
                topRight.position.right = (int) FloatMath
                        .floor(topLeft.position.left + minSize);
                topRight.position.left = (int) FloatMath
                        .floor(topRight.position.right - bSize);
            } else {
                topLeft.position.left = (int) FloatMath
                        .ceil(topRight.position.right - minSize);
                topLeft.position.right = (int) FloatMath
                        .ceil(topLeft.position.left + bSize);
            }
            bottomRight.position.left = topRight.position.left;
            bottomRight.position.right = topRight.position.right;
        }
        if (curHeight < minSize) {
            if (Math.abs(topRight.position.top - bounds.top) < Math
                    .abs(bounds.bottom - bottomRight.position.bottom)) {
                // scrap is near the top border of the screen
                bottomRight.position.bottom = (int) FloatMath
                        .floor(topLeft.position.top + minSize);
                bottomRight.position.top = (int) FloatMath
                        .floor(bottomRight.position.bottom - bSize);
            } else {
                topLeft.position.top = (int) FloatMath
                        .ceil(bottomRight.position.bottom - minSize);
                topLeft.position.bottom = (int) FloatMath
                        .ceil(topLeft.position.top + bSize);
            }
            topRight.position.top = topLeft.position.top;
            topRight.position.bottom = topLeft.position.bottom;
        }
    }

    private void setHitAreas() {
        for (Button button : buttons) {
            button.setPosition(null, buttonDisplaySize * 0.25f);
        }
    }

    private void updateMenu(Scrap selection) {
        updatePositionAndSize(selection.getBorder());
    }

    /**
     * Returns whether this bubble menu is currently displayed on screen.
     * 
     * @return <code>true</code> if this menu is currently being drawn on screen
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets whether this bubble menu is currently being displayed on screen.
     * 
     * @param visible
     *            <code>true</code> if this bubble menu is shown on screen
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Returns whether this bubble menu is ready to be drawn.
     * 
     * @return <code>true</code> if this bubble menu must be drawn to Canvas
     */
    public boolean isDrawable() {
        return drawable;
    }

    /**
     * Sets whether this bubble menu should be drawn.
     * 
     * @param drawable
     *            <code>true</code> if this bubble menu must be drawn
     */
    public void setDrawable(boolean drawable) {
        this.drawable = drawable;
    }
}
