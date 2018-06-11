package mudmap

import (
	"os"
	"fmt"
	"strconv"
	"project/tools"
	"project/persistence"
)

var REGION_HASH = tools.Hash( "project.mudclient.mudmap.Region" )
var intLen = int(strconv.IntSize) / 8
type Region struct {
	id int64
	name string
	entrances []int64
	// these don't actually belong to this region.  These are rooms
	// of other regions which rooms of this region exit directly to.
	exits []int64
	paths [][]*IPath
}
func newRegion( bin *persistence.PBin ) (r *Region, e os.Error) {
	r = new( Region )
	r.SetId( bin.MaxId() + 1 )
	r.entrances = make( []int64, 0 )
	r.exits = make( []int64, 0 )
	r.paths = make( [][]*IPath, 0 )
	e = bin.Store( r )
	return r, e
}
func getRegion( id int64, bin *persistence.PBin ) (r *Region, e os.Error) {
	r = new( Region )
	e = bin.Load( id, r )
	return r, e
}
// A function that correctly updates the paths
// Preferrable (more efficient) to adding it directly and calling repathRegion 
func (mm *MudMap) addRegionEntrance( r *Region, roomId int64 ) os.Error {
	eStr := fmt.Sprint("Region.addRegionEntrance: ")
	// sanity check
	rm, e := mm.room( roomId )
	if e != nil { return os.NewError( eStr+e.String() ) }
	if rm.regionId != r.id {
		return os.NewError( eStr+"Room not in region." )
	}
	// first check if it's already there
	for i := range r.entrances {
		if roomId == r.entrances[ i ] {
			return nil
		}
	}
	r.entrances = append( r.entrances, roomId )
	newPaths, e := mm.pllIPaths( roomId, r.exits )
	if e != nil { return os.NewError( eStr+e.String() ) }
	r.paths = append( r.paths, newPaths )
	mm.bin.Store( r )
	return nil
}
// the reason we don't have a removeEntrance function is it is very 
// difficult to determine when an entrance is no longer valid.
// exits, you scan each room in this region.  For entrances, you
// have to scan every room in all regions.


// A function that correctly updates the paths
// Preferrable (more efficient) to adding it directly and calling repathRegion 
func (mm *MudMap) addRegionExit( r *Region, roomId int64 ) os.Error {
	eStr := fmt.Sprint("Region.addRegionExit: ")
	// sanity check
	rm, e := mm.room( roomId )
	if e != nil { return os.NewError( eStr+e.String() ) }
	if rm.regionId == r.id {
		return os.NewError( eStr+"Room in region cannot be exit." )
	}
	// first check if it's already there
	for i := range r.exits {
		if roomId == r.exits[ i ] {
			return nil
		}
	}
	r.exits = append( r.exits, roomId )
	for i := range r.entrances {
		ps, e := mm.pllIPaths( r.entrances[ i ], []int64{ roomId } )
		if e != nil { return os.NewError( eStr+e.String() ) }
		p := ps[0]
		r.paths[ i ] = append( r.paths[ i ], p )
	}
	mm.bin.Store( r )
	// This part is maybe shady, but every exit should be an entrance in
	// it's region.  So we take care of that
	var room *Room
	if room, e = mm.room( roomId ); e != nil {
		return os.NewError( eStr + e.String() )
	}
	r2 := mm.regionsById[ room.regionId ]
	if e = mm.addRegionEntrance( r2, roomId ); e != nil {
		return os.NewError( eStr + e.String() )
	}
	return nil
}
// removes the given exit and related IPaths.  Assumes the caller
// has verified that it is, in fact no longer an exit.
func (mm *MudMap) removeRegionExit( r *Region, rmId int64 ) {
	newLen := len( r.exits )-1
	if newLen < 0 { return }
	var i int
	for i = range r.exits {
		if r.exits[ i ] == rmId { break }
	}
	if i > newLen { return }
	r.exits[ i ] = r.exits[ newLen ]
	r.exits = r.exits[ : newLen ]
	for j := range r.paths {
		r.paths[j][ i ] = r.paths[j][ newLen ]
		r.paths[j] = r.paths[j][ : newLen ]
	}
	mm.bin.Store( r )
}
// This will recalculate the cached paths.  Useful if entrances and exits
// have been manually changed, or rooms have been added/removed which might
// potentially change the paths.
func (mm *MudMap) repathRegion( regionId int64 ) (e os.Error) {
	r := mm.regionsById[ regionId ]
	// check for and remove duplicates (On^2, but presumably faster
	// than getting duplicate paths)
	// I suppose we could sort them with an Onlogn algorithm and then
	// scan for dups, but I don't feel like it right now.
	var i, j, oldLen int
	for i = len(r.exits)-1; i >= 0; i-- {
		for j = i-1; j >= 0; j-- {
			if r.exits[ i ] == r.exits[ j ] {
				oldLen = len( r.exits )
				r.exits[ j ] = r.exits[ oldLen-1 ]
				r.exits = r.exits[:oldLen-1]
				// if we just moved i, break from inner loop and continue
				// outer, which will decrement i and correctly reset j
				if i == oldLen-1 { break }
			}
		}
	}
	for i = len(r.entrances)-1; i >= 0; i-- {
		for j = i-1; j >= 0; j-- {
			if r.entrances[ i ] == r.entrances[ j ] {
				oldLen = len( r.entrances )
				r.entrances[ j ] = r.entrances[ oldLen-1 ]
				r.entrances = r.entrances[:oldLen-1]
				// if we just moved i, break from inner loop and continue
				// outer, which will decrement i and correctly reset j
				if i == oldLen-1 { break }
			}
		}
	}
	r.paths = make( [][]*IPath, len( r.entrances ) )
	for i = range r.entrances {
		r.paths[ i ], e = mm.pllIPaths( r.entrances[ i ], r.exits )
		if e != nil {
			return os.NewError("Path.repathRegion: "+e.String())
		}
	}
	mm.bin.Store( r )
	return nil
}

// handle Persistable.  These are pretty straight forward
func (r *Region) Id() (id int64) {
	return r.id
}
func (r *Region) SetId(id int64) {
	r.id = id
}
func (t *Region) TypeHash() (th int32){
	return REGION_HASH;
}
func (r *Region) Read( b []byte ) (e os.Error) {
	// for array index out of bounds panics, don't halt, just fail
	defer func(){
		if r.entrances == nil {
			r.entrances = make( []int64, 0 )
		}
		if r.exits == nil {
			r.exits = make( []int64, 0 )
		}
		if r.paths == nil {
			r.paths = make( [][]*IPath, 0 )
		}
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	x := 0
	r.id = persistence.BToI64( b[x:x+8] )
	x += 8
	l := persistence.BToI( b[x:x+intLen] )
	x += intLen
	r.name = string( b[x:x+l] )
	x += l
	entCnt := persistence.BToI( b[x:x+intLen] )
	x += intLen
	r.entrances = make( []int64, entCnt )
	r.paths = make( [][]*IPath, entCnt )
	exCnt := persistence.BToI( b[x:x+intLen] )
	x += intLen
	r.exits = make( []int64, exCnt )
	for i := range r.paths {
		r.paths[ i ] = make( []*IPath, exCnt )
	}
	for i := range r.entrances {
		r.entrances[ i ] = persistence.BToI64( b[x:x+8] )
		x += 8
	}
	for i := range r.exits {
		r.exits[ i ] = persistence.BToI64( b[x:x+8] )
		x += 8
	}
	var pl int
	for i := range r.paths {
		for j := range r.paths[ i ] {
			// MARK this seem wrong.
			pl = persistence.BToI( b[x:x+intLen] )
			x += intLen
			r.paths[i][j] = new( IPath )
			r.paths[i][j].Read( b[x:x+pl] )
			x += pl
		}
	}
	return e
}
func (r *Region) Write() (b []byte) {
	a := 8 / intLen
	l := len(r.name)+16+intLen*((len(r.exits)+a)*(len(r.entrances)+a)+1)
	b = make( []byte, 0, l )
	b = append( b, persistence.I64ToB( r.id )... )
	b = append( b, persistence.IToB( len(r.name) )... )
	b = append( b, []byte( r.name )... )
	b = append( b, persistence.IToB( len(r.entrances) )... )
	b = append( b, persistence.IToB( len(r.exits) )... )
	for i := range r.entrances {
		b = append( b, persistence.I64ToB( r.entrances[ i ] )... )
	}
	for i := range r.exits {
		b = append( b, persistence.I64ToB( r.exits[ i ] )... )
	}
	var pb []byte
	for i := range r.paths {
		for j := range r.paths[ i ] {
			if r.paths[i][j] != nil {
				pb = r.paths[i][j].Write()
			} else {
				pb = make( []byte, 0, 0 )
			}
			b = append( b, persistence.IToB( len( pb ) )... )
			b = append( b, pb... )
		}
	}
	return b
}
