package javaserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import javaserver.JavaServer.HTTPSession.HTTPRequest;
import javaserver.JavaServer.HTTPSession.HTTPResponse;
import javaserver.JavaServer.HTTPSessionInterface;

public class TestServer {

	class HTTPSessionInterfaceTest implements HTTPSessionInterface {

		@Override
		public void onRequestHeader(HTTPRequest httpReq) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onRequestBody(HTTPRequest httpReq) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onResponse(HTTPRequest httpReq, HTTPResponse httpResp) {
			// TODO Auto-generated method stub

			System.out.println("Received " + httpReq.getMethod().toString()
					+ " request.\n");

			String pathname = "." + httpReq.getPath();
			// System.out.println(pathname);

			File curr = new File(pathname);
			String responseHeaders = "";
			String responseBody = "";

			if (curr.isDirectory()) {
				File[] listFiles = curr.listFiles();

				responseBody += "<!DOCTYPE html>\n" + "<html>\n";
				responseBody += "<head>"
						+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"/css/style.css\">"
						+ "</head>\n"
						+ "<h2> Index of "
						+ curr.getName()
						+ "/</h2>"
						+ "<div class=\"body\"><table>"
						+ "<tr>	<th> File Name </th><th> Last Modified </th><th> Size </th></tr>";

				for (int i = 0; i < listFiles.length; i++) {
					File currFile = listFiles[i];
					// String relativePath = httpReq.uri.substring(1).trim();

					String url = currFile.getName();
					
					responseBody += "<tr>";
					String cssClass = "\"file\"";
					if (currFile.isDirectory()) {
						url += "/";
						cssClass = "\"folder\"";
					}
					responseBody += "<td><div><img src=\"\\images\\folder-icon.jpg\"  width=\"32\"><a class=" +  cssClass +  " href=\"" + url + "\">"
							+ currFile.getName() + " </a></div></td>";
					responseBody += "<td>"
							+ new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
									.format(currFile.lastModified()) + "</td>";
					responseBody += "<td>" + currFile.length() + "</td>";
					responseBody += "</tr>";
					//
				}
				responseBody += "</div></table></body>\n";
			} else {

				try {
					BufferedInputStream f = new BufferedInputStream(new FileInputStream(pathname));
					byte[] byteArray = new byte[512];
					
					int pos = 0;
					int nRead;
					while (true) {
						
						nRead = f.read(byteArray, 0, 512 - pos);
						
						if (nRead < 0) {
							if (pos > 0) {
								httpReq.getHttpResp().addToResponseBody(Arrays.copyOfRange(byteArray, 0, pos));
							}
										
							break;
						}
						pos += nRead;
						
						if (pos == 512) {
							//responseBody += new String(byteArray);
							httpReq.getHttpResp().addToResponseBody(byteArray);
							byteArray = new byte[512];
							pos = 0;
						}
					}

					//while ((line = f.readLine()) != null) {
					//	responseBody += line + "\n"; // better to use a pipe
					//									// style
					//}
					// responseBody += " <br> \n";
					byteArray = null;
					f.close();
				} catch (IOException e) {
					//responseHeaders = "HTTP/1.1 404 Not Found\r\n";
					httpResp.setStatusCode(404);
					httpResp.setStatusLine("Not Found\r\n");
					responseHeaders += "Content-Length: "
							+ responseBody.length() + "\r\n";
					responseHeaders += "Connection: Close\r\n";
					//responseHeaders += "\r\n\r\n";
					responseBody = "<div style=\"font-family: Helvetica, Arial, sans-serif; font-size: 12pt; color: blue;\">"
							+ "File Not Found </div>";
				}
			}
			if (responseHeaders.equals("")) {
				httpResp.setStatusCode(200);
				httpResp.setStatusLine("OK\r\n");
				//responseHeaders = "HTTP/1.1 200 OK\r\n" + "Content-Length: "
				//		+ responseBody.length() + "\r\n\r\n";
				
			}

			System.out.println("Response Headers:\n");
			System.out.println(responseHeaders);
			System.out.println("Response Body:\n");
			System.out.println(responseBody);
			System.out.println("-----------------------------------------------------------------------------------------");
			
			httpResp.addToResponseHeaders(responseHeaders);
			httpResp.addToResponseBody(responseBody.getBytes());
		}

	}

	public static void main(String[] args) throws IOException {
		JavaServer js = new JavaServer(new TestServer().new HTTPSessionInterfaceTest());
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		br.readLine();
	}
}
