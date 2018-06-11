package mudclient.automapper;

import java.util.*;
import java.io.*;

import tools.*;
import tools.persistence.*;

// this should be the only way to get a SpecialExit object.
// Other objects should only keep transiet references 
// to specialExits.
public class SpecialExitManager implements Observer{
  private static HashSet names = new HashSet();

  private static RoomCmdSEIndex roomCmdSEIndex = new RoomCmdSEIndex();

  private int currentSpecialExitIndex = 0;

  private SoftIntMap specialExits;
  private IntMap changedSpecialExits;

  private File myFileName;
  private PersistentStore myPS;

  public SpecialExitManager( File fileName ){
    myFileName = fileName;
    specialExits = new SoftIntMap();
    changedSpecialExits = new IntMap();
    try{
      initializePS();
    }catch( IOException e ){
      e.printStackTrace( System.err );
    }
  }
  public int getCurrentSpecialExitIndex(){
    return currentSpecialExitIndex;
  }
  public int[] getAllIDs(){
    return myPS.getIDs();
  }
  public int[] getIDs( int roomID, String cmd ){
    return myPS.getIDs( new int[]{ roomCmdSEIndex.getCode( roomID, cmd ) } );
  }
  public SpecialExit get( int roomID, String cmd ){
    int i;
    int[] seids = getIDs( roomID, cmd );
    if( seids.length == 0 )
      return null;
    SpecialExit se;
    for( i = 0; i < seids.length; i++ ){
      se = get( seids[i] );
      if( se != null && se.getRoomID() == roomID && se.getCmd().equals( cmd ) )
        return se;
    }
    return null;
  }
  public SpecialExit get( int id ){
    SpecialExit se = getFromCache( id );
    if( se != null )
      return se;
    se = loadFromPS( id );
    if( se != null ){
      specialExits.put( id, se );
      se.addObserver( this );
    }
    return se;
  }
  public int createNewSpecialExit( int roomID, String cmd ){
    SpecialExit se;
    if( (se = get( roomID, cmd )) != null ) return se.getID();
    se = new SpecialExit( ++currentSpecialExitIndex, roomID, cmd );
    specialExits.put( se.getID(), se );
    try{
      updateSpecialExit( se.getID() );
    }catch( IOException e ){
      e.printStackTrace( System.err );
      markChanged( se );
    }
    se.addObserver( this );
    return se.getID();
  }

  private void unmarkChanged( int i ){
    changedSpecialExits.remove( i );
  }
  private void markChanged( SpecialExit r ){
    changedSpecialExits.put( r.getID(), r );
  }
  private void initializePS() throws IOException {
    myPS = new PersistentStore( myFileName, 
        new Index[]{roomCmdSEIndex},
        new SpecialExit(-1, -1, "") );
    currentSpecialExitIndex = myPS.getMaxID();
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
    int[] is = getChangedSpecialExits();
    for( i = 0; i < is.length; i++ ){
      updateSpecialExit( is[ i ] );
    }
    myPS.flush();
  }
  private void updateSpecialExit( int i ) throws IOException {
    
    // no use writing it if it's not in memory.
    SpecialExit r = getFromCache( i );
    if( r != null ){
      myPS.store( r );
    }
    unmarkChanged( i );
  }
  private int[] getChangedSpecialExits(){
    return changedSpecialExits.keys();
  }

  private SpecialExit getFromCache( int id ){
    return (SpecialExit)specialExits.get(id);
  }
  private SpecialExit loadFromPS( int id ) {
    SpecialExit r = new SpecialExit( -1, -1, null);
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
    // for now we onlyl observer specialExits
    SpecialExit r = (SpecialExit) o;
    markChanged( r );
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
  private static final class RoomCmdSEIndex implements Index{
    public int getCode( Object o ){
      SpecialExit se = (SpecialExit)o;
      return getCode( se.getRoomID(), se.getCmd() );
    }
    public int getCode( int i, String s ){
      return i ^ (s == null?0:s.hashCode());
    }
  }
}
