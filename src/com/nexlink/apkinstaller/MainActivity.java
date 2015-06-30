package com.nexlink.apkinstaller;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import com.nexlink.utilites.InstallUtils;
import com.nexlink.utilites.Shell;

public class MainActivity extends Activity {
	public static final boolean haveRoot = Shell.su();
	
    private static final ArrayList<InstallItem> installs = new ArrayList<InstallItem>();
    private static InstallUtils mInstaller;
    
    private static ListAdapter mListAdapter;
    private static ListView mListView;
    private static ProgressDialog mProgress;
    private static AlertDialog mAlert;
	private Button mButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mInstaller = new InstallUtils(this);
        
        mListView = (ListView) findViewById(R.id.list);
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				mButton.setEnabled(false);
				doNextInstall();
			}
        });
        mProgress = new ProgressDialog(this);
        mAlert = new AlertDialog.Builder(this).show();
        mAlert.dismiss();
        
        if(mListAdapter == null){
            mProgress = ProgressDialog.show(this, "", "Extracting temp files...", false);
            new AsyncTask<Void,Void,Void>(){
                @Override
                protected void onPreExecute(){
                    lockOrientation(true);
                }
                @SuppressLint("WorldReadableFiles")
				@Override
                protected Void doInBackground(Void... params) {
                    PackageManager packageManager = getPackageManager();
                    ZipInputStream zis = null;
                    FileOutputStream fos = null;
                    try {
                        byte[] buffer = new byte[64*1024];
                        zis = new ZipInputStream(MainActivity.this.getResources().openRawResource(R.raw.apks));
                        ZipEntry ze;
                        while((ze = zis.getNextEntry()) != null){
                            if(ze.isDirectory()){
                                continue;
                            }
                            InstallItem inst = new InstallItem();
                            String[] fileName = ze.getName().split("/");
                            inst.system = fileName[0].equals("system") ? true : false;
                            fos = openFileOutput(fileName[1], MODE_WORLD_READABLE);
                            int chunk = 0;
                            while ((chunk = zis.read(buffer)) != -1) {
                                fos.write(buffer, 0, chunk);
                            }
                            inst.apkFile = getFileStreamPath(fileName[1]);
                            inst.apkFile.setReadable(true, false);
                            inst.apkFile.setWritable(true, false);
                            inst.apkFile.setExecutable(true, false);
                            PackageInfo packageInfo = mInstaller.getPackageInfoFromFile(inst.apkFile);
                            if(packageInfo != null){
                                inst.text = (String) packageInfo.applicationInfo.loadLabel(packageManager) + " " + (packageInfo.versionName != null ? packageInfo.versionName : "");
                                inst.icon = packageInfo.applicationInfo.loadIcon(packageManager);
                            }
                            installs.add(inst);
                        }
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                    
                    finally{
                        if(fos != null){
                            try {
                                fos.close();
                            } catch (IOException e) {}
                        }
                        if(zis != null){
                            try {
                                zis.close();
                            } catch (IOException e) {}
                        }
                        
                        Collections.sort(installs, new Comparator<InstallItem>(){
                            @Override
                            public int compare(InstallItem arg0, InstallItem arg1) {
                                return arg0.apkFile.getName().compareTo(arg1.apkFile.getName());
                            }
                        });
                    }
                    
                    return null;
                }
                @Override
                protected void onPostExecute(Void v){
                    mListAdapter = new ListAdapter(MainActivity.this, R.layout.list_item);
                    mListAdapter.addAll(installs);
                    mListView.setAdapter(mListAdapter);
                    mProgress.dismiss();
                    lockOrientation(false);
                    startInstallProcess();
                }
            }.execute();
        }
        else{
            mListView.setAdapter(mListAdapter);
        }
    }
            
            private void lockOrientation(boolean lock){
                if(lock){
                    int currentOrientation = getResources().getConfiguration().orientation;
                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    }
                    else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                    }
                }
                else{
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
            
            private void startInstallProcess(){
                String msg = haveRoot ? "Device is rooted; packages can be installed automatically." : "Device is not rooted; packages will need to be confirmed manually.";
                mAlert = new AlertDialog.Builder(MainActivity.this)
                .setMessage(msg + " Click Start to begin installation.")
                .setCancelable(false)
                .setPositiveButton("OK", null).show();
            }
            
            private void doNextInstall(){
                InstallItem temp = null;
                for(InstallItem ii : installs){
                    if(ii.pending){
                        temp = ii;
                        break;
                    }
                }
                if(temp == null){
                    mProgress.dismiss();
                    finishInstallProcess();
                    return;
                }
                
                final InstallItem installItem = temp;
                if(!haveRoot){
                    mInstaller.installIntent(installItem.apkFile);
                    mAlert = new AlertDialog.Builder(this)
                    .setMessage("Click OK to continue.")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            InstallItem installItem = null;
                            for(InstallItem temp : installs){
                                if(temp.pending){
                                    installItem = temp;
                                    break;
                                }
                            }
                            if(installItem != null){
                                installItem.installed = mInstaller.isInstalled(installItem.apkFile);
                                installItem.pending = false;
                                installItem.apkFile.delete();
                                mListAdapter.notifyDataSetChanged();
                                doNextInstall();
                            }}}).show();
                }
                else{
                    mProgress.setMessage("Installing "+installItem.text+" ("+(installs.indexOf(installItem)+1)+"/"+installs.size()+")");
                    mProgress.show();
                    new AsyncTask<Void,Void,Void>(){
                        @Override
                        protected void onPreExecute(){
                            lockOrientation(true);
                        }
                        @Override
                        protected Void doInBackground(Void... params) {
                            try{
                                installItem.installed = mInstaller.installRoot(installItem.apkFile, installItem.system, false);
                            }
                            catch(Exception e){
                            	Log.e("ROOT INSTALL FAILED", e.getMessage());
                                installItem.installed = false;
                            }
                            finally{installItem.apkFile.delete();}
                            return null;
                        }
                        @Override
                        protected void onPostExecute(Void v){
                            installItem.pending = false;
                            mListAdapter.notifyDataSetChanged();
                            doNextInstall();
                            lockOrientation(false);
                        }
                    }.execute();
                }
            }
            
            private void finishInstallProcess(){
                boolean success = true;
                for(InstallItem installItem : installs){
                    if(!installItem.installed){
                        success = false;;
                        break;
                    }
                }
                
                //Apply default policy
                InputStream is = getResources().openRawResource(R.raw.policy);
                Writer writer = new StringWriter();
                char[] buffer = new char[1024];
                try {
                    Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    int n;
                    while ((n = reader.read(buffer)) != -1) {
                        writer.write(buffer, 0, n);
                    }
                    is.close();
                    GlobalPrefs.setGlobalPrefs(this, new JSONObject(writer.toString()));
                } catch(Exception e){}
                
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage(success ? "All packages were successfully installed." : "Some packages were not installed.")
                .setPositiveButton("Finish", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", getPackageName(), null));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        System.gc();
                        System.exit(0);
                    }
                });
                
                if(!success){
                	alertDialogBuilder.setNegativeButton("Retry", new DialogInterface.OnClickListener(){
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Context context = MainActivity.this;
							Intent mStartActivity = new Intent(context, MainActivity.class);
							PendingIntent mPendingIntent = PendingIntent.getActivity(context, 0, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
							AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
							mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
							System.exit(0);
						}		
                	});
                }
                alertDialogBuilder.show();
            }
        }
        