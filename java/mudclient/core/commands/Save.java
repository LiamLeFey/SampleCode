package mudclient.core.commands;

import mudclient.core.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

public class Save implements Command{

  private static final File homeDir = new File(System.getProperty("user.home"));
  private static final String defaultFileName = ".mudclientrc";

  // help, global, local, append
  private static String legalFlags = "hglfa";
  // hopefully this is reasonably efficient.  it pulls out flags and
  // filename as $1 and $4 respectively if they exist.
  private static Matcher validMatcher = Pattern.compile(
      "^\\s*(" + // may or may not have flags (capturing paren 1)
      "(" + // may have one or more flag arguments
      "-[" + legalFlags + "]+" + // - followed by legal flags (>0)
      "(\\s|$)" + // followed by space or end of string
      ")+" + // end one or more flag blocks
      ")?" +  // end of potential flag section (capturing paren 1)
      "(" + // may or may not specify filename (capturing paren 4)
      "\\.?" + // potential leading dot (hidden file in unix fs)
      "\\w+" + // filename
      "(\\.\\w+)*" + // potential dot extensions (unlimited)
                   // note that this should not match "..",
                   // and our filenames cannot end with "."
      ")?" // end filename capturing section (capturing paren 4)
      ).matcher("");

  // we can group the flags, like -la (local and append) or 
  // separate them like -l -a.
  private static String flagMatchPref =
      "^-" + // flags must start the argument, and we trim the string
      "[" + legalFlags + "]*" + // we can have any legal flags grouped
      "(\\s-[" + legalFlags + "]*)*";  // we might have separate flags
  private static String flagMatchSuff =
  "[" + legalFlags + "]*(\\s|$)"; // there might be more in this group
  private static Matcher helpMatcher = Pattern.compile( 
      flagMatchPref + "h" + flagMatchSuff ).matcher("");
  private static Matcher globalMatcher = Pattern.compile(
      flagMatchPref + "g" + flagMatchSuff ).matcher("");
  private static Matcher formatedMatcher = Pattern.compile(
      flagMatchPref + "f" + flagMatchSuff ).matcher("");
  private static Matcher localMatcher = Pattern.compile(
      flagMatchPref + "l" + flagMatchSuff ).matcher("");
  private static Matcher appendMatcher = Pattern.compile(
      flagMatchPref + "a" + flagMatchSuff ).matcher("");

  public Save( ){
  }

  public String getName(){
    return "save";
  }

  public Object doCommand( 
      String[] args,
      WorldConnection wc ){
    return doCommand( args, wc == null ? null : wc.getWorldVariables(), wc );
  }
  public Object doCommand( 
      String[] args,
      VariableScope scope,
      WorldConnection wc ){

    if( scope == null && wc != null )
      scope = wc.getWorldVariables();
    if( scope == null )
      scope = MudClient.getRootVariableScope();

    String s = tools.Utils.arrayToString( args, " " );
    boolean help = false;
    boolean global = false;
    boolean local = false;
    boolean formated = false;
    boolean append = false;
    String fileName = null;
    String flags = null;
    synchronized(validMatcher){
      validMatcher.reset( s );
      if( validMatcher.find() ){
        fileName = validMatcher.group( 4 );
        flags = validMatcher.group( 1 );
      } else
        help = true;
    }
    // we keep this outside the synchronized block to minimize the
    // time spent inside.  We don't know how long dispay takes, and
    // want to be thread friendly...
    if( help ){
      MudClient.display( getHelpString() );
      return null;
    }
    if( flags != null && flags.length() > 1 ){
      synchronized( helpMatcher ){
        helpMatcher.reset( flags );
        if( helpMatcher.matches() ){
          help = true;
        }
      }
      if( help ){
        MudClient.display( getHelpString() );
        return null;
      }
      synchronized( globalMatcher ){
        globalMatcher.reset( flags );
        if( globalMatcher.matches() ){
          global = true;
        }
      }
      synchronized( localMatcher ){
        localMatcher.reset( flags );
        if( localMatcher.matches() ){
          local = true;
        }
      }
      synchronized( formatedMatcher ){
        formatedMatcher.reset( flags );
        if( formatedMatcher.matches() ){
          formated = true;
        }
      }
      synchronized( appendMatcher ){
        appendMatcher.reset( flags );
        if( appendMatcher.matches() ){
          append = true;
        }
      }
    }
    File outFile;
    if( fileName != null && fileName.length() > 0 ){
      outFile = new File( fileName );
      if( ! outFile.isAbsolute() ){
        outFile = new File( homeDir, fileName );
      }
    } else {
      outFile = new File( homeDir, defaultFileName );
    }
    String[] listArgs = new String[]{ " -q " + 
        (local  ? " -l " : "" ) +
        (formated  ? " -f " : "" ) +
        (global ? " -g " : "" ) };
    String defs = "" + (new List()).doCommand( listArgs, scope, wc );
    try{
      FileWriter fw = new FileWriter( outFile, append );
      fw.write( defs );
      fw.flush();
      fw.close();
      MudClient.display( "wrote defs to file \"" + outFile + "\".\n" );
    } catch (IOException e){
      MudClient.display( "Could not write to file \"" + outFile + "\".\n" );
      MudClient.display( "Caught IOException " + e );
    }
    return outFile;

  }
  public String getHelpString(){
    return "Usage: /save [-hgla] [filename]\n" +
          "Options:\n" +
          "-h\tprints this help file.\n" +
          "-g\tsave global definitions only.\n" +
          "-l\tsave local definitions only.\n" +
          "-a\tappend to the file. Do not overwrite.\n";
  }
}
