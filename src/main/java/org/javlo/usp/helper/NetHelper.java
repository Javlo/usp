package org.javlo.usp.helper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.javlo.usp.servlet.bean.UspRequest;
import org.owasp.encoder.Encode;

public class NetHelper {
	
	private static Logger logger = Logger.getLogger(NetHelper.class.getName());

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
		HttpClientConnectionManager cm;
		if (!usp.isCheckSsl()) {
			try {
				TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
				SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
				SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
				cm = PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(csf).build();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			System.out.println("WARNING : NO SSL check");
		} else {
			cm = PoolingHttpClientConnectionManagerBuilder.create().build();
		}
		try (CloseableHttpClient httpclient = HttpClients.custom().setConnectionManager(cm).build()) {
			StringBuffer url = new StringBuffer(inUrl);
			usp.getParams().forEach((k, v) -> {
				for (String str : v) {
					addParam(url, k, str);
				}
			});
			HttpPost httpPost = new HttpPost(url.toString());
			if (usp.getProxyHost() != null && usp.getProxyPort() > 0) {
				logger.info("proxy : "+usp.getProxyHost()+':'+usp.getProxyPort());
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
				HttpEntity httpEntiry = MultipartEntityBuilder.create()
						.addBinaryBody(k, data, ContentType.APPLICATION_OCTET_STREAM, v.fileName).build();
				httpPost.setEntity(httpEntiry);
			});
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				HttpEntity entity = response.getEntity();
				entity.writeTo(out);
			}
		}
	}

	public static void main(String[] args) throws Exception {

		/** disabled certificat for main test **/

		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };

		// Install the all-trusting trust manager
		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

		String url = "https://eptoolkitlb.secure.ep.parl.union.eu/usp/javloorg/img/laptop3/projection___phone.webp";
		// String url =
		// "http://localhost:9090/usp/www.javlo.org/img/laptop3/projection___phone.webp";
		// String url = "https://www.javlo.org/img/laptop3/projection___phone.webp";
		// String url =
		// "http://localhost/javlo2/sexy/transform/screen1/bootstrap-5.2.0/content-large/comp-167346887293588770876/h1744848977/static/images/fun/47629-charley-atwell-nude.jpg.webp";

		UspRequest usp = new UspRequest();
		usp.setCheckSsl(true);
		usp.addParam("name", "Patrick", true);
		File src = new File("c:/trans/work/1.jpg");
		usp.addData("file", new FileInputStream(src), src.getName());

		executeRequest(url, usp, new FileOutputStream(new File("c:/trans/work/out.jpg")));

		System.out.println(">>>>>>>>> NetHelper DONE."); // TODO: remove debug trace
	}

}
