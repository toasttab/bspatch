package me.ele.bspatch;

public class Patcher {

    static {
        System.loadLibrary("Patcher");
    }

    /**
     * 调用.so库中的方法,合并apk
     *
     * @param oldApkPath 旧Apk地址
     * @param newApkPath 新apk地址(名字)
     * @param patch      增量包地址
     * @return int        0为成功，-1为失败
     */
    private static native int patch(String oldApkPath, String newApkPath, String patch);

    public static boolean work(String oldApkPath, String newApkPath, String patch) {
        return patch(oldApkPath, newApkPath, patch) == 0;
    }
}
