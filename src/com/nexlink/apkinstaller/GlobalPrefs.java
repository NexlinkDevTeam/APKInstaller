package com.nexlink.apkinstaller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.ResolveInfo;
import android.preference.PreferenceManager;

/*
 * Global prefs format
 * {"com.nexlink.somepackage":
 *     {
 *     "astring":"hello",
 *     "abooolean":true,
 *     "anumber":1
 *     },
 *  "com.nexlink.someotherpackage":
 *     {
 *     ...
 *     },
 *  ...
 *  }
 */

public class GlobalPrefs {
	public static JSONObject getGlobalPrefs(Context context){
		JSONObject globalPrefs = new JSONObject();
		List<ResolveInfo> packageInfoList = context.getPackageManager().queryIntentActivities(new Intent("com.nexlink.action.LAUNCH_MDM_SETTINGS").addCategory("com.nexlink.category.MDM_SETTINGS"), 0);
		for(ResolveInfo resolveInfo : packageInfoList) {
			String packageName = resolveInfo.activityInfo.packageName;
			try {
				Context appContext = context.createPackageContext(packageName, Context.MODE_WORLD_READABLE);
				JSONObject appPrefs = globalPrefs.put(packageName, new JSONObject()).getJSONObject(packageName);
				Map<String,?> savedPrefs = PreferenceManager.getDefaultSharedPreferences(appContext).getAll();
				Set<String> prefKeys = savedPrefs.keySet();
				for(String prefKey : prefKeys){
					appPrefs.put(prefKey, savedPrefs.get(prefKey));
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return globalPrefs;
	}

	public static void setGlobalPrefs(Context context, JSONObject globalPrefs){
		Iterator<?> packageNames = globalPrefs.keys();
		Context appContext = null;
		Editor editor = null;
		while(packageNames.hasNext()){
			String packageName = (String) packageNames.next();
			try {
				appContext = context.createPackageContext(packageName, Context.MODE_WORLD_WRITEABLE);
				editor = PreferenceManager.getDefaultSharedPreferences(appContext).edit();		
				JSONObject appPrefs = globalPrefs.getJSONObject(packageName);
				Iterator<?> prefKeys = appPrefs.keys();
				while(prefKeys.hasNext()){
					String prefKey = (String) prefKeys.next();
					try{
					    Object value = appPrefs.get(prefKey);
					    if(value instanceof Boolean){
					        editor.putBoolean(prefKey, (Boolean) value);
					    }
					    else if(value instanceof Integer){
					    	editor.putInt(prefKey, (Integer) value);
					    }
					    else if(value instanceof String){
					    	editor.putString(prefKey, (String) value);
					    }
					    else if(value instanceof JSONArray){
						    JSONArray arr = (JSONArray) value;
						    Set<String> set = new HashSet<String>();
						    for(int i = 0; i < arr.length(); i++){
						        set.add(arr.getString(i));
						    }
						    editor.putStringSet(prefKey, set);
					    }
					    else if(value instanceof Float){
					    	editor.putFloat(prefKey, (Float) value);
					    }
					    else if(value instanceof Long){
					    	editor.putLong(prefKey, (Long) value);
					    }
					}
					catch(JSONException e){
						e.printStackTrace();
					}
				}
			}
			catch (Exception e){
				e.printStackTrace();
				}
			finally{
				if(editor != null){
					editor.apply();
				}
			}
		}
		//Let the apps know their prefs changed
		Intent i = new Intent("com.nexlink.GLOBAL_PREFS_CHANGED");
		context.sendBroadcast(i);
	}
}
