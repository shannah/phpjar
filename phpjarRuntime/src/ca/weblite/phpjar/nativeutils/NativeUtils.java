package ca.weblite.phpjar.nativeutils;
 
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
 
/**
 * Simple library class for working with JNI (Java Native Interface)
 * 
 * @see http://frommyplayground.com/how-to-load-native-jni-library-from-jar
 *
 * @author Adam Heirnich <adam@adamh.cz>, http://www.adamh.cz
 */
public class NativeUtils {
 
    /**
     * Private constructor - this class will never be instanced
     */
    private NativeUtils() {
    }
 
    public static void loadLibraryFromJar(String path) throws IOException {
        loadLibraryFromJar(path, NativeUtils.class);
    }
    
    
    
    /**
     * Loads library from current JAR archive
     * 
     * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after exiting.
     * Method uses String as filename because the pathname is "abstract", not system-dependent.
     * 
     * @param filename The filename inside JAR as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws IOException If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than three characters (restriction of {@see File#createTempFile(java.lang.String, java.lang.String)}).
     */
    public static void loadLibraryFromJar(String path, Class source) throws IOException {
 
        
        // Finally, load the library
        System.load(loadFileFromJar(path, source).getAbsolutePath());
    }
    
    public static void extractZipTo(File src, File dest) throws IOException {
        try {
            File destF = dest;
            //System.out.println("Extracting "+src.unwrap()+" to "+dest.unwrap());
            ZipFile zf = new ZipFile(src);
            
            zf.extractAll(destF.getAbsolutePath());
            int countFiles = 0;
            File lastFile = null;
            for (File f : destF.listFiles()) {
                countFiles++;
                lastFile = f;
            }
            if (countFiles == 1) {
                // This lib always places zip contents in an extra subdirectory
                // In most cases I prefer that the destination file is the location
                // of the extracted directory... but in case there is more than one
                // file, we only do this in the case of a single file.
                int nameSuffix = 0;
                String tmpName = destF.getName() + nameSuffix;
                File tmpFile = new File(destF.getParentFile(), tmpName);
                while (tmpFile.exists()) {
                    nameSuffix++;
                    tmpName = destF.getName()+nameSuffix;
                    tmpFile = new File(destF.getParentFile(), tmpName);
                }
                if (!lastFile.renameTo(tmpFile)) {
                    throw new IOException("Failed to make temp file");
                }
                if (!delTree(destF)) {
                    throw new IOException("Failed to delete temp directory");
                }
                if (!tmpFile.renameTo(destF)) {
                    throw new IOException("Failed to move extracted zip to desired location");
                }
                
            }
            
        } catch (ZipException ex) {
            
            throw new IOException(ex);
        }
        
    }
    
    public static boolean delTree(File f) throws IOException {
        
        if (f.isDirectory()) {
            for (File child : f.listFiles()) {
                if (!delTree(child)) {
                    return false;
                }
            }
        }
        if (f.exists()) {
            if (!f.delete()) {
                return false;
            }
        }
        return true;
    }
    
    public static void downloadToFile(String url, File file) throws IOException {
        URL u = new URL(url);
        URLConnection conn = u.openConnection();
        if (conn instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection)conn;
            http.setInstanceFollowRedirects(true);
            
            int responseCode = http.getResponseCode();
            /*
            String etag = http.getHeaderField("ETag");
            if (etag != null) {
                ETags.add(url, etag);
            }*/
        }
        
        File f = file;
        try (InputStream input = conn.getInputStream()) {
            try (FileOutputStream output = new FileOutputStream(f)) {
                byte[] buf = new byte[128 * 1024];
                int len;
                while ((len = input.read(buf)) >= 0) {
                    output.write(buf, 0, len);
                }
            }
        }
    }
    
    
    public static File loadFileFromURL(String url) throws IOException {
        File out = File.createTempFile("tempdownload", ".zip");
        out.deleteOnExit();
        downloadToFile(url, out);
        return out;
        
    }
    
    public static File loadFileFromJar(String path, Class source) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("The path has to be absolute (start with '/').");
        }
 
        // Obtain filename from path
        String[] parts = path.split("/");
        String filename = (parts.length > 1) ? parts[parts.length - 1] : null;
 
        // Split filename to prexif and suffix (extension)
        String prefix = "";
        String suffix = null;
        if (filename != null) {
            parts = filename.split("\\.", 2);
            prefix = parts[0];
            suffix = (parts.length > 1) ? "."+parts[parts.length - 1] : null; // Thanks, davs! :-)
        }
 
        // Check if the filename is okay
        if (filename == null || prefix.length() < 3) {
            throw new IllegalArgumentException("The filename has to be at least 3 characters long.");
        }
 
        // Prepare temporary file
        File temp = File.createTempFile(prefix, suffix);
        temp.deleteOnExit();
 
        if (!temp.exists()) {
            throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
        }
 
        // Prepare buffer for data copying
        byte[] buffer = new byte[1024];
        int readBytes;
 
        // Open and check input stream
        InputStream is = source.getResourceAsStream(path);
        if (is == null) {
            throw new FileNotFoundException("File " + path + " was not found inside JAR.");
        }
 
        // Open output stream and copy data between source file in JAR and the temporary file
        OutputStream os = new FileOutputStream(temp);
        try {
            while ((readBytes = is.read(buffer)) != -1) {
                os.write(buffer, 0, readBytes);
            }
        } finally {
            // If read/write fails, close streams safely before throwing an exception
            os.close();
            is.close();
        }
        return temp;
 
    }
    
    
    public static String readFileToString(File file, String encoding) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    public static void writeFileToString(File file, String content, String encoding) throws IOException {
        try (PrintWriter writer = new PrintWriter(file.getAbsolutePath())){
            
            writer.print(content);
        }
    }
}