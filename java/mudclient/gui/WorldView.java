package mudclient.gui;

import java.util.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.text.Segment;
import java.text.AttributedCharacterIterator.Attribute;
import java.awt.font.TextAttribute;
import mudclient.core.*;

/** This encapsulates the input and output buffers of
 * a single connection.  
 */
public class WorldView extends JSplitPane{

  private TLBView inputView;
  private TLBView outputView;
  // we use a convention here. we terminate the array with null
  // 16 should be plenty, but we check as we grow it just in case...
  private char[] outputPartialAnsi = new char[ 16 ];
  private Map<Attribute, Object> currentAttributes;
  private static final Integer MINUS_ONE = new Integer( -1 );
  public static Map<Attribute, Object> DEFAULT_ATTRIBUTES;
  private HashMap<char[], Map<Attribute, Object>> attributeCache;

  static{
    DEFAULT_ATTRIBUTES = new HashMap<Attribute, Object>();
    DEFAULT_ATTRIBUTES.put( TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD );
  }

  public WorldView(){
    super( JSplitPane.VERTICAL_SPLIT, 
           true,
           new TLBView( 5000 ),
           new TLBView( 1000 ) );

    outputView = (TLBView)getTopComponent();
    inputView = (TLBView)getBottomComponent();
    setResizeWeight( 0.8f );
    setDividerSize( 3 );
    attributeCache = new HashMap<char[], Map<Attribute, Object>>();
    currentAttributes = DEFAULT_ATTRIBUTES;
  }
  public void display( Segment s ){
for( int ii = s.offset; ii < s.offset + s.count; ii++ )
if( Character.isISOControl(s.array[ii]) && 
        s.array[ii] != 0x0D &&  // ignore \r
        s.array[ii] != 0x0A &&  // ignore \n
        s.array[ii] != 0x09 ){  // ignore \t
System.out.printf("got ISO Control character 0x%02X.\n", (s.array[ii] & 0xFF));
int jj = ii - 2;
if( jj < s.offset ) jj = s.offset;
System.out.print("Surrounding characters are: \"");
for( ; jj < ii + 6 && jj < s.offset + s.count; jj++ ){
if( Character.isISOControl(s.array[jj]) )
System.out.printf("0x%02X,", (s.array[jj] & 0xFF) );
else
System.out.print(s.array[jj] + ",");
}
System.out.println();
}
    if( outputPartialAnsi[0] == AnsiCode.CSIA )
      completeAnsi( s );
    int end = s.offset + s.count;
    for( int i = s.offset; i < end; i++ ){
      if( s.array[i] == AnsiCode.CSIA ){
        appendToOutput( s.array, s.offset, i - s.offset, currentAttributes );
        //outputView.getTextLineBuffer().append( s.array, s.offset, i - s.offset );
        s.count -= i - s.offset;
        s.offset = i;
        completeAnsi( s );
      }
    }
    appendToOutput( s, currentAttributes );
    //outputView.getTextLineBuffer().append( s );
  }
  private void appendToOutput( Segment s, Map<Attribute, Object> textAttributes ){
    appendToOutput( s.array, s.offset, s.count, textAttributes );
  }
  private void appendToOutput( char[] c,
                               int offset,
                               int length,
                               Map<Attribute, Object> textAttributes ){
    TextLineBuffer output = outputView.getTextLineBuffer();
    TLBAttributes atts = outputView.getTLBAttributes();
    atts.setAttributes( textAttributes, -1, -1 );
    output.append( c, offset, length );
  }
  // this reads ansi codes from s, and stores them in outputPartialAnsi
  // until it's done.  It also modifies s to 'use up' the characters it reads
  private void completeAnsi( Segment s ){
    int i = -1;
    // empty loop to find the end of the useful part of the array.
    while( outputPartialAnsi[++i] != 0 );
    while( s.count > 0 ){
      s.count--;
      // if we need more room, make it.
      if( i+2 >= outputPartialAnsi.length ){
        char[] newArray = new char[ outputPartialAnsi.length * 2 ];
        System.arraycopy( outputPartialAnsi, 0, 
                          newArray, 0, outputPartialAnsi.length );
        outputPartialAnsi = newArray;
      }
      // ansi codes consist of "<esc>["followed by 0 or more 
      // semicolon delimeted numbers, followed by a letter.  We 
      // only handle the 'm' (display mode, mostly used for color)

      char c = outputPartialAnsi[i++] = s.array[s.offset++];
      if( c == AnsiCode.CST ){
        currentAttributes = getAttributesFromAnsi( outputPartialAnsi, 0, i );
        outputPartialAnsi[0] = 0;
        break;
      } else if( c != AnsiCode.CSIA && 
                 c != AnsiCode.CSIB &&
                 c != AnsiCode.CSD &&
                 !Character.isDigit( c ) ) {
        // some non-handled ansi code (like clear screen or cursor movement...
        outputPartialAnsi[0] = 0;
        break;
      } else {
        // set the next character to terminate the array for the case where
        // we're out of s.
        outputPartialAnsi[ i ] = 0;
      }
    }
  }
  public void logInput( String s ){
    inputView.getTextLineBuffer().append( s.toCharArray() );
  }
  public TLBView getInputView(){
    return inputView;
  }
  public TLBView getOutputView(){
    return outputView;
  }

  public void setForeground( Color c ){
    super.setForeground( c );
    try{
      inputView.setForeground( c );
      outputView.setForeground( c );
    } catch( NullPointerException e ){
      getTopComponent().setForeground( c );
      getBottomComponent().setForeground( c );
    }
  }
  public void setBackground( Color c ){
    super.setBackground( c );
    try{
      inputView.setBackground( c );
      outputView.setBackground( c );
    } catch( NullPointerException e ){
      getTopComponent().setBackground( c );
      getBottomComponent().setBackground( c );
    }
  }
  public void setFont( Font f ){
    super.setFont( f );
    try{
      inputView.setFont( f );
      outputView.setFont( f );
    } catch( NullPointerException e ){
      getTopComponent().setFont( f );
      getBottomComponent().setFont( f );
    }
  }
}
