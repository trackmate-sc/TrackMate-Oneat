
package fiji.plugin.trackmate.oneat;

import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;

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
import javax.swing.JFormattedTextField;
import javax.swing.filechooser.FileFilter;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.DIVISION_FILE;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.APOPTOSIS_FILE;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_TRACKLET_LENGTH;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_TIME_GAP;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_SIZE_RATIO;

public class TrackCorrectorConfigPanel extends ConfigurationPanel
{
	private static final long serialVersionUID = 1L;
    private static File oneatdivisionfile;
    private static File oneatapoptosisfile;

    private JButton Loaddivisioncsvbutton;
    private JButton Loadapoptosiscsvbutton;
    private  JFormattedTextField MinTracklet;
    private  JFormattedTextField TimeGap;
    private  JFormattedTextField MotherDaughterSizeRatio;
	
	
	public TrackCorrectorConfigPanel( final Settings settings, final Model model )
	{
		setLayout( null );

		
		Loaddivisioncsvbutton = new JButton("Load Oneat division detections From CSV");
		add(Loaddivisioncsvbutton);
		
		Loadapoptosiscsvbutton = new JButton("Load Oneat apoptosis detections From CSV");
		add(Loadapoptosiscsvbutton);
		
		MinTracklet = new JFormattedTextField(Integer.valueOf(2));
		add(MinTracklet);
		
		TimeGap = new JFormattedTextField(Integer.valueOf(2));
		add(TimeGap);
		
		MotherDaughterSizeRatio = new JFormattedTextField(Double.valueOf(4));
		add(MotherDaughterSizeRatio);
		
		Loaddivisioncsvbutton.addActionListener(new ActionListener() {

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
				csvfile.setDialogTitle("Division Detection file");
				csvfile.setFileSelectionMode(JFileChooser.FILES_ONLY);
				csvfile.setFileFilter(csvfilter);

				if (csvfile.showOpenDialog(getParent()) == JFileChooser.APPROVE_OPTION) 

					oneatdivisionfile = new File(csvfile.getSelectedFile().getPath());
			}
		
		
	});
		
		Loadapoptosiscsvbutton.addActionListener(new ActionListener() {

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
				csvfile.setDialogTitle("Apoptosis Detection file");
				csvfile.setFileSelectionMode(JFileChooser.FILES_ONLY);
				csvfile.setFileFilter(csvfilter);

				if (csvfile.showOpenDialog(getParent()) == JFileChooser.APPROVE_OPTION) 

					oneatapoptosisfile = new File(csvfile.getSelectedFile().getPath());
			}
		
		
	});
		
		
	}

	@Override
	public void setSettings( final Map< String, Object > settings )
	{
		MinTracklet.setValue(settings.get(KEY_TRACKLET_LENGTH));
		TimeGap.setValue(settings.get(KEY_TIME_GAP));
		MotherDaughterSizeRatio.setValue(settings.get(KEY_SIZE_RATIO));
		
	}

	@Override
	public Map< String, Object > getSettings()
	{
		final Map< String, Object > settings = new HashMap<>();
		
		settings.put( DIVISION_FILE, oneatdivisionfile );
		settings.put( APOPTOSIS_FILE, oneatapoptosisfile );
		settings.put(KEY_TRACKLET_LENGTH, ((Number) MinTracklet.getValue()).doubleValue());
		settings.put(KEY_TIME_GAP, ((Number) TimeGap.getValue()).doubleValue());
		settings.put(KEY_SIZE_RATIO, ((Number) MotherDaughterSizeRatio.getValue()).doubleValue());
		
		return settings;
	}

	@Override
	public void clean()
	{}
}
