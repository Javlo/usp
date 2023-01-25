package org.javlo.usp.servlet;

import java.io.File;
import java.io.InputStream;

public class CacheReturn {
	private String mimeType;
	private InputStream inputStream;
	private boolean compress;
	private File file;
	
	public CacheReturn(String mimeType, InputStream out, boolean compress, File file) {
		super();
		this.mimeType = mimeType;
		this.inputStream = out;
		this.compress = compress;
		this.setFile(file);
	}
	public String getMimeType() {
		return mimeType;
	}
	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}
	public InputStream getInputStream() {
		return inputStream;
	}
	public void setInputStream(InputStream out) {
		this.inputStream = out;
	}
	public boolean isCompress() {
		return compress;
	}
	public void setCompress(boolean compress) {
		this.compress = compress;
	}
	public File getFile() {
		return file;
	}
	public void setFile(File file) {
		this.file = file;
	}
}
