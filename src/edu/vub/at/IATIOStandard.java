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
import java.io.OutputStream;
import java.io.PrintWriter;

public final class IATIOStandard extends IATIO {

	public static final IATIOStandard _INSTANCE_ = new IATIOStandard(System.in, System.out);
	
	private final BufferedReader input_;
	private final PrintWriter output_;
	
	private IATIOStandard(InputStream in, OutputStream out) {
		input_ = new BufferedReader(new InputStreamReader(in));
		output_ = new PrintWriter(System.out, true);
	}
	
	// output
	
	public void print(String txt) {
		 output_.print(txt); output_.flush();
	}

	public void print(int nbr){
		 output_.print(nbr); output_.flush();
	}
	
	public void print(double frc){
		 output_.print(frc); output_.flush();
	}
	
	public void print(boolean bool){
		 output_.print(bool); output_.flush();
	}
	
	public void println(String txt) {
		 output_.println(txt);
	}

	public void println(int nbr){
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
	 * @return the next line on the input stream or null if EOF has been reached
	 */
	public String readln(String prompt) throws IOException {
		//providing equivalent functionality to jline (e.g. console_.readLine(prompt);)
		output_.print(prompt); output_.flush();
		return readln();
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
			String read = inputBuffer.toString();
			if (!IAT._QUIET_ARG_) {
				output_.println(read);
			}
			return read;
		} else{
		    return null;
		}
	}

}
