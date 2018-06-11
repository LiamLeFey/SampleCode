package mudclient.core;

import java.awt.Color;

public final class AnsiCode{

  public static final int RESET = 0;

  public static final int BRIGHT = 1;
  public static final int DIM = 2;
  public static final int ITALIC = 3;
  public static final int UNDERLINE = 4;
  public static final int BLINK = 5;
  public static final int FAST_BLINK = 6;
  public static final int INVERSE = 7;
  public static final int INVISIBLE = 8;
  public static final int STRIKETHROUGH = 9;

  public static final int NORMAL_INTENSITY = 22;
  public static final int ITALIC_OFF = 23;
  public static final int UNDERLINE_OFF = 24;
  public static final int BLINK_OFF = 25;
  public static final int INVERSE_OFF = 27;
  public static final int INVISIBLE_OFF = 28;
  public static final int STRIKETHROUGH_OFF = 29;

  public static final int FG_BLACK = 30;
  public static final int FG_RED = 31;
  public static final int FG_GREEN = 32;
  public static final int FG_YELLOW = 33;
  public static final int FG_BLUE = 34;
  public static final int FG_MAGENTA = 35;
  public static final int FG_CYAN = 36;
  public static final int FG_WHITE = 37;
  public static final int FG_DEFAULT = 39;

  public static final int BG_BLACK = 40;
  public static final int BG_RED = 41;
  public static final int BG_GREEN = 42;
  public static final int BG_YELLOW = 43;
  public static final int BG_BLUE = 44;
  public static final int BG_MAGENTA = 45;
  public static final int BG_CYAN = 46;
  public static final int BG_WHITE = 47;
  public static final int BG_DEFAULT = 49;

  // pulled from wikipedias ansi escape code page.
  // I like the Putty normal colors, and the xterm brights
  public static final Color BLACK = new Color( 0, 0, 0 );
  public static final Color RED = new Color( 187, 0, 0 );
  public static final Color GREEN = new Color( 0, 187, 0 );
  public static final Color YELLOW = new Color( 187, 107, 0 );
  public static final Color BLUE = new Color( 0, 0, 187 );
  public static final Color MAGENTA = new Color( 187, 0, 187 );
  public static final Color CYAN = new Color( 0, 187, 187 );
  public static final Color WHITE = new Color( 187, 187, 187 );
  public static final Color HI_BLACK = new Color( 85, 85, 85 );
  public static final Color HI_RED = new Color( 255, 0, 0 );
  public static final Color HI_GREEN = new Color( 0, 255, 0 );
  public static final Color HI_YELLOW = new Color( 255, 255, 0 );
  public static final Color HI_BLUE = new Color( 20, 20, 255 );
  public static final Color HI_MAGENTA = new Color( 255, 0, 255 );
  public static final Color HI_CYAN = new Color( 0, 255, 255 );
  public static final Color HI_WHITE = new Color( 255, 255, 255 );

  // control sequence initiator (2 bytes, ESC - [ )
  public static final byte CSIA = 0x1B;
  public static final byte CSIB = 0x5B;
  // control sequence delimiter ( ; )
  public static final byte CSD = 0x3B;
  // control sequence terminator (m)
  public static final byte CST = 0x6D;

  public static Color getColor( int colorCode, boolean bright ){
    colorCode = colorCode % 10;
    if( bright ) colorCode += 10;
    switch( colorCode ){
      case 0: return BLACK;
      case 1: return RED;
      case 2: return GREEN;
      case 3: return YELLOW;
      case 4: return BLUE;
      case 5: return MAGENTA;
      case 6: return CYAN;
      case 7: return WHITE;
      case 10: return HI_BLACK;
      case 11: return HI_RED;
      case 12: return HI_GREEN;
      case 13: return HI_YELLOW;
      case 14: return HI_BLUE;
      case 15: return HI_MAGENTA;
      case 16: return HI_CYAN;
      case 17: return HI_WHITE;
      default: return null;
    }
  }

  public boolean bright = false;
  public boolean dim = false;
  public boolean italic = false;
  public boolean underline = false;
  public boolean blink = false;
  public boolean fastBlink = false;
  public boolean inverse = false;
  public boolean invisible = false;
  public boolean strikethrough = false;
  public int background = BG_DEFAULT;
  public int foreground = FG_DEFAULT;


  // defaults are defined above, so nothing to do really.
  public AnsiCode(){ }

  public void transform( int[] transforms ){
    for( int i = 0; i < transforms.length; i++ ){
      switch( transforms[ i ] ){
        case RESET:
          bright = dim = italic = underline = blink = fastBlink = inverse =
              invisible = strikethrough = false;
          foreground = FG_DEFAULT;
          background = BG_DEFAULT;
          break;
        case BRIGHT: bright = true; break;
        case DIM: dim = true; break;
        case ITALIC: italic = true; break;
        case UNDERLINE: underline = true; break;
        case BLINK: blink = true; fastBlink = false; break;
        case FAST_BLINK: fastBlink = true; blink = false; break;
        case INVERSE: inverse = true; break;
        case INVISIBLE: invisible = true; break;
        case STRIKETHROUGH: strikethrough = true; break;
        case NORMAL_INTENSITY: bright = dim = false; break;
        case ITALIC_OFF: italic = false; break;
        case UNDERLINE_OFF: underline = false; break;
        case BLINK_OFF: blink = fastBlink = false; break;
        case INVERSE_OFF: inverse = false; break;
        case INVISIBLE_OFF: invisible = false; break;
        case STRIKETHROUGH_OFF: strikethrough = false; break;
        case FG_BLACK:
        case FG_RED:
        case FG_GREEN:
        case FG_YELLOW:
        case FG_BLUE:
        case FG_MAGENTA:
        case FG_CYAN:
        case FG_WHITE:
        case FG_DEFAULT:
          foreground = transforms[ i ];
          break;
        case BG_BLACK:
        case BG_RED:
        case BG_GREEN:
        case BG_YELLOW:
        case BG_BLUE:
        case BG_MAGENTA:
        case BG_CYAN:
        case BG_WHITE:
        case BG_DEFAULT:
          background = transforms[ i ];
      }
    }
  }
  public AnsiCode clone(){
    AnsiCode clone = new AnsiCode();
    clone.bright = bright;
    clone.dim = dim;
    clone.italic = italic;
    clone.underline = underline;
    clone.blink = blink;
    clone.fastBlink = fastBlink;
    clone.inverse = inverse;
    clone.invisible = invisible;
    clone.strikethrough = strikethrough;
    clone.background = background;
    clone.foreground = foreground;
    return clone;
  }
  public int hashCode(){
    int code = 0;
    if( bright ) code |= 0x01;
    if( dim ) code |= 0x02;
    if( italic ) code |= 0x04;
    if( underline ) code |= 0x08;
    if( blink ) code |= 0x10;
    if( fastBlink ) code |= 0x20;
    if( inverse ) code |= 0x40;
    if( invisible ) code |= 0x80;
    if( strikethrough ) code |= 0x100;
    code += background << 9;
    code += foreground << 13;
    return code;
  }
  public boolean equals( AnsiCode other ){
    return (this == other) || 
    other != null &&
    other.bright == bright &&
    other.dim == dim &&
    other.italic == italic &&
    other.underline == underline &&
    other.blink == blink &&
    other.fastBlink == fastBlink &&
    other.inverse == inverse &&
    other.invisible == invisible &&
    other.strikethrough == strikethrough &&
    other.background == background && 
    other.foreground == foreground;
  }
  public String toString(){
    return "flags:{" +
        (bright?"bright,":"") + 
        (dim?"dim,":"") + 
        (italic?"italic,":"") + 
        (underline?"underline,":"") + 
        (blink?"blink,":"") + 
        (fastBlink?"fastBlink,":"") + 
        (inverse?"inverse,":"") + 
        (invisible?"invisible,":"") + 
        (strikethrough?"strikethrough":"") + 
        "} fg: " + foreground + ", bg: " + background;
  }
}
