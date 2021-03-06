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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

/**
 * A small version of Calico for Android devices.
 * 
 * <p>
 * This version is still a standalone app that does no communication with Calico
 * server(s).
 * 
 * @author Michele Bonazza
 */
public class CaliSmall extends Activity implements JSONSerializable<CaliSmall> {

    private final class LoadProgressBar extends
            AsyncTask<InputStream, Void, Void> {

        private final CaliSmall parent;
        private ProgressDialog dialog;
        private InputStream toBeLoaded;

        private LoadProgressBar(CaliSmall parent) {
            this.parent = parent;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(InputStream... params) {
            toBeLoaded = params[0];
            load(toBeLoaded);
            restartAutoSaving();
            return null;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            if (loadDialog != null) {
                loadDialog.hide();
                loadDialog.dismiss();
            }
            Resources res = getResources();
            dialog = ProgressDialog.show(parent,
                    res.getString(R.string.load_dialog_progress),
                    res.getString(R.string.load_dialog_progress_message), true);
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Void result) {
            dialog.dismiss();
            setTitle(String.format(POS_FORMAT, currentFileListIndex + 1,
                    fileList.size(), parent.chosenFile));
        }

    }
    
    private final class StyleWrapper {
        public int color = Color.BLACK, thickness = 2;
    }

    private final class SaveProgressBar extends AsyncTask<String, Void, Void> {

        private final CaliSmall parent;
        private ProgressDialog dialog;
        private String toBeSaved;
        private Runnable callback;

        private SaveProgressBar(CaliSmall parent) {
            this.parent = parent;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(String... params) {
            toBeSaved = params[0];
            try {
                saveLock.lock();
                save(toBeSaved, parent.toJSON().toString());
                fileHasBeenSaved = true;
                fileSaved.signalAll();
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                saveLock.unlock();
            }
            restartAutoSaving();
            return null;
        }

        private SaveProgressBar setCallback(Runnable callback) {
            this.callback = callback;
            return this;
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            if (saveDialog != null) {
                saveDialog.hide();
                saveDialog.dismiss();
            }
            Resources res = getResources();
            dialog = ProgressDialog.show(parent,
                    res.getString(R.string.save_dialog_progress),
                    res.getString(R.string.save_dialog_progress_message), true);
        }

        /*
         * (non-Javadoc)
         * 
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Void result) {
            dialog.dismiss();
            Toast.makeText(
                    getApplicationContext(),
                    String.format(
                            getResources().getString(R.string.file_saved),
                            toBeSaved), Toast.LENGTH_SHORT).show();
            if (callback != null)
                callback.run();
        }

    }

    private final class AutoSaveTask extends TimerTask {

        private final boolean saveBackupFiles;
        private final CaliSmall parent;
        private long lastRun;

        private AutoSaveTask(CaliSmall parent, boolean saveBackupFiles) {
            this.saveBackupFiles = saveBackupFiles;
            this.parent = parent;
            lastRun = System.currentTimeMillis();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.TimerTask#run()
         */
        @Override
        public void run() {
            if (saveBackupFiles) {
                File file = new File(parent.getApplicationContext()
                        .getExternalFilesDir(null), chosenFile + FILE_EXTENSION);
                if (file.lastModified() > lastRun) {
                    // time to save a new backup!
                    try {
                        save("~" + chosenFile, parent.toJSON().toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                lastRun = System.currentTimeMillis();
            } else {
                if (view.hasChanged()) {
                    try {
                        save(chosenFile, parent.toJSON().toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Tag used for the whole application in LogCat files.
     */
    public static final String TAG = "CaliSmall";
    /**
     * The number of threads used to execute timer tasks with.
     */
    public static final int THREADS_FOR_TIMERS = 2;
    /**
     * The result code to be passed back to this activity when an image was
     * loaded from the gallery app.
     */
    public static final int IMAGE_LOADED_FROM_GALLERY = 0;
    /**
     * The result code to be passed back to this activity when an image was
     * loaded from the camera app.
     */
    public static final int IMAGE_LOADED_FROM_CAMERA = 1;
    /**
     * The file extension used when saving thumbnail snapshots of sketches.
     */
    public static final String THUMBNAIL_EXTENSION = ".thumb";
    /**
     * The result code to be passed back to this activity when a CaliSmall file
     * (or a snapshot) was successfully shared.
     */
    public static final int CONTENT_SHARED = 2;

    private static final SparseIntArray COLOR_SWATCHES = new SparseIntArray();
    private static final SparseIntArray LINE_THICKNESSES = new SparseIntArray();
    private static final int[] COLOR_SWATCHES_IDS = { R.id.black, R.id.gray,
        R.id.light_gray, R.id.purple, R.id.blue, R.id.green, R.id.red,
        R.id.orange, R.id.yellow };
    private static final int[] LINE_THICKNESS_IDS = { R.id.line_1px,
        R.id.line_2px, R.id.line_3px, R.id.line_5px, R.id.line_7px,
        R.id.line_9px };
    private static final String FILE_EXTENSION = ".csf";
//    private static final String THUMBNAIL_EXTENSION = ".thumb";
    private static final String LIST_FILE_NAME = ".file_list";
    private static final String DISABLE_GALLERY = ".nomedia";
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final long AUTO_SAVE_TIME = 20 * 1000,
            AUTO_BACKUP_TIME = 3 * 60 * 1000;
    private static final long MIN_FILE_SIZE_FOR_PROGRESSBAR = 100 * 1024;
    private static final int MIN_POINTS_PER_PROGRESSBAR = 1000;
    private static final String POS_FORMAT = "(%d/%d) %s";
    private final List<ImageButton> colorSwatches = new ArrayList<ImageButton>(),
            lineThicknesses = new ArrayList<ImageButton>();
    private final StyleWrapper styleWrapper = new StyleWrapper();

    /*************************************************************************
     * DISCLAIMER FOR THE READER
     * 
     * A good bunch of all the following package-protected fields should really
     * be private if we were in Javadom. As it turns out, whenever private
     * fields are accessed from within other nested classes (such as CaliView,
     * just to name one), you hit a performance slowdown of allegedly 7x (see
     * http://developer.android.com/guide/practices/performance.html for
     * reference). Since we don't care about shielding access from other classes
     * in the package that much, we can just use package-protected. It is also
     * quite good from a Javadoc point of view. Just don't abuse accessing these
     * variables from outside of this file, don't be that guy!
     ************************************************************************/

    /**
     * A lock that is shared between the drawing thread and the Event Dispatch
     * Thread used when opening files.
     */
    Lock openLock = new ReentrantLock();
    /**
     * A lock that is used to synchronize the saving and opening actions.
     */
    Lock saveLock = new ReentrantLock();
    /**
     * Condition that is signalled by the drawing thread just before setting
     * itself to sleep.
     */
    Condition drawingThreadWaiting = openLock.newCondition();
    /**
     * Condition that is signalled by the file opening thread just after having
     * loaded a sketch file.
     */
    Condition fileOpened = openLock.newCondition();

    /**
     * Condition that is signalled by the thread that is saving a file when it's
     * done.
     */
    Condition fileSaved = saveLock.newCondition();
    /**
     * The current view.
     */
    CaliView view;
    /**
     * Whether the current project has been saved to file.
     */
    boolean fileHasBeenSaved;
    private List<String> fileList = new ArrayList<String>();
    private String chosenFile, autoSaveName, tmpSnapshotName;
    private File homeFolder;
    private int currentFileListIndex = 0;
    private EditText input;
    private Dialog styleDialog, loadDialog, outOfMemoryDialog;
    private AlertDialog saveDialog, deleteDialog;
    private TimerTask autoSaver, autoBackupSaver;
    private ScheduledExecutorService autoSaverTimer;
    private CheckBox resizeWithZoom;
    private Uri imageURI;
    private boolean userPickedANewName, eraserMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new CaliView(this);
        initSwatches();
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            homeFolder = getApplicationContext().getExternalFilesDir(null);
        } else {
            // FIXME use internal memory?
        }
        setContentView(view);
        autoSaveName = getResources().getString(R.string.unnamed_files);
        autoSaver = new AutoSaveTask(this, false);
        autoBackupSaver = new AutoSaveTask(this, true);
        final EditText input = new EditText(this);
        this.input = input;
        input.setSingleLine();
        input.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String name = input.getText().toString();
                    userPickedANewName = !name.startsWith(autoSaveName)
                            && !name.startsWith("~" + autoSaveName);
                    saveAndMaybeShowProgressBar(name);
                    setTitle(String.format(POS_FORMAT,
                            currentFileListIndex + 1, fileList.size(),
                            chosenFile));
                    saveDialog.dismiss();
                }
                return true;
            }
        });
        // @formatter:off
        saveDialog = new AlertDialog.Builder(this)
		                            .setTitle(R.string.save_dialog_title)
		                            .setMessage(R.string.save_dialog_message)
		                            .setView(input)
		                            .setPositiveButton(android.R.string.ok,
		                                    new DialogInterface.OnClickListener() {
		                                        public void onClick(DialogInterface dialog, int whichButton) {
		                                            String name = input.getText().toString();
		                                            userPickedANewName = !name.equals(chosenFile);
		                                            saveAndMaybeShowProgressBar(name);
		                                            setTitle(String.format(POS_FORMAT,
		                                                    currentFileListIndex + 1, fileList.size(), chosenFile));
		                                        }
		                            })
		                            .setNegativeButton(android.R.string.cancel,
		                                    new DialogInterface.OnClickListener() {
		                                        public void onClick(DialogInterface dialog, int whichButton) {
		                                            // Canceled.
		                                        }
		                            }).create();
        deleteDialog = new AlertDialog.Builder(this)
        .setTitle(R.string.delete_dialog_title)
        .setPositiveButton(android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        delete();
                    }
        })
        .setNegativeButton(android.R.string.no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
        }).create();
        fileList = initFileList();
		// @formatter:on
        final Intent intent = getIntent();
        if (intent != null) {
            final android.net.Uri data = intent.getData();
            if (data != null) {
                if (chosenFile != null)
                    saveAndMaybeShowProgressBar(chosenFile);
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            /**
                             * The load call must be delayed because if the app
                             * was started through an intent the drawing thread
                             * is not started until this method ends, causing a
                             * deadlock on the load function
                             */
                            loadAndMaybeShowProgressBar(getContentResolver()
                                    .openInputStream(data));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }, Painter.SCREEN_REFRESH_TIME * 2);
            }
        }
        createStyleDialog();
        createOutOfMemoryDialog();
        if (chosenFile == null) {
            newSketch();
        }
    }
    
    private void initSwatches() {
        Resources rez = getResources();
        COLOR_SWATCHES.put(R.id.black, rez.getColor(R.color.black));
        COLOR_SWATCHES.put(R.id.gray, rez.getColor(R.color.gray));
        COLOR_SWATCHES.put(R.id.light_gray, rez.getColor(R.color.light_gray));
        COLOR_SWATCHES.put(R.id.purple, rez.getColor(R.color.purple));
        COLOR_SWATCHES.put(R.id.green, rez.getColor(R.color.green));
        COLOR_SWATCHES.put(R.id.blue, rez.getColor(R.color.blue));
        COLOR_SWATCHES.put(R.id.red, rez.getColor(R.color.red));
        COLOR_SWATCHES.put(R.id.orange, rez.getColor(R.color.orange));
        COLOR_SWATCHES.put(R.id.yellow, rez.getColor(R.color.yellow));
        LINE_THICKNESSES.put(R.id.line_1px, 1);
        LINE_THICKNESSES.put(R.id.line_2px, 2);
        LINE_THICKNESSES.put(R.id.line_3px, 3);
        LINE_THICKNESSES.put(R.id.line_5px, 5);
        LINE_THICKNESSES.put(R.id.line_7px, 7);
        LINE_THICKNESSES.put(R.id.line_9px, 9);
    }
    
    private void createStyleDialog() {
        styleDialog = new Dialog(this);
        LayoutInflater inflater = getLayoutInflater();
        View swatchesView = inflater.inflate(R.layout.color_swatches, null);
        styleDialog.setContentView(swatchesView);
        styleDialog.setOnDismissListener(new OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dialog) {
                view.styleChanged(styleWrapper.color,
                        resizeWithZoom.isChecked(), styleWrapper.thickness);
            }
        });
        colorSwatches.clear();
        for (int id : COLOR_SWATCHES_IDS) {
            ImageButton button = (ImageButton) (swatchesView.findViewById(id));
            colorSwatches.add(button);
            button.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    for (ImageButton button : colorSwatches) {
                        button.setSelected(false);
                    }
                    v.setSelected(true);
                    styleWrapper.color = COLOR_SWATCHES.get(v.getId());
                }
            });
        }
        colorSwatches.get(0).setSelected(true);
        lineThicknesses.clear();
        for (int id : LINE_THICKNESS_IDS) {
            ImageButton button = (ImageButton) (swatchesView.findViewById(id));
            lineThicknesses.add(button);
            button.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    for (ImageButton button : lineThicknesses) {
                        button.setSelected(false);
                    }
                    v.setSelected(true);
                    styleWrapper.thickness = LINE_THICKNESSES.get(v.getId());
                }
            });
        }
        lineThicknesses.get(1).setSelected(true);
        resizeWithZoom = (CheckBox) (swatchesView
                .findViewById(R.id.resize_with_zoom));
        Button close = (Button) (swatchesView
                .findViewById(R.id.close_style_dialog));
        close.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                styleDialog.dismiss();
            }
        });
        styleDialog.setTitle(R.string.style_dialog_title);
    }
    
    private void createOutOfMemoryDialog() {
        // @formatter:off
        outOfMemoryDialog = new AlertDialog.Builder(this)
            .setTitle(R.string.out_of_memory_dialog_title)
            .setMessage(R.string.out_of_memory_dialog_message)
            .setCancelable(false)
            .setPositiveButton(getResources().getText(android.R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                     outOfMemoryDialog.dismiss();
                }
            })               
            .create();
        // @formatter:on
    }

    private RectF getDisplaySize() {
        Point screenSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(screenSize);
        return new RectF(0, 0, screenSize.x, screenSize.y);
    }

    /**
     * Puts the auto save in pause.
     */
    public void pauseAutoSaving() {
        if (autoSaverTimer != null) {
            autoSaver.cancel();
            autoBackupSaver.cancel();
            autoSaverTimer.shutdownNow();
        }
        autoSaverTimer = Executors.newScheduledThreadPool(THREADS_FOR_TIMERS);
    }

    private void restartAutoSaving() {
        pauseAutoSaving();
        autoSaverTimer.scheduleAtFixedRate(autoSaver, AUTO_SAVE_TIME,
                AUTO_SAVE_TIME, TimeUnit.MILLISECONDS);
        autoSaverTimer.scheduleAtFixedRate(autoBackupSaver, AUTO_BACKUP_TIME,
                AUTO_BACKUP_TIME, TimeUnit.MILLISECONDS);
    }

    private List<String> initFileList() {
        List<String> files = new ArrayList<String>();
        BufferedReader reader = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            File listFileProject = new File(homeFolder, LIST_FILE_NAME);
            try {
                if (!listFileProject.exists())
                    listFileProject.createNewFile();
                reader = new BufferedReader(new FileReader(
                        listFileProject));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    String fileName = line.endsWith(FILE_EXTENSION) ? line
                            .substring(0, line.lastIndexOf(FILE_EXTENSION))
                            : line;
                    files.add(fileName);
                }
            } catch (FileNotFoundException e) {
                try {
                    listFileProject.createNewFile();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            	if (reader != null)
					try {
						reader.close();
					} catch (IOException e) {
						// can't do much more than that, really...
						e.printStackTrace();
					}
            }
        }
        return files;
    }

    /**
     * Returns the current view.
     * 
     * @return the view
     */
    CaliView getView() {
        return view;
    }

    private void save(final String input, final String jsonData) {
        try {
            openLock.lock();
            File newFile = new File(homeFolder, input + FILE_EXTENSION);
            chosenFile = input;
            updateFileList();
            FileWriter writer = new FileWriter(newFile);
            writer.write(jsonData);
            writer.flush();
            writer.close();
            File thumbnail = new File(homeFolder, input + THUMBNAIL_EXTENSION);
            view.createThumbnail(thumbnail);
            view.resetChangeCounter();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            openLock.unlock();
        }
    }

    private SaveProgressBar getSaveProgressBar(final String input) {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            if (userPickedANewName) {
                new File(homeFolder, chosenFile + FILE_EXTENSION).delete();
                new File(homeFolder, "~" + chosenFile + FILE_EXTENSION).delete();
                fileList.remove(currentFileListIndex);
                chosenFile = input;
                fileList.add(currentFileListIndex, input);
                userPickedANewName = false;
                restartAutoSaving();
            }
            updateFileList();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.save_dialog_fail_title)
                    .setMessage(R.string.save_dialog_fail_message)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    saveInternal(input);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    Toast.makeText(
                                            getApplicationContext(),
                                            R.string.save_dialog_file_not_saved,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }).create();
        }
        if (view.areThereMoreThanThisPoints(MIN_POINTS_PER_PROGRESSBAR)) {
            return new SaveProgressBar(this);
        }
        return null;
    }

    private void saveAndMaybeShowProgressBar(final String input) {
        fileHasBeenSaved = false;
        SaveProgressBar progressBar = getSaveProgressBar(input);
        if (progressBar != null)
            progressBar.execute(chosenFile);
        else {
            try {
                saveLock.lock();
                save(chosenFile, toJSON().toString());
                fileHasBeenSaved = true;
                fileSaved.signalAll();
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                saveLock.unlock();
            }
        }
    }

    private void updateFileList() {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            File listFileProject = new File(homeFolder, LIST_FILE_NAME);
            File noMedia = new File(homeFolder, DISABLE_GALLERY);
            try {
                if (!noMedia.exists()) {
                    noMedia.createNewFile();
                }
                PrintStream ps = new PrintStream(listFileProject);
                for (String file : fileList) {
                    ps.println(file);
                }
                ps.flush();
                ps.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void load(File toBeLoaded) {
        try {
            load(new FileInputStream(toBeLoaded));
        } catch (FileNotFoundException e) {
            fileList.remove(currentFileListIndex);
            updateFileList();
        }
    }

    private void load(InputStream toBeLoaded) {
        try {
            runOnUiThread(new Runnable() {
                public void run() {
                    invalidateOptionsMenu();
                }
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    toBeLoaded));
            String in, newLine = "";
            StringBuilder builder = new StringBuilder();
            while ((in = reader.readLine()) != null) {
                builder.append(newLine);
                builder.append(in);
                newLine = "\n";
            }
            reader.close();
            syncAndLoad(builder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            Log.e(TAG, "JSONException", e);
            loadPrevious();
        }
    }

    private void saveInternal(String fileName) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.activity_cali_small, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean knownOption = true;
        switch (item.getItemId()) {
        case R.id.fit_zoom:
            view.fitZoom();
            break;
        case R.id.eraser:
            view.toggleEraserMode();
            eraserMode = !eraserMode;
            invalidateOptionsMenu();
            break;
        case R.id.line_style:
            if (eraserMode) {
                view.toggleEraserMode();
                eraserMode = !eraserMode;
                invalidateOptionsMenu();
            }
            styleDialog.show();
            break;
        case R.id.camera:
            loadImage(true);
            break;
        case R.id.gallery:
            loadImage(false);
            break;
        case R.id.save:
            saveButtonClicked();
            break;
        case R.id.save_as:
            if (chosenFile.startsWith(autoSaveName)) {
                input.setText("");
            }
            saveDialog.show();
            break;
        case R.id.open:
            showLoadDialog();
            break;
        case R.id.create_new:
            newSketch();
            break;
        case R.id.share:
            share();
            break;
        case R.id.share_snapshot:
            shareSnapshot();
            break;
        case R.id.delete:
            deleteDialog.setMessage(String.format(
                    getResources().getString(R.string.delete_dialog_message),
                    chosenFile));
            deleteDialog.show();
            break;
        default:
            knownOption = false;
        }
        return knownOption || super.onOptionsItemSelected(item);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem eraser = menu.findItem(R.id.eraser);
        eraser.setIcon(eraserMode ? R.drawable.ic_eraser_highlighted
                : R.drawable.ic_eraser);
        return super.onPrepareOptionsMenu(menu);
    }

    private void newSketch() {
        userPickedANewName = false;
        if (chosenFile != null) {
            saveAndMaybeShowProgressBar(chosenFile);
            try {
                saveLock.lock();
                while (!fileHasBeenSaved) {
                    try {
                        fileSaved.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                saveLock.unlock();
            }
        }
        view.reset();
        view.forceRedraw();
        view.setDrawableCanvas(getDisplaySize());
        chosenFile = generateAutoSaveName();
        saveAndMaybeShowProgressBar(chosenFile);
        fileList.add(currentFileListIndex, chosenFile);
        invalidateOptionsMenu();
        input.setText("");
        setTitle(String.format(POS_FORMAT, currentFileListIndex + 1,
                fileList.size(),
                getResources().getString(R.string.unnamed_files)));
    }

    private void delete() {
        String fileName = chosenFile;
        File newFile = new File(homeFolder, fileName + FILE_EXTENSION);
        if (newFile.exists()) {
            if (!newFile.delete())
                Utils.debug("couldn't delete file");
            newFile = new File(homeFolder, "~" + fileName + FILE_EXTENSION);
            if (newFile.exists())
                if (!newFile.delete())
                    Utils.debug("couldn't delete file");
            fileList.remove(currentFileListIndex);
            updateFileList();
        } else {
            Utils.debug("file " + newFile.getAbsolutePath() + " doesn't exist!");
        }
        fileList = initFileList();
        if (fileList.isEmpty()) {
            currentFileListIndex = 0;
            newSketch();
        } else {
            loadPrevious();
        }
        Toast.makeText(
                getApplicationContext(),
                String.format(getResources().getString(R.string.delete_done),
                        fileName), Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    private void saveButtonClicked() {
        if (chosenFile.startsWith(autoSaveName)) {
            input.setText("");
            saveDialog.show();
        } else {
            restartAutoSaving();
            saveAndMaybeShowProgressBar(chosenFile);
        }
    }

    private void share() {
        saveAndMaybeShowProgressBar(chosenFile);
        File newFile = new File(homeFolder, chosenFile + FILE_EXTENSION);
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(newFile));
        intent.putExtra(Intent.EXTRA_SUBJECT, R.string.share_message_subject);
        intent.putExtra(Intent.EXTRA_TEXT,
                getResources().getString(R.string.share_message_text));
        startActivity(Intent.createChooser(intent,
                getResources().getString(R.string.share_message)));
    }

    private void shareSnapshot() {
        File tmpImage = new File(homeFolder, chosenFile + ".png");
        tmpSnapshotName = chosenFile + ".png";
        try {
            openLock.lock();
            Painter painter = view.getPainter();
            painter.stopForFileOpen(openLock, fileOpened, drawingThreadWaiting);
            while (!painter.isWaiting())
                try {
                    drawingThreadWaiting.await(Painter.SCREEN_REFRESH_TIME,
                            TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            view.createSnapshot(tmpImage, fileOpened);
        } finally {
            openLock.unlock();
        }
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tmpImage));
        intent.putExtra(Intent.EXTRA_SUBJECT, R.string.share_message_subject);
        intent.putExtra(Intent.EXTRA_TEXT,
                getResources().getString(R.string.share_message_text));
        startActivityForResult(
                Intent.createChooser(intent,
                        getResources().getString(R.string.share_message)),
                CONTENT_SHARED);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onActivityResult(int, int,
     * android.content.Intent)
     */
    @Override
    protected void
            onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case IMAGE_LOADED_FROM_CAMERA:
            if (resultCode == RESULT_OK)
            	if (resultCode == RESULT_OK)
                    view.addScrap(new ImageScrap(view).setImage(ImageScrap
                            .copyImage(homeFolder.getAbsolutePath(),
                                    imageURI.getPath())), false);
            break;
        case IMAGE_LOADED_FROM_GALLERY:
            if (resultCode == RESULT_OK) {
                String imagePath = getGalleryImagePath(data.getData());
                if (imagePath != null)
                    view.addScrap(
                            new ImageScrap(view).setImage(ImageScrap.copyImage(
                                    homeFolder.getAbsolutePath(), imagePath)),
                            false);
            }
            break;
        case CONTENT_SHARED:
            File tmpImage = new File(homeFolder, tmpSnapshotName);
            if (tmpImage.exists())
                tmpImage.delete();
            break;
        }
    }
    
    /**
     * Returns a reference to the home directory for the app.
     * 
     * @return a file pointing to the home folder for the app
     */
    public File getHomeFolder() {
        return homeFolder;
    }

    private String getGalleryImagePath(Uri path) {
        if (path.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(path, null, null, null,
                    null);
            cursor.moveToFirst();
            return cursor.getString(cursor
                    .getColumnIndexOrThrow(Images.Media.DATA));
        }
        return null;
    }
    
    private String generateAutoSaveName() {
        return autoSaveName + " created on "
                + DATE_FORMATTER.format(new Date());
    }
    
    /**
     * Displays an error dialog informing the user that there's not enough
     * memory to load the image that she tried to import to the sketch.
     */
    public void showOutOfMemoryError() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                outOfMemoryDialog.show();
            }
        });
    }
    
    private void showLoadDialog() {
        loadDialog = new Dialog(this);
        if (chosenFile == null) {
            // no escape from Arkham City
            loadDialog.setCancelable(false);
            loadDialog.setCanceledOnTouchOutside(false);
        }
        ListView fileList = new ListView(getApplicationContext());
        fileList.setAdapter(new FileListAdapter(this, homeFolder,
                this.fileList, currentFileListIndex));
        fileList.setSelection(currentFileListIndex / FileListAdapter.COLUMNS);
        loadDialog.setContentView(fileList);
        loadDialog.setTitle(R.string.load_dialog_message);
        loadDialog.show();
    }
    
    /**
     * Callback called by {@link FileListAdapter} when an item is selected from
     * the list.
     * 
     * @param index
     *            the selection index
     */
    public void sketchSelected(int index) {
        // first slot is occupied by "create new"
        index--;
        loadDialog.dismiss();
        if (index < 0) {
            currentFileListIndex = fileList.size() - 1;
            newSketch();
        } else if (index < fileList.size()) {
            currentFileListIndex = index;
            loadAndMaybeShowProgressBar(fileList.get(index));
        }
    }

    private void loadAndMaybeShowProgressBar(final String file) {
        final CaliSmall parent = this;
        Runnable callback = new Runnable() {
            public void run() {
                chosenFile = file;
                input.setText(chosenFile);
                input.setSelection(chosenFile.length());
                final File toBeLoaded = new File(getApplicationContext()
                        .getExternalFilesDir(null), file + FILE_EXTENSION);
                if (toBeLoaded.length() > MIN_FILE_SIZE_FOR_PROGRESSBAR) {
                    try {
                        new LoadProgressBar(parent)
                                .execute(new FileInputStream(toBeLoaded));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } else {
                    load(toBeLoaded);
                    String fileName = toBeLoaded.getName();
                    if (fileName.endsWith(FILE_EXTENSION)) {
                        fileName = fileName.substring(0,
                                fileName.lastIndexOf(FILE_EXTENSION));
                    }
                    setTitle(String
                            .format(POS_FORMAT, currentFileListIndex + 1,
                                    fileList.size(), fileName));
                    restartAutoSaving();
                }
            }
        };
        if (view.hasChanged()) {
            SaveProgressBar saveProgressBar = getSaveProgressBar(chosenFile);
            if (saveProgressBar != null) {
                saveProgressBar.setCallback(callback);
                saveProgressBar.execute(chosenFile);
                return;
            } else {
                saveAndMaybeShowProgressBar(chosenFile);
            }
        }
        callback.run();
    }

    private void loadAndMaybeShowProgressBar(InputStream input) {
        new LoadProgressBar(this).execute(input);
    }

/*    private void loadImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File picture;
        picture = new File(homeFolder, "tmp_image.jpg");
        picture.delete();
        imageURI = Uri.fromFile(picture);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageURI);
        // start camera intent
        startActivityForResult(intent, IMAGE_LOADED_FROM_CAMERA);

    }*/
    
    private void loadImage(boolean fromCamera) {
        File picture;
        picture = new File(homeFolder, "tmp_image.jpg");
        picture.delete();
        imageURI = Uri.fromFile(picture);
        int returnCode;
        Intent intent;
        if (fromCamera) {
            intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageURI);
            returnCode = IMAGE_LOADED_FROM_CAMERA;
        } else {
            intent = new Intent(
                    Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            returnCode = IMAGE_LOADED_FROM_GALLERY;
        }
        startActivityForResult(intent, returnCode);
    }

    private void loadPrevious() {
        userPickedANewName = false;
        currentFileListIndex = currentFileListIndex > 0 ? --currentFileListIndex
                : currentFileListIndex;
        loadAndMaybeShowProgressBar(fileList.get(currentFileListIndex));
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#toJSON()
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        return view.toJSON();
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.calismall.JSONSerializable#fromJSON(org.json.JSONObject)
     */
    @Override
    public CaliSmall fromJSON(JSONObject jsonData) throws JSONException {
        view.fromJSON(jsonData);
        view.resetChangeCounter();
        return this;
    }

    private void syncAndLoad(String jsonString) throws JSONException {
        try {
            openLock.lock();
            Painter painter = view.getPainter();
            painter.stopForFileOpen(openLock, fileOpened, drawingThreadWaiting);
            while (!painter.isWaiting()) {
                drawingThreadWaiting.await(Painter.SCREEN_REFRESH_TIME,
                        TimeUnit.MILLISECONDS);
            }
            view.close();
            fromJSON(new JSONObject(jsonString));
            view.fitZoom();
            fileOpened.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            openLock.unlock();
        }
    }

}
