package javaserver;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.SequenceInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;

import javaserver.JavaServer.HTTPSession.HTTPRequest;
import javaserver.JavaServer.HTTPSession.HTTPResponse;

public class JavaServer {
	final int portNum = 8005;

	public enum Method {
		GET, POST, HEAD;
		public static Method lookup(String method) {
			for (Method m : Method.values()) {
				if (m.toString().equalsIgnoreCase(method))
					return m;
			}
			return null;
		}
	}

	interface HTTPSessionInterface {
		void onRequestHeader(HTTPRequest httpReq);
		void onRequestBody(HTTPRequest httpReq);
		void onResponse(HTTPRequest httpReq, HTTPResponse httpResp);
	}
	
	public JavaServer(final HTTPSessionInterface interf) throws IOException {
		final ServerSocket s = new ServerSocket(portNum);
		System.out.println("Server is listening on port " + portNum + ".");
	
		while (true) {
			final Socket client = s.accept();
			// Client thread
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						HTTPSession hs = new HTTPSession(client);
						hs.setHTTPSessionInterface(interf);
						hs.start();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			thread.start();
		}
		
		// thread.setDaemon(true);
		//s.close();
	}

	public class HTTPSession {
		private OutputStream clientOut;
		private InputStream clientIn;
		private BufferedReader inputBuffer;
		private HTTPSessionInterface interf;
		
		public HTTPSession(Socket client) throws IOException {
			clientOut = new DataOutputStream(client.getOutputStream());
			clientIn = client.getInputStream();
			// Thread thread = new Thread(new Runnable() {
			// @Override
			// public void run() {

		}
		
		public void setHTTPSessionInterface(HTTPSessionInterface interf) {
			this.interf = interf;  
		}
		
		public void start() throws IOException {
			while (true) {
				HTTPRequest httpReq = new HTTPRequest();
				if (httpReq.processRequest() == -1)
					break;
				HTTPResponse httpResp = new HTTPResponse();
				httpReq.setResponse(httpResp);
				
				interf.onResponse(httpReq, httpResp);
				
				//generateResponse(httpReq, httpResp);
							
				httpResp.send();
			}
		}
	
		class HTTPRequest {
			private Map<String, String> headers;
			private Method method;
			private String uri;
			private String path = null;
			private Map<String, String> query = null;
			final int readBlockSize = 4096;
			private LinkedList<byte[]> body;
			private HTTPResponse httpResp;
				
			int processRequest() throws IOException {

				byte[] buff = new byte[readBlockSize];
				int nRead, pos, emptyLinePos;
				pos = 0;
				emptyLinePos = 0;
				nRead = 0;
				int buffLenLeft = 0;
				// nRead = clientIn.read(buff, 0, readBlockSize);
				while (true) {
					nRead = clientIn.read(buff, pos, readBlockSize - pos);
					if (nRead == -1) {
						System.out.println("Socket has been closed.");
						return -1;
					}

					pos += nRead;
					System.out.println(new String(buff, 0, pos));

					emptyLinePos = findEmptyLine(buff, pos - nRead, nRead);
					if (emptyLinePos > 0) {
						inputBuffer = new BufferedReader(
								new InputStreamReader(new ByteArrayInputStream(
										buff, 0, emptyLinePos)));
						if (emptyLinePos < pos) {
							clientIn = new SequenceInputStream(
									new ByteArrayInputStream(buff,
											emptyLinePos, pos), clientIn);
						}
						break;
					}

					buffLenLeft = readBlockSize - pos;
					if (buffLenLeft == 0) {
						System.out.println("Header too long.");
						break;
					}

				}

				headers = new HashMap<String, String>();
				
				// BufferedReader clientbr = new BufferedReader(new
				// InputStreamReader(new ByteArrayInputStream(buff, 0, len)));
				// BufferedReader clientbr = new BufferedReader(new
				// InputStreamReader(clientIn));

				String clientLine;
				clientLine = inputBuffer.readLine();

				processRequestLine(clientLine);
				processHeaders(inputBuffer);
				interf.onRequestHeader(this);
				// req.printHeaders();
				if (method.toString().equals("POST")) {
					processBody();
					//printBody();
					interf.onRequestBody(this);
				}

				return 1;
			}

			public void setResponse(HTTPResponse httpResp) {
				this.httpResp = httpResp;
				
			}

			private int findEmptyLine(byte[] buff, int start, int len) {
				int emptyLinePos = Math.max(0, start - 3);

				while (emptyLinePos < start + len - 3) {
					if (buff[emptyLinePos] == '\r'
							&& buff[emptyLinePos + 1] == '\n'
							&& buff[emptyLinePos + 2] == '\r'
							&& buff[emptyLinePos + 3] == '\n') {
						return emptyLinePos + 4;
					}

					emptyLinePos++;
				}
				return 0;
			}
			
			void printHeaders() {
				for (Map.Entry<String, String> entry : headers.entrySet()) {
					String key = entry.getKey();
					String value = entry.getValue();
					// do stuff
					System.out.println(key + " : " + value);
				}
			}

			void processRequestLine(String reqLine) throws IOException {
				StringTokenizer st = new StringTokenizer(reqLine);
				method = Method.lookup(st.nextToken());
				uri = st.nextToken();
				path = uri;
				int qMark = uri.lastIndexOf('?');
				if (qMark != -1) {
					path = uri.substring(0, qMark);
					this.query = new HashMap<String, String>();
					StringTokenizer query = new StringTokenizer(uri.substring(qMark + 1), "&");
					while (query.hasMoreTokens()) {
						String[] keyVal = query.nextToken().split("=");
						this.query.put(keyVal[0], keyVal[1]);
					}	
				}
				//TODO: Throw exception if last token is not HTTP/1.1 or 1.0
				if (!st.nextToken().equals("HTTP/1.1")) {
					throw new IOException();
				}
			}

			void processHeaders(BufferedReader clientbr) throws IOException {
				String clientLine = null;
				while ((clientLine = clientbr.readLine()) != null) {
					// System.out.println(clientLine);
					int colon = clientLine.indexOf(':');
					if (colon != -1)
						headers.put(clientLine.substring(0, colon).trim()
								.toLowerCase(), clientLine.substring(colon + 1)
								.trim());
				}
			}
			
			void processBody() throws IOException {
				byte[] buff = null;
				int pos = 0, nRead = 0, len = 0, size = 0;

				if (headers.containsKey("content-length"))
					size = Integer.parseInt(headers.get("content-length"));

				body = new LinkedList<byte[]>();
				while (len < size) {
					if (pos == 0) {
						buff = new byte[512];
					}
					nRead = clientIn.read(buff, pos,
							Math.min(size - len, 512 - pos));
					pos += nRead;
					len += nRead;
					if (pos == 512 || len == size) {
						body.add(buff);
						pos = 0;
					}
				}

			}

			void printBody() {
				System.out.println("Request body:");
				for (byte[] byteArray: body) {
					System.out.println(new String(byteArray));
				}
				System.out.println();
			}

			public Method getMethod() {
				return method;
			}

			public String getPath() {
				return path;
			}

			public HTTPResponse getHttpResp() {
				return httpResp;
			}

		}
		
		class HTTPResponse {
			private String responseHeaders;
			private LinkedList<byte[]> responseBody;
			private int statusCode;
			private String statusLine;
			private int bodyLen;
			
			HTTPResponse() {
				this(200, "");
			}
				
			HTTPResponse(int statusCode, String statusLine) {
				this.statusCode = statusCode;
				this.statusLine = statusLine;
				responseHeaders = "";
				responseBody = new LinkedList<byte[]>();
				bodyLen = 0;
			}
			
			void setStatusCode(int statusCode) {
				this.statusCode = statusCode;
			}
			
			void setStatusLine(String statusLine) {
				this.statusLine = statusLine;
			}
			
			void send() throws IOException {
				clientOut.write(("HTTP/1.1 " + statusCode + " " + statusLine).getBytes());
				clientOut.write(("Content-Length: " + bodyLen).getBytes());
				clientOut.write(responseHeaders.getBytes());
				clientOut.write("\r\n\r\n".getBytes());
				for (byte[] byteArray : responseBody) {
					clientOut.write(byteArray);
				}
				clientOut.flush();
			}
			
			void addToResponseHeaders(String responseHeaders) {
				this.responseHeaders += responseHeaders;  
			}

			public void addToResponseBody(byte[] responseBody) {
				this.responseBody.add(responseBody);
				bodyLen += responseBody.length; 
			}


		}
	}
}