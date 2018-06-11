/* The tools package is a place for useful tools that aren't
   available from the go standard library (at the time of writing
   */
package tools


// Copyright 2010 Liam LeFey.  All rights reserved.
// Use of this code is governed by the GPL v3

// many thanks to wikipedia for algorithm and structure hints

// SI64Set is a sorted set of int64s.
// It also has some useful methods for searching the number space,
// for instance Ceil( x ) will return the lowest value v contained
// in the set where x <= v.

// this is useful for things such as maintaining 'edge' indexes, for
// instance data boundaries in a flat file.

// it is backed by a red-black tree, packed into an array, so it should
// be efficient with respect to both storage and speed, regardless of
// how the data is manipulated.  (using an array avoids GC churn and
// should limit memory fragmentation)
import (
	"os"
)

const NULL_INDEX = -1	// standard null index
type SI64Set struct {
	array []node			// the tree itself
	root int			// the index of the root node
	modifications int64		// a mod count to make the iterator fail
					// if someone changes the set
}
func NewSI64Set( room int ) (s *SI64Set) {
	s = new( SI64Set )
	s.Initialize( room )
	return s
}
func (s *SI64Set) Initialize( room int ) {
	if room <= 0 {
		room = 1
	}
	s.array = make([]node, 0, room)
	s.root = NULL_INDEX
}
// resonably efficient iteration of elements.
// creation of the iterator is an O(log(n)) operation
// each HasNext() is O(log(n)), and Next() is O(1)
// So iterating over the entire set is O(nlog(n))
// if you can come up with something O(n) I'd love it.
// consider getting a copy from Values if:
// a) speed is important
// b) the set is small and the memory hit of duplication
//    is a non-issue
// c) having a snapshot is more important than working on
//    the current data
func (s *SI64Set) Iterator() I64Iter{
	it := new( sI64SetIter )
	it.initialize( s )
	return it
}
// completes in O(n) time (I think), and duplicates the
// data.
// Consider using the Iterator if:
// a) you don't want the memory overhead of copying all the
// data
// or b) you need to be notified if the underlying data changes
func (s *SI64Set) Values() (v []int64) {
	v = make([]int64, 0, len(s.array) )
	v = s.buildValue( v, s.root )
	return v
}
func (s *SI64Set) buildValue( v []int64, r int ) []int64 {
	if r == NULL_INDEX { return v }
	v = s.buildValue( v, s.array[ r ].left )
	v = append( v, s.array[ r ].value )
	v = s.buildValue( v, s.array[ r ].right )
	return v
}
// completes in O(m log(n) + i log(i)) where m is the size of
// the smaller source sets, n is the size of the larger, and
// i is the size of the intersection
func (s *SI64Set) Intersection( other *SI64Set ) (intersection *SI64Set) {
	if s == nil || other == nil { return nil }
	if len( other.array ) < len( s.array ) {
		return other.Intersection( s )
	}
	intersection = NewSI64Set( len( s.array ) )
	for i := 0; i < len( s.array ); i++ {
		if other.Contains( s.array[i].value ) {
			intersection.Add( s.array[i].value )
		}
	}
	return intersection
}
func (s *SI64Set) Size() int{
	return len(s.array)
}
func (s *SI64Set) Contains( val int64 ) bool{
	return s.getIndex( val, s.root ) != NULL_INDEX
}
// returns the lowest value currently in the set.  If there
// are no values, returns 0 and an error
func (s *SI64Set) Min() (int64, os.Error) {
	if s.Size() == 0 {
		return 0, os.NewError( "No Values In Set" )
	}
	i := s.root
	for s.array[ i ].left != NULL_INDEX {
		i = s.array[ i ].left
	}
	return s.array[ i ].value, nil
}
// returns the highest value currently in the set.  If there
// are no values, returns 0 and an error
func (s *SI64Set) Max() (int64, os.Error) {
	if s.Size() == 0 {
		return 0, os.NewError( "No Values In Set" )
	}
	i := s.root
	for s.array[ i ].right != NULL_INDEX {
		i = s.array[ i ].right
	}
	return s.array[ i ].value, nil
}
// returns val if it is in the set.
// if it is not in, returns the next highest value,
// if it is larger than any in the set, returns 0, and 
// an error.
func (s *SI64Set) Ceil( val int64 ) (int64, os.Error) {
	i := s.getCeilIndex( val, s.root )
	if i == NULL_INDEX {
		return 0, os.NewError( "No Greater Value In Set" )
	}
	return s.array[i].value, nil
}
func (s *SI64Set) getCeilIndex( val int64, i int ) int{
	if i == NULL_INDEX || s.array[ i ].value == val{
		return i
	}
	if s.array[ i ].value < val {
		return s.getCeilIndex( val, s.array[ i ].right )
	}
	if s.array[ i ].left == NULL_INDEX {
		return i
	}
	c := s.getCeilIndex( val, s.array[ i ].left )
	if c == NULL_INDEX {
		return i
	}
	return c
}
// returns val if it is in the set.
// if it is not in, returns the next lowest value,
// if it is smaller than any in the set, returns 0, and 
// an error.
func (s *SI64Set) Floor( val int64 ) (int64, os.Error) {
	i := s.getFloorIndex( val, s.root )
	if i == NULL_INDEX {
		return 0, os.NewError( "No Lesser Value In Set" )
	}
	return s.array[i].value, nil
}
func (s *SI64Set) getFloorIndex( val int64, i int ) int{
	if i == NULL_INDEX || s.array[ i ].value == val{
		return i
	}
	if s.array[ i ].value > val {
		return s.getFloorIndex( val, s.array[ i ].left )
	}
	if s.array[ i ].right == NULL_INDEX {
		return i
	}
	f := s.getFloorIndex( val, s.array[ i ].right )
	if f == NULL_INDEX {
		return i
	}
	return f
}
func (s *SI64Set) getIndex( val int64, r int ) int {
	if r == NULL_INDEX || s.array[ r ].value == val {
		return r
	}
	if s.array[ r ].value < val {
		return s.getIndex( val, s.array[ r ].right )
	}
	return s.getIndex( val, s.array[ r ].left )
}
// removes the value val from the set.  Returns true
// if the value was in the set, false if it wasn't 
// in the set to begin with
func (s *SI64Set) Remove( val int64 ) bool {
	i := s.getIndex( val, s.root )
	if i == NULL_INDEX {
		return false
	}
	s.deleteNode( i )
	return true
}
// adds the value val to the set.  Returns true
// if the value was not in the set, false if it
// was already there
func (s *SI64Set) Add( val int64 ) bool {
	i := s.root
	if i == NULL_INDEX {
		s.root = s.insert( val, NULL_INDEX )
		s.modifications++
		return true;
	}
	for {
		if val == s.array[ i ].value {
			return false
		}
		if val < s.array[ i ].value {
			if s.array[ i ].left == NULL_INDEX {
				s.array[ i ].left = s.insert( val, i )
				s.balanceAfterInsert( s.array[ i ].left )
				s.modifications++
				return true
			}
			i = s.array[ i ].left
		} else {
			if s.array[ i ].right == NULL_INDEX {
				s.array[ i ].right = s.insert( val, i )
				s.balanceAfterInsert( s.array[ i ].right )
				s.modifications++
				return true
			}
			i = s.array[ i ].right
		}
	}
	// we never get here
	return false
}
func (s *SI64Set) insert( val int64, p int ) (i int) {
	i = len( s.array )
	s.array = append( s.array, node{false, p, NULL_INDEX, NULL_INDEX, val})
	return i
}
func (s *SI64Set) deleteNode( p int ){
	s.modifications++
	// this is a quick check that makes things more efficient.
	// if p is an full node (has both left & right child) we
	// can make the job easier by replacing p with the next
	// value, and then deleting that next one (which cannot be
	// a full node)
	if s.array[ p ].left != NULL_INDEX && s.array[ p ].right != NULL_INDEX {
		n := s.successor( p )
		s.array[ p ].value = s.array[ n ].value
		p = n
	}
	var replacement int
	if s.array[ p ].left != NULL_INDEX {
		replacement = s.array[ p ].left
	} else {
		replacement = s.array[ p ].right
	}
	switch {
	// p has a child replacement
	case replacement != NULL_INDEX :
		s.array[ replacement ].parent = s.array[ p ].parent
		switch {
		case s.array[ p ].parent == NULL_INDEX :
			s.root = replacement
		case s.array[ s.array[ p ].parent ].left == p :
			s.array[ s.array[ p ].parent ].left = replacement
		default :
			s.array[ s.array[ p ].parent ].right = replacement
		}
		if ! s.array[ p ].red {
			s.fixAfterDeletion( replacement )
		}
	// p has no child replacement, and is the root
	case s.array[ p ].parent == NULL_INDEX :
		s.root = NULL_INDEX
	// p has no child replacement and is not the root
	default :
		if ! s.array[ p ].red {
			s.fixAfterDeletion( p )
		}
		if s.array[ p ].parent != NULL_INDEX {
			switch{
			case s.array[ s.array[ p ].parent ].left == p :
				s.array[ s.array[ p ].parent ].left = NULL_INDEX
			case s.array[ s.array[ p ].parent ].right == p :
				s.array[ s.array[ p ].parent ].right = NULL_INDEX
			}
			s.array[ p ].parent = NULL_INDEX
		}
	}
	// now we swap p with the last node, and shorten the slice
	// to reclaim the space
	l := len( s.array ) - 1
	if p != l {
		if s.array[ l ].parent == NULL_INDEX {
			s.root = p
		} else {
			if s.array[ s.array[ l ].parent ].left == l {
				s.array[ s.array[ l ].parent ].left = p
			} else {
				s.array[ s.array[ l ].parent ].right = p
			}
		}
		s.array[ p ] = s.array[ l ]
		if s.array[ p ].left != NULL_INDEX {
			s.array[ s.array[ p ].left ].parent = p
		}
		if s.array[ p ].right != NULL_INDEX {
			s.array[ s.array[ p ].right ].parent = p
		}
	}
	s.array = s.array[0:l]
}
func (s *SI64Set) fixAfterDeletion( x int ) {
	var y int
	for x != s.root && ! s.array[ x ].red {
		if x == s.array[ s.array[ x ].parent ].left {
			y = s.array[ s.array[ x ].parent ].right
			if s.isRed( y ) {
				s.array[ y ].red = false
				s.array[ s.array[ x ].parent ].red = true
				s.rotateLeft( s.array[ x ].parent )
				y = s.array[ s.array[ x ].parent ].right
			}
			if ! s.isRed( s.array[ y ].left ) &&
					! s.isRed( s.array[ y ].right ) {
				s.array[ y ].red = true
				x = s.array[ x ].parent
			} else {
				if ! s.isRed( s.array[ y ].right ) {
					s.array[ s.array[ y ].left ].red = false
					s.array[ y ].red = true
					s.rotateRight( y )
					y = s.array[ s.array[ x ].parent ].right
				}
				// doublecheck this line(s)
				s.array[ y ].red = s.isRed( s.array[ x ].parent )
				s.array[ s.array[ x ].parent ].red = false
				s.array[ s.array[ y ].right ].red = false
				s.rotateLeft( s.array[ x ].parent )
				x = s.root
			}
		} else {
			y = s.array[ s.array[ x ].parent ].left
			if s.isRed( y ) {
				s.array[ y ].red = false
				s.array[ s.array[ x ].parent ].red = true
				s.rotateRight( s.array[ x ].parent )
				y = s.array[ s.array[ x ].parent ].left
			}
			if ! s.isRed( s.array[ y ].left ) &&
					! s.isRed( s.array[ y ].right ) {
				s.array[ y ].red = true
				x = s.array[ x ].parent
			} else {
				if ! s.isRed( s.array[ y ].left ) {
					s.array[ s.array[ y ].right ].red = false
					s.array[ y ].red = true
					s.rotateLeft( y )
					y = s.array[ s.array[ x ].parent ].left
				}
				// doublecheck this line(s)
				s.array[ y ].red = s.isRed( s.array[ x ].parent )
				s.array[ s.array[ x ].parent ].red = false
				s.array[ s.array[ y ].left ].red = false
				s.rotateRight( s.array[ x ].parent )
				x = s.root
			}
		}
	}
	s.array[ x ].red = false
}
func (s *SI64Set) rotateRight( i int ){
	l := s.array[ i ].left
	s.array[ i ].left = s.array[ l ].right
	if s.array[ l ].right != NULL_INDEX {
		s.array[ s.array[ l ].right ].parent = i
	}
	s.array[ l ].parent = s.array[ i ].parent
	switch {
	case s.array[ i ].parent == NULL_INDEX :
		s.root = l
	case s.array[ s.array[ i ].parent ].right == i :
		s.array[ s.array[ i ].parent ].right = l
	default :
		s.array[ s.array[ i ].parent ].left = l
	}
	s.array[ l ].right = i
	s.array[ i ].parent = l
}
func (s *SI64Set) rotateLeft( i int ){
	r := s.array[ i ].right
	s.array[ i ].right = s.array[ r ].left
	if s.array[ r ].left != NULL_INDEX {
		s.array[ s.array[ r ].left ].parent = i
	}
	s.array[ r ].parent = s.array[ i ].parent
	switch {
	case s.array[ i ].parent == NULL_INDEX :
		s.root = r
	case s.array[ s.array[ i ].parent ].left == i :
		s.array[ s.array[ i ].parent ].left = r
	default :
		s.array[ s.array[ i ].parent ].right = r
	}
	s.array[ r ].left = i
	s.array[ i ].parent = r
}
func (s *SI64Set) balanceAfterInsert( x int ){
	s.array[ x ].red = true
	var y, p, g int
	for x != NULL_INDEX && x != s.root && s.array[ s.array[ x ].parent ].red {
		p = s.array[ x ].parent
		g = s.array[ p ].parent
		if s.array[ g ].left == p {
			y = s.array[ g ].right
			if s.isRed( y ) {
				s.array[ p ].red = false
				s.array[ y ].red = false
				s.array[ g ].red = true
				x = g
			} else {
				if x == s.array[ p ].right {
					x = p
					s.rotateLeft( x )
					p = s.array[ x ].parent
					g = s.array[ p ].parent
				}
				s.array[ p ].red = false
				s.array[ g ].red = true
				if g != NULL_INDEX {
					s.rotateRight( g )
				}
			}
		} else {
			y = s.array[ g ].left
			if s.isRed( y ) {
				s.array[ p ].red = false
				s.array[ y ].red = false
				s.array[ g ].red = true
				x = g
			} else {
				if x == s.array[ p ].left {
					x = p
					s.rotateRight( x )
					p = s.array[ x ].parent
					g = s.array[ p ].parent
				}
				s.array[ p ].red = false
				s.array[ g ].red = true
				if g != NULL_INDEX {
					s.rotateLeft( g )
				}
			}
		}
	}
	s.array[ s.root ].red = false
}
func (s *SI64Set) isRed( i int ) bool {
	// nulls are black by the deffinition of the rb tree
	return i != NULL_INDEX && s.array[ i ].red
}
func (s *SI64Set) successor( i int ) (j int) {
	if i == NULL_INDEX {
		return NULL_INDEX
	}
	if j = s.array[ i ].right; j != NULL_INDEX {
		for s.array[ j ].left != NULL_INDEX {
			j = s.array[ j ].left
		}
		return j
	}
	j = s.array[ i ].parent
	for j != NULL_INDEX && s.array[ j ].right == i {
		i = j
		j = s.array[ j ].parent
	}
	return j
}
// tests the sets for equality.  Sets are equal if all elements
// in each set are in the other set
func (s *SI64Set) Equals( o *SI64Set ) (bool, os.Error){
	if &o == &s {
		return true, nil
	}
	if o == nil || len( o.array ) != len ( s.array ) {
		return false, nil
	}
	my := s.Iterator()
	its := o.Iterator()
	for my.HasNext() {
		v, e := my.Next()
		v2, e2 := its.Next()
		switch {
		case e != nil : return false, e
		case e2 != nil : return false, e2
		case v != v2 : return false, nil
		}
	}
	return true, nil
}
func (s *SI64Set) Clone() (clone *SI64Set) {
	clone = new(SI64Set)
	l := len(s.array)
	c := cap(s.array)
	clone.array = make([]node, l, c)
	for i := 0; i < l; i++ {
		clone.array[ i ] = s.array[ i ]
	}
	clone.root = s.root
	clone.modifications = s.modifications
	return clone
}
type sI64SetIter struct{
	curr, prev int
	expectedModifications int64
	set *SI64Set
}
func (it *sI64SetIter) initialize( s *SI64Set ) {
	it.set = s
	it.expectedModifications = s.modifications
	it.prev = NULL_INDEX
	it.curr = s.root
	if s.root != NULL_INDEX {
		for s.array[ it.curr ].left != NULL_INDEX {
			it.curr = s.array[ it.curr ].left
		}
	}
}
func (it *sI64SetIter) HasNext() (bool)  {
	if it.expectedModifications != it.set.modifications {
		return false
	}
	return it.curr != NULL_INDEX
}
func (it *sI64SetIter) Next() (int64, os.Error) {
	if it.expectedModifications != it.set.modifications {
		return 0, os.NewError( "Concurrent Modification" )
	}
	if it.curr == NULL_INDEX {
		return 0, os.NewError( "No Such Element" )
	}
	it.prev = it.curr
	it.curr = it.set.successor( it.curr )
	return it.set.array[ it.prev ].value, nil
}
func (it *sI64SetIter) Remove() os.Error {
	if it.expectedModifications != it.set.modifications {
		return os.NewError( "Concurrent Modification" )
	}
	if it.prev == NULL_INDEX {
		return os.NewError( "No Such Element" )
	}
	// the value might move during the deletion, so 
	// we have to save it and find it again.
	if it.curr != NULL_INDEX {
		v := it.set.array[ it.curr ].value
		it.set.deleteNode( it.prev )
		it.curr = it.set.getIndex( v, it.set.root )
	} else {
		it.set.deleteNode( it.prev )
	}
	it.expectedModifications++
	it.prev = NULL_INDEX
	return nil
}

type I64Iter interface{
	// returns true if there is a next, false otherwise
	// returns a non-nil "Concurrent Modification" error if the underlying
	// data has been modified by some means other than this iterator
	HasNext() (bool)
	// returns the next int64
	// returns a non-nil "Concurrent Modification" error if the underlying
	// data has been modified by some means other than this iterator
	// returns a non-nil "No Such Element" error if there is no next element
	Next() (int64, os.Error)
	// removes the last element returned from the underlying data without
	// causing subsequent modification errors
	// returns a non-nil "Concurrent Modification" error if the underlying
	// data has been modified by some means other than this iterator
	// returns a non-nil "No Such Element" error if no element has been
	// returned yet, or if it has already been removed
	Remove() (os.Error)
}
type node struct{
	red bool
	parent int
	right int
	left int
	value int64
}
