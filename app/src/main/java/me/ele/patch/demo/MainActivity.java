package me.ele.patch.demo;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;

import me.ele.patch.BsPatch;
import me.ele.zip.ZipPatch;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BsPatch.init(this);
        findViewById(R.id.bspatch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bspatch();
            }
        });

        findViewById(R.id.zip_bspatch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zipPatch();
            }
        });

    }

    /**
     * use this with zippatch_server
     */
    private void zipPatch() {
        File oldApk = new File(Environment.getExternalStorageDirectory(), "old.apk");
        File diffApk = new File(Environment.getExternalStorageDirectory(), "diff2.apk");
        File newApk = new File(Environment.getExternalStorageDirectory(), "new2.apk");
        ZipPatch.patchASync(oldApk, diffApk, newApk, new ZipPatch.ZipPatchListener() {
            @Override
            public void onSuccess(File sourceFile, File diffFile, File dstFile) {
                try {
                    Bundle data = getPackageManager().getPackageArchiveInfo(dstFile
                            .getCanonicalPath(),
                            PackageManager.GET_META_DATA).applicationInfo
                            .metaData;
                    Log.e(TAG, "data-->" + data.getString("io.fabric.ApiKey"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Toast.makeText(MainActivity.this, "patch success-->" + dstFile, Toast
                        .LENGTH_SHORT).show();
            }

            @Override
            public void onFail(File sourceFile, File diffFile, File dstFile, Exception e) {
                Toast.makeText(MainActivity.this, "patch fails-->" + e.getMessage(),
                        Toast
                                .LENGTH_SHORT).show();
            }
        });
    }

    /**
     * use this with /tools/**
     * cd tools -> bsdiff old.apk new.apk diff.apk -> adb push diff.apk /sdcard/diff.apk
     */
    private void bspatch() {
        //files all are in tools/
        final String oldApk = Environment.getExternalStorageDirectory().getAbsolutePath() + "/old" +
                ".apk";
        final String newApk = Environment.getExternalStorageDirectory().getAbsolutePath() + "/new" +
                ".apk";
        final String patch = Environment.getExternalStorageDirectory().getAbsolutePath() + "/diff" +
                ".apk";

        BsPatch.workAsync(oldApk, newApk, patch, new BsPatch.BsPatchListener() {
            @Override
            public void onSuccess(String oldPath, String newPath, String patchPath) {
                Toast.makeText(MainActivity.this, "patch success-->" + newApk, Toast
                        .LENGTH_SHORT).show();
            }

            @Override
            public void onFail(String oldPath, String newPath, String patchPath, Exception e) {
                Toast.makeText(MainActivity.this, "sadly, patch fails", Toast
                        .LENGTH_SHORT).show();
            }
        });
    }
}
