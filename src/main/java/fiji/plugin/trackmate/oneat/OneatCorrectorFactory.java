package fiji.plugin.trackmate.oneat;

import java.io.File;
import java.util.Map;

import javax.swing.ImageIcon;

import org.jdom2.Element;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;

public class OneatCorrectorFactory implements TrackCorrectorFactory {

	
	public static final String DivisionFile = "Division_File";
	
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
	public TrackCorrector create(SpotCollection spots, DivisionSpotCollection specialspots, Settings settings, Model model,
			Map<String, Object> mapsettings) {
		
		
		  File oneatfile = (File) mapsettings.get(DivisionFile);
		
		  return new OneatCorrector(oneatfile, settings, model);
	}

	@Override
	public ConfigurationPanel getTrackCorrectorConfigurationPanel(Settings settings, Model model) {
		
		return new TrackCorrectorConfigPanel(settings, model);
	}

	@Override
	public boolean marshall(Map<String, Object> settings, Element element) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean unmarshall(Element element, Map<String, Object> settings) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String toString(Map<String, Object> sm) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getDefaultSettings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean checkSettingsValidity(Map<String, Object> settings) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getErrorMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TrackCorrectorFactory copy() {
		// TODO Auto-generated method stub
		return null;
	}

	

}
