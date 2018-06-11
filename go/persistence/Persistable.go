// Copyright 2010 Liam LeFey.  All rights reserved.
// Use of this code is governed by the GPL v3

// Storable is a type that the PBin
// can store 

package persistence

type Persistable interface {
	Storable
	TypeHash() (hash int32)
	Id() (id int64)
	SetId( id int64 )
}
