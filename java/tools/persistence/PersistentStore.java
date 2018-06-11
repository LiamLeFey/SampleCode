package tools.persistence;

import tools.*;
import java.io.*;

public class PersistentStore{
  private static final int MOBILE_DATA_START = 8;

  private RandomAccessFile myRAF;

  private boolean changedFromStore;

  //private IdIndex index;
  //private IdIndex committedIndex;
  private IndexBlock indexBlock;
  private IndexBlock committedIndexBlock;


  // free space in the file
  private FreeSpace freeSpace;
  private FreeSpace usableFreeSpace;
  private FreeSpace committedFreeSpace;
  // this contains a mapping of used space only.
  // the key is the start location, and the value is the length.
  // it is in an inconsistant state if key + value( key ) > successor( key )
  private IntIntMap usedRecord;
  private IntIntMap usableUsedRecord;
  private IntIntMap committedUsedRecord;

  public PersistentStore( File filename ) throws IOException{
    this( filename, new Index[0], null );
  }
  public PersistentStore(File filename, Index[] hashers, Streamable s )
    throws IOException{
    if( ! filename.exists() ){
      filename.createNewFile();
      DataOutputStream dos = new DataOutputStream( 
          new FileOutputStream( filename, false ) );
      dos.writeInt( 0 ); // indexBlock location
      dos.writeInt( 0 ); // indexBlock space
      dos.flush();
      dos.close();
    }
    myRAF = new RandomAccessFile( filename, "rw" );
    buildFromStoredIndexBlock( hashers, s );
    changedFromStore = false;
  }
  private void buildFromStoredIndexBlock( Index[] hashers, Streamable s ) 
    throws IOException{
    readIndexBlockFromStore( hashers, s );
    buildUsedRecordFromIndex();
    buildFreeSpaceFromUsedRecord();

    committedIndexBlock = (IndexBlock)indexBlock.clone();
    usableFreeSpace = (FreeSpace)freeSpace.clone();
    committedFreeSpace = (FreeSpace)freeSpace.clone();
    usableUsedRecord = (IntIntMap)usedRecord.clone();
    committedUsedRecord = (IntIntMap)usedRecord.clone();
  }
  private void readIndexBlockFromStore( Index[] hashers, Streamable s ) 
    throws IOException{
    indexBlock = new IndexBlock( hashers, s );
    int indexBlockLocation;
    byte[] bs;
    myRAF.seek( 0 );
    indexBlockLocation = myRAF.readInt();
    if( indexBlockLocation != 0 ){
      myRAF.seek( indexBlockLocation );
      fillStreamable( indexBlock );
    }
  }
  private void buildUsedRecordFromIndex() throws IOException{
    myRAF.seek( 0 );
    int indexBlockLocation = myRAF.readInt();
    int indexBlockSpace = myRAF.readInt();
    usedRecord = new IntIntMap( indexBlock.idCount() );
    IntIterator it = indexBlock.idIterator();
    int key, location, size;
    while( it.hasNext() ){
      key = it.next();
      location = indexBlock.getLocation( key );
      myRAF.seek( location );
      size = myRAF.readInt();
      usedRecord.put( location, size );
    }
    if( indexBlockLocation != 0 && indexBlockSpace != 0 )
      usedRecord.put( indexBlockLocation, indexBlockSpace );
  }
  private void buildFreeSpaceFromUsedRecord() throws IOException{
    IntIterator it = usedRecord.keyIterator();
    freeSpace = new FreeSpace( 8 );
    int current = MOBILE_DATA_START;
    int nextUsed;
    while( it.hasNext() ){
      nextUsed = it.next();
      if( current < nextUsed )
        freeSpace.add( nextUsed - current, current );
      current = nextUsed + usedRecord.get( nextUsed );
    }
    if( current < myRAF.length() )
      freeSpace.add( (int)myRAF.length()-current, current );
  }
  private void fillStreamable( Streamable s ) throws IOException{
    int size = myRAF.readInt();
    byte[] bs = new byte[ size ];
    bs[0] = (byte)(0xFF & (size >> 24));
    bs[1] = (byte)(0xFF & (size >> 16));
    bs[2] = (byte)(0xFF & (size >> 8));
    bs[3] = (byte)(0xFF & size);
    myRAF.readFully( bs, 4, size-4 );
    s.setState( new DataInputStream( new ByteArrayInputStream( bs ) ) );
  }
  // this does not check for space or anything.  it just moves
  // bytes.
  private void copyData( int fromLocation, int toLocation, int length ) throws IOException{
    byte[] bs = new byte[ length ];
    myRAF.seek( fromLocation );
    myRAF.readFully( bs );
    myRAF.seek( toLocation );
    myRAF.write( bs );
  }

  public int getMaxID(){
    return indexBlock.getMaxID();
  }
  public int[] getIDs(){
    return indexBlock.getIDs();
  }
  public int[] getIDs( int[] userIndexCodes ){
    boolean[] b = new boolean[ userIndexCodes.length ];
    java.util.Arrays.fill( b, true );
    return getIDs( userIndexCodes, b );
  }
  public int[] getIDs( int[] userIndexCodes, boolean[] useThisCode ){
    if( useThisCode.length != indexBlock.codesToIds.length )
      return new int[0];
    int i, j, setCount = 0;
    for( i = 0; i < useThisCode.length; i++ )
      if( useThisCode[i] )
        setCount++;
    if( setCount == 0 )
      return new int[0];
    IntSet[] sets = new IntSet[setCount];
    for( j = 0, i = 0; i < useThisCode.length; i++ )
      if( useThisCode[i] ){
        sets[j] = indexBlock.getIDs( userIndexCodes[i], i );
        if( sets[j] == null || sets[j++].size() == 0 )
          return new int[0];
      }
    if( sets.length == 1 )
      return sets[0].toArray();
    if( sets.length == 2 )
      return sets[0].intersection(sets[1]).toArray();
    Heap setHeap = new Heap( new java.util.Comparator(){
      public int compare( Object o1, Object o2 ){
        return ((IntSet)o1).size() - ((IntSet)o2).size();
      } } );
    for( i = 0; i < sets.length; i++ )
      setHeap.add( sets[i] );
    IntSet set = (IntSet)setHeap.next();
    while( set != null && set.size() > 0 && setHeap.hasNext() ){
      set = set.intersection( (IntSet)setHeap.next() );
    }
    return set != null && set.size() > 0 ? set.toArray() : new int[0];
  }
  // packs the file, removing all free space
  // good to use after making many changes
  // the tricky part will be updating all the location pointers...
  // Let's see, if we built a temporary map from location to index,
  public void pack() throws IOException{
    commit();
    // we build a map from location to pointer into the 
    // data store of the index, because from here on in,
    // we ignore the software index object, and write
    // things directly to disk.
    IntIntMap locationsToIndexPtr = new IntIntMap( indexBlock.idCount() );
    int[] keys = indexBlock.getIDs();
    int i;
    for(i = 0; i < keys.length; i++){
      // the actual location in the index block is 8 + 8*i + 4
      // for 2 ints (IndexBlock and IdIndex) then 2 ints per record,
      // but the location is the second record
      locationsToIndexPtr.put( indexBlock.getLocation( keys[i] ), (i+1)*8+4 );
    }
    // now, check if the index starts at the 8th byte
    myRAF.seek( 0 );
    int indexBlockLocation = myRAF.readInt();
    int indexBlockSpace = myRAF.readInt();
    int nextSpace, nextLocation, newLocation, recordSize, iPtr;
    if( indexBlockLocation != MOBILE_DATA_START ){
      nextLocation = usedRecord.getCeilKey( MOBILE_DATA_START );
      while( nextLocation < indexBlockSpace+MOBILE_DATA_START ){
        newLocation = (int)myRAF.length();
        recordSize = usedRecord.get( nextLocation );
        copyData( nextLocation, newLocation, recordSize );
        if( nextLocation != indexBlockLocation ){
          iPtr = locationsToIndexPtr.get( nextLocation );
          myRAF.seek( indexBlockLocation + iPtr );
          myRAF.writeInt( newLocation );
          locationsToIndexPtr.remove( nextLocation );
          locationsToIndexPtr.put( newLocation, iPtr );
        }else{
          myRAF.seek( 0 );
          myRAF.writeInt( newLocation );
          indexBlockLocation = newLocation;
        }
        usedRecord.remove( nextLocation );
        usedRecord.put( newLocation, recordSize );
        nextLocation = usedRecord.getCeilKey( MOBILE_DATA_START );
      }
      copyData( indexBlockLocation, MOBILE_DATA_START, indexBlockSpace );
      myRAF.seek( 0 );
      myRAF.writeInt( MOBILE_DATA_START );
      usedRecord.remove( indexBlockLocation );
      usedRecord.put( MOBILE_DATA_START, indexBlockSpace );
    }
    nextSpace = indexBlockSpace+MOBILE_DATA_START;
    nextLocation = usedRecord.getCeilKey( nextSpace );
    while( nextLocation != Integer.MIN_VALUE ){
      recordSize = usedRecord.get( nextLocation );
      if( nextLocation == nextSpace ){
        // don't need to move anything, go to next space
        nextSpace = nextLocation + recordSize;
      }else{
        if( recordSize <= nextLocation - nextSpace ){
          copyData( nextLocation, nextSpace, recordSize );
          iPtr = locationsToIndexPtr.get( nextLocation );
          myRAF.seek( MOBILE_DATA_START+iPtr );
          myRAF.writeInt( nextSpace );
          locationsToIndexPtr.remove( nextLocation );
          locationsToIndexPtr.put( nextSpace, iPtr );
          usedRecord.remove( nextLocation );
          usedRecord.put( nextSpace, recordSize );
          nextSpace = nextSpace + recordSize;
        }else{
          newLocation = (int)myRAF.length();
          copyData( nextLocation, newLocation, recordSize );
          iPtr = locationsToIndexPtr.get( nextLocation );
          myRAF.seek( MOBILE_DATA_START+iPtr );
          myRAF.writeInt( newLocation );
          locationsToIndexPtr.remove( nextLocation );
          locationsToIndexPtr.put( newLocation, iPtr );
          usedRecord.remove( nextLocation );
          usedRecord.put( newLocation, recordSize );
        }
      }
      nextLocation = usedRecord.getCeilKey( nextSpace );
    }
    myRAF.seek( MOBILE_DATA_START );
    fillStreamable( indexBlock );
    myRAF.getChannel().truncate( nextSpace );

    committedIndexBlock = (IndexBlock)indexBlock.clone();
    freeSpace = new FreeSpace( 1 );
    usableFreeSpace = new FreeSpace( 1 );
    committedFreeSpace = new FreeSpace( 1 );
    usableUsedRecord = (IntIntMap)usedRecord.clone();
    committedUsedRecord = (IntIntMap)usedRecord.clone();
    myRAF.getChannel().force( false );
    changedFromStore = false;
  }
  public void rollback(){
    if( changedFromStore ){
      indexBlock = (IndexBlock)committedIndexBlock.clone();
      usedRecord = (IntIntMap)committedUsedRecord.clone();
      usableUsedRecord = (IntIntMap)committedUsedRecord.clone();
      freeSpace = (FreeSpace)committedFreeSpace.clone();
      usableFreeSpace = (FreeSpace)committedFreeSpace.clone();
      changedFromStore = false;
    }
  }
  public void commit() throws IOException{
    if( ! changedFromStore )
      return;
    myRAF.seek( 0 );
    int oldIndexBlockLocation = myRAF.readInt();
    int oldIndexBlockSpace = myRAF.readInt();
    int indexBlockLength = indexBlock.getStreamedLength();
    int indexBlockSpace = 1;
    while( indexBlockSpace <= indexBlockLength )
      indexBlockSpace <<= 1;
    // store the new index
    ByteArrayOutputStream baos = new ByteArrayOutputStream( indexBlockLength );
    DataOutputStream os = new DataOutputStream( baos );
    indexBlock.getState( os );
    byte[] ibs = baos.toByteArray();
    //int indexBlockLocation = (int)myRAF.length();
    int indexBlockLocation = usableFreeSpace.get( indexBlockSpace );
    int ceil = usedRecord.getCeilKey( indexBlockLocation );
    ceil = ceil == Integer.MIN_VALUE? (int)myRAF.length() : ceil;
    freeSpace.remove( ceil - indexBlockLocation, indexBlockLocation );
    if( ceil - indexBlockLocation > indexBlockSpace )
      freeSpace.add( 
          (ceil - indexBlockLocation) - indexBlockSpace, 
          indexBlockLocation + indexBlockSpace );
    myRAF.seek( indexBlockLocation );
    myRAF.write( ibs );
    usedRecord.put( indexBlockLocation, indexBlockSpace );
    // commit and usedRecord
    // at this point, only the non-usable and non-committed 
    // versions of freespace and usedRecord are valid.
    // (freespace is not stored, so if something goes wrong, it
    // just gets rebuilt.)
    // switch the pointer to the new index
    // record index space
    // These need to be done as attomically as possible, so we
    // write them as a single byte array.
    byte[] bs = new byte[ 8 ];
    bs[0] = (byte)(0xFF & (indexBlockLocation >> 24));
    bs[1] = (byte)(0xFF & (indexBlockLocation >> 16));
    bs[2] = (byte)(0xFF & (indexBlockLocation >> 8));
    bs[3] = (byte)(0xFF & indexBlockLocation);
    bs[4] = (byte)(0xFF & (indexBlockSpace >> 24));
    bs[5] = (byte)(0xFF & (indexBlockSpace >> 16));
    bs[6] = (byte)(0xFF & (indexBlockSpace >> 8));
    bs[7] = (byte)(0xFF & indexBlockSpace);
    myRAF.seek( 0 );
    myRAF.write( bs );
    committedIndexBlock = (IndexBlock)indexBlock.clone();
    usedRecord.remove( oldIndexBlockLocation );
    ceil = usedRecord.getCeilKey( oldIndexBlockLocation );
    int floor = usedRecord.getFloorKey( oldIndexBlockLocation - 1 );
    ceil = ceil == Integer.MIN_VALUE? (int)myRAF.length() : ceil;
    floor = floor == Integer.MAX_VALUE? MOBILE_DATA_START 
      : floor + usedRecord.get( floor );
    if( ceil != oldIndexBlockLocation + oldIndexBlockSpace ){
      freeSpace.remove( 
          ceil - (oldIndexBlockLocation + oldIndexBlockSpace), 
          (oldIndexBlockLocation + oldIndexBlockSpace) );
    }
    if( floor != oldIndexBlockLocation ){
      freeSpace.remove( oldIndexBlockLocation - floor, floor );
    }
    freeSpace.add( ceil - floor, floor );

    // copy index to beginning if we have space

    int startFreeSpace = usedRecord.getCeilKey( MOBILE_DATA_START ) 
      - MOBILE_DATA_START;
    if( startFreeSpace >= indexBlockSpace ){
      freeSpace.remove( startFreeSpace, MOBILE_DATA_START );
      freeSpace.add( 
          startFreeSpace - indexBlockSpace, 
          MOBILE_DATA_START + indexBlockSpace );
      usedRecord.put( MOBILE_DATA_START, indexBlockSpace );

      myRAF.seek( MOBILE_DATA_START );
      myRAF.write( ibs );

      // switch index pointer again
      bs[0] = (byte)(0xFF & (MOBILE_DATA_START >> 24));
      bs[1] = (byte)(0xFF & (MOBILE_DATA_START >> 16));
      bs[2] = (byte)(0xFF & (MOBILE_DATA_START >> 8));
      bs[3] = (byte)(0xFF & MOBILE_DATA_START);
      myRAF.seek( 0 );
      myRAF.write( bs );
      usedRecord.remove( indexBlockLocation );

      ceil = usedRecord.getCeilKey( indexBlockLocation );
      floor = usedRecord.getFloorKey( indexBlockLocation - 1 );
      ceil = ceil == Integer.MIN_VALUE? (int)myRAF.length() : ceil;
      floor = floor == Integer.MAX_VALUE 
          ? MOBILE_DATA_START : floor + usedRecord.get( floor );
      if( ceil != indexBlockLocation + indexBlockSpace ){
        freeSpace.remove( ceil - (indexBlockLocation + indexBlockSpace), 
          (indexBlockLocation + indexBlockSpace) );
      }
      if( floor != indexBlockLocation ){
        freeSpace.remove( indexBlockLocation - floor, floor );
      }
      freeSpace.add( ceil - floor, floor );

      if( startFreeSpace > indexBlockSpace ){
        int indexEnd = MOBILE_DATA_START + indexBlockSpace;
        ceil = usedRecord.getCeilKey( indexEnd );
        ceil = ceil == Integer.MIN_VALUE? (int)myRAF.length() : ceil;
        freeSpace.add( ceil - indexEnd, indexEnd );
      }
    }
    committedUsedRecord = (IntIntMap)usedRecord.clone();
    usableUsedRecord = (IntIntMap)usedRecord.clone();
    committedFreeSpace = (FreeSpace)freeSpace.clone();
    usableFreeSpace = (FreeSpace)freeSpace.clone();
    changedFromStore = false;
  }
  public void flush() throws IOException{
    commit();
    myRAF.getChannel().force( false );
  }
  public void close() throws IOException{
    myRAF.close();
  }
  public void deleteStorable( int id ) throws IOException{
    int location = indexBlock.getLocation( id );
    if( location < MOBILE_DATA_START || location >= myRAF.length() )
      return;
    int size = usedRecord.get( location );
    usedRecord.remove( location );
    int ceil, floor;
    ceil = usedRecord.getCeilKey( location+size );
    ceil = ceil == Integer.MIN_VALUE? (int)myRAF.length() : ceil;
    floor = usedRecord.getFloorKey( location-1 );
    floor = floor == Integer.MAX_VALUE? MOBILE_DATA_START 
      : floor + usedRecord.get( floor );
    if( ceil != location + size )
      freeSpace.remove( ceil - (location + size), (location+size) );
    if( floor != location )
      freeSpace.remove( location - floor, floor );
    freeSpace.add( ceil - floor, floor );
    indexBlock.remove( id );
    changedFromStore = true;
  }
  public void store( Storable o ) throws IOException{
    int id = o.getID();
    if( indexBlock.containsId( id ) )
      deleteStorable( id );
    int length = o.getStreamedLength();
    ByteArrayOutputStream baos = new ByteArrayOutputStream( length );
    o.getState( new DataOutputStream( baos ) );
    byte[] bs = baos.toByteArray();
    if( bs.length != length ){
      String message = "Storable length missmatch. " + o.getClass().getName() 
        + " with id of " + o.getID() + " returned " + length 
        + " from getStreamedLength(), but wrote " + bs.length + " bytes.";
      throw new ArrayIndexOutOfBoundsException( message );
    }
    //int location = freeSpace.getAndRemove( length );
    //We only pull from the freeSpace that is committed.
    int location = usableFreeSpace.get( length );
    int usableCeil = usableUsedRecord.getCeilKey( location );
    int ceil = usedRecord.getCeilKey( location );
    ceil = ceil == Integer.MIN_VALUE ? 
      (int)myRAF.length() 
      : ceil;
    usableCeil = usableCeil == Integer.MIN_VALUE ? 
      (int)myRAF.length() 
      : usableCeil;
    int usableFreeLength = usableCeil - location;
    int freeLength = ceil - location;
    usableFreeSpace.remove( usableFreeLength, location );
    usableFreeSpace.add( usableFreeLength - length, location + length );
    freeSpace.remove( freeLength, location );
    freeSpace.add( freeLength - length, location + length );
    myRAF.seek( location );
    myRAF.write( bs );
    usableUsedRecord.put( location, length );
    usedRecord.put( location, length );
    indexBlock.put( id, location );
    changedFromStore = true;
  }
  public void loadStorable( int index, Storable o ) throws IOException{
    int location = indexBlock.getLocation( index );
    if( location < MOBILE_DATA_START || location >= myRAF.length() )
      return;
    myRAF.seek( location );
    fillStreamable( o );
  }
  public void retrieve( Storable o ) throws IOException{
    loadStorable( o.getID(), o );
  }
  public String getReport(){
    StringBuffer sb = new StringBuffer("PersistentStoreReport\n");
    sb.append("myRAF is " + myRAF + "\n" );
    sb.append("indexBlock follows:\n");
    sb.append( indexBlock.getReport() );
    sb.append("committedIndexBlock follows:\n ");
    sb.append( committedIndexBlock.getReport());
    sb.append("usedRecord:\n");
    int key, value;
    IntIterator it = usedRecord.keyIterator();
    while( it.hasNext() ){
      key = it.next();
      value = usedRecord.get( key );
      sb.append( niceInt(key, 8) + "->" + niceInt( value, 8 )+ "\n" );
    }
    sb.append("committedUsedRecord:\n");
    it = committedUsedRecord.keyIterator();
    while( it.hasNext() ){
      key = it.next();
      value = committedUsedRecord.get( key );
      sb.append( niceInt(key, 8) + "->" + niceInt( value, 8 )+ "\n" );
    }
    sb.append("freeSpace:\n");
    sb.append(freeSpace.getReport());
    sb.append("usableFreeSpace:\n");
    sb.append(usableFreeSpace.getReport());
    sb.append("committedFreeSpace:\n");
    sb.append(committedFreeSpace.getReport());
    return sb.toString();
  }
  private static String niceInt( int i, int width ){
    String nStr = "" + i;
    int len = nStr.length();
    if( len >= width )
      return nStr;
    return ("            ").substring( 12-(width-len) ) + nStr;
  }


  private class FreeSpace implements Cloneable{
    IntMap map;
    public FreeSpace(int size){
      map = new IntMap(size);
    }
    public void add( int size, int location ) throws IOException{
      if( size < 1 || location < MOBILE_DATA_START 
          || location > myRAF.length() )
        return;
      IntSet set;
      if( map.contains( size ) )
        set = (IntSet)map.get( size );
      else{
        set = new IntSet( 1 );
        map.put( size, set );
      }
      set.add( location );
    }
    public void remove( int size, int location ){
      if( map.contains( size ) ){
        IntSet set = (IntSet)map.get( size );
        set.remove( location );
        if( set.size() == 0)
          map.remove( size );
      }
    }
    public boolean contains( int size, int location ){
      return map.contains( size )
        && ((IntSet)map.get( size )).contains( location );
    }
    // returns a location which is at least as large as size.
    // if there are no internal locations, returns the eof location
    public int get( int size ) throws IOException{
      size = map.getCeilKey( size );
      if( size == Integer.MIN_VALUE )
        return (int) myRAF.length();
      return ((IntSet)map.get( size )).getCeil( 0 );
    }
    public int getAndRemove( int size ) throws IOException{
      size = map.getCeilKey( size );
      if( size == Integer.MIN_VALUE )
        return (int) myRAF.length();
      int location = ((IntSet)map.get( size )).getCeil( 0 );
      remove( size, location );
      return location;
    }
    public Object clone(){
      int i;
      FreeSpace clone = new FreeSpace( map.size() );
      int[] keys = map.keys();
      for( i = 0; i < keys.length; i++ ){
        clone.map.put( keys[i], ((IntSet)map.get( keys[i] )).clone() );
      }
      return clone;
    }
    private String getReport(){
      StringBuffer sb = new StringBuffer();
      IntIterator it = map.keyIterator();
      IntIterator jt;
      while( it.hasNext() ){
        int i;
        i = it.next();
        sb.append( niceInt( i, 7 ) + " -> ");
        jt = (IntIterator)((IntSet)map.get(i)).iterator();
        sb.append( jt.next() );
        while(jt.hasNext())
          sb.append( ", " + jt.next() );
        sb.append("\n");
      }
      return sb.toString();
    }
  }
  private static class UserIndex implements Streamable, Cloneable {
    int stl;
    IntMap map;
    public UserIndex(int size){
      map = new IntMap(size);
      stl = 8;
    }
    public boolean add( int code, int id ) throws IOException{
      IntSet set;
      if( map.contains( code ) ){
        set = (IntSet)map.get( code );
      }else{
        set = new IntSet( 1 );
        map.put( code, set );
        stl += 8;
      }
      if( set.add( id ) ){
        stl += 4;
        return true;
      }
      return false;
    }
    public boolean remove( int code, int id ){
      IntSet set = (IntSet)map.get( code );
      if( set != null && set.remove( id ) ){
        stl -= 4;
        if( set.size() == 0){
          map.remove( code );
          stl -= 8;
        }
        return true;
      }
      return false;
    }
    public boolean contains( int code, int id ){
      IntSet set = (IntSet)map.get( code );
      return set != null && set.contains( id );
    }
    public IntSet getIDs( int code ) {
      IntSet inst = (IntSet)map.get( code );
      return inst == null ? null : (IntSet)inst.clone();
      //return (IntSet)((IntSet)map.get( code )).clone();
    }
    public Object clone(){
      int i;
      UserIndex clone = new UserIndex( map.size() );
      int[] keys = map.keys();
      for( i = 0; i < keys.length; i++ ){
        clone.map.put( keys[i], ((IntSet)map.get( keys[i] )).clone() );
      }
      return clone;
    }
    public int getStreamedLength(){
      return stl;
    }
    public void getState( DataOutputStream os ) throws IOException{
      os.writeInt( stl );
      int i, j;
      int[] keys = map.keys();
      os.writeInt( keys.length );
      int[] vals;
      for( i = 0; i < keys.length; i++ ){
        os.writeInt( keys[i] );
        vals = ((IntSet)map.get( keys[i] )).toArray();
        os.writeInt( vals.length );
        for( j = 0; j < vals.length; j++ ){
          os.writeInt( vals[j] );
        }
      }
    }
    public void setState( DataInputStream is ) throws IOException{
      stl = is.readInt();
      int len = is.readInt();
      map = new IntMap( len );
      int key, i, j;
      int setLen;
      IntSet set;
      for( i = 0; i < len; i++ ){
        key = is.readInt();
        setLen = is.readInt();
        set = new IntSet( setLen );
        for( j = 0; j < setLen; j++ ){
          set.add( is.readInt() );
        }
        map.put( key, set );
      }
    }
    private String getReport(){
      StringBuffer sb = new StringBuffer();
      IntIterator it = map.keyIterator();
      IntIterator jt;
      while( it.hasNext() ){
        int i;
        i = it.next();
        sb.append( niceInt( i, 7 ) + " -> ");
        jt = (IntIterator)((IntSet)map.get(i)).iterator();
        sb.append( jt.next() );
        while(jt.hasNext())
          sb.append( ", " + jt.next() );
        sb.append("\n");
      }
      return sb.toString();
    }
  }
  // we have this because we need to control how it is 
  // Stored, which would break encapsulation of IntIntMap >sigh<
  private class IdIndex implements Streamable, Cloneable {
    private IntIntMap myMap;
    private IdIndex(){
      myMap = new IntIntMap();
    }
    private IdIndex( int i ){
      myMap = new IntIntMap( i );
    }
    private int getMax(){
      int i = myMap.getFloorKey( Integer.MAX_VALUE );
      return i == Integer.MAX_VALUE? -1 : i;
    }
    private int get( int i ){
      if( containsKey( i ) )
        return myMap.get( i );
      return -1;
    }
    private boolean remove( int i ){
      return myMap.remove( i );
    }
    private int[] keys(){
      return myMap.keys();
    }
    private boolean containsKey( int i ){
      return myMap.containsKey( i );
    }
    private void put( int i, int j ){
      myMap.put( i, j );
    }
    private int size() {
      return myMap.size();
    }
    private IntIterator keyIterator(){
      return myMap.keyIterator();
    }
    public int getStreamedLength(){
      return myMap.size()*8+4;
    }
    public void getState( DataOutputStream os ) throws IOException{
      int[] keys = myMap.keys();
      int i;
      os.writeInt( getStreamedLength() );
      for( i = 0; i < keys.length; i++ ){
        os.writeInt( keys[i] );
        os.writeInt( myMap.get( keys[i] ) );
      }
    }
    public void setState( DataInputStream is ) throws IOException{
      int key, val, len;
      int i;
      len = is.readInt();
      len = (len-4)/8;

      if( ! myMap.isEmpty() ){
        myMap = new IntIntMap( len );
      }
      for( i = 0; i < len; i++ ){
        key = is.readInt();
        val = is.readInt();
        myMap.put(key, val);
      }
    }
    public Object clone(){
      IdIndex clone = new IdIndex( size() );
      clone.myMap = (IntIntMap)myMap.clone();
      return clone;
    }
  }
  private class IndexBlock implements Streamable, Cloneable{
    IdIndex idToLocation;
    UserIndex[] codesToIds;
    Index[] codeGetters;
    Streamable myStreamable;
    private IndexBlock( int i ){
      codesToIds = new UserIndex[ i ];
      codeGetters = new Index[ i ];
    }
    public IndexBlock( Index[] hashers, Streamable s ){
      codeGetters = hashers;
      myStreamable = s;
      idToLocation = new IdIndex();
      codesToIds = new UserIndex[ hashers.length ];
      int i;
      for( i = 0; i < hashers.length; i++ ){
        codesToIds[i] = new UserIndex( 1 );
      }
    }

    public int[] getIDs(){
      return idToLocation.keys();
    }
    public boolean remove( int id ) throws IOException{
      int i, code;
      try{
        if( codeGetters.length > 0 ){
          int location = getLocation( id );
          myRAF.seek( location );
          fillStreamable( myStreamable );
        }
        for( i = 0; i < codeGetters.length; i++ ){
          code = codeGetters[i].getCode( myStreamable );
          codesToIds[i].remove( code, id );
        }
      }catch(IOException e){
        e.printStackTrace( System.err );
      }
      return idToLocation.remove( id );
    }
    public void put( int id, int location ){
      try{
        if( idToLocation.containsKey( id ) ){
          remove( id );
        }
        if( codeGetters.length > 0 ){
          myRAF.seek( location );
          fillStreamable( myStreamable );
        }
        int i, code;
        for( i = 0; i < codeGetters.length; i++ ){
          code = codeGetters[i].getCode( myStreamable );
          codesToIds[i].add( code, id );
        }
      }catch(IOException e){
        e.printStackTrace( System.err );
      }
      idToLocation.put( id, location );
    }
    public int idCount(){
      return idToLocation.size();
    }
    public int getMaxID(){
      return idToLocation.getMax();
    }
    public IntIterator idIterator(){
      return idToLocation.keyIterator();
    }
    public boolean containsId( int id ){
      return idToLocation.containsKey( id );
    }
    public int getLocation( int id ){
      return idToLocation.get( id );

    }
    public IntSet getIDs( int code, int hasherIndex ){
      return codesToIds[hasherIndex].getIDs( code );
    }
    public Object clone(){
      IndexBlock clone = new IndexBlock( codeGetters.length );
      clone.myStreamable = myStreamable;
      clone.idToLocation = (IdIndex)idToLocation.clone();
      int i;
      for( i = 0; i < codeGetters.length; i++ ){
        clone.codesToIds[i] = (UserIndex)codesToIds[i].clone();
        // these aren't neccessarily clonable.
        clone.codeGetters[i] = codeGetters[i];
      }
      return clone;
    }

    public int getStreamedLength(){
      int stl = 8;
      stl += idToLocation.getStreamedLength();
      int i;
      for( i = 0; i < codesToIds.length; i++ )
        stl += codesToIds[i].getStreamedLength();
      return stl;
    }
    public void getState( DataOutputStream os ) throws IOException{
      os.writeInt( getStreamedLength() );
      idToLocation.getState( os );
      os.writeInt( codesToIds.length );
      int i;
      for( i = 0; i < codesToIds.length; i++ ){
        codesToIds[i].getState( os );
      }
    }
    public void setState( DataInputStream is ) throws IOException{
      int stl = is.readInt();
      idToLocation.setState( is );
      int count = is.readInt();
      codesToIds = new UserIndex[ count ];
      int i;
      for( i = 0; i < count; i++ ){
        codesToIds[i] = new UserIndex( idToLocation.size() );
        codesToIds[i].setState( is );
      }

    }
    private String getReport(){
      StringBuffer sb = new StringBuffer("id -> location:\n");
      int key, value;
      IntIterator it = idToLocation.keyIterator();
      while( it.hasNext() ){
        key = it.next();
        value = idToLocation.get( key );
        sb.append( niceInt(key, 8) + "->" + niceInt( value, 8 )+ "\n" );
      }
      for( int i = 0; i < codesToIds.length; i++ ){
        sb.append("codesToIds " + i + "\n");
        sb.append( codesToIds[i].getReport() );
      }
      return sb.toString();
    }
  }
  protected void finalize(){
    if( myRAF != null )
      try{
        myRAF.close();
        super.finalize();
      }catch(Throwable e){
        e.printStackTrace( System.err );
      }
  }
}
