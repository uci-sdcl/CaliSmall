package edu.uci.calismall;

import android.content.Context;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.graphics.PorterDuff;

//GridView Adapter for displaying and selecting color and line styles
public class GridAdapter extends BaseAdapter {
	
	private final Context context;
	
	//btnMap holds positions mapped to their respective Image Buttons
	private final SparseArray<ImageButton> btnMap = new SparseArray<ImageButton>();
	
	//swatchInfo should hold R.drawable Image Resource IDs mapped to colors
	private final SparseIntArray swatchInfo;
	private int style;
	private int selected;
	
	GridAdapter(Context context, SparseIntArray swatchInfo, int defaultSelected){
		this.context = context;
		this.swatchInfo = swatchInfo;
		this.selected = defaultSelected;
		style = swatchInfo.valueAt(selected);
	}
	
	private void select(int position) {
		
		//remove background highlight from previously selected button
		btnMap.get(selected).getBackground().clearColorFilter();
		
		//add (sky blue) highlight to newly selected button
		((ImageButton)getItem(position)).getBackground().setColorFilter(0xe087ceeb,PorterDuff.Mode.SRC_OUT);
		style = swatchInfo.valueAt(position);
		selected = position;
	}
	
	public int getStyle() {
		return style;
	}


    public int getCount() {
        return swatchInfo.size();
    }

    public Object getItem(int position) {
        return btnMap.get(position);
    }

    public long getItemId(int position) {
        return 0;
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        final ImageButton imageButton;
        
        if (convertView == null) { 
        	imageButton = new ImageButton(context);
        	imageButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					select(position);
				}
        		
        	});
        	imageButton.setLayoutParams(new GridView.LayoutParams(
        			GridView.LayoutParams.WRAP_CONTENT, GridView.LayoutParams.WRAP_CONTENT));
        	imageButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
        	imageButton.setPadding(8, 8, 8, 8);
        	
        	//the following two lines fix a weird bug that caused ImageButtons at position 0 to be
        	//populated twice, which made the first ImageButton in each GridView unable to be highlighted
        	if (btnMap.get(position) == null)
        		btnMap.put(position, imageButton);
        	
        	if (position == selected) 
        		select(selected);
        	
        	
            
        } else {
        	imageButton = (ImageButton) convertView;
        }
        
        imageButton.setImageResource(swatchInfo.keyAt(position));
        
        
        return imageButton;
    }
      
    
}
