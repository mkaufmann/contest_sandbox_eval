package de.tum.swc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Watcher extends TimerTask {
	
	static final Pattern parseSubmissionNamePattern = Pattern.compile("^([0-9]+)_([0-9]+)_.*");
	
	final WatcherConfig config;
	CancelWatcherTask cancelTask;
	Timer checkSubmissionsTimer;
	
	public Watcher(WatcherConfig config) {
		this.config = config;
		this.cancelTask = new CancelWatcherTask();
		this.checkSubmissionsTimer = null;
	}
	
	public void start() {
		if(checkSubmissionsTimer == null) {
			checkSubmissionsTimer = new Timer(false);
		}
		checkSubmissionsTimer.schedule(this, 0, config.checkSubmissionInterval);
	}
	
	public void stop() {
		checkSubmissionsTimer.cancel();
	}
	
	public CancelWatcherTask getCancelWatcherTask() {
		return cancelTask;
	}

	@Override
	public void run() {
		//Get new submissions
		File[] newSubmissions = config.newSubmissionsFolder.listFiles();
		for(File f : newSubmissions) {
			if(f.getName().equals("refresh.txt")) {
				printTimestampedLn(System.out, "Refresh of top submissions requested ... ");
				
				//Refresh all top submissions
				refreshTopSubmissions();
				
				f.delete();
				newSubmissions = config.newSubmissionsFolder.listFiles();
			}
		}
		if(newSubmissions.length > 0) {
			//Order by name and find first with the right extension
			Arrays.sort(newSubmissions, new Comparator<File>() {
				@Override
				public int compare(File o1, File o2) {
					return o1.getAbsolutePath().compareTo(o2.getAbsolutePath());
				}
			});
			File newSubmission = null;
			for(File s : newSubmissions) {
				if(s.getName().endsWith(config.archiveFileExtension)) {
					newSubmission = s;
					break;
				}
			}
			if(newSubmission == null) {
				//No submission archive found
				return;
			}
			final String submissionName = newSubmission.getName().substring(0, newSubmission.getName().length() - config.archiveFileExtension.length()); 
			
			//Ensure that this submission was not processed before 
			File processedSubmission = new File(config.processedSubmissionsFolder.getAbsolutePath() + "/" + newSubmission.getName());
			if(processedSubmission.exists()) {
				printTimestampedLn(System.err, "New submission '"+newSubmission.getName()+"' found that was already processed before. Aborting.");
				throw new RuntimeException();
			}
			printTimestampedLn(System.out, "Processing new submission '"+newSubmission.getName()+"' ... ");
			
			//Transfer submission archive to execution server
			{
				boolean transferedResult = false;
				while(!transferedResult) {
					transferedResult = true;
					try {
						Process transferNewSubmissionProcess = new ProcessBuilder("rsync", "-qWI", "--timeout=5", newSubmission.getAbsolutePath(),config.workerMachinePath+newSubmission.getName()).start();
						BufferedReader processErrorReader = new BufferedReader(new InputStreamReader(transferNewSubmissionProcess.getErrorStream()));
						String processError;
						while ((processError = processErrorReader.readLine()) != null) {
							printTimestampedLn(System.err, "Ignored RSYNC error: " + processError);
							transferedResult = false;
						}
						processErrorReader.close();
						transferNewSubmissionProcess.waitFor();
						if(transferNewSubmissionProcess.exitValue() != 0) {
							printTimestampedLn(System.err, "Ignored RSYNC exit value " + transferNewSubmissionProcess.exitValue());
							transferedResult = false;
						}
						Thread.sleep(100);
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
						printTimestampedLn(System.err, "Could not transfer submission '"+newSubmission.getName()+"'. Aborting.");
						throw new RuntimeException();
					}
				}
				printTimestampedLn(System.out, "    transferred to execution machine.");
			}
			
			long processingStartMillis = System.currentTimeMillis();
			for(String resultExtension : config.resultFileExtensions) {
				String resultFileName = submissionName + resultExtension;
				printTimestamped(System.out, "    waiting for result file '"+resultFileName+"' ... ");
				System.out.flush();
				
				//Continuously try to move result file from execution server
				boolean transferedResult = false;
				while(!transferedResult) {
					transferedResult = true;
					try {
						Process transferResultProcess = new ProcessBuilder("rsync", "-qWI", "--timeout=5", "--remove-source-files", config.workerMachinePath+resultFileName, config.resultsFolder.getAbsolutePath()+"/"+resultFileName).start();
						BufferedReader processErrorReader = new BufferedReader(new InputStreamReader(transferResultProcess.getErrorStream()));
						while (processErrorReader.readLine() != null) {
							transferedResult = false;
						}
						processErrorReader.close();
						transferResultProcess.waitFor();
						if(transferResultProcess.exitValue() != 0) {
							transferedResult = false;
						}
						Thread.sleep(100);
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
						printTimestampedLn(System.err, "\nCould not transfer result '"+resultFileName+"'. Aborting.");
						throw new RuntimeException();
					}
				}
				System.out.println("transferred.");
			}
			long processingDoneMillis = System.currentTimeMillis();
			
			//Read results and write to database
			storeResultInDB(new File(config.resultsFolder.getAbsolutePath()+"/"+submissionName+config.resultTimesFileExtension));
			
			//Move submission to processed folder
			newSubmission.renameTo(processedSubmission);
			
			printTimestampedLn(System.out, "  done in "+((processingDoneMillis-processingStartMillis)/1000.0)+"s.");
		}
	}
	
	void storeResultInDB(File resultFile) {
		//Parse submission name
		Matcher submissionNameMatcher = parseSubmissionNamePattern.matcher(resultFile.getName());
		if(!submissionNameMatcher.find()) {
			printTimestampedLn(System.err, "Could not parse submission name '" + resultFile.getName() + "'. Aborting.");
			throw new RuntimeException();
		}
		
		//Read execution times
		double smallTime,mediumTime,largeTime,vLargeTime;
		int resultCode = 0;
		Properties resultPropertiesFile = new Properties();
		InputStream resultInputStream = null;
		try {
			resultInputStream = new FileInputStream(resultFile);
			resultPropertiesFile.load(resultInputStream);
	 
			smallTime =  readProperty(resultPropertiesFile,"small"); resultCode += smallTime>=0 ? 1 : 0;
			mediumTime =  readProperty(resultPropertiesFile,"medium"); resultCode += mediumTime>=0 ? 1 : 0;
			largeTime =  readProperty(resultPropertiesFile,"large"); resultCode += largeTime>=0 ? 1 : 0;
			vLargeTime =  readProperty(resultPropertiesFile,"vLarge"); resultCode += vLargeTime>=0 ? 1 : 0;
		} catch (IOException ex) {
			printTimestampedLn(System.err, "Could not open result file.");
			throw new RuntimeException();
		} finally {
			if (resultInputStream != null) {
				try {
					resultInputStream.close();
				} catch (IOException e) { }
			}
		}
		
		Connection con = null;
		PreparedStatement st = null;
		try {
			con = DriverManager.getConnection(config.databaseUrl, config.databaseUser, config.databasePassword);
			st = con.prepareStatement("update submissions set runtime_small=?,runtime_big=?,runtime_large=?,runtime_vlarge=?,result_code=? where team_id=? and submission_time=?");
			st.setDouble(1, smallTime);
			st.setDouble(2, mediumTime);
			st.setDouble(3, largeTime);
			st.setDouble(4, vLargeTime);
			st.setInt(5, resultCode);
			st.setLong(6, Long.parseLong(submissionNameMatcher.group(2)));
			st.setLong(7, Long.parseLong(submissionNameMatcher.group(1)));
			st.execute();
		} catch (SQLException ex) {
			printTimestampedLn(System.err, "Could not execute update query ("+Long.parseLong(submissionNameMatcher.group(2))+","+Long.parseLong(submissionNameMatcher.group(1))+","+smallTime+","+mediumTime+","+largeTime+","+vLargeTime+"). " + ex.getMessage() + ". Aborting.");
			throw new RuntimeException();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
				if (con != null) {
					con.close();
				}
			} catch (SQLException ex) { }
		}
	}
	
	void refreshTopSubmissions() {
		Connection con = null;
		PreparedStatement st = null;
		try {
			con = DriverManager.getConnection(config.databaseUrl, config.databaseUser, config.databasePassword);
			st = con.prepareStatement("select distinct filename from submissions S;");
			ResultSet filesToRefresh = st.executeQuery();
			while(filesToRefresh.next()) {
				printTimestampedLn(System.out, "Resubmitting '"+filesToRefresh.getString(1)+"' ... ");
				
				File submissionFile = new File(config.processedSubmissionsFolder.getAbsolutePath() + "/" + filesToRefresh.getString(1));
				if(!submissionFile.exists()) {
					printTimestampedLn(System.err, "Top submission '" + submissionFile.getAbsolutePath() + "' does not exist. Aborting.");
					throw new RuntimeException();
				}
				
				File resubmissionFile = new File(config.newSubmissionsFolder.getAbsolutePath() + "/" + filesToRefresh.getString(1));
				if(!submissionFile.renameTo(resubmissionFile)) {
					printTimestampedLn(System.err, "Could not move top submission '" + submissionFile.getAbsolutePath() + "'. Aborting.");
					throw new RuntimeException();
				}
			}
		} catch (SQLException ex) {
			printTimestampedLn(System.err, "Could not query top submissions. Aborting.");
			throw new RuntimeException();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
				if (con != null) {
					con.close();
				}
			} catch (SQLException ex) { }
		}
	}

	public class CancelWatcherTask extends Thread {
		@Override
        public void run()
        {
			printTimestampedLn(System.out, "Shutting down ...");
			Watcher.this.cancel();
        }
	}
	
	static double readProperty(Properties resultFile, String property) {
		 String propVal = resultFile.getProperty(property);
		 if(propVal != null) {
			 return Long.parseLong(propVal,10)/1000.0;
		 } else {
			 return -1;
		 }
	}
	
	static void printTimestamped(PrintStream stream, String str) {
		Date time = Calendar.getInstance().getTime();
		String timeStr = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")).format(time);
		stream.print("["+timeStr+"] "+str);
	}
	static void printTimestampedLn(PrintStream stream, String str) {
		printTimestamped(stream, str+System.lineSeparator());
	}
}
