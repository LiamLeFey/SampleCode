package mudclient

import (
	"net"
	"os"
	"strings"
	"bufio"
	"io"
	"fmt"
)


type GMC struct{
	Conn net.Conn
	Store *StoreMgr
	uiOut *bufio.Writer
	uiIn *bufio.Reader
}

func (gmc *GMC) SetUIOut( w io.Writer ){
	gmc.uiOut = bufio.NewWriter( w )
}
func (gmc *GMC) SetUIIn( r io.Reader ){
	gmc.uiIn = bufio.NewReader( r )
}
func (gmc *GMC) Run() (message string) {
	if gmc.conn != nil {
		go gmc.handleConnData()
	}
	for {
		input, e := readInput(gmc.uiIn)
		if e != nil {
			fmt.Fprintln( gmc.uiOut, "Error:", e )
			fmt.Fprintln( gmc.uiOut, "Shutting down." )
			break
		}
		if strings.HasPrefix( input, "/" ) {
			gmc.handleCommand( input )
			if input == "/quit" || input == "/Quit" || input == "QUIT" {
				break
			}
		} else {
			if gmc.conn != nil {
				gmc.conn.Write( []byte(input) )
			} else {
				fmt.Fprintln( gmc.uiOut,"No connection. Not sent.")
			}
		}
	}
	return "Thanks for playing! Hope you had fun. :-)"
}
func (gmc *GMC) handleCommand( s string ) {
	var cName string
	split := strings.SplitN( s, " ", 2 )
	if strings.HasPrefix( split[0], "/" ) {
		cName = split[0][1:]
	}else{
		cName = split[0]
	}
	c := cmd.Retrieve( cName )
	if c == nil {
		fmt.Fprintln( gmc.uiOut, "Unknown command:", cName )
	}
	split, e := c.Do( split, gmc )
	if e != nil {
		fmt.Fprintln( gmc.uiOut, "Error:", e )
	}
	for i := range split {
		fmt.Fprintln( gmc.uiOut, split[i] )
	}
}
// MARK
// currently just shuffles data to uiOut
// will need to run it through filters/triggers
func (gmc *GMC) handleConnData() {
	bs := make( []byte, 1024 )
	for {
		n, e := gmc.conn.Read( bs )
		if e != nil {
			if e == os.EOF {
				fmt.Fprintln( gmc.uiOut, "Connection Closed:", e )
				break
			}
			fmt.Fprintln( gmc.uiOut, "Error:", e )
		}
		gmc.uiOut.Write( bs[0:n] )
	}
}
