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

	public OneatCorrector(final File oneatdivision, final File oneatapoptosis, final Settings settings,
			final Model model) {

		this.oneatdivision = oneatdivision;

		this.oneatapoptosis = oneatapoptosis;

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
