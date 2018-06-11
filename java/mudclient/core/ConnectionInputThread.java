package mudclient.core;

import javax.swing.text.Segment;
import java.io.IOException;
//import java.awt.event.KeyEvent;
import java.nio.charset.*;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;

/** this class is in charge of listening on a connection. 
 * It also passes the information through the trigger manager
 * before passing it on to the ui.
 */
  /////////////////////////////////
  //
  /////////////////////////////////
public class ConnectionInputThread extends Thread{
  private UserInterface ui;
  private WorldConnection wc;
  private String cName;

  public ConnectionInputThread( UserInterface ui, WorldConnection wc, String s ){
    this.ui = ui;
    this.wc = wc;
    cName = s;
    this.setDaemon( true );
  }
  public void run(){
    byte[] bb = new byte[ 4096 ];
    char[] cb = new char[ 4096 ];
    Segment s = new Segment();
    s.array = cb;
    s.offset = 0;
    int readLength;
    CharsetDecoder decoder = Charset.defaultCharset().newDecoder();
    decoder.onUnmappableCharacter( CodingErrorAction.IGNORE );
    decoder.onMalformedInput( CodingErrorAction.IGNORE );
    CoderResult result;
    while( true ){
      try{
        // this read blocks until data is available, so it's safe
        // in a spin loop
        readLength = wc.read( bb );
        // we aren't always neccessarily done with the source, so 
        // this gets set outside and only when needed inside
        ByteBuffer source = ByteBuffer.wrap( bb, 0, readLength );
        CharBuffer destination;
        while( readLength > 0 ){
          // we always flush the destination, so this get reset
          // every time we loop.
          destination = CharBuffer.wrap( cb, 0, cb.length );
          result = decoder.decode( source, destination, false );

          if( result == CoderResult.OVERFLOW ){
            // out of destination room: flush to ui and continue
            s.count = destination.position();
            ui.display( cName, s );
            destination = CharBuffer.wrap( cb, 0, cb.length );
          } else if ( result == CoderResult.UNDERFLOW ){
            // out of source: flush to ui, refill buffer, and continue
            s.count = destination.position();
            ui.display( cName, s );
            readLength = wc.read( bb );
            if( readLength >= 0 ){
              source = ByteBuffer.wrap( bb, 0, readLength );
            }
          }
        } 
        if( readLength == 0 ) {
          throw new Error( "BufferedInputReader.read does not block." +
                            "  Fix your program!");
        }
        ui.display( cName, "<====> connection closed <====>\n" );
        ui.display( "<====> connection " + cName + " closed <====>\n" );
        break;
      }catch( IOException e ){
        e.printStackTrace( System.err );
        break;
      }
    }
  }
}
