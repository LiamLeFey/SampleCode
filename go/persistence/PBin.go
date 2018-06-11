// Copyright 2010 Liam LeFey.  All rights reserved.
// Use of this code is governed by the GPL v3
//
// a collection of data of Persistable type which is backed by an
// io.ReadWriteSeeker, presumably a file, but any ReadWriteSeeker
// will do.
//
// Every attempt is made to keep the file in a consistant state.
// Data changes are not permanent until Commit is called.
// Any changes can be undone back to the last point at
// which "Commit" was called by calling "Rollback"
//
// The data must support the interface Persistable, and Ids must be
// non-negative and unique between unique instances of the data.
//
// the data should be considered owned by the PBin to prevent
// unintended corruption when moving data from one PBin to
// another as it is difficult to manage prevention of duplicate Ids
// otherwise.
//
// Notes that should help while reading the code:
// indexes and sizes are stored as 8 bytes (int64) most significant
// byte first (big endian, I believe)
//
// a convention we use is for each 'chunk' of data, we store the
// size in bytes of that chunk of data (including the size storage)
// and then the data itself.
//
// The first pieces of data in the file are:
// at offset 0, one byte 0x00 indicating use the first offset as an
// index to the index block, 0x01 indicates use of the second index.
// This allows us to keep data in a consistant
// state, regardless of possible failure mid-write.
//
// So an empty file is equivalent to
// 0x00,0x0000000000000017,0x0000000000000017,0x0000000000000008(EOF)
// which would denote that the index block is stored at byte offset
// 17, just following the char selector and the two index pointers,
// (the second of which is ignored), followed by the length of the block
// (8) which indicates no data after the length itself

package persistence

import (
	"os"
	"io"
	"fmt"
	"strconv"
	. "project/tools"
)
var log io.Writer
func SetLog( w io.Writer ){
	log = w
}
func init (){
	log = os.Stdout
}
type PBin struct {
	// ids and indexing data are loaded from the ReadWriteSeeker,
	// but the data from the collected Persistables are loaded as
	// needed.

	// in the future there may be the option to leave the indexing
	// data stored in the ReadWriteSeeker, but for now, that is
	// considered too much of a performance hit.

	// to facilitate rollback without reloading and rebuilding
	// the tables, some information is stored in multiple versions:
	// current (no var name prefix) and committed

	f io.ReadWriteSeeker
	changed bool

	// the collection of unique Ids for the Persistables stored
	// in this PBin
	ids *SI64Set
	committedIds *SI64Set

	// a collection of the locations pointed to by the ids
	locs *SI64Set
	committedLocs *SI64Set

	// map from id to the location (in byte offset) of each
	// Persistable
	id2loc map[int64] int64
	committedId2loc map[int64] int64

	// map from location (start boundary) to id.
	loc2id map[int64] int64
	committedLoc2id map[int64] int64

	// map from id to typeHash
	id2typeHash map[int64] int32
	committedId2typeHash map[int64] int32

	// map from typeHash to set of ids
	typeHash2ids map[int32] *SI64Set
	committedTypeHash2ids map[int32] *SI64Set

	// this keeps track of used and free areas in the backing
	// ReadWriteSeeker
	fmap *byteMap

}
// creates an initialized PBin, loading the id block from f, if it exists.
func NewPBin( f io.ReadWriteSeeker ) (*PBin, os.Error) {
	p := new(PBin)
	e := p.Initialize( f )
	return p, e
}
// initializes the PBin, loading the id block from f, if it exists.
// if f is empty, initializes f (see comments above)
func (p *PBin) Initialize( rws io.ReadWriteSeeker ) (e os.Error) {
	p.f = rws
	offset, e := p.getIBPtr()
	// initialize the file if it's empty
	if e != nil {
		p.seek( 0 )
		p.f.Write( []byte{ 0x00 } )
		p.writeI64( 17 )
		p.writeI64( 17 )
		p.writeI64( 8 )
		p.changed = true
		offset = 17
		e = nil
	}
	// mark the invariant indices
	p.fmap = newByteMap()
	p.fmap.use( 0, 17 )
	// find the index block
	if e = p.seek( offset ); e != nil { return e }
	var l int64
	if l, e = p.readI64(); e != nil { return e }
	// mark the index block as used in fmap
	p.fmap.use( offset, l )

	// remove the 8 bytes used to store the length, and
	// the result is the length of the data
	l -= 8
	p.locs = NewSI64Set( int(l) / 20 )

	// index block is made up of sets of, (id, location, typeHash)
	// so it must be a multiple of 20 in size.  Otherwise it's corrupt
	if l % 20 != 0 {
		return os.NewError("index block reported size % 20 != 0")
	}
	// load the stored part of the index
	buf := make([]byte, int(l))
	var n int
	if len(buf) != 0 {
		if n, e = p.f.Read( buf ); e != nil { return e }
	}
	if n != int(l) {
		return os.NewError("did not read expected number of bytes from index block")
	}
	p.ids = NewSI64Set( n / 20 )
	p.locs = NewSI64Set( n / 20 )
	// we expect to have few types compared to number of stored records
	p.id2loc = make(map[int64] int64, n / 20 )
	p.loc2id = make(map[int64] int64, n / 20 )
	p.typeHash2ids = make(map[int32] *SI64Set, 1 )
	p.id2typeHash = make(map[int64] int32, n / 20 )
	for i := 0; i < n; i += 20 {
		id := BToI64( buf[i:i+8] )
		p.ids.Add( id )
		loc := BToI64( buf[i+8:i+16] )
		p.locs.Add( loc )
		hash := BToI32( buf[i+16:i+20] )
		p.id2loc[ id ] = loc
		p.loc2id[ loc ] = id
		p.id2typeHash[ id ] = hash
		if _, ok := p.typeHash2ids[ hash ]; ! ok {
			p.typeHash2ids[ hash ] = NewSI64Set( 1 )
		}
		p.typeHash2ids[ hash ].Add( id )
	}
	// build the used record
	locs := p.locs.Values()
	//for i := range locs {
	for i := 0; i < len(locs); i++ {
		if e = p.seek( locs[ i ] ); e != nil { return e }
		if l, e = p.readI64(); e != nil { return e }
		p.fmap.use( locs[ i ], l )
	}
	p.committedIds = p.ids.Clone()
	p.committedLocs = p.locs.Clone()
	p.committedId2loc = cloneMap6464( p.id2loc )
	p.committedLoc2id = cloneMap6464( p.loc2id )
	p.committedId2typeHash = cloneMap6432( p.id2typeHash )
	p.committedTypeHash2ids = cloneSetMap( p.typeHash2ids )

	p.fmap.commit()
	p.changed = false
	return e
}
func (p *PBin) verifyWritten( v bool ) (e os.Error) {
	p2 := new(PBin)
	e = p2.Initialize( p.f )
	if v {
		p2.SmallReport()
	}
	return e
}
// encapsulates writing a new index block pointer
// if a non-nil error is returned, the old index
// block pointer is still valid
func (p *PBin) setIBPtr( ptr int64 ) (e os.Error) {
	if e = p.seek( 0 ); e != nil { return e }
	b := make( []byte, 1 )
	if _, e = p.f.Read( b ); e != nil { return e }
	if b[ 0 ] == 0x01 { p.seek( 1 )
	} else { p.seek( 9 ) }
	if e = p.writeI64( ptr ); e != nil { return e }
	b[ 0 ] ^= 0x01
	if e = p.seek( 0 ); e != nil { return e }
	if _, e = p.f.Write( b ); e != nil { return e }
	return nil
}
func (p *PBin) getIBPtr() (ptr int64, e os.Error) {
	if e = p.seek( 0 ); e != nil { return 0, e }
	b := make( []byte, 1 )
	if _, e = p.f.Read( b ); e != nil { return 0, e }
	if b[ 0 ] == 0x00 { p.seek( 1 )
	} else { p.seek( 9 ) }
	return p.readI64()
}
func (p *PBin) MaxId() (id int64) {
	id, _ = p.ids.Max()
	return id
}
func (p *PBin) Ids() (ids []int64) {
	return p.ids.Values()
}
func (p *PBin) TypeOfId( id int64 ) (h int32) {
	return p.id2typeHash[ id ]
}
func (p *PBin) IdsOfType( typeHash int32 ) (ids []int64) {
	if p.typeHash2ids[ typeHash ] != nil {
		return p.typeHash2ids[ typeHash ].Values()
	}
	return make( []int64, 0, 0 )
}
func (p *PBin) Count() int {
	return p.ids.Size()
}
// this breaks the OO concept of encapsulation, but it's
// crazy useful.  With this, if you know how a Persistable
// is stored, you can search the storage space for the bytes
// representing, the value 3.14159265 or the string "The quick
// red fox".  You find all locations of the bytes, get the
// Ids, and load only those Persistables that might be the
// one(s) you're looking for.  Saves brute search of the entire
// PBin. I know you're still brute searching the storage space,
// but presumably that's more efficient than loading each record
// and checking it.
// Note that it works on the committed data, since you are presumably
// searching the storage
func (p *PBin) LocInfo( l int64 ) (id int64, typeHash int32, b bool) {
	// get state of offset
	// 0x00 = free, 0x01 = free on commit,
	// 0x02 = used, 0x03 = free on rollback
	floor, e := p.fmap.borders.Floor( l )
	if e != nil { return -1, 0, false }
	switch p.fmap.states[ floor ] {
	// invalid location
	case 0x00 :
		l = -1
	// contains committed data
	case 0x01, 0x02 :
		l,_ = p.committedLocs.Floor( l )
		id, b = p.committedLoc2id[ l ]
		typeHash = p.committedId2typeHash[ id ]
	// contains uncommitted data
	case 0x03 :
		l,_ = p.locs.Floor( l )
		id, b = p.loc2id[ l ]
		typeHash = p.id2typeHash[ id ]
	}

	if l < 17 { return -1, 0, false }
	return
}
// returns a set of offsets in the backing data which contain b
func (p *PBin) GetLocs( b []byte ) (locs []int64) {
	p.f.Seek( 0, 0 )
	locs = make( []int64, 0 )
	// the logic is if b[0] matches, we create an entry in this
	// with key = loc, We check each byte thereafter and remove
	// it if it doesn't match, if it gets to len(b), add loc to locs
	potMatches := make( map[ int64 ] bool, 10 )
	curLoc := int64(0)
	a := make( []byte, 1 )
	n, e := p.f.Read( a )
	for e != os.EOF {
		if n < 1 { continue }
		curLoc += int64(n)
		if a[0] == b[0] {
			potMatches[ curLoc ] = true
		}
		for loc, _ := range potMatches {
			if b[ curLoc - loc ] != a[0] {
				potMatches[ loc ] = false, false
				continue
			}
			if int(curLoc - int64(loc)) == len(b)-1 {
				potMatches[ loc ] = false, false
				locs = append( locs, loc )
			}
		}
		n, e = p.f.Read( a )
	}
	return
}
// This commits all changes and then removes all free space from
// the underlying storage space.  This is good to do when most
// of the expected changes are complete, or periodically if the
// storage space is experiencing excessive fragmentation
//
// it's slow.  It causes lots of storage space access.  Don't use
// it in place of typical commits.
func (p *PBin) CommitAndPack() os.Error {
	var e os.Error
	// it may not be ideomatic go to throw this all on one line,
	// but almost every step needs to be checked.  if there's an
	// error, we want to stop and return immediately.  So if
	// the if... return fits, it's going on one line.
	if e = p.Commit(); e != nil { return e }

	// okay, the trick is to move everything around while
	// never leaving the data in an inconsistant or 
	// incorrectly referenced state.
	// the plan will be:
	// 1: duplicate the index, and set up some index pointers
	var iPtr1, iPtr2, iByteSize int64
	if iPtr1, e = p.getIBPtr(); e != nil { return e }
	if e = p.seek( iPtr1 ); e != nil { return e }
	if iByteSize, e = p.readI64(); e != nil { return e }
	iPtr2 = p.fmap.available( iByteSize )
	p.copyBytes( iPtr1, iPtr2, iByteSize )
	p.fmap.use( iPtr2, iByteSize )
	if e = p.setIBPtr( iPtr2 ); e != nil { return e }
	// iPtr1 & iPtr2 are now interchangeable
	// arbitrarily set iPtr1 to the lower value
	if iPtr2 < iPtr1 {
		temp := iPtr1
		iPtr1 = iPtr2
		iPtr2 = temp
	}
	p.seek( iPtr1 )
	var ibLen, id int64
	if ibLen, e = p.readI64(); e != nil { return e }
	if (ibLen - 8) % 20 != 0 {
		return os.NewError("in CommitAndPack, (ibLen - 8) % 20 != 0")
	}
	id2index := make (map[int64] int, (ibLen - 8) / 20 )
	for i := 0; i < p.ids.Size(); i++ {
		p.seek( iPtr1 + 8 + (20 * int64(i)) )
		if id, e = p.readI64(); e != nil { return e }
		id2index[ id ] = i
	}
	// 2: for each record in front of the index and after free space
	x, _ := p.fmap.borders.Min() // should be 0
	for ; p.fmap.states[ x ] != 0x00; x,_ = p.fmap.borders.Ceil( x+1 ) {}
		// x now points at the first free space
	y,_ := p.locs.Min()
	for ; y < x && y != 0; y, _ = p.locs.Ceil( y+1 ) {
	}
		// y should now point to next record after x (or 0)
	var yl int64
	var ny int64
	for y < iPtr1 && y != 0 {
		if e = p.seek( y ); e != nil { return e }
		if yl, e = p.readI64(); e != nil { return e }
		// A: if there's room below
		if y - x > yl {
			// a: copy the data flush
			ny = x
		// A: else
		} else {
			// a: copy the data elsewhere
			ny = p.fmap.available( yl )
		}
		p.fmap.use( ny, yl )
		if e = p.copyBytes( y, ny, yl ); e != nil { return e }
		// B: update inactive index
		iOffset := int64(id2index[ p.loc2id[ y ] ] * 20 + 16)
		var curPtr, otherPtr int64
		if curPtr, e = p.getIBPtr(); e != nil { return e }
		if curPtr == iPtr1 {
			otherPtr = iPtr2
		} else {
			otherPtr = iPtr1
		}
		if e = p.seek( otherPtr + iOffset ); e != nil { return e }
		if e = p.writeI64( ny ); e != nil { return e }
		// C: change active index
		if e = p.setIBPtr( otherPtr ); e != nil { return e }
		p.locs.Add( ny )
		p.locs.Remove( y )
		p.loc2id[ ny ] = p.loc2id[ y ]
		p.loc2id[ y ] = 0, false
		p.id2loc[ p.loc2id[ ny ] ] = ny
		// D: update (now) inactive index
		if e = p.seek( curPtr + iOffset ); e != nil { return e }
		if e = p.writeI64( ny ); e != nil { return e }
		// E: remove original data (mark space free)
		p.fmap.free( y, yl )
		p.fmap.commit()
		// reset x
		x, _ = p.fmap.borders.Ceil( x )
		for ; p.fmap.states[x] != 0x00; x,_ = p.fmap.borders.Ceil(x+1){}
		// x now points at the first free space
		// reset y
		y, _ = p.locs.Ceil( y )
		for ; y < x && y != 0; y, _ = p.locs.Ceil(y+1){}
	}
	// 3: point to back index
	if e = p.setIBPtr( iPtr2 ); e != nil { return e }
	// 4: free front index
	p.fmap.free( iPtr1, iByteSize )
	p.fmap.commit()
	// 4.a: reset x in case low index was below x
	x, _ = p.fmap.borders.Min() // should be 0
	for ; p.fmap.states[ x ] != 0x00; x,_ = p.fmap.borders.Ceil( x+1 ) {}
	// 5: copy back index flush
	iPtr1 = x
	p.fmap.use( iPtr1, iByteSize )
	if e = p.copyBytes( iPtr2, iPtr1, iByteSize ); e != nil { return e }
	p.fmap.commit()
	// 6: point to front index
	if e = p.setIBPtr( iPtr1 ); e != nil { return e }
	// 7: free back index
	p.fmap.free( iPtr2, iByteSize )
	p.fmap.commit()
	// 8: copy front index to end of file
	if iPtr2, e = p.fmap.borders.Max(); e != nil { return e }
	p.fmap.use( iPtr2, iByteSize )
	p.fmap.commit()
	if e = p.copyBytes( iPtr1, iPtr2, iByteSize ); e != nil { return e }
	// 9: point to back index
	if e = p.setIBPtr( iPtr2 ); e != nil { return e }
	// reset x
	x, _ = p.fmap.borders.Ceil( x )
	for ; p.fmap.states[ x ] != 0x00; x,_ = p.fmap.borders.Ceil(x+1){}
	// x now points at the first free space
	// reset y
	y, _ = p.locs.Ceil( y )
	for ; y < x && y != 0; y, _ = p.locs.Ceil(y+1){}
	// 10: for each stored struct remaining
	for y != 0 {
		if e = p.seek( y ); e != nil { return e }
		if yl, e = p.readI64(); e != nil { return e }
		// A: if there's room below
		if y - x > yl {
			// a: copy the data flush
			ny = x
		// A: else
		} else {
			// a: copy the data elsewhere
			ny = p.fmap.available( yl )
		}
		p.fmap.use( ny, yl )
		if e = p.copyBytes( y, ny, yl ); e != nil { return e }
		// B: update inactive index
		iOffset := int64(id2index[ p.loc2id[ y ] ] * 20 + 16)
		var curPtr, otherPtr int64
		if curPtr, e = p.getIBPtr(); e != nil { return e }
		if curPtr == iPtr1 {
			otherPtr = iPtr2
		} else {
			otherPtr = iPtr1
		}
		if e = p.seek( otherPtr + iOffset ); e != nil { return e }
		if e = p.writeI64( ny ); e != nil { return e }
		// C: change active index
		if e = p.setIBPtr( otherPtr ); e != nil { return e }
		p.locs.Add( ny )
		p.locs.Remove( y )
		p.loc2id[ ny ] = p.loc2id[ y ]
		p.loc2id[ y ] = 0, false
		p.id2loc[ p.loc2id[ ny ] ] = ny
		// D: update (now) inactive index
		if e = p.seek( curPtr + iOffset ); e != nil { return e }
		if e = p.writeI64( ny ); e != nil { return e }
		// E: remove original data (mark space free)
		p.fmap.free( y, yl )
		p.fmap.commit()
		// reset x
		x, _ = p.fmap.borders.Ceil( x )
		for ; p.fmap.states[x] != 0x00; x,_ = p.fmap.borders.Ceil(x+1){}
		// x now points at the first free space
		// reset y
		y, _ = p.locs.Ceil( y )
		for ; y < x && y != 0; y, _ = p.locs.Ceil(y+1){}
	}
	// 11: point to front index
	if e = p.setIBPtr( iPtr1 ); e != nil { return e }
	// 12: remove back index
	p.fmap.free( iPtr2, iByteSize )
	p.fmap.commit()
	end,_ := p.fmap.borders.Max()
	if e = p.seek( end ); e != nil { return e }
	if f, ok := p.f.(*os.File); ok {
		if e = f.Truncate( end ); e != nil { return e }
	}
	p.committedIds = p.ids.Clone()
	p.committedLocs = p.locs.Clone()
	p.committedId2loc = cloneMap6464( p.id2loc )
	p.committedLoc2id = cloneMap6464( p.loc2id )
	p.committedId2typeHash = cloneMap6432( p.id2typeHash )
	p.committedTypeHash2ids = cloneSetMap( p.typeHash2ids )
	return nil
}
func (p *PBin) Rollback() {
	if ! p.changed { return }
	p.ids = p.committedIds.Clone()
	p.locs = p.committedLocs.Clone()
	p.id2loc = cloneMap6464( p.committedId2loc )
	p.loc2id = cloneMap6464( p.committedLoc2id )
	p.id2typeHash = cloneMap6432( p.committedId2typeHash )
	p.typeHash2ids = cloneSetMap( p.committedTypeHash2ids )
	p.fmap.rollback()
	p.changed = false
}
func (p *PBin) Commit() (e os.Error) {
	if ! p.changed { return nil }
	// find space for new index block
	nl := int64(p.ids.Size() * 20 + 8)
	nloc := p.fmap.available( nl )
	// get old index block info
	var l, loc int64
	if loc, e = p.getIBPtr(); e != nil { return e }
	if e = p.seek( loc ); e != nil { return e }
	if l, e = p.readI64(); e != nil { return e }

	// reserve what we're about to write to
	p.fmap.use( nloc, nl )
	if e = p.seek( nloc ); e != nil { return e }
	if e = p.writeI64( nl ); e != nil { return e }
	// write out the new indexes
	ids := p.ids.Values()
	for i := 0; i < len(ids); i++ {
		if e = p.writeI64( ids[ i ] ); e != nil { return e }
		if e = p.writeI64( p.id2loc[ ids[ i ] ] ); e != nil { return e }
		if e = p.writeI32( p.id2typeHash[ ids[ i ] ] ); e != nil { return e }
	}
	// switch the offset
	if e = p.setIBPtr( nloc ); e != nil { return e }
	// free the old space
	p.fmap.free( loc, l )
	p.committedIds = p.ids.Clone()
	p.committedLocs = p.locs.Clone()
	p.committedId2loc = cloneMap6464( p.id2loc )
	p.committedLoc2id = cloneMap6464( p.loc2id )
	p.committedId2typeHash = cloneMap6432( p.id2typeHash )
	p.committedTypeHash2ids = cloneSetMap( p.typeHash2ids )
	p.fmap.commit()
	p.changed = false
	return nil
}
func (p *PBin) Committed() bool {
	return !p.changed
}
func (p *PBin) Close() os.Error {
	if c, ok := p.f.(io.Closer); ok {
		return c.Close()
	}
	return nil
}
type flusher interface {
	Flush() os.Error
}
func (p *PBin) Flush() os.Error {
	if b, ok := p.f.(flusher); ok {
		return b.Flush()
	}
	// if it's not buffered, flush isn't needed... 
	return nil
}

func (p *PBin) seek( i int64 ) os.Error {
	_, e := p.f.Seek( i, 0 )
	return e
}
func (p *PBin) writeI32( i int32 ) os.Error {
	n, e := p.f.Write( I32ToB( i ) )
	if n != 4 && e == nil {
		return os.NewError( "writeI32 did not write 4 bytes" )
	}
	return e
}
func (p *PBin) writeI64( i int64 ) os.Error {
	n, e := p.f.Write( I64ToB( i ) )
	if n != 8 && e == nil {
		return os.NewError( "writeI64 did not write 8 bytes" )
	}
	return e
}
func (p *PBin) readI64() (i int64, e os.Error) {
	bs := make( []byte, 8 )
	_, e = p.f.Read( bs )
	i = BToI64( bs )
	return i, e
}
func IToB( i int ) (bs []byte) {
	switch strconv.IntSize {
	case 32 :
		bs = make( []byte, 4 )
		bs[ 0 ] = byte(i >> 24)
		bs[ 1 ] = byte(i >> 16)
		bs[ 2 ] = byte(i >> 8)
		bs[ 3 ] = byte(i)
	case 64 :
		bs = make( []byte, 8 )
		bs[ 0 ] = byte(i >> 56)
		bs[ 1 ] = byte(i >> 48)
		bs[ 2 ] = byte(i >> 40)
		bs[ 3 ] = byte(i >> 32)
		bs[ 4 ] = byte(i >> 24)
		bs[ 5 ] = byte(i >> 16)
		bs[ 6 ] = byte(i >> 8)
		bs[ 7 ] = byte(i)
	}
	return bs
}
func I32ToB( i int32 ) (bs []byte) {
	bs = make( []byte, 4 )
	bs[ 0 ] = byte(i >> 24)
	bs[ 1 ] = byte(i >> 16)
	bs[ 2 ] = byte(i >> 8)
	bs[ 3 ] = byte(i)
	return bs
}
func I64ToB( i int64 ) (bs []byte) {
	bs = make( []byte, 8 )
	bs[ 0 ] = byte(i >> 56)
	bs[ 1 ] = byte(i >> 48)
	bs[ 2 ] = byte(i >> 40)
	bs[ 3 ] = byte(i >> 32)
	bs[ 4 ] = byte(i >> 24)
	bs[ 5 ] = byte(i >> 16)
	bs[ 6 ] = byte(i >> 8)
	bs[ 7 ] = byte(i)
	return bs
}
func BToI32( bs []byte ) int32 {
	return int32(bs[0])<<24 | int32(bs[1])<<16 | int32(bs[2])<<8 | int32(bs[3])
}
func BToI( bs []byte ) int {
	switch strconv.IntSize {
	case 32 :
		return int(bs[0])<<24 | int(bs[1])<<16 | int(bs[2])<<8 | int(bs[3])
	case 64 :
		return int(bs[0])<<56 | int(bs[1])<<48 | int(bs[2])<<40 | int(bs[3])<<32 | int(bs[4])<<24 | int(bs[5])<<16 | int(bs[6])<<8 | int(bs[7])
	}
	return 0
}
func BToI64( bs []byte ) int64 {
	return int64(bs[0])<<56 | int64(bs[1])<<48 | int64(bs[2])<<40 | int64(bs[3])<<32 | int64(bs[4])<<24 | int64(bs[5])<<16 | int64(bs[6])<<8 | int64(bs[7])
}
func (p *PBin) copyBytes( from int64, to int64, size int64 ) (e os.Error) {
	if size != int64( int( size ) ) {
		return os.NewError("size > int not yet supported")
	}
	buff := make( []byte, int(size) )
	if e = p.seek( from ); e != nil { return e }
	if _, e = p.f.Read( buff ); e != nil { return e }
	if e = p.seek( to ); e != nil { return e }
	if _, e = p.f.Write( buff ); e != nil { return e }
	return nil
}

func (p *PBin) Delete( id int64 ) (e os.Error) {
	if ! p.ids.Contains( id ) { return nil }
	loc := p.id2loc[ id ]
	if e = p.seek( loc ); e != nil { return e }
	var length int64
	if length, e = p.readI64(); e != nil { return e }
	p.fmap.free( loc, length )
	p.ids.Remove( id )
	p.locs.Remove( loc )
	p.loc2id[ loc ] = 0, false
	p.id2loc[ id ] = 0, false
	p.typeHash2ids[ p.id2typeHash[ id ] ].Remove( id )
	if p.typeHash2ids[ p.id2typeHash[ id ] ].Size() == 0 {
		p.typeHash2ids[ p.id2typeHash[ id ] ] = nil, false
	}
	p.id2typeHash[ id ] = 0, false
	p.changed = true
	return nil
}
// this will store a Persistable struct in the PBin.  If there
// is not item with the Id of the passed persistable, it will be
// created.  If there is, it will be replaced.
func (p *PBin) Store( s Persistable ) (e os.Error) {
	id := s.Id()
	if p.ids.Contains( id ) {
		if e = p.Delete( id ); e != nil { return e }
	}
	bs := s.Write()
	length := int64(len( bs ) + 8)
	loc := p.fmap.available( length )
	p.fmap.use( loc, length )
	if e = p.seek( loc ); e != nil { return e }
	if e = p.writeI64( length ); e != nil { return e }
	var n int
	if n, e = p.f.Write( bs ); e != nil { return e }
	if n != len( bs ) {
		return os.NewError( "Store failed to write all bytes.")
	}
	p.ids.Add( id )
	p.locs.Add( loc )
	p.id2loc[ id ] = loc
	p.loc2id[ loc ] = id
	p.id2typeHash[ id ] = s.TypeHash()
	if p.typeHash2ids[ s.TypeHash() ] == nil {
		p.typeHash2ids[ s.TypeHash() ] = NewSI64Set( 1 )
	}
	p.typeHash2ids[ s.TypeHash() ].Add( id )
	p.changed = true
	return nil
}
func (p *PBin) Load( id int64, t Persistable ) (e os.Error) {
	if ! p.ids.Contains( id ) {
		return os.NewError( "Load called with unrecorded id.")
	}
	if t.TypeHash() != p.id2typeHash[ id ] {
		return os.NewError( "Load called with incompatable type.")
	}
	t.SetId( id )
	var loc, length int64
	loc = p.id2loc[ id ]
	if e = p.seek( loc ); e != nil { return e }
	if length, e = p.readI64(); e != nil { return e }
	bs := make( []byte, length-8 )
	var c, r int
	c = 0
	for c < len( bs ) {
		if r, e = p.f.Read( bs ); e != nil { return e }
		c += r
	}
	if e = t.Read( bs ); e != nil { return e }
	return nil
}
func (p *PBin) Retrieve( s Persistable ) os.Error {
	return p.Load( s.Id(), s )
}

//func (p *PBin) GetReport() string {
//}

// okay, the idea is that this will keep track of the bytes that
// are used, and the bytes that are free.  This is a bit less than
// trivial, because we need to keep track of things that are used
// currently, and things that are currently deleted, but still
// stored and need to be kept around pending possible rollback
type byteMap struct {
	// the set of all internal free space sizes
	fsSizes *SI64Set
	// for each size in fsSizes, a mapping to the set of
	// all locations of free space of that size.
	fsMap map[int64] *SI64Set
	// a set of all borders between states
	// and one border at the end
	borders *SI64Set
	// a set of all floor borders changed since last commit
	// so it should be a collection of all borders s.t.
	// states[ border ] == 0x01 || states[ border ] == 0x03
	changedFloors *SI64Set
	// the keys are the borders (from borders set)
	// the values are the state of the bytes between this 
	// border and the next.
	// 0x00 = free, 0x01 = free on commit,
	// 0x02 = used, 0x03 = free on rollback
	states map[int64] byte
}
func newByteMap() (m *byteMap) {
	m = new( byteMap )
	m.fsSizes = NewSI64Set( 10 )
	m.fsMap = make( map[int64] *SI64Set )
	m.borders = NewSI64Set( 10 )
	m.changedFloors = NewSI64Set( 10 )
	m.states = make( map[int64] byte )
	m.borders.Add( 0 )
	m.states[ 0 ] = 0x00
	return
}
// addFS manipulates fsSizes and fsMap
func (m *byteMap) addFS( size int64, loc int64 ) {
	if m.fsSizes.Add( size ) {
		m.fsMap[ size ] = NewSI64Set( 1 )
	}
	m.fsMap[ size ].Add( loc )
}
// removeFS manipulates fsSizes and fsMap
func (m *byteMap) removeFS( size int64, loc int64 ) {
	if fm, ok := m.fsMap[ size ]; ok {
		fm.Remove( loc )
		if fm.Size() == 0 {
			m.fsMap[ size ] = nil, false
			m.fsSizes.Remove( size )
		}
	}
}
// changes the state of the specified span
func (m *byteMap) setState( loc int64, length int64, state byte ){
	// 0x00 = free, 0x01 = free on commit,
	// 0x02 = used, 0x03 = free on rollback
	if loc < 0 || length < 1 {
		fmt.Fprint(log, "byteMap.setState called with bad args\n")
		fmt.Fprint(log, "loc:",loc,", length:",length,", ")
		fmt.Fprint(log, "state:",state,"\n\n")
		m.Report()
		panic( os.NewError("byteMap.setState called with bad args.") )
	}
	if max, _ := m.borders.Max(); loc >= max {
		if state == 0x00 { return }
		m.borders.Add( loc + length )
		m.states[ loc + length ] = 0x00
		m.addFS( (loc+length) - max, max )
	}
	f, fe := m.borders.Floor( loc )
	if fe != nil {
		fmt.Fprint(log, "byteMap.setState got error finding floor\n")
		fmt.Fprint(log, "loc:",loc,", length:",length,", ")
		fmt.Fprint(log, "state:",state,"\n\n")
		m.Report()
		panic( os.NewError("setState doesn't know what to do.") )
	}
	c, ce := m.borders.Ceil( loc + 1 )
	if ce == nil && c < loc + length {
		fmt.Fprint(log, "byteMap.setState called with bad args\n")
		fmt.Fprint(log, "loc:",loc,", length:",length,", ")
		fmt.Fprint(log, "state:",state,"\n\n")
		m.Report()
		panic( os.NewError("setState called on non-contiguous span.") )
	}
	oldState := m.states[ f ]
	if oldState == state { return }
	switch {
	case f == loc && loc + length < c :
		m.states[ loc ] = state
		m.borders.Add(loc+length)
		m.states[ loc+length ] = oldState
		if oldState & 0x01 != 0x00 {
			m.changedFloors.Add( loc+length )
			if state & 0x01 == 0x00 {
				m.changedFloors.Remove( f )
			}
		}
		if oldState & 0x01 == 0x00 && state & 0x01 != 0x00 {
			m.changedFloors.Add( loc )
		}
		if oldState == 0x00 {
			m.removeFS( c-f, f )
			m.addFS( c-(loc+length), (loc+length) )
		}
		if state == 0x00 {
			m.addFS( length, loc )
		}
	case f == loc && loc + length == c :
		m.states[ loc ] = state
		if oldState & 0x01 != 0x00 && state & 0x01 == 0x00 {
			m.changedFloors.Remove( f )
		}
		if oldState & 0x01 == 0x00 && state & 0x01 != 0x00 {
			m.changedFloors.Add( loc )
		}
		if oldState == 0x00 {
			m.removeFS( c-f, f )
		}
		if state == 0x00 {
			m.addFS( length, loc )
		}
	case f < loc && loc + length < c :
		m.borders.Add(loc)
		m.states[ loc ] = state
		m.borders.Add(loc+length)
		m.states[ loc+length ] = oldState
		if oldState & 0x01 != 0x00 {
			m.changedFloors.Add( loc+length )
		}
		if state & 0x01 != 0x00 {
			m.changedFloors.Add( loc )
		}
		if oldState == 0x00 {
			m.removeFS( c-f, f )
			m.addFS( loc-f, f )
			m.addFS( c-(loc+length), (loc+length) )
		}
		if state == 0x00 {
			m.addFS( length, loc )
		}
	case f < loc && loc + length == c :
		m.borders.Add(loc)
		m.states[ loc ] = state
		if state & 0x01 != 0x00 {
			m.changedFloors.Add( loc )
		}
		if oldState == 0x00 {
			m.removeFS( c-f, f )
			m.addFS( loc-f, f )
		}
		if state == 0x00 {
			m.addFS( length, loc )
		}
	default :
		fmt.Fprint(log, "Unknown situation in byteMap.setState\n")
		fmt.Fprint(log, "loc:",loc,", length:",length,", ")
		fmt.Fprint(log, "state:",state,"\n\n")
		m.Report()
		panic( os.NewError("setState doesn't know what to do.") )
	}
	m.checkBorder( loc )
	m.checkBorder( loc+length )
	return
}
func (m *byteMap) checkBorder( border int64 ){
	max, _ := m.borders.Max()
	min, _ := m.borders.Min()
	if border == min { return }
	f, fe := m.borders.Floor( border-1 )
	if fe != nil {
		fmt.Fprint(log, "byteMap.checkBorder got error finding floor\n")
		fmt.Fprint(log, "border:",border,"\n")
		m.Report()
		panic( os.NewError("could not check border.") )
	}
	fState, bf := m.states[f]
	bState, bb := m.states[border]
	if !(bb && bf) {
		fmt.Fprint(log, "byteMap.checkBorder could not get states\n")
		fmt.Fprint(log, "border:",border,"\n")
		m.Report()
		panic( os.NewError("could not check border.") )
	}
	if fState != bState { return }
	m.borders.Remove( border )
	m.states[ border ] = 0x00, false
	if bState & 0x01 != 0x00 {
		m.changedFloors.Remove( border )
	}
	if border == max {
		m.removeFS( border-f, f )
		return
	}
	if bState == 0x00 {
		c, ce := m.borders.Ceil( border+1 )
		if ce != nil {
			s := "byteMap.checkBorder couldn't find ceil\n"
			fmt.Fprint(log, s)
			fmt.Fprint(log, "border:",border,"\n")
			m.Report()
			panic( os.NewError("could not check border.") )
		}
		m.removeFS( c-border, border )
		m.removeFS( border-f, f )
		m.addFS( c-f, f )
	}
	return
}
// this resolves the free on commit and free on rollback states
// because commit and rollback are identical except for which
// state moves in which direction.
func (m *byteMap) resolveStates( toFree byte, toUse byte ){
	fs := m.changedFloors.Values()
	for i := range fs {
		c, ce := m.borders.Ceil( fs[i]+1 )
		if ce != nil {
			s :="byteMap.resolveStates couldn't find ceil\n"
			fmt.Fprint(log, s)
			fmt.Fprint(log, "fs[i]:",fs[i],"\n")
			m.Report()
			panic( os.NewError("could not resolve state.") )
		}
		switch m.states[ fs[i] ] {
		case toFree :
			m.states [ fs [i] ] = 0x00
			m.checkBorder( fs[i] )
			m.checkBorder( c )
		case toUse :
			m.states [ fs [i] ] = 0x02
			m.checkBorder( fs[i] )
			m.checkBorder( c )
		default :
			fmt.Fprint(log," byteMap.resolveStates bad state\n" )
			fmt.Fprint(log, "fs[i]:",fs[i],"\n")
			m.Report()
			panic( os.NewError("could not resolve state.") )
		}
	}
	m.changedFloors = NewSI64Set( 10 )
	return
}
func (m *byteMap) commit(){
	m.resolveStates( 0x01, 0x03 )
}
func (m *byteMap) rollback(){
	m.resolveStates( 0x03, 0x01 )
}
func (m *byteMap) use( loc int64, length int64 ){
	if loc < 0 || length < 1 {
		fmt.Fprint(log," byteMap.use bad args\n" )
		fmt.Fprint(log, "loc:",loc,", length:",length,"\n")
		m.Report()
		panic( os.NewError("failed assertion.") )
	}
	f, _ := m.borders.Floor( loc )
	c, _ := m.borders.Ceil( loc + length )
	if t, _ := m.borders.Ceil( f+1 ); m.states[ f ] != 0x00 || t != c {
		fmt.Fprint(log," byteMap.use called on non-free span.\n" )
		fmt.Fprint(log, "loc:",loc,", length:",length,"\n")
		fmt.Fprint(log, "f=",f,", c=",c,".\n")
		m.Report()
		panic( os.NewError( "failed assertion." ) )
	}
	m.setState( loc, length, 0x03 )
}
func (m *byteMap) free( loc int64, length int64 ){
	if loc < 0 || length < 1 {
		fmt.Fprint(log," byteMap.free bad args\n" )
		fmt.Fprint(log, "loc:",loc,", length:",length,"\n")
		m.Report()
		panic( os.NewError("failed assertion.") )
	}
	f, _ := m.borders.Floor( loc )
	c, _ := m.borders.Ceil( loc + length )
	if t,_:=m.borders.Ceil(f+1);m.states[f]!=0x02&&m.states[f]!=0x03||t!=c{
		fmt.Fprint(log," byteMap.free called on non-used span.\n" )
		fmt.Fprint(log, "loc:",loc,", length:",length,"\n")
		fmt.Fprint(log, "f=",f,", c=",c,".\n")
		m.Report()
		panic( os.NewError( "failed assertion." ) )
	}
	if m.states[ f ] == 0x02 { // used
		m.setState( loc, length, 0x01 )
	} else { // m.states[ f ] == 0x03 (free at rollback)
		m.setState( loc, length, 0x00 )
	}
}
// will find a location where there is at least size bytes available
// (this call does not mark the bytes used, for that call use())
func (m *byteMap) available( size int64 ) (loc int64) {
	s, _ := m.fsSizes.Ceil( size )
	if s == 0 {
		loc, _ = m.borders.Max()
		return loc
	}
	loc, _ = m.fsMap[ s ].Min()
	return loc
}
func cloneMap6464( m map[int64] int64 ) (c map[int64] int64) {
	c = make( map[int64] int64, len( m ) )
	for k, v := range m {
		c[k] = v
	}
	return c
}
func cloneMap6432( m map[int64] int32 ) (c map[int64] int32) {
	c = make( map[int64] int32, len( m ) )
	for k, v := range m {
		c[k] = v
	}
	return c
}
func cloneSetMap( m map[int32] *SI64Set ) (c map[int32] *SI64Set) {
	c = make( map[int32] *SI64Set, len( m ) )
	for k, v := range m {
		c[k] = v.Clone()
	}
	return c
}
func (p *PBin) SmallReport(){
	if log == nil {
		log = os.Stderr
	}
	fmt.Fprintln(log,"ids:", p.ids.Values())
	fmt.Fprintln(log,"locs:", p.locs.Values())
	fmt.Fprintln(log,"id2loc:", m6464String(p.id2loc))
}
func (p *PBin) FullReport(){
	if log == nil {
		log = os.Stderr
	}
	fmt.Fprintln(log,"PBin Full Report:")
	fmt.Fprintln(log,"f: ", p.f)
	fmt.Fprintln(log,"changed:", p.changed)

	fmt.Fprintln(log,"ids:", p.ids.Values())
	fmt.Fprintln(log,"committedIds:", p.committedIds.Values())

	fmt.Fprintln(log,"locs:", p.locs.Values())
	fmt.Fprintln(log,"committedLocs:", p.committedLocs.Values())

	fmt.Fprintln(log,"id2loc:", m6464String(p.id2loc))
	fmt.Fprintln(log,"committedId2loc:", m6464String(p.committedId2loc))

	fmt.Fprintln(log,"loc2id:", m6464String(p.loc2id))
	fmt.Fprintln(log,"committedLoc2id:", m6464String(p.committedLoc2id))

	fmt.Fprintln(log,"id2typeHash:", m6432String(p.id2typeHash))
	fmt.Fprintln(log,"committedId2typeHash:", m6432String(p.committedId2typeHash))

	fmt.Fprintln(log,"typeHash2ids:")
	for k, v := range p.typeHash2ids {
		fmt.Fprint(log, "[", k, "]->{")
		va := v.Values()
		for i := range va {
			fmt.Fprint(log, va[i], ",")
		}
		fmt.Fprintln(log, "}")
	}
	fmt.Fprintln(log,"committedTypeHash2ids:")
	for k, v := range p.committedTypeHash2ids {
		fmt.Fprint(log, "[", k, "]->{")
		va := v.Values()
		for i := range va {
			fmt.Fprint(log, va[i], ",")
		}
		fmt.Fprintln(log, "}")
	}

	p.ByteMapReport()

}
func m6464String( m map[int64] int64 ) (s string) {
	ks := NewSI64Set( len(m) )
	for k, _ := range m {
		ks.Add( k )
	}
	s = "map["
	ka := ks.Values()
	for i := range ka {
		if i > 0 { s = s + " " }
		s = s + fmt.Sprint(ka[i],":",m[ka[i]] )
	}
	s = s + "]"
	return s
}
func m6432String( m map[int64] int32 ) (s string) {
	ks := NewSI64Set( len(m) )
	for k, _ := range m {
		ks.Add( k )
	}
	s = "map["
	ka := ks.Values()
	for i := range ka {
		if i > 0 { s = s + " " }
		s = s + fmt.Sprint(ka[i],":",m[ka[i]] )
	}
	s = s + "]"
	return s
}
func (p *PBin) ByteMapReport(){
	p.fmap.Report()
}
func (m *byteMap) Report(){
	if log == nil {
		log = os.Stderr
	}
	fmt.Fprintln(log,"byte map:")

	fmt.Fprintln(log,"free space sizes:", m.fsSizes.Values())
	// all locations of free space of that size.
	fmt.Fprintln(log,"free space size to locations map: ")
	fmt.Fprint(log, "[" )
	for k, v := range m.fsMap {
		fmt.Fprint(log, " ", k, ":", v.Values())
	}
	fmt.Fprintln(log, "]" )
	fmt.Fprintln(log,"borders:", m.borders.Values())
	fmt.Fprintln(log,"changed floors (states == 0x01||0x03:", m.changedFloors.Values())
	// 0x00 = free, 0x01 = free on commit,
	// 0x02 = used, 0x03 = free on rollback
	fmt.Fprint(log,"states: map[")

	// sort it first, so it's easier to read.
	ks := NewSI64Set( len(m.states) )
	for k, _ := range m.states {
		ks.Add( k )
	}
	ka := ks.Values()
	for i := range ka {
		fmt.Fprint(log, ka[i], ":")
		switch m.states[ka[i]] {
		case 0x00 :
			fmt.Fprint(log, "free ")
		case 0x01 :
			fmt.Fprint(log, "free@commit ")
		case 0x02 :
			fmt.Fprint(log, "used ")
		case 0x03 :
			fmt.Fprint(log, "free@rollback ")
		default :
			fmt.Fprint(log, "UNDEFINED!! ")
		}
	}
	fmt.Fprintln(log,"]")
}
