package mudclient.automapper;
import java.io.*;
import tools.persistence.Streamable;

public class Exit implements Streamable{
  // Denotes a destination has not been set for this exit yeh
  // (haven't been there yet.)
  public static final int UNASSIGNED_DESTINATION = -999999999;
  // Indicates that this is some sort of special exit
  public static final int SPECIAL_EXIT = -999999998;

  protected String command;
  private int destination;
  private int stl;

  public Exit(){
    this( null );
  }
  protected Exit( String cmd ){
    command = cmd;
    destination = UNASSIGNED_DESTINATION;
    stl = 8 + (cmd == null ? 0 : cmd.getBytes().length);
  }
  protected void setDestination( int roomID ){
    destination = roomID;
  }
  protected String getCommand(){
    return command;
  }
  protected int getDestination(){
    return destination;
  }
  public int getStreamedLength(){
    return stl;
  }
  public void setState( DataInputStream is ) throws IOException{
    stl = is.readInt();
    destination = is.readInt();
    byte[] bs = new byte[ stl-8 ];
    is.readFully( bs );
    command = new String( bs );
  }
  public void getState( DataOutputStream os ) throws IOException{
    os.writeInt( stl );
    os.writeInt( destination );
    byte[] bs = command.getBytes();
    os.write( bs, 0, bs.length );
  }
}
