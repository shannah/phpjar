/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phpjarbuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author admin
 */
public class XAMPPLinuxBuilder {
    private File xamppPath = new File("/opt/lampp");
    private final File destDir = new File("dist/phpjar-linux");
    public void execute() throws IOException {
        
        if (!xamppPath.exists()) {
            throw new IOException("Building phpjar requires XAMPP to be installed.  Please download and install it first https://www.apachefriends.org/download.html");
        }
        
        File dist = new File("dist");
        if (destDir.exists()) {
            try {
                if (new ProcessBuilder("rm", "-rf", destDir.getAbsolutePath()).inheritIO().start().waitFor() != 0) {
                    throw new IOException("Failed to delete destination directory");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPLinuxBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
        }
        if (!dist.exists()) {
            dist.mkdir();
        }
        
        File phpDir = new File(destDir, "php");
        phpDir.mkdirs();
        
        File binDir = new File(phpDir, "bin");
        binDir.mkdir();
        
        try {
            if (new ProcessBuilder("cp", xamppPath.getAbsolutePath()+"/bin/php", binDir.getAbsolutePath()+"/php")
                    .inheritIO().start().waitFor() != 0) {
                throw new IOException("Failed to copy php binary");
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(XAMPPLinuxBuilder.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
        
        File libDir = new File(phpDir, "lib");
        libDir.mkdir();
        
        ProcessBuilder lddPb = new ProcessBuilder("ldd", xamppPath.getAbsolutePath()+"/bin/php");
        Process lddP = lddPb.start();
        InputStream lddInput = lddP.getInputStream();
        Scanner lddScanner = new Scanner(lddInput, "UTF-8");
        Set<String> requiredLibs = new HashSet<String>();
        while (lddScanner.hasNextLine()) {
            String line = lddScanner.nextLine().trim();
            int dividerPos = line.indexOf("=>");
            if (dividerPos <= 0) {
                continue;
            }
            String libName = line.substring(0, dividerPos).trim();
            String remainder = line.substring(dividerPos+2).trim();
            int libPos = remainder.indexOf(libName);
            if (libPos <= 0) {
                continue;
            }
            String libPathPrefix = remainder.substring(0, libPos).trim();
            if (libPathPrefix.startsWith(xamppPath.getAbsolutePath())) {
                requiredLibs.add(libName);
            }
        }
        
        if (requiredLibs.isEmpty()) {
            throw new IOException("No required libraries found for PHP binary.  Something is wrong");
        }
        
        for (File lib : new File(xamppPath.getAbsolutePath(), "lib").listFiles()) {
            if (!requiredLibs.contains(lib.getName())) {
                // This library isn't required by PHP
                continue;
            }
            try {
                if (new ProcessBuilder("cp", lib.getAbsolutePath(), libDir.getAbsolutePath()).inheritIO().start().waitFor() != 0) {
                    throw new IOException("Failed to copy "+lib);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPLinuxBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
        }
        
        
        File phpIniSrc = new File(xamppPath, "etc/php.ini");
        File phpIniDest = new File(phpDir, "php.ini");
        try {
            if (new ProcessBuilder("cp", phpIniSrc.getAbsolutePath(), phpIniDest.getAbsolutePath()).inheritIO().start().waitFor() != 0) {
                throw new IOException("Failed to copy "+phpIniSrc);
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(XAMPPLinuxBuilder.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
        
        System.out.println("Finished copying PHP into "+destDir);
        
         
    }
    
    public static void main(String[] args) throws IOException  {
        XAMPPLinuxBuilder builder = new XAMPPLinuxBuilder();
        builder.execute();
    }
    
}
