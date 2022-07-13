package fiji.plugin.trackmate.action.oneat;

import java.util.ArrayList;

import fiji.plugin.trackmate.Spot;
import net.imglib2.util.Pair;

public class Graphobject {
	
	
	public final ArrayList<Pair<Spot, Spot>> removeedges;
	
	public final ArrayList<Pair<Spot, Spot>> addedges;
	
	public final ArrayList<Double> costlist;
	
	
	public Graphobject(ArrayList<Pair<Spot, Spot>> removeedges, ArrayList<Pair<Spot, Spot>> addedges, ArrayList<Double> costlist ) {
		
		this.removeedges = removeedges;
		
		this.addedges = addedges;
		
		this.costlist = costlist;
		
	}

}
