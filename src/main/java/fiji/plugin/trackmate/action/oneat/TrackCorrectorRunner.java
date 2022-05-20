package fiji.plugin.trackmate.action.oneat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.GraphIterator;
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.options.OptionsService;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Logger.SlaveLogger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.tracking.sparselap.costfunction.CostFunction;
import fiji.plugin.trackmate.tracking.sparselap.costmatrix.JaqamanSegmentCostMatrixCreator;
import fiji.plugin.trackmate.tracking.sparselap.linker.JaqamanLinker;
import fiji.plugin.trackmate.util.TMUtils;
import net.imglib2.util.Util;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

import static fiji.plugin.trackmate.Spot.POSITION_X;
import static fiji.plugin.trackmate.Spot.POSITION_Y;
import static fiji.plugin.trackmate.Spot.POSITION_Z;
import static fiji.plugin.trackmate.Spot.FRAME;
import static fiji.plugin.trackmate.Spot.RADIUS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_BREAK_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_CREATE_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_PROB_THRESHOLD;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_TRACKLET_LENGTH;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.Spot.QUALITY;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_SPLITTING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_GAP_CLOSING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_MERGING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_CUTOFF_PERCENTILE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALTERNATIVE_LINKING_COST_FACTOR;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_MERGING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.DEFAULT_SPLITTING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.DEFAULT_LINKING_FEATURE_PENALTIES;
public class TrackCorrectorRunner {

	private final static Context context = TMUtils.getContext();

	private static SimpleWeightedGraph<Spot, DefaultWeightedEdge> removeTracklets(final Model model,
			final SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph, final Map<String, Object> settings) {
		double timecutoff = 1;
		TrackModel trackModel = model.getTrackModel();
		if (settings.get(KEY_TRACKLET_LENGTH) != null)
			timecutoff = (Integer) settings.get(KEY_TRACKLET_LENGTH);

		for (final Integer trackID : trackModel.trackIDs(false)) {

			ArrayList<Pair<Integer, Spot>> Sources = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Targets = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Integer> SourcesID = new ArrayList<Integer>();
			ArrayList<Integer> TargetsID = new ArrayList<Integer>();
			ArrayList<Pair<Integer, Spot>> Starts = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Ends = new ArrayList<Pair<Integer, Spot>>();
			HashSet<Pair<Integer, Spot>> Splits = new HashSet<Pair<Integer, Spot>>();

			final Set<DefaultWeightedEdge> track = trackModel.trackEdges(trackID);

			for (final DefaultWeightedEdge e : track) {

				Spot Spotbase = model.getTrackModel().getEdgeSource(e);
				Spot Spottarget = model.getTrackModel().getEdgeTarget(e);

				Integer targetID = Spottarget.ID();
				Integer sourceID = Spotbase.ID();
				Sources.add(new ValuePair<Integer, Spot>(sourceID, Spotbase));
				Targets.add(new ValuePair<Integer, Spot>(targetID, Spottarget));
				SourcesID.add(sourceID);
				TargetsID.add(targetID);

			}
			// find track ends
			for (Pair<Integer, Spot> tid : Targets) {

				if (!SourcesID.contains(tid.getA())) {

					Ends.add(tid);

				}

			}

			// find track starts
			for (Pair<Integer, Spot> sid : Sources) {

				if (!TargetsID.contains(sid.getA())) {

					Starts.add(sid);

				}

			}

			// find track splits
			int scount = 0;
			for (Pair<Integer, Spot> sid : Sources) {

				for (Pair<Integer, Spot> dupsid : Sources) {

					if (dupsid.getA().intValue() == sid.getA().intValue()) {
						scount++;
					}
				}
				if (scount > 1) {
					Splits.add(sid);
				}
				scount = 0;
			}

			if (Splits.size() > 0) {

				for (Pair<Integer, Spot> sid : Ends) {

					Spot Spotend = sid.getB();

					int trackletlength = 0;

					double minsize = Double.MAX_VALUE;
					Spot Actualsplit = null;
					for (Pair<Integer, Spot> splitid : Splits) {
						Spot Spotstart = splitid.getB();
						Set<Spot> spotset = connectedSetOf(graph, Spotend, Spotstart);

						if (spotset.size() < minsize) {

							minsize = spotset.size();
							Actualsplit = Spotstart;

						}

					}

					if (Actualsplit != null) {
						Set<Spot> connectedspotset = connectedSetOf(graph, Spotend, Actualsplit);
						trackletlength = (int) Math.abs(Actualsplit.diffTo(Spotend, Spot.FRAME));

						if (trackletlength <= timecutoff) {

							Iterator<Spot> it = connectedspotset.iterator();
							while (it.hasNext())
								graph.removeVertex(it.next());

						}
					}

				}
			}
		}

		return graph;

	}

	private static Set<Spot> connectedSetOf(SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph, Spot vertex,
			Spot split) {

		Set<Spot> connectedSet = new HashSet<>();

		connectedSet = new HashSet<>();

		BreadthFirstIterator<Spot, DefaultWeightedEdge> i = new BreadthFirstIterator<>(graph, vertex);

		do {
			Spot spot = i.next();
			if (spot.ID() == split.ID()) {
				break;

			}
			connectedSet.add(spot);
		} while (i.hasNext());

		return connectedSet;
	}

	public static SimpleWeightedGraph<Spot, DefaultWeightedEdge> getCorrectedTracks(final Model model,
			HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID, 
			Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots,
			HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Mitosisspots,
			HashMap<Integer, Pair<Spot, Spot>> Apoptosisspots, Map<String, Object> settings, final int ndim,
			final Logger logger, int numThreads,final ImgPlus<IntType> intimg, HashMap<Integer, ArrayList<Spot>> framespots,
			 double[] calibration) {

		// Get the trackmodel and spots in the default tracking result and start to
		// create a new graph
		TrackModel trackmodel = model.getTrackModel();
		SpotCollection allspots = model.getSpots();
		SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

		double searchdistance = (double) settings.get(KEY_LINKING_MAX_DISTANCE);
		int tmoneatdeltat = (int) settings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		boolean createlinks = (boolean) settings.get(KEY_CREATE_LINKS);
		boolean breaklinks = (boolean) settings.get(KEY_BREAK_LINKS);
		Set<Integer> MitosisIDs = new HashSet<Integer>();
		Set<Integer> ApoptosisIDs = new HashSet<Integer>();
		
		
		//Generate the default graph
				
					// Nothing special here just maintaining the normal links found
					Set<DefaultWeightedEdge> normaltracks = trackmodel.edgeSet();
					for (final DefaultWeightedEdge edge : normaltracks) {
						final Spot source = trackmodel.getEdgeSource(edge);
						final Spot target = trackmodel.getEdgeTarget(edge);
						graph.addVertex(source);
						graph.addVertex(target);
						final DefaultWeightedEdge newedge = graph.addEdge(source, target);
						graph.setEdgeWeight(newedge, graph.getEdgeWeight(newedge));

						
					}

				
				
				if (breaklinks) 
					
					 graph = BreakLinksTrack(model, uniquelabelID, DividingStartspots, framespots,  intimg, logger, graph,calibration,tmoneatdeltat); 
				
				
		int count = 0;
		if (Apoptosisspots != null) {

			logger.log("Verifying apoptosis.\n");
			// Lets take care of apoptosis
			for (Map.Entry<Integer, Pair<Spot, Spot>> trackidspots : Apoptosisspots.entrySet()) {
				count++;
				// Get the current trackID
				int trackID = trackidspots.getKey();
				Pair<Spot, Spot> trackspots = trackidspots.getValue();
				ApoptosisIDs.add(trackID);

				// Apoptosis cell can not be source of an edge
				Spot killerspot = trackspots.getB(); 
                
					logger.setProgress((float) (count) / Apoptosisspots.size());
					Set<DefaultWeightedEdge> killertrack = trackmodel.trackEdges(trackID);
					for (final DefaultWeightedEdge edge : killertrack) {
						final Spot source = trackmodel.getEdgeSource(edge);
						graph.addVertex(source);
						if (source != killerspot) {

							final Spot target = trackmodel.getEdgeTarget(edge);
							graph.addVertex(target);
							final DefaultWeightedEdge newedge = graph.addEdge(source, target);
							graph.setEdgeWeight(newedge, graph.getEdgeWeight(newedge));
						}
					

				}

			}

		}

		
		
		count = 0;
		
		
		
		if (createlinks) {
			Map<String, Object> cmsettings = new HashMap<>();
			// Gap closing.
			
			final int maxFrameInterval = ( Integer ) settings.get( KEY_GAP_CLOSING_MAX_FRAME_GAP );
			final double gcMaxDistance = ( Double ) settings.get( KEY_GAP_CLOSING_MAX_DISTANCE );
			
			final boolean allowGapClosing = ( Boolean ) settings.get( KEY_ALLOW_GAP_CLOSING );
			final boolean allowTrackMerging = ( Boolean ) settings.get( KEY_ALLOW_TRACK_MERGING );
			final boolean allowTrackSplitting = ( Boolean ) settings.get( KEY_ALLOW_TRACK_SPLITTING );
			// Merging
			 double mMaxDistance = Double.MAX_VALUE;
			 double sMaxDistance = Double.MAX_VALUE;
			final boolean allowMerging = ( Boolean ) settings.get( KEY_ALLOW_TRACK_MERGING );
			if(allowTrackMerging) 
			mMaxDistance = ( Double ) settings.get( KEY_MERGING_MAX_DISTANCE );
			else 
				mMaxDistance = searchdistance;
			if (allowTrackSplitting) 
			// Splitting
			sMaxDistance = ( Double ) settings.get( KEY_SPLITTING_MAX_DISTANCE );
			else 
				sMaxDistance = searchdistance;
			// Alternative cost
			final double alternativeCostFactor = ( Double ) settings.get( KEY_ALTERNATIVE_LINKING_COST_FACTOR );
			final double percentile = ( Double ) settings.get( KEY_CUTOFF_PERCENTILE );
			cmsettings.put(KEY_ALLOW_TRACK_SPLITTING, true);
			cmsettings.put(KEY_SPLITTING_MAX_DISTANCE, sMaxDistance);
			cmsettings.put(KEY_GAP_CLOSING_MAX_FRAME_GAP, maxFrameInterval);
			cmsettings.put(KEY_ALLOW_GAP_CLOSING, allowGapClosing);
			cmsettings.put(KEY_ALLOW_TRACK_MERGING, allowMerging);
			cmsettings.put(KEY_CUTOFF_PERCENTILE, percentile);
			cmsettings.put(KEY_ALTERNATIVE_LINKING_COST_FACTOR, alternativeCostFactor);
			cmsettings.put(KEY_GAP_CLOSING_MAX_DISTANCE, gcMaxDistance);
			cmsettings.put(KEY_MERGING_MAX_DISTANCE, mMaxDistance);
			if (settings.get(KEY_SPLITTING_FEATURE_PENALTIES)!=DEFAULT_SPLITTING_FEATURE_PENALTIES)
			cmsettings.put(KEY_SPLITTING_FEATURE_PENALTIES, settings.get(KEY_SPLITTING_FEATURE_PENALTIES));
			else
			cmsettings.put(KEY_SPLITTING_FEATURE_PENALTIES, settings.get(KEY_LINKING_FEATURE_PENALTIES));
			logger.log("Removing mitotic edges.\n");
			// Lets take care of mitosis
			if (Mitosisspots != null) {
				

				int trackcount = 0;
				for (Map.Entry<Integer, Pair<Spot, ArrayList<Spot>>> trackidspots : Mitosisspots.entrySet()) {

					SimpleWeightedGraph<Spot, DefaultWeightedEdge> localgraph 
					= new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
					
					logger.setProgress((float) (count) / Mitosisspots.size());
					// Get the current trackID
					int trackID = trackidspots.getKey();
					Set<DefaultWeightedEdge> dividingtracks = trackmodel.trackEdges(trackID);
					
					
					// List of all the mother cells and the root of the lineage tree
					Pair<Spot, ArrayList<Spot>> trackspots = trackidspots.getValue();
					
					ArrayList<Spot> mitosismotherspots = trackspots.getB();
					count++;
				
					//Remove edges corresponding to mitotic trajectories
					for (final DefaultWeightedEdge edge : dividingtracks) {
						
						final Spot source = trackmodel.getEdgeSource(edge);
						
						if(mitosismotherspots.contains(source)) {
							
							graph.removeEdge(edge);
							
						}
						
					}
					
					for(DefaultWeightedEdge localedge: dividingtracks) {
						
						final Spot source = trackmodel.getEdgeSource(localedge);
						final Spot target = trackmodel.getEdgeTarget(localedge);
						final double linkcost = trackmodel.getEdgeWeight(localedge);
						localgraph.addVertex(source);
						localgraph.addVertex(target);
						localgraph.addEdge(source, target);
						localgraph.setEdgeWeight(localedge, linkcost);
						
						
					}
					
					for(Spot motherspot : mitosismotherspots ) {
					
						
						for(int i = 0; i < tmoneatdeltat/2; ++i) {
					
							
							double frame = motherspot.getFeature(FRAME) + i;
						    if (frame > 0) {
						
						SpotCollection regionspots =  regionspot(allspots,motherspot, (int) frame, searchdistance);
						if(regionspots.getNSpots((int)frame, false) > 0)
					    for(Spot spot : regionspots.iterable((int)frame, false)) {
					    
					    	if(trackmodel.trackIDOf(spot)!=null ) { 
					    	int regiontrackID = trackmodel.trackIDOf(spot);
					    	Set<DefaultWeightedEdge> localtracks = trackmodel.trackEdges(regiontrackID);
					    	
					    	for(DefaultWeightedEdge localedge: localtracks) {
								
					    		
					    		
								final Spot source = trackmodel.getEdgeSource(localedge);
								if(source.getFeature(FRAME) >= frame) {
								final Spot target = trackmodel.getEdgeTarget(localedge);
								final double linkcost = trackmodel.getEdgeWeight(localedge);
								
							
								localgraph.addVertex(source);
								localgraph.addVertex(target);
								localgraph.addEdge(source, target);
								localgraph.setEdgeWeight(localedge, linkcost);
								
								}
							}
					    	
					    	}
					    }
					
					}
						}
					}
					
					
					
					final OneatCostMatrix costMatrixCreator = new OneatCostMatrix( localgraph, cmsettings );
					costMatrixCreator.setNumThreads( numThreads );
					final SlaveLogger jlLogger = new SlaveLogger( logger, 0, 0.9 );
					final JaqamanLinker< Spot, Spot > linker = new JaqamanLinker<>( costMatrixCreator, jlLogger );
					if ( !linker.checkInput() || !linker.process() )
					{
						System.out.println(linker.getErrorMessage());
					}


					/*
					 * Create links in graph.
					 */

					logger.setProgress( trackcount/ (Mitosisspots.size() + 1));
					
                    trackcount++;
					final Map< Spot, Spot > assignment = linker.getResult();
					final Map< Spot, Double > costs = linker.getAssignmentCosts();
					//Recreate new links
					if(assignment!=null)
					for ( final Spot source : assignment.keySet() )
					{
						final Spot target = assignment.get( source );
						
						Set<DefaultWeightedEdge> targetlinks = trackmodel.edgesOf(target);
						// Remove the targetsource and target edge prior to assingment
						for(DefaultWeightedEdge targetedge: targetlinks) {
							
							Spot targetsource = trackmodel.getEdgeSource(targetedge);
							graph.removeEdge(targetsource, target);
						}
						
						
						final double cost = costs.get( source );
					    if(cost >= 10) {		
						System.out.println(cost);
						final DefaultWeightedEdge edge = graph.addEdge( source, target );
						graph.setEdgeWeight( edge, cost );
						
					    }
					
						
					}


				}
				
				
				
			}
			
		
			

		}
		logger.setProgress( 1d );
		logger.flush();
		logger.log("Done, please review the TrackScheme by going back.\n");
	
	
		
		return graph;

	}



	private static SpotCollection regionspot(SpotCollection allspots, Spot motherspot, int frame, double region) {

		SpotCollection regionspots = new SpotCollection();
		final int Nspots = allspots.getNSpots(frame, false);
		if (Nspots> 0)
		for (Spot spot : allspots.iterable(frame, false)) {

			if (motherspot.squareDistanceTo(spot) <= region * region) {

				regionspots.add(spot, frame);

			}

		}

		return regionspots;
	} 
	
	public static Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>>> getfirstTrackMateobject(
			final Model model, 
			final ImgPlus<IntType> intimg, 
			final Logger logger, 
			double[] calibration) {
		
		Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots = getTMDividing(model);
		int ndim = intimg.numDimensions() - 1;
		RandomAccess<IntType> ranac = intimg.randomAccess();
		Set<Integer> AllTrackIds = model.getTrackModel().trackIDs(false);
		HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID = new HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>();
		logger.flush();
		logger.log("Collecting tracks, in total " + AllTrackIds.size() + ".\n");
		int count = 0;
		for (int trackID : AllTrackIds) {

			Set<Spot> trackspots = model.getTrackModel().trackSpots(trackID);
			count++;
			for (Spot spot : trackspots) {

				logger.setProgress((float) (count) / (AllTrackIds.size() + 1));

				int frame = spot.getFeature(FRAME).intValue();
				if (frame < intimg.dimension(ndim) - 1) {
					long[] location = new long[ndim];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) (spot.getDoublePosition(d) / calibration[d]);
						ranac.setPosition(location[d], d);
					}

					ranac.setPosition(frame, ndim);
					int label = ranac.get().get();

					uniquelabelID.put(new ValuePair<Integer, Integer>(label, frame),
							new ValuePair<Spot, Integer>(spot, trackID));

				}
			}
		}
		
		
		return new ValuePair <HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>>> (uniquelabelID, DividingStartspots);
		
	}
	
	public static <T extends RealType<T> & NativeType<T>> HashMap<Integer, Pair<Spot, Spot>> getapoptosisTrackID(
			HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID,
			final Model model, final ImgPlus<IntType> intimg, HashMap<Integer, ArrayList<Spot>> framespots,
			final Map<String, Object> mapsettings, final Logger logger, double[] calibration) {

		HashMap<Integer, Spot> Apoptosisspots = new HashMap<Integer, Spot>();

		// Starting point of the tree + apoptotic spot in the trackID
		HashMap<Integer, Pair<Spot, Spot>> Trackapoptosis = new HashMap<Integer, Pair<Spot, Spot>>();
		// Spots from trackmate

		int ndim = intimg.numDimensions() - 1;
		int tmoneatdeltat = (int) mapsettings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		RandomAccess<IntType> ranac = intimg.randomAccess();

		
		int count = 0;
		

		logger.log("Matching with oneat apoptosis spots.\n");
		logger.setProgress(0.);
		

		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			int frame = framemap.getKey();
			if (frame < intimg.dimension(ndim) - 1) {
				count++;

				ArrayList<Spot> spotlist = framemap.getValue();

				for (Spot currentspots : spotlist) {

					logger.setProgress((float) (count) / framespots.size());

					long[] location = new long[ndim];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) (currentspots.getDoublePosition(d) / calibration[d]);
						ranac.setPosition(location[d], d);
					}
					ranac.setPosition(frame, ndim);
					// Get the label ID of the current interesting spot
					int labelID = ranac.get().get();

					if (uniquelabelID.containsKey(new ValuePair<Integer, Integer>(labelID, frame))) {
						Pair<Spot, Integer> spotandtrackID = uniquelabelID
								.get(new ValuePair<Integer, Integer>(labelID, frame));
						// Now get the spot ID

						Spot spot = spotandtrackID.getA();

						int trackID = spotandtrackID.getB();
						Pair<Boolean, Pair<Spot, Spot>> isDividingTMspot = isDividingTrack(spot, trackID, tmoneatdeltat,
								model);

						// If it is an apoptosis event we currently do not have any function to check if
						// TM trajectory has it so we add oneat given trackid

						Spot startspot = isDividingTMspot.getB().getA();

						ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
						trackspotlist.add(spot);
						Apoptosisspots.put(trackID, spot);
						Pair<Spot, Spot> pair = new ValuePair<Spot, Spot>(spot, startspot);
						Trackapoptosis.put(trackID, pair);

					}
				}

			}
		}

		logger.log("Verifying lineage trees.\n");
		logger.setProgress(0.);

		return Trackapoptosis;
	}

	public static <T extends RealType<T> & NativeType<T>> HashMap<Integer, Pair<Spot, ArrayList<Spot>>> getmitosisTrackID(
			HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID,
			final Model model, final ImgPlus<IntType> intimg, HashMap<Integer, ArrayList<Spot>> framespots,
			final Map<String, Object> mapsettings, final Logger logger,
			double[] calibration) {

		HashMap<Integer, ArrayList<Spot>> Mitosisspots = new HashMap<Integer, ArrayList<Spot>>();

		// Starting point of the tree + list of mitosis spots in the trackID
		HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Trackmitosis = new HashMap<Integer, Pair<Spot, ArrayList<Spot>>>();
		// Spots from trackmate

		int ndim = intimg.numDimensions() - 1;
		int tmoneatdeltat = (int) mapsettings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		RandomAccess<IntType> ranac = intimg.randomAccess();

		logger.log("Matching with oneat mitosis spots.\n");
		logger.setProgress(0.);
		int count = 0;

		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			int frame = framemap.getKey();
			if (frame < intimg.dimension(ndim) - 1) {
				count++;

				ArrayList<Spot> spotlist = framemap.getValue();

				for (Spot currentspots : spotlist) {

					logger.setProgress((float) (count) / framespots.size());

					long[] location = new long[ndim];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) (currentspots.getDoublePosition(d) / calibration[d]);
						ranac.setPosition(location[d], d);
					}
					ranac.setPosition(frame, ndim);
					// Get the label ID of the current interesting spot
					int labelID = ranac.get().get();

					if (uniquelabelID.containsKey(new ValuePair<Integer, Integer>(labelID, frame))) {
						Pair<Spot, Integer> spotandtrackID = uniquelabelID
								.get(new ValuePair<Integer, Integer>(labelID, frame));
						// Now get the spot ID

						Spot spot = spotandtrackID.getA();

						int trackID = spotandtrackID.getB();
						Pair<Boolean, Pair<Spot, Spot>> isDividingTMspot = isDividingTrack(spot, trackID, tmoneatdeltat,
								model);
						Boolean isDividing = isDividingTMspot.getA();
						// If isDividing is true oneat does not need to correct the track else it has to
						// correct the trackid
						if (!isDividing) {

							Spot startspot = isDividingTMspot.getB().getA();

							if (Mitosisspots.containsKey(trackID)) {

								ArrayList<Spot> trackspotlist = Mitosisspots.get(trackID);
								if (!trackspotlist.contains(spot))
									trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<Spot, ArrayList<Spot>> pairlist = new ValuePair<Spot, ArrayList<Spot>>(startspot,
										trackspotlist);
								Trackmitosis.put(trackID, pairlist);

							} else {

								ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
								trackspotlist.add(spot);

								Pair<Spot, ArrayList<Spot>> pairlist = new ValuePair<Spot, ArrayList<Spot>>(startspot,
										trackspotlist);
								Trackmitosis.put(trackID, pairlist);

							}

						}

					}
				}

			}
		}

		logger.log("Verifying lineage trees.\n");
		logger.setProgress(0.);

		return Trackmitosis;
	}
	
	private static SimpleWeightedGraph<Spot, DefaultWeightedEdge> BreakLinksTrack(final Model model, 
			HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID, 
			Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots,
			HashMap<Integer, ArrayList<Spot>>  framespots, 
			final ImgPlus<IntType> intimg, final Logger logger,final SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph,
			double[] calibration, int N) {
		
		int count = 0;
		logger.log("Breaking links not found by oneat.\n");
		
		
		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Dividingspotlocations  = DividingStartspots.getB();
		int ndim = intimg.numDimensions() - 1;
		
		
		Set<Integer> AllTrackIds = model.getTrackModel().trackIDs(false);
		
		
		RandomAccess<IntType> ranac = intimg.randomAccess();
		ArrayList<Integer> DividingTrackids = new ArrayList<Integer>();
		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			int frame = framemap.getKey();
			if (frame < intimg.dimension(ndim) - 1) {
				count++;

				ArrayList<Spot> spotlist = framemap.getValue();

				for (Spot currentspots : spotlist) {

					logger.setProgress((float) (count) / framespots.size());

					long[] location = new long[ndim];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) (currentspots.getDoublePosition(d) / calibration[d]);
						ranac.setPosition(location[d], d);
					}
					ranac.setPosition(frame, ndim);
					// Get the label ID of the current interesting spot
					int labelID = ranac.get().get();

					if (uniquelabelID.containsKey(new ValuePair<Integer, Integer>(labelID, frame))) {
						Pair<Spot, Integer> spotandtrackID = uniquelabelID
								.get(new ValuePair<Integer, Integer>(labelID, frame));
						// Now get the spot ID

						Spot spot = spotandtrackID.getA();
						int trackID = spotandtrackID.getB();
						Pair<Double, Spot> closestspotpair = closestSpot(spot, Dividingspotlocations.get(trackID));
						double closestdistance = closestspotpair.getA();
						Spot closestSpot = closestspotpair.getB();
						// There could be a N frame gap at most between the TM detected dividing spot
						// location and oneat found spot location
						if (closestdistance > N && closestSpot!=null ) {
							
							
							Set<DefaultWeightedEdge> e = model.getTrackModel().edgesOf(closestSpot);
							
							Iterator<DefaultWeightedEdge> it = e.iterator();
							while(it.hasNext()) {
								
								DefaultWeightedEdge edge  = it.next();
								graph.getEdgeSource(edge);
								graph.getEdgeTarget(edge);
								DefaultWeightedEdge thisedge = graph.getEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge));
								graph.removeEdge(thisedge );
									   
							}
							
							
						}
						else
						   DividingTrackids.add(trackID);
					}
					
				}
				
			}
			
		}
		
		AllTrackIds.removeAll(DividingTrackids);
		System.out.println(AllTrackIds.size() + " " + DividingTrackids.size());
		for(int trackID: AllTrackIds) {
			
			
			ArrayList<Pair<Integer, Spot>> badapple = Dividingspotlocations.get(trackID);
			
			for(Pair<Integer, Spot> removeapple: badapple) {
				
				Set<DefaultWeightedEdge> e = model.getTrackModel().edgesOf(removeapple.getB());
				
				Iterator<DefaultWeightedEdge> it = e.iterator();
				while(it.hasNext()) {
					
					DefaultWeightedEdge edge  = it.next();
					graph.getEdgeSource(edge);
					graph.getEdgeTarget(edge);
					DefaultWeightedEdge thisedge = graph.getEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge));
					graph.removeEdge(thisedge );
						   
				}
			
		}
			
		}
		return graph;
		
	}

	private static Pair<Boolean, Pair<Spot, Spot>> isDividingTrack(final Spot spot, final int trackID, final int N,
			final Model model) {

		Boolean isDividing = false;
		Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots = getTMDividing(
				model);
		Spot closestSpot = null;
		Spot startingspot = null;
		final Set<DefaultWeightedEdge> track = model.getTrackModel().trackEdges(trackID);

		for (final DefaultWeightedEdge e : track) {

			Spot Spotbase = model.getTrackModel().getEdgeSource(e);
			int id = model.getTrackModel().trackIDOf(Spotbase);

			if (id == trackID) {

				ArrayList<Pair<Integer, Spot>> Dividingspotlocations = DividingStartspots.getB().get(id);
				Pair<Integer, Spot> Startingspotlocations = DividingStartspots.getA().get(id);
				startingspot = Startingspotlocations.getB();
				Pair<Double, Spot> closestspotpair = closestSpot(spot, Dividingspotlocations);
				double closestdistance = closestspotpair.getA();
				closestSpot = closestspotpair.getB();
				// There could be a N frame gap at most between the TM detected dividing spot
				// location and oneat found spot location
				if (closestdistance < N) {
					isDividing = true;
					break;
				}

			}
		}

		return new ValuePair<Boolean, Pair<Spot, Spot>>(isDividing,
				new ValuePair<Spot, Spot>(startingspot, closestSpot));
	}

	private static Pair<Double, Spot> closestSpot(final Spot targetspot,
			final ArrayList<Pair<Integer, Spot>> Dividingspotlocations) {

		double mintimeDistance = Double.MAX_VALUE;
		Spot closestsourcespot = null;

		for (Pair<Integer, Spot> Dividingspot : Dividingspotlocations) {

			final Spot sourcespot = Dividingspot.getB();

			final double dist = sourcespot.diffTo(targetspot, FRAME);

			if (dist <= mintimeDistance) {

				mintimeDistance = dist;
				closestsourcespot = sourcespot;
			}

		}

		Pair<Double, Spot> closestspotpair = new ValuePair<Double, Spot>(Math.abs(mintimeDistance), closestsourcespot);

		return closestspotpair;

	}

	private static Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> getTMDividing(
			final Model model) {

		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Dividingspots = new HashMap<Integer, ArrayList<Pair<Integer, Spot>>>();
		HashMap<Integer, Pair<Integer, Spot>> Startingspots = new HashMap<Integer, Pair<Integer, Spot>>();
		TrackModel trackmodel = model.getTrackModel();
		for (final Integer trackID : trackmodel.trackIDs(false)) {

			final Set<DefaultWeightedEdge> track = trackmodel.trackEdges(trackID);

			ArrayList<Pair<Integer, Spot>> Sources = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Targets = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Integer> SourcesID = new ArrayList<Integer>();
			ArrayList<Integer> TargetsID = new ArrayList<Integer>();

			Pair<Integer, Spot> Starts = null;
			ArrayList<Pair<Integer, Spot>> Ends = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Splits = new ArrayList<Pair<Integer, Spot>>();

			for (final DefaultWeightedEdge e : track) {

				Spot Spotbase = trackmodel.getEdgeSource(e);
				Spot Spottarget = trackmodel.getEdgeTarget(e);

				Integer targetID = Spottarget.ID();
				Integer sourceID = Spotbase.ID();
				Sources.add(new ValuePair<Integer, Spot>(sourceID, Spotbase));
				Targets.add(new ValuePair<Integer, Spot>(targetID, Spottarget));
				SourcesID.add(sourceID);
				TargetsID.add(targetID);

			}
			// find track ends
			for (Pair<Integer, Spot> tid : Targets) {

				if (!SourcesID.contains(tid.getA())) {

					Ends.add(tid);

				}

			}

			// find track start
			for (Pair<Integer, Spot> sid : Sources) {

				if (!TargetsID.contains(sid.getA())) {

					Starts = sid;

				}

			}

			// find track splits
			int scount = 0;
			for (Pair<Integer, Spot> sid : Sources) {

				for (Pair<Integer, Spot> dupsid : Sources) {

					if (dupsid.getA().intValue() == sid.getA().intValue()) {
						scount++;
					}
				}
				if (scount > 1) {
					Splits.add(sid);
				}
				scount = 0;
			}

			Startingspots.put(trackID, Starts);
			Dividingspots.put(trackID, Splits);

		}

		return new ValuePair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>>(
				Startingspots, Dividingspots);

	}

	public static Pair<Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>> run(
			final File oneatdivisionfile, final File oneatapoptosisfile, Map<String, Object> settings, final int ndims,
			final double[] calibration) {

		SpotCollection divisionspots = new SpotCollection();
		HashMap<Integer, ArrayList<Spot>> DivisionSpotListFrame = new HashMap<Integer, ArrayList<Spot>>();

		double probthreshold = (double) settings.get(KEY_PROB_THRESHOLD);

		if (oneatdivisionfile != null) {
			String line = "";
			String cvsSplitBy = ",";
			int count = 0;

			ArrayList<Oneatobject> DivisionSpots = new ArrayList<Oneatobject>();
			HashMap<Integer, ArrayList<Oneatobject>> DivisionMap = new HashMap<Integer, ArrayList<Oneatobject>>();
			try (BufferedReader br = new BufferedReader(new FileReader(oneatdivisionfile))) {

				while ((line = br.readLine()) != null) {

					// use comma as separator
					String[] divisionspotsfile = line.split(cvsSplitBy);

					if (count > 0) {

						int time = (int) Double.parseDouble(divisionspotsfile[0]) - 1;
						double Z = Double.parseDouble(divisionspotsfile[1]) * calibration[2];
						double Y = Double.parseDouble(divisionspotsfile[2]) * calibration[1];
						double X = Double.parseDouble(divisionspotsfile[3]) * calibration[0];
						double score = Double.parseDouble(divisionspotsfile[4]);
						double size = Double.parseDouble(divisionspotsfile[5]);
						double confidence = Double.parseDouble(divisionspotsfile[6]);
						double angle = Double.parseDouble(divisionspotsfile[7]);

						if (score >= probthreshold) {
							Oneatobject Spot = new Oneatobject(time, Z, Y, X, score, size, confidence, angle);

							if (DivisionMap.get(time) == null) {
								DivisionSpots = new ArrayList<Oneatobject>();
								DivisionMap.put(time, DivisionSpots);
							} else
								DivisionMap.put(time, DivisionSpots);
							DivisionSpots.add(Spot);
						}

					}
					count = count + 1;
				}
			} catch (IOException ie) {
				ie.printStackTrace();
			}

			// Parse each component.

			final Iterator<Entry<Integer, ArrayList<Oneatobject>>> iterator = DivisionMap.entrySet().iterator();

			while (iterator.hasNext()) {
				final Map.Entry<Integer, ArrayList<Oneatobject>> region = iterator.next();

				int frame = region.getKey();
				ArrayList<Oneatobject> currentcell = region.getValue();
				ArrayList<Spot> currentspots = new ArrayList<Spot>();
				for (Oneatobject cell : currentcell) {
					final double x = (cell.X);
					final double y = (cell.Y);
					final double z = (cell.Z);

					double volume = cell.size * calibration[0] * calibration[1] * calibration[2];
					double quality = cell.size;

					final double radius = (ndims == 2) ? Math.sqrt(volume / Math.PI)
							: Math.pow(3. * volume / (4. * Math.PI), 1. / 3.);

					Spot currentspot = new Spot(x, y, z, radius, quality);
					// Put spot features so we can get it back by feature name
					currentspot.putFeature(POSITION_X, Double.valueOf(x));
					currentspot.putFeature(POSITION_Y, Double.valueOf(y));
					currentspot.putFeature(POSITION_Z, Double.valueOf(z));
					currentspot.putFeature(FRAME, Double.valueOf(frame));
					currentspot.putFeature(RADIUS, Double.valueOf(radius));
					currentspot.putFeature(QUALITY, Double.valueOf(quality));

					currentspots.add(currentspot);
					divisionspots.add(currentspot, frame);
					DivisionSpotListFrame.put(frame, currentspots);
				}

			}

		}

		SpotCollection apoptosisspots = new SpotCollection();
		HashMap<Integer, ArrayList<Spot>> ApoptosisSpotListFrame = new HashMap<Integer, ArrayList<Spot>>();
		if (oneatapoptosisfile != null) {
			String line = "";
			String cvsSplitBy = ",";
			int count = 0;
			ArrayList<Oneatobject> ApoptosisSpots = new ArrayList<Oneatobject>();
			HashMap<Integer, ArrayList<Oneatobject>> ApoptosisMap = new HashMap<Integer, ArrayList<Oneatobject>>();
			try (BufferedReader br = new BufferedReader(new FileReader(oneatapoptosisfile))) {

				while ((line = br.readLine()) != null) {

					// use comma as separator
					String[] apoptosisspotsfile = line.split(cvsSplitBy);

					if (count > 0) {

						int time = Integer.parseInt(apoptosisspotsfile[0]) - 1;
						double Z = Double.parseDouble(apoptosisspotsfile[1]) * calibration[2];
						double Y = Double.parseDouble(apoptosisspotsfile[2]) * calibration[1];
						double X = Double.parseDouble(apoptosisspotsfile[3]) * calibration[0];
						double score = Double.parseDouble(apoptosisspotsfile[4]);
						double size = Double.parseDouble(apoptosisspotsfile[5]);
						double confidence = Double.parseDouble(apoptosisspotsfile[6]);
						double angle = Double.parseDouble(apoptosisspotsfile[7]);
						if (score >= probthreshold) {
							Oneatobject Spot = new Oneatobject(time, Z, Y, X, score, size, confidence, angle);

							if (ApoptosisMap.get(time) == null) {
								ApoptosisSpots = new ArrayList<Oneatobject>();
								ApoptosisMap.put(time, ApoptosisSpots);
							} else
								ApoptosisMap.put(time, ApoptosisSpots);
							ApoptosisSpots.add(Spot);
						}

					}
					count = count + 1;
				}
			} catch (IOException ie) {
				ie.printStackTrace();
			}

			// Parse each component.

			final Iterator<Entry<Integer, ArrayList<Oneatobject>>> iterator = ApoptosisMap.entrySet().iterator();

			while (iterator.hasNext()) {
				final Map.Entry<Integer, ArrayList<Oneatobject>> region = iterator.next();

				int frame = region.getKey();
				ArrayList<Oneatobject> currentcell = region.getValue();
				ArrayList<Spot> currentspots = new ArrayList<Spot>();
				for (Oneatobject cell : currentcell) {
					final double x = (cell.X);
					final double y = (cell.Y);
					final double z = (cell.Z);

					double volume = cell.size * calibration[0] * calibration[1] * calibration[2];
					double quality = cell.size;

					final double radius = (ndims == 2) ? Math.sqrt(volume / Math.PI)
							: Math.pow(3. * volume / (4. * Math.PI), 1. / 3.);

					Spot currentspot = new Spot(x, y, z, radius, quality);
					currentspot.putFeature(POSITION_X, Double.valueOf(x));
					currentspot.putFeature(POSITION_Y, Double.valueOf(y));
					currentspot.putFeature(POSITION_Z, Double.valueOf(z));
					currentspot.putFeature(FRAME, Double.valueOf(frame));
					currentspot.putFeature(RADIUS, Double.valueOf(radius));
					currentspot.putFeature(QUALITY, Double.valueOf(quality));
					currentspots.add(currentspot);
					apoptosisspots.add(currentspot, frame);
					ApoptosisSpotListFrame.put(frame, currentspots);
				}

			}

		}

		Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> DivisionPair = new ValuePair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>(
				divisionspots, DivisionSpotListFrame);

		Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> ApoptosisPair = new ValuePair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>(
				apoptosisspots, ApoptosisSpotListFrame);

		return new ValuePair<Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>>(
				DivisionPair, ApoptosisPair);
	}

}
