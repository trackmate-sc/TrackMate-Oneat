package fiji.plugin.trackmate.action.oneat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collection;
import java.util.HashSet;
import org.jgrapht.graph.DefaultWeightedEdge;
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
	
	protected final double[] largemotherslope;

	public OneatOverlay(final Spot motherspot, final Spot source, final Spot target, final double[] motherslope, final double[] largemotherslope, final ImagePlus imp) {
		super(0, 0, imp);
		this.motherspot = motherspot;
		this.source = source;
		this.target = target;
		this.motherslope = motherslope;
		this.largemotherslope = largemotherslope;
		this.calibration = TMUtils.getSpatialCalibration( imp );
		this.imp = imp;
	
	}
	@Override
	public final synchronized void drawOverlay( final Graphics g )
	{
		final Graphics2D g2d = ( Graphics2D ) g;
		final double magnification = getMagnification();

		// Painted clip in window coordinates.
		final int xcorner = ic.offScreenX( 0 );
		final int ycorner = ic.offScreenY( 0 );
		
		drawSlope(g2d, motherspot, motherslope, largemotherslope, xcorner, ycorner, magnification); 
		drawEdge(g2d,source,target,xcorner,ycorner,magnification );
		
				
	}
	
	protected void drawSlope(final Graphics2D g2d, final Spot motherspot, final double[] motherslope, final double[] largemotherslope, final int xcorner, final int ycorner, final double magnification) {
		
		double length = 10;
		final double x0i = motherspot.getFeature( Spot.POSITION_X );
		final double y0i = motherspot.getFeature( Spot.POSITION_Y );
		final double x0p = x0i / calibration[ 0 ] + 0.5f;
		final double y0p = y0i / calibration[ 1 ] + 0.5f;
		final double x0s = ( x0p - xcorner ) * magnification;
		final double y0s = ( y0p - ycorner ) * magnification;
		final int x0 = ( int ) Math.round( x0s );
		final int y0 = ( int ) Math.round( y0s );
		
		final double slope = motherslope[1] / motherslope[0];
		final int x1 = (int) ( x0 - length / Math.sqrt(1 + slope * slope) ); 
		final int y1 = (int) ( y0 -  length * slope / Math.sqrt(1 + slope * slope) );
		g2d.setColor( Color.ORANGE );
		
		g2d.setStroke(new BasicStroke(2));
		g2d.drawLine( x0, y0, x1, y1 );
		final int x2 = (int) ( x0 +length / Math.sqrt(1 + slope * slope) ); 
		final int y2 = (int) ( y0 +  length * slope / Math.sqrt(1 + slope * slope) );
		g2d.setColor( Color.ORANGE );
		g2d.drawLine( x0, y0, x2, y2 );
		
		
		
		final double largeslope = largemotherslope[1] / largemotherslope[0];
		final int largex1 = (int) ( x0 - length / Math.sqrt(1 + largeslope * largeslope) ); 
		final int largey1 = (int) ( y0 -  length * largeslope / Math.sqrt(1 + largeslope * largeslope) );
		g2d.setColor( Color.RED );
		
		g2d.setStroke(new BasicStroke(2));
		g2d.drawLine( x0, y0, largex1, largey1 );
		final int largex2 = (int) ( x0 +length / Math.sqrt(1 + largeslope * largeslope) ); 
		final int largey2 = (int) ( y0 +  length * largeslope / Math.sqrt(1 + largeslope * largeslope) );
		g2d.setColor( Color.RED );
		g2d.drawLine( x0, y0, largex2, largey2 );
		
		
		
		
		
		
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
		g2d.setStroke(new BasicStroke(2));
		g2d.setColor( Color.BLUE );
		g2d.drawLine( x0, y0, x1, y1 );
	}
}
