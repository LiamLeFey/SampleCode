package mudmap

import (
	"fmt"
	"os"
	"strings"
	"project/tools"
	"project/persistence"
)

// the RoomFinder is a way to find rooms given easily visible
// information about them.  Notably, the short description, 
// long description, and list of obvious exits.

// it is meant as a tool for the user to narrow down/answer the "where am I?"
// question.

type RoomFinder struct {
	regRoomIds map[ int64 ] *tools.SI64Set
	sdRoomIds map[ int64 ] *tools.SI64Set
	ldRoomIds map[ int64 ] *tools.SI64Set
	elRoomIds map[ int32 ] *tools.SI64Set
}

// this should only be done once the room is "complete"
func (rf *RoomFinder) RegisterRoom( r *Room ) {
	if rf.regRoomIds == nil {
		rf.regRoomIds = make( map[ int64 ] *tools.SI64Set, 1 )
	}
	if rf.sdRoomIds == nil {
		rf.sdRoomIds = make( map[ int64 ] *tools.SI64Set, 1 )
	}
	if rf.ldRoomIds == nil {
		rf.ldRoomIds = make( map[ int64 ] *tools.SI64Set, 1 )
	}
	if rf.elRoomIds == nil {
		rf.elRoomIds = make( map[ int32 ] *tools.SI64Set, 1 )
	}
	if rf.regRoomIds[ r.regionId ] == nil {
		rf.regRoomIds[ r.regionId ] = tools.NewSI64Set( 1 )
	}
	rf.regRoomIds[ r.regionId ].Add( r.id )
	if rf.sdRoomIds[ r.shortDescId ] == nil {
		rf.sdRoomIds[ r.shortDescId ] = tools.NewSI64Set( 1 )
	}
	rf.sdRoomIds[ r.shortDescId ].Add( r.id )
	if rf.ldRoomIds[ r.longDescId ] == nil {
		rf.ldRoomIds[ r.longDescId ] = tools.NewSI64Set( 1 )
	}
	rf.ldRoomIds[ r.longDescId ].Add( r.id )
	es := make( []string, len(r.exits) )
	i := 0
	for k, _ := range r.exits {
		es[i] = k
		i++
	}
	h := hashExits( es )
	if rf.elRoomIds[ h ] == nil {
		rf.elRoomIds[ h ] = tools.NewSI64Set( 1 )
	}
	rf.elRoomIds[ h ].Add( r.id )
	return
}
// this removes the room from the index.  If the data have changed
// it doesn't scan all of the entries, it just fails to remove it.
func (rf *RoomFinder) RemoveRoom( r *Room ) {
	if rf.regRoomIds == nil {
		rf.regRoomIds = make( map[ int64 ] *tools.SI64Set, 1 )
	}
	if rf.sdRoomIds == nil {
		rf.sdRoomIds = make( map[ int64 ] *tools.SI64Set, 1 )
	}
	if rf.ldRoomIds == nil {
		rf.ldRoomIds = make( map[ int64 ] *tools.SI64Set, 1 )
	}
	if rf.elRoomIds == nil {
		rf.elRoomIds = make( map[ int32 ] *tools.SI64Set, 1 )
	}

	if rf.regRoomIds[ r.regionId ] != nil {
		rf.regRoomIds[ r.regionId ].Remove( r.id )
	}
	if rf.sdRoomIds[ r.shortDescId ] != nil {
		rf.sdRoomIds[ r.shortDescId ].Remove( r.id )
	}
	if rf.ldRoomIds[ r.longDescId ] != nil {
		rf.ldRoomIds[ r.longDescId ].Remove( r.id )
	}
	es := make( []string, len(r.exits) )
	i := 0
	for k, _ := range r.exits {
		es[i] = k
		i++
	}
	h := hashExits( es )
	if rf.elRoomIds[ h ] != nil {
		rf.elRoomIds[ h ].Remove( r.id )
	}
	return
}
// this strips the hidden exits and returns the hash of the rest.
// because we use this just on obvious exits.
func hashExits( es []string ) int32 {
	oes := make( []string, 0, len( es ) )
	for i := range es {
		if ! strings.HasPrefix( es[i], "." ) {
			oes = append( oes, es[i] )
		}
	}
	rv := tools.HashA( oes )
	return rv

}
// to ignore r s or l, set them to negative. to ignore es set it nil
func (rf *RoomFinder) Ids( r, s, l int64, es []string ) (ids *tools.SI64Set) {
	if rf.regRoomIds == nil {
		rf.regRoomIds = make( map[int64] *tools.SI64Set, 0 )
	}
	if rf.sdRoomIds == nil {
		rf.sdRoomIds = make( map[int64] *tools.SI64Set, 0 )
	}
	if rf.ldRoomIds == nil {
		rf.ldRoomIds = make( map[int64] *tools.SI64Set, 0 )
	}
	if rf.elRoomIds == nil {
		rf.elRoomIds = make( map[int32] *tools.SI64Set, 0 )
	}
	// if any non-ignored item matches nothing, return nothing
	if r > 0 && rf.regRoomIds[ r ] == nil { return tools.NewSI64Set( 0 ) }
	if l > 0 && rf.ldRoomIds[ l ] == nil { return tools.NewSI64Set( 0 ) }
	if s > 0 && rf.sdRoomIds[ s ] == nil { return tools.NewSI64Set( 0 ) }
	if es != nil && rf.elRoomIds[ hashExits(es) ] == nil {
		return tools.NewSI64Set( 0 )
	}
	var idSet *tools.SI64Set
	switch{
	case r > 0 :
		idSet = rf.regRoomIds[ r ]
		if l > 0 {
			idSet = idSet.Intersection( rf.ldRoomIds[ s ] )
		}
		if s > 0 {
			idSet = idSet.Intersection( rf.sdRoomIds[ s ] )
		}
		if es != nil {
			idSet=idSet.Intersection(rf.elRoomIds[hashExits(es)])
		}
	case l > 0 :
		idSet = rf.ldRoomIds[ l ]
		if s > 0 {
			idSet = idSet.Intersection( rf.sdRoomIds[ s ] )
		}
		if es != nil {
			idSet=idSet.Intersection(rf.elRoomIds[hashExits(es)])
		}
	case s > 0 :
		idSet = rf.sdRoomIds[ s ]
		if es != nil {
			idSet=idSet.Intersection(rf.elRoomIds[hashExits(es)])
		}
	case es != nil :
		idSet = rf.elRoomIds[ hashExits( es ) ]
	default :
		idSet = tools.NewSI64Set( 0 )
	}
	return idSet.Clone()
}
// reads state from a byte array written by the Write() method
// so after rf1.Read( rf2.Write() ), rf1 is identical to rf2, while
// rf2 remains unchanged.
func (rf *RoomFinder) Read( b []byte ) (e os.Error) {

	// each index is set up like this:
	// [len]
	//   V
	// [key][len]->[val]...
	//   .
	//   .
	//   .
	defer func(){
		if rf.regRoomIds == nil {
			rf.regRoomIds = make( map[int64] *tools.SI64Set, 0 )
		}
		if rf.sdRoomIds == nil {
			rf.sdRoomIds = make( map[int64] *tools.SI64Set, 0 )
		}
		if rf.ldRoomIds == nil {
			rf.ldRoomIds = make( map[int64] *tools.SI64Set, 0 )
		}
		if rf.elRoomIds == nil {
			rf.elRoomIds = make( map[int32] *tools.SI64Set, 0 )
		}
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	// just in case it's empty:
	x := 0
	lk := persistence.BToI( b[x:x+intLen] )
	x += intLen
	rf.regRoomIds = make( map[int64] *tools.SI64Set, lk )
	for i := 0; i < lk; i++ {
		key := persistence.BToI64( b[x:x+8] )
		x += 8
		lv := persistence.BToI( b[x:x+intLen] )
		x += intLen
		rf.regRoomIds[ key ] = tools.NewSI64Set( lv )
		for j := 0; j < lv; j++ {
			rf.regRoomIds[ key ].Add( persistence.BToI64(b[x:x+8]) )
			x += 8
		}
	}
	lk = persistence.BToI( b[x:x+intLen] )
	x += intLen
	rf.sdRoomIds = make( map[int64] *tools.SI64Set, lk )
	for i := 0; i < lk; i++ {
		key := persistence.BToI64( b[x:x+8] )
		x += 8
		lv := persistence.BToI( b[x:x+intLen] )
		x += intLen
		rf.sdRoomIds[ key ] = tools.NewSI64Set( lv )
		for j := 0; j < lv; j++ {
			rf.sdRoomIds[ key ].Add( persistence.BToI64(b[x:x+8]) )
			x += 8
		}
	}
	lk = persistence.BToI( b[x:x+intLen] )
	x += intLen
	rf.ldRoomIds = make( map[int64] *tools.SI64Set, lk )
	for i := 0; i < lk; i++ {
		key := persistence.BToI64( b[x:x+8] )
		x += 8
		lv := persistence.BToI( b[x:x+intLen] )
		x += intLen
		rf.ldRoomIds[ key ] = tools.NewSI64Set( lv )
		for j := 0; j < lv; j++ {
			rf.ldRoomIds[ key ].Add( persistence.BToI64(b[x:x+8]) )
			x += 8
		}
	}
	lk = persistence.BToI( b[x:x+intLen] )
	x += intLen
	rf.elRoomIds = make( map[int32] *tools.SI64Set, lk )
	for i := 0; i < lk; i++ {
		key := persistence.BToI32( b[x:x+4] )
		x += 4
		lv := persistence.BToI( b[x:x+intLen] )
		x += intLen
		rf.elRoomIds[ key ] = tools.NewSI64Set( lv )
		for j := 0; j < lv; j++ {
			rf.elRoomIds[ key ].Add( persistence.BToI64(b[x:x+8]) )
			x += 8
		}
	}
	return e
}
// writes state to a byte array which can be read from the Read method
// so after rf1.Read( rf2.Write() ), rf1 is identical to rf2, while
// rf2 remains unchanged.
func (rf *RoomFinder) Write() (b []byte) {

	if rf.regRoomIds == nil {
		rf.regRoomIds = make( map[int64] *tools.SI64Set, 0 )
	}
	if rf.sdRoomIds == nil {
		rf.sdRoomIds = make( map[int64] *tools.SI64Set, 0 )
	}
	if rf.ldRoomIds == nil {
		rf.ldRoomIds = make( map[int64] *tools.SI64Set, 0 )
	}
	if rf.elRoomIds == nil {
		rf.elRoomIds = make( map[int32] *tools.SI64Set, 0 )
	}
	// each index (region, short, long, & exits) is set up like this:
	// [len]
	//   V
	// [key][len]->[val]...
	//   .
	//   .
	//   .
	b = make( []byte, 0, 1000 )
	lk := len( rf.regRoomIds )
	b = append( b, persistence.IToB( lk )... )
	for key, vals := range rf.regRoomIds {
		b = append( b, persistence.I64ToB( key )... )
		lv := vals.Size()
		b = append( b, persistence.IToB( lv )... )
		it := vals.Iterator()
		for it.HasNext() {
			v, _ := it.Next()
			b = append( b, persistence.I64ToB( v )... )
		}
	}
	lk = len( rf.sdRoomIds )
	b = append( b, persistence.IToB( lk )... )
	for key, vals := range rf.sdRoomIds {
		b = append( b, persistence.I64ToB( key )... )
		lv := vals.Size()
		b = append( b, persistence.IToB( lv )... )
		it := vals.Iterator()
		for it.HasNext() {
			v, _ := it.Next()
			b = append( b, persistence.I64ToB( v )... )
		}
	}
	lk = len( rf.ldRoomIds )
	b = append( b, persistence.IToB( lk )... )
	for key, vals := range rf.ldRoomIds {
		b = append( b, persistence.I64ToB( key )... )
		lv := vals.Size()
		b = append( b, persistence.IToB( lv )... )
		it := vals.Iterator()
		for it.HasNext() {
			v, _ := it.Next()
			b = append( b, persistence.I64ToB( v )... )
		}
	}
	lk = len( rf.elRoomIds )
	b = append( b, persistence.IToB( lk )... )
	for key, vals := range rf.elRoomIds {
		b = append( b, persistence.I32ToB( key )... )
		lv := vals.Size()
		b = append( b, persistence.IToB( lv )... )
		it := vals.Iterator()
		for it.HasNext() {
			v, _ := it.Next()
			b = append( b, persistence.I64ToB( v )... )
		}
	}
	return b
}
