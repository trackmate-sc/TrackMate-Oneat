package fiji.plugin.trackmate.action.oneat;

import java.awt.Frame;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.APOPTOSIS_FILE;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.DIVISION_FILE;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_BREAK_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_CREATE_LINKS;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_TRACKLET_LENGTH;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;

import static fiji.plugin.trackmate.gui.Icons.TRACKMATE_ICON;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_MAX_DISTANCE;

import org.scijava.plugin.Plugin;
import static fiji.plugin.trackmate.gui.Icons.CAMERA_ICON;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.action.AbstractTMAction;
import fiji.plugin.trackmate.action.CaptureOverlayAction;
import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.action.TrackMateActionFactory;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.util.TMUtils;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class  OneatExporterAction < T extends RealType< T > & NativeType< T > > extends AbstractTMAction {

	
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
	
	private static int detchannel = 1;
	
	
	private double linkdist = 50;
	
	private int deltat = 10;
	
	private int tracklet = 2;
	
	private boolean breaklinks = true;
	
	private boolean createlinks = false;
	
	@Override
	public void execute(TrackMate trackmate, SelectionModel selectionModel, DisplaySettings displaySettings,
			Frame gui) {
		
		Settings settings = trackmate.getSettings();
	
		Map<String, Object> trackmapsettings = settings.trackerSettings;
		
		Map<String, Object> detectorsettings = settings.detectorSettings;
		Model model = trackmate.getModel();
		final ImgPlus<T> img = TMUtils.rawWraps( settings.imp );
		final double[] calibration = new double[ 3 ];
		calibration[ 0 ] = settings.dx;
		calibration[ 1 ] = settings.dy;
		calibration[ 2 ] = settings.dz;
		if (gui!=null)
		{
			
			
			final OneatExporterPanel panel = new OneatExporterPanel(settings,trackmapsettings,detectorsettings, model);
			final int userInput = JOptionPane.showConfirmDialog(gui, panel, "Launch Oneat track corrector", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, TRACKMATE_ICON);
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
			Map<String, Object> mapsettings = getSettings(oneatdivisionfile,oneatapotosisfile,trackmapsettings);
			OneatCorrectorFactory corrector = new OneatCorrectorFactory();
			ImgPlus <T> detectionimg =  img;
			if (img.dimensionIndex(Axes.CHANNEL) > 0) {
			     detectionimg = ImgPlusViews.hyperSlice( img, img.dimensionIndex( Axes.CHANNEL ), (int) detchannel - 1 );
			     System.out.println(detchannel + " " + linkdist);
			}
			else if ((img.dimensionIndex(Axes.CHANNEL) < 0) && img.numDimensions() < 5)
				  
				  detectionimg = img;
			
			else if (img.numDimensions() == 5) {
				 
				detectionimg = ImgPlusViews.hyperSlice( img, 2, (int) detchannel );
			}
			AxisType[] axes = new AxisType[] {
						Axes.X,
						Axes.Y,
						Axes.Z,
						Axes.TIME };
			final ImgPlus< IntType > intimg = new ImgPlus<IntType>( Util.getArrayOrCellImgFactory( detectionimg, new IntType() ).create( detectionimg ), "lblimg", axes);
			LoopBuilder
						.setImages( Views.zeroMin( detectionimg ), intimg )
						.multiThreaded( false )
						.forEachPixel( ( i, o ) -> o.setReal( i.getRealDouble() ) );
			
			OneatCorrector oneatcorrector = corrector.create(intimg, model, mapsettings, logger, calibration );
			oneatcorrector.checkInput();
			oneatcorrector.process();
		}
		
		
		
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
		settings.put(KEY_TARGET_CHANNEL, detchannel);
		settings.put(KEY_SPLITTING_MAX_DISTANCE, linkdist);
		settings.put(KEY_GAP_CLOSING_MAX_FRAME_GAP, deltat);
		
		
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
			return CAMERA_ICON;
		}

		@Override
		public String getName()
		{
			return NAME;
		}
	}

}
