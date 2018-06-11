package test;

import java.util.regex.*;
import java.awt.Font;
import javax.swing.text.Segment;
// just a class for testing quickies, like "what's the default charset
// of this jvm?"
public class RegexTest{
  private static String legalFlags = "gh";
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
  public static void main( String[] args ){
    String testString = new String("-g name value");
    validMatcher.reset( testString );
    System.out.println( "test String: " + testString );
    System.out.println( "pattern: " + validMatcher.pattern() );
    String name, value, flags;
    if( validMatcher.find() ){
      System.out.println("got a match!");

        name = validMatcher.group( 4 );
        value = validMatcher.group( 6 );
        flags = validMatcher.group( 1 );
        System.out.println("flags: \"" + flags + "\"");
        System.out.println("name: \"" + name + "\"");
        System.out.println("value: \"" + value + "\"");
    } else {
      System.out.println("failed to match.");
    }
    System.out.println("matcher Info: " + validMatcher );
  }
}
