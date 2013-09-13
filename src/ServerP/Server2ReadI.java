package ServerP;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import ServerP.diff_match_patch.Patch;

public class Server2ReadI extends Thread {
	private int Changes = -1;
	private final int BUFFER_SIZE = 65535;
	private BufferedInputStream in;
	private BufferedOutputStream out;
	private Server2HTTPSession connection;
	private Server2Server server;
	private String line = "";
	private int nRead;
	private String answerHASH = "";
	private boolean found = false;
	private String Encoding = "0";
	private int CompressedSize = 0;
	private String HostName = "";
	private String uri = "";
	private int RealSize = -1;
	private boolean RealS = false;
	private boolean requestWF = false;
	private String requestFileHash = "-1";
	private String Header = "";
	private String StatusCode = "";
	private String line2 = "";
	private String TransferEncoding = "";
	private String Type = "";
	private final float DiffTimeOut;
	private final short DiffEditCost;
	// private String Charset="ISO-8859-1";
	private final String Charset = "UTF-8";
	private boolean ContentRange = false;
	private boolean Comprobar = false;

	public Server2ReadI(Server2Server server, Server2HTTPSession connection,
			BufferedInputStream l_in, BufferedOutputStream l_out,
			Server2ClientInputStream in2) {
		in = l_in;
		out = l_out;
		this.connection = connection;
		this.server = server;
		setPriority(Thread.MIN_PRIORITY);
		requestWF = in2.RequestWF;
		HostName = in2.getRemoteHostName();
		uri = in2.url;
		requestFileHash = in2.requestFileHash;
		DiffTimeOut = server.difftimeout;
		DiffEditCost = server.diffeditcost;
		start();
	}

	@Override
	public void run() {
		read();

		server = null;
		connection = null;
		in = null;
		out = null;

	}

	byte[] Entrada = null;
	ByteArrayOutputStream entr = new ByteArrayOutputStream();
	boolean ent = true;

	public int leetodo() throws IOException {
		int p = 0;
		byte[] buffin = new byte[65535];
		p = in.read(buffin);
		if (p != -1) {
			if (p > 0) {
				entr.write(buffin, 0, p);
				if (p > 3) {
					if (buffin[p - 1] == 10 && buffin[p - 2] == 13
							&& buffin[p - 3] == 10 && buffin[p - 4] == 13) {
						cab = false;
					}
				}
			}
		} else {
			// System.out.print("nada");
			ent = false;
			// return -1;
		}
		// ent = false;
		if (p > 0) {
			Entrada = new byte[entr.size()];
			Entrada = entr.toByteArray();
		}
		return p;
	}

	int lindex = 0;
	boolean cab = true;

	public int getLine() throws IOException {

		boolean e = false;
		int c;
		line = "";
		nRead = 0;
		if (Entrada != null) {
			for (int i = lindex; i < Entrada.length; i++) {
				c = Entrada[i];
				if (c != '\n') {
					line += (char) c;
					nRead++;
				} else {
					line += (char) c;
					nRead++;
					lindex = i + 1;
					e = true;
					break;
				}
			}
		}
		// System.out.println("e:" + !e + " " + ent + " " + cab + " "
		// + in.available() + " " + line.length());
		if (!e && (ent || cab)) {
			int pp = leetodo();
			if (pp == -1) {
				line = "";
				return -1;
			} else if (pp == 0) {
				ent = true;
			}
			return getLine();
		}
		// System.out.println(ent + " " + line.length() + " " + line);
		// System.out.print(">" + line.length() + " " + line + "<");
		return line.length();
	}

	public byte[] restt() {
		if (Entrada == null || Entrada.length - lindex <= 0) {
			return null;
		}
		byte[] k = new byte[Entrada.length - lindex];
		for (int t = 0; t < Entrada.length - lindex; t++) {
			k[t] = Entrada[lindex + t];
		}
		lindex += Entrada.length - lindex;
		Entrada = null;
		lindex = 0;
		entr = new ByteArrayOutputStream();
		ent = true;
		cab = true;
		return k;
	}

	public byte[] restt(int size) {
		if (Entrada == null || Entrada.length - lindex <= 0) {
			return null;
		}
		int toout = 0;
		if (Entrada.length - lindex >= size) {
			toout = size;
		} else {
			toout = Entrada.length - lindex;
		}
		byte[] k = new byte[toout];
		for (int t = 0; t < toout; t++) {
			k[t] = Entrada[lindex + t];
		}
		lindex += toout;
		return k;
	}

	public int getLine2() throws IOException {
		int c = 0;
		line = "";
		nRead = 0;
		while (c != '\n') {
			c = in.read();
			if (c != -1) {
				line += (char) c;
				nRead++;
			} else
				break;
		}
		return nRead;
	}

	public int ChunkSize() throws IOException {
		if (Entrada == null || Entrada.length - lindex <= 0) {
			return ChunkSize2();
		} else {
			int c = 0;
			line = "";
			while (c != '\n') {
				if (Entrada.length > lindex) {
					c = Entrada[lindex];
					if (c != -1) {
						line += (char) c;
						lindex++;
					} else {
						break;
					}
				} else {
					break;
				}
			}
			line2 = line.trim();
			if (line2.equals("")) {
				return ChunkSize();
			} else {
				return Integer.parseInt(line2, 16);
			}
		}
	}

	public int ChunkSize2() throws IOException {
		int c = 0;
		line = "";
		nRead = 0;
		while (c != '\n') {
			c = in.read();
			if (c != -1) {
				line += (char) c;
				nRead++;
			} else
				break;
		}

		line2 = line.trim();
		if (line2.equals("")) {
			return ChunkSize();
		} else {
			// System.out.println("-"+Integer.parseInt(line2, 16)+"-");
			return Integer.parseInt(line2, 16);
		}
	}

	private void read() {
		int nChars = 0;
		// long end = 0;
		// long start = 0;
		//
		try {
			nChars = getLine();
		} catch (IOException e1) {
		}
		try {
			// looking for the code.
			cab = true;
			while (nChars != -1 && nChars > 2) {
				if (line.toUpperCase().contains("HTTP/1.")) {
					int ind = line.indexOf(" ");
					StatusCode = line.substring(ind + 1, ind + 4);
					sendLine(nChars);
				} else if (line.toUpperCase().contains("CONTENT-LENGTH")) {
					sendLine(nChars);
					RealSize = Integer.parseInt(line.substring(
							line.indexOf(" ") + 1, line.indexOf("\r\n")));
					if (!(RealSize == -1) && !(RealSize == 0)) {
						RealS = true;
					}
				} else if (line.toUpperCase().contains("TRANSFER-ENCODING")) {
					TransferEncoding = line.substring(line.indexOf(" ") + 1,
							line.indexOf("\r\n"));
					if (TransferEncoding.toUpperCase().equals("CHUNKED")) {
						if (requestFileHash.equals("0")
								|| !(new File(HostName + "/" + requestFileHash)
										.exists()) || Encoding.equals("gzip")) {
							sendLine(nChars);
							// System.out.println("chunked");
						}
					}
				} else if (line.toUpperCase().contains("CONTENT-ENCODING")) {
					Encoding = line.substring(line.indexOf(" ") + 1,
							line.indexOf("\r\n"));
					sendLine(nChars);

				} else if (line.toUpperCase().contains("CONTENT-RANGE")) {
					ContentRange = true;
					sendLine(nChars);

				} else if (line.toUpperCase().contains("CONTENT-TYPE")) {
					Type = line.substring(line.indexOf(" ") + 1,
							line.indexOf("\r\n"));
					sendLine(nChars);
				} else {
					// normal headers
					sendLine(nChars);
				}
				nChars = getLine();
			}

			cab = false;
			//
			// System.out.print("\r\n" + requestFileHash + " "
			// + StatusCode.equals("200") + " "
			// + (!Encoding.equals("gzip")) + " " + (!ContentRange) + " "
			// + (!Type.toUpperCase().contains("IMAGE")));
			if (requestWF && StatusCode.equals("200")
					&& (!Encoding.equals("gzip")) && (!ContentRange)
					&& (!Type.toUpperCase().contains("IMAGE"))) {
				// if(requestWF && StatusCode.equals("200") &&
				// (!Encoding.equals("gzip"))){
				// (!Type.toUpperCase().contains("IMAGE"))
				// look for file already there
				if (requestFileHash.equals("0")
						|| !(new File(HostName + "/" + requestFileHash)
								.exists())) {
					Changes = 1;
					addParameters(new String("X-protocolX:" + "0-" + RealSize
							+ "," + "0" + "," + RealSize + "," + Changes
							+ "\r\n\r\n"));
					byte[] gdata = SendDataDirect2();
					answerHASH = GetHash(gdata) + "-" + gdata.length;
					SaveCache(false, gdata, gdata.length);
					// System.out.println(uri + " direct");
					// System.out.println(uri + "->" + answerHASH + "," +
					// Encoding
					// + "," + gdata.length + "," + Changes);
					Encoding = "";
					if (Comprobar) {
						line = "";
						answerHASH = "";
						found = false;
						Encoding = "0";
						CompressedSize = 0;
						HostName = "";
						uri = "";
						RealSize = -1;
						RealS = false;
						requestWF = false;
						requestFileHash = "-2";
						Header = "";
						StatusCode = "";
						line2 = "";
						TransferEncoding = "";
						Type = "";
						ContentRange = false;
						Comprobar = false;
						read();
					}
				} else {

					byte[] gdata = getData();
					// start = System.currentTimeMillis();
					byte[] rdata = null;
					byte[] senddata = null;
					// String Logger = "";
					RealSize = gdata.length;
					answerHASH = GetHash(gdata) + "-" + RealSize;

					if (CompareHash()) {
						// the client has the same, no changes
						Changes = 0;
						senddata = (new String("")).getBytes(Charset);
					} else {
						byte[] ldata = null;
						ldata = loadDatafromCache();
						if (!found) {
							// if no file found
							// rebuild
							rdata = gdata;
							Changes = 1;
							// save hash and data with size and more info
						} else {
							Changes = 2;
							// compare data if isn't a image;
							if (!Type.contains("image")) {
								String ans = new String(gdata, Charset);
								String loa = new String(ldata, Charset);

								diff_match_patch comparator = new diff_match_patch();
								// last
								comparator.Diff_Timeout = DiffTimeOut;
								// comparator.Diff_Timeout = (float)0.001;

								String patch_text = "";
								LinkedList list = comparator
										.diff_main(loa, ans);
								LinkedList<Patch> paths;

								// comparator.Diff_EditCost =
								// (short)(Math.pow(2, 6)-1);
								comparator.Diff_EditCost = DiffEditCost;
								comparator.diff_cleanupEfficiency(list);
								paths = comparator.patch_make(loa, list);
								patch_text = comparator.patch_toText(paths);
								senddata = compressData(patch_text
										.getBytes(Charset));
								// fin last

								if (senddata.length < RealSize) {
									Encoding = "gzip";
								} else {
									// System.out.println(patch_text.length()+" "+senddata.length+" old "+this.requestFileHash+" ans "+this.answerHASH);
									Changes = 1;
									rdata = gdata;
								}

							}// if image
							else {
								rdata = gdata;
								Changes = 1;
								// Logger = "-1\t-1\t-1\t";
							}
						}// else no found
					}

					if (Changes == 1) {
						senddata = compressData(rdata);
						if (senddata.length < RealSize) {
							Encoding = "gzip";
						} else {
							Encoding = "0";
							senddata = rdata;
						}
					}
					// end = System.currentTimeMillis();
					addParameters(new String("X-protocolX:" + answerHASH + ","
							+ Encoding + "," + senddata.length + "," + Changes
							+ "\r\n\r\n"));
					// addParameters(new String("\r\n\r\n"));
					SendToBrowser(senddata, senddata.length);
					if (Changes > 0) {
						SaveCache(false, gdata, gdata.length);
					}
					// System.out.print("["+Header+"]\r\n");
					// System.out.println(uri + "->" + answerHASH + "," +
					// Encoding
					// + "," + senddata.length + "," + Changes);
					// Logger = Changes + "\t" + RealSize + "\t" +
					// senddata.length
					// + "\t" + (end - start) + "\n";
					// log(Logger, "nlog.txt");
					Encoding = "";
					if (Comprobar) {
						line = "";
						answerHASH = "";
						found = false;
						Encoding = "0";
						CompressedSize = 0;
						HostName = "";
						uri = "";
						RealSize = -1;
						RealS = false;
						requestWF = false;
						requestFileHash = "-3";
						Header = "";
						StatusCode = "";
						line2 = "";
						TransferEncoding = "";
						Type = "";
						ContentRange = false;
						Comprobar = false;
						read();
					}
				}
			} else {
				// no format
				sendLine(nChars);
				// send answer to client without changes
				SendDataDirect();
			}

		} catch (IOException e) {
			// System.out.print(e);
		}

		try {
			// System.out.println("end "+connection.getStatus()+" - "+Server2HTTPSession.SC_CONNECTING_TO_HOST+" "+uri);
			if (connection.getStatus() != Server2HTTPSession.SC_CONNECTING_TO_HOST) {
				connection.getLocalSocket().close();
			}
			// If we are connecting to a new host (and this thread is
			// already running!) , the upstream
			// socket will be closed. So we get here and close our own
			// downstream socket..... and the browser
			// displays an empty page because Server
			// closes the connection..... so close the downstream socket only
			// when NOT connecting to a new host....
		} catch (IOException e_socket_close) {
		}
	}

	private void log(String l, String tfile) throws IOException {
		try {
			FileWriter fstream = new FileWriter(HostName + "/" + tfile, true);
			fstream.write(l);
			fstream.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private byte[] getData() throws IOException {
		byte[] gdata = null;
		if (TransferEncoding.toUpperCase().equals("CHUNKED")) {
			int cs = ChunkSize();
			String a = "";
			while (cs != 0) {
				RealS = true;
				gdata = ReceiveData(cs);
				a = a + new String(gdata, 0, cs, Charset);
				cs = ChunkSize();
			}
			gdata = ReceiveData(-1);
			a = a + new String(gdata, Charset) + "\r\n";
			gdata = a.getBytes(Charset);
		} else if (RealS) {
			gdata = ReceiveData(RealSize);
		} else {
			gdata = ReceiveData(-1);
		}

		/*
		 * if(Encoding.equals("gzip")){ byte[] a = UncompressData(gdata);
		 * System.out.print(RealSize+" "+a.length); RealSize = a.length; return
		 * a; }
		 */
		return gdata;
	}

	private boolean CompareHash() {
		if (answerHASH.equals(requestFileHash)) {
			return true;
		} else {
			return false;
		}
	}

	private void addParameters(String into) throws IOException {
		Header = Header + into;
		byte[] addedb = into.getBytes(Charset);
		SendToBrowser(addedb, addedb.length);
	}

	private String GetHash(byte[] r) {
		String s = null;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			md.update(r);
			// s = Base64.encodeToString(md.digest(), true);
			BigInteger bigInt = new BigInteger(1, md.digest());
			s = bigInt.toString(16);
			while (s.length() < 32) {
				s = "0" + s;
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return s;
	}

	private void sendLine(int nChars) throws IOException {
		byte[] a = line.getBytes();
		SendToBrowser(a, a.length);
		Header = Header + line;
	}

	private void SendToBrowser(byte[] ucdata, int size) throws IOException {
		out.write(ucdata, 0, size);
		out.flush();
		// server.addBytesRead(size);
	}

	private byte[] loadDatafromCache() throws IOException {
		return open(HostName + "/" + this.requestFileHash);
	}

	private void SaveCache2(boolean bo, byte[] sdata, int size) {
		// create directory
		boolean dir = (new File(HostName)).mkdir();
		try {
			save(HostName + "/" + answerHASH, new String(sdata, 0, size,
					Charset));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void save(String fFileName, String text) {
		try {
			FileWriter fstream = new FileWriter(fFileName);
			BufferedWriter out = new BufferedWriter(fstream);
			out.write(text);
			// Close the output stream
			out.close();
		} catch (Exception e) {// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}

	private void SaveCache(boolean bo, byte[] sdata, int size)
			throws IOException {
		FileOutputStream out = null;
		boolean dir = (new File(HostName)).mkdir();
		try {
			out = new FileOutputStream(HostName + "/" + answerHASH);
			out.write(sdata, 0, size);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
			// delete(HostName+"/"+requestFileHash);
		}
	}

	private void delete(String file) {
		File f1 = new File(file);
		boolean success = f1.delete();
		if (!success) {
			// System.out.println("Deletion failed.");
		} else {
			// System.out.println("File deleted.");
		}

	}

	private byte[] open(String file) throws IOException {
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);

			int Size = -1;
			String[] a = requestFileHash.split("-");
			if (!(a[1].equals(""))) {
				Size = Integer.parseInt(a[1]);
			}
			if (Size != -1) {
				byte[] loaded = new byte[Size];
				in.read(loaded, 0, Size);
				found = true;
				return loaded;
			} else {
				int c;
				byte[] buff = new byte[BUFFER_SIZE * 100];
				c = in.read(buff);
				byte[] loaded = new byte[c];
				for (int i = 0; i < c; i++) {
					loaded[i] = buff[i];
				}
				found = true;
				return loaded;
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			found = false;
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			found = false;
			e.printStackTrace();
		} finally {
			if (in != null) {
				in.close();
			}
		}
		return null;
	}

	private byte[] open2(String file) {
		int Size = -1;
		String[] a = requestFileHash.split("-");
		if (!(a[1].equals(""))) {
			Size = Integer.parseInt(a[1]);
		}
		if (Size != -1) {
			byte[] bytes = new byte[Size];
			char[] opened = new char[Size];
			int r = 0;
			try {
				FileReader fr = new FileReader(file);
				BufferedReader inr = new BufferedReader(fr);
				r = inr.read(opened);
			} catch (FileNotFoundException e) {
				found = false;
				// e.printStackTrace();
			} catch (IOException e) {
				found = false;
				// e.printStackTrace();
			}
			if (r == (Size)) {
				found = true;
				try {
					bytes = (new String(opened)).getBytes(Charset);
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return bytes;
		} else {
			byte[] bytes = null;
			char[] opened = new char[BUFFER_SIZE];
			String get = "";
			int br = 0;
			int r = 0;
			try {
				FileReader fr = new FileReader(file);
				BufferedReader inr = new BufferedReader(fr);
				while (r != -1) {
					r = inr.read(opened);
					if (r != -1) {
						get = get + new String(opened);
						br += r;
					} else {
						break;
					}
				}

			} catch (FileNotFoundException e) {
				found = false;
				e.printStackTrace();
			} catch (IOException e) {
				found = false;
				e.printStackTrace();
			}
			try {
				bytes = get.getBytes(Charset);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			found = true;
			return bytes;
		}
	}

	public byte[] compressData(byte[] tocomp) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream(tocomp.length);
		GZIPOutputStream gos = new GZIPOutputStream(os);
		gos.write(tocomp);
		gos.close();
		byte[] compressed = os.toByteArray();
		os.close();
		// byte[] compressed =
		// org.apache.commons.codec.binary.Base64.decodeBase64(compressed1);
		// byte[] compressed =
		// org.apache.commons.codec.binary.Base64.encodeBase64(compressed1);
		return compressed;
	}

	public byte[] compressData2(byte[] tocomp) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			OutputStream out = new DeflaterOutputStream(baos);
			out.write(tocomp);
			out.flush();
			out.close();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		return baos.toByteArray();
	}

	private byte[] UncompressData(byte[] touncomp) throws IOException {
		ByteArrayInputStream is = new ByteArrayInputStream(touncomp);
		GZIPInputStream gis = new GZIPInputStream(is, BUFFER_SIZE);
		StringBuilder string = new StringBuilder();
		byte[] data = new byte[BUFFER_SIZE];
		int bytesRead;
		while ((bytesRead = gis.read(data)) != -1) {
			string.append(new String(data, 0, bytesRead, Charset));
		}
		gis.close();
		is.close();
		return string.toString().getBytes(Charset);
	}

	private byte[] ReceiveData(int bsize2) throws IOException {
		byte[] rr = null;
		int br = 0;
		if (bsize2 > 0) {
			int b = 0;
			rr = new byte[bsize2];
			// <-
			byte[] aux = restt(bsize2);
			if (aux != null) {
				for (int a = 0; a < aux.length; a++) {
					rr[br + a] = aux[a];
				}
				br += aux.length;
			}
			// <-
			while (br < bsize2) {
				b = in.read(rr, br, bsize2 - br);
				br = br + b;
			}
			// System.out.print("(1) ");
			Comprobar = false;
		} else {
			return blind();
		}
		if (br == 0) {
			// System.out.println(Header);
		}
		return rr;
	}

	private byte[] blind() throws IOException {
		int br = 0;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int r = 0;
		// rr = new byte[size];
		byte[] buf = new byte[BUFFER_SIZE];
		// <-
		byte[] aux = restt();
		if (aux != null) {
			buffer.write(aux, 0, aux.length);
			br += aux.length;
		}
		// <-
		while (r != -1 && in.available() > 0) {
			r = in.read(buf);
			if (r != -1) {
				buffer.write(buf, 0, r);
				br += r;
			} else {
				break;
			}

		}
		RealSize = br;
		byte[] kk = buffer.toByteArray();
		Comprobar = false;
		return kk;
	}

	private void SendDataDirect() throws IOException {
		int bytes_read = 0;
		byte[] buf = new byte[BUFFER_SIZE];
		// System.out.print("(3) ");
		// <-
		byte[] aux = restt();
		if (aux != null) {
			out.write(aux, 0, aux.length);
			out.flush();
		}
		// <-
		while (in.available() > 0) {
			bytes_read = in.read(buf);
			if (bytes_read != -1) {
				out.write(buf, 0, bytes_read);
				out.flush();
				// server.addBytesRead(bytes_read);
			} else
				break;
		}
	}

	private byte[] SendDataDirect2() throws IOException {
		int bytes_read = 0;
		byte[] buf = new byte[BUFFER_SIZE];
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		// <-
		byte[] aux = restt();
		if (aux != null) {
			out.write(aux, 0, aux.length);
			out.flush();
			buffer.write(aux, 0, aux.length);
		}
		// <-
		while (in.available() > 0) {
			bytes_read = in.read(buf);
			if (bytes_read != -1) {
				out.write(buf, 0, bytes_read);
				out.flush();
				// server.addBytesRead(bytes_read);
				buffer.write(buf, 0, bytes_read);
			} else
				break;
		}

		byte[] kk = new byte[buffer.size()];
		kk = buffer.toByteArray();
		return kk;
	}

	/**
	 * stop the thread by closing the socket
	 */
	public void close() {
		try {
			in.close();
		} catch (Exception e) {
		}
	}
}
