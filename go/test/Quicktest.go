package main

import (
	//"project/mudmap"
	//"project/tools"
	//"flag"
	//"strings"
	//"regexp"
	//"strconv"
	//"bufio"
	"fmt"
	"os"
	"syscall"
	)


func main() {

	//flag.Parse()
	//args := flag.Args()
	//if len( args ) != 3 {
		//fmt.Println( args )
		//fmt.Println( "Usage: MMfifo [mapDir] [inputFifo] [outputFifo]" )
		//syscall.Exit( -1 )
	//}
	//mapDirName := args[ 0 ]
	inFileName := "./infifo"
	outFileName := "./outfifo"

	//perms := syscall.S_IRUSR | syscall.S_IWUSR | syscall.S_IXUSR
	//e := os.MkdirAll( mapDirName, perms )
	//e := os.MkdirAll( mapDirName, syscall.S_IRUSR | syscall.S_IWUSR | syscall.S_IXUSR )
	//if e != nil {
		//fmt.Println( "Could not make directory:", mapDirName )
		//fmt.Println( e )
		//syscall.Exit( -1 )
		//panic( os.NewError( "could not make directory" ) )
	//}

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
	//inReader = bufio.NewReader( inFifo )
	flag := os.O_TRUNC | os.O_WRONLY
	outFifo, e := os.OpenFile( outFileName, flag, 0600 )
	if e != nil {
		fmt.Println( "problem with outFifo:", e )
		//syscall.Exit( -1 )
		panic( os.NewError( "problem with outFifo" ) )
	}
	defer outFifo.Close()
	//outWriter = bufio.NewWriter( outFifo )
	//defer outWriter.Flush()
}
