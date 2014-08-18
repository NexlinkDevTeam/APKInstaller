package com.nexlink.apkinstaller;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ListAdapter extends ArrayAdapter < InstallItem > {
	private Context mContext;
	private LayoutInflater mLayoutInflater;
	private int mRowLayout;
	
	public ListAdapter(Context context, int resource) {
		super(context, resource);
		mContext = context;
		mLayoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mRowLayout = resource;
	}
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if(convertView == null){
		    convertView = mLayoutInflater.inflate(mRowLayout, parent, false);
		}
		
		ImageView iconImageView = (ImageView) convertView.findViewById(R.id.icon);
		TextView argsTextView = (TextView) convertView.findViewById(R.id.name);
		
		InstallItem installItem = getItem(position);

		iconImageView.setImageDrawable(installItem.icon);
		argsTextView.setText(installItem.text);

		if(!installItem.pending){
			argsTextView.setTextColor(installItem.installed ? Color.GREEN : Color.RED);
		}
		return convertView;
	}
}