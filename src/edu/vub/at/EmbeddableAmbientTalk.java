/**
 * 
 */
package edu.vub.at;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;

import edu.vub.at.actors.natives.ELActor;
import edu.vub.at.actors.natives.ELVirtualMachine;
import edu.vub.at.actors.natives.NATActorMirror;
import edu.vub.at.actors.natives.SharedActorField;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XIllegalOperation;
import edu.vub.at.exceptions.XParseError;
import edu.vub.at.exceptions.XTypeMismatch;
import edu.vub.at.objects.ATAbstractGrammar;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.coercion.Coercer;
import edu.vub.at.objects.natives.OBJNil;
import edu.vub.at.objects.natives.SAFLobby;
import edu.vub.at.objects.natives.SAFWorkingDirectory;
import edu.vub.at.parser.NATParser;
import edu.vub.at.util.logging.Logging;
import edu.vub.util.Pattern;

/**
 * The EmbeddableAmbientTalk class provides a general framework to start an AmbientTalk virtual machine from
 * within a Java application. It provides functions to evaluate AmbientTalk scripts, and get the output of such
 * scripts either to be printed on the screen or to be used in independent Java code.
 * <p>
 * Since many AmbientTalk code depends on the availability of objects such as <tt>system</tt>, <tt>~</tt> and 
 * <tt>lobby</tt> (commonly used as <tt>/</tt>), this class provides static methods to create the shared actor
 * fields which correspond to these items. However, which fields are included when creating the virtual machine
 * is decided entirely by the user of this class (i.e. the aforementioned fields are not included by default). 
 * 
 * @author smostinc
 *
 */
public abstract class EmbeddableAmbientTalk {

	protected String			scriptSource_;
	protected ELActor			evaluator_; 
	protected ELVirtualMachine	virtualMachine_;
	
	/**
	 * Initializes a new instance, which is done in a method rather than a constructor to allow the use of 
	 * instance methods such as computeObjectPath etc. to be used to pass in arguments.
	 */
	public void initialize(ATAbstractGrammar ast, SharedActorField[] fields, String networkName) {
		try {
			// initialize the virtual machine using object path, init file and network name
			virtualMachine_ = new ELVirtualMachine(ast, fields, networkName);
						
			// create a new actor on this vm with the appropriate main body.
			evaluator_ = virtualMachine_.createEmptyActor().getFarHost();
		} catch (InterpreterException cause) {
			abort("Fatal error while initializing the evaluator actor:" + cause.getMessage(), cause);
		}
	}
	
	/**
	 * Parses the given script into an AmbientTalk Abstract Syntax Tree and subsequently evaluates this AST 
	 * with the evaluator. The behaviour of this method can be summarized as follows: 
	 * <ul>
	 *   <li>When no exceptions are reported, the result of the script is returned. 
	 *   <li>If the script contains parse errors, the {@link EmbeddableAmbientTalk#handleParseError(String, XParseError)} 
	 *       template method is called which may report/correct the error. 
	 *   <li>If the execution of the script results in errors, the {@link EmbeddableAmbientTalk#handleATException(String, InterpreterException)} 
	 *       template method will be called which may report the error and/or return an alternate value. 
	 *   <li>Any other exception will trigger the {@link EmbeddableAmbientTalk#abort(String, Exception)} template 
	 *       method. If this method returns properly (i.e. it does not halt or exit the System), the returned value
	 *       will be the AmbientTalk <tt>nil<tt>.
	 * </ul> 
	 * @param script a string containing the AmbientTalk code to be executed.
	 * @return the result of executing the script, or of executing the error handling template methods.
	 */
	protected ATObject parseAndSend(String script) {
		try {
			ATAbstractGrammar ast = NATParser.parse(scriptSource_, script);
			
			// By using sync_eval_event, we force the system to wait for the evaluation result
			// This also ensures that any uncaught exceptions raised while evaluating the script
			// will be re-raised in this thread so that they may be properly caught in the catch
			// blocks provided below.
			ATObject result = evaluator_.sync_event_eval(ast);
			
			return result;			
		} catch (XParseError e) {
			return handleParseError(script, e);
		} catch (InterpreterException e) {
			return handleATException(script, e);
		} catch (Exception e) {
			abort("Unexpected exception: " + e.getMessage(), e);
		}
	
		return OBJNil._INSTANCE_;
	}
	
	/**
	 * Template method to handle parse errors occurring while parsing the script. This method may be used to
	 * report but also repair the parse error.
	 * @return an alternate return value which is subsequently propagated as the result of the {@link EmbeddableAmbientTalk#parseAndSend(String)}
	 * method. If this method simply reports an error message, it should return the AmbientTalk <tt>nil</tt> value,
	 * if it can correct the error it may re-attempt to parse and send the corrected code and use the resulting value
	 * as its return value.
	 */
	protected abstract ATObject handleParseError(String script, XParseError e);

	/**
	 * Template method to handle errors occurring while executing the script. This method may be used to
	 * report but also handle/transform the reported error.
	 * @return an alternate return value which is subsequently propagated as the result of the {@link EmbeddableAmbientTalk#parseAndSend(String)}
	 * method. If this method simply reports an error message, it may return the AmbientTalk <tt>nil</tt> value,
	 * but implementations are free to return other objects which better integrate with the exception handling 
	 * facilities of the application in which the AmbientTalk VM is embedded.
	 */
	protected abstract ATObject handleATException(String script, InterpreterException e);
	
	/**
	 * Template method to deal with unexpected exceptions arising from the Embedded AmbientTalk VM. Typically such
	 * exceptions signal some form of important bug in the application or interpreter. When this method returns,
	 * the {@link EmbeddableAmbientTalk#parseAndSend(String)} will return the AmbientTalk <tt>nil</tt> value, and 
	 * will attempt to continue evaluating future scripts.
	 * <p>
	 * This method maybe used to halt the application or restart the faulty VM and actors to restart a new session.
	 * @param message the reported error message.
	 * @param cause the error that caused this method to be called.
	 */
	protected abstract void abort(String message, Exception cause);
	
	/**
	 * Evaluates an AmbientTalk script and prints the resulting object on the provided output channel
	 * 
	 * @param script a string containing the script to be executed
	 * @param output an output channel to print the resulting object to
	 */
	public void evalAndPrint(String script, PrintStream output) {
		output.println(parseAndSend(script));
	}
	
	/**
	 * Evaluates an AmbientTalk script and coerces the resulting object (if possible) to conform to the
	 * requested type. As a result, the object will be wrapped in a thread-safe wrapper which ensures that
	 * when the wrapped object would be used by an external Java thread, this thread will not itself enter
	 * the AmbientTalk world, but will rather schedule a message with the appropriate actor and wait until
	 * this message is answered by the actor.
	 * 
	 * @param script a string containing the script to be executed
	 * @param requestedInterface an interface to which the resulting object should be written
	 * @return an object which can be safely cast to match the requested interface
	 * @throws XTypeMismatch when the class to which the return value should be coerced is not an interface
	 * @throws XIllegalOperation when the class to which the return value should be coerced is a subclass of 
	 * ATObject, since such coercions may be no-ops. As such, coercing to an ATObject interface may result in 
	 * the possibility that multiple threads operate within a single actor. 
	 */
	public Object evalAndWrap(String script, Class requestedInterface) throws XTypeMismatch, XIllegalOperation {
		// When given a class which extends from the ATObject hierarchy, the resulting object might conform
		// innately to the requested type. Since in such cases a coercer is not needed, it is not safe to 
		// request wrapping an object as such an interface since the resulting object may not be thread-safe
		// (i.e. calling methods on it may result in having two active threads within a single actor). To
		// ensure that such mishaps do not occur we raise an exception preventively.
		if(ATObject.class.isAssignableFrom(requestedInterface))
			throw new XIllegalOperation("Cannot wrap a value returned to a pure Java thread as an ATObject derivative: " +
					"this incurs a possible violation of the event-loop concurrency properties");
		
		return Coercer.coerce(parseAndSend(script), requestedInterface, evaluator_.getExecutor());
	}
	
	// SHARED ACTOR FIELD Constructors
	// The following constructors serve to create some of the default fields which are to be
	// installed in every actor, applications can use the default implementation or specialize
	// the provided code. The collection of shared actor fields must be passed to the initialize
	// method which will them properly initialize a virtual machine and an evaluator actor.
	
	
	/**
	 * AmbientTalk scripts regularly use the <tt>lobby</tt> object (abbreviated as <tt>/</tt>) to load code 
	 * which they need to execute (e.g. type tag definitions, default exceptions, language constructs etc.).
	 * To support this, the {@link EmbeddableAmbientTalk} class provides a helper function which is able to 
	 * transform a textual description of the object path, of the form:
	 * <pre>
	 * name1=path1:name2=path2:...
	 * 
	 * creates a listing:
	 * name1 -> File(path1)
	 * name2 -> File(path2)
	 * ...
	 * 
	 * and uses the listing to initialize the lobby of the actors.
	 * </pre>
	 * 
	 * @return a {@link SAFLobby} actor field which can be passed when initializing a virtual machine so that
	 * every new actor is equipped with a lobby object.
	 * @see EmbeddableAmbientTalk#abort(String, Exception) which is called to report exceptions occurring while
	 * transforming the object root.
	 * 
	 */
	public SharedActorField computeObjectPath(String objectPath) {
		// split the object path using ':' (on *nix)
        String[] roots = Pattern.compile(File.pathSeparator).split(new StringBuffer(objectPath));
        // Backport from JDK 1.4 to 1.3
        // String[] roots = objectPath.split(System.getProperty("path.separator"));
		LinkedList namedPaths = new LinkedList();
		
		// for each named file path, add an entry to the mapping
		for (int i = 0; i < roots.length; i++) {
			if (roots[i].length()==0) {
				continue; // skip empty entries
			}
			
			// extract name = path components
            String[] pair = Pattern.compile("=").split(new StringBuffer(roots[i]));
            // Backport from JDK 1.4 to 1.3
			// String[] pair = roots[i].split("=");
			if (pair.length != 2) {
				abort("Error: invalid name=path entry on object path: " + roots[i], null);
			}
			
			String name = pair[0];
			File pathfile = new File(pair[1]);
			
			// check whether the given pathfile is a directory
			if (!pathfile.isDirectory()) {
			    abort("Error: non-directory file on objectpath: " + pathfile.getAbsolutePath(), null);
			}
			
			if (!pathfile.isAbsolute()) {
				try {
					pathfile = pathfile.getCanonicalFile();
				} catch (IOException e) {
					abort("Fatal error while constructing objectpath: " + e.getMessage(), e);
				}
			}
			Logging.Init_LOG.info("Added entry to object path: " + name + "=" + pathfile.getPath());
			namedPaths.add(new Object[] { name, pathfile });
		}
		
		return new SAFLobby(namedPaths);
	}
	
	/**
	 * Defines the <tt>~</tt> working directory symbol and ties it to the home directory of the user. This
	 * directory can be used to lookup additional files which may not necessarily be accessible through the
	 * lobby.
	 * 
	 * @return a shared actor field which will install a <tt>~</tt> binding in every actor.
	 */
	public SharedActorField computeWorkingDirectory() {
		return new SAFWorkingDirectory();
	}
	
	/**
	 * Defines a shared actor field which will ensure that the <tt>system</tt> object receives a proper binding.
	 * The semantics of this object are heavily dependent on the application in which AmbientTalk is being embedded
	 * and therefore this method is intentionally left abstract.
	 * <p>
	 * Designers should think about the semantics of typical operations such as printing and reading data, exiting
	 * the system and possibly consider providing access to reset the VM (i.e. restarting the embedded engine) and 
	 * offering (transformed) command-line arguments to the AmbientTalk interpreter.
	 */
	public abstract SharedActorField computeSystemObject(Object[] arguments); 
}
