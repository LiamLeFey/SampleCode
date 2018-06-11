package connection

import (
	"os"
	"net"
	"flag"
	//"strconv"
)

func main() {
	flag.Parse()
	nArg := flag.NArg()
	var hostName string
	var port string
	if nArg < 1 {
		hostName = "3k.org"
	} else {
		hostName = flag.Arg( 0 );
	}
	if nArg < 2 {
		port = "23"
	} else {
		port = flag.Arg( 1 );
	}
	addr := hostName + ":" + port
	tcpAddr, error := net.ResolveTCPAddr("tcp", addr )
	if error != nil {
		os.Stdout.WriteString( error.String() )
	} else {
		os.Stdout.WriteString( tcpAddr.String() )
	}
	os.Stdout.WriteString("\n")
	os.Stdout.WriteString("Attempting to connect")
	os.Stdout.WriteString("\n")
	tcpConn, error := net.DialTCP( "eth0", nil, tcpAddr )
	if error != nil {
		os.Stdout.WriteString( error.String() )
	} else {
		os.Stdout.WriteString( "connection succeeded" )
	}
	os.Stdout.WriteString("\n")
	os.Stdout.WriteString("Attempting to read")
	os.Stdout.WriteString("\n")
	tcpConn.SetReadTimeout( 5e9 )
	buffer := make( []byte, 1024 )
	byteCount, error := tcpConn.Read( buffer )
	if error != nil {
		os.Stdout.WriteString( error.String() )
	} else {
		os.Stdout.WriteString( string(buffer[0:byteCount]) )
	}
	os.Stdout.WriteString("\n")
	os.Stdout.WriteString("Attempting to close")
	os.Stdout.WriteString("\n")
	error = tcpConn.Close()
	if error != nil {
		os.Stdout.WriteString( error.String() )
	} else {
		os.Stdout.WriteString( "close succeeded" )
	}
	os.Stdout.WriteString("\n")

}
