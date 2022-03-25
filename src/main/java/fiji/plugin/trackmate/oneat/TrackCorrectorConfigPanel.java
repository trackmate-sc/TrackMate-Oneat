package fiji.plugin.trackmate.oneat;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import net.imglib2.Point;

public class TrackCorrectorConfigPanel extends ConfigurationPanel
{
	private static final long serialVersionUID = 1L;


	public TrackCorrectorConfigPanel( final Settings settings, final Model model )
	{
		setLayout( null );

		
		final JButton Loadcsvbutton = new JButton("Load Oneat detections From CSV");
		
		add(Loadcsvbutton);
		Loadcsvbutton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent a) {

				JFileChooser csvfile = new JFileChooser();
				FileFilter csvfilter = new FileFilter() {
					// Override accept method
					public boolean accept(File file) {

						// if the file extension is .log return true, else false
						if (file.getName().endsWith(".csv")) {
							return true;
						}
						return false;
					}

					@Override
					public String getDescription() {

						return null;
					}
				};
				
				csvfile.setCurrentDirectory(new File(settings.imp.getOriginalFileInfo().directory));
				csvfile.setDialogTitle(" Cell CSV file");
				csvfile.setFileSelectionMode(JFileChooser.FILES_ONLY);
				csvfile.setFileFilter(csvfilter);
				int count = 0;

				if (csvfile.showOpenDialog(getParent()) == JFileChooser.APPROVE_OPTION) {

					File oneatfile = new File(csvfile.getSelectedFile().getPath());

					

		
				}
			}
		
		
	});
		
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