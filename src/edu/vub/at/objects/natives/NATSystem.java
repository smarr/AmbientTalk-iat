/**
 * AmbientTalk/2 Project
 * OBJSystem.java created on 21-sep-2006 at 17:18:32
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
package edu.vub.at.objects.natives;

import edu.vub.at.IAT;
import edu.vub.at.IATIO;
import edu.vub.at.actors.eventloops.Event;
import edu.vub.at.actors.natives.ELActor;
import edu.vub.at.eval.Evaluator;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XIOProblem;
import edu.vub.at.objects.ATClosure;
import edu.vub.at.objects.ATNil;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.ATTable;
import edu.vub.at.util.logging.Logging;

import java.io.IOException;

/**
 * Instances of this class represent the 'system' object,
 * accessible from the lexical root during execution of 'iat'.
 * This object contains various native methods to interface with the shell environment.
 * 
 * The interface of the system object is as follows:
 * 
 *  def system := object: {
 *   def argv := the table of extra command-line arguments passed to iat
 *   def exit() { quits iat }
 *   def print(@objs) { print objects to standard output }
 *   def println(@objs) { print objects to standard output, followed by a newline }
 *   // def read() { read character from standard input }
 *   def readln() { read next line from input }
 *   // def reset() { reset VM into fresh startup state and re-evaluates init and argument file }
 * }
 *
 * @author tvc
 */
public final class NATSystem extends NATByCopy {

	private final NATTable argv_;
	private final IAT shell_;
	
	public NATSystem(IAT shell, String[] argv) {
		ATObject[] convertedArgv = new ATObject[argv.length];
		for (int i = 0; i < convertedArgv.length; i++) {
			convertedArgv[i] = NATText.atValue(argv[i]);
		}
		argv_ = NATTable.atValue(convertedArgv);
		shell_ = shell;
	}
	
	public NATText meta_print() throws InterpreterException {
		return NATText.atValue("<native object: system>");
	}
	
	/**
	 * def argv := command-line arguments passed to iat
	 * @return a table of ATText values
	 */
	public ATTable base_getArgv() {
		return argv_;
	}
	
	/**
	 * def argv := command-line arguments passed to iat
	 * @return a table of ATText values
	 */
	public ATTable base_argv() {
		return argv_;
	}
	
	/**
	 * def exit() { quits iat }
	 */
	public ATNil base_exit() {
		System.exit(0);
		return Evaluator.getNil();
	}
	
	/**
	 * def print(@obj) { print obj to standard output }
	 * @param obj the object to print
	 * @throws InterpreterException if obj cannot be converted into a native text value
	 */
	public ATNil base_print(ATObject[] objs) throws InterpreterException {
		try {
			for (int i = 0; i < objs.length; i++) {
				ATObject obj = objs[i];
				if (obj.isNativeText()) {
					IATIO._INSTANCE_.print(obj.asNativeText().javaValue);
				} else {
					IATIO._INSTANCE_.print(obj.meta_print().javaValue);
				}
			}
			return Evaluator.getNil();
		} catch (IOException e) {
			throw new XIOProblem(e);
		}
	}
	
	/**
	 * def println(@obj) { self.print(@[@obj, '\n']) }
	 */
	public ATNil base_println(ATObject[] objs) throws InterpreterException {
		base_print(objs);
		try {
			IATIO._INSTANCE_.println();
			return Evaluator.getNil();
		} catch (IOException e) {
			throw new XIOProblem(e);
		}
	}
	
	/**
	 * readNext: { |c| ... } catch: { |ioException| ... }
	 * Reads the next character on the input stream, represented by an ATText or nil if
	 * EOF has been reached. The character is subsequently passed to the first closure.
	 * Any exception is signalled by invoking the "catch:" closure.
	 * @return nil
	 */
	/*public ATNil base_readNext_catch_(final ATClosure success, final ATClosure failure) throws XIOProblem {
		shell_.repl_.event_read(ELActor.currentActor(), success, failure);
		return Evaluator.getNil();
	}*/
	
	/**
	 * readNextLine: { |line| ... } catch: { |ioException| ... }
	 * Reads the next line on the input stream, represented by an ATText or nil if
	 * EOF has been reached. The line string is subsequently passed to the first closure.
	 * Any exception is signalled by invoking the "catch:" closure.
	 * @return nil
	 */
	public ATNil base_readNextLine_catch_(final ATClosure success, final ATClosure failure) throws XIOProblem {
		shell_.repl_.event_readLine(ELActor.currentActor(), success, failure);
		return Evaluator.getNil();
	}
	
	/**
	 * def reset() { reset VM into fresh startup state and re-evaluates init and main file }
	 * 
	 * Resets the global lexical scope to an empty object.
	 * Re-fills the lobby namespace with the directories on the objectpath.
	 * Re-initializes the system object.
	 * Re-loads the init file used on startup (user or default init file)
	 * Re-loads the -e option or main argument file (if applicable)
	 *   
	 * @return value of evaluating the main file or -e option; nil if there is no such file or option
	 */
//	public ATObject base_reset() {
//		Evaluator.resetEnvironment();
//		IAT.boot();
//		return OBJNil._INSTANCE_;
//	}

}
