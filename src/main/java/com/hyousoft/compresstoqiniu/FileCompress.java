package com.hyousoft.compresstoqiniu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class FileCompress {
	private static int linebreakpos = -1;
	private static boolean munge = true;
	private static boolean verbose = false;
	private static boolean preserveAllSemiColons = false;
	private static boolean disableOptimizations = false;

	
	public static void main(String[] args) throws Exception {
		FileCompress fileCompress = new FileCompress();
		FileOperation.createDir("G:\\test");
		fileCompress.checkFile("G:\\app_sp_product_publish_rule.js","G:\\test");
	}

	public void checkFile(String filePath,String tmpFilePath) throws Exception {
		File file = new File(filePath);
		if (file.isFile()) {
			fileCompress(filePath,tmpFilePath);
			return;
		}
		File[] files = file.listFiles();
		if (files == null || files.length == 0)
			return;
		for (File f : files) {
			if (file.isFile()) {
				fileCompress(filePath,tmpFilePath);
				continue;
			}
			checkFile(filePath,tmpFilePath);
		}
	}

	public static String fileCompress(String filePath,String tmpFilePath) throws Exception {
		File file = new File(filePath);
		String fileName = file.getName();
		if (fileName.endsWith(".js") == false && fileName.endsWith(".css") == false) {
			return null;
		}
		
		
		Reader in = new InputStreamReader(new FileInputStream(file), "utf-8");  
		 
		
		
//		Reader in = new FileReader(file);
		FileOperation.createFile(tmpFilePath+File.separator+fileName);
		File tempFile = new File(tmpFilePath+File.separator+fileName);
//		Writer out = new FileWriter(tempFile);
		Writer out = new OutputStreamWriter(new FileOutputStream(tempFile), "utf-8"); 
		if (fileName.endsWith(".js")) {
			JavaScriptCompressor jscompressor = new JavaScriptCompressor(in, new ErrorReporter() {
				public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
					if (line < 0) {
						System.err.println("\n[WARNING] " + message);
					} else {
						System.err.println("\n[WARNING] " + line + ':' + lineOffset + ':' + message);
					}
				}

				public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
//					if (line < 0) {
//						System.err.println("\n[ERROR] " + message);
//					} else {
//						System.err.println("\n[ERROR] " + line + ':' + lineOffset + ':' + message);
//					}
				}

				public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource,
						int lineOffset) {
					error(message, sourceName, line, lineSource, lineOffset);
					return new EvaluatorException(message);
				}
			});
			jscompressor.compress(out, linebreakpos, munge, verbose, preserveAllSemiColons, disableOptimizations);
		} else if (fileName.endsWith(".css")) {
			CssCompressor csscompressor = new CssCompressor(in);
			csscompressor.compress(out, linebreakpos);
		}
		out.close();
		in.close();
		return tempFile.getPath();
	}
}
