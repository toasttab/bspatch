package me.ele.bspatch.demo;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import me.ele.bspatch.Patcher;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //files all are in tools/
        final String oldApkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/old.apk";
        final String newApkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/new.apk";
        final String patch = Environment.getExternalStorageDirectory().getAbsolutePath() + "/diff.apk";

        new Thread() {

            @Override
            public void run() {
                super.run();
                Log.e(TAG, "start patch");
                final boolean result = Patcher.work(oldApkPath, newApkPath, patch);
                Log.e(TAG, "end patch-->" + result);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result) {
                            Toast.makeText(MainActivity.this, "patch success-->" + newApkPath, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "sadly, patch fails", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }.start();
    }
}
