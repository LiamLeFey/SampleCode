package mudclient.automapper;

import java.util.*;
import java.io.*;

import tools.*;
import tools.persistence.*;

// this should be the only way to get a Room object.
// Other objects should only keep transiet references 
// to rooms.
public class RoomManager implements Observer{
  private static HashSet names = new HashSet();

  private static ShortDescIndex shortDescIndex 
    = new ShortDescIndex();
  private static LongDescIndex longDescIndex 
    = new LongDescIndex();
  private static ObviousExitsIndex obviousExitsIndex 
    = new ObviousExitsIndex();
  private static RegionIDIndex regionIDIndex 
    = new RegionIDIndex();
  private static UnassignedExitIndex unassignedExitIndex 
    = new UnassignedExitIndex();

  private int currentRoomIndex = 0;

  private HashMap specialRooms;

  private SoftIntMap rooms;
  private IntMap changedRooms;

  private File myFileName;
  private PersistentStore myPS;

  public RoomManager( File fileName ){
    myFileName = fileName;
    rooms = new SoftIntMap();
    changedRooms = new IntMap();
    try{
      initializePS();
    }catch( IOException e ){
      e.printStackTrace( System.err );
    }
  }
  public int getCurrentRoomIndex(){
    return currentRoomIndex;
  }
  public Room get( int roomID ){
    Room r = getFromCache( roomID );
    if( r != null )
      return r;
    r = loadFromPS( roomID );
    if( r != null ){
      r.addObserver( this );
      rooms.put( roomID, r );
    }
    return r;
  }
  public int createNewRoom( int region ){
    Room r = new Room( ++currentRoomIndex, region );
    rooms.put( r.getID(), r );
    try{
      updateRoom( r.getID() );
    }catch( IOException e){
      e.printStackTrace( System.err );
      markChanged( r );
    }
    r.addObserver( this );
    return r.getID();
  }

  private void unmarkChanged( int i ){
    changedRooms.remove( i );
  }
  private void markChanged( Room r ){
    changedRooms.put( r.getID(), r );
  }
  private void initializePS() throws IOException {
    myPS = new PersistentStore( 
        myFileName, 
        new Index[]{
          shortDescIndex,
          longDescIndex,
          obviousExitsIndex,
          regionIDIndex,
          unassignedExitIndex },
        new Room( -1, -1 ));
    currentRoomIndex = myPS.getMaxID();
  }
  public boolean save(){
    try{
      updatePS();
    }catch (IOException e ){
      e.printStackTrace( System.err );
      return false;
    }
    return true;
  }
  private void updatePS() throws IOException {
    int i;
    int[] is = getChangedRooms();
    for( i = 0; i < is.length; i++ ){
      updateRoom( is[ i ] );
    }
    myPS.flush();
  }
  private void updateRoom( int i ) throws IOException {
    
    // no use writing it if it's not in memory.
    Room r = getFromCache( i );
    if( r != null ){
      myPS.store( r );
    }
    unmarkChanged( i );
  }
  private int[] getChangedRooms(){
    return changedRooms.keys();
  }

  private Room getFromCache( int id ){
    return (Room)rooms.get(id);
  }
  private Room loadFromPS( int id ){
    Room r = new Room( -1, -1 );
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
    // for now we onlyl observer rooms
    Room r = (Room) o;
    // this is the only thing we don't index.
    // everything else needs to go into the PS right away
    if( arg == Room.NOTES )
      markChanged( r );
    else{
      try{
        updateRoom( r.getID() );
      }catch( IOException e){
        e.printStackTrace( System.err );
        markChanged( r );
      }
    }
  }
  public int[] getRoomsMatching(
      String shortDesc,
      String longDesc,
      String[] obviousExits,
      int[] region,
      boolean[] hasUnassignedExits ){
    int[] hashCodes = new int[ 5 ];
    boolean[] used = new boolean[ 5 ];
    hashCodes[0] = shortDescIndex.getCode( shortDesc );
    used[0] = shortDesc != null;
    hashCodes[1] = longDescIndex.getCode( longDesc );
    used[1] = longDesc != null;
    hashCodes[2] = obviousExitsIndex.getCode( obviousExits );
    used[2] = obviousExits != null;
    if( region != null && region.length > 0 ){
      hashCodes[3] = regionIDIndex.getCode( region[0] );
      used[3] = true;
    }else used[3] = false;
    if( hasUnassignedExits != null && hasUnassignedExits.length > 0 ){
      hashCodes[4] = unassignedExitIndex.getCode(hasUnassignedExits[0]);
      used[4] = true;
    }else used[4] = false;
    return myPS.getIDs( hashCodes, used );
  }
  public void finalize(){
    try{
      myPS.flush();
      myPS.close();
    }catch( IOException ioe ){
      ioe.printStackTrace( System.err );
    }
  }
  static final class ShortDescIndex implements Index{
    final public int getCode( Object s ){
      return getCode( ((Room)s).getShortDesc() );
    }
    final public int getCode( String shortDesc ){
      return shortDesc == null ? 0 : shortDesc.hashCode();
    }
  }
  static final class LongDescIndex implements Index{
    final public int getCode( Object s ){
      return getCode( ((Room)s).getLongDesc() );
    }
    final public int getCode( String longDesc ){
      return longDesc == null ? 0 : longDesc.hashCode();
    }
  }
  static final class ObviousExitsIndex implements Index{
    final public int getCode( Object s ){
      return getCode( ((Room)s).getObviousExits() );
    }
    final public int getCode( String[] obviousExits ){
      if( obviousExits == null )
        return 0;
      int code = 0;
      int i;
      for( i = 0; i < obviousExits.length; i++ )
        code ^= obviousExits[i] == null? 0 : obviousExits[i].hashCode();
      return code;
    }
  }
  static final class RegionIDIndex implements Index{
    public final int getCode( Object r ){
      return getCode( ((Room)r).getRegion() );
    }
    public final int getCode( int regionID ){
      return regionID;
    }
  }
  static final class UnassignedExitIndex implements Index{
    public final int getCode( Object r ){
      return getCode( ((Room)r).hasUnassignedExit() );
    }
    public final int getCode( boolean hasUnassignedExit ){
      return hasUnassignedExit ? 1 : 0;
    }
  }
}
