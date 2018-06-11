package mudclient.automapper;

import java.util.*;
import java.io.*;

import tools.*;

// this should be the only way to get a Room object.
// Other objects should only keep transient references 
// to rooms.
public class MudMap implements Observer{
  public static final String[] stringArrayObj = new String[0];
  private static HashMap maps = new HashMap();

  private String name;

  private HashMap specialRooms;

  private RoomManager rmMgr;
  private RegionManager rgMgr;
  private SpecialExitManager seMgr;

  private File myDir;
  private File specialRoomsFile;

  public MudMap( String mapDataDirName, String name ){
    if( maps.keySet().contains( name ) ){
      throw new NameCollisionException( "RoomManager with name \"" 
          + name + "\" already exists." );
    }
    this.name = name;
    maps.put( name, this );
    File parentDir = new File( mapDataDirName );
    myDir = new File( parentDir, name );
    if( ! myDir.exists() ){
      myDir.mkdir();
    }
    specialRoomsFile = new File( myDir, "SpecialRooms.dat" );
    if( ! specialRoomsFile.exists() ){
      try{
        specialRoomsFile.createNewFile();
        DataOutputStream os = new DataOutputStream(
            new FileOutputStream( specialRoomsFile, false ) );
        os.writeInt( 0 );
        os.flush();
        os.close();
      }catch(IOException e ){
        e.printStackTrace( System.err );
      }
    }
    rollback();
  }

  public void rollback(){
    rmMgr = null;
    rgMgr = null;
    seMgr = null;
    File rmFile = new File( myDir, "Rooms.dat" );
    rmMgr = new RoomManager( rmFile );
    File rgFile = new File( myDir, "Regions.dat" );
    rgMgr = new RegionManager( rgFile );
    File seFile = new File( myDir, "SpecialExit.dat" );
    seMgr = new SpecialExitManager( seFile );
    specialRooms = new HashMap();
    loadSpecialRooms();
  }
  public String getName(){
    return name;
  }
  public String[] getSpecialRoomNames(){
    return (String[])specialRooms.keySet().toArray( stringArrayObj );
  }
  public boolean registerSpecialRoom( String s, int i){
    if( specialRooms.containsKey( s ) )
      return false;
    specialRooms.put( s, new Integer( i ) );
    return true;
  }
  public int removeSpecialRoom( String s ){
    return ((Integer)specialRooms.remove( s )).intValue();
  }
  public int getSpecialRoomID( String s ){
    Integer i = (Integer)specialRooms.get( s );
    return (i == null)?-1:i.intValue();
  }
  private void loadSpecialRooms(){
    try{
      DataInputStream is = new DataInputStream(
          new FileInputStream( specialRoomsFile ) );
      int size = is.readInt();
      int i, len;
      byte[] bs;
      String key;
      for( i = 0; i < size; i++ ){
        len = is.readInt();
        bs = new byte[ len ];
        is.readFully( bs );
        key = new String( bs );
        specialRooms.put( key, new Integer( is.readInt() ) );
      }
      is.close();
    }catch( IOException e ){
      e.printStackTrace( System.err );
    }
  }
  private boolean saveSpecialRooms(){
    try{
      DataOutputStream os = new DataOutputStream(
          new FileOutputStream( specialRoomsFile, false ) );
      os.writeInt( specialRooms.size() );
      Iterator it = specialRooms.keySet().iterator();
      String key;
      byte[] bs;
      while( it.hasNext() ){
        key = (String)it.next();
        bs = key.getBytes();
        os.writeInt( bs.length );
        os.write( bs );
        os.writeInt( ((Integer)specialRooms.get( key )).intValue() );
      }
      os.flush();
      os.close();
      return true;
    }catch( IOException e ){
      e.printStackTrace( System.err );
      return false;
    }
  }
  public int[] getRoomsInRegion( int regionID ){
    return rmMgr.getRoomsMatching( null, null, null, new int[]{regionID}, null);
  }
  public int[] getRegions(){
    return rgMgr.getIDs();
  }
  public int[] getRegions( String name ){
    return rgMgr.getIDsByName( name );
  }
  public Region getRegion( int id ){
    return rgMgr.get( id );
  }
  public Room getRoom( int id ){
    Room r = rmMgr.get( id );
    if( r != null )
      r.addObserver( this );
    return r;
  }
  public int[] getSpecialExits(){
    return seMgr.getAllIDs();
  }
  public SpecialExit getSpecialExit( int roomId, String cmd ){
    SpecialExit se = seMgr.get( roomId, cmd );
    return registerSpecialExit( se );
  }
  public SpecialExit getSpecialExit( int id ){
    SpecialExit se = seMgr.get( id );
    return registerSpecialExit( se );
  }
  private SpecialExit registerSpecialExit( SpecialExit se ){
    if( se != null ){
      se.setManagers( rmMgr, rgMgr, seMgr );
      Room r = rmMgr.get( se.getRoomID() );
      if( r != null ){
        se.addObserver( r );
        r.addObserver( this );
      }
    }
    return se;
  }
  public int createNewRegion(){
    return rgMgr.createNewRegion();
  }
  public int createNewRoom( int regionID ){
    return rmMgr.createNewRoom( regionID );
  }
  public int createNewSpecialExit( int roomID, String cmd ){
    return seMgr.createNewSpecialExit( roomID, cmd );
  }
  public Path getShortestPath( int roomA, int roomB ){
    return Path.getShortestPath( roomA, roomB, rmMgr, rgMgr, seMgr );
  }
  public void update( Observable o, Object arg ){
    // for now we only observer rooms
    if( o instanceof Room ){
      if( arg != Room.EXITS )
        return;
      Room r = (Room) o;
      updateRoomExits( r );
    }
  }
  private void updateRoomExits( Room r ){
    String[] es = r.getExits();
    int destCount = 0;
    int i, j, k;
    for( i = 0; i < es.length; i++ )
      if( r.getDest( es[i] ) != Exit.UNASSIGNED_DESTINATION )
        if( r.getDest( es[i] ) != Exit.SPECIAL_EXIT )
          destCount++;
        else
          destCount += seMgr.get( r.getID(), es[i] )
            .getDestinations().length;
    int[] dests = new int[ destCount ];
    for( i = 0, j = 0; j < es.length; j++ )
      if( r.getDest( es[j] ) != Exit.UNASSIGNED_DESTINATION )
        if( r.getDest( es[j] ) != Exit.SPECIAL_EXIT )
          dests[i++] = r.getDest( es[j] );
        else{
          int[] specialDests = seMgr.get( r.getID(), es[i] )
            .getDestinations();
          for(k = 0; k < specialDests.length; k++ )
            dests[i++] = specialDests[k];
        }
    Room dest;
    boolean isExit = false;
    for( i = 0; i < dests.length; i++ ){
      dest = rmMgr.get( dests[ i ] );
      if( dest != null && dest.getRegion() != r.getRegion() ){
        rgMgr.get( dest.getRegion() ).addEntrance(dest.getID(), rmMgr, seMgr );
        if( ! isExit ){
          rgMgr.get( r.getRegion() ).addExit( r.getID(), rmMgr, seMgr );
          isExit = true;
        }
      }
    }
    if( ! isExit && rgMgr.get( r.getRegion() ).isExit( r.getID() ) )
      rgMgr.get( r.getRegion() ).removeExit( r.getID() );
    // unfortunately, there's no way to check for entrance going away.
  }
  public boolean save(){
    return rgMgr.save( rmMgr, seMgr ) 
      && rmMgr.save() 
      && seMgr.save() 
      && saveSpecialRooms();
  }

  public void finalize(){
    maps.remove( name );
  }
  public static boolean mapExists( String mapDataDirName, String name ){
    //File f = new File( System.getProperty("HOME")+"automapper.dat");
    File f = new File( mapDataDirName );
    if( ! f.exists() ){
      return false;
    }
    f = new File( f, name );
    if( ! f.exists() ){
      return false;
    }
    f = new File( f, "SpecialRooms.dat" );
    if( ! f.exists() ){
      return false;
    }
    return true;
  }
}
