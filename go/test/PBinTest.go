package main

import (
	"../persistence/persistence"
	//"rand"
	"flag"
	"strconv"
	"fmt"
	"os"
	)

func main() {
	rounds := 10000
	//limit := int64(100)
	args := flag.Args()
	if len( args ) > 0 {
		rounds, _ = strconv.Atoi( args[ 0 ] )
	}
	f, _ := os.Open("/home/liam/go/src/project/test/PBinTestFile.dat", os.O_RDWR | os.O_CREAT, 0640 )
	defer func(){
		f.Close()
	}()
	bin, _ := persistence.NewPBin( f )
	a := make( []*testPable, rounds )
	lastRecId := bin.MaxID()
	var fi *os.FileInfo
	fmt.Println("created the bin, lastRecId =", lastRecId, ".");
	fmt.Println("number of items in bin at creation:", bin.Count() ,".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	for i := range a {
		nid := lastRecId+1 + int64(i)
		a[ i ] = newTestPable( nid, "testPable id " + strconv.Itoa64( nid ) + ", created iteration " + strconv.Itoa( i ) + ".", i )
	}
	fmt.Println("created", len(a), "storables.");
	for i := range a {
		bin.Store( a[ i ] )
	}
	fmt.Println("Stored 'em. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	bin.Rollback()
	fmt.Println("rolled back. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	for i := range a {
		bin.Store( a[ i ] )
	}
	fmt.Println("Stored 'em. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	bin.Commit()
	fmt.Println("committed. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	for i := 0; i < rounds; i += 2 {
		bin.Delete( a[ i ].ID() )
	}
	fmt.Println("deleted every other one. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	bin.Rollback()
	fmt.Println("rolled back. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	for i := 0; i < rounds; i += 2 {
		bin.Delete( a[ i ].ID() )
	}
	fmt.Println("deleted every other one. count is", bin.Count(), ".");
	bin.Commit()
	fmt.Println("committed. count is", bin.Count(), ".");
	for i := 0; i < rounds; i += 2 {
		bin.Delete( a[ i ].ID() )
	}
	fmt.Println("redeleted the ones that are already gone. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	bin.Commit()
	for i := 0; i < rounds; i += 3 {
		bin.Delete( a[ i ].ID() )
	}
	fmt.Println("deleted every third one. count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	bin.Commit()
	fmt.Println("Committed, count is", bin.Count(), ".");
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");
	bin.CommitAndPack()
	fi, _ = f.Stat()
	fmt.Println("Size is", fi.Size, ".");

}

type testPable struct{
	id int64
	name string
	val int
}
func (t *testPable) ID() (id int64){
	return t.id
}
func (t *testPable) SetID( id int64 ){
	t.id = id
}
func (t *testPable) Read( bs []byte ) (e os.Error){
	defer func(){
		e = os.NewError( fmt.Sprintln( "Error: ", recover() ) )
	}()
	iBytes := int(strconv.IntSize) / 8
	x := 0
	t.id = persistence.BToI64( bs[x:x+8] )
	x += 8
	strLen := persistence.BToI( bs[x:x+iBytes] )
	x += iBytes
	t.name = string(bs[x:x+strLen])
	x += strLen
	t.val = persistence.BToI( bs[x:x+iBytes] )
	return e
}
func (t *testPable) Write() (bs []byte) {
	iBytes := int(strconv.IntSize) / 8
	strLen := len(t.name)
	bs = make( []byte, 0, strLen + 8 + 2*iBytes )
	bs = append( bs, persistence.I64ToB( t.id )... )
	bs = append( bs, persistence.IToB( strLen )... )
	bs = append( bs, []byte(t.name)... )
	bs = append( bs, persistence.IToB( t.val )... )
	return bs
}
func (t *testPable) Name() (n string){
	return t.name
}
func (t *testPable) SetName( n string ){
	t.name = n
}
func (t *testPable) Val() (v int){
	return t.val
}
func (t *testPable) SetVal( v int ){
	t.val = v
}
func newTestPable( id int64, name string, val int ) (t *testPable){
	t = new(testPable)
	t.SetID( id )
	t.SetName( name )
	t.SetVal( val )
	return t
}
