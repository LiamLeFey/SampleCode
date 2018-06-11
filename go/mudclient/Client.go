package main

import (
	"project/mudclient"
	"bufio"
	"fmt"
	"os"
)


func main() {
	client := new(mudclient.GMC)
	// MARK add logic to handle command line options, including
	//  (potentially) other displays (windowed, ncurses, etc.)
	//  and connecting to a server immediately
	client.SetUIOut( os.Stdout )
	client.SetUIIn( os.Stdin )
	var e os.Error
	client.store, e = mudclient.NewStoreMgr( "~/.gmc" )
	if e != nil {
		fmt.Println( "Error creating storage:", e )
		return
	}
	fmt.Println( client.Run() )
}
func readInput( r *bufio.Reader ) (s string, e os.Error) {
	var bs []byte
	var b bool
	var b1 []byte
	b1, b, e = r.ReadLine()
	if e != nil {
		return string(b1), e
	}
	bs = make( []byte, len(b1) )
	copy( bs, b1 )
	for b {
		b1, b, e = r.ReadLine()
		bs = append( bs, b1... )
		if e != nil {
			return string(bs), e
		}
	}
	s = string(bs)
	return s, e
}
