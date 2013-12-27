package org.jahia.params.valves.custom;

import org.jahia.pipelines.PipelineException;
import org.jahia.pipelines.valves.ValveContext;

public class DummyValveContext implements ValveContext {

	private boolean nextValveIsInvoked = false;
	private Object context;
	
	public boolean getNextValveisInvoked(){
		return nextValveIsInvoked;
	}
	
	public Object getContext() {
		return context;
	}

	public void invokeNext(Object context) throws PipelineException {
		this.context = context;
		nextValveIsInvoked = true;
	}

}
