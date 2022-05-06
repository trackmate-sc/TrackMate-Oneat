package fiji.plugin.trackmate.action.oneat;

import java.io.File;
import java.util.Map;

import javax.swing.JPanel;

import org.jdom2.Element;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMateModule;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import fiji.plugin.trackmate.tracking.SpotTracker;
import fiji.plugin.trackmate.tracking.SpotTrackerFactory;
import fiji.plugin.trackmate.tracking.TrackerKeys;
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;

/**
 * Interface for track corrector factories that need to process all the tracks at
 * once. They return new tracks.
 * 
 * @author Varun Kapoor
 *
 * @param <T>
 */
public interface  TrackCorrectorFactory  extends TrackMateModule
 {

	
	
	   
	
	
		/**
		 * Instantiates and returns a new {@link SpotTracker} configured to operate
		 * on the specified {@link SpotCollection}, using the specified settins map.
		 *
		 * @param spots
		 *            the {@link SpotCollection} containing the spots to track.
		 * @param settings
		 *            the settings map configuring the tracker.
		 * @return a new {@link SpotTracker} instance.
		 */
		public TrackCorrector create(  ImgPlus< IntType > img,  Model model,
				final Map< String, Object > settings, final Logger logger, final double[] calibration );

		/**
		 * Returns a new GUI panel able to configure the settings suitable for the
		 * target tracker identified by the key parameter.
		 *
		 * @param model
		 *            the model that will be modified by the target tracker.
		 * @return a new configuration panel.
		 */
		public JPanel getTrackCorrectorConfigurationPanel(final Settings settings,Map<String, Object> trackmapsettings,
				Map<String, Object> detectorsettings,  final Model model);

		/**
		 * Marshalls a settings map to a JDom element, ready for saving to XML. The
		 * element is <b>updated</b> with new attributes.
		 * <p>
		 * Only parameters specific to the concrete tracker factory are marshalled.
		 * The element also always receive an attribute named
		 * {@value TrackerKeys#XML_ATTRIBUTE_TRACKER_NAME} that saves the target
		 * {@link SpotTracker} key.
		 *
		 * @return true if marshalling was successful. If not, check
		 *         {@link #getErrorMessage()}
		 */
		public boolean marshall( final Map< String, Object > settings, final Element element );

		/**
		 * Un-marshall a JDom element to update a settings map, and sets the target
		 * tracker of this provider from the element.
		 * <p>
		 * Concretely: the the specific settings map for the targeted tracker is
		 * updated from the element.
		 *
		 * @param element
		 *            the JDom element to read from.
		 * @param settings
		 *            the map to update. Is cleared prior to updating, so that it
		 *            contains only the parameters specific to the target tracker.
		 * @return true if unmarshalling was successful. If not, check
		 *         {@link #getErrorMessage()}
		 */
		public boolean unmarshall( final Element element, final Map< String, Object > settings );

		/**
		 * A utility method that builds a string representation of a settings map
		 * owing to the currently selected tracker in this provider.
		 *
		 * @param sm
		 *            the map to echo.
		 * @return a string representation of the map.
		 */
		public String toString( final Map< String, Object > sm );



		/**
		 * Checks the validity of the given settings map for the tracker. The
		 * validity check is strict: we check that all needed parameters are here
		 * and are of the right class, and that there is no extra unwanted
		 * parameters.
		 *
		 * @return true if the settings map can be used with the target factory. If
		 *         not, check {@link #getErrorMessage()}
		 */
		public boolean checkSettingsValidity( final Map< String, Object > settings );

		/**
		 * Returns a meaningful error message for the last action on this factory.
		 *
		 * @return an error message.
		 * @see #marshall(Map, Element)
		 * @see #unmarshall(Element, Map)
		 * @see #checkSettingsValidity(Map)
		 */
		public String getErrorMessage();

		/**
		 * Returns a copy the current instance.
		 * 
		 * @return a new instance of this tracker factory.
		 */
		public TrackCorrectorFactory copy();
	}