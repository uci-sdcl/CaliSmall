/**
 * Painter.java Created on Oct 19, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * The task executed by the thread that draws the view.
 * 
 * @author Michele Bonazza
 * 
 */
public class Painter implements Runnable {

    /**
     * Time in milliseconds between two consecutive screen refreshes (i.e. two
     * consecutive calls to {@link CaliView#drawView(Canvas)}). To get the FPS
     * that this value sets, just divide 1000 by the value (so a
     * <tt>SCREEN_REFRESH_TIME</tt> of <tt>20</tt> translates to 50 FPS).
     */
    public static final long SCREEN_REFRESH_TIME = 20;
    private final SurfaceHolder holder;
    private final CaliView view;
    private Lock lock;
    private Condition fileOpened, drawingThreadWaiting;
    private boolean waitForFileOpen, drawingThreadSleeping;

    /**
     * Creates a new painter that will retrieve a {@link Canvas} from the
     * argument <tt>holder</tt> and pass it to the argument <tt>view</tt> by
     * means of {@link CaliView#drawView(Canvas)} calls.
     * 
     * @param holder
     *            the <tt>SurfaceHolder</tt> from which canvas instances will be
     *            retrieved through {@link SurfaceHolder#lockCanvas()} calls
     * @param view
     *            the view to be drawn onto the canvas
     */
    Painter(SurfaceHolder holder, CaliView view) {
        this.holder = holder;
        this.view = view;
    }

    public void run() {
        Canvas canvas = null;
        long timer;
        while (view.isRunning()) {
            timer = -System.currentTimeMillis();
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    view.drawView(canvas);
                }
                if (waitForFileOpen) {
                    waitForFileOpen();
                }
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
                    Utils.debug("interrupted!");
                }
            }
        }
    }

    /**
     * Requests for the {@link Painter} thread to stop drawing the associated
     * {@link CaliView} until the file opener thread is done.
     * 
     * @param lock
     *            the lock that must be acquired to communicate with the opener
     *            thread
     * @param waitingCondition
     *            the condition on which the painter must
     *            {@link Condition#await()}
     * @param signalCondition
     *            the condition that the painter must {@link Condition#signal()}
     *            right before going to sleep
     */
    void stopForFileOpen(Lock lock, Condition waitingCondition,
            Condition signalCondition) {
        this.lock = lock;
        fileOpened = waitingCondition;
        drawingThreadWaiting = signalCondition;
        waitForFileOpen = true;
    }

    /**
     * Returns whether this painter is currently waiting for the opener thread
     * to be done.
     * 
     * @return <code>true</code> if this thread is currently sleeping
     */
    boolean isWaiting() {
        return drawingThreadSleeping;
    }

    private void waitForFileOpen() {
        try {
            lock.lock();
            drawingThreadSleeping = true;
            drawingThreadWaiting.signalAll();
            fileOpened.await();
            waitForFileOpen = false;
            drawingThreadSleeping = false;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

}
