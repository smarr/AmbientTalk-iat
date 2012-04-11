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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Properties;

import edu.vub.at.actors.eventloops.Event;
import edu.vub.at.actors.eventloops.EventLoop;
import edu.vub.at.actors.natives.ELActor;
import edu.vub.at.actors.natives.ELVirtualMachine;
import edu.vub.at.actors.natives.SharedActorField;
import edu.vub.at.eval.Evaluator;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XIOProblem;
import edu.vub.at.exceptions.XParseError;
import edu.vub.at.objects.ATAbstractGrammar;
import edu.vub.at.objects.ATClosure;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.natives.NATTable;
import edu.vub.at.objects.natives.NATText;
import edu.vub.at.objects.natives.SAFSystem;
import edu.vub.at.objects.natives.SAFWorkingDirectory;
import edu.vub.at.parser.NATParser;
import edu.vub.at.util.logging.Logger;
import edu.vub.at.util.logging.Logging;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

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
 * @see edu.vub.at.natives.NATSystem
 * 
 * @author tvcutsem
 */
public class IAT extends EmbeddableAmbientTalk {

	protected static final String _EXEC_NAME_ = "iat";
	private static final String _ENV_AT_OBJECTPATH_ = "AT_OBJECTPATH";
	protected static final String _ENV_AT_INIT_ = "AT_INIT";
	private static final String _ENV_AT_LIBPATH_ = "AT_LIBPATH";
		
	protected static final Properties _IAT_PROPS_ = new Properties();
	private static String _INPUT_PROMPT_;
	protected static String _OUTPUT_PROMPT_;
	public static String _READ_PROMPT_;	
	public static String _CONTINUATION_PROMPT_;

	/**
	 * The read-eval-print loop is modelled as an event loop with
	 * dedicated execution semantics. Whenever the event loop sits
	 * idle (i.e. when its eventQ is empty), the loop performs a
	 * read-eval-print cycle. Otherwise, it processes events from
	 * its event queue. These extra events pertain to reading characters
	 * or lines from the console.
	 * 
	 * @author tvcutsem
	 */
	public class ReadEvalPrintLoop extends EventLoop {
		private IATIO iatio_;
		
		public ReadEvalPrintLoop(IATIO iatio) {
			super("The Read-Eval-Print Loop");
			iatio_ = iatio;
		}

		public void handle(Event event) {
			event.process(this);
		}
		
		protected void execute() {
			// if Idle, perform a Read-Eval-Print cycle
			if (eventQueue_.isEmpty()) {
				try {
					String input = readFromConsole();
					if (input == null) {
						this.stopProcessing();
						return;
					} else if (input.trim().isEmpty()) {
						// ignore empty input strings
					} else if (input.startsWith(":")) {
						processShellCommand(input.substring(1));
					} else {
						if (countBalanced(input) <= 0) {
							// no outstanding opening braces, input is just one line
							evalAndPrint(input);
						} else {
							// there are outstanding opening braces, emit continuation lines
							StringBuffer multilineInput = new StringBuffer(input).append("\n");
							int diff = countBalanced(input);
							do {
								// read another line from the console, this time with a leading continuation prompt
								input = readContinuationLine(diff);
								multilineInput.append(input).append("\n");
								// perform another brace count, this time on the extended input
								diff = countBalanced(multilineInput.toString());
								// continue repeating until either the extended input balances,
								// or until the user explicitly inputs an empty line
							} while (diff != 0 && !input.isEmpty());
							// finally, evaluate the multi-line input
							evalAndPrint(multilineInput.toString());
						}
					}
				} catch (IOException e) {
					abort("Error reading input: "+e.getMessage(), e);
				}

			} else {
				// otherwise process incoming event
				super.execute();
			}
		}
		
		public void event_readLine(final ELActor owner, final ATClosure success, final ATClosure failure) {
		  receive(new Event() {
			public void process(Object eventloop) {
				try{
						 // blocking input
						String line = null;
						line = iatio_.readln(IAT._READ_PROMPT_);
				         if (line != null)
				            // success<-apply([c])
							Evaluator.trigger(owner, success, NATTable.of(NATText.atValue(line)), "readNextLine:");
				         else
				        	Evaluator.trigger(owner, success, NATTable.of(Evaluator.getNil()),"readNextLine:");
					} catch (IOException e) {
						Evaluator.trigger(owner, failure, NATTable.of(new XIOProblem(e).getAmbientTalkRepresentation()),"readNextLine:");
					}
			}
		  });
	    }
		
		private String readFromConsole() throws IOException{
			if (!IAT._QUIET_ARG_) {
				return iatio_.readln(_INPUT_PROMPT_);
			} else {
				return iatio_.readln();
			}
		}
		
		/**
		 * In quiet mode or no-jline mode, just reads a line from the Console,
		 * otherwise calculate a continuation prompt based on size diff, prompt,
		 * then read a line. The continuation prompt is of the form
		 * '.   ', where '.' is the contination prompt and
		 * the number of spaces printed is specified by the diff parameter
		 */
		private String readContinuationLine(int diff) throws IOException{
			if (!IAT._QUIET_ARG_ && !IAT._NO_JLINE_ARG_) {
				char[] spaces = new char[diff];
				Arrays.fill(spaces, ' ');
				return iatio_.readln(_CONTINUATION_PROMPT_ + new String(spaces));
			} else {
				return iatio_.readln();
			}
		}
	}
	
	public ReadEvalPrintLoop repl_;
	public IATIO iatio_;

	/**
	 * Performs the main boot sequence of iat and the AmbientTalk VM.
	 * Startup sequence:
	 *  I) parse command-line arguments, extract properties
	 *  II) initialize the i/o (jline or default depending on -nojline flag).
	 * III) check for simple -help or -version arguments
	 * 
	 * IV) Boot sequence:
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
	 * 6) boot sequence for AmbientTalkVM and environment:
	 * 		1) Create a virtual machine using the correct object path and actor initialisation
	 * 		2) Create a new actor which knows how to interface with IAT (it knows execute: and the system object)
	 * 		3) Create a barrier which allows synchronizing between an actor and the REPL
	 * 		4) Ensure the barrier is informed of results by registering it as an observer
 	 * 		5) Send the main code specified by the user to be executed by the actor.
	 * 
	 * An important side-effect of booting is that the variable _evaluator will 
	 * point to a newly create actor serving as iat's global evaluation context 
	 * and iatio_ will point to the i/o output used by iat and interpreter.
	 * 
	 * V) enter REPL:
	 *       1) print input prompt (unless -q)
	 *       2) read input
	 *       3) parse input
	 *       4) eval input in the 'main' namespace
	 *       5) print output prompt (unless -q)
	 *       6) print value of last evaluation
	 *       
	 * @param args arguments passed to the JVM, which should all be interpreted as arguments to 'iat'
	 * @throws InterpreterException
	 */
	public IAT(String[] args) throws InterpreterException{
		this(args, null);
	}
	
	protected IAT(String[] args, IATIO iatio) throws InterpreterException {
		// I) parse the command-line options
		parseArguments(args);
		
		// II) initialize i/o used by AmbientTalk before any task that may use the i/o
		if (iatio == null) {
			initializeIATIO();
		} else {
			iatio_ = iatio;
		}
		
		// III) handle -help or -version arguments
		processInformativeArguments();
		
		// IV) boot sequence: 

		//boot the virtual machine and evaluator actor.
		repl_ = new ReadEvalPrintLoop(iatio_);
		// use the super method to initialize a virtual machine and evaluator actor 
		super.initialize(	parseInitFile(),
				new SharedActorField[] {
			computeSystemObject(_ARGUMENTS_ARG_),
			computeWorkingDirectory(),
			computeObjectPath(initObjectPathString()) },
			(_NETWORK_NAME_ARG_ == null) ?
					ELVirtualMachine._DEFAULT_GROUP_NAME_ :
						_NETWORK_NAME_ARG_,
						(_IP_ADDRESS_ARG_ == null) ?
							       ELVirtualMachine._DEFAULT_IP_ADDRESS_ :
							       _IP_ADDRESS_ARG_);

		// evaluate the main code within the newly created shell
		loadMainCode();

		// if -p was specified, quit immediately
		if (_PRINT_ARG_)
			System.exit(0);

		// V) go into the REPL
		startReadEvalPrintLoop();
	}

	// program arguments
	public static String _FILE_ARG_ = null;
	public static String[] _ARGUMENTS_ARG_ = null;
	
	public static String _INIT_ARG_ = null;
	public static String _OBJECTPATH_ARG_ = null;
	public static String _EVAL_ARG_ = null;
	public static String _NETWORK_NAME_ARG_ = null;
	public static String _IP_ADDRESS_ARG_ = null;
	public static String _LOG_LEVEL_ARG_ = null;
	
	public static boolean _PRINT_ARG_ = false;
	public static boolean _HELP_ARG_ = false;
	public static boolean _VERSION_ARG_ = false;
	public static boolean _QUIET_ARG_ = false;
	public static boolean _NO_JLINE_ARG_ = false;
	public static boolean _DEBUG_ARG_ = false;

	
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
			new LongOpt("quiet", LongOpt.NO_ARGUMENT, null, 'q'),
			new LongOpt("nojline", LongOpt.NO_ARGUMENT, null, 'j'),
			new LongOpt("ip", LongOpt.REQUIRED_ARGUMENT, null, 'a'),
			new LongOpt("Xdebug", LongOpt.NO_ARGUMENT, null, 'd'),
			new LongOpt("log", LongOpt.REQUIRED_ARGUMENT, null, 'l')
		};
		
		Getopt g = new Getopt(_EXEC_NAME_, args, "i:o:e:n:a:l:phvqjd:", longopts, true);

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
		          case 'j': _NO_JLINE_ARG_ = true;break;
		          case 'a': _IP_ADDRESS_ARG_ = g.getOptarg(); break;
		          case 'd': _DEBUG_ARG_ = true;break;
		          case 'l': _LOG_LEVEL_ARG_ = g.getOptarg(); break;
		          case '?':
		        	   // getopt() already printed an error
		        	   throw new Error("There were illegal options, quittING.");
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

	
	
	protected void processInformativeArguments() {
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
		
		if (_LOG_LEVEL_ARG_ != null){
			//initialize the logging framework
			computeLogProperties();
		}
		
	}
	
	static {
		// First read the property file
		try {
			_IAT_PROPS_.load(IAT.class.getResourceAsStream("iat.props"));
			_INPUT_PROMPT_ = _IAT_PROPS_.getProperty("inputprompt", ">");
			_OUTPUT_PROMPT_ = _IAT_PROPS_.getProperty("outputprompt", ">>");
			_READ_PROMPT_ = _IAT_PROPS_.getProperty("readprompt", "<<");
			_CONTINUATION_PROMPT_ = _IAT_PROPS_.getProperty("continuationprompt", ".");
		} catch (IOException e) {
			System.err.println("Fatal error while trying to load internal properties: "+e.getMessage());
		}
	}
	
	/**
	 * Initializes the lobby namespace with a slot for each directory in the object path.
	 * The slot name corresponds to the last name of the directory. The slot value corresponds
	 * to a namespace object initialized with the directory.
	 * 
	 * The object path always ends in the 'AT_LIBPATH" which is set by the 'iat' script to equal
	 * "at=$AT_HOME/atlib/at:demo=$AT_HOME/atlib/demo:..."
	 * 
	 * If the user did not specify an objectpath using -o, a default object path
	 * is constructed based on the environment variable $AT_OBJECTPATH
	 */
	protected static String initObjectPathString() {
		// if -o was not used, consult the AT_OBJECTPATH environment variable instead
		if (_OBJECTPATH_ARG_ == null) {
			_OBJECTPATH_ARG_ = File.pathSeparator + System.getProperty(_ENV_AT_OBJECTPATH_, "");
		}
		// always append the root atlib directories
		// 'iat' automatically passes all of these atlib/* dirs in the 'AT_LIBPATH' env var
		return _OBJECTPATH_ARG_ + File.pathSeparator + System.getProperty(_ENV_AT_LIBPATH_, "");
	}
	
	/*
	 * Initializes the I/O functionality of IAT
	 * either jline or standard i/o
	 * 
	 */
	protected void initializeIATIO() {
		if (_NO_JLINE_ARG_) {
			iatio_ = IATIOStandard._INSTANCE_;
		}else{
			iatio_ = IATIOJline._INSTANCE_;
		}
	}

	protected String getInitDebuggerCode(File initFile) {
		try{
			// initDebugger.at adds the behaviour slot to the default actor mirror so that actors can be debuggeable
			String initFilePath = initFile.getAbsolutePath();
			String initDebugPath = initFilePath.replaceFirst("init.at", "initDebugger.at");
			File initDebugFile = new File (initDebugPath);
			if (initDebugFile == null) {
				abort("Cannot load initDebugger.at: " + initDebugPath, null);
			} else { 
				return Evaluator.loadContentOfFile(initDebugFile);		
			}
		} catch (IOException e) {
			abort("Error reading the initDebugger file: "+e.getMessage(), e);
		}

		return null;
	}
	
	protected ATAbstractGrammar parseInitFile() throws InterpreterException {
		// first, load the proper code from the init file
		try {
			if (_INIT_ARG_ != null) {
				// the user specified a custom init file
				File initFile = new File(_INIT_ARG_);
				if (!initFile.exists()) {
					abort("Unknown init file: "+_INIT_ARG_, null);
				}
				String initCode = Evaluator.loadContentOfFile(initFile);
				if (_DEBUG_ARG_) {
					 String initDebugCode = getInitDebuggerCode(initFile);
					 return NATParser.parse(initFile.getName(),  initCode + initDebugCode);
				} else{
				  return NATParser.parse(initFile.getName(), initCode);
				}
			} else {
				// use the default init file under $AT_INIT provided with the distribution
				String defaultInit = System.getProperty(_ENV_AT_INIT_);
				if (defaultInit == null) {
					abort("Cannot load init.at: none specified and no AT_INIT environment variable set", null);
				} else {
					File initFile = new File(defaultInit);
					if (initFile.exists()) {
						String initCode = Evaluator.loadContentOfFile(initFile);
						if (_DEBUG_ARG_) {
							 String initDebugCode = getInitDebuggerCode(initFile);
							 return NATParser.parse(initFile.getName(), initCode + initDebugCode );
						} else{
							//new BufferedInputStream(new FileInputStream(initFile));
						  return NATParser.parse(initFile.getName(), initCode);	
						}
					} else {
						abort("Cannot load init.at from default location " + initFile.getPath(), null);
					}
				}
			}
		} catch (XParseError e) {
			handleParseError(null, e);
			abort("Parse error in init file, aborting", e);
		} catch (IOException e) {
			abort("Error reading the init file: "+e.getMessage(), e);
		}
		
		return null;
	}
	
	/**
	 * Load the code in the main argument file or the code specified using the -e option.
	 * As a side-effect, sets the scriptSource variable to reflect where the code was taken
	 * from.
	 * <p>
	 * Subsequently evaluates the main code that was read and prints the result of evaluation 
	 * on the console.
	 */
	protected void loadMainCode() {
		// evaluate startup code, which is either the given code (-e) or the code in the main file
		if (_EVAL_ARG_ != null) {
			// the executed script is provided via the command line
			scriptSource_ = "commandline";

			// evaluate the -e code and disregard the main file
			evalAndPrint(_EVAL_ARG_);			
		} else if (_FILE_ARG_ != null) {
			loadCodeFromFile(_FILE_ARG_);
		}
	}
	
	protected void loadCodeFromFile(String sourcePath) {
		// evaluate the main file
		File source = new File(sourcePath.trim());
		if (!source.exists()) {
			abort("File does not exist: " + source.getAbsolutePath(), null);
		} else {
			try {
				// the executed script is contained in the provided file
				scriptSource_ = source.getCanonicalPath();
				
				String sourceCode = Evaluator.loadContentOfFile(source);
				evalAndPrint(sourceCode);	
			} catch (IOException e) {
				abort("Error reading file or printing evaluation result: "+e.getMessage(), e);
			}
		}
	}
		
	/**
	 * Reads a single line of input, and schedules it for evaluation. The scheduling is performed by
	 * calling the {@link ELActor#sync_event_eval(ATAbstractGrammar)} method on the evaluator_ actor.
	 * This allows scheduling a parse tree for execution and implicitly synchronizes upon the event
	 * until the result is available. As such, this method can be written as an ordinary loop.
	 */
	protected void startReadEvalPrintLoop() {
		scriptSource_ = "REPL";
		repl_.start();
		
		/*String input;
		try {
			while ((input = readFromConsole()) != null) {
				evalAndPrint(input, System.out);
			}
		} catch (IOException e) {
			abort("Error reading input: "+e.getMessage(), e);
		}*/
	}
	/**
	 * @see {@link #IAT(String[])}       
	 * @param args arguments passed to the JVM, which should all be interpreted as arguments to 'iat'
	 * @throws InterpreterException
	 */
	public static void main(String[] args) {		
		try {
			new IAT(args);
		} catch (Exception e) {
			System.err.println("Fatal error, quitting");
			e.printStackTrace();
		}
	}
	
	
	// AUXILIARY FUNCTIONS
	
	protected ATObject handleParseError(String script, XParseError e) {
		iatio_.println("parse error in "+e.getMessage());
		// try to mark the parse error on the console if that info is available
		InputStream code = e.getErroneousCode();

		if (code != null) {

			int lineNo = e.getLine();
			int colNo = e.getColumn();

			BufferedReader reader = new BufferedReader(new InputStreamReader(code));
			int lineCount = 0;
			String line = "";

			// first, find the appropriate line in the source code
			while ((lineCount++ < lineNo) && (line != null)) {
				try {
					line = reader.readLine();
				} catch (IOException ioe) {
					// could not read from the source string, ignore further parse error handling
				}
			}
			// was the correct line found?
			if (line != null) {
				// print the line and mark the column position with a ^
				iatio_.println(line);
				// print col-1 whitespaces
				while (colNo-- > 1) {
					iatio_.print(" ");
				}
				iatio_.println("^");
			}

		}
		return Evaluator.getNil();
	}
	
	protected ATObject handleATException(String script, InterpreterException e) {
		iatio_.println(e.getMessage());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		e.printAmbientTalkStackTrace(ps);
		try {
			String content = baos.toString("UTF8");
			iatio_.println(content);
		} catch (UnsupportedEncodingException e1) {
			Logging.Actor_LOG.error("unnable to print exception from handleATException");
		}	
		return Evaluator.getNil();
	}
	
	public void evalAndPrint(String script) {
		String result = parseSendAndPrint(script);
		if (!_QUIET_ARG_) {
			iatio_.print(_OUTPUT_PROMPT_);
		}
		iatio_.println(result);
	}
	
	// return 0 if everything goes fine, 1 otherwise
	public int softReset(){
		try {
			ATAbstractGrammar initCode = parseInitFile();
			super.reinitialize(initCode);
			// show info if not quiet version
			if (!_QUIET_ARG_) {
				printVersion();
			}
			return 0;
		} catch (InterpreterException e) {
			Logging.Init_LOG.error("error while parsing init file:", e);
			return 1;
		} catch (Exception e2) {
			Logging.Init_LOG.error("fatal error while reinitializing the VM and evaluator actor:", e2);
			return 1;
		}
	}
	
	protected void abort(String message, Exception e) {
		System.out.println(message);
		System.exit(1);
	}
	
	public SharedActorField computeSystemObject(Object[] arguments) {
		return new SAFSystem(this, (String[])arguments);
	}
	
	public SharedActorField computeWorkingDirectory() {
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
		return super.computeWorkingDirectory();
	}
	
	public IATIO getIatio(){return iatio_;}
		
	private void printVersion() {
		String progname = _IAT_PROPS_.getProperty("name", "unknown program name");
		String version = _IAT_PROPS_.getProperty("version","unknown version");
		iatio_.println(progname + ", version " + version);
	}
	
	private void processShellCommand(String command) {
		if (command.equals("q") || command.equals("quit")) {
			System.exit(0);
		} else if (command.startsWith("l ") || command.startsWith("load ")) {
			String fileName = command.substring(command.indexOf(" ")+1);
			System.out.println("Loading " + fileName);
			loadCodeFromFile(fileName);
		} else {
			System.out.println("Unknown command: " + command);
		}
	}
	
	/**
	 * @return the difference between the number of opening '(', '{' and '[' tokens
	 * and the number of closing ')', '}', ']' tokens in the input string.
	 */
	public static int countBalanced(String input) {
		int open = input.replaceAll("[^(\\[{]","").length();
		int close = input.replaceAll("[^)\\]}]","").length();
		return open - close;
	}
	
	/**
	 * Helper function which is able to transform a textual description of the log properties, of the form:
	 * <pre>
	 * key=value:key2=value2:...
	 * 
	 * where key = the name of a logger and value = a priority level (e.g. WARN, DEBUG, ...)
	 * 
	 * creates a listing:
	 * key -> value
	 * key -> value2
	 * ...
	 * 
	 * and uses the listing to initialize the logging support.
	 * </pre>
	 * 
	 * @see EmbeddableAmbientTalk#computeObjectPath(String) which works in a similar way.
	 * @see EmbeddableAmbientTalk#abort(String, Exception) which is called to report exceptions occurring while
	 * transforming the object root.
	 * 
	 */
	protected static void computeLogProperties() {
		
		// force the loading of the Logging class so that the default logs are initialized before the changes.
		Logging var = new Logging();

		// This code works similar to EmbeddabeAmbientTalk.computeObjectPath to extract the key=value pairs.
		// We need to update both codes if changes required.
		String[] logProps = _LOG_LEVEL_ARG_.split(pathSeparatorRegExp);
		for (int i = 0; i < logProps.length; i++) {
			if (logProps[i].length()==0) {
				continue; // skip empty entries
			}
			// extract key = value components
			String[] pair = logProps[i].split(equalsRegExp);
			if (pair.length != 2) {
				System.out.println("Error: invalid KEY=VALUE entry on log properties: " + logProps[i]);
				System.exit(1);
			}

			String key = pair[0];
			String value = pair[1];

			if (key.equals("all")) {
				// set the same value for all instances
				Object[] logs = Logger.getAllInstances();
				for (int j = 0; j < logs.length; j++) {
					Logger log = (Logger) logs[j];
					log.setPriority(value);
				}
			} else{
				// set only for one instance with name = value.
				Logger.getInstance(key).setPriority(value);
			}

		}

	}
}
