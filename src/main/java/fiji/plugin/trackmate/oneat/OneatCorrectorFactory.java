package fiji.plugin.trackmate.oneat;

import static fiji.plugin.trackmate.io.IOUtils.readStringAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readIntegerAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readDoubleAttribute;
import static fiji.plugin.trackmate.io.IOUtils.writeAttribute;
import static fiji.plugin.trackmate.util.TMUtils.checkParameter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

import org.jdom2.Element;
import org.scijava.plugin.Plugin;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import net.imagej.ImgPlus;
import net.imglib2.type.numeric.integer.IntType;

@Plugin( type = TrackCorrectorFactory.class )
public class OneatCorrectorFactory implements TrackCorrectorFactory {

	
	public static final String DIVISION_FILE = "Division_File";
	
    
    public static final String APOPTOSIS_FILE = "Apoptosis_File";
    
    public static final String KEY_TRACKLET_LENGTH = "TRACKLET_LENGTH";
    
    public static final String KEY_TIME_GAP = "TIME_GAP";
    
    public static final String KEY_SIZE_RATIO = "SIZE_RATIO";

    public static final String KEY_LINKING_MAX_DISTANCE = "MAX_LINKING_DISTANCE";
    
    protected ImgPlus< IntType > img;
	
	/** A default value for the {@value #DEFAULT_KEY_TRACKLET_LENGTH} parameter. */
	public static final double DEFAULT_KEY_TRACKLET_LENGTH = 2;
	public static final double DEFAULT_KEY_TIME_GAP = 10;
	public static final double DEFAULT_SIZE_RATIO = 0.75;
	public static final double DEFAULT_LINKING_DISTANCE = 75;
	
	
	private String errorMessage;
	@Override
	public String getInfoText() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ImageIcon getIcon() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getKey() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TrackCorrector create(  ImgPlus< IntType > img,  Model model,
			Map<String, Object> settings) {
		
		
		  File oneatdivisionfile = (File) settings.get(DIVISION_FILE);
		  
		  File oneatapoptosisfile = (File) settings.get(APOPTOSIS_FILE);
		  
		  int mintrackletlength = (int) settings.get(KEY_TRACKLET_LENGTH);
		  
		  int timegap = (int) settings.get(KEY_TIME_GAP);
		  
		  double sizeratio = (double) settings.get(KEY_SIZE_RATIO);
		  
		  double linkingdistance = (double) settings.get(KEY_LINKING_MAX_DISTANCE);
				  
		
		  return new OneatCorrector(oneatdivisionfile, oneatapoptosisfile, img, mintrackletlength, timegap, sizeratio, linkingdistance, model, settings);
	}

	@Override
	public ConfigurationPanel getTrackCorrectorConfigurationPanel(Settings settings, Model model) {
		
		return new TrackCorrectorConfigPanel(settings, model);
	}

	@Override
	public boolean marshall(Map<String, Object> settings, Element element) {
		boolean ok = true;
		final StringBuilder str = new StringBuilder();
		ok = ok & writeAttribute( settings, element, DIVISION_FILE, String.class, str );
		ok = ok & writeAttribute( settings, element, APOPTOSIS_FILE, String.class, str );
		ok = ok & writeAttribute( settings, element, KEY_TRACKLET_LENGTH, Integer.class, str );
		ok = ok & writeAttribute( settings, element, KEY_TIME_GAP, Integer.class, str );
		ok = ok & writeAttribute( settings, element, KEY_SIZE_RATIO, Double.class, str );
		ok = ok & writeAttribute( settings, element, KEY_LINKING_MAX_DISTANCE, Double.class, str );
		return ok;
	}

	@Override
	public boolean unmarshall(Element element, Map<String, Object> settings) {
		settings.clear();
		final StringBuilder errorHolder = new StringBuilder();
		boolean ok = true;
		ok = ok & readStringAttribute( element, settings, DIVISION_FILE, errorHolder );
		ok = ok & readStringAttribute( element, settings, APOPTOSIS_FILE, errorHolder );
		ok = ok & readIntegerAttribute( element, settings, KEY_TRACKLET_LENGTH, errorHolder );
		ok = ok & readIntegerAttribute( element, settings, KEY_TIME_GAP, errorHolder );
		ok = ok & readDoubleAttribute( element, settings, KEY_SIZE_RATIO, errorHolder );
		ok = ok & readDoubleAttribute( element, settings, KEY_LINKING_MAX_DISTANCE, errorHolder );
		return ok;
	}

	@Override
	public String toString( final Map< String, Object > settings )
	{
		if ( !checkSettingsValidity( settings ) ) { return errorMessage; }

		final String oneatdivisionfile = ( String ) settings.get( DIVISION_FILE );
		
		final String oneatapoptosisfile = ( String ) settings.get( APOPTOSIS_FILE );
		
		final String mintrackletlength = (String) settings.get(KEY_TRACKLET_LENGTH);
		
		final String timegap = (String) settings.get(KEY_TIME_GAP);
		
		final String sizeratio = (String) settings.get(KEY_SIZE_RATIO);
		
		final String linkingdistance = (String) settings.get(KEY_LINKING_MAX_DISTANCE);
		
		final StringBuilder str = new StringBuilder();

		str.append( String.format( "  - oneat division detection file: %.1f\n", oneatdivisionfile));
		str.append( String.format( "  - oneat apoptosis detection file: %.1f\n", oneatapoptosisfile));
		str.append( String.format( "  - Min Tracklet Length: %.1f\n", mintrackletlength));
		str.append( String.format( "  - Time Gap between Oneat and TM division: %.1f\n", timegap));
		str.append( String.format( "  - Size ratio between mother and daughter cells: %.1f\n", sizeratio));
		str.append( String.format( "  - Max linking distance to consider for making links: %.1f\n", linkingdistance));
		
		return str.toString();
	}

	@Override
	public Map<String, Object> getDefaultSettings() {
		final Map< String, Object > sm = new HashMap<>( 3 );
		sm.put( KEY_TRACKLET_LENGTH, DEFAULT_KEY_TRACKLET_LENGTH );
		sm.put( KEY_TIME_GAP, DEFAULT_KEY_TIME_GAP );
		sm.put( KEY_SIZE_RATIO, DEFAULT_SIZE_RATIO );
		sm.put( KEY_LINKING_MAX_DISTANCE, DEFAULT_LINKING_DISTANCE );
		return sm;
	}

	@Override
	public boolean checkSettingsValidity(Map<String, Object> settings) {
		if ( null == settings )
		{
			errorMessage = "Settings map is null.\n";
			return false;
		}

		boolean ok = true;
		final StringBuilder str = new StringBuilder();

		ok = ok & checkParameter( settings, DIVISION_FILE, String.class, str );
		
		ok = ok & checkParameter( settings, APOPTOSIS_FILE, String.class, str );
		
		ok = ok & checkParameter( settings, KEY_TRACKLET_LENGTH, Integer.class, str );
		
		ok = ok & checkParameter( settings, KEY_TIME_GAP, Integer.class, str );
		
		ok = ok & checkParameter( settings, KEY_SIZE_RATIO, Double.class, str );
		
		ok = ok & checkParameter( settings, KEY_LINKING_MAX_DISTANCE, Double.class, str );
		

		if ( !ok )
		{
			errorMessage = str.toString();
		}
		return ok;
	}

	@Override
	public String getErrorMessage() {
		return errorMessage;
	}

	@Override
	public OneatCorrectorFactory copy() {
		return new OneatCorrectorFactory();
	}

	

}
