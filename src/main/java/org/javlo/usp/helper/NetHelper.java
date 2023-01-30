package org.javlo.usp.helper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.javlo.usp.servlet.bean.UspRequest;
import org.owasp.encoder.Encode;

public class NetHelper {
	
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

	public static void addParam(StringBuffer url, String name, String value) {
		if (url == null) {
			return;
		}
		if (url.toString().contains("?")) {
			url.append('&' + name + '=' + Encode.forUriComponent(StringHelper.neverNull(value)));
		} else {
			url.append('?' + name + '=' + Encode.forUriComponent(StringHelper.neverNull(value)));
		}
	}

	public static void executeRequest(String inUrl, UspRequest usp, OutputStream out) throws IOException {
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			StringBuffer url = new StringBuffer(inUrl);
			usp.getParams().forEach((k, v) -> {
				for (String str : v) {
					addParam(url, k, str);
				}
			});

			HttpPost httpPost = new HttpPost(url.toString());
			if (usp.getProxyHost() != null && usp.getProxyPort() > 0) {
				HttpHost proxy = new HttpHost(usp.getProxyHost(), usp.getProxyPort());
				RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
				httpPost.setConfig(config);
			}
			httpPost.addHeader("_USP_HASH", usp.getHash());
			usp.getHeader().forEach((k, v) -> {
				httpPost.addHeader(k, v);
			});

			usp.getData().forEach((k, v) -> {
				ByteArrayInputStream data = new ByteArrayInputStream(v.data);
				HttpEntity httpEntiry = MultipartEntityBuilder.create().addBinaryBody(k, data, ContentType.APPLICATION_OCTET_STREAM, v.fileName).build();
				httpPost.setEntity(httpEntiry);
			});
			
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				HttpEntity entity = response.getEntity();
				entity.writeTo(out);
			}
		}
	}

	public static void main(String[] args) throws Exception {
			String url = "http://localhost:9090/usp/www.javlo.org/img/laptop3/projection___phone.webp";
		//String url = "https://www.javlo.org/img/laptop3/projection___phone.webp";		
		//String url = "http://localhost/javlo2/sexy/transform/screen1/bootstrap-5.2.0/content-large/comp-167346887293588770876/h1744848977/static/images/fun/47629-charley-atwell-nude.jpg.webp";

		UspRequest usp = new UspRequest();
		usp.addParam("name", "Patrick", true);
		File src = new File("c:/trans/work/1.jpg");
		usp.addData("file", new FileInputStream(src), src.getName());
		
		executeRequest(url, usp, new FileOutputStream(new File("c:/trans/work/out.jpg")));
		
		System.out.println(">>>>>>>>> NetHelper DONE."); //TODO: remove debug trace
	}

}
