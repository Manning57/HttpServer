import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

// The tutorial can be found just here on the SSaurel's Blog : 
// https://www.ssaurel.com/blog/create-a-simple-http-web-server-in-java
// Each Client Connection will be managed in a dedicated Thread
public class HttpServer implements Runnable{ 
	
	static final File WEB_ROOT = new File("public_html");
	static final String DEFAULT_FILE = "index.html";
	static final String FILE_NOT_FOUND = "404.html";
	static final String BAD_REQUEST = "400.html";
	static final String METHOD_NOT_SUPPORTED = "not_supported.html";
	
	// port to listen connection
	static int PORT = 16405;
	
	// Client Connection via Socket Class
	private Socket connect;
	
	public HttpServer(Socket c) {
		connect = c;
	}
	
	public static void main(String[] args) {
		
		try {
			PORT = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Invalid Port, defaulting to port 16405");
		}
		
		try {
			ServerSocket serverConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");
			
			// we listen until user halts server execution
			while (true) {
				HttpServer myServer = new HttpServer(serverConnect.accept());
				
				System.out.println("Connecton opened. (" + new Date() + ")");
				
				// create dedicated thread to manage the client connection
				Thread thread = new Thread(myServer);
				thread.start();
			}
			
		} catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}

	@Override
	public void run() {
		// we manage our particular client connection
		BufferedReader in = null; 
		PrintWriter out = null; 
		BufferedOutputStream dataOut = null;
		String fileRequested = null;
		
		try {
			// we read characters from the client via input stream on the socket
			in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			// we get character output stream to client (for headers)
			out = new PrintWriter(connect.getOutputStream());
			// get binary output stream to client (for requested data)
			dataOut = new BufferedOutputStream(connect.getOutputStream());
			// get first line of the request from the client
			String input = in.readLine();
			// we parse the request with a string tokenizer
			StringTokenizer parse = new StringTokenizer(input);
			// we get the HTTP method of the client
			String method = parse.nextToken().toUpperCase(); 
			// we get file requested
			fileRequested = parse.nextToken().toLowerCase();
			
			System.out.println("-------------START OF REQUEST HEADER------------\n");
			StringBuilder request = new StringBuilder();
			while (input != null) {
				System.out.println(input);
				request.append(input).append("\r\n");
				if (input.isEmpty()) {
					break;
				}
				input = in.readLine();
			}
			System.out.println("-------------END OF REQUEST HEADER---------------\n");
			
			// we support only GET and HEAD methods, we check
			if (!method.equals("GET")  &&  !method.equals("HEAD")) {
				// we return the not supported file to the client
				File file = new File(WEB_ROOT, METHOD_NOT_SUPPORTED);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";
				//read content to return to client
				byte[] fileData = readFileData(file, fileLength);
				
				System.out.println("-------------START OF RESPONSE HEADER---------------\n");
				System.out.println("HTTP/1.1 501 Not Implemented");
				System.out.println("Server: HttpServer 1.0.0");
				System.out.println("Date: " + new Date());
				System.out.println("Content-type: " + contentMimeType);
				System.out.println("Content-length: " + fileLength);
				System.out.println();
				System.out.println("-------------END OF RESPONSE HEADER---------------");
					
				// we send HTTP Headers with data to client
				out.println("HTTP/1.1 501 Not Implemented");
				out.println("Server: HttpServer 1.0.0");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println(); // blank line between headers and content, very important !
				out.flush(); // flush character output stream buffer
				// file
				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
				
			} else {
				// GET or HEAD method
				if (fileRequested.endsWith("/")) {
					fileRequested += DEFAULT_FILE;
				}
				
				File file = new File(WEB_ROOT, fileRequested);
				int fileLength = (int) file.length();
				String content = getContentType(fileRequested);
				
				if (method.equals("GET")) { // GET method so we return content
					byte[] fileData = readFileData(file, fileLength);
					
					System.out.println("-------------START OF RESPONSE HEADER---------------\n");
					System.out.println("HTTP/1.1 200 OK");
					System.out.println("Server: HttpServer 1.0.0");
					System.out.println("Date: " + new Date());
					System.out.println("Content-type: " + content);
					System.out.println("Content-length: " + fileLength);
					System.out.println();
					System.out.println("-------------END OF RESPONSE HEADER---------------");
					
					// send HTTP Headers
					out.println("HTTP/1.1 200 OK");
					out.println("Server: HttpServer 1.0.0");
					out.println("Date: " + new Date());
					out.println("Content-type: " + content);
					out.println("Content-length: " + fileLength);
					out.println(); // blank line between headers and content, very important !
					out.flush(); // flush character output stream buffer
					
					dataOut.write(fileData, 0, fileLength);
					dataOut.flush();
				}
			}
			
		} catch (FileNotFoundException fnfe) {
			try {
				fileNotFound(out, dataOut, fileRequested);
			} catch (IOException ioe) {
				System.err.println("Error with file not found exception : " + ioe.getMessage());
			}
			
		} catch (IOException ioe) {
			System.err.println("Server error : " + ioe);
		} catch (Exception e) {
			try {
				badRequest(out, dataOut, fileRequested);
			} catch (IOException ioe) {
				System.err.println("Error with bad request exception : " + ioe.getMessage());
			}
		} finally {
			try {
				in.close();
				out.close();
				dataOut.close();
				connect.close(); // we close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			System.out.println("Connection closed.\n");
		}
	}
	
	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		
		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null) 
				fileIn.close();
		}
		return fileData;
	}
	
	// return supported MIME Types
	private String getContentType(String fileRequested) {
		if (fileRequested.endsWith(".htm")  ||  fileRequested.endsWith(".html")) {
			return "text/html";
		} else if (fileRequested.endsWith(".gif")) {
			return "image/gif";
		} else if (fileRequested.endsWith(".pdf")) {
			return "application/pdf";
		} else if (fileRequested.endsWith(".jpg") || fileRequested.endsWith(".jpeg")) {
			return "image/jpeg";
		} else {
			return "text/plaintext";
		}
		
	}
	
	private void fileNotFound(PrintWriter out, OutputStream dataOut, String fileRequested) throws IOException {
		File file = new File(WEB_ROOT, FILE_NOT_FOUND);
		int fileLength = (int) file.length();
		String content = getContentType(fileRequested);
		byte[] fileData = readFileData(file, fileLength);
		
		System.out.println("-------------START OF RESPONSE HEADER---------------\n");
		System.out.println("HTTP/1.1 404 File Not Found");
		System.out.println("Server: HttpServer 1.0.0");
		System.out.println("Date: " + new Date());
		System.out.println("Content-type: " + content);
		System.out.println("Content-length: " + fileLength);
		System.out.println();
		System.out.println("-------------END OF RESPONSE HEADER---------------");
		
		out.println("HTTP/1.1 404 File Not Found");
		out.println("Server: HttpServer 1.0.0");
		out.println("Date: " + new Date());
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println(); // blank line between headers and content, very important !
		out.flush(); // flush character output stream buffer
		
		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
	}
	
	private void badRequest(PrintWriter out, OutputStream dataOut, String fileRequested) throws IOException {
		File file = new File(WEB_ROOT, BAD_REQUEST);
		int fileLength = (int) file.length();
		String content = getContentType(fileRequested);
		byte[] fileData = readFileData(file, fileLength);
		
		System.out.println("-------------START OF RESPONSE HEADER---------------\n");
		System.out.println("HTTP/1.1 400 Bad Request");
		System.out.println("Server: HttpServer 1.0.0");
		System.out.println("Date: " + new Date());
		System.out.println("Content-type: " + content);
		System.out.println("Content-length: " + fileLength);
		System.out.println();
		System.out.println("-------------END OF RESPONSE HEADER---------------");
		
		out.println("HTTP/1.1 400 Bad Request");
		out.println("Server: HttpServer 1.0.0");
		out.println("Date: " + new Date());
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println(); // blank line between headers and content, very important !
		out.flush(); // flush character output stream buffer
		
		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
	}
	
}