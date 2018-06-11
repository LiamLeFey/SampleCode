package main

import (
	. "project/tools"
	"rand"
	"flag"
	"strconv"
	"os"
	)

func main() {
	rounds := 1000
	limit := int64(100)
	seed := int64(1)
	args := flag.Args()
	if len( args ) > 0 {
		rounds, _ = strconv.Atoi( args[ 0 ] )
	}
	if len( args ) > 1 {
		limit, _ = strconv.Atoi64( args[ 1 ] )
	}
	if len( args ) > 2 {
		seed, _ = strconv.Atoi64( args[ 2 ] )
	}
	r := rand.New( rand.NewSource( seed) )
	os.Stdout.WriteString("Beginning test " + strconv.Itoa(rounds) + " rounds in range -" + strconv.Itoa64(limit) + " to " + strconv.Itoa64(limit) + "\n")
	testObj := new( SI64Set )
	testObj.Initialize( 10 )
	var i, goodRemoves, badRemoves, goodPuts, badPuts int
	var j int64
	for i = 0; i < rounds; i++ {
		j = r.Int63n( limit*2 ) - limit
		if testObj.Contains( j ){
			if r.Intn( 2 ) == 1 {
				if testObj.Add( j ) {
					os.Stdout.WriteString("added " + strconv.Itoa64(j) +", which was already in, and got true back.\n")
				}
				if ! testObj.Contains( j ) {
					os.Stdout.WriteString("added "+strconv.Itoa64(j)+", but it's not there.\n" )
				}
				badPuts++
			} else {
				if ! testObj.Remove( j ) {
					os.Stdout.WriteString("removed "+strconv.Itoa64(j)+" which was already in, and got false back.\n")
				}
				if testObj.Contains( j ) {
					os.Stdout.WriteString("removed "+strconv.Itoa64(j)+", wbut it's still there.\n")
				}
				goodRemoves++
			}
		}else{
			if r.Intn( 2 ) == 1 {
				if ! testObj.Add( j ) {
					os.Stdout.WriteString("added " + strconv.Itoa64(j) +", which was not in, and got false back.\n")
				}
				if ! testObj.Contains( j ) {
					os.Stdout.WriteString("added "+strconv.Itoa64(j)+", but it's not there.\n" )
				}
				goodPuts++
			} else {
				if testObj.Remove( j ) {
					os.Stdout.WriteString("removed "+strconv.Itoa64(j)+" which was not in, and got true back.\n")
				}
				if testObj.Contains( j ) {
					os.Stdout.WriteString("removed "+strconv.Itoa64(j)+", wbut it's still there.\n")
				}
				badRemoves++
			}
		}
	}
	os.Stdout.WriteString("\nDone with random stuff.\n")
	os.Stdout.WriteString("Number of new Puts: " + strconv.Itoa(goodPuts)+".\n")
	os.Stdout.WriteString("Number of existing Puts (replacements): " + strconv.Itoa(badPuts)+".\n")
	os.Stdout.WriteString("Number of existing items removed: " + strconv.Itoa(goodRemoves)+".\n")
	os.Stdout.WriteString("Number of non-existing items removed: " + strconv.Itoa(badRemoves)+".\n")
	os.Stdout.WriteString("Set size should be new Puts - existing Removes = " + strconv.Itoa(goodPuts - goodRemoves) + ".\n")
	os.Stdout.WriteString("Set size is " + strconv.Itoa(testObj.Size()) + ".\n")
	os.Stdout.WriteString("Set contains:\n")
	it := testObj.Iterator()
	for it.HasNext() {
		j, e = it.Next()
		if e == nil {
			os.Stdout.WriteString(", " + strconv.Itoa64(j) )
		} else {
			os.Stdout.WriteString("error: " + e.String() )
		}
	}
	it = testObj.Iterator()
	for it.HasNext() {
		j, e = it.Next()
		it.Remove()
	}
}
