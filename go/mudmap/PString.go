package mudmap

import (
	"os"
	"fmt"
	"project/tools"
	"project/persistence"
)

// This is a type hash, used to ensure we get the right type from
// a PBin retrieve
var PSTRING_HASH = tools.Hash( "project.mudclient.mudmap.PString" )


// Persistent String.  You can't actually change val because they
// are meant to be shared.  Use getPStringId to create new ones or get
// a reference (id) to an existing one that matches
type PString struct {
	id int64
	val string
}

var bfMap map[*persistence.PBin] *PStringFinder

func setPStringFinder( bin *persistence.PBin, psf *PStringFinder) {
	if bfMap == nil {
		bfMap = make( map[*persistence.PBin] *PStringFinder, 1 )
	}
	bfMap[bin] = psf
}
func getPStringFinder( bin *persistence.PBin ) (*PStringFinder) {
	if bfMap == nil {
		bfMap = make( map[*persistence.PBin] *PStringFinder, 1 )
	}
	if bfMap[bin] == nil {
		sf := new( PStringFinder )
		ids := bin.IdsOfType( PSTRING_HASH )
		sf.ids = make( map[int32] []int64, len( ids ) )
		for i := range ids {
			v := new(PString)
			if e := bin.Load( ids[i], v ); e == nil {
				sf.register( v )
			}
		}
		bfMap[bin] = sf
	}
	return bfMap[bin]
}

func ( s PString ) String() (string){
	return s.val
}
func getPString( id int64, bin *persistence.PBin ) (t *PString, e os.Error) {
	t = new( PString )
	e = bin.Load( id, t )
	return t, e
}

// if s doesn't exist, creates it.  then returns the id of s
func getPStringId( s *string, bin *persistence.PBin ) (id int64, e os.Error) {
	if id, ok := findPStringId( s, bin ); ok {
		return id, e
	}
	t := new( PString )
	t.SetId( bin.MaxId() + 1 )
	t.val = *s
	e = bin.Store( t )
	f := getPStringFinder( bin )
	f.register( t )
	return t.id, e
}
// finds it if it exists.  Does not create it if it doesn't
func findPStringId( s *string, bin *persistence.PBin ) (int64, bool ) {
	// if the PStringFinder is nil, we have to brute force the search...
	f := getPStringFinder( bin )
	if s == nil { return -1, false }
	h := tools.Hash( *s )
	if f.ids == nil {
		f.ids = make( map[ int32 ] []int64, 0 )
	}
	ids := f.ids[ h ]
	if ids != nil {
		for i := range ids {
			t, e := getPString( ids[ i ], bin )
			if e != nil { continue }
			if t.val == *s {
				return t.id, true
			}
		}
	}
	return -1, false
}

// handle Persistable.  These are pretty straight forward
func (t *PString) TypeHash() (th int32){
	return PSTRING_HASH
}
func (t *PString) Id() (id int64) {
	return t.id
}
func (t *PString) SetId(id int64) {
	t.id = id
}
func (s *PString) Read( b []byte ) (e os.Error) {
	// in case of array index out of bounds, just fail, don't panic
	defer func(){
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	x := 0
	s.id = persistence.BToI64( b[x:x+8] )
	x += 8
	l := persistence.BToI( b[x:x+intLen] )
	x += intLen
	s.val = string( b[x:x+l] )
	x += l
	return e
}
func (s *PString) Write() (b []byte) {
	l := len( s.val ) + 8 + intLen
	b = make( []byte, 0, l )
	b = append( b, persistence.I64ToB( s.id )... )
	b = append( b, persistence.IToB( len(s.val) )... )
	b = append( b, []byte( s.val )... )
	return
}
type PStringFinder struct {
	ids map[ int32 ] []int64
}
func (f *PStringFinder) register( s *PString ) {

	h := tools.Hash( s.val )

	if f.ids == nil {
		f.ids = make( map[ int32 ] []int64, 100 )
	}
	if f.ids[ h ] == nil {
		f.ids[ h ] = []int64{ s.id }
	} else {
		f.ids[ h ] = append( f.ids[ h ], s.id )
	}
}
func (f *PStringFinder) Write() (b []byte) {
	if f.ids == nil {
		f.ids = make( map[ int32 ] []int64, 0 )
	}
	b = make( []byte, 0, 100 )
	b = append( b, persistence.IToB( len( f.ids ) )... )
	for key, val := range f.ids {
		b = append( b, persistence.I32ToB( key )... )
		b = append( b, persistence.IToB( len( val ) )... )
		for i := range val {
			b = append( b, persistence.I64ToB( val[i] )... )
		}
	}
	return b
}
func (f *PStringFinder) Read( b []byte ) (e os.Error) {
	defer func(){
		if f.ids == nil {
			f.ids = make( map[ int32 ] []int64, 0 )
		}
		if err := recover(); err != nil {
			e = os.NewError( fmt.Sprintln( "Error: ", err ) )
		}
	}()
	if len(b) < intLen {
		f.ids = make( map[ int32 ] []int64, 0 )
		return nil
	}
	if b == nil || len( b ) == 0 {
		b = persistence.IToB( 0 )
	}
	x := 0
	l := persistence.BToI( b[x:x+intLen] )
	x += intLen
	f.ids = make( map[ int32 ] []int64, l )
	for i := 0; i < l; i++ {
		k := persistence.BToI32( b[x:x+4] )
		x += 4
		lv := persistence.BToI( b[x:x+intLen] )
		x += intLen
		f.ids[ k ] = make( []int64, lv )
		for j := 0; j < lv; j++ {
			f.ids[ k ][ j ] = persistence.BToI64(b[x:x+8])
			x += 8
		}
	}
	return nil
}
