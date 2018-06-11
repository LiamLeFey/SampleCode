package mudclient.gui;

import mudclient.core.*;
import mudclient.core.TextLineBuffer.AnsiRun;
import java.awt.*;
import java.awt.event.AdjustmentListener;
import java.awt.event.AdjustmentEvent;
import java.util.Map;
import javax.swing.*;
import javax.swing.text.Segment;

public class TLBView extends JPanel 
                      implements BufferListener, AdjustmentListener{

  // we make a string 80 chars wide with each letter in it 
  private String metricString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345678901234" +
                                "abcdefghijklmnopqrstuvwxyz12345678901234";
                                 
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
  //private boolean suppressRepaint;
  // this turns off suppressRepaint functionality.  The viewable
  // area always jumps to any new content from the buffer
  private boolean alwaysShowNew = false;

  private int fgColor;
  private int bgColor;

  private TLBView(){}
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
    Font f = new Font( "DejaVu Sans Mono", Font.BOLD, 12 );
    setFont( f );
    lineSpace = 1;
    preferredLineCount = 23;
    fgColor = AnsiCode.FG_WHITE;
    bgColor = AnsiCode.BG_BLACK;
    setForeground( AnsiCode.getColor( fgColor, false ) );
    setBackground( AnsiCode.getColor( bgColor, false ) );
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
    setFont( getFont().deriveFont( (float)points ) );
    repaint();
  }
  public void setPreferredLineCount( int c ){ preferredLineCount = c; }

  public Dimension getPreferredSize(){
    // width, height
    FontMetrics fontMetrics = getFontMetrics( getFont() );
    int height = (fontMetrics.getHeight() + lineSpace) * preferredLineCount;
    int width = fontMetrics.stringWidth( metricString ) +
                (int)verticalScrollBar.getMinimumSize().getWidth() + 3;
    return new Dimension( width, height );
  }
  public void paintComponent( Graphics g ){
    //if( suppressRepaint ) return;
    setBackground( AnsiCode.getColor( bgColor, false ) );
    super.paintComponent( g );
    FontMetrics fontMetrics = getFontMetrics( getFont() );
    //we now set this multiple times when drawing....
    g.setFont( getFont() );

    int ascent = fontMetrics.getAscent();
    int lineHeight = fontMetrics.getHeight() + lineSpace;
    // the "zero" of the lines so the ith line gets put at i*lineHeight+drop
    int drop = lineSpace + ascent;

    Dimension dT = getSize( new Dimension() );
    Dimension dS = verticalScrollBar.getSize ( new Dimension() );
    int lineCount = ((int)dT.getHeight()) / lineHeight;

    // this updates the scrollbar values in the case where the window
    // size changed since last time extent was set.
    updateScrollBarForExtent( lineCount );

    int i, j, k; 
    int baseLine = verticalScrollBar.getValue();

    // these are used for drawing with differing attributes (colors)
    int lengthOfAnsiRun, drawnLength, pixelWidthOfRun;
    AnsiCode currentAnsi, defaultAnsi;
    defaultAnsi = myTlb.getDefaultAnsi();
    AnsiRun ar;

    Segment s = new Segment();
    if( lineWrap ){
      // perhaps we should do more in case people switch to
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
        ar = myTlb.getAnsiRun( baseLine + i );
        // we don't initialize j here, because we're using the value
        // calculated earlier to possibly skip the first part of the
        // first line in the event that we are more interested in showing
        // the last part of the last line, and there isn't room to show 
        // both
        for( ; j < s.count && count <= lineCount; j += lineWidth ){
          drawnLength = 0;
          for( k = 0; k < lineWidth; ){
            while( ar != null &&
                    ar.getNext() != null &&
                    ar.getNext().getLineOffset() <= j + k )
              ar = ar.getNext();
            if( ar != null ){
              currentAnsi = (ar.getAnsi() == null) ? defaultAnsi : ar.getAnsi();
              if( ar.getNext() != null &&
                  ar.getNext().getLineOffset() < j + lineWidth )
                lengthOfAnsiRun = ar.getNext().getLineOffset() - (j + k);
              else
                lengthOfAnsiRun = lineWidth - k;
            } else {
              currentAnsi = defaultAnsi;
              lengthOfAnsiRun = lineWidth - k;
            }
            setAnsiOnGraphics( currentAnsi, g );
            pixelWidthOfRun = g.getFontMetrics().charsWidth( s.array,
                                                            s.offset+j+k,
                                                            lengthOfAnsiRun);
            drawAnsiBGColor( currentAnsi, g, 
                                2 + drawnLength, count*lineHeight + lineSpace,
                                pixelWidthOfRun, lineHeight );
            g.drawChars( s.array,
                         s.offset + j + k,
                         lengthOfAnsiRun,
                         2 + drawnLength,
                         count*lineHeight + drop );
            drawnLength += pixelWidthOfRun;
            k += lengthOfAnsiRun;
          }
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
        ar = myTlb.getAnsiRun( baseLine + i );

        drawnLength = 0;
        for( j = 0; j < s.count; ){
          if( ar != null ){
            currentAnsi = (ar.getAnsi() == null) ? defaultAnsi : ar.getAnsi();
            if( ar.getNext() != null && ar.getNext().getLineOffset() < s.count )
              lengthOfAnsiRun = ar.getNext().getLineOffset() - j;
            else
              lengthOfAnsiRun = s.count - j;
          } else {
            currentAnsi = defaultAnsi;
            lengthOfAnsiRun = s.count - j;
          }
          setAnsiOnGraphics( currentAnsi, g );
          pixelWidthOfRun = g.getFontMetrics().charsWidth( s.array,
                                                          s.offset+j,
                                                          lengthOfAnsiRun);
          drawAnsiBGColor( currentAnsi, g, 
                              2 + drawnLength, i*lineHeight + lineSpace,
                              pixelWidthOfRun, lineHeight );
          g.drawChars( s.array,
                       s.offset + j,
                       lengthOfAnsiRun,
                       2 + drawnLength,
                       i*lineHeight + drop );
          drawnLength += pixelWidthOfRun;
          if( ar != null ) ar = ar.getNext();
          j += lengthOfAnsiRun;
        }
        //g.drawChars( s.array, s.offset, s.count, 2, i*lineHeight + drop );
      }
      g.setClip( oldClip );
    }
  }
  private void setAnsiOnGraphics( AnsiCode ac, Graphics g ){
    // currently, the only things we support are bright (not dim),
    // color, and inverse.
    // italic, underline, and strikethrough shouldn't be too hard
    // blink might be tough.
    int fg;
    if( ac.inverse )
      fg = (ac.background == AnsiCode.BG_DEFAULT) ? bgColor : ac.background;
    else
      fg = (ac.foreground == AnsiCode.FG_DEFAULT) ? fgColor : ac.foreground;
    g.setColor( AnsiCode.getColor( fg, !ac.inverse && ac.bright ) );
  }
  private void drawAnsiBGColor( AnsiCode ac, 
                                Graphics g, 
                                int x, int y,
                                int width, int height ){
    Color c, save;
    int bg;
    if( ac.inverse )
      bg = (ac.foreground == AnsiCode.FG_DEFAULT) ? fgColor : ac.foreground;
    else
      bg = (ac.background == AnsiCode.BG_DEFAULT) ? bgColor : ac.background;
    // if bg is default, we don't draw it
    if( ac.background == AnsiCode.BG_DEFAULT && ! ac.inverse ) return;
    save = g.getColor();
    g.setColor( AnsiCode.getColor( bg, ac.inverse && ac.bright ) );
    g.fillRect( x, y, width, height );
    g.setColor( save );
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
    //suppressRepaint = true;
    int oldValue = verticalScrollBar.getValue();
    int oldExtent = verticalScrollBar.getVisibleAmount();
    int oldMax = verticalScrollBar.getMaximum();
    int newMax = oldMax + appendedLines - removedLines;
    int newValue;
    if (removedLines <= oldValue){
      newValue = oldValue - removedLines;
    } else{
      newValue = 0;
      //suppressRepaint = false;
    }
    // default to the extent stays fixed
    int newExtent = oldExtent;
    // fix the bounds cases
    // 1 if old values showed entire thing assume new does
    if( oldExtent == oldMax ){// only way is to start at 0 and show entire
      newExtent = newMax;
      //suppressRepaint = false;
    }
    // 2 if newExtent got shoved past end, "Off with it's head!"
    if( newValue + newExtent > newMax ){
      newExtent = newMax - newValue;
      //suppressRepaint = false;
    }
    // if we need to be at the end fix it.
    if( ((alwaysShowNew && appendedChars > 0) || 
              oldValue + oldExtent == oldMax ) &&
          newValue + newExtent < newMax ){
      newValue = newMax - newExtent;
      //suppressRepaint = (appendedChars == 0 && removedLines <= oldValue);
    }
    verticalScrollBar.setValues( newValue, newExtent, 0, newMax);
    repaint();
  }
  public void adjustmentValueChanged( AdjustmentEvent e ){
    if( e.getAdjustable() == verticalScrollBar ){
      //suppressRepaint = false;
      repaint();
    }
  }
}
