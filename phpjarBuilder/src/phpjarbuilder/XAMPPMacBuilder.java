/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phpjarbuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author shannah
 */
public class XAMPPMacBuilder {
    private String git = "/usr/bin/git";
    private final File destDir = new File("dist/phpjar-macos");
    private File xamppPath = new File("/Applications/XAMPP");
    private File macDylibBunder = new File("tools/macdylibbundler");
    private String macDylibBundlerUrl = "https://github.com/shannah/macdylibbundler";
    
    public void execute() throws IOException {
        File tmpBuLocation = null;
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
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
        }
        if (!dist.exists()) {
            dist.mkdir();
        }
        
        
        if (!destDir.getAbsoluteFile().getParentFile().exists()) {
            throw new IOException("Destination directory does not exist");
        }
        
        if (!macDylibBunder.exists()) {
            System.out.println("macdylibbunder not found.");
            System.out.println("Trying to install it from "+macDylibBundlerUrl+" at "+macDylibBunder.getAbsolutePath());
            macDylibBunder.getParentFile().mkdirs();
                    
            ProcessBuilder pb = new ProcessBuilder(git, "clone", macDylibBundlerUrl, macDylibBunder.getAbsolutePath());
            pb.inheritIO();
            Process p = pb.start();
            try {
                int resultCode = p.waitFor();
                if (resultCode != 0) {
                    throw new IOException("Failed to clone macdylibBundler");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
        }
        
        File dylibBundlerExe = new File(macDylibBunder, "dylibbundler");
        if (!dylibBundlerExe.exists()) {
            ProcessBuilder pb = new ProcessBuilder("make").directory(macDylibBunder).inheritIO();
            Process p = pb.start();
            try {
                if (p.waitFor() != 0) {
                    throw new IOException("Failed to compile macdylibBundler");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
        }
        
        // Now we need to backup the XAMPP directory
        tmpBuLocation = File.createTempFile("XAMPP", "tmp", new File("/Applications"));
        tmpBuLocation.delete();
        File buLocation = new File(xamppPath.getParentFile(), xamppPath.getName()+"."+System.currentTimeMillis());
        if (buLocation.exists()) {
            throw new IOException("Backup location for XAMPP already exists.  Try running this again.");
        }
        if (!xamppPath.renameTo(buLocation)) {
            throw new IOException("Failed to move XAMPP to backup location");
        }
        try {
            
            ProcessBuilder pb = new ProcessBuilder("rsync", "-av",  
                    "--prune-empty-dirs", 
                    "--exclude", "xamppfiles/var",
                    "--include", "*/",
                    "--include", "xamppfiles/etc/php.ini",
                    "--include", "*.dylib",
                    
                    "--include", "xamppfiles/bin/php*",
                    "--exclude", "*",
                    buLocation.getAbsolutePath(),
                    tmpBuLocation.getAbsolutePath()
                    
            ).inheritIO();
            try {
                if (pb.start().waitFor() != 0) {
                    throw new IOException("Failed to rsync XAMPP to proper location");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
            
            if (!new File(tmpBuLocation, buLocation.getName()).renameTo(xamppPath)) {
                throw new IOException("Failed to move XAMPP copy out of temp directory after rsync was complete");
            }
            
            
            
            // Fix issue with postgresql libraries.
            File postgresql = new File(xamppPath, "xamppfiles/postgresql");
            File postgresqlLibs = new File(postgresql, "lib");
            if (!postgresqlLibs.mkdirs()) {
                throw new IOException("Failed to make directory "+postgresqlLibs);
            }
            //try {
            //    Thread.sleep(20000);
            //} catch (Throwable t){}
            File copyPqLibsScript = File.createTempFile("copyPqLibs", ".sh");
            String copyCommand = "#!/bin/sh\ncp " +
                        xamppPath.getAbsolutePath() +
                        "/xamppfiles/lib/libpq* " +
                        postgresqlLibs.getAbsolutePath()+"/";
            try (PrintWriter pw = new PrintWriter(copyPqLibsScript)) {
                pw.append(copyCommand);
                
            }
            System.out.println("Copy command: "+copyCommand);
            
            
            pb = new ProcessBuilder("/bin/sh", copyPqLibsScript.getAbsolutePath())
                    .inheritIO();
            try {
                if (pb.start().waitFor() != 0) {
                    throw new IOException("Failed to copy libpq files to proper location.");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
            if (copyPqLibsScript.exists()) {
                copyPqLibsScript.delete();
            }
            File phpPath = new File(xamppPath, "xamppfiles/bin/php");
            
            
            destDir.mkdir();
            pb = new ProcessBuilder(dylibBundlerExe.getAbsolutePath(), 
                    "-x", phpPath.getAbsolutePath(),
                    "-d", destDir.getAbsolutePath()+"/libs",
                    "-cd", "-b"
            ).inheritIO();
            try {
                if (pb.start().waitFor() != 0) {
                    throw new IOException("Error occurred while bundling dylibs");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
            
            
            //File destBinDir = new File(destDir, "bin");
            //if (!destBinDir.mkdir()) {
            //    throw new IOException("Failed to make directory "+destBinDir);
            //}
            
            new File(destDir, "bin").mkdir();
            
            pb = new ProcessBuilder("cp", 
                    xamppPath.getAbsolutePath()+"/xamppfiles/bin/php", 
                    destDir.getAbsolutePath()+"/bin/php")
                    .inheritIO();
            try {
                if (pb.start().waitFor() != 0) {
                    throw new IOException("Failed to copy php binaries");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
            
            pb = new ProcessBuilder("cp",
                    xamppPath.getAbsolutePath()+"/xamppfiles/etc/php.ini",
                    destDir.getAbsolutePath()+"/php.ini")
                    .inheritIO();
            try {
                if (pb.start().waitFor() != 0) {
                    throw new IOException("Failed to copy php.ini");
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
            
        } finally {
            if (buLocation.exists()) {
                if (xamppPath.exists()) {
                    ProcessBuilder pb = new ProcessBuilder("rm", "-rf", xamppPath.getAbsolutePath()).inheritIO();
                    try {
                        if (pb.start().waitFor() != 0) {
                            throw new IOException("Failed to remove temp XAMPP directory.  You can find your original XAMPP directory located at "+buLocation.getAbsolutePath());
                        }
                    } catch (InterruptedException ex) {
                        Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (!buLocation.renameTo(xamppPath)) {
                    throw new IOException("Failed to restore your XAMPP directory to its original location after completion.  You can find your XAMPP directory located at "+buLocation.getAbsolutePath());
                }
            }
            if (tmpBuLocation != null && tmpBuLocation.exists()) {
                ProcessBuilder pb = new ProcessBuilder("rm", "-rf", tmpBuLocation.getAbsolutePath()).inheritIO();
                try {
                    if (pb.start().waitFor() != 0) {
                        throw new IOException("Failed to remove temp backup location directory.  You can find your original XAMPP directory located at "+buLocation.getAbsolutePath());
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(XAMPPMacBuilder.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    
    public static void main(String[] args) throws IOException  {
        XAMPPMacBuilder builder = new XAMPPMacBuilder();
        builder.execute();
    }
}
