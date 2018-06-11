package main

import (
	"project/persistence"
	"project/mudclient/mudmap"
	"project/tools"
	//"flag"
	"strings"
	"strconv"
	"bufio"
	"fmt"
	"os"
	)

var stdin = bufio.NewReader( os.Stdin )

var bin *persistence.PBin
var si *mudmap.PStringFinder
var ri *mudmap.RoomFinder

func main() {
	binf, _ := os.OpenFile("/home/liam/go/src/project/test/MudMapTest.dat", os.O_RDWR | os.O_CREATE, 0640 )
	sif, _ := os.OpenFile("/home/liam/go/src/project/test/StringIndex.dat", os.O_RDWR | os.O_CREATE, 0640 )
	rif, _ := os.OpenFile("/home/liam/go/src/project/test/RoomIndex.dat", os.O_RDWR | os.O_CREATE, 0640 )
	defer func(){
		bin.Commit()
		binf.Close()
		sif.Seek( 0, 0 )
		sif.Write( si.Write() )
		sif.Close()
		rif.Seek( 0, 0 )
		rif.Write( ri.Write() )
		rif.Close()
	}()
	bin, _ = persistence.NewPBin( binf )
	si = new( mudmap.PStringFinder )
	stat, _ := sif.Stat()
	sib := make( []byte, int(stat.Size) )
	n := 0
	for n < len( sib ) {
		r, e := sif.Read( sib[ n: ] )
		if e != nil { break }
		n += r
	}
	si.Read( sib )
	mudmap.SetPStringFinder( bin, si )
	ri = new( mudmap.RoomFinder )
	stat, _ = rif.Stat()
	rib := make( []byte, int(stat.Size) )
	n = 0
	for n < len( rib ) {
		r, e := rif.Read( rib[ n: ] )
		if e != nil { break }
		n += r
	}
	ri.Read( rib )
	lastRecId := bin.MaxID()
	var fi *os.FileInfo
	fmt.Println("created the bin, lastRecId =", lastRecId, ".")
	fmt.Println("number of items in bin at creation:", bin.Count() ,".")
	fi, _ = binf.Stat()
	fmt.Println("Size is", fi.Size, ".")
F:	for {
		printMainMenu()
		input := getUserInput()
		switch input {
		case "S", "s":
			printMapSummary()
		case "R", "r":
			setCurrentRoom()
		case "K", "k":
			createRegion()
		case "C", "c":
			createRoom()
		case "X", "x":
			break F
		}
	}
}

func printMainMenu() {
	fmt.Println("Main Menu:")
	fmt.Println("S: Print Summary")
	fmt.Println("R: Set current room")
	fmt.Println("C: create new room")
	fmt.Println("K: create new region")
	fmt.Println("X: Exit")
	fmt.Println("")
}
func printMapSummary() {
	roomIDs := bin.IDsOfType( new( mudmap.Room ).TypeHash() )
	region2rooms := make( map[ int64 ] *tools.SI64Set, 3 )
	var r *mudmap.Room
	for i := range roomIDs {
		r, _ = mudmap.GetRoom( roomIDs[i], bin )
		if region2rooms[ r.RegionId ] == nil {
			region2rooms[ r.RegionId] = tools.NewSI64Set( 3 )
		}
		region2rooms[ r.RegionId ].Add( r.ID() )
	}
	for k, v := range region2rooms {
		reg, _ := mudmap.GetRegion( k, bin )
		fmt.Println( "______Region ", k, ": ", reg.Name, "______" )
		vs := v.Values()
		for i := range vs {
			r, _ = mudmap.GetRoom( vs[i], bin )
			fmt.Println( "Room ", r.ID(), ": ", r.ShortDesc( bin ) )
			for ex, d := range r.Exits {
				fmt.Print( "\t", ex, "\t-> " )
				if d == mudmap.UNKN_DEST {
					fmt.Println( "UNKN_DEST" )
				} else {
					tr, _ := mudmap.GetRoom( d, bin )
					fmt.Println( d, ":", tr.ShortDesc(bin) )
				}
			}
			fmt.Println( "---------------------------------" )
		}
	}
}
func setCurrentRoom() {
	r := getValidRoom()
	if r == nil { return }
	id := r.ID()
	printRoomMenu( id )
F:	for {
		s := getUserInput()
		switch s {
		case "l", "L":
			printLookDisplay( r )
		case "glance":
			printGlanceDisplay( r )
		case "/where":
			// It seems silly to pass the room, but the codepath
			// is the important part... In the real game, you would
			// be able to find the information from the environment
			findCurrentRoom( r )
		case "/x", "/X":
			break F
		case "/exit":
			editExit( r )
		case "?", "help":
			printRoomMenu( id )
		default:
			if strings.HasPrefix( s, "/go" ) {
				split := strings.Split( s, " ", 2 )
				destID, _ := strconv.Atoi64( split[1] )
				p := mudmap.GetPath( id, destID, bin )
				for i := range p.Steps {
					r = handleTraversal(r, p.Steps[i].Text)
				}
				id = r.ID()
				break
			}
			if _, ok := r.Exits[ s ]; ok {
				r = handleTraversal( r, s )
				id = r.ID()
				break
			}
			fmt.Println("what?")
		}
	}
}
func printRoomMenu( id int64 ) {
	fmt.Printf("Room %d Menu:\n", id)
	fmt.Println("l: look (shows short and long descriptions and exits")
	fmt.Println("glance: shows short description and exits")
	fmt.Println("<dir>: traverses the exit if a destination exists")
	fmt.Println("/where: uses short & long descs to get room number")
	fmt.Println("/exit: edit an exit")
	fmt.Println("?: help: print current menu")
	fmt.Println("/X: Unset current room and exit to main menu")
}
func findCurrentRoom( r *mudmap.Room ) {
	sDesc := r.ShortDesc( bin )
	sID, _ := mudmap.FindPStringId( &sDesc, bin )
	lDesc := r.LongDesc( bin )
	lID, _ := mudmap.FindPStringId( &lDesc, bin )
	vExits := make( []string, 0 )
	for k, _ := range r.Exits {
		if ! strings.HasPrefix( k, "." ) {
			vExits = append( vExits, k )
		}
	}
	fmt.Println("with just short, rooms:", ri.IDs( sID, -1, nil ) )
	fmt.Println("with just long, rooms:", ri.IDs( -1, lID, nil ) )
	fmt.Println("with just exits, rooms:", ri.IDs( -1, -1, vExits ) )
	fmt.Println("with long & short, rooms:", ri.IDs( sID, lID, nil ) )
	fmt.Println("with short & exits, rooms:", ri.IDs( sID, -1, vExits ) )
	fmt.Println("with long & exits, rooms:", ri.IDs( -1, lID, vExits ) )
	fmt.Println("with all, rooms:", ri.IDs( sID, lID, vExits ) )
}
func printLookDisplay( r *mudmap.Room ) {
	printShortDesc( r )
	fmt.Println()
	printLongDesc( r )
	fmt.Println()
	printVisibleExits( r )
}
func printGlanceDisplay( r *mudmap.Room ) {
	printShortDesc( r )
	printVisibleExits( r )
}
func printShortDesc( r *mudmap.Room ) {
	fmt.Println( r.ShortDesc( bin ) )
}
func printLongDesc( r *mudmap.Room ) {
	fmt.Println( r.LongDesc( bin ) )
}
func printVisibleExits( r *mudmap.Room ) {
	visibleExits := make( []string, 0 )
	for k, _ := range r.Exits {
		if ! strings.HasPrefix( k, "." ) {
			visibleExits = append( visibleExits, k )
		}
	}
	fmt.Print( "Obvious Exits: " )
	for i := range visibleExits {
		fmt.Print( visibleExits[ i ], " " )
	}
}
func editExit( r *mudmap.Room ) {
	fmt.Print( "Editting exit, enter exit string:" )
	for {
		s := getUserInput()
		dest, b := r.Exits[ s ]
		if !b {
			fmt.Print( s, " is not currently a valid exit. Create?")
			switch getUserInput() {
			case "Y", "y", "Yes", "yes" :
				r.Exits[ s ] = mudmap.UNKN_DEST
				fmt.Print( s, " created.")
				bin.Store( r )
				return
			default :
				fmt.Print( s, " not created. Aborting.")
				return
			}
		}
		if dest == mudmap.UNKN_DEST {
			fmt.Println( s, " is currently unset." )
		} else {
			fmt.Println( s, " currently leads to ", dest, "." )
		}
		fmt.Print( "(S)et destination or (D)elete?")
		switch getUserInput() {
		case "D", "d" :
			r.Exits[ s ] = 0, false
			fmt.Print( s, " deleted.")
			bin.Store( r )
			return
		case "s", "S" :
			var id int64
			id = getValidRoom().ID()
			r.Exits[ s ] = id
			fmt.Print( s, " destination changed to ", id, ".")
			bin.Store( r )
			return
		default :
			fmt.Print( "Unrecognized response. Aborting.")
			return
		}
	}
}
func getValidRoom() (r *mudmap.Room){
	var e os.Error
	var id int64
	for {
		fmt.Print( "Enter room id, or \"list\" to see a list: ")
		n := getUserInput()
		if len( n ) == 0 { 
			fmt.Print( "Aborting")
			return nil
		}
		if n == "list" {
			listRooms()
			continue
		}
		id, e = strconv.Atoi64( n )
		if e != nil {
			fmt.Print( "Whole numbers please. ")
			continue
		}
		if r, e = mudmap.GetRoom( id, bin ); e != nil{
			fmt.Println( "Invalid Room ID. Try again.")
			continue
		}
		break
	}
	return
}
func listRooms() {
	roomIDs := bin.IDsOfType( new( mudmap.Room ).TypeHash() )
	for i := range roomIDs {
		r, _ := mudmap.GetRoom( roomIDs[i], bin )
		fmt.Println( "Room ", r.ID(), ":", r.ShortDesc( bin ))
	}
}
func createRoom () (r *mudmap.Room){
	r, e := mudmap.NewRoom( bin )
	if e != nil {
		fmt.Println( "Error while creating:", e )
		return nil
	}
	fmt.Println( "Room created. id: ", r.ID() )
	var s string
	for {
		fmt.Print( "Enter regionId: " )
		s = getUserInput()
		if len( s ) == 0 { break }
		id, e := strconv.Atoi64( s )
		if e != nil {
			fmt.Println("Enter a number. Try again.")
			continue
		}
		_, e = mudmap.GetRegion( id, bin )
		if e != nil {
			fmt.Println("Not a valid Region. Try again.")
			continue
		}
		r.RegionId = id
		break
	}
	fmt.Print( "Enter short description: " )
	s = getUserInput()
	r.SetShortDesc( &s, bin )
	fmt.Print( "Enter long description: " )
	s = getUserInput()
	r.SetLongDesc( &s, bin )
	for {
		fmt.Print( "Exit (empty when done): " )
		s = getUserInput()
		if len( s ) == 0 { break }
		r.Exits[ s ] = mudmap.UNKN_DEST
	}
	bin.Store( r )
	ri.RegisterRoom( r, bin )
	return r
}
func handleTraversal( r *mudmap.Room, dir string ) ( nr *mudmap.Room ) {
	destID := r.Exits[ dir ]
	if destID == mudmap.UNKN_DEST {
		fmt.Print( "That exit leads to the unknown.  Would you like to define it? " )
		s := getUserInput()
		switch s {
		case "y", "Y", "yes", "Yes" :
			fmt.Print("(A)ttach to existing room, or (C)reate new?")
			s = getUserInput()
			switch s {
			case "a", "A":
				nr = getValidRoom()
			case "c", "C":
				nr = createRoom()
			default:
				fmt.Print( "What?!" )
				fmt.Print( "You don't go anywhere!" )
				return r
			}
			if nr == nil {
				fmt.Print( "You don't go anywhere!" )
				return r
			}
		default:
			fmt.Print( "You don't go anywhere!" )
			return r
		}
	} else {
		nr, _ = mudmap.GetRoom( destID, bin )
	}
	r.Exits[ dir ] = nr.ID()
	bin.Store( r )
	printLookDisplay( nr )
	return
}
func createRegion() {
	r, e := mudmap.NewRegion( bin )
	if e != nil {
		fmt.Println( "Error while creating:", e )
		return
	}
	fmt.Println( "Region created. id: ", r.ID() )
	fmt.Print( "Please name it: " )
	var s string
	s = getUserInput()
	r.Name = s
	bin.Store( r )
}
func getUserInput() (string) {
	b, _, _ := stdin.ReadLine()
	return string(b)
}
