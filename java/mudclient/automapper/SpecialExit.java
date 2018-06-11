package mudclient.automapper;
import java.io.*;

import java.util.Observable;
import tools.persistence.Storable;

/** This is a problematic exit.
 * If an exit is always available, always works, and gets you 
 * where you're going immediately, it is a normal exit.  This
 * is for those casses where one of the above is not true.
 *
 * We model it as a state machine, each node
 * representing a state and each vertex representing either
 * a user command or a String received from the mud.
 *
 * It might be a good idea to attach state machines to rooms as
 * well... Heck, state machine logic could run your bot...
 */
public class SpecialExit extends Observable implements Storable{
  // indications as to what changed when Observable.notifyObservers
  // is called.
  public static final Object DESTINATIONS_CHANGED = new Object();
  public static final Object MECHANICS_CHANGED = new Object();

  public static final int LATENCY_DELAY_COST = 30;
  public static final int COST_PER_SECOND = 100;
  // indication that the pathing algorithm needs to
  // re-calculate the rest of the path once the exit is traversed
  // (for exits that result in a non-fixed resultant location.)
  public static final String BREAKPATH = "SE_REPATH";

  private static final int SUMMON_COST = 1;

  // the managers to get the other map objects, etc.
  private SpecialExitManager seMgr;
  private RoomManager rmMgr;
  private RegionManager rgMgr;

  // streamed length (for Storable)
  private int stl;
  private int id;
  // origin room ID
  // hmm, what about exits that move around to more than one room...
  // maybe they are exits in each room that are occasionally available?
  private int roomID;
  // The name of the exit.  Usually it will be the primary command
  // that is issued, but it doesn't need to be. (The name could be
  // 'bridge' and the command could be 'say bridge', like on the
  // Enterprise)
  private String name;
  // the nodes of the state machine.
  private SENode[] nodes;
  // this tracks the lowest cost of reaching each node.  It is not created
  // until needed, and not saved to disk since it is discoverable, and
  // this conserves storage space.  Integer.MAX_VALUE indicates an
  // unreachable node.
  private int[] nodeCosts;
  // these likewise are discovered rather than stored.
  private int[] destinations;
  private int[] destinationCosts;



  /** Constructor.
   *  @param id the Storable id, unique to this SpecialExit for this Manager.
   *  @param roomID the originating room's Storable ID.
   *  @param name the name of the exit.  The roomID and name constitute
   *  a lookup key.
   */
  public SpecialExit( int id, int roomID, String name ){
    this.id = id;
    this.roomID = roomID;
    this.name = name;
    stl = getLen( name ) + 20;
    // I know it's inefficient to create it this way, but creation 
    // will be a rare occurance.  Once it's created, it will be loaded
    // from disk, which is much more efficient.
    nodes = new SENode[ 0 ];
    addNode();
    setNodeName( 0, "origin" );
  }
  public int getID(){
    return id;
  }
  public int getRoomID(){
    return roomID;
  }
  public String getName(){
    return name;
  }
  private boolean transientsSet(){
    return nodeCosts != null &&
          destinations != null &&
          destinationCosts != null;
  }
  private void calculateTransients(){
    nodeCosts = new int[ nodes.length ];
    int i;
    for( i = 0; i < nodeCosts.length; i++ )
      nodeCosts[i] = Integer.MAX_VALUE;
    // the origin costs zero
    nodeCosts[ 0 ] = 0;
    calculateNodeCosts( 0 );
    int j = 0;
    for( i = 0; i < nodeCosts.length; i++ ){
      if( nodeCosts[i] < Integer.MAX_VALUE && nodes[i].roomID >= 0 ) j++;
    }
    destinations = new int[ j ];
    destinationCosts = new int[ j ];
    for( i = 0; i < nodeCosts.length; i++ ){
      if( nodeCosts[i] < Integer.MAX_VALUE && nodes[i].roomID >= 0 ){
        destinationCosts[--j] = nodeCosts[i];
        destinations[j] = nodes[i].roomID;
      }
    }
  }
  // assumes that nodeCosts exists and has been initialized
  // basically a depth first recursive traversal of the tree
  private void calculateNodeCosts( int i ){
    if( i < 0 || i >= nodes.length ) return;
    SENode n = nodes[i];
    SECommand c;
    SETrigger t;
    if( n.commands != null )
      for( j = 0; j < n.commands.length; j++ ){
        c = n.commands[j];
        if( nodeCosts[i]+1 < nodeCosts[ c.node ] ){
          nodeCosts[ c.node ] = nodeCosts[i]+1;
          calculateNodeCosts( c.node );
        }
      }
    if( n.triggers != null )
      for( j = 0; j < nodes[i].triggers.length; j++ ){
        t = n.triggers[j];
        if( nodeCosts[i] + t.time*COST_PER_SECOND + LATENCY_DELAY_COST
                < nodeCosts[ t.node ] ){
          nodeCosts[ t.node ] = nodeCosts[i] +
                      t.time*COST_PER_SECOND + LATENCY_DELAY_COST;
          calculateNodeCosts( t.node );
        }
      }
  }
  // get the minimum cost of reaching each destination
  // also has the side effect of ensuring that nodeCosts is created.
  public int[] getDestinationCosts(){
    if(transientsSet()) return destinationCosts.clone();
    calculateTransients();
    return destinationCosts.clone();
  }
  public int[] getDestinations(){
    if(transientsSet()) return destinations.clone();
    calculateTransients();
    return destinations.clone();
  }
  // adds a node. returns the new node.
  public SENode addNode(){
    nodeCosts = null;
    destinations = null;
    destinationCosts = null;

    stl += 16; // roomID(4)+name(4)+commands(4)+triggers(4)
    SENode n = new SENode();
    if( nodes == null ){
      nodes = new SENode[]{ n };
      notifyObservers( MECHANICS_CHANGED );
      return n;
    }
    SENode[] newArray = new SENode[ nodes.length + 1 ];
    System.arraycopy( nodes, 0, newArray, 0, nodes.length );
    newArray[ newArray.length - 1 ] = n;
    nodes = newArray;
    notifyObservers( MECHANICS_CHANGED );
    return n;
  }
  /* This has been removed because it opens a real mess of accounting
   * difficulties:
   * If you remove a node, what do you do with commands or triggers that
   * lead there?
   * There needs to be a starting place - the origin - we set it at
   * creation time, but there's nothing to stop someone from deleting
   * it a putting the start point at some other index.
  // removes a node and returns it (though it's pretty much useless, you
  // might want to query the info), or null if an invalid index is passed
  public SENode removeNode( int index ){
    if( nodes == null || index < 0 || index >= nodes.length ) return null;
    SENode n = nodes[ index ];
    SENode[] newArray = new SENode[ nodes.length - 1 ];
    System.arraycopy( nodes, 0, newArray, 0, index );
    System.arraycopy( nodes, index+1, newArray, index, nodes.length-index-1 );
    nodes = newArray;
    stl -= 16;
    stl -= getLen( n.name );
    int i;
    if( n.commands != null ){
      for( i = 0; i < n.commands.length; i++ ){
        stl -= 8;
        stl -= getLen( n.commands[ i ].text );
      }
    }
    if( n.triggers != null ){
      for( i = 0; i < n.triggers.length; i++ ){
        stl -= 12;
        stl -= getLen( n.triggers[ i ].text );
      }
    }

    if( n.roomID < 0 )
      notifyObservers( MECHANICS_CHANGED );
    else
      notifyObservers( DESTINATIONS_CHANGED );
    return n;
  }
  */
  // returns the changed node, or null if index is invalid
  // This is one setter that doesn't invalidate the transient data.
  public SENode setNodeName( int index, String nodeName ){
    if( nodes == null || index < 0 || index >= nodes.length ) return null;
    if( nodeName == nodes[ index ].name ||
        (nodeName != null && nodeName.equals( nodes[ index ].name )) )
      return nodes[ index ];

    stl -= getLen( nodes[ index ].name );
    stl += nodeName;
    nodes[ index ].name = nodeName;
    // okay, the mechanics didn't really change, but something did, so
    // we need to notify...
    notifyObservers( null );
    return nodes[ index ];
  }
  // returns the changed node, or null if index is invalid
  public SENode setNodeExitRoomID( int index, int exitRoomID ){
    if( index < 0 || index >= nodes.length ) return null;
    if( exitRoomID == nodes[ index ].roomID ) return nodes[ index ];
    nodeCosts = null;
    destinations = null;
    destinationCosts = null;
    nodes[ index ].roomID = exitRoomID;
    notifyObservers( DESTINATIONS_CHANGED );
    return nodes[ index ];
  }
  // returns the changed node, or null if index is invalid
  public SENode addNodeCommand( int index,
                                int targetIndex,
                                String text ){
    if( nodes == null || index < 0 || index >= nodes.length ) return null;
    nodeCosts = null;
    destinations = null;
    destinationCosts = null;
    stl += 8;
    stl += getLen( text );
    SECommand c = new SECommand();
    c.node = targetIndex;
    c.text = text;
    SECommand[] newArray = new SECommand[ commands == null ? 
                                          1 : commands.length + 1 ];
    if( commands != null )
      System.arraycopy( commands, 0, newArray, 0, commands.length );
    commands = newArray;
    commands[ commands.length - 1 ] = c;
    notifyObservers( MECHANICS_CHANGED );
    return nodes[ index ];
  }
  // returns the changed node, or null if index is invalid
  public SENode addNodeTrigger( int index,
                                int targetIndex,
                                int averageDelay,
                                String triggerRegex ){
    if( nodes == null || index < 0 || index >= nodes.length ) return null;
    nodeCosts = null;
    destinations = null;
    destinationCosts = null;
    stl += 12;
    stl += getLen( triggerRegex );
    SETrigger t = new SETrigger();
    t.node = targetIndex;
    t.time = averageDelay;
    t.text = triggerRegex;
    SETrigger[] newArray = new SETrigger[ triggers == null ? 
                                          1 : triggers.length + 1 ];
    if( triggers != null )
      System.arraycopy( triggers, 0, newArray, 0, triggers.length );
    triggers = newArray;
    triggers[ triggers.length - 1 ] = t;
    notifyObservers( MECHANICS_CHANGED );
    return nodes[ index ];
  }
  public String getSequence( int destinationRoomID ){
  }
  private static final int getLen( String s ){
    if( s == null )
      return 0;
    return s.getBytes().length;
  }
  // package private so MudMap can access it
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
  private static final SENode[] readNodes( 
      DataInputStream is ) throws IOException {
    int len = is.readInt();
    SENode[] a = new SENode[ len ];
    for( int i = 0; i < len; i++ )
      a[ i ] = readNode( is );
    return a;
  }
  private static final SECommand readCommand(
      DataInputStream is ) throws IOException {
    SECommand c = new SECommand();
    c.node = is.readInt();
    c.text = readString( is );
    return c;
  }
  private static final SECommand[] readCommands(
      DataInputStream is ) throws IOException {
    int len = is.readInt();
    SECommand[] a = new SECommand[ len ];
    for( int i = 0; i < len; i++ )
      a[ i ] = readCommand( is );
    return a;
  }
  private static final SETrigger readTrigger(
      DataInputStream is ) throws IOException {
    SETrigger t = new SETrigger();
    t.node = is.readInt();
    t.time = is.readInt();
    t.text = readString( is );
    return t;
  }
  private static final SETrigger[] readTriggers(
      DataInputStream is ) throws IOException {
    int len = is.readInt();
    SETrigger[] a = new SETrigger[ len ];
    for( int i = 0; i < len; i++ )
      a[ i ] = readTrigger( is );
    return a;
  }
  private static final SENode readNode(
      DataInputStream is ) throws IOException {
    SENode n = new SENode();
    n.roomID = is.readInt();
    n.name = readString( is );
    n.commands = readCommands( is );
    n.triggers = readTriggers( is );
  }


  public void setState( DataInputStream is ) throws IOException {
    stl = is.readInt();
    id = is.readInt();
    roomID = is.readInt();
    cmd = readString( is );
    len = is.readInt();
    nodes = readNodes( is );
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
  private static final void writeCommand(
      DataOutputStream os, SECommand c ) throws IOException {
    os.writeInt( c.node );
    writeString( os, c.text );
  }
  private static final void writeCommands(
      DataOutputStream os, SECommand[] a ) throws IOException {
    if( a == null ){
      os.writeInd( 0 );
      return;
    }
    os.writeInt( a.length );
    for( int i = 0; i < a.length; i++ ){
      writeCommand( os, a[ i ] );
    }
  }
  private static final void writeTrigger(
      DataOutputStream os, SETrigger t ) throws IOException {
    os.writeInt( t.node );
    os.writeInt( t.time );
    writeString( os, t.text );
  }
  private static final void writeTriggers(
      DataOutputStream os, SETrigger[] a ) throws IOException {
    if( a == null ){
      os.writeInd( 0 );
      return;
    }
    os.writeInt( a.length );
    for( int i = 0; i < a.length; i++ ){
      writeTrigger( os, a[ i ] );
    }
  }
  private static final void writeNode(
      DataOutputStream os, SENode n ) throws IOException {
    os.writeInt( n.roomID );
    writeString( os, n.name );
    writeCommands( os, n.commands );
    writeTriggers( os, n.triggers );
  }
  private static final void writeNodes(
      DataOutputStream os, SENode[] a ) throws IOException {
    if( a == null ){
      os.writeInd( 0 );
      return;
    }
    os.writeInt( a.length );
    for( int i = 0; i < a.length; i++ ){
      writeNode( os, a[ i ] );
    }
  }

  public void getState( DataOutputStream os ) throws IOException {
    byte[] bs;
    int i;
    os.writeInt( stl );
    os.writeInt( id );
    os.writeInt( roomID );
    writeString( os, name );
    writeNodes( os, nodes );
  }
  public String getDisplayString(){
    StringBuffer sb = new StringBuffer( "Special Exit - id:" + id +
              ", Name: \"" + name + "\"" +
              ", Origin room:" + roomID + ", Nodes:\n" )
    if( nodes != null )
      for( i = 0; i < nodes.length; i++ )
        sb.append( i + nodes[i].getDisplayString() );
    else
      sb.append( "NO NODES\n" );
    return sb.toString();
  }
  private class SENode{
    // this is for a terminus node, and is the ID of the exit room
    private int roomID;
    // optional name
    private String name;
    // commands for changing current node
    private SECommand[] commands;
    // triggers that indicate the current node has been changed
    // by the server (train  pulls out of station, etc)
    private SETrigger[] triggers;
    // the thing we need to initialize is roomID, since java
    // autoinits it to 0, and we want the null to be -1.
    private SENode(){
      roomID = -1;
    }
    public int getRoomID(){ return roomID; }
    public String getName(){ return name; }
    // returns a COPY of the commands
    public SECommand[] getCommands(){
      return commands == null ? null : commands.clone();
    }
    // returns a COPY of the triggers
    public SETrigger[] getTriggers(){
      return triggers == null ? null : triggers.clone();
    }
    public String getDisplayString(){
      StringBuffer sb = new StringBuffer(
          "  name: \"" + name + "\" roomID: " + roomID + "\n" );
      if( commands != null ){
        for( i = 0; i < commands.length; i++ )
          sb.append( commands[i].getDisplayString() ).append( "\n" );
      }
      if( triggers != null ){
        for( i = 0; i < triggers.length; i++ )
          sb.append( triggers[i].getDisplayString() ).append( "\n" );
      }
      return sb.toString();
    }
  }
  private class SECommand{
    // a no-op constructor
    private SETrigger(){}

    // this is an index into the node array of the containing special exit
    // great care must be taken to keep it up to date and valid or we 
    // risk array index out of bounds errors.
    private int node;
    private String text;
    public int getTargetNode(){ return node; }
    public String getCommandText(){ return text; }
    public String getDisplayString(){
      return "        C: \"" + text = "\" -> " + node;
    }
  }
  private class SETrigger{
    // a no-op constructor
    private SETrigger(){}

    // this is an index into the node array of the containing special exit
    // great care must be taken to keep it up to date and valid or we 
    // risk array index out of bounds errors.
    private int node;
    // this is supposed to be an average time for the wait on the
    // trigger, which could be useful if we're trying to figure out
    // the cost of getting through the state machine.
    private int time;
    // the trigger regexp.
    private String text;
    public int getTargetNode(){ return node; }
    public int getAvgDelay(){ return time; }
    public String getTriggerText(){ return text; }
    public String getDisplayString(){
      return "        T: \"" + text = "\" (" + time + " sec avg) -> " + node;
    }
  }
}
