/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2022 - 2023 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.action.oneat;

import static fiji.plugin.trackmate.io.IOUtils.readStringAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readIntegerAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readDoubleAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readBooleanAttribute;
import static fiji.plugin.trackmate.io.IOUtils.writeAttribute;
import static fiji.plugin.trackmate.util.TMUtils.checkParameter;

import java.io.File;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

import org.jdom2.Element;
import org.scijava.plugin.Plugin;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import net.imagej.ImgPlus;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_MAX_DISTANCE;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
@Plugin( type = TrackCorrectorFactory.class, visible = true )
public  class  OneatCorrectorFactory implements TrackCorrectorFactory  {

	
	public static final String DIVISION_FILE = "MITOSIS_FILE";
    public static final String APOPTOSIS_FILE = "APOPTOSIS_FILE";
    public static final String KEY_TRACKLET_LENGTH = "TRACKLET_LENGTH";
    public static final String KEY_PROB_THRESHOLD = "DETECTION_THRESHOLD";
    public static final String KEY_CREATE_LINKS = "CREATE_LINKS";
    public static final String KEY_BREAK_LINKS = "BREAK_LINKS";
    public static final String KEY_USE_MARI_PRINCIPLE = "USE_MARI_PRINCIPLE";
    public static final String KEY_MARI_ANGLE = "MARI_ANGLE";
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
	public   OneatCorrector  create(  ImgPlus<FloatType> img,  Model model, TrackMate trackmate, Settings modelsettings, DisplaySettings displaysettings,
			Map<String, Object> settings, final Logger logger, double[] calibration, boolean addDisplay) {
		
		
		  
		  File oneatdivisionfile = (File) settings.get(DIVISION_FILE);
		  
		  File oneatapoptosisfile = (File) settings.get(APOPTOSIS_FILE);
		  
	
		  return new OneatCorrector(oneatdivisionfile, oneatapoptosisfile, img,  model, trackmate, modelsettings, displaysettings, calibration, settings, logger, addDisplay);
	}

	@Override
	public JPanel getTrackCorrectorConfigurationPanel(Settings settings, Map<String, Object> trackmapsettings, 
			Map<String, Object> detectorsettings, Model model) {
		
		return new OneatExporterPanel(settings, trackmapsettings, detectorsettings, model);
	}

	@Override
	public boolean marshall(Map<String, Object> settings, Element element) {
		boolean ok = true;
		final StringBuilder str = new StringBuilder();
		ok = ok & writeAttribute( settings, element, DIVISION_FILE, String.class, str );
		ok = ok & writeAttribute( settings, element, APOPTOSIS_FILE, String.class, str );
		ok = ok & writeAttribute( settings, element, KEY_TRACKLET_LENGTH, Integer.class, str );
		ok = ok & writeAttribute( settings, element, KEY_SPLITTING_MAX_DISTANCE, Double.class, str );
		ok = ok & writeAttribute( settings, element, KEY_CREATE_LINKS, Boolean.class, str );
		ok = ok & writeAttribute( settings, element, KEY_BREAK_LINKS, Boolean.class, str );
		ok = ok & writeAttribute( settings, element, KEY_TARGET_CHANNEL, Integer.class, str );
		ok = ok & writeAttribute( settings, element, KEY_USE_MARI_PRINCIPLE, Boolean.class, str );
		ok = ok & writeAttribute( settings, element, KEY_MARI_ANGLE, Double.class, str );
		ok = ok & writeAttribute( settings, element, KEY_PROB_THRESHOLD, Double.class, str );
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
		ok = ok & readDoubleAttribute( element, settings, KEY_SPLITTING_MAX_DISTANCE, errorHolder );
		ok = ok & readBooleanAttribute( element, settings, KEY_CREATE_LINKS, errorHolder );
		ok = ok & readBooleanAttribute( element, settings, KEY_BREAK_LINKS, errorHolder );
		ok = ok & readBooleanAttribute( element, settings, KEY_TARGET_CHANNEL, errorHolder );
		ok = ok & readDoubleAttribute( element, settings, KEY_MARI_ANGLE, errorHolder );
		ok = ok & readBooleanAttribute( element, settings, KEY_USE_MARI_PRINCIPLE, errorHolder );
		ok = ok & readDoubleAttribute( element, settings, KEY_PROB_THRESHOLD, errorHolder );
		return ok;
	}

	@Override
	public String toString( final Map< String, Object > settings )
	{
		if ( !checkSettingsValidity( settings ) ) { return errorMessage; }

		final String oneatdivisionfile = ( String ) settings.get( DIVISION_FILE );
		
		final String oneatapoptosisfile = ( String ) settings.get( APOPTOSIS_FILE );
		
		final String mintrackletlength = (String) settings.get(KEY_TRACKLET_LENGTH);
		
		final String linkingdistance = (String) settings.get(KEY_SPLITTING_MAX_DISTANCE);
		
		final String createlinks = (String) settings.get(KEY_CREATE_LINKS);
		
		final String breaklinks  = (String) settings.get(KEY_BREAK_LINKS);
		
		final String detectionchannel = (String) settings.get(KEY_TARGET_CHANNEL);
		
		final String mariprinciple = (String) settings.get(KEY_USE_MARI_PRINCIPLE);
		
		final String mariangle = (String) settings.get(KEY_MARI_ANGLE);
		
		final String probthreshold = (String) settings.get(KEY_PROB_THRESHOLD);
		
		final StringBuilder str = new StringBuilder();

		str.append( String.format( "  - oneat division detection file: %.1f\n", oneatdivisionfile));
		str.append( String.format( "  - oneat apoptosis detection file: %.1f\n", oneatapoptosisfile));
		str.append( String.format( "  - Min Tracklet Length: %.1f\n", mintrackletlength));
		str.append( String.format( "  - Max linking distance to consider for making links: %.1f\n", linkingdistance));
		str.append( String.format( "  -Create new links: %.1f\n", createlinks));
		str.append( String.format( "  - Break links: %.1f\n", breaklinks));
		str.append( String.format( "  - Detection Channel: %.1f\n", detectionchannel));
		str.append( String.format( "  - Use Mari Principle: %.1f\n", mariprinciple));
		str.append( String.format( "  - Use Mari Angle: %.1f\n", mariangle));
		str.append( String.format( "  - Probthreshold: %.1f\n", probthreshold));
		
		return str.toString();
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
		
		
		ok = ok & checkParameter( settings, KEY_SPLITTING_MAX_DISTANCE, Double.class, str );
		
        ok = ok & checkParameter( settings, KEY_CREATE_LINKS, Boolean.class, str );
		
		ok = ok & checkParameter( settings, KEY_BREAK_LINKS, Boolean.class, str );
		
		ok = ok & checkParameter( settings, KEY_TARGET_CHANNEL, Integer.class, str );
		
        ok = ok & checkParameter( settings, KEY_USE_MARI_PRINCIPLE, Boolean.class, str );
		
		ok = ok & checkParameter( settings, KEY_MARI_ANGLE, Double.class, str );
		
		ok = ok & checkParameter( settings, KEY_PROB_THRESHOLD, Double.class, str );

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
