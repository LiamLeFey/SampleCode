package mudclient.gui;

import mudclient.core.*;
import java.awt.*;
import java.awt.event.AdjustmentListener;
import java.awt.event.AdjustmentEvent;
import java.util.Map;
import javax.swing.*;
import javax.swing.text.Segment;

public class TLBView extends JPanel implements BufferListener, AdjustmentListener{

  // we make a string 80 chars wide with each letter in it 
  private String metricString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345678901234" +
                                "abcdefghijklmnopqrstuvwxyz12345678901234";
                                 
  // font size in points
  private int fontSize;
  private Font font;
  private FontMetrics fontMetrics;
  // the amount of space (in pixels? points?) between lines
  private int lineSpace;
  private int preferredLineCount;
  private TextLineBuffer myTlb;
  private JScrollBar verticalScrollBar;
  //private Color background;
  //private Color foreground;
  // if true, any lines too long will "wrap"
  // if false, any lines too long will only display the beginning
  private boolean lineWrap;

  // this is for the special case where the scrollbar is somewhere in
  // the middle (not at the beginning or the end), and the buffer
  // is moving.  We leave the visible lines visible.
  private boolean suppressRepaint;
  // this turns off suppressRepaint functionality.  The viewable
  // area always jumps to any new content from the buffer
  private boolean alwaysShowNew;

  public TLBView( int size ){
    myTlb = new TextLineBuffer( size );
    setDefaults();
  }
  public TLBView( TextLineBuffer buffer ){
    myTlb = buffer;
    setDefaults();
  }
  public TextLineBuffer getTextLineBuffer(){
    return myTlb;
  }
  public void setDefaults(){
    // sets the default font, size, and preferredLineCount
    myTlb.addBufferListener( this );
    fontSize = 10;
    font = new Font( Font.MONOSPACED, Font.PLAIN, fontSize );
    fontMetrics = getFontMetrics( font );
    lineSpace = 1;
    preferredLineCount = 23;
    int bufferLines = myTlb.getCurrentLineCount();
    // so we have a problem. the scrollbar goes from the min
    // to the max, not the min to the max+1 like everything
    // else in the known programing universe.  Which we could
    // deal with except that when the user drags the bar it
    // gets set all wrong.  There's just no good way using a
    // scrollbar to model a single line (start at 0(min), end
    // at 0(max), 1 line(extent), but 0+1>0, which is illegal
    // for a scrollbar)
    // So we keep our extent at 1 fewer lines than we can show.
    // This will mainly show up in setScrollBarForExtend and
    // anywhere we use the extent.
    verticalScrollBar = new JScrollBar( JScrollBar.VERTICAL, 
                                        0, 
                                        bufferLines-1, 
                                        0, 
                                        bufferLines-1 );
    verticalScrollBar.addAdjustmentListener( this );
    setLayout( new BorderLayout() );
    add( verticalScrollBar, BorderLayout.EAST );
  }
  public void setFontSize( int points ){ 
    fontSize = points; 
    font = font.deriveFont( points );
    fontMetrics = getFontMetrics( font );
    repaint();
  }
  public Font getFont(){
    return font;
  }
  public void setFont( Font f ){
    font = f;
    fontMetrics = getFontMetrics( font );
    repaint();
  }
  public void setFont( String fontName ){
    font = new Font( fontName, Font.PLAIN, fontSize );
    fontMetrics = getFontMetrics( font );
    repaint();
  }
  public void setPreferredLineCount( int c ){ preferredLineCount = c; }

  public Dimension getPreferredSize(){
    // width, height
    int height = (fontMetrics.getHeight() + lineSpace) * preferredLineCount;
    int width = fontMetrics.stringWidth( metricString ) +
                (int)verticalScrollBar.getMinimumSize().getWidth() + 3;
    return new Dimension( width, height );
  }
  public void paintComponent( Graphics g ){
    if( suppressRepaint ) return;
    super.paintComponent( g );
    //we now set this multiple times when drawing....
    //g.setFont( font );
    // the "zero" of the lines so the ith line gets put at i*lineHeight+drop
    int drop = lineSpace + fontMetrics.getAscent();
    int lineHeight = fontMetrics.getHeight() + lineSpace;
    Dimension dT = getSize( new Dimension() );
    Dimension dS = verticalScrollBar.getSize ( new Dimension() );
    int lineCount = ((int)dT.getHeight()) / lineHeight;

    // this updates the scrollbar values in the case where the window
    // size changed since last time extent was set.
    updateScrollBarForExtent( lineCount );

    int i, j, k; 
    int baseLine = verticalScrollBar.getValue();

    // the first line not visible
    Segment s = new Segment();
    if( lineWrap ){
      // perhaps we should do something more in case people switch to
      // a non monospaced font, but hey...  Maybe later.

      // the number of lines of text displayed (buffer lines, not graphics lines)
      int linesDisplayed = 0;
      // the number of characters we print before wrapping text
      int lineWidth = (int)(dT.getWidth() - dS.getWidth()) /
                      ((int)fontMetrics.getMaxAdvance());
      int extent = verticalScrollBar.getVisibleAmount();
      int max = verticalScrollBar.getMaximum();
      j = 0;
      // if we are at the end of the buffer, we need to go backwards
      // and figure out where to start.
      // otherwise, we just start from scrollbar.value and go until
      // we run out of room (we won't run out of content, since otherwise
      // we'd be at the end of the buffer...)
      if( baseLine + extent == max ){
        // backwards: we actually need to calculate what to show backwards,
        // and then actually draw it forwards, to prevent it from sticking
        // to the bottom if there isn't enough content to fill the screen

        // here i is the number of graphical lines used, and j is the reverse
        // index of the buffer line grabbed
        // if all the lines are short enough to fit, i and j are always the same
        for( i = 0, j = 0; i < lineCount && j <= max ; j++ ){
          s = myTlb.getLineAsSegment( s, max - j );
          i += (s.count / lineWidth) + 1;
        }
        // now j is switched to count graphical lines within a text line
        if( i != j ){
          baseLine = max - j;
          // int the previous loop, i will have overshot lineCount
          // by the amount we need to skip
          j = (i - lineCount) * lineWidth;
        } else
          j = 0;
      }
      // if we are starting by skipping the beginning of a line, 
      // we don't count it as a displayed line (since it isn't
      // completely displayed)
      if( j != 0 ) linesDisplayed = -1;
      // draw it forewards
      int count;
      for( i = 0, count = 0; i < extent && count <= lineCount; i++ ){
        s = myTlb.getLineAsSegment( s, baseLine + i );
        // we don't initialize j here, because we're using the value
        // calculated earlier to possibly skip the first part of the
        // first line in the event that we are more interested in showing
        // the last part of the last line, and there isn't room to show 
        // both
        for( ; j < s.count && count <= lineCount; j += lineWidth ){
          g.drawChars( s.array,
                       s.offset + j,
                       (j + lineWidth >= s.count) ? s.count - j : lineWidth,
                       2,
                       count*lineHeight + drop );
          count++;
        }
        // only if we finished displaying this line do we count it.
        if( j >= s.count ) linesDisplayed++;
        j = 0;
      }
      // it is possible at this point that linesDisplayed == 0, if
      // we are at the end of the scrollbuffer, and there are too many
      // characters in the last line to be displayed.
      if( linesDisplayed == 0 ) linesDisplayed++;
      updateScrollBarForExtent( linesDisplayed );
    } else {
      Shape oldClip = g.getClip();
      g.setClip( 0, 0, (int)(dT.getWidth() - dS.getWidth()), (int)dT.getHeight());
      for( i = verticalScrollBar.getVisibleAmount(); i >= 0; i-- ){
        s = myTlb.getLineAsSegment( s, baseLine + i );
        g.drawChars( s.array, s.offset, s.count, 2, i*lineHeight + drop );
      }
      g.setClip( oldClip );
    }
  }
  // this fixes the scrollbar to match the amount of text to be displayed
  // actually (see note by verticalScrollBar declaration) we keep the
  // extend = (actualScreenRealEstate) - 1.
  private void updateScrollBarForExtent( int lineCount ){
    int oldExtent = verticalScrollBar.getVisibleAmount();
    int newExtent = lineCount-1;
    if( newExtent == oldExtent ) return;
    int max = verticalScrollBar.getMaximum();
    if( newExtent > max ){
      verticalScrollBar.setValues( 0, max, 0, max );
      return;
    }
    // from here on, we know we can't display the entire buffer, so,
    // if oldValue == 0, oldValue + newExtent < max.
    int oldValue = verticalScrollBar.getValue();
    // if we were at the end, continue to follow the end.
    if( oldValue + oldExtent == max || oldValue > max - newExtent ){
      verticalScrollBar.setValues( max - newExtent, newExtent, 0, max );
    // if we were at the beginning, stay  there.
    } else if( oldValue == 0 ) {
      verticalScrollBar.setValues( 0, newExtent, 0, max );
    // otherwise, just reset to the window size
    } else {
      verticalScrollBar.setValues( oldValue, newExtent, 0, max );
    }
  }
  public void processEvent( BufferEvent be ){

    if( be.getEventType() == be.NONE ) return;
    // if the extent is at the beginning or the end,
    // we scroll the text.  Otherwise, we just change the
    // scrollbar to follow the text currently in view

    //int removedChars = be.getRemoveLength();
    int appendedChars = be.getAppendLength();
    int removedLines = be.getRemovedLines();
    int appendedLines = be.getAppendedLines();
    suppressRepaint = true;
    int oldValue = verticalScrollBar.getValue();
    int oldExtent = verticalScrollBar.getVisibleAmount();
    int oldMax = verticalScrollBar.getMaximum();
    int newMax = oldMax + appendedLines - removedLines;
    int newValue;
    if (removedLines <= oldValue){
      newValue = oldValue - removedLines;
    } else{
      newValue = 0;
      suppressRepaint = false;
    }
    // default to the extent stays fixed
    int newExtent = oldExtent;
    // fix the bounds cases
    // 1 if old values showed entire thing assume new does
    if( oldExtent == oldMax ){// only way is to start at 0 and show entire
      newExtent = newMax;
      suppressRepaint = false;
    }
    // 2 if newExtent got shoved past end, "Off with it's head!"
    if( newValue + newExtent > newMax ){
      newExtent = newMax - newValue;
      suppressRepaint = false;
    }
    // if we need to be at the end fix it.
    if( ((alwaysShowNew && appendedChars > 0) || 
              oldValue + oldExtent == oldMax ) &&
          newValue + newExtent < newMax ){
      newValue = newMax - newExtent;
      suppressRepaint = (appendedChars == 0 && removedLines <= oldValue);
    }
    verticalScrollBar.setValues( newValue, newExtent, 0, newMax);
    repaint();
  }
  public void adjustmentValueChanged( AdjustmentEvent e ){
    if( e.getAdjustable() == verticalScrollBar ){
      suppressRepaint = false;
      repaint();
    }
  }
private String vsbValues(){
return "value: " + verticalScrollBar.getValue() + ", extent: " + verticalScrollBar.getVisibleAmount() + ", min: " + verticalScrollBar.getMinimum() + ", max: " + verticalScrollBar.getMaximum();
}
}
