These are building instructions for developing the plugin.

Requirements
============

- [commons.io](https://commons.apache.org/proper/commons-io/)
- servlet-api, from servlet container

Commands
========

Compile with: `javac -cp servlet-api.jar:commons-io.jar src/LogentriesServlet.java`

Build jar with: `cd src/; jar cf le_jelastic_ingenium.jar *.class`

