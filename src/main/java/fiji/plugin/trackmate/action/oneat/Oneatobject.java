package fiji.plugin.trackmate.action.oneat;

import net.imglib2.RealLocalizable;

public class Oneatobject {

	public final int time;

	public final double Z;
	
	public final double Y;
	
	public final double X;

	public final double score;

	public final double size;

	public final double confidence;

	public final double angle;

	public Oneatobject(int time, double Z, double Y, double X, double score, double size, double confidence, double angle) {

		
		this.time = time;
		
		this.Z = Z;
		
		this.Y = Y;
		
		this.X = X;
		
		this.score = score;

		this.size = size;

		this.confidence = confidence;
		
		this.angle = angle;


	}
	
}
