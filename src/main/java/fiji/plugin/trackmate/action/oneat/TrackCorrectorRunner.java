/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2022 - 2023 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.action.oneat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.graph.SortedDepthFirstIterator;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_BREAK_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_CREATE_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_USE_MARI_PRINCIPLE;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_PROB_THRESHOLD;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_MARI_ANGLE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_SPLITTING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_GAP_CLOSING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_MERGING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_CUTOFF_PERCENTILE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALTERNATIVE_LINKING_COST_FACTOR;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_MERGING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.DEFAULT_SPLITTING_FEATURE_PENALTIES;



public class TrackCorrectorRunner {

	
	private static SimpleWeightedGraph<Spot, DefaultWeightedEdge> removeTracklets(final Model model,
			final SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph, final Map<String, Object> settings) {
		int timecutoff = 1;
		TrackModel trackModel = model.getTrackModel();

		for (final Integer trackID : trackModel.trackIDs(true)) {

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
	
	public static List<Future<Graphobject>> LinkCreator(final Model model, final TrackMate trackmate,
			HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID,
			Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> DividingStartspots,
			HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Mitosisspots, Map<String, Object> settings, final int ndim,
			final Logger logger, final ImgPlus<UnsignedShortType> img, HashMap<Integer, ArrayList<Spot>> framespots,
			int numThreads, double[] calibration, boolean addDisplay) {

		// Get the trackmodel and spots in the default tracking result and start to
		// create a new graph
		TrackModel trackmodel = model.getTrackModel();
		SpotCollection allspots = model.getSpots();

		final ExecutorService executorS = Executors.newWorkStealingPool();

		final ArrayList<Integer> trackcountlist = new ArrayList<Integer>();
		double searchdistance = (double) (settings.get(KEY_LINKING_MAX_DISTANCE) != null
				? (double) settings.get(KEY_LINKING_MAX_DISTANCE)
				: 10);
		int tmoneatdeltat = (int) settings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);

		boolean mariprinciple = (boolean) settings.get(KEY_USE_MARI_PRINCIPLE);
		double mariangle = (double) settings.get(KEY_MARI_ANGLE);

		Map<String, Object> cmsettings = new HashMap<>();
		// Gap closing.

		int maxFrameInterval = tmoneatdeltat;
		if (settings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP) != null)
			maxFrameInterval = (Integer) settings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		double gcMaxDistance = searchdistance;

		if (settings.get(KEY_GAP_CLOSING_MAX_DISTANCE) != null)

			gcMaxDistance = (double) settings.get(KEY_GAP_CLOSING_MAX_DISTANCE);
		boolean allowGapClosing = false;
		if (settings.get(KEY_ALLOW_GAP_CLOSING) != null) {
			allowGapClosing = (Boolean) settings.get(KEY_ALLOW_GAP_CLOSING);
		}
		boolean allowTrackMerging = false;
		if (settings.get(KEY_ALLOW_TRACK_MERGING) != null)
			allowTrackMerging = (Boolean) settings.get(KEY_ALLOW_TRACK_MERGING);
		boolean allowTrackSplitting = true;
		if (settings.get(KEY_ALLOW_TRACK_SPLITTING) != null)
			allowTrackSplitting = (Boolean) settings.get(KEY_ALLOW_TRACK_SPLITTING);
		// Merging
		double mMaxDistance = Double.MAX_VALUE;
		double sMaxDistance = Double.MAX_VALUE;
		boolean allowMerging = false;
		if (settings.get(KEY_ALLOW_TRACK_MERGING) != null)
			allowMerging = (Boolean) settings.get(KEY_ALLOW_TRACK_MERGING);
		if (allowTrackMerging)
			mMaxDistance = (Double) settings.get(KEY_MERGING_MAX_DISTANCE);
		else
			mMaxDistance = searchdistance;

		if (allowTrackSplitting)
			// Splitting
			sMaxDistance = (Double) settings.get(KEY_SPLITTING_MAX_DISTANCE);
		else
			sMaxDistance = searchdistance;
		// Alternative cost
		double alternativeCostFactor = 1.05d;
		if (settings.get(KEY_ALTERNATIVE_LINKING_COST_FACTOR) != null)
			alternativeCostFactor = (Double) settings.get(KEY_ALTERNATIVE_LINKING_COST_FACTOR);
		double percentile = 0.9d;
		if (settings.get(KEY_CUTOFF_PERCENTILE) != null)
			percentile = (Double) settings.get(KEY_CUTOFF_PERCENTILE);

		cmsettings.put(KEY_ALLOW_TRACK_SPLITTING, true);
		cmsettings.put(KEY_SPLITTING_MAX_DISTANCE, sMaxDistance);
		cmsettings.put(KEY_GAP_CLOSING_MAX_FRAME_GAP, maxFrameInterval);
		cmsettings.put(KEY_ALLOW_GAP_CLOSING, allowGapClosing);
		cmsettings.put(KEY_ALLOW_TRACK_MERGING, allowMerging);
		cmsettings.put(KEY_CUTOFF_PERCENTILE, percentile);
		cmsettings.put(KEY_ALTERNATIVE_LINKING_COST_FACTOR, alternativeCostFactor);
		cmsettings.put(KEY_GAP_CLOSING_MAX_DISTANCE, gcMaxDistance);
		cmsettings.put(KEY_MERGING_MAX_DISTANCE, mMaxDistance);

		if (settings.get(KEY_SPLITTING_FEATURE_PENALTIES) != DEFAULT_SPLITTING_FEATURE_PENALTIES)
			cmsettings.put(KEY_SPLITTING_FEATURE_PENALTIES, settings.get(KEY_SPLITTING_FEATURE_PENALTIES));
		else
			cmsettings.put(KEY_SPLITTING_FEATURE_PENALTIES, settings.get(KEY_LINKING_FEATURE_PENALTIES));
		List<Future<Graphobject>> grapherlist = new ArrayList<>();
		// Lets take care of mitosis
		if (Mitosisspots != null) {
			logger.log("Matched Oneat Locations with Spots in Tracks " + " " + Mitosisspots.entrySet().size() + "\n");
			if (mariprinciple)
				logger.log("Using Mari's priniciple for track linking.\n");
			logger.setStatus("Local Jaqaman Linker");
			logger.setProgress(0.);

			for (Map.Entry<Integer, Pair<Spot, ArrayList<Spot>>> trackidspots : Mitosisspots.entrySet()) {

				ArrayList<Pair<Spot, Spot>> removeedges = new ArrayList<>();
				ArrayList<Pair<Spot, Spot>> addedges = new ArrayList<>();
				ArrayList<Double> costlist = new ArrayList<>();

				Future<Graphobject> result = executorS.submit(new Callable<Graphobject>() {
					@Override
					public Graphobject call() throws Exception {
						int trackcount = 0;
						trackcountlist.add(trackcount);
						// List of all the mother cells and the root of the lineage tree
						Pair<Spot, ArrayList<Spot>> trackspots = trackidspots.getValue();

						ArrayList<Spot> mitosismotherspots = trackspots.getB();

						// Create a new map of spot and label

						// Create the pixel list for mother cells

						Ellipsoid ellipsoid = null;
						double[] motherslope = new double[2];
						double[] largemotherslope = new double[2];
						Pair<double[], double[]> slope = new ValuePair<double[], double[]>(motherslope,
								largemotherslope);

						for (Spot motherspot : mitosismotherspots) {

							Set<DefaultWeightedEdge> mothertrack = trackmodel.edgesOf(motherspot);

							ellipsoid = getEllipsoid(motherspot, img, calibration);

							if (ellipsoid != null) {
								slope = getEigen(ellipsoid, ndim);
								motherslope = slope.getA();
								largemotherslope = slope.getB();
							}

							final SimpleWeightedGraph<Spot, DefaultWeightedEdge> localgraph = new SimpleWeightedGraph<>(
									DefaultWeightedEdge.class);

							for (DefaultWeightedEdge localedge : mothertrack) {

								final Spot source = trackmodel.getEdgeSource(localedge);

								final Spot target = trackmodel.getEdgeTarget(localedge);

								if (target.ID() == motherspot.ID() || source.ID() == motherspot.ID()) {
									final double linkcost = trackmodel.getEdgeWeight(localedge);
									localgraph.addVertex(source);
									localgraph.addVertex(target);
									localgraph.addEdge(source, target);
									localgraph.setEdgeWeight(localedge, linkcost);

								}

							}

							for (int i = 1; i < tmoneatdeltat; ++i) {

								double frame = motherspot.getFeature(Spot.FRAME) + i;
								if (frame > 0) {

									SpotCollection regionspots = regionspot(img, allspots, motherspot, logger,
											calibration, (int) frame, searchdistance, motherslope, mariangle,
											mariprinciple);

									if (regionspots.getNSpots((int) frame, false) > 0)
										for (Spot spot : regionspots.iterable((int) frame, false)) {

											if (trackmodel.trackIDOf(spot) != null) {
												int regiontrackID = trackmodel.trackIDOf(spot);
												Set<DefaultWeightedEdge> localtracks = trackmodel
														.trackEdges(regiontrackID);

												for (DefaultWeightedEdge localedge : localtracks) {

													final Spot source = trackmodel.getEdgeSource(localedge);

													if (source.getFeature(Spot.FRAME) == frame) {
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

							final OneatCostMatrix costMatrixCreator = new OneatCostMatrix(localgraph, cmsettings);
							costMatrixCreator.setNumThreads(numThreads);
							logger.setProgress((double) trackcountlist.size() / Mitosisspots.entrySet().size());
							final LocalJaqamanLinker<Spot, Spot> linker = new LocalJaqamanLinker<>(costMatrixCreator,
									logger);
							if (!linker.checkInput() || !linker.process()) {
								System.out.println(linker.getErrorMessage());
							}

						

							final Map<Spot, Spot> assignment = linker.getResult();
							final Map<Spot, Double> costs = linker.getAssignmentCosts();
							
							
							// Recreate new links
							if (assignment != null) {

								for (final Spot source : assignment.keySet()) {

									final Spot target = assignment.get(source);

									Set<DefaultWeightedEdge> targetlinks = trackmodel.edgesOf(target);

									boolean validlink = true;
									if (mariprinciple)
										validlink = false;
									final double cost = costs.get(source);
									
									Set<DefaultWeightedEdge> drawlinkslinks = trackmodel.edgesOf(source);
									OneatOverlay oneatOverlayFirst = new OneatOverlay(motherspot, source, target,
											motherslope, largemotherslope, trackmate.getSettings().imp);
									double motheraxis = largemotherslope[1] / largemotherslope[0];

									double intercept = motherspot.getDoublePosition(1)
											- motheraxis * motherspot.getDoublePosition(0);

									double daughtermotheraxis = (target.getDoublePosition(1)
											- motheraxis * target.getDoublePosition(0) - intercept);

									for (DefaultWeightedEdge targetedge : drawlinkslinks) {
										Spot targetsource = trackmodel.getEdgeTarget(targetedge);
										OneatOverlay oneatOverlay = new OneatOverlay(motherspot, source, targetsource,
												motherslope, largemotherslope, trackmate.getSettings().imp);

										if (source.getDoublePosition(0) != targetsource.getDoublePosition(0)) {
											double daughtermotheraxisB = (targetsource.getDoublePosition(1)
													- motheraxis * targetsource.getDoublePosition(0) - intercept);

											if (mariprinciple)
												if (Math.signum(daughtermotheraxisB)
														* Math.signum(daughtermotheraxis) < 0) {
													validlink = true;
													if (addDisplay) {
														addOverlay(oneatOverlayFirst, trackmate.getSettings().imp,
																motherspot);
														addOverlay(oneatOverlay, trackmate.getSettings().imp,
																motherspot);
													}
												}
											if (!mariprinciple) {
												if (addDisplay) {
													addOverlay(oneatOverlayFirst, trackmate.getSettings().imp,
															motherspot);
													addOverlay(oneatOverlay, trackmate.getSettings().imp, motherspot);
												}
											}

										}
									}
									
									if (validlink && cost < searchdistance * searchdistance /2) {
										// Remove the targetsource and target edge prior to assingment
										for (DefaultWeightedEdge targetedge : targetlinks) {

											Spot targetsource = trackmodel.getEdgeSource(targetedge);
											removeedges.add(new ValuePair<Spot, Spot>(targetsource, target));
										}

										for (DefaultWeightedEdge targetedge : drawlinkslinks) {

											Spot targetsource = trackmodel.getEdgeSource(targetedge);
											removeedges.add(new ValuePair<Spot, Spot>(targetsource, target));
										}

										addedges.add(new ValuePair<Spot, Spot>(source, target));
										costlist.add(cost);
										
									}

								}

							}

						}

						Graphobject grapher = new Graphobject(removeedges, addedges, costlist);

						return grapher;
					}

				});

				grapherlist.add(result);
			}

		}

		return grapherlist;

	}

	/**
	 * 
	 * @param model              The TrackMate model
	 * @param trackmate          The TrackMate object
	 * @param uniquelabelID      HashMap of (label, frame) with value being
	 *                           TrackMate Spot from collection and its TrackID
	 * @param DividingStartspots A pair of HashMap of Track ID with starting Spot
	 *                           and a list of dividing spots for this ID
	 * @param Mitosisspots       A HashMap of TrackID, starting spot and list of
	 *                           dividing spots
	 * @param Apoptosisspots     A HashMap of TrackID, starting spot and the
	 *                           apoptotic spot
	 * @param settings           A HashMap of String and Object
	 * @param ndim               Image dimensions
	 * @param logger             TrackMate logger
	 * @param img                The ImgPlus of the integer label image
	 * @param framespots         HashMap of frame and Oneat found Spot
	 * @param numThreads         The number of threads used for the linking
	 *                           algorithm
	 * @param calibration        The image calibration
	 * @param addDisplay         A boolean to add Oneat display, set no if saving
	 *                           memory is of concern
	 * @return Returns corrected graph that is then set on the model
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */

	public static SimpleWeightedGraph<Spot, DefaultWeightedEdge> getCorrectedTracks(final Model model,
			final TrackMate trackmate, HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID,
			Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> DividingStartspots,
			HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Mitosisspots,
			HashMap<Integer, Pair<Spot, Spot>> Apoptosisspots, Map<String, Object> settings, final int ndim,
			final Logger logger, final ImgPlus<UnsignedShortType> img, HashMap<Integer, ArrayList<Spot>> framespots,
			int numThreads, double[] calibration, boolean addDisplay) throws InterruptedException, ExecutionException {

		// Get the trackmodel and spots in the default tracking result and start to
		// create a new graph
		TrackModel trackmodel = model.getTrackModel();

		SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

		int tmoneatdeltat = (int) settings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		boolean createlinks = (boolean) settings.get(KEY_CREATE_LINKS);
		boolean breaklinks = (boolean) settings.get(KEY_BREAK_LINKS);

		// Generate the default graph
		for (final Integer trackID : trackmodel.trackIDs(true)) {
			// Nothing special here just maintaining the normal links found
			Set<DefaultWeightedEdge> normaltracks = trackmodel.trackEdges(trackID);
			for (final DefaultWeightedEdge edge : normaltracks) {

				final Spot source = trackmodel.getEdgeSource(edge);
				final Spot target = trackmodel.getEdgeTarget(edge);
				graph.addVertex(source);
				graph.addVertex(target);
				final DefaultWeightedEdge newedge = graph.addEdge(source, target);
				graph.setEdgeWeight(newedge, graph.getEdgeWeight(newedge));

			}
		}
		if (breaklinks)

			graph = BreakLinksTrack(model, uniquelabelID, DividingStartspots, framespots, img, logger, graph,
					calibration, tmoneatdeltat);

		int count = 0;
		if (Apoptosisspots != null) {

			logger.log("Verifying apoptosis.\n");
			// Lets take care of apoptosis
			for (Map.Entry<Integer, Pair<Spot, Spot>> trackidspots : Apoptosisspots.entrySet()) {
				count++;
				// Get the current trackID
				int trackID = trackidspots.getKey();
				Pair<Spot, Spot> trackspots = trackidspots.getValue();

				// Apoptosis cell can not be source of an edge
				Spot killerspot = trackspots.getB();

				logger.setProgress((float) (count) / Apoptosisspots.size());
				
				
				Comparator<Spot> comparator = new SpotComparator();
				SortedDepthFirstIterator< Spot, DefaultWeightedEdge > depthiterator = model.getTrackModel().getSortedDepthFirstIterator(killerspot, comparator, true);
				
				List<Spot> verticesToRemove = new ArrayList<>();
				boolean skipRemoval = false;

				while (depthiterator.hasNext()) {
				    Spot vertex = depthiterator.next();
				    
				        if (comparator.compare(vertex, killerspot) <= 0) {
				            skipRemoval = true;
				            continue;
				        }
				    if (!skipRemoval) {
				        verticesToRemove.add(vertex);
				    }
				    
				    // Reset the skipRemoval flag if needed
				    if (skipRemoval && comparator.compare(vertex, killerspot) > 0) {
				        skipRemoval = false;
				    }
				}

				// Remove vertices outside the iterator loop to avoid concurrent modification
				for (Spot vertex : verticesToRemove) {
				    graph.removeVertex(vertex);
				}
				
				
			}

		}

		count = 0;

		if (createlinks) {
			if (Mitosisspots != null) {

				List<Future<Graphobject>> graphlistresult = LinkCreator(model, trackmate, uniquelabelID,
						DividingStartspots, Mitosisspots, settings, ndim, logger, img, framespots, numThreads,
						calibration, addDisplay);
				for (Future<Graphobject> graphresult : graphlistresult) {

					Graphobject object = graphresult.get();
					ArrayList<Pair<Spot, Spot>> removeedges = object.removeedges;
					ArrayList<Pair<Spot, Spot>> addedges = object.addedges;
					ArrayList<Double> costlist = object.costlist;

					for (int i = 0; i < costlist.size(); ++i) {

						Pair<Spot, Spot> removesourcetarget = removeedges.get(i);
						graph.removeEdge(removesourcetarget.getA(), removesourcetarget.getB());

					}
					for (int i = 0; i < costlist.size(); ++i) {

						Pair<Spot, Spot> addsourcetarget = addedges.get(i);
						double cost = costlist.get(i);
						graph.addVertex(addsourcetarget.getA());
						graph.addVertex(addsourcetarget.getB());

						if (graph.degreeOf(addsourcetarget.getB()) < 2) {
							final DefaultWeightedEdge edge = graph.addEdge(addsourcetarget.getA(),
									addsourcetarget.getB());

							graph.setEdgeWeight(edge, cost);

						}

					}

				}

			}
		}

		
		logger.setProgress(1d);
		
		logger.log("Done, please review the TrackScheme by going back.\n");

		model.beginUpdate();
		model.clearTracks(true);
        IJ.wait(1000);

		model.setTracks(graph, true);
		
		logger.log("New tracks: " + model.getTrackModel().nTracks(true));
		model.endUpdate();

		return graph;

	}
	


	private static void addOverlay(final Roi overlay, final ImagePlus imp, final Spot spot) {

		imp.getOverlay().add(overlay);

	}

	private static SpotCollection regionspot(final ImgPlus<UnsignedShortType> img, final SpotCollection allspots,
			final Spot motherspot, final Logger logger, final double[] calibration, final int frame,
			final double region, final double[] motherslope, final double mariangle, final boolean mariprinciple) {

		SpotCollection regionspots = new SpotCollection();

		final int Nspots = allspots.getNSpots(frame, false);
		if (Nspots > 0)
			for (Spot spot : allspots.iterable(frame, false)) {

				if (mariprinciple) {

					double motheraxis = motherslope[1] / motherslope[0];

					double daughtermotheraxis = (motherspot.getDoublePosition(1) - spot.getDoublePosition(1))
							/ (motherspot.getDoublePosition(0) - spot.getDoublePosition(0));

					double signeddaughtermotherangle = ((180 / 3.14)
							* Math.atan((motheraxis - daughtermotheraxis) / (1 + motheraxis * daughtermotheraxis)));

					double daughtermotherangle = Math.abs(signeddaughtermotherangle);

					if (motherspot.squareDistanceTo(spot) <= region * region && daughtermotherangle <= mariangle) {

						regionspots.add(spot, frame);

					}

				}

				else {

					if (motherspot.squareDistanceTo(spot) <= region * region) {

						regionspots.add(spot, frame);

					}

				}

			}

		return regionspots;
	}

	private static Pair<double[], double[]> getEigen(final Ellipsoid ellipsoid, int ndim) {

		double[][] covariance = ellipsoid.getCovariance();
		final EigenvalueDecomposition eig = new Matrix(covariance).eig();
		final double[] Eigenvalues = eig.getRealEigenvalues();
		final Matrix Eigenvector = eig.getV();

		double smallesteigenval = Double.MAX_VALUE;
		double largesteigenval = -Double.MAX_VALUE;
		int index = -1;
		int largeindex = -1;
		for (int i = 0; i < Eigenvalues.length; ++i) {

			if (Eigenvalues[i] < smallesteigenval) {

				smallesteigenval = Eigenvalues[i];
				index = i;
			}

			if (Eigenvalues[i] > largesteigenval) {

				largesteigenval = Eigenvalues[i];
				largeindex = i;
			}
		}

		double[] smallvec = new double[Eigenvalues.length];
		double[] largevec = new double[Eigenvalues.length];
		for (int i = 0; i < Eigenvector.getRowDimension(); ++i) {

			smallvec[i] = Eigenvector.get(i, index);
			largevec[i] = Eigenvector.get(i, largeindex);
		}

		return new ValuePair<double[], double[]>(smallvec, largevec);

	}

	private static Ellipsoid getEllipsoid(Spot currentspot, ImgPlus<UnsignedShortType> img, double[] calibration) {

		int ndim = img.numDimensions();
		Ellipsoid currentellipsoid = null;
		long[] center = new long[currentspot.numDimensions()];
		for (int d = 0; d < center.length; d++) {
			center[d] = Math.round(currentspot.getFeature(Spot.POSITION_FEATURES[d]).doubleValue() / calibration[d]);
		}

		ImgPlus<UnsignedShortType> frameimg = ImgPlusViews.hyperSlice(img, ndim - 1,
				(int) currentspot.getFeature(Spot.FRAME).intValue());

		long[] location = new long[ndim - 1];
		RandomAccess<UnsignedShortType> ranac = frameimg.randomAccess();
		for (int d = 0; d < ndim - 1; ++d) {
			location[d] = (long) (currentspot.getDoublePosition(d) / calibration[d]);
			ranac.setPosition(location[d], d);
		}

		int label = ranac.get().get();

		Cursor<UnsignedShortType> cur = frameimg.localizingCursor();
		ArrayList<Localizable> points = new ArrayList<Localizable>();
		while (cur.hasNext()) {

			cur.fwd();

			if (cur.get().get() == label) {

				long[] point = new long[center.length];
				for (int d = 0; d < center.length; d++) {
					point[d] = cur.getLongPosition(d) - center[d];

				}
				points.add(new Point(point));

			}

		}

		int nPoints = points.size();

		if (nPoints >= 6) {

			RealMatrix MatrixD = new Array2DRowRealMatrix(nPoints, 5);
			int i = 0;
			for (Localizable point : points) {

				final double x = point.getDoublePosition(0);
				final double y = point.getDoublePosition(1);

				double xx = x * x;
				double yy = y * y;
				double xy = 2 * x * y;
				MatrixD.setEntry(i, 0, xx);
				MatrixD.setEntry(i, 1, yy);
				MatrixD.setEntry(i, 2, xy);
				MatrixD.setEntry(i, 3, 2 * x);
				MatrixD.setEntry(i, 4, 2 * y);

				i = i + 1;
			}
			RealMatrix dtd = MatrixD.transpose().multiply(MatrixD);

			// Create a vector of ones.
			RealVector ones = new ArrayRealVector(nPoints);
			ones.mapAddToSelf(1);

			// Multiply: d' * ones.mapAddToSelf(1)
			RealVector dtOnes = MatrixD.transpose().operate(ones);

			// Find ( d' * d )^-1
			DecompositionSolver solver = new SingularValueDecomposition(dtd).getSolver();
			RealMatrix dtdi = solver.getInverse();

			// v = (( d' * d )^-1) * ( d' * ones.mapAddToSelf(1));
			RealVector v = dtdi.operate(dtOnes);

			currentellipsoid = ellipsoidFromEquation2D(v);

		}

		return currentellipsoid;
	}

	private static Ellipsoid ellipsoidFromEquation2D(final RealVector V) {
		final double a = V.getEntry(0);
		final double b = V.getEntry(1);
		final double c = V.getEntry(2);
		final double d = V.getEntry(3);
		final double e = V.getEntry(4);
		double[] Coefficents = V.toArray();

		final double[][] aa = new double[][] { { a, c }, { c, b } };

		final double[] bb = new double[] { d, e };
		double det = new Matrix(aa).det();

		if (det > 1.0E-15) {
			final double[] cc = new Matrix(aa).solve(new Matrix(bb, 2)).getRowPackedCopy();
			LinAlgHelpers.scale(cc, -1, cc);
			final double[] At = new double[2];
			LinAlgHelpers.mult(aa, cc, At);
			final double r33 = LinAlgHelpers.dot(cc, At) + 2 * LinAlgHelpers.dot(bb, cc) - 1;
			LinAlgHelpers.scale(aa, -1 / r33, aa);
			int n = cc.length;
			double[][] covariance = new Matrix(aa).inverse().getArray();

			return (new Ellipsoid(cc, covariance, aa, null, computeAxisAndRadiiFromCovariance(covariance, n),
					Coefficents));
		} else
			return null;
	}

	private static double[] computeAxisAndRadiiFromCovariance(double[][] covariance, int n) {
		final EigenvalueDecomposition eig = new Matrix(covariance).eig();
		final Matrix ev = eig.getD();
		double[] radii = new double[n];
		for (int d = 0; d < n; ++d) {
			radii[d] = Math.sqrt(ev.get(d, d));
		}
		return radii;
	}

	/**
	 * 
	 * @param model       The TrackMate model object
	 * @param img         The integer labelled image
	 * @param logger      TrackMate logger
	 * @param calibration Image calibration
	 * @return A HashMap of {@code <Segment Label, Frame>: <Spot, TrackID>} and
	 *         Spot, A second HashMap of trackID + starting spot and trackID +
	 *         list of dividing spots for that track
	 */
	public static Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>>> getFirstTrackMateobject(
			final Model model, final ImgPlus<UnsignedShortType> img, final Logger logger, double[] calibration) {

		Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> DividingStartspots = getTMStartSplit(model);
		int ndim = img.numDimensions() - 1;
		RandomAccess<UnsignedShortType> ranac = img.randomAccess();
		Set<Integer> AllTrackIds = model.getTrackModel().trackIDs(true);
		HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID = new HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>();

		logger.flush();
		logger.log("Collecting tracks, in total " + AllTrackIds.size() + ".\n");
		int count = 0;
		for (int trackID : AllTrackIds) {

			Set<Spot> trackspots = model.getTrackModel().trackSpots(trackID);

			count++;
			for (Spot spot : trackspots) {

				logger.setProgress((float) (count) / (AllTrackIds.size() + 1));

				int frame = spot.getFeature(Spot.FRAME).intValue();
				if (frame < img.dimension(ndim) - 1) {
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

		return new ValuePair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>>>(
				uniquelabelID, DividingStartspots);

	}

	public static <T extends RealType<T> & NativeType<T>> HashMap<Integer, Pair<Spot, Spot>> getapoptosisTrackID(
			HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID,
			Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> DividingStartspots, final Model model,
			final ImgPlus<UnsignedShortType> img, HashMap<Integer, ArrayList<Spot>> framespots,
			final Map<String, Object> mapsettings, final Logger logger, final int numThreads, double[] calibration) {

		// Starting point of the tree + apoptotic spot in the trackID
		HashMap<Integer, Pair<Spot, Spot>> Trackapoptosis = new HashMap<Integer, Pair<Spot, Spot>>();
		// Spots from trackmate

		int ndim = img.numDimensions() - 1;
		RandomAccess<UnsignedShortType> ranac = img.randomAccess();

		logger.log("Matching with oneat apoptosis spots.\n");
		logger.setProgress(1.);

		int count = 0;
		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			logger.setProgress(count / (framespots.entrySet().size() + 1));
			count++;
			int frame = framemap.getKey();
			if (frame < img.dimension(ndim) - 1) {

				ArrayList<Spot> spotlist = framemap.getValue();

				for (Spot currentspots : spotlist) {

					long[] location = new long[ndim];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) (currentspots.getDoublePosition(d) / calibration[d]);
						ranac.setPosition(location[d], d);
					}
					ranac.setPosition(frame, ndim);
					ArrayList<Integer> Alllabels = new ArrayList<Integer>();
					int labelID = ranac.get().get();
					if (labelID != 0)
						Alllabels.add(labelID);
				

					Iterator<Integer> labeliter = Alllabels.iterator();
					while (labeliter.hasNext()) {

						int label = labeliter.next();
						if (uniquelabelID.containsKey(new ValuePair<Integer, Integer>(label, frame))) {
							Pair<Spot, Integer> spotandtrackID = uniquelabelID
									.get(new ValuePair<Integer, Integer>(label, frame));
							// Now get the spot ID

							Spot spot = spotandtrackID.getA();

							int trackID = spotandtrackID.getB();
							Spot startspot = DividingStartspots.getA().get(trackID);

							ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
							trackspotlist.add(spot);
							Pair<Spot, Spot> pair = new ValuePair<Spot, Spot>(spot, startspot);
							Trackapoptosis.put(trackID, pair);

						}
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
			Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> DividingStartspots, final Model model,
			final ImgPlus<UnsignedShortType> img, HashMap<Integer, ArrayList<Spot>> framespots,
			final Map<String, Object> mapsettings, final Logger logger, final int numThreads, double[] calibration) {

		// Starting point of the tree + list of mitosis spots in the trackID
		HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Trackmitosis = new HashMap<Integer, Pair<Spot, ArrayList<Spot>>>();
		// Spots from trackmate

		int ndim = img.numDimensions() - 1;
		int tmoneatdeltat = (int) mapsettings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		RandomAccess<UnsignedShortType> ranac = img.randomAccess();

		logger.log("Matching with oneat mitosis spots.\n");
		logger.setProgress(1.);

		int count = 0;
		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			logger.setProgress(count / (framespots.entrySet().size() + 1));
			count++;
			int frame = framemap.getKey();
			if (frame < img.dimension(ndim) - 1) {

				ArrayList<Spot> spotlist = framemap.getValue();

				for (Spot currentspots : spotlist) {

					long[] location = new long[ndim];
					for (int d = 0; d < ndim; ++d) {
						location[d] = (long) (currentspots.getDoublePosition(d) / calibration[d]);
						ranac.setPosition(location[d], d);
					}
					ranac.setPosition(frame, ndim);

					ArrayList<Integer> Alllabels = new ArrayList<Integer>();
					int labelID = ranac.get().get();
					if (labelID != 0)
						Alllabels.add(labelID);
		
					Iterator<Integer> labeliter = Alllabels.iterator();
					while (labeliter.hasNext()) {

						int label = labeliter.next();
						if (uniquelabelID.containsKey(new ValuePair<Integer, Integer>(label, frame))) {
							Pair<Spot, Integer> spotandtrackID = uniquelabelID
									.get(new ValuePair<Integer, Integer>(label, frame));
							// Now get the spot ID

							Spot spot = spotandtrackID.getA();

							int trackID = spotandtrackID.getB();
							Pair<Boolean, Pair<Spot, Spot>> isDividingTMspot = isDividingTrack(DividingStartspots, spot,
									trackID, tmoneatdeltat);
							Boolean isDividing = isDividingTMspot.getA();

							// If isDividing is true oneat does not need to correct the track else it has to
							// correct the trackid
							if (!isDividing) {

								Spot startspot = isDividingTMspot.getB().getA();

								if (Trackmitosis.containsKey(trackID)) {

									Pair<Spot, ArrayList<Spot>> trackstartspotlist = Trackmitosis.get(trackID);
									if (!trackstartspotlist.getB().contains(spot))
										trackstartspotlist.getB().add(spot);

									Pair<Spot, ArrayList<Spot>> pairlist = new ValuePair<Spot, ArrayList<Spot>>(
											startspot, trackstartspotlist.getB());
									Trackmitosis.put(trackID, pairlist);

								} else {

									ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
									trackspotlist.add(spot);

									Pair<Spot, ArrayList<Spot>> pairlist = new ValuePair<Spot, ArrayList<Spot>>(
											startspot, trackspotlist);
									Trackmitosis.put(trackID, pairlist);

								}

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
			Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> DividingStartspots,
			HashMap<Integer, ArrayList<Spot>> framespots, final ImgPlus<UnsignedShortType> img, final Logger logger,
			final SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph, double[] calibration, int N) {

		int count = 0;
		logger.log("Breaking links not found by oneat.\n");

		HashMap<Integer, ArrayList<Spot>> Dividingspotlocations = DividingStartspots.getB();
		int ndim = img.numDimensions() - 1;

		Set<Integer> AllTrackIds = model.getTrackModel().trackIDs(true);

		RandomAccess<UnsignedShortType> ranac = img.randomAccess();
		ArrayList<Integer> DividingTrackids = new ArrayList<Integer>();
		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {

			int frame = framemap.getKey();
			if (frame < img.dimension(ndim) - 1) {
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
						if (closestdistance > N && closestSpot != null) {

							Set<DefaultWeightedEdge> e = model.getTrackModel().edgesOf(closestSpot);

							Iterator<DefaultWeightedEdge> it = e.iterator();
							while (it.hasNext()) {

								DefaultWeightedEdge edge = it.next();
								graph.getEdgeSource(edge);
								graph.getEdgeTarget(edge);
								DefaultWeightedEdge thisedge = graph.getEdge(graph.getEdgeSource(edge),
										graph.getEdgeTarget(edge));
								graph.removeEdge(thisedge);

							}

						} else
							DividingTrackids.add(trackID);
					}

				}

			}

		}

		AllTrackIds.removeAll(DividingTrackids);
		for (int trackID : AllTrackIds) {

			ArrayList<Spot> badapple = Dividingspotlocations.get(trackID);

			for (Spot removeapple : badapple) {

				Set<DefaultWeightedEdge> e = model.getTrackModel().edgesOf(removeapple);

				Iterator<DefaultWeightedEdge> it = e.iterator();
				while (it.hasNext()) {

					DefaultWeightedEdge edge = it.next();
					graph.getEdgeSource(edge);
					graph.getEdgeTarget(edge);
					DefaultWeightedEdge thisedge = graph.getEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge));
					graph.removeEdge(thisedge);

				}

			}

		}
		return graph;

	}

	/**
	 * 
	 * @param spot
	 * @param trackID
	 * @param N
	 * @param model
	 * @return
	 */

	private static Pair<Boolean, Pair<Spot, Spot>> isDividingTrack(
			final Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> DividingStartspots, final Spot spot,
			final int trackID, final int N) {

		Boolean isDividing = false;

		Spot closestSpot = null;

		ArrayList<Spot> Dividingspotlocations = DividingStartspots.getB().get(trackID);
		Spot startingspot = DividingStartspots.getA().get(trackID);

		Pair<Double, Spot> closestspotpair = closestSpot(spot, Dividingspotlocations);
		double closestdistance = closestspotpair.getA();
		closestSpot = closestspotpair.getB();
		// There could be a N frame gap at most between the TM detected dividing spot
		// location and oneat found spot location
		if (closestdistance < N)
			isDividing = true;

		return new ValuePair<Boolean, Pair<Spot, Spot>>(isDividing,
				new ValuePair<Spot, Spot>(startingspot, closestSpot));
	}

	private static Pair<Double, Spot> closestSpot(final Spot targetspot, final ArrayList<Spot> Dividingspotlocations) {

		double mintimeDistance = Double.MAX_VALUE;
		Spot closestsourcespot = null;

		for (Spot sourcespot : Dividingspotlocations) {

			final double dist = sourcespot.diffTo(targetspot, Spot.FRAME);

			if (dist <= mintimeDistance) {

				mintimeDistance = dist;
				closestsourcespot = sourcespot;
			}

		}

		Pair<Double, Spot> closestspotpair = new ValuePair<Double, Spot>(Math.abs(mintimeDistance), closestsourcespot);

		return closestspotpair;

	}

	/**
	 * 
	 * @param model TrackMate Model
	 * @return Pair of HashMap <TrackID, TrackStartSpot> and <TrackID, List of
	 *         dividing Spots in that track>
	 */
	private static Pair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>> getTMStartSplit(final Model model) {

		HashMap<Integer, ArrayList<Spot>> Dividingspots = new HashMap<Integer, ArrayList<Spot>>();
		HashMap<Integer, Spot> Startingspots = new HashMap<Integer, Spot>();
		TrackModel trackmodel = model.getTrackModel();

		for (final Integer trackID : trackmodel.trackIDs(true)) {

			final Set<DefaultWeightedEdge> track = trackmodel.trackEdges(trackID);
			ArrayList<Pair<Integer, Spot>> Sources = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Pair<Integer, Spot>> Targets = new ArrayList<Pair<Integer, Spot>>();
			ArrayList<Integer> SourcesID = new ArrayList<Integer>();
			ArrayList<Integer> TargetsID = new ArrayList<Integer>();

			Spot Starts = null;

			ArrayList<Spot> Splits = new ArrayList<Spot>();

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

			// find track start
			for (Pair<Integer, Spot> sid : Sources) {

				if (!TargetsID.contains(sid.getA())) {

					Starts = sid.getB();

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
					Splits.add(sid.getB());
				}
				scount = 0;
			}

			Startingspots.put(trackID, Starts);
			Dividingspots.put(trackID, Splits);

		}

		return new ValuePair<HashMap<Integer, Spot>, HashMap<Integer, ArrayList<Spot>>>(Startingspots, Dividingspots);

	}

	
	public static Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> get_action_spotlist_frame(final HashMap<Integer, ArrayList<Oneatobject>> ActionMap, final double[] calibration, final int ndims) {
		
		// Parse each component.

		final Iterator<Entry<Integer, ArrayList<Oneatobject>>> iterator = ActionMap.entrySet().iterator();
		SpotCollection actionspots = new SpotCollection();
		HashMap<Integer, ArrayList<Spot>> ActionSpotListFrame = new HashMap<Integer, ArrayList<Spot>>();
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
				currentspot.putFeature(Spot.POSITION_X, Double.valueOf(x));
				currentspot.putFeature(Spot.POSITION_Y, Double.valueOf(y));
				currentspot.putFeature(Spot.POSITION_Z, Double.valueOf(z));
				currentspot.putFeature(Spot.FRAME, Double.valueOf(frame));
				currentspot.putFeature(Spot.RADIUS, Double.valueOf(radius));
				currentspot.putFeature(Spot.QUALITY, Double.valueOf(quality));

				currentspots.add(currentspot);
				actionspots.add(currentspot, frame);
				ActionSpotListFrame.put(frame, currentspots);
			}

		}
		
		return new ValuePair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>(actionspots, ActionSpotListFrame);

	}
	
	
	public static Pair<ArrayList<Oneatobject>, HashMap<Integer, ArrayList<Oneatobject>>> get_action_spots(final File oneatactionfile, final Logger logger, final double[] calibration, double probthreshold) {
		
		String line = "";
		String cvsSplitBy = ",";
		int count = 0;

		ArrayList<Oneatobject> ActionSpots = new ArrayList<Oneatobject>();
		HashMap<Integer, ArrayList<Oneatobject>> ActionMap = new HashMap<Integer, ArrayList<Oneatobject>>();
		
		try (BufferedReader br = new BufferedReader(new FileReader(oneatactionfile))) {

			while ((line = br.readLine()) != null) {

				// use comma as separator
				String[] divisionspotsfile = line.split(cvsSplitBy);

				if (count > 0) {

					if(divisionspotsfile.length > 4) { 
					int time = (int) Double.parseDouble(divisionspotsfile[0]);
					double Z = Double.parseDouble(divisionspotsfile[1]) * calibration[2];
					double Y = Double.parseDouble(divisionspotsfile[2]) * calibration[1];
					double X = Double.parseDouble(divisionspotsfile[3]) * calibration[0];
					double score = Double.parseDouble(divisionspotsfile[4]);
					double size = Double.parseDouble(divisionspotsfile[5]);
					double confidence = Double.parseDouble(divisionspotsfile[6]);
					
					if (score >= probthreshold) {
						Oneatobject Spot = new Oneatobject(time, Z, Y, X, score, size, confidence);

						if (ActionMap.get(time) == null) {
							ActionSpots = new ArrayList<Oneatobject>();
							ActionMap.put(time, ActionSpots);
						} else
							ActionMap.put(time, ActionSpots);
						ActionSpots.add(Spot);
						
						count = count + 1;
					}
					

				}
					
					else {
						
						int time = (int) Double.parseDouble(divisionspotsfile[0]);
						double Z = Double.parseDouble(divisionspotsfile[1]) * calibration[2];
						double Y = Double.parseDouble(divisionspotsfile[2]) * calibration[1];
						double X = Double.parseDouble(divisionspotsfile[3]) * calibration[0];
						double score = 1.0;
						double size = 10;
						double confidence = 1.0;
						
						if (score >= probthreshold) {
							Oneatobject Spot = new Oneatobject(time, Z, Y, X, score, size, confidence);

							if (ActionMap.get(time) == null) {
								ActionSpots = new ArrayList<Oneatobject>();
								ActionMap.put(time, ActionSpots);
							} else
								ActionMap.put(time, ActionSpots);
							ActionSpots.add(Spot);
							count = count + 1;
						}
						
					}
					
					
					
				}
				
				if (count == 0)
					count = 1;
				
				
			}
		} catch (IOException ie) {
			ie.printStackTrace();
		}
		logger.log("Oneat found action events:" + " " + count + "\n");
		return new ValuePair<ArrayList<Oneatobject>, HashMap<Integer, ArrayList<Oneatobject>>>(ActionSpots, ActionMap);
	}
	
	
	/**
	 * 
	 * @param oneatdivisionfile  The file containing oneat locations of mitosis
	 *                           events
	 * @param oneatapoptosisfile The file containing oneat locations of cell death
	 *                           events
	 * @param settings           HashMap of oneat specific parameters to veto events
	 *                           found in file
	 * @param logger             TrackMate logger to log the number of found events
	 * @param ndims              The image dimensions
	 * @param calibration        The image calibration
	 * @return SpotCollection and HashMap of {@code <frame, SpotList>} for
	 *         mitosis/cell death
	 */
	
	
	
	
	public static Pair<Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>> run(
			final File oneatdivisionfile, final File oneatapoptosisfile, Map<String, Object> settings,
			final Logger logger, final int ndims, final double[] calibration) {

		SpotCollection divisionspots = new SpotCollection();
		HashMap<Integer, ArrayList<Spot>> DivisionSpotListFrame = new HashMap<Integer, ArrayList<Spot>>();

		ArrayList<Oneatobject> DivisionSpots = new ArrayList<Oneatobject>();
		HashMap<Integer, ArrayList<Oneatobject>> DivisionMap = new HashMap<Integer, ArrayList<Oneatobject>>();
		
		double probthreshold = (double) settings.get(KEY_PROB_THRESHOLD);

		if (oneatdivisionfile != null) {
			Pair<ArrayList<Oneatobject>, HashMap<Integer, ArrayList<Oneatobject>>> mitosisobject   =  get_action_spots(oneatdivisionfile, logger, calibration, probthreshold);
            
			DivisionSpots = mitosisobject.getA();
			DivisionMap = mitosisobject.getB();	
			
			// Parse each component.
			Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> divisionspotlistframe = get_action_spotlist_frame(DivisionMap, calibration, ndims);
			
			divisionspots = divisionspotlistframe.getA();
			DivisionSpotListFrame = divisionspotlistframe.getB();	
			
		}

		SpotCollection apoptosisspots = new SpotCollection();
		HashMap<Integer, ArrayList<Spot>> ApoptosisSpotListFrame = new HashMap<Integer, ArrayList<Spot>>();
		ArrayList<Oneatobject> ApoptosisSpots = new ArrayList<Oneatobject>();
		HashMap<Integer, ArrayList<Oneatobject>> ApoptosisMap = new HashMap<Integer, ArrayList<Oneatobject>>();
		if (oneatapoptosisfile != null) {
			
            Pair<ArrayList<Oneatobject>, HashMap<Integer, ArrayList<Oneatobject>>> apoptosisobject =  get_action_spots(oneatapoptosisfile, logger, calibration, probthreshold);
            
			ApoptosisSpots = apoptosisobject.getA();
			ApoptosisMap = apoptosisobject.getB();	
			
			// Parse each component.
			Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> apoptosisspotlistframe = get_action_spotlist_frame(ApoptosisMap, calibration, ndims);
			
			apoptosisspots = apoptosisspotlistframe.getA();
			ApoptosisSpotListFrame = apoptosisspotlistframe.getB();	
		}

		Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> DivisionPair = new ValuePair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>(
				divisionspots, DivisionSpotListFrame);

		Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> ApoptosisPair = new ValuePair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>(
				apoptosisspots, ApoptosisSpotListFrame);

		return new ValuePair<Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>, Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>>(
				DivisionPair, ApoptosisPair);
	}

}
