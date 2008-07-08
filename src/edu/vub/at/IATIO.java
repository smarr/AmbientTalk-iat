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

import edu.vub.at.util.logging.Logging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;

import jline.ConsoleReader;

/**
 * The class IATIO provides the core input/output functionality of IAT.
 * It is used both by the IAT REPL itself, as well as by the AmbientTalk programmers
 * (indirectly via the system object).
 *
 * Input/output for the REPL is augmented with Command-line editing and history
 * thanks to the JLine library.
 *
 * @author tvcutsem
 */
public final class IATIO {

	public static final IATIO _INSTANCE_ = new IATIO(System.in, System.out);
	
	//private final BufferedReader input_;
	//private final PrintStream output_;
	private ConsoleReader console_;
	
	private IATIO(InputStream in, PrintStream out) {
		try {
			console_ = new ConsoleReader(in, new OutputStreamWriter(out));
		} catch(IOException e) {
			Logging.Init_LOG.fatal("Failed to initialize IAT I/O", e);
		}
		//input_ = new BufferedReader(new InputStreamReader(in));
		//output_ = out;
	}
	
	// output
	
	public void print(String txt) throws IOException {
		console_.printString(txt);
		// output_.print(txt); output_.flush();
	}

	public void print(int nbr) throws IOException {
		console_.printString(Integer.toString(nbr));
		// output_.print(nbr); output_.flush();
	}
	
	public void print(double frc) throws IOException {
		console_.printString(Double.toString(frc));
		// output_.print(frc); output_.flush();
	}
	
	public void print(boolean bool) throws IOException {
		console_.printString(Boolean.toString(bool));
		// output_.print(bool); output_.flush();
	}
	
	public void println(String txt) throws IOException {
		print(txt); console_.printNewline();
		// output_.println(txt);
	}

	public void println(int nbr) throws IOException {
		print(nbr); console_.printNewline();
		// output_.println(nbr);
	}
	
	public void println(double frc) throws IOException {
		print(frc); console_.printNewline();
		// output_.println(frc);
	}
	
	public void println(boolean bool) throws IOException {
		print(bool); console_.printNewline();
		// output_.println(bool);
	}
	
	public void println() throws IOException {
		console_.printNewline();
		// output_.println();
	}
	
	// input

	/**
	 * @return the next line on the input stream or null if EOF has been reached
	 */
	public String readln(String prompt) throws IOException {
		return console_.readLine(prompt); //, new Character((char)0));
	}
	
	/**
	 * @return the next line on the input stream or null if EOF has been reached
	 */
	public String readln() throws IOException {
		return console_.readLine(); //new Character((char)0));
		/* StringBuffer inputBuffer = new StringBuffer();
		inputBuffer.append(input_.readLine());
		
		while (inputBuffer.length() > 0 && inputBuffer.charAt(inputBuffer.length() - 1) == '\\') {
			inputBuffer.setCharAt(inputBuffer.length() -1, '\n');
			inputBuffer.append(input_.readLine());
		}
		
	    return inputBuffer.toString(); */ 
	}

}
