package me.ele.patch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.getkeepsafe.relinker.ReLinker;
import java.io.File;


public class BsPatch {

    private static final String TAG = BsPatch.class.getSimpleName();

    private static boolean isInitialized = false;
    private static boolean isFailure = false;

    public static void init(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("param context cannot be null");
        }

      if (isInitialized) {
        Log.d(TAG, "initialization shall not be done twice");
        return;
      }

        ReLinker.loadLibrary(context.getApplicationContext(), "Patcher",
            new ReLinker.LoadListener() {
                @Override public void success() {

                }

                @Override public void failure(Throwable t) {
                    isFailure = true;
                }
            });
      isInitialized = true;
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 调用.so库中的方法,合并apk
     *
     * @param oldPath   旧Apk地址
     * @param newPath   新apk地址(名字)
     * @param patchPath 增量包地址
     * @return int        0为成功，-1为失败
     */
    private static native int patch(String oldPath, String newPath, String patchPath);

    public static boolean workSync(String oldPath, String newPath, String patchPath) {

      if (!isInitialized) {
        throw new RuntimeException("call BsPatch.init(context) first");
      }

      if (isFailure) {
        Log.e(TAG, "loading libPatcher.so fails, so no further more");
        return false;
      }

        //check oldApkFile's existence and readability
        File oldApkFile = new File(oldPath);
        if (!oldApkFile.exists()) {
            throw new IllegalArgumentException(String.format("oldPath %s don't exist, please " +
                    "check", oldPath));
        }
        if (!oldApkFile.canRead()) {
            throw new IllegalArgumentException(String.format("oldPath %s cannot be read, please " +
                    "check", oldPath));
        }

        //check patchFile's existence and readability
        File patchFile = new File(patchPath);
        if (!patchFile.exists()) {
            throw new IllegalArgumentException(String.format("patch %s don't exist, please " +
                    "check", patchPath));
        }
        if (!patchFile.canRead()) {
            throw new IllegalArgumentException(String.format("patchPath %s cannot be read, please" +
                    " check", patchPath));
        }
        if (!patchFile.isFile()) {
            throw new IllegalArgumentException(String.format("make sure patchPath %s is a valid " +
                    "file", patchPath));
        }

        File newApkFile = new File(newPath);

        //delete newApkFile if exists
        if (newApkFile.exists()) {
            boolean success = newApkFile.delete();
            if (!success) {
                throw new IllegalArgumentException(String.format("newPath %s exists and cannot be" +
                        " deleted. please check", newPath));
            }
        }

        //if directory exists, mkdirs
        if (!newApkFile.getParentFile().exists()) {
            boolean success = newApkFile.getParentFile().mkdirs();
            if (!success) {
                throw new IllegalArgumentException(String.format("newPath cannot execute mkdirs. " +
                        "please check", newPath));
            }
        }


        return patch(oldPath, newPath, patchPath) == 0;
    }

    public static void workAsync(final String oldPath, final String newPath, final String patchPath,
                                 final BsPatchListener listener) {
      if (!isInitialized) {
        throw new RuntimeException("call BsPatch.init(context) first");
      }

      if (isFailure) {
        Log.e(TAG, "loading libPatcher.so fails, so no further more");
        return;
      }

        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    boolean success = workSync(oldPath, newPath, patchPath);
                    if (success) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onSuccess(oldPath, newPath, patchPath);
                                }
                            }
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onFail(oldPath, newPath, patchPath, null);
                                }
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onFail(oldPath, newPath, patchPath, e);
                            }
                        }
                    });
                }
            }
        }.start();

    }

    public interface BsPatchListener {

        void onSuccess(String oldPath, String newPath, String patchPath);

        void onFail(String oldPath, String newPath, String patchPath, Exception e);
    }

}
