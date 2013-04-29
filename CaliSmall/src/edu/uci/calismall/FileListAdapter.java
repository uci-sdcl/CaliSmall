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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * An adapter that shows items in a list, where a thumbnail preview of a sketch
 * is displayed next to its name.
 * 
 * @author Michele Bonazza
 * 
 */
public class FileListAdapter extends BaseAdapter {

    private class FileListItem {
        private final String sketchName;
        private final Drawable image;

        private FileListItem(Drawable newSketch) {
            image = newSketch;
            sketchName = "Create new";
        }

        public FileListItem(File folder, String sketchName) {
            image = Drawable.createFromPath(new File(folder, sketchName
                    + CaliSmall.THUMBNAIL_EXTENSION).getAbsolutePath());
            this.sketchName = sketchName;
        }

        /**
         * Returns the name of the linked sketch, without any file extension.
         * 
         * @return the sketchName
         */
        public String getSketchName() {
            return sketchName + "";
        }

        /**
         * Returns the image to be drawn next to the linked sketch's name.
         * 
         * @return the snapshot to be drawn as a thumbnail preview
         */
        public Drawable getImage() {
            return image;
        }

    }

    /**
     * The number of items displayed for every row.
     */
    public static final int COLUMNS = 3;
    /*
     * yes, COLUMNS is hardcoded, it's ugly, but {@link GridView} is not
     * behaving and I need to have things done now!
     */
    private static final List<FileListItem> items = new ArrayList<FileListItem>();
    private static final List<Integer> IMAGE_IDS = Arrays.asList(R.id.icon1,
            R.id.icon2, R.id.icon3);
    private static final List<Integer> LABELS_IDS = Arrays.asList(R.id.label1,
            R.id.label2, R.id.label3);
    private final CaliSmall parentActivity;
    private final int selection;

    /**
     * Creates an adapter to show the argument list of file names along with
     * their thumbnail snapshot.
     * 
     * @param parent
     *            the current main instance of the activity
     * @param homeFolder
     *            the folder where all project and thumbnail files are stored
     * @param files
     *            a list of sketch names <b>without</b> any file extension
     * @param selectedSketch
     *            the index in the list of the last edited sketch
     */
    public FileListAdapter(CaliSmall parent, File homeFolder,
            List<String> files, int selectedSketch) {
        this.parentActivity = parent;
        items.clear();
        items.add(new FileListItem(parent.getResources().getDrawable(
                R.drawable.new_sketch)));
        for (String file : files) {
            items.add(new FileListItem(homeFolder, file));
        }
        // don't count the "create new" button
        selection = selectedSketch + 1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.widget.Adapter#getCount()
     */
    @Override
    public int getCount() {
        return (int) Math.ceil((double) items.size() / COLUMNS);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.widget.Adapter#getItem(int)
     */
    @Override
    public Object getItem(int position) {
        if (position > items.size() - 1)
            return null;
        return items.get(position);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.widget.Adapter#getItemId(int)
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        private final List<ImageView> images = new ArrayList<ImageView>();
        private final List<TextView> text = new ArrayList<TextView>();

        private void addLayout(View rowView) {
            for (int i : LABELS_IDS)
                text.add((TextView) rowView.findViewById(i));
            for (int i : IMAGE_IDS)
                images.add((ImageView) rowView.findViewById(i));
        }

        private void
                populate(int startingIndex, final CaliSmall parentActivity) {
            for (int i = 0; i < images.size(); i++) {
                String textString = "";
                Drawable drawable = null;
                final int position = startingIndex * images.size() + i;
                if (position < items.size()) {
                    FileListItem item = items.get(position);
                    textString = item.getSketchName();
                    drawable = item.getImage();
                    if (drawable == null)
                        drawable = parentActivity.getResources().getDrawable(
                                R.drawable.no_preview);
                }
                ImageView image = images.get(i);
                OnClickListener listener = new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        parentActivity.sketchSelected(position);
                    }
                };
                image.setOnClickListener(listener);
                TextView textView = text.get(i);
                textView.setText(textString);
                textView.setSelected(false);
                textView.setOnClickListener(listener);
                images.get(i).setImageDrawable(drawable);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.widget.Adapter#getView(int, android.view.View,
     * android.view.ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = parentActivity.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.file_list, null);
        ViewHolder viewHolder = new ViewHolder();
        viewHolder.addLayout(rowView);
        rowView.setTag(viewHolder);
        viewHolder.populate(position, parentActivity);
        // every row has COLUMNS sketches, check if selection is in this row
        if (selection > 0 && selection / COLUMNS == position) {
            viewHolder.text.get(selection % COLUMNS).setSelected(true);
        }
        return rowView;
    }
}
