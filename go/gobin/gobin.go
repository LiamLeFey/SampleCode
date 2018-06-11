package gobin

import (
	"bytes"
	"encoding/gob"
	"errors"
	"io"
	"reflect"
	"strings"
)

// the internal workings are the following:
// - We map from names to ids. This is optional but useful to help
//   the user find items from a cold start
// - We map from ids to indexes, to be able to return the item the
//   user wants.
// - We keep track of a usage map of the storage. This is stored at
//   the end of the store, and overwrites itself, it is discoverable
//   if there is data corruption
// - We keep track of a set of types, to ensure that before we read
//   or write a gob, we have read the Type definition so the
//   gob.Decoder knows what to do with it.
// - We separate the gob.Encoder from the backing store, because we
//   don't know how much data we will be writing until we write it.
//   - the process is:
//     - encode to our buffer
//     - measure the []byte that was written
//     - find space using the usage map
//     - write the []byte to storage
// - Because we don't know how much data a thing takes until after
//   writing it, we can't record the storage map in itself. (once we
//   write it, we'd have update it, which might change its length,
//   which we'd have update, which might...) So it's tacked on at
//   the end, and assumed corrupted any time we write to the store

// Each piece of data stored in the header is written as an int.
// potential max size is 8 bytes for data, 1 for length, and 1
// for type. Total 10 bytes (unlikely to be all used)
//
// the storage layout is as follows:
//
//          gobin    gobin    smap                        data
//          state    state    offset                      end
// toggle   offset   offset   0 means  data               map
// 0 or 1:    0        1      dirty    start              start
// |        |        |        |        |                  |
// +--------+--------+--------+--------+- - - - - - - - - +
// 0        1        2        3        4                  ?
// 0        0        0        0        0                  ?

const (
	OFF_ST_0 = 10
	OFF_ST_1 = 20
	OFF_SM = 30
	OFF_DATA = 40
)

// The Gobin maintains a backing store into which you can put things.
// The results are undefined if other processes are reading or writing
// to the stored data.
type Gobin struct {

	// the idMap and nameMap are encapsulated in binState when stored
	idMap   map[int]int    // map from id to store offset
	nameMap map[string]int // map from names to an id

	smap *storeMap // record of free, used, and conditionally free/used space

	// stuff below this line is not stored in the store; it's initialized
	// from the data that is passed to the constructor, built from what is
	// in the store, or it's just transient

	// a map of types we have seen with this instance
	types map[reflect.Type]bool

	encoder   *gob.Encoder
	decoder   *gob.Decoder
	store     *rwsWrap      // wrap the ReadWriteSeeker
	buffer    *bytes.Buffer // buffer for measuring written bytes
	committed bool          // changes to data
	smapDirty bool          // the stored smap may have been overwritten
	maxId     int
}

// Creates a gobin, using the ReadWriteSeeker as the store.
// If the ReadWriteSeeker already has data stored from a previous
// use of gobin, the gobin will initialize with this data. If the
// ReadWriteSeeker is empty, it will initialize it for use. If the
// ReadWriteSeeker is not empty and contains data from some other
// source, this will probably panic. Unless you're really lucky,
// and even then, it's not likely to be useful.
func New(store io.ReadWriteSeeker) (b *Gobin, err error) {
	b = new(Gobin)

	b.store = &rwsWrap{store, [1]byte{0}, false, nil}
	b.buffer = new(bytes.Buffer)
	b.store.buf = b.buffer
	b.encoder = gob.NewEncoder(b.buffer)
	b.decoder = gob.NewDecoder(b.store)
	b.types = make(map[reflect.Type]bool)
	b.sendTypeThrough(new(gobinState))
	hBuff := make([]byte, OFF_DATA, OFF_DATA)
	store.Seek(0, 0)
	n, e := store.Read(hBuff) // we don't use hBuff, just verifying size
	if n < OFF_DATA && (e == nil || e == io.EOF) {
		err = b.initializeStore()
	} else {
		err = b.inflateFromStore()
	}
	if err != nil {
		return nil, err
	}
	for k, _ := range b.idMap {
		if b.maxId < k {
			b.maxId = k
		}
	}
	return b, nil
}
func (bin *Gobin) initializeStore() (err error) {
	bin.smap = new(storeMap)
	bin.smap.initialize()
	bin.smap.use(0, OFF_DATA) // reserve space for the header
	bin.idMap = make(map[int]int)
	bin.nameMap = make(map[string]int)
	err = bin.encoder.Encode(0)
	if err != nil {
		return err
	}
	err = bin.writeBufferAt(0)
	if err != nil {
		return err
	}
	err = bin.Commit()
	return err
}
func (bin *Gobin) inflateFromStore() (err error) {
	err = bin.loadState()
	if err != nil {
		return err
	}
	err = bin.loadSmap()
	return err
}

// Decode retrieves a value by its id. The interface passed
// in must be a pointer or it won't work. Just like gob.Decode.
func (bin *Gobin) Decode(v interface{}, id int) error {
	// fast and easy first:
	var loc int
	var b bool
	if loc, b = bin.idMap[id]; !b {
		return errors.New("Invalid id for Decode.")
	}
	t := reflect.TypeOf(v)
	if _, b = bin.types[t]; !b {
		bin.sendTypeThrough(v)
	}
	bin.store.Seek(int64(loc), 0)
	return bin.decoder.Decode(v)
}

// IdForName retrieves an id by the name set with NameId.
// So
// 	b.NameId( "root", i )
// 	id, _ := b.IdForName( "root" )
// 	b.IdForName( v, id )
// is analogous to:
// 	b.IdForName( v, i )
//
// but the former is easier to use while bootstraping a process
// or if you forget i between line 1 and line 2
func (bin *Gobin) IdForName(name string) (i int, b bool) {
	i, b = bin.nameMap[name]
	return i, b
}

// NameId names an id for retrieval with IdForName. If there
// is already an id stored with the name, the previously named
// id is un-named (though its value is still stored under its id.)
// There is no protection from naming invalid or unused ids. 
func (bin *Gobin) NameId(name string, id int) {
	bin.nameMap[name] = id
	bin.committed = false
	return
}

// removes a name id pair
func (bin *Gobin) UnNameId(name string) {
	delete(bin.nameMap, name)
	bin.committed = false
	return
}

// removes a value
func (bin *Gobin) Delete(id int) (err error) {
	loc := bin.idMap[id]
	_, err = bin.store.Seek(int64(loc), 0)
	if err != nil {
		return err
	}
	err = bin.decoder.Decode(nil)
	// It is an error to make gob decode a value before the decoder
	// has decoded the wiretype. So if we delete (by decoding to nil) a
	// value by id before we have loaded, encoded, or stored any values
	// of that type, decode returns an error. It does, however, read the
	// message first, so we can currently safely ignore the error.
	// If the implementation of gob changes, this may break.
	if err != nil {
		// currently the only occurance of the word undefined in the package
		// gob is the error we can (for now) safely ignore.
		// So broken.
		if !strings.Contains(err.Error(), "undefined") {
			return err
		}
	}
	var end int64
	end, err = bin.store.Seek(0, 1) // returns current offset
	if err != nil {
		return err
	}
	bin.smap.free(loc, int(end)-loc)
	delete(bin.idMap, id)
	bin.committed = false
	return nil
}

// Encode stores a value for the first time. It returns an id,
// which you can use to retrieve the value later.
func (bin *Gobin) Encode(v interface{}) (id int, err error) {
	bin.maxId++
	id = bin.maxId
	return id, bin.EncodeId(v, id)
}

// EncodeId stores a value under the given id. If there is already
// an item with that id, the former item is forgotten (deleted).
// Any int is valid, and gobin will not assign (via Encode) a
// negative int, so you can store values with id < 0 as special
// key values rather than using the NameId/IdForName funcs.
func (bin *Gobin) EncodeId(v interface{}, id int) (err error) {
	if _, b := bin.idMap[id]; b {
		err = bin.Delete(id)
		if err != nil {
			return err
		}
	}
	loc, err := bin.put(v)
	if err != nil {
		return err
	}
	bin.idMap[id] = loc
	bin.committed = false
	return nil
}

// this does the actual work of storing the data in the store
func (bin *Gobin) put(v interface{}) (loc int, err error) {
	err = bin.markSmapDirty()
	if err != nil {
		return 0, err
	}
	t := reflect.TypeOf(v)
	if _, b := bin.types[t]; !b {
		err = bin.sendTypeThrough(v)
		if err != nil {
			return 0, err
		}
	}
	err = bin.encoder.Encode(v)
	if err != nil {
		return 0, err
	}
	l := bin.buffer.Len()
	loc = bin.smap.available(l)
	bin.smap.use(loc, l)
	err = bin.writeBufferAt(loc)
	if err != nil {
		return 0, err
	}
	return loc, nil
}
// Rollback returns the gobin to the state of the last commit.
// Any new values are forgotten, any changes to stored values
// are reverted.
func (bin *Gobin) Rollback() (err error) {
	if bin.committed {
		return nil
	}
	// rebuild gobinState from store
	bin.loadState()
	// rollback smap
	bin.smap.rollback()
	// store smap
	bin.writeSmap()
	bin.committed = true
	return nil
}
// Commit commits the changes. Changes made to stored values
// and new values are not permanent until commit is called.
// Once committed, changes will be seen by new gobins created
// using the store.
func (bin *Gobin) Commit() (err error) {
	if bin.committed {
		return nil
	}
	var loc, toggle int
	// find out which is the 'live' index
	bin.store.Seek(0, 0)
	err = bin.decoder.Decode(&toggle)
	if err != nil {
		return err
	}
	// store gobinState
	st := new(gobinState)
	st.IdMap = bin.idMap
	st.NameMap = bin.nameMap
	loc, err = bin.put(st)
	if err != nil {
		return err
	}
	// store gobinState loc
	// note that we write to the 'non-live' block
	err = bin.encoder.Encode(loc)
	if err != nil {
		return err
	}
	if toggle == 0 {
		err = bin.writeBufferAt(OFF_ST_1)
	} else {
		err = bin.writeBufferAt(OFF_ST_0)
	}
	if err != nil {
		return err
	}
	// swap pointer blocks.
	if toggle == 0 {
		err = bin.encoder.Encode(1)
	} else {
		err = bin.encoder.Encode(0)
	}
	if err != nil {
		return err
	}
	err = bin.writeBufferAt(0)
	if err != nil {
		return err
	}
	// commit smap
	bin.smap.commit()
	// store smap
	err = bin.writeSmap()
	if err != nil {
		return err
	}
	bin.committed = true
	return nil
}

// Analagous to gob.Register, only use it if you're pulling things
// as iterfaces (you want to receive an io.Reader or somesuch)
func (bin *Gobin) Register(value interface{}) (err error) {
	gob.Register(value)
	return bin.sendTypeThrough(value)
}
// Sends a type from the Encoder to the Decoder to make sure
// they have the types compiled.
func (bin *Gobin) sendTypeThrough(i interface{}) (err error) {
	reflect.New(reflect.TypeOf(i))
	err = bin.encoder.Encode(i)
	if err != nil {
		return err
	}
	bin.store.readFromBuffer = true
	err = bin.decoder.Decode(nil)
	bin.store.readFromBuffer = false
	if err != nil {
		return err
	}
	bin.types[reflect.TypeOf(i)] = true
	return nil
}
// Pulls the state from the store
func (bin *Gobin) loadState() (err error) {
	var i int
	bin.store.Seek(0, 0)
	err = bin.decoder.Decode(&i)
	if err != nil {
		return err
	}
	if i == 0 {
		_, err = bin.store.Seek(OFF_ST_0, 0) // see store layout
	} else {
		_, err = bin.store.Seek(OFF_ST_1, 0) // see store layout
	}
	if err != nil {
		return err
	}
	err = bin.decoder.Decode(&i)
	if err != nil {
		return err
	}
	_, err = bin.store.Seek(int64(i), 0)
	gs := new(gobinState)
	err = bin.decoder.Decode(&gs)
	if err != nil {
		return err
	}
	bin.idMap = gs.IdMap
	bin.nameMap = gs.NameMap
	return nil
}
// Tries to pull the storeMap from the store, if for some reason
// it can't, it builds it by scanning the store.
func (bin *Gobin) loadSmap() (err error) {
	if bin.smapDirty {
		return bin.buildSmap()
	}
	_, err = bin.store.Seek(OFF_SM, 0) // see store layout
	var i int
	err = bin.decoder.Decode(&i)
	if err != nil {
		return err
	}
	if i == 0 {
		return bin.buildSmap()
	}
	if i < OFF_DATA {
		panic("gobin loadmap found map offset < OFF_DATA. data store corrupt")
	}
	_, err = bin.store.Seek(int64(i), 0)
	st := make(map[int]int)
	err = bin.decoder.Decode(&st)
	if err != nil {
		// try to build it anyway, and return the error regardless
		e2 := bin.buildSmap()
		if err != nil {
			return e2
		}
		return err
	}
	bin.smap = new(storeMap)
	bin.smap.initialize()
	for key, val := range st {
		bin.smap.use(key, val)
	}
	bin.smap.commit()
	bin.smapDirty = false
	return nil
}

// buildSmap builds the smap from the data. For use when the
// smap could not be loaded from the store for some reason
// it will be slower than loadSmap because it has to scan the
// entire storage.
// 
// bin.idMap must already be loaded for it to
// have a chance.
func (bin *Gobin) buildSmap() (err error) {
	bin.smap = new(storeMap)
	bin.smap.initialize()
	var loc int
	var end int64
	for _, loc = range bin.idMap {
		_, err = bin.store.Seek(int64(loc), 0)
		if err != nil {
			return err
		}
		err = bin.decoder.Decode(nil) // decoding a nil v discards the results,
		                              // but still reads
		if err != nil {
			return err
		}
		end, err = bin.store.Seek(0, 1) // get current offset
		if err != nil {
			return err
		}
		bin.smap.use(loc, int(end)-loc)
	}
	bin.smap.commit()
	err = bin.writeSmap()
	return err
}

// write out the smap at the end
func (bin *Gobin) writeSmap() (err error) {
	loc, b := bin.smap.borders.Max()
	if !b {
		panic("gobin.writeSmap called with empty smap (logic error)")
	}
	st := bin.smap.getState()
	err = bin.encoder.Encode(st)
	if err != nil {
		return err
	}
	err = bin.writeBufferAt(loc)
	if err != nil {
		return err
	}
	err = bin.encoder.Encode(loc)
	if err != nil {
		return err
	}
	err = bin.writeBufferAt(OFF_SM)
	if err != nil {
		return err
	}
	bin.smapDirty = false
	return nil
}
func (bin *Gobin) markSmapDirty() (err error) {
	if bin.smapDirty {
		return nil
	}
	err = bin.encoder.Encode(0)
	if err != nil {
		return err
	}
	err = bin.writeBufferAt(OFF_SM)
	if err != nil {
		return err
	}
	bin.smapDirty = true
	return nil
}
func (bin *Gobin) writeBufferAt(loc int) (err error) {
	_, err = bin.store.Seek(int64(loc), 0)
	if err != nil {
		return err
	}
	l := bin.buffer.Len()
	var n, w int
	bs := bin.buffer.Bytes()
	for w < l {
		n, err = bin.store.Write(bs[w:])
		if err != nil {
			return err
		}
		w += n
	}
	bin.buffer.Truncate(0)
	return nil
}

type gobinState struct {
	IdMap   map[int]int
	NameMap map[string]int
}

// we prevent gob.Decoder from wrapping our store with a buffer by
// providing a ByteReader method. Buffering is bad because we jump
// all over for reads. (and writes).
// 
// We also provide a way to read directly from the byte buffer that
// the Encoder uses. That way, we can bypass the store when we just
// want to send a new type through the *coder s
type rwsWrap struct {
	io.ReadWriteSeeker
	b              [1]byte // avoid allocating for each ReadByte call
	readFromBuffer bool    // bypass rws for sending types
	buf            *bytes.Buffer
}

func (r rwsWrap) ReadByte() (c byte, err error) {
	n, err := r.Read(r.b[0:1])
	if n != 1 {
		return 0, err
	}
	return r.b[0], err
}
func (r rwsWrap) Read(p []byte) (n int, err error) {
	if r.readFromBuffer {
		n, err = r.buf.Read(p)
	} else {
		n, err = r.ReadWriteSeeker.Read(p)
	}
	return n, err
}
