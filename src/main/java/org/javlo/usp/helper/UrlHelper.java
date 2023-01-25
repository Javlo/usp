package org.javlo.usp.helper;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.owasp.encoder.Encode;

public class UrlHelper {

	public static String mergePath(String... paths) {
		String outPath = "";
		for (String path : paths) {
			if (path != null) {
				outPath = mergePath(outPath, path);
			}
		}
		return outPath.toString();
	}

	public static String addParam(String url, String name, String value, boolean encode) {
		if (url == null) {
			return null;
		}
		if (encode) {
			if (url.contains("?")) {
				return url = url + '&' + name + '=' + Encode.forUriComponent(StringHelper.neverNull(value));
			} else {
				return url = url + '?' + name + '=' + Encode.forUriComponent(StringHelper.neverNull(value));
			}
		} else {
			if (url.contains("?")) {
				return url = url + '&' + name + '=' + StringHelper.neverNull(value);
			} else {
				return url = url + '?' + name + '=' + StringHelper.neverNull(value);
			}
		}
	}

	public static String cleanUrl(String uri, String... removeParam) throws MalformedURLException, UnsupportedEncodingException {
		URL url = new URL(uri);
		String query = url.getQuery();
		String decodedQuery = URLDecoder.decode(query, "UTF-8");
		List<String> params = Arrays.asList(decodedQuery.split("&"));
		Collections.sort(params);
		String newQuery = "";
		String sep = "";
		for (String param : params) {
			boolean acceptParam = true;
			for (String badParam : removeParam) {
				if (param.startsWith(badParam + "=")) {
					acceptParam = false;
				}
			}
			if (acceptParam) {
				newQuery += sep + param;
				sep = "&";
			}
		}
		return "//" + url.getHost() + ":" + (url.getPort() == -1 ? 80 : url.getPort()) + url.getPath() + (newQuery != null && newQuery.length() > 0 ? "?" + newQuery : "");
	}

	public static String cleanUri(String uri, String... removeParam) throws MalformedURLException, UnsupportedEncodingException {
		String newQuery = "";
		String newUri = uri;
		if (uri.contains("?")) {
			String[] splittedUrl = uri.split("\\?");
			newUri = splittedUrl[0];
			String query = splittedUrl[1];
			String decodedQuery = URLDecoder.decode(query, "UTF-8");
			List<String> params = Arrays.asList(decodedQuery.split("&"));
			Collections.sort(params);
			String sep = "";
			for (String param : params) {
				boolean acceptParam = true;
				for (String badParam : removeParam) {
					if (param.startsWith(badParam + "=")) {
						acceptParam = false;
					}
				}
				if (acceptParam) {
					newQuery += sep + param;
					sep = "&";
				}
			}
		}
		return newUri + (newQuery != null && newQuery.length() > 0 ? "?" + newQuery : "");
	}

	public static void main(String[] args) throws MalformedURLException, UnsupportedEncodingException {
		String url = "https://javlo.org/api/test?username=patrick&auth=true&token=TEST";
		System.out.println(cleanUrl(url, "token"));
		url = "/api/test?username=patrick&auth=true&token=TEST";
		System.out.println(cleanUri(url, "token"));
	}

	/**
	 * merge the path. sample mergePath ("/cat", "element" ) -> /cat/element,
	 * mergePath ("/test/", "/google) -> /test/google
	 * 
	 * @param path1
	 * @param path2
	 * @return
	 */
	public static String mergePath(String path1, String path2) {
		if (path1 == null) {
			return StringHelper.neverNull(path2);
		} else if (path2 == null) {
			return path1;
		}
//		path1 = StringUtils.replace(path1, "\\", "/");
//		path2 = StringUtils.replace(path2, "\\", "/");
		if ((path1 == null) || (path1.trim().length() == 0)) {
			return path2;
		} else if ((path2 == null) || (path2.trim().length() == 0)) {
			return path1;
		} else {
			String[] pathSep = path1.split("\\?");
			String paramPath1 = "";
			if (pathSep.length > 1) {
				path1 = pathSep[0];
				paramPath1 = pathSep[1];
			}
			pathSep = path2.split("\\?");
			String paramPath2 = "";
			if (pathSep.length > 1) {
				path2 = pathSep[0];
				paramPath2 = pathSep[1];
			}

			if (paramPath1.length() > 0 && paramPath2.length() > 0) {
				paramPath1 = '?' + paramPath1 + '&' + paramPath2;
			} else {
				paramPath1 = paramPath1 + paramPath2;
				if (paramPath1.length() > 0) {
					paramPath1 = "?" + paramPath1;
				}
			}
			if (path1.endsWith("/")) {
				if (path2.startsWith("/")) {
					path2 = path2.replaceFirst("/", "");
					return path1 + path2 + paramPath1;
				} else {
					return path1 + path2 + paramPath1;
				}
			} else {
				if (path2.startsWith("/")) {
					return path1 + path2 + paramPath1;
				} else {
					return path1 + '/' + path2 + paramPath1;
				}
			}
		}
	}

}
