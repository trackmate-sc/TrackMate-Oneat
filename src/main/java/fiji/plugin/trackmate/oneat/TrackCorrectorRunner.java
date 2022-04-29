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

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.util.TMUtils;
import net.imglib2.util.Util;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import static fiji.plugin.trackmate.Spot.POSITION_X;
import static fiji.plugin.trackmate.Spot.POSITION_Y;
import static fiji.plugin.trackmate.Spot.POSITION_Z;
import static fiji.plugin.trackmate.Spot.FRAME;
import static fiji.plugin.trackmate.Spot.RADIUS;
import static fiji.plugin.trackmate.Spot.QUALITY;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_SIZE_RATIO;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_LINKING_MAX_DISTANCE;

public class TrackCorrectorRunner {

	private final static Context context = TMUtils.getContext();

	
	
	
	public static SimpleWeightedGraph<Spot, DefaultWeightedEdge> getCorrectedTracks(final Model model, HashMap<Integer, Pair<ArrayList<Spot>, Spot>> Mitosisspots,
			HashMap<Integer, Pair<ArrayList<Spot>, Spot>> Apoptosisspots, Map<String, Object> settings, final int ndim) {

		
		//Get the trackmodel and spots in the default tracking result and start to create a new graph
        TrackModel trackmodel = model.getTrackModel();
		SpotCollection allspots = model.getSpots();
		SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
		
		
		double motherdaughtersize = (double) settings.get(KEY_SIZE_RATIO);
		double searchdistance = (double) settings.get(KEY_LINKING_MAX_DISTANCE);
		Set<Integer> AlltrackIDs = trackmodel.trackIDs(false);
		Set<Integer> MitosisIDs = new HashSet<Integer>();
		Set<Integer> ApoptosisIDs = new HashSet<Integer>();
		
		
		//Lets take care of apoptosis
		for (Map.Entry<Integer, Pair<ArrayList<Spot>, Spot>> trackidspots : Apoptosisspots.entrySet()) {
			
			// Get the current trackID
			int trackID = trackidspots.getKey();
			Pair<ArrayList<Spot>, Spot> trackspots = trackidspots.getValue();
			ApoptosisIDs.add(trackID);
			
			// Apoptosis cell can not be source of an edge
			for (Spot killerspot : trackspots.getA()) {
				
				Set<DefaultWeightedEdge> killertrack = trackmodel.trackEdges(trackID);
				for ( final DefaultWeightedEdge edge : killertrack )
				{
					final Spot source = graph.getEdgeSource( edge );
					if(source!=killerspot) {
					final Spot target = graph.getEdgeTarget( edge );
					final DefaultWeightedEdge newedge = graph.addEdge(source, target);
					graph.setEdgeWeight(newedge, -1);
					}
				}
				
			}
			
			
		}
		// Lets take care of mitosis
		for (Map.Entry<Integer, Pair<ArrayList<Spot>, Spot>> trackidspots : Mitosisspots.entrySet()) {

			// Get the current trackID
			int trackID = trackidspots.getKey();
			Pair<ArrayList<Spot>, Spot> trackspots = trackidspots.getValue();
			
			
			Boolean acceptFirstdaughter = false;
			Boolean acceptSeconddaughter = false;
			for (Spot motherspot : trackspots.getA()) {

				// Get the location of spot in current frame
				int currentframe = motherspot.getFeature(FRAME).intValue();
				double mothersize = motherspot.getFeature(QUALITY);
				long[] location = new long[ndim];
				for (int d = 0; d < ndim; ++d)
					location[d] = (long) motherspot.getDoublePosition(d);

				// Get spots in the next frame
				Iterable<Spot> spotsIt = allspots.iterable(currentframe + 1, false);

				Spot firstdaughter = null;
				Spot seconddaughter = null;

				do {

					// Get the closest trackmate spot in the next frame

					Pair<Double, Spot> firstclosestspot = closestnextframeSpot(motherspot, spotsIt);
					double firstclosestdistance = firstclosestspot.getA();

					double firstdaughtersize = firstclosestspot.getB().getFeature(QUALITY);

					if (mothersize / firstdaughtersize <= motherdaughtersize && acceptFirstdaughter == false
							&& firstclosestdistance <= searchdistance) {

						acceptFirstdaughter = true;
						firstdaughter = firstclosestspot.getB();

					}

					// Now remove that spot from the iterable
					spotsIt = removespot(spotsIt, firstclosestspot.getB());
					// Get the second closest trackmate spot in the next frame
					Pair<Double, Spot> secondclosestspot = closestnextframeSpot(motherspot, spotsIt);
					double secondclosestdistance = secondclosestspot.getA();
					double seconddaughtersize = secondclosestspot.getB().getFeature(QUALITY);

					if (mothersize / seconddaughtersize <= motherdaughtersize && acceptSeconddaughter == false
							&& secondclosestdistance <= searchdistance) {

						acceptSeconddaughter = true;
						seconddaughter = secondclosestspot.getB();

					}

				} while (!acceptFirstdaughter && !acceptSeconddaughter || spotsIt.iterator().hasNext());

				if (acceptFirstdaughter && acceptSeconddaughter) {

					// We have to get rid of this trackID and the trackID of the daughters
					MitosisIDs.add(trackID);
					trackmodel.trackSpots(trackID).clear();
					int firstdaughtertrackID = trackmodel.trackIDOf(firstdaughter);
					int seconddaughtertrackID = trackmodel.trackIDOf(seconddaughter);
					
					
					
					// If we are in here we have found the closest two spots to create links to, now
					// we get their track ID

					// Get the backward track of the mother from { #startingspot}

					Set<DefaultWeightedEdge> startmothertrack = trackmodel.trackEdges(trackID);
					for ( final DefaultWeightedEdge edge : startmothertrack )
					{
						final Spot source = graph.getEdgeSource( edge );
						if(source!=motherspot) {
						final Spot target = graph.getEdgeTarget( edge );
						final DefaultWeightedEdge newedge = graph.addEdge(source, target);
						graph.setEdgeWeight(newedge, -1);
						}
					}
					
					// Get the forward track of the first daughter
					Set<DefaultWeightedEdge> firstdaughtertrack = trackmodel.trackEdges(firstdaughtertrackID);

					// Get the forward track of the second daughter
					Set<DefaultWeightedEdge> seconddaughtertrack = trackmodel.trackEdges(seconddaughtertrackID);
             
					// Mitosis daughters have to break previous connections so can not be target of a spot
					for ( final DefaultWeightedEdge edge : firstdaughtertrack )
					{
						final Spot source = graph.getEdgeSource( edge );
						
						final Spot target = graph.getEdgeTarget( edge );
						if(target!=firstdaughter) {
						final DefaultWeightedEdge newedge = graph.addEdge(source, target);
						graph.setEdgeWeight(newedge, -1);
						}
					}
					
					
					for ( final DefaultWeightedEdge edge : seconddaughtertrack )
					{
						final Spot source = graph.getEdgeSource( edge );
						
						final Spot target = graph.getEdgeTarget( edge );
						if(target!=seconddaughter) {
						final DefaultWeightedEdge newedge = graph.addEdge(source, target);
						graph.setEdgeWeight(newedge, -1);
						}
					}
					
					
					trackmodel.trackSpots(firstdaughtertrackID).clear();
					trackmodel.trackSpots(seconddaughtertrackID).clear();
					
					// Establish the connection between the split spot and daughters
					final DefaultWeightedEdge firstdaughteredge = graph.addEdge(motherspot, firstdaughter);
					graph.setEdgeWeight(firstdaughteredge, -1);
					
					final DefaultWeightedEdge seconddaughteredge = graph.addEdge(motherspot, seconddaughter);
					graph.setEdgeWeight(seconddaughteredge, -1);
					
					

				}

			}
		}
		
		//Lets take care of no event tracks
		AlltrackIDs.removeAll(ApoptosisIDs);
		AlltrackIDs.removeAll(MitosisIDs);
		
		for (int trackID : AlltrackIDs) {
			
				// Nothing special here just mantaining the normal links found
				Set<DefaultWeightedEdge> normaltracks = trackmodel.trackEdges(trackID);
				for ( final DefaultWeightedEdge edge : normaltracks )
				{
					final Spot source = graph.getEdgeSource( edge );
					final Spot target = graph.getEdgeTarget( edge );
					final DefaultWeightedEdge newedge = graph.addEdge(source, target);
					graph.setEdgeWeight(newedge, -1);
					
				}
				
			
			
			
		}
		
		
		
		return graph;

	}

	private static Iterable<Spot> removespot(Iterable<Spot> spotsIt, Spot removespot) {

		Iterator<Spot> spots = spotsIt.iterator();
		Set<Spot> removespots = new HashSet<Spot>();
		while (spots.hasNext()) {

			Spot currentspot = spots.next();

			if (currentspot.equals(removespot))
				spots.remove();
			removespots.add(currentspot);
		}

		return removespots;

	}
	
	public static HashMap<Integer, Pair<ArrayList<Spot>, Spot>> getTrackID(final Model model,
			final ImgPlus<IntType> img, HashMap<Integer, ArrayList<Spot>> framespots, final boolean checkdivision,
			final int timegap) {

		HashMap<Integer, ArrayList<Spot>> Mitosisspots = new HashMap<Integer, ArrayList<Spot>>();
		HashMap<Integer, Pair<ArrayList<Spot>, Spot>> TrackIDstartspots = new HashMap<Integer, Pair<ArrayList<Spot>, Spot>>();
		// Spots from trackmate
		SpotCollection allspots = model.getSpots();

		int ndim = img.numDimensions();
		RandomAccess<IntType> ranac = img.randomAccess();
		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			int frame = framemap.getKey();
			ArrayList<Spot> spotlist = framemap.getValue();

			for (Spot currentspots : spotlist) {

				long[] location = new long[ndim];
				for (int d = 0; d < ndim; ++d)
					location[d] = (long) currentspots.getDoublePosition(d);

				ranac.setPosition(frame, img.dimensionIndex(Axes.TIME));
				ranac.setPosition(location);
				// Get the label ID of the current interesting spot
				int labelID = ranac.get().get();

				// Now get the spot ID

				final Iterable<Spot> spotsIt = allspots.iterable(frame, false);
				for (final Spot spot : spotsIt) {

					// Now we have all the spots in this frame that are a part of the track

					long[] currentlocation = new long[ndim];
					for (int d = 0; d < ndim; ++d)
						currentlocation[d] = (long) spot.getDoublePosition(d);

					ranac.setPosition(currentlocation);

					int spotlabelID = ranac.get().get();

					if (spotlabelID == labelID) {

						int trackID = model.getTrackModel().trackIDOf(spot);
						Pair<Boolean, Pair<Spot, Spot>> isDividingTMspot = isDividingTrack(spot, trackID, timegap,
								model);
						Boolean isDividing = isDividingTMspot.getA();
						// If isDividing is true oneat does not need to correct the track else it has to
						// correct the trackid
						if (checkdivision & isDividing == false) {

							Spot startspot = isDividingTMspot.getB().getA();

							if (Mitosisspots.containsKey(trackID)) {

								ArrayList<Spot> trackspotlist = Mitosisspots.get(trackID);
								trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(trackspotlist,startspot);
								TrackIDstartspots.put(trackID, pairlist);
							} else {

								ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
								trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(trackspotlist,startspot);
								TrackIDstartspots.put(trackID, pairlist);
							}

						}
						// If it is an apoptosis event we currently do not have any function to check if
						// TM trajectory has it so we add oneat given trackid
						if (checkdivision == false) {

							Spot startspot = isDividingTMspot.getB().getA();
							if (Mitosisspots.containsKey(trackID)) {

								ArrayList<Spot> trackspotlist = Mitosisspots.get(trackID);
								trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(trackspotlist,startspot);
								TrackIDstartspots.put(trackID, pairlist);
							} else {

								ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
								trackspotlist.add(spot);
								Mitosisspots.put(trackID, trackspotlist);
								Pair<ArrayList<Spot>, Spot> pairlist = new ValuePair<ArrayList<Spot>, Spot>(trackspotlist,startspot);
								TrackIDstartspots.put(trackID, pairlist);
							}

						}
						
						

					}
				}

			}

		}

		
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

						int time = Integer.parseInt(divisionspotsfile[0]);
						double Z = Double.parseDouble(divisionspotsfile[1]);
						double Y = Double.parseDouble(divisionspotsfile[2]);
						double X = Double.parseDouble(divisionspotsfile[3]);
						double score = Double.parseDouble(divisionspotsfile[4]);
						double size = Double.parseDouble(divisionspotsfile[5]);
						double confidence = Double.parseDouble(divisionspotsfile[6]);
						double angle = Double.parseDouble(divisionspotsfile[7]);

						RealPoint point = new RealPoint(X, Y, Z);
						Oneatobject Spot = new Oneatobject(time, point, score, size, confidence, angle);

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
					final double x = (cell.Location.getDoublePosition(0));
					final double y = (cell.Location.getDoublePosition(1));
					final double z = (cell.Location.getDoublePosition(2));

					double volume = cell.size;
					double quality = cell.size;

					final double radius = (ndims == 2) ? Math.sqrt(volume / Math.PI)
							: Math.pow(3. * volume / (4. * Math.PI), 1. / 3.);

					Spot currentspot = new Spot(x, y, z, radius, quality);
					// Put spot features so we can get it back by feature name
					currentspot.putFeature(POSITION_X, Double.valueOf(x));
					currentspot.putFeature(POSITION_Y, Double.valueOf(y));
					currentspot.putFeature(POSITION_Z, Double.valueOf(x));
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

						RealPoint point = new RealPoint(X, Y, Z);
						Oneatobject Spot = new Oneatobject(time, point, score, size, confidence, angle);

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
					final double x = (cell.Location.getDoublePosition(0));
					final double y = (cell.Location.getDoublePosition(1));
					final double z = (cell.Location.getDoublePosition(2));

					double volume = cell.size;
					double quality = cell.size;

					final double radius = (ndims == 2) ? Math.sqrt(volume / Math.PI)
							: Math.pow(3. * volume / (4. * Math.PI), 1. / 3.);

					Spot currentspot = new Spot(x, y, z, radius, quality);
					currentspot.putFeature(POSITION_X, Double.valueOf(x));
					currentspot.putFeature(POSITION_Y, Double.valueOf(y));
					currentspot.putFeature(POSITION_Z, Double.valueOf(x));
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
