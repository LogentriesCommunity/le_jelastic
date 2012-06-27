Logging To Logentries from Jelastic
===================================

You can find Jelastic specific documentation on Logentries at `logentries.com/doc/jelastic`

To setup the Logentries plugin in your Jelastic app, please follow these few steps.


Setup
====================

In the downloads section, download LogentriesServlet.java and place it in your apps src folder along with the other servlets.

Then add the following 3 lines to your web.xml file in WEB-INF

    <listener>
      <listener-class>LogentriesServlet</logentries-class>
    </listener>
  
This simply specifies that it is a Listener rather than a Servlet that will handle web requests.

Next you must download the commons.io library which contains the Tailer class used in our plugin.

Be sure to download the binaries, and place the jar file in the WEB-INF/lib folder of your app,

and add it to the buildpath.

Config files
---------------
Before deploying your app to Jelastic, download the logentries.cfg file from the Downloads Section

and upload it to your environments home folder through the Jelastic UI.

If you open this config file, you will see a list of options, currently it contains the config for each

Jelastic environment. Pending which environment you are using, remove all other lines.

Create logs on Logentries
---------------------------
The last step is to create the logs on Logentries. For this, you must go back to the Logentries UI,

click on the host which you made earlier, create a new log and give it a name representing the Jelastic logfiles.

These names are on the left hand side of the logentries.cfg file. When creating the logfile on Logentries,

choose Token TCP as the Source Type, a token id will be printed, copy and paste this into your config file

where it says FILE_TOKEN beside the appropriate filename. Repeat this step for each log file.

Save the changes and upload your app's war file. You will now receive log data on Logentries.

Troubleshooting
========================

If you encounter problems, change the debug parameter on the first line of your logentries.cfg file to true.

This will print debug messages from our plugin code which should help to solve the issue