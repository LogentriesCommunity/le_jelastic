

//
// Copyright (c) 2010-2012 Logentries, Jlizard
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
//
// * Redistributions of source code must retain the above copyright notice,
//   this list of conditions and the following disclaimer.
//
// * Redistributions in binary form must reproduce the above copyright notice,
//   this list of conditions and the following disclaimer in the documentation
//   and/or other materials provided with the distribution.
//
// * Neither the name of Logentries nor the names of its
//   contributors may be used to endorse or promote products derived from this
//   software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
//
// Mark Lacomber <marklacomber@gmail.com>

package com.logentries;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.commons.io.input.*;

//By extending ServletContextListener this class can execute application-wide background thread(s)
//Will run in the background when app is deployed
public class LogentriesServlet implements ServletContextListener {

	/*
	 *  CONSTANTS
	 */

	/** Size of the internal event queue. */
	static final int QUEUE_SIZE = 32768;
	/** Logentries API Server address */
	static final String LE_API = "api.logentries.com";
	/** Default port number for token-based logging on Logentries API server. */
	static final int LE_PORT = 10000;
	/** Minimal delay between attempts to reconnect in milliseconds. */
	static final int MIN_DELAY = 100;
	/** Maximal delay between attempts to reconnect in milliseconds. */
	static final int MAX_DELAY = 10000;
	/** Minimal delay between checks for latest log files */
	static final int MIN_CHECK_DELAY = 1000;
	/** LE appender signature - used for debugging messages. */
	static final String LE = "LE: ";
	/** UTF-8 output character set */
	static final Charset UTF8 = Charset.forName("UTF-8");

	/** Array of Listeners to be used by Tailers*/
	private LogentriesListener[] listeners;
	/** LogentriesSocketAppender thread */
	private Thread t_SockAppender;
	/** LogChecker thread */
	private Thread t_LogChecker;

	/** Message Queue  */
	ArrayBlockingQueue<byte[]> queue;
	/** Properties File Reader */
	Properties prop;
	/** Debug flag */
	boolean debug;
	/** Number of log files to be tailed */
	int numFiles;
	/** Directory of log files for any Jelastic environment */
	String LOG_HOME_DIR;
	/** Separating character in Jelastic environment timestamps, Jetty different from Tomcat */
	String timestamp_sep;
	/** Indicating whether context has started */
	boolean started;

	@Override
	public void contextInitialized(ServletContextEvent event) {

		try{
			readConfig();
		}catch (Exception e){
			dbg("logentries.cfg failed to load");
			return;
		}

		initVars();

		if(!started)
		{
			startListeners();

			startHelpers();
		}

		started = true;
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {

		shutdownAll();
		started = false;
	}

	public void dbg(String msg)
	{
		if(debug)
			System.err.println(LE + msg);
	}

	public void readConfig() throws Exception
	{
		prop = new Properties();

		/* Load logentries config file */
		prop.load(new FileInputStream(System.getProperty("user.home") + "/logentries.cfg"));

		/* Get debug value from properties file then remove it from hashtable */
		String debug_value = prop.getProperty("debug");
		debug = debug_value.equals("true") ? true : false;
		prop.remove("debug");

		/* Set Array sizes based on number of files to be tailed */
		numFiles = prop.size();
	}

	public void initVars()
	{
		/* Initialise variables */
		queue = new ArrayBlockingQueue<byte[]>(QUEUE_SIZE);
		listeners = new LogentriesListener[numFiles];

		/* Set LOG_HOME_DIR for any of the Jelastic environments */
		String dir = System.getProperty("user.home");
		LOG_HOME_DIR = dir.substring(0, dir.lastIndexOf("/"))+"/logs/";
		dbg("LOG_HOME set to " + LOG_HOME_DIR);

		/* Jelastic environments have different format timestamps in their logfiles
		 *
		 *  Tomcat 6/7, Glassfish 3 = 2012-03-18
		 *
		 *  Jetty 6 				= 2012_03_18
		 */
		// Set timestamp separator according to environment
		timestamp_sep = dir.contains("jetty") ? "_" : "-";
	}

	public void startListeners()
	{
		/* Only logfile - token pairs left in prop */
		Enumeration<Object> keys = prop.keys();

		/* Iterate through logfile - token pairs from properties file */
		int i = 0;
		while(keys.hasMoreElements())
		{
			String fileName = keys.nextElement().toString();
			String token = prop.get(fileName).toString();

			/* Add latest timestamp to filename */
			String todayLog = addTimestamp(fileName);

			/* Add LogentriesListener to array with token and filename */
			listeners[i] = new LogentriesListener(token, LOG_HOME_DIR + todayLog);
			i++;
		}
	}

	public void startHelpers()
	{
		/* Start thread for connecting to Logentries and polling message queue */
		t_SockAppender = new Thread(new LogentriesSocketAppender());
		t_SockAppender.setName("Logentries Socket Appender");
		t_SockAppender.setDaemon(true);
		t_SockAppender.start();

		/* Start thread for checking that all files being tailed are the latest */
		// If using Glassfish3 env, LogChecker not necessary. (no timestamps)
		if(!System.getProperty("user.home").contains("glassfish3"))
		{
			t_LogChecker = new Thread(new LogChecker());
			t_LogChecker.setName("Logentries Latest Log Checker");
			t_LogChecker.setDaemon(true);
			t_LogChecker.start();
		}
	}

	public void shutdownAll()
	{
		/* Shutting down, stop all threads */
		for(int i = 0; i < listeners.length; ++i)
		{
			/* Stop the tailer thread */
			listeners[i].tail.stop();
		}
		/* Interrupt LogentriesSocketAppender */
		t_SockAppender.interrupt();
		/* Interrupt LogChecker */
		if(!System.getProperty("user.home").contains("glassfish3"))
		{
			t_LogChecker.interrupt();
		}
	}

	/** Returns the given log filename with timestamp inserted
	 *
	 * @param fileName
	 * @return
	 */

	public String addTimestamp(String fileName)
	{
		if(System.getProperty("user.home").contains("glassfish3"))
			return fileName;

		Calendar cal = Calendar.getInstance();

		String year = Integer.toString(cal.get(Calendar.YEAR));
		int monthNum = cal.get(Calendar.MONTH)+1;
		String month = Integer.toString(monthNum);
		/* Log file timestamps must have 0 in front of single digits, i.e 01 */
		month = monthNum < 10 ? "0" + month : month;

		int dayNum = cal.get(Calendar.DAY_OF_MONTH);
		String day = Integer.toString(dayNum);
		day = dayNum < 10 ? "0" + day : day;

		String timestamp = year + timestamp_sep + month + timestamp_sep + day;

		/* Split filename at '.', 'catalina.log' -> {'catalina', 'log'} */
		String temp[] = fileName.split("\\.");

		/* Recreate filename with timestamp */
		return temp[0] + "." + timestamp + "." + temp[1];
	}

	/** Returns the given full log filename with the middle timestamp removed
	 *
	 * @param fileName
	 * @return
	 */
	public String removeTimestamp(String fileName)
	{
		String[] temp = fileName.split("\\.");

		return temp[0] + "." + temp[2];
	}

	/* Internal Classes */
	/* Separate thread running in background to ensure that latest log files are being tailed */
	class LogChecker implements Runnable
	{
		final Random random = new Random();

		@Override
		public void run() {
			while (true) {
				/* Iterate through listeners, make sure they are set to latest files */
				for(int i = 0; i < listeners.length; ++i)
				{
					/* Get filename of file currently being tailed */
					String tailingNow = listeners[i].file.getName();

					String logName = removeTimestamp(tailingNow);

					/* Add timestamp to get latest possible log filename */
					String latestName = addTimestamp(logName);
					dbg("Comparing files: " + tailingNow + " <> " + latestName);
					/* If already tailing latest file, continue */
					if(tailingNow.equals(latestName)){
						continue;
					}
					File f = new File(LOG_HOME_DIR + latestName);
					/* File doesn't exist yet, continue */
					if(!f.isFile()){
						continue;
					}
					dbg("Updating existing tailer to tail latest file: " + LOG_HOME_DIR + latestName);
					// Stop thread/tailer that needs to be updated
					listeners[i].tail.stop();
					String tempToken = listeners[i].token;
					// Add new tailer to array in place of old one, with latest file
					listeners[i] = new LogentriesListener(tempToken ,LOG_HOME_DIR + latestName);
				}
				try {
					// Sleep for random number between 0 and 10 seconds
					Thread.sleep(random.nextInt(MAX_DELAY));
				} catch (InterruptedException e) {
					// No need to do anything here
				}
			}
		}
	}


	/** Tails a single log file, controlled by its own thread */
	class LogentriesListener extends TailerListenerAdapter
	{
		private Tailer tail;
		private File file;
		public Thread thread;
		/* Token that relates this log file to its destination on Logentries */
		private String token;

		public LogentriesListener(String token, String fileName)
		{
			this.token = token;
			this.file = new File(fileName);
			if(!file.exists()){
				try {
					file.createNewFile();
				} catch (IOException e) {
					dbg("Unable to create file at start-up: " + fileName);
				}
				dbg("Created new file at start-up: " + fileName);
			}
			this.thread = new Thread(new Tailer(file, this, 1, true));
			this.thread.setName(fileName);
			this.thread.setDaemon(true);
			this.thread.start();
		}

		public void init(Tailer tailer)
		{
			this.tail = tailer;
		}

		public void fileNotFound()
		{
			dbg("LogFile To Be Tailed Not Found - "+tail.getFile().getName());
		}

		public void fileRotated()
		{
			//Tailer class handles this, tries to re-open file
		}

		//Called when the Tailer detects a new line
		public void handle(String line)
		{
			try{
				byte[] data = (token+line+'\n').getBytes(UTF8);

				boolean is_full = !queue.offer(data);

				//If its full, remove latest item and try again
				if(is_full){
					dbg("Queue is full. Data: " + line);
					queue.poll();
					queue.offer(data);
				}

			}catch(Exception e){
				if(debug)
					dbg("Unable to send message to Logentries");
			}
		}

		public void handle(Exception ex)
		{
			dbg(ex.toString());
		}
	}

	/* Polls the queue, sends logs to Logentries, runs on its own thread */
	class LogentriesSocketAppender implements Runnable
	{
		private Socket socket;
		private OutputStream stream;

		final Random random = new Random();

		/**
		 * Opens connection to Logentries.
		 *
		 * @throws IOException
		 */
		void openConnection() throws IOException {
			if(debug)
				dbg( "Reopening connection to Logentries API server " + LE_API + ":" + LE_PORT);

			// Open physical connection
			socket = new Socket(LE_API, LE_PORT);
			stream = socket.getOutputStream();

			if(debug)
				dbg("Connection established");
		}

		/**
		 * Tries to opens connection to Logentries until it succeeds.
		 *
		 * @throws InterruptedException
		 */
		void reopenConnection() throws InterruptedException {
			// Close the previous connection
			closeConnection();

			// Try to open the connection until we get through
			int root_delay = MIN_DELAY;
			while (true) {
				try {
					openConnection();

					// Success, leave
					return;
				} catch (IOException e) {
					// Get information if in debug mode
					if (debug) {
						dbg("Unable to connect to Logentries");
						e.printStackTrace();
					}
				}

				// Wait between connection attempts
				root_delay *= 2;
				if (root_delay > MAX_DELAY)
					root_delay = MAX_DELAY;
				int wait_for = root_delay + random.nextInt( root_delay);
				dbg( "Waiting for " + wait_for + "ms");
				Thread.sleep( wait_for);
			}
		}

		/**
		 * Closes the connection. Ignores errors.
		 */
		void closeConnection() {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					// Nothing we can do here
				}
			}
			stream = null;
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					// Nothing we can do here
				}
			}
			socket = null;
		}

		/**
		 * Initializes the connection and starts to log.
		 *
		 */
		@Override
		public void run() {
			try {
				// Open connection
				reopenConnection();

				// Send data in queue
				while (true) {
					// Take data from queue
					byte[] data = queue.take();

					// Send data, reconnect if needed
					while (true) {
						try {
							stream.write(data);
							stream.flush();
						} catch (IOException e) {
							// Reopen the lost connection
							reopenConnection();
							continue;
						}
						break;
					}
				}
			} catch (InterruptedException e) {
				// We got interrupted, stop
				dbg("Asynchronous socket writer interrupted");
			}

			closeConnection();
		}
	}
}