package fiji.plugin.trackmate.action.oneat;

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
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Pair;

public class OneatCorrector implements TrackCorrector {

	private final File oneatdivision;

	private final File oneatapoptosis;

    private final int mintrackletlength;
    
    private final int detectionchannel;
    
    private final int timegap;
    
    
    private final double linkingdistance;
	
	private final Model model;
	
	private final boolean createlinks;
	
	private final boolean breaklinks;
	
	private final double[] calibration;

	private SpotCollection divisionspots;

	private HashMap<Integer, ArrayList<Spot>> divisionframespots;

	private SpotCollection apoptosisspots;

	private HashMap<Integer, ArrayList<Spot>> apoptosisframespots;
	
	private  HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Mitossisspots;
	
	private  HashMap<Integer, Pair<Spot, Spot>> Apoptosisspots;
	
	private final ImgPlus<IntType> img;
	
	private final Map<String, Object> settings;
	
	private Logger logger;
	
	private int numThreads;
	
	private long processingTime;
	
	private String errorMessage;
	
	private  SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;
	
	private static final String BASE_ERROR_MESSAGE = "[OneatTrackCorrector] ";
	

	public OneatCorrector(
			final File oneatdivision, 
			final File oneatapoptosis, 
			final ImgPlus<IntType> img, 
			final int mintrackletlength,
			final int timegap,
			final int detectionchannel,
			final double linkingdistance,
			final boolean createlinks, 
			final boolean breaklinks, final Model model, double[] calibration,
			Map<String, Object> settings, final Logger logger) {

		this.oneatdivision = oneatdivision;

		this.oneatapoptosis = oneatapoptosis;

		this.img = img;
		
		this.mintrackletlength = mintrackletlength;
		
		this.timegap = timegap;
		
		this.detectionchannel = detectionchannel;
		
		
		this.linkingdistance = linkingdistance;

		this.createlinks = createlinks;
		
		this.breaklinks = breaklinks;
		
		this.model = model;
		
		this.settings = settings;
		
		this.logger = logger;
		
		this.calibration = calibration;
		
		setNumThreads();

	}

	@Override
	public SimpleWeightedGraph<Spot, DefaultWeightedEdge> getResult() {
		return graph;
	}

	@Override
	public boolean checkInput() {
		return true;
	}

	@Override
	public boolean process() {

		
		final long start = System.currentTimeMillis();
		divisionspots = new SpotCollection();
		divisionframespots = new HashMap<Integer, ArrayList<Spot>>();

		apoptosisspots = new SpotCollection();
		apoptosisframespots = new HashMap<Integer, ArrayList<Spot>>();
        int ndims = img.numDimensions() - 1;
		Pair<  Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>> result = TrackCorrectorRunner.run(
				oneatdivision, oneatapoptosis, settings, ndims, calibration);
		//Oneat found spots for mitosis
		divisionspots = result.getA().getA();
		divisionframespots = result.getA().getB();
		
		//Oneat found spots for apoptosis
		apoptosisspots = result.getB().getA();
		apoptosisframespots = result.getB().getB();
		
		//We have to regerenate the graph and tracks after correction
		if(divisionspots.keySet().size() > 0) {
			
			// This object contains the track ID and a list of split points and the root of the lineage tree
			Mitossisspots = TrackCorrectorRunner.getmitosisTrackID(model, img, divisionframespots, settings, true, logger, calibration);
			
			
			// To be safe let us sort the split points in ascending order of frame
			
			for (Map.Entry<Integer, Pair<Spot, ArrayList<Spot>>> dividingTrack: Mitossisspots.entrySet()) {
				
				
				ArrayList<Spot> splitpoints = dividingTrack.getValue().getB();
				
				splitpoints.sort(Spot.frameComparator);
				
			}
			
		}
		
        if(apoptosisspots.keySet().size() > 0) {
			
        	// This object contains the track ID and a list of single object with the apoptotic spot where the track has to terminate and the root of the lineage tree
			Apoptosisspots = TrackCorrectorRunner.getapoptosisTrackID( model, img, apoptosisframespots, settings, logger, calibration); 
			
			// To be safe let us sort the dead points in ascending order of frame
			
			for (Map.Entry<Integer, Pair<Spot, Spot>> dyingTrack: Apoptosisspots.entrySet()) {
				
				
			Spot deadpoints = dyingTrack.getValue().getB();
			
			
        }
        
        }
        
			graph = TrackCorrectorRunner.getCorrectedTracks(model, Mitossisspots, Apoptosisspots, settings, ndims, logger, numThreads); 	
			
			
			 //Check that the objects list itself isn't null
			if ( null == graph )
			{
				errorMessage = BASE_ERROR_MESSAGE + "The output graph is null.";
				return false;
			}
			
			model.beginUpdate();
			
			
			model.setTracks(graph, false);
			
			
			model.endUpdate();
		
			logger.setProgress( 1d );
			logger.setStatus( "" );
			final long end = System.currentTimeMillis();
			processingTime = end - start;

		return true;
	}

	@Override
	public String getErrorMessage() {
		
		return errorMessage;
	}

	@Override
	public void setNumThreads() {
		
		this.numThreads = Runtime.getRuntime().availableProcessors();

	}

	@Override
	public void setNumThreads(int numThreads) {
		
		this.numThreads = numThreads;

	}

	@Override
	public int getNumThreads() {
		
		return numThreads;
		
	}
	
	@Override
	public long getProcessingTime()
	{
		return processingTime;
	}

	@Override
	public void setLogger(Logger logger) {
		
		this.logger = logger;

	}

}
