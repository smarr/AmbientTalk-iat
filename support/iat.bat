@rem BAT script for running iat on Windows

@rem cd /d %0\..
@pushd %0\..

@set AT_HOME=%CD%

@set AT_CLASSPATH="%AT_HOME%;%CLASSPATH%;%AT_HOME%\ambienttalk2.jar;%AT_HOME%\jlib\antlr.jar;%AT_HOME%\jlib\java-getopt-1.0.13.jar;%AT_HOME%\jlib\jline-0.9.94.jar"

@set DEFAULT_OBJPATH="at=%AT_HOME%\atlib\at;applications=%AT_HOME%\atlib\applications;bridges=%AT_HOME%\atlib\bridges;frameworks=%AT_HOME%\atlib\frameworks;demo=%AT_HOME%\atlib\demo;test=%AT_HOME%\atlib\test"

@rem invoke the IAT shell via the JVM and:
@rem - pass the AT_HOME and AT_OBJECTPATH environment variables to the JVM environment via -D
@rem - make sure to include all the jar files in the ./jlib directory
@rem - invoke the main IAT class
@rem - pass any parameters to this script to IAT

@java -DAT_HOME="%AT_HOME%" -DAT_INIT="%AT_HOME%\atlib\at\init\init.at" -DAT_OBJECTPATH=%AT_OBJECTPATH% -DAT_LIBPATH=%DEFAULT_OBJPATH% -classpath %AT_CLASSPATH% edu.vub.at.IAT %*
@popd