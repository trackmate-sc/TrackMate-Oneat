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

import java.awt.Frame;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.APOPTOSIS_FILE;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.DIVISION_FILE;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_BREAK_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_CREATE_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_TRACKLET_LENGTH;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_USE_MARI_PRINCIPLE;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_MARI_ANGLE;
import static fiji.plugin.trackmate.action.oneat.gui.Icons.ONEAT_ICON;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_PROB_THRESHOLD;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.action.oneat.gui.Icons.ONEAT_BIG_ICON;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import org.scijava.plugin.Plugin;



import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.action.AbstractTMAction;
import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.action.TrackMateActionFactory;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.util.TMUtils;
import fiji.plugin.trackmate.visualization.hyperstack.SpotOverlay;
import fiji.plugin.trackmate.visualization.hyperstack.TrackOverlay;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

public class  OneatExporterAction < T extends NativeType< T > & NumericType< T > > extends AbstractTMAction {

	
	public static final String INFO_TEXT = "<html>"
			+ "This action initiates Oneat track correction for the tracking results. "
			+  "<p> "
			+ "Oneat is a keras based library in python written by Varun Kapoor. "
			+ "It provides csv files of event locations such as mitosis/apoptosis "
			+ "using the csv file of event locations the tracks are corrected "
			+ "and a new trackscheme is generated with corrected tracks. "
			+ "</html>";

	public static final String KEY = "LAUNCH_ONEAT";

	public static final String NAME = "Launch Oneat track corrector";
	
	private static int detchannel;
	
	private Model model;
	
	private double linkdist;
	
	private int deltat;
	
	private int tracklet;
	
	private double probthreshold ;
	
	private double angle;
	
	private boolean breaklinks = true;
	
	private boolean createlinks = false;
	
	private boolean mariprinciple = true;
	@Override
	public void execute(TrackMate trackmate, SelectionModel selectionModel, DisplaySettings displaySettings,
			Frame gui) {

     	model = trackmate.getModel();

		
		trackmate.getSettings().imp.getOverlay().clear();
		
		
		
		HyperStackDisplayer displayer = new  HyperStackDisplayer( model, selectionModel, trackmate.getSettings().imp, displaySettings );
		displayer.render();
		displayer.refresh();
		
		Settings settings = trackmate.getSettings();
	    
		Map<String, Object> trackmapsettings = settings.trackerSettings;
		
		Map<String, Object> detectorsettings = settings.detectorSettings;
		Model model = trackmate.getModel();
		@SuppressWarnings("unchecked")
		final ImgPlus<T> img = TMUtils.rawWraps( settings.imp );
		
		final double[] calibration = new double[ 3 ];
		calibration[ 0 ] = settings.dx;
		calibration[ 1 ] = settings.dy;
		calibration[ 2 ] = settings.dz;
		
		if (gui!=null)
		{
			
			
			final OneatExporterPanel panel = new OneatExporterPanel(settings,trackmapsettings,detectorsettings, model);
			final int userInput = JOptionPane.showConfirmDialog(gui, panel, "Launch Oneat track corrector",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, ONEAT_BIG_ICON);
			
			if ( userInput != JOptionPane.OK_OPTION )
				return;
			
			File oneatdivisionfile = panel.getMistosisFile();
			File oneatapotosisfile = panel.getApoptosisFile();
			tracklet = panel.getMinTracklet();
			deltat = panel.getTimeGap();
			breaklinks = panel.getBreakLinks();
			createlinks = panel.getCreateLinks();
			detchannel = panel.getDetectionChannel();
			linkdist = panel.getLinkDist();
			probthreshold = panel.getProbThreshold();
			mariprinciple = panel.getMariPrinciple();
			angle = panel.getMariAngle();
			Map<String, Object> mapsettings = getSettings(oneatdivisionfile,oneatapotosisfile,trackmapsettings);
			OneatCorrectorFactory corrector = new OneatCorrectorFactory();
			ImgPlus <T> detectionimg =  img;
			if (img.dimensionIndex(Axes.CHANNEL) > 0) {
			     detectionimg = ImgPlusViews.hyperSlice( img, img.dimensionIndex( Axes.CHANNEL ), (int) detchannel - 1 );
			}
			else if ((img.dimensionIndex(Axes.CHANNEL) < 0) && img.numDimensions() < 5)
				  
				  detectionimg = img;
			
			else if (img.numDimensions() == 5) {
				 
				detectionimg = ImgPlusViews.hyperSlice( img, 2, (int) detchannel );
			}
			
			@SuppressWarnings({ "rawtypes", "unchecked" })
			final ImgPlus< FloatType > intimg = (ImgPlus) detectionimg;
		
			
			OneatCorrector oneatcorrector = corrector.create(intimg, model, trackmate, settings, displaySettings, mapsettings, logger, calibration, false );
			oneatcorrector.checkInput();
			oneatcorrector.process();
		}
		
		
		
		
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
	public Map<String, Object> getSettings(File oneatdivisionfile, File oneatapoptosisfile, Map<String, Object> trackmapsettings ) {
		final Map<String, Object> settings = new HashMap<>();

		// Get all the available tracker keys previously set
		for( Map.Entry<String, Object> trackkeys :  trackmapsettings.entrySet()) 
			
			
			settings.put(trackkeys.getKey(), trackkeys.getValue());
		
		settings.put(DIVISION_FILE, oneatdivisionfile);
		settings.put(APOPTOSIS_FILE, oneatapoptosisfile); 
		settings.put(KEY_TRACKLET_LENGTH, tracklet);
		settings.put(KEY_BREAK_LINKS, breaklinks);
		settings.put(KEY_CREATE_LINKS, createlinks);
		settings.put(KEY_USE_MARI_PRINCIPLE, mariprinciple);
		settings.put(KEY_TARGET_CHANNEL, detchannel);
		settings.put(KEY_LINKING_MAX_DISTANCE, linkdist);
		settings.put(KEY_GAP_CLOSING_MAX_FRAME_GAP, deltat);
		settings.put(KEY_PROB_THRESHOLD, probthreshold);
		settings.put(KEY_MARI_ANGLE, angle);
		return settings;
	}
	
	@Plugin( type = TrackMateActionFactory.class )
	public static class Factory < T extends RealType< T > & NativeType< T > > implements TrackMateActionFactory
	{

		@Override
		public String getInfoText()
		{
			return INFO_TEXT;
		}

		@Override
		public String getKey()
		{
			return KEY;
		}

		@Override
		public TrackMateAction create()
		{
			return new OneatExporterAction<T>();
		}

		@Override
		public ImageIcon getIcon()
		{
			return ONEAT_ICON;
		}

		@Override
		public String getName()
		{
			return NAME;
		}
	}

}
