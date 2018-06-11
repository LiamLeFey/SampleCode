package mudmap

import (
	"os"
	"fmt"
	"strings"
	"strconv"
	"container/heap"
	"project/tools"
	"project/persistence"
)
// a Path is a list of steps, each of which contains either a trigger or
// a command, and move successively through states (rooms) from an origin
// to a target.  Each step has a cost, and the path itself also has an
// aggregate cost.

// This file also contains the methods used to find an optimal path
// from one room (location state) to another.

// this is the principle reason for the whole mapper.
// It's a monster.
// the idea is to be able to quickly find a path from any given point
// to any other, taking any steps necessary (within reason) to get there

type Path struct {
	Steps []Step
}

// a step of the path.
// It can be either
// a command (STYPE_CMD)
//   an immediate command,
// a trigger (STYPE_TRG) or
//   a wait, or
// a branch (STYPE_BRN)
//   it can branch the path
type Step struct {
	// Command or Trigger  (STYPE_CMD || STYPE_TRG)
	SType byte
	// Either the command or the (regexp) text
	Text string
	// index of NextStep in Path
	NextStep int
}
// STypes
const STYPE_CMD = 0x01
const STYPE_TRG = 0x02
// only use this one internally. convert to TRG in Path
const stype_brn = 0x03
// to use in search algorithm
type dijkStep struct {
	// Step is built from these
	stype byte
	text string
	next int
	// transient. total cost to dest from origin
	tCost int
	// transient. cost of the individual step
	// the cost of a command is 1. 
	// cost of a trigger depends on expected wait,
	// (5 + (100 * seconds) is reasonable)
	// for a branch, 5 + the cost of travelling between the farthest results
	sCost int
	// pointer to the previous step, in order to build the path
	// when we find the/a goal
	// transient.  used to build array from the reverse linked list
	prev *dijkStep
	// used in branch resolution
	orig int64
	// used in Dijkstra's and branch resolution
	dest int64
}
// this is 'public' only because it needs to be to be Storable
type IPath struct {
	orig int64
	dest int64
	cost int
	steps []dijkStep
}
func (p IPath) Write() (bs []byte) {
	l := 2*intLen + 16 + len( p.steps )*(intLen+17)
	for i := range p.steps {
		l += len( p.steps[ i ].text )
	}
	bs = make( []byte, 0, l )
	bs = append( bs, persistence.IToB( len(p.steps) )... )
	bs = append( bs, persistence.I64ToB( p.orig )... )
	bs = append( bs, persistence.I64ToB( p.dest )... )
	bs = append( bs, persistence.IToB( p.cost )... )
	for i := range p.steps {
		bs = append( bs, p.steps[ i ].stype )
		bs = append( bs, persistence.IToB( len(p.steps[i].text) )... )
		bs = append( bs, []byte( p.steps[i].text )... )
		bs = append( bs, persistence.I64ToB( p.steps[i].orig )... )
		bs = append( bs, persistence.I64ToB( p.steps[i].dest )... )
	}
	return
}
func (p *IPath) Read( bs []byte ) (e os.Error) {
	// for array index out of bounds panics, don't halt, just fail
	defer func(){
		if p.steps == nil {
			p.steps = make( []dijkStep, 0 )
		}
		if err := recover(); err != nil {
			e = os.NewError(fmt.Sprintln("Path.Read Error: ",err))
		}
	}()
	x := 0
	c := persistence.BToI( bs[x:x+intLen] )
	x += intLen
	p.orig = persistence.BToI64( bs[x:x+8] )
	x += 8
	p.dest = persistence.BToI64( bs[x:x+8] )
	x += 8
	p.cost = persistence.BToI( bs[x:x+intLen] )
	x += intLen
	var l int
	p.steps = make( []dijkStep, c )
	for i := range p.steps {
		p.steps[i].stype = bs[x]
		x += 1
		l = persistence.BToI( bs[x:x+intLen] )
		x += intLen
		p.steps[i].text =  string( bs[x:x+l] )
		x += l
		p.steps[i].orig = persistence.BToI64( bs[x:x+8] )
		x += 8
		p.steps[i].dest = persistence.BToI64( bs[x:x+8] )
		x += 8
	}
	return nil
}

// for stage 2, when we're putting path segments together
type dIPath struct {
	*IPath
	tCost int
	prev *dIPath
}


func (mm *MudMap) path( origId, destId int64 ) (p *Path, e os.Error) {
	ip, e := mm.iPath( origId, destId )
	if e != nil { return nil, os.NewError( "MudMap.path err: "+e.String()) }
	dijkSteps, e := mm.resolveBranches( ip )
	if e != nil { return nil, os.NewError( "MudMap.path err: "+e.String()) }
	p = new( Path )
	p.Steps = make( []Step, len( dijkSteps ) )
	for i := range dijkSteps {
		s := new(Step)
		s.SType = dijkSteps[ i ].stype
		if s.SType == stype_brn { s.SType = STYPE_TRG }
		s.Text = dijkSteps[ i ].text
		s.NextStep = dijkSteps[ i ].next
		p.Steps[i] = *s
	}
	return p, nil
}
// this is the main pathing function.  It runs in 2 stages.
// Stage 1:  create IPaths b/t orig, exits, entrances, & dest
// stage 2:  use IPaths (segments) to make the whole IPath
func (mm *MudMap) iPath( origId, destId int64 ) (ip *IPath, e os.Error) {
	eStr := fmt.Sprint("Path.iPath origId: ",origId,", destId: ",destId,", error: ")

	// pathologically easy: the nil path
	if origId == destId {
		return &IPath{ origId, destId, 0, make([]dijkStep, 0) }, nil
	}

	visited := tools.NewSI64Set( 100 )
	visited.Add( origId )
	h := new(dIPathHeap)


	orig, e := mm.room( origId )
	if e != nil { return nil, os.NewError( fmt.Sprint(eStr,e) ) }
	dest, e := mm.room( destId )
	if e != nil { return nil, os.NewError( fmt.Sprint(eStr,e) ) }

	if orig.regionId == dest.regionId {
		// add intra region path orig to dest
		ps, e := mm.pllIPaths( origId, []int64{ destId } )
		if e != nil {
			return nil, os.NewError( fmt.Sprint(eStr,e) )
		}
		p := ps[0]
		if p != nil {
			heap.Push( h, &dIPath{p, p.cost, nil} )
		}
	}
	// get paths from orig to orig.region.exits
	r := mm.regionsById[ orig.regionId ]

	// if orig is an entrance, the paths are already cached.
	// this caused problems when a region path was nil...
	//i := 0
	//for ; i < len(r.entrances) && r.entrances[ i ] != origId; i++ {}
	//var ps []*IPath
	// else we find 'em ourselves
	//if i == len( r.entrances ) {
		//ps, e = mm.pllIPaths( origId, r.exits )
		//if e != nil { return nil, os.NewError( eStr+e.String() ) }
	//} else {
		// yay.
		//ps = r.paths[ i ]
	//}

	i := 0
	ps, e := mm.pllIPaths( origId, r.exits )
	if e != nil { return nil, os.NewError( eStr+e.String() ) }
	// prime by adding paths( origin -> region.exits ) to heap
	for i = range r.exits {
		if visited.Contains( r.exits[i] ) { continue }
		heap.Push( h, &dIPath{ ps[ i ], ps[ i ].cost, nil } )
	}
	for h.Len() > 0 {
		dp := heap.Pop( h ).(*dIPath)
		if visited.Contains( dp.dest ) { continue }
		visited.Add( dp.dest )
		if dp.dest == destId {
			return srlIPath( dp ), nil
		}
		rm, e := mm.room( dp.dest )
		if e != nil { return nil, os.NewError( fmt.Sprint(eStr,e )) }
		r = mm.regionsById[ rm.regionId ]
		for i = range r.entrances {
			if dp.dest == r.entrances[ i ] {
				break
			}
		}
		if i == len( r.entrances ) {
			s := "logic error: dp.dest not listed as region entrance."
			return nil, os.NewError( eStr+s )
		}
		ps = r.paths[ i ]
		for i = range ps {
			p := ps[ i ]
			// just in case the region is in bad shape...
			if p == nil {
				continue
			}
			pCost := p.cost + dp.tCost
			if visited.Contains( r.exits[i] ) { continue }
			heap.Push( h, &dIPath{ p, pCost, dp } )
		}
		if r.id == dest.regionId {
			ps, e := mm.pllIPaths( dp.dest, []int64{ destId } )
			if e != nil {return nil, os.NewError(eStr+e.String())}
			p := ps[0]
			pCost := p.cost + dp.tCost
			heap.Push( h, &dIPath{ p, pCost, dp } )
		}
	}
fmt.Println("mm.iPath exhausted heap without finding path. returning err.")
	return nil, os.NewError( eStr + "No path found." )
}
func srlIPath( dp *dIPath ) (p *IPath) {
	stack := new( dIPathHeap )
	p = new( IPath )
	p.cost = dp.tCost
	p.dest = dp.dest
	l := len( dp.steps )
	stack.Push( dp )
	for dp.prev != nil {
		dp = dp.prev
		l += len( dp.steps )
		stack.Push( dp )
	}
	p.steps = make( []dijkStep, 0, l )
	dp, ok := stack.Pop().(*dIPath)
	p.orig = dp.orig
	for ; ok && dp != nil; dp, ok = stack.Pop().(*dIPath) {
		for i := range dp.steps {
			p.steps = append( p.steps, dp.steps[ i ] )
		}
	}
	return p
}

// this one is limited to working within a single Region.  it fans out,
// using Dijkstra's algorithm, and finds the path from the origin to the
// destinations.
func (mm *MudMap) pllIPaths(orig int64, dests []int64)(ps []*IPath, e os.Error){
	eStr :=fmt.Sprint("Path.pllIPaths orig: ",orig,", dests: ",dests,", error: ")
	r, e := mm.room( orig )
	if e != nil { return nil, os.NewError( fmt.Sprint(eStr,e) ) }
	reg := mm.regionsById[ r.regionId ]
	ps = make([]*IPath, len(dests))
	founds := make([]bool, len(dests))
	for i := range dests {
		if dests[ i ] == orig {
			ps[ i ] = &IPath{ orig, orig, 0, []dijkStep{} }
			founds[ i ] = true
		}
	}
	// fail if used wrong (find path to non-exit non-region loc)
	for i := range dests {
		r, e = mm.room( dests[ i ] )
		if e != nil {
			s := fmt.Sprint(eStr,"Getting dest ",dests[i]," ",e)
			return nil, os.NewError( s )
		}
		if ! founds[i] && r.regionId != reg.id && ! isExit(r.id, reg) {
			s := fmt.Sprint(eStr,"Dest ",dests[i]," is not in region.")
			return nil, os.NewError( s )
		}
	}
	visited := tools.NewSI64Set( 100 )
	visited.Add( orig )
	h := new(dijkStepHeap)
	r, e = mm.room( orig )
	if e != nil { return nil, os.NewError( fmt.Sprint(eStr,e) ) }
	tCount := 0
	for k, v := range r.exits {
		switch {
		case v == UNKN_DEST :
			continue
		// for triggers, the key is ".<cost>.<trig_regexp>", and
		// the value is (-1)*destId.
		case v < 0 :
			// record any triggers.  after the loop, count
			// the triggers, and if > 1 add 
			// branches, else add trigger.
			tCount++
		default :
			if strings.HasPrefix( k, "." ) {
				k = k[1:]
			}
			heap.Push(h, &dijkStep{STYPE_CMD,k,-1,1,1,nil,r.id,v})
		}
	}
	switch {
	case tCount == 1 :
		dSs := branches( r, 0, nil )
		// we might have 0 in the case of UNKN_DEST
		if len( dSs ) > 0 {
			dSs[0].stype = STYPE_TRG
			heap.Push( h, dSs[0] )
		}
	case tCount > 1 :
		dSs := branches( r, 0, nil )
		for i := range dSs {
			heap.Push( h, dSs[i] )
		}
	}
	tCount = 0

	for h.Len() > 0 && ! allTrue( founds ) {
		step := heap.Pop( h ).(*dijkStep)
		if visited.Contains( step.dest ) {
			continue
		}
		visited.Add( step.dest )

		// Here is the actual stop condition.  It adds the
		// path to our return value, and marks this destination
		// as found.  When all destinations are found, the
		// enclosing loop will stop
		for i := range dests {
			if ! founds[ i ] && dests[ i ] == step.dest {
				ps[ i ] = pathToHere( step )
				founds[ i ] = true
			}
		}
		r, e = mm.room( step.dest )
		if e != nil { return nil, os.NewError( fmt.Sprint(eStr,e) ) }
		// stop when outside region.
		if r.regionId != reg.id && ! isExit( step.dest, reg ) {
			continue
		}
		for k, v := range r.exits {
			switch {
			// unknowns are useless to us
			case v == UNKN_DEST :
				continue
			// record any triggers.  after the loop, count
			// the triggers, and if there are multiple add 
			// branches, else add the wait trigger.
			case v < 0 :
				tCount++
			default :
				if strings.HasPrefix( k, "." ) { k = k[1:] }
				heap.Push( h, &dijkStep{ STYPE_CMD, k, -1, step.tCost + 1, 1, step, r.id, v } )
			}
		}
		switch {
		case tCount == 1 :
			dSs := branches( r, step.tCost, step )
			// we might have 0 in the case of UNKN_DEST
			if len( dSs ) > 0 {
				dSs[0].stype = STYPE_TRG
				heap.Push( h, dSs[0] )
			}
		case tCount > 1 :
			dSs := branches( r, step.tCost, step )
			for i := range dSs {
				heap.Push( h, dSs[i] )
			}
		}
		tCount = 0
	}
	// Once the loop stops, we either found all destinations (see the 
	// comment about in the middle) or we ran out of places to go.
	return
}

func (mm *MudMap) resolveBranches( ip *IPath ) ( ds []dijkStep, e os.Error ) {
	eStr := "Path.resolveBranches error: "
	rid2idx := make( map[int64] int, len( ip.steps ) )
	// the -1 index represents completion of path
	rid2idx[ ip.dest ] = -1

	incomplete := make( map[int] bool )

	ds = make( []dijkStep, 0, len( ip.steps ) )

	j := 0
	jSave := 0
	steps := ip.steps
	for i := range steps {
		ds = append( ds, steps[i] )
		rid2idx[ds[j].orig] = j
		jSave = j
		j++
		if steps[i].stype == stype_brn {
			r, e := mm.room( steps[i].orig )
			if e != nil {return nil,os.NewError(fmt.Sprint(eStr,e))}
			sisters := branches( r, 0, steps[i].prev )
			for ii := range sisters {
				// skip the identity
				if sisters[ii].text == steps[i].text {continue}
				ds = append( ds, *sisters[ii] )
				rid2idx[ds[j].orig] = j
				incomplete[j] = true
				j++
			}
		}
		ds[jSave].next = j
	}
	if len(ds) == 0 { return ds, nil }
	// the last step recorded now points off the end of the array.
	// set it instead to off the beginning (completion)
	ds[jSave].next = -1
	// now go through and put in branched paths stubbed in above
	for len( incomplete ) > 0 {
		for i, _ := range incomplete {
			if idx, ok := rid2idx[ds[i].dest]; ok {
				ds[i].next = idx
				incomplete[ i ] = false, false
				continue
			}
			subPath, e := mm.iPath( ds[i].dest, ip.dest )
			if e != nil {return nil,os.NewError(fmt.Sprint(eStr,e))}
			for ii := range subPath.steps {
				ds = append( ds, subPath.steps[ii] )
				rid2idx[ ds[j].orig ] = j
				jSave = j
				j++
				if subPath.steps[ii].stype == stype_brn {
					r, e := mm.room(subPath.steps[ii].orig)
					if e != nil {
						return nil,os.NewError(fmt.Sprint(eStr,e))
					}
					sisters := branches(r,0,subPath.steps[ii].prev)
					for i2 := range sisters {
						if sisters[i2].text == subPath.steps[ii].text {
							continue
						}
						ds = append( ds, *sisters[i2] )
						rid2idx[ds[j].orig] = j
						incomplete[j] = true
						j++
					}
				}
				if idx, ok := rid2idx[ds[jSave].dest]; ok {
					ds[jSave].next = idx
					break
				} else {
					ds[jSave].next = j
				}
			}
		}
	}
	return ds, nil
}

func branches( r *Room, baseCost int, prev *dijkStep )( bs []*dijkStep ) {
	bs = make( []*dijkStep, 0, len(r.exits) )
	for k, v := range r.exits {
		if v == -UNKN_DEST || v > 0 { continue }
		splits := strings.SplitN( k[1:], ".", 2 )
		sCost, _ := strconv.Atoi( splits[0] )
		k = splits[1]
		bs =append(bs,&dijkStep{stype_brn,k,-1,baseCost+sCost,sCost,prev,r.id,-v})
	}
	return bs
}
func isExit( roomId int64, region *Region ) (b bool){
	for i := range region.exits {
		if roomId == region.exits[ i ] {
			return true
		}
	}
	return false
}

func pathToHere( d *dijkStep ) (p *IPath){
	// we use dijkStepHeap as a stack to reverse the order
	stack := new (dijkStepHeap)
	stack.Push( d )
	p = new(IPath)
	p.cost = d.tCost
	p.dest = d.dest
	for d.prev != nil {
		d = d.prev
		stack.Push( d )
	}
	d, ok := stack.Pop().(*dijkStep)
	p.orig = d.orig
	p.steps = make( []dijkStep, 0, stack.Len() )
	for ; ok && d != nil; d, ok = stack.Pop().(*dijkStep) {
		p.steps = append( p.steps, *d )
		if len(p.steps) > 1 {
			p.steps[ len(p.steps)-2 ].next = len(p.steps)-1
		}
	}
	return p
}

type dIPathHeap []*dIPath
// from sort.Interface
func (h *dIPathHeap) Len() int {
	return len(*h)
}
// from sort.Interface
func (h *dIPathHeap) Less( i, j int ) bool {
	return (*h)[i].tCost < (*h)[j].tCost
}
// from sort.Interface
func (h *dIPathHeap) Swap( i, j int ) {
	t := (*h)[j]
	(*h)[j] = (*h)[i]
	(*h)[i] = t
}
// from heap.Interface
func (h *dIPathHeap) Push( x interface{} ) {
	i := append( *h, x.(*dIPath) )
	if &i != h {
		*h = i
	}
}
// from heap.Interface
func (h *dIPathHeap) Pop() interface{} {
	if len(*h) < 1 { return nil }
	x := (*h)[len(*h)-1]
	*h = (*h)[:len(*h)-1]
	return x
}
type dijkStepHeap []*dijkStep
// from sort.Interface
func (h *dijkStepHeap) Len() int {
	return len(*h)
}
// from sort.Interface
func (h *dijkStepHeap) Less( i, j int ) bool {
	return (*h)[i].tCost < (*h)[j].tCost
}
// from sort.Interface
func (h *dijkStepHeap) Swap( i, j int ) {
	t := (*h)[j]
	(*h)[j] = (*h)[i]
	(*h)[i] = t
}
// from heap.Interface
func (h *dijkStepHeap) Push( x interface{} ) {
	i := append( *h, x.(*dijkStep) )
	if &i != h {
		*h = i
	}
}
// from heap.Interface
func (h *dijkStepHeap) Pop() interface{} {
	if len(*h) < 1 { return nil }
	x := (*h)[len(*h)-1]
	*h = (*h)[:len(*h)-1]
	return x
}

func allTrue( a []bool ) bool {
	for i := range a {
		if ! a[i] { return false }
	}
	return true
}
func (ds *dijkStep) String() string {
	var t string
	switch ds.stype {
	case STYPE_CMD :
		t = "CMD:"
	case STYPE_TRG :
		t = "TRG:"
	case stype_brn :
		t = "brn:"
	default :
		t = "Bad Stype:"
	}
	return fmt.Sprint(t,"\"",ds.text,"\",tC",ds.tCost,",sC",ds.sCost,",o:",ds.orig,",d:",ds.dest,",->",ds.next)
}
func (ip *IPath) String() (s string) {
	s = "orig:"
	s = s + fmt.Sprintln( ip.orig )
	s = s + "dest:"
	s = s + fmt.Sprintln( ip.dest )
	s = s + "cost:"
	s = s + fmt.Sprintln( ip.cost )
	for i := range ip.steps {
		s = s + fmt.Sprint("[",i,"] ",ip.steps[i],"\n" )
	}
	return s
}
