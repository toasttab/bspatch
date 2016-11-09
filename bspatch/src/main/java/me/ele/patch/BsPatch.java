package me.ele.patch;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.lang.reflect.Method;

public class BsPatch {

    static {
        try {
            System.loadLibrary("Patcher");
        } catch (Throwable e) {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method method = activityThreadClass.getMethod("currentApplication");
                Application context = (Application) method.invoke(null, (Object[]) null);
                String libraryPath = context.getFilesDir().getParentFile().getPath() + "/lib";
                System.load(libraryPath + "/libPatcher.so");
            } catch (Exception e1) {
                try {
                    Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                    Method method = activityThreadClass.getDeclaredMethod("currentActivityThread");
                    Object activityThread = method.invoke(null);
                    Method getApplicationMethod = activityThreadClass.getDeclaredMethod("getApplication");
                    Application context = (Application) getApplicationMethod.invoke(activityThread);
                    String libraryPath = context.getFilesDir().getParentFile().getPath() + "/lib";
                    System.load(libraryPath + "/libPatcher.so");
                } catch (Exception e2) {
                    throw new RuntimeException(e2);
                }
            }
        }
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
