package edu.vub.at;

import edu.vub.at.actors.ATAsyncMessage;
import edu.vub.at.exceptions.InterpreterException;
import edu.vub.at.exceptions.XArityMismatch;
import edu.vub.at.exceptions.XIllegalOperation;
import edu.vub.at.objects.ATObject;
import edu.vub.at.objects.ATTable;
import edu.vub.at.objects.mirrors.NativeClosure;
import edu.vub.at.objects.natives.NATNil;
import edu.vub.at.objects.natives.NATNumber;

public class IATSynchronizer extends NativeClosure {

	public IATSynchronizer() {
		super(NATNil._INSTANCE_);
	}
	
	private ATAsyncMessage waitingFor_ = null;
	
	public synchronized void yield(ATAsyncMessage token) throws XIllegalOperation {
		if(waitingFor_ != null)
			throw new XIllegalOperation("IAT yielded while the synchronizer was still in use");
		
		waitingFor_ = token;
		while(waitingFor_ != null) {
			try {
				wait();
			} catch (InterruptedException e) {
				continue;
			}
		}
	}
	
	public synchronized ATObject base_apply(ATTable arguments) throws InterpreterException {
		int length = arguments.base_getLength().asNativeNumber().javaValue;
		
		if (length < 2) {
			throw new XArityMismatch("apply", 2, length);
		} else {
			ATAsyncMessage msg = arguments.base_at(NATNumber.ONE).base_asAsyncMessage();
			
			if(msg.base__opeql__opeql_(waitingFor_).asNativeBoolean().javaValue) {
				IAT.proceed(arguments.base_at(NATNumber.atValue(2)));
				waitingFor_ = null;
				notifyAll();
			}
		}
		return NATNil._INSTANCE_;
	}
	
	public ATObject base_applyInScope(ATTable args, ATObject scope) throws InterpreterException {
		return this.base_apply(args);
	}
}
