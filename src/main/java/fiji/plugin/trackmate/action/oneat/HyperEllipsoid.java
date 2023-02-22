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


import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;

/**
 * Hyperellipsoid in this.n dimensions.
 *
 * <p>
 * Points <em>x</em> on the ellipsoid are <em>(x - c)^T * M * (x - c) = 1</em>, where
 * <em>c = </em>{@link #getCenter()} and <em>M = </em>{@link #getPrecision()}.
 *
 * <p>
 * <em>M = R * D * R^T</em>, where <em>D</em> is diagonal with entries <em>1/e_i^2</em> and <em>e_i</em> are radii {@link #getRadii()}.
 * <em>R</em> is orthogonal matrix whose columns are the axis directions, that is, <em>R^T = </em>{@link #getAxes()}.
 *
 * <p>
 * To rotate a point <em>x</em> into ellipsoid coordinates (axis-aligned ellipsoid) compute <em>R^T * x</em>.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt; Modified to implement Ellipsoid of ImageJ-roi by V. Kapoor
 * */

public class HyperEllipsoid {

	
	private double[][] axes;

	private double[] radii;

	private double[][] covariance;

	private double[][] precision;
	
	private double[] Coefficients;
	
	private double[] center;
	
	private int n;

	/**
	 * Construct hyperellipsoid. Some of the parameters may be null. The center
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
	protected HyperEllipsoid( final double[] center, final double[][] covariance, final double[][] precision, final double[][] axes, final double[] radii )
	{
		this.axes = axes;
		this.center = center;
		this.radii = radii;
		this.covariance = covariance;
		this.precision = precision;
		this.n = center.length;
	}
	
	
	protected HyperEllipsoid(final double[] center, final double[][] covariance, final double[][] precision, final double[][] axes, final double[] radii , final double[] Coefficients) {
		
		this.axes = axes;
		this.center = center;
		this.radii = radii;
		this.covariance = covariance;
		this.precision = precision;
		this.Coefficients = Coefficients;
		this.n = center.length;
	}
	

	/**
	 * Get Coefficient matrix for ellipsoid in quadratic form.
	 *
	 * @return quadratic form coefficients.
	 */
	public double[] getCoefficients()
	{
		return Coefficients;
	}

	/**
	 * Get coordinates of center.
	 *
	 * @return center coordinates.
	 */
	public double[] getCenter()
	{
		return this.center;
	}

	/**
	 * Get axes as unit vectors.
	 * Indices are {@code axes[axisIndex][dimensionIndex]}.
	 *
	 * @return axes as array of unit vectors.
	 */
	public double[][] getAxes()
	{
		if ( axes == null )
		{
			if ( covariance != null )
				computeAxisAndRadiiFromCovariance();
			else
				computeAxisAndRadiiFromPrecision();
		}
		return axes;
	}

	
	/**
	 * Get array of radius along each {@link #getAxes() axis}.
	 *
	 * @return radii.
	 */
	public double[] getRadii()
	{
		if ( radii == null )
		{
			if ( covariance != null )
				computeAxisAndRadiiFromCovariance();
			else
				computeAxisAndRadiiFromPrecision();
		}
		return radii;
	}

	/**
	 * Get the covariance matrix.
	 *
	 * @return covariance matrix.
	 */
	public double[][] getCovariance()
	{
		if ( covariance == null )
		{
			if ( precision != null )
				computeCovarianceFromPrecision();
			else
				computeCovarianceFromAxesAndRadii();
		}
		return covariance;
	}

	/**
	 * Get the covariance matrix.
	 *
	 * @param m is set to covariance matrix.
	 */
	public void getCovariance( final double[][] m )
	{
		LinAlgHelpers.copy( getCovariance(), m );
	}

	/**
	 * Get the precision (inverse covariance) matrix.
	 *
	 * @return precision matrix.
	 */
	public double[][] getPrecision()
	{
		if ( precision == null )
		{
			if ( covariance != null )
				computePrecisionFromCovariance();
			else
				computePrecisionFromAxesAndRadii();
		}
		return precision;
	}
	
	public boolean test( final double[] point )
	{
		final double[] x = new double[ this.n ];
		final double[] y = new double[ this.n ];
		LinAlgHelpers.subtract( point, getCenter(), x );
		LinAlgHelpers.mult( getPrecision(), x, y );
		return LinAlgHelpers.dot( x, y ) <= 1;
	}
	public boolean test( final RealLocalizable point )
	{
		final double[] p = new double[ this.n ];
		point.localize( p );
		return test( p );
	}

	private void computeCovarianceFromAxesAndRadii()
	{
		final double[][] tmp = new double[ this.n ][];
		covariance = new double[ this.n ][];
		for ( int d = 0; d < this.n; ++d )
		{
			tmp[ d ] = new double[ this.n ];
			covariance[ d ] = new double[ this.n ];
			LinAlgHelpers.scale( axes[ d ], radii[ d ] * radii[ d ], tmp[ d ] );
		}
		LinAlgHelpers.multATB( axes, tmp, covariance );
	}

	private void computeCovarianceFromPrecision()
	{
		covariance = new Matrix( precision ).inverse().getArray();
	}
	
	private void computePrecisionFromAxesAndRadii()
	{
		final double[][] tmp = new double[ this.n ][];
		precision = new double[ this.n ][];
		for ( int d = 0; d < this.n; ++d )
		{
			tmp[ d ] = new double[ this.n ];
			precision[ d ] = new double[ this.n ];
			LinAlgHelpers.scale( axes[ d ], 1.0 / ( radii[ d ] * radii[ d ] ), tmp[ d ] );
		}
		LinAlgHelpers.multATB( axes, tmp, precision );
	}

	private void computePrecisionFromCovariance()
	{
		precision = new Matrix( covariance ).inverse().getArray();
	}

	private void computeAxisAndRadiiFromPrecision()
	{
		final EigenvalueDecomposition eig = new Matrix( precision ).eig();
		axes = eig.getV().transpose().getArray();
		final Matrix ev = eig.getD();
		radii = new double[ this.n ];
		for ( int d = 0; d < this.n; ++d )
			radii[ d ] = Math.sqrt( 1 / ev.get( d, d ) );
	}

	private void computeAxisAndRadiiFromCovariance()
	{
		final EigenvalueDecomposition eig = new Matrix( covariance ).eig();
		axes = eig.getV().transpose().getArray();
		final Matrix ev = eig.getD();
		radii = new double[ this.n ];
		for ( int d = 0; d < this.n; ++d )
			radii[ d ] = Math.sqrt( ev.get( d, d ) );
	}

	public double exponent() {

		return 2;
	}

	public double semiAxisLength(int d) {

		
		return radii[d];
	}

	public RealPoint center() {
		
		
		
		return new RealPoint(getCenter());
	}

	public void setSemiAxisLength(int d, double length) {
		radii[d] = length;
		
	}

	



	// -- Helper methods --

	/**
	 * Computes the unit distance squared between a given location and the
	 * center of the ellipsoid.
	 *
	 * @param l
	 *            location to check
	 * @return squared unit distance
	 */
	protected double distancePowered( final RealLocalizable l )
	{
		assert ( l.numDimensions() >= this.n ): "l must have no less than " + this.n + " dimensions";

		double distancePowered = 0;
		for ( int d = 0; d < this.n; d++ )
			distancePowered += ( ( l.getDoublePosition( d ) - getCenter()[ d ] ) / radii[ d ] ) * ( ( l.getDoublePosition( d ) - getCenter()[ d ] ) / radii[ d ] );

		return distancePowered;
	}

	public double realMin(int d) {
		
		return getCenter()[d] - radii[d];
	}
	public double realMax(int d) {

		return getCenter()[d] + radii[d];
	}
}
