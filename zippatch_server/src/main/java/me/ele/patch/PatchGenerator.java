
package me.ele.patch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * This class is for generating patch file in server client
 */
public class PatchGenerator {

    public static void main(String[] args) throws IOException, InterruptedException {
        String folder = System.getProperty("user.dir") + "/tools";
        File oldApk = new File(folder, "old.apk");
        File newApk = new File(folder, "new.apk");
        File diffApk = new File(folder, "diff.apk");

        File temp = new File(folder, "tmp");
        temp.mkdir();
        File newDir = new File(temp, "newDir");
        File oldDir = new File(temp, "oldDir");
        File diffDir = new File(temp, "diffDir");
        File components = new File(diffDir, "components");

        //init
        removeFile(oldDir);
        removeFile(newDir);
        removeFile(diffDir);
        removeFile(diffApk);
        diffDir.mkdir();
        releaseZip(oldApk, oldDir);
        releaseZip(newApk, newDir);

        //diff
        diff(oldDir, newDir, diffDir, components);

        //zip
        zipFolder(diffDir.getCanonicalPath(), diffApk.getCanonicalPath());

        //clear
        removeFile(temp);
    }

    private static void diff(File sourceDir, File newDir, File diffDir, File components) throws
            IOException, InterruptedException {
        //patch all files
        List<File> newChildren = getAllFiles(newDir);

        StringBuffer sb = new StringBuffer();
        for (File newChild : newChildren) {
            String relativePath = getRelativePath(newChild, newDir);
            File sourceChild = new File(sourceDir, relativePath);
            File diffChild = new File(diffDir, relativePath);
            if (!sourceChild.exists()) {
                copyFile(newChild, diffChild);
                System.out.println("copied--->" + relativePath);
            } else {
                String command = String.format("bsdiff %s %s %s", sourceChild.getCanonicalPath(),
                        newChild.getCanonicalPath(), diffChild.getCanonicalPath());
                executeCommand(command);
                System.out.println("diffed--->" + relativePath);
            }
            sb.append(relativePath);
            sb.append("\n");
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(components));
        writer.write(sb.toString());
        writer.close();
    }

    private static void releaseZip(File zipFile, File outputDir) throws IOException {

        byte[] buffer = new byte[8 * 1024];
        mkdirChecked(outputDir);

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

    }

    private static void mkdirChecked(File dir) throws IOException {
        dir.mkdir();
        if (!dir.isDirectory()) {
            throw new IOException("Failed to create directory " + dir.getPath());
        }
    }

    private static List<File> getAllFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.canRead()) {
            return null;
        }

        List<File> result = new ArrayList<>();
        if (dir.isFile()) {
            result.add(dir);
            return result;
        }

        if (dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (file.isFile()) {
                    result.add(file);
                } else if (file.isDirectory()) {
                    result.addAll(getAllFiles(file));
                }
            }

            return result;
        }

        return null;
    }

    private static String getRelativePath(File file, File dir) throws IOException {
        String filePath = file.getCanonicalPath();
        String dirPath = dir.getCanonicalPath();
        return filePath.substring(filePath.indexOf(dirPath) + dirPath.length() + 1);
    }

    private static boolean copyFile(File sourceFile, File dstFile) throws IOException {
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
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }

            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static String executeCommand(String command) throws InterruptedException, IOException {

        StringBuffer output = new StringBuffer();

        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(p.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line + "\n");
        }


        return output.toString();

    }


    private static void zipFolder(String srcFolder, String destZipFile) throws IOException {
        FileOutputStream fileWriter = new FileOutputStream(destZipFile);
        ZipOutputStream zip = new ZipOutputStream(fileWriter);
        zip.setLevel(1);
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
            String name = (path + "/" + folder.getName()).substring("diffDir".length());
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

        if (folder.list() == null) {
            return;
        }
        for (String fileName : folder.list()) {
            if (path.equals("")) {
                addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
            } else {
                addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
            }
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


}
