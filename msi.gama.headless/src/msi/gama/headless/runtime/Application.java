/*********************************************************************************************
 * 
 *
 * GAMA modeling and simulation platform.
 * 'Application.java', in plugin 'msi.gama.headless', is part of the source code of the 
 * (c) 2007-2014 UMI 209 UMMISCO IRD/UPMC & Partners
 * 
 * Visit https://code.google.com/p/gama-platform/ for license information and developers contact.
 * 
 * 
 **********************************************************************************************/
package msi.gama.headless.runtime;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import msi.gama.headless.common.Globals;
import msi.gama.headless.common.HeadLessErrors;
import msi.gama.headless.core.HeadlessJarLoader;
import msi.gama.headless.core.HeadlessSimulationLoader;
import msi.gama.headless.job.ExperimentJob;
import msi.gama.headless.job.IExperimentJob;
import msi.gama.headless.job.JobPlan;
import msi.gama.headless.script.ExperimentationPlanFactory;
import msi.gama.headless.script.ScriptFactory;
import msi.gama.headless.xml.ConsoleReader;
import msi.gama.headless.xml.Reader;
import msi.gama.headless.xml.XMLWriter;
import msi.gama.runtime.GAMA;

import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.w3c.dom.Document;

import com.googlecode.lanterna.TerminalFacade;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.Terminal;




public class Application implements IApplication {

	final public static String CONSOLE_PARAMETER = "-c";
	final public static String TUNNELING_PARAMETER = "-p";
	final public static String THREAD_PARAMERTER = "-hpc";
	final public static String VERBOSE_PARAMERTER = "-v";
	final public static String HELP_PARAMERTER = "-help";
	final public static String BUILD_XML_PARAMERTER = "-xml";
	
	
	public static boolean headLessSimulation = false;
	public int numberOfThread = -1;
	public boolean consoleMode = false;
	public boolean tunnelingMode = false;
	public boolean verbose = false;
	public SimulationRuntime processorQueue;

	
	private static String showHelp()
	{
		String res = " sh ./gama-headless.sh [Options] [XML Input] [output directory]\n" +
		 "\nList of available options:" +
		 "\n      -help     -- get the help of the command line" +
		 "\n      -m mem    -- allocate memory (ex 2048m)" +
		 "\n      -c        -- start the console to write xml parameter file" +
		 "\n      -hpc core -- set the number of core available for experimentation" +
		 "\n      -p        -- start piplines to interact with another framework" +
		 "\n" +
		 "\n" +
		 " sh ./gama-headless.sh -xml gamlFile xmlOutputFile\n" +
		 "\n      build an xml parameter file from a model" +
		 "\n" +
		 "\n";
		return res;
	}
	
	private static boolean containParameter(final String[] args, String param)
	{
		for(String p:args)
			{
				if(p.equals(param))
					return true;
			}
		return false;
	}

	private static boolean containConsoleParameter(final String[] args)
	{
		return containParameter(args, CONSOLE_PARAMETER);
	}

	private static boolean containHelpParameter(final String[] args)
	{
		return containParameter(args, HELP_PARAMERTER);
	}

	private static boolean containXMLParameter(final String[] args)
	{
		return containParameter(args, BUILD_XML_PARAMERTER);
	}

	private static boolean containTunnellingParameter(final String[] args)
	{
		return containParameter(args, TUNNELING_PARAMETER);
	}

	private static boolean containVerboseParameter(final String[] args)
	{
		return containParameter(args, VERBOSE_PARAMERTER);
	}
	private static boolean containNumberOfThread(final String[] args)
	{
		return containParameter(args, THREAD_PARAMERTER);
	}
	
	private static int getNumberOfThread(final String[] args)
	{
		for(int n = 0; n<args.length; n++)
		{
			if(args[n].equals(THREAD_PARAMERTER))
				return Integer.valueOf(args[n+1]).intValue();	
		}
		return SimulationRuntime.UNDEFINED_QUEUE_SIZE;
	}
	

	
	private  boolean checkParameters(final String[] args) {
		
		int size = args.length;
		boolean mustContainInFile = true;
		boolean mustContainOutFile = true;
		if(containConsoleParameter(args))
		{
			size = size - 1;
			mustContainInFile = false;
		}
		if(containTunnellingParameter(args))
		{
			size = size - 1;
			mustContainOutFile = false;
		}
		if(containNumberOfThread(args))
		{
			size = size - 2;
		}
		if(containVerboseParameter(args))
		{
			size = size - 1;
		}
		if(mustContainInFile && mustContainOutFile && size <2 ) { 
			showError(HeadLessErrors.INPUT_NOT_DEFINED, null); 
			return false;
		}
		if(!mustContainInFile && mustContainOutFile && size <1 ) { 
			showError(HeadLessErrors.OUTPUT_NOT_DEFINED, null);
			return false;
		}
		
		if(mustContainOutFile)
		{
			int outIndex = args.length -1;
			Globals.OUTPUT_PATH = args[outIndex];
			Globals.IMAGES_PATH = args[outIndex] + "/snapshot";
			File output = new File(Globals.OUTPUT_PATH);
			if(!output.exists())
				output.mkdir();
			File images = new File(Globals.IMAGES_PATH);
			if(!images.exists())
				images.mkdir();
		}
		
		if(mustContainInFile)
		{
			int inIndex = mustContainOutFile ? args.length -2:args.length-1;
			File input = new File(args[inIndex]);
			if (!input.exists()) {
				showError(HeadLessErrors.NOT_EXIST_FILE_ERROR, args[inIndex]);
				return false;
			}
		}
		return true;
	}

	private static boolean showError(final int errorCode, final String path) {
		System.out.println(HeadLessErrors.getError(errorCode, path));
		return false;
	}

	
	
	@Override
	public Object start(final IApplicationContext context) throws Exception {
		Map<String, String[]> mm = context.getArguments();
		String[] args = mm.get("application.args");
		if(containHelpParameter(args))
		{
			System.out.println(showHelp());
		} 
		else if(containXMLParameter(args))
		{
			buildXML(args);
		}
		else runSimulation(args);
		return null;
	}
	
	public void buildXML(String arg[]) throws ParserConfigurationException, TransformerException
	{
		if(arg.length<3)
		{
			System.out.println("Check parameters");
			System.out.println(showHelp());
			return;
		}
		HeadlessSimulationLoader.preloadGAMA();

		List<IExperimentJob> jb = ExperimentationPlanFactory.buildExperiment(arg[arg.length-2]);
		ArrayList<IExperimentJob> selectedJob = new ArrayList<IExperimentJob>();
		for(IExperimentJob j : jb)
		{
			if(j.getExperimentName().equals(arg[arg.length-3]))
			{
				selectedJob.add(j);
				break;
			}
		}
		
		Document dd =ExperimentationPlanFactory.buildXmlDocument(selectedJob);
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(dd);
		StreamResult result = new StreamResult(new File(arg[arg.length-1]));
		transformer.transform(source, result);

		System.out.println("File saved!");
	}
	
	public void runSimulation(String args[]) throws FileNotFoundException, InterruptedException
	{
		if(!checkParameters(args))
		{
			System.exit(-1);
		}
		
		SystemLogger.removeDisplay();
		verbose = containVerboseParameter(args);
		if(verbose)
		{
			  SystemLogger.activeDisplay();  
		}
		HeadlessSimulationLoader.preloadGAMA();
	//	loadJars();
	//	HeadlessJarLoader.testJarLoading();
		

		
		

		
		
		this.tunnelingMode = Application.containTunnellingParameter(args);
		this.consoleMode = Application.containConsoleParameter(args);
		this.numberOfThread = Application.getNumberOfThread(args);
		processorQueue = new LocalSimulationRuntime(this.numberOfThread);
		
		Reader in = null;
		if(this.verbose &&!this.tunnelingMode)
		{
			SystemLogger.activeDisplay();
		}
		
		if(this.consoleMode)
		{
			in =new Reader(ConsoleReader.readOnConsole());
		}
		else
		{
			 in = new Reader(args[args.length-2]);
		}
		in.parseXmlFile();
		 this.buildAndRunSimulation(in.getSimulation());
		 in.dispose();
		// Screen terminal = TerminalFacade.createScreen(TerminalFacade.createTextTerminal());
		 
//		 Screen term = TerminalFacade.createScreen(); 

//	     term.startScreen(); 
		 while (processorQueue.isPerformingSimulation()) {
			  Thread.sleep(1000);
		 }
//			xx++;
//			//term.clearScreen(); 
//			
//	//		HashMap<String, Double> states=processorQueue.getSimulationState();
//		//	int i = 1;
//		//	//show(term, "                                ",0,0);
//			term.putString(0, 0, "                              ", Terminal.Color.WHITE, Terminal.Color.BLACK);
//			for(String k :states.keySet() )
//			{
//				term.putString(0, i, k+"\t"+states.get(k)+ "                ", Terminal.Color.WHITE, Terminal.Color.BLACK);
//				
//				//show(term,k+"\t"+states.get(k)+ "                ",i,0);
//				i++;   
//			}
//			term.putString(0, 0, "                              ", Terminal.Color.WHITE, Terminal.Color.BLACK);
//			term.refresh();
//	  	    Thread.sleep(1000);
//		 }
//		 term.stopScreen();
		 System.exit(0);	
	}

	private void show(Terminal term, String str, int x, int y) 
	   { 
	       term.moveCursor(x, y); 

	       int len = str.length(); 

	       for (int i = 0; i < len; i++) 
	       { 
	           term.putCharacter(str.charAt(i)); 
	       } 
	   } 
	
	public void buildAndRunSimulation(Collection<ExperimentJob> sims)
	{
		Iterator<ExperimentJob> it = sims.iterator();
		while (it.hasNext()) {
			ExperimentJob sim = it.next();
			try {
				XMLWriter ou = null;
				if(tunnelingMode)
				{
					ou = new XMLWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
				}
				else
				{
					ou = new XMLWriter(Globals.OUTPUT_PATH + "/" + Globals.OUTPUT_FILENAME + sim.getExperimentID() + ".xml");
				}
				sim.setBufferedWriter(ou);	
				
				processorQueue.pushSimulation(sim);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	@Override
	public void stop() {}

}
