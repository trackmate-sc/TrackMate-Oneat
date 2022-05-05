package fiji.plugin.trackmate.oneat;

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
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_TIME_GAP;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_SPLITTING_MAX_DISTANCE;
import static fiji.plugin.trackmate.Spot.QUALITY;

public class TrackCorrectorRunner {

	private final static Context context = TMUtils.getContext();

	public static SimpleWeightedGraph<Spot, DefaultWeightedEdge> getCorrectedTracks(final Model model,
			HashMap<Integer, Pair<ArrayList<Spot>, Spot>> Mitosisspots,
			HashMap<Integer, Pair<ArrayList<Spot>, Spot>> Apoptosisspots, Map<String, Object> settings, final int ndim,
			final Logger logger, int numThreads) {

		// Get the trackmodel and spots in the default tracking result and start to
		// create a new graph
		TrackModel trackmodel = model.getTrackModel();
		SpotCollection allspots = model.getSpots();
		SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

		double searchdistance = (double) settings.get(KEY_SPLITTING_MAX_DISTANCE);
		int tmoneatdeltat = (int) settings.get(KEY_TIME_GAP);
		boolean createlinks = (boolean) settings.get(KEY_CREATE_LINKS);
		boolean breaklinks = (boolean) settings.get(KEY_BREAK_LINKS);
		Set<Integer> AlltrackIDs = trackmodel.trackIDs(false);
		Set<Integer> MitosisIDs = new HashSet<Integer>();
		Set<Integer> ApoptosisIDs = new HashSet<Integer>();
		int count = 0;
		if (Apoptosisspots != null) {

			logger.log("Verifying apoptosis.\n");
			// Lets take care of apoptosis
			for (Map.Entry<Integer, Pair<ArrayList<Spot>, Spot>> trackidspots : Apoptosisspots.entrySet()) {
				count++;
				// Get the current trackID
				int trackID = trackidspots.getKey();
				Pair<ArrayList<Spot>, Spot> trackspots = trackidspots.getValue();
				ApoptosisIDs.add(trackID);

				// Apoptosis cell can not be source of an edge
				for (Spot killerspot : trackspots.getA()) {

					logger.setProgress((float) (count) / Apoptosisspots.size());
					Set<DefaultWeightedEdge> killertrack = trackmodel.trackEdges(trackID);
					for (final DefaultWeightedEdge edge : killertrack) {
						final Spot source = trackmodel.getEdgeSource(edge);
						graph.addVertex(source);
						if (source != killerspot) {

							final Spot target = trackmodel.getEdgeTarget(edge);
							graph.addVertex(target);
							final DefaultWeightedEdge newedge = graph.addEdge(source, target);
							graph.setEdgeWeight(newedge, -1);
						}
					}

				}

			}

		}

		count = 0;
		if (createlinks) {
			logger.log("Creating mitosis links.\n");
			// Lets take care of mitosis
			if (Mitosisspots != null)
				for (Map.Entry<Integer, Pair<ArrayList<Spot>, Spot>> trackidspots : Mitosisspots.entrySet()) {

					// Get the current trackID
					int trackID = trackidspots.getKey();
					// List of all the mother cells and the root of the lineage tree
					Pair<ArrayList<Spot>, Spot> trackspots = trackidspots.getValue();
                    Spot rootspot = trackspots.getB(); 
					count++;

					for (Spot motherspot : trackspots.getA()) {

						int currentframe = motherspot.getFeature(FRAME).intValue();
						// Get all the spots in the next frame in the local region
						SpotCollection regionspots = regionspot(allspots, motherspot, currentframe + 1, searchdistance);

						// Set of spots in mother track
						
						GraphIterator<Spot, DefaultWeightedEdge> rootiterator = 
								trackmodel.getDepthFirstIterator(rootspot, true);
						Set<Spot> rootspots = Gettrackspots(rootiterator);
						GraphIterator<Spot, DefaultWeightedEdge> motheriterator = 
								trackmodel.getDepthFirstIterator(motherspot, true);
						Set<Spot> motherspots = Gettrackspots(motheriterator);
						//Exclude mother spots from rootspots
						rootspots.removeAll(motherspots);
						//Create a graph from the rootspots
						Creategraph(rootspots, graph);
						for(Spot regionalspot: regionspots.iterable(false)) {
							
							// ALl the candidates for segment linking
							GraphIterator<Spot, DefaultWeightedEdge> regionspotiterator = 
									trackmodel.getDepthFirstIterator(regionalspot, true);
							Set<Spot> regionalspots = Gettrackspots(regionspotiterator);
							Creategraph(regionalspots, graph);
						}
						// Create the local graph in this region and create the cost matrix to find
						// local links

						logger.setStatus("Creating the segment linking cost matrix...");
						final JaqamanSegmentCostMatrixCreator costMatrixCreator = new JaqamanSegmentCostMatrixCreator(
								graph, settings);

						costMatrixCreator.setNumThreads(numThreads);
						final SlaveLogger jlLogger = new SlaveLogger( logger, 0, 0.9 );
						final JaqamanLinker< Spot, Spot > linker = new JaqamanLinker<>( costMatrixCreator, jlLogger );
						if ( !linker.checkInput() || !linker.process() )
						{
							linker.getErrorMessage();
							return null;
						}


						/*
						 * Create links in graph.
						 */

						logger.setProgress( 0.9d );
						logger.setStatus( "Creating links..." );

						final Map< Spot, Spot > assignment = linker.getResult();
						final Map< Spot, Double > costs = linker.getAssignmentCosts();

						for ( final Spot source : assignment.keySet() )
						{
							final Spot target = assignment.get( source );
							final DefaultWeightedEdge edge = graph.addEdge( source, target );

							final double cost = costs.get( source );
							graph.setEdgeWeight( edge, cost );
						}

					}
				}
		    }

		// Lets take care of no event tracks
		AlltrackIDs.removeAll(ApoptosisIDs);
		AlltrackIDs.removeAll(MitosisIDs);

		for (int trackID : AlltrackIDs) {

			// Nothing special here just mantaining the normal links found
			Set<DefaultWeightedEdge> normaltracks = trackmodel.trackEdges(trackID);
			for (final DefaultWeightedEdge edge : normaltracks) {
				final Spot source = trackmodel.getEdgeSource(edge);
				final Spot target = trackmodel.getEdgeTarget(edge);
				graph.addVertex(source);
				graph.addVertex(target);
				final DefaultWeightedEdge newedge = graph.addEdge(source, target);
				graph.setEdgeWeight(newedge, -1);

			}

		}

		return graph;

	}
	
	private static void Creategraph(Set<Spot> spots, SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph ) {
		
		Iterator<Spot> spotiterator = spots.iterator();
		
		while(spotiterator.hasNext()) {
			
			Spot source = spotiterator.next();
			
			if (spotiterator.hasNext()) {
				Spot target = spotiterator.next();
			
			    graph.addVertex(source);
			    graph.addVertex(target);
			    final DefaultWeightedEdge newedge = graph.addEdge(source, target);
				graph.setEdgeWeight(newedge, -1);
				
			}
			
		}
	}

	private static Set<Spot> Gettrackspots(GraphIterator<Spot, DefaultWeightedEdge> iterator) {
		
		Set<Spot> spots = new HashSet<Spot>();
		while(iterator.hasNext()) {
			
			Spot spot = iterator.next();
			spots.add(spot);
			
		}
		
		return spots;
		
	}
	
	private static SpotCollection regionspot(SpotCollection allspots, Spot motherspot, int frame, double region) {

		SpotCollection regionspots = new SpotCollection();
		for (Spot spot : allspots.iterable(frame, false)) {

			if (motherspot.squareDistanceTo(spot) < region * region) {

				regionspots.add(spot, frame);

			}

		}

		return regionspots;
	}

	private static Iterable<Spot> removespot(Iterable<Spot> spotsIt, Spot removespot) {

		Iterator<Spot> spots = spotsIt.iterator();
		Set<Spot> removespots = new HashSet<Spot>();

		while (spots.hasNext()) {
			Spot currentspot = spots.next();

			if (currentspot.hashCode() == (removespot.hashCode()))
				spots.remove();
			else
				removespots.add(currentspot);
		}

		return removespots;

	}

	public static <T extends RealType<T> & NativeType<T>> HashMap<Integer, Pair<ArrayList<Spot>, Spot>> getTrackID(
			final Model model, final ImgPlus<IntType> intimg, HashMap<Integer, ArrayList<Spot>> framespots,
			final Map<String, Object> mapsettings, final boolean checkdivision, final Logger logger) {

		HashMap<Integer, ArrayList<Spot>> Mitosisspots = new HashMap<Integer, ArrayList<Spot>>();
		HashMap<Integer, Pair<ArrayList<Spot>, Spot>> TrackIDstartspots = new HashMap<Integer, Pair<ArrayList<Spot>, Spot>>();
		// Spots from trackmate

		int ndim = intimg.numDimensions() - 1;
		int tmoneatdeltat = (int) mapsettings.get(KEY_TIME_GAP);
		RandomAccess<IntType> ranac = intimg.randomAccess();

		Set<Integer> AllTrackIds = model.getTrackModel().trackIDs(false);
		HashMap<String, Pair<Spot, Integer>> uniquelabelID = new HashMap<String, Pair<Spot, Integer>>();
		logger.flush();
		logger.log("Collecting tracks, in total " + AllTrackIds.size() + ".\n");
		int count = 0;
		for (int trackID : AllTrackIds) {

			Set<Spot> trackspots = model.getTrackModel().trackSpots(trackID);
			count++;
			for (Spot spot : trackspots) {

				logger.setProgress((float) (count) / AllTrackIds.size());

				int time = spot.getFeature(FRAME).intValue();
				if (time < intimg.dimension(ndim) - 1) {
					long[] location = new long[ndim];
					long[] timelocation = new long[ndim + 1];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) spot.getDoublePosition(d);
						timelocation[d] = location[d];
					}
					timelocation[ndim] = time;
					ranac.setPosition(timelocation);
					int label = ranac.get().get();

					String uniqueID = Integer.toString(label) + Integer.toString(time);
					uniquelabelID.put(uniqueID, new ValuePair<Spot, Integer>(spot, trackID));

				}
			}
		}

		logger.log("Matching with oneat spots.\n");
		logger.setProgress(0.);
		count = 0;

		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			int frame = framemap.getKey();
			if (frame < intimg.dimension(ndim) - 1) {
				count++;

				ArrayList<Spot> spotlist = framemap.getValue();

				for (Spot currentspots : spotlist) {

					logger.setProgress((float) (count) / framespots.size());

					long[] location = new long[ndim];
					long[] timelocation = new long[ndim + 1];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) currentspots.getDoublePosition(d);
						timelocation[d] = location[d];
					}
					timelocation[ndim] = frame;
					ranac.setPosition(timelocation);
					// Get the label ID of the current interesting spot
					int labelID = ranac.get().get();

					String uniqueID = Integer.toString(labelID) + Integer.toString(frame);
					if (uniquelabelID.containsKey(uniqueID)) {
						Pair<Spot, Integer> spotandtrackID = uniquelabelID.get(uniqueID);
						// Now get the spot ID

						Spot spot = spotandtrackID.getA();

						int trackID = spotandtrackID.getB();
						Pair<Boolean, Pair<Spot, Spot>> isDividingTMspot = isDividingTrack(spot, trackID, tmoneatdeltat,
								model);
						Boolean isDividing = isDividingTMspot.getA();
						// If isDividing is true oneat does not need to correct the track else it has to
						// correct the trackid
						if (checkdivision & isDividing == false) {

							Spot startspot = isDividingTMspot.getB().getA();

							if (Mitosisspots.containsKey(trackID)) {

								ArrayList<Spot> trackspotlist = Mitosisspots.get(trackID);
								if (!trackspotlist.contains(spot))
									trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(
										trackspotlist, startspot);
								TrackIDstartspots.put(trackID, pairlist);
							} else {

								ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
								trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(
										trackspotlist, startspot);
								TrackIDstartspots.put(trackID, pairlist);
							}

						}
						// If it is an apoptosis event we currently do not have any function to check if
						// TM trajectory has it so we add oneat given trackid
						if (checkdivision == false) {

							Spot startspot = isDividingTMspot.getB().getA();
							if (Mitosisspots.containsKey(trackID)) {

								ArrayList<Spot> trackspotlist = Mitosisspots.get(trackID);
								if (!trackspotlist.contains(spot))
									trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(
										trackspotlist, startspot);
								TrackIDstartspots.put(trackID, pairlist);
							} else {

								ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
								trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(
										trackspotlist, startspot);
								TrackIDstartspots.put(trackID, pairlist);
							}

						}

					}
				}

			}
		}

		logger.log("Verifying lineage trees.\n");
		logger.setProgress(0.);

		return TrackIDstartspots;
	}

	private static Pair<Boolean, Pair<Spot, Spot>> isDividingTrack(final Spot spot, final int trackID, final int N,
			final Model model) {

		Boolean isDividing = false;
		Pair<HashMap<Integer, ArrayList<Pair<Integer, Spot>>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots = getTMDividing(
				model);
		Spot closestSpot = null;
		Spot startingspot = null;
		final Set<DefaultWeightedEdge> track = model.getTrackModel().trackEdges(trackID);

		for (final DefaultWeightedEdge e : track) {

			Spot Spotbase = model.getTrackModel().getEdgeSource(e);
			int id = model.getTrackModel().trackIDOf(Spotbase);

			if (id == trackID) {

				ArrayList<Pair<Integer, Spot>> Dividingspotlocations = DividingStartspots.getB().get(id);
				ArrayList<Pair<Integer, Spot>> Startingspotlocations = DividingStartspots.getA().get(id);
				startingspot = Startingspotlocations.get(0).getB();
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

	private static Pair<Double, Spot> closestnextframeSpot(final Spot currentspot, final Iterable<Spot> nextspot) {

		double mintimeDistance = Double.MAX_VALUE;
		Spot closestsourcespot = null;

		for (Spot Dividingspot : nextspot) {

			final Spot sourcespot = Dividingspot;

			final double dist = currentspot.squareDistanceTo(Dividingspot);

			if (dist <= mintimeDistance) {

				mintimeDistance = dist;
				closestsourcespot = sourcespot;
			}

		}

		Pair<Double, Spot> closestspotpair = new ValuePair<Double, Spot>(Math.abs(mintimeDistance), closestsourcespot);

		return closestspotpair;

	}

	private static Pair<HashMap<Integer, ArrayList<Pair<Integer, Spot>>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> getTMDividing(
			final Model model) {

		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Dividingspots = new HashMap<Integer, ArrayList<Pair<Integer, Spot>>>();
		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Startingspots = new HashMap<Integer, ArrayList<Pair<Integer, Spot>>>();
		for (final Integer trackID : model.getTrackModel().trackIDs(false)) {

			final Set<DefaultWeightedEdge> track = model.getTrackModel().trackEdges(trackID);

			ArrayList<Pair<Integer, Spot>> Sources = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Targets = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Integer> SourcesID = new ArrayList<Integer>();
			ArrayList<Integer> TargetsID = new ArrayList<Integer>();

			ArrayList<Pair<Integer, Spot>> Starts = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Ends = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Splits = new ArrayList<Pair<Integer, Spot>>();

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

			Startingspots.put(trackID, Starts);
			Dividingspots.put(trackID, Splits);

		}

		return new ValuePair<HashMap<Integer, ArrayList<Pair<Integer, Spot>>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>>(
				Startingspots, Dividingspots);

	}

	public static Pair<Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>> run(
			final File oneatdivisionfile, final File oneatapoptosisfile, final int ndims) {

		SpotCollection divisionspots = new SpotCollection();
		HashMap<Integer, ArrayList<Spot>> DivisionSpotListFrame = new HashMap<Integer, ArrayList<Spot>>();

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

						int time = (int) Double.parseDouble(divisionspotsfile[0]);
						double Z = Double.parseDouble(divisionspotsfile[1]);
						double Y = Double.parseDouble(divisionspotsfile[2]);
						double X = Double.parseDouble(divisionspotsfile[3]);
						double score = Double.parseDouble(divisionspotsfile[4]);
						double size = Double.parseDouble(divisionspotsfile[5]);
						double confidence = Double.parseDouble(divisionspotsfile[6]);
						double angle = Double.parseDouble(divisionspotsfile[7]);

						Oneatobject Spot = new Oneatobject(time, Z, Y, X, score, size, confidence, angle);

						if (DivisionMap.get(time) == null) {
							DivisionSpots = new ArrayList<Oneatobject>();
							DivisionMap.put(time, DivisionSpots);
						} else
							DivisionMap.put(time, DivisionSpots);
						DivisionSpots.add(Spot);
					}
					count = count + 1;
				}
			}

			catch (IOException ie) {
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

					double volume = cell.size;
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
					currentspot.putFeature(QUALITY, Double.valueOf(radius));

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

						int time = Integer.parseInt(apoptosisspotsfile[0]);
						double Z = Double.parseDouble(apoptosisspotsfile[1]);
						double Y = Double.parseDouble(apoptosisspotsfile[2]);
						double X = Double.parseDouble(apoptosisspotsfile[3]);
						double score = Double.parseDouble(apoptosisspotsfile[4]);
						double size = Double.parseDouble(apoptosisspotsfile[5]);
						double confidence = Double.parseDouble(apoptosisspotsfile[6]);
						double angle = Double.parseDouble(apoptosisspotsfile[7]);

						Oneatobject Spot = new Oneatobject(time, Z, Y, X, score, size, confidence, angle);

						if (ApoptosisMap.get(time) == null) {
							ApoptosisSpots = new ArrayList<Oneatobject>();
							ApoptosisMap.put(time, ApoptosisSpots);
						} else
							ApoptosisMap.put(time, ApoptosisSpots);
						ApoptosisSpots.add(Spot);
					}
					count = count + 1;
				}
			}

			catch (IOException ie) {
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

					double volume = cell.size;
					double quality = cell.size;

					final double radius = (ndims == 2) ? Math.sqrt(volume / Math.PI)
							: Math.pow(3. * volume / (4. * Math.PI), 1. / 3.);

					Spot currentspot = new Spot(x, y, z, radius, quality);
					currentspot.putFeature(POSITION_X, Double.valueOf(x));
					currentspot.putFeature(POSITION_Y, Double.valueOf(y));
					currentspot.putFeature(POSITION_Z, Double.valueOf(z));
					currentspot.putFeature(FRAME, Double.valueOf(frame));
					currentspot.putFeature(RADIUS, Double.valueOf(radius));
					currentspot.putFeature(QUALITY, Double.valueOf(radius));
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
