package mudclient.core;


public class BufferEvent {
  
  // indicates a no-op action.
  // Nothing appended or removed.
  // (not very useful, but included for completeness)
  public static int NONE = 0;

  // indicates a simple append action.  
  // (data added to end (ONLY) of buffer)
  public static int APPEND = 1;

  // indicates a simple remove action.
  // (data removed from begining (ONLY!) of buffer
  public static int REMOVE = 2;

  // indicates a compound action.
  // (data appended to end AND removed from begining)
  public static int COMPOUND = 3;

  private int appendLength;
  private int removeLength; 
  private int eventType;

  // we don't, strictly speaking, need these, since the information
  // is derivable from what we already provide.  But it makes it a lot
  // easier for any listeners who are not keeping track of some of our state.
  private int appendedLines;
  private int removedLines;

  // creates a new Event, automatically setting eventType
  // by checking for zero values.
  // negative values don't make a lot of sense for the
  // inteded use, but hey, knock yourself out.
  public BufferEvent( int remove, int add, int remLines, int addLines ){
    appendLength = add;
    removeLength = remove;
    if( remove != 0 ){
      if( add != 0 ) eventType = COMPOUND;
      else eventType = REMOVE;
    } else {
      if( add != 0 ) eventType = APPEND;
      else eventType = NONE;
    }
    appendedLines = addLines;
    removedLines = remLines;
  }

  public int getEventType(){ return eventType; }
  public int getAppendLength(){ return appendLength; }
  public int getRemoveLength(){ return removeLength; }
  public int getAppendedLines(){ return appendedLines; }
  public int getRemovedLines(){ return removedLines; }
}
