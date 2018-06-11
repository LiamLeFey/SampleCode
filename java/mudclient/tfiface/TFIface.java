package mudclient.tfiface;

import java.io.*;
import tools.Utils;
import tools.BlockingISReadLiner;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class TFIface{
  private final static String password;
  static{
    Random r = new Random();
    password = "pw" + r.nextInt();
  }
  public static void main( String[] args ){
    try{
      File homeDir = new File( System.getProperty( "user.home" ) );
      File mapsDir = new File( homeDir, "automapper.dat" );

      System.out.println( password );
      Thread errThread = new Thread( new Runnable(){
        public void run(){
          try{
            Socket errSocket = null;
            ServerSocket errSSok = new ServerSocket( 0 );
            System.out.println( "err=" + errSSok.getLocalPort() );
            errSocket = errSSok.accept();
            errSSok.close();

            InputStream in = errSocket.getInputStream();
            BufferedReader br = new BufferedReader( 
              new InputStreamReader( in ) );
            String pw = br.readLine();
            if( ! password.equals( pw ) ){
              throw new Exception("wrong pwd: expected "+password+" got "+pw);
            }

            OutputStream err = errSocket.getOutputStream();
            PrintStream errStream = new PrintStream( err );
            System.setErr( errStream );
          }catch( Exception e ){
            e.printStackTrace( System.err );
          }
        }
      } );
      errThread.start();
      Socket outSocket = null;
      ServerSocket outSSok = new ServerSocket( 0 );
      System.out.println( "out=" + outSSok.getLocalPort() );
      outSocket = outSSok.accept();
      outSSok.close();

      OutputStream out = outSocket.getOutputStream();
      InputStream in = outSocket.getInputStream();
      BufferedReader br = new BufferedReader( new InputStreamReader( in ) );
      String pw = br.readLine();
      if( ! password.equals( pw ) ){
        throw new Exception("wrong password: expected "+password+" got "+pw);
      }

      PrintStream outStream = new PrintStream( out );
      System.setIn( in );
      System.setOut( outStream );

      errThread.join();


// the real program goes here when we start to rock&roll
  
      String instr;
      //BlockingISReadLiner inReader = new BlockingISReadLiner( System.in );
      BufferedReader inReader = new BufferedReader( 
          new InputStreamReader( System.in ) );
      do{
        instr = inReader.readLine();
        System.out.println( "Read '" + instr + "' from System.in.");
        //try{
          //Thread.sleep( 1000 );
        //}catch(InterruptedException e ){
          //e.printStackTrace( System.err );
        //}
      }while( ! "stop".equalsIgnoreCase( instr ) );
    }catch( Exception e ){
      e.printStackTrace( System.err );
    }
  }
  //private void handleWhereAmI(){
  //}
  //private void handleGo(){
  //}
  //private void handleAddRoom(){
  //}
}
