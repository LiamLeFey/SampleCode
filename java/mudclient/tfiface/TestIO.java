package mudclient.tfiface;

import java.io.*;
import tools.Utils;
import tools.BlockingISReadLiner;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class TestIO{
  private final static String password;
  static{
    Random r = new Random();
    password = "pw" + r.nextInt();
  }
  public static void main( String[] args ){
    try{
      File homeDir = new File( System.getProperty( "user.home" ) );
      File mapsDir = new File( homeDir, "automapper.dat" );
      File tfIODir = null;
      if( args.length == 1 ){
        tfIODir = new File( args[0] );
      }else{
        tfIODir = new File( homeDir, "tf" );
      }

      //String tempDirName = System.getProperty( "java.io.tmpdir" );
      //File tempDir = new File( tempDirName );
      //File outFile = File.createTempFile( "iface.System.out", null, tempDir );
      //File errFile = File.createTempFile( "iface.System.err", null, tempDir );
      //File inFile = File.createTempFile( "iface.System.in", null, tempDir );

      //File outFile = new File( tfIODir, "iface.System.out" );
      //File errFile = new File( tfIODir, "iface.System.err" );
      //File inFile = new File( tfIODir, "iface.System.in" );
      //outFile.deleteOnExit();
      //errFile.deleteOnExit();
      //inFile.deleteOnExit();
      System.out.println( password );
      Socket outSocket = null;
      Thread errThread = new Thread( new Runnable(){
        public void run(){
          try{
            Socket errSocket = null;
            ServerSocket errSSok = new ServerSocket( 0 );
            System.out.println( "err=" + errSSok.getLocalPort() );
            errSocket = errSSok.accept();
            errSSok.close();
            OutputStream err = errSocket.getOutputStream();
            PrintStream errStream = new PrintStream( err );
            InputStream in = errSocket.getInputStream();
            BufferedReader br = new BufferedReader( 
              new InputStreamReader( in ) );
            String pw = br.readLine();
            if( ! password.equals( pw ) ){
              throw new Exception("wrong pwd: expected "+password+" got "+pw);
            }
            System.setErr( errStream );
          }catch( Exception e ){
            e.printStackTrace( System.err );
          }
        }
      } );
      errThread.start();
      ServerSocket outSSok = new ServerSocket( 0 );
      System.out.println( "out=" + outSSok.getLocalPort() );
      outSocket = outSSok.accept();
      outSSok.close();

      errThread.join();

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


// the real program goes here when we start to rock&roll
      System.out.println("System.out message");
      System.out.println("System.out message number 2");
      System.out.println("System.out message number 3");
      System.out.println("System.out message number 4");
      System.err.println("System.err message");
      System.err.println("System.err message number 2");
      System.err.println("System.err message number 3");
      System.err.println("System.err message number 4");
      System.err.println("System.err message number 5");
      System.err.println("System.err message number 6");
      System.err.println("System.err message number 7");
      System.err.println("System.err message number 8");
      System.err.println("System.err message number 9");
  
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
}
