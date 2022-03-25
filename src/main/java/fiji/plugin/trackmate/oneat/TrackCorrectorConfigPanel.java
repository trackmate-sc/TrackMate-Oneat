package fiji.plugin.trackmate.oneat;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;

public class TrackCorrectorConfigPanel extends ConfigurationPanel
{
	private static final long serialVersionUID = 1L;


	public TrackCorrectorConfigPanel(  )
	{
		setLayout( null );

		
		final JButton Loadcsvbutton = new JButton("Load Oneat detections From CSV");
		
		add(Loadcsvbutton);
		
		
			
		
	}

	@Override
	public void setSettings( final Map< String, Object > settings )
	{
		
	}

	@Override
	public Map< String, Object > getSettings()
	{
		final Map< String, Object > settings = new HashMap<>();
		return settings;
	}

	@Override
	public void clean()
	{}
}