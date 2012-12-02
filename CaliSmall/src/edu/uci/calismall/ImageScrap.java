/*
 * ImageScrap.java Created on Nov 29, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.io.File;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.FloatMath;

/**
 * @author Michele Bonazza
 * 
 */
public class ImageScrap extends Scrap {

    private static final String IMAGES_FOLDER_NAME = "images";
    private static final int ABS_SCREEN_TO_IMG_RATIO = 3;
    private final String srcImage;
    private Matrix bitmapMatrix;
    private Bitmap scaled;

    /**
     * @param parentView
     * @param imageUri
     */
    public ImageScrap(CaliView parentView, Uri imageUri) {
        super(parentView);
        File src = new File(imageUri.getPath());
        File imagesFolder = new File(src.getParent(), IMAGES_FOLDER_NAME);
        if (!imagesFolder.exists()) {
            imagesFolder.mkdir();
        }
        File dst = new File(imagesFolder, System.currentTimeMillis() + ".jpg");
        srcImage = dst.getAbsolutePath();
        src.renameTo(dst);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(srcImage, options);
        int imageHeight = options.outHeight;
        int imageWidth = options.outWidth;
        PointF viewSize = parentView.getDrawableSize();
        float scale;
        if (imageWidth < imageHeight) {
            // img in portrait mode, fit height
            scale = imageHeight / (viewSize.y / (ABS_SCREEN_TO_IMG_RATIO));
        } else {
            // img in landscape or square, fit width
            scale = imageWidth / (viewSize.x / (ABS_SCREEN_TO_IMG_RATIO));
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = (int) FloatMath.floor(scale);
        scaled = BitmapFactory.decodeFile(srcImage, options);
        Utils.debug("original size: " + imageWidth + "x" + imageHeight
                + ", new: " + scaled.getWidth() + "x" + scaled.getHeight()
                + " scale is " + scale);
        bitmapMatrix = new Matrix();
        PointF topLeft = new PointF(viewSize.x / 2 - scaled.getWidth() / 2,
                viewSize.y / 2 - scaled.getHeight() / 2);
        PointF topRight = new PointF(topLeft.x + scaled.getWidth(), topLeft.y);
        PointF bottomLeft = new PointF(topLeft.x, topLeft.y
                + scaled.getHeight());
        outerBorder = new RectStroke(parentView, new RectF(topLeft.x,
                topLeft.y, topRight.x, bottomLeft.y));
        bitmapMatrix.postTranslate(topLeft.x, topLeft.y);
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#startEditing(float)
     */
    @Override
    public void startEditing(float scaleFactor) {
        setBoundaries();
        Rect size = getBounds();
        // create a new bitmap as large as the scrap
        Bitmap snapshot = Bitmap.createBitmap(size.width(), size.height(),
                Config.ARGB_4444);
        // move the bitmap over the scrap
        Canvas snapshotCanvas = new Canvas(snapshot);
        snapshotCanvas.translate(-size.left, -size.top);
        // draw scrap content to the bitmap
        drawOnBitmap(snapshotCanvas, snapshot, scaleFactor);
        this.snapshot = snapshot;
        super.startEditing(scaleFactor);
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#applyTransform(boolean)
     */
    @Override
    public void applyTransform(boolean forceSnapshotRedraw) {
        bitmapMatrix.postConcat(matrix);
        super.applyTransform(forceSnapshotRedraw);
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#transformationEnded(android.graphics.Matrix)
     */
    @Override
    public void transformationEnded(Matrix matrix) {
        super.transformationEnded(matrix);
        bitmapMatrix.postConcat(matrix);
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#draw(edu.uci.calismall.CaliView,
     * android.graphics.Canvas, float, boolean)
     */
    @Override
    public void draw(CaliView parent, Canvas canvas, float scaleFactor,
            boolean drawBorder) {
        if (hasToBeDrawnVectorially() || (snapshot == null)) {
            canvas.drawBitmap(scaled, bitmapMatrix, null);
            for (int i = 0; i < scraps.size(); i++) {
                Scrap scrap = scraps.get(i);
                scrap.draw(parent, canvas, scaleFactor, drawBorder);
            }
        } else if (topLevelForEdit) {
            canvas.drawBitmap(snapshot, snapshotMatrix, null);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#drawOnBitmap(android.graphics.Canvas,
     * android.graphics.Bitmap, float)
     */
    @Override
    public void drawOnBitmap(Canvas canvas, Bitmap bitmap, float scaleFactor) {
        canvas.drawBitmap(scaled, bitmapMatrix, null);
        super.drawOnBitmap(canvas, bitmap, scaleFactor);
    }

}
