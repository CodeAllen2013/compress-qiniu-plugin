package com.hyousoft.compresstoqiniu;

import org.kohsuke.stapler.DataBoundConstructor;

public class QiniuEntry {
	public String profileName, source, bucket,checkFile,rootPathMatchPattern,uploadPath,uploadChildPath/* , formatKey */;
	public boolean noUploadOnExists, noUploadOnFailure,isNeedCompress;

	public QiniuEntry() {
	}

	@DataBoundConstructor
	public QiniuEntry(String profileName, String source, String bucket,String checkFile,String rootPathMatchPattern,
			String uploadPath,String uploadChildPath,
	        boolean noUploadOnFailure, boolean noUploadOnExists, boolean isNeedCompress) {
		this.profileName = profileName;
		this.source = source;
		this.bucket = bucket;
		this.checkFile = checkFile;
		this.rootPathMatchPattern = rootPathMatchPattern;
		this.uploadPath = uploadPath;
		this.uploadChildPath = uploadChildPath;
		this.noUploadOnExists = noUploadOnExists;
		this.noUploadOnFailure = noUploadOnFailure;
		this.isNeedCompress = isNeedCompress;
	}

}
