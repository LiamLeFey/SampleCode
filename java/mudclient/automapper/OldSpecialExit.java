package mudclient.automapper;
import java.io.*;

import java.util.Observable;
import tools.persistence.Storable;

/** This is a problematic exit.
 * If an exit is always available, always works, and gets you 
 * where you're going immediately, it is a normal exit.  This
 * is for those casses where one of the above is not true.
 * We split things up into two sections: availability, and
 * success.  Those things which happen before the command, and
 * those that happen afterwards.
 *
 * I think it would be better to model this with a state machine.
 * It might be a good idea to attach state machines to rooms as
 * well...
 */
public class SpecialExit extends Observable implements Storable{
  public static final Object DESTINATIONS_CHANGED = new Object();
  public static final Object MECHANICS_CHANGED = new Object();

  public static final String BREAKPATH = "SE_REPATH";

  public static final byte PERIODIC_AVAILABILITY = 0x01; ;
  public static final byte PERIODIC_DESTINATION = 0x02; ;
  public static final byte CONTROLLED = 0x04;
  public static final byte SUMMONABLE = 0x08;
  public static final byte DELAYED = 0x10;
  public static final byte RANDOM_DESTINATION = 0x20;

  private static final int DELAY_COST = 300;
  private static final int SUMMON_COST = 1;
  private static final int CHECK_COST = 20;
  private static final int PERIODIC_COST = 300;

  private SpecialExitManager seMgr;
  private RoomManager rmMgr;
  private RegionManager rgMgr;

  private int stl;
  private int id;
  private int roomID;
  private String cmd;
  private byte type;

  private String availableCheck;
  private String[] availableTrigger;
  private String[] summon;
  private String[] summonTrigger;
  private String arrivalTrigger;
  private int[] destination;
  private int[] destinationCost;
  private String destinationCheck;
  private String[] destinationTrigger;


  public SpecialExit( int id, int roomID, String cmd ){
    this.id = id;
    this.roomID = roomID;
    this.cmd = cmd;
    type = 0x00;
    stl = getLen( cmd ) + 52;
    availableTrigger = new String[0];
    summon = new String[0];
    summonTrigger = new String[0];
    destination = new int[0];
    destinationCost = new int[0];
    destinationTrigger = new String[0];
  }
  public int getID(){
    return id;
  }
  public int getRoomID(){
    return roomID;
  }
  public String getCmd(){
    return cmd;
  }
  public int getDestinationIndex( int dest ){
    int i;
    for( i = 0; i < destination.length; i++ )
      if( destination[i] == dest )
        return i;
    return -1;
  }
  public String getSequence( int destinationIndex ){
    if( destinationIndex < 0 || destinationIndex > destination.length )
      return null;
    StringBuffer sb = new StringBuffer();
    if( availableCheck != null )
      sb.append( availableCheck + "\n" );
    if( availableTrigger[destinationIndex] != null )
      sb.append( "SEWAITFOR:" + availableTrigger[destinationIndex] + "\n" );
    if( summon[destinationIndex] != null )
        sb.append( summon[destinationIndex] + "\n" );
    if( summonTrigger[destinationIndex] != null )
        sb.append( "SEWAITFOR:" + summonTrigger[destinationIndex] + "\n" );
    sb.append( cmd + "\n" );
    if( arrivalTrigger != null )
      sb.append( "SEWAITFOR:" + arrivalTrigger + "\n" );
    if( destinationCheck != null )
      sb.append( destinationCheck + "\n" );
    if( (type & RANDOM_DESTINATION) == 0 ){
      if( destinationTrigger != null )
        sb.append( "SEWAITFOR:" + destinationTrigger[destinationIndex] + "\n" );
    }else{
      sb.append( BREAKPATH );
    }
    return sb.toString();
  }
  public String getAvailableCheck( ){
    return availableCheck;
  }
  public void setAvailableCheck( String cmd ){
    if( ("").equals( cmd ) )
      cmd = null;
    if( cmd == availableCheck || cmd != null && cmd.equals( availableCheck ))
      return;
    if( availableCheck != null )
      stl -= availableCheck.getBytes().length;
    availableCheck = cmd;
    stl += (cmd == null) ? 0 : cmd.getBytes().length;
    setChanged();
    type |= PERIODIC_AVAILABILITY;
    notifyObservers( MECHANICS_CHANGED );
  }
  public String getArrivalTrigger( ){
    return arrivalTrigger;
  }
  public void setArrivalTrigger( String trig ){
    if( ("").equals( trig ) )
      trig = null;
    if( trig == arrivalTrigger || trig != null && trig.equals( arrivalTrigger ))
      return;
    if( arrivalTrigger != null )
      stl -= arrivalTrigger.getBytes().length;
    arrivalTrigger = trig;
    stl += (trig == null) ? 0 : trig.getBytes().length;
    setChanged();
    type |= DELAYED;
    notifyObservers( MECHANICS_CHANGED );
  }
  private static final int getLen( String s ){
    if( s == null )
      return 0;
    return s.getBytes().length;
  }
  private static final String[] addString( String[] ss, String s ){
    int len = ss == null?1:ss.length+1;
    String[] ns = new String[ len ];
    ns[ --len ] = ("").equals(s)? null : s;
    while( --len >= 0 )
      ns[len] = ss[len];
    return ns;
  }
  void setManagers( RoomManager rmMgr, 
      RegionManager rgMgr, 
      SpecialExitManager seMgr ){
    this.rmMgr = rmMgr;
    this.rgMgr = rgMgr;
    this.seMgr = seMgr;
  }
  boolean managersSet(){
    return rmMgr != null && rgMgr != null && seMgr != null;
  }

  public void addDestination( 
      String availableTrig, 
      String summonCmd, 
      String summonTrig, 
      int dest, 
      String destinationTrig ){
    availableTrigger = addString( availableTrigger, availableTrig );
    stl += getLen( availableTrig );
    summon = addString( summon, summonCmd );
    stl += getLen( summonCmd );
    summonTrigger = addString( summonTrigger, summonTrig );
    stl += getLen( summonTrig );
    destinationTrigger = addString( destinationTrigger, destinationTrig );
    stl += getLen( destinationTrig );
    int[] ni = new int[ destination.length+1 ];
    ni[ destination.length ] = dest;
    int i = destination.length;
    while( --i >= 0 )
      ni[i] = destination[i];
    destination = ni;
    stl += 24;
    figureType();
    if( managersSet() )
      calculateCosts();
    setChanged();
    notifyObservers( DESTINATIONS_CHANGED );
  }
  private void figureType(){
    int aLen = destination == null ? 0 : destination.length;
    type = 0;
    if( availableCheck != null || aLen > 0 && availableTrigger[0] != null )
      type |= PERIODIC_AVAILABILITY;
    if( aLen > 1 
        && availableTrigger[0] != null 
        && availableTrigger[1] != null 
        && ! availableTrigger[0].equals( availableTrigger[1] ) )
      type |= PERIODIC_DESTINATION;
    if( aLen > 0 && summon[0] != null )
      type |= SUMMONABLE;
    if( aLen > 1 
        && summon[0] != null 
        && summon[1] != null 
        && ! summon[0].equals( summon[1] ) )
      type |= CONTROLLED;
    if( arrivalTrigger != null || aLen > 0 && summonTrigger[0] != null )
      type |= DELAYED;
    if( aLen > 1 
        && (type & PERIODIC_DESTINATION) == 0
        && (type & CONTROLLED) == 0 )
      type |= RANDOM_DESTINATION;
  }
  public String getDestinationCheck( ){
    return destinationCheck;
  }
  public void setDestinationCheck( String cmd ){
    if( ("").equals( cmd ) )
      cmd = null;
    if( cmd==destinationCheck || cmd!=null && cmd.equals( destinationCheck ))
      return;
    if( destinationCheck != null )
      stl -= destinationCheck.getBytes().length;
    destinationCheck = cmd;
    stl += (cmd == null) ? 0 : cmd.getBytes().length;
    setChanged();
    notifyObservers( MECHANICS_CHANGED );
  }
  public String[] getAvailableTriggers(){
    return (String[])availableTrigger.clone();
  }
  public String[] getSummonCmds(){
    return (String[])summon.clone();
  }
  public String[] getSummonTriggers(){
    return (String[])summonTrigger.clone();
  }
  public int[] getDestinations(){
    return (int[])destination.clone();
  }
  public String[] getDestinationTriggers(){
    return (String[])destinationTrigger.clone();
  }
  public int[] getDestinationCosts(){
    return (int[])destinationCost.clone();
  }
  public String getDestinationTrigger( int index ){
    if( index < 0 || index > destinationTrigger.length )
      return null;
    return destinationTrigger[ index ];
  }
  public int getDestination( String key ){
    int i;
    if( destination.length == 1 )
      return destination[0];
    if( key != null )
      for( i = 0; i < destination.length; i++ )
        if( key.equals( destinationTrigger[i] ) )
          return destination[i];
    return Exit.UNASSIGNED_DESTINATION;
  }
  private void calculateCosts( ){
    int baseCost = 1;
    if( availableCheck != null )
      baseCost += CHECK_COST;
    if( arrivalTrigger != null )
      baseCost += DELAY_COST;
    if( destinationCheck != null )
      baseCost += CHECK_COST;

      baseCost += PERIODIC_COST;
      baseCost += DELAY_COST;
    destinationCost = new int[ destination.length ];
    int i;
    for( i = 0; i < destination.length; i++ ){
      destinationCost[i] = baseCost;
      if( availableTrigger[i] != null )
        destinationCost[i] += PERIODIC_COST;
      if( summon[i] != null )
        destinationCost[i] += 1;
      if( summonTrigger[i] != null )
        destinationCost[i] += DELAY_COST;
      if( destinationTrigger != null )
        destinationCost[i] += DELAY_COST;
      if( (type & RANDOM_DESTINATION) != 0 ){
        // the toughie.
        int j, cost;
        int averageCost = 0;
        for( j = 0; j < destination.length; j++ ){
          cost = Path.getPathCost( 
              destination[j],
              destination[i],
              rmMgr, rgMgr, seMgr );
          if( cost < 0 )
            cost = 500;
          averageCost += cost;
        }
        if( destinationCheck == null ){
          averageCost /= destination.length;
        }else{
          averageCost /= destination.length+1;
          averageCost += CHECK_COST;
        }
        destinationCost[i] += averageCost;
      }
    }
  }
  private static final String[] remove( String[] os, int i ){
    if( os == null || os.length < 1 )
      return null;
    if( i < 0 || i >= os.length )
      return os;
    String[] no = new String[ os.length-1 ];
    int j;
    for( j = 0; j < i; j++ )
      no[j] = os[j];
    for( j = i; j < no.length; j++ )
      no[j] = os[j+1];
    return no;
  }
  public void removeDestination( int destinationIndex ) throws IOException{
    int i, j;
    int[] ni;
    if( destinationIndex < 0 || destinationIndex >= destination.length )
      return;
    ni = new int[ destination.length-1 ];
    for( i = 0; i < destinationIndex; i++ )
      ni[i] = destination[i];
    for( i = destinationIndex; i < ni.length; i++ )
      ni[i] = destination[i+1];
    destination = ni;
    stl -= getLen(availableTrigger[destinationIndex]);
    availableTrigger = remove( availableTrigger, destinationIndex );
    stl -= getLen(summon[destinationIndex]);
    summon = remove( summon, destinationIndex );
    stl -= getLen(summonTrigger[destinationIndex]);
    summonTrigger = remove( summonTrigger, destinationIndex );
    stl -= getLen(destinationTrigger[destinationIndex]);
    destinationTrigger = remove( destinationTrigger, destinationIndex );
    stl -= 24;
    if( managersSet() )
      calculateCosts();
  }
  public byte getType(){
    return type;
  }
  public int getStreamedLength(){
    return stl;
  }
  private static final String readString( 
      DataInputStream is ) throws IOException {
    int len = is.readInt();
    if( len == 0 )
      return null;
    byte[] bs = new byte[ len ];
    is.readFully( bs );
    return new String( bs );
  }
  private static final String[] readStrings( 
      DataInputStream is ) throws IOException {
    String[] retVal;
    int len = is.readInt();
    retVal = new String[ len ];
    int i;
    for( i =0; i < len; i++ )
      retVal[i] = readString( is );
    return retVal;
  }
  private static final int[] readInts(
      DataInputStream is ) throws IOException {
    int[] retVal;
    int len = is.readInt();
    retVal = new int[ len ];
    int i;
    for( i =0; i < len; i++ )
      retVal[i] = is.readInt();
    return retVal;
  }


  public void setState( DataInputStream is ) throws IOException {
    int len;
    int i;
    stl = is.readInt();
    id = is.readInt();
    roomID = is.readInt();
    cmd = readString( is );
    availableCheck = readString( is );
    availableTrigger = readStrings( is );
    summon = readStrings( is );
    summonTrigger = readStrings( is );
    arrivalTrigger = readString( is );
    destination = readInts( is );
    destinationCost = readInts( is );
    destinationCheck = readString( is );
    destinationTrigger = readStrings( is );
  }
  private static final void writeString( 
      DataOutputStream os, String s ) throws IOException {
    if( s == null || ("").equals( s ) ){
      os.writeInt( 0 );
      return;
    }
    byte[] bs = s.getBytes();
    os.writeInt( bs.length );
    os.write( bs );
  }
  private static final void writeStrings( 
      DataOutputStream os, String[] s ) throws IOException {
    os.writeInt( s.length );
    for( int i = 0; i < s.length; i++ )
      writeString( os, s[i] );
  }
  private static final void writeInts( 
      DataOutputStream os, int[] ints ) throws IOException{
    os.writeInt( ints.length );
    for( int i = 0; i < ints.length; i++ )
      os.writeInt( ints[i] );
  }

  public void getState( DataOutputStream os ) throws IOException {
    byte[] bs;
    int i;
    os.writeInt( stl );
    os.writeInt( id );
    os.writeInt( roomID );
    writeString( os, cmd );
    writeString( os, availableCheck );
    writeStrings( os, availableTrigger );
    writeStrings( os, summon );
    writeStrings( os, summonTrigger );
    writeString( os, arrivalTrigger );
    writeInts( os, destination );
    writeInts( os, destinationCost );
    writeString( os, destinationCheck );
    writeStrings( os, destinationTrigger );
  }
}
