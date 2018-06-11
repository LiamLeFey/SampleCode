package tools

import (
	"sort"
	"hash/crc32"
)

var crcTable = crc32.MakeTable( crc32.Koopman )

func Hash( s string ) int32 {
	return int32(crc32.Checksum( []byte(s), crcTable ))
}
func HashA( a []string ) int32 {
	sort.Strings(a)
	b := make( []byte, 0 )
	for i := range a {
		b = append( b, []byte(a[ i ])... )
		// this is to avoid having {"ab"} hash the same as {"a","b"}
		b = append( b, 0x00 )
	}
	return int32(crc32.Checksum( b, crcTable ))
}
