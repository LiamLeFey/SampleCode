package mudclient.console;

import java.io.*;
import java.nio.*;
import java.util.*;
// these allow us to do fast text manimulation (without creating
// a zillion Strings) and add attributes to them (color.) we
// ignore the attributes (hey, we're simple)
import javax.swing.text.Segment;
import javax.swing.text.AttributeSet;
import mudclient.core.*;

// very simple interface.  There's currently no way to switch
// from one connection to another...
public class SimpleIface implements UserInterface{

  private HashMap<String, StringBuffer> connections;
  private String currentConnection;

  public SimpleIface(){
    connections = new HashMap<String, StringBuffer>();
    Thread inputLoop = new InputThread();
    inputLoop.start();
  }

  public String addConnection( String name ){
    Set keys = connections.keySet();
    if( keys.contains( name ) ){
      int i = 1; 
      while( keys.contains( name + ++i ) );
      name = name + i;
    }
    connections.put( name, new StringBuffer() );
    switchToConnection( name );
    return name;
  }

  public void switchToConnection( String name ){
    if( currentConnection != null )
      if( currentConnection.equals( name ) )
        return;
      else
        System.out.println( "------ End " + currentConnection + " ------" );
    if( name != null ){
      System.out.println( "----- Begin " + name + " -----" );
      String bufferedOutput = connections.get( name ).toString();
      if( bufferedOutput.length() != 0 ){
        System.out.println( bufferedOutput );
        connections.put( name, new StringBuffer() );
      }
    }
    currentConnection = name;
  }

  public String getCurrentConnection(){
    return currentConnection;
  }

  public void display( Segment s ){
    display( s.toString() );
  }
  public void display( String s ){
    display( currentConnection, s );
  }

  public void display( String connection, Segment s ){
    display( connection, s.toString() );
  }
  public void display( String connection, String s ){
    if( connection == null 
        || currentConnection.equals( connection ) ){
      System.out.print( s );
    }else if( ! connections.keySet().contains( connection ) ){
      System.out.println( "Bad connection name: " + connection );
      System.out.print( s );
    }else{
      ((StringBuffer)connections.get( connection )).append( s );
    }
  }
  private class InputThread extends Thread{
    private BufferedReader in;
    private InputThread(){
      this.setDaemon( false );
      in = new BufferedReader( new InputStreamReader( System.in ) );
    }
    public void run(){
      while( true ){
        try{
          String s = in.readLine();
          MudClient.handleInput( currentConnection, s );
        }catch( IOException e ){
          e.printStackTrace( System.err );
        }
      }
    }
  }
}
