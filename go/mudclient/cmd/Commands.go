package cmd

import (
	"project/mudclient"
	"os"
)
//var bin map[string] *Command
var bin = make( map[string] Command )

type Command interface {
	Do( []string, *mudclient.GMC ) ([]string, os.Error)
	Name() (string)
}

func Register( c Command ){
	bin[ c.Name() ] = c
}
func Get( name string ) (c Command) {
	return bin[ name ]
}
