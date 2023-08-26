package fiji.plugin.trackmate.action.oneat;

import java.util.Comparator;

import fiji.plugin.trackmate.Spot;


public class SpotComparator implements Comparator<Spot> {
	    @Override
	    public int compare(Spot spot1, Spot spot2) {
	        return Integer.compare(spot1.getFeature(Spot.POSITION_T).intValue(), spot2.getFeature(Spot.POSITION_T).intValue());
	    }
	}