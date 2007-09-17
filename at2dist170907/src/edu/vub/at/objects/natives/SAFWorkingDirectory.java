/**
 * AmbientTalk/2 Project
 * SAFWorkingDirectory.java created on Feb 25, 2007 at 10:26:22 AM
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

import java.io.File;

import edu.vub.at.actors.natives.SharedActorField;
import edu.vub.at.eval.Evaluator;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.objects.ATObject;

/**
 * SAFWorkingDirectory initialises the field "~" to the current directory, allowing a file to 
 * refer to its peers using a relative id.
 *
 * @author smostinc
 */
public class SAFWorkingDirectory extends SharedActorField {

	private final File workingDirectory_;
	
	/**
	 * Default constructor: when no working directory can be derived, use the directory from which
	 * the (java) virtual machine was started.
	 */
	public SAFWorkingDirectory() {
		this(new File(System.getProperty("user.dir")));
	}

	public SAFWorkingDirectory(File path) {
		super(Evaluator._CURNS_SYM_);
		workingDirectory_ = path;
	}

	public ATObject initialize() throws InterpreterException {
		if(workingDirectory_.exists())
			return new NATNamespace(workingDirectory_.getAbsolutePath(), workingDirectory_);
		else 
			return null;
	}

}
