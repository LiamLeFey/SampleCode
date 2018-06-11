package mudmap

import (
	"code.google.com/p/gobin"
)

// UNKN_DEST signifies a room listed in the exit list that we don't know
// the destination of yet.
const UNKN_DEST = 0

// the room object.  the main object for the mapper

type Room struct {
	RegionId int

	// map from command to destination room id
	// exit we've not yet traversed (UNKN_DEST)
	// hidden (non obvious) exits start with '.' like unix hidden files
	// triggers are hidden, and designated with a special format
	// string for trigger is ".<cost>.<trigger regexp>"
	// Wnen adding or removing exits, check if the status of entrance
	//   or exit room in Region has changed, and update Region if so.
	Exits map[string]int

	SDescId int
	sDesc   *string // unexported to prevent gobin from storing it.
	LDescId int
	lDesc   *string // unexported to prevent gobin from storing it.

	Anomalous  bool
	AnomalyIds []int64
}

func (r *Room) ShortDesc(bin gobin.Gobin) (sd string) {
	if r.sDesc == nil {
		if e := bin.Decode(&sd, r.SDescId); e != nil {
			return e.Error()
		}
		r.sDesc = &sd
	}
	return *r.sDesc
}
func (r *Room) LongDesc(bin gobin.Gobin) (ld string) {
	if r.lDesc == nil {
		if e := bin.Decode(&ld, r.LDescId); e != nil {
			return e.Error()
		}
		r.lDesc = &ld
	}
	return *r.lDesc
}
