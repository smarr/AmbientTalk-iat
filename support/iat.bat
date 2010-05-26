@rem BAT script for running iat on Windows

@set AT_HOME=.
@set AT_CLASSPATH=".;%CLASSPATH%;%AT_HOME%\ambienttalk2.jar;%AT_HOME%\lib\antlr.jar;%AT_HOME%\lib\java-getopt-1.0.13.jar;%AT_HOME%\lib\jakarta-regexp-1.5.jar;%AT_HOME%\lib\jline-0.9.94.jar"

@set DEFAULT_OBJPATH="%AT_OBJECTPATH%;at=%AT_HOME%\atlib\at;applications=%AT_HOME%\atlib\applications;bridges=%AT_HOME%\atlib\bridges;frameworks=%AT_HOME%\atlib\frameworks;demo=%AT_HOME\atlib\demo;test=%AT_HOME%\atlib\test"

@rem invoke the IAT shell via the JVM and:
@rem - pass the AT_HOME and AT_OBJECTPATH environment variables to the JVM environment via -D
@rem - make sure to include all the jar files in the ./lib directory
@rem - invoke the main IAT class
@rem - pass any parameters to this script to IAT
@java -DAT_INIT=%AT_HOME%\atlib\at\init\init.at -DAT_OBJECTPATH=%DEFAULT_OBJPATH% -classpath %AT_CLASSPATH% edu.vub.at.IAT %1 %2 %3 %4 %5 %6 %7 %8 %9