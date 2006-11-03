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
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import edu.vub.at.actors.ATActor;
import edu.vub.at.actors.ATAsyncMessage;
import edu.vub.at.actors.beholders.BHActor;
import edu.vub.at.actors.events.EActorDelete;
import edu.vub.at.actors.events.EMailboxAdd;
import edu.vub.at.actors.events.EMailboxRemove;
import edu.vub.at.actors.events.EMsgProcess;
import edu.vub.at.actors.natives.NATActor;
import edu.vub.at.actors.natives.NATVirtualMachine;
import edu.vub.at.eval.Evaluator;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XDuplicateSlot;
import edu.vub.at.exceptions.XParseError;
import edu.vub.at.objects.ATAbstractGrammar;
import edu.vub.at.objects.ATClosure;
import edu.vub.at.objects.ATField;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.grammar.ATBegin;
import edu.vub.at.objects.natives.NATAsyncMessage;
import edu.vub.at.objects.natives.NATClosure;
import edu.vub.at.objects.natives.NATContext;
import edu.vub.at.objects.natives.NATMethod;
import edu.vub.at.objects.natives.NATNamespace;
import edu.vub.at.objects.natives.NATNil;
import edu.vub.at.objects.natives.NATObject;
import edu.vub.at.objects.natives.NATTable;
import edu.vub.at.objects.natives.NATText;
import edu.vub.at.objects.natives.OBJSystem;
import edu.vub.at.objects.natives.grammar.AGBegin;
import edu.vub.at.objects.natives.grammar.AGDefField;
import edu.vub.at.objects.natives.grammar.AGSymbol;
import edu.vub.at.parser.NATParser;
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
 * Synchronisation:
 * IAT interacts with an interface actor which will perform the evaluation on its behalf.
 * This implies that IAT will schedule asynchronous messages in the inbox of that actor,
 * and subsequently the thread will be blocked waiting for a result. To be notified when 
 * the message it scheduled in the queue was processed (and the IAT thread can be resumed)
 * IAT registers itself as an actor beholder on its interface actor.
 * 
 * During execution of the iat program, the AmbientTalk Lexical root contains an object called
 * 'system'. For more information about this object, see the OBJSystem class.
 * 
 * @see edu.vub.at.actors.beholders.BHActor
 * @see edu.vub.at.natives.OBJSystem
 * 
 * @author tvcutsem
 */
public final class IAT implements BHActor {
	
	public static IAT _INSTANCE_;
	
	private static final String _EXEC_NAME_ = "iat";
	private static final String _ENV_AT_OBJECTPATH_ = "AT_OBJECTPATH";
	private static final String _ENV_AT_HOME_ = "AT_HOME";
	
	private static final AGSymbol _SYSTEM_SYM_ = AGSymbol.alloc("system");
	
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
	
	private static NATVirtualMachine _iat_vm;
	
	// The actor which evaluates all code entered in the REPL.
	// Set to a correctly created actor with correct lobby, init and optionally maincode by boot()
	private static NATActor _interfaceActor;
	
	// Used to synchronise between the interface actor and this interface
	private static ATAsyncMessage _waitingFor = null;

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
		
		// default object path
		if (_OBJECTPATH_ARG_ == null) {
			String envObjPath = System.getProperty(_ENV_AT_OBJECTPATH_);
			String envHome = System.getProperty(_ENV_AT_HOME_);
			_OBJECTPATH_ARG_ = System.getProperty("user.dir") +
			(envObjPath == null ? "" : (";"+envObjPath)) +
			(envHome == null ? "" : (";"+envHome));
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
	 * Schedules the code passed in the main argument file or specified using the -e option.
	 * for execution by the interface actor. If the code was passed in a file, the command
	 * execute: inNameSpaceObject: is used instead of the default execute: method.
	 */
	private String readMainCode() {
		// evaluate startup code, which is either the given code (-e) or the code in the main file
		if(_EVAL_ARG_ != null) {
			// evaluate the -e code and disregard the main file
			return _EVAL_ARG_;
		} else if(_FILE_ARG_ != null) {
			File main = new File(_FILE_ARG_);
			if (!main.exists()) {
				abort("Main file does not exist: " + main.getName());
			} else {
				try {
					return Evaluator.loadContentOfFile(main);
				} catch (IOException e) {
					abort("Error reading main file: "+e.getMessage());
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Continually reads input, evaluates it and (through the invervention of 
	 * base_onMessageProcessed) prints out the result. The interface actor with 
	 * which the program communicates is specified in the variable _interfaceActor.
	 */
	private void readEvalPrintLoop() {
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
	 * 1) (performed automatically by edu.vub.at.Evaluator): create a global lexical scope and a lobby
	 * 2) initialize the lobby using the specified or default object path
	 * 3) load the specified or default init file and evaluate it
	 * 4) load the specified main file or -e code and evaluate it (in the global context)
	 * 
	 * An important side-effect of calling boot is that the variable _globalContext will point
	 * to the correct global evaluation context to use (e.g. in the REPL)
	 * 
	 * @return the result of evaluating the main file or -e code; nil if none of both was specified
	 */
	public IAT() {
		
		_INSTANCE_ = this;
		
		// Initialises a virtual machine with an object path and initialisation file
		// which will be used by every actor created on the particular virtual machine
		try {
			
			_iat_vm = NATVirtualMachine.createVirtualMachine(
					NATText.atValue(_OBJECTPATH_ARG_),
					NATText.atValue(_INIT_ARG_));
			
			boot();
		} catch (InterpreterException e) {
			abort(e.getMessage());
		}
	}
	
	public void boot() throws InterpreterException {
		_interfaceActor = new NATActor(_iat_vm, actorInitializationCode());
		
		_interfaceActor.base_registerBeholder(this);
		
		printObjectPath();
		
		String mainCode = readMainCode();
		if(mainCode != null) {
			parseAndSend(mainCode, _FILE_ARG_ == null ? "option" : _FILE_ARG_);	
		}
}
	
	/*
	 * Defines a closure which will initialise the interface actor such that it has
	 * a system object, a method execute: to allow IAT to interact with it and if a 
	 * main code file was passed in, it will bind the ~ operator to the according scope.
	 */
	private ATClosure actorInitializationCode() throws XParseError {
		ATBegin executorCode;
		
		if(_FILE_ARG_ != null) {
			File main = new File(_FILE_ARG_);
			
			if (!main.exists())
				abort("Main file does not exist: " + main.getName());
			
 			executorCode = new AGBegin(new NATTable(new ATObject[] {
					// System Object
					new AGDefField(_SYSTEM_SYM_, new OBJSystem(_ARGUMENTS_ARG_)),
					// ~ allows accessing the namespace around the main file
					new AGDefField(Evaluator._CURNS_SYM_, new NATNamespace("/"+main.getName(), main)),
					// to execute code inside the scope of an actor
					NATParser.parse("iat-executor", "def execute: code { eval: code in: self };")}));
		} else {
			// if no mainfile is passed there is no useful semantics for ~, hence it is not defined
			executorCode = new AGBegin(new NATTable(new ATObject[] {
					// System Object
					new AGDefField(_SYSTEM_SYM_, new OBJSystem(_ARGUMENTS_ARG_)),
					// to execute code inside the scope of an actor
					NATParser.parse("iat-executor", "def execute: code { eval: code in: self };")}));
		}
		
		return new NATClosure(
				new NATMethod(
						AGSymbol.alloc("executor"),
						NATTable.EMPTY,
						executorCode),
						// TODO(scoping for actors) Context of the closure is irrelevant since it is replaced by the NATActor
						new NATContext(null,null,null));
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
		// parse the command-line options
		parseArguments(args);
		
		// handle -help or -version arguments
		processInformativeArguments();
		
		// enter the main boot sequence
		IAT instance = new IAT();
				
         // if -p was specified, quit immediately
	    if (_PRINT_ARG_)
	    	    System.exit(0);
		
        // go into the REPL
		instance.readEvalPrintLoop();
	}
	
	// AUXILIARY FUNCTIONS
	
	private void printObjectPath() {
		try {
			if(!_QUIET_ARG_) {
				System.out.println("objectpath = " + _OBJECTPATH_ARG_);
				NATObject lobby = Evaluator.getLobbyNamespace();
				ATObject[] slots = lobby.meta_listFields().asNativeTable().elements_;
				for (int i = 0; i < slots.length; i++) {
					ATField f = (ATField) slots[i];
					System.out.print(f.base_getName().base_getText().asNativeText().javaValue);
					System.out.println("=" + f.base_getValue().meta_print().javaValue);
				}
			}
		} catch (InterpreterException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Given a String of code, creates a parsetree and schedules it for execution with
	 * the interface actor. As a side effect the thread blocks waiting for the result 
	 * of the message it has just sent. The thread will be unblocked when base_onMessage-
	 * Processed is called with the correct message.
	 */
	private void parseAndSend(String code, String inFile) {
		try {
			ATAbstractGrammar ast = NATParser.parse(inFile, code);
			ATObject beh = _interfaceActor.base_getBehaviour();
			ATAsyncMessage msg = new NATAsyncMessage(
					beh,
					NATNil._INSTANCE_, 
					AGSymbol.alloc("execute:"), 
					new NATTable(new ATObject[] { ast }));
			_waitingFor = msg;
			
			// Synchronize before sending such that the notification always comes
			// after the wait instruction, if the send is not included the program
			// may be blocked forever as the notification comes before the wait.
			synchronized (_waitingFor) {
				_interfaceActor.base_getMailbox(ATActor._IN_).base_enqueue(msg);
				//msg.base_sendTo(beh);
			
				while( true ) {
					try {
						_waitingFor.wait();
						break;
					} catch (InterruptedException e) {
						// Continue waiting
					}
				}
			}
		} catch (XParseError e) {
			handleParseError(e);
		} catch (InterpreterException e) {
			abort("Implicit message send failed : " + e.getMessage());
		}
	}
	

	/* ----------------------------
	 * -- iat Exception Handling --
	 * ---------------------------- */
	
	/*
	 * Shows the error along with the line which produced the error. The erroneous
	 * token is indicated with a ^ mark.
	 */
	private void handleParseError(XParseError e) {
		System.out.println("parse error in "+e.getMessage());
		// try to mark the parse error on the console if that info is available
		String code = e.getErroneousCode();
		
		if (code != null) {
			try {
				int lineNo = e.getLine();
				int colNo = e.getColumn();
				
				BufferedReader reader = new BufferedReader(new StringReader(code));
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
	
	/*
	 * Shows the error and includes the stack of AmbientTalk invocations leading to
	 * the error. This stack is constructed during closure application.
	 * 
	 * @see edu.vub.at.eval.InvocationStack
	 */
	private void handleATException(InterpreterException e) {
		System.out.println(e.getMessage());
		e.printAmbientTalkStackTrace(System.out);
	}
	
	/* ----------------------
	 * -- iat Input/Output --
	 * ---------------------- */
	
	private String readFromConsole() throws IOException {
		if (!_QUIET_ARG_) {
			System.out.print(_INPUT_PROMPT_);
		}
		return IATIO._INSTANCE_.readln();
	}
	
	private void printToConsole(String txt) {
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

	/* ------------------------------
	 * -- BHActor beholder methods --
	 * ------------------------------ */
	
	/**
	 * This method is used to synchronize the IAT thread with the one of the interface
	 * actor it is sending messages to. When a message was sucessfully processed by the
	 * actor, test whether it is the message IAT is waiting for. If this is the case,
	 * print the result and unblock the IAT thread allowing it to read new input.
	 * 
	 * @see edu.vub.at.IAT#parseAndSend(String, String) which will block the IAT thread
	 */
	public void base_onMessageProcessed(EMsgProcess event) {
		if(event.base_getMessage() == _waitingFor) {
			// the actor has served our message
			try {
				printToConsole(Evaluator.toString(event.base_getResult()));
			} catch(InterpreterException e) {
				handleATException(e);
			}
			synchronized (_waitingFor) {
				_waitingFor.notify();
			}
		}
	}
	
	public void base_onDeleted(EActorDelete e) {} // irrelevant

	public void base_onMailboxAdded(EMailboxAdd e) {} // irrelevant

	public void base_onMailboxRemoved(EMailboxRemove e) {} // irrelevant
	
}
