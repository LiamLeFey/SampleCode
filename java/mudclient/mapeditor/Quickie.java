package mudclient.mapeditor;

import tools.*;
import java.io.*;
import mudclient.automapper.*;

public class Quickie{
  private static BufferedReader inReader = 
    new BufferedReader( 
      new InputStreamReader( System.in) );
  private MudMap map;
  public Quickie( String mapDataDirName, String name ){
    map = new MudMap( mapDataDirName, name );
  }
  public void editMudMap(){
    String menu = 
      "cRegion      create new region\n" + 
      "cRoom        create new room\n" + 
      "cSpecialExit create new SpecialExit\n" + 
      "especial     edit special exit\n" + 
      "eroom        edit room\n" + 
      "eregion      edits a region\n" + 
      "listregion   lists regions\n" + 
      "listrooms    lists rooms in region\n" + 
      "special      adds a special room\n" + 
      "lspecial     lists special rooms\n" + 
      "rspecial     removes a special room)\n" + 
      "path         finds path\n" +
      "undo         undoes everything back to the last save\n" + 
      "save\n" + 
      "done\n";
    String input = "help";
    do{
      if( "cRegion".equalsIgnoreCase( input ) )
        createRegion();
      else if( "cRoom".equalsIgnoreCase( input ) )
        createRoom();
      else if( "cSpecialExit".equalsIgnoreCase( input ) )
        createSpecialExit();
      else if( "eRegion".equalsIgnoreCase( input ) )
        editRegion( getValidRegionID() );
      else if( "eRoom".equalsIgnoreCase( input ) )
        editRoom( getValidRoomID() );
      else if( "eSpecial".equalsIgnoreCase( input ) )
        editSpecialExit( getValidSpecialExitID() );
      else if( "listRegion".equalsIgnoreCase( input ) )
        listRegions();
      else if( "listRooms".equalsIgnoreCase( input ) )
        listRooms( map.getRoomsInRegion( getValidRegionID() ) );
      else if( "special".equalsIgnoreCase( input ) )
        addSpecialRoom();
      else if( "lspecial".equalsIgnoreCase( input ) )
        listSpecialRooms();
      else if( "rSpecial".equalsIgnoreCase( input ) )
        removeSpecialRoom();
      else if( "path".equalsIgnoreCase( input ) )
        findPath();
      else if( "undo".equalsIgnoreCase( input ) )
        undo();
      else if( "save".equalsIgnoreCase( input ) )
        save();
      else if( "help".equalsIgnoreCase( input ) 
          || "?".equalsIgnoreCase( input ) )
        System.out.print( menu );
      input = readFromIn();
    }while( ! ("done".equalsIgnoreCase( input ) 
        || "exit".equalsIgnoreCase( input )
        || "quit".equalsIgnoreCase( input ) ) );
  }
  public int createSpecialExit(){
    return map.createNewSpecialExit( 
        getValidRoomID(), 
        readFromIn("Enter command.", null ) );
  }
  public int getValidSpecialExitID(){
    String s;
    int i;
    SpecialExit se;
    System.out.print( "Enter special exit ID or 'create', or 'list' or 'key':");
    int id = -1;
    while( id == -1 ){
      s = readFromIn();
      if( "create".equalsIgnoreCase( s ) )
        return createSpecialExit();
      if( "list".equalsIgnoreCase( s ) ){
        int[] ids = map.getSpecialExits();
        for( i = 0; i < ids.length; i++ ){
          se = map.getSpecialExit( ids[i] );
          Room r = map.getRoom( se.getRoomID() );
          System.out.println( niceInt( se.getRoomID(), 4 ) 
              + " ("+r.getShortDesc()+")"
              + "->"+ se.getCmd() );
        }
      }else if( "key".equalsIgnoreCase( s ) ){
        se = map.getSpecialExit( 
            getValidRoomID(), 
            readFromIn("command") );
        if( se != null ) id = se.getID();
      }else
        System.out.println( "Er, huh? try again.");

      if( map.getSpecialExit( id ) == null )
        id = -1;
    }
    return id;
  }
  public void removeSpecialRoom( ){
    System.out.print("Enter room name");
    String name = readFromIn();
    map.removeSpecialRoom( name );
  }

  public void findPath( ){
    findPath( -1, -1 );
  }
  public void findPath( int roomA ){
    findPath( roomA, -1 );
  }
  public void findPath( int roomA, int roomB ){
    if( map.getRoom( roomA ) == null ){
      System.out.print("Please enter a roomID for the origin.");
      roomA = getValidRoomID();
    }
    if( map.getRoom( roomB ) == null ){
      System.out.print("Please enter a roomID for the destination.");
      roomB = getValidRoomID();
    }
    Path p = map.getShortestPath( roomA, roomB );
    String path = (p == null ? null : p.getSteps());
    if( path != null ){
      java.util.StringTokenizer tok = new java.util.StringTokenizer( path, "\n" );
      String[] s;
      int i;
      int len = tok.countTokens();
      s = new String[len];
      for( i = 0; i < len; i++ ){
        s[i] = tok.nextToken();
      }
      System.out.println( Utils.horizontalList( s ) );
    }else
      System.out.println( "No Path found");
  }
  public boolean addSpecialRoom(){
    String name;
    do{
      System.out.print("enter name for room: ");
      name = readFromIn();
    }while( name == null || ("").equals( name ) || 
        map.getRoom( map.getSpecialRoomID( name ) ) != null );
    int roomID = getValidRoomID();
    return addSpecialRoom( roomID, name );
  }
  public boolean addSpecialRoom( int roomID, String name ){
    return map.registerSpecialRoom( name, roomID );
  }
  public void listSpecialRooms(){
    System.out.println( Utils.horizontalList( map.getSpecialRoomNames() ) );
  }

  public void listRegions( int[] rids ){
    int i;
    for( i = 0; i < rids.length; i++ ){
      System.out.println( niceInt( rids[i], 4 ) + " " 
          + map.getRegion( rids[i] ).getName() );
    }
  }
  public void listRegions(){
    listRegions( map.getRegions() );
  }
  public int createRegion(){
    int regionID = map.createNewRegion();
    changeRegionName( regionID );
    System.out.println("Region " + regionID + " created.");
    return regionID;
  }
  public void listRooms( int[] ids ){
    System.out.println( " room ID  |  short description" );
    int i;
    for( i = 0; i < ids.length; i++ ){
      System.out.println( niceInt( ids[i], 8 ) 
          + "     " + map.getRoom( ids[i] ).getShortDesc() );
    }
  }
  public int getValidRoomID(){
    int roomID = -1;
    String s;
    System.out.print( "Enter room ID or name, or"
        + " 'create', 'list' (special) 'list all' (all in Region):");
    while( roomID == -1 ){
      s = readFromIn();
      if(("list").equals( s )){
        listSpecialRooms();
      }else if(("list all").equals( s )){
        listRooms( map.getRoomsInRegion( getValidRegionID() ) );
      }else if(("create").equals( s )){
        roomID = createRoom();
      } else {
        try{
          roomID = Integer.parseInt( s );
          if( map.getRoom( roomID ) == null ){
            System.out.println( "invalid room number. try again.");
            roomID = -1;
          }
        }catch( NumberFormatException e ){
          roomID = map.getSpecialRoomID( s );
          if( roomID == -1 )
            System.out.println("could not find room or parse number."
                + " try again.");
        }
      }
    }
    return roomID;
  }
  public int getValidRegionID( ){
    return getValidRegionID( -1 );
  }
  public int getValidRegionID( int defaultID ){
    int regionID = defaultID;
    String s;
    System.out.print( "Enter region number or name "
        + "or 'create' to make new one 'list' to list:");
    do{
      if( regionID == -1 )
        s = readFromIn();
      else
        s = readFromIn( "" + regionID );
      if(("list").equals( s )){
        listRegions();
        regionID = -1;
      }else if(("create").equals( s )){
        regionID = createRegion();
      } else {
        try{
          regionID = Integer.parseInt( s );
          if( map.getRegion( regionID ) == null ){
            System.out.println( "invalid region number. try again.");
            regionID = -1;
          }
        }catch( NumberFormatException e ){
          int[] is = map.getRegions( s );
          if( is.length == 0 ){
            System.out.println("could not find region or parse number."
                + " try again.");
            regionID = -1;
          }else if( is.length > 1){
            System.out.println("unfortunately, more than one region matches."
                + " please select one by ID.");
            listRegions( is );
            regionID = -1;
          }else
            regionID = is[0];
        }
      }
    } while( regionID == -1 );
    return regionID;
  }
  public int createRoom(){
    return createRoom( getValidRegionID() );
  }
  public int createRoom( int regionID ){
    int roomID = map.createNewRoom( regionID );
    setShortDesc( roomID );
    System.out.println("Room " + roomID + " created.");
    return roomID;
  }
  public void setShortDesc( int roomID ){
    Room r = map.getRoom( roomID );
    System.out.print( "Short descritpion: ");
    r.setShortDesc( readFromIn( "room" + roomID ) );
  }
  public void editRoom( int roomID ){
    String menu =
      "l         look around\n" +
      "<dir>     move a direction or add an exit\n" +
      "sdesc     set short description\n" +
      "ldesc     set long description\n" +
      "note      add note\n" +
      "rexit     remove an exit\n" +
      "findpath  finds path\n" +
      "undo      undoes everything back to the last save\n" + 
      "          (may cause instability unless you exit immediately)\n" +
      "save\n" + 
      "done\n";
    String input = "help";
    do{
      if( "l".equalsIgnoreCase( input ) || "look".equalsIgnoreCase( input ) ){
        printRoom( roomID );
      }else if( ("sdesc").equalsIgnoreCase( input ) ){
        setShortDesc( roomID );
      }else if( ("ldesc").equalsIgnoreCase( input ) ){
        setLongDesc( roomID );
      }else if( ("note").equalsIgnoreCase( input ) ){
        addNote( roomID );
      }else if( ("rexit").equalsIgnoreCase( input ) ){
        removeExitFromRoom( roomID );
      }else if( ("findpath").equalsIgnoreCase( input ) ){
        findPath( roomID, -1 );
      }else if( ("undo").equalsIgnoreCase( input ) ){
        undo();
      }else if( ("save").equalsIgnoreCase( input ) ){
        save();
      }else if( "help".equalsIgnoreCase( input ) 
          || "?".equalsIgnoreCase( input ) ){
        System.out.print( menu );
      }else if( isNormalDirection( input ) ){
        roomID = handleDirection( input, roomID );
        input = "l";
        continue;
      }else{
        Room r = map.getRoom( roomID );
        if( r.hasExit( input ) ){
          roomID = handleDirection( input, roomID );
          input = "l";
          continue;
        }else if( confirm("Unrecognized command.  Handle as exit?", false) ){
          roomID = handleDirection( input, roomID );
          input = "l";
          continue;
        }
      }
      input = readFromIn();
    }while( ! ("done".equalsIgnoreCase( input ) 
        || "exit".equalsIgnoreCase( input )
        || "quit".equalsIgnoreCase( input ) ) );
  }
  public static final String expandIfNormal( String dir ){
    if( "n".equalsIgnoreCase( dir ) ) return "north";
    if( "s".equalsIgnoreCase( dir ) ) return "south";
    if( "e".equalsIgnoreCase( dir ) ) return "east";
    if( "w".equalsIgnoreCase( dir ) ) return "west";
    if( "ne".equalsIgnoreCase( dir ) ) return "northeast";
    if( "nw".equalsIgnoreCase( dir ) ) return "northwest";
    if( "se".equalsIgnoreCase( dir ) ) return "southeast";
    if( "sw".equalsIgnoreCase( dir ) ) return "southwest";
    if( "u".equalsIgnoreCase( dir ) ) return "up";
    if( "d".equalsIgnoreCase( dir ) ) return "down";
    return dir;
  }
  public int handleDirection( String dir, int roomID ){
    Room r = map.getRoom( roomID );
    int dest;
    dir = expandIfNormal( dir );
    if( r.hasExit( dir ) ){
      dest = r.getDest( dir );
      if( dest == Exit.UNASSIGNED_DESTINATION ){
        System.out.print("That exit is unassigned.  ");
        if( confirm( "Assign it?", true ) )
          return assignDestination( r, dir );
        else
          return roomID;
      }else if( dest == Exit.SPECIAL_EXIT ){
        return handleSpecialExit( roomID, dir );
      }else{
        r = map.getRoom( dest );
        if( r != null )
          return dest;
        System.out.println("That exit has an invalid destination.");
        System.out.println("Please delete it.");
        return roomID;
      }
    }else{
      if( confirm("That exit does not exist.  Create it?", true ) )
        r.addExit( dir );
      return roomID;
    }
  }

  public int assignDestination( Room r, String dir ){
    int dest = getValidDestination( r );
    if( dest == Exit.SPECIAL_EXIT ){
      int seID = map.createNewSpecialExit( r.getID(), dir );
      r.setDestination( dir, Exit.SPECIAL_EXIT );
      if( confirm( "Edit special exit now?", true ) )
        editSpecialExit( seID );
      return r.getID();
    }else if( isNormalDirection( dir ) ){
      String recip = getReciprocalDirection( dir );
      Room d = map.getRoom( dest );
      if( d.hasExit( recip ) ){
        int recipDest = d.getDest( recip );
        if( recipDest == Exit.UNASSIGNED_DESTINATION ){
          if(confirm( "Set the reciprocal exit?", true )){
            d.setDestination( recip, r.getID() );
          }
        }else if( recipDest != Exit.SPECIAL_EXIT && recipDest != r.getID() ){
          System.out.println("WARNING!! Reciprocal exit does not match!");
        }
      }else{
        if(confirm( "Add the reciprocal exit?", true )){
          d.addExit( recip );
          d.setDestination( recip, r.getID() );
        }
      }
    }
    r.setDestination( dir, dest );
    return dest;
  }
  public int getValidDestination( Room r ){
    String input;
    System.out.print("Assign 'existing', create 'new', or make 'special'?");
    do{
      input = readFromIn( "new" );
    }while( ! "existing".equalsIgnoreCase(input) 
         && ! "new".equalsIgnoreCase(input) 
         && ! "special".equalsIgnoreCase(input) );
    if( "new".equalsIgnoreCase( input ) ){
      int regID = getValidRegionID( r.getRegion() );
      return map.createNewRoom( regID );
    }else if( "special".equalsIgnoreCase( input ) ){
      return Exit.SPECIAL_EXIT;
    }else{
      return getValidRoomID();
    }
  }

  public int handleSpecialExit( int roomID, String dir ){
    String input;
    SpecialExit se = map.getSpecialExit( roomID, dir );
    int id;
    if( se == null )
      if( confirm( "Special exit does not exist. create?", true ) ){
          id = map.createNewSpecialExit( roomID, dir );
          se = map.getSpecialExit( id );
      }else
        return roomID;
    id = se.getID();
    do{
      System.out.print( "Special exit "+roomID+"->"+dir+".  Edit or Follow?");
      input = readFromIn( "follow" );
    }while(!"edit".equalsIgnoreCase(input)&& !"follow".equalsIgnoreCase(input));
    if( "edit".equalsIgnoreCase(input) ){
      editSpecialExit( id );
      return roomID;
    } 
    int[] dests = se.getDestinations();
    String desc;
    int i;
    if(dests.length == 0){
      System.out.println("Nowhere to go!  add some destinations.");
      return roomID;
    }
    if( dests.length == 1)
      i = 0;
    else{
      System.out.println( "Select a destination (by index):");
      for( i = 0; i < dests.length; i++ ){
        desc = map.getRoom( dests[i] ) == null ? 
          "Null (error)" : map.getRoom( dests[i] ).getShortDesc();
        System.out.println( niceInt( i, 3 ) + ")  "+dests[i]+"  ("+desc+")");
      }
      do{
        i = getNumber( null, -1, 0 );
      }while( i < 0 && i >= dests.length );
    }
    return dests[i];
  }
  public boolean isNormalDirection( String s ){
    if( s == null ) return false;
    return
          ("n").equalsIgnoreCase( s ) 
          || ("s").equalsIgnoreCase( s ) 
          || ("e").equalsIgnoreCase( s ) 
          || ("w").equalsIgnoreCase( s ) 
          || ("u").equalsIgnoreCase( s ) 
          || ("d").equalsIgnoreCase( s ) 
          || ("ne").equalsIgnoreCase( s ) 
          || ("nw").equalsIgnoreCase( s ) 
          || ("se").equalsIgnoreCase( s ) 
          || ("sw").equalsIgnoreCase( s ) 
          || ("north").equalsIgnoreCase( s ) 
          || ("south").equalsIgnoreCase( s ) 
          || ("east").equalsIgnoreCase( s ) 
          || ("west").equalsIgnoreCase( s ) 
          || ("up").equalsIgnoreCase( s ) 
          || ("down").equalsIgnoreCase( s ) 
          || ("northwest").equalsIgnoreCase( s ) 
          || ("northeast").equalsIgnoreCase( s ) 
          || ("southeast").equalsIgnoreCase( s ) 
          || ("southwest").equalsIgnoreCase( s );
  }
  public String getReciprocalDirection( String s ){
    if( ("n").equalsIgnoreCase( s ) || ("north").equalsIgnoreCase( s ) )
      return "south";
    if( ("s").equalsIgnoreCase( s ) || ("south").equalsIgnoreCase( s ) )
      return "north";
    if( ("e").equalsIgnoreCase( s ) || ("east").equalsIgnoreCase( s ) )
      return "west";
    if( ("w").equalsIgnoreCase( s ) || ("west").equalsIgnoreCase( s ) )
      return "east";
    if( ("u").equalsIgnoreCase( s ) || ("up").equalsIgnoreCase( s ) )
      return "down";
    if( ("d").equalsIgnoreCase( s ) || ("down").equalsIgnoreCase( s ) )
      return "up";
    if( ("ne").equalsIgnoreCase( s ) || ("northeast").equalsIgnoreCase( s ) )
      return "southwest";
    if( ("nw").equalsIgnoreCase( s ) || ("northwest").equalsIgnoreCase( s ) )
      return "northeast";
    if( ("se").equalsIgnoreCase( s ) || ("southeast").equalsIgnoreCase( s ) )
      return "northwest";
    if( ("sw").equalsIgnoreCase( s ) || ("southwest").equalsIgnoreCase( s ) )
      return "northeast";
    return null;
  }
  public void printRoom( int roomID ){
    Room r = map.getRoom( roomID );
    int i;
    String[] strings;
    if( r == null ) return;
    Region reg = map.getRegion( r.getRegion() );
    String regionName = reg == null ? null : reg.getName();

    System.out.println( "Room " + roomID + ",  in Region " + r.getRegion()
        + " (" + regionName + ")" );
    System.out.println( r.getShortDesc() );
    System.out.println();
    System.out.println( r.getLongDesc() );
    strings = r.getObviousExits();
    System.out.print( "Obvious Exits: "  );
    if( strings.length == 0 )
      System.out.print( "None!");
    else
      System.out.println( Utils.horizontalList( strings, 16, 75 ) );
    strings = r.getHiddenExits();
    System.out.println();
    System.out.print( "Hidden Exits: "  );
    if( strings.length == 0 )
      System.out.print( "None!");
    else
      System.out.println( Utils.horizontalList( strings, 15, 75 ) );
    System.out.println();
    System.out.println( "Notes:");
    strings = r.getNotes();
    if( strings.length == 0 )
      System.out.println( "None!");
    else
      for( i = 0; i < strings.length; i++ ){
        System.out.println( strings[i] );
      }
  }
  public void setLongDesc( int roomID ){
    String s = readLongStringFromIn( "enter new long description" );
    Room r = map.getRoom( roomID );
    r.setLongDesc( s );
  }
  public void addNote( int roomID ){
    String s = readLongStringFromIn( "enter note" );
    Room r = map.getRoom( roomID );
    r.addNote( s );
  }
  public String readLongStringFromIn( String message ){
    System.out.println( message + " ('**' on empty line to finish)");
    StringBuffer sb = new StringBuffer();
    String input;
    input = readFromIn();
    while( ! "**".equals( input ) ){
      sb.append( input ).append( "\n" );
      input = readFromIn();
    }
    return sb.toString();
  }
  public void removeExitFromRoom( int roomID ){
    Room r = map.getRoom( roomID );
    System.out.println( "Remove which exit?");
    String[] es = r.getExits();
    System.out.println( Utils.horizontalList( es ) );
    System.out.println();
    String s = readFromIn();
    r.removeExit( s );
  }
  public void editRegion( int regionID ){
    String menu =
      "Name      change name\n" +
      "l         examines the path legnth array.\n" +
      "exits     shows region exit rooms\n" +
      "entrances shows region entrance rooms\n" +
      "undo      undoes everything back to the last save\n" + 
      "          (may cause instability unless you exit immediately)\n" +
      "save\n" + 
      "done\n";
    String input = "help";
    do{
      if( ("name").equalsIgnoreCase( input ) ){
        changeRegionName( regionID );
      }else if( ("l").equalsIgnoreCase( input ) ){
        showCachedExitLengths( regionID );
      }else if( ("exits").equalsIgnoreCase( input ) ){
        listRegionExits( regionID );
      }else if( ("entrances").equalsIgnoreCase( input ) ){
        listRegionEntrances( regionID );
      }else if( ("undo").equalsIgnoreCase( input ) ){
        undo();
      }else if( ("save").equalsIgnoreCase( input ) ){
        save();
      }else if( "help".equalsIgnoreCase( input ) 
          || ("?".equals( input ))){
        System.out.print( menu );
      }
      input = readFromIn();
    }while( ! ("done".equalsIgnoreCase( input )
        || "exit".equalsIgnoreCase( input )) );

  }
  public void changeRegionName( int regionID ){
    boolean b = false;
    Region r = map.getRegion( regionID );
    String s;
    do{
      System.out.print( "Region name: ");
      s = readFromIn( "region" + regionID );
      if( map.getRegions( s ).length != 0 ){
        System.out.println("A region(s) with that name "
            + "already exist(s) try agian." );
      }else{
        r.setName( s );
        b = true;
      }
    }while( !b );
  }
  public void showCachedExitLengths( int regionID ){
    Region region = map.getRegion( regionID );
    int[] entrances = region.getEntrances();
    int[] exits = region.getExits();
    System.out.println("entrances                     exits");
    System.out.print("           ");
    int i, j;
    for( j = 0; j < exits.length; j++ ){
      System.out.print( niceInt( exits[j], 6 ) );
    }
    System.out.println();
    for( i = 0; i < entrances.length; i++ ){
      System.out.print( niceInt( entrances[i], 9 ) + "  " );
      int[][] retVal = region.getCachedExitLengths( entrances[i] );
      for( j = 0; j < exits.length; j++ ){
        System.out.print( niceInt( retVal[j][1], 6 ) );
      }
      System.out.println();
    }
  }
  public void listRegionExits( int regionID ){
    Region region = map.getRegion( regionID );
    listRooms( region.getExits() );
  }
  public void listRegionEntrances( int regionID ){
    Region region = map.getRegion( regionID );
    listRooms( region.getEntrances() );
  }
  public void editSpecialExit( int specialExitID ){
    String menu =
      "l      lists the specifics of this exit\n" + 
      "steps  shows the steps required to get to a destination\n" + 
      "acheck sets availability check\n" + 
      "atrig  sets arrival trigger\n" + 
      "dcheck sets the destination check\n" + 
      "dest   adds a destination\n" + 
      "rdest  removes a destination\n" + 
      "undo   undoes everything back to the last save\n" + 
      "save\n" + 
      "done\n";
    String input = "help";
    SpecialExit se = map.getSpecialExit( specialExitID );
    do{
      if("l".equalsIgnoreCase( input ) ){
        printSpecialExit( se );
      }else if( "steps".equalsIgnoreCase( input ) ){
        if( se.getDestinations().length <= 1 )
          System.out.println( Utils.horizontalList( se.getSequence(0),"\n"));
        else{
          printSpecialExitDestinations( se );
          System.out.println( Utils.horizontalList( se.getSequence( 
                getNumber( "Select (by index) the destination", 0, 1)),"\n"));
        }
      }else if( "acheck".equalsIgnoreCase( input ) ){
        se.setAvailableCheck( readFromIn( "Enter availability check: "
              , null ));
      }else if( "atrig".equalsIgnoreCase( input ) ){
        se.setArrivalTrigger( readFromIn( "Enter arrival trigger: "
              , null ));
      }else if( "dest".equalsIgnoreCase( input ) ){
        addSpecialExitDestination( se );
      }else if( "dcheck".equalsIgnoreCase( input ) ){
        se.setDestinationCheck( readFromIn( "Enter destination check: "
              , null ));
      }else if( "rdest".equalsIgnoreCase( input ) ){
        printSpecialExitDestinations( se );
        try{
          se.removeDestination( getNumber("select index: " ) );
        }catch(IOException e){
          e.printStackTrace( System.out );
        }
      }else if( "undo".equalsIgnoreCase( input ) ){
        undo();
        System.out.println("changes undone.\nUnless you know what you're"
            + " doing, exit back to main menu. " );
      }else if( "save".equalsIgnoreCase( input ) ){
        System.out.print("saving...");
        save();
        System.out.println("done.");
      }else if( "help".equalsIgnoreCase( input ) 
          || ("?".equals( input ))){
        System.out.print( menu );
      }
      input = readFromIn();
    }while( ! ("done".equalsIgnoreCase( input )
        || "exit".equalsIgnoreCase( input )) );
  }
  public void addSpecialExitDestination( SpecialExit se ){
    int roomID;
    String aTrig, summon, summonTrig, destTrig;
    String[] strs;
    roomID = getValidRoomID();
    strs = se.getAvailableTriggers();
    if( strs.length > 0 )
      aTrig = readFromIn( "available trigger: ", strs[0] );
    else
      aTrig = readFromIn( "available trigger: ", null );
    strs = se.getSummonCmds();
    if( strs.length > 0 )
      summon = readFromIn( "summon command: ", strs[0] );
    else
      summon = readFromIn( "summon command: ", null );
    strs = se.getSummonTriggers();
    if( strs.length > 0 )
      summonTrig = readFromIn( "summon trigger: ", strs[0] );
    else
      summonTrig = readFromIn( "summon trigger: ", null );
    strs = se.getDestinationTriggers();
    if( strs.length > 0 )
      destTrig = readFromIn( "destination trigger: ", strs[0] );
    else
      destTrig = readFromIn( "destination trigger: ", null );
    se.addDestination( aTrig, summon, summonTrig, roomID, destTrig );
  }
  public void printSpecialExit( SpecialExit se ){
    System.out.println("ID: " + se.getID() );
    System.out.println("Room: " + se.getRoomID() 
      + " ("+map.getRoom(se.getRoomID()).getShortDesc()+")");
    System.out.println("Command: " + se.getCmd());
    System.out.print("type: ");
    byte type = se.getType();
    if( (type & SpecialExit.PERIODIC_AVAILABILITY) != 0 )
      System.out.print("PERIODIC_AVAILABILITY, " );
    if( (type & SpecialExit.PERIODIC_DESTINATION) != 0 )
      System.out.print("PERIODIC_DESTINATION, " );
    if( (type & SpecialExit.CONTROLLED) != 0 )
      System.out.print("CONTROLLED, " );
    if( (type & SpecialExit.SUMMONABLE) != 0 )
      System.out.print("SUMMONABLE, " );
    if( (type & SpecialExit.DELAYED) != 0 )
      System.out.print("DELAYED, " );
    if( (type & SpecialExit.RANDOM_DESTINATION) != 0 )
      System.out.print("RANDOM_DESTINATION" );
    System.out.println();
    System.out.println("Available Chack: " + se.getAvailableCheck() );
    System.out.println("Arrival Trigger: " + se.getArrivalTrigger() );
    System.out.println("Destination Chack: " + se.getDestinationCheck() );
    System.out.println("Destinations: ");
    printSpecialExitDestinations( se );
    int i;
    System.out.println("Destination Costs: ");
    int[] costs = se.getDestinationCosts();
    if( costs == null ) System.out.println("Null");
    else
      for( i = 0; i < costs.length; i++ )
        System.out.println(Utils.niceInt(i,4)+"->"+Utils.niceInt(costs[i],4));
    System.out.println("Summon Commands:");
    String[] strs = se.getSummonCmds();
    if( strs == null ) System.out.println("null");
    else
      for( i = 0; i < strs.length; i++ )
        System.out.println( Utils.niceInt( i, 4 )+"-> "+strs[i] );
    System.out.println("Summon Triggers:");
    strs = se.getSummonTriggers();
    if( strs == null ) System.out.println("null");
    else
      for( i = 0; i < strs.length; i++ )
        System.out.println( Utils.niceInt( i, 4 )+"-> "+strs[i] );
    System.out.println("Available Triggers:");
    strs = se.getAvailableTriggers();
    if( strs == null ) System.out.println("null");
    else
      for( i = 0; i < strs.length; i++ )
        System.out.println( Utils.niceInt( i, 4 )+"-> "+strs[i] );
    System.out.println("Destination Triggers:");
    strs = se.getDestinationTriggers();
    if( strs == null ) System.out.println("null");
    else
      for( i = 0; i < strs.length; i++ )
        System.out.println( Utils.niceInt( i, 4 )+"-> "+strs[i] );
  }
  public void printSpecialExitDestinations( SpecialExit se ){
    int[] dests = se.getDestinations();
    if( dests == null ) {
      System.out.println("Null");
      return;
    }
    int i;
    for( i = 0; i < dests.length; i++ ){
      System.out.println( Utils.niceInt( i, 4 )+"->"+Utils.niceInt(dests[i],4)
          + " ("+map.getRoom(dests[i]).getShortDesc()+")");
    }
  }
  public void undo(){
    map.rollback();
  }
  public void save(){
    System.out.print("Saving map...");
    map.save();
    System.out.println("Done.");
  }
  public static void main(String[] args ){
    File dir = null;;
    String input;
    boolean b = false;
    Quickie q = null;
    while( !b ){
      System.out.print("Enter a map data directory ");
      input = readFromIn();
      dir = new File( input );
      if( dir.isDirectory() )
        b = true;
      else
        if( confirm("No directory by that name.  Create?") )
          if(dir.mkdirs())
            b = true;
          else
            System.out.println("Failed to create directory.");
    }
    b = false;
    while( !b ){
      System.out.print("Enter a map name ");
      input = readFromIn("test");
      if( MudMap.mapExists( dir.getAbsolutePath(), input ) ){
        if( confirm("map exists. load it?", true ) ){
          q = new Quickie( dir.getAbsolutePath(), input );
          b = true;
        }
      }else{
        if( confirm("map does not exist. create it?", false ) ){
          q = new Quickie( dir.getAbsolutePath(), input );
          b = true;
        }
      }
    }
    q.editMudMap();
  }
  private static String readFromIn( String message, String defaultVal ){
    System.out.print( message );
    return readFromIn( defaultVal );
  }
  private static String readFromIn( String defaultVal ){
    try{
      if( defaultVal != null && defaultVal.length() != 0 )
        System.out.print(" ["+defaultVal+"] " );
      String s = inReader.readLine();
      if( ("").equals(s) )
        s = defaultVal;
      return s;
    }catch(IOException e ){
      return defaultVal;
    }
  }
  private static String readFromIn(){
    try{
      return inReader.readLine( );
    }catch(IOException e ){
      return null;
    }
  }
  private static int getNumber( String message ){
    return getNumber( message, 0, 0 );
  }
  private static int getNumber( String message, int defaultValue, int tries ){
    int val = defaultValue;
    int i;
    for( i = 0; i < tries || tries < 1; i++ ){
      if( message != null )
        System.out.print( message );
      String s = readFromIn(  );
      try{
        val = Integer.parseInt( s );
        return val;
      }catch( NumberFormatException e ){
        System.out.println( "could not format number." );
      }
    }
    return val;
  }
  private static boolean confirm( String message ){
    return confirm( message, false );
  }
  private static boolean confirm( String message, boolean defaultValue ){
    return confirm( message, defaultValue, 1 );
  }
  private static boolean confirm( 
      String message, 
      boolean defaultValue, 
      int tries ){
    int i;
    for( i = 0; i < tries || tries < 1; i++ ){
      System.out.print( message + " (Y/N) [" + (defaultValue?"Y":"N") + "]");
      String s = readFromIn();
      if( s.startsWith( "y" ) || s.startsWith( "Y") )
        return true;
      if( s.startsWith( "n" ) || s.startsWith( "N") )
        return false;
    }
    return defaultValue;
  }
  private static String niceInt( int i, int width ){
    if (width > 14) width = 14;
    String iStr = "" + i;
    int len = iStr.length();
    if( len >= width ) return iStr;
    return ("             ").substring(13-(width-len)) + iStr;
  }
}
