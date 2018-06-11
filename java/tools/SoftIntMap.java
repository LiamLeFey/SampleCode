package tools;

import java.util.Iterator;
import java.lang.ref.*;

/** maintains a map of Objects, keyed by ints, and refered to
 * by SoftReferences.
 * It simply returns null when the SoftReference is cleared
 * by the GC, so there is no way to guarantee that what you
 * put in there will still be there when you come back, unless
 * you maintain your own hard reference to it.
 *  
 */
public final class SoftIntMap implements java.io.Serializable{
  private IntMap map;
  // Internally, we just wrap an IntMap and provide the same
  // public interface, just with different behaviour

  public SoftIntMap(){
    this( 100 );
  }
  public SoftIntMap( int size ){
    map = new IntMap( size );
  }

  /** note that hasNext() is not a guarantee that
   * next() will return a non-null value, and that the
   * gc can cause ConcurrentModificationExceptions to be thrown
   */
  public Iterator valueIterator(){
    return new SoftIntMapValueIterator();
  }
  /** note that hasNext() is not a guarantee that
   * next() will return a key which will map to a non-null value
   */
  public IntIterator keyIterator(){
    return map.keyIterator();
  }
  // completes in O(n) time, some values may have become null.
  public Object[] values(){
    Object[] r = map.values();
    int i;
    for( i = 0; i < r.length; i++ ){
      r[ i ] = ((Reference)r[ i ]).get();
    }
    return r;
  }
  // completes in O(n) time
  public int[] keys(){
    return map.keys();
  }
  public int size( ){
    return map.size();
  }
  public boolean isEmpty(){
    return map.isEmpty();
  }
  public boolean contains( int val ){
    return map.contains( val );
  }
  public Object get( int key ){
    Reference r = (Reference)map.get( key );
    return r == null ? null : r.get();
  }
  public Object remove( int key ){
    Reference r = (Reference)map.remove( key );
    return r == null ? null : r.get();
  }
  public Object put( int key, Object val ){
    Reference r = (Reference)map.put(key, new SoftIntMapReference(val, key));
    return r == null ? null : r.get();
  }
  private class SoftIntMapReference extends SoftReference {
    private int key;
    private SoftIntMapReference( Object o, int i ){
      super( o );
      key = i;
    }
    public void clear(){
      super.clear();
      remove( key );
    }
  }

  private class SoftIntMapValueIterator implements Iterator{
    Iterator it;
    private SoftIntMapValueIterator(){
      it = map.valueIterator();
    }
    public boolean hasNext(){
      return it.hasNext();
    }
    public Object next(){
      return ((SoftReference)it.next()).get();
    }
    public void remove(){
      it.remove();
    }
  }
}
