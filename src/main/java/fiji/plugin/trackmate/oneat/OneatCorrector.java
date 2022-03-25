package fiji.plugin.trackmate.oneat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import net.imglib2.util.Pair;

public class OneatCorrector implements TrackCorrector {

	
	
	private final File oneatfile;
	
	private final Settings settings;
	
	private final Model model;
	
	private DivisionSpotCollection divisionspots;
	
	private HashMap<Integer, ArrayList<DivisionSpot>> divisionframespots;
	
	public OneatCorrector(final File oneatfile, final Settings settings, final Model model) {
		
		
		this.oneatfile = oneatfile;
		
		this.settings = settings;
		
		this.model = model;
		
	}
	
	
	
	
	@Override
	public SimpleWeightedGraph<DivisionSpot, DefaultWeightedEdge> getResult() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean checkInput() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean process() {
		
		
		divisionspots = new DivisionSpotCollection();
		divisionframespots = new HashMap<Integer, ArrayList<DivisionSpot>>();
		
		Pair<DivisionSpotCollection, HashMap<Integer, ArrayList<DivisionSpot>>> result = TrackCorrectorRunner.run(settings, model, oneatfile);
		
		divisionspots = result.getA();
		divisionframespots = result.getB();
		
		
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getErrorMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setNumThreads() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setNumThreads(int numThreads) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getNumThreads() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setLogger(Logger logger) {
		// TODO Auto-generated method stub
		
	}

}
