package mudclient.core.commands;

import mudclient.core.*;
import java.util.*;
import java.util.regex.*;

public class List implements Command{

  private static String legalFlags = "lgqfh";
  private static String flagMatchPref =
      "^-" + // flags must start the argument, and we trim the string
      "[" + legalFlags + "]*" + // we can have any legal flags grouped
      "(\\s-[" + legalFlags + "]*)*";  // we might have separate flags
  private static String flagMatchSuff =
  "[" + legalFlags + "]*(\\s|$)"; // there might be more in this group
  private static Matcher localMatcher = Pattern.compile( 
      flagMatchPref + "l" + flagMatchSuff ).matcher("");
  private static Matcher globalMatcher = Pattern.compile( 
      flagMatchPref + "g" + flagMatchSuff ).matcher("");
  private static Matcher quietMatcher = Pattern.compile( 
      flagMatchPref + "q" + flagMatchSuff ).matcher("");
  private static Matcher formatedMatcher = Pattern.compile( 
      flagMatchPref + "f" + flagMatchSuff ).matcher("");
  private static Matcher helpMatcher = Pattern.compile( 
      flagMatchPref + "h" + flagMatchSuff ).matcher("");

  public List( ){
  }

  public String getName(){
    return "list";
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

    String s;
    StringBuffer sb = new StringBuffer();
    Iterator<String> keyIterator = null;
    // handle no-arg version
    boolean global = false;
    boolean local = false;
    boolean quiet = false;
    boolean formated = false;
    boolean help = false;
    String ar = tools.Utils.arrayToString( args, " " );
    if( ar != null && ar.length() > 1 ){
      synchronized( globalMatcher ){
        helpMatcher.reset( ar );
        if( helpMatcher.matches() ) help = true;
        globalMatcher.reset( ar );
        if( globalMatcher.matches() ) global = true;
        localMatcher.reset( ar );
        if( localMatcher.matches() ) local = true;
        quietMatcher.reset( ar );
        if( quietMatcher.matches() ) quiet = true;
        formatedMatcher.reset( ar );
        if( formatedMatcher.matches() ) formated = true;
      }
    }
    if( help ){
      MudClient.display( getHelpString() );
    }
    if( global ){
      scope = scope.getRoot();
      keyIterator = scope.getVarNames().iterator();
    }else if( local ){
      keyIterator = scope.getLocalVarNames().iterator();
    }else if( formated ){
      s = formattedList( sb, scope );
      if( !quiet )
        MudClient.display( s );
      return s;
    }
    // default to the no-arg version. standard iterating through
    if( keyIterator == null )
      keyIterator = scope.getVarNames().iterator();
    while( keyIterator.hasNext() )
      sb.append( scope.getDefineString( keyIterator.next() ) ).append("\n");
    s = sb.toString();
    if( !quiet )
      MudClient.display( s );
    return s;
  }
  private String formattedList( StringBuffer sb, VariableScope scope ){
    if( scope.getParent() != null )
      formattedList( sb, scope.getParent() );
    sb.append( "## Scope Level Change ##\n");
    Iterator<String> keyIterator = scope.getLocalVarNames().iterator();
    while( keyIterator.hasNext() )
      sb.append( scope.getDefineString( keyIterator.next() ) ).append("\n");
    return sb.toString();
  }
  public String getHelpString(){
    return "Usage: /list [-glfqh]\n\n" +
          "flags:\n" +
          "-g\tList only global definitions\n" +
          "\t\t(takes precedence over -l and -f)\n\n" +
          "-l\tList only local definitions (takes precedence over -f)\n\n" +
          "-f\tList all definitions in formatted form, including those\n" +
          "\t\tthat are hidden by more local scope.\n\n" +
          "-q\t(quiet) do not display list to ui, just return as value.\n";
  }
}
