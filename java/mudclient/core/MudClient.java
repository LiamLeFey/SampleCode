package mudclient.core;

import mudclient.core.commands.*;
import mudclient.core.events.*;
import java.util.*;
import javax.swing.text.Segment;
import java.io.IOException;
import java.net.UnknownHostException;

import mudclient.console.SimpleIface;
import mudclient.gui.MudClientGui;

public class MudClient {

  private static UserInterface ui;
  //private TriggerManager triggerManager;
  private static HashSet aliases;

  private static VariableScope root;
  private static HashMap<String, WorldConnection> worldConnections;
  private static HashMap<String, Command> commands;
  static{
    root = new VariableScope( null );
    worldConnections = new HashMap<String, WorldConnection>();
    commands = new HashMap<String, Command>();
    commands.put( "def", new Define() );
    commands.put( "list", new mudclient.core.commands.List() );
    commands.put( "addworld", new AddWorld() );
    commands.put( "quit", new Quit() );
    commands.put( "connect", new Connect() );
    commands.put( "load", new Load() );
    commands.put( "save", new Save() );
  }

  public MudClient( ){
  }

  public static void setUserInterface( UserInterface userInterface ){
    ui = userInterface;
    root.setValue( "_UserInterface", ui );
  }

  public static void display( String s ){
    ui.display( s );
  }
  public static void handleInput( String cxName, String input ){
    WorldConnection wc = (WorldConnection)worldConnections.get( cxName );
    handleInput( wc, input );
  }
  public static void handleInput( WorldConnection wc, String input ){
    try{
      CmdInterpreter.interpret( input, wc );
    }catch( IOException e ){
      e.printStackTrace( System.err );
    }
  }
  public static Command getCommand( String name ){
    return commands.get( name.toLowerCase() );
  }
  public static WorldConnection getWorldConnection( String connectionName ){
    return worldConnections.get( connectionName );
  }
  public static VariableScope getRootVariableScope(){
    return root;
  }
  public static String addWorldConnection( String name, WorldConnection wc ){
    name = ui.addConnection( name );
    if( ! worldConnections.keySet().contains( name ) ){
      worldConnections.put( name, wc );
      ConnectionInputThread ct = new ConnectionInputThread( ui, wc, name );
      ct.start();
      return name;
    }
    return null;
  }
  public static void main( String[] args ){
    // here we create and start the user interface.
    int i = 0;
    // TODO: change this to pull classes in via Class.forName,
    // so we don't have to have all possible user interfaces
    // compiled.
    if( args.length > i && args[i].equals( "-g" ) ){
      ui = new MudClientGui();
      i++;
    } else {
      ui = new SimpleIface();
    }
    root.setValue( "_UserInterface", ui );
    WorldConnection wc = null;
    String host = null;
    String port = null;
    if( args.length > i ){
      host = args[i++];
    }
    if( args.length > i ){
      port = args[i++];
    }
    if( port == null ) port = "23";
    if( host != null )
      try{
        wc = new WorldConnection( host, Integer.parseInt( port ), root );
      } catch( UnknownHostException e ) { }
      catch( IOException e ) { }
    if( wc != null )
      addWorldConnection( wc.getWorld().getName(), wc );
    // it is the waiting for user input that creates the running
    // program

  }
}

