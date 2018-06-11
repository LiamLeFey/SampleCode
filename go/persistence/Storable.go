// Copyright 2010 Liam LeFey.  All rights reserved.
// Use of this code is governed by the GPL v3

// Persistable is a type that the PersistantCollection
// can store 

// the Write and Read must be compatable so that a struct can be
// written with Write, and an identical struct initialized from
// the data written by calling Read on the data.
package persistence

import "os"

type Storable interface {
	// returns any required data in a byte slice.
	Write() ([]byte)
	// initializes the struct from the byte slice.
	// may return an error if the passed []byte is incompatible
	Read( bs []byte ) (os.Error)
}
