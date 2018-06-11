package tools;

import java.io.*;
import java.util.*;

public final class Utils{

  public static final String niceInt( long i, int width ){
    String spaces = "                              ";
    String iStr = "" + i;
    if( width > 30 ) width = 30;
    return iStr.length()<width
      ?  spaces.substring(30-(width - iStr.length()))+iStr
      : iStr;
  }
  public static final String arrayToString( String[] array, String delimiter ){
    StringBuffer sb = new StringBuffer();
    if( array == null || array.length == 0 ) return "";
    if( delimiter == null ) delimiter = "";
    for( int i = 0; i < array.length - 1; i++ ){
      sb.append( array[i] ).append( delimiter );
    }
    sb.append( array[array.length-1] );
    return sb.toString();
  }
  public static final String[] tokensToArray( String vals, String delimeter ){
    if( vals == null ) return null;
    StringTokenizer tok;
    if( delimeter == null )
      tok = new StringTokenizer( vals );
    else
      tok = new StringTokenizer( vals, delimeter );
    int len = tok.countTokens();
    String[] strs = new String[ len ];
    int i;
    for( i = 0; i < len; i++)
      strs[i] = tok.nextToken();
    return strs;
  }
  public static final String horizontalList( String vals, String delimeter ){
    return horizontalList( tokensToArray( vals, delimeter ) );
  }
  public static final String horizontalList( 
      String vals, 
      String delimeter,
      int startWidth,
      int width){
    return horizontalList( tokensToArray(vals, delimeter), startWidth, width );
  }
  public static final String horizontalList(
      String vals, 
      String delimeter,
      int width){
    return horizontalList( tokensToArray(vals, delimeter), width );
  }
  public static final String horizontalList( int[] vals ){
    return horizontalList( vals, 0, 75 );
  }
  public static final String horizontalList( String[] vals ){
    return horizontalList( vals, 0, 75 );
  }
  public static final String horizontalList( int[] vals, int width ){
    return horizontalList( vals, 0, width );
  }
  public static final String horizontalList( String[] vals, int width ){
    return horizontalList( vals, 0, width );
  }
  public static final String horizontalList( 
      int[] vals, 
      int startwidth, 
      int width ){
    if( vals == null || vals.length == 0 ) return "";
    StringBuffer sb = new StringBuffer( "" + vals[0] );
    int len = startwidth;
    len += ("" + vals[0]).length();
    for( int i = 1; i < vals.length; i++ ){
      if( len + (""+vals[i]).length() > width ){
        sb.append(",\n");
        len = 0;
      }else{
        sb.append(", ");
        len += 2;
      }
      sb.append( ""+vals[i] );
      len += (""+vals[i]).length();
    }
    return sb.toString();
  }
  public static final String horizontalList( 
      String[] vals, 
      int startwidth, 
      int width ){
    if( vals == null || vals.length == 0 ) return "";
    StringBuffer sb = new StringBuffer( vals[0] );
    int len = startwidth;
    len += vals[0].length();
    for( int i = 1; i < vals.length; i++ ){
      if( len + vals[i].length() > width ){
        sb.append(",\n");
        len = 0;
      }else{
        sb.append(", ");
        len += 2;
      }
      sb.append( vals[i] );
      len += vals[i].length();
    }
    return sb.toString();
  }
  public static String readFromIn(){
    try{
    return new BufferedReader( new InputStreamReader( System.in ) ).readLine();
    }catch (IOException e){
      return null;
    }
  }
}
