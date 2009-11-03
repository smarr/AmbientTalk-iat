/**
 * AmbientTalk/2 Project
 * IATIO.java created on 21-sep-2006 at 17:50:38
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

import java.io.IOException;

/**
 * The class IATIOJline provides the core input/output functionality of IAT.
 * It is used both by the IAT REPL itself, as well as by the AmbientTalk programmers
 * (indirectly via the system object).
 * 
 * To be overridden by subclasses to specify the behaviour of I/0.
 * By convention, all subclasses are prefixed with IATIO.
 */
public abstract class IATIO {
	
	// output
	
	public abstract void print(String txt) throws IOException;

	public abstract void print(int nbr) throws IOException ;
	
	public abstract void print(double frc) throws IOException;
	
	public abstract void print(boolean bool) throws IOException;
	
	public abstract void println(String txt) throws IOException;

	public abstract void println(int nbr) throws IOException;
	
	public abstract void println(double frc) throws IOException;
	
	public abstract void println(boolean bool) throws IOException;
	
	public abstract void println() throws IOException;
	
	// input

	/**
	 * @return the next line on the input stream or null if EOF has been reached
	 */
	public abstract String readln(String prompt) throws IOException;
	public abstract String readln() throws IOException;
	
}
