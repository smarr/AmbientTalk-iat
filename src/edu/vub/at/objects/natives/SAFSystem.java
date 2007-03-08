/**
 * AmbientTalk/2 Project
 * SAFSystem.java created on Feb 25, 2007 at 10:46:56 AM
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

import edu.vub.at.actors.natives.SharedActorField;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.natives.grammar.AGSymbol;

/**
 * SAFSystem initialises the "system" field with an object which provides access to the 
 * input and output streams of IAT.
 *
 * @author smostinc
 */
public class SAFSystem extends SharedActorField {

	private static final AGSymbol _SYSTEM_SYM_ = AGSymbol.jAlloc("system");

	private final String[] commandLineArguments_;
	
	public SAFSystem(String[] commandLineArguments) {
		super(_SYSTEM_SYM_);
		commandLineArguments_ = commandLineArguments;
	}

	public ATObject initialize() throws InterpreterException {
		return new OBJSystem(commandLineArguments_);
	}

}
