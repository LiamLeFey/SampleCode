package mudclient.automapper;

import java.io.*;
import java.util.*;
import tools.persistence.Storable;
import tools.IntSet;

public class Room extends Observable implements Storable, Observer{
  // These are used for type on getNotes and getExits,
  // and also as the messages (what type of changes) for
  // notify observers
  private static final String[] STRINGS = new String[0];
  public static final Object EXITS = new Object();
  public static final Object SHORT_DESC = new Object();
  public static final Object LONG_DESC = new Object();
  public static final Object NOTES = new Object();

  private int stl;
  private int id;
  private int region;
  private String longDesc;
  private String shortDesc;
  private ArrayList notes;
  private HashMap exits;
  private HashMap obviousExits;
  private String[] cachedExits = null;
  private String[] cachedObviousExits = null;

  Room( int id, int region ){
    this.id = id;
    this.region = region;
    exits = new HashMap( 3 );
    obviousExits = new HashMap( 3 );
    stl = 28;
  }
  public int getStreamedLength(){
    return stl;
  }
  public int getRegion(){
    return region;
  }
  public int getID(){
    return id;
  }
  public String getLongDesc(){
    return longDesc;
  }
  public String getShortDesc(){
    return shortDesc;
  }
  public void setLongDesc( String s ){
    if("".equals(s)) s = null;
    int oldLength = longDesc == null? 0 : (longDesc.getBytes().length);
    longDesc = s;
    stl = stl - oldLength + (s == null?0:(s.getBytes().length));
    setChanged();
    notifyObservers( LONG_DESC );
  }
  public void setShortDesc( String s ){
    if("".equals(s)) s = null;
    int oldLength = shortDesc == null? 0 : (shortDesc.getBytes().length);
    shortDesc = s;
    stl = stl - oldLength + (s == null?0:(s.getBytes().length));
    setChanged();
    notifyObservers( SHORT_DESC );
  }
  public void addNote( String note ){
    if( note == null || note.equals("")) return;
    if( notes == null ){
      notes = new ArrayList();
    }
    notes.add( note );
    setChanged();
    stl = stl + note.getBytes().length + 4;
    notifyObservers( NOTES );
  }
  public void removeNote( String note ){
    if(note == null || notes == null) return;
    if(notes.remove( note )){
      stl = stl - (note.getBytes().length+4);
      setChanged();
      notifyObservers( NOTES );
    }
  }
  public String[] getNotes(){
    return (notes == null)?
      new String[0]
      :(String[])notes.toArray( STRINGS );
  }
  public String[] getExits(){
    if( cachedExits == null )
      cachedExits = (String[])exits.keySet().toArray( STRINGS );
    return (String[])cachedExits.clone();
  }
  public String[] getObviousExits(){
    if( cachedObviousExits == null )
      cachedObviousExits = (String[])obviousExits.keySet().toArray( STRINGS );
    return (String[])cachedObviousExits.clone();
  }
  public String[] getHiddenExits(){
    String[] hes = new String[ exits.size() - obviousExits.size() ];
    if( hes.length != 0 ){
      String[] es = getExits();
      int i, j;
      for( j = 0, i = 0; i < es.length; i++ )
        if( ! isObviousExit( es[i] ) )
          hes[j++] = es[j];
    }
    return hes;
  }
  public boolean hasExit( String s ){
    return exits.containsKey( s );
  }
  public boolean isObviousExit( String s ){
    return obviousExits.containsKey( s );
  }
  public boolean isHiddenExit( String s ){
    return hasExit( s ) && ! isObviousExit( s );
  }
  public boolean hasUnassignedExit(){
    Iterator it = exits.values().iterator();
    while( it.hasNext() )
      if( ((Exit)it.next()).getDestination() == Exit.UNASSIGNED_DESTINATION )
        return true;
    return false;
  }
  public int getDest( String s ){
    return ((Exit)exits.get( s )).getDestination();
  }
  // this weeds out all the UNASSIGNED and SPECIAL destinations.and duplicates
  public int[] getDestinations(){
    try{
      return getDestinations( null );
    } catch (IOException cantHappen){
      return null;
    }
  }
  // this weeds out all the UNASSIGNED destinations.and duplicates
  public int[] getDestinations( SpecialExitManager seMgr ) throws IOException{
    IntSet dests = new IntSet( exits.size() );
    Iterator it = exits.values().iterator();
    Exit e;
    int i, j;
    while(it.hasNext()){
      e = (Exit)it.next();
      i = e.getDestination();
      if( i == Exit.SPECIAL_EXIT ){
        if( seMgr != null ){
          int[] seDests = seMgr.get( id, e.getCommand() ).getDestinations();
          for( j = 0; j < seDests.length; j++ )
            if( seDests[j] != Exit.UNASSIGNED_DESTINATION )
              dests.add( seDests[j] );
        }
      }else if( i != Exit.UNASSIGNED_DESTINATION ){
        dests.add( i );
      }
    }
    return dests.toArray();
  }
  public boolean addExit( String s ){
    return addExit( s, true );
  }
  public boolean addExit( String s, boolean obvious ){
    if( hasExit( s ) )
      return false;
    Exit e = new Exit( s );
    exits.put( s, e );
    cachedExits = null;
    if( obvious ){
      obviousExits.put( s, e );
      cachedObviousExits = null;
    }
    stl += (1+((Exit)exits.get( s )).getStreamedLength());
    setChanged();
    notifyObservers( EXITS );
    return true;
  }
  public boolean removeExit( String s ){
    if( ! hasExit( s ) )
      return false;
    stl -= (1+((Exit)exits.remove( s )).getStreamedLength());
    cachedExits = null;
    if( obviousExits.containsKey( s ) ){
      obviousExits.remove( s );
      cachedObviousExits = null;
    }
    setChanged();
    notifyObservers( EXITS );
    return true;
  }
  public boolean setDestination( String s, int dest ){
    if( ! hasExit( s ) )
      return false;
    ((Exit)exits.get( s )).setDestination( dest );
    setChanged();
    notifyObservers( EXITS );
    return true;
  }
  public void update( Observable o, Object arg ){
    if( o instanceof SpecialExit && arg == SpecialExit.DESTINATIONS_CHANGED ){
      setChanged();
      notifyObservers( EXITS );
    }
    // otherwise, we don't care.
  }
  public void setState( DataInputStream is ) throws IOException{
    stl = is.readInt();
    id = is.readInt();
    region = is.readInt();
    longDesc = getStringFromIS( is );
    shortDesc = getStringFromIS( is );
    int len = is.readInt();
    int i;
    if(len > 0)
      notes = new ArrayList();
    else
      notes = null;
    for( i = 0; i < len; i++ )
      notes.add( getStringFromIS( is ) );
    len = is.readInt();
    exits = new HashMap( len );
    obviousExits = new HashMap( len );
    for( i = 0; i < len; i++ ){
      Exit x = new Exit();
      x.setState( is );
      exits.put( x.getCommand(), x );
      if( is.readBoolean() );
        obviousExits.put( x.getCommand(), x );
    }
  }
  private String getStringFromIS( DataInputStream is ) throws IOException{
    int i;
    byte[] bs;
    i = is.readInt();
    if( i > 0 ){
      bs = new byte[ i ];
      is.readFully( bs );
      return new String( bs );
    }
    return null;
  }
  public void getState( DataOutputStream os ) throws IOException{
    os.writeInt( stl );
    os.writeInt( id );
    os.writeInt( region );
    writeToOS( longDesc, os );
    writeToOS( shortDesc, os );
    int len;
    int i;
    if (notes == null ){
      os.writeInt( 0 );
    }else{
      len = notes.size();
      os.writeInt( len );
      for( i = 0; i < len; i++ )
        writeToOS( (String)notes.get( i ), os );
    }
    len = exits.size();
    os.writeInt( len );
    Iterator it = exits.values().iterator();
    Exit x;
    while( it.hasNext() ){
      x = (Exit)it.next();
      x.getState( os );
      if( isObviousExit( x.getCommand() ) )
        os.writeBoolean( true );
      else
        os.writeBoolean( false );
    }
  }
  private void writeToOS( String s, DataOutputStream os ) throws IOException{
    if( s == null || s.equals( "" ) ){
      os.writeInt( 0 );
    } else {
      byte[] bs = s.getBytes();
      os.writeInt( bs.length );
      os.write( bs, 0, bs.length );
    }
  }
}
