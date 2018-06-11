package mudmap

import (
	//"errors"
	//"code.google.com/p/gobin"
)

type Region struct {
	Name string

	// all the rooms in the region
	RoomsId int
	// we don't always want to load all the rooms (nil means not loaded).
	rooms []int
	// the rooms that other regions feed into via some exit
	Entrances []int
	// these don't actually belong to this region.  These are rooms
	// of other regions which rooms of this region exit directly to.
	// (so they're all entrances in their regions)
	Exits []int

	LayoutId int
	// in general, we'll be looking for a room based on a location
	layout map[Coordinates]int
	// we want to go the other way too, though.
	revLayout map[int]Coordinates

	// if the room list has changed, we should re-calculate the paths
	// before saving the region
	roomListChanged bool
	// paths from entrances to exits, indexed by [entranceIdx][exitIdx]
	// value is the gobin id of the path
	Paths [][]int
	// value is the length of the path
	PathLengths [][]int
}
func newRegion() (r *Region) {
	r = new( Region )
	r.Entrances = make( []int, 0 )
	r.Exits = make( []int, 0 )
	r.Paths = make( [][]int, 0 )
	r.PathLengths = make( [][]int, 0 )
	return r
}
// This will recalculate the cached paths.  Used when any changes
// have been made that might change the paths through the region.
// This includes adding/removing rooms, room exits, exits or entrances
// (so pretty much any change.)
// Note that this has the potential to get things out of synch if
// this is called and then the region is not stored (since it stores
// the new paths in the gobin.) For this reason, it returns an array
// of the ids of the new paths, so they can be deleted if a commit
// happens without storing the region (but then you lose your old paths...)
/*func (r *Region) repathRegion( bin *gobin.Gobin ) (newIds []int, e error) {
	// first delete the old paths
	var i int
	for _, newIds = range r.Paths {
		for _, i = range newIds {
			if e = bin.Delete( i ); e != nil {
				return nil, e
			}
		}
	}
	// check for and remove duplicates 
	newIds = make( []int, 0, len(r.Entrances) )
	set := make( map[int] bool, len( r.Entrances ) )
	for _, i = range r.Entrances {
		if !set[ i ] {
			newIds = append( newIds, i )
			set[ i ] = true
		}
	}
	r.Entrances = newIds
	newIds = make( []int, 0, len(r.Exits) )
	set = make( map[int] bool, len( r.Exits ) )
	for _, i = range r.Exits {
		if !set[ i ] {
			newIds = append( newIds, i )
			set[ i ] = true
		}
	}
	r.Exits = newIds
	// find the paths
	newIds = make( []int, 0, len(r.Entrances) * len(r.Exits) )
	r.Paths = make( [][]int, len( r.Entrances ) )
	r.PathLengths = make( [][]int, len( r.Entrances ) )
	for i = range r.Entrances {
		r.Paths[ i ], r.PathLengths[ i ], e = pllIPaths( r.Entrances[ i ], r.Exits, bin )
		newIds = append( newIds, r.Paths[ i ]... )
		if e != nil {
			return newIds, errors.New("Path.repathRegion: "+e.Error())
		}
	}
	return newIds, nil
}*/

type Coordinates struct {
	X, Y, Z int
}
