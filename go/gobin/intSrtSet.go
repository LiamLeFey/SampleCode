package gobin

import (
	"errors"
)

// Copyright 2010 Liam LeFey.  All rights reserved.
// Use of this code is governed by the Apache License
//
// I used wikipedia for algorithm and structure hints

// IntSrtSet is a sorted set of ints.
// It also has some useful methods for searching the number space.
//
// this is useful for things such as maintaining 'edge' indexes, for
// instance data boundaries in a flat file.
//
// I needed a scalable ordered set of ints, and didn't find what I
// wanted in the standard packages. I didn't break it out to a separate
// package, because it's pretty specific (no support of int64 or string,
// etc.). I did the general case first, but ran some benchmarks, and
// this was twice as fast.
//
// it is backed by a red-black tree, 
type intSrtSet struct {
	root          *node // the index of the root node
	mods int   // a mod count to make the iterator fail
	// if someone changes the set
	cnt int // the number of elements in the set
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
func (s *intSrtSet) Iterator() intIter {
	it := new(issIt)
	it.initialize(s)
	return it
}

// completes in O(n) time and duplicates the data.
// Consider using the Iterator if:
// a) you don't want the memory overhead of copying all the
// data
// or b) you need to be notified if the underlying data changes
func (s *intSrtSet) Values() (v []int) {
	v = make([]int, 0, s.cnt)
	v = buildValue(v, s.root)
	return v
}
func buildValue(v []int, r *node) []int {
	if r == nil {
		return v
	}
	v = buildValue(v, r.l)
	v = append(v, r.v)
	v = buildValue(v, r.r)
	return v
}

// completes in O(m log(n) + i log(i)) where m is the size of
// the smaller source sets, n is the size of the larger, and
// i is the size of the intersection
func (s *intSrtSet) Intersection(o *intSrtSet) (intersection *intSrtSet) {
	if s == nil || o == nil {
		return nil
	}
	if o.cnt < s.cnt {
		return o.Intersection(s)
	}
	intersection = new(intSrtSet)
	a := s.Values()
	for _, i := range a {
		if o.Contains(i) {
			intersection.Add(i)
		}
	}
	return intersection
}
func (s *intSrtSet) Size() int {
	return s.cnt
}
func (s *intSrtSet) Contains(val int) bool {
	return findNode(val, s.root) != nil
}

// returns the lowest value currently in the set.  If there
// are no values, returns 0 and an error
func (s *intSrtSet) Min() (int, bool) {
	if s.Size() == 0 {
		return 0, false
	}
	n := s.root
	for n.l != nil {
		n = n.l
	}
	return n.v, true
}

// returns the highest value currently in the set.  If there
// are no values, returns 0 and an error
func (s *intSrtSet) Max() (int, bool) {
	if s.Size() == 0 {
		return 0, false
	}
	n := s.root
	for n.r != nil {
		n = n.r
	}
	return n.v, true
}

// returns val if it is in the set.
// if it is not in, returns the next highest value,
// if it is larger than any in the set, returns 0, and 
// an error.
func (s *intSrtSet) Ceil(val int) (int, bool) {
	n := findCeilNode(val, s.root)
	if n == nil {
		return 0, false
	}
	return n.v, true
}
func findCeilNode(val int, n *node) *node {
	if n == nil || n.v == val {
		return n
	}
	if n.v < val {
		return findCeilNode(val, n.r)
	}
	if n.l == nil {
		return n
	}
	c := findCeilNode(val, n.l)
	if c == nil {
		return n
	}
	return c
}

// returns val if it is in the set.
// if it is not in, returns the next lowest value,
// if it is smaller than any in the set, returns 0, and 
// an error.
func (s *intSrtSet) Floor(val int) (int, bool) {
	n := findFloorNode(val, s.root)
	if n == nil {
		return 0, false
	}
	return n.v, true
}
func findFloorNode(val int, n *node) *node {
	if n == nil || n.v == val {
		return n
	}
	if n.v > val {
		return findFloorNode(val, n.l)
	}
	if n.r == nil {
		return n
	}
	f := findFloorNode(val, n.r)
	if f == nil {
		return n
	}
	return f
}
func findNode(val int, r *node) *node {
	if r == nil || r.v == val {
		return r
	}
	if r.v < val {
		return findNode(val, r.r)
	}
	return findNode(val, r.l)
}

// removes the value val from the set.  Returns true
// if the value was in the set, false if it wasn't 
// in the set to begin with
func (s *intSrtSet) Remove(val int) bool {
	i := findNode(val, s.root)
	if i == nil {
		return false
	}
	s.deleteNode(i)
	s.mods++
	s.cnt--
	return true
}

// adds the value val to the set.  Returns true
// if the value was not in the set, false if it
// was already there
func (s *intSrtSet) Add(val int) bool {
	i := s.root
	if i == nil {
		s.root = mkNode(val, nil)
		s.mods++
		s.cnt++
		return true
	}
	for {
		if val == i.v {
			return false
		}
		if val < i.v {
			if i.l == nil {
				i.l = mkNode(val, i)
				s.mods++
				s.cnt++
				s.balanceAfterInsert(i.l)
				return true
			}
			i = i.l
		} else {
			if i.r == nil {
				i.r = mkNode(val, i)
				s.mods++
				s.cnt++
				s.balanceAfterInsert(i.r)
				return true
			}
			i = i.r
		}
	}
	// we never get here
	panic("Logic error. intSrtSet.Add fell out of loop")
}
func mkNode(val int, p *node) *node {
	n := node{false, p, nil, nil, val}
	return &n
}
func (s *intSrtSet) deleteNode(p *node) {
	s.mods++
	// this is a quick check that makes things more efficient.
	// if p is an full node (has both l & right child) we
	// can make the job easier by replacing p with the next
	// value, and then deleting that next one (which cannot be
	// a full node)
	if p.l != nil && p.r != nil {
		n := successor(p)
		p.v = n.v
		p = n
	}
	var repl *node
	if p.l != nil {
		repl = p.l
	} else {
		repl = p.r
	}
	switch {
	// p has a child replacement
	case repl != nil:
		repl.p = p.p
		switch {
		case p.p == nil:
			s.root = repl
		case p.p.l == p:
			p.p.l = repl
		default:
			p.p.r = repl
		}
		if !p.red {
			s.fixAfterDeletion(repl)
		}
	// p has no child replacement, and is the root
	case p.p == nil:
		s.root = nil
	// p has no child replacement and is not the root
	default:
		if !p.red {
			s.fixAfterDeletion(p)
		}
		if p.p != nil {
			switch {
			case p.p.l == p:
				p.p.l = nil
			case p.p.r == p:
				p.p.r = nil
			}
			p.p = nil
		}
	}
}
func (s *intSrtSet) fixAfterDeletion(x *node) {
	var y *node
	for x != s.root && !x.red {
		if x == x.p.l {
			y = x.p.r
			if isRed(y) {
				y.red = false
				x.p.red = true
				s.rotateLeft(x.p)
				y = x.p.r
			}
			if !isRed(y.l) && !isRed(y.r) {
				y.red = true
				x = x.p
			} else {
				if !isRed(y.r) {
					y.l.red = false
					y.red = true
					s.rotateRight(y)
					y = x.p.r
				}
				// doublecheck this line(s)
				y.red = isRed(x.p)
				x.p.red = false
				y.r.red = false
				s.rotateLeft(x.p)
				x = s.root
			}
		} else {
			y = x.p.l
			if isRed(y) {
				y.red = false
				x.p.red = true
				s.rotateRight(x.p)
				y = x.p.l
			}
			if !isRed(y.l) && !isRed(y.r) {
				y.red = true
				x = x.p
			} else {
				if !isRed(y.l) {
					y.r.red = false
					y.red = true
					s.rotateLeft(y)
					y = x.p.l
				}
				// doublecheck this line(s)
				y.red = isRed(x.p)
				x.p.red = false
				y.l.red = false
				s.rotateRight(x.p)
				x = s.root
			}
		}
	}
	x.red = false
}
func (s *intSrtSet) rotateRight(i *node) {
	l := i.l
	i.l = l.r
	if l.r != nil {
		l.r.p = i
	}
	l.p = i.p
	switch {
	case i.p == nil:
		s.root = l
	case i.p.r == i:
		i.p.r = l
	default:
		i.p.l = l
	}
	l.r = i
	i.p = l
}
func (s *intSrtSet) rotateLeft(i *node) {
	r := i.r
	i.r = r.l
	if r.l != nil {
		r.l.p = i
	}
	r.p = i.p
	switch {
	case i.p == nil:
		s.root = r
	case i.p.l == i:
		i.p.l = r
	default:
		i.p.r = r
	}
	r.l = i
	i.p = r
}
func (s *intSrtSet) balanceAfterInsert(x *node) {
	x.red = true
	var y, p, g *node
	for x != nil && x != s.root && x.p.red {
		p = x.p
		g = p.p
		if g.l == p {
			y = g.r
			if isRed(y) {
				p.red = false
				y.red = false
				g.red = true
				x = g
			} else {
				if x == p.r {
					x = p
					s.rotateLeft(x)
					p = x.p
					g = p.p
				}
				p.red = false
				g.red = true
				if g != nil {
					s.rotateRight(g)
				}
			}
		} else {
			y = g.l
			if isRed(y) {
				p.red = false
				y.red = false
				g.red = true
				x = g
			} else {
				if x == p.l {
					x = p
					s.rotateRight(x)
					p = x.p
					g = p.p
				}
				p.red = false
				g.red = true
				if g != nil {
					s.rotateLeft(g)
				}
			}
		}
	}
	s.root.red = false
}
func isRed(i *node) bool {
	// nulls are black by the deffinition of the rb tree
	return i != nil && i.red
}
func successor(i *node) (j *node) {
	if i == nil {
		return nil
	}
	if j = i.r; j != nil {
		for j.l != nil {
			j = j.l
		}
		return j
	}
	j = i.p
	for j != nil && j.r == i {
		i = j
		j = j.p
	}
	return j
}

// tests the sets for equality.  Sets are equal if all elements
// in each set are in the other set
func (s *intSrtSet) Equals(o *intSrtSet) (bool, error) {
	if &o == &s {
		return true, nil
	}
	if o == nil || o.cnt != s.cnt {
		return false, nil
	}
	my := s.Iterator()
	its := o.Iterator()
	b, e := my.HasNext()
	for b && e == nil {
		v, e1 := my.Next()
		v2, e2 := its.Next()
		switch {
		case e1 != nil:
			return false, e1
		case e2 != nil:
			return false, e2
		case v != v2:
			return false, nil
		}
		b, e = my.HasNext()
	}
	if e != nil {
		return false, e
	}
	return true, nil
}
func (s *intSrtSet) Clone() (clone *intSrtSet) {
	clone = new(intSrtSet)
	clone.root = deepNodeClone( s.root, nil )
	clone.mods = s.mods
	clone.cnt = s.cnt
	return clone
}
func deepNodeClone( n *node, p *node ) (c *node) {
// maybe change to static initializer to see what it does to bench
	c = new(node)
	c.red = n.red
	c.p = p
	c.l = deepNodeClone(n.l, c)
	c.r = deepNodeClone(n.r, c)
	c.v = n.v
	return c
}

type issIt struct {
	curr, prev            *node
	expMods int
	set                   *intSrtSet
}

func (it *issIt) initialize(s *intSrtSet) {
	it.set = s
	it.expMods = s.mods
	it.prev = nil
	it.curr = s.root
	if s.root != nil {
		for it.curr.l != nil {
			it.curr = it.curr.l
		}
	}
}
func (it *issIt) HasNext() (bool, error) {
	if it.expMods != it.set.mods {
		return false, errors.New("Concurrent Modification")
	}
	return it.curr != nil, nil
}
func (it *issIt) Next() (int, error) {
	if it.expMods != it.set.mods {
		return 0, errors.New("Concurrent Modification")
	}
	if it.curr == nil {
		return 0, errors.New("No Such Element")
	}
	it.prev = it.curr
	it.curr = successor(it.curr)
	return it.prev.v, nil
}
func (it *issIt) Remove() error {
	if it.expMods != it.set.mods {
		return errors.New("Concurrent Modification")
	}
	if it.prev == nil {
		return errors.New("No Such Element")
	}
	it.set.deleteNode(it.prev)
	it.expMods++
	it.prev = nil
	return nil
}

type intIter interface {
	// returns true if there is a next, false otherwise
	// returns false and a "Concurrent Modification" error if the underlying
	// data have been modified by some means other than this iterator
	HasNext() (bool, error)
	// returns the next int
	// returns a non-nil "Concurrent Modification" error if the underlying
	// data have been modified by some means other than this iterator
	// returns a non-nil "No Such Element" error if there is no next element
	Next() (int, error)
	// removes the last element returned from the underlying data without
	// causing subsequent modification errors
	// returns a non-nil "Concurrent Modification" error if the underlying
	// data have been modified by some means other than this iterator
	// returns a non-nil "No Such Element" error if no element has been
	// returned yet, or if it has already been removed
	Remove() error
}
type node struct {
	red   bool
	p     *node
	r     *node
	l     *node
	v int
}
