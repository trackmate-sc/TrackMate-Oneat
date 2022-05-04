package fiji.plugin.trackmate.oneat;

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
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_SIZE_RATIO;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_TIME_GAP;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_TRACKLET_LENGTH;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_LINKING_MAX_DISTANCE;
import static fiji.plugin.trackmate.action.oneat.OneatCorrectorFactory.KEY_TARGET_CHANNEL;

import static fiji.plugin.trackmate.gui.Icons.TRACKMATE_ICON;
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
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class  OneatExporterAction < T extends RealType< T > & NativeType< T > > extends AbstractTMAction {

	
	public static final String INFO_TEXT = "<html>"
			+ "This action initiates Oneat track correction for the tracking results. "
			+  "<p> "
			+ "Oneat is a keras based library in python by Varun Kapoor. "
			+ "It provides csv files of event locations such as mitosis/apoptosis "
			+ "using the csv file of event locations the tracks are corrected "
			+ "and a new trackscheme is generated with corrected tracks. "
			+ "</html>";

	public static final String KEY = "LAUNCH_ONEAT";

	public static final String NAME = "Launch Oneat track corrector";
	
	private static int detchannel = -1;
	
	private double sizeratio = -1;
	
	private double linkdist = -1;
	
	private int deltat = -1;
	
	private int tracklet = -1;
	
	private boolean createlinks = false;
	
	private boolean breaklinks = true;
	
	@Override
	public void execute(TrackMate trackmate, SelectionModel selectionModel, DisplaySettings displaySettings,
			Frame gui) {
		
		Settings settings = trackmate.getSettings();
		Model model = trackmate.getModel();
		final ImgPlus<T> img = TMUtils.rawWraps( settings.imp );
		
		if (gui!=null)
		{
			
			
			final OneatExporterPanel panel = new OneatExporterPanel(settings, model, detchannel,  sizeratio,  linkdist,  deltat,
					 tracklet,  createlinks,  breaklinks);
			final int userInput = JOptionPane.showConfirmDialog(gui, panel, "Launch Oneat track corrector", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, TRACKMATE_ICON);
			if ( userInput != JOptionPane.OK_OPTION )
				return;
			
			File oneatdivisionfile = panel.getMistosisFile();
			File oneatapotosisfile = panel.getApoptosisFile();
			int tracklet = panel.getMinTracklet();
			int deltat = panel.getTimeGap();
			double sizeratio = panel.getSizeRatio();
			boolean breaklinks = panel.getBreakLinks();
			boolean createlinks = panel.getCreateLinks();
			int detchannel = panel.getDetectionChannel();
			double linkdist = panel.getLinkDist();
			Map<String, Object> mapsettings = getSettings(oneatdivisionfile,oneatapotosisfile,tracklet,deltat,sizeratio,breaklinks,createlinks,detchannel,linkdist );
			OneatCorrectorFactory<T> corrector = new OneatCorrectorFactory<T>();
			corrector.create(img, model, mapsettings);
		}
		
		
		
	}
	
	public Map<String, Object> getSettings(File oneatdivisionfile, File oneatapoptosisfile, int tracklet, int deltat, double sizeratio, boolean breaklinks, boolean createlinks, int detchannel, double linkdist  ) {
		final Map<String, Object> settings = new HashMap<>();

		settings.put(DIVISION_FILE, oneatdivisionfile);
		settings.put(APOPTOSIS_FILE, oneatapoptosisfile);
		settings.put(KEY_TRACKLET_LENGTH, tracklet);
		settings.put(KEY_TIME_GAP, deltat);
		settings.put(KEY_SIZE_RATIO, sizeratio);
		settings.put(KEY_BREAK_LINKS, breaklinks);
		settings.put(KEY_CREATE_LINKS, createlinks);
		settings.put(KEY_TARGET_CHANNEL, detchannel);
		settings.put(KEY_LINKING_MAX_DISTANCE, linkdist);

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
