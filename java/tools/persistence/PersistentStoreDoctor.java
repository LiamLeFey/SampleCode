package tools.persistence;

import java.io.*;
import tools.*;

public class PersistentStoreDoctor{
  public static void main( String[] args ){
    try{
      if( args.length != 1 ) return;
      File f = new File( args[0] );
    
      PersistentStore ps = new PersistentStore( f );
      System.out.println( ps.getReport() );
    }catch( IOException e ){
      e.printStackTrace( System.out );
    }
  }
}
