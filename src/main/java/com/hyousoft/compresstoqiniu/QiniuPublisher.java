package com.hyousoft.compresstoqiniu;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.json.JSONException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.qiniu.api.auth.AuthException;
import com.qiniu.api.auth.digest.Mac;
import com.qiniu.api.config.Config;
import com.qiniu.api.io.PutExtra;
import com.qiniu.api.io.PutRet;
import com.qiniu.api.net.CallRet;
import com.qiniu.api.net.Client;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

/**
 * Sample {@link Builder}.
 * 
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link QiniuPublisher} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 * 
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 * 
 * @author Kohsuke Kawaguchi
 */
public class QiniuPublisher extends Recorder {

	private final List<QiniuEntry> entries = new ArrayList<QiniuEntry>();

	public QiniuPublisher() {
		super();
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {
		// This is where you 'build' the project.
		// Since this is a dummy, we just say 'hello world' and call that a
		// build.

		// This also shows how you can consult the global configuration of the
		// builder
		FilePath ws = build.getWorkspace();
		PrintStream logger = listener.getLogger();
		Map<String, String> envVars = build.getEnvironment(listener);
		final boolean buildFailed = build.getResult() == Result.FAILURE;
		HashSet<String> errorList = new HashSet<String>();
		logger.println("开始压缩并上传到七牛...");
		for (QiniuEntry entry : this.entries) {
			String rootUploadPath = null;
			String finalUploadPath = null;
			if (entry.noUploadOnFailure && buildFailed) {
				logger.println("构建失败,跳过上传");
				continue;
			}

			QiniuProfile profile = this.getDescriptor().getProfileByName(entry.profileName);
			if (profile == null) {
				logger.println("找不到配置项,跳过");
				continue;
			}

			logger.println("解析包含指定根路径的文件，" + "" + " 获取上传的根路径");
			if(entry.uploadPath != null && !entry.uploadPath.isEmpty()){ 
				rootUploadPath = entry.uploadPath;
			}else{
				rootUploadPath = FileOperation.getUploadPath(ws +File.separator + entry.checkFile,entry.rootPathMatchPattern);
				
				if(rootUploadPath == null ||rootUploadPath.isEmpty()){
					logger.println("上传根路径为空，请检查 文件 "+ws +File.separator + entry.checkFile + "正则表达式："+entry.rootPathMatchPattern);
					break;
				}
			}
			finalUploadPath = rootUploadPath+File.separator+entry.uploadChildPath;
			logger.println("最终上传七牛的文件路径" + finalUploadPath);

			PutExtra extra = new PutExtra();

			String expanded = Util.replaceMacro(entry.source, envVars);
			FilePath[] paths = ws.list(expanded);
			String tmpFilePathDir = ws +File.separator+"tmp"+File.separator+finalUploadPath;
			if(entry.isNeedCompress){
				//首先删除文件路径
				FileOperation.deleteDirectory(tmpFilePathDir);
				FileOperation.createDir(tmpFilePathDir);
			}
			for (FilePath path : paths) {
				String key = finalUploadPath +"/"+path.getName();
				try {
					String tmpFilePath = null;
					if(entry.isNeedCompress){
						logger.println("开始压缩文件保存到临时路径 :" + tmpFilePathDir);
						tmpFilePath = FileCompress.fileCompress(path.getRemote(),tmpFilePathDir);
					}else{
						tmpFilePath = path.getRemote();
					}
					String uptoken = getUpToken(profile.getAccessKey(), profile.getSecretKey(), entry.bucket,key);
					byte[] filebyte = getByte(new File(tmpFilePath));
					PutRet ret = put(uptoken, key, filebyte, extra);
					JSONObject retJsonObject = (JSONObject) JSONSerializer.toJSON(ret.toString());
					if (ret.ok()) {
						logger.println(
								"上传 " + tmpFilePath + " 到  bucket:" + entry.bucket + " 相对路径：" +key + " 成功: " + retJsonObject.getString("key"));
					} else {
						errorList.add(path.getRemote());
						logger.print("上传 " + tmpFilePath + " 到  bucket:" + entry.bucket + " 相对路径：" +key + " 失败: ");
						String error = retJsonObject.getString("error");
						if (error != null) {
							logger.print(error);
						}
						logger.println();
					}
					logger.println("           " + ret);
				} catch (Exception e) {
					errorList.add(path.getRemote());
					logger.println("###########################" + String.valueOf(e.getStackTrace()));
					build.setResult(Result.UNSTABLE);
				}
			}
		}
		logger.println("删除临时文件目录..."+ws +File.separator+"tmp");
		FileOperation.deleteDirectory(ws +File.separator+"tmp");
		logger.println("上传导致错误的文件列表如下：");
		for (String errorPath:errorList) {
			logger.println(errorPath);
		}
		
		return true;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link QiniuPublisher}. Used as a singleton. The class is
	 * marked as public so that it can be accessed from views.
	 * 
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	// This indicates to Jenkins that this is an implementation of an extension
	// point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		/**
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 * 
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private final CopyOnWriteList<QiniuProfile> profiles = new CopyOnWriteList<QiniuProfile>();

		public List<QiniuProfile> getProfiles() {
			return Arrays.asList(profiles.toArray(new QiniuProfile[0]));
		}

		public QiniuProfile getProfileByName(String profileName) {
			List<QiniuProfile> profiles = this.getProfiles();
			for (QiniuProfile profile : profiles) {
				if (profileName.equals(profile.getName())) {
					return profile;
				}
			}
			return null;
		}

		/**
		 * In order to load the persisted global configuration, you have to call
		 * load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 *         <p>
		 *         Note that returning {@link FormValidation#error(String)} does
		 *         not prevent the form from being saved. It just means that a
		 *         message will be displayed to the user.
		 */
		public FormValidation doCheckAccessKey(@QueryParameter String value) throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Access Key 不能为空");
			return FormValidation.ok();
		}

		public FormValidation doCheckProfileName(@QueryParameter String value) throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("设置项名称不能为空");
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "压缩文件上传到七牛";
		}

		@Override
		public QiniuPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			List<QiniuEntry> entries = req.bindJSONToList(QiniuEntry.class, formData.get("e"));
			QiniuPublisher pub = new QiniuPublisher();
			pub.getEntries().addAll(entries);
			return pub;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			profiles.replaceBy(req.bindJSONToList(QiniuProfile.class, formData.get("profile")));
			save();
			return true;
		}
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.STEP;
	}

	public List<QiniuEntry> getEntries() {
		return entries;
	}

	private static PutRet put(String uptoken, String key, byte[] file, PutExtra extra) {

		if (file == null || file.length == 0) {
			return new PutRet(new CallRet(400, new Exception("File data is empty.")));
		}

		MultipartEntity requestEntity = new MultipartEntity();
		try {
			requestEntity.addPart("token", new StringBody(uptoken));
			ByteArrayBody byteArrayBody = new ByteArrayBody(file, key);
			requestEntity.addPart("file", byteArrayBody);
			if (key != null)
				requestEntity.addPart("key", new StringBody(key));

			if (extra.checkCrc != com.qiniu.api.io.IoApi.NO_CRC32) {
				if (extra.crc32 == 0) {
					return new PutRet(new CallRet(400, new Exception("no crc32 specified!")));
				}
				requestEntity.addPart("crc32", new StringBody(extra.crc32 + ""));
			}
			if (extra.params != null) {
				for (Map.Entry<String, String> entry : extra.params.entrySet()) {
					requestEntity.addPart(entry.getKey(), new StringBody(entry.getValue(), Charset.forName("UTF-8")));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new PutRet(new CallRet(400, e));
		}

		String url = Config.UP_HOST;
		CallRet ret = new Client().callWithMultiPart(url, requestEntity);
		return new PutRet(ret);
	}

	/**
	 * 把一个文件转化为字节
	 * 
	 * @param file
	 * @return byte[]
	 * @throws Exception
	 */
	@SuppressWarnings("resource")
	public byte[] getByte(File file) throws Exception {
		byte[] bytes = null;
		if (file != null) {
			InputStream is = new FileInputStream(file);
			int length = (int) file.length();
			if (length > Integer.MAX_VALUE) // 当文件的长度超过了int的最大值
			{
				System.out.println("this file is max ");
				return null;
			}
			bytes = new byte[length];
			int offset = 0;
			int numRead = 0;
			while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
				offset += numRead;
			}
			// 如果得到的字节长度和file实际的长度不一致就可能出错了
			if (offset < bytes.length) {
				System.out.println("file length is error");
				return null;
			}
			is.close();
		}
		return bytes;
	}

	private String getUpToken(String qiniuAccessKey, String qiniuSecretKey, String bucketName,String key) {
		Mac mac = new Mac(qiniuAccessKey, qiniuSecretKey);
		MyPutPolicy photoPutPolicy = new MyPutPolicy(bucketName);
		photoPutPolicy.scope = bucketName + ":" + key;
		photoPutPolicy.expires = 3 * 60 * 60 * 24 * 365;
		// 头像上传令牌
		String photoUptoken = null;
		try {
			// 请确保该bucket已经存在
			photoPutPolicy.returnBody = "{\"size\": $(fsize),\"type\": $(mimeType),\"key\": $(etag),\"w\": $(imageInfo.width),\"h\": $(imageInfo.height)}";
			photoUptoken = photoPutPolicy.token(mac);
		} catch (AuthException e) {
		} catch (JSONException e) {
		}
		return photoUptoken;
	}

}
