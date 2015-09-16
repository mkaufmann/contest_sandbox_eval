package de.tum.ewc;

import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IllegalArgumentException, IOException {
		if(args.length < 1) {
			throw new IllegalArgumentException("Usage: EWC [config file]");
		}
		
		//Read config file
		WatcherConfig config = new WatcherConfig(args[0]);
		
		//Start watcher
		Watcher watcher = new Watcher(config);
		Runtime.getRuntime().addShutdownHook(watcher.getCancelWatcherTask());
		watcher.start();
	}

}
