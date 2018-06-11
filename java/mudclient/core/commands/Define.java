package mudclient.core.commands;

import mudclient.core.*;
import java.util.*;
import java.util.regex.*;

public class Define implements Command{

  private static String legalFlags = "hg";
  private static Matcher validMatcher = Pattern.compile(
      "^\\s*(" + // may or may not have flags (capturing paren 1)
      "(" + // may have one or more flag arguments
      "-[" + legalFlags + "]+" + // - followed by legal flags (>0)
      "(\\s|$)" + // followed by space or end of string
      ")+" + // end one or more flag blocks
      ")?" +  // end of potential flag section (capturing paren 1)
      "(" + // the name is not optional (capturing paren 4)
      "\\w+" + // [a-zA-Z_0-9], no .s, or anything fancy
      ")" + // end of name section (capturing paren 4)
      "(\\s+|$)" + // whitespace or end of String
      "(" + // may or may not specify value (capturing paren 6)
      ".+" + // value (match anything to end of String, really)
      ")?$" // end value capturing section (capturing paren 6)
      ).matcher("");

  private static Matcher triggerMatcher = Pattern.compile(
      "^\\s" + // potentially start with white space
      "*-t" +  // first valid non-whitespace must be -t.
      "\\s+" + // must have at least a bit of whitespace
      "(.*)$"  // this captures everything to the end, which includes 
               // any body of the definition following the regex.
      ).matcher("");

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

  public Define( ){
  }

  public String getName(){
    return "def";
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

    // handle the empty definition.
    // for now, no-op, later, maybe a list of defined things
    // the args and body of the command.
    String body;
    body = tools.Utils.arrayToString( args, " " ).trim();
    boolean help = false;
    if( body == null || body.length() == 0 )
      help = true;

    if( help ){
      MudClient.display( getHelpString() );
      return null;
    }
    if( scope == null && wc != null )
      scope = wc.getWorldVariables();
    if( scope == null )
      scope = MudClient.getRootVariableScope();

    String name = null;
    String value = null;
    String flags = null;

    synchronized(validMatcher){
      validMatcher.reset( body );
      if( validMatcher.find() ){
        name = validMatcher.group( 4 );
        value = validMatcher.group( 6 );
        flags = validMatcher.group( 1 );
      } else
        help = true;
    }
    if( help ){
      MudClient.display( getHelpString() );
      return null;
    }
    // first, do we want it to be global?
    boolean global = false;
    if( flags != null ){
      synchronized( helpMatcher ){
        helpMatcher.reset( flags );
        if( helpMatcher.matches() ){
          help = true;
        }
      }
      synchronized( globalMatcher ){
        globalMatcher.reset( flags );
        if( globalMatcher.matches() ){
          global = true;
        }
      }
    }
    if( name == null || name.length() == 0 )
      help = true;
    if( help ){
      MudClient.display( getHelpString() );
      return null;
    }
    if( global ){
      scope = scope.getRoot();
    }
    if( value == null ||
        value.length() == 0 ) // just "def name" which we treat as undef
      return scope.unSetVariable( name );
    // at this point we know we have value, a valid name, and that
    // 'h' was not in the flags section.
    // Check to see if this is a trigger.
    synchronized( triggerMatcher ){
      triggerMatcher.reset( value );
      if( triggerMatcher.matches() ){
        TODO;
      }
    }
    ActionSequence sequence = new ActionSequence();
    int i1, i2;
    i1 = 0;
    i2 = -1;
    while( i1 < value.length() ){
      if( value.charAt( i1 ) == '(' ){
        i2 = findMatchingParen( value, i1 );
        if( i2 != -1 ){
          sequence.addStep( value.substring( ++i1, i2 ) );
          i1 = i2+2;
        }else{
          sequence.addStep( value.substring( ++i1 ) );
          break;
        }
      }else{
        i2 = value.indexOf("/", i1);
        if( i2 != -1 ){
          sequence.addStep( value.substring( i1, i2 ) );
          i1 = i2+1;
        }else{
          sequence.addStep( value.substring( i1 ) );
          break;
        }
      }
    }
    if( sequence.getSteps().length == 0 ){ // again with the paranoia
      return scope.unSetVariable( name );
    } else {
      return scope.setValue( name, sequence );
    }
  }
  private static int findMatchingParen( String s, int i ){
    char open = s.charAt( i );
    char close;
    if( open == '(' ) close = ')';
    else if( open == '{' ) close = '}';
    else if( open == '[' ) close = ']';
    else return -1;
    int depth = 1;
    int length = s.length();
    while( depth > 0 && ++i < length ){
      char c = s.charAt( i );
      if( c == open ) depth++;
      else if( c == close ) depth--;
    }
    return (i < length) ? i : -1;
  }
  public String getHelpString(){
    return "Usage: /def [-hg] <name> [command[/command]*]\n" +
           "       /def [-hg] <name> -t {regex} [command[/command]*]\n\n" +
          "Flags:\n" +
          "-h\tDisplay this help and exit\n" +
          "-g\tDefine a global variable\n" +
          "-t\tDefine a trigger.  Note that you can use any style of braces\n" +
          "\tyou want, '(', '{', or '[', but any braces within the regex of\n" +
          "\tthe same type must have open and close equality, and the\n" +
          "\tenclosing pair are not included in the regex.";
  }
}
