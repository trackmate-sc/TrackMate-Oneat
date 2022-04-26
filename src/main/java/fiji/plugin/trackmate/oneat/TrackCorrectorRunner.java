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
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.options.OptionsService;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import static fiji.plugin.trackmate.Spot.FRAME;
import fiji.plugin.trackmate.SpotCollection;
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

public class TrackCorrectorRunner {

	private final static Context context = TMUtils.getContext();

	public static HashMap<Integer, ArrayList<Spot>> getTrackID(final Model model, final ImgPlus<IntType> img,
			HashMap<Integer, ArrayList<Spot>> framespots, final boolean checkdivision, final int timegap) {

		HashMap<Integer, ArrayList<Spot>> TrackIDspots = new HashMap<Integer, ArrayList<Spot>>();
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
						Pair<Boolean, Spot> isDividingTMspot = isDividingTrack(spot, trackID, timegap, model);
						Boolean isDividing = isDividingTMspot.getA();
						// If isDividing is true oneat does not need to correct the track else it has to
						// correct the trackid
						if (checkdivision & isDividing == false) {

							if (TrackIDspots.containsKey(trackID)) {

								ArrayList<Spot> trackspotlist = TrackIDspots.get(trackID);
								trackspotlist.add(spot);
								TrackIDspots.put(trackID, trackspotlist);
							} else {

								ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
								trackspotlist.add(spot);
								TrackIDspots.put(trackID, trackspotlist);

							}

						}
						// If it is an apoptosis event we currently do not have any function to check if
						// TM trajectory has it so we add oneat given trackid
						if (checkdivision == false) {

							if (TrackIDspots.containsKey(trackID)) {

								ArrayList<Spot> trackspotlist = TrackIDspots.get(trackID);
								trackspotlist.add(spot);
								TrackIDspots.put(trackID, trackspotlist);
							} else {

								ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
								trackspotlist.add(spot);
								TrackIDspots.put(trackID, trackspotlist);

							}

						}

					}
				}

			}

		}

		return TrackIDspots;
	}

	private static Pair<Boolean, Spot> isDividingTrack(final Spot spot, final int trackID, final int N,
			final Model model) {

		Boolean isDividing = false;
		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Dividingspots = getTMDividing(model);
		Spot closestSpot = null;
		final Set<DefaultWeightedEdge> track = model.getTrackModel().trackEdges(trackID);

		for (final DefaultWeightedEdge e : track) {

			Spot Spotbase = model.getTrackModel().getEdgeSource(e);
			int id = model.getTrackModel().trackIDOf(Spotbase);

			if (id == trackID) {

				ArrayList<Pair<Integer, Spot>> Dividingspotlocations = Dividingspots.get(id);
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

		return new ValuePair<Boolean, Spot>(isDividing, closestSpot);
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

	private static HashMap<Integer, ArrayList<Pair<Integer, Spot>>> getTMDividing(final Model model) {

		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Dividingspots = new HashMap<Integer, ArrayList<Pair<Integer, Spot>>>();

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

			Dividingspots.put(trackID, Splits);

		}

		return Dividingspots;

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
