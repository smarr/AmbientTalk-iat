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
import edu.vub.at.actors.natives.Packet;
import edu.vub.at.eval.Evaluator;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XParseError;
import edu.vub.at.objects.ATAbstractGrammar;
import edu.vub.at.objects.ATField;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.natives.NATIsolate;
import edu.vub.at.objects.natives.NATNamespace;
import edu.vub.at.objects.natives.NATObject;
import edu.vub.at.objects.natives.OBJSystem;
import edu.vub.at.objects.natives.grammar.AGSymbol;
import edu.vub.at.parser.NATParser;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
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
	
	private static final AGSymbol _SYSTEM_SYM_ = AGSymbol.jAlloc("system");
	
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
	
	public static boolean _PRINT_ARG_ = false;
	public static boolean _HELP_ARG_ = false;
	public static boolean _VERSION_ARG_ = false;
	public static boolean _QUIET_ARG_ = false;
	
	// the actor serving as iat's global evaluation context
	private static ELActor _evaluator;
	
	// IMPORTANT SEQUENTIAL STARTUP ACTIONS
	
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
	 * If the user did not specify an objectpath, the default is .:$AT_OBJECTPATH:$AT_HOME
	 */
	private static String initObjectPathString() {
		if (_OBJECTPATH_ARG_ == null) {
			String envObjPath = System.getProperty(_ENV_AT_OBJECTPATH_);
			String envHome = System.getProperty(_ENV_AT_HOME_);
			_OBJECTPATH_ARG_ = System.getProperty("user.dir") +
			   (envObjPath == null ? "" : (":"+envObjPath)) +
			   (envHome == null ? "" : (":"+envHome));
		}
		return _OBJECTPATH_ARG_;
	}
	
	private static NATIsolate iatInitializer() throws XParseError {
		NATIsolate isolate = new NATIsolate();
				
		try {
			// Define the System Object
			isolate.meta_defineField(_SYSTEM_SYM_, new OBJSystem(_ARGUMENTS_ARG_));
			
			if(_FILE_ARG_ != null) {
				// define ~ in terms of the location of the main file
				
				File main = new File(_FILE_ARG_);
				if (!main.exists())
					abort("Main file does not exist: " + main.getName());
				
				// ~ allows accessing the namespace around the main file
				isolate.meta_defineField(Evaluator._CURNS_SYM_, new NATNamespace("/"+main.getName(), main));
			}
			
			return isolate;
		} catch (InterpreterException e) {
			// impossible: we constructed the method by hand, so we are sure
			// that it has no illegal parameter list
			e.printStackTrace();
			System.exit(0);
			return null;
		}
	}
//	private static void initSystemObject() {
//		try {
//			Evaluator.getGlobalLexicalScope().meta_defineField(_SYSTEM_SYM_, new OBJSystem(_ARGUMENTS_ARG_));
//		} catch (XDuplicateSlot e) {
//			// should not happen because the global lexical scope is empty at this point
//			abort("Failed to initialize system object: 'system' name already bound in global scope.");
//		} catch (InterpreterException e) {
//			// should again never happen because the meta_defineField is native
//			abort("Failed to initialize system object: " + e.getMessage());
//		}
//	}
	
	
	/*
	 * Given a textual object path computes and verifies all passed entries to see
	 * whether they exist and whether they are proper directories.
	 */
	private static final File[] computeObjectPath(String objectPath) {
		// split the object path using its ':' separator
		String[] roots = objectPath.split(":");
		File[] objectPathRoots = new File[roots.length];
		
		// for each path to the lobby, add an entry for each directory in the path
		for (int i = 0; i < roots.length; i++) {
			File pathfile = new File(roots[i]);
			
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
			
			objectPathRoots[i] = pathfile;
		}
		
		return objectPathRoots;
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
				// use the default init file provided with the distribution
				InputStream initstream = IAT.class.getResourceAsStream("/edu/vub/at/init/init.at");
				return NATParser.parse("init.at", initstream);
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
	 * 
	 * @return the result of evaluating the main file or -e code; nil if none of both was specified
	 */
	public static void boot() {
		try {
			// initialize the virtual machine using object path and init file
			ELVirtualMachine virtualMachine =
				new ELVirtualMachine(computeObjectPath(initObjectPathString()), parseInitFile());
			
			// create a new actor on this vm with the appropriate main body.
			_evaluator = NATActorMirror.atValue(
					virtualMachine,
					new Packet("behaviour", iatInitializer()),
					new NATActorMirror(virtualMachine)).getFarHost();
			
			String mainCode = loadMainCode();
			
			if (mainCode != null) {
				parseAndSend(mainCode, _EVAL_ARG_ == null?_FILE_ARG_:"command line");
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
	
	private static void printObjectPath() {
		try {
			System.out.println("objectpath = " + _OBJECTPATH_ARG_);
			NATObject lobby = Evaluator.getLobbyNamespace();
			ATObject[] slots = lobby.meta_listFields().asNativeTable().elements_;
			for (int i = 0; i < slots.length; i++) {
				ATField f = (ATField) slots[i];
				System.out.print(f.base_getName().base_getText().asNativeText().javaValue);
				System.out.println("=" + f.base_readField().meta_print().javaValue);
			}
		} catch (InterpreterException e) {
			e.printStackTrace();
		}
	}
	
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
	
	private static void warn(String message) {
		System.err.println("[warning] "+message);
	}
	
	private static void printVersion() {
		String progname = _IAT_PROPS_.getProperty("name", "unknown program name");
		String version = _IAT_PROPS_.getProperty("version","unknown version");
		System.out.println(progname + ", version " + version);
	}
}
