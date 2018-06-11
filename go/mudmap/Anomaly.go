package mudmap

import (
	"os"
	"fmt"
	"project/tools"
	"project/persistence"
)

var ANOMALY_HASH = tools.Hash( "project.mudclient.mudmap.Anomaly" )

type Anomaly struct {
	id int64
	atype uint8
	// points to the sub-struct that defines the Anomaly
	// this way we can define new things to stick on rooms without
	// changing Room.  All a room knows is that it has an Anomaly
	// (Tag, Note, MobileExit)
	subId int64
}

const TAG = 0x01
const NOTE = 0x02
const MOBILE_EXIT = 0x03

func newAnomaly( bin *persistence.PBin ) (a *Anomaly, e os.Error) {
	a = new( Anomaly )
	a.SetId( bin.MaxId() + 1 )
	e = bin.Store( a )
	return a, e
}
func (mm *MudMap) anomaly( id int64 ) (a *Anomaly, e os.Error) {
	a = new( Anomaly )
	e = mm.bin.Load( id, a )
	return a, e
}
// handle Persistable.  These are pretty straight forward
func (a *Anomaly) Id() (id int64) {
	return a.id
}
func (a *Anomaly) SetId(id int64) {
	a.id = id
}
func (t *Anomaly) TypeHash() (th int32){
	return ANOMALY_HASH;
}
func (a *Anomaly) Read( b []byte ) (e os.Error) {
	// in case of array index out of bounds, just fail, don't panic
	defer func(){
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	x := 0
	a.id = persistence.BToI64( b[x:x+8] )
	x += 8
	a.atype = b[x]
	x += 1
	a.subId = persistence.BToI64( b[x:x+8] )
	return e
}
func (a *Anomaly) Write() (b []byte) {
	l := 17
	b = make( []byte, 0, l )
	b = append( b, persistence.I64ToB( a.id )... )
	b = append( b, a.atype )
	b = append( b, persistence.I64ToB( a.subId )... )
	return b
}
