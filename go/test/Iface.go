package main

import (
	"project/mudclient/mudmap"
	//"project/tools"
	"flag"
	"strings"
	"regexp"
	"strconv"
	"bufio"
	"fmt"
	"os"
	"syscall"
	)

// Regexp To Extract Quoted String
const RTEQS = "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\""
var rteqs = regexp.MustCompile( RTEQS )

var mm *mudmap.MudMap
var inReader *bufio.Reader
var outWriter *bufio.Writer

// called with MapFifoIface [mapDir] [inputfifo] [outputfifo]

func main() {

	flag.Parse()
	args := flag.Args()
	if len( args ) != 3 {
		fmt.Println( args )
		fmt.Println( "Usage: MapFifoIface [mapDir] [inputFifo] [outputFifo]" )
		syscall.Exit( -1 )
	}
	mapDirName := args[ 0 ]
	inFileName := args[ 1 ]
	outFileName := args[ 2 ]

	//perms := syscall.S_IRUSR | syscall.S_IWUSR | syscall.S_IXUSR
	//e := os.MkdirAll( mapDirName, perms )
	e := os.MkdirAll( mapDirName, syscall.S_IRUSR | syscall.S_IWUSR | syscall.S_IXUSR )
	if e != nil {
		fmt.Println( "Could not make directory:", mapDirName )
		fmt.Println( e )
		syscall.Exit( -1 )
	}

fmt.Println( "Made map directory, or ensured it exists." )

	//perms = syscall.S_IRUSR | syscall.S_IWUSR
	// syscall returns an int for the error. 0 = success.
	var errInt int

	//errInt = syscall.Mkfifo( inFileName, perms )
	errInt = syscall.Mknod( inFileName, syscall.S_IFIFO|0600, 0 )
	if errInt != 0 {
		fi, e := os.Stat( inFileName )
		if e != nil {
			fmt.Println( "problem with inFifo:", e )
			syscall.Exit( -1 )
		}
		if ! fi.IsFifo() {
			fmt.Println( inFileName,"is already a nonFifo file." )
			syscall.Exit( -1 )
		}
	}
fmt.Println( "Made inputFifo, or ensured it exists." )
	//errInt = syscall.Mkfifo( outFileName, perms )
	errInt = syscall.Mknod( outFileName, syscall.S_IFIFO|0600, 0 )
	if errInt != 0 {
		fi, e := os.Stat( outFileName )
		if e != nil {
			fmt.Println( "problem with outFifo:", e )
			syscall.Exit( -1 )
		}
		if ! fi.IsFifo() {
			fmt.Println(outFileName,"is already a nonFifo file.")
			syscall.Exit( -1 )
		}
	}
fmt.Println( "Made outputFifo, or ensured it exists." )
	inFifo, e := os.Open( inFileName )
	if e != nil {
		fmt.Println( "problem with inFifo:", e )
		syscall.Exit( -1 )
	}
fmt.Println( "opened inputFifo." )
	defer inFifo.Close()
	inReader = bufio.NewReader( inFifo )
fmt.Println( "attached inputFifo reader." )
	flag := os.O_TRUNC | os.O_WRONLY
	outFifo, e := os.OpenFile( outFileName, flag, 0600 )
fmt.Println( "opened outputFifo." )
	if e != nil {
		fmt.Println( "problem with outFifo:", e )
		syscall.Exit( -1 )
	}
	defer outFifo.Close()
	outWriter = bufio.NewWriter( outFifo )
fmt.Println( "attached outputFifo writer." )
	defer outWriter.Flush()

	dir, e := os.Open( mapDirName )
	if e != nil {
		sendOutput("!! Error opening file: " + e.String())
	}
	defer dir.Close()

fmt.Println( "opened map dir." )
	mm, e = mudmap.Create( dir )
	if e != nil {
		sendOutput("!! Error creating map: " + e.String())
	}
	mm, e = mudmap.Create( dir )
fmt.Println( "created MudMap." )

	cmd, e := getInput()
fmt.Println( "got input." )
	if e != nil {
		sendOutput("!! Error getting input: " + e.String())
	}
	handleCmd( cmd )
	for cmd != "quit" {
fmt.Println( "cmd != quit, looping." )
		cmd, e = getInput()
fmt.Println( "got input." )
		if e != nil {
			sendOutput("!! Error getting input: " + e.String())
			break
		}
		handleCmd( cmd )
	}
}
func getInput() (s string, e os.Error) {

fmt.Println( "waiting for input." )
	b, pref, e := inReader.ReadLine()
	if e != nil {
		return "", e
	}
	if !pref {
		return string(b), nil
	}
	// make it twice as big as default buffer size..
	bs := make([]byte, 8192)
	bs = append( bs, b... )
	for pref {
		b, pref, e = inReader.ReadLine()
		if e != nil {
			return string(bs), e
		}
		bs = append( bs, b... )
	}
	return string(bs), nil
}
func sendOutput(s string) {
fmt.Println( "sending output:" + s )
	_, _ = outWriter.WriteString( s + "\n" )
fmt.Println( "sent output." )
	outWriter.Flush()
fmt.Println( "flushed the outWriter." )
}
func handleCmd( cmd string ) {

fmt.Println( "handling cmd:" + cmd )
	switch {
	// commit
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "commit"):
		e := mm.Commit()
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// commitAndPack
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "commitAndPack"):
		e := mm.CommitAndPack()
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// rollback
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "rollback"):
		e := mm.Rollback()
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// releaseResources
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "releaseResources"):
		e := mm.Release()
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// quit
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "quit"):
		e := mm.Release()
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// regionList
	// (name)(;name)* || FAIL {reason}
	case strings.HasPrefix(cmd, "regionList"):
		rs := mm.RegionNames()
		cat := ""
		for i := range rs {
			if i >= 1 { cat = cat + ";" }
			cat = cat + rs[i]
		}
		sendOutput(cat)
	// newRegion [name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "newRegion"):
		_, e := mm.NewRegion( cmd[10:] )
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// renameRegion [oldName] [newName]
	// SUCCESS Region {oldName} changed to {newName} || FAIL {reason}
	case strings.HasPrefix(cmd, "renameRegion"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: renameRegion [old] [new]" )
			break
		}
		id, e := mm.RegionId( split[1] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		mm.RenameRegion( id, split[2] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
		} else {
			sendOutput("SUCCESS Region "+split[1]+" changed to "+split[2])
		}
	// regionRoomList [regionName]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "regionRoomList"):
		id, e := mm.RegionId( cmd[15:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		rs, e := mm.RoomsInRegion( id )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		cat := ""
		is := rs.Values()
		for i := range is {
			if i > 0 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( is[i] )
		}
		sendOutput(cat)
	// deleteRegion [region name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "deleteRegion"):
		id, e := mm.RegionId( cmd[13:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		mm.DeleteRegion( id )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
		} else {
			sendOutput("SUCCESS")
		}
	// newRoom [regionName]
	// Room [id] created || FAIL {reason}
	case strings.HasPrefix(cmd, "newRoom"):
		id, e := mm.RegionId( cmd[8:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		id, e = mm.NewRoom( id )
		if e == nil {
			sendOutput("Room "+strconv.Itoa64(id)+" created")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// deleteRoom [roomId||prev||cur||name]
	// Room [id] deleted || FAIL {reason}
	case strings.HasPrefix(cmd, "deleteRoom"):
		id, e := roomId( cmd[11:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.DeleteRoom( id )
		if e == nil {
			sendOutput("Room "+strconv.Itoa64(id)+" deleted")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// setRoomShortDesc [roomId||prev||cur||name] <short description>
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "setRoomShortDesc"):
		split := strings.SplitN( cmd[17:], " ", 2 )
		for len(split) < 2 { split = append( split, "" ) }
		id, e := roomId( split[0] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.SetRmSDesc( id, &split[1] )
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// setRoomLongDesc [roomId||prev||cur||name] <long description>
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "setRoomLongDesc"):
		split := strings.SplitN( cmd[16:], " ", 2 )
		for len(split) < 2 { split = append( split, "" ) }
		id, e := roomId( split[0] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.SetRmLDesc( id, &split[1] )
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// shortDesc [roomId||prev||cur||name]
	// <short description> || FAIL {reason}
	case strings.HasPrefix(cmd, "shortDesc"):
		id, e := roomId( cmd[10:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		s, e := mm.RmSDesc( id )
		if e == nil {
			sendOutput( s )
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// longDesc [roomId||prev||cur||name]
	// <long description of room> || FAIL {reason}
	case strings.HasPrefix(cmd, "longDesc"):
		id, e := roomId( cmd[9:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		s, e := mm.RmLDesc( id )
		if e == nil {
			sendOutput( s )
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// register [roomId||prev||cur||name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "register"):
		id, e := roomId( cmd[9:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.RegisterRoom( id )
		if e == nil {
			sendOutput( "SUCCESS" )
		} else {
			sendOutput("FAIL: " +e.String() )
		}

	// setCur [roomId||prev||cur||name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "setCur"):
		id, e := roomId( cmd[7:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.SetCurrentRoom( id )
		if e == nil {
			sendOutput( "SUCCESS" )
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// cur
	// [roomId] || FAIL {reason}
	case strings.HasPrefix(cmd, "cur"):
		id, e := mm.CurrentRoom()
		if e == nil {
			sendOutput( strconv.Itoa64(id) )
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// prev
	// [roomId] || FAIL {reason}
	case strings.HasPrefix(cmd, "prev"):
		id, e := mm.PreviousRoom()
		if e == nil {
			sendOutput( strconv.Itoa64(id) )
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// tags
	// TAGLIST:[tagName]?[;tagName]* || FAIL {reason}
	case strings.HasPrefix(cmd, "tags"):
		ns := mm.Tags()
		cat := "TAGLIST:"
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + ns[i]
		}
		sendOutput( cat )
	// newTag [name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "newTag"):
		e := mm.CreateRoomTag( cmd[7:] )
		if e == nil {
			sendOutput("SUCCESS")
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// deleteTag [tag name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "deleteTag"):
		e := mm.DeleteTag( cmd[10:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
		} else {
			sendOutput("SUCCESS")
		}
	// taggedRooms [tag name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "taggedRooms"):
		ids, e := mm.TaggedRms( cmd[12:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		ns := ids.Values()
		cat := ""
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( ns[i] )
		}
		sendOutput( cat )
	// removeRoomTag [room] [tag]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "removeRoomTag"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: removeRoomTag [room] [tag]" )
			break
		}
		rid, e := roomId( split[1] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.RemRoomTag( rid, split[2] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
		} else {
			sendOutput("SUCCESS")
		}
	// tagRoom [room] [tag]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "tagRoom"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: tagRoom [room] [tag]" )
			break
		}
		rid, e := roomId( split[1] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.AddRoomTag( rid, split[2] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
		} else {
			sendOutput("SUCCESS")
		}
	// tagsOnRoom [room]
	// TAGS:(tagName)?(;tagName)* || FAIL {reason}
	case strings.HasPrefix(cmd, "tagsOnRoom"):
		id, e := roomId( cmd[11:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		ns, e := mm.RoomTags( id )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		cat := "TAGS:"
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + ns[i]
		}
		sendOutput( cat )
	// taggedInRegion [tag] [regionName]
	// (roomId)(;roomId)* || FAIL {reason}
	case strings.HasPrefix(cmd, "taggedInRegion"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: taggedInRegion [tag] [region]")
			break
		}
		id, e := mm.RegionId( split[2] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		set1 := mm.FindRmIds( id, nil, nil, nil )
		set2, e := mm.TaggedRms( cmd[12:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		ids := set1.Intersection( set2 ).Values()
		cat := ""
		for i := range ids {
			if i >= 1 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( ids[i] )
		}
		sendOutput( cat )
	// namedRooms
	// (roomName)(;roomName)* || FAIL {reason}
	case strings.HasPrefix(cmd, "namedRooms"):
		ns := mm.ListNamedRooms()
		if len( ns ) == 0 {
			sendOutput("FAIL: No named rooms." )
			break
		}
		cat := ""
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + ns[i]
		}
		sendOutput( cat )
	// nameRoom [roomId||prev||cur] [name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "nameRoom"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: nameRoom [room] [name]" )
			break
		}
		rid, e := roomId( split[1] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.NameRoom( rid, split[2] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
		} else {
			sendOutput("SUCCESS")
		}
	// roomId [roomId||prev||cur||name]
	// [roomId] || FAIL {reason}
	case strings.HasPrefix(cmd, "roomId"):
		id, e := roomId( cmd[7:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		sendOutput( strconv.Itoa64( id ) )
	// unnameRoom [name]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "unnameRoom"):
		e := mm.UnnameRoom( cmd[11:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		sendOutput( "SUCCESS" )
	// cleanMap
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "cleanMap"):
		e := mm.CleanData()
		if e == nil {
			sendOutput( "SUCCESS" )
		} else {
			sendOutput("FAIL: " +e.String() )
		}
	// roomcacheSize
	// size
	case strings.HasPrefix(cmd, "roomcacheSize"):
		i := mm.RoomCacheSize()
		sendOutput( strconv.Itoa(i) )
	// setRoomcacheSize
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "setRoomcacheSize"):
		i, e := strconv.Atoi( cmd[17:] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		mm.SetRoomCacheSize( i )
		sendOutput( "SUCCESS" )
	// whereAmI s="short desc" l="the \"long\" description" (n,s,u,leave)
	// (roomId)?(;roomId)* || FAIL {reason}
	case strings.HasPrefix(cmd, "whereAmI"):
		args := cmd[9:]
		if !strings.HasPrefix( args, "s=\"" ) {
			sendOutput("FAIL: usage: whereAmI s=\"<short desc>\" l=\"<The \\\"long\\\" description>\" (<n,s,u,leave>)")
			break
		}
		indices := rteqs.FindStringSubmatchIndex( args )
		if indices == nil || len(indices) < 4 || indices[2] < 0 {
			sendOutput("FAIL: usage: whereAmI s=\"<short desc>\" l=\"<The \\\"long\\\" description>\" (<n,s,u,leave>)")
			break
		}
		sDesc := args[indices[2]:indices[3]]
		sDesc = unSlashEscape( sDesc )
		args = args[indices[3]+1:]
		indices = rteqs.FindStringSubmatchIndex( args )
		if indices == nil || len(indices) < 4 || indices[2] < 0{
			sendOutput("FAIL: usage: whereAmI s=\"<short desc>\" l=\"<The \\\"long\\\" description>\" (<n,s,u,leave>)")
			break
		}
		lDesc := args[indices[2]:indices[3]]
		lDesc = unSlashEscape( lDesc )
		args = args[indices[3]+1:]
		args = strings.Trim( args, " ()" )
		exits := strings.Split( args, "," )
		sp := &sDesc
		lp := &lDesc
		if len(sDesc) == 0 { sp = nil }
		if len(lDesc) == 0 { lp = nil }
		if len(exits) == 0 || len(exits) == 1 && len(exits[0]) == 0 {
			exits = nil
		}
		ids := mm.FindRmIds( -1, sp, lp, exits ).Values()
		cat := ""
		for i := range ids {
			if i >= 1 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( ids[i] )
		}
		sendOutput( cat )
	// exitInfo [room] [exit string]
	// destId,(H,)?(T,)?cost || FAIL {reason}
	case strings.HasPrefix(cmd, "exitInfo"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: exitInfo [room] [exit string]")
			break
		}
		id, e := roomId( split[1] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		dest, h, t, cost, e := mm.ExitInfo( id, split[2] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		str := strconv.Itoa64(dest)
		if h { str = str + ",H" }
		if t { str = str + ",T" }
		str = str + "," + strconv.Itoa(cost)
		sendOutput( str )
	// exitInfo [room]
	// (destId,(H,)?(T,)?cost)?(;destId,(H,)?(T,)?cost)* || FAIL {reason}
	case strings.HasPrefix(cmd, "exits"):
		id, e := roomId( cmd[6:] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		cmds, dests, hs, ts, costs, e := mm.Exits( id )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		str := ""
		for i := range dests {
			if i > 0 { str = str + ";" }
			str = str + cmds[i]
			str = str + ","
			str = str + strconv.Itoa64(dests[i])
			if hs[i] { str = str + ",H" }
			if ts[i] { str = str + ",T" }
			str = str + "," + strconv.Itoa(costs[i])
		}
		sendOutput( str )
	// deleteExit [room] [exit string]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "deleteExit"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: deleteExit [room] [exit string]")
			break
		}
		id, e := roomId( split[1] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		e = mm.DelExit( id, split[2] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		sendOutput( "SUCCESS" )
	// addExit ([HhTt])? [room] [exit string]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "addExit"):
		rx, e := regexp.Compile( "^addExit( -?(([Hh])|([Tt]))*)* ([a-zA-Z0-9\\(\\)])* (.)*$" )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		indices := rx.FindStringSubmatchIndex( cmd )
fmt.Println("fifo.Iface cmd addExit matched string \""+cmd+"\" using \""+rx.String()+"\" and got indexes ", indices )
		if indices == nil || len(indices) < 14 || indices[10] < 0{
			sendOutput("FAIL: usage: addExit ([HhTt])? [room] [exit string]")
			break
		}
		id, e := roomId( cmd[indices[10]:indices[11]] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		if indices[12] < 0{
			sendOutput("FAIL: usage: addExit ([HhTt])? [room] [exit string]")
			break
		}
		eStr := cmd[indices[12]:indices[13]]
		var h, t bool
		if indices[6] > 0 && indices[6] < indices[7] { h = true }
		if indices[8] > 0 && indices[8] < indices[9] { t = true }
		e = mm.AddExit( id, eStr, h, t )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		sendOutput( "SUCCESS" )
	// setExit [room] [exit string] [dest] [cost]
	// SUCCESS || FAIL {reason}
	case strings.HasPrefix(cmd, "setExit"):
		usage := "FAIL: usage: setExit [room] [exit string] [destRoom] [cost]"
		rx, e := regexp.Compile( "^setExit ([a-zA-Z0-9\\(\\)])+ (.)+ ([a-zA-Z0-9\\(\\)])+ ([0-9])+$" )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		indices := rx.FindStringSubmatchIndex( cmd )
fmt.Println("fifo.Iface cmd setExit matched string \""+cmd+"\" using \""+rx.String()+"\" and got indexes ", indices )
		if indices == nil || len(indices) < 10 || indices[2] < 0{
			sendOutput( usage )
			break
		}
		id, e := roomId( cmd[indices[2]:indices[3]] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		if indices[6] < 0{
			sendOutput( usage )
			break
		}
		dest, e := roomId( cmd[indices[6]:indices[7]] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		if indices[8] < 0{
			sendOutput( usage )
			break
		}
		cost, e := strconv.Atoi( cmd[indices[8]:indices[9]] )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		if indices[4] < 0{
			sendOutput( usage )
			break
		}
		e = mm.SetExitInfo( id, cmd[indices[4]:indices[5]], dest, cost )
		if e != nil {
			sendOutput( "FAIL " + e.String() )
			break
		}
		sendOutput( "SUCCESS" )
	// path [roomId||prev||cur] [roomId||prev||cur]
	// Path:(step[nextIndex])?(;step[nextIndex])* || FAIL {reason}
	case strings.HasPrefix(cmd, "path"):
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL: usage: path [room1] [room2]" )
			break
		}
		orig, e := roomId( split[1] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		dest, e := roomId( split[2] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		p, e := mm.Path( orig, dest )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		pbs := []byte("Path:")
		tbs := []byte("T:")
		sbs := []byte(";")
		obs := []byte("[")
		cbs := []byte("]")
		for i := range p.Steps {
			if i > 0 { pbs = append( pbs, sbs... ) }
			if p.Steps[i].SType == mudmap.STYPE_TRG {
				pbs = append( pbs, tbs... )
			}
			pbs = append( pbs, []byte(p.Steps[i].Text)... )
			pbs = append( pbs, obs... )
			pbs = append( pbs, []byte(strconv.Itoa(p.Steps[i].NextStep))... )
			pbs = append( pbs, cbs... )
		}
		sendOutput( string( pbs ) )
	case strings.HasPrefix(cmd, "help"):
		outputCommands()
	default :
		sendOutput("Unknown command: \""+cmd+"\" ('help' for help)")
	}
fmt.Println( "done handling cmd:" + cmd )
}
func roomId( s string ) (id int64, e os.Error){
	if s == "prev" { return mm.PreviousRoom() }
	if s == "cur" { return mm.CurrentRoom() }
	id, e = strconv.Atoi64( s )
	if e == nil { return id, e }
	return mm.NamedRoomId( s )
}
// this removes any single backslashes in s, and replaces
// any double backslashes with a single backslash.
func unSlashEscape( s string ) (result string) {
	i := strings.Index( s, "\\" )
	if i < 0 { return s }
	result = ""
	for i >= 0 {
		result = result + s[0:i]
		s = s[i+1:]
		i = strings.Index( s, "\\" )
	}
	result = result + s
	return result
}

// -- deal with anomalies later
// Notes
func outputCommands() {
	sendOutput("regionList")
	sendOutput("newRegion [name]")
	sendOutput("renameRegion [oldName] [newName]")
	sendOutput("regionRoomList [regionName]")
	sendOutput("deleteRegion [region name]")
	sendOutput("newRoom [regionName]")
	sendOutput("deleteRoom [room]")
	sendOutput("setRoomShortDesc [room] <short description>")
	sendOutput("setRoomLongDesc [room] <long description>")
	sendOutput("shortDesc [room]")
	sendOutput("longDesc [room]")
	sendOutput("register [room]")
	sendOutput("setCur [room]")
	sendOutput("cur")
	sendOutput("prev")
	sendOutput("tags")
	sendOutput("newTag [name]")
	sendOutput("deleteTag [tag name]")
	sendOutput("taggedRooms [tag name]")
	sendOutput("removeRoomTag [room] [tag]")
	sendOutput("tagRoom [room] [tag]")
	sendOutput("tagsOnRoom [room]")
	sendOutput("taggedInRegion [tag] [regionName]")
	sendOutput("namedRooms")
	sendOutput("nameRoom [room] [name]")
	sendOutput("roomId [room]")
	sendOutput("unnameRoom [name]")
	sendOutput("cleanMap")
	sendOutput("roomcacheSize")
	sendOutput("setRoomcacheSize")
	sendOutput("whereAmI s=\"short desc\" l=\"the \\\"long\\\" description\" (n,s,u,leave)")
	sendOutput("exitInfo [room] [exit string]")
	sendOutput("exitInfo [room]")
	sendOutput("deleteExit [room] [exit string]")
	sendOutput("addExit ([HhTt])? [room] [exit string]")
	sendOutput("setExit [room] [exit string] [dest] [cost]")
	sendOutput("path [room] [room]")
	sendOutput("commit")
	sendOutput("commitAndPack")
	sendOutput("rollback")
	sendOutput("releaseResources")
	sendOutput("quit")
}
