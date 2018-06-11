package mudclient.core;

import java.util.*;
import javax.swing.text.Segment;
import java.awt.Font;

/* A buffer of lines of text.
 */
public class TextLineBuffer{

  private TextLine[] lines;
  private int currentLineCount;
  private int firstLineIndex;
  // this is used in the case of clear.
  // when clear is called, we no longer have
  // a running index in the front of each line,
  // so we need somewhere to store it
  private long totalCharCount;

  // used for notification of changes...
  private boolean notifyInProgress;
  private HashSet<BufferListener> listeners;

  // used for synchronization and thread safety
  private int readLocks;
  private Thread writingThread;
  private int writeLocks;

  private static final char[] ANSI_RESET = {AnsiCode.CSIA,
                                              AnsiCode.CSIB,
                                              '0',
                                              AnsiCode.CST};
  // the AnsiCode representing reset.
  private AnsiCode blankAnsi = new AnsiCode();
  // the last ansi set.  Can be null (for "<esc>[0m")
  private AnsiCode currentAnsi;

  // a recyle bin for these guys.
  private AnsiRun ansiRunBin;

  public TextLineBuffer( ){
    this( 10 );
  }
  public TextLineBuffer( int size ){
    if( size < 0 ) size = 1;

    TextLine l = new TextLine();
    l.offset = l.count = 0;

    lines = new TextLine[ size ];
    firstLineIndex = 0;

    lines[ firstLineIndex ] = l;

    currentLineCount = 1;
    totalCharCount = 0;

    listeners = new HashSet<BufferListener>();
  }
  ////////////////////////////////////////////
  //      Writing methods
  ////////////////////////////////////////////

  /** sets the default font.  If any text has
   * a null font attached, this is the font
   * that gets used. returns previous default
  public Font setFont( Font f ){
    if( defaultFont != null && defaultFont.equals( f ) )
      return defaultFont;
    Font prev = defaultFont;
    defaultFont = f;
    return prev;
  }
  public Font getDefaultFont(){
    return defaultFont;
  }
   */
  /** sets the current font.  All subsequent
   * appends will have the font set.  may be
   * null. returns previous font
  public Font setFont( Font f ){
    Font prev = currentFont;
    if( currentFont != null && currentFont.equals( f ) )
      f = currentFont;
    currentFont = f;
    if( currentFont != null && currentFont.equals( defaultFont ) )
      currentFont = null;
    return prev;
  }
  public Font getFont(){
    return currentFont == null ? defaultFont : currentFont;
  }
   */
  public AnsiCode getDefaultAnsi(){
    return blankAnsi;
  }
  public void setAnsi( Segment ansi ){
    if( ansi == null || ansi.array == null )
      setAnsi( null, 0, 0 );
    else
      setAnsi( ansi.array, ansi.offset, ansi.count );
  }
  public void setAnsi( char[] ansi ){
    if( ansi == null )
      setAnsi( null, 0, 0 );
    else
      setAnsi( ansi, 0, ansi.length );
  }

  public void setAnsi( char[] ansi, int offset, int length ){
    if( ansi == null || 
        length < 3 ||
        Arrays.equals( ansi, ANSI_RESET ) ){
      currentAnsi = null;
      return;
    }

    int code, codeStart, codeEnd, codeCount, i;
    int end = offset + length;
    // first, count the codes so we can make the codes array
    codeCount = 0;
    int[] codes;
    for( i = offset; i < end; i++ ){
      char c = ansi[i];
      if( c == AnsiCode.CSD || c == AnsiCode.CST ) codeCount++;
    }
    codes = new int[ codeCount ];
    i = 0;
    for( codeStart = codeEnd = offset + 2; codeEnd < end; ){
      // empty loop to find end index of current code
      while( ansi[ ++codeEnd ] != AnsiCode.CSD && 
             ansi[ codeEnd ] != AnsiCode.CST );
      codes[ i++ ] = parseCode( ansi, codeStart, codeEnd );
      codeStart = codeEnd = codeEnd+1;
    }
    currentAnsi = (currentAnsi != null) ? currentAnsi.clone() : blankAnsi.clone();
    currentAnsi.transform( codes );
    // we check to see if we removed all interest
    if( currentAnsi.equals( blankAnsi ) )
      currentAnsi = null;
  }
  private int parseCode( char[] ansi, int codeStart, int codeEnd ){
    if( codeStart >= codeEnd ) return -1;
    int retVal = 0;
    for( int i = codeStart; i < codeEnd; i++ ){
      retVal *= 10;
      switch( ansi[ i ] ){
        case '1': retVal += 1; break;
        case '2': retVal += 2; break;
        case '3': retVal += 3; break;
        case '4': retVal += 4; break;
        case '5': retVal += 5; break;
        case '6': retVal += 6; break;
        case '7': retVal += 7; break;
        case '8': retVal += 8; break;
        case '9': retVal += 9; break;
      }
    }
    return retVal;
  }
  public AnsiCode getAnsi(){
    return (currentAnsi == null)?blankAnsi:currentAnsi;
  }
  // this appends a Segment, which is just a 
  // char[] with an offset and char count
  public TextLineBuffer append( Segment s ){
    return append( s.array, s.offset, s.count );
  }
  public TextLineBuffer append( char[] c ){
    if( c == null ) return this;
    return append( c, 0, c.length );
  }
  public TextLineBuffer append( char[] c, int offset, int len ){
    // enforce sanity
    if( c == null || offset > c.length-1 || len < 1 ) return this;
    if( offset < 0 ) offset = 0;
    if( offset+len > c.length ) len = c.length-offset;
    writeLock();
    try{
    // Listener notification and required accounting info
      long prevStartOffset = lines[ firstLineIndex ].offset;
      long prevTotalCharCount = totalCharCount;
      int prevFirstLine = firstLineIndex;
      int prevLineCount = currentLineCount;
      int localLinesAppended = 0;
      BufferEvent be;
  
      int endIndex = offset+len;
      int nlIndex = indexOf( c, '\n', offset, endIndex );
      // we want to make sure we don't append more than buffersize
      // lines before we notify our listeners.  Otherwise, some of
      // the new data could be lost before it gets logged
      while ( nlIndex != -1 ){
        appendLine( c, offset, nlIndex+1 );
        localLinesAppended++;
        if( localLinesAppended == lines.length-1 ){
          int removedLines = ((firstLineIndex + lines.length) - prevFirstLine)
                                    % lines.length;
          // but Master, why don't we just use localLinesAppended?
          // Because, grasshopper, it doesn't know if the previous last
          // line was newline terminated or not.
          int addedLines = currentLineCount - prevLineCount + removedLines;
          be = new BufferEvent( (int)(lines[firstLineIndex].offset 
                                      - prevStartOffset),
                                (int)(totalCharCount - prevTotalCharCount),
                                removedLines,
                                addedLines );
          notifyListeners( be );
          prevStartOffset = lines[firstLineIndex].offset;
          prevTotalCharCount = totalCharCount;
          prevFirstLine = firstLineIndex;
          prevLineCount = currentLineCount;
          localLinesAppended = 0;
        }
        offset = nlIndex+1;
        nlIndex = indexOf( c, '\n', offset, endIndex );
      }
      // if there's trailing data after last newline, append it.
      if( offset < endIndex ) {
        appendLine( c, offset, endIndex );
      }
      int removedLines = ((firstLineIndex + lines.length) - prevFirstLine)
                                % lines.length;
      // but Master, why don't we just use localLinesAppended?
      // Because, grasshopper, it doesn't correctly account for the
      // possibility of having previously had an empty last line.
      int addedLines = currentLineCount - prevLineCount + removedLines;
      be = new BufferEvent( (int)(lines[firstLineIndex].offset 
                                  - prevStartOffset),
                            (int)(totalCharCount - prevTotalCharCount),
                            removedLines,
                            addedLines );
      if( be.getEventType() != BufferEvent.NONE )
        notifyListeners( be );
    } finally {
      writeUnlock();
    }
    return this;
  }
  // appends with the assumption that c[ x ] != '\n'
  // for start <= x < end-1
  private void appendLine( char[] c, int start, int end ){
    prepForAppend( end - start );
    TextLine line = getLastLine();
    // we only ignore ansi if there are none recorded on this line
    // and the current is null (default)
    if( line.firstAnsiRun != null || currentAnsi != null ){
      if( line.firstAnsiRun == null ){ // we know currentAnsi != null from above
        if( line.count == 0 )
          line.firstAnsiRun = line.lastAnsiRun = 
            initializeAnsiRun( currentAnsi, 0 );
        else { // line.count != 0, no ansi runs
          line.firstAnsiRun = initializeAnsiRun( null, 0 );
          line.firstAnsiRun.next = line.lastAnsiRun =
            initializeAnsiRun( currentAnsi, line.count );
        }
      } else { // ansiRuns exist in line already
          if( ( currentAnsi != null && 
                ! currentAnsi.equals( line.lastAnsiRun.ansi )) ||
              ( currentAnsi == null && line.lastAnsiRun.ansi != null ) )
              line.lastAnsiRun = 
                line.lastAnsiRun.next = 
                  initializeAnsiRun( currentAnsi, line.count );
      }
    }
    System.arraycopy( c, start, line.chars, line.count, end - start );
    line.count += end - start;
    totalCharCount += end - start;
  }
  private TextLine getLastLine(){
    return ((currentLineCount < 1) ? null :
            lines[ (firstLineIndex + (currentLineCount-1))%lines.length ]);
  }
  // this preps the last line (only!) for appending
  // if the last line is full (\n terminated) set
  //   a (possibly new) empty line as the last line
  // regardless, ensure there's space in the last line.
  private void prepForAppend( int lengthOfAppend ){
    if( lengthOfAppend < 1 ) return;
    TextLine lastLine = getLastLine();
    if( lastLine == null || 
          (lastLine.count > 0 && 
            lastLine.chars[ lastLine.count-1 ] == '\n') ){
      lastLine = appendEmptyLine();
    }
    if( lastLine.count + lengthOfAppend > lastLine.chars.length ){
      int newLength;
      for( newLength = lastLine.chars.length * 2; 
            newLength < lastLine.count + lengthOfAppend; 
            newLength *= 2 );
      char[] newArray = new char[ newLength ];
      System.arraycopy( lastLine.chars, 0, newArray, 0, lastLine.count );
      lastLine.chars = newArray;
    }
  }
  // appends a new or recycled empty line, and returns it.
  private TextLine appendEmptyLine(){
    TextLine newLast, oldFirst;
    if( currentLineCount == lines.length ){
      oldFirst = lines[ firstLineIndex ];
      recycleAnsiRuns( oldFirst );
      firstLineIndex = ( firstLineIndex + 1 ) % lines.length;
      newLast = oldFirst;
    } else {
      // this grabs and re-uses the next line if it exists (after clear())
      newLast = lines[ (firstLineIndex + currentLineCount) % lines.length ];
      if( newLast == null ){
        newLast = new TextLine();
        lines[ (firstLineIndex + currentLineCount) % lines.length ] = newLast;
      }
      currentLineCount++;
    }
    newLast.offset = totalCharCount;
    newLast.count = 0;
    return newLast;
  }
  public TextLineBuffer clear(){
    writeLock();
    try{
      for( int i = 0; i < currentLineCount; i++ ){
        recycleAnsiRuns( lines[ ( firstLineIndex + i++ ) % lines.length ] );
      }
      BufferEvent be = new BufferEvent( (int)(totalCharCount 
                                        - lines[firstLineIndex].offset), 0,
                                          currentLineCount - 1, 0 );
      firstLineIndex = 0;
      // set it so there is one valid empty line which indicates the
      // totalCharCount to go forward with.
      lines[ firstLineIndex ].offset = totalCharCount;
      lines[ firstLineIndex ].count = 0;
      currentLineCount = 1;
      notifyListeners( be );
    } finally {
      writeUnlock();
    }
    return this;
  }

  ////////////////////////////////////////////
  //      Reading methods
  ////////////////////////////////////////////
  // theoretically it could get big enough to require a long
  // but we'll keep that out of the public stuff...
  public int getCurrentLength(){
    readLock();
    try{
      return (int)(totalCharCount - lines[ firstLineIndex ].offset);
    } finally {
      readUnlock();
    }
  }
  public int getCurrentLineCount(){
    return currentLineCount;
  }
  // gets a copy of the line requested or null if it doesn't exist
  public char[] getLine( int lineIndex ){
    readLock();
    try{
      if( lineIndex < 0 || lineIndex >= currentLineCount ) return null;
      int internalIndex = (firstLineIndex + lineIndex) % lines.length;
      char[] retValue = new char[ lines[ internalIndex ].count ];
      System.arraycopy( lines[ internalIndex ].chars, 0,
                        retValue, 0, retValue.length );
      return retValue;
    } finally {
      readUnlock();
    }
  }
  // returns the line in the Segment object, which is not guaranteed
  // to remain static when there are no active readlocks.
  public Segment getLineAsSegment( Segment rv, int lineIndex ){
    readLock();
    try{
      if( lineIndex < 0 || lineIndex >= currentLineCount ){
        rv.array = new char[0];
        rv.offset = rv.count = 0;
        return rv;
      }
      int internalIndex = (firstLineIndex + lineIndex) % lines.length;
      rv.array = lines[ internalIndex ].chars;
      rv.offset = 0;
      rv.count = lines[ internalIndex ].count;
      return rv;
    } finally {
      readUnlock();
    }
  }
  public char getCharAt( int index ){
    //cheater.
    char[] holder = getChars( index, index+1 );
    return ((holder == null || holder.length == 0)?((char)-1):(holder[ 0 ]));
  }
  public char[] getChars( int startIndex, int endIndex ){
    if( startIndex > endIndex ) return null;
    if( startIndex < 0 ) startIndex = 0;
    if( endIndex > totalCharCount - getInternalIndex( 0 ) )
      endIndex = (int)(totalCharCount - getInternalIndex( 0 ));
    readLock();
    try{
      char[] retValue = new char[ (int)(endIndex - startIndex) ];
      long internalCharIndex = getInternalIndex( startIndex );
      int lineIndex = getInternalLineIndex( internalCharIndex );
      int count = 0;
      int currentCharsToCopy;
      TextLine current = lines[ lineIndex ];
      while ( count < retValue.length ){
        currentCharsToCopy = (int)((current.offset+current.count) 
                                    - internalCharIndex);
        if( count + currentCharsToCopy > retValue.length )
          currentCharsToCopy = retValue.length - count;
        System.arraycopy( current.chars, 
                          (int)(internalCharIndex - current.offset),
                          retValue, 
                          count, 
                          currentCharsToCopy );
        count += currentCharsToCopy;
        internalCharIndex += currentCharsToCopy;
        lineIndex = (lineIndex+1)%lines.length;
        current = lines[ lineIndex ];
      }
      return retValue;
    } finally {
      readUnlock();
    }
  }
  
  public int getFirstCharIndex( int lineIndex ){
    readLock();
    try{
      if( lineIndex >= currentLineCount ) return -1;
      int interndex = ( firstLineIndex + lineIndex ) % lines.length;
      return (int)(lines[ interndex ].offset - lines[ firstLineIndex ].offset);
    } finally {
      readUnlock();
    }
  }
  public int getLineIndex( int charIndex ){
    readLock();
    try{
      int internalIndex = getInternalLineIndex( getInternalIndex( charIndex ) );
      if( internalIndex == -1 ) return -1;
      return ((internalIndex + lines.length) - firstLineIndex) % lines.length;
    } finally {
      readUnlock();
    }
    
  }
  // this assumes we already have readlock!
  // gets the internal index corresponding to the offset from
  // current first char
  private long getInternalIndex( int externalIndex ){
    return externalIndex + lines[ firstLineIndex ].offset;
  }
  // this assumes we already have readlock!
  // returns internal index of line holding the charInternalIndexth
  // character or -1 if it is currently not in the buffer.
  private int getInternalLineIndex( long charInternalIndex ){
    if( charInternalIndex < lines[ firstLineIndex ].offset
        || charInternalIndex >= totalCharCount )
      return -1;
    long target = charInternalIndex;
    int lowerB = 0;
    int upperB = currentLineCount;
    int mid = (upperB - lowerB) / 2;
    while( mid < upperB-1 ){
      if( lines[ (firstLineIndex + mid) % lines.length ].offset < target ){
        lowerB = mid;
      } else {
        upperB = mid;
      }
      mid = (upperB - lowerB) / 2;
    }
    if( lines[ (firstLineIndex + mid) % lines.length ].offset > target )
      mid--;
    return (firstLineIndex + mid) % lines.length;
  }

  ////////////////////////////////////////////
  //      Notification methods
  ////////////////////////////////////////////
  //private boolean notifyInProgress;
  //private HashSet listeners;

  public boolean addBufferListener( BufferListener bl ){
    writeLock();
    try{
      return listeners.add( bl );
    } finally {
      writeUnlock();
    }
  }
  public boolean removeBufferListener( BufferListener bl ){
    writeLock();
    try{
      return listeners.remove( bl );
    } finally {
      writeUnlock();
    }
  }
  private void notifyListeners( BufferEvent be ){
    // we should already have the writelock, since the only times
    // listeners are notified are if changes (writes) are made.
    notifyInProgress = true;
    try{
      Iterator<BufferListener> it = listeners.iterator();
      while( it.hasNext() )
        it.next().processEvent( be );
    } finally {
      notifyInProgress = false;
    }
  }



  ////////////////////////////////////////////
  //      Locking methods
  ////////////////////////////////////////////
  //private int readLocks;
  //private Thread writingThread;
  //private int writeLocks;
  //
  //hmm, there is a problem with deadlocking when a
  //thread with a readlock requests a writelock...
  //I can't think of any way to fix it other than
  //keeping track of the threads holding a readlock.

  protected synchronized final void writeLock() {
    try {
      while ((readLocks > 0) || (writingThread != null)) {
        if (Thread.currentThread() == writingThread) {
          if (notifyInProgress) {
            // Dont try to change things in a
            // notify.  It messes things up.
            throw new IllegalStateException(
            "TextLineBuffer changed in notify");
          }
          writeLocks++;
          return;
        }
        wait();
      }
      writingThread = Thread.currentThread();
      writeLocks = 1;
    } catch (InterruptedException e) {
      throw new Error("Interrupted attempt to aquire write lock");
    }
  }

  protected synchronized final void writeUnlock() {
    if (--writeLocks <= 0) {
      writeLocks = 0;
      writingThread = null;
      notifyAll();
    }
  }
  public synchronized final void readLock() {
    try {
      while (writingThread != null) {
        if (writingThread == Thread.currentThread()) {
          // writer has full read access.... Most likely
          // a notified listener (in the thread that 
          // did a write) is reading the new state
          // since we notify before releasing writelock
          return;
        }
        wait();
      }
      readLocks++;
    } catch (InterruptedException e) {
      throw new Error("Interrupted attempt to aquire read lock");
    }
  }
  public synchronized final void readUnlock() {
    if (writingThread == Thread.currentThread()) {
      // writer has full read access.... Most likely
      // a notified listener (in the thread that 
      // did a write) is reading the new state
      // since we notify before releasing writelock
      return;
    }
    readLocks--;
    notify();
  }

  ////////////////////////////////////////////
  //      Utility methods
  ////////////////////////////////////////////
  
  public static int indexOf( char[] array, char target, int start, int end ){
    for( ; start < end; start++ )
      if( array[ start ] == target ) return start;
    return -1;
  }

  public String toString(){
    String rv = "Accounting:\n rLs-" + readLocks + " wLs-" + writeLocks +
               " wThread-" + writingThread + " notfyInProg-" + notifyInProgress +
               "\n" + "Data:\n currentLineCount: " + currentLineCount +
               " firstLineIndex: " + firstLineIndex + " totalCharCount: " +
               totalCharCount + " lines.length: " + lines.length +
               "\nlines:\n";
    for ( int i = 0; i < lines.length; i++ )
      rv = rv.concat( ((lines[i] == null)?(""):(lines[ i ] + "\n")) );
    return rv;
  }
  // very like Segment, but we use a long for the offset
  // in case the buffer gets very very long (more than 2 Gig)
  private class TextLine{
    // the characters
    private char[] chars;
    // the offset from the very beginning of the buffer
    private long offset;
    // the number of useful characters in the line
    private int count;
    private AnsiRun firstAnsiRun;
    private AnsiRun lastAnsiRun;
    public TextLine(){ this( 81 ); }
    public TextLine( int width ){
      chars = new char[ width ];
    }
    public String toString(){
      String s = "chars.length = " + chars.length + " .content = (\"" +
              new String( chars ) + "\"), offset = " + offset + 
              ", count = " + count + "\nfirstAnsiRun -> ";
      AnsiRun ar;

      for( ar = firstAnsiRun; ar != null; ar = ar.next ){
        s += ar;
      }
      return s + "null";
    }
  }
  private void recycleAnsiRuns( TextLine l ){
    AnsiRun ar;
    l.lastAnsiRun = null;
    while( l.firstAnsiRun != null ){
      ar = l.firstAnsiRun;
      l.firstAnsiRun = l.firstAnsiRun.next;
      ar.next = ansiRunBin;
      ansiRunBin = ar;
    }
  }
  private AnsiRun initializeAnsiRun( AnsiCode ansi, int index ){
    AnsiRun r;
    if( ansiRunBin != null ){
      r = ansiRunBin;
      ansiRunBin = ansiRunBin.next;
    } else
      r = new AnsiRun();
    r.ansi = ansi;
    r.lineOffset = index;
    r.next = null;
    return r;
  }
  public AnsiRun getAnsiRun( int lineIndex ){
    if( lineIndex < 0 || lineIndex >= currentLineCount ) return null;
    return lines[ (firstLineIndex + lineIndex) % lines.length ].firstAnsiRun;
  }
  public class AnsiRun{
    
    private AnsiCode ansi;
    private int lineOffset;
    private AnsiRun next;
    public AnsiCode getAnsi(){ return ansi; }
    public int getLineOffset(){ return lineOffset; }
    public AnsiRun getNext(){ return next; }
    public String toString(){
      return "code: " + ansi + ", offset: " + lineOffset + " -> ";
    }
  }
}
