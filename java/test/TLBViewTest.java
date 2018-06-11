package test;

import mudclient.core.TextLineBuffer;
import mudclient.core.AnsiCode;
import mudclient.gui.TLBView;
import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;

public class TLBViewTest {
  public static final char[] ANSI_RESET = { AnsiCode.CSIA, AnsiCode.CSIB, '0', AnsiCode.CST };
  public static final char[] ANSI_HI_DEFAULT = { AnsiCode.CSIA, AnsiCode.CSIB, '1', AnsiCode.CST };
  public static final char[] ANSI_RED = { AnsiCode.CSIA, AnsiCode.CSIB, '3', '1', AnsiCode.CST };
  public static final char[] ANSI_INVERSE = { AnsiCode.CSIA, AnsiCode.CSIB, '7', AnsiCode.CST };
  public static final char[] ANSI_BLUE = { AnsiCode.CSIA, AnsiCode.CSIB, '3', '4', AnsiCode.CST };
  public static final char[] ANSI_CYAN = { AnsiCode.CSIA, AnsiCode.CSIB, '3', '6', AnsiCode.CST };
  public static final char[] ANSI_HI_CYAN = { AnsiCode.CSIA, AnsiCode.CSIB, '3', '6', ';', '1', AnsiCode.CST };

  public static void main( String[] ignored ){
    JFrame frame = new JFrame("TLBViewTest");
    frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    TextLineBuffer myTLB = new TextLineBuffer();

    myTLB.append(new Segment("line with nothing special.\n".toCharArray(), 0, 27));
    myTLB.setAnsi( ANSI_RESET );
    myTLB.append(new Segment("another line with nothing special.\n".toCharArray(), 0, 35));
    myTLB.append(new Segment("this one has a ".toCharArray(), 0, 15));
    myTLB.setAnsi(ANSI_HI_DEFAULT);
    myTLB.append(new Segment("bright ".toCharArray(), 0, 7));
    myTLB.setAnsi( ANSI_RESET );
    myTLB.append(new Segment("word.\n".toCharArray(), 0, 6));
    myTLB.append(new Segment("tough one:".toCharArray(), 0, 10));
    myTLB.setAnsi(ANSI_RED);
    myTLB.append(new Segment(" red".toCharArray(), 0, 4));
    myTLB.setAnsi(ANSI_INVERSE);
    myTLB.append(new Segment(" inverted red ".toCharArray(), 0, 14));
    myTLB.setAnsi( ANSI_RESET );
    myTLB.append(new Segment(" normal".toCharArray(), 0, 7));
    myTLB.setAnsi( ANSI_BLUE );
    myTLB.append(new Segment(" blue".toCharArray(), 0, 5));
    myTLB.setAnsi( ANSI_CYAN );
    myTLB.append(new Segment(" cyan".toCharArray(), 0, 5));
    myTLB.setAnsi( ANSI_HI_CYAN );
    myTLB.append(new Segment(" hi_cyan".toCharArray(), 0, 8));
    myTLB.setAnsi( ANSI_INVERSE );
    myTLB.append(new Segment(" inverted hi_cyan".toCharArray(), 0, 17));
    myTLB.setAnsi( ANSI_RESET );
    myTLB.append(new Segment(" normal\n".toCharArray(), 0, 8));
    myTLB.append(new Segment("tough one:".toCharArray(), 0, 10));
    myTLB.append(new Segment(" normal".toCharArray(), 0, 7));
    myTLB.setAnsi( ANSI_BLUE );
    myTLB.append(new Segment(" blue\n".toCharArray(), 0, 6));
    myTLB.append(new Segment("still blue".toCharArray(), 0, 10));
    System.out.println("The State of the Text Line Buffer:");
    System.out.println("" + myTLB );
    TLBView myTLBView = new TLBView( myTLB );
    frame.add( myTLBView );
    frame.pack();
    frame.setVisible( true );
  }
}
