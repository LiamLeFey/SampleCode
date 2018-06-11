package mudmap

import (
	"os"
	"fmt"
	"project/tools"
	"project/persistence"
)

// This is a type hash, used to ensure we get the right type from
// a PBin retrieve
var NAMED_ROOM_HASH = tools.Hash( "project.mudclient.mudmap.NamedRoom" )

type NamedRoom struct {
	id int64
	roomId int64
	name string
}

// handle Persistable.  These are pretty straight forward
func (nr *NamedRoom) TypeHash() (th int32){
	return NAMED_ROOM_HASH;
}
func (nr *NamedRoom) Id() (id int64) {
	return nr.id
}
func (nr *NamedRoom) SetId(id int64) {
	nr.id = id
}
func (nr *NamedRoom) Read( b []byte ) (e os.Error) {
	// in case of array index out of bounds, just fail, don't panic
	defer func(){
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	x := 0
	nr.id = persistence.BToI64( b[x:x+8] )
	x += 8
	nr.roomId = persistence.BToI64( b[x:x+8] )
	x += 8
	l := persistence.BToI( b[x:x+intLen] )
	x += intLen
	nr.name = string( b[x:x+l] )
	x += l

	return e
}
func (nr *NamedRoom) Write() (b []byte) {
	l := 16 + intLen + len( nr.name )
	b = make( []byte, 0, l )
	b = append( b, persistence.I64ToB( nr.id )... )
	b = append( b, persistence.I64ToB( nr.roomId )... )
	b = append( b, persistence.IToB( len(nr.name) )... )
	b = append( b, []byte( nr.name )... )
	return b
}
