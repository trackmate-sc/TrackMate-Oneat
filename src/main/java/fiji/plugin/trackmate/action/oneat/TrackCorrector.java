package fiji.plugin.trackmate.action.oneat;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.tracking.SpotTracker;
import net.imglib2.algorithm.MultiThreaded;
import net.imglib2.algorithm.OutputAlgorithm;

public interface TrackCorrector extends OutputAlgorithm< SimpleWeightedGraph< Spot, DefaultWeightedEdge > >, MultiThreaded
{
	/**
	 * Sets the {@link Logger} instance that will receive messages from this
	 * {@link SpotTracker}.
	 *
	 * @param logger
	 *            the logger to echo messages to.
	 */
	public void setLogger( final Logger logger );
	
	public long getProcessingTime();
	
}
