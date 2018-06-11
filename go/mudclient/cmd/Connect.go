package cmd

import (
	"os"
	"strconv"
	"net"
	"project/mudclient"
)

func init() {
	Register( new( Connect ) )
}
type Connect byte

func (q *Connect) Name() string {
	return "connect"
}
func (q *Connect) Do(s []string, gmc *mudclient.GMC) (rv []string, e os.Error) {
	// the s arg should end with "host", "port" or "host:port"
	addr := s[len(s)-1]
	if _, e = strconv.Atoi( addr ); e != nil {
		addr = net.JoinHostPort( s[len(s)-2], s[len(s)-1] )
		e = nil
	}
	gmc.conn, e = net.Dial( "tcp", addr )
	return nil, e
}
