package tools;

import java.util.Comparator;

public class Heap{
  private Object[] heap;
  private Comparator c;
  //points to the next blank spot on the array.
  private int currentEnd;

  public Heap(){
    this( 100, null );
  }
  public Heap( int initialCapacity ){
    this( initialCapacity, null );
  }
  public Heap( Comparator c ){
    this( 100, c );
  }
  public Heap( int initialCapacity, Comparator c ){
    if( initialCapacity < 1 ) initialCapacity = 1;
    int size = 2;
    while( size < initialCapacity +1){
      size <<=1;
    }
    heap = new Object[ size ];
    currentEnd = 1;
    if( c == null )
      this.c = new Comparator(){
        public int compare( Object o1, Object o2 ){
          return ((Comparable)o1).compareTo(o2);
        }
      };
    else
      this.c = c;
  }
  public int size(){
    return currentEnd - 1;
  }
  public boolean hasNext(){
    return currentEnd > 1;
  }
  public void add( Object o ){
    int i = currentEnd;
    if( currentEnd >= heap.length )
      grow();
    while(i>1 && c.compare(o, heap[i>>1]) < 0 ){
      heap[i] = heap[i>>1];
      i >>=1;
    }
    heap[i] = o;
    currentEnd++;
  }
  public Object next(){
    if( currentEnd < 2 ) return null;
    Object n = heap[1];
    Object r = heap[--currentEnd];
    heap[currentEnd] = null;
    int i = 1;
    while( i*2 < currentEnd ){
      if( i*2+1 == currentEnd || c.compare( heap[i*2], heap[i*2+1] ) < 0 ){
        if( c.compare( r, heap[i*2]) > 0 ){
          heap[i] = heap[i*2];
          i *= 2;
        }else
          break;
      } else {
        if( c.compare( r, heap[i*2+1]) > 0 ){
          heap[i] = heap[i*2+1];
          i = i*2+1;
        }else
          break;
      }
    }
    heap[i] = r;
    return n;
  }
  private void grow(){
    Object[] newHeap = new Object[ heap.length << 1 ];
    int i;
    for( i = 0; i < currentEnd; i++ ){
      newHeap[i] = heap[i];
    }
    heap = newHeap;
  }
  public static void main(String[] args){
    Heap heap = new Heap(2);
    String input = "?";
    while( ! "quit".equalsIgnoreCase( input )){
      if("print".equalsIgnoreCase( input )){
        for( int i = 0; i < heap.heap.length; i++ ){
          System.out.println( Utils.niceInt(i, 4)+"->"+heap.heap[i]);
        }
      }else if("?".equalsIgnoreCase( input )){
        System.out.println("'#' to input, 'get' to get next, 'print' to print, 'quit' to quit.");
      }else if("get".equalsIgnoreCase( input )){
        if( ! heap.hasNext() )
          System.out.println( "Empty.");
        else
          System.out.println( heap.next() );
      }else{
        try{
          heap.add( new Integer( input ) );
        }catch( NumberFormatException e ){
          input = "?";
          continue;
        }
      }
      input = Utils.readFromIn();
    }
  }
}
