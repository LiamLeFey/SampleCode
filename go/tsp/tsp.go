package main

import (
	"bufio"
	"fmt"
	"math"
	"os"
	"strconv"
	"strings"
)

// this is an implementation of a traveling salesman algorithm, using recursion.
// I'm not entirely sure, but I think I wrote it to test wall clock speed of go, 
// because the big O is exponential, so it's easy to hit measurable speeds with 
// a small data set.
func main() {
	fmt.Print("starting coordinates: ")
	inReader := bufio.NewReader(os.Stdin)
	bs, _, _ := inReader.ReadLine()
	s := string(bs)
	origin := interpret(s)
	dests := make([]coords, 0, 5)
	for {
		fmt.Print("destination coordinates (empty to stop): ")
		bs, _, _ = inReader.ReadLine()
		s = string(bs)
		if len(s) < 2 {
			break
		}
		dests = append(dests, interpret(s))
	}
	tsp(origin, dests)
}

func interpret(s string) (c coords) {
	ints := strings.Fields(s)
	c.x, _ = strconv.Atoi(ints[0])
	c.y, _ = strconv.Atoi(ints[1])
	return c
}

type coords struct {
	x, y int
}

func dist(origin, dest coords) int {
	a := origin.x - dest.x
	b := origin.y - dest.y
	return int(math.Sqrt(float64(a*a + b*b)))
}
func tsp(origin coords, dests []coords) {
	b, cost := best(origin, dests, make([]int, 0, len(dests)))
	fmt.Println("Best order, with cost", cost, "is:")
	for i := range b {
		fmt.Println(dests[b[i]])
	}
}
func best(origin coords, dests []coords, partial []int) (order []int, cst int) {
	if len(partial) == len(dests) {
		return partial, cost(origin, dests, partial)
	}
	//partialPlus := make([]int, len(partial)+1, cap(partial))
	partialPlus := make([]int, 0, cap(partial))
	partialPlus = append(partialPlus, partial...)
	partialPlus = append(partialPlus, 0)
	var bo []int
	bc := math.MaxInt32
OUTER:
	for i := 0; i < len(dests); i++ {
		for j := 0; j < len(partial); j++ {
			if partial[j] == i {
				continue OUTER
			}
		}
		partialPlus[len(partial)] = i
		o, c := best(origin, dests, partialPlus)
		if c < bc {
			bo = o
			bc = c
		}
	}
	return bo, bc
}
func cost(origin coords, dests []coords, order []int) int {
	cost := 0
	cost += dist(origin, dests[order[0]])
	for i := 0; i < len(order)-1; i++ {
		cost += dist(dests[order[i]], dests[order[i+1]])
	}
	cost += dist(dests[order[len(order)-1]], origin)
	return cost
}
