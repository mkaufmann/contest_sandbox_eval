package de.tum.ewc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class WatcherConfig {
	public final String evaluationScriptPath, submissionFolderPath, submissionFileExtension;
	public final long checkSubmissionInterval;
	public final File submissionFolder;
	
	
	public WatcherConfig(String configFilePath) throws IOException, IllegalArgumentException {
		Properties configFile = new Properties();
		InputStream input = null;
		
		try {
			input = new FileInputStream(configFilePath);
			configFile.load(input);
	 
			evaluationScriptPath = readProperty(configFile,"evaluation_script");
			submissionFolderPath = readProperty(configFile,"submission_folder");
			submissionFolder = ensureFolderExistance(submissionFolderPath);
			submissionFileExtension = readProperty(configFile,"submission_extension");
			checkSubmissionInterval = Long.parseLong(readProperty(configFile, "check_interval"), 10);
		} catch (IOException ex) {
			System.err.println("Could not open config file.");
			throw new IllegalArgumentException();
		} finally {
			if (input != null) {
				input.close();
			}
		}
	}
	
	static String readProperty(Properties configFile, String property) {
		 String propVal = configFile.getProperty(property);
		 if(propVal != null) {
			 return propVal;
		 } else {
			 System.err.println("Property '"+property+"' not set in config file.");
			 throw new IllegalArgumentException();
		 }
	}
	
	static File ensureFolderExistance(String path) {
		File folder = new File(path);
		if(!folder.exists()) {
			folder.mkdirs();
		} else {
			if(!folder.isDirectory()) {
				System.err.println("'"+path+"' is not a folder.");
				throw new IllegalArgumentException();
			}
		}
		return folder;
	}
}
