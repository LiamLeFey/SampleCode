package mudmap

import (
	"os"
	"fmt"
	"strings"
	"sort"
	"regexp"
	"container/list"
	"strconv"
	"project/tools"
	"project/persistence"
)

// so this is the mud map.
// this has most of the logic of dealing with more than one type of 
// map object.  The specific logic of dealing with just one object
// type (ike Room) is in the specific object file.  This file works
// with the logic of dealing with multiple types of object.  The
// exception to this is Path, which deals with multiple types of
// object, but has it's own file, since there's so much logic
// involved.

//Also, this is where the objects are cached.
// Rooms are cached in a limited size cache, all of the Tags and
// Regions are kept in memory, and everything else is loaded from
// disk when needed.
// Tags and NamedRooms are also kept in memory.
type MudMap struct {
	bin *persistence.PBin
	strIndex *PStringFinder
	siFile *os.File
	rmIndex *RoomFinder
	riFile *os.File
	prevRoom int64
	currentRoom int64
	regionsById map[int64] *Region
	ridsByName map[string] int64
	alteredRegions map [int64] bool

	tagsById map[int64]*Tag
	tagsByName map[string]*Tag

	namedRooms map[string] *NamedRoom

	rmc *roomCache
}

func Create( dir *os.File ) (m *MudMap, e os.Error){
	dirfi, e := dir.Stat()
	if e != nil { return nil, e }
	var bfn, sfn, rfn string
	if strings.HasSuffix( dirfi.Name, "/" ) {
		bfn = dirfi.Name + "MapData.dat"
		sfn = dirfi.Name + "StringIndex.dat"
		rfn = dirfi.Name + "RoomIndex.dat"
	} else {
		bfn = dirfi.Name + "/MapData.dat"
		sfn = dirfi.Name + "/StringIndex.dat"
		rfn = dirfi.Name + "/RoomIndex.dat"
	}
	binf, e := os.OpenFile( bfn, os.O_RDWR | os.O_CREATE, 0640 )
	if e != nil { return nil, e }
	m = new( MudMap )
	m.siFile, e = os.OpenFile( sfn, os.O_RDWR | os.O_CREATE, 0640 )
	if e != nil { return nil, e }
	m.riFile, e = os.OpenFile( rfn, os.O_RDWR | os.O_CREATE, 0640 )
	if e != nil { return nil, e }
	m.bin, e = persistence.NewPBin( binf )
	if e != nil { return nil, e }
	e = m.loadIndexes()
	if e != nil { return nil, e }
	m.alteredRegions = make( map [int64] bool, 5 )
	return m, nil
}

func (mm *MudMap) Release() (e os.Error) {
	mm.bin.Rollback()
	e = mm.bin.Close()
	if e != nil { return e }
	e = mm.siFile.Close()
	if e != nil { return e }
	e = mm.riFile.Close()
	if e != nil { return e }
	mm.bin = nil
	mm.strIndex = nil
	mm.rmIndex = nil
	mm.regionsById = nil
	mm.ridsByName = nil
	mm.alteredRegions = nil
	mm.tagsById = nil
	mm.tagsByName = nil
	mm.namedRooms = nil
	return nil
}
func (mm *MudMap) Commit() (e os.Error) {
	e = mm.fixAlteredRegions()
	if e != nil { return e }
	e = mm.bin.Commit()
	if e != nil { return e }
	_, e = mm.siFile.Seek( 0, 0 )
	if e != nil { return e }
	_, e = mm.siFile.Write( mm.strIndex.Write() )
	if e != nil { return e }
	_, e = mm.riFile.Seek( 0, 0 )
	if e != nil { return e }
	_, e = mm.riFile.Write( mm.rmIndex.Write() )
	if e != nil { return e }
	return nil
}
func (mm *MudMap) CommitAndPack() (e os.Error) {
	e = mm.fixAlteredRegions()
	if e != nil { return e }
	e = mm.bin.CommitAndPack()
	if e != nil { return e }
	_, e = mm.siFile.Seek( 0, 0 )
	if e != nil { return e }
	_, e = mm.siFile.Write( mm.strIndex.Write() )
	if e != nil { return e }
	_, e = mm.riFile.Seek( 0, 0 )
	if e != nil { return e }
	_, e = mm.riFile.Write( mm.rmIndex.Write() )
	if e != nil { return e }
	return nil
}
func (mm *MudMap) loadIndexes() (e os.Error) {
	_, e = mm.siFile.Seek( 0, 0 )
	if e != nil { return e }
	mm.strIndex = new( PStringFinder )
	fi, e := mm.siFile.Stat()
	if e != nil { return e }
	bs := make( []byte, int(fi.Size) )
	n := 0
	for n < len( bs ) {
		r, e := mm.siFile.Read( bs[ n: ] )
		if e != nil {return e}
		n += r
	}
	e = mm.strIndex.Read( bs )
	// an error is returned if len(bs) == 0, but the PStringFinder is
	// still valid (though empty)
	if e != nil && len( bs ) > 0 { return e }
	setPStringFinder( mm.bin, mm.strIndex )
	_, e = mm.riFile.Seek( 0, 0 )
	if e != nil { return e }
	mm.rmIndex = new( RoomFinder )
	fi, e = mm.riFile.Stat()
	if e != nil { return e }
	bs = make( []byte, int(fi.Size) )
	n = 0
	for n < len( bs ) {
		r, e := mm.riFile.Read( bs[ n: ] )
		if e != nil {return e}
		n += r
	}
	e = mm.rmIndex.Read( bs )
	// an error is returned if len(bs) == 0, but the RoomFinder is
	// still valid (though empty)
	if e != nil && len( bs ) > 0 { return e }
	ids := mm.bin.IdsOfType( REGION_HASH )
	mm.regionsById = make( map[int64] *Region, len( ids ) )
	mm.ridsByName = make( map[string] int64, len( ids ) )
	for i := range ids {
		r := new (Region)
		e = mm.bin.Load( ids[i], r )
		if e != nil { return e }
		mm.regionsById[ids[i]] = r
		mm.ridsByName[ mm.regionsById[ids[i]].name ] = ids[i]
	}
	ids = mm.bin.IdsOfType( TAG_HASH )
	mm.tagsById = make( map[int64]*Tag, len( ids ) )
	mm.tagsByName = make( map[string]*Tag, len( ids ) )
	for i := range ids {
		t := new (Tag)
		e = mm.bin.Load( ids[i], t )
		if e != nil { return e }
		mm.tagsById[t.id] = t
		mm.tagsByName[t.desc] = t
	}

	ids = mm.bin.IdsOfType( NAMED_ROOM_HASH )
	mm.namedRooms = make( map[ string ] *NamedRoom, len( ids ) )
	for i := range ids {
		nr := new (NamedRoom)
		e = mm.bin.Load( ids[i], nr )
		if e != nil{ return e }
		mm.namedRooms[ nr.name ] = nr
	}
	var oldSize int
	if mm.rmc != nil {
		oldSize = mm.rmc.size
	}else{
		oldSize = 1000
	}
	mm.rmc = new( roomCache )
	mm.rmc.size = oldSize
	mm.rmc.id2e = make( map[int64] *list.Element, mm.rmc.size )
	mm.rmc.lst = list.New()
	return nil
}
func (mm *MudMap) Rollback() (e os.Error) {
	mm.alteredRegions = make( map [int64] bool, 5 )
	mm.bin.Rollback()
	if e != nil { return e }
	// hmm, this is probably not very efficient.
	// ideally we'd store a static copy of all indexes? and clone them?
	// on second thought, this is probably fine.
	e = mm.loadIndexes()
	if e != nil { return e }
	return nil
}
func (mm *MudMap) fixAlteredRegions() (e os.Error) {
	eStr := "MudMap.fixAlteredRegions: "
	for k, v := range mm.alteredRegions {
		if v {
			e = mm.repathRegion( k )
			if e != nil {
				return os.NewError( eStr+e.String())
			}
		}
		mm.alteredRegions[ k ] = false, false
	}
	return nil
}

// regionList
// (name\(id\))+ || FAIL {reason}
func (mm *MudMap) RegionNames() ( []string ) {
	names := make( []string, len( mm.ridsByName ) )
	i := 0
	for k, _ := range mm.ridsByName {
		names[i] = k
		i++
	}
	sort.Strings( names )
	return names
}

// createNewRegion [name]
// regionId || FAIL {reason}
func (mm *MudMap) NewRegion( name string ) ( id int64, e os.Error ) {
	eStr := "MudMap.NewRegion name: "+name+", error: "
	if _, ok := mm.ridsByName[ name ]; ok {
		return -1, os.NewError(eStr+"Region already exists.")
	}
	r, e := newRegion( mm.bin )
	if e != nil { return -1, e }
	r.name = name
	e = mm.bin.Store( r )
	if e != nil { return -1, e }
	mm.regionsById[ r.id ] = r
	mm.ridsByName[ name ] = r.id
	return r.id, nil
}

// regionName [id]
// regionName || FAIL {reason}
func (mm *MudMap) RegionName( id int64 ) ( name string, e os.Error ) {
	eStr := fmt.Sprint("MudMap.RegionName id: ",id,", error: ")
	if r, ok := mm.regionsById[ id ]; ok {
		return r.name, nil
	}
	return "", os.NewError( eStr + "no region with id " )
}

// regionId [name]
// regionId || FAIL {reason}
func (mm *MudMap) RegionId( name string ) ( id int64, e os.Error ) {
	eStr := "MudMap.RegionId Name: "+name+", error: "
	if i, ok := mm.ridsByName[ name ]; ok {
		return i, nil
	}
	return -1, os.NewError( eStr+"no region with name." )
}

// renameRegion [regionId] [newName]
// SUCCESS Region {oldName} changed to {newName} || FAIL {reason}
func (mm *MudMap) RenameRegion( id int64, name string ) ( e os.Error ) {
	eStr := fmt.Sprint("MudMap.RenameRegion id: ",id,", name: "+name+", error: ")
	if _, ok := mm.ridsByName[ name ]; ok {
		return os.NewError(eStr+"Region \""+name+"\" already exists.")
	}
	r, ok := mm.regionsById[ id ]
	if !ok {
		return os.NewError( fmt.Sprint(eStr+"No region with id " , id ))
	}
	mm.ridsByName[ r.name ] = 0, false
	r.name = name
	mm.ridsByName[ r.name ] = id
	e = mm.bin.Store( r )
	if e != nil { return e }
	return nil
}

// deleteRegion [regionId]
// SUCCESS || FAIL {reason}
func (mm *MudMap) DeleteRegion( id int64 ) ( e os.Error ) {
	eStr := fmt.Sprint("MudMap.DeleteRegion id: ",id,", error: ")
	// make sure it's a region
	if _, ok := mm.regionsById[ id ]; !ok {
		return os.NewError( eStr+"No region with id." )
	}
	// ensure we aren't orphaning any Rooms
	set, e := mm.RoomsInRegion( id )
	if e != nil {
		return os.NewError( eStr+e.String() )
	}
	rids := set.Values()
	for i := range rids {
		mm.DeleteRoom( rids[i] )
	}
	e = mm.bin.Delete( id )
	if e != nil {
		return os.NewError( eStr+e.String() )
	}
	mm.ridsByName[ mm.regionsById[ id ].name ] = 0, false
	mm.regionsById[ id ] = nil, false
	mm.alteredRegions[ id ] = false, false
	return nil
}
func (mm *MudMap) RoomsInRegion( id int64 ) ( ids *tools.SI64Set, e os.Error ){
	eStr := fmt.Sprint("MudMap.RoomsInRegion id: ",id,", error: ")
	// make sure it's a region
	if _, ok := mm.regionsById[ id ]; !ok {
		return nil, os.NewError( eStr+"No region with id." )
	}
	return mm.rmIndex.Ids( id, -1, -1, nil ), nil
}
func (mm *MudMap) RegionOfRoom( id int64 ) ( reg string, e os.Error ){
	eStr := fmt.Sprint("MudMap.RegionOfRoom id: ",id,", error: ")
	r, e := mm.room( id )
	if e != nil { return "", os.NewError( eStr + e.String() ) }
	if r, ok := mm.regionsById[ r.regionId ]; ok {
		return r.name, nil
	}
	return "", os.NewError( eStr + "Room has bad regionId." )
}

// createNewRoom <regionId>
// roomId || FAIL {reason}
func (mm *MudMap) NewRoom( regionId int64 ) ( id int64, e os.Error ) {
	eStr := fmt.Sprint("MudMap.NewRoom regionId: ",regionId,", error: ")
	if _, ok := mm.regionsById[ regionId ]; !ok {
		return -1, os.NewError(eStr+"Invalid regionId")
	}
	r, e := newRoom( mm.bin )
	if e != nil { return -1, e }
	r.regionId = regionId
	mm.bin.Store( r )
	mm.rmIndex.RegisterRoom( r )
	// we don't add it yet.  Without exits, the room doesn't make a
	// difference to the region.
	//mm.alteredRegions[regionId] = true
	return r.id, e
}
// if used willy nilly, this will almost certainly screw things up.
// Unless you're very careful, you'll end up with invalid exits that
// used to lead here.
func (mm *MudMap) DeleteRoom( rmId int64 ) ( e os.Error ) {
	r, e := mm.room( rmId )
	if e != nil { return e }
	ts, e := mm.RoomTags( rmId )
	if e != nil { return e }
	for i := range ts {
		mm.tagsByName[ts[i]].rooms.Remove( rmId )
	}
	mm.rmIndex.RemoveRoom( r )
	mm.rmc.remove( rmId )
	mm.alteredRegions[ r.regionId ] = true
	// probably forgot something.  I'm not going to 
	// run through every room in the map to delete exits
	// leading here.
	mm.bin.Delete( r.id )
	return nil
}

// setCurrentRoom [roomId]
// SUCCESS || FAIL {reason}
func (mm *MudMap) SetCurrentRoom( id int64 ) ( e os.Error ) {
	eStr := fmt.Sprint("MudMap.SetCurrentRoom id: ",id,", error: ")
	if mm.bin.TypeOfId( id ) != ROOM_HASH {
		return os.NewError( eStr+" No Room with id." )
	}
	mm.prevRoom = mm.currentRoom
	mm.currentRoom = id
	return nil
}

// currentRoom
// roomid || FAIL {reason}
func (mm *MudMap) CurrentRoom() ( id int64, e os.Error ) {
	eStr := "MudMap.CurrentRoom, error: "
	if mm.currentRoom < 1 {
		return -1, os.NewError( eStr+"Current room not set." )
	}
	return mm.currentRoom, nil
}

// currentRoom
// roomid || FAIL {reason}
func (mm *MudMap) PreviousRoom() ( id int64, e os.Error ) {
	eStr := "MudMap.PreviousRoom, error: "
	if mm.prevRoom < 1 {
		return -1, os.NewError( eStr+"Previous room not set." )
	}
	return mm.prevRoom, nil
}

// this can end up orphaning PStrings, but there's not much we can
// do about that except scrub the bin once in a while, since we
// don't keep track of reference counts.
// setRoomShortDesc <roomId> [newShortDesc]
// SUCCESS || FAIL {reason}
func (mm *MudMap) SetRmSDesc( rmId int64, sDesc *string ) ( e os.Error ) {
	eStr := fmt.Sprint("MudMap.SetRmSDesc rmId: ",rmId,", sDesc: "+*sDesc+", error: ")
	r, e := mm.room( rmId )
	if e != nil { return os.NewError( fmt.Sprint(eStr , e )) }
	mm.rmIndex.RemoveRoom( r )
	r.SetShortDesc( sDesc, mm.bin )
	mm.bin.Store( r )
	mm.rmIndex.RegisterRoom( r )
	return nil
}

// roomShortDesc <roomId>
// shortDesc || FAIL {reason}
func (mm *MudMap) RmSDesc( rmId int64 ) ( sDesc string, e os.Error ) {
	eStr := fmt.Sprint("MudMap.RmSDesc rmId: ",rmId,", error: ")
	r, e := mm.room( rmId )
	if e != nil { return "", os.NewError( fmt.Sprint(eStr , e )) }
	return r.ShortDesc( mm.bin ), nil
}

// this can end up orphaning PStrings, but there's not much we can
// do about that except scrub the bin once in a while, since we
// don't keep track of reference counts.
// setRoomLongDesc <roomId> [newShortDesc]
// SUCCESS || FAIL {reason}
func (mm *MudMap) SetRmLDesc( rmId int64, lDesc *string ) ( e os.Error ) {
	eStr := fmt.Sprint("MudMap.SetRmLDesc rmId: ",rmId,", lDesc: "+*lDesc+", error: ")
	r, e := mm.room( rmId )
	if e != nil { return os.NewError( fmt.Sprint(eStr , e )) }
	mm.rmIndex.RemoveRoom( r )
	r.SetLongDesc( lDesc, mm.bin )
	mm.bin.Store( r )
	mm.rmIndex.RegisterRoom( r )
	return nil
}

// roomLongDesc <roomId>
// longDesc || FAIL {reason}
func (mm *MudMap) RmLDesc( rmId int64 ) ( lDesc string, e os.Error ) {
	eStr := fmt.Sprint("MudMap.RmLDesc rmId: ",rmId,", error: ")
	r, e := mm.room( rmId )
	if e != nil { return "", os.NewError( eStr + e.String() ) }
	return r.LongDesc( mm.bin ), nil
}

// whereami
// regionId
// shortdesc
// longdesc
// obviousexits
func (mm *MudMap) FindRmIds( regId int64, sDesc, lDesc *string, obvExits []string ) ( ids *tools.SI64Set ) {
	var sdId int64
	var ldId int64
	var b bool
	if sDesc == nil {
		sdId = -1
	} else {
		sdId, b = findPStringId( sDesc, mm.bin )
		if !b { return tools.NewSI64Set( 0 ) }
	}
	if lDesc == nil {
		ldId = -1
	} else {
		ldId, b = findPStringId( lDesc, mm.bin )
		if !b { return tools.NewSI64Set( 0 ) }
	}
	return mm.rmIndex.Ids( regId, sdId, ldId, obvExits )
}
// checks to see if the given room is still a valid region exit. removes
// it if not.
func (mm *MudMap) checkRegionExit( regId int64, rmId int64 ) ( e os.Error ) {
	eStr :=fmt.Sprint("MudMap.checkRegionExit regId: ",regId,", rmId: ",rmId," error: ")
	r, e := mm.room( rmId )
	if e != nil { return os.NewError( fmt.Sprint(eStr , e )) }
	reg := mm.regionsById[ regId ]
	if r.regionId == regId {
		mm.removeRegionExit( reg, rmId )
		return nil
	}
	idSet, e := mm.RoomsInRegion( regId )
	rmIds := idSet.Values()
	if e != nil { return os.NewError( fmt.Sprint(eStr , e )) }
	for i := range rmIds {
		r, e := mm.room( rmIds[i] )
		if e != nil { return os.NewError( fmt.Sprint(eStr , e )) }
		for _, j := range r.exits {
			if j == rmId || j == -rmId { return nil }
		}
	}
	mm.removeRegionExit( reg, rmId )
	return nil
}

func (mm *MudMap) ExitInfo( rmId int64, exitStr string ) ( dest int64, hidden, trigger bool, cost int, e os.Error ){
	if strings.HasPrefix( exitStr, "T:" ) { exitStr = exitStr[2:] }
	k, dest, b := mm.exitData( rmId, exitStr )
	if !b {
		e = os.NewError( "MudMap.ExitInfo error: could not find exit." )
		dest = UNKN_DEST
		return
	}
	_, dest, hidden, trigger, cost = splitExitInfo( k, dest )
	return
}
func splitExitInfo( key string, val int64 ) (cmd string, dest int64, hidden, trigger bool, cost int){
	dest = val
	cost = 1
	switch {
	case dest < 0 :
		trigger = true
		splits := strings.SplitN( key[1:], ".", 2 )
		cost, _ = strconv.Atoi( splits[ 0 ] )
		dest *= -1
		cmd = splits[ 1 ]
	case strings.HasPrefix( key, "." ) :
		hidden = true
		cmd = key[1:]
	default :
		cmd = key
	}
	return
}
func (mm *MudMap) exitData( rmId int64, regOrCmd string ) (k string, v int64, found bool) {
	r, e := mm.room( rmId )
	if e != nil { return "", 0, false }
	if v, b := r.exits[regOrCmd]; b { return regOrCmd, v, b }
	for k, v := range r.exits {
		if !strings.HasPrefix( k, "." ) {continue}
		if v < 0 {
			if regOrCmd == strings.SplitN( k[1:], ".", 2 )[1] {
				return k, v, true
			}
		}else{
			if regOrCmd == k[1:] {
				return k, v, true
			}
		}
	}
	return "", 0, false
}
func (mm *MudMap) Exits( rmId int64 ) (cmd []string, dest []int64, hid, trig []bool, cost []int, e os.Error) {
	r, e := mm.room( rmId )
	if e != nil {
		e = os.NewError( "MudMap.Exits error getting room"+ e.String())
		return
	}
	cmd = make( []string, len(r.exits) )
	dest = make( []int64, len(r.exits) )
	hid = make( []bool, len(r.exits) )
	trig = make( []bool, len(r.exits) )
	cost = make( []int, len(r.exits) )
	i := 0
	for k, v := range r.exits {
		cmd[i], dest[i], hid[i], trig[i], cost[i] = splitExitInfo(k, v)
		i++
	}
	return
}
func (mm *MudMap) DelExit( rmId int64, cmd string ) (e os.Error) {
	k, v, b := mm.exitData( rmId, cmd )
	if !b { return os.NewError( "MudMap.DelExit could not find exit" ) }
	rm, e := mm.room( rmId )
	if e != nil { return os.NewError( "MudMap.DelExit "+e.String() ) }
	mm.rmIndex.RemoveRoom( rm )
	rm.exits[ k ] = 0, false
	mm.bin.Store( rm )
	mm.rmIndex.RegisterRoom( rm )
	if v < 0 { v = -v }
	if v == UNKN_DEST {
		return
	}
	// check & deal with if it was a region exit
	r1 := rm.regionId
	rm2, e := mm.room( v )
	if e != nil { return os.NewError( "MudMap.DelExit "+e.String() ) }
	r2 := rm2.regionId
	if r1 != r2 {
		e = mm.checkRegionExit( r1, rm2.id )
		if e != nil {return os.NewError( "MudMap.DelExit "+e.String()) }
	} else {
		// if r1 != r2, no internal paths changed.  r1 is still valid
		// add region to modified
		mm.alteredRegions[ r1 ] = true
	}
	return nil
}
func (mm *MudMap) AddExit( rmId int64, cmd string, hidden, trigger bool ) os.Error {
	if _, _, b := mm.exitData( rmId, cmd ); b {
		return os.NewError( "MudMap.AddExit: Exit already exists." )
	}
	r, e := mm.room( rmId )
	if e != nil { return os.NewError( "MudMap.AddExit: "+e.String() ) }
	switch {
	case trigger :
		r.exits[ ".1."+cmd ] = -UNKN_DEST
	case hidden :
		r.exits[ "."+cmd ] = UNKN_DEST
	default :
		mm.rmIndex.RemoveRoom( r )
		r.exits[ cmd ] = UNKN_DEST
		mm.rmIndex.RegisterRoom( r )
	}
	return mm.bin.Store( r )
}
// set cost = 0 to leave it as is.
// set dest = 0 to leave it as is.
func (mm *MudMap) SetExitInfo( rmId int64, cmd string, dest int64, cost int ) os.Error {
	eStr := "MudMap.SetExitInfo: "
	var k string
	var v int64
	var b bool
	if k, v, b = mm.exitData( rmId, cmd ); !b {
		return os.NewError( eStr+"No such exit." )
	}
	oCmd, oDest, hid, trig, oCost := splitExitInfo( k, v )
	if oCmd != cmd {
		return os.NewError( eStr+"old and new cmd mismatch!" )
	}
	if dest == 0 {
		dest = oDest
	}
	if oDest != UNKN_DEST && oDest != dest {
		e := mm.DelExit( rmId, cmd )
		if e != nil { return os.NewError( eStr+e.String() ) }
		e = mm.AddExit( rmId, cmd, hid, trig )
		if e != nil { return os.NewError( eStr+e.String() ) }
	}
	if !trig && cost > 1 {
		return os.NewError( eStr+"Only triggers may have cost > 1." )
	}
	if cost < 0 {
		return os.NewError( eStr+"Cost may not be negative." )
	}
	r, e := mm.room( rmId )
	if e != nil {
		return os.NewError(eStr+"Room not found. "+e.String())
	}
	var dr *Room
	if dest != UNKN_DEST {
		dr, e = mm.room( dest )
		if e != nil {
			return os.NewError(eStr+"Dest not found. "+e.String())
		}
	}
	newVal := dest
	newKey := k
	switch{
	case trig && oCost != cost :
		// if changing cost, the key changes.
		r.exits[ k ] = 0, false
		newKey = fmt.Sprint(".",cost,".",cmd)
		newVal = -dest
	case trig && oCost == cost :
		newVal = -dest
	case hid :
	default :
	}
	r.exits[ newKey ] = newVal
	mm.bin.Store( r )
	if dest != UNKN_DEST {
		if dr.regionId != r.regionId {
			e = mm.addRegionExit(mm.regionsById[r.regionId],dest)
			if e != nil { return os.NewError(eStr+e.String()) }
			e=mm.addRegionEntrance(mm.regionsById[dr.regionId],dest)
			if e != nil { return os.NewError(eStr+e.String()) }
		} else {
			mm.alteredRegions[ r.regionId ] = true
		}
	}
	return nil
}
// getpath <originid> [destinationid]
// FULL {path} || PARTIAL {path} || FAIL {reason}
func (mm *MudMap) Path( rmId, destId int64 ) ( p *Path, e os.Error ) {
	return mm.path( rmId, destId )
}

// tags
// (tagdesc\(tagId\))* || FAIL {reason}
func (mm *MudMap) Tags() ( tags []string ) {
	tags = make( []string, len( mm.tagsByName ) )
	i := 0
	for k, _ := range mm.tagsByName {
		tags[i] = k
		i++
	}
	sort.Strings( tags )
	return tags
}

// RoomTags
// (tagdesc\(tagId\))* || FAIL {reason}
func (mm *MudMap) RoomTags( rmId int64 ) ( tags []string, e os.Error ) {
	r, e := mm.room( rmId )
	if !r.anomalous {
		return make( []string, 0 ), nil
	}
	tags = make( []string, 0, len( r.anomalyIds ) )
	for i := range r.anomalyIds {
		a, e := mm.anomaly( r.anomalyIds[ i ] )
		if e != nil { return nil, e }
		if a.atype == TAG {
			t := mm.tagsById[a.subId]
			tags = append( tags, t.desc )
			continue
		}
	}
	return tags, nil
}

func (mm *MudMap) CreateRoomTag( tag string ) ( e os.Error ) {
	var t *Tag
	if mm.tagsByName[tag] != nil {
		return os.NewError("Tag "+tag+" already exists!")
	}
	t, e = newTag( mm.bin )
	if e != nil { return e }
	t.desc = tag
	a, e := newAnomaly( mm.bin )
	if e != nil { return e }
	a.atype = TAG
	a.subId = t.id
	mm.bin.Store( a )
	t.anomalyId = a.id
	mm.bin.Store( t )
	mm.tagsById[ t.id ] = t
	mm.tagsByName[ tag ] = t
	return nil
}

// addTag <roomId> [tagId]
// SUCCESS || FAIL {reason}
func (mm *MudMap) AddRoomTag( rmId int64, tag string ) ( e os.Error ) {
	t := mm.tagsByName[ tag ]
	if t == nil {
		return os.NewError("Not a valid tag!")
	}
	if t.rooms.Contains( rmId ) { return nil }

	t.rooms.Add( rmId )
	mm.bin.Store( t )
	r, e := mm.room( rmId )

	aid := t.anomalyId
	r.anomalous = true
	if r.anomalyIds == nil {
		r.anomalyIds = make( []int64, 0, 1 )
	}
	r.anomalyIds = append( r.anomalyIds, aid )
	return mm.bin.Store( r )

}

// removeTag <roomId> [tagId]
// SUCCESS || FAIL {reason}
func (mm *MudMap) RemRoomTag( rmId int64, tag string ) ( e os.Error ) {
	t := mm.tagsByName[ tag ]
	if t == nil {
		return os.NewError("Not a valid tag!")
	}

	t.rooms.Remove( rmId )
	mm.bin.Store( t )
	aid := t.anomalyId
	r, e := mm.room( rmId )
	if e != nil { return e }
	if !r.anomalous { return nil }
	if r.anomalyIds == nil {
		r.anomalous = false
		mm.bin.Store( r )
		return nil
	}
	for i := range r.anomalyIds {
		if r.anomalyIds[i] == aid {
			r.anomalyIds[i] = r.anomalyIds[ len(r.anomalyIds)-1 ]
			r.anomalyIds = r.anomalyIds[:len(r.anomalyIds)-1]
			mm.bin.Store( r )
			return nil
		}
	}
	return nil
}

// taggedRooms [tagId]
// (roomId)* || FAIL {reason}
func (mm *MudMap) TaggedRms( tag string ) ( ids *tools.SI64Set, e os.Error ) {
	t := mm.tagsByName[ tag ]
	if t == nil {
		return nil, os.NewError("Not a valid tag!")
	}
	return t.rooms.Clone(), nil
}
func (mm *MudMap) DeleteTag( tag string ) ( e os.Error ) {
	t := mm.tagsByName[ tag ]
	if t == nil {
		return os.NewError("Not a valid tag!")
	}
	ids := t.rooms.Values()
	for i := range ids {
		mm.RemRoomTag( ids[i], tag )
	}
	return mm.bin.Delete( t.id )
}
func (mm *MudMap) NameRoom( rId int64, name string ) ( e os.Error ) {
	_, b := mm.namedRooms[ name ]
	if b { return os.NewError( "Name \""+name+"\" already in use." ) }
	nr := new ( NamedRoom )
	nr.SetId( mm.bin.MaxId() + 1 )
	nr.name = name
	nr.roomId = rId
	e = mm.bin.Store( nr )
	if e != nil { return e }
	mm.namedRooms[ name ] = nr
	return nil
}
func (mm *MudMap) NamedRoomId( name string ) ( id int64, e os.Error ) {
	nr, b := mm.namedRooms[ name ]
	if !b { return 0, os.NewError( "No room named \""+name+"\"." ) }
	return nr.roomId, nil
}
func (mm *MudMap) UnnameRoom( name string ) ( e os.Error ) {
	nr, b := mm.namedRooms[ name ]
	if !b { return os.NewError( "No room named \""+name+"\"." ) }
	mm.namedRooms[ name ] = nil, false
	return mm.bin.Delete( nr.id )
}
func (mm *MudMap) ListNamedRooms() ( names []string ) {
	names = make( []string, 0, len( mm.namedRooms ) )
	for k, _ := range mm.namedRooms {
		names = append( names, k )
	}
	sort.Strings( names )
	return names
}
func (mm *MudMap) SetRoomCacheSize( size int ) {
	mm.rmc.setSize( size )
}
func (mm *MudMap) RoomCacheSize() ( size int ) {
	return mm.rmc.size
}

// internal get room gets the cached room if available, otherwise hits
// the bin.
func (mm *MudMap) room( rId int64 ) ( r *Room, e os.Error ) {
	r = mm.rmc.room( rId )
	if r != nil { return r, nil }
	if mm.bin.TypeOfId( rId ) != ROOM_HASH {
		s:=fmt.Sprint("MudMap.room error: ",rId," is not a Room id." )
		return nil, os.NewError( s )
	}
	r = new ( Room )
	e = mm.bin.Load( rId, r )
	if e != nil { return nil, e }
	mm.rmc.add( r )
	return r, nil
}
// MARK need to add ability to put notes on rooms.

type roomCache struct {
	size int
	id2e map[int64] *list.Element
	lst *list.List
}
func (rc *roomCache) room( id int64 ) (r *Room) {
	e := rc.id2e[ id ]
	if e == nil { return nil }
	r = (e.Value).(*Room)
	rc.lst.MoveToFront( e )
	return r
}
func (rc *roomCache) add( r *Room ) {
	e := rc.lst.PushFront( r )
	rc.id2e[ r.id ] = e
	rc.trimSize()
}
func (rc *roomCache) remove( id int64 ) {
	e := rc.id2e[ id ]
	rc.lst.Remove( e )
	rc.id2e[ id ] = nil, false
}
func (rc *roomCache) trimSize() {
	for i := rc.lst.Len(); i > rc.size; i-- {
		e := rc.lst.Back()
		r := e.Value.(*Room)
		rc.id2e[r.id] = nil, false
		rc.lst.Remove( e )
	}
}
func (rc *roomCache) setSize( newSize int ) {
	rc.size = newSize
	rc.trimSize()
}

// beyond this point lies the cleaning crew.
func (mm *MudMap) CleanData() (e os.Error) {
	mm.bin.Commit()
	mm.rmIndex = new( RoomFinder )
	mm.strIndex = nil
	mm.regionsById = make( map[int64] *Region, len(mm.regionsById) )
	mm.ridsByName = nil
	mm.alteredRegions = nil
	mm.tagsById = nil
	mm.tagsByName = nil
	mm.namedRooms = make( map[string] *NamedRoom, len(mm.namedRooms) )
	oldRmcSize := mm.rmc.size
	mm.rmc = nil
	bfMap[ mm.bin ] = nil, false
	storedPStringIds := mm.bin.IdsOfType( PSTRING_HASH )
	duplicatePStrings := make(map[int64] bool, 10 )
	hashedPStringMap := make(map[int32] int64, 10 )
	referencedPStringIds := make(map[int64] bool, len(storedPStringIds) )
	ids := mm.bin.IdsOfType( REGION_HASH )
	for i := range ids {
		r := new( Region )
		e = mm.bin.Load( ids[i], r )
		if e != nil { return e }
		r.entrances = make( []int64, 0 )
		r.exits = make( []int64, 0 )
		r.paths = make( [][]*IPath, 0 )
		e = mm.bin.Store( r )
		if e != nil { return e }
		mm.regionsById[ ids[i] ] = r
	}
	ids = mm.bin.IdsOfType( ROOM_HASH )
	for i := range ids {
		rm := new( Room )
		e = mm.bin.Load( ids[i], rm )
		if e != nil { return e }
		if _, b := mm.regionsById[ rm.regionId ]; !b {
			mm.bin.Delete( rm.id )
			rm = nil
			continue
		}
		for k, v := range rm.exits {
			if v == UNKN_DEST || v == -UNKN_DEST { continue }
			if mm.bin.TypeOfId( v ) != ROOM_HASH {
				if strings.HasPrefix( k, "." ) {
					rm.exits[ k ] = 0, false
				} else {
					rm.exits[ k ] = UNKN_DEST
				}
			}
		}
		keys := make( []string, 0, len( rm.exits ) )
		for k, _ := range rm.exits {
			keys = append( keys, k )
		}
		for i := len(keys)-1; i >= 0; i-- {
			for j := i-1; j >= 0; j-- {
				// don't keep the long version of common alii
				switch {
				case keys[i]=="n" && keys[j]=="north" ||
					keys[j]=="n" && keys[i]=="north":
					rm.exits["north"] = 0, false
				case keys[i]=="s" && keys[j]=="south" ||
					keys[j]=="s" && keys[i]=="south":
					rm.exits["south"] = 0, false
				case keys[i]=="e" && keys[j]=="east" ||
					keys[j]=="e" && keys[i]=="east":
					rm.exits["east"] = 0, false
				case keys[i]=="w" && keys[j]=="west" ||
					keys[j]=="w" && keys[i]=="west":
					rm.exits["west"] = 0, false
				case keys[i]=="ne" && keys[j]=="northeast" ||
					keys[j]=="ne" && keys[i]=="northeast":
					rm.exits["northeast"] = 0, false
				case keys[i]=="se" && keys[j]=="southeast" ||
					keys[j]=="se" && keys[i]=="southeast":
					rm.exits["southeast"] = 0, false
				case keys[i]=="sw" && keys[j]=="southwest" ||
					keys[j]=="sw" && keys[i]=="southwest":
					rm.exits["southwest"] = 0, false
				case keys[i]=="nw" && keys[j]=="northwest" ||
					keys[j]=="nw" && keys[i]=="northwest":
					rm.exits["northwest"] = 0, false
				case keys[i]=="u" && keys[j]=="up" ||
					keys[j]=="u" && keys[i]=="up":
					rm.exits["up"] = 0, false
				case keys[i]=="d" && keys[j]=="down" ||
					keys[j]=="d" && keys[i]=="down":
					rm.exits["down"] = 0, false
				}
				// if two triggers are the same, remove
				// the lower cost one.
				if rm.exits[keys[i]]<0 && rm.exits[keys[j]]<0 {
					si := strings.SplitN(keys[i][1:],".",2)
					sj := strings.SplitN(keys[j][1:],".",2)
					trigi := si[1]
					trigj := sj[1]
					if trigi == trigj {
						ci, e := strconv.Atoi(si[0])
						if e != nil { return e }
						cj, e := strconv.Atoi(sj[0])
						if e != nil { return e }
						if ci < cj {
							rm.exits[keys[i]]=0,false
						}else{
							rm.exits[keys[j]]=0,false
						}
					}
				}
			}
		}
		// now that anything that will be removed has, check regions
		for _, v := range rm.exits {
			if v < 0 { v *= -1 }
			if v == UNKN_DEST { continue }
			dr := new ( Room )
			e = mm.bin.Load( v, dr )
			if e != nil { return e }
			if dr.regionId != rm.regionId {
				// it's okay if we duplicate these in the
				// arrays, since the first thing repathRegion
				// does is scan and remove duplicates.
				r1 := mm.regionsById[ rm.regionId ]
				r2 := mm.regionsById[ dr.regionId ]
				r1.exits = append( r1.exits, v )
				r2.entrances = append( r2.entrances, v )
			}
		}
		// now that anything that will be removed has, check regions
		if rm.anomalous {
			if rm.anomalyIds == nil || len( rm.anomalyIds ) == 0 {
				rm.anomalous = false
			}
		}
		if rm.anomalyIds != nil {
			for i := range rm.anomalyIds {
				a := new( Anomaly )
				e = mm.bin.Load( rm.anomalyIds[i], a )
				if e != nil { return e }
				switch a.atype {
				case TAG :
					t := new( Tag )
					if mm.bin.TypeOfId( a.subId ) != TAG_HASH {
						e = mm.bin.Delete( a.id )
						if e != nil { return e }
						rm.anomalyIds[i] = rm.anomalyIds[len(rm.anomalyIds)-1]
						rm.anomalyIds = rm.anomalyIds[:len(rm.anomalyIds)-1]
						break
					}
					e = mm.bin.Load( a.subId, t )
					if e != nil { return e }
					if t.anomalyId != a.id {
						e = mm.bin.Delete( a.id )
						if e != nil { return e }
						a = new( Anomaly )
						e = mm.bin.Load( t.anomalyId, a )
						if e != nil { return e }
						if a.subId == t.id {
							rm.anomalyIds[i] = a.id
						} else {
							rm.anomalyIds[i] = rm.anomalyIds[len(rm.anomalyIds)-1]
							rm.anomalyIds = rm.anomalyIds[:len(rm.anomalyIds)-1]
						}
					}
					if !t.rooms.Contains( rm.id ) {
						t.rooms.Add( rm.id )
						e = mm.bin.Store( t )
						if e != nil { return e }
					}
				case NOTE :
					// not implemented yet
				case MOBILE_EXIT :
					// not implemented yet
				default :
					e = mm.bin.Delete( a.id )
					if e != nil { return e }
					rm.anomalyIds[i] = rm.anomalyIds[len(rm.anomalyIds)-1]
					rm.anomalyIds = rm.anomalyIds[:len(rm.anomalyIds)-1]
				}
			}
			if len( rm.anomalyIds ) > 0 {
				rm.anomalous = true
			} else {
				rm.anomalous = false
			}
		}

		if mm.bin.TypeOfId( rm.shortDescId ) != PSTRING_HASH {
			rm.shortDescId = 0
			rm.sDesc = nil
		} else {
			ps1 := new( PString )
			e = mm.bin.Load( rm.shortDescId, ps1 )
			if e != nil { return e }
			hash := tools.Hash( ps1.val )
			hashedId, b := hashedPStringMap[ hash ]
			if b && hashedId != ps1.id {
				ps2 := new( PString )
				e = mm.bin.Load( hashedId, ps2 )
				if e != nil { return e }
				if ps2.val == ps1.val {
					// duplicate PString.  Delete it once all rooms are 
					// scanned (or it could mess up a later room).
					duplicatePStrings[ ps1.id ] = true
					if e != nil { return e }
					rm.shortDescId = ps2.id
				}
			}
			hashedPStringMap[ hash ] = rm.shortDescId
		}
		referencedPStringIds[ rm.shortDescId ] = true

		if mm.bin.TypeOfId( rm.longDescId ) != PSTRING_HASH {
			rm.longDescId = 0
			rm.lDesc = nil
		} else {
			ps1 := new( PString )
			e = mm.bin.Load( rm.longDescId, ps1 )
			if e != nil { return e }
			hash := tools.Hash( ps1.val )
			hashedId, b := hashedPStringMap[ hash ]
			if b && hashedId != ps1.id {
				ps2 := new( PString )
				e = mm.bin.Load( hashedId, ps2 )
				if e != nil { return e }
				if ps2.val == ps1.val {
					// duplicate PString.  Delete it once all rooms are 
					// scanned (or it could mess up a later room).
					duplicatePStrings[ ps1.id ] = true
					if e != nil { return e }
					rm.longDescId = ps2.id
				}
			}
			hashedPStringMap[ hash ] = rm.longDescId
		}
		referencedPStringIds[ rm.longDescId ] = true

		mm.rmIndex.RegisterRoom( rm )
		mm.bin.Store( rm )
	}
	for k, _ := range duplicatePStrings {
		e = mm.bin.Delete( k )
		if e != nil { return e }
	}
	_, e = mm.riFile.Seek( 0, 0 )
	if e != nil { return e }
	_, e = mm.riFile.Write( mm.rmIndex.Write() )
	if e != nil { return e }

	// now that the rooms are all cleaned up, which also 
	// added all the region entrance and exits, we repath
	// the regions

	// first, we need to make a new roomCache, which we nilled at the top
	mm.rmc = new( roomCache )
	mm.rmc.size = oldRmcSize
	mm.rmc.id2e = make( map[int64] *list.Element, mm.rmc.size )
	mm.rmc.lst = list.New()

	for id, r := range mm.regionsById {
		e = mm.repathRegion( id )
		if e != nil { return e }
		e = mm.bin.Store( r )
		if e != nil { return e }
	}

	ids = mm.bin.IdsOfType( TAG_HASH )
	for i := range ids {
		t := new( Tag )
		e = mm.bin.Load( ids[i], t )
		if e != nil { return e }
		rids := t.rooms.Values()
G:		for i := range rids {
			if mm.bin.TypeOfId( rids[i] ) != ROOM_HASH {
				t.rooms.Remove( rids[i] )
				continue
			}
			rm := new( Room )
			e = mm.bin.Load( rids[i], rm )
			if e != nil { return e }
			if rm.anomalous {
				for j := range rm.anomalyIds {
					if rm.anomalyIds[j] == t.anomalyId {
						continue G
					}
				}
			}
			t.rooms.Remove( rids[i] )
		}
		mm.bin.Store( t )
	}

	ids = mm.bin.IdsOfType( ANOMALY_HASH )
	for i := range ids {
		a := new( Anomaly )
		e = mm.bin.Load( ids[i], a )
		if e != nil { return e }
		switch a.atype{
		case TAG :
			if mm.bin.TypeOfId( a.subId ) != TAG_HASH {
				mm.bin.Delete( a.id )
				continue
			}
			t := new( Tag )
			e = mm.bin.Load( a.subId, t )
			if e != nil { return e }
			if t.anomalyId != a.id {
				mm.bin.Delete( a.id )
				if e != nil { return e }
				a = new( Anomaly )
				e = mm.bin.Load( t.anomalyId, a )
				if e != nil { return e }
				if a.subId != t.id {
					mm.bin.Delete( t.id )
					if e != nil { return e }
				}
				continue
			}
		case NOTE :
		// not yet defined
		case MOBILE_EXIT :
		// not yet defined
		default :
		// not yet defined
		}
	}
	ids = mm.bin.IdsOfType( NAMED_ROOM_HASH )
H:	for i := range ids {
		nr := new( NamedRoom )
		e = mm.bin.Load( ids[i], nr )
		if e != nil { return e }
		if mm.bin.TypeOfId( nr.roomId ) != ROOM_HASH {
			mm.bin.Delete( nr.id )
			continue
		}
		nr2, b := mm.namedRooms[ nr.name ]
		for b {
			if nr2.roomId == nr.roomId {
				e = mm.bin.Delete( nr.id )
				if e != nil { return e }
				continue H
			}
			m, e := regexp.MatchString( "\\([0-9]+\\)$", nr.name )
			if e != nil { return e }
			if m {
				j := strings.LastIndex( nr.name, "(" )
				nri, e := strconv.Atoi( nr.name[j+1:len(nr.name)-1] )
				if e != nil { return e }
				nr.name = fmt.Sprint(nr.name[:j+1],nri+1,")")
			} else {
				nr.name = nr.name + "(2)"
			}
			nr2, b = mm.namedRooms[ nr.name ]
		}
		e = mm.bin.Store( nr )
		if e != nil { return e }
	}
	// check PStrings for orphans and remove them
	for i := range storedPStringIds {
		if !referencedPStringIds[storedPStringIds[i]] {
			e = mm.bin.Delete( storedPStringIds[i] )
			if e != nil { return e }
		}
	}
	// now load it from the DB
	mm.strIndex = getPStringFinder( mm.bin )
	_, e = mm.siFile.Seek( 0, 0 )
	if e != nil { return e }
	_, e = mm.siFile.Write( mm.strIndex.Write() )
	if e != nil { return e }
	e = mm.bin.CommitAndPack()
	if e != nil { return e }

	e = mm.loadIndexes()
	if e != nil { return e }
	mm.alteredRegions = make( map [int64] bool, 5 )

// for next version:
// break it into subfuncs
// check for orphaned Anomalies (no room or tag references)
// check for orphaned Notes
	return nil
}

