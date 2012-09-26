/**
 * BubbleMenu.java
 * Created on Aug 8, 2012
 * Copyright 2012 Michele Bonazza
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
import android.view.MotionEvent;
import edu.uci.calismall.CaliSmall.CaliView;

/**
 * A pop-up menu shown when the user "closes" a path creating a scrap selection.
 * 
 * @author Michele Bonazza
 */
public class BubbleMenu {

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
		 * 	return super.touched(action, touchPoint, selected)
		 * 			|| myMethod(action, touchPoint, selected);
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
		private ButtonListener listener;

		private Button(BitmapDrawable bitmap) {
			scaledButton = bitmap;
			position = new Rect();
		}

		private void draw(Canvas canvas) {
			scaledButton.setBounds(position);
			scaledButton.draw(canvas);
		}

		private boolean contains(int x, int y) {
			return position.contains(x, y);
		}
	}

	/**
	 * Absolute size of buttons in bubble menus. This value assumes that all
	 * buttons have this height AND width (i.e. they're contained in a square).
	 */
	public static final int ABS_B_SIZE = 48;
	private static final int MIN_BUTTON_DISTANCE = 3;
	private final Button[] buttons;
	private final Button topLeft;
	private final Button topRight;
	private final Button bottomRight;
	private final RectF sel, bounds;
	private final CaliView view;
	private PointF lastPosition;
	private Button touched;
	private float buttonDisplaySize = ABS_B_SIZE, bSize, padding, minSize,
			selWidth, selHeight, scaleFactor;

	/**
	 * Initializes a new BubbleMenu that will pick image files from the
	 * resources associated to the argument <tt>parent</tt> main activity class.
	 * 
	 * @param parent
	 *            the running instance of CaliSmall
	 */
	public BubbleMenu(CaliSmall parent) {
		view = parent.getView();
		sel = new RectF();
		bounds = new RectF();
		buttons = initButtons(parent.getResources());
		topLeft = buttons[0];
		topRight = buttons[3];
		bottomRight = buttons[6];
		lastPosition = new PointF();
	}

	private Button[] initButtons(Resources resources) {
		// TODO check whether the shrink button size is good for every button
		BitmapDrawable shrinkButton = new BitmapDrawable(resources,
				BitmapFactory.decodeResource(resources,
						R.drawable.shrinkwrapped));
		buttonDisplaySize = shrinkButton.getIntrinsicWidth();
		Button shrink = new Button(shrinkButton);
		shrink.listener = new ClickableButtonListener(shrink) {

			@Override
			public boolean touched(int action, PointF touchPoint, Scrap selected) {
				return super.touched(action, touchPoint, selected)
						|| shrinkWrapped(action, touchPoint, selected);
			}
		};
		Button scrap = createButton(resources, R.drawable.scrap);
		Button erase = createButton(resources, R.drawable.scrap_erase);
		Button move = createButton(resources, R.drawable.scrap_move);
		Button copy = createButton(resources, R.drawable.scrap_copy);
		Button rotate = createButton(resources, R.drawable.scrap_rotate);
		Button resize = createButton(resources, R.drawable.scrap_resize);
		Button[] tmpButtons = new Button[] { shrink, scrap, erase, move, copy,
				rotate, resize };
		scrap.listener = new ClickableButtonListener(scrap) {

			@Override
			public boolean touched(int action, PointF touchPoint, Scrap selected) {
				return super.touched(action, touchPoint, selected)
						|| scrap(action, touchPoint, selected);
			}
		};
		erase.listener = new ClickableButtonListener(erase) {

			@Override
			public boolean touched(int action, PointF touchPoint, Scrap selected) {
				return super.touched(action, touchPoint, selected)
						|| scrapErase(action, touchPoint, selected);
			}
		};
		move.listener = new ButtonListener() {

			@Override
			public boolean touched(int action, PointF touchPoint, Scrap selected) {
				return scrapMove(action, touchPoint, selected);
			}
		};
		copy.listener = new ClickableButtonListener(copy) {

			@Override
			public boolean touched(int action, PointF touchPoint, Scrap selected) {
				return super.touched(action, touchPoint, selected)
						|| scrapCopy(action, touchPoint, selected);
			}
		};
		rotate.listener = new ButtonListener() {

			@Override
			public boolean touched(int action, PointF touchPoint, Scrap selected) {
				return scrapRotate(action, touchPoint, selected);
			}
		};
		resize.listener = new ButtonListener() {

			@Override
			public boolean touched(int action, PointF touchPoint, Scrap selected) {
				return scrapResize(action, touchPoint, selected);
			}
		};
		return tmpButtons;
	}

	private Button createButton(Resources resources, int buttonID) {
		return new Button(new BitmapDrawable(resources,
				BitmapFactory.decodeResource(resources, buttonID)));
	}

	private boolean scrap(int action, PointF touchPoint, Scrap selected) {
		if (selected instanceof Scrap.Temp) {
			// FIXME control should be moved outside
			Scrap newScrap = new Scrap(selected, false);
			view.addScrap(newScrap);
			view.setSelected(newScrap);
			touched = null;
			return true;
		} else {
			return false;
		}
	}

	private boolean scrapCopy(int action, PointF touchPoint, Scrap selected) {
		Scrap newScrap;
		if (selected instanceof Scrap.Temp) {
			newScrap = new Scrap.Temp(selected, scaleFactor);
			view.addStrokes(newScrap);
			view.changeTempScrap(newScrap);
		} else {
			newScrap = new Scrap(selected, true);
			view.addScrap(newScrap);
			view.setSelected(newScrap);
		}
		touched = null;
		return true;
	}

	private boolean scrapErase(int action, PointF touchPoint, Scrap selected) {
		view.removeScrap(selected);
		return false;
	}

	private boolean scrapMove(int action, PointF touchPoint, Scrap selected) {
		if (action == MotionEvent.ACTION_DOWN) {
			selected.startEditing(scaleFactor);
		}
		PointF quantizedMove = moveMenu(selected,
				touchPoint.x - lastPosition.x, touchPoint.y - lastPosition.y);
		selected.translate(quantizedMove.x, quantizedMove.y);
		if (action == MotionEvent.ACTION_UP) {
			selected.applyTransform();
			touched = null;
			lastPosition = new PointF();
		}
		return true;
	}

	private boolean scrapResize(int action, PointF touchPoint, Scrap selected) {
		return (action != MotionEvent.ACTION_UP);
	}

	private boolean scrapRotate(int action, PointF touchPoint, Scrap selected) {
		return (action != MotionEvent.ACTION_UP);
	}

	private boolean shrinkWrapped(int action, PointF touchPoint, Scrap selected) {
		selected.shrinkBorder(scaleFactor);
		touched = null;
		setBounds(selected.getBorder(), scaleFactor, bounds);
		if (!selected.isEmpty()) {
			scrap(action, touchPoint, selected);
		}
		return true;
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
	public boolean onTouch(int action, PointF touchPoint, Scrap selection) {
		if (selection == null)
			return false;
		// if not clicking on buttons hide menu when the touch action is over
		boolean keepShowingMenu = action != MotionEvent.ACTION_UP;
		if (touched != null) {
			restrictWithinBounds(touchPoint);
			keepShowingMenu = touched.listener.touched(action, touchPoint,
					selection);
			lastPosition = touchPoint;
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
	 * @return <code>true</code> if <tt>touchPoint</tt> is within any of the
	 *         buttons
	 */
	public boolean buttonTouched(PointF touchPoint) {
		if (touched != null)
			return true;
		int x = Math.round(touchPoint.x);
		int y = Math.round(touchPoint.y);
		for (Button button : buttons) {
			if (button.contains(x, y)) {
				touched = button;
				lastPosition = touchPoint;
				return true;
			}
		}
		return false;
	}

	private void restrictWithinBounds(PointF touchPoint) {
		touchPoint.set(
				Math.max(bounds.left + bSize,
						Math.min(bounds.right - bSize, touchPoint.x)),
				Math.min(bounds.bottom - bSize,
						Math.max(bounds.top, touchPoint.y)));
	}

	/**
	 * Draws the menu on the argument canvas.
	 * 
	 * @param canvas
	 *            the canvas to which the menu must be drawn.
	 */
	public void draw(Canvas canvas) {
		for (Button button : buttons) {
			button.draw(canvas);
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
	public void setBounds(Path selectionPath, float scaleFactor, RectF bounds) {
		this.scaleFactor = scaleFactor;
		this.bounds.set(bounds);
		bSize = buttonDisplaySize / scaleFactor;
		minSize = bSize * MIN_BUTTON_DISTANCE;
		padding = bSize / 2;
		if (selectionPath != null) {
			selectionPath.computeBounds(sel, true);
			selWidth = sel.right - sel.left;
			selHeight = sel.bottom - sel.top;
			updatePivotButtonsPositions(sel);
			updateButtonsPositions(0, 0);
		}
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
		setBounds(null, scaleFactor, bounds);
	}

	private void updatePivotButtonsPositions(RectF sel) {
		updateBounds(topLeft.position, sel.left - bSize, sel.top - bSize,
				sel.left, sel.top);
		updateBounds(topRight.position, sel.right, sel.top - bSize, sel.right
				+ bSize, sel.top);
		updateBounds(bottomRight.position, sel.right, sel.bottom, sel.right
				+ bSize, sel.bottom + bSize);
	}

	private void updateNonPivotButtonsPositions() {
		// second button on the left
		updateBounds(buttons[1].position, topLeft.position.left,
				topLeft.position.bottom + padding, topLeft.position.right,
				topLeft.position.bottom + padding + bSize);
		// button in the bottom-left position
		updateBounds(buttons[2].position, topLeft.position.left,
				bottomRight.position.top, topLeft.position.right,
				bottomRight.position.bottom);
		// second button on the right
		updateBounds(buttons[4].position, topRight.position.left,
				topRight.position.bottom + padding, topRight.position.left
						+ bSize, topRight.position.bottom + padding + bSize);
		// second-to-last button on the right
		updateBounds(buttons[5].position, bottomRight.position.left,
				bottomRight.position.top - bSize - padding,
				bottomRight.position.right, bottomRight.position.top - padding);
	}

	private PointF updateButtonsPositions(float dx, float dy) {
		Rect pivot = topRight.position;
		final float oldTop = pivot.top;
		final float oldLeft = pivot.left;
		updateBounds(pivot, oldLeft + dx, oldTop + dy, oldLeft + dx + bSize,
				oldTop + dy + bSize);
		updateBounds(topLeft.position, pivot.left - selWidth - bSize,
				pivot.top, pivot.left - selWidth, pivot.top + bSize);
		updateBounds(bottomRight.position, pivot.left,
				pivot.bottom + selHeight, pivot.right, pivot.bottom + selHeight
						+ bSize);
		applySizeConstraints();
		updateNonPivotButtonsPositions();
		return new PointF(pivot.left - oldLeft, pivot.top - oldTop);
	}

	private void applySizeConstraints() {
		final int curWidth = topRight.position.left - topLeft.position.right;
		final int curHeight = bottomRight.position.top
				- topRight.position.bottom;
		if (curWidth < minSize) {
			if (topLeft.position.right > bounds.right - bSize - minSize) {
				// a small scrap on the right of the screen
				atMostThisRight(topLeft.position, topRight.position.left
						- minSize);
			} else {
				// a small scrap on the left or user is dragging a scrap all the
				// way to the left beyond the dislay limit
				atLeastThisRight(topRight.position, topLeft.position.right
						+ minSize);
			}
		}
		if (curHeight < minSize) {
			if (topRight.position.bottom > bounds.bottom - bSize - minSize) {
				// a small scrap on the bottom of the screen
				atMostThisDown(topRight.position, bottomRight.position.top
						- minSize);
			} else {
				// a small scrap on the top
				atLeastThisDown(bottomRight.position, topRight.position.bottom
						+ minSize);
			}
		}
	}

	private void atLeastThisRight(Rect rect, float right) {
		rect.left = Math.round(Math.max(right, rect.left));
		rect.right = Math.round(rect.left + bSize);
	}

	private void atMostThisRight(Rect rect, float right) {
		rect.right = Math.round(Math.min(right, rect.right));
		rect.left = Math.round(rect.right - bSize);
	}

	private void atLeastThisDown(Rect rect, float down) {
		rect.top = Math.round(Math.max(down, rect.top));
		rect.bottom = Math.round(rect.top + bSize);
	}

	private void atMostThisDown(Rect rect, float down) {
		rect.bottom = Math.round(Math.min(down, rect.bottom));
		rect.top = Math.round(rect.bottom - bSize);
	}

	private PointF moveMenu(Scrap selection, float dx, float dy) {
		return updateButtonsPositions(dx, dy);
	}

	/**
	 * Updates the bounds of the argument <tt>rect</tt> using the argument
	 * coordinates, ensuring that it doesn' go out of the portion of canvas
	 * currently being shown.
	 */
	private void updateBounds(Rect rect, float left, float top, float right,
			float bottom) {
		if (left < bounds.left) {
			left = bounds.left;
			right = bounds.left + bSize;
		}
		if (right > bounds.right) {
			right = bounds.right;
			left = bounds.right - bSize;
		}
		if (top < bounds.top) {
			top = bounds.top;
			bottom = bounds.top + bSize;
		}
		if (bottom > bounds.bottom) {
			bottom = bounds.bottom;
			top = bounds.bottom - bSize;
		}
		rect.set(Math.round(left), Math.round(top), Math.round(right),
				Math.round(bottom));
	}
}
