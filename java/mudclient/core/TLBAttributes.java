package mudclient.core;

import java.text.AttributedCharacterIterator.Attribute;
import java.util.*;


/* This class maintains AttributeSets for a TextLineBuffer.
 * It tracks changes to length, etc to keep the attributes
 * matched to the text they were originally assigned to.
 * Attributes are stored in a Red Black Tree, since a non-
 * balanced tree will degenerate to a linked list, as the
 * buffer adds to the high end and removes from the low.
 */

public class TLBAttributes implements BufferListener{

  // internally we keep track with a red-black tree
  // which is always kept contiguous from 0 to
  // long.MAX_VALUE.  Any gaps are filled with the
  // SimpleAttributeSet.EMPTY
  
  private static Map<Attribute, Object> EMPTY_MAP 
                          = new HashMap<Attribute, Object>();

  private long bufferStart;
  private long bufferEnd;
  // We only track one.
  // And we really only keep a handle on it so we can
  // hit it with a lock when we need to.
  private TextLineBuffer trackedBuffer;

  private AttributeSegment root;

  // a recycle bin for used AttributeSegments
  private ArrayDeque<AttributeSegment> recycleBin;

  // locking stuff
  private int readLocks;
  private int writeLocks;
  private Thread writingThread;
  private boolean notifying;

  public TLBAttributes( TextLineBuffer tlb ){
    tlb.writeLock();
    try{
      bufferStart = 0;
      bufferEnd = tlb.getCurrentLength();
      tlb.addBufferListener( this );
      trackedBuffer = tlb;
      root = new AttributeSegment();
      root.attributes = EMPTY_MAP;
      root.startIndex = 0;
      root.endIndex= Long.MAX_VALUE;
      root.black = true;
      recycleBin = new ArrayDeque<AttributeSegment>();
    } finally {
      tlb.writeUnlock();
    }
  }

  public Map<Attribute, Object> getAttributesForPosition( int index ){
    readLock();
    try{
      return getSegment( index + bufferStart ).attributes;
    } finally {
      readUnlock();
    }
  }
  // gets the highest index such that getAttributesForPosition(x)
  // is the same for all index <= x < returnValue
  // returns -1 if it runs off the end of the buffer.
  public int getEndOfAttributeRun( int index ){
    readLock();
    try{
      long internalIndex = getSegment( index + bufferStart ).endIndex;
      return ((internalIndex == Long.MAX_VALUE)? 
              -1 : 
              ((int)(internalIndex - bufferStart)));
    } finally {
      readUnlock();
    }
  }
  // gets the lowest index such that getAttributesForPosition(x)
  // is the same for all retrunValue <= x <= index
  public int getStartOfAttributeRun( int index ){
    readLock();
    try{
      long internalIndex = getSegment( index + bufferStart ).startIndex;
      return ((internalIndex <= bufferStart) ?
              0 :
              ((int)(internalIndex - bufferStart)));
    } finally {
      readUnlock();
    }
  }
  private AttributeSegment getNextSegment( AttributeSegment node ){
    if( node.endIndex == Long.MAX_VALUE ) return null;
    readLock();
    try{
      return getSegment( node.endIndex );
    } finally {
      readUnlock();
    }
  }
  private AttributeSegment getPrevSegment( AttributeSegment node ){
    readLock();
    try{
      if( node.startIndex < bufferStart ) return null;
      return getSegment( node.startIndex -1 );
    } finally {
      readUnlock();
    }
  }
  // sets the attributes.  this will clobber any attributes already
  // in place.  If startPos is passed as -1, it indicates to start
  // with the next data (don't change any attributes for the text
  // already in buffer.) If endPos is passed as -1, it indicates that
  // the attributes should extend to all new data.  In short, -1 
  // indicates any future data.
  public void setAttributes( Map<Attribute, Object> as, 
                             int startPos, 
                             int endPos ){
//System.out.println("TLBAttributes.setAttributes called.");
//System.out.println("startPos = " + startPos + ", endPos = " + endPos );
//System.out.println("bufferStart = " + bufferStart + ", bufferEnd = " + bufferEnd);

    writeLock();
    try{
      long start, end;
      if( startPos == -1 ) start = bufferEnd;
      else start = bufferStart + startPos;
      if( endPos == -1 ) end = Long.MAX_VALUE;
      else end = bufferStart + endPos;
      if( end <= start || end <= bufferStart ) return;
      // the segment we're interested in is actually the one just before
      // the start.
      AttributeSegment startSeg = getSegment( start-1 );
//System.out.println("startSeg = getSegment( " + (start-1) + " ) got " + startSeg);
      AttributeSegment endSeg = getSegment( end );
//System.out.println("endSeg = getSegment( " + end + " ) got " + endSeg);
      boolean matchStart = startSeg != null && startSeg.attributes.equals( as );
      // case: no change. start and end are encompased by a single Segment
      // with segment.attributes.equal( as )
      // noop
      if( startSeg == endSeg && matchStart ){
//System.out.println("startSeg == endSeg && matchStart. returning");
        return;
      }
      boolean matchEnd = (endSeg != null && endSeg.attributes.equals( as ) );
      // case: split a segment with unequal attributes (www -> wrw)
      // case: just clobber ( wwwxyzrrr -> wwsssssrr )
      // removeSpan , insertsegment
      if( startSeg == endSeg || (!matchStart && !matchEnd) ){
//System.out.println("removing " +start+" to "+end+", and putting in new segment");
        removeSpan( start, end );
        AttributeSegment newSeg = getBlankSegment();
        newSeg.startIndex = start;
        newSeg.endIndex = end;
        newSeg.attributes = as;
        insertSegment( newSeg );
        return;
      }
      // case: extend from front ( wwrrrrr -> wwwwrrr )
      // case: extend from front clobbering ( wxyzrrr -> wwwwwrr )
      // removeSpan, extend front
      if( matchStart && !matchEnd ){
//System.out.println("matchStart && !matchEnd, removing " +start+" to "+end+", and extending startSeg");
        // special case here. if start == startSeg.startIndex,
        // we would end up with a gap.
        removeSpan( start, end );
        startSeg.endIndex = end;
        return;
      }
      // case: smooth out ( wwwrrwww -> wwwwwwww )
      // switch start  to start.start
      if( matchEnd && matchStart )
{
        start = startSeg.startIndex;
//System.out.println("matchEnd && matchStart, setting start to " + start);
}
      // case: extend from end ( wwwwwwrrr -> wwwrrrrrr )
      // case: extend from end clobbering ( wwwxyzrrr -> wwrrrrrrr )
      // removeSpan, extend end
//System.out.println("removing " + start + " to " +end+", and extending endSeg");
      removeSpan( start, end );
      endSeg.startIndex = start;
    } finally {
      writeUnlock();
    }
  }

  public void processEvent( BufferEvent be ){
    writeLock();
    try{
      if( be.getRemoveLength() > 0 )
        removeSpan( bufferStart, bufferStart + be.getRemoveLength() );
      bufferStart += be.getRemoveLength();
      bufferEnd += be.getAppendLength();
    } finally {
      writeUnlock();
    }
  }

  private synchronized final void writeLock(){
    try{
      while ((readLocks > 0 ) || (writingThread != null )){
        if( Thread.currentThread() == writingThread){
          if( notifying ){
            throw new IllegalStateException("writeLock attempted in Notify");
          }
          writeLocks++;
          return;
        }
        wait();
      }
      writingThread = Thread.currentThread();
      writeLocks = 1;
    } catch (InterruptedException e){
      throw new Error("Interrupted exception: " + e);
    }
  }
  private synchronized final void writeUnlock(){
    if( --writeLocks <= 0 ){
      writeLocks = 0;
      writingThread = null;
      notifyAll();
    }
  }

  public synchronized final void readLock(){
    try{
      while ( writingThread != null ){
        if( Thread.currentThread() == writingThread ){
          // writer has full read access.... Most likely
          // a notified listener (in the thread that
          // did a write) is reading the new state
          // since we notify before releasing writelock
          return;
        }
        wait();
      }
      readLocks++;
    } catch (InterruptedException e){
      throw new Error("Interrupted exception: " + e);
    }
  }
  public synchronized final void readUnlock(){
    if( Thread.currentThread() == writingThread ){
      // writer has full read access.... Most likely
      // a notified listener (in the thread that 
      // did a write) is reading the new state
      // since we notify before releasing writelock
      return;
    }
    readLocks--;
    notify();
  }

  // we need to fill the span right after this, because it
  // leaves our data structure in a not-publicly correct state
  // Assumptions: bufferStart <= startIndex < endIndex <= bufferEnd
  private void removeSpan( long startIndex, long endIndex ){
    AttributeSegment startSeg = getSegment( startIndex );
    AttributeSegment endSeg = getSegment( endIndex );
    // it is possible for endSeg to be null, (if endIndex == bufferEnd)
    // but because of our assumptions, startSeg should not be.

    // two possibilities: the span is in the middle of a
    // segment, or it crosses or touches at least one boundary
    if( startSeg == endSeg ){
      // create a new endSeg
      endSeg = getBlankSegment();
      endSeg.attributes = startSeg.attributes;
      endSeg.endIndex = startSeg.endIndex;
      endSeg.startIndex = endIndex;
      startSeg.endIndex = endIndex;
      insertSegment( endSeg );
    }
    // find all segments between startSeg and endSeg, and remove them.
    HashSet<AttributeSegment> segSet = new HashSet<AttributeSegment>();
    AttributeSegment temp = getNextSegment( startSeg );
    while( temp != endSeg ){
      segSet.add( temp );
      temp = getNextSegment( temp );
    }
    Iterator<AttributeSegment> it = segSet.iterator();
    while( it.hasNext() )
      removeSegment( it.next() );
    startSeg.endIndex = startIndex;
    if( startSeg.startIndex == startSeg.endIndex ) removeSegment( startSeg );
    // endSeg might be null...
    if( endSeg != null ){
      endSeg.startIndex = endIndex;
      if( endSeg.startIndex == endSeg.endIndex ) removeSegment( endSeg );
    }
  }
  // this ASSUMES that there is a gap in the tree into which
  // the segment fits perfectly.
  // so make sure that's true before you call it...
  // (n for node.)
  private void insertSegment( AttributeSegment n ){
    n.black = false;
    // if the tree is empty, our job is pretty easy.
    if( root == null ){
      root = n;
      n.black = true;
      n.parent = n.left = n.right = null;
      return;
    }
    AttributeSegment localRoot = root;
    while( true ){
      if( localRoot.endIndex <= n.startIndex ){ // n after localRoot
        if( localRoot.right == null ){
          localRoot.right = n;
          n.parent = localRoot;
          break;
        } else {
          localRoot = localRoot.right;
          continue;
        }
      } else { // n before localRoot
        if( localRoot.left == null ){
          localRoot.left = n;
          n.parent = localRoot;
          break;
        } else {
          localRoot = localRoot.left;
          continue;
        }
      }
    }
    // red-black tree steps from wikipedia
    while( true ){
      // case1
      if( n.parent == null ){
        n.black = true;
        return;
      }
      // case2
      if( n.parent.black ) return;
      // get n's "uncle"
      AttributeSegment u = ((n.parent.parent.left == n.parent) ?
                              n.parent.parent.right :
                              n.parent.parent.left );
      // case3 (else in wiki)
      if( u == null || u.black ) break;
      // case3 (if in wiki)
      n.parent.black = true;
      u.black = true;
      n.parent.parent.black = false;
      n = n.parent.parent;
    }
    // case4
    if( n == n.parent.right && n.parent == n.parent.parent.left ){
      rotateLeft( n.parent );
      n = n.left;
    } else if ( n == n.parent.left && n.parent == n.parent.parent.right ){
      rotateRight( n.parent );
      n = n.right;
    }
    n.parent.black = true;
    n.parent.parent.black = false;
    // case5
    if( n == n.parent.left && n.parent == n.parent.parent.left )
      rotateRight( n.parent.parent );
    else
      rotateLeft( n.parent.parent );
  }
  // this one is more complicated, so I didn't mush the 
  // various wikipedia methods into one.
  private void removeSegment( AttributeSegment n ){

    // if this node has two children, we put the information
    // from it's least greater subnode in it and delete that
    // node instead.
    if( n.left != null && n.right != null ){
      // the replacement r
      AttributeSegment r;
      r = getNextSegment( n );
      n.attributes = r.attributes;
      n.startIndex = r.startIndex;
      n.endIndex = r.endIndex;
      // in this case we are actually removing something other
      // than the one that was passed, so we set node to 
      // the one we are actually removing so it will be
      // recycled.
      n = r;
    }

    // now from the wikipedia article on red-black trees
    // delete_one_child
    // the child and the parent
    AttributeSegment c, p;
    c = ((n.right == null) ? n.left : n.right );
    if( n.black ){
      n.black = c == null || c.black;
      dc1( n );
    }
    // replace_node
    p = n.parent;
    if( c != null ) c.parent = p;
    if( p == null ){
      root = c;
      if( root != null ) root.black = true; // in case it was red
    }else{
      if (p.left == n)
        p.left = c;
      else
        p.right = c;
    }
    // end replace_node
    recycle( n );
  }
  // delete case1 from wikipedia red-black tree article
  // it seems silly to have such a simple case, but it's
  // here to be a recursion point.
  private void dc1( AttributeSegment n ){
    if( n.parent != null )
      dc2( n );
  }
  // delete case2 from wikipedia red-black tree article
  private void dc2( AttributeSegment n ){
    // sibling and parent
    AttributeSegment s, p;
    p = n.parent;
    s = (p.left == n)?p.right:p.left;
    if( !s.black ){
      p.black = false;
      s.black = true;
      if( n == p.left ) rotateLeft( p );
      else rotateRight( p );
    }
    dc3( n );
  }
  // delete case3 from wikipedia red-black tree article
  private void dc3( AttributeSegment n ){
    // case3
    // sibling and parent
    AttributeSegment s, p;
    p = n.parent;
    s = (p.left == n)?p.right:p.left;
    if( p.black && 
        s.black && 
        (s.left == null || s.left.black) && 
        (s.right == null  || s.right.black) ){
      s.black = false;
      dc1( p );
    } else
      dc4( n );
  }
  // delete case4 from wikipedia red-black tree article
  private void dc4( AttributeSegment n ){
    // sibling and parent
    AttributeSegment s, p;
    p = n.parent;
    s = (p.left == n)?p.right:p.left;
    if( !p.black &&
        s.black && 
        (s.left == null || s.left.black) && 
        (s.right == null  || s.right.black) ){
      s.black = false;
      p.black = true;
    } else
      dc5( n );
  }
  // delete case5 from wikipedia red-black tree article
  private void dc5( AttributeSegment n ){
    // sibling and parent
    AttributeSegment s, p;
    p = n.parent;
    s = (p.left == n)?p.right:p.left;
    if( n == p.left && (s.right == null || s.right.black) ){
      s.black = false;
      if( s.left != null ) s.left.black = true;
      rotateRight( s );
    } else if ( n == p.right && (s.left == null || s.left.black)){
      s.black = false;
      if( s.right != null ) s.right.black = true;
      rotateLeft( s );
    }
    dc6( n );
  }
  // delete case6 from wikipedia red-black tree article
  private void dc6( AttributeSegment n ){
    // sibling and parent
    AttributeSegment s, p;
    p = n.parent;
    s = (p.left == n)?p.right:p.left;
    s.black = p.black;
    p.black = true;
    if( n == p.left ){
      if( s.right != null ) s.right.black = true;
      rotateLeft( p );
    } else {
      if( s.left != null ) s.left.black = true;
      rotateRight( p );
    }
  }

  private AttributeSegment getSegment( long internalIndex ){
    readLock();
    AttributeSegment node = root;
    try{
      while( node != null ){
        if( node.startIndex > internalIndex )
          node = node.left;
        else if( node.endIndex <= internalIndex )
          node = node.right;
        else
          return node;
      }
      return node;
    } finally {
      readUnlock();
    }
  }
  // assumes that node.left != null, else NullPointerEx.
  private void rotateRight( AttributeSegment node ){
    AttributeSegment node1;
    node1 = node.left;
    if( node1.right != null ) node1.right.parent = node;
    node.left = node1.right;
    node1.right = node;
    node1.parent = node.parent;
    node.parent = node1;
    if( node1.parent != null ){
      if( node1.parent.right == node )
        node1.parent.right = node1;
      else
        node1.parent.left = node1;
    } else {
      root = node1;
    }
  }
  // assumes that node.right != null, else NullPointerEx.
  private void rotateLeft( AttributeSegment node ){
    AttributeSegment node1;
    node1 = node.right;
    if( node1.left != null ) node1.left.parent = node;
    node.right = node1.left;
    node1.left = node;
    node1.parent = node.parent;
    node.parent = node1;
    if( node1.parent != null ){
      if( node1.parent.right == node )
        node1.parent.right = node1;
      else
        node1.parent.left = node1;
    } else {
      root = node1;
    }
  }
  private void recycle( AttributeSegment s ){
    s.parent = s.left = s.right = null;
    s.black = false;
    s.startIndex = s.endIndex = 0;
    s.attributes = EMPTY_MAP;
    recycleBin.add( s );
  }
  private AttributeSegment getBlankSegment(){
    AttributeSegment blank;
    blank = recycleBin.poll();
    return (blank == null)?new AttributeSegment():blank;
  }

  // a red/black tree node with Attribute information.
  private class AttributeSegment{
    private AttributeSegment parent;
    private AttributeSegment left;
    private AttributeSegment right;
    private boolean black; // black == true, red == false

    private long startIndex;
    private long endIndex;
    private Map<Attribute, Object> attributes;
public String toString(){
return "[AttributeSegment: startI: " + startIndex + ", endI: " + endIndex + ", atts: " + attributes + "]";
}
  }
}
