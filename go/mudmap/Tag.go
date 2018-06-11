package mudmap

import (
	"os"
	"fmt"
	"project/tools"
	"project/persistence"
)

var TAG_HASH = tools.Hash( "project.mudclient.mudmap.Tag" )

// A Tag is an anomaly which represents a multi room note.
// it contains a description, and a list of Rooms to which it
// applies.
// it is intended to have few tags.  Things like "Search Exhausted"
// or "Untraversed Exits"
type Tag struct {
	id int64
	// we keep this so the rooms can share an anomaly. 
	anomalyId int64
	desc string
	rooms *tools.SI64Set
}

func newTag( bin *persistence.PBin ) (t *Tag, e os.Error) {
	t = new( Tag )
	t.SetId( bin.MaxId() + 1 )
	t.rooms = tools.NewSI64Set( 0 )
	e = bin.Store( t )
	return t, e
}
func getTag( id int64, bin *persistence.PBin ) (t *Tag, e os.Error) {
	t = new( Tag )
	e = bin.Load( id, t )
	return t, e
}
// handle Persistable.  These are pretty straight forward
func (t *Tag) TypeHash() (th int32){
	return TAG_HASH;
}
func (t *Tag) Id() (id int64) {
	return t.id
}
func (t *Tag) SetId(id int64) {
	t.id = id
}
func (t *Tag) Read( b []byte ) (e os.Error) {
	// in case of array index out of bounds, just fail, don't panic
	defer func(){
		if t.rooms == nil {
			t.rooms = tools.NewSI64Set( 0 )
		}
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	x := 0
	t.id = persistence.BToI64( b[x:x+8] )
	x += 8
	t.anomalyId = persistence.BToI64( b[x:x+8] )
	x += 8
	l := persistence.BToI( b[x:x+intLen] )
	x += intLen
	t.desc = string( b[x:x+l] )
	x += l
	l = persistence.BToI( b[x:x+intLen] )
	x += intLen
	t.rooms = tools.NewSI64Set( l )
	for i := 0; i < l; i++ {
		t.rooms.Add( persistence.BToI64( b[x:x+8] ) )
		x += 8
	}
	return e
}
func (t *Tag) Write() (b []byte) {
	l := 16 + 2*intLen + len( t.desc ) + 8*t.rooms.Size()
	b = make( []byte, 0, l )
	b = append( b, persistence.I64ToB( t.id )... )
	b = append( b, persistence.I64ToB( t.anomalyId )... )
	b = append( b, persistence.IToB( len(t.desc) )... )
	b = append( b, []byte( t.desc )... )
	rs := t.rooms.Values()
	b = append( b, persistence.IToB( len(rs) )... )
	for i := range rs {
		b = append( b, persistence.I64ToB( rs[ i ] )... )
	}
	return b
}

