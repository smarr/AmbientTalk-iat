@rem BAT script for running iat on Windows

@set JARPATH="ambienttalk2.jar;lib\antlr.jar;lib\java-getopt-1.0.13.jar"
@set AT_HOME=%PWD%

@rem invoke the IAT shell via the JVM and:
@rem - pass the AT_HOME and AT_OBJECTPATH environment variables to the JVM environment via -D
@rem - make sure to include all the jar files in the ./lib directory
@rem - invoke the main IAT class
@rem - pass any parameters to this script to IAT
@java -DAT_HOME=%AT_HOME% -DAT_OBJECTPATH=%AT_OBJECTPATH% -classpath %CLASSPATH%;%JARPATH% edu.vub.at.IAT %1 %2 %3 %4 %5 %6 %7 %8 %9