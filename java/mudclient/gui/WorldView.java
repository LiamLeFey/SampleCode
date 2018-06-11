package mudclient.gui;

import java.util.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.text.Segment;
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

  private static final Integer MINUS_ONE = new Integer( -1 );

  public WorldView(){
    super( JSplitPane.VERTICAL_SPLIT, 
           true,
           new TLBView( 5000 ),
           new TLBView( 1000 ) );

    outputView = (TLBView)getTopComponent();
    inputView = (TLBView)getBottomComponent();
    outputView.setPreferredLineCount( 23 );
    inputView.setPreferredLineCount( 5 );
    setResizeWeight( 0.8f );
    setDividerSize( 3 );
  }
  public void display( Segment s ){
    // we copy the segment, because we chew up the
    // offset and count, (but don't mess with array data)
    // and we don't want to screw up the caller.
    Segment sc = new Segment();
    sc.array = s.array;
    sc.offset = s.offset;
    sc.count = s.count;
for( int ii = sc.offset; ii < sc.offset + sc.count; ii++ )
if( Character.isISOControl(sc.array[ii]) && 
        sc.array[ii] != AnsiCode.CSIA &&  // ignore <esc>
        sc.array[ii] != 0x0D &&  // ignore \r
        sc.array[ii] != 0x0A &&  // ignore \n
        sc.array[ii] != 0x09 ){  // ignore \t
        if( sc.array[ii] == 0x01 ){ // used to terminate a prompt
          sc.array[ii] = '\n';  // for now, just replace with newline
          continue;
        }
System.out.printf("got ISO Control character 0x%02X.\n", (sc.array[ii] & 0xFF));
int jj = ii - 5;
if( jj < sc.offset ) jj = sc.offset;
System.out.print("Surrounding characters are: \"");
for( ; jj < ii + 12 && jj < sc.offset + sc.count; jj++ ){
if( Character.isISOControl(sc.array[jj]) )
System.out.printf("0x%02X,", (sc.array[jj] & 0xFF) );
else
System.out.print(sc.array[jj] + ",");
}
System.out.println();
}
    if( outputPartialAnsi[0] == AnsiCode.CSIA )
      completeAnsi( sc );
    int end = sc.offset + sc.count;
    for( int i = sc.offset; i < end; i++ ){
      if( sc.array[i] == AnsiCode.CSIA ){
        appendToOutput( sc.array, sc.offset, i - sc.offset );
        sc.count -= i - sc.offset;
        sc.offset = i;
        completeAnsi( sc );
        i = sc.offset - 1;
      }
    }
    appendToOutput( sc );
    //outputView.getTextLineBuffer().append( sc );
  }
  private void appendToOutput( Segment s ){
    appendToOutput( s.array, s.offset, s.count );
  }
  private void appendToOutput( char[] c,
                               int offset,
                               int length ){
    TextLineBuffer output = outputView.getTextLineBuffer();
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
        outputView.getTextLineBuffer()
                  .setAnsi( Arrays.copyOf( outputPartialAnsi, i ) );
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

  /*
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
      outputView.getTextLineBuffer().setDefaultFont( f );
    } catch( NullPointerException e ){
      ((VFTLBView)getTopComponent()).getTextLineBuffer().setDefaultFont( f );
      getBottomComponent().setFont( f );
    }
  }
  */
}
