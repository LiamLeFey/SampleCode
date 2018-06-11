package mudclient.core;

import javax.swing.text.Segment;
//import javax.swing.text.AttributeSet;

public interface UserInterface{

  // returns the actual connection name, which may be different
  // than the suggested name in the event of a name collision.
  public String addConnection( String name );

  // this is meant to display immediately, via popup, or on
  // whatever screen is currently showing.
  // for informational messages and such.
  // the segment version is usually preferrable.
  public void display( Segment s );
  public void display( String s );

  // this is meant to display on the output of a specific connection
  // the segment version is usually preferrable.
  public void display( String connection, Segment s );
  public void display( String connection, String s );
}
