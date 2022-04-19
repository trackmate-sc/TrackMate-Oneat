package fiji.plugin.trackmate.oneat;

import static fiji.plugin.trackmate.io.IOUtils.readStringAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readIntegerAttribute;
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
    
    protected ImgPlus< IntType > img;
	
	public static final int DEFAULT_TRACKLET_LENGTH = 2; 
	
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
	public TrackCorrector create(SpotCollection spots, SpotCollection specialspots, ImgPlus< IntType > img, Settings settings, Model model,
			Map<String, Object> mapsettings) {
		
		
		  File oneatdivisionfile = (File) mapsettings.get(DIVISION_FILE);
		  
		  File oneatapoptosisfile = (File) mapsettings.get(APOPTOSIS_FILE);
		
		  return new OneatCorrector(oneatdivisionfile, oneatapoptosisfile, img, settings, model);
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
		ok = ok & writeAttribute( settings, element, TRACKLET_LENGTH, Integer.class, str );
		return ok;
	}

	@Override
	public boolean unmarshall(Element element, Map<String, Object> settings) {
		settings.clear();
		final StringBuilder errorHolder = new StringBuilder();
		boolean ok = true;
		ok = ok & readStringAttribute( element, settings, DIVISION_FILE, errorHolder );
		ok = ok & readStringAttribute( element, settings, APOPTOSIS_FILE, errorHolder );
		ok = ok & readIntegerAttribute( element, settings, TRACKLET_LENGTH, errorHolder );
		return ok;
	}

	@Override
	public String toString( final Map< String, Object > settings )
	{
		if ( !checkSettingsValidity( settings ) ) { return errorMessage; }

		final String oneatdivisionfile = ( String ) settings.get( DIVISION_FILE );
		
		final String oneatapoptosisfile = ( String ) settings.get( APOPTOSIS_FILE );
		
		
		final StringBuilder str = new StringBuilder();

		str.append( String.format( "  - oneat division detection file: %.1f\n", oneatdivisionfile));
		str.append( String.format( "  - oneat apoptosis detection file: %.1f\n", oneatapoptosisfile));
		
		
		return str.toString();
	}

	@Override
	public Map<String, Object> getDefaultSettings() {
		final Map< String, Object > sm = new HashMap<>( 1 );
		sm.put( TRACKLET_LENGTH, DEFAULT_TRACKLET_LENGTH );
		
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
		
		ok = ok & checkParameter( settings, TRACKLET_LENGTH, Integer.class, str );

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
