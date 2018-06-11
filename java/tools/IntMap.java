package tools;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Iterator;

/** maintains a map of Objects, keyed by ints.
 *  it is backed by an array, which is set up as a red-black btree
 *  all opperations are completed in O(log n) time on average, and
 *  other than the array, there are no internal objects, so it is
 *  very fast and light.  Occasionally, the array must be resized,
 *  which takes O(n) time, but this occurs with O(log growth)
 *  frequency, so if you know ahead of time how large your array
 *  will need to be, this can be avoided. 
 */
public final class IntMap implements java.io.Serializable{
  private Object[] referents;
  private int[] array;
  private static final int NULL_POINTER = 0x7FFFFFFF;
  private int size; // the number of elements
  private int modifications; // used to make the iterator failfast
  private int root; // the index of the root node

  // each node consists of value, parent, leftChild, and rightChild
  // parent is overloaded with the first bit indicating a red node

  public IntMap(){
    this( 100 );
  }
  public IntMap( int size ){
    if( size <= 0 )
      size = 1;
    array = new int[ size<<2 ];
    referents = new Object[ size ];
    this.size = 0;
    modifications = 0;
    root = NULL_POINTER;
  }

  public Iterator valueIterator(){
    return new IntMapValueIterator();
  }
  public IntIterator keyIterator(){
    return new IntSetIterator();
  }
  // completes in O(n logn) time
  public Object[] values(){
    Object[] r = new Object[ size ];
    Iterator it = valueIterator();
    int i = 0;
    while( it.hasNext() )
      r[ i++ ] = it.next();
    return r;
  }
  // completes in O(n logn) time
  public int[] keys(){
    int[] r = new int[ size ];
    IntIterator it = keyIterator();
    int i = 0;
    while( it.hasNext() )
      r[ i++ ] = it.next();
    return r;
  }
  public int size( ){
    return size;
  }
  public boolean isEmpty(){
    return size == 0;
  }
  public boolean contains( int val ){
    return getIndex( val ) != NULL_POINTER;
  }
  // returns val if it is in the set.
  // if it is not in, returns the next highest value,
  // if it is larger than any in the set, returns MIN_VALUE,
  // which is an impossible return value unless you send
  // it as val, in which case, you should use contains to 
  // double check.
  public int getCeilKey( int val ){
    int i = getCeilKeyIndex( val, root );
    if( i == NULL_POINTER ) return Integer.MIN_VALUE;
    return value( i );
  }
  private int getCeilKeyIndex( int val, int i ){
    if( i == NULL_POINTER || value( i ) == val ) return i;
    if( value( i ) < val )
      return getCeilKeyIndex( val, rightChild( i ) );
    if( leftChild( i ) == NULL_POINTER )
      return i;
    int j = getCeilKeyIndex( val, leftChild( i ) );
    if( j == NULL_POINTER )
      return i;
    return j;
  }
  // returns val if it is in the set.
  // if it is not in, returns the next lowest value,
  // if it is smaller than any in the set, returns MAX_VALUE,
  // which is an impossible return value unless you send
  // it as val, in which case, you should use contains to 
  // double check.
  public int getFloorKey( int val ){
    int i = getFloorKeyIndex( val, root );
    if( i == NULL_POINTER ) return Integer.MAX_VALUE;
    return value( i );
  }
  private int getFloorKeyIndex( int val, int i ){
    if( i == NULL_POINTER || value( i ) == val ) return i;
    if( value( i ) > val )
      return getFloorKeyIndex( val, leftChild( i ) );
    if( rightChild( i ) == NULL_POINTER )
      return i;
    int j = getFloorKeyIndex( val, rightChild( i ) );
    if( j == NULL_POINTER )
      return i;
    return j;
  }
  public Object get( int key ){
    int i = getIndex( key );
    return i == NULL_POINTER ? null : referent( i );
  }
  private int getIndex( int val ){
    return getIndex( val, root );
  }
  private int getIndex( int val, int i ){
    if( i == NULL_POINTER || value( i ) == val )
      return i;
    return ( value( i ) > val )?
      getIndex( val, leftChild(i)):
        getIndex( val, rightChild(i));
  }
  public Object remove( int val ){
    int i = getIndex( val );
    if( i == NULL_POINTER )
      return null;
    Object o = referent( i );
    deleteElement( i );
    return o;
  }
  public Object put( int val, Object referent ){
    Object o = null;
    int i;
    // insert
    i = root;
    if( i == NULL_POINTER ){
      root = insert( val, NULL_POINTER, referent );
      modifications++;
      return o;
    }
    while( true ){
      if( val == value( i ) ){
        o = referent( i );
        setReferent( i, referent );
        return o;
      }
      if( val < value( i ) )
        if(  leftChild( i ) == NULL_POINTER ){
          insertLeft( val, i, referent );
          modifications++;
          return o;
        } else
          i = leftChild( i );
      else
        if( rightChild( i ) == NULL_POINTER ){
          insertRight( val, i, referent );
          modifications++;
          return o;
        } else
          i = rightChild( i );
    }
  }
  // makes sure there is room for one more elephant
  private void ensureRoom(){
    // make it bigger if we need to.
    if( (size << 2) >= array.length ){
      int[] newArray = new int[ array.length << 1 ];
      int i = array.length;
      while( i-- > 0 )
        newArray[ i ] = array[ i ];
      array = newArray;
      Object[] newReferents = new Object[ referents.length << 1 ];
      i = referents.length;
      while( i-- > 0 )
        newReferents[ i ] = referents[ i ];
      referents = newReferents;
    }
  }
  private void insertRight( int val, int p, Object referent ){
    setRightChild( p, insert( val, p, referent ) ) ;
    balanceAfterInsert( rightChild( p ) );
  }
  private void insertLeft( int val, int p, Object referent ){
    setLeftChild( p, insert( val, p, referent ) );
    balanceAfterInsert( leftChild( p ) );
  }
  private int insert( int val, int p, Object referent ){
    ensureRoom();
    setValue( size, val );
    setReferent( size, referent );
    setParent( size, p );
    setLeftChild( size, NULL_POINTER );
    setRightChild( size, NULL_POINTER );
    return size++;
  }
  private void deleteElement( int p ){
    modifications++;
    size--;

    if( leftChild( p ) != NULL_POINTER && rightChild( p ) != NULL_POINTER ){
      int s = successor( p );
      setValue( p, value( s ) );
      setReferent( p, referent( s ) );
      p = s;
    }

    int replacement = (leftChild(p)!=NULL_POINTER?leftChild(p):rightChild(p));
    if( replacement != NULL_POINTER ){
      // link replacement to parent
      setParent( replacement, parent( p ) );
      if( parent( p ) == NULL_POINTER )
        root = replacement;
      else if( leftChild( parent( p ) ) == p )
        setLeftChild( parent( p ), replacement );
      else
        setRightChild( parent( p ), replacement );

      // null out links
      setRightChild( p, NULL_POINTER );
      setLeftChild( p, NULL_POINTER );
      setParent( p, NULL_POINTER );

      if( isBlack( p ) )
        fixAfterDeletion( replacement );
    } else if( parent( p ) == NULL_POINTER ){
      root = NULL_POINTER;
    } else {
      if( isBlack( p ) )
        fixAfterDeletion( p );
      if( parent( p ) != NULL_POINTER ){
        if( leftChild( parent( p ) ) == p )
          setLeftChild( parent( p ), NULL_POINTER );
        else if( rightChild( parent( p ) ) == p )
          setRightChild( parent( p ), NULL_POINTER );
        setParent( p, NULL_POINTER );
      }
    }
    // now we reclaim the last elemnt of the array by putting
    // it in the place of i, and letting i fall off the end
    if( p != size ){
      setValue( p, value( size ) );
      setReferent( p, referent( size ) );
      if( parent( size ) == NULL_POINTER )
        root = p;
      else{
        if( rightChild( parent( size ) ) == size )
          setRightChild( parent( size ), p );
        else
          setLeftChild( parent( size ), p );
      }
      setParent( p, parent( size ) );
      if( isBlack( size ) )
        setBlack( p );
      else
        setRed( p );
      setRightChild( p, rightChild( size ) );
      setLeftChild( p, leftChild( size ) );
      if( rightChild( p ) != NULL_POINTER ) setParent( rightChild( p ), p );
      if( leftChild( p ) != NULL_POINTER ) setParent( leftChild( p ), p );
    }
    // avoid the map as memory leak problem
    setReferent( size, null );
  }
  private void fixAfterDeletion( int x ){
    int y;
    while( x != root && isBlack( x ) ){
      if( x == leftChild( parent( x ) ) ){
        y = rightChild( parent( x ) );
        if( isRed( y ) ){
          setBlack( y );
          setRed( parent( x ) );
          rotateLeft( parent( x ) );
          y = rightChild( parent( x ) );
        }

        if( isBlack( leftChild( y ) ) && isBlack( rightChild( y ) ) ){
          setRed( y );
          x = parent( x );
        }else{
          if( isBlack( rightChild( y ) ) ){
            setBlack( leftChild( y ) );
            setRed( y );
            rotateRight( y );
            y = rightChild( parent( x ) );
          }
          if( isBlack( parent( x ) ) ) setBlack( y );
          else setRed( y );
          setBlack( parent( x ) );
          setBlack( rightChild( y ) );
          rotateLeft( parent( x ) );
          x = root;
        }
      } else {
        y = leftChild( parent( x ) );
        if( isRed( y ) ){
          setBlack( y );
          setRed( parent( x ) );
          rotateRight( parent( x ) );
          y = leftChild( parent( x ) );
        }
        if( isBlack( rightChild( y ) ) && isBlack( leftChild( y ) ) ){
          setRed( y );
          x = parent( x );
        }else{
          if( isBlack( leftChild( y ) ) ){
            setBlack( rightChild( y ) );
            setRed( y );
            rotateLeft( y );
            y = leftChild( parent( x ) );
          }
          if( isBlack( parent( x ) ) ) setBlack( y );
          else setRed( y );
          setBlack( parent( x ) );
          setBlack( leftChild( y ) );
          rotateRight( parent( x ) );
          x = root;
        }
      }
    }
    setBlack( x );
  }
  private Object referent( int i ){
    return referents[ i ];
  }
  private void setReferent( int i, Object r ){
    referents[ i ] = r;
  }
  private int value( int i ){
    return array[ i<<2 ];
  }
  private void setValue( int i, int val ){
    array[ i<<2 ] = val;
  }
  private int leftChild( int i ){
    return i == NULL_POINTER ? NULL_POINTER : array[ (i<<2) + 2 ];
  }
  private void setLeftChild( int i, int c ){
    array[ (i<<2) + 2 ] = c;
  }
  private int rightChild( int i ){
    return  i == NULL_POINTER ? NULL_POINTER : array[ (i<<2) + 3 ];
  }
  private void setRightChild( int i, int c ){
    array[ (i<<2) + 3 ] = c;
  }
  private int parent( int i ){
    return i == NULL_POINTER ? NULL_POINTER : array[ (i<<2) + 1] & 0x7FFFFFFF;
  }
  private void setParent( int i, int p ){
    int rbHold = array[ (i<<2) + 1] & 0x80000000;
    array[ (i<<2) + 1 ] = p | rbHold;
  }

  private boolean isRed( int i ){
    return (i==NULL_POINTER)?false:(array[ (i<<2) + 1 ] & 0x80000000) != 0;
  }
  private boolean isBlack( int i ){
    return (i==NULL_POINTER)?true:(array[ (i<<2) + 1 ] & 0x80000000) == 0;
  }
  private void setRed( int i ){
    if( i != NULL_POINTER ) array[ (i<<2) + 1 ] |= 0x80000000;
  }
  private void setBlack( int i ){
    if( i != NULL_POINTER ) array[ (i<<2) + 1 ] &= 0x7FFFFFFF;
  }
  private void rotateRight( int i ){
    int l = leftChild( i );
    setLeftChild( i, rightChild( l ) );
    if( rightChild( l ) != NULL_POINTER ) 
      setParent( rightChild( l ), i );
    setParent( l, parent( i ) );
    if( parent( i ) == NULL_POINTER )
      root = l;
    else if( rightChild( parent( i ) ) == i )
      setRightChild( parent( i ), l );
    else
      setLeftChild( parent( i ), l );
    setRightChild( l, i );
    setParent( i, l );
  }
  private void rotateLeft( int i ){
    int r = rightChild( i );
    setRightChild( i, leftChild( r ) );
    if( leftChild( r ) != NULL_POINTER ) 
      setParent( leftChild( r ), i );
    setParent( r, parent( i ) );
    if( parent( i ) == NULL_POINTER )
      root = r;
    else if( leftChild( parent( i ) ) == i )
      setLeftChild( parent( i ), r );
    else
      setRightChild( parent( i ), r );
    setLeftChild( r, i );
    setParent( i, r );
  }
  private void balanceAfterInsert( int x ){
    setRed( x );

    int y, p, g;
    while( x != NULL_POINTER && x != root && isRed( parent(x) ) ){
      g = parent(p = parent(x));
      if( p == leftChild( g ) ){
        y = rightChild( g );
        if( isRed( y ) ){
          setBlack( p );
          setBlack( y );
          setRed( g );
          x = g;
        }else{
          if( x == rightChild( p ) ){
            rotateLeft( x = p );
            g = parent(p = parent(x));
          }
          setBlack( p );
          setRed( g );
          if( g != NULL_POINTER )
            rotateRight( g );
        }
      }else{
        y = leftChild( g );
        if( isRed( y ) ){
          setBlack( p );
          setBlack( y );
          setRed( g );
          x = g;
        }else{
          if( x == leftChild( p ) ){
            rotateRight( x = p );
            g = parent(p = parent(x));
          }
          setBlack( p );
          setRed( g );
          if( g != NULL_POINTER)
            rotateLeft( g );
        }
      }
    }
    setBlack( root );
  }
  private int successor( int i ){
    if( i == NULL_POINTER )
      return NULL_POINTER;
    int j;
    if( (j = rightChild( i )) != NULL_POINTER ){
      while( leftChild( j ) != NULL_POINTER )
        j = leftChild( j );
      return j;
    }
    j = parent( i );
    while( j != NULL_POINTER && rightChild( j ) == i ){
      i = j;
      j = parent( j );
    }
    return j;
  }

  private class IntMapValueIterator implements Iterator{
    IntSetIterator it;
    private IntMapValueIterator(){
      it = new IntSetIterator();
    }
    public boolean hasNext(){
      return it.hasNext();
    }
    public Object next(){
      return get( it.next() );
    }
    public void remove(){
      it.remove();
    }
  }

  private class IntSetIterator implements IntIterator{
    int curr;
    int prev;
    int expectedModifications;
    private IntSetIterator(){
      expectedModifications = modifications;
      prev = NULL_POINTER;
      curr = root;
      if( root != NULL_POINTER )
        while( leftChild( curr ) != NULL_POINTER )
          curr = leftChild( curr );
    }
    public boolean hasNext(){
      if(modifications != expectedModifications)
        throw new ConcurrentModificationException();
      return curr != NULL_POINTER;
    }
    public int next(){
      if(modifications != expectedModifications)
        throw new ConcurrentModificationException();
      if( curr == NULL_POINTER )
        throw new NoSuchElementException();
      int i = value( curr );
      prev = curr;
      curr = successor( curr );
      return i;
    }
    public void remove(){
      if(modifications != expectedModifications)
        throw new ConcurrentModificationException();
      if( prev == NULL_POINTER )
        throw new NoSuchElementException();
      // we have to grab the value to find it again.
      // it might move after the deletion
      if( curr != NULL_POINTER ){
        int v = value( curr );
        deleteElement( prev );
        curr = getIndex( v );
      }else
        deleteElement( prev );
      expectedModifications++;
      prev = NULL_POINTER;
    }
  }
  public static void main( String[] args ){
    int rounds = 1000;
    int range = 100;
    int seed;
    java.util.Random r = null;
    if( args.length > 0 ){
      try{
        rounds = java.lang.Integer.parseInt( args[ 0 ] );
        if( args.length > 1 )
          range = java.lang.Integer.parseInt( args[ 1 ] );
        if( args.length > 2 )
          r = new java.util.Random( java.lang.Integer.parseInt( args[2] ) );
      }catch( Exception e ){}
    }
    if( r == null ){
      r = new java.util.Random();
    }
    System.out.println("beginning test. " + rounds 
        + " rounds in range -" + range + " to " + range );
    IntMap testObj = new IntMap(0);
    int i, j;
    int goodRemoves = 0;
    int badRemoves = 0;
    int goodPuts = 0;
    int badPuts = 0;
    Integer J;
    for( i = 0; i < rounds; i++ ){
      j = r.nextInt( range * 2 ) - range;
      if( testObj.contains( j ) ){
        if( r.nextBoolean() ){
          if( testObj.put( j, new Integer( j ) ) == null )
            System.out.println( "inserted "+j
                +" which was already in, and got null value back.");
          if( ! testObj.contains( j ) )
            System.out.println( "put "+j+" in, but it's not there." );
          badPuts++;
        }else{
          if( testObj.remove( j ) == null )
            System.out.println( "removevd "+j
                +" which was already in, and got null value back.");
          if( testObj.contains( j ) )
            System.out.println( "removed "+j+", but it's still there." );
          goodRemoves++;
        }
      }else{
        if( r.nextBoolean() ){
          if( testObj.put( j, new Integer( j ) ) != null )
            System.out.println( "inserted "+j
                +" which was not in, and got non-null value back.");
          if( ! testObj.contains( j ) )
            System.out.println( "put "+j+" in, but it's not there." );
          goodPuts++;
        }else{
          if( testObj.remove( j ) != null )
            System.out.println( "removevd "+j
                +" which was not in, and got non-null value back.");
          if( testObj.contains( j ) )
            System.out.println( "removed "+j+", but it's still there." );
          badRemoves++;
        }
      }
    }
    System.out.println();
    System.out.println("Done with random stuff.  ");
    System.out.println("Number of new Puts:" + goodPuts );
    System.out.println("Number of existing Puts (replacements):"+badPuts);
    System.out.println("Number of existing items removed:"+goodRemoves);
    System.out.println("Number of non-existing items removed:"+badRemoves);

    System.out.println("Map size should be new Puts - existing Removes ="
        + (goodPuts - goodRemoves) );
    System.out.println("Map size is " + testObj.size);
    System.out.println("Map contains:");
    IntIterator it = testObj.keyIterator();
    Iterator itt = testObj.valueIterator();
    while( it.hasNext() ){
      if( ! itt.hasNext() ){
        System.out.println("Iterator hasNext mismatch");
      }
      j = it.next();
      if( ((Integer)itt.next()).intValue() != j ){
        System.out.println( "Iterator next value mismatch");
      }
      System.out.print(", " + j );
    }
    it = testObj.keyIterator();
    itt = testObj.valueIterator();
    System.out.println();
    while( it.hasNext() ){
      it.next();
      it.remove();
    }
    for( j = 0; j < testObj.referents.length; j++ ){
      if( testObj.referents[ j ] != null )
        System.out.println("Failed to null out referent "+testObj.referents[j]);
    }
    System.out.println();
    try{
      itt.hasNext();
      System.out.println( "Failed to throw concurrent modification error" );
    }catch( ConcurrentModificationException e ){
      System.out.println();
      System.out.println( " and finally a successfully thrown exception.");
    }
  }
}
