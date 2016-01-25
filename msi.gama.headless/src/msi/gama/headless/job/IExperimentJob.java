package msi.gama.headless.job;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import msi.gama.headless.runtime.RuntimeContext;

public interface IExperimentJob {
	
	public String getExperimentID();
	public String getExperimentName();
	public String getModelName();
	public void addParameter(final Parameter p);
	public void addOutput(final Output p);
	public List<String> getOutputNames();
	public void removeOutputWithName(final String name);
	public void setOutputFrameRate(final String name, final int frate);
	public void setParameterValueOf(final String name,final Object val);
	public void setSeed(final long s);
	public long getSeed();
	public long getStep();
	public void setFinalStep(long step);
	
	public void loadAndBuild(RuntimeContext rtx) throws InstantiationException, IllegalAccessException, ClassNotFoundException;
	public Element asXMLDocument(Document doc);
	
	
	public void play();
	public void doStep();
}