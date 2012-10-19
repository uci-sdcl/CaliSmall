/**
 * CaliSmall.java Created on July 11, 2012 Copyright 2012 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package edu.uci.calismall;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

import yuku.ambilwarna.AmbilWarnaDialog;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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

    private final class ProgressBar extends AsyncTask<InputStream, Void, Void> {

        private final CaliSmall parent;
        private ProgressDialog dialog;
        private InputStream toBeLoaded;

        private ProgressBar(CaliSmall parent) {
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
            parent.setTitle("CaliSmall - " + parent.chosenFile);
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
                    save("~" + chosenFile);
                }
                lastRun = System.currentTimeMillis();
            } else {
                if (view.hasChanged()) {
                    save(chosenFile);
                }
            }
        }
    }

    /**
     * Tag used for the whole application in LogCat files.
     */
    public static final String TAG = "CaliSmall";

    private static final String FILE_EXTENSION = ".csf";
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
    private static final long AUTO_SAVE_TIME = 20 * 1000,
            AUTO_BACKUP_TIME = 3 * 60 * 1000;
    private static final long MIN_FILE_SIZE_FOR_PROGRESSBAR = 50 * 1024;

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
    Lock lock = new ReentrantLock();
    /**
     * Condition that is signalled by the drawing thread just before setting
     * itself to sleep.
     */
    Condition drawingThreadWaiting = lock.newCondition();
    /**
     * Condition that is signalled by the file opening thread just after having
     * loaded a sketch file.
     */
    Condition fileOpened = lock.newCondition();
    /**
     * Lock used while loading files to prevent the drawing thread from
     * encountering {@link ConcurrentModificationException}'s.
     */
    Object loadingLock = new Object();
    /**
     * The current view.
     */
    CaliView view;
    private String[] fileList;
    private String chosenFile, autoSaveName, tmpSnapshotName;
    private EditText input;
    private FilenameFilter fileNameFilter;
    private AlertDialog saveDialog, loadDialog, deleteDialog;
    private TimerTask autoSaver, autoBackupSaver;
    private Timer autoSaverTimer;
    private boolean userPickedANewName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new CaliView(this);
        setContentView(view);
        autoSaveName = getResources().getString(R.string.unnamed_files);
        fileNameFilter = new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                File test = new File(dir, filename);
                return (!filename.startsWith("~")
                        && filename.endsWith(FILE_EXTENSION) && test.canRead());
            }
        };
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
                    save(name);
                    setTitle(chosenFile);
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
		                                            userPickedANewName = !name.startsWith(autoSaveName)
		                                                    && !name.startsWith("~" + autoSaveName);
		                                            save(name);
		                                            setTitle(chosenFile);
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
		// @formatter:on
        final Intent intent = getIntent();
        if (intent != null) {
            final android.net.Uri data = intent.getData();
            if (data != null) {
                if (chosenFile != null)
                    save(chosenFile);
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
            if (chosenFile == null)
                newProject();
        }
    }

    private void restartAutoSaving() {
        if (autoSaverTimer != null) {
            autoSaverTimer.cancel();
        }
        autoSaverTimer = new Timer();
        autoSaver = new AutoSaveTask(this, false);
        autoBackupSaver = new AutoSaveTask(this, true);
        autoSaverTimer.scheduleAtFixedRate(autoSaver, AUTO_SAVE_TIME,
                AUTO_SAVE_TIME);
        autoSaverTimer.schedule(autoBackupSaver, AUTO_BACKUP_TIME,
                AUTO_BACKUP_TIME);
    }

    private String[] initFileList() {
        String[] fileList = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            fileList = getApplicationContext().getExternalFilesDir(null).list(
                    fileNameFilter);
            for (int i = 0; i < fileList.length; i++) {
                fileList[i] = fileList[i].endsWith(FILE_EXTENSION) ? fileList[i]
                        .substring(0, fileList[i].lastIndexOf(FILE_EXTENSION))
                        : fileList[i];
            }
            Arrays.sort(fileList);
        }
        return fileList;
    }

    /**
     * Returns the current view.
     * 
     * @return the view
     */
    public CaliView getView() {
        return view;
    }

    private void save(final String input) {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {

            try {
                lock.lock();
                File path = getApplicationContext().getExternalFilesDir(null);
                File newFile = new File(path, input + FILE_EXTENSION);
                if (userPickedANewName) {
                    // delete the "Unnamed Sketch" file
                    fileList = null;
                    if (chosenFile.startsWith(autoSaveName)) {
                        new File(path, chosenFile + FILE_EXTENSION).delete();
                        new File(path, "~" + chosenFile + FILE_EXTENSION)
                                .delete();
                    }
                    fileList = initFileList();
                    chosenFile = input;
                    userPickedANewName = false;
                    restartAutoSaving();
                }
                String json = toJSON().toString();
                FileWriter writer = new FileWriter(newFile);
                writer.write(json);
                writer.flush();
                writer.close();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
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
    }

    private void load(File toBeLoaded) {
        try {
            load(new FileInputStream(toBeLoaded));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load(InputStream toBeLoaded) {
        try {
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
            JSONObject json = new JSONObject(builder.toString());
            syncAndLoad(json);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
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
        switch (item.getItemId()) {
        case R.id.color:
            AmbilWarnaDialog dialog = view.getColorPicker();
            dialog.show();
            return true;
        case R.id.clear:
            view.clear();
            return true;
        case R.id.log:
            view.printLog();
            return true;
        case R.id.menu_save:
            saveButtonClicked();
            return true;
        case R.id.save:
            saveDialog.show();
            return true;
        case R.id.open:
            showLoadDialog();
            return true;
        case R.id.open_next:
            loadNext();
            return true;
        case R.id.open_previous:
            loadPrevious();
            return true;
        case R.id.create_new:
            newProject();
            return true;
        case R.id.share:
            share();
            return true;
        case R.id.share_snapshot:
            shareSnapshot();
            return true;
        case R.id.delete:
            deleteDialog.setMessage(String.format(
                    getResources().getString(R.string.delete_dialog_message),
                    chosenFile));
            deleteDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void newProject() {
        userPickedANewName = false;
        chosenFile = generateAutoSaveName();
        view.clear();
        input.setText("");
        setTitle("CaliSmall - "
                + getResources().getString(R.string.unnamed_files));
    }

    private void delete() {
        fileList = null;
        String fileName = chosenFile;
        File path = getApplicationContext().getExternalFilesDir(null);
        File newFile = new File(path, fileName + FILE_EXTENSION);
        if (newFile.exists()) {
            newFile.delete();
            newFile = new File(path, "~" + fileName + FILE_EXTENSION);
            if (newFile.exists())
                newFile.delete();
        }
        fileList = initFileList();
        loadNext();
        Toast.makeText(
                getApplicationContext(),
                String.format(getResources().getString(R.string.delete_done),
                        fileName), Toast.LENGTH_SHORT).show();
    }

    private void saveButtonClicked() {
        if (chosenFile.startsWith(autoSaveName))
            saveDialog.show();
        else {
            restartAutoSaving();
            save(chosenFile);
            Toast.makeText(
                    getApplicationContext(),
                    String.format(
                            getResources().getString(R.string.file_saved),
                            chosenFile), Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        save(chosenFile);
        File path = getApplicationContext().getExternalFilesDir(null);
        File newFile = new File(path, chosenFile + FILE_EXTENSION);
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
        File path = getApplicationContext().getExternalFilesDir(null);
        File tmpImage = new File(path, chosenFile + ".png");
        tmpSnapshotName = chosenFile + ".png";
        try {
            lock.lock();
            Painter painter = view.getPainter();
            painter.stopForFileOpen(lock, fileOpened, drawingThreadWaiting);
            while (!painter.isWaiting())
                try {
                    drawingThreadWaiting.await(Painter.SCREEN_REFRESH_TIME,
                            TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            view.createSnapshot(tmpImage, fileOpened);
        } finally {
            lock.unlock();
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
                        getResources().getString(R.string.share_message)), 1);
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
        File path = getApplicationContext().getExternalFilesDir(null);
        File tmpImage = new File(path, tmpSnapshotName);
        if (tmpImage.exists())
            tmpImage.delete();
    }

    private String generateAutoSaveName() {
        return autoSaveName + " created on "
                + DATE_FORMATTER.format(new Date());
    }

    private void showLoadDialog() {
        fileList = initFileList();
        loadDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.load_dialog_message)
                .setItems(fileList, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        loadAndMaybeShowProgressBar(fileList[which]);
                    }
                }).create();
        loadDialog.show();
    }

    private void loadAndMaybeShowProgressBar(String file) {
        chosenFile = file;
        input.setText(chosenFile);
        view.resetChangeCounter();
        final File toBeLoaded = new File(getApplicationContext()
                .getExternalFilesDir(null), file + FILE_EXTENSION);
        if (toBeLoaded.length() > MIN_FILE_SIZE_FOR_PROGRESSBAR) {
            try {
                new ProgressBar(this).execute(new FileInputStream(toBeLoaded));
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
            setTitle("CaliSmall - " + fileName);
            restartAutoSaving();
        }
    }

    private void loadAndMaybeShowProgressBar(InputStream input) {
        view.resetChangeCounter();
        new ProgressBar(this).execute(input);
    }

    private void loadNext() {
        userPickedANewName = false;
        fileList = initFileList();
        if (fileList != null && fileList.length > 0) {
            userPickedANewName = !chosenFile.startsWith(autoSaveName);
            int fileIndex = Arrays.binarySearch(fileList, chosenFile);
            fileIndex = fileIndex % fileList.length;
            if (fileIndex < 0)
                fileIndex += fileList.length;
            fileIndex++;
            fileIndex = fileIndex % fileList.length;
            loadAndMaybeShowProgressBar(fileList[fileIndex]);
        }
    }

    private void loadPrevious() {
        userPickedANewName = false;
        fileList = initFileList();
        if (fileList != null && fileList.length > 0) {
            userPickedANewName = !chosenFile.startsWith(autoSaveName);
            int fileIndex = Arrays.binarySearch(fileList, chosenFile);
            fileIndex = fileIndex % fileList.length;
            if (fileIndex < 0)
                fileIndex += fileList.length;
            fileIndex--;
            fileIndex = fileIndex % fileList.length;
            if (fileIndex < 0)
                fileIndex += fileList.length;
            loadAndMaybeShowProgressBar(fileList[fileIndex]);
        }
    }

    /**
     * Returns what <tt>PointF.toString()</tt> should have returned, but Android
     * developers were too lazy to implement.
     * 
     * @param point
     *            the point of which a String representation must be returned
     * @return a String containing the point's coordinates enclosed within
     *         parentheses
     */
    public static String pointToString(PointF point) {
        return new StringBuilder("(").append(point.x).append(",")
                .append(point.y).append(")").toString();
    }

    /**
     * Returns what <tt>Point.toString()</tt> should have returned (without the
     * initial <tt>"Point"</tt> that the <tt>toString()</tt> default
     * implementation returns).
     * 
     * @param point
     *            the point of which a String representation must be returned
     * @return a String containing the point's coordinates enclosed within
     *         parentheses
     */
    public static String pointToString(Point point) {
        return new StringBuilder("(").append(point.x).append(",")
                .append(point.y).append(")").toString();
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
        return this;
    }

    private void syncAndLoad(JSONObject jsonData) throws JSONException {
        try {
            lock.lock();
            Painter painter = view.getPainter();
            painter.stopForFileOpen(lock, fileOpened, drawingThreadWaiting);
            while (!painter.isWaiting())
                drawingThreadWaiting.await(Painter.SCREEN_REFRESH_TIME,
                        TimeUnit.MILLISECONDS);
            fromJSON(jsonData);
            fileOpened.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

}
