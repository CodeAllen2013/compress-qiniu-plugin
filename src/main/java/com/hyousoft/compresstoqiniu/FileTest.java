package com.hyousoft.compresstoqiniu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileTest{
	public static void main(String[] args) {
		try {
			FileReader fileReader = new FileReader(new File("G:\\common.jsp"));
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line = null;
			String patternString = "String CDN_TYPE  = \"(.*)\"";
			while ((line = bufferedReader.readLine()) != null) {
				Pattern p = Pattern.compile(patternString);
				Matcher m = p.matcher(line);
				if(m.find()){
					System.out.println(m.group(1));
					break;
				}
				
			}
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		String testString= "     String CDN_TYPE  = \"/client20150814\";";
//		Pattern p = Pattern.compile("String CDN_TYPE  = \"(.*)\"");
//		Matcher m = p.matcher(testString);
//		System.out.println(m.find());
//		System.out.println(m.group(1));
		
	}
}