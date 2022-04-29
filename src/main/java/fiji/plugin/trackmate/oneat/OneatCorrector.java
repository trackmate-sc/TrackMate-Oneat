package fiji.plugin.trackmate.oneat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import net.imagej.ImgPlus;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Pair;

public class OneatCorrector implements TrackCorrector {

	private final File oneatdivision;

	private final File oneatapoptosis;

    private final int mintrackletlength;
    
    private final int timegap;
    
    private final double sizeratio;
    
    private final double linkingdistance;
	
	private final Model model;

	private SpotCollection divisionspots;

	private HashMap<Integer, ArrayList<Spot>> divisionframespots;

	private SpotCollection apoptosisspots;

	private HashMap<Integer, ArrayList<Spot>> apoptosisframespots;
	
	private  HashMap<Integer, Pair<ArrayList<Spot>, Spot>> DivisionTrackIDs;
	
	private  HashMap<Integer, Pair<ArrayList<Spot>, Spot>> ApoptosisTrackIDs;
	
	private final ImgPlus<IntType> img;
	
	private final Map<String, Object> settings;
	
	

	public OneatCorrector(
			final File oneatdivision, 
			final File oneatapoptosis, 
			final ImgPlus<IntType> img, 
			final int mintrackletlength,
			final int timegap,
			final double sizeratio,
			final double linkingdistance,
			final Model model,
			Map<String, Object> settings) {

		this.oneatdivision = oneatdivision;

		this.oneatapoptosis = oneatapoptosis;

		this.img = img;
		
		this.mintrackletlength = mintrackletlength;
		
		this.timegap = timegap;
		
		this.sizeratio = sizeratio;
		
		this.linkingdistance = linkingdistance;

		this.model = model;
		
		this.settings = settings;

	}

	@Override
	public SimpleWeightedGraph<Spot, DefaultWeightedEdge> getResult() {
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

		divisionspots = new SpotCollection();
		divisionframespots = new HashMap<Integer, ArrayList<Spot>>();

		apoptosisspots = new SpotCollection();
		apoptosisframespots = new HashMap<Integer, ArrayList<Spot>>();
        int ndims = img.numDimensions();
		Pair<  Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>> result = TrackCorrectorRunner.run(
				oneatdivision, oneatapoptosis, ndims);
		//Oneat found spots for mitosis
		divisionspots = result.getA().getA();
		divisionframespots = result.getA().getB();
		
		//Oneat found spots for apoptosis
		apoptosisspots = result.getB().getA();
		apoptosisframespots = result.getB().getB();
		
		//We have to regerenate the graph and tracks after correction
		if(divisionspots.keySet().size() > 0) 
			
			DivisionTrackIDs = TrackCorrectorRunner.getTrackID(model, img, divisionframespots, true, timegap);
		
        if(apoptosisspots.keySet().size() > 0) 
			
			ApoptosisTrackIDs = TrackCorrectorRunner.getTrackID( model, img, apoptosisframespots, false, timegap); 
			
        
        
			SimpleWeightedGraph<Spot, DefaultWeightedEdge> correctedgraph = TrackCorrectorRunner.getCorrectedTracks(model, DivisionTrackIDs, ApoptosisTrackIDs, settings, ndims); 	
			
			model.beginUpdate();
			
			
			model.setTracks(correctedgraph, false);
			
			
			model.endUpdate();
		
 		

		return true;
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
