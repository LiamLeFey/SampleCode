package mudclient

import (
	"project/persistence"
	"project/mudmap"
	"container/vector"
	"os"
	"fmt"
	"strconv"
)

// This manages the storage for the mudclient.  It contains methods to
// load and store the macros, triggers, and list of named rooms.
// It also is the mudmap manager, encapsulating the caching of map
// objects such as rooms and regions.

type StoreMgr struct {
	dir string
	mapBin *persistence.PBin
	roomCache map[int64] mudmap.Room
	regionCache map[int64] mudmap.Region
	rf *mudmap.RoomFinder
	macros map[string] []string
	triggers *vector.Vector
	namedRooms map[string] int64
}
const PathSeparator = "/"
func NewStoreMgr( dirName string ) (sm *StoreMgr, e os.Error) {
	e = os.MkdirAll( dirName, 0700 )
	if e != nil { return nil, e }
	sm = new( StoreMgr )
	sm.dir = dirName
	sm.LoadMap()
	sm.LoadNamedRooms()
	sm.LoadMacros()
	sm.LoadTriggers()
	return sm, e
}
func (sm *StoreMgr) LoadMap() {
	tfn := sm.dir + PathSeparator + "map.dat"
	var e os.Error
	binf,e := os.OpenFile( tfn, os.O_RDWR | os.O_CREATE, 0600 )
	if e != nil {
		fmt.Println("Error:", e )
	}
	sm.mapBin, e = persistence.NewPBin( binf )
	if e != nil {
		fmt.Println("Error:", e )
	}
	sm.roomCache = make( map[int64] mudmap.Room, 1000 )
	sm.regionCache = make( map[int64] mudmap.Region, 100 )
	tfn = sm.dir + PathSeparator + "map.roomIndex.dat"
	rff, e := os.OpenFile( tfn, os.O_RDWR | os.O_CREATE, 0600 )
	if e != nil {
		fmt.Println("Error:", e )
	}
	stat, e := rff.Stat()
	if e != nil {
		fmt.Println("Error:", e )
	}
	bs := make( []byte, int(stat.Size) )
	n := 0
	for n < len( bs ) {
		r, e := rff.Read( bs[ n: ] )
		if e != nil { break }
		n += r
	}
	sm.rf = new( mudmap.RoomFinder )
	sm.rf.Read( bs )
	tfn = sm.dir + PathSeparator + "map.pstringIndex.dat"
	rff, e = os.OpenFile( tfn, os.O_RDWR | os.O_CREATE, 0600 )
	if e != nil {
		fmt.Println("Error:", e )
	}
	stat, e = rff.Stat()
	if e != nil {
		fmt.Println("Error:", e )
	}
	bs = make( []byte, int(stat.Size) )
	n = 0
	for n < len( bs ) {
		r, e := rff.Read( bs[ n: ] )
		if e != nil { break }
		n += r
	}
	psf := new( mudmap.PStringFinder )
	psf.Read( bs )
	mudmap.SetPStringFinder( sm.mapBin, psf )
}
func (sm *StoreMgr) LoadMacros() {
	sm.macros = make( map[ string ] []string )
}
func (sm *StoreMgr) LoadTriggers() {
	sm.triggers = new( vector.Vector )
}
func (sm *StoreMgr) LoadNamedRooms() {
	tfn := sm.dir + PathSeparator + "map.namedRooms.dat"
	rff,_ := os.OpenFile( tfn, os.O_RDWR | os.O_CREATE, 0600 )
	stat, _ := rff.Stat()
	bs := make( []byte, int(stat.Size) )
	n := 0
	for n < len( bs ) {
		r, e := rff.Read( bs[ n: ] )
		if e != nil { break }
		n += r
	}
	n = 0
	sm.namedRooms = make( map[string] int64 )
	for n < len(bs) {
		sl := persistence.BToI( bs[n:n+intLen] )
		n += intLen
		k := string( bs[n:n+sl] )
		n += sl
		v := persistence.BToI64( bs[n:n+8] )
		n += 8
		sm.namedRooms[k] = v
	}
}
var intLen = int(strconv.IntSize) / 8

