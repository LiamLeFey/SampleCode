package mudmap

import (
	"os"
	"fmt"
	"project/tools"
	"project/persistence"
)

// This is a type hash, used to ensure we get the right type from
// a PBin retrieve
var ROOM_HASH = tools.Hash( "project.mudclient.mudmap.Room" )

// the room object.  the main object for the mapper

type Room struct {
	id int64
	regionId int64
	shortDescId int64
	sDesc *string // a cached value, sometimes nil
	longDescId int64
	lDesc *string // a cached value, sometimes nil
	// map from command to destination room id
	// exit we've not yet traversed (unknown dest 0x7FFFFFFFFFFFFFFF)
	// hidden (non obvious) exits start with '.' like unix hidden files
	// triggers are hidden, and designated with a special format
	// string for trigger is ".<cost>.<trigger regexp>"
	// Wnen adding or removing exits, check if the status of entrance
	//   or exit room in Region has changed, and update Region if so.
	exits map[string] int64
	anomalous bool
	anomalyIds []int64
}
func newRoom( bin *persistence.PBin ) (r *Room, e os.Error) {
	r = new( Room )
	r.SetId( bin.MaxId() + 1 )
	r.exits = make( map[string] int64, 0 )
	r.anomalyIds = make( []int64, 0 )
	e = bin.Store( r )
	return r, e
}
func getRoom( id int64, bin *persistence.PBin ) (r *Room, e os.Error) {
	r = new( Room )
	e = bin.Load( id, r )
	return r, e
}
// max int64.  Something else will probably break long before we 
// get 9 quintillion Ids
const UNKN_DEST = 0x7FFFFFFFFFFFFFFF

func (r *Room) ShortDesc( bin *persistence.PBin ) (sd string) {
	if r.sDesc == nil {
		t, _ := getPString( r.shortDescId, bin )
		r.sDesc = &t.val
	}
	return *r.sDesc
}
func (r *Room) LongDesc( bin *persistence.PBin ) (ld string) {
	if r.lDesc == nil {
		t, _ := getPString( r.longDescId, bin )
		r.lDesc = &t.val
	}
	return *r.lDesc
}
func (r *Room) SetShortDesc(s *string, bin *persistence.PBin){
	tt := *s
	r.sDesc = &tt
	r.shortDescId, _ = getPStringId( r.sDesc, bin )
}
func (r *Room) SetLongDesc(s *string, bin *persistence.PBin) {
	tt := *s
	r.lDesc = &tt
	r.longDescId, _ = getPStringId( r.lDesc, bin )
}

// handle Persistable.  These are pretty straight forward
func (t *Room) TypeHash() (th int32){
	return ROOM_HASH;
}
func (r *Room) Id() (id int64) {
	return r.id
}
func (r *Room) SetId(id int64) {
	r.id = id
}
func (r *Room) Read( b []byte ) (e os.Error) {
	// for array index out of bounds panics, don't halt, just fail
	defer func(){
		if r.exits == nil {
			r.exits = make( map[string] int64, 0 )
		}
		if r.anomalyIds == nil {
			r.anomalyIds = make( []int64, 0 )
		}
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	x := 0
	r.id = persistence.BToI64( b[x:x+8] )
	x += 8
	r.regionId = persistence.BToI64( b[x:x+8] )
	x += 8
	r.shortDescId = persistence.BToI64( b[x:x+8] )
	x += 8
	r.longDescId = persistence.BToI64( b[x:x+8] )
	x += 8
	c := persistence.BToI( b[x:x+intLen] )
	r.exits = make( map[string] int64, c )
	x += intLen
	for i := 0; i < c; i++ {
		l := persistence.BToI( b[x:x+intLen] )
		x += intLen
		x += l
		r.exits[string( b[x-l:x] )] = persistence.BToI64( b[x:x+8] )
		x += 8
	}
	if b[x] == 0x00 {
		r.anomalous = false
	} else {
		r.anomalous = true
		x += 1
		c = persistence.BToI( b[x:x+intLen] )
		x += intLen
		r.anomalyIds = make( []int64, c )
		for i := 0; i < c; i++ {
			r.anomalyIds[ i ] = persistence.BToI64( b[x:x+8] )
			x += 8
		}
	}
	return e
}
func (r *Room) Write() (b []byte) {
	// just a guess on length
	l := 22*len(r.exits) + 34
	b = make( []byte, 0, l )
	b = append( b, persistence.I64ToB( r.id )... )
	b = append( b, persistence.I64ToB( r.regionId )... )
	b = append( b, persistence.I64ToB( r.shortDescId )... )
	b = append( b, persistence.I64ToB( r.longDescId )... )
	b = append( b, persistence.IToB( len( r.exits ) )... )
	for k, v := range r.exits {
		b = append( b, persistence.IToB( len(k) )... )
		b = append( b, []byte( k )... )
		b = append( b, persistence.I64ToB( v )... )
	}
	if r.anomalous {
		b = append( b, 0x01 )
		b = append( b, persistence.IToB( len( r.anomalyIds ) )... )
		for i := range r.anomalyIds {
			b = append( b, persistence.I64ToB(r.anomalyIds[i])... )
		}
	} else {
		b = append( b, 0x00 )
	}
	return b
}
