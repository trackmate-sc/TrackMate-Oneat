package fiji.plugin.trackmate.action.oneat;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collection;
import java.util.HashSet;

import org.jgrapht.graph.DefaultWeightedEdge;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.util.TMUtils;
import ij.ImagePlus;
import ij.gui.Roi;

public class OneatOverlay extends Roi {
	
	private static final long serialVersionUID = 1L;

	protected final double[] calibration;

	protected Collection< DefaultWeightedEdge > highlight = new HashSet<>();

	protected DisplaySettings displaySettings;

	protected final Spot motherspot;
	
	protected final Spot source;
	
	protected final Spot target;
	
	protected final double[] motherslope;

	public OneatOverlay(final Spot motherspot, final Spot source, final Spot target, final double[] motherslope, final ImagePlus imp, final DisplaySettings displaySettings) {
		super(0, 0, imp);
		this.motherspot = motherspot;
		this.source = source;
		this.target = target;
		this.motherslope = motherslope;
		this.calibration = TMUtils.getSpatialCalibration( imp );
		this.imp = imp;
		this.displaySettings = displaySettings;
	}
	@Override
	public final synchronized void drawOverlay( final Graphics g )
	{
		final Graphics2D g2d = ( Graphics2D ) g;
				
	}
	
	protected void drawSlope(final Graphics2D g2d, final Spot motherspot, final double[] motherslope) {
		
		double length = motherspot.getFeature(Spot.RADIUS);
		
	}
	
	
	protected void drawEdge( final Graphics2D g2d, final Spot source, final Spot target, final int xcorner, final int ycorner, final double magnification )
	{
		// Find x & y in physical coordinates
		final double x0i = source.getFeature( Spot.POSITION_X );
		final double y0i = source.getFeature( Spot.POSITION_Y );
		final double x1i = target.getFeature( Spot.POSITION_X );
		final double y1i = target.getFeature( Spot.POSITION_Y );
		// In pixel units
		final double x0p = x0i / calibration[ 0 ] + 0.5f;
		final double y0p = y0i / calibration[ 1 ] + 0.5f;
		final double x1p = x1i / calibration[ 0 ] + 0.5f;
		final double y1p = y1i / calibration[ 1 ] + 0.5f;
		// Scale to image zoom
		final double x0s = ( x0p - xcorner ) * magnification;
		final double y0s = ( y0p - ycorner ) * magnification;
		final double x1s = ( x1p - xcorner ) * magnification;
		final double y1s = ( y1p - ycorner ) * magnification;
		// Round
		final int x0 = ( int ) Math.round( x0s );
		final int y0 = ( int ) Math.round( y0s );
		final int x1 = ( int ) Math.round( x1s );
		final int y1 = ( int ) Math.round( y1s );

		g2d.drawLine( x0, y0, x1, y1 );
	}
}
