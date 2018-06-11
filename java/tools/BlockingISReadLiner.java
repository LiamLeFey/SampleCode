package tools;

import java.io.*;

public class BlockingISReadLiner{

  private static final int expectedLineLength = 100;
  private static final byte NL_BYTE = "\n".getBytes()[0];
  private static final byte CR_BYTE = "\r".getBytes()[0];

  private InputStream in;
  private boolean ignoreNextNL;

  public BlockingISReadLiner( InputStream is ){
    if( is == null )
      throw new NullPointerException( "is" );
    in = is;
    ignoreNextNL = false;
  }
  public String readLine() throws IOException {
    byte[] rbs = new byte[expectedLineLength];
    int bytesRead = 0;
    int readVal;
    byte byteVal;
    do {
      readVal = in.read();
      if( readVal == -1 ){
        throw new EOFException();
      }
      byteVal = (byte)readVal;
      if( byteVal == NL_BYTE )
        if( ignoreNextNL ){
          ignoreNextNL = false;
          continue;
        } else break;
      if( byteVal == CR_BYTE ){
        ignoreNextNL = true;
        break;
      }
      if( bytesRead == rbs.length ){
        byte[] nbs = new byte[ rbs.length * 2 ];
        System.arraycopy( rbs, 0, nbs, 0, rbs.length );
        rbs = nbs;
      }
      rbs[bytesRead++] = byteVal;
    } while( true );
    return new String( rbs, 0, bytesRead );
  }
}
