package mudclient.automapper;
import java.io.*;
import java.util.Observable;

import tools.IntSet;
import tools.Utils;

public class Region extends Observable implements tools.persistence.Storable{
  public static final Object EXIT_REMOVED = new Object();
  public static final Object EXIT_ADDED = new Object();
  public static final Object ENTRANCE_REMOVED = new Object();
  public static final Object ENTRANCE_ADDED = new Object();
  public static final Object PATH_LENGTHS_UPDATED = new Object();
  public static final Object NAME_CHANGED = new Object();
  private int stl;
  private int id;
  private String name;
  private int[] exits;
  private int[] entrances;
  private int[][] cachedExitLengths;

  Region( int id ){
    this.id = id;
    exits = new int[0];
    entrances = new int[0];
    cachedExitLengths = new int[0][0];
    stl = 20;
  }
  public int getID(){
    return id;
  }
  public void setName( String s ){
    if( s == name || name != null && name.equals( s ) )
      return;
    String oldName = name;
    int oldLength = name == null?0:name.getBytes().length;
    name = s;
    stl += ((s == null?0:s.getBytes().length) - oldLength);
    setChanged();
    notifyObservers( NAME_CHANGED );
  }
  public String getName(){
    return name;
  }

  boolean addExit( int exit, 
      RoomManager rm, 
      SpecialExitManager sem ){
    if( isExit( exit ) || rm.get( exit ).getRegion() != id  )
      return false;
    int[] newExits = new int[ (exits==null?0:exits.length)+1 ];
    int i = newExits.length;
    while( --i >= 0 ){
      if( i > 0 && exit < exits[ i-1 ] ){
        newExits[i] = exits[i-1];
      }else{
        newExits[i] = exit;
        break;
      }
    }
    while( --i >= 0 ){
      newExits[i] = exits[i];
    }
    exits = newExits;
    updatePathLengthCache( rm, sem );
    stl += (entrances.length +1 ) *4;
    setChanged();
    notifyObservers( EXIT_ADDED );
    return true;
  }
  boolean addEntrance( int entrance,
      RoomManager rm, 
      SpecialExitManager sem ){
    if(isEntrance( entrance ) || rm.get( entrance ).getRegion() != id  )
      return false;
    int[] newEntrances = new int[ (entrances==null)?1:(entrances.length+1) ];
    int i = newEntrances.length;
    while( --i >= 0 ){
      if( i > 0 && entrance < entrances[ i-1 ] ){
        newEntrances[i] = entrances[i-1];
      }else{
        newEntrances[i] = entrance;
        break;
      }
    }
    while( --i >= 0 ){
      newEntrances[i] = entrances[i];
    }
    entrances = newEntrances;
    updatePathLengthCache( rm, sem );
    stl += (exits.length +1 ) *4;
    setChanged();
    setChanged();
    notifyObservers( ENTRANCE_ADDED );
    return true;
  }
  void removeAllExits(){
    exits = new int[0];
    int i;
    for( i = 0; i < entrances.length; i++ )
      cachedExitLengths[i] = new int[0];
    stl -= exits.length*(entrances.length + 1)*4;
    setChanged();
    notifyObservers( EXIT_REMOVED );
  }
  void removeAllEntrances(){
    entrances = new int[0];
    cachedExitLengths = new int[0][];
    stl -= entrances.length*(exits.length + 1)*4;
    setChanged();
    notifyObservers( ENTRANCE_REMOVED );
  }
  boolean removeEntrance( int roomID ){
    int index = entranceIndex( roomID );
    if( index == -1 )
      return false;
    int i = entrances.length;
    int[] newArray = new int[ entrances.length - 1 ];
    int[][] newCachedExitLengths = new int[entrances.length - 1 ][];
    while( --i > index ){
      newArray[i-1] = entrances[i];
      newCachedExitLengths[i-1] = cachedExitLengths[i];
    }
    while( --i >= 0 ){
      newArray[i] = entrances[i];
      newCachedExitLengths[i] = cachedExitLengths[i];
    }
    entrances = newArray;
    cachedExitLengths = newCachedExitLengths;
    stl -= (exits.length + 1)*4;
    setChanged();
    notifyObservers( ENTRANCE_REMOVED );
    return true;
  }
  boolean removeExit( int roomID ){
    int index = exitIndex( roomID );
    if( index == -1 )
      return false;
    int i = exits.length;
    int[] newArray = new int[ exits.length - 1 ];
    while( --i > index )
      newArray[i-1] = exits[i];
    while( --i >= 0 )
      newArray[i] = exits[i];
    exits = newArray;
    int j;
    for( j = 0; j < entrances.length; j++ ){
      newArray = new int[ exits.length ];
      i = exits.length+1;
      while( --i > index )
        newArray[i-1] = cachedExitLengths[j][i];
      while( --i >= 0 )
        newArray[i] = cachedExitLengths[j][i];
      cachedExitLengths[ j ] = newArray;
    }
    stl -= (entrances.length + 1)*4;
    setChanged();
    notifyObservers( EXIT_REMOVED );
    return true;
  }
  private int entranceIndex( int roomID ){
    for( int i = 0; i < entrances.length; i++ )
      if( roomID == entrances[i] )
        return i;
    return -1;
  }
  boolean isEntrance( int roomID ){
    return entranceIndex( roomID ) != -1;
  }
  private int exitIndex( int roomID ){
    for( int i = 0; i < exits.length; i++ )
      if( roomID == exits[i] )
        return i;
    return -1;
  }
  boolean isExit( int roomID ){
    return exitIndex( roomID ) != -1;
  }
  public int[][] getCachedExitLengths( int entranceID ){
    if( ! isEntrance( entranceID ) )
      return new int[0][];
    int i;
    int j = entranceIndex( entranceID );
    int[][] returnValue = new int[exits.length][2];
    for( i = 0; i < exits.length; i++ ){
      returnValue [i][0] = exits[i];
      returnValue [i][1] = cachedExitLengths[j][i];
    }
    return returnValue;
  }
  public int[] getExits(){
    if ( exits == null ) return new int[ 0 ];
    int[] r = new int[ exits.length ];
    for( int i = 0; i < exits.length; i++ )
      r[i] = exits[i];
    return r;
  }
  public int[] getEntrances(){
    if ( entrances == null ) return new int[ 0 ];
    int[] r = new int[ entrances.length ];
    for( int i = 0; i < entrances.length; i++ )
      r[i] = entrances[i];
    return r;
  }
  public void updatePathLengthCache( 
      RoomManager rm, 
      SpecialExitManager sem ){
    int i;
    int j;
    int l;
    if( cachedExitLengths.length != entrances.length
        || entrances.length > 0 
           && cachedExitLengths[0].length != exits.length ){
      cachedExitLengths = new int[ entrances.length ][ exits.length ];
      setChanged();
    }
    for( i = 0; i < entrances.length; i++ ){
      int[] costs = Path
        .getIntraRegionCosts( entrances[ i ], exits, rm, sem );
      for( j = 0; j < costs.length; j++ ){
        if( cachedExitLengths[ i ][ j ] != costs[j] ){
          cachedExitLengths[ i ][ j ] = costs[ j ];
          setChanged();
        }
      }
    }
    notifyObservers( PATH_LENGTHS_UPDATED );
  }
  public void setState( DataInputStream is ) throws IOException{
    stl = is.readInt();
    id = is.readInt();
    int len = is.readInt();
    byte[] bs;
    if( len > 0 ){
      bs = new byte[ len ];
      is.readFully( bs );
      name = new String( bs );
    }else{
      name = null;
    }
    len = is.readInt();
    entrances = new int[ len ];
    int i, j, len2;
    for( i = 0; i < len; i++ )
      entrances[ i ] = is.readInt();
    len2 = is.readInt();
    exits = new int[ len2 ];
    for( i = 0; i < len2; i++ )
      exits[i] = is.readInt();
    cachedExitLengths = new int[ len ][ len2 ];
    for( i = 0; i < len; i++ )
      for( j = 0; j < len2; j++ )
        cachedExitLengths[ i ][ j ] = is.readInt();
  }
  public void getState( DataOutputStream os ) throws IOException{
    os.writeInt( stl );
    os.writeInt( id );
    if( name == null ){
      os.writeInt( 0 );
    }else{
      byte[] bs = name.getBytes();
      os.writeInt( bs.length );
      os.write( bs, 0, bs.length );
    }
    int i, j;
    if( entrances == null ){
      os.writeInt( 0 );
    }else{
      os.writeInt( entrances.length );
      for( i = 0; i < entrances.length; i++ )
        os.writeInt( entrances[i] );
    }
    if( exits == null ){
      os.writeInt( 0 );
    }else{
      os.writeInt( exits.length );
      for( i = 0; i < exits.length; i++ )
        os.writeInt( exits[i] );
    }
    if( entrances != null && entrances.length > 0 
        && exits != null && exits.length > 0){
      for( i = 0; i < entrances.length; i++ )
        for( j = 0; j < exits.length; j++ )
          os.writeInt( cachedExitLengths[ i ][ j ] );
    }
  }
  public int getStreamedLength(){
    return stl;
  }
}
