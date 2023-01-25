package org.javlo.usp.servlet;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.javlo.usp.helper.ResourceHelper;
import org.javlo.usp.helper.StringHelper;
import org.javlo.usp.helper.UrlHelper;

@MultipartConfig(fileSizeThreshold=1024*1024*8*10,maxFileSize=1024*1024*8*50,maxRequestSize=1024*1024*8*100)
public class MainServlet extends HttpServlet {

	private static final int DEFAULT_BUFFER_SIZE = 10240; // ..bytes = 10KB.
	public static final long DEFAULT_EXPIRE_TIME = 604800000L; // ..ms = 1 week.
	private static final String MULTIPART_BOUNDARY = "MULTIPART_BYTERANGES";

	private static final Properties NOT_FOUND = new Properties();
	
	private static final String HASH_PARAM_NAME = "_USP_HASH";

	private static Logger logger = Logger.getLogger(MainServlet.class.getName());

	private static final String VERSION = "B 0.0.1";
	
	private static Map<String, Properties> config = new HashMap<>();

	public static File CONFIG_FOLDER = new File(System.getProperty("user.home") + "/etc/usp");
	public static File DATA_FOLDER = new File(System.getProperty("user.home") + "/data/usp");

	@Override
	public void init() throws ServletException {
		super.init();
		System.out.println("");
		System.out.println("*******************************************");
		System.out.println("*** Uniserval Service Proxy : " + VERSION + " ***");
		System.out.println("*******************************************");
		System.out.println("");
		System.out.println("CONFIG_FOLDER = " + CONFIG_FOLDER);
		System.out.println("DATA_FOLDER   = " + DATA_FOLDER);

		if (!CONFIG_FOLDER.exists()) {
			CONFIG_FOLDER.mkdirs();
		}

		if (!DATA_FOLDER.exists()) {
			DATA_FOLDER.mkdirs();
		}
	}

	private static final File createFileCache(String host, String uri, String hash, Boolean compress) {
		File file = new File(DATA_FOLDER.getAbsolutePath() + '/' + StringHelper.createFileName(host) + '/' + hash + '/' + StringHelper.createFileName(uri) + "." + StringHelper.getFileExtension(uri).toLowerCase());
		if (compress == null && !file.exists()) {
			File cFile = new File(DATA_FOLDER.getAbsolutePath() + '/' + StringHelper.createFileName(host) + '/' + hash + '/'+ StringHelper.createFileName(uri) + "." + StringHelper.getFileExtension(uri).toLowerCase() + ".gzip");
			if (cFile.exists()) {
				return cFile;
			}
		}
		if (compress != null && compress) {
			file = new File(file.getAbsolutePath() + ".gzip");
		}
		return file;
	}

	private void reset(String host) throws IOException {
		File file = new File(DATA_FOLDER.getAbsolutePath() + '/' + StringHelper.createFileName(host));
		logger.info("delete cache : " + file);
		File fileDest = new File(DATA_FOLDER.getAbsolutePath() + "/___DELETE_ME___" + StringHelper.createFileName(host));
		file.renameTo(fileDest);
		deleteDirectoryRecursion(fileDest);
	}

	private static final void deleteDirectoryRecursion(File file) throws IOException {
		if (file.isDirectory()) {
			File[] entries = file.listFiles();
			if (entries != null) {
				for (File entry : entries) {
					deleteDirectoryRecursion(entry);
				}
			}
		}
		if (!file.delete()) {
			throw new IOException("Failed to delete " + file);
		}
	}

	private static boolean isCompress(File file) {
		return file.getAbsolutePath().endsWith(".gzip");
	}

	private CacheReturn getInCache(String host, String uri, String hash) throws FileNotFoundException {
		File cacheFile = createFileCache(host, uri, hash, null);
		if (cacheFile.exists()) {
			return new CacheReturn(ResourceHelper.getFileExtensionToMineType(StringHelper.getFileExtension(cacheFile.getName())), new FileInputStream(cacheFile), isCompress(cacheFile), cacheFile);
		} else {
			return null;
		}
	}

	private CacheReturn putInCache(String host, String uri, String hash, InputStream in) throws IOException {
		String ext = StringHelper.getFileExtension(uri);
		boolean compress = false;
		if (ext != null && ext.length() > 0) {
			ext = ext.toLowerCase();
			if (ext.equals("js") || ext.equals("html") || ext.equals("txt") || ext.equals("css") || ext.equals("svg")) {
				compress = true;
			}
		}
		OutputStream out = null;
		File cacheFile = createFileCache(host, uri, hash, compress);
		try {
			if (in.available()>0) {
				cacheFile.getParentFile().mkdirs();
			}
			out = new FileOutputStream(cacheFile);
			if (compress) {
				out = new GZIPOutputStream(out);
			}
			ResourceHelper.writeStreamToStream(in, out);
		} finally {
			out.close();
		}
		return getInCache(host, uri, hash);
	}

	private static Properties getConfig(String host) throws FileNotFoundException, IOException {
		host = StringHelper.createFileName(host);
		Properties out = config.get(host);
		if (out == null) {
			synchronized (config) {
				File prop = new File(CONFIG_FOLDER.getAbsoluteFile() + "/" + host + ".properties");
				if (!prop.exists()) {
					logger.warning("host not found : " + prop);
					out = NOT_FOUND;
				} else {
					out = new Properties();
					try (InputStream in = new FileInputStream(prop)) {
						out.load(in);
					}
				}
				config.put(host, out);
			}
		}
		if (out == NOT_FOUND) {
			return null;
		} else {
			return out;
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		process(request, response, false, true);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		process(request, response, true, true);
	}

	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		process(request, response, false, false);

	}

	private void process(HttpServletRequest request, HttpServletResponse response, boolean post, boolean content) {
		try {
			String host = StringHelper.getDomainName(request.getRequestURL().toString());
			String uri = request.getPathInfo();
			String hash = request.getHeader(HASH_PARAM_NAME);
			if (uri.length() > 3) {
				uri = uri.substring(1); // remove '/'
				Properties config = getConfig(host);
				if (config == null) {
					int index = uri.indexOf('/');
					if (index > 0) {
						host = uri.substring(0, index);
						uri = uri.substring(index);
						config = getConfig(host);
					} else {
						logger.severe("context not found.");
					}
				}
				
				if (config != null) {
					System.out.println(">>>>>>>>> MainServlet.process : config found."); //TODO: remove debug trace
					String urlHost = config.getProperty("url.target");
					if (urlHost != null) {
						if (uri.equals("/" + config.get("code.reset"))) {
							reset(host);
						} else if (uri.equals("/check")) {
							response.getOutputStream().write("ok".getBytes());
						} else {
							CacheReturn cache = getInCache(host, uri, hash);
							if (cache == null) {
								synchronized (this) {
									cache = getInCache(host, uri, hash);
									System.out.println(">>>>>>>>> MainServlet.process : cache = "+cache); //TODO: remove debug trace
									if (cache == null) {
										System.out.println(">>>>>>>>> MainServlet.process : not found in cache 1"); //TODO: remove debug trace
										System.out.println(">>>>>>>>> MainServlet.process : urlHost = "+urlHost); //TODO: remove debug trace
										System.out.println(">>>>>>>>> MainServlet.process : uri     = "+uri); //TODO: remove debug trace
										String sourceUrl = UrlHelper.mergePath(urlHost, uri);
										System.out.println(">>>>>>>>> MainServlet.process : not found in cache 2"); //TODO: remove debug trace
										sourceUrl = UrlHelper.addParam(sourceUrl, "ts", "" + System.currentTimeMillis(), false);
										
										System.out.println(">>>>>>>>> MainServlet.process : get input stream"); //TODO: remove debug trace
										
										System.out.println(">>>>>>>>> MainServlet.process : request.getContentType() = "+request.getContentType()); //TODO: remove debug trace
										
										Collection<Part> parts = request.getParts();
										System.out.println(">>>>>>>>> MainServlet.process : #parts = "+parts.size()); //TODO: remove debug trace

										if (parts.size()>1) {
											File test = new File("c:/trans/test.jpg");
											System.out.println(">>>>>>>>> MainServlet.process : test = "+test); //TODO: remove debug trace
											ResourceHelper.writeStreamToFile(parts.iterator().next().getInputStream(), test);
										} else {
											System.out.println(">>>>>>>>> MainServlet.process : no request inputStream"); //TODO: remove debug trace
										}
										
										URL url = new URL(sourceUrl);
										InputStream in = null;
										try {
											URLConnection conn = url.openConnection();
											in = conn.getInputStream();
											logger.info("add in cache : " + sourceUrl);
											cache = putInCache(host, uri, hash, in);
										} catch (Exception e) {
											e.printStackTrace();
											logger.severe("error connection : " + url);
										} finally {
											ResourceHelper.safeClose(in);
										}
									} else {
										System.out.println(">>>>>>>>> MainServlet.process : found in cache 1"); //TODO: remove debug trace
									}
								}
							}
							System.out.println(">>>>>>>>> MainServlet.process : BETWEEN IF"); //TODO: remove debug trace
							if (cache != null) {
								System.out.println(">>>>>>>>> MainServlet.process : found in cache 2"); //TODO: remove debug trace
								if (cache.getMimeType() != null) {
									response.setContentType(cache.getMimeType());
								}
								String cleanHost = urlHost;
								if (cleanHost.endsWith("/")) {
									cleanHost = cleanHost.substring(0, cleanHost.length() - 1);
								}
								if (cache.isCompress()) {
									response.addHeader("Content-Encoding", "gzip");
								}
								response.addHeader("Access-Control-Allow-Origin", cleanHost);
								/*
								 * response.addHeader("Cache-control", "max-age=" + (60 * 60 * 24 * 30) +
								 * ", public"); // 30 days
								 * ResourceHelper.writeStreamToStream(cache.getInputStream(),
								 * response.getOutputStream());
								 * ResourceHelper.safeClose(cache.getInputStream());
								 */
								processCache(request, response, cache, content);
							} else {
								response.setStatus(HttpServletResponse.SC_NOT_FOUND);
							}
						}
					} else {
						logger.severe("bad config file (no url.target) : " + host);
					}
				} else {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void processCache(HttpServletRequest request, HttpServletResponse response, CacheReturn cache, boolean content) throws IOException {

		if (!cache.getFile().exists()) {
			logger.warning("file not found : " + cache.getFile());
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		// Prepare some variables. The ETag is an unique identifier of the file.
		// String fileName = file.getName();
		long length = cache.getFile().length();
		long lastModified = cache.getFile().lastModified();
		String eTag = cache.getFile().getName() + "_" + length + "_" + lastModified;
		long expires = System.currentTimeMillis() + DEFAULT_EXPIRE_TIME;

		// Validate request headers for caching
		// ---------------------------------------------------

		// If-None-Match header should contain "*" or ETag. If so, then return 304.
		String ifNoneMatch = request.getHeader("If-None-Match");
		if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			response.setHeader("ETag", eTag); // Required in 304.
			response.setDateHeader("Expires", expires); // Postpone cache with 1 week.
			return;
		}

		// If-Modified-Since header should be greater than LastModified. If so, then
		// return 304.
		// This header is ignored if any If-None-Match header is specified.
		long ifModifiedSince = request.getDateHeader("If-Modified-Since");
		if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince + 1000 > lastModified) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			response.setHeader("ETag", eTag); // Required in 304.
			response.setDateHeader("Expires", expires); // Postpone cache with 1 week.
			return;
		}

		// Validate request headers for resume
		// ----------------------------------------------------

		// If-Match header should contain "*" or ETag. If not, then return 412.
		String ifMatch = request.getHeader("If-Match");
		if (ifMatch != null && !matches(ifMatch, eTag)) {
			response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}

		// If-Unmodified-Since header should be greater than LastModified. If not, then
		// return 412.
		long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
		if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified) {
			response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}

		// Validate and process range
		// -------------------------------------------------------------

		// Prepare some variables. The full Range represents the complete file.
		Range full = new Range(0, length - 1, length);
		List<Range> ranges = new ArrayList<Range>();

		// Validate and process Range and If-Range headers.
		String range = request.getHeader("Range");

		if (range != null) {

			// Range header should match format "bytes=n-n,n-n,n-n...". If not, then return
			// 416.
			if (!range.matches("^bytes=\\d*-\\d*(,\\d*-\\d*)*$")) {
				response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
				response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
				return;
			}

			// If-Range header should either match ETag or be greater then LastModified. If
			// not,
			// then return full file.
			String ifRange = request.getHeader("If-Range");
			if (ifRange != null && !ifRange.equals(eTag)) {
				try {
					long ifRangeTime = request.getDateHeader("If-Range"); // Throws IAE if invalid.
					if (ifRangeTime != -1 && ifRangeTime + 1000 < lastModified) {
						ranges.add(full);
					}
				} catch (IllegalArgumentException ignore) {
					ranges.add(full);
				}
			}

			// If any valid If-Range header, then process each part of byte range.
			if (ranges.isEmpty()) {
				for (String part : range.substring(6).split(",")) {
					// Assuming a file with length of 100, the following examples returns bytes at:
					// 50-80 (50 to 80), 40- (40 to length=100), -20 (length-20=80 to length=100).
					long start = sublong(part, 0, part.indexOf("-"));
					long end = sublong(part, part.indexOf("-") + 1, part.length());

					if (start == -1) {
						start = length - end;
						end = length - 1;
					} else if (end == -1 || end > length - 1) {
						end = length - 1;
					}

					// Check if Range is syntactically valid. If not, then return 416.
					if (start > end) {
						response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
						response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
						return;
					}

					// Add range.
					ranges.add(new Range(start, end, length));
				}
			}
		}

		// Prepare and initialize response
		// --------------------------------------------------------

		// Get content type by file name and set default GZIP support and content
		// disposition.
		String contentType = cache.getMimeType();
		boolean acceptsGzip = cache.isCompress();
		String disposition = "inline";

		// If content type is unknown, then set the default value.
		// For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
		// To add new content types, add new mime-mapping entry in web.xml.
		if (contentType == null) {
			contentType = "application/octet-stream";
		}

		// If content type is text, then determine whether GZIP content encoding is
		// supported by
		// the browser and expand content type with the one and right character
		// encoding.
//		if (contentType.startsWith("text")) {
//			String acceptEncoding = request.getHeader("Accept-Encoding");
//			acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
//			contentType += ";charset=UTF-8";
//		}

		// Else, expect for images, determine content disposition. If content type is
		// supported by
		// the browser, then set to inline, else attachment which will pop a 'save as'
		// dialogue.
		else if (!contentType.startsWith("image")) {
			String accept = request.getHeader("Accept");
			disposition = accept != null && accepts(accept, contentType) ? "inline" : "attachment";
		}

		// Initialize response.
		//response.reset();
		response.setBufferSize(DEFAULT_BUFFER_SIZE);
		response.setHeader("Content-Disposition", disposition + ";filename=\"" + cache.getFile().getName() + "\"");
		response.setHeader("Accept-Ranges", "bytes");
		response.setHeader("ETag", eTag);
		response.setDateHeader("Last-Modified", lastModified);
		response.setDateHeader("Expires", expires);

		// Send requested file (part(s)) to client
		// ------------------------------------------------

		// Prepare streams.
		RandomAccessFile input = null;
		OutputStream output = null;

		try {
			// Open streams.
			input = new RandomAccessFile(cache.getFile(), "r");
			output = response.getOutputStream();

			if (ranges.isEmpty() || ranges.get(0) == full) {

				// Return full file.
				Range r = full;
				response.setContentType(contentType);

				if (content) {
					if (acceptsGzip) {
						// The browser accepts GZIP, so GZIP the content.
						response.setHeader("Content-Encoding", "gzip");
						//output = new GZIPOutputStream(output, DEFAULT_BUFFER_SIZE);
					} else {
						// Content length is not directly predictable in case of GZIP.
						// So only add it if there is no means of GZIP, else browser will hang.
						response.setHeader("Content-Length", String.valueOf(r.length));
					}

					// Copy full range.
					copy(input, output, r.start, r.length);
				}

			} else if (ranges.size() == 1) {

				// Return single part of file.
				Range r = ranges.get(0);
				response.setContentType(contentType);
				response.setHeader("Content-Range", "bytes " + r.start + "-" + r.end + "/" + r.total);
				response.setHeader("Content-Length", String.valueOf(r.length));
				response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.

				if (content) {
					// Copy single part range.
					copy(input, output, r.start, r.length);
				}

			} else {

				// Return multiple parts of file.
				response.setContentType("multipart/byteranges; boundary=" + MULTIPART_BOUNDARY);
				response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.

				if (content) {
					// Cast back to ServletOutputStream to get the easy println methods.
					ServletOutputStream sos = (ServletOutputStream) output;

					// Copy multi part range.
					for (Range r : ranges) {
						// Add multipart boundary and header fields for every range.
						sos.println();
						sos.println("--" + MULTIPART_BOUNDARY);
						sos.println("Content-Type: " + contentType);
						sos.println("Content-Range: bytes " + r.start + "-" + r.end + "/" + r.total);

						// Copy single part range of multi part range.
						copy(input, output, r.start, r.length);
					}

					// End with multipart boundary.
					sos.println();
					sos.println("--" + MULTIPART_BOUNDARY + "--");
				}
			}
		} finally {
			// Gently close streams.
			close(output);
			close(input);
		}
	}

	// Helpers (can be refactored to public utility class)
	// ----------------------------------------

	/**
	 * Returns true if the given accept header accepts the given value.
	 * 
	 * @param acceptHeader
	 *            The accept header.
	 * @param toAccept
	 *            The value to be accepted.
	 * @return True if the given accept header accepts the given value.
	 */
	private static boolean accepts(String acceptHeader, String toAccept) {
		String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
		Arrays.sort(acceptValues);
		return Arrays.binarySearch(acceptValues, toAccept) > -1 || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1 || Arrays.binarySearch(acceptValues, "*/*") > -1;
	}

	/**
	 * Returns true if the given match header matches the given value.
	 * 
	 * @param matchHeader
	 *            The match header.
	 * @param toMatch
	 *            The value to be matched.
	 * @return True if the given match header matches the given value.
	 */
	public static boolean matches(String matchHeader, String toMatch) {
		String[] matchValues = matchHeader.split("\\s*,\\s*");
		Arrays.sort(matchValues);
		return Arrays.binarySearch(matchValues, toMatch) > -1 || Arrays.binarySearch(matchValues, "*") > -1;
	}

	/**
	 * Returns a substring of the given string value from the given begin index to
	 * the given end index as a long. If the substring is empty, then -1 will be
	 * returned
	 * 
	 * @param value
	 *            The string value to return a substring as long for.
	 * @param beginIndex
	 *            The begin index of the substring to be returned as long.
	 * @param endIndex
	 *            The end index of the substring to be returned as long.
	 * @return A substring of the given string value as long or -1 if substring is
	 *         empty.
	 */
	private static long sublong(String value, int beginIndex, int endIndex) {
		String substring = value.substring(beginIndex, endIndex);
		return (substring.length() > 0) ? Long.parseLong(substring) : -1;
	}

	/**
	 * Copy the given byte range of the given input to the given output.
	 * 
	 * @param input
	 *            The input to copy the given range to the given output for.
	 * @param output
	 *            The output to copy the given range from the given input for.
	 * @param start
	 *            Start of the byte range.
	 * @param length
	 *            Length of the byte range.
	 * @throws IOException
	 *             If something fails at I/O level.
	 */
	private static void copy(RandomAccessFile input, OutputStream output, long start, long length) throws IOException {
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		int read;

		if (input.length() == length) {
			// Write full range.
			while ((read = input.read(buffer)) > 0) {
				output.write(buffer, 0, read);
			}
		} else {
			// Write partial range.
			input.seek(start);
			long toRead = length;

			while ((read = input.read(buffer)) > 0) {
				if ((toRead -= read) > 0) {
					output.write(buffer, 0, read);
				} else {
					output.write(buffer, 0, (int) toRead + read);
					break;
				}
			}
		}
	}

	/**
	 * Close the given resource.
	 * 
	 * @param resource
	 *            The resource to be closed.
	 */
	private static void close(Closeable resource) {
		if (resource != null) {
			try {
				resource.close();
			} catch (IOException ignore) {
				// Ignore IOException. If you want to handle this anyway, it might be useful to
				// know
				// that this will generally only be thrown when the client aborted the request.
			}
		}
	}

	// Inner classes
	// ------------------------------------------------------------------------------

	/**
	 * This class represents a byte range.
	 */
	protected class Range {
		long start;
		long end;
		long length;
		long total;

		/**
		 * Construct a byte range.
		 * 
		 * @param start
		 *            Start of the byte range.
		 * @param end
		 *            End of the byte range.
		 * @param total
		 *            Total length of the byte source.
		 */
		public Range(long start, long end, long total) {
			this.start = start;
			this.end = end;
			this.length = end - start + 1;
			this.total = total;
		}

	}

}
