package test;

import java.io.*;
import tools.persistence.*;

public class PersistentStoreTest{
  private static final int ARRAY_SIZE = 100;
  private static IntIndex intIndex = new IntIndex();
  private static StringIndex stringIndex = new StringIndex();

  public static void main(String[] args){
    int arraySize = ARRAY_SIZE;
    try{
      if( args.length > 0 )
        arraySize = java.lang.Integer.parseInt( args[ 0 ] );
      File myFile = new File( "/home/bill/automapper.dat/test/"
          + "PersistentStoreTest.dat" );
      PersistentStore aPS = new PersistentStore( 
          myFile,
          new Index[]{ intIndex, stringIndex },
          new TestStorable( -1, null, -1 ) );
      int i;
      int[] ids;
      TestStorable[] ts = new TestStorable[ arraySize ];
      TestStorable t;
      for( i = 0; i < arraySize; i++ ){
        ts[i] = new TestStorable( i, "Test object number " + i, i/2 );
      }
      System.out.println("MaxID is " + aPS.getMaxID() );
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      //System.out.println( aPS.getReport() );
      System.out.println("putting array in aPS");
      for( i = 0; i < arraySize; i++ ){
        aPS.store( ts[i] );
        //System.out.print(".");
      }
      System.out.println("MaxID is " + aPS.getMaxID() );
      //System.out.println( aPS.getReport() );
      System.out.println("number of objects stored is now " 
          + aPS.getIDs().length );
      System.out.println("number of objects with nonID int of 4 is "
          + (ids = aPS.getIDs( 
              new int[]{ intIndex.getCode( 4 ), 0 },
              new boolean[] { true, false } 
              )).length );
      printArray( ids );

      System.out.println( "packing...");
      aPS.pack();
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      //System.out.println( aPS.getReport() );

      System.out.println("deleting evens");
      for( i = 0; i < arraySize; i += 2 ){
        aPS.deleteStorable( ts[i].getID() );
        //System.out.print(".");
      }
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      System.out.println("number of objects with nonID int of 4 is "
          + (ids = aPS.getIDs( 
              new int[]{ intIndex.getCode( 4 ), 0 },
              new boolean[] { true, false } 
              )).length );
      printArray( ids );
      System.out.println("rolling back.");
      aPS.rollback();
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      System.out.println("number of objects with nonID int of 4 is "
          + (ids = aPS.getIDs( 
              new int[]{ intIndex.getCode( 4 ), 0 },
              new boolean[] { true, false } 
              )).length );
      printArray( ids );

      System.out.println( "packing...");
      aPS.pack();
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      //System.out.println( aPS.getReport() );

      System.out.println("deleting multiples of 3");
      for( i = 0; i < arraySize; i += 3 ){
        aPS.deleteStorable( ts[i].getID() );
        //System.out.print(".");
      }
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      System.out.println("number of objects with nonID int of 4 is "
          + (ids = aPS.getIDs( 
              new int[]{ intIndex.getCode( 4 ), 0 },
              new boolean[] { true, false } 
              )).length );
      printArray( ids );

      System.out.println( "packing...");
      aPS.pack();
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      System.out.println(" re-populating data to original size");
      for( i = 0; i < arraySize; i++ ){
        aPS.store( ts[i] );
      }
      System.out.println("number of objects stored is now " 
          + (ids = aPS.getIDs()).length );
      System.out.println("beginning read-speed test: ");
      System.out.println("will read through all objects 10 times.");
      for( int j = 0; j < 10; j++ ){
        ids = aPS.getIDs();
        for( i = 0; i < ids.length; i++ ){
          t = new TestStorable( -1, null, -1 );
          aPS.loadStorable( ids[i], t );
        }
      }
      System.out.println("Done with read-speed test.");
      System.out.println( "packing...");
      aPS.pack();
      System.out.println("beginning read-speed test: ");
      System.out.println("will read through all objects 10 times.");
      for( int j = 0; j < 10; j++ ){
        ids = aPS.getIDs();
        for( i = 0; i < ids.length; i++ ){
          t = new TestStorable( -1, null, -1 );
          aPS.loadStorable( ids[i], t );
        }
      }
      System.out.println("Done with read-speed test.");
      //System.out.println( aPS.getReport() );

    }
    catch ( IOException e ){
      e.printStackTrace( System.err );
    }
  }
  private static void printArray( String[] a ){
    int i;
    if( a == null ){
      System.out.println( "null" );
      return;
    }
    if( a.length == 0 ){
      System.out.println( "empty" );
      return;
    }
    System.out.print( a[0] );
    for( i = 1; i < a.length; i++ )
      System.out.print(", " + a[i] );
    System.out.println();
  }
  private static void printArray( int[] a ){
    int i;
    if( a == null ){
      System.out.println( "null" );
      return;
    }
    if( a.length == 0 ){
      System.out.println( "empty" );
      return;
    }
    System.out.print( "" + a[0] );
    for( i = 1; i < a.length; i++ )
      System.out.print(", " + a[i] );
    System.out.println();
  }

  private static class TestStorable implements Storable{
    private String s;
    private int stl;
    private int id;
    private int otherInt;
    public TestStorable( int ID, String str, int nonIDInt ){
      id = ID;
      s = str;
      otherInt = nonIDInt;
      stl = 16 + (s == null? 0 : s.getBytes().length);
    }
    public int getID(){
      return id;
    }
    public int getStreamedLength(){
      return stl;
    }
    public void setString( String str ){
      stl -= (s == null)? 0 : s.getBytes().length;
      s = str;
      stl += (s == null)? 0 : s.getBytes().length;
    }
    public String getString(){
      return s;
    }
    public void setInt( int i ){
      otherInt = i;
    }
    public int getInt(){
      return otherInt;
    }
    public void getState( DataOutputStream os ) throws IOException {
      os.writeInt( stl );
      os.writeInt( id );
      os.writeInt( otherInt );
      if( s == null )
        os.writeInt( 0 );
      else{
        byte[] bs = s.getBytes();
        os.writeInt( bs.length );
        os.write( bs );
      }
    }
    public void setState( DataInputStream is ) throws IOException {
      stl = is.readInt();
      id = is.readInt();
      otherInt = is.readInt();
      int l = is.readInt();
      if( l == 0 ) s = null;
      else{
        byte[] bs = new byte[ l ];
        is.readFully( bs );
        s = new String( bs );
      }
    }
  }
  private static class IntIndex implements Index{
    public int getCode( Object o ){
      return getCode( ((TestStorable)o).getInt() );
    }
    public int getCode( int o ){
      return o;
    }
  }
  private static class StringIndex implements Index{
    public int getCode( Object o ){
      return getCode( ((TestStorable)o).getString() );
    }
    public int getCode( String o ){
      return o.hashCode();
    }
  }
}
