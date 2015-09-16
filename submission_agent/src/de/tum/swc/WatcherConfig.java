package de.tum.swc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class WatcherConfig {
	public final String newSubmissionsFolderPath, processedSubmissionsFolderPath, resultsFolderPath, archiveFileExtension, resultTimesFileExtension, workerMachinePath;
	public final String databaseUrl, databaseUser, databasePassword;
	public final String[] resultFileExtensions;
	public final long checkSubmissionInterval;
	public final File newSubmissionsFolder, processedSubmissionsFolder, resultsFolder;
	
	
	public WatcherConfig(String configFilePath) throws IOException, IllegalArgumentException {
		Properties configFile = new Properties();
		InputStream input = null;
		
		try {
			input = new FileInputStream(configFilePath);
			configFile.load(input);
	 
			newSubmissionsFolderPath = readProperty(configFile,"new_submissions_folder");
			newSubmissionsFolder = ensureFolderExistance(newSubmissionsFolderPath);
			processedSubmissionsFolderPath = readProperty(configFile,"processed_submissions_folder");
			processedSubmissionsFolder = ensureFolderExistance(processedSubmissionsFolderPath);
			resultsFolderPath = readProperty(configFile,"results_folder");
			resultsFolder = ensureFolderExistance(resultsFolderPath);
			workerMachinePath = readProperty(configFile,"worker_machine_path");
			archiveFileExtension = readProperty(configFile,"archive_extension");
			resultTimesFileExtension = readProperty(configFile,"result_times_file_extension");
			resultFileExtensions = readProperty(configFile,"result_extension").split("\\|");
			checkSubmissionInterval = Long.parseLong(readProperty(configFile, "check_interval"), 10);
			databaseUrl = readProperty(configFile,"db_url");
			databaseUser = readProperty(configFile,"db_user");
			databasePassword = readProperty(configFile,"db_password");
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
