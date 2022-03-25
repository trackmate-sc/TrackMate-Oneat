package fiji.plugin.trackmate.oneat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.options.OptionsService;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.util.TMUtils;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RealPoint;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class TrackCorrectorRunner {


	private final static Context context = TMUtils.getContext();
	
	public static Pair<SpotCollection, HashMap<Integer, ArrayList<Spot>>> run(final Settings settings, final Model model, final File oneatfile) {
		
		String line = "";
		String cvsSplitBy = ",";
		int count = 0;
		final LogService logService = context.getService( LogService.class );
		final StatusService statusService = context.getService( StatusService.class );
		final OptionsService optionService = context.getService( OptionsService.class );
        ArrayList<Oneatobject> DivisionSpots = new ArrayList<Oneatobject>();	
        HashMap<Integer, ArrayList<Oneatobject>> DivisionMap = new HashMap<Integer, ArrayList<Oneatobject>>();
		try (BufferedReader br = new BufferedReader(new FileReader(oneatfile))) {

			while ((line = br.readLine()) != null) {

				// use comma as separator
				String[] divisionspots = line.split(cvsSplitBy);

				if (count > 0) {

					int time = Integer.parseInt(divisionspots[0]);
					double Z = Double.parseDouble(divisionspots[1]);
					double Y = Double.parseDouble(divisionspots[2]);
					double X = Double.parseDouble(divisionspots[3]);
					double score = Double.parseDouble(divisionspots[4]);
					double size = Double.parseDouble(divisionspots[5]);
					double confidence = Double.parseDouble(divisionspots[6]);
					double angle = Double.parseDouble(divisionspots[7]);
					
					RealPoint point = new RealPoint(X, Y, Z);
					Oneatobject Spot = new Oneatobject(time, point, score, size, confidence, angle);
					
					if (DivisionMap.get(time) == null) {
						DivisionSpots = new ArrayList<Oneatobject>();
						DivisionMap.put(time, DivisionSpots);
					} else
						DivisionMap.put(time, DivisionSpots);
					DivisionSpots.add(Spot);
				}
				count = count + 1;
			}
		}

		catch (IOException ie) {
			ie.printStackTrace();
		}
		
		
		// Parse each component.
		HashMap<Integer, ArrayList<Spot>> DivisionSpotListFrame = new HashMap<Integer, ArrayList<Spot>>();
		final Iterator<Entry<Integer, ArrayList<Oneatobject>>> iterator = DivisionMap.entrySet().iterator();
		SpotCollection spots = new SpotCollection();
		while (iterator.hasNext()) {
			final Map.Entry<Integer, ArrayList<Oneatobject>> region = iterator.next();

			int frame = region.getKey();
			ArrayList<Oneatobject> currentcell = region.getValue();
			ArrayList<Spot> currentspots = new ArrayList<Spot>();
			for (Oneatobject cell : currentcell) {
				final double x =  (cell.Location.getDoublePosition(0));
				final double y =  (cell.Location.getDoublePosition(1));
				final double z =  (cell.Location.getDoublePosition(2));

				double volume = cell.size;
				double quality = cell.size;
				int ndims = settings.imp.getNDimensions();

				final double radius = (ndims == 2) ? Math.sqrt(volume / Math.PI)
						: Math.pow(3. * volume / (4. * Math.PI), 1. / 3.);

				Spot currentspot = new Spot(x, y, z, radius, quality);
				currentspots.add(currentspot);
				spots.add(currentspot, frame);
				DivisionSpotListFrame.put(frame, currentspots);
			}

		}

		return new ValuePair<SpotCollection, HashMap<Integer, ArrayList<Spot>>>(spots, DivisionSpotListFrame);
	}
	
}
