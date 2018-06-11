package tools.persistence;

import java.io.*;

public interface Streamable{

  // must return the number of bytes it will take to store
  // this object
  public int getStreamedLength();

  public void setState( DataInputStream is ) throws IOException;
  // the first 4 bytes written to os MUST be the value returned
  // from getStreamedLength()
  public void getState( DataOutputStream os ) throws IOException;
}
