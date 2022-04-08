package fiji.plugin.trackmate.oneat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

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

	private final Settings settings;

	private final Model model;

	private SpotCollection divisionspots;

	private HashMap<Integer, ArrayList<Spot>> divisionframespots;

	private SpotCollection apoptosisspots;

	private HashMap<Integer, ArrayList<Spot>> apoptosisframespots;
	
	private ArrayList<Integer> DivisionTrackIDs;
	
	private ArrayList<Integer> ApoptosisTrackIDs;
	
	private final ImgPlus<IntType> img;
	

	public OneatCorrector(final File oneatdivision, final File oneatapoptosis, final ImgPlus<IntType> img, final Settings settings,
			final Model model) {

		this.oneatdivision = oneatdivision;

		this.oneatapoptosis = oneatapoptosis;

		this.img = img;
		
		this.settings = settings;

		this.model = model;

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

		Pair<  Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>> result = TrackCorrectorRunner.run(settings, model,
				oneatdivision, oneatapoptosis);
		divisionspots = result.getA().getA();
		divisionframespots = result.getA().getB();
		
		apoptosisspots = result.getB().getA();
		apoptosisframespots = result.getB().getB();
		
		//Get the track IDs of the spots detected by oneat to belong to dividing cells
		if(divisionspots.keySet().size() > 0)
			
			DivisionTrackIDs = TrackCorrectorRunner.getTrackID(settings, model, img, divisionframespots);
 		
		// Ge tthe track IDs of the spots detected by oneat to belong to apoptotic cells
        if(apoptosisspots.keySet().size() > 0)
			
			ApoptosisTrackIDs = TrackCorrectorRunner.getTrackID(settings, model, img, apoptosisframespots);  		

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
