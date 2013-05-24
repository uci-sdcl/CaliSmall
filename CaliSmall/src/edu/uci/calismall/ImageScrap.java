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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;

/**
 * @author Michele Bonazza
 * 
 */
public class ImageScrap extends Scrap {
	
    private int initialRotation, sampleSize, scaledWidth, scaledHeight;

    private static final String IMAGES_FOLDER_NAME = "images";
    private String srcImage;
    private Matrix bitmapMatrix;
    private Bitmap scaled;

    /**
     * Creates a new image scrap.
     * 
     * @param parentView
     *            the current view
     */
    public ImageScrap(CaliView parentView) {
        super(parentView);
        bitmapMatrix = new Matrix();
    }
    
    /**
     * Clones the argument image.
     * 
     * @param clone
     *            the image to be used to create a new clone
     * @throws OutOfMemoryError
     *             in case there's not enough memory to create a copy of this
     *             image
     */
    public ImageScrap(ImageScrap clone) throws OutOfMemoryError {
        super(clone.parentView);
        bitmapMatrix = new Matrix(clone.bitmapMatrix);
        srcImage = clone.srcImage;
        try {
            scaled = Bitmap.createBitmap(clone.scaled);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            throw e;
        }
        initialRotation = clone.initialRotation;
        scaledHeight = clone.scaledHeight;
        scaledWidth = clone.scaledWidth;
        outerBorder = new RectStroke((RectStroke) clone.outerBorder);
        copyContent(clone);
    }
    
    /**
     * Creates a copy of this image scrap
     * 
     * @param deepCopy
     * 		does not affect anything, but must have a boolean value since it
     * 		overrides Scrap.copy(boolean)
     * @return a reference to a clone of this image scrap
     * @see #ImageScrap(ImageScrap)
     */
    @Override
    public Scrap copy(boolean deepCopy) {
    	return new ImageScrap(this);
    }
    
    /**
     * Sets the path to the image that has to be loaded within this scrap.
     * 
     * @param imagePath
     *            the absolute path to the image file
     * @return a reference to this scrap
     */
    public ImageScrap setImage(String imagePath) {
        srcImage = imagePath;
        initialRotation = readRotationFromExif(srcImage);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeFile(srcImage, options);
        } catch (OutOfMemoryError e) {
            parentView.showOutOfMemoryError();
            Utils.debug("Out Of Memory ImageScrap.setImage");
        }
        Utils.debug("decoding " + srcImage + ", result is " + options.outWidth
                + "x" + options.outHeight);
        PointF viewSize = parentView.getDrawableSize();
        computeInitialTransform(viewSize, new Point(options.outWidth,
                options.outHeight), initialRotation);
        scaleImage(scaledWidth, scaledHeight);
        PointF topLeft = new PointF(viewSize.x / 2 - scaled.getWidth() / 2,
                viewSize.y / 2 - scaled.getHeight() / 2);
        PointF topRight = new PointF(topLeft.x + scaled.getWidth(), topLeft.y);
        PointF bottomLeft = new PointF(topLeft.x, topLeft.y
                + scaled.getHeight());
        outerBorder = new RectStroke(parentView, new RectF(topLeft.x,
                topLeft.y, topRight.x, bottomLeft.y));
        bitmapMatrix.postTranslate(topLeft.x, topLeft.y);
        setBoundaries();
        return this;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#drawShadedRegion(android.graphics.Canvas)
     */
    @Override
    protected void drawShadedRegion(Canvas canvas) {
        // no shaded region for images
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#clone()
     */
    @Override
    public ImageScrap clone() {
        ImageScrap clone = null;
        try {
            clone = new ImageScrap(this);
        } catch (OutOfMemoryError e) {
            return null;
        }
        return clone;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#drawBorder(android.graphics.Canvas, float)
     */
    @Override
    protected void drawBorder(Canvas canvas, float scaleFactor) {
        // no border for images
    }
    
    private int readRotationFromExif(String src) {
        int rotate = 0;
        try {
            ExifInterface exif = new ExifInterface(src);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotate = 270;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotate = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotate = 90;
                break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rotate;
    }
    
    /**
     * Copies the image pointed by the argument uri the argument folder changing
     * its name to follow CaliSmall's convention.
     * 
     * <p>
     * If <tt>imageUri</tt> points to a file in <tt>folder</tt> the file is
     * <b>renamed</b>, and not copied (this is because that case should happen
     * only when getting pictures from the camera app, which can be set to save
     * pictures to a specific folder -- hence there's no need to copy files).
     * 
     * @param folder
     *            the absolute path to the folder into which the image is going
     *            to be copied
     * @param imageUri
     *            the absolute path to the image
     * @return the absolute path to the newly copied image
     */
    public static String copyImage(String folder, String imageUri) {
        File src = new File(imageUri);
        File imagesFolder = new File(folder, IMAGES_FOLDER_NAME);
        if (!imagesFolder.exists()) {
            imagesFolder.mkdir();
        }
        File dst = new File(imagesFolder, System.currentTimeMillis() + ".jpg");
        if (folder.equals(src.getParent()))
            // this is coming from the camera, just rename
            src.renameTo(dst);
        else {
            // coming from the gallery, copy image
            FileInputStream in = null;
            FileOutputStream out = null;
            try {
                in = new FileInputStream(src);
                out = new FileOutputStream(dst);
                FileChannel source = in.getChannel();
                FileChannel destination = out.getChannel();
                destination.transferFrom(source, 0, source.size());
                source.close();
                destination.close();
            } catch (IOException e) {
                // FIXME notify user!
                e.printStackTrace();
            } finally {
                if (in != null)
                    try {
                        in.close();
                    } catch (IOException e) {
                        // not much we can do about it...
                    }
                ;
                if (out != null)
                    try {
                        out.close();
                    } catch (IOException e) {
                        // not much we can do about it...
                    }
                ;
            }
        }
        return dst.getAbsolutePath();
    }

    /**
     * Called when changing sketch, so memory can be freed.
     */
    public void close() {
        if (scaled != null && !scaled.isRecycled())
            scaled.recycle();
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
    
    private void computeInitialTransform(PointF viewSize, Point originalSize,
            int rotate) {
        sampleSize = 1;
        if (rotate == 90 || rotate == 270) {
            viewSize = new PointF(viewSize.y, viewSize.x);
        }
        scaledWidth = originalSize.x;
        scaledHeight = originalSize.y;
        if (originalSize.x > viewSize.x || originalSize.y > viewSize.y) {
            // else if it fits, it ships!
            final float wRatio = ((float) originalSize.x / viewSize.x);
            final float hRatio = ((float) originalSize.y / viewSize.y);
            float scale = Math.max(wRatio, hRatio);
            Utils.debug("large image - original size: " + originalSize.x + "x"
                    + originalSize.y);
            scaledWidth = (int) Math.floor(originalSize.x / scale);
            scaledHeight = (int) Math.floor(originalSize.y / scale);
            sampleSize = (int) Math.max(Math.floor(scale), 1);
        }
    }
    
    private void scaleImage(int requestedWidth, int requestedHeight) {
        Utils.debug("requested size: " + requestedWidth + "x" + requestedHeight);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        System.gc();
        Bitmap downSampled = null;
        try {
            downSampled = BitmapFactory.decodeFile(srcImage, options);
            Utils.debug("loaded image size: " + downSampled.getWidth() + "x"
                    + downSampled.getHeight() + " scale is " + sampleSize);
            if (downSampled.getWidth() < requestedWidth
                    || downSampled.getHeight() < requestedHeight) {
                requestedWidth = downSampled.getWidth();
                requestedHeight = downSampled.getHeight();
            }
            float scale = (float) requestedWidth / downSampled.getWidth();
            Utils.debug("scaling of a factor " + scale + " rotating "
                    + initialRotation + " degrees");
            Matrix matrix = new Matrix();
            matrix.postRotate(initialRotation);
            matrix.postScale(scale, scale);
            scaled = Bitmap.createBitmap(downSampled, 0, 0,
                    downSampled.getWidth(), downSampled.getHeight(), matrix,
                    false);
            if (scale < 1)
                downSampled.recycle();
            Utils.debug("scaled image size: " + scaled.getWidth() + "x"
                    + scaled.getHeight());
        } catch (OutOfMemoryError e) {
            Utils.debug("out of memory, lol");
            if (downSampled != null)
                downSampled.recycle();
            downSampled = null;
            if (scaled != null)
                scaled.recycle();
            scaled = null;
            System.gc();
            e.printStackTrace();
            parentView.showOutOfMemoryError();
        }
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#toJSON()
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject object = super.toJSON();
        object.put("i", true);
        object.put("file", srcImage);
        object.put("r", initialRotation);
        object.put("h", scaledHeight);
        object.put("w", scaledWidth);
        object.put("m", Utils.matrixToJson(bitmapMatrix));
        return object;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.Scrap#fromJSON(org.json.JSONObject)
     */
    @Override
    public ImageScrap fromJSON(JSONObject jsonData) throws JSONException {
        try {
            id = jsonData.getLong("id");
        } catch (JSONException e) {
            Utils.debug("old format!");
        }
        outerBorder = new RectStroke(parentView);
        outerBorder.fromJSON(jsonData.getJSONObject("b"));
        outerBorder.getPath().close();
        try {
            JSONArray array = jsonData.getJSONArray("str");
            for (int i = 0; i < array.length(); i++) {
                Stroke stroke = parentView.getStrokeList().getById(
                        array.getString(i));
                add(stroke);
            }
        } catch (JSONException e) { /* it's ok, no strokes */}
        try {
            JSONArray array = jsonData.getJSONArray("scr");
            scrapIDs = new ArrayList<String>(array.length());
            for (int i = 0; i < array.length(); i++) {
                scrapIDs.add(array.getString(i));
            }
        } catch (JSONException e) { /* it's ok, no scraps */}
        srcImage = jsonData.getString("file");
        initialRotation = jsonData.getInt("r");
        scaledHeight = jsonData.getInt("h");
        scaledWidth = jsonData.getInt("w");
        sampleSize = 1;
        scaleImage(scaledWidth, scaledHeight);
        bitmapMatrix.set(Utils.jsonToMatrix(jsonData.getJSONArray("m")));
        setBoundaries();
        return this;
    }

}
