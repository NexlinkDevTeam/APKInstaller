package com.nexlink.apkinstaller;

import java.io.File;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

final class InstallItem{
	boolean system = false;
	File apkFile = null; //Will be defined when resource is extracted
	String text = "";
	Drawable icon = new ColorDrawable(Color.TRANSPARENT);
	boolean pending = true;
	boolean installed = false;
}