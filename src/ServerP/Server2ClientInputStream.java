package ServerP;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Server2ClientInputStream extends BufferedInputStream {

	private String buf;
	/**
	 * How many Bytes read?
	 */
	private int nRead = 0;

	/**
	 * one line
	 */
	private String line;
	/**
	 * The length of the header (with body, if one)
	 */
	private int headerLength = 0;
	/**
	 * The length of the (optional) body of the actual request
	 */
	private int contentLength = 0;
	/**
	 * This is set to true with requests with bodies, like "POST"
	 */
	private boolean body = false;

	private static Server2Server server;

	/**
	 * Connection variables
	 */
	private final Server2HTTPSession connection;
	private InetAddress remoteHostAddress;
	private String remoteHostName;
	private boolean ssl = false;

	private String errordescription;
	private int statuscode;

	public String url;
	public String method;
	public int HTTPversion;
	public boolean ipv6reference; // true only for IPv6 address in URL (RFC
									// 2732)
	public int remotePort = 0;
	public int post_data_len = 0;
	public byte[] a;
	public byte[] URLH = null;
	public String loadedURLHash = "";
	public boolean ffound;
	public boolean RequestWF = false;
	public String requestFileHash = "-1";

	// int cc = 0;

	public int getHeaderLength() {
		return headerLength;
	}

	public InetAddress getRemoteHost() {
		return remoteHostAddress;
	}

	public String getRemoteHostName() {
		return remoteHostName;
	}

	public Server2ClientInputStream(Server2Server server,
			Server2HTTPSession connection, InputStream a) {
		super(a);
		Server2ClientInputStream.server = server;
		this.connection = connection;
	}

	/**
	 * Handler for the actual HTTP request
	 * 
	 * @exception IOException
	 */
	@Override
	public int read(byte[] a) throws IOException {
		long s1 = System.currentTimeMillis();
		statuscode = Server2HTTPSession.SC_OK;
		this.a = a;
		if (ssl) // no parsing required if in SSL mode
			return super.read(this.a);

		if (server == null) {
			throw new IOException("Stream closed");
		}

		boolean cookies_enabled = server.enableCookiesByDefault();
		boolean start_line = true;
		int nChars;

		String rq = "";
		headerLength = 0;
		post_data_len = 0;
		contentLength = 0;

		nChars = getLine(); // reads the first line
		buf = line;

		while (nChars != -1 && nChars > 2) {
			// while (lread > 2) {
			if (start_line) {
				start_line = false;
				int methodID = server.getHttpMethod(buf);
				switch (methodID) {
				case -1:
					statuscode = Server2HTTPSession.SC_NOT_SUPPORTED;
					break;
				case 2:
					ssl = true;
				default:
					InetAddress host = parseRequest(buf, methodID);
					if (statuscode != Server2HTTPSession.SC_OK) {
						break; // error occurred, go on with the next line
					}
					if (!server.useProxy && !ssl) {
						/* creates a new request without the host name */
						buf = method + " " + url + " "
								+ server.getHttpVersion() + "\r\n";
						nRead = buf.length();
					}

					if ((server.useProxy && connection.notConnected())
							|| host.equals(remoteHostAddress)) {
						// if ((server.useProxy && connection.notConnected()) ||
						// !host.equals(remoteHostAddress)) {
						if (server.debug) {
							server.writeLog("connect: " + remoteHostAddress
									+ " -> " + host);
						}
						statuscode = Server2HTTPSession.SC_CONNECTING_TO_HOST;
						remoteHostAddress = host;
					}

				} // end switch
			}// end if(startline)
			else {
				/*-----------------------------------------------
				 * Content-Length parsing
				 *-----------------------------------------------*/
				if (server.startsWith(buf.toUpperCase(), "CONTENT-LENGTH")) {
					String clen = buf.substring(16);
					if (clen.indexOf("\r") != -1)
						clen = clen.substring(0, clen.indexOf("\r"));
					else if (clen.indexOf("\n") != -1)
						clen = clen.substring(0, clen.indexOf("\n"));
					try {
						contentLength = Integer.parseInt(clen);
					} catch (NumberFormatException e) {
						statuscode = Server2HTTPSession.SC_CLIENT_ERROR;
					}
					if (server.debug)
						server.writeLog("read_f: content_len: " + contentLength);
					if (!ssl)
						body = true; // Note: in HTTP/1.1 any method can have a
					// body, not only "POST"
					/*-----------------------------------------------
					 * Proxy-Connection parsing
					 *-----------------------------------------------*/
				} else if (server.startsWith(buf, "Proxy-Connection:")) {
					if (!server.useProxy)
						buf = null;
					else {
						buf = "Proxy-Connection: Keep-Alive\r\n";
						nRead = buf.length();
					}
					/*-----------------------------------------------
					 * X-protocolX parsing
					 *-----------------------------------------------*/
				} else if (server.startsWith(buf, "X-protocolX:")) {
					// look for parameters
					String pa = buf.substring(buf.indexOf(":") + 1,
							buf.indexOf("\r\n"));
					requestFileHash = pa;
					RequestWF = true;
				}
				/*
				 * else if (server.startsWith(buf,"Connection:")) { if
				 * (!server.use_proxy) { buf="Connection: Keep-Alive\r\n"; //use
				 * always keep-alive lread=buf.length(); } else buf=null; }
				 */
				/*
				 * cookie crunch section
				 */
				else if (server.startsWith(buf, "Cookie:")) {
					if (!cookies_enabled)
						buf = null;
				}
			}

			if (buf != null) {
				rq += buf;
				headerLength += nRead;
			}
			nChars = getLine();
			buf = line;
		}

		if (nChars != -1) {
			// adds last line (should be an empty line) to the header
			// String
			if (nChars > 0) {
				rq += buf;
				headerLength += nRead;
			}

			if (headerLength == 0) {
				// server.writeLog("lread: " + lread);
				// server.writeLog("Buf: ####" + buf + "###");
				// server.writeLog("Line: " + line);
				// server.writeLog("rq: #-->" + rq + "<--#");
				// if (server.debug)
				// server.writeLog("header_length=0, setting status to SC_CONNECTION_CLOSED (buggy request)");
				statuscode = Server2HTTPSession.SC_CONNECTION_CLOSED;
			}

			for (int i = 0; i < headerLength; i++)
				this.a[i] = (byte) rq.charAt(i);

			if (body) {// read the body, if "Content-Length" given
				post_data_len = 0;
				if (contentLength + headerLength > a.length) {
					byte b[] = new byte[headerLength + contentLength];
					for (int k = 0; k < headerLength; k++) {
						b[k] = this.a[k];
					}
					this.a = b;
				}

				byte[] r = read2(contentLength);
				for (int u = 0; u < contentLength; u++) {
					this.a[headerLength + u] = r[u];
				}

				// while (post_data_len < contentLength) {
				// this.a[headerLength + post_data_len] = (byte) read(); //
				// writes data
				// into the
				// array
				// post_data_len++;
				// }
				// modified
				headerLength += contentLength; // add the body-length to the
				// header-length
				body = false;
			}
		}
		// cc++;
		// long s2 = System.currentTimeMillis();
		// System.out.println("(" + (s2 - s1) + " " + cc + ")" + url);
		ent = true;
		lindex = 0;
		entr = new ByteArrayOutputStream();
		Entrada = null;
		ent = true;
		// return -1 with an error
		return (statuscode == Server2HTTPSession.SC_OK) ? headerLength : -1;

	}

	@Override
	public int available() {
		int p = -2;
		try {
			p = super.in.available();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			return -1;
		}
		return (statuscode == Server2HTTPSession.SC_CONNECTING_TO_HOST) ? p
				: -1;
	}

	/**
	 * reads a line
	 * 
	 * @exception IOException
	 * @return number of chars in the line
	 */
	public int getLine2() throws IOException {
		int c = 0;
		line = "";
		nRead = 0;
		while (c != '\n') {
			c = read();
			if (c != -1) {
				line += (char) c;
				nRead++;
			} else
				break;
		}
		return nRead;
	}

	byte[] Entrada;
	ByteArrayOutputStream entr = new ByteArrayOutputStream();
	boolean ent = true;

	public void leetodo() throws IOException {
		// System.out.println("->>" + super.available() + " ");
		// if (super.available() > 0) {
		int p = 0;
		byte[] buffin = new byte[65535];
		// while (true) {
		// System.out.print("->>" + super.available() + " ");
		p = super.read(buffin);
		// System.out.println(p + "<<-");
		if (p != -1) {
			entr.write(buffin, 0, p);
			if (buffin[p - 1] == 10 && buffin[p - 2] == 13
					&& buffin[p - 3] == 10 && buffin[p - 4] == 13) {
				// System.out.print(buffin[p - 1] + " " + buffin[p - 2] +
				// " "
				// + buffin[p - 3] + " " + buffin[p - 4] + " "
				// + buffin[p - 5]);
				// break;
				if (super.available() > 0) {
					ent = true;
				} else {
					ent = false;
				}
			}
		} else {
			ent = false;
			// break;
		}
		// }
		ent = false;
		Entrada = new byte[entr.size()];
		Entrada = entr.toByteArray();
		// } else {
		// ent = false;
		// }
	}

	int lindex = 0;

	public int getLine() throws IOException {
		if (ent) {
			leetodo();
		}
		int c;
		line = "";
		nRead = 0;
		for (int i = lindex; i < Entrada.length; i++) {
			c = Entrada[i];
			if (c != '\n') {
				line += (char) c;
				nRead++;
			} else {
				line += (char) c;
				nRead++;
				lindex = i + 1;
				break;
			}
		}
		// System.out.println(ent + " " + line.length() + " " + line);
		return line.length();
	}

	public byte[] read2(int l) throws IOException {
		byte[] k = new byte[l];
		if (l <= Entrada.length) {
			for (int t = 0; t < l; t++) {
				k[t] = Entrada[lindex + t];
			}
			lindex += l;
		} else {
			leetodo();
			return read2(l);
		}
		return k;
	}

	/**
	 * Parser for the first line from the HTTP request. Sets up the URL, method
	 * and remote host name.
	 * 
	 * @return an InetAddress for the host name, null on errors with a
	 *         statuscode!=SC_OK
	 */
	public InetAddress parseRequest(String a, int method_index) {

		int pos;
		int ipv6bracket;

		String f = "";
		String r_host_name = "";
		String r_port = "";

		url = "";

		if (ssl) {
			// remove CONNECT
			f = a.substring(8);
		} else {

			method = a.substring(0, a.indexOf(" ")); // first word in the line
			pos = a.indexOf(":"); // locate first ":"
			if (pos == -1) {
				// Occurs with "GET / HTTP/1.1"
				// This is not a proxy request
				url = a.substring(a.indexOf(" ") + 1, a.lastIndexOf(" "));
				// System.out.println("url: "+url+" statuscode "+statuscode+" \r\n");

				// Strip white spaces
				f = a.replace("\r", "").replace("\n", "");

				int versionp = f.indexOf("HTTP/");
				String HTTPversionRaw;

				// length of "HTTP/x.x": 8 chars
				if (versionp == (f.length() - 8)) {
					// Detect the HTTP version
					HTTPversionRaw = f.substring(versionp + 5);
					if (HTTPversionRaw.equals("1.1"))
						HTTPversion = 1;
					else if (HTTPversionRaw.equals("1.0"))
						HTTPversion = 0;

					// remove " HTTP/x.x"
					f = f.substring(0, versionp - 1);
					if (server.debug)
						server.writeLog("-->" + f + "<--");
				} else {
					// bad request: no "HTTP/xxx" at the end of the line
					HTTPversionRaw = "";
				}
				remotePort = server.TargetPort;
				// remoteHostAddress = connection.serveraddress;
				// remoteHostName = remoteHostAddress.getHostName();
				remoteHostName = server.TargetHost;
				// Resolve host name
				try {
					remoteHostAddress = InetAddress.getByName(remoteHostName);

				} catch (UnknownHostException e_u_host) {
					if (!server.useProxy)
						statuscode = Server2HTTPSession.SC_HOST_NOT_FOUND;
				}
				return remoteHostAddress;
			}
			// Proxy request
			f = a.substring(pos + 3); // removes "http://"
		}
		// Strip white spaces
		f = f.replace("\r", "").replace("\n", "");

		int versionp = f.indexOf("HTTP/");
		String HTTPversionRaw;

		// length of "HTTP/x.x": 8 chars
		if (versionp == (f.length() - 8)) {
			// Detect the HTTP version
			HTTPversionRaw = f.substring(versionp + 5);
			if (HTTPversionRaw.equals("1.1"))
				HTTPversion = 1;
			else if (HTTPversionRaw.equals("1.0"))
				HTTPversion = 0;

			// remove " HTTP/x.x"
			f = f.substring(0, versionp - 1);
			if (server.debug)
				server.writeLog("-->" + f + "<--");
		} else {
			// bad request: no "HTTP/xxx" at the end of the line
			HTTPversionRaw = "";
		}

		/*
		 * pos = f.indexOf(" "); // locate space, should be the space before //
		 * "HTTP/1.1" if (pos == -1) { // buggy request statuscode =
		 * ServerHTTPSession.SC_CLIENT_ERROR; errordescription =
		 * "Invalid request: \"" + a + "\""; return null; } f = f.substring(0,
		 * pos); // removes all after space
		 */

		pos = f.indexOf("/"); // locate the first slash
		if (pos != -1) {
			url = f.substring(pos); // saves path without host name
			r_host_name = f.substring(0, pos); // reduce string to the host name
		} else {
			url = "/";
			r_host_name = f;
		}

		if (server.debug)
			server.writeLog("#->" + url);

		// search for bracket in host name (IPv6, RFC 2732)
		ipv6bracket = r_host_name.indexOf("[");
		if (ipv6bracket == 0) {
			r_host_name = r_host_name.substring(1);
			ipv6bracket = r_host_name.indexOf("]");
			r_port = r_host_name.substring(ipv6bracket + 1);
			r_host_name = r_host_name.substring(0, ipv6bracket);

			if (server.debug)
				server.writeLog("ipv6 bracket ->" + r_host_name + "<--");

			// URL with brackets, must be IPv6 address
			ipv6reference = true;

			// detect the remote port number, if any
			pos = r_port.indexOf(":");
			if (pos != -1) {
				r_port = r_port.substring(pos + 1);
			} else {
				r_port = null;
			}

		} else {
			// no IPv6 reference with brackets according to RFC 2732
			ipv6reference = false;
			pos = r_host_name.indexOf(":");
			if (pos != -1) {
				r_port = r_host_name.substring(pos + 1);
				r_host_name = r_host_name.substring(0, pos);
				r_port = server.TargetPort + "";
			} else
				r_port = null;
		}

		// Port number: parse String and convert to integer
		if (r_port != null && !r_port.equals("")) {
			try {
				remotePort = Integer.parseInt(r_port);
			} catch (NumberFormatException e_get_host) {
				if (server.debug)
					server.writeLog("get_Host :" + e_get_host
							+ " Failed to parse remote port numer!");
				remotePort = 80;
			}
		} else
			remotePort = 80;

		if (server.debug)
			server.writeLog(method + " " + url + " " + HTTPversionRaw);

		remoteHostName = server.TargetHost;
		InetAddress address = null;

		// if (server.log_access)
		// server.logAccess(connection.getLocalSocket().getInetAddress()
		// .getHostAddress()
		// + " " + method + " " + getFullUrl());

		// Resolve host name
		try {
			address = InetAddress.getByName(remoteHostName);

		} catch (UnknownHostException e_u_host) {
			if (!server.useProxy)
				statuscode = Server2HTTPSession.SC_HOST_NOT_FOUND;
		}

		return address;
	}

	/**
	 * @return boolean whether the current connection was established with the
	 *         CONNECT method.
	 */
	public boolean isTunnel() {
		return ssl;
	}

	/**
	 * @return the full qualified URL of the actual request.
	 */
	public String getFullUrl() {
		return "http"
				+ (ssl ? "s" : "")
				+ "://"
				+ (ipv6reference ? "[" + getRemoteHostName() + "]"
						: getRemoteHostName())
				+ (remotePort != 80 ? (":" + remotePort) : "") + url;
	}

	/**
	 * @return the URL in a MD5 String
	 */
	public String getURLH() {
		String s = null;
		try {
			// MessageDigest md = MessageDigest.getInstance("SHA-256");
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			md.update(url.getBytes());
			URLH = md.digest();
			BigInteger bigInt = new BigInteger(1, URLH);
			s = bigInt.toString(16);
			while (s.length() < 32) {
				s = "0" + s;
			}

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return s;
	}

	/**
	 * @return status-code for the current request
	 */
	public int getStatusCode() {
		return statuscode;
	}

	/**
	 * @return the (optional) error description for this request
	 */
	public String getErrorDescription() {
		return errordescription;
	}
}
