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

import net.imglib2.util.LinAlgHelpers;

public class Ellipsoid extends HyperEllipsoid
{
	/**
	 * Construct 3D ellipsoid. Some of the parameters may be null. The center
	 * parameter is always required. Moreover, either
	 * <ul>
	 * <li>covariance or</li>
	 * <li>precision or</li>
	 * <li>axes and radii</li>
	 * </ul>
	 * must be provided.
	 *
	 * @param center
	 *            coordinates of center. must not be {@code null}.
	 * @param covariance
	 * @param precision
	 * @param axes
	 * @param radii
	 */
	public Ellipsoid( final double[] center, final double[][] covariance, final double[][] precision, final double[][] axes, final double[] radii )
	{
		super( center, covariance, precision, axes, radii );
	}
	
	
	
	
	
	/**
	 * 
	 * In addition to the above ellipsoid, also include a representation of ellipsoid in a quadratic form
	 * The coefficient matrix contains these coefficients of such a form of the ellipsoid.
	 * 
	 * @param center
	 * @param covariance
	 * @param precision
	 * @param axes
	 * @param radii
	 * @param Coefficients
	 */
	
	public Ellipsoid(final double[] center, final double[][] covariance, final double[][] precision, final double[][] axes, final double[] radii,final double[] Coefficients) {
		
		
		super( center, covariance, precision, axes, radii, Coefficients );
	}
	

	@Override
	public String toString()
	{
		return "center = " +
				LinAlgHelpers.toString( getCenter() )
				+ "\nradii = " +
				LinAlgHelpers.toString( getRadii() )
				+ "\naxes = " +
				LinAlgHelpers.toString( getAxes() )
				+ "\nprecision = " +
				LinAlgHelpers.toString( getPrecision() )
		        +  "\nCoefficients = " +
				LinAlgHelpers.toString(getCoefficients());
	}

	
}
