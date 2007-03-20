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

import edu.vub.at.actors.natives.ELActor;
import edu.vub.at.actors.natives.ELVirtualMachine;
import edu.vub.at.actors.natives.NATActorMirror;
import edu.vub.at.actors.natives.SharedActorField;
import edu.vub.at.actors.net.Logging;
import edu.vub.at.eval.Evaluator;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XParseError;
import edu.vub.at.objects.ATAbstractGrammar;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.natives.SAFLobby;
import edu.vub.at.objects.natives.SAFSystem;
import edu.vub.at.objects.natives.SAFWorkingDirectory;
import edu.vub.at.parser.NATParser;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
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
 * 'system'. For more information about this object, see the OBJSystem class:
 * @see edu.vub.at.natives.OBJSystem
 * 
 * @author tvcutsem
 */
public final class IAT {

	private static final String _EXEC_NAME_ = "iat";
	private static final String _ENV_AT_OBJECTPATH_ = "AT_OBJECTPATH";
	private static final String _ENV_AT_HOME_ = "AT_HOME";
		
	protected static final Properties _IAT_PROPS_ = new Properties();
	private static String _INPUT_PROMPT_;
	private static String _OUTPUT_PROMPT_;
	
	static {
		try {
			_IAT_PROPS_.load(IAT.class.getResourceAsStream("iat.props"));
			_INPUT_PROMPT_ = _IAT_PROPS_.getProperty("inputprompt", ">");
			_OUTPUT_PROMPT_ = _IAT_PROPS_.getProperty("outputprompt", ">>");
		} catch (IOException e) {
			abort("Fatal error while trying to load internal properties: "+e.getMessage());
		}
	}
	
	// program arguments
	public static String _FILE_ARG_ = null;
	public static String[] _ARGUMENTS_ARG_ = null;
	
	public static String _INIT_ARG_ = null;
	public static String _OBJECTPATH_ARG_ = null;
	public static String _EVAL_ARG_ = null;
	public static String _NETWORK_NAME_ARG_ = null;
	
	public static boolean _PRINT_ARG_ = false;
	public static boolean _HELP_ARG_ = false;
	public static boolean _VERSION_ARG_ = false;
	public static boolean _QUIET_ARG_ = false;
	
	// the actor serving as iat's global evaluation context
	private static ELActor _evaluator;
	
	// IMPORTANT SEQUENTIAL STARTUP ACTIONS
	
	private static void parseArguments(String[] args) {
		// initialize long options
		LongOpt[] longopts = new LongOpt[] {
			new LongOpt("init", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
			new LongOpt("objectpath", LongOpt.REQUIRED_ARGUMENT, null, 'o'),
			new LongOpt("eval", LongOpt.REQUIRED_ARGUMENT, null, 'e'),
			new LongOpt("print", LongOpt.NO_ARGUMENT, null, 'p'),
			new LongOpt("network", LongOpt.REQUIRED_ARGUMENT, null, 'n'),
			new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
			new LongOpt("version", LongOpt.NO_ARGUMENT, null, 'v'),
			new LongOpt("quiet", LongOpt.NO_ARGUMENT, null, 'q')
		};
		
		Getopt g = new Getopt(_EXEC_NAME_, args, "i:o:e:n:phvq", longopts);

		int c;
		while ((c = g.getopt()) != -1) {
		     switch(c) {
		          case 'i': _INIT_ARG_ = g.getOptarg(); break;
		          case 'o': _OBJECTPATH_ARG_ = g.getOptarg(); break;
		          case 'e': _EVAL_ARG_ = g.getOptarg(); break;
		          case 'n': _NETWORK_NAME_ARG_ = g.getOptarg(); break;
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
			_FILE_ARG_ = args[firstNonOptionArgumentIdx++];
		}
		_ARGUMENTS_ARG_ = new String[args.length - firstNonOptionArgumentIdx];
		for (int i = 0; i < _ARGUMENTS_ARG_.length ; i++) {
			_ARGUMENTS_ARG_[i] = args[i + firstNonOptionArgumentIdx];
		}
	}
	
	private static void processInformativeArguments() {
		// first process the informative arguments, -h, -v
		if (_VERSION_ARG_) {
		  printVersion();
		  System.exit(0);
		}
		
		if (_HELP_ARG_) {
		  printVersion();
		  System.out.println(_IAT_PROPS_.getProperty("help", "no help available"));
		  System.exit(0);
		}
		
		if (!_QUIET_ARG_) {
			printVersion();
		}
	}
	
	/**
	 * Initializes the lobby namespace with a slot for each directory in the object path.
	 * The slot name corresponds to the last name of the directory. The slot value corresponds
	 * to a namespace object initialized with the directory.
	 * 
	 * If the user did not specify an objectpath, the default is $AT_OBJECTPATH:at=$AT_HOME/at
	 */
	private static String initObjectPathString() {
		// if -o was not used, consult the AT_OBJECTPATH environment variable
		if (_OBJECTPATH_ARG_ == null) {
			String envObjPath = System.getProperty(_ENV_AT_OBJECTPATH_);
			_OBJECTPATH_ARG_ = (envObjPath == null ? "" : (":"+envObjPath));
		}
		// always append the entry ':at=$AT_HOME/at'
		String atHome = System.getProperty(_ENV_AT_HOME_);
		if (atHome != null) {
		  _OBJECTPATH_ARG_ += ":at="+atHome+"/at";
		}
		return _OBJECTPATH_ARG_;
	}
	
	/**
	 * Given a textual description of the object path, of the form:
	 * name1=path1:name2=path2:...
	 * 
	 * creates a listing:
	 * name1 -> File(path1)
	 * name2 -> File(path2)
	 * ...
	 * 
	 * and uses the listing to initialize the lobby of the actors.
	 */
	private static final SAFLobby computeObjectPath(String objectPath) {
		// split the object path using its path separator (normally ':')
		String[] roots = objectPath.split(System.getProperty("path.separator"));
		LinkedList namedPaths = new LinkedList();
		
		// for each named file path, add an entry to the mapping
		for (int i = 0; i < roots.length; i++) {
			if (roots[i].length()==0) {
				continue; // skip empty entries
			}
			
			// extract name = path components
			String[] pair = roots[i].split("=");
			if (pair.length != 2) {
				abort("Error: invalid name=path entry on object path: " + roots[i]);
			}
			
			String name = pair[0];
			File pathfile = new File(pair[1]);
			
			// check whether the given pathfile is a directory
			if (!pathfile.isDirectory()) {
			    abort("Error: non-directory file on objectpath: " + pathfile.getAbsolutePath());
			}
			
			if (!pathfile.isAbsolute()) {
				try {
					pathfile = pathfile.getCanonicalFile();
				} catch (IOException e) {
					abort("Fatal error while constructing objectpath: " + e.getMessage());
				}
			}
			Logging.Init_LOG.info("Added entry to object path: " + name + "=" + pathfile.getPath());
			namedPaths.add(new Object[] { name, pathfile });
		}
		
		return new SAFLobby(namedPaths);
	}
	
	/*
	 * Given a main file, derives the relative directory, when no such file exists uses the
	 * "user.dir" from java.
	 */
	private static final SAFWorkingDirectory computeWorkingDirectory() {
		if (_FILE_ARG_ != null) {
			// define ~ in terms of the location of the main file

			File main = new File(_FILE_ARG_);
			if (main.exists()) {
				// if main file is valid ~ is its enclosing directory
				
				File workingDirectory = main.getParentFile();
				if(workingDirectory != null && workingDirectory.exists()) {
					return new SAFWorkingDirectory(workingDirectory);
				}
			}
		} 
		
		// In all other cases...
		return new SAFWorkingDirectory();
	}
	
	private static ATAbstractGrammar parseInitFile() throws InterpreterException {
		// first, load the proper code from the init file
		try {
			if (_INIT_ARG_ != null) {
				// the user specified a custom init file
				File initFile = new File(_INIT_ARG_);
				if (!initFile.exists()) {
					abort("Unknown init file: "+_INIT_ARG_);
				}
				return NATParser.parse(initFile.getName(), Evaluator.loadContentOfFile(initFile));
			} else {
				// use the default init file under $AT_HOME/at/init/init.at provided with the distribution
				String iatHome = System.getProperty(_ENV_AT_HOME_);
				if (iatHome == null) {
					abort("Cannot load init.at: none specified and no AT_HOME environment variable set");
				} else {
					File initFile = new File(iatHome, "at/init/init.at");
					if (initFile.exists()) {
						return NATParser.parse("init.at", new FileInputStream(initFile));	
					} else {
						abort("Cannot load init.at from default location " + initFile.getPath());
					}
				}
			}
		} catch (XParseError e) {
			handleParseError(e);
			abort("Parse error in init file, aborting");
		} catch (IOException e) {
			abort("Error reading the init file: "+e.getMessage());
		}
		
		return null;
	}
	
	/**
	 * Load the code in the main argument file or the code specified using the -e option.
	 * As a side-effect, sets the _globalContext variable to the correct global context to
	 * be used subsequently in the REPL.
	 * 
	 * @return the result of evaluating the main initialization file or the -e option; null if
	 * no main file or -e option were specified.
	 */
	private static String loadMainCode() {
		// evaluate startup code, which is either the given code (-e) or the code in the main file
		String startupCode = null;
		if (_EVAL_ARG_ != null) {
			// evaluate the -e code and disregard the main file
			startupCode = _EVAL_ARG_;
		} else if (_FILE_ARG_ != null) {
			// evaluate the main file
			File main = new File(_FILE_ARG_);
			if (!main.exists()) {
				abort("Main file does not exist: " + main.getName());
			} else {
				try {
					startupCode = Evaluator.loadContentOfFile(main);
				} catch (IOException e) {
					abort("Error reading main file: "+e.getMessage());
				}
			}
		}
		return startupCode;
	}
	
	/**
	 * Reads a single line of input, and schedules it for evaluation. The result will
	 * be printed as a result of proceed, which is invoked by the IATSynchronizer. 
	 * The continual reading is ensured as that method in its turn calls readEvalPrintLoop
	 */
	private static void readEvalPrintLoop() {
		String input;
		try {
			while ((input = readFromConsole()) != null) {
				parseAndSend(input, "console");
			}
		} catch (IOException e) {
			abort("Error reading input: "+e.getMessage());
		}
	}
	
	/**
	 * Performs the main boot sequence of the AmbientTalk VM and environment:
	 * 1) Create a virtual machine using the correct object path and actor initialisation
	 * 2) Create a new actor which knows how to interface with IAT (it knows execute: and the system object)
	 * 3) Create a barrier which allows synchronizing between an actor and the REPL
	 * 4) Ensure the barrier is informed of results by registering it as an observer
 	 * 5) Send the main code specified by the user to be executed by the actor.
	 * 
	 * An important side-effect of calling boot is that the variable _evaluator will 
	 * point to a newly create actor serving as iat's global evaluation context.
	 */
	public static void boot() {
		try {
			
			// initialize the virtual machine using object path, init file and network name
			String networkName = (_NETWORK_NAME_ARG_ == null) ?
					              ELVirtualMachine._DEFAULT_GROUP_NAME_ :
					              _NETWORK_NAME_ARG_;
			ELVirtualMachine virtualMachine =
				new ELVirtualMachine(parseInitFile(),
						             new SharedActorField[] { new SAFSystem(_ARGUMENTS_ARG_),
					                                                        computeWorkingDirectory(),
					                                                        computeObjectPath(initObjectPathString()) },
					                 networkName);
						
			// create a new actor on this vm with the appropriate main body.
			_evaluator = NATActorMirror.createEmptyActor(virtualMachine, new NATActorMirror(virtualMachine)).getFarHost();
			
			String mainCode = loadMainCode();
			
			if (mainCode != null) {
				parseAndSend(mainCode, _EVAL_ARG_ == null ? _FILE_ARG_ : "command line");
			}
		} catch (Exception e) {
			abort(e.getMessage());
		}
	}
	
	/**
	 * Startup sequence:
	 *  I) parse command-line arguments, extract properties
	 * II) check for simple -help or -version arguments
	 * 
	 * III) Boot sequence:
	 * 1) initialize the lobby using the object path (-o or default)
	 * 2) add system object to the global lexical scope
	 * 3) evaluate init file (-i or default) in context of the global scope
	 * 4) if -e was specified, then
	 *      evaluate the given code in a 'main' namespace
	 *    else if a filename was specified then
	 *      load the file and evaluate it within its 'main' namespace
	 *    else
	 *      skip
	 * 5) if -p was specified, then
	 *      print value of last evaluation
	 *      quit
	 *    else
	 * IV) enter REPL:
	 *       1) print input prompt (unless -q)
	 *       2) read input
	 *       3) parse input
	 *       4) eval input in the 'main' namespace
	 *       5) print output prompt (unless -q)
	 *       6) print value of last evaluation
	 *       
	 * @param args arguments passed to the JVM, which should all be interpreted as arguments to 'iat'
	 */
	public static void main(String[] args) {
		try {
			// parse the command-line options
			parseArguments(args);
			
			// handle -help or -version arguments
			processInformativeArguments();
			
			// enter the main boot sequence
			boot();
			
			 // if -p was specified, quit immediately
			if (_PRINT_ARG_)
				    System.exit(0);
			
			// go into the REPL
			readEvalPrintLoop();
		} catch (RuntimeException e) {
			System.err.println("Fatal error, quitting");
			e.printStackTrace();
		}
	}
	
	// AUXILIARY FUNCTIONS
	
	private static void parseAndSend(final String code, final String inFile) {
		try {
			ATAbstractGrammar ast = NATParser.parse(inFile, code);
			ATObject result = _evaluator.sync_event_eval(ast);
			
			// wait for evaluation result
			printToConsole(result.toString());
			
		} catch (XParseError e) {
			handleParseError(e);
		} catch (InterpreterException e) {
			handleATException(e);
		} catch (Exception e) {
			e.printStackTrace();
			abort("Unexpected exception: " + e.getMessage());
		}
	}
	
	private static void handleParseError(XParseError e) {
		System.out.println("parse error in "+e.getMessage());
		// try to mark the parse error on the console if that info is available
		InputStream code = e.getErroneousCode();
		
		if (code != null) {
			try {
				int lineNo = e.getLine();
				int colNo = e.getColumn();
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(code));
				int lineCount = 0;
				String line = "";
				
				// first, find the appropriate line in the source code
				while ((lineCount++ < lineNo) && (line != null)) {
					line = reader.readLine();
				}
				// was the correct line found?
				if (line != null) {
					// print the line and mark the column position with a ^
					System.out.println(line);
					// print col-1 whitespaces
					while (colNo-- > 1) {
						System.out.print(" ");
					}
					System.out.println("^");
				}
			} catch (IOException ioe) {
				// could not read from the source string, ignore further parse error handling
			}
		}
	}
	
	private static void handleATException(InterpreterException e) {
		System.out.println(e.getMessage());
		e.printAmbientTalkStackTrace(System.out);
	}
	
	private static String readFromConsole() throws IOException {
		if (!_QUIET_ARG_) {
			System.out.print(_INPUT_PROMPT_);
		}
		return IATIO._INSTANCE_.readln();
	}
	
	private static void printToConsole(String txt) {
		if (!_QUIET_ARG_) {
			System.out.print(_OUTPUT_PROMPT_);
		}
		IATIO._INSTANCE_.println(txt);
	}
	
	private static void abort(String message) {
		System.err.println(message);
		System.exit(1);
	}
	
	private static void printVersion() {
		String progname = _IAT_PROPS_.getProperty("name", "unknown program name");
		String version = _IAT_PROPS_.getProperty("version","unknown version");
		System.out.println(progname + ", version " + version);
	}
}
