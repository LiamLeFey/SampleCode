package mudclient.automapper;

import java.util.*;
import java.io.*;

import tools.*;
import tools.persistence.*;

// this should be the only way to get a Region object.
// Other objects should only keep transiet references 
// to regions.
public class RegionManager implements Observer{
  private static HashSet names = new HashSet();

  private static RegionNameIndex regionNameIndex = new RegionNameIndex();

  private int currentRegionIndex = 0;

  private SoftIntMap regions;
  private IntMap changedRegions;
  private boolean needToRecalcEntrances = false;

  private File myFileName;
  private PersistentStore myPS;

  public RegionManager( File fileName ){
    myFileName = fileName;
    regions = new SoftIntMap();
    changedRegions = new IntMap();
    try{
      initializePS();
    }catch( IOException e ){
      e.printStackTrace( System.err );
    }
  }
  public int getCurrentRegionIndex(){
    return currentRegionIndex;
  }
  public int[] getIDs( ){
    return myPS.getIDs();
  }
  public int[] getIDsByName( String name ){
    return myPS.getIDs( new int[]{ regionNameIndex.getCode( name ) } );
  }
  public Region get( int regionID ){
    Region r;
    if( (r = getFromCache(regionID)) == null ){
      r = loadFromPS( regionID );
      if( r != null ){
        regions.put( r.getID(), r );
        r.addObserver( this );
      }
    }
    return r;
  }
  public int createNewRegion( ){
    Region r = new Region( ++currentRegionIndex );
    regions.put( r.getID(), r );
    try{
      updateRegion( r.getID() );
    }catch (IOException e ){
      e.printStackTrace( System.err );
      markChanged( r );
    }
    r.addObserver( this );
    return r.getID();
  }

  private void unmarkChanged( int i ){
    changedRegions.remove( i );
  }
  private void markChanged( Region r ){
    changedRegions.put( r.getID(), r );
  }
  private void initializePS() throws IOException {
    myPS = new PersistentStore( 
        myFileName, 
        new Index[]{ regionNameIndex }, 
        new Region(-1) );
    currentRegionIndex = myPS.getMaxID();
  }
  public boolean save( RoomManager rmMgr, SpecialExitManager seMgr ){
    try{
      updatePS( rmMgr, seMgr );
    }catch (IOException e ){
      e.printStackTrace( System.err );
      return false;
    }
    return true;
  }
  private void updatePS( RoomManager rmMgr, SpecialExitManager seMgr ) 
    throws IOException {
    int i;
    if( needToRecalcEntrances )
      recalcEntrances( rmMgr, seMgr );
    int[] is = getChangedRegions();
    for( i = 0; i < is.length; i++ ){
      updateRegion( is[ i ] );
    }
    myPS.flush();
  }
  private void recalcEntrances( RoomManager rmMgr, SpecialExitManager seMgr ) 
    throws IOException {
      int[] regionIDs = getIDs();
      IntMap previousRegionEntrances = new IntMap( regionIDs.length );
      IntMap currentRegionEntrances = new IntMap( regionIDs.length );
      IntSet previousEntrances, currentEntrances;
      Region rg;
      Room rm;
      int[] entrances, exits, destinations;
      int i, j, k;
      for( i = 0; i < regionIDs.length; i++ ){
        currentRegionEntrances.put( regionIDs[i], new IntSet() );
      }
      for( i = 0; i < regionIDs.length; i++ ){
        previousEntrances = new IntSet();
        rg = get( regionIDs[i] );
        entrances = rg.getEntrances();
        for( j = 0; j < entrances.length; j++ )
          previousEntrances.add( entrances[j] );
        previousRegionEntrances.put( regionIDs[i], previousEntrances );
        exits = rg.getExits();
        for( j = 0; j < exits.length; j++ ){
          rm = rmMgr.get( exits[j] );
          destinations = rm.getDestinations( seMgr );
          for( k = 0; k < destinations.length; k++ ){
            rm = rmMgr.get( destinations[k] );
            if( rm.getRegion() != regionIDs[i] )
              ((IntSet)currentRegionEntrances.get( rm.getRegion() ))
                .add( rm.getID() );
          }
        }
      }
      for( i = 0; i < regionIDs.length; i++ ){
        previousEntrances = (IntSet)previousRegionEntrances.get( regionIDs[i] );
        currentEntrances = (IntSet)currentRegionEntrances.get( regionIDs[i] );
        if( ! previousEntrances.equals( currentEntrances ) ){
          rg = get( regionIDs[i] );
          IntIterator it = previousEntrances.iterator();
          while( it.hasNext() ){
            k = it.next();
            if( ! currentEntrances.contains( k ) )
              rg.removeEntrance( k );
          }
        }
      }
      needToRecalcEntrances = false;
    }

  private void updateRegion( int i ) throws IOException {
    
    // no use writing it if it's not in memory.
    Region r = getFromCache( i );
    if( r != null ){
      myPS.store( r );
    }
    unmarkChanged( i );
  }
  private boolean isMarkedChanged( int id ){
    return changedRegions.contains( id );
  }
  private int[] getChangedRegions(){
    return changedRegions.keys();
  }

  private Region getFromCache( int id ){
    return (Region)regions.get(id);
  }
  private Region loadFromPS( int id ){
    Region r = new Region(-1);
    try{
      myPS.loadStorable( id, r );
    }catch( IOException e ){
      e.printStackTrace( System.err );
      return null;
    }
    if( r.getID() == -1 )
      return null;
    return r;
  }
  public void update( Observable o, Object arg ){
    // for now we onlyl observer regions
    Region r = (Region) o;
    markChanged( r );
    if( arg == Region.NAME_CHANGED ){
      try{
        updateRegion( r.getID() );
      }catch( IOException e ){
        e.printStackTrace( System.err );
        markChanged( r );
      }
    }
    if( arg == Region.EXIT_REMOVED )
      needToRecalcEntrances = true;
    return;
  }
  public void finalize(){
    try{
      myPS.flush();
      myPS.close();
    }catch( IOException ioe ){
      ioe.printStackTrace( System.err );
    }
  }
  static final class RegionNameIndex implements Index{
    public final int getCode( Object region ){
      return getCode( ((Region)region).getName() );
    }
    public final int getCode( String name ){
      return name == null ? 0 : name.hashCode();
    }
  }
}
