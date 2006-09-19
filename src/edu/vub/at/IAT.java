/**
 * AmbientTalk/2 Project
 * IAT.java created on 19-sep-2006 at 21:33:07
 * (c) Programming Technology Lab, 2006 - 2007
 * Authors: Tom Van Cutsem & Stijn Mostinckx
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package edu.vub.at;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.IOException;
import java.util.Properties;

/**
 * IAT is the main entry point for the 'iat' Interactive AmbientTalk shell.
 * 
 * usage: iat [options] [file] [arguments]
 *
 * Environment variables used by iat:
 * AT_HOME: path to the AmbientTalk home directory (for native libraries)
 * AT_OBJECTPATH: default objectpath to use (is prefixed by '.' and suffixed by AT_HOME by the interpreter)
 *
 * Command Line Options:
 * -i, --init init-file: specifies which file to load as the preamble of the language
 *  (the content of this file is evaluated in the context of the global lexical root)
 * -o, --objectpath objectpath: specifies the object-path, a list of directories separated by ';' which contain the necessary libraries
 * -e, --eval codestring: evaluates the given codestring and ignores the filename 
 * -p, --print print value of last evaluated expression, then quit instead of entering REPL
 * -h, --help display help, then quit
 * -v, --version display version information, then quit
 * -q, --quiet quiet mode - don't print welcome message or any prompts
 * 
 * Program arguments:
 * an optional filename and optional arguments to the script
 *
 * Program behaviour:
 * iat evaluates the code in the argument file, if one is given, then enters a read-eval-print loop
 * (unless -p was specified, in which case it prints out the last evaluated expression and quits)
 * 
 * During execution of the iat program, the AmbientTalk Lexical root contains an object called
 * 'system' with the following methods:
 * def system := object: {
 *   def exit() { quits iat }
 *   def print(txt) { print string to standard output }
 *   def println(txt) { print(txt + '\n') }
 *   def read() { read character from standard input }
 *   def readln() { read next line from input }
 *   def reset() { reset VM into fresh startup state and re-evaluates init file }
 *   def reload() { evaluate the argument file again }
 *   def args() { returns the table of extra command-line arguments passed to iat }
 * }
 * 
 * @author tvcutsem
 */
public final class IAT {

	private static final String _EXEC_NAME_ = "iat";
	
	private static final Properties _IAT_PROPS_ = new Properties();
	
	static {
		try {
			_IAT_PROPS_.load(IAT.class.getResourceAsStream("iat.props"));
		} catch (IOException e) {
			System.out.println("Fatal error while trying to load internal properties: "+e.getMessage());
			System.exit(1);
		}
	}
	
	// program arguments
	public static String _FILE_ARG_ = null;
	public static String[] _ARGUMENTS_ARG_ = null;
	
	public static String _INIT_ARG_ = null;
	public static String _OBJECTPATH_ARG_ = null;
	public static String _EVAL_ARG_ = null;
	
	public static boolean _PRINT_ARG_ = false;
	public static boolean _HELP_ARG_ = false;
	public static boolean _VERSION_ARG_ = false;
	public static boolean _QUIET_ARG_ = false;
	
	
	private static void parseArguments(String[] args) {
		// initialize long options
		LongOpt[] longopts = new LongOpt[7];
		longopts[0] = new LongOpt("init", LongOpt.REQUIRED_ARGUMENT, null, 'i');
		longopts[1] = new LongOpt("objectpath", LongOpt.REQUIRED_ARGUMENT, null, 'o');
		longopts[2] = new LongOpt("eval", LongOpt.REQUIRED_ARGUMENT, null, 'e');
		longopts[3] = new LongOpt("print", LongOpt.NO_ARGUMENT, null, 'p');
		longopts[4] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h');
		longopts[5] = new LongOpt("version", LongOpt.NO_ARGUMENT, null, 'v');
		longopts[6] = new LongOpt("quiet", LongOpt.NO_ARGUMENT, null, 'q');
		
		Getopt g = new Getopt(_EXEC_NAME_, args, "i:o:e:phvq", longopts);

		int c;
		String arg;
		while ((c = g.getopt()) != -1) {
		     switch(c) {
		          case 'i': _INIT_ARG_ = g.getOptarg(); break;
		          case 'o': _OBJECTPATH_ARG_ = g.getOptarg(); break;
		          case 'e': _EVAL_ARG_ = g.getOptarg(); break;
		          case 'p': _PRINT_ARG_ = true; break;
		          case 'h': _HELP_ARG_ = true; break;
		          case 'v': _VERSION_ARG_ = true; break;
		          case 'q': _QUIET_ARG_ = true; break;
		          case '?':
		        	   // getopt() already printed an error
		        	   System.out.println("There were illegal options, quitting.");
		        	   System.exit(1);
		          default:
		            System.err.print("getopt() returned " + c + "\n");
		       }
		}
		int firstNonOptionArgumentIdx = g.getOptind();
		if (firstNonOptionArgumentIdx < args.length) {
			// a file name to load was passed
			_FILE_ARG_ = args[firstNonOptionArgumentIdx];
		}
		_ARGUMENTS_ARG_ = new String[args.length - firstNonOptionArgumentIdx];
		for (int i = 0; i < _ARGUMENTS_ARG_.length ; i++) {
			_ARGUMENTS_ARG_[i] = args[i + firstNonOptionArgumentIdx + 1];
		}
	}
	
	private static void processInformativeArguments() {
		// first process the informative arguments, -h, -v
		if (_VERSION_ARG_) {
		  printVersion();
		  System.exit(0);
		}
		
		if (_HELP_ARG_) {
		  System.out.println(_IAT_PROPS_.getProperty("help", "no help available"));
		  System.exit(0);
		}
	}
	
	private static void printVersion() {
		String progname = _IAT_PROPS_.getProperty("name", "unknown program name");
		String version = _IAT_PROPS_.getProperty("version","unknown version");
		System.out.println(progname + ", version " + version);
	}
	
	/**
	 * @param args arguments passed to the JVM, which should all be interpreted as arguments to 'iat'
	 */
	public static void main(String[] args) {
		parseArguments(args);
		processInformativeArguments();
		
		if (!_QUIET_ARG_) {
			printVersion();
		}
		
		/*
		 * Startup sequence:
		 * 1) initialize the lobby using the object path (-o or default)
		 * 2) add system object to the lexical root
		 * 3) evaluate init file (-i or default) in context of lexical root
		 * 4) if -e was specified, then
		 *      evaluate the given code
		 *    else if a filename was specified then
		 *      load the file and evaluate it within its own namespace
		 *    else
		 *      skip
		 * 5) if -p was specified, then
		 *      print value of last evaluation
		 *      quit
		 *    else
		 *      enter REPL:
		 *       1) print input prompt (unless -q)
		 *       2) read input
		 *       3) parse input
		 *       4) eval input in global file namespace
		 *       5) print output prompt (unless -q)
		 *       6) print value of last evaluation
		 */

	}
}
