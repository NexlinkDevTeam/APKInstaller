package com.nexlink.apkinstaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.widget.ListView;

public class MainActivity extends Activity {
    
    private static final ArrayList<InstallItem> installs = new ArrayList<InstallItem>();
    public static final boolean haveRoot = Shell.su();
    
    
    private static ListAdapter listAdapter;
    private static ListView listView;
    private static ProgressDialog mProgress;
    private static AlertDialog mAlert;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        listView = (ListView) findViewById(R.id.list);
        mProgress = new ProgressDialog(this);
        mAlert = new AlertDialog.Builder(this).show();
        mAlert.dismiss();
        
        if(listAdapter == null){
            mProgress = ProgressDialog.show(this, "", "Extracting temp files...", false);
            new AsyncTask<Void,Void,Void>(){
                @Override
                protected void onPreExecute(){
                    lockOrientation(true);
                }
                @Override
                protected Void doInBackground(Void... params) {
                    PackageManager packageManager = getPackageManager();
                    InputStream fin = null;
                    FileOutputStream fout = null;
                    try {
                        byte[] buffer = new byte[64*1024];
                        ZipInputStream zis = new ZipInputStream(MainActivity.this.getResources().openRawResource(R.raw.apks));
                        ZipEntry ze;
                        while((ze = zis.getNextEntry()) != null){
                            if(ze.isDirectory()){
                                continue;
                            }
                            InstallItem inst = new InstallItem();
                            String[] fileName = ze.getName().split("/");
                            inst.system = fileName[0].equals("system") ? true : false;
                            fout = openFileOutput(fileName[1], MODE_WORLD_READABLE);
                            int chunk = 0;
                            while ((chunk = zis.read(buffer)) != -1) {
                                fout.write(buffer, 0, chunk);
                            }
                            inst.apkFile = getFileStreamPath(fileName[1]);
                            inst.apkFile.setReadable(true, false);
                            inst.apkFile.setWritable(true, false);
                            inst.apkFile.setExecutable(true, false);
                            String path = inst.apkFile.getAbsolutePath();
                            PackageInfo packageInfo = packageManager.getPackageArchiveInfo(path, 0);
                            if(packageInfo != null){
                                ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                                applicationInfo.sourceDir = path;
                                applicationInfo.publicSourceDir = path;
                                inst.text = (String) applicationInfo.loadLabel(packageManager) + " " + packageInfo.versionName;
                                inst.icon = applicationInfo.loadIcon(packageManager);
                            }
                            installs.add(inst);
                        }
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                    
                    finally{
                        if(fout != null){
                            try {
                                fout.close();
                            } catch (IOException e) {}
                        }
                        if(fin != null){
                            try {
                                fin.close();
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
                    listAdapter = new ListAdapter(MainActivity.this, R.layout.list_item);
                    listAdapter.addAll(installs);
                    listView.setAdapter(listAdapter);
                    mProgress.dismiss();
                    lockOrientation(false);
                    startInstallProcess();
                }
            }.execute();
        }
        else{
            listView.setAdapter(listAdapter);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        listAdapter.notifyDataSetChanged();
        mAlert = new AlertDialog.Builder(this)
        .setMessage("Click OK to continue.")
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setCancelable(false)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                InstallItem installItem = null;
                for(InstallItem ii : installs){
                    if(ii.pending){
                        installItem = ii;
                        break;
                    }
                }
                if(installItem != null){
                    PackageInfo packageInfo = getPackageManager().getPackageArchiveInfo(Uri.fromFile(installItem.apkFile).getPath(), 0);
                    if(packageInfo != null){
                        packageInfo.applicationInfo.sourceDir = installItem.apkFile.getAbsolutePath();
                        packageInfo.applicationInfo.publicSourceDir = installItem.apkFile.getAbsolutePath();
                        List<PackageInfo> pkglist = getPackageManager().getInstalledPackages(0);
                        for(PackageInfo pi : pkglist){
                            if(packageInfo.packageName.compareTo(pi.packageName) == 0 && packageInfo.versionCode == pi.versionCode){
                                installItem.installed = true;
                                break;
                            }
                        }
                    }
                    installItem.pending = false;
                    installItem.apkFile.delete();
                    listAdapter.notifyDataSetChanged();
                    doNextInstall();
                }}})
                .setNegativeButton(android.R.string.cancel, null).show();
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
                .setMessage(msg + " Click OK to continue.")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mProgress = ProgressDialog.show(MainActivity.this, "", "Please wait...");
                        doNextInstall();
                    }
                }).show();
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
                    System.out.println(installItem.installed);
                    Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(installItem.apkFile), "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivityForResult(intent, 0);
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
                                PackageInfo packageInfo = getPackageManager().getPackageArchiveInfo(Uri.fromFile(installItem.apkFile).getPath(), 0);
                                packageInfo.applicationInfo.sourceDir = installItem.apkFile.getAbsolutePath();
                                packageInfo.applicationInfo.publicSourceDir = installItem.apkFile.getAbsolutePath();
                                if(!installItem.system){
                                    Shell.sudo("pm install -r -d -t " + installItem.apkFile.getAbsolutePath());
                                    List<PackageInfo> pkglist = getPackageManager().getInstalledPackages(0);
                                    for(PackageInfo pi : pkglist){
                                        if(packageInfo.packageName.compareTo(pi.packageName) == 0 && packageInfo.versionCode == pi.versionCode){
                                            installItem.installed = true;
                                        }
                                    }
                                }
                                else{
                                    String copypath = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ? "/system/app/" : "/system/priv-app/") + packageInfo.packageName + ".apk";
                                    Shell.sudo(
                                    "mount -o rw,remount /system"
                                    + ";cp " + installItem.apkFile.getAbsolutePath() + " " + copypath
                                    + ";chmod 644 " + copypath
                                    + ";chown 0.0 " + copypath
                                    + ";mount -o ro,remount /system"
                                    + ";sync"
                                    );
                                    File copied = new File(copypath);
                                    installItem.installed = copied.exists() && copied.isFile();
                                }
                            }
                            catch(Exception e){installItem.installed = false;}
                            finally{installItem.apkFile.delete();}
                            return null;
                        }
                        @Override
                        protected void onPostExecute(Void v){
                            installItem.pending = false;
                            listAdapter.notifyDataSetChanged();
                            doNextInstall();
                            lockOrientation(false);
                        }
                    }.execute();
                }
            }
            
            private void finishInstallProcess(){
                String msg = "All packages were successfully installed.";
                for(InstallItem installItem : installs){
                    if(!installItem.installed){
                        msg = "Some packages were not installed.";
                        break;
                    }
                }
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage(msg + " Click OK to finish.")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", getPackageName(), null));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        System.gc();
                        System.exit(0);
                    }
                }).show();
            }
        }
        