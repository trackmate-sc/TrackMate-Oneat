/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2022 TrackMate developers.
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
