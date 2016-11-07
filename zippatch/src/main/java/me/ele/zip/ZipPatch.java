package me.ele.zip;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import me.ele.patch.BsPatch;

public class ZipPatch {

    public static final void patchSync(File sourceFile, File diffFile, File dstFile) throws
            IOException {
        checkParams(sourceFile, diffFile, dstFile);
        File tmpDir = new File(dstFile.getParentFile(), "/bspatch/tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        } else {
            removeFile(tmpDir);
        }

        //release source.zip
        File sourceDir = new File(tmpDir, "source");
        releaseZip(sourceFile, sourceDir);

        //release diff.zip
        File diffDir = new File(tmpDir, "diff");
        releaseZip(diffFile, diffDir);

        //patch all files
        List<String> components = getComponents(new File(diffDir, "components"));
        File dstDir = new File(tmpDir, "dst");
        for (String component : components) {
            final File diffChild = new File(diffDir, component);
            final File oldChild = new File(sourceDir, component);
            final File dstChild = new File(dstDir, component);
            if (diffChild.exists() && oldChild.exists()) {
                BsPatch.workSync(oldChild.getCanonicalPath(), dstChild.getCanonicalPath(),
                        diffChild.getCanonicalPath());

            } else if (diffChild.exists()) {
                copyFile(diffChild, dstChild);
            } else {
                copyFile(oldChild, dstChild);
            }
        }

        //zip
        zipFolder(dstDir.getCanonicalPath(), dstFile.getCanonicalPath());

        //clear
        removeFile(tmpDir);
    }

    public static final void patchASync(final File sourceFile, final File diffFile, final File
            dstFile, final ZipPatchListener listener) {
        checkParams(sourceFile, diffFile, dstFile);
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    patchSync(sourceFile, diffFile, dstFile);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onSuccess(sourceFile, diffFile, dstFile);
                            }
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onFail(sourceFile, diffFile, dstFile, e);
                        }
                    });
                }
            }
        }.start();
    }

    private static final void checkParams(File sourceFile, File diffFile, File dstFile) {
        if (sourceFile == null
                || !sourceFile.exists() || !sourceFile.canRead()) {
            throw new IllegalArgumentException("sourceFile doesn't exist or can't be read");
        }

        if (diffFile == null
                || !diffFile.exists() || !diffFile.canRead()) {
            throw new IllegalArgumentException("diffFile doesn't exist or can't be read");
        }

        if (dstFile == null) {
            throw new IllegalArgumentException("dstFile cannot be null");
        }

        if (dstFile.exists()) {
            if (!dstFile.delete()) {
                throw new IllegalArgumentException("dstFile cannot be deleted, please make sure " +
                        "this file can be written");
            }
        }

        if (!dstFile.getParentFile().exists()) {
            if (dstFile.getParentFile().mkdirs()) {
                throw new ZipPatchException("dstFile's parent cannot be mkdirs, please check path");
            }
        }

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(sourceFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ZipPatchException("sourceFile isn't a valid zip file");
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            zipFile = new ZipFile(diffFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ZipPatchException("sourceFile isn't a valid zip file");
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void releaseZip(File zipFile, File outputDir) {
        byte[] buffer = new byte[8 * 1024];
        try {
            outputDir.mkdir();
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outputDir + File.separator + fileName);
                if (!newFile.getParentFile().exists() || !newFile.getParentFile().isDirectory()) {
                    removeFile(newFile.getParentFile());
                    newFile.getParentFile().mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(newFile);

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void removeFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isFile()) {
            file.delete();
            return;
        }

        if (file.isDirectory()) {

            File[] listFiles = file.listFiles();
            if (listFiles != null && listFiles.length > 0) {
                for (File f : listFiles) {
                    removeFile(f);
                }
            }

            file.delete();
        }
    }

    private static boolean copyFile(File sourceFile, File dstFile) {
        FileOutputStream outputStream = null;
        FileInputStream inputStream = null;
        try {
            if (!dstFile.exists()) {
                dstFile.getParentFile().mkdirs();
            }
            outputStream = new FileOutputStream(dstFile);
            inputStream = new FileInputStream(sourceFile);
            copyFile(inputStream, outputStream);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static void zipFolder(String srcFolder, String destZipFile) throws IOException {
        ZipOutputStream zip;
        FileOutputStream fileWriter;

        fileWriter = new FileOutputStream(destZipFile);
        zip = new ZipOutputStream(fileWriter);

        addFolderToZip("", srcFolder, zip);

        zip.flush();
        zip.close();
    }

    private static void addFileToZip(String path, String srcFile, ZipOutputStream zip)
            throws IOException {

        File folder = new File(srcFile);
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            FileInputStream in = new FileInputStream(srcFile);
            String name = (path + "/" + folder.getName()).substring("dst".length() + 1);
            zip.putNextEntry(new ZipEntry(name));
            while ((len = in.read(buf)) > 0) {
                zip.write(buf, 0, len);
            }
            in.close();
        }
    }

    private static void addFolderToZip(String path, String srcFolder, ZipOutputStream zip)
            throws IOException {
        File folder = new File(srcFolder);

        for (String fileName : folder.list()) {
            if (path.equals("")) {
                addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
            } else {
                addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
            }
        }
    }

    private static List<String> getComponents(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        List<String> components = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            components.add(line);
        }
        br.close();

        return components;
    }


    static class ZipPatchException extends RuntimeException {
        public ZipPatchException(String message) {
            super(message);
        }
    }


    public interface ZipPatchListener {

        void onSuccess(File sourceFile, File diffFile, File dstFile);

        void onFail(File sourceFile, File diffFile, File dstFile, Exception e);
    }

}
