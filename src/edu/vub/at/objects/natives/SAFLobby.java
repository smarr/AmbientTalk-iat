/**
 * AmbientTalk/2 Project
 * SAFLobby.java created on Feb 25, 2007 at 9:53:34 AM
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
import java.io.FilenameFilter;

import edu.vub.at.actors.natives.SharedActorField;
import edu.vub.at.actors.net.Logging;
import edu.vub.at.eval.Evaluator;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XDuplicateSlot;
import edu.vub.at.exceptions.XIllegalOperation;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.grammar.ATSymbol;
import edu.vub.at.objects.mirrors.Reflection;
import edu.vub.at.objects.natives.grammar.AGSymbol;

/**
 * SAFLobby initializes the lobby namespace with a slot for each directory in the object path.
 * The slot name corresponds to the last name of the directory. The slot value corresponds
 * to a namespace object initialized with the directory.
 * 
 * If the user did not specify an objectpath, the default is .;$AT_OBJECTPATH;$AT_HOME
 *
 * @author smostinc
 */
public class SAFLobby extends SharedActorField {

	private static final AGSymbol _LOBBY_SYM_ = AGSymbol.jAlloc("lobby");
	
	private final File[] objectPathRoots_;
	
	public SAFLobby(File[] objectPathRoots) {
		super(_LOBBY_SYM_);
		objectPathRoots_ = objectPathRoots;
	}
	
	public ATObject initialize() throws InterpreterException {
		NATObject lobby = Evaluator.getLobbyNamespace();
		
		// for each path to the lobby, add an entry for each directory in the path
		for (int i = 0; i < objectPathRoots_.length; i++) {
			File pathRoot = objectPathRoots_[i];
							
			File[] filesInDirectory = pathRoot.listFiles(new FilenameFilter() {
				// filter out all hidden files (starting with .)
				public boolean accept(File parent, String name) {
					return !(name.startsWith("."));
				}
			});
			for (int j = 0; j < filesInDirectory.length; j++) {
				File subdir = filesInDirectory[j];
				if (subdir.isDirectory()) {
					// convert the filename into an AmbientTalk selector
					ATSymbol selector = Reflection.downSelector(subdir.getName());
					try {
						lobby.meta_defineField(selector, new NATNamespace("/"+subdir.getName(), subdir));
					} catch (XDuplicateSlot e) {
						// TODO(review) Should throw dedicated exceptions (difference warning - abort)
						Logging.Init_LOG.error("shadowed path on classpath:", e);
						throw new XIllegalOperation("shadowed path on classpath: "+subdir.getAbsolutePath());
					} catch (InterpreterException e) {
						// should not happen as the meta_defineField is native
						Logging.Init_LOG.fatal("Fatal error while constructing objectpath:", e);
						throw new XIllegalOperation("Fatal error while constructing objectpath: " + e.getMessage());
					}	
				} else {
					Logging.Init_LOG.warn("skipping non-directory file on classpath: " + subdir.getName());
				}
			}
		}
		return null;
	}

}
