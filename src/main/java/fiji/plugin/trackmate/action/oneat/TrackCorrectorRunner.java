package fiji.plugin.trackmate.action.oneat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Logger.SlaveLogger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.tracking.sparselap.linker.JaqamanLinker;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
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



	public static SimpleWeightedGraph<Spot, DefaultWeightedEdge> getCorrectedTracks(final Model model,
			final TrackMate trackmate,
			Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>> uniquelabelID,
			Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots,
			HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Mitosisspots,
			HashMap<Integer, Pair<Spot, Spot>> Apoptosisspots, Map<String, Object> settings, final int ndim,
			final Logger logger,  final ImgPlus<UnsignedShortType> img,
			HashMap<Integer, ArrayList<Spot>> framespots, int numThreads, double[] calibration) {

		// Get the trackmodel and spots in the default tracking result and start to
		// create a new graph
		TrackModel trackmodel = model.getTrackModel();
		SpotCollection allspots = model.getSpots();

		SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

		double searchdistance = (double) (settings.get(KEY_LINKING_MAX_DISTANCE) != null
				? (double) settings.get(KEY_LINKING_MAX_DISTANCE)
				: 10);
		int tmoneatdeltat = (int) settings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		boolean createlinks = (boolean) settings.get(KEY_CREATE_LINKS);
		boolean breaklinks = (boolean) settings.get(KEY_BREAK_LINKS);
		boolean mariprinciple = (boolean) settings.get(KEY_USE_MARI_PRINCIPLE);
		double mariangle = (double) settings.get(KEY_MARI_ANGLE);

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
			int roiindex = 0;
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

			/*
			 * HashMap<String, Double> qu = new HashMap<String, Double>(); qu.put(QUALITY,
			 * 1.0); qu.put(POSITION_Z, 1.0); qu.put(RADIUS, 1.0);
			 * 
			 * cmsettings.put(KEY_SPLITTING_FEATURE_PENALTIES, qu);
			 * 
			 */

			if (settings.get(KEY_SPLITTING_FEATURE_PENALTIES) != DEFAULT_SPLITTING_FEATURE_PENALTIES)
				cmsettings.put(KEY_SPLITTING_FEATURE_PENALTIES, settings.get(KEY_SPLITTING_FEATURE_PENALTIES));
			else
				cmsettings.put(KEY_SPLITTING_FEATURE_PENALTIES, settings.get(KEY_LINKING_FEATURE_PENALTIES));

			// Lets take care of mitosis
			if (Mitosisspots != null) {
				logger.log("Total oneat Mitosis events " + " " + Mitosisspots.entrySet().size() + "\n");
				if (mariprinciple)
					logger.log("Using Mari's priniciple for track linking.\n");

				int trackcount = 0;
				for (Map.Entry<Integer, Pair<Spot, ArrayList<Spot>>> trackidspots : Mitosisspots.entrySet()) {

				
					// Get the current trackID
					int trackID = trackidspots.getKey();
					Set<DefaultWeightedEdge> dividingtracks = trackmodel.trackEdges(trackID);

					// List of all the mother cells and the root of the lineage tree
					Pair<Spot, ArrayList<Spot>> trackspots = trackidspots.getValue();

					ArrayList<Spot> mitosismotherspots = trackspots.getB();

					// Create a new map of spot and label

					// Create the pixel list for mother cells

				

					// Remove edges corresponding to mitotic trajectories
					for (final DefaultWeightedEdge edge : dividingtracks) {

						final Spot source = trackmodel.getEdgeSource(edge);

						if (mitosismotherspots.contains(source)) {

							graph.removeEdge(edge);
						}

					}

					int maricount = 0;
					Ellipsoid ellipsoid = null;
					double[] motherslope = new double[2];
					double[] largemotherslope = new double[2];
					Pair<double[], double[]> slope = new ValuePair<double[], double[]>(motherslope, largemotherslope);
					for (Spot motherspot : mitosismotherspots) {

						Set<DefaultWeightedEdge> mothertrack = trackmodel.edgesOf(motherspot);

						ellipsoid = getEllipsoid(motherspot, img, calibration);

						if (ellipsoid != null) {
							slope = getEigen(ellipsoid, ndim);
							motherslope = slope.getA();
							largemotherslope = slope.getB();

						}
						logger.setProgress(maricount / mitosismotherspots.size());

						maricount++;
						SimpleWeightedGraph<Spot, DefaultWeightedEdge> localgraph = new SimpleWeightedGraph<>(
								DefaultWeightedEdge.class);

						for (DefaultWeightedEdge localedge : mothertrack) {

							if (!graph.containsEdge(localedge)) {
								final Spot source = trackmodel.getEdgeSource(localedge);

								final Spot target = trackmodel.getEdgeTarget(localedge);
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

								SpotCollection regionspots = regionspot(img, allspots, motherspot, logger, calibration,
										(int) frame, searchdistance, motherslope, mariangle, mariprinciple);

								if (regionspots.getNSpots((int) frame, false) > 0)
									for (Spot spot : regionspots.iterable((int) frame, false)) {

										if (trackmodel.trackIDOf(spot) != null) {
											int regiontrackID = trackmodel.trackIDOf(spot);
											Set<DefaultWeightedEdge> localtracks = trackmodel.trackEdges(regiontrackID);

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
						final SlaveLogger jlLogger = new SlaveLogger(logger, 0, 0.9);
						final JaqamanLinker<Spot, Spot> linker = new JaqamanLinker<>(costMatrixCreator, jlLogger);
						if (!linker.checkInput() || !linker.process()) {
							System.out.println(linker.getErrorMessage());
						}

						/*
						 * Create links in graph.
						 */
                        System.gc();
						logger.setProgress(trackcount / (Mitosisspots.size() + 1));

						trackcount++;
						final Map<Spot, Spot> assignment = linker.getResult();
						final Map<Spot, Double> costs = linker.getAssignmentCosts();
						// Recreate new links
						if (assignment != null)
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

								roiindex++;
								for (DefaultWeightedEdge targetedge : drawlinkslinks) {
									Spot targetsource = trackmodel.getEdgeTarget(targetedge);
									OneatOverlay oneatOverlay = new OneatOverlay(motherspot, source, targetsource,
											motherslope, largemotherslope, trackmate.getSettings().imp);

									if (source.getDoublePosition(0) != targetsource.getDoublePosition(0)) {
										double daughtermotheraxisB = (targetsource.getDoublePosition(1)
												- motheraxis * targetsource.getDoublePosition(0) - intercept);

										if(mariprinciple)
										if (Math.signum(daughtermotheraxisB) * Math.signum(daughtermotheraxis) < 0) {
											validlink = true;
											addOverlay(oneatOverlayFirst, trackmate.getSettings().imp, roiindex);
											addOverlay(oneatOverlay, trackmate.getSettings().imp, roiindex);
										}
										if(!mariprinciple) {
											addOverlay(oneatOverlayFirst, trackmate.getSettings().imp, roiindex);
											addOverlay(oneatOverlay, trackmate.getSettings().imp, roiindex);
											
										}
											
										roiindex++;
									}
								}
								if (validlink) {
									// Remove the targetsource and target edge prior to assingment
									for (DefaultWeightedEdge targetedge : targetlinks) {

										Spot targetsource = trackmodel.getEdgeSource(targetedge);
										graph.removeEdge(targetsource, target);
									}

									graph.addVertex(source);
									graph.addVertex(target);
									final DefaultWeightedEdge edge = graph.addEdge(source, target);
									// if (edge != null)
									graph.setEdgeWeight(edge, cost);
								}

							}

					}

				}

			}

		}

		logger.setProgress(1d);
		logger.flush();
		logger.log("Done, please review the TrackScheme by going back.\n");

		model.beginUpdate();
		model.clearTracks(true);

		IJ.wait(1000);

		model.setTracks(graph, true);
		logger.log("New tracks: " + model.getTrackModel().nTracks(true));
		model.endUpdate();

		return graph;

	}

	private static void addOverlay(final Roi overlay, final ImagePlus imp, int index) {

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

	public static Pair<Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>>, Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>>> getFirstTrackMateobject(
			final Model model, final ImgPlus<UnsignedShortType> img, final Logger logger, double[] calibration) {

		Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots = getTMStartSplit(
				model);
		int ndim = img.numDimensions() - 1;
		RandomAccess<UnsignedShortType> ranac = img.randomAccess();
		Set<Integer> AllTrackIds = model.getTrackModel().trackIDs(true);
		HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>> uniquelabelID = new HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>();
		HashMap<Spot, Integer> uniqueSpot = new HashMap<Spot, Integer>();
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
					uniqueSpot.put(spot, label);

				}
			}
		}
		Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>> uniquelabelIDSpot = new ValuePair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>>(
				uniquelabelID, uniqueSpot);
		return new ValuePair<Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>>, Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>>>(
				uniquelabelIDSpot, DividingStartspots);

	}

	public static <T extends RealType<T> & NativeType<T>> HashMap<Integer, Pair<Spot, Spot>> getapoptosisTrackID(
			Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>> uniquelabelID,
			final Model model, final ImgPlus<UnsignedShortType> img, HashMap<Integer, ArrayList<Spot>> framespots,
			final Map<String, Object> mapsettings, final Logger logger,final int numThreads, double[] calibration) {


		// Starting point of the tree + apoptotic spot in the trackID
		HashMap<Integer, Pair<Spot, Spot>> Trackapoptosis = new HashMap<Integer, Pair<Spot, Spot>>();
		// Spots from trackmate

		int ndim = img.numDimensions() - 1;
		int tmoneatdeltat = (int) mapsettings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		RandomAccess<UnsignedShortType> ranac = img.randomAccess();


		
		final ExecutorService executorS = Executors.newFixedThreadPool( numThreads );
		
		logger.log("Matching with oneat apoptosis spots.\n");


		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {
			
			executorS.submit( new Runnable()
			{
				@Override
				public void run()
				{
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

					HyperSphere<UnsignedShortType> hyperSphere = new HyperSphere<UnsignedShortType>(img, ranac, 1);
					int maxval = 0;
					for (UnsignedShortType val : hyperSphere) {

						if (maxval < val.get())
							maxval = val.get();
					}

					int labelID = maxval;

					if (uniquelabelID.getA().containsKey(new ValuePair<Integer, Integer>(labelID, frame))) {
						Pair<Spot, Integer> spotandtrackID = uniquelabelID.getA()
								.get(new ValuePair<Integer, Integer>(labelID, frame));
						// Now get the spot ID

						Spot spot = spotandtrackID.getA();

						int trackID = spotandtrackID.getB();
						Spot startspot = startSpotTrack(spot, trackID, tmoneatdeltat, model);
						
						ArrayList<Spot> trackspotlist = new ArrayList<Spot>();
						trackspotlist.add(spot);
						Pair<Spot, Spot> pair = new ValuePair<Spot, Spot>(spot, startspot);
						Trackapoptosis.put(trackID, pair);

					}
				}
			}

		}
			}
					);
		}
		executorS.shutdown();
		try
		{
			executorS.awaitTermination( 1, TimeUnit.DAYS );
		}
		catch ( final InterruptedException e )
		{
			
		}
		logger.log("Verifying lineage trees.\n");
		logger.setProgress(0.);

		return Trackapoptosis;
	}

	public static <T extends RealType<T> & NativeType<T>> HashMap<Integer, Pair<Spot, ArrayList<Spot>>> getmitosisTrackID(
			Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>> uniquelabelID,
			final Model model, final ImgPlus<UnsignedShortType> img, HashMap<Integer, ArrayList<Spot>> framespots,
			final Map<String, Object> mapsettings, final Logger logger, final int numThreads, double[] calibration) {

		HashMap<Integer, ArrayList<Spot>> Mitosisspots = new HashMap<Integer, ArrayList<Spot>>();

		// Starting point of the tree + list of mitosis spots in the trackID
		HashMap<Integer, Pair<Spot, ArrayList<Spot>>> Trackmitosis = new HashMap<Integer, Pair<Spot, ArrayList<Spot>>>();
		// Spots from trackmate

		int ndim = img.numDimensions() - 1;
		int tmoneatdeltat = (int) mapsettings.get(KEY_GAP_CLOSING_MAX_FRAME_GAP);
		RandomAccess<UnsignedShortType> ranac = img.randomAccess();

		logger.log("Matching with oneat mitosis spots.\n");
		
		final ExecutorService executorS = Executors.newFixedThreadPool( numThreads );
		
			
		for (Map.Entry<Integer, ArrayList<Spot>> framemap : framespots.entrySet()) {
			
			executorS.submit( new Runnable()
			{
				@Override
				public void run()
				{
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
					HyperSphere<UnsignedShortType> hyperSphere = new HyperSphere<UnsignedShortType>(img, ranac, 1);
					int maxval = 0;
					for (UnsignedShortType val : hyperSphere) {

						if (maxval < val.get())
							maxval = val.get();
					}

					int labelID = maxval;

					if (uniquelabelID.getA().containsKey(new ValuePair<Integer, Integer>(labelID, frame))) {
						Pair<Spot, Integer> spotandtrackID = uniquelabelID.getA()
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
			}
					);
		}
		executorS.shutdown();
		try
		{
			executorS.awaitTermination( 1, TimeUnit.DAYS );
		}
		catch ( final InterruptedException e )
		{
			
		}
		logger.log("Verifying lineage trees.\n");
		logger.setProgress(0.);

		return Trackmitosis;
	}

	private static SimpleWeightedGraph<Spot, DefaultWeightedEdge> BreakLinksTrack(final Model model,
			Pair<HashMap<Pair<Integer, Integer>, Pair<Spot, Integer>>, HashMap<Spot, Integer>> uniquelabelID,
			Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots,
			HashMap<Integer, ArrayList<Spot>> framespots, final ImgPlus<UnsignedShortType> img, final Logger logger,
			final SimpleWeightedGraph<Spot, DefaultWeightedEdge> graph, double[] calibration, int N) {

		int count = 0;
		logger.log("Breaking links not found by oneat.\n");

		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Dividingspotlocations = DividingStartspots.getB();
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

					if (uniquelabelID.getA().containsKey(new ValuePair<Integer, Integer>(labelID, frame))) {
						Pair<Spot, Integer> spotandtrackID = uniquelabelID.getA()
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

			ArrayList<Pair<Integer, Spot>> badapple = Dividingspotlocations.get(trackID);

			for (Pair<Integer, Spot> removeapple : badapple) {

				Set<DefaultWeightedEdge> e = model.getTrackModel().edgesOf(removeapple.getB());

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
	private static  Spot startSpotTrack(final Spot spot, final int trackID, final int N,
			final Model model) {

		
		Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots = getTMStartSplit(
				model);
		
		Spot startingspot = null;
		final Set<DefaultWeightedEdge> track = model.getTrackModel().trackEdges(trackID);

		for (final DefaultWeightedEdge e : track) {

			Spot Spotbase = model.getTrackModel().getEdgeSource(e);
			int id = model.getTrackModel().trackIDOf(Spotbase);

			if (id == trackID) {

				
				Pair<Integer, Spot> Startingspotlocations = DividingStartspots.getA().get(id);
				startingspot = Startingspotlocations.getB();
				

			}
		}

		return startingspot;
	}
	private static Pair<Boolean, Pair<Spot, Spot>> isDividingTrack(final Spot spot, final int trackID, final int N,
			final Model model) {

		Boolean isDividing = false;
		Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> DividingStartspots = getTMStartSplit(
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

			final double dist = sourcespot.diffTo(targetspot, Spot.FRAME);

			if (dist <= mintimeDistance) {

				mintimeDistance = dist;
				closestsourcespot = sourcespot;
			}

		}

		Pair<Double, Spot> closestspotpair = new ValuePair<Double, Spot>(Math.abs(mintimeDistance), closestsourcespot);

		return closestspotpair;

	}

	private static Pair<HashMap<Integer, Pair<Integer, Spot>>, HashMap<Integer, ArrayList<Pair<Integer, Spot>>>> getTMStartSplit(
			final Model model) {

		HashMap<Integer, ArrayList<Pair<Integer, Spot>>> Dividingspots = new HashMap<Integer, ArrayList<Pair<Integer, Spot>>>();
		HashMap<Integer, Pair<Integer, Spot>> Startingspots = new HashMap<Integer, Pair<Integer, Spot>>();
		TrackModel trackmodel = model.getTrackModel();
		for (final Integer trackID : trackmodel.trackIDs(true)) {

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

						int time = (int) Double.parseDouble(divisionspotsfile[0]);
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
					currentspot.putFeature(Spot.POSITION_X, Double.valueOf(x));
					currentspot.putFeature(Spot.POSITION_Y, Double.valueOf(y));
					currentspot.putFeature(Spot.POSITION_Z, Double.valueOf(z));
					currentspot.putFeature(Spot.FRAME, Double.valueOf(frame));
					currentspot.putFeature(Spot.RADIUS, Double.valueOf(radius));
					currentspot.putFeature(Spot.QUALITY, Double.valueOf(quality));

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
					currentspot.putFeature(Spot.POSITION_X, Double.valueOf(x));
					currentspot.putFeature(Spot.POSITION_Y, Double.valueOf(y));
					currentspot.putFeature(Spot.POSITION_Z, Double.valueOf(z));
					currentspot.putFeature(Spot.FRAME, Double.valueOf(frame));
					currentspot.putFeature(Spot.RADIUS, Double.valueOf(radius));
					currentspot.putFeature(Spot.QUALITY, Double.valueOf(quality));
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