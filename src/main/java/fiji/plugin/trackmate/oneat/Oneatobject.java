package fiji.plugin.trackmate.oneat;

import net.imglib2.RealLocalizable;

public class Oneatobject {

	public final int time;

	public final RealLocalizable Location;

	public final double score;

	public final double size;

	public final double confidence;

	public final double angle;

	public Oneatobject(int time, RealLocalizable Location, double score, double size, double confidence, double angle) {

		
		this.time = time;
		
		this.Location = Location;
		
		this.score = score;

		this.size = size;

		this.confidence = confidence;
		
		this.angle = angle;


	}
	
}
