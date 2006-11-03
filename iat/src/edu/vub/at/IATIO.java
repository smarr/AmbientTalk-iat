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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * The class IATIO provides the core input/output functionality of IAT.
 * It is used both by the IAT REPL itself, as well as by the AmbientTalk programmers
 * (indirectly via the system object)
 *
 * @author tvc
 */
public final class IATIO {

	public static final IATIO _INSTANCE_ = new IATIO();
	
	private final BufferedReader input_;
	private final PrintStream output_;
	
	private IATIO() {
		input_ = new BufferedReader(new InputStreamReader(System.in));
		output_ = System.out;
	}
	
	// output
	
	public void print(String txt) {
		output_.print(txt); output_.flush();
	}

	public void print(int nbr) {
		output_.print(nbr); output_.flush();
	}
	
	public void print(double frc) {
		output_.print(frc); output_.flush();
	}
	
	public void print(boolean bool) {
		output_.print(bool); output_.flush();
	}
	
	public void println(String txt) {
		output_.println(txt);
	}

	public void println(int nbr) {
		output_.println(nbr);
	}
	
	public void println(double frc) {
		output_.println(frc);
	}
	
	public void println(boolean bool) {
		output_.println(bool);
	}
	
	public void println() {
		output_.println();
	}
	
	// input
	
	/**
	 * @return the next character on the input stream or -1 if EOF has been reached
	 */
	public int read() throws IOException {
	  return input_.read();
	}
	
	/**
	 * @return the next line on the input stream or null if EOF has been reached
	 */
	public String readln() throws IOException {
	  return input_.readLine(); 
	}

}
