Logging To Logentries from Jelastic
===================================

*This plug in will no longer be officially supported or maintained by Logentries.<br>

You can find official documentation on Logentries at <https://logentries.com/doc/jelastic>

To setup the Logentries plugin in your Jelastic app, please follow these few steps.


Setup
=====

In the downloads section, download `le_jelastic-1.0.2.jar` and place it in the `/lib` folder of your environment via Jelastic UI.

You can find this by clicking the config button on your environments options.

Then using the Jelastic UI, add the following 3 lines to your `web.xml`. This can either be your global `web.xml` found in `/server` of your environment, or in your apps `web.xml` found in `/webapps/ROOT/WEB-INF/`

    <listener>
      <listener-class>LogentriesServlet</listener-class>
    </listener>

Next you must download the `commons.io` library which contains the `Tailer` class used in our plugin.

This can be found here

    http://commons.apache.org/io/download_io.cgi

and place the jar file in the same `/lib` folder as `le_jelastic-1.0.2.jar`

Config files
------------

To configure the plugin with your app, download the `logentries.cfg` file from the Downloads Section and upload it to your environments `/home` folder through the Jelastic UI.

If you open this config file, you will see a list of options, currently it contains the config for each Jelastic environment. Pending which environment you are using, remove all other lines.

Create logfiles on Logentries
-----------------------------

The last step is to create the logfiless on Logentries. For this, you must go back to the Logentries UI, create a new host with the name Jelastic for example, create a new logfile and give it a name representing the Jelastic logfiles. These names are on the left hand side of the `logentries.cfg` file. When creating the logfile on Logentries, choose Token TCP as the Source Type, a token id will be printed, copy and paste this into your config file where it says `FILE_TOKEN` beside the appropriate filename. Repeat this step for each log file.

For this plugin to take effect, you need to restart your environment node.

Troubleshooting
===============

If you encounter problems, change the debug parameter on the first line of your `logentries.cfg` file to true.

This will print debug messages from our plugin code which should help to solve the issue.

