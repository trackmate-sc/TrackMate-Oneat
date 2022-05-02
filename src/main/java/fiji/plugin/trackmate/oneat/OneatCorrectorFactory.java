package fiji.plugin.trackmate.oneat;

import static fiji.plugin.trackmate.io.IOUtils.readStringAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readIntegerAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readDoubleAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readBooleanAttribute;
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
import fiji.plugin.trackmate.trackcorrection.TrackCorrector;
import fiji.plugin.trackmate.trackcorrection.TrackCorrectorFactory;
import fiji.plugin.trackmate.trackcorrection.oneat.OneatCorrector;
import fiji.plugin.trackmate.trackcorrection.oneat.TrackCorrectorConfigPanel;
import net.imagej.ImgPlus;
import net.imglib2.type.numeric.integer.IntType;

@Plugin( type = TrackCorrectorFactory.class, visible = true )
public class OneatCorrectorFactory implements TrackCorrectorFactory {

	
	public static final String DIVISION_FILE = "Division_File";
	
    
    public static final String APOPTOSIS_FILE = "Apoptosis_File";
    
    public static final String KEY_TRACKLET_LENGTH = "TRACKLET_LENGTH";
    
    public static final String KEY_TIME_GAP = "TIME_GAP";
    
    public static final String KEY_SIZE_RATIO = "SIZE_RATIO";

    public static final String KEY_LINKING_MAX_DISTANCE = "MAX_LINKING_DISTANCE";
    
    public static final String KEY_CREATE_LINKS = "CREATE_LINKS";
    
    public static final String KEY_BREAK_LINKS = "BREAK_LINKS";
    
    protected ImgPlus< IntType > img;
	
	/** A default value for the {@value #DEFAULT_KEY_TRACKLET_LENGTH} parameter. */
	public static final double DEFAULT_KEY_TRACKLET_LENGTH = 2;
	public static final double DEFAULT_KEY_TIME_GAP = 10;
	public static final double DEFAULT_SIZE_RATIO = 0.75;
	public static final double DEFAULT_LINKING_DISTANCE = 75;
	public static final boolean DEFAULT_CREATE_LINKS = true;
	public static final boolean DEFAULT_BREAK_LINKS = false;
	
	public static final String THIS_TRACK_CORRECTOR = "Oneat_Corrector";

	public static final String THIS_NAME = "Oneat Corrector";

	public static final String THIS_INFO_TEXT = "<html>"
			+ "This is the corrector based on oneat, a CNN + LSTM based action classification network <br>"
			+ " </html>";

	@Override
	public String getKey()
	{
		return THIS_TRACK_CORRECTOR;
	}

	@Override
	public String getName()
	{
		return THIS_NAME;
	}

	@Override
	public String getInfoText()
	{
		return THIS_INFO_TEXT;
	}
	
	private String errorMessage;
	

	@Override
	public ImageIcon getIcon() {
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
				  
		  final boolean createlinks = ( boolean ) settings.get( KEY_CREATE_LINKS );
		  
		  final boolean breaklinks = ( boolean ) settings.get( KEY_BREAK_LINKS );
		
		  return new OneatCorrector(oneatdivisionfile, oneatapoptosisfile, img, mintrackletlength, timegap, sizeratio, linkingdistance, createlinks, breaklinks, model, settings);
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
		ok = ok & writeAttribute( settings, element, KEY_CREATE_LINKS, Boolean.class, str );
		ok = ok & writeAttribute( settings, element, KEY_BREAK_LINKS, Boolean.class, str );
		
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
		ok = ok & readBooleanAttribute( element, settings, KEY_CREATE_LINKS, errorHolder );
		ok = ok & readBooleanAttribute( element, settings, KEY_BREAK_LINKS, errorHolder );
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
		
		final String createlinks = (String) settings.get(KEY_CREATE_LINKS);
		
		final String breaklinks  = (String) settings.get(KEY_BREAK_LINKS);
		
		final StringBuilder str = new StringBuilder();

		str.append( String.format( "  - oneat division detection file: %.1f\n", oneatdivisionfile));
		str.append( String.format( "  - oneat apoptosis detection file: %.1f\n", oneatapoptosisfile));
		str.append( String.format( "  - Min Tracklet Length: %.1f\n", mintrackletlength));
		str.append( String.format( "  - Time Gap between Oneat and TM division: %.1f\n", timegap));
		str.append( String.format( "  - Size ratio between mother and daughter cells: %.1f\n", sizeratio));
		str.append( String.format( "  - Max linking distance to consider for making links: %.1f\n", linkingdistance));
		str.append( String.format( "  -Create new links: %.1f\n", createlinks));
		str.append( String.format( "  - Breal links: %.1f\n", breaklinks));
		
		
		return str.toString();
	}

	@Override
	public Map<String, Object> getDefaultSettings() {
		final Map< String, Object > sm = new HashMap<>( 6 );
		sm.put( KEY_TRACKLET_LENGTH, DEFAULT_KEY_TRACKLET_LENGTH );
		sm.put( KEY_TIME_GAP, DEFAULT_KEY_TIME_GAP );
		sm.put( KEY_SIZE_RATIO, DEFAULT_SIZE_RATIO );
		sm.put( KEY_LINKING_MAX_DISTANCE, DEFAULT_LINKING_DISTANCE );
		sm.put(KEY_BREAK_LINKS, DEFAULT_BREAK_LINKS);
		sm.put(KEY_CREATE_LINKS, DEFAULT_CREATE_LINKS);
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
		
        ok = ok & checkParameter( settings, KEY_CREATE_LINKS, Boolean.class, str );
		
		ok = ok & checkParameter( settings, KEY_BREAK_LINKS, Boolean.class, str );
		

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
