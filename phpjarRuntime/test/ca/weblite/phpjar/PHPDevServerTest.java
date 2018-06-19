/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.phpjar;

import ca.weblite.phpjar.PHPDevServer;
import ca.weblite.phpjar.nativeutils.NativeUtils;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author shannah
 */
public class PHPDevServerTest {
    private PHPDevServer server;
    private File documentRoot;
    
    
    public PHPDevServerTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        documentRoot = null;
        try {
            server = new PHPDevServer();
            documentRoot = File.createTempFile("PHPDevServerTest", "dir");
            documentRoot.delete();
            documentRoot.mkdir();
            server.setDocumentRoot(documentRoot);
            server.start();
            
        } catch (IOException ex) {
            
            if (documentRoot != null) {
                try {
                    NativeUtils.delTree(documentRoot);
                } catch (Throwable t) {
                    //throw new RuntimeException(t);
                }
            }
            Logger.getLogger(PHPDevServerTest.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
            
            
        }
    }
    
    @After
    public void tearDown() {
        try {
            if (documentRoot != null) {
                NativeUtils.delTree(documentRoot);
            }
            server.close();
        } catch (Exception ex) {
            Logger.getLogger(PHPDevServerTest.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
    }

    

    /**
     * Test of executeUTF8 method, of class PHPDevServer.
     */
    @Test
    public void testExecute() throws Exception {
        System.out.println("execute");
        String phpCode = "<?php echo 'hello world';";
        PHPDevServer instance = server;
        String expResult = "hello world";
        String result = instance.executeUTF8(phpCode);
        assertEquals(expResult, result);

    }
    
    
    
}
