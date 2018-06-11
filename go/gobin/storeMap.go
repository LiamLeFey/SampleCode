package gobin

import (
	"errors"
)

// okay, the idea is that this will keep track of the bytes that
// are used, and the bytes that are free.  This is a bit less than
// trivial, because we need to keep track of things that are used
// currently, and things that are currently deleted, but still
// stored and need to be kept around pending possible rollback
type storeMap struct {
	// the set of all internal free space sizes
	fsSizes *intSrtSet
	// for each size in fsSizes, a mapping to the set of
	// all locations of free space of that size.
	fsMap map[int]*intSrtSet
	// a set of all borders between states
	// and one border at the end
	borders *intSrtSet
	// a set of all floor borders changed since last commit
	// so it should be a collection of all borders s.t.
	// states[ border ] == 0x01 || states[ border ] == 0x03
	changedFloors *intSrtSet
	// the keys are the borders (from borders set)
	// the values are the state of the bytes between this 
	// border and the next.
	// 0x00 = free, 0x01 = free on commit,
	// 0x02 = used, 0x03 = free on rollback
	states map[int]byte
}
// user func.
// initialize must be called to make the storeMap useful.
func (m *storeMap) initialize() {
	m.fsSizes = new(intSrtSet)
	m.fsMap = make(map[int]*intSrtSet)
	m.borders = new(intSrtSet)
	m.changedFloors = new(intSrtSet)
	m.states = make(map[int]byte)
	// add our invariant (unsized free space at top)
	m.borders.Add(0)
	m.states[0] = 0x00
}

// addFS manipulates fsSizes and fsMap
func (m *storeMap) addFS(size, loc int) {
	if m.fsSizes.Add(size) {
		m.fsMap[size] = new(intSrtSet)
	}
	m.fsMap[size].Add(loc)
}

// removeFS manipulates fsSizes and fsMap
func (m *storeMap) removeFS(size, loc int) {
	if fm, ok := m.fsMap[size]; ok {
		fm.Remove(loc)
		if fm.Size() == 0 {
			delete(m.fsMap, size)
			m.fsSizes.Remove(size)
		}
	}
}

// changes the state of the specified span
func (m *storeMap) setState(loc, length int, state byte) {
	// 0x00 = free, 0x01 = free on commit,
	// 0x02 = used, 0x03 = free on rollback
	if loc < 0 || length < 1 {
		panic(errors.New("storeMap.setState called with bad args."))
	}
	if max, _ := m.borders.Max(); loc >= max {
		if state == 0x00 {
			return
		}
		m.borders.Add(loc + length)
		m.states[loc+length] = 0x00
		m.addFS((loc+length)-max, max)
	}
	f, fe := m.borders.Floor(loc)
	if !fe {
		panic(errors.New("setState couldn't find floor of loc."))
	}
	c, ce := m.borders.Ceil(loc + 1)
	if !ce && c < loc+length {
		panic(errors.New("setState called on non-contiguous span."))
	}
	oldState := m.states[f]
	if oldState == state {
		return
	}
	switch {
	case f == loc && loc+length < c:
		m.states[loc] = state
		m.borders.Add(loc + length)
		m.states[loc+length] = oldState
		if oldState&0x01 != 0x00 {
			m.changedFloors.Add(loc + length)
			if state&0x01 == 0x00 {
				m.changedFloors.Remove(f)
			}
		}
		if oldState&0x01 == 0x00 && state&0x01 != 0x00 {
			m.changedFloors.Add(loc)
		}
		if oldState == 0x00 {
			m.removeFS(c-f, f)
			m.addFS(c-(loc+length), (loc + length))
		}
		if state == 0x00 {
			m.addFS(length, loc)
		}
	case f == loc && loc+length == c:
		m.states[loc] = state
		if oldState&0x01 != 0x00 && state&0x01 == 0x00 {
			m.changedFloors.Remove(f)
		}
		if oldState&0x01 == 0x00 && state&0x01 != 0x00 {
			m.changedFloors.Add(loc)
		}
		if oldState == 0x00 {
			m.removeFS(c-f, f)
		}
		if state == 0x00 {
			m.addFS(length, loc)
		}
	case f < loc && loc+length < c:
		m.borders.Add(loc)
		m.states[loc] = state
		m.borders.Add(loc + length)
		m.states[loc+length] = oldState
		if oldState&0x01 != 0x00 {
			m.changedFloors.Add(loc + length)
		}
		if state&0x01 != 0x00 {
			m.changedFloors.Add(loc)
		}
		if oldState == 0x00 {
			m.removeFS(c-f, f)
			m.addFS(loc-f, f)
			m.addFS(c-(loc+length), (loc + length))
		}
		if state == 0x00 {
			m.addFS(length, loc)
		}
	case f < loc && loc+length == c:
		m.borders.Add(loc)
		m.states[loc] = state
		if state&0x01 != 0x00 {
			m.changedFloors.Add(loc)
		}
		if oldState == 0x00 {
			m.removeFS(c-f, f)
			m.addFS(loc-f, f)
		}
		if state == 0x00 {
			m.addFS(length, loc)
		}
	default:
		panic(errors.New("setState doesn't know what to do."))
	}
	m.checkBorder(loc)
	m.checkBorder(loc + length)
	return
}
// checks a newly added border. If the border separates two adjacent
// sections of the same state, the border is removed. For instance,
// if free is called on the last used block, checkBorder will remove
// the end state (since it's no longer the end)
func (m *storeMap) checkBorder(border int) {
	max, _ := m.borders.Max()
	min, _ := m.borders.Min()
	if border == min {
		return
	}
	f, fe := m.borders.Floor(border - 1)
	if !fe {
		panic(errors.New("could not check border: couldn't find floor."))
	}
	fState, bf := m.states[f]
	bState, bb := m.states[border]
	if !(bb && bf) {
		panic(errors.New("could not check border: floor or border state missing."))
	}
	if fState != bState {
		return
	}
	m.borders.Remove(border)
	delete(m.states, border)
	if bState&0x01 != 0x00 {
		m.changedFloors.Remove(border)
	}
	if border == max {
		m.removeFS(border-f, f)
		return
	}
	if bState == 0x00 {
		c, ce := m.borders.Ceil(border + 1)
		if !ce {
			panic(errors.New("could not check border: couldn't find ceil."))
		}
		m.removeFS(c-border, border)
		m.removeFS(border-f, f)
		m.addFS(c-f, f)
	}
	return
}

// this resolves the free on commit and free on rollback states
// because commit and rollback are identical except for which
// state moves in which direction.
func (m *storeMap) resolveStates(toFree byte, toUse byte) {
	fs := m.changedFloors.Values()
	for i := range fs {
		c, b := m.borders.Ceil(fs[i] + 1)
		if !b {
			panic(errors.New("could not resolve state: couldn't find ceil."))
		}
		switch m.states[fs[i]] {
		case toFree:
			m.states[fs[i]] = 0x00
			m.addFS( c - fs[i], fs[i] )
		case toUse:
			m.states[fs[i]] = 0x02
		default:
			panic(errors.New("resolve state: bad state found in changedFloors."))
		}
		m.checkBorder(fs[i])
		m.checkBorder(c)
	}
	m.changedFloors = new(intSrtSet)
	return
}

// this returns a map from indexes to lengths
// it only captures used states, and cannot be called
// when the map is in an uncommitted state. It will
// panic if it is.
func (m *storeMap) getState() (st map[int]int) {
	// in general we'll have half as many states,
	// because we only  store used sections
	st = make(map[int]int, m.borders.Size()/2)
	bs := m.borders.Values()
	for i, b := range bs {
		switch m.states[b] {
		// 0x00 = free, 0x01 = free on commit,
		// 0x02 = used, 0x03 = free on rollback
		case 0x00:
		case 0x02:
			// this will panic if the top border is marked used.
			// as it should.
			st[b] = bs[i+1] - b
		case 0x01, 0x03:
			panic("storeMap.getState called with uncommitted values")
		}
	}
	return st
}
func (m *storeMap) commit() {
	m.resolveStates(0x01, 0x03)
}
func (m *storeMap) rollback() {
	m.resolveStates(0x03, 0x01)
}
// user func
func (m *storeMap) use(loc, length int) {
	if loc < 0 || length < 1 {
		panic(errors.New("failed assertion."))
	}
	f, _ := m.borders.Floor(loc)
	c, _ := m.borders.Ceil(loc + length)
	if t, _ := m.borders.Ceil(f + 1); m.states[f] != 0x00 || t != c {
		panic(errors.New("failed assertion."))
	}
	m.setState(loc, length, 0x03)
}
// user func
func (m *storeMap) free(loc, length int) {
	if loc < 0 || length < 1 {
		panic(errors.New("failed assertion."))
	}
	f, _ := m.borders.Floor(loc)
	c, _ := m.borders.Ceil(loc + length)
	if t, _ := m.borders.Ceil(f + 1); m.states[f] != 0x02 && m.states[f] != 0x03 || t != c {
		panic(errors.New("storeMap.free: non-contiguous or non used span."))
	}
	if m.states[f] == 0x02 { // used
		m.setState(loc, length, 0x01)
	} else { // m.states[ f ] == 0x03 (free at rollback)
		m.setState(loc, length, 0x00)
	}
}

// user func
// will find a location where there is at least size bytes available
// (this call does not mark the bytes used, for that call use())
func (m *storeMap) available(size int) (loc int) {
	s, _ := m.fsSizes.Ceil(size)
	if s == 0 {
		loc, _ = m.borders.Max()
		return loc
	}
	loc, _ = m.fsMap[s].Min()
	return loc
}
