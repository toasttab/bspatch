package me.ele.bspatch;

import java.io.File;

public class Patcher {

    static {
        System.loadLibrary("Patcher");
    }

    /**
     * 调用.so库中的方法,合并apk
     *
     * @param oldApkPath   旧Apk地址
     * @param newApkPath   新apk地址(名字)
     * @param patchApkPath 增量包地址
     * @return int        0为成功，-1为失败
     */
    private static native int patch(String oldApkPath, String newApkPath, String patchApkPath);

    public static boolean work(String oldApkPath, String newApkPath, String patchApkPath) {

        //check oldApkFile's existence and readability
        File oldApkFile = new File(oldApkPath);
        if (!oldApkFile.exists()) {
            throw new IllegalArgumentException(String.format("oldApkPath %s don't exist, please check", oldApkPath));
        }
        if (!oldApkFile.canRead()) {
            throw new IllegalArgumentException(String.format("oldApkPath %s cannot be read, please check", oldApkPath));
        }
        if (!oldApkPath.endsWith(".apk") || !oldApkFile.isFile()) {
            throw new IllegalArgumentException(String.format("make sure oldApkPath %s is a valid apk", oldApkPath));
        }

        //check patchFile's existence and readability
        File patchFile = new File(patchApkPath);
        if (!patchFile.exists()) {
            throw new IllegalArgumentException(String.format("patch %s don't exist, please check", patchApkPath));
        }
        if (!patchFile.canRead()) {
            throw new IllegalArgumentException(String.format("patchApkPath %s cannot be read, please check", patchApkPath));
        }
        if (!patchFile.isFile()) {
            throw new IllegalArgumentException(String.format("make sure patchApkPath %s is a valid file", patchApkPath));
        }

        //check newApkFile is an apk or not
        File newApkFile = new File(newApkPath);
        if (!newApkPath.endsWith(".apk") || (newApkFile.exists() && !newApkFile.isFile())) {
            throw new IllegalArgumentException(String.format("make sure newApkPath %s is a valid apk", newApkPath));
        }

        //delete newApkFile if exists
        if (newApkFile.exists()) {
            boolean success = newApkFile.delete();
            if (!success) {
                throw new IllegalArgumentException(String.format("newApkPath %s exists and cannot be deleted. please check", newApkPath));
            }
        }

        //if directory exists, mkdirs
        if (!newApkFile.getParentFile().exists()) {
            boolean success = newApkFile.getParentFile().mkdirs();
            if (!success) {
                throw new IllegalArgumentException(String.format("newApkPath cannot execute mkdirs. please check", newApkPath));
            }
        }


        return patch(oldApkPath, newApkPath, patchApkPath) == 0;
    }
}
