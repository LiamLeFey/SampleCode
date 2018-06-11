package cmd

import (
	"os"
	"project/mudclient"
)

func init(){
	Register( new( Quit ) )
}
type Quit byte

func (q *Quit) Name() string {
	return "quit"
}
func (q *Quit) Do( s []string, gmc *mudclient.GMC ) (rv []string, e os.Error) {
	if gmc.conn != nil {
		e = gmc.conn.Close()
	}
	return
}
