
package fiji.plugin.trackmate.oneat;

import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileFilter;

import org.scijava.prefs.PrefService;

import fiji.plugin.trackmate.LoadTrackMatePlugIn;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import fiji.plugin.trackmate.util.TMUtils;
import ij.ImageJ;
import ij.plugin.PlugIn;

import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.DIVISION_FILE;
import static fiji.plugin.trackmate.gui.Fonts.SMALL_FONT;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.APOPTOSIS_FILE;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_TRACKLET_LENGTH;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_TIME_GAP;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_SIZE_RATIO;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_CREATE_LINKS;
import static fiji.plugin.trackmate.oneat.OneatCorrectorFactory.KEY_BREAK_LINKS;

public class TrackCorrectorPanel extends JPanel 
{
	private static final long serialVersionUID = 1L;
    private static File oneatdivisionfile;
    private static File oneatapoptosisfile;

    private JButton Loaddivisioncsvbutton;
    private JButton Loadapoptosiscsvbutton;
    private  JFormattedTextField MinTracklet;
    private  JFormattedTextField TimeGap;
    private  JFormattedTextField MotherDaughterSizeRatio;
    
    private JCheckBox CreateNewLinks;
    private JCheckBox BreakCurrentLinks;
	
	public TrackCorrectorPanel( final Settings settings, final Model model )
	{
		
		final GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 144, 0, 32 };
		gridBagLayout.rowHeights = new int[] { 0, 0, 84, 0, 27, 0, 0, 0, 0, 37, 23 };
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0, 0.0 };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0 };
		setLayout( gridBagLayout );
		
		final JLabel lblOneatModel = new JLabel( "Load Oneat division detections:" );
		lblOneatModel.setFont( SMALL_FONT );
		final GridBagConstraints gbcLblOneatModel = new GridBagConstraints();
		gbcLblOneatModel.anchor = GridBagConstraints.EAST;
		gbcLblOneatModel.insets = new Insets( 0, 5, 5, 5 );
		gbcLblOneatModel.gridx = 0;
		gbcLblOneatModel.gridy = 2;
		add( lblOneatModel, gbcLblOneatModel );
		
		
		Loaddivisioncsvbutton = new JButton("Load Oneat division detections From CSV");
		Loaddivisioncsvbutton.setHorizontalTextPosition( SwingConstants.LEFT );
		Loaddivisioncsvbutton.setFont( SMALL_FONT );
		final GridBagConstraints gbcLoadDivision = new GridBagConstraints();
		gbcLoadDivision.anchor = GridBagConstraints.EAST;
		gbcLoadDivision.gridwidth = 2;
		gbcLoadDivision.insets = new Insets( 0, 5, 5, 5 );
		gbcLoadDivision.gridx = 0;
		gbcLoadDivision.gridy = 4;
		add( Loaddivisioncsvbutton, gbcLoadDivision );
		
		
		Loadapoptosiscsvbutton = new JButton("Load Oneat apoptosis detections From CSV");
		Loaddivisioncsvbutton.setHorizontalTextPosition( SwingConstants.LEFT );
		Loaddivisioncsvbutton.setFont( SMALL_FONT );
		final GridBagConstraints gbcLoadApoptosis = new GridBagConstraints();
		gbcLoadApoptosis.anchor = GridBagConstraints.EAST;
		gbcLoadApoptosis.gridwidth = 2;
		gbcLoadApoptosis.insets = new Insets( 0, 5, 5, 5 );
		gbcLoadApoptosis.gridx = 0;
		gbcLoadApoptosis.gridy = 6;
		add( Loadapoptosiscsvbutton, gbcLoadApoptosis );		
		
		
		MinTracklet = new JFormattedTextField(Integer.valueOf(2));
		MinTracklet.setFont( new Font( "Arial", Font.PLAIN, 10 ) );
		MinTracklet.setColumns( 15 );
		final GridBagConstraints gbc_MinTracklet = new GridBagConstraints();
		gbc_MinTracklet.gridwidth = 3;
		gbc_MinTracklet.insets = new Insets( 0, 5, 5, 5 );
		gbc_MinTracklet.fill = GridBagConstraints.BOTH;
		gbc_MinTracklet.gridx = 0;
		gbc_MinTracklet.gridy = 8;
		add( MinTracklet, gbc_MinTracklet );
		
		
		TimeGap = new JFormattedTextField(Integer.valueOf(2));
		TimeGap.setFont( new Font( "Arial", Font.PLAIN, 10 ) );
		TimeGap.setColumns( 15 );
		final GridBagConstraints gbcTimeGap = new GridBagConstraints();
		gbcTimeGap.gridwidth = 3;
		gbcTimeGap.insets = new Insets( 0, 5, 5, 5 );
		gbcTimeGap.fill = GridBagConstraints.BOTH;
		gbcTimeGap.gridx = 0;
		gbcTimeGap.gridy = 10;
		add( MinTracklet, gbcTimeGap );
		
		MotherDaughterSizeRatio = new JFormattedTextField(Double.valueOf(4));
		MotherDaughterSizeRatio.setFont( new Font( "Arial", Font.PLAIN, 10 ) );
		MotherDaughterSizeRatio.setColumns( 15 );
		final GridBagConstraints gbc_MotherDaughterSizeRatio = new GridBagConstraints();
		gbc_MotherDaughterSizeRatio.gridwidth = 3;
		gbc_MotherDaughterSizeRatio.insets = new Insets( 0, 5, 5, 5 );
		gbc_MotherDaughterSizeRatio.fill = GridBagConstraints.BOTH;
		gbc_MotherDaughterSizeRatio.gridx = 0;
		gbc_MotherDaughterSizeRatio.gridy = 12;
		add( MotherDaughterSizeRatio, gbc_MotherDaughterSizeRatio );
		
		CreateNewLinks = new JCheckBox(" Create new mitosis events ");
		CreateNewLinks.setHorizontalTextPosition( SwingConstants.LEFT );
		CreateNewLinks.setFont( SMALL_FONT );
		final GridBagConstraints gbcChckbxCreateNewLinks = new GridBagConstraints();
		gbcChckbxCreateNewLinks.anchor = GridBagConstraints.EAST;
		gbcChckbxCreateNewLinks.gridwidth = 2;
		gbcChckbxCreateNewLinks.insets = new Insets( 0, 0, 5, 5 );
		gbcChckbxCreateNewLinks.gridx = 0;
		gbcChckbxCreateNewLinks.gridy = 14;
		add( CreateNewLinks, gbcChckbxCreateNewLinks );
		
		
		BreakCurrentLinks = new JCheckBox(" Break current mitosis events ");
		BreakCurrentLinks.setHorizontalTextPosition( SwingConstants.LEFT );
		BreakCurrentLinks.setFont( SMALL_FONT );
		final GridBagConstraints gbcChckbxBreakCurrentLinks = new GridBagConstraints();
		gbcChckbxBreakCurrentLinks.anchor = GridBagConstraints.EAST;
		gbcChckbxBreakCurrentLinks.gridwidth = 2;
		gbcChckbxBreakCurrentLinks.insets = new Insets( 0, 0, 5, 5 );
		gbcChckbxBreakCurrentLinks.gridx = 0;
		gbcChckbxBreakCurrentLinks.gridy = 16;
		add( CreateNewLinks, gbcChckbxBreakCurrentLinks );
		
	
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

	
	public void setSettings( final Map< String, Object > settings )
	{
		MinTracklet.setValue(settings.get(KEY_TRACKLET_LENGTH));
		TimeGap.setValue(settings.get(KEY_TIME_GAP));
		MotherDaughterSizeRatio.setValue(settings.get(KEY_SIZE_RATIO));
		CreateNewLinks.setSelected( ( boolean ) settings.get( KEY_CREATE_LINKS ) );
		BreakCurrentLinks.setSelected( ( boolean ) settings.get( KEY_BREAK_LINKS ) );
	}

	
	public Map< String, Object > getSettings()
	{
		final Map< String, Object > settings = new HashMap<>();
		
		settings.put( DIVISION_FILE, oneatdivisionfile );
		settings.put( APOPTOSIS_FILE, oneatapoptosisfile );
		settings.put(KEY_TRACKLET_LENGTH, ((Number) MinTracklet.getValue()).doubleValue());
		settings.put(KEY_TIME_GAP, ((Number) TimeGap.getValue()).doubleValue());
		settings.put(KEY_SIZE_RATIO, ((Number) MotherDaughterSizeRatio.getValue()).doubleValue());
		settings.put(KEY_BREAK_LINKS, BreakCurrentLinks.isSelected());
		settings.put(KEY_CREATE_LINKS, CreateNewLinks.isSelected());
		
		return settings;
	}




	
	
	
}
