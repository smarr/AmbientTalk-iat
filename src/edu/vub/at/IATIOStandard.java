/**
 * AmbientTalk/2 Project
 * IATIOStandard.java
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

public final class IATIOStandard extends IATIO {

	public static final IATIOStandard _INSTANCE_ = new IATIOStandard(System.in, System.out);
	
	private final BufferedReader input_;
	private final PrintStream output_;
	
	private IATIOStandard(InputStream in, PrintStream out) {
		input_ = new BufferedReader(new InputStreamReader(in));
		output_ = out;
	}
	
	// output
	
	public void print(String txt) throws IOException {
		 output_.print(txt); output_.flush();
	}

	public void print(int nbr) throws IOException {
		 output_.print(nbr); output_.flush();
	}
	
	public void print(double frc) throws IOException {
		 output_.print(frc); output_.flush();
	}
	
	public void print(boolean bool) throws IOException {
		 output_.print(bool); output_.flush();
	}
	
	public void println(String txt) throws IOException {
		 output_.println(txt);
	}

	public void println(int nbr) throws IOException {
		output_.println(nbr);
	}
	
	public void println(double frc) throws IOException {
		output_.println(frc);
	}
	
	public void println(boolean bool) throws IOException {;
		 output_.println(bool);
	}
	
	public void println() throws IOException {
	    output_.println();
	}
	
	// input

	/**
	 * @return the next line on the input stream or null if EOF has been reached
	 */
	public String readln(String prompt) throws IOException {
		//providing equivalent functionality to jline (e.g. console_.readLine(prompt);)
		output_.print(prompt);
		String read = readln();
		if (read !=null ){
			output_.println(read);
		}
	    return read;
	}
	
	
	/**
	 * @return the next line on the input stream or null if EOF has been reached
	 */
	public String readln() throws IOException {
		StringBuffer inputBuffer = new StringBuffer();
		String line = input_.readLine();
		if (line != null){
			inputBuffer.append(line);
			while (inputBuffer.length() > 0 && inputBuffer.charAt(inputBuffer.length() - 1) == '\\') {
				inputBuffer.setCharAt(inputBuffer.length() -1, '\n');
				inputBuffer.append(input_.readLine());
			}
			return inputBuffer.toString();
		} else{
		  return null;
		}
	}

}