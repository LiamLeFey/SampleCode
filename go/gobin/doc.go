
/*
Package gobin allows us to store and retrieve values using encode/gob
in a non-linear manner.

In go, the encoding and decoding with Gob is fast and efficient, but
it expects a sequential stream. Plucking data from the middle of the
stream could be problematic if the Decode function is reading a
position in the stream beyond the first encoding of that particular
type, the Decoder will not have read the Type encoding yet, and won't
know what to do with the data.

Goals:

Simplicity. The aim is to have a package that can be as easy to use
as encode/gob.

Checkpointing via Commit and Rollback functions. 

Failsafety. Since this is intended to store long-lived data, every
effort has been made to keep the data in a consistent state. Even
if the backing media is a USB drive that gets unplugged mid-write,
you should be able to read the data back in. You won't have the
data that was being written, but it should still be in the state it
was in at the last commit before the failed write.  This includes
the previous version of your value if the write was an update on an
already stored value.

Names. The ability to store select items under a name. This helps
when reloading your values from a cold start. For instance, if
you're storing a tree structure, it's helpful to be able to start
by loading the "root". For other collections, you could load
"array_of_ids"

NOT YET IMPLEMENTED
Defrag. There is necessarily some redundant or unused space in the
storage. This is required in order to keep rollback data around
until a commit. It is then reclaimed, but just like with disk
fragmentation, gaps will creep in. This can be packed periodically,
which will reduce the size, and may improve performance.

  
Restrictions:

Since we're using the gob package, the things you store in a gobin
are limited to the things that the gob package can store.
Specifically, you cannot store functions or channels. (No. Not
even if you hide them in a struct.) Well, maybe if you use
GobEncoder and do it yourself.

We use int for the index into the backing store, so you are
limited to MaxInt bytes of storage. So the maximum size of the
store will vary with the bit size of int.

This is not a threadsafe item. If you want threadsafe access, you
need to wrap it in a locking mechanism to ensure that multiple writes
or reads and writes do not occur at the same time. It's on the
wishlist.

Since we're using gobs, only exported fields are encoded and
decoded. If you must have unexported (private) fields, one
way to handle this is to implement GobEncoder and GobDecoder:
	type MyObject struct {
		superSecreEncapsulatedData bool
		...
		PublicStuff string
	}
	func (o MyObject) GobEncode() ([]byte, error) {
		//write it yourself
	}
The drawback to this is that it will probably be slower and
result in a larger data footprint than what gobs can do. And you
have to do it yourself, rather than let gob handle it

Other ways to handle it are

	type myState{
		ExportedData1 bool
		...
		ExportedDataN string
	}
And then call 
	func (o MyObject) Gobinize( g Gobin ) {
		g.Store( myState{o.superSecretEncapsulatedData, ..., o.PublicStuff} )
	}
	o.Gobinize(g)
instead of
	g.Store( o )

or

	func (o MyObject) getState() myState {
		return myState{o.superSecretEncapsulatedData, ..., o.PublicStuff}
	}
	g.Store( o.getState() )

This is also useful if the state of an object can be stored more compactly
than the internal representation. (an example is the storeMap in the gobin
package)

The drawback to this is that it requires more complexity than using
the gob package capabilities.
*/
package gobin
