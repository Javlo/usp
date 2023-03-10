package org.javlo.usp.servlet.bean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.javlo.usp.helper.RequestService;
import org.javlo.usp.helper.ResourceHelper;
import org.javlo.usp.helper.StringHelper;

public class UspRequest {

	public static class DataBean {
		public DataBean(String name, byte[] data, String fileName) {
			super();
			this.name = name;
			this.data = data;
			this.fileName = fileName;
		}

		public String name;
		public byte[] data;
		public String fileName;
	}

	public static final String PARAM_RELEVANT_HEADER = "RELEVANT_HEADER";
	public static final String PARAM_RELEVANT_PARAMS = "RELEVANT_PARAMS";

	public static final String METHOD_POST = "post";
	public static final String METHOD_GET = "get";
	
	public static final String HASH_PARAM_NAME = "usp-hash";

	private String method = METHOD_POST;
	private Map<String, String> header = new TreeMap<>();
	private List<String> relevantHeader = new LinkedList<>();
	private Map<String, String[]> params = new TreeMap<>();
	private List<String> relevantParams = new LinkedList<>();
	private Map<String, DataBean> data = new TreeMap<>();
	
	private String proxyHost;
	private int proxyPort;
	private boolean checkSsl = true;

	public UspRequest() {
	};

	public UspRequest(HttpServletRequest request) {
		RequestService rs = RequestService.getInstance(request);

		String relHeader = rs.getParameter(PARAM_RELEVANT_HEADER);
		if (relHeader != null) {
			relevantHeader = StringHelper.stringToCollection(relHeader, ",");
		}

		String relParams = rs.getParameter(PARAM_RELEVANT_PARAMS);
		if (relParams != null) {
			relevantParams = StringHelper.stringToCollection(relParams, ",");
		}

		rs.getParameterMap().forEach((k, v) -> {
			addParam(k, v.toString(), relevantParams.contains(k));
		});

		Enumeration<String> headerKeys = request.getHeaderNames();
		while (headerKeys.hasMoreElements()) {
			String key = headerKeys.nextElement();
			addHeader(key, request.getHeader(key), relevantHeader.contains(key));
		}

		rs.getAllFileItem().forEach(fi -> {
			try {
				addData(fi.getName(), fi.getInputStream(), fi.getFieldName());
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

	}

	public Map<String, String> getHeader() {
		return header;
	}

	public void addHeader(String name, String value, boolean relevant) {
		header.put(name, value);
		if (relevant) {
			relevantHeader.add(name);
		}
	}

	public Map<String, String[]> getParams() {
		return params;
	}

	public void addParam(String name, String value, boolean relevant) {
		params.put(name, new String[] {value});
		if (relevant) {
			relevantParams.add(name);
		}
	}
	
	public void addParam(String name, String[] value, boolean relevant) {
		params.put(name, value);
		if (relevant) {
			relevantParams.add(name);
		}
	}

	public Map<String, DataBean> getData() {
		return data;
	}

	public void addData(String name, InputStream in, String fileName) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ResourceHelper.writeStreamToStream(in, out);		
		data.put(name, new DataBean(name, out.toByteArray(), fileName));
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getMethod() {
		if (isMultipart()) {
			return "post";
		} else {
			return method;
		}
	}

	public String getHash() {
		try {
			ByteArrayOutputStream hashStream = new ByteArrayOutputStream();
			hashStream.write(method.getBytes());
			header.forEach((k, v) -> {
				try {
					if (relevantHeader.contains(k)) {
						hashStream.write(k.getBytes());
						hashStream.write(v.getBytes());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			params.forEach((k, v) -> {
				try {
					if (relevantParams.contains(k)) {
						hashStream.write(k.getBytes());
						for (String str : v) {
							hashStream.write(str.getBytes());
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			data.forEach((k, v) -> {
				try {
					hashStream.write(k.getBytes());
					hashStream.write(v.data);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			return ResourceHelper.sha512(new ByteArrayInputStream(hashStream.toByteArray()));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public boolean isMultipart() {
		return data.size() > 0;
	}

	public void setProxy(String host, int port) {
		this.proxyHost = host;
		this.proxyPort = port;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public boolean isCheckSsl() {
		return checkSsl;
	}

	public void setCheckSsl(boolean checkSsl) {
		this.checkSsl = checkSsl;
	}

}
