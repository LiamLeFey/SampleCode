package tools

import (
	"testing"
	"math/rand"
//	"sort"
)
func TestIntSrtSet(t *testing.T) {
	s := new(IntSrtSet)
	if !s.Add( 1 ) {
		t.Error( "s.Add( 1 ) on empty set returned false." )
	}
	if s.Add(1) {
		t.Error( "s.Add( 1 ) returned true the second time." )
	}
	if s.Remove(10) {
		t.Error( "remove 10 returned true (10 not in).")
	}
	if !s.Add( 10 ) {
		t.Error( "s.Add( 10 ) returned false." )
	}
	if s.Size() != 2 {
		t.Error( "size not 2.")
	}
	if !s.Remove(1) {
		t.Error( "remove 1 returned false (1 was in).")
	}
	if s.Size() != 1 {
		t.Error( "size not 1.")
	}
	if !s.Remove(10) {
		t.Error( "remove 10 returned false (10 was in).")
	}
	if s.Size() != 0 {
		t.Error( "size not 0.")
	}
}
func BenchmarkIntSrtSet_GrowToN(b *testing.B) {
	s := new(IntSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(i)
	}
	a := s.Values()
	if sort.IntsAreSorted(a) {
		b.Log("ints sorted after benchmark")
	} else {
		b.Error("ints not sorted!")
	}
}
func BenchmarkIntSrtSet_GrowDelete(b *testing.B) {
	s := new(IntSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(i)
	}
	a := s.Values()
	if sort.IntsAreSorted(a) {
		b.Log("ints sorted after added")
	} else {
		b.Error("ints not sorted after added!")
	}
	for i := 0; i < b.N; i++ {
		if !s.Remove(i) {
			b.Error("remove returned false when removing existing int")
		}
	}
	if s.Size() != 0 {
		b.Error("Size() != 0 after removing all elements")
	}
	a = s.Values()
	if len(a) != 0 {
		b.Error("Values returned non-empty slice after removing all elements")
	}
}
func BenchmarkIntSrtSet_GrowToN_Rand(b *testing.B) {
	s := new(IntSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(rand.Int())
	}
	a := s.Values()
	if sort.IntsAreSorted(a) {
		b.Log("ints sorted after benchmark")
	} else {
		b.Error("ints not sorted!")
	}
}
func BenchmarkIntSrtSet_GrowDelete_Rand(b *testing.B) {
	s := new(IntSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(rand.Int())
	}
	a := s.Values()
	if sort.IntsAreSorted(a) {
		b.Log("ints sorted after added")
	} else {
		b.Error("ints not sorted after added!")
	}
	for i := 0; i < b.N; i++ {
		s.Remove(rand.Int())
	}
	a = s.Values()
	if sort.IntsAreSorted(a) {
		b.Log("ints sorted after removed")
	} else {
		b.Error("ints not sorted after removed!")
	}
}
