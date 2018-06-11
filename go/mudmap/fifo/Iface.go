package main

import (
	"project/mudmap"
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

// This is the fifo interface for the mudmap.
// It takes 3 command line arguments:
//   a directory for the map data files,
//     create the directory and files if they don't exist, load them if they do
//   the name of a fifo for input of commands
//     it will create it if it doesn't exist
//   the name of a fifo for output of command results
//     it will create it if it doesn't exist
//
// the convention is that any command will return with either
// FAIL:reason
// or
// SUCCESS explanation:
// or
// SUCCESS explanation:result
//
// So success or failure are clearly labeled, and any useful informaiton
// from a success (return value) follows the ':'
//
// This is to make it easy to parse and deal with the map using triggers
// and scripts attached to the fifos, and also human readable, for testing
// your scripts and the mudmap.
//
// note that you can screw up your scripts by (for instance) naming
// a region 'SUCESS:' or even newbie:rabbitGrounds, since user
// defined variables may appear in the explanation.  Be careful.
//
// colon is used to indicate the start of the returned result of a command
// semicolon is used to separate results
// coma is used to separate fields in results


// Regexp To Extract Quoted String.  Has 2 paren sets, quoted value is 2, 3
const RTEQS = "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\""
var rteqs = regexp.MustCompile( RTEQS )
// so our indices will be s: 2, 3; l: 6, 7; exits: 10, 11
var rxWhereAmI = regexp.MustCompile("^[^ ]+ s="+RTEQS+" l="+RTEQS+" \\(([^\\)]*)\\)$")
// single arg following command name. indices 2, 3
var rxUnquotedArg = regexp.MustCompile("^[^ ]+ ([^ ]+)$")
// two args following command name. arg1: 2, 3; arg2: 4, 5
var rx2UnquotedArgs = regexp.MustCompile("^[^ ]+ ([^ ]+) ([^ ]+)$")
// complex. h?: 6, 7; t?: 8, 9; room: 10, 11; exit string: 12, 13
var rxAddExit = regexp.MustCompile( "^[^ ]+( -?(([Hh])|([Tt]))*)* ([a-zA-Z0-9\\(\\)]+) (.+)$" )
// complex.: sourceRoom: 2, 3; exit string: 4, 5; destRoom: 6, 7; cost: 8, 9
var rxSetExit = regexp.MustCompile( "^[^ ]+ ([a-zA-Z0-9\\(\\)]+) (.+) ([a-zA-Z0-9\\(\\)]+) ([0-9]+)$" )

var mm *mudmap.MudMap
var inReader *bufio.Reader
var outWriter *bufio.Writer

// called with MapFifoIface [mapDir] [inputfifo] [outputfifo]

func main() {

	flag.Parse()
	args := flag.Args()
	if len( args ) != 3 {
		fmt.Println( args )
		fmt.Println( "Usage: MMfifo [mapDir] [inputFifo] [outputFifo]" )
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
		//syscall.Exit( -1 )
		panic( os.NewError( "could not make directory" ) )
	}

	//perms = syscall.S_IRUSR | syscall.S_IWUSR
	// syscall returns an int for the error. 0 = success.
	var errInt int

	//errInt = syscall.Mkfifo( inFileName, perms )
	errInt = syscall.Mknod( inFileName, syscall.S_IFIFO|0600, 0 )
	if errInt != 0 {
		fi, e := os.Stat( inFileName )
		if e != nil {
			fmt.Println( "problem with inFifo:", e )
			//syscall.Exit( -1 )
			panic( os.NewError( "problem with inFifo" ) )
		}
		if ! fi.IsFifo() {
			fmt.Println( inFileName,"is already a nonFifo file." )
			//syscall.Exit( -1 )
			panic( os.NewError( "problem with inFifo" ) )
		}
	}
	//errInt = syscall.Mkfifo( outFileName, perms )
	errInt = syscall.Mknod( outFileName, syscall.S_IFIFO|0600, 0 )
	if errInt != 0 {
		fi, e := os.Stat( outFileName )
		if e != nil {
			fmt.Println( "problem with outFifo:", e )
			//syscall.Exit( -1 )
			panic( os.NewError( "problem with outFifo" ) )
		}
		if ! fi.IsFifo() {
			fmt.Println(outFileName,"is already a nonFifo file.")
			//syscall.Exit( -1 )
			panic( os.NewError( "problem with outFifo" ) )
		}
	}
	inFifo, e := os.Open( inFileName )
	if e != nil {
		fmt.Println( "problem with inFifo:", e )
		//syscall.Exit( -1 )
		panic( os.NewError( "problem with inFifo" ) )
	}
	defer inFifo.Close()
	inReader = bufio.NewReader( inFifo )
	flag := os.O_TRUNC | os.O_WRONLY
	outFifo, e := os.OpenFile( outFileName, flag, 0600 )
	if e != nil {
		fmt.Println( "problem with outFifo:", e )
		//syscall.Exit( -1 )
		panic( os.NewError( "problem with outFifo" ) )
	}
	defer outFifo.Close()
	outWriter = bufio.NewWriter( outFifo )
	defer outWriter.Flush()

	dir, e := os.Open( mapDirName )
	if e != nil {
		sendOutput("!! Error opening file: " + e.String())
		panic( os.NewError( "!! Error opening file "+e.String() ) )
	}
	defer dir.Close()

	mm, e = mudmap.Create( dir )
	if e != nil {
		sendOutput("!! Error creating map: " + e.String())
		panic( os.NewError( "!! Error creating map "+e.String() ) )
	}

	cmd, e := getInput()
	if e != nil {
		sendOutput("!! Error getting input: " + e.String())
	}
	handleCmd( cmd )
	for cmd != "quit" {
		cmd, e = getInput()
		if e != nil {
			sendOutput("!! Error getting input: " + e.String())
			break
		}
		handleCmd( cmd )
	}
}
func getInput() (s string, e os.Error) {

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
	_, _ = outWriter.WriteString( s + "\n" )
	outWriter.Flush()
}
func handleCmd( cmd string ) {

	cmd = strings.Trim( cmd, " " )
	switch strings.ToLower(strings.SplitN(cmd," ",2)[0]){
	case "commitandpack" :
		e := mm.CommitAndPack()
		if e == nil {
			sendOutput("SUCCESS committed and packed:")
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "commit" :
		e := mm.Commit()
		if e == nil {
			sendOutput("SUCCESS committed:")
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "rollback" :
		e := mm.Rollback()
		if e == nil {
			sendOutput("SUCCESS rolled back:")
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "quit":
		e := mm.Release()
		if e == nil {
			sendOutput("SUCCESS quitting. Goodbye.:")
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "regions":
		rs := mm.RegionNames()
		cat := ""
		for i := range rs {
			if i >= 1 { cat = cat + ";" }
			cat = cat + rs[i]
		}
		sendOutput("SUCCESS Regions:"+cat)
	case "newregion":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract name (no spaces)" )
			break
		}
		rName := cmd[is[2]:is[3]]
		_, e := mm.NewRegion( rName )
		if e == nil {
			sendOutput("SUCCESS Created Region:"+rName)
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "renameregion":
		is := rx2UnquotedArgs.FindStringSubmatchIndex( cmd )
		if len(is) < 6 || is[2] == -1 || is[4] == -1 {
			sendOutput("FAIL:Could not extract names (no spaces)" )
			break
		}
		oldName := cmd[ is[2]:is[3] ]
		newName := cmd[ is[4]:is[5] ]
		id, e := mm.RegionId( oldName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		mm.RenameRegion( id, newName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
		} else {
			sendOutput("SUCCESS "+oldName+" renamed to:"+newName)
		}
	case "regionrooms":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract name (no spaces)" )
			break
		}
		id, e := mm.RegionId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		rs, e := mm.RoomsInRegion( id )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		cat := ""
		vs := rs.Values()
		for i := range vs {
			if i > 0 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( vs[i] )
		}
		sendOutput("SUCCESS Rooms in region "+cmd[is[2]:is[3]]+":"+cat)
	case "delregion":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract name (no spaces)" )
			break
		}
		id, e := mm.RegionId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		mm.DeleteRegion( id )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
		} else {
			sendOutput("SUCCESS deleted:"+cmd[is[2]:is[3]])
		}
	case "roomregion":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room (no spaces)" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		name, e := mm.RegionOfRoom( id )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		sendOutput( "SUCCESS Room "+strconv.Itoa64(id)+" region is:"+name )
	case "newroom":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract name (no spaces)" )
			break
		}
		id, e := mm.RegionId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		id, e = mm.NewRoom( id )
		if e == nil {
			sendOutput("SUCCESS Room created id:"+strconv.Itoa64(id))
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "delroom":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		e = mm.DeleteRoom( id )
		if e == nil {
			sendOutput("SUCCESS Room deleted:"+strconv.Itoa64(id))
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "setsdesc":
		split := strings.SplitN( cmd, " ", 3 )
		for len(split) < 3 { split = append( split, "" ) }
		id, e := roomId( split[1] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		e = mm.SetRmSDesc( id, &split[2] )
		if e == nil {
			sendOutput("SUCCESS Room "+strconv.Itoa64(id)+" ShortDescription:"+split[2])
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "setldesc":
		split := strings.SplitN( cmd, " ", 3 )
		for len(split) < 3 { split = append( split, "" ) }
		id, e := roomId( split[1] )
		if e != nil {
			sendOutput("FAIL: " +e.String() )
			break
		}
		e = mm.SetRmLDesc( id, &split[2] )
		if e == nil {
			sendOutput("SUCCESS Room "+strconv.Itoa64(id)+" LongDescription:"+split[2])
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "sdesc":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		s, e := mm.RmSDesc( id )
		if e == nil {
			sendOutput("SUCCESS Room "+strconv.Itoa64(id)+" short description:"+s)
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "ldesc":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		s, e := mm.RmLDesc( id )
		if e == nil {
			sendOutput( "SUCCESS Room "+strconv.Itoa64(id)+" long description:"+s )
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "setcur":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		e = mm.SetCurrentRoom( id )
		if e == nil {
			sendOutput( "SUCCESS cur:"+strconv.Itoa64(id))
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "cur":
		id, e := mm.CurrentRoom()
		if e == nil {
			sendOutput( "SUCCESS cur:"+strconv.Itoa64(id) )
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "prev":
		id, e := mm.PreviousRoom()
		if e == nil {
			sendOutput( "SUCCESS prev:"+strconv.Itoa64(id) )
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "tags":
		ns := mm.Tags()
		cat := "SUCCESS Tags:"
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + ns[i]
		}
		sendOutput( cat )
	case "newtag":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract tag name" )
			break
		}
		e := mm.CreateRoomTag( cmd[is[2]:is[3]] )
		if e == nil {
			sendOutput("SUCCESS created tag:"+cmd[is[2]:is[3]])
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "deltag":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract tag name" )
			break
		}
		e := mm.DeleteTag( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
		} else {
			sendOutput("SUCCESS deleted tag:"+cmd[is[2]:is[3]])
		}
	case "tagged":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract tag name" )
			break
		}
		ids, e := mm.TaggedRms( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		ns := ids.Values()
		cat := "SUCCESS Rooms tagged with "+cmd[is[2]:is[3]]+":"
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( ns[i] )
		}
		sendOutput( cat )
	case "untag":
		is := rx2UnquotedArgs.FindStringSubmatchIndex( cmd )
		if len(is) < 6 || is[2] == -1 || is[4] == -1 {
			sendOutput("FAIL:usage untag [room] [tag]" )
			break
		}
		roomName := cmd[ is[2]:is[3] ]
		tagName := cmd[ is[4]:is[5] ]
		rid, e := roomId( roomName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		e = mm.RemRoomTag( rid, tagName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
		} else {
			sendOutput("SUCCESS tag "+tagName+" removed from room "+roomName+":")
		}
	case "tag":
		is := rx2UnquotedArgs.FindStringSubmatchIndex( cmd )
		if len(is) < 6 || is[2] == -1 || is[4] == -1 {
			sendOutput("FAIL:usage tag [room] [tag]" )
			break
		}
		roomName := cmd[ is[2]:is[3] ]
		tagName := cmd[ is[4]:is[5] ]
		rid, e := roomId( roomName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		e = mm.AddRoomTag( rid, tagName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
		} else {
			sendOutput("SUCCESS tag "+tagName+" added to room "+roomName+":")
		}
	case "tagsonroom":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		ns, e := mm.RoomTags( id )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		cat := "SUCCESS Tags:"
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + ns[i]
		}
		sendOutput( cat )
	case "taggedinregion":
		is := rx2UnquotedArgs.FindStringSubmatchIndex( cmd )
		if len(is) < 6 || is[2] == -1 || is[4] == -1 {
			sendOutput("FAIL:usage taggedInRegion [tag] [region]")
			break
		}
		tagName := cmd[ is[2]:is[3] ]
		regionName := cmd[ is[4]:is[5] ]
		id, e := mm.RegionId( regionName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		set1 := mm.FindRmIds( id, nil, nil, nil )
		set2, e := mm.TaggedRms( tagName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		ids := set1.Intersection( set2 ).Values()
		cat := "SUCCESS Rooms in region "+regionName+" tagged with "+tagName+":"
		for i := range ids {
			if i >= 1 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( ids[i] )
		}
		sendOutput( cat )
	case "names":
		ns := mm.ListNamedRooms()
		if len( ns ) == 0 {
			sendOutput("FAIL:No named rooms." )
			break
		}
		cat := "SUCCESS Named Rooms:"
		for i := range ns {
			if i >= 1 { cat = cat + ";" }
			cat = cat + ns[i]
		}
		sendOutput( cat )
	case "name":
		is := rx2UnquotedArgs.FindStringSubmatchIndex( cmd )
		if len(is) < 6 || is[2] == -1 || is[4] == -1 {
			sendOutput("FAIL:usage nameRoom [room] [name]" )
			break
		}
		roomIdentifier := cmd[ is[2]:is[3] ]
		roomName := cmd[ is[4]:is[5] ]
		rid, e := roomId( roomIdentifier )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		e = mm.NameRoom( rid, roomName )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
		} else {
			sendOutput("SUCCESS Room "+roomIdentifier+" named:"+roomName)
		}
	case "roomid":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		sendOutput( "SUCCESS Room Id:"+strconv.Itoa64( id ) )
	case "unname":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		e := mm.UnnameRoom( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		sendOutput( "SUCCESS room formerly known as "+cmd[is[2]:is[3]]+" unnamed:" )
	case "cleanmap":
		e := mm.CleanData()
		if e == nil {
			sendOutput( "SUCCESS:" )
		} else {
			sendOutput("FAIL:" +e.String() )
		}
	case "cachesize":
		i := mm.RoomCacheSize()
		sendOutput( "SUCCESS Room cache size:"+strconv.Itoa(i) )
	case "setcachesize":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract size argument" )
			break
		}
		i, e := strconv.Atoi( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		mm.SetRoomCacheSize( i )
		sendOutput( "SUCCESS Room cache size set to:"+cmd[is[2]:is[3]])
	// whereAmI s="short desc" l="the \"long\" description" (n,s,u,leave)
	// (roomId)?(;roomId)* || FAIL {reason}
	case "whereami":
		// so our indices will be s: 2, 3; l: 6, 7; exits: 10, 11
		is := rxWhereAmI.FindStringSubmatchIndex( cmd )
		if len(is) < 12 || is[2] < 0 || is[6] < 0 || is[10] < 0 {
			sendOutput("FAIL:usage whereAmI s=\"<short desc>\" l=\"<The \\\"long\\\" description>\" (<n,s,u,leave>)")
			break
		}
		sDesc := cmd[is[2]:is[3]]
		sDesc = unSlashEscape( sDesc )
		lDesc := cmd[is[6]:is[7]]
		lDesc = unSlashEscape( lDesc )
		exits := strings.Split( cmd[is[10]:is[11]], "," )
		for i := range exits {
			exits[ i ] = strings.Trim( exits[i], " " )
		}
		sp := &sDesc
		lp := &lDesc
		if len(sDesc) == 0 { sp = nil }
		if len(lDesc) == 0 { lp = nil }
		if len(exits) == 0 || len(exits) == 1 && len(exits[0]) == 0 {
			exits = nil
		}
		ids := mm.FindRmIds( -1, sp, lp, exits ).Values()
		cat := "SUCCESS Possible rooms:"
		for i := range ids {
			if i >= 1 { cat = cat + ";" }
			cat = cat + strconv.Itoa64( ids[i] )
		}
		sendOutput( cat )
	case "exitinfo":
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL:usage exitInfo [room] [exit string]")
			break
		}
		id, e := roomId( split[1] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		dest, h, t, cost, e := mm.ExitInfo( id, split[2] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		str := "SUCCESS:"+strconv.Itoa64(dest)
		if dest == mudmap.UNKN_DEST {
			str = "SUCCESS:UNKNOWN"
		}
		if h { str = str + ",H" } else { str = str + "," }
		if t { str = str + ",T" } else { str = str + "," }
		str = str + "," + strconv.Itoa(cost)
		sendOutput( str )
	case "exits":
		is := rxUnquotedArg.FindStringSubmatchIndex( cmd )
		if len(is) < 4 || is[2] == -1 {
			sendOutput("FAIL:Could not extract room argument" )
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		cmds, dests, hs, ts, costs, e := mm.Exits( id )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		str := "SUCCESS:"
		for i := range dests {
			if i > 0 { str = str + ";" }
			str = str + cmds[i]
			str = str + ","
			if dests[i] == mudmap.UNKN_DEST {
				str = str + "UNKNOWN"
			} else {
				str = str + strconv.Itoa64(dests[i])
			}
			if hs[i] { str = str + ",H" } else { str = str + "," }
			if ts[i] { str = str + ",T" } else { str = str + "," }
			str = str + "," + strconv.Itoa(costs[i])
		}
		sendOutput( str )
	case "delexit":
		split := strings.SplitN( cmd, " ", 3 )
		if len( split ) != 3 {
			sendOutput("FAIL:usage delExit [room] [exit string]")
			break
		}
		id, e := roomId( split[1] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		e = mm.DelExit( id, split[2] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		sendOutput( "SUCCESS Exit "+split[2]+" deleted:" )
	case "addexit":
		is := rxAddExit.FindStringSubmatchIndex( cmd )
		if len(is) < 14 || is[10] < 0 || is[12] < 0 {
			sendOutput("FAIL:usage addExit ([HhTt])? [room] [exit string]")
			break
		}
		id, e := roomId( cmd[is[10]:is[11]] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		eStr := cmd[is[12]:is[13]]
		var h, t bool
		if is[6] > 0 && is[6] < is[7] { h = true }
		if is[8] > 0 && is[8] < is[9] { t = true }
		e = mm.AddExit( id, eStr, h, t )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		sendOutput( "SUCCESS exit "+eStr+" added:" )
	// setExit [room] [exit string] [dest] [cost]
	// SUCCESS || FAIL {reason}
	case "setexit":
		is := rxSetExit.FindStringSubmatchIndex( cmd )
		if len(is) < 10 || is[2] < 0 || is[4] < 0 || is[6] < 0 || is[8] < 0 {
			sendOutput("FAIL:usage setExit [room] [exit string] [destRoom] [cost]")
			break
		}
		id, e := roomId( cmd[is[2]:is[3]] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		dest, e := roomId( cmd[is[6]:is[7]] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		cost, e := strconv.Atoi( cmd[is[8]:is[9]] )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		e = mm.SetExitInfo( id, cmd[is[4]:is[5]], dest, cost )
		if e != nil {
			sendOutput( "FAIL:" + e.String() )
			break
		}
		sendOutput( "SUCCESS exit info set:" )
	case "path":
		is := rx2UnquotedArgs.FindStringSubmatchIndex( cmd )
		if len(is) < 6 || is[2] == -1 || is[4] == -1 {
			sendOutput("FAIL:usage path [room1] [room2]" )
			break
		}
		orig, e := roomId( cmd[ is[2]:is[3] ] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		dest, e := roomId( cmd[ is[4]:is[5] ] )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		p, e := mm.Path( orig, dest )
		if e != nil {
			sendOutput("FAIL:" +e.String() )
			break
		}
		pbs := []byte("SUCCESS path:")
		tbs := []byte("T=")
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
	case "help":
		outputCommands()
	default :
		sendOutput("FAIL:Unknown command \""+cmd+"\" ('help' for help)")
	}
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
	sendOutput("SUCCESS:"+
	"regions;"+
	"newRegion [name];"+
	"renameRegion [oldName] [newName];"+
	"regionRooms [regionName];"+
	"delRegion [region name];"+
	";"+
	"newRoom [regionName];"+
	"roomRegion [room];"+
	"delRoom [room];"+
	"setSDesc [room] <shrt desc>;"+
	"setLDesc [room] <long description>;"+
	"sDesc [room];"+
	"lDesc [room];"+
	"exitInfo [room] [exit string], returns<dest|UNKNOWN>,H?,T?,<cost>;"+
	"exits [room], returns<cmd>,<dest|UNKNOWN>,H?,T?,<cost>;"+
	"delExit [room] [exit string];"+
	"addExit ([HhTt])? [room] [exit string];"+
	"setExit [room] [exit string] [dest] [cost];"+
	";"+
	"setCur [room];"+
	"cur;"+
	"prev;"+
	";"+
	"tags;"+
	"newTag [name];"+
	"delTag [tag name];"+
	"tagged [tag name];"+
	"untag [room] [tag];"+
	"tag [room] [tag];"+
	"tagsOnRoom [room];"+
	"taggedInRegion [tag] [regionName];"+
	"names;"+
	"name [room] [name];"+
	"roomId [room];"+
	"unname [name];"+
	";"+
	"whereAmI s=\"short desc\" l=\"the \\\"long\\\" description\" (n,s,u,jump);"+
	";"+
	"path [room] [room];"+
	";"+
	"cleanMap;"+
	"cacheSize;"+
	"setcacheSize;"+
	";"+
	"commit;"+
	"commitAndPack;"+
	"rollback;"+
	"quit")
}
