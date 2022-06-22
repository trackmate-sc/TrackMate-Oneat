package fiji.plugin.trackmate.action.oneat;

import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_SPLITTING;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.visualization.hyperstack.SpotOverlay;
import fiji.plugin.trackmate.visualization.hyperstack.TrackOverlay;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;

public class OneatCorrector implements TrackCorrector {

	private final File oneatdivision;

	private final File oneatapoptosis;

   
	
	private final Model model;
	
	private final TrackMate trackmate;
	

	
	private final double[] calibration;

	private SpotCollection divisionspots;

	private HashMap<Integer, ArrayList<Spot>> divisionframespots;

	private SpotCollection apoptosisspots;

	private HashMap<Integer, ArrayList<Spot>> apoptosisframespots;
	
	private  HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Mitossisspots;
	
	private  HashMap<Integer, Pair<Spot, Spot>> Apoptosisspots;
	
	private final ImgPlus<UnsignedShortType> img;
	
	private final Map<String, Object> settings;
	
	private final DisplaySettings displaySettings;
	
	private final Settings modelsettings;
	
	private Logger logger;
	
	private int numThreads;
	
	private long processingTime;
	
	private String errorMessage;
	
	private  SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;
	
	private static final String BASE_ERROR_MESSAGE = "[OneatTrackCorrector] ";
	

	public OneatCorrector(
			final File oneatdivision, 
			final File oneatapoptosis, 
			final ImgPlus<UnsignedShortType> intimg, 
			final Model model, final TrackMate trackmate, final Settings modelsettings, final DisplaySettings displaySettings, double[] calibration,
			Map<String, Object> settings, final Logger logger) {

		this.oneatdivision = oneatdivision;

		this.oneatapoptosis = oneatapoptosis;

		this.img = intimg;
		
		this.trackmate = trackmate;
		
		this.model = model;
		
		this.settings = settings;
		
		this.modelsettings = modelsettings;
		
		this.displaySettings = displaySettings;
		
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

		
		trackmate.getSettings().imp.getOverlay().clear();
		SpotOverlay spotOverlay = createSpotOverlay( displaySettings,trackmate.getSettings().imp );
		TrackOverlay trackOverlay = createTrackOverlay( displaySettings, trackmate.getSettings().imp );
		trackmate.getSettings().imp.getOverlay().add( spotOverlay );
		
		trackmate.getSettings().imp.getOverlay().add( trackOverlay );
		
		
		final long start = System.currentTimeMillis();
		divisionspots = new SpotCollection();
		divisionframespots = new HashMap<Integer, ArrayList<Spot>>();

		apoptosisspots = new SpotCollection();
		apoptosisframespots = new HashMap<Integer, ArrayList<Spot>>();
        int ndims = img.numDimensions() - 1;
		Pair<  Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>> result = 
				TrackCorrectorRunner.run(oneatdivision, oneatapoptosis, settings, ndims, calibration);
		
		// Get first TrackMate object as in blue print
		Pair<Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>>, Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>>> Tmobject =  
				TrackCorrectorRunner.getFirstTrackMateobject(model, img, logger, 
				calibration);
		
		
		//Oneat found spots for mitosis
		divisionspots = result.getA().getA();
		divisionframespots = result.getA().getB();
		
		//Oneat found spots for apoptosis
		apoptosisspots = result.getB().getA();
		apoptosisframespots = result.getB().getB();
		
		
		
		
		//We have to regerenate the graph and tracks after correction
		if(divisionspots.keySet().size() > 0) 
			
			// This object contains the track ID and a list of split points and the root of the lineage tree
			Mitossisspots = TrackCorrectorRunner.getmitosisTrackID( Tmobject.getA(), model, img, divisionframespots, settings, logger, calibration);
			
			
		
        if(apoptosisspots.keySet().size() > 0) 
			
        	// This object contains the track ID and a list of single object with the apoptotic spot where the track has to terminate and the root of the lineage tree
			Apoptosisspots = TrackCorrectorRunner.getapoptosisTrackID( Tmobject.getA(), model, img, apoptosisframespots, settings, logger, calibration); 
			
			// To be safe let us sort the dead points in ascending order of frame
		
			graph = TrackCorrectorRunner.getCorrectedTracks(model, trackmate, Tmobject.getA(), Tmobject.getB(), Mitossisspots, Apoptosisspots, settings, ndims, logger, numThreads,
					img,divisionframespots, calibration ); 	
			
			
			 //Check that the objects list itself isn't null
			if ( null == graph )
			{
				errorMessage = BASE_ERROR_MESSAGE + "The output graph is null.";
				return false;
			}
			
			
			
		
		  
		
			modelsettings.trackerSettings.put(KEY_ALLOW_TRACK_SPLITTING, true);
		    
			trackmate.computeTrackFeatures(true);
		  
		  
			logger.setProgress( 1d );
			logger.setStatus( "" );
			final long end = System.currentTimeMillis();
			processingTime = end - start;

		return true;
	}
	
	public void refresh(ImagePlus imp)
	{
		if ( null != imp )
			imp.updateAndDraw();
	}
	
	
	protected SpotOverlay createSpotOverlay(final DisplaySettings displaySettings, ImagePlus imp)
	{
		return new SpotOverlay( model, imp, displaySettings );
	}

	/**
	 * Hook for subclassers. Instantiate here the overlay you want to use for
	 * the spots.
	 * @param displaySettings
	 *
	 * @return the track overlay
	 */
	protected TrackOverlay createTrackOverlay(final DisplaySettings displaySettings, ImagePlus imp)
	{
		return new TrackOverlay( model, imp, displaySettings );
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
