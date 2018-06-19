# phpjar

A self-contained PHP runtime bundled in a .jar file

## License

phpjar includes PHP which was compiled by XAMPP.  Their compilation is distributed under the terms of the GNU General Public license.  Their license is described as follows:

> XAMPP is a compilation of free software (comparable to a Linux distribution), it's free of charge and it's free to copy under the terms of the GNU General Public Licence. But it is only the compilation of XAMPP that is published under GPL. Please check every single licence of the contained products to get an overview of what is, and what isn't, allowed. In the case of commercial use please take a look at the product licences (especially MySQL), from the XAMPP point of view commercial use is also free.

See [the XAMPP about page](https://www.apachefriends.org/about.html) for more information.

## Synopsis

This project aims to make it easier to set up self-contained PHP development environments. It includes the PHP distributions for Mac, Windows, and Linux - which were all extracted
from [XAMPP](https://www.apachefriends.org/index.html).  It provides simple Java and CLI wrappers around the [PHP built-in web server](http://php.net/manual/en/features.commandline.webserver.php) allowing you to fire up a server with a specified document root and port number.  

## Requirements

* Java 8
* Mac, Windows, or Linux

## Current Version

The current bundled version of PHP is from XAMPP 7.2.6

## Installation

Download either [phpjar-thin.jar](bin/phpjar-thin.jar) or [phpjar-fat.jar](bin/phpjar-fat.jar) and add it to your project's classpath.  The thin jar is only 25kb and does not include the full PHP distribution.  It will download the required files from Github at runtime.  The Fat jar is over 50 megabytes and includes PHP distributions for Mac, Windows, and Linux.  The appropriate PHP distribution will be installed in the user's home directory on first run at `$HOME/.phpjar`, but this can be changed at runtime to any directory.


## Usage Instructions


### Starting the Server 

~~~~
PHPDevServer server = new PHPDevServer();
server.setDocumentRoot(new File("/path/to/htdocs"));
server.setPort(8080);	// Optional: omit to just start on available port
server.start();
	
// Server is now ready to receive requests
	
~~~~

### Stopping the Server

~~~~
server.close();
~~~~

### Making HTTP Requests

~~~~
HttpURLConnection conn = (HttpURLConnection)new URL("http://localhost:"+server.getPort()+"/index.php")
	.openConnection();

String contents = readFromInputStream(conn.openInputStream());
~~~~

### Executing PHP Directly

~~~~
String hello = server.executeUTF8("<?php echo 'hello world';");
	// returns hello world

// If the PHP script outputs binary like an image, you can use
// InputStream blob = server.execute("<?php phpFuncThatOutputsBinary();");

~~~~

## Credits

* Created by [Steve Hannah](http://www.sjhannah.com)
* Uses [XAMPP](https://www.apachefriends.org/index.html)'s builds of PHP.
* Thanks to [The PHP Group](http://php.net/copyright.php) for the wonderful [PHP](http://www.php.net).
	



	
	