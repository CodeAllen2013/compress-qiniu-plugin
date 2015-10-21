package com.hyousoft.compresstoqiniu;

import org.json.JSONException;
import org.json.JSONObject;

import com.qiniu.api.io.PutRet;
import com.qiniu.api.net.CallRet;

/**
 * @Description: MyPutRet
 * @author Brian
 * @date Aug 14, 2013 2:41:28 PM
 * @version
 */
public class MyPutRet extends PutRet {

	private int width;
	private int height;
	private String mimeType;
	private int fileSize;
	
	public MyPutRet(CallRet ret) {
		super(ret);
		if (this.response != null) {
			try {
				unmarshal(ret.getResponse());
			} catch (Exception e) {
				this.exception = e;
			}
		}
	}
	
	private void unmarshal(String json) throws JSONException {
		JSONObject jsonObject = new JSONObject(json);
		if (jsonObject.has("w")) {
			this.width = jsonObject.getInt("w");
		}
		if (jsonObject.has("h")) {
			this.height = jsonObject.getInt("h");
		}
		if (jsonObject.has("type")) {
			this.mimeType = jsonObject.getString("type");
		}
		if (jsonObject.has("size")) {
			this.fileSize = jsonObject.getInt("size");
		}
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public String getMimeType() {
		return mimeType;
	}

	public int getFileSize() {
		return fileSize;
	}

}
