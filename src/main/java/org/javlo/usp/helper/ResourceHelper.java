/*
 * Created on 27-dec.-2003O
 */
package org.javlo.usp.helper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

public class ResourceHelper {

	public static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

	/**
	 * create a static logger.
	 */
	protected static java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ResourceHelper.class.getName());

	public static final String getFileExtensionToMineType(String ext) {
		ext = ext.trim().toLowerCase();
		if (ext.equals("gif")) {
			return "image/GIF";
		} else if (ext.equals("png")) {
			return "image/PNG";
		} else if (ext.equals("webp")) {
			return "image/webp";
		} else if (ext.equals("xml")) {
			return "application/xml";
		} else if (ext.equals("ico")) {
			return "image/x-icon";
		} else if ((ext.equals("jpg")) || (ext.equals("jpeg"))) {
			return "image/JPEG";
		} else if (ext.equals("mpg") || ext.equals("mpeg") || ext.equals("mpe")) {
			return "video/mpeg";
		} else if (ext.equals("svg")) {
			return "image/svg+xml";
		} else if (ext.equals("mp3")) {
			return "audio/mpeg";
		} else if (ext.equals("mp4")) {
			return "video/mp4";
		} else if (ext.equals("m4a")) {
			return "audio/mp4";
		} else if (ext.equals("avi") || ext.equals("wmv")) {
			return "video/msvideo";
		} else if (ext.equals("qt") || ext.equals("mov")) {
			return "video/quicktime";
		} else if (ext.equals("ogg") || ext.equals("ogv")) {
			return "video/ogg";
		} else if (ext.equals("aif") || ext.equals("aiff") || ext.equals("aifc")) {
			return "audio/x-aiff";
		} else if (ext.equals("webm")) {
			return "video/webm";
		} else if (ext.equals("qt") || ext.equals("mov")) {
			return "video/quicktime";
		} else if (ext.equals("pdf")) {
			return "application/pdf";
		} else if (ext.equals("js")) {
			return "application/javascript";
		} else if (ext.equals("css")) {
			return "text/css";
		} else if (ext.equals("csv")) {
			return "text/csv";
		} else if (ext.equals("html")) {
			return "text/html";
		} else if (ext.equals("html")) {
			return "text/html";
		} else if (ext.equals("swf")) {
			return "application/x-shockwave-flash";
		} else if (ext.equals("zip")) {
			return "application/zip";
		} else if (ext.equals("properties")) {
			return "text/text";
		} else if (ext.equals("xls")) {
			return "application/vnd.ms-excel";
		} else if (ext.equals("ppt")) {
			return "application/vnd.ms-powerpoint";
		} else if (ext.equals("xlsx")) {
			return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		} else if (ext.equals("doc")) {
			return "application/msword";
		} else if (ext.equals("xlsx")) {
			return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		} else if (ext.equals("odp")) {
			return "application/vnd.oasis.opendocument.presentation";
		} else if (ext.equals("odt")) {
			return "application/vnd.oasis.opendocument.text";
		} else if (ext.equals("ods")) {
			return "application/vnd.oasis.opendocument.spreadsheet";
		} else if (ext.equals("ics")) {
			return "text/calendar";
		} else if (ext.equals("json")) {
			return "application/json";
		} else if (ext.equals("epub")) {
			return "application/epub+zip";
		}
		return "application/octet-stream";
	}

	/**
	 * Close streams, writers, readers, etc without any exception even if they are
	 * <code>null</code>.
	 * 
	 * @param closeables
	 *            the objects to close
	 */
	public static void safeClose(Closeable... closeables) {
		for (Closeable closeable : closeables) {
			if (closeable != null) {
				try {
					closeable.close();
				} catch (Exception ignored) {
				}
			}
		}
	}

	public static final int writeStreamToFile(InputStream in, File file) throws IOException {
		return writeStreamToFile(in, file, Long.MAX_VALUE);
	}

	public static final int writeStreamToFile(InputStream in, File file, long maxSize) throws IOException {
		int countByte = 0;
		if (file.getParentFile() != null && !file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}

		if (!file.exists()) {
			file.createNewFile();
		}
		OutputStream out = null;
		try {
			out = new FileOutputStream(file);
			countByte = writeStreamToStream(in, out, maxSize);
			if (countByte < 0) {
				safeClose(out);
				file.delete();
				return -1;
			}
		} finally {
			safeClose(out);
		}

		return countByte;
	}
	
	public static final int writeReaderToFile(Reader in, File file) throws IOException {
		int countByte = 0;
		if (file.getParentFile() != null && !file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}

		if (!file.exists()) {
			file.createNewFile();
		}
		OutputStream out = null;
		try {
			out = new FileOutputStream(file);
			int read = in.read();
			while (read>=0) {
				out.write(read);
				read = in.read();
			}
		} finally {
			safeClose(out);
		}

		return countByte;
	}
	
	public static InputStream getBodyStream(HttpServletRequest request) throws IOException {
		ByteArrayOutputStream tempOut = new ByteArrayOutputStream();
		writeStreamToStream(request.getInputStream(), tempOut);
		BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(tempOut.toByteArray())));
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(outStream);
		String line = reader.readLine();
		System.out.println(">>>>>>>>> ResourceHelper.getBodyStream : line  = "+line); //TODO: remove debug trace
		while (line.length()>0) {
			System.out.println(">>>>>>>>> ResourceHelper.getBodyStream : line = "+line); //TODO: remove debug trace
			line = reader.readLine();
			out.println(line);
		}
		out.close();
		String headerText = new String(outStream.toByteArray());
		InputStream in = new ByteArrayInputStream(tempOut.toByteArray());
		System.out.println(">>>>>>>>> ResourceHelper.getBodyStream : headerText.getBytes().length = "+headerText.getBytes().length); //TODO: remove debug trace
		in.skip(headerText.getBytes().length);
		return in;
	}

	public static final String writeStreamToString(InputStream in, String encoding) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeStreamToStream(in, out);
		return new String(out.toByteArray(), Charset.forName(encoding));
	}

	public static final int writeFileToFile(File fileIn, File file) throws IOException {
		InputStream in = null;
		try {
			in = new FileInputStream(fileIn);
			return writeStreamToFile(in, file);
		} finally {
			safeClose(in);
		}
	}

	public static final int writeFileToStream(File fileIn, OutputStream out) throws IOException {
		InputStream in = null;
		try {
			in = new FileInputStream(fileIn);
			return writeStreamToStream(in, out);
		} finally {
			safeClose(in);
		}
	}

	public static final int writeStreamToStream(InputStream in, OutputStream out) throws IOException {
		return writeStreamToStream(in, out, Long.MAX_VALUE);
	}

	/**
	 * write a InputStream in a OuputStream, without close.
	 * 
	 * @return the size of transfered data in byte.
	 */
	public static final int writeStreamToStream(InputStream in, OutputStream out, long maxSize) throws IOException {
		final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		int size = 0;
		int byteReaded = in.read(buffer);
		while (byteReaded >= 0) {
			size = size + byteReaded;
			if (size > maxSize && maxSize > 0) {
				return -1;
			}
			out.write(buffer, 0, byteReaded);
			byteReaded = in.read(buffer);
		}
		return size;
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println(">>>>>>>>> ResourceHelper.main : START"); //TODO: remove debug trace
		long start = System.currentTimeMillis();
		File file = new File("c:/trans/ULB offre technique.pdf");
		String sha;
		try (InputStream in = new FileInputStream(file)) {
			sha = sha512(in);
		}
		System.out.println(">>>>>>>>> ResourceHelper.main : sha = "+sha); //TODO: remove debug trace
		System.out.println(">>>>>>>>> ResourceHelper.main : time = "+((System.currentTimeMillis()-start))+" msec."); //TODO: remove debug trace
	}
	
	public static String sha512(final InputStream in) throws IOException, IllegalArgumentException {
		return sha(in, "SHA-512");
	}
	
	public static String sha256(final InputStream in) throws IOException, IllegalArgumentException {
		return sha(in, "SHA-256");
	}
	
	private static String sha(final InputStream in, String algo) throws IOException, IllegalArgumentException {
		final int BUFFER_SIZE = 1024 * 1024;
		Objects.requireNonNull(in);
		try {
			final byte[] buf = new byte[BUFFER_SIZE];
			final MessageDigest messageDigest = MessageDigest.getInstance(algo);
			int bytesRead;
			while ((bytesRead = in.read(buf)) != -1) {
				messageDigest.update(buf, 0, bytesRead);
			}
			return new String(Base64.getEncoder().encode(messageDigest.digest()));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 hashing algorithm unknown in this VM.", e);
		}
	}

}
