package fiji.plugin.trackmate.oneat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import fiji.plugin.trackmate.features.FeatureFilter;
import net.imglib2.algorithm.MultiThreaded;

public class DivisionSpotCollection implements MultiThreaded
{

	public static final Double ZERO = Double.valueOf( 0d );

	public static final Double ONE = Double.valueOf( 1d );

	public static final String VISIBILITY = "VISIBILITY";

	/**
	 * Time units for filtering and cropping operation timeouts. Filtering
	 * should not take more than 1 minute.
	 */
	private static final TimeUnit TIME_OUT_UNITS = TimeUnit.MINUTES;

	/**
	 * Time for filtering and cropping operation timeouts. Filtering should not
	 * take more than 1 minute.
	 */
	private static final long TIME_OUT_DELAY = 1;

	/** The frame by frame list of spot this object wrap. */
	private ConcurrentSkipListMap< Integer, Set< DivisionSpot > > content = new ConcurrentSkipListMap<>();

	private int numThreads;

	/*
	 * CONSTRUCTORS
	 */

	/**
	 * Construct a new empty spot collection.
	 */
	public DivisionSpotCollection()
	{
		setNumThreads();
	}

	/*
	 * METHODS
	 */

	/**
	 * Retrieves and returns the {@link DivisionSpot} object in this collection with the
	 * specified ID. Returns <code>null</code> if the spot cannot be found. All
	 * spots, visible or not, are searched for.
	 *
	 * @param ID
	 *            the ID to look for.
	 * @return the spot with the specified ID or <code>null</code> if this spot
	 *         does not exist or does not belong to this collection.
	 */
	public DivisionSpot search( final int ID )
	{
		/*
		 * Having a map id -> spot would be better, but we don't have a big need
		 * for this.
		 */
		for ( final DivisionSpot spot : iterable( false ) )
			if ( spot.ID() == ID )
				return spot;

		return null;
	}

	@Override
	public String toString()
	{
		String str = super.toString();
		str += ": contains " + getNDivisionSpots( false ) + " spots total in "
				+ keySet().size() + " different frames, over which "
				+ getNDivisionSpots( true ) + " are visible:\n";
		for ( final int key : content.keySet() )
			str += "\tframe " + key + ": "
					+ getNDivisionSpots( key, false ) + " spots total, "
					+ getNDivisionSpots( key, true ) + " visible.\n";

		return str;
	}

	/**
	 * Adds the given spot to this collection, at the specified frame, and mark
	 * it as visible.
	 * <p>
	 * If the frame does not exist yet in the collection, it is created and
	 * added. Upon adding, the added spot has its feature {@link DivisionSpot#FRAME}
	 * updated with the passed frame value.
	 * 
	 * @param spot
	 *            the spot to add.
	 * @param frame
	 *            the frame to add it to.
	 */
	public void add( final DivisionSpot spot, final Integer frame )
	{
		Set< DivisionSpot > spots = content.get( frame );
		if ( null == spots )
		{
			spots = new HashSet<>();
			content.put( frame, spots );
		}
		spots.add( spot );
		spot.putFeature( DivisionSpot.FRAME, Double.valueOf( frame ) );
		spot.putFeature( VISIBILITY, ONE );
	}

	/**
	 * Removes the given spot from this collection, at the specified frame.
	 * <p>
	 * If the spot frame collection does not exist yet, nothing is done and
	 * <code>false</code> is returned. If the spot cannot be found in the frame
	 * content, nothing is done and <code>false</code> is returned.
	 * 
	 * @param spot
	 *            the spot to remove.
	 * @param frame
	 *            the frame to remove it from.
	 * @return <code>true</code> if the spot was succesfully removed.
	 */
	public boolean remove( final DivisionSpot spot, final Integer frame )
	{
		final Set< DivisionSpot > spots = content.get( frame );
		if ( null == spots )
			return false;
		return spots.remove( spot );
	}

	/**
	 * Marks all the content of this collection as visible or invisible.
	 *
	 * @param visible
	 *            if true, all spots will be marked as visible.
	 */
	public void setVisible( final boolean visible )
	{
		final Double val = visible ? ONE : ZERO;
		final Collection< Integer > frames = content.keySet();

		final ExecutorService executors = Executors.newFixedThreadPool( numThreads );
		for ( final Integer frame : frames )
		{

			final Runnable command = new Runnable()
			{
				@Override
				public void run()
				{

					final Set< DivisionSpot > spots = content.get( frame );
					for ( final DivisionSpot spot : spots )
						spot.putFeature( VISIBILITY, val );
				}
			};
			executors.execute( command );
		}

		executors.shutdown();
		try
		{
			final boolean ok = executors.awaitTermination( TIME_OUT_DELAY, TIME_OUT_UNITS );
			if ( !ok )
				System.err.println( "[DivisionSpotCollection.setVisible()] Timeout of " + TIME_OUT_DELAY + " " + TIME_OUT_UNITS + " reached." );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * Filters out the content of this collection using the specified
	 * {@link FeatureFilter}. DivisionSpots that are filtered out are marked as
	 * invisible, and visible otherwise.
	 *
	 * @param featurefilter
	 *            the filter to use.
	 */
	public final void filter( final FeatureFilter featurefilter )
	{

		final Collection< Integer > frames = content.keySet();
		final ExecutorService executors = Executors.newFixedThreadPool( numThreads );

		for ( final Integer frame : frames )
		{

			final Runnable command = new Runnable()
			{
				@Override
				public void run()
				{
					final Set< DivisionSpot > spots = content.get( frame );
					final double tval = featurefilter.value;

					if ( featurefilter.isAbove )
					{
						for ( final DivisionSpot spot : spots )
						{
							final Double val = spot.getFeature( featurefilter.feature );
							spot.putFeature( VISIBILITY, val.compareTo( tval ) < 0 ? ZERO : ONE );
						}

					}
					else
					{
						for ( final DivisionSpot spot : spots )
						{
							final Double val = spot.getFeature( featurefilter.feature );
							spot.putFeature( VISIBILITY, val.compareTo( tval ) > 0 ? ZERO : ONE );
						}
					}
				}
			};
			executors.execute( command );
		}

		executors.shutdown();
		try
		{
			final boolean ok = executors.awaitTermination( TIME_OUT_DELAY, TIME_OUT_UNITS );
			if ( !ok )
				System.err.println( "[DivisionSpotCollection.filter()] Timeout of " + TIME_OUT_DELAY + " " + TIME_OUT_UNITS + " reached while filtering." );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * Filters out the content of this collection using the specified
	 * {@link FeatureFilter} collection. DivisionSpots that are filtered out are marked
	 * as invisible, and visible otherwise. To be marked as visible, a spot must
	 * pass <b>all</b> of the specified filters (AND chaining).
	 *
	 * @param filters
	 *            the filter collection to use.
	 */
	public final void filter( final Collection< FeatureFilter > filters )
	{

		final Collection< Integer > frames = content.keySet();
		final ExecutorService executors = Executors.newFixedThreadPool( numThreads );

		for ( final Integer frame : frames )
		{
			final Runnable command = new Runnable()
			{
				@Override
				public void run()
				{
					final Set< DivisionSpot > spots = content.get( frame );
					for ( final DivisionSpot spot : spots )
					{

						boolean shouldNotBeVisible = false;
						for ( final FeatureFilter featureFilter : filters )
						{

							final Double val = spot.getFeature( featureFilter.feature );
							final double tval = featureFilter.value;
							final boolean isAbove = featureFilter.isAbove;

							if ( null == val || isAbove && val.compareTo( tval ) < 0 || !isAbove && val.compareTo( tval ) > 0 )
							{
								shouldNotBeVisible = true;
								break;
							}
						} // loop over filters
						spot.putFeature( VISIBILITY, shouldNotBeVisible ? ZERO : ONE );

					} // loop over spots
				}
			};
			executors.execute( command );
		}

		executors.shutdown();
		try
		{
			final boolean ok = executors.awaitTermination( TIME_OUT_DELAY, TIME_OUT_UNITS );
			if ( !ok )
				System.err.println( "[DivisionSpotCollection.filter()] Timeout of " + TIME_OUT_DELAY + " " + TIME_OUT_UNITS + " reached while filtering." );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * Returns the closest {@link DivisionSpot} to the given location (encoded as a
	 * DivisionSpot), contained in the frame <code>frame</code>. If the frame has no
	 * spot, return <code>null</code>.
	 *
	 * @param location
	 *            the location to search for.
	 * @param frame
	 *            the frame to inspect.
	 * @param visibleDivisionSpotsOnly
	 *            if true, will only search though visible spots. If false, will
	 *            search through all spots.
	 * @return the closest spot to the specified location, member of this
	 *         collection.
	 */
	public final DivisionSpot getClosestDivisionSpot( final DivisionSpot location, final int frame, final boolean visibleDivisionSpotsOnly )
	{
		final Set< DivisionSpot > spots = content.get( frame );
		if ( null == spots )
			return null;
		double minDist = Double.POSITIVE_INFINITY;
		DivisionSpot target = null;
		for ( final DivisionSpot spot : spots )
		{

			if ( visibleDivisionSpotsOnly && !isVisible( spot ) )
				continue;

			final double d2 = spot.squareDistanceTo( location );
			if ( d2 < minDist )
			{
				minDist = d2;
				target = spot;
			}
		}
		return target;
	}

	/**
	 * Returns the {@link DivisionSpot} at the given location (encoded as a DivisionSpot),
	 * contained in the frame <code>frame</code>. A spot is returned <b>only</b>
	 * if there exists a spot such that the given location is within the spot
	 * radius. Otherwise <code>null</code> is returned.
	 *
	 * @param location
	 *            the location to search for.
	 * @param frame
	 *            the frame to inspect.
	 * @param visibleDivisionSpotsOnly
	 *            if true, will only search though visible spots. If false, will
	 *            search through all spots.
	 * @return the closest spot such that the specified location is within its
	 *         radius, member of this collection, or <code>null</code> is such a
	 *         spots cannot be found.
	 */
	public final DivisionSpot getDivisionSpotAt( final DivisionSpot location, final int frame, final boolean visibleDivisionSpotsOnly )
	{
		final Set< DivisionSpot > spots = content.get( frame );
		if ( null == spots || spots.isEmpty() )
			return null;

		double minDist2 = Double.POSITIVE_INFINITY;
		DivisionSpot bestDivisionSpot = null;
		for ( final DivisionSpot spot : spots )
		{
			if ( visibleDivisionSpotsOnly && !isVisible( spot ) )
				continue;

			final double d2 = spot.squareDistanceTo( location );
			final double radius = spot.getFeature( DivisionSpot.RADIUS );
			if ( d2 < Math.min( minDist2, radius * radius ) )
			{
				minDist2 = d2;
				bestDivisionSpot = spot;
			}
		}
		return bestDivisionSpot;
	}

	/**
	 * Returns the total number of spots in this collection, over all frames.
	 *
	 * @param visibleDivisionSpotsOnly
	 *            if true, will only count visible spots. If false count all
	 *            spots.
	 * @return the total number of spots in this collection.
	 */
	public final int getNDivisionSpots( final boolean visibleDivisionSpotsOnly )
	{
		int nspots = 0;
		if ( visibleDivisionSpotsOnly )
		{
			final Iterator< DivisionSpot > it = iterator( true );
			while ( it.hasNext() )
			{
				it.next();
				nspots++;
			}

		}
		else
		{
			for ( final Set< DivisionSpot > spots : content.values() )
				nspots += spots.size();
		}
		return nspots;
	}

	/**
	 * Returns the number of spots at the given frame.
	 *
	 * @param frame
	 *            the frame.
	 * @param visibleDivisionSpotsOnly
	 *            if true, will only count visible spots. If false count all
	 *            spots.
	 * @return the number of spots at the given frame.
	 */
	public int getNDivisionSpots( final int frame, final boolean visibleDivisionSpotsOnly )
	{
		if ( visibleDivisionSpotsOnly )
		{
			final Iterator< DivisionSpot > it = iterator( frame, true );
			int nspots = 0;
			while ( it.hasNext() )
			{
				it.next();
				nspots++;
			}
			return nspots;
		}

		final Set< DivisionSpot > spots = content.get( frame );
		if ( null == spots )
			return 0;

		return spots.size();
	}

	/*
	 * ITERABLE & co
	 */

	/**
	 * Return an iterator that iterates over all the spots contained in this
	 * collection.
	 *
	 * @param visibleDivisionSpotsOnly
	 *            if true, the returned iterator will only iterate through
	 *            visible spots. If false, it will iterate over all spots.
	 * @return an iterator that iterates over this collection.
	 */
	public Iterator< DivisionSpot > iterator( final boolean visibleDivisionSpotsOnly )
	{
		if ( visibleDivisionSpotsOnly )
			return new VisibleDivisionSpotsIterator();

		return new AllDivisionSpotsIterator();
	}

	/**
	 * Return an iterator that iterates over the spots in the specified frame.
	 *
	 * @param visibleDivisionSpotsOnly
	 *            if true, the returned iterator will only iterate through
	 *            visible spots. If false, it will iterate over all spots.
	 * @param frame
	 *            the frame to iterate over.
	 * @return an iterator that iterates over the content of a frame of this
	 *         collection.
	 */
	public Iterator< DivisionSpot > iterator( final Integer frame, final boolean visibleDivisionSpotsOnly )
	{
		final Set< DivisionSpot > frameContent = content.get( frame );
		if ( null == frameContent )
			return EMPTY_ITERATOR;

		if ( visibleDivisionSpotsOnly )
			return new VisibleDivisionSpotsFrameIterator( frameContent );

		return frameContent.iterator();
	}

	/**
	 * A convenience methods that returns an {@link Iterable} wrapper for this
	 * collection as a whole.
	 *
	 * @param visibleDivisionSpotsOnly
	 *            if true, the iterable will contains only visible spots.
	 *            Otherwise, it will contain all the spots.
	 * @return an iterable view of this spot collection.
	 */
	public Iterable< DivisionSpot > iterable( final boolean visibleDivisionSpotsOnly )
	{
		return new WholeCollectionIterable( visibleDivisionSpotsOnly );
	}

	/**
	 * A convenience methods that returns an {@link Iterable} wrapper for a
	 * specific frame of this spot collection. The iterable is backed-up by the
	 * actual collection content, so modifying it can have unexpected results.
	 *
	 * @param visibleDivisionSpotsOnly
	 *            if true, the iterable will contains only visible spots of the
	 *            specified frame. Otherwise, it will contain all the spots of
	 *            the specified frame.
	 * @param frame
	 *            the frame of the content the returned iterable will wrap.
	 * @return an iterable view of the content of a single frame of this spot
	 *         collection.
	 */
	public Iterable< DivisionSpot > iterable( final int frame, final boolean visibleDivisionSpotsOnly )
	{
		if ( visibleDivisionSpotsOnly )
			return new FrameVisibleIterable( frame );

		return content.get( frame );
	}

	/*
	 * SORTEDMAP
	 */

	/**
	 * Stores the specified spots as the content of the specified frame. The
	 * added spots are all marked as not visible. Their {@link DivisionSpot#FRAME} is
	 * updated to be the specified frame.
	 *
	 * @param frame
	 *            the frame to store these spots at. The specified spots replace
	 *            the previous content of this frame, if any.
	 * @param spots
	 *            the spots to store.
	 */
	public void put( final int frame, final Collection< DivisionSpot > spots )
	{
		final Set< DivisionSpot > value = new HashSet<>( spots );
		for ( final DivisionSpot spot : value )
		{
			spot.putFeature( DivisionSpot.FRAME, Double.valueOf( frame ) );
			spot.putFeature( VISIBILITY, ZERO );
		}
		content.put( frame, value );
	}

	/**
	 * Returns the first (lowest) frame currently in this collection.
	 *
	 * @return the first (lowest) frame currently in this collection.
	 */
	public Integer firstKey()
	{
		if ( content.isEmpty() )
			return 0;
		return content.firstKey();
	}

	/**
	 * Returns the last (highest) frame currently in this collection.
	 *
	 * @return the last (highest) frame currently in this collection.
	 */
	public Integer lastKey()
	{
		if ( content.isEmpty() )
			return 0;
		return content.lastKey();
	}

	/**
	 * Returns a NavigableSet view of the frames contained in this collection.
	 * The set's iterator returns the keys in ascending order. The set is backed
	 * by the map, so changes to the map are reflected in the set, and
	 * vice-versa. The set supports element removal, which removes the
	 * corresponding mapping from the map, via the Iterator.remove, Set.remove,
	 * removeAll, retainAll, and clear operations. It does not support the add
	 * or addAll operations.
	 * <p>
	 * The view's iterator is a "weakly consistent" iterator that will never
	 * throw ConcurrentModificationException, and guarantees to traverse
	 * elements as they existed upon construction of the iterator, and may (but
	 * is not guaranteed to) reflect any modifications subsequent to
	 * construction.
	 *
	 * @return a navigable set view of the frames in this collection.
	 */
	public NavigableSet< Integer > keySet()
	{
		return content.keySet();
	}

	/**
	 * Removes all the content from this collection.
	 */
	public void clear()
	{
		content.clear();
	}

	/*
	 * MULTITHREADING
	 */

	@Override
	public void setNumThreads()
	{
		this.numThreads = Runtime.getRuntime().availableProcessors();
	}

	@Override
	public void setNumThreads( final int numThreads )
	{
		this.numThreads = numThreads;
	}

	@Override
	public int getNumThreads()
	{
		return numThreads;
	}

	/*
	 * PRIVATE CLASSES
	 */

	private class AllDivisionSpotsIterator implements Iterator< DivisionSpot >
	{

		private boolean hasNext = true;

		private final Iterator< Integer > frameIterator;

		private Iterator< DivisionSpot > contentIterator;

		private DivisionSpot next = null;

		public AllDivisionSpotsIterator()
		{
			this.frameIterator = content.keySet().iterator();
			if ( !frameIterator.hasNext() )
			{
				hasNext = false;
				return;
			}
			final Set< DivisionSpot > currentFrameContent = content.get( frameIterator.next() );
			contentIterator = currentFrameContent.iterator();
			iterate();
		}

		private void iterate()
		{
			while ( true )
			{

				// Is there still spots in current content?
				if ( !contentIterator.hasNext() )
				{
					// No. Then move to next frame.
					// Is there still frames to iterate over?
					if ( !frameIterator.hasNext() )
					{
						// No. Then we are done
						hasNext = false;
						next = null;
						return;
					}

					contentIterator = content.get( frameIterator.next() ).iterator();
					continue;
				}
				next = contentIterator.next();
				return;
			}
		}

		@Override
		public boolean hasNext()
		{
			return hasNext;
		}

		@Override
		public DivisionSpot next()
		{
			final DivisionSpot toReturn = next;
			iterate();
			return toReturn;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException( "Remove operation is not supported for DivisionSpotCollection iterators." );
		}
	}

	private class VisibleDivisionSpotsIterator implements Iterator< DivisionSpot >
	{

		private boolean hasNext = true;

		private final Iterator< Integer > frameIterator;

		private Iterator< DivisionSpot > contentIterator;

		private DivisionSpot next = null;

		private Set< DivisionSpot > currentFrameContent;

		public VisibleDivisionSpotsIterator()
		{
			this.frameIterator = content.keySet().iterator();
			if ( !frameIterator.hasNext() )
			{
				hasNext = false;
				return;
			}
			currentFrameContent = content.get( frameIterator.next() );
			contentIterator = currentFrameContent.iterator();
			iterate();
		}

		private void iterate()
		{

			while ( true )
			{
				// Is there still spots in current content?
				if ( !contentIterator.hasNext() )
				{
					// No. Then move to next frame.
					// Is there still frames to iterate over?
					if ( !frameIterator.hasNext() )
					{
						// No. Then we are done
						hasNext = false;
						next = null;
						return;
					}

					// Yes. Then start iterating over the next frame.
					currentFrameContent = content.get( frameIterator.next() );
					contentIterator = currentFrameContent.iterator();
					continue;
				}
				next = contentIterator.next();
				// Is it visible?
				if ( next.getFeature( VISIBILITY ).compareTo( ZERO ) > 0 )
				{
					// Yes! Be happy and return
					return;
				}
			}
		}

		@Override
		public boolean hasNext()
		{
			return hasNext;
		}

		@Override
		public DivisionSpot next()
		{
			final DivisionSpot toReturn = next;
			iterate();
			return toReturn;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException( "Remove operation is not supported for DivisionSpotCollection iterators." );
		}
	}

	private class VisibleDivisionSpotsFrameIterator implements Iterator< DivisionSpot >
	{

		private boolean hasNext = true;

		private DivisionSpot next = null;

		private final Iterator< DivisionSpot > contentIterator;

		public VisibleDivisionSpotsFrameIterator( final Set< DivisionSpot > frameContent )
		{
			this.contentIterator = ( null == frameContent ) ? EMPTY_ITERATOR : frameContent.iterator();
			iterate();
		}

		private void iterate()
		{
			while ( true )
			{
				if ( !contentIterator.hasNext() )
				{
					// No. Then we are done
					hasNext = false;
					next = null;
					return;
				}
				next = contentIterator.next();
				// Is it visible?
				if ( next.getFeature( VISIBILITY ).compareTo( ZERO ) > 0 )
				{
					// Yes. Be happy, and return.
					return;
				}
			}
		}

		@Override
		public boolean hasNext()
		{
			return hasNext;
		}

		@Override
		public DivisionSpot next()
		{
			final DivisionSpot toReturn = next;
			iterate();
			return toReturn;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException( "Remove operation is not supported for DivisionSpotCollection iterators." );
		}
	}

	/**
	 * Remove all the non-visible spots of this collection.
	 */
	public void crop()
	{
		final Collection< Integer > frames = content.keySet();
		for ( final Integer frame : frames )
		{
			final Set< DivisionSpot > fc = content.get( frame );
			final List< DivisionSpot > toRemove = new ArrayList<>();
			for ( final DivisionSpot spot : fc )
				if ( !isVisible( spot ) )
					toRemove.add( spot );

			fc.removeAll( toRemove );
		}
	}

	/**
	 * A convenience wrapper that implements {@link Iterable} for this spot
	 * collection.
	 */
	private final class WholeCollectionIterable implements Iterable< DivisionSpot >
	{

		private final boolean visibleDivisionSpotsOnly;

		public WholeCollectionIterable( final boolean visibleDivisionSpotsOnly )
		{
			this.visibleDivisionSpotsOnly = visibleDivisionSpotsOnly;
		}

		@Override
		public Iterator< DivisionSpot > iterator()
		{
			if ( visibleDivisionSpotsOnly )
				return new VisibleDivisionSpotsIterator();

			return new AllDivisionSpotsIterator();
		}
	}

	/**
	 * A convenience wrapper that implements {@link Iterable} for this spot
	 * collection.
	 */
	private final class FrameVisibleIterable implements Iterable< DivisionSpot >
	{

		private final int frame;

		public FrameVisibleIterable( final int frame )
		{
			this.frame = frame;
		}

		@Override
		public Iterator< DivisionSpot > iterator()
		{
			return new VisibleDivisionSpotsFrameIterator( content.get( frame ) );
		}
	}

	private static final Iterator< DivisionSpot > EMPTY_ITERATOR = new Iterator< DivisionSpot >()
	{

		@Override
		public boolean hasNext()
		{
			return false;
		}

		@Override
		public DivisionSpot next()
		{
			return null;
		}

		@Override
		public void remove()
		{}
	};

	/*
	 * STATIC METHODS
	 */

	/**
	 * Creates a new {@link DivisionSpotCollection} containing only the specified spots.
	 * Their frame origin is retrieved from their {@link DivisionSpot#FRAME} feature, so
	 * it must be set properly for all spots. All the spots of the new
	 * collection have the same visibility that the one they carry.
	 *
	 * @param spots
	 *            the spot collection to build from.
	 * @return a new {@link DivisionSpotCollection} instance.
	 */
	public static DivisionSpotCollection fromCollection( final Iterable< DivisionSpot > spots )
	{
		final DivisionSpotCollection sc = new DivisionSpotCollection();
		for ( final DivisionSpot spot : spots )
		{
			final int frame = spot.getFeature( DivisionSpot.FRAME ).intValue();
			Set< DivisionSpot > fc = sc.content.get( frame );
			if ( null == fc )
			{
				fc = new HashSet<>();
				sc.content.put( frame, fc );
			}
			fc.add( spot );
		}
		return sc;
	}

	/**
	 * Creates a new {@link DivisionSpotCollection} from a copy of the specified map of
	 * sets. The spots added this way are completely untouched. In particular,
	 * their {@link #VISIBILITY} feature is left untouched, which makes this
	 * method suitable to de-serialize a {@link DivisionSpotCollection}.
	 *
	 * @param source
	 *            the map to buidl the spot collection from.
	 * @return a new DivisionSpotCollection.
	 */
	public static DivisionSpotCollection fromMap( final Map< Integer, Set< DivisionSpot > > source )
	{
		final DivisionSpotCollection sc = new DivisionSpotCollection();
		sc.content = new ConcurrentSkipListMap<>( source );
		return sc;
	}

	private static final boolean isVisible( final DivisionSpot spot )
	{
		return spot.getFeature( VISIBILITY ).compareTo( ZERO ) > 0;
	}
}