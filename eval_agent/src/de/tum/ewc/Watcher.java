package de.tum.ewc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Watcher extends TimerTask {
	
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
		File[] submissionFolderFiles = config.submissionFolder.listFiles();
		if(submissionFolderFiles.length > 0) {
			//Find submission by file extension
			File newSubmission = null;
			for(File s : submissionFolderFiles) {
				if(s.getName().endsWith(config.submissionFileExtension)) {
					newSubmission = s;
					break;
				}
			}
			if(newSubmission == null) {
				//No submission archive found
				return;
			}
			
			printTimestamped(System.out, "Processing submission '"+newSubmission.getName()+"' ... ");
			System.out.flush();
			
			final String submissionName = newSubmission.getName().substring(0, newSubmission.getName().length() - config.submissionFileExtension.length());
			
			//Evaluate submission
			long evalStartMillis = System.currentTimeMillis();
			try {
				ProcessBuilder evalProcessBuilder = new ProcessBuilder(config.evaluationScriptPath, submissionName);
				evalProcessBuilder.directory(config.submissionFolder);
				Process evalProcess = evalProcessBuilder.start(); 
				BufferedReader processErrorReader = new BufferedReader(new InputStreamReader(evalProcess.getErrorStream()));
				String processError = "";
				String processErrorPart;
				while ((processErrorPart = processErrorReader.readLine()) != null) {
					processError += processErrorPart;
				}
				if(processError.length() > 0) {
					printTimestampedLn(System.err, "\nEvaluation error: " + processError);
					throw new RuntimeException();
				}
				processErrorReader.close();
				evalProcess.waitFor();
				if(evalProcess.exitValue()!=0) {
					printTimestampedLn(System.err, "\nEvaluation exit value " + evalProcess.exitValue());
					throw new RuntimeException();
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				printTimestampedLn(System.err, "\nCould not run submission evaluation. Aborting.");
				throw new RuntimeException();
			}
			long evalDoneMillis = System.currentTimeMillis();
			
			//Delete submission
			newSubmission.delete();
			
			printTimestampedLn(System.out, "done in "+((evalDoneMillis-evalStartMillis)/1000.0)+"s.");
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
	
	static void printTimestamped(PrintStream stream, String str) {
		Date time = Calendar.getInstance().getTime();
		String timeStr = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")).format(time);
		stream.print("["+timeStr+"] "+str);
	}
	static void printTimestampedLn(PrintStream stream, String str) {
		printTimestamped(stream, str+System.lineSeparator());
	}
}
