/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.phpjar;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Java wrapper around the PHP build-in web server.
 * @author Steve Hannah
 */
public class PHPDevServer implements AutoCloseable, Runnable {

    /**
     * @return the routerScript
     */
    public String getRouterScript() {
        return routerScript;
    }

    /**
     * @param routerScript the routerScript to set
     */
    public void setRouterScript(String routerScript) {
        this.routerScript = routerScript;
    }

    private File phpIniFile = null;
    private final Map<String,String> phpIniDirectives = new HashMap<String,String>();
    private final Map<String,String> environment = new HashMap<String,String>();
    
    public Map<String,String> getEnvironment() {
        return environment;
    }
    
    public Map<String,String> getPhpIniDirectives() {
        return phpIniDirectives;
    }
    
    public File getPhpIniFile() {
        return phpIniFile;
    }
    
    
    public void setPhpIniFile(File f) {
        this.phpIniFile = f;
    }
    
    /**
     * Gets the install directory where native PHP should be installed.
     * If null, then the default directory will be used.
     * @return the installDirectory
     * @see PHPLoader#getInstallDir() 
     * @see PHPLoader#setInstallDir(java.io.File) 
     */
    public File getInstallDirectory() {
        return installDirectory;
    }

    /**
     * Sets the install directory where native PHP should be installed.
     * Set this to {@literal null} to just use the default directory.
     * @param installDirectory the installDirectory to set
     */
    public void setInstallDirectory(File installDirectory) {
        this.installDirectory = installDirectory;
    }
    
    /**
     * The directory where to install phpjar
     */
    private File installDirectory;
    
    /**
     * The path to the php binary, if not using the bundled version.
     */
    private String phpPath="php";
    
    /**
     * The port for the php server to listen on.
     * If this is set to {@literal 0}, it will choose an open port when
     * {@link #start() } is called, and this port will thereafter be retrievable
     * via {@link #getPort() }
     */
    private int port=0;
    
    /**
     * The document root for the PHP server.
     */
    private File documentRoot = new File(".").getAbsoluteFile();
    
    /**
     * The php process
     */
    private Process proc;
    
    /**
     * The thread that is running the PHP process.
     */
    private Thread thread;
    
    /**
     * Flag to indicate that the server is running.
     */
    private boolean running;
    
    /**
     * Flag to indicate that the server was running but is not stopped.
     */
    private boolean ended;
    
    /**
     * A lock used for synchronizing access to the PHP process and thread.
     */
    private final Object lock = new Object();
    
    /**
     * Flag to indicate that the server will use the bundled version
     * of PHP.
     */
    private boolean useBundledPHP = true;
    
    private String routerScript = null;
    
    public PHPDevServer() {
        
    }
    
    /**
     * Starts the PHP server.  When this method returns, the server
     * is finished starting up and is available to receive requests.
     */
    public void start() {
        thread = new Thread(this);
        thread.start();
        while (!isRunning() && !isEnded()) {
            synchronized(lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(PHPDevServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        System.out.println("Finished starting...");
    }
    
    
    private static void inheritIO(final InputStream src, final PrintStream dest) {
        new Thread(new Runnable() {
            public void run() {
                Scanner sc = new Scanner(src);
                while (sc.hasNextLine()) {
                    dest.println(sc.nextLine());
                }
            }
        }).start();
    }

    public File getPHPExe() throws IOException {
        String phpPath = getPhpPath();

        if (useBundledPHP) {
            PHPLoader phpLoader = new PHPLoader();
            if (getInstallDirectory() != null) {
                phpLoader.setInstallDir(getInstallDirectory());
            }
            File bundledPhpDir = phpLoader.load(false);
            File phpExe = new File(new File(bundledPhpDir, "bin"), "php");
            if (!phpExe.exists()) {
                phpExe = new File(phpExe.getParentFile(), "php.exe");
            }
            if (!phpExe.exists()) {
                phpExe = new File(bundledPhpDir, "php.exe");
            }
            if (!phpExe.exists()) {
                throw new IOException("Bundled PHP executable not found");
            }
            phpExe.setExecutable(true);
            phpPath = phpExe.getAbsolutePath();

        }
        return new File(phpPath);
    }
    
    /**
     * Runs the server.  Don't call this method directly.  It is used
     * internally.  Use {@link #start() } to start the server.
     */
    public void run() {
        try {
            if (port == 0) {
                ServerSocket sock = new ServerSocket(0);
                port = sock.getLocalPort();
                sock.close();
            }
            String phpPath = getPHPExe().getAbsolutePath();
            String hostname = "0";
            if (PHPLoader.isWindows()) {
                hostname = InetAddress.getByName(null).getHostAddress();
            }
            //System.out.println("Starting server at "+hostname+":"+getPort());
            
            
            ProcessBuilder pb = new ProcessBuilder(phpPath, "-S", hostname+":"+getPort());
            pb.environment().putAll(environment);
            if (useBundledPHP && PHPLoader.isWindows()) {
                File phpDir = new File(phpPath).getParentFile();
                File phpIni = new File(phpDir, "php.ini");
                if (this.phpIniFile != null && this.phpIniFile.exists()) {
                    phpIni = this.phpIniFile;
                }
                if (phpIni.exists()) {
                    pb.command().add("-c");
                    pb.command().add(phpDir.getAbsolutePath());
                } else {
                    throw new IOException("Could not find php.ini file at "+phpIni.getAbsolutePath());
                }
                //pb.environment().put("PATH", new File(new File(phpPath).getParentFile(), "ext").getAbsolutePath()+File.pathSeparator+System.getenv("PATH"));
                //System.out.println(pb.environment());
                
                //pb.command().add("-d");
                //pb.command().add("extension_dir="+new File(new File(phpPath).getParentFile(), "ext").getAbsolutePath());
            } else if (useBundledPHP && PHPLoader.isMac()) {
                File phpDir = new File(phpPath).getParentFile().getParentFile();
                File phpIni = new File(phpDir, "php.ini");
                if (this.phpIniFile != null && this.phpIniFile.exists()) {
                    phpIni = this.phpIniFile;
                }
                if (phpIni.exists()) {
                    pb.command().add("-c");
                    pb.command().add(phpDir.getAbsolutePath());
                } else {
                    throw new IOException("Could not find php.ini file at "+phpIni.getAbsolutePath());
                }
            } else if (useBundledPHP && PHPLoader.isUnix()) {
                File phpDir = new File(phpPath).getParentFile().getParentFile();
                File phpIni = new File(phpDir, "php.ini");
                if (this.phpIniFile != null && this.phpIniFile.exists()) {
                    phpIni = this.phpIniFile;
                }
                if (phpIni.exists()) {
                    pb.command().add("-c");
                    pb.command().add(phpDir.getAbsolutePath());
                } else {
                    throw new IOException("Could not find php.ini file at "+phpIni.getAbsolutePath());
                }
                File libDir = new File(phpDir, "lib");
                pb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
            }
            
            for (String phpIniFlag : phpIniDirectives.keySet()) {
                pb.command().add("-d");
                pb.command().add(phpIniFlag+"="+phpIniDirectives.get(phpIniFlag));
            }
            
            if (routerScript != null) {
                pb.command().add(routerScript);
            }
            
            //System.out.println(pb.command());
            pb.directory(getDocumentRoot());
            
            pb.inheritIO();
            proc = pb.start();
            long startTime = System.currentTimeMillis();
            long timeout = 5000;
            String testFileName = "tmp-"+startTime+".txt";
            File testFile = new File(getDocumentRoot(), testFileName);
            if (!testFile.createNewFile()) {
                throw new IOException("Failed to create test file");
            }
            
            URL testUrl = new URL("http://localhost:"+getPort()+"/"+testFile.getName());
            boolean success = false;
            while (System.currentTimeMillis() - timeout < startTime) {
                try {
                    //System.out.println("Connecting to "+testUrl);
                    HttpURLConnection conn = (HttpURLConnection)testUrl.openConnection();
                    
                    conn.setUseCaches(false);
                    int responseCode = conn.getResponseCode();
                    //System.out.println("Response code "+responseCode);
                    if (responseCode == 200) {
                        success = true;
                        break;
                    }
                } catch (Throwable t) {
                    //System.out.println(t.getMessage());
                    //t.printStackTrace();
                }
            }
            testFile.delete();
            if (!success) {
               
                throw new IOException("Failed to start PHP Server");
            }
            synchronized(lock) {
                running = true;
                lock.notifyAll();
            }
            //System.out.println("Now we are running");
            int res = proc.waitFor();
            //System.out.println("Result code "+res);
        } catch (IOException ex) {
            Logger.getLogger(PHPDevServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(PHPDevServer.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (proc != null && proc.isAlive()) {
                try {
                    proc.destroyForcibly();
                } catch (Throwable t){}
            }
            running = false;
            ended = true;
            synchronized(lock) {
                
                lock.notifyAll();
            }
        }
        
        
    }
    
    /**
     * Stops the server.  Satisfies the {@link AutoCloseable} interface
     * so that you can use inside Java 8 try-with blocks.
     * @throws Exception 
     */
    @Override
    public void close() throws Exception {
        System.out.println("Closing test runner");
        proc.destroyForcibly();
        running = false;
    }

    /**
     * Gets the path to the system PHP command.  This is only used if 
     * {@link #useBundledPHP } is false.
     * @return the phpPath The path to the PHP command
     */
    public String getPhpPath() {
        return phpPath;
    }

    /**
     * Sets the path to the system PHP command.  This is not used if {@link #useBundledPHP}
     * is true.
     * @param phpPath the phpPath to set
     */
    public void setPhpPath(String phpPath) {
        this.phpPath = phpPath;
    }

    /**
     * Returns the port that the PHP server is to listen on.  If the server is running,
     * then this will return the port number that it is listening on. 
     * 
     * 
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port that the PHP server should listen on.  After {@link #start() }
     * has been called, this method will have not effect.
     * 
     * <p>Setting the port to {@link 0} before calling {@link #start() } will result
     * in the PHP server listing on an available port. After the server is started,
     * {@link #getPort() } will reflect the actual port that the server is listening on.
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets the document root for the server.
     * @return the documentRoot
     */
    public File getDocumentRoot() {
        return documentRoot;
    }

    /**
     * Sets the document root for the server.
     * This has no effect after server is already started.
     * @param documentRoot the documentRoot to set
     */
    public void setDocumentRoot(File documentRoot) {
        this.documentRoot = documentRoot;
    }

    /**
     * Checks if the server is running.
     * @return the running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Checks if the server is ended.
     * @return the ended
     */
    public boolean isEnded() {
        return ended;
    }
    
    /**
     * Executes given PHP code.
     * @param phpCode The PHP code to execute.
     * @return The output as a string (assuming PHP is returning content-type UTF-8)
     * @throws IOException 
     */
    public String executeUTF8(String phpCode) throws IOException {
        try (InputStream is = execute(phpCode)) {
            StringBuilder sb = new StringBuilder();
            int len;
            byte[] buf = new byte[8096 * 4];
            while ((len = is.read(buf)) >= 0) {
                sb.append(new String(buf, 0, len, "UTF-8"));
            }
            return sb.toString();
        } 
    }
    
    /**
     * Executes PHP code and returns the result.
     * @param phpCode The PHP code to execute
     * @return The output as an InputStream.
     * @throws IOException 
     */
    public InputStream execute(String phpCode) throws IOException {
        if (!isRunning()) {
            throw new RuntimeException("PHP development server is currently not running");
        }
        InputStream out;
        File tmp = File.createTempFile("execute", ".php", getDocumentRoot());
        tmp.deleteOnExit();
        try {
            try (PrintWriter pw = new PrintWriter(tmp)) {
                pw.print(phpCode);

            } 
            
            
            URL url = new URL("http://localhost:"+getPort()+"/"+tmp.getName());
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setUseCaches(false);
            return conn.getInputStream();
           
        } finally {
            tmp.delete();
        }
    }
    
}
