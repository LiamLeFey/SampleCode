package gobin

import (
	"math/rand"
	"os"
	"path/filepath"
	"sort"
	"testing"
)

func TestIntSrtSet(t *testing.T) {
	s := new(intSrtSet)
	if !s.Add( 1 ) {
		t.Error( "s.Add( 1 ) on empty set returned false." )
	}
	if s.Add(1) {
		t.Error( "s.Add( 1 ) returned true the second time." )
	}
	if s.Remove(10) {
		t.Error( "remove 10 returned true (10 not in).")
	}
	if !s.Add( 10 ) {
		t.Error( "s.Add( 10 ) returned false." )
	}
	if s.Size() != 2 {
		t.Error( "size not 2.")
	}
	if !s.Remove(1) {
		t.Error( "remove 1 returned false (1 was in).")
	}
	if s.Size() != 1 {
		t.Error( "size not 1.")
	}
	if !s.Remove(10) {
		t.Error( "remove 10 returned false (10 was in).")
	}
	if s.Size() != 0 {
		t.Error( "size not 0.")
	}
}
func BenchmarkIntSrtSet_GrowToN(b *testing.B) {
	s := new(intSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(i)
	}
	a := s.Values()
	if !sort.IntsAreSorted(a) {
		b.Error("ints not sorted!")
	}
}
func BenchmarkIntSrtSet_GrowDelete(b *testing.B) {
	s := new(intSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(i)
	}
	a := s.Values()
	if !sort.IntsAreSorted(a) {
		b.Error("ints not sorted after added!")
	}
	for i := 0; i < b.N; i++ {
		if !s.Remove(i) {
			b.Error("remove returned false when removing existing int")
		}
	}
	if s.Size() != 0 {
		b.Error("Size() != 0 after removing all elements")
	}
	a = s.Values()
	if len(a) != 0 {
		b.Error("Values returned non-empty slice after removing all elements")
	}
}
func BenchmarkIntSrtSet_GrowToN_Rand(b *testing.B) {
	s := new(intSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(rand.Int())
	}
	a := s.Values()
	if !sort.IntsAreSorted(a) {
		b.Error("ints not sorted!")
	}
}
func BenchmarkIntSrtSet_GrowDelete_Rand(b *testing.B) {
	s := new(intSrtSet)
	for i := 0; i < b.N; i++ {
		s.Add(rand.Int())
	}
	a := s.Values()
	if !sort.IntsAreSorted(a) {
		b.Error("ints not sorted after added!")
	}
	for i := 0; i < b.N; i++ {
		s.Remove(rand.Int())
	}
	a = s.Values()
	if !sort.IntsAreSorted(a) {
		b.Error("ints not sorted after removed!")
	}
}


func smallReport(bin *Gobin, t *testing.T){
	t.Log("types:", bin.types )
	t.Log("idMap:", bin.idMap )
	t.Log("nameMap:", bin.nameMap )
}
func mediumReport( bin *Gobin, t *testing.T){
	t.Log("Medium Report:")
	smallReport(bin, t)
	t.Log("committed:", bin.committed)
	t.Log("smapDirty:", bin.smapDirty)
	t.Log("maxId:", bin.maxId)
	smapReport( bin.smap, t )
}
func fullReport(bin *Gobin, t *testing.T){
	t.Log("Full Report:")
	t.Log("store:", bin.store)
	smallReport(bin, t)
	t.Log("buffer:", *bin.buffer)
	t.Log("encoder:", *bin.encoder)
	t.Log("decoder:", *bin.decoder)
	t.Log("committed:", bin.committed)
	t.Log("smapDirty:", bin.smapDirty)
	t.Log("maxId:", bin.maxId)
	smapReport( bin.smap, t )
}
func smapReport( m *storeMap, t *testing.T ){
	t.Log("byte map:")

	a := m.fsSizes.Values()
	t.Log("free space sizes:", a)
	// all locations of free space of that size.
	t.Log("free space size to locations map: ")
	// sort it first, so it's easier to read.
	ks := new(intSrtSet)
	for k, _ := range m.fsMap {
		ks.Add( k )
	}
	ka := ks.Values()
	for _, k := range ka {
		t.Log("  ", k, ":", m.fsMap[k].Values())
	}
	t.Log("borders:", m.borders.Values())
	t.Log("changed floors (states == 0x01||0x03):", m.changedFloors.Values())
	// 0x00 = free, 0x01 = free on commit,
	// 0x02 = used, 0x03 = free on rollback
	t.Log("states:")

	// sort it first, so it's easier to read.
	ks = new(intSrtSet)
	for k, _ := range m.states {
		ks.Add( k )
	}
	ka = ks.Values()
	for i := range ka {
		switch m.states[ka[i]] {
		case 0x00 :
			t.Log( "  ",ka[i],": free ")
		case 0x01 :
			t.Log( "  ",ka[i], ": free@commit ")
		case 0x02 :
			t.Log( "  ",ka[i], ": used ")
		case 0x03 :
			t.Log( "  ",ka[i], ": free@rollback ")
		default :
		t.Log( "  ",ka[i], ": UNDEFINED!! ")
		}
	}
}
func TestGobinA( t *testing.T){
	fn := filepath.Join(os.TempDir(), "TestGobinA.dat")
	err := os.RemoveAll( fn )
	if err != nil {
		t.Log(err.Error())
		t.Error("error removing TestGobinA.dat")
	}
	f, err := os.Create( fn )
	//defer os.RemoveAll(fn)
	defer f.Close()
	if err != nil {
		t.Log(err.Error())
		t.Error("error creating TestGobinA.dat")
	}
	gobinA, err := New( f )
	if err != nil {
		t.Log(err.Error())
		t.Error("error creating gobinA")
	}
	err = gobinA.Commit()
	if err != nil {
		t.Log(err.Error())
		t.Error("error committing gobinA")
	}
	err = f.Close()
	if err != nil {
		t.Log(err.Error())
		t.Error("error closing file")
	}
	f2, err := os.OpenFile( fn, os.O_RDWR, 0666 )
	defer f2.Close()
	if err != nil {
		t.Log(err.Error())
		t.Error("error re-openning file")
	}
	gobinB, err := New( f2 )
	if err != nil {
		t.Log(err.Error())
		t.Error("error creating gobinB")
	}
	// add and read back some stuff
	id, err := gobinB.Encode( 3 )
	if err != nil {
		t.Log(err.Error())
		t.Error("error storing ( 3 )")
	}
	var i, j int
	err = gobinB.Decode( &i, id )
	if err != nil {
		t.Log(err.Error())
		t.Error("error retrieving ( 3 ) by id")
	}
	if i != 3 {
		t.Error("retrieved value != 3")
	}
	i = 12
	err = gobinB.EncodeId( &i, 17 )
	gobinB.NameId( "seventeen", 17 )
	id2, b := gobinB.IdForName("seventeen")
	if id2 != 17 || !b {
		t.Error("error resolving \"seventeen\" to 17")
	}
	err = gobinB.Decode( &j, id2 )
	if err != nil {
		t.Log(err.Error())
		t.Error("error retrieving ( id2 ) ")
	}
	if i != j {
		t.Error("i != j")
	}
	err = gobinB.Commit()
	if err != nil {
		t.Log(err.Error())
		t.Error("error committing gobinB")
	}
	// createC, read the stuff, delete it
	gobinC, err := New( f2 )
	if err != nil {
		t.Log(err.Error())
		t.Error("error creating gobinC")
	}
	var k, l int
	err = gobinC.Decode( &k, id )
	if err != nil {
		t.Log(err.Error())
		t.Error("error retrieving ValueOf( 3 ) by id from gobinC")
	}
	id2, b = gobinC.IdForName("seventeen")
	if id2 != 17 || !b {
		t.Error("error resolving \"seventeen\" to 17 from gobinC")
	}
	err = gobinC.Decode( &l, id2 )
	if err != nil {
		t.Log(err.Error())
		t.Error("error retrieving ( id2 ) from gobinC ")
	}
	if k != 3 || l != 12 {
		t.Error(" error: k != 3 || l != 12 ")
	}
	gobinC.Delete( id2 )
	gobinC.Rollback()
	err = gobinC.Decode( &k, id2 )
	if err != nil || k != 12 {
		t.Log(err.Error())
		t.Error("in gobinC, retrieve failed after delete/rollback ")
	}
	k = 532
	gobinC.Delete( id2 )
	gobinC.Commit()
	err = gobinC.Decode( &k, id2 )
	if err == nil || k != 532 {
		t.Log(err.Error())
		t.Error("in gobinC, retrieve succeeded after delete/commit ")
	}
}
func TestGobinB( t *testing.T){
	fn := filepath.Join(os.TempDir(), "TestGobinB.dat")
	f, err := os.Create( fn )
	defer f.Close()
	if err != nil {
		t.Error(err.Error())
	}
	gtl := newGobinTestList(f)
	for _, s := range words {
		gtl.add( s )
	}
	if !gtl.isSorted() {
		t.Error("list of words was not properly sorted")
	}
	cnt := gtl.count()
	if cnt != len(words) {
		t.Log("cnt:", cnt, "len(words):", len(words))
		t.Error("list of words had the wrong count")
	}
	// now we test deleting with a gobin that hasn't seen a type
	gb, err := New(f)
	if err != nil {
		t.Log("Error while creating a second gobin on an active gobin file.")
		t.Error(err.Error())
	}
	_, err = gb.Encode(23)
	if err != nil {
		t.Log("Error encoding into a second gobin on an active gobin file.")
		t.Error(err.Error())
	}
	var id int
	var b bool
	id, b = gb.IdForName("head")
	if !b {
		t.Error("could not IdForName(\"head\") from second gobin.")
	}
	err = gb.Delete(id)
	if err != nil {
		t.Log("Error deleting a value of type gobin hasn't seen.")
		t.Error(err.Error())
	}
	gb.Commit()
	var n gobinListNode
	err = gb.Decode( &n, id )
	if err == nil {
		t.Log("retrieved node:", n, "after delete.")
		t.Error("in second gobin, retrieve succeeded after delete/commit ")
	}
}
func BenchmarkGobinA(b *testing.B) {
	fn := filepath.Join(os.TempDir(), "BenchmarkGobinA.dat")
	f, err := os.Create( fn )
	if err != nil {
		panic(err)
	}
	var s string // just so we aren't simply disposing the retrieved values
	defer f.Close()
	bin, _ := New( f )
	n := len(words)
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		x := rand.Intn(n)
		if rand.Intn(2) == 0 {
			bin.EncodeId( words[x], x )
		} else {
			bin.Decode( &s, x )
		}
	}
}

type gobinTestList struct {
	bin Gobin
}
func newGobinTestList( file *os.File ) (gtl *gobinTestList) {
	gb, err := New( file )
	if err != nil {
		panic(err)
	}
	if _, b := gb.IdForName("head"); !b {
		gb.NameId("head", -1 )
		gb.NameId("tail", -1 )
		gb.Commit()
	}
	return &gobinTestList{*gb}
}
func (gtl *gobinTestList) add( s string ) {
	var p, n gobinListNode
	next, _ := gtl.bin.IdForName("head")
	for next != -1 {
		gtl.bin.Decode(&n, next)
		if n.S > s {
			break
		}
		next = n.Next
		p.S, p.Next, p.Prev = n.S, n.Next, n.Prev
	}
	if next == -1 {
		n = gobinListNode{"", -1, -1}
	}
	var id, t int
	var err error
	if p.S == "" { // janky, I know. but...
		id, err = gtl.bin.Encode( gobinListNode{s, next, -1} )
		if err != nil {
			panic(err)
		}
		gtl.bin.NameId( "head", id )
		if next != -1 {
			n.Prev = id
			gtl.bin.EncodeId( n, next )
		}
	} else if n.S != "" { //p != nil & n != nil we're in middle
		id, err = gtl.bin.Encode( gobinListNode{s, p.Next, n.Prev } )
		if err != nil {
			panic(err)
		}
		p.Next = id
		gtl.bin.EncodeId( p, n.Prev )
		n.Prev = id
		gtl.bin.EncodeId( n, next )
	} else { // s is now tail
		t, _ = gtl.bin.IdForName("tail")
		id, err = gtl.bin.Encode(gobinListNode{s, -1, t})
		if err != nil {
			panic(err)
		}
		p.Next = id
		gtl.bin.EncodeId( p, t )
	}
	if next == -1 {
		gtl.bin.NameId( "tail", id )
	}
	gtl.bin.Commit()
}
func (gtl *gobinTestList) isSorted() (b bool) {
	var n gobinListNode
	var prev string
	var next int
	if next, b = gtl.bin.IdForName("head"); !b {
		next = -1
	}
	for next != -1 {
		gtl.bin.Decode( &n, next )
		if prev > n.S {
			return false
		}
		prev = n.S
		next = n.Next
	}
	return true
}
func (gtl *gobinTestList) count() (i int) {
	var n gobinListNode
	next, b := gtl.bin.IdForName("head")
	if !b {
		next = -1
	}
	for next != -1 {
		i++
		gtl.bin.Decode( &n, next )
		next = n.Next
	}
	return i
}
type gobinListNode struct {
	S string
	Next int
	Prev int
}
var words = [...]string{"patten","disorganized","slime","outring","nutwood",
		"jargonistic","prevoid","subsect","pyramidia","milage","utricle",
		"disarrange","aureola","anglophil","mikan","camellia","horseweed",
		"noncrustaceous","volitational","neoter","radiolysis","unlapped",
		"elizabethtown","meager","unblended","titrate","anticivic","machete",
		"battalion.","cupeled","dargah","massless","superconfident","tsukahara",
		"griping","lithaemia","hughes","euryphagous","microphone","zeta",
		"phyllodial","careworn","avalanched","jun","inflexionless","abridger",
		"tricot","democratized","balmont","preemptive","antipope","signora",
		"tirunelveli","autacoid","messines","nonactinic","misconceive",
		"parnahyba.","plumberies","forensically","lobulate","vitriol",
		"golschmann","annemarie","fair","oversystematically","privity",
		"demilune","hecabe","jawbone","tableland","adductive","lengthways",
		"ignescent","lyart","whatever","nathanael","nonconservation",
		"biddableness","sleepers","unsummable","episcopalian","honied",
		"junggrammatiker","genl","husker","glaciate.","electrobiology",
		"divisive","spit","zebralike","stylite","nonpersecuting","narcotist",
		"espanol","waterhouse","sideswiped","ectoblastic","reversibly","lifar",
		"shockingly","seashore","interinfluenced","phallus","kindle","dhooly",
		"unballoted","latinate","aegirite","solfeggio","rejoiceful","outpacing",
		"trivialize","theirs","unscrimped","teleutosorus.","unbuckle","lyttae",
		"lucerne","slumlord","rouletting","lydia","funneled","roxane",
		"unimprecated","hightstown","supposedly","glean","stockpile","priggish",
		"cheri","jiff","silverius","hypermetropy","resuscitative","apposability",
		"nakedly","bourg","massiveness","subsaturation","urbanise",
		"regenerateness","merited","astrophotographic","inefficacity.","surge",
		"lasagna","iceland","moshoeshoe","bluebird","untwined","boutonniere",
		"stereotyped","emancipative","predislike","phylloquinone","hairspring",
		"avignon","nonassimilatory","nolan","carpentaria","papen","redispersing",
		"charleton","sager","monsoon","epiclike","intercatenated","simulation",
		"enjoiner","stepwise","langlaufer","gainful","evyleen.","balshem",
		"prouniversity","postmammillary","nonsubtractive","speaking",
		"limicoline","carman","pommeling","semifictional","blackbutt","withier",
		"immotile","pauline","unnarratable","empathic","springhalt","oligopoly",
		"creaky","faming","sickness","foresleeve","pathographic","unsequent",
		"cobden","knp","hamelin","preconfession","bedeman","unredacted.",
		"hydrosere","unapplicative","ajee","overrationalize","disorder",
		"commensurateness","sailorly","synecious","nonexpedient","disrupt",
		"bacteriological","horoscopic","hyperalgia","sapiency","talker",
		"imploring","ureterostomy","underprentice","paunchy","filter","eponymy",
		"fertilizer","torsion","paraffine","sulphamerazine","peplos",
		"benedictus","erwinia","senusi.","orchard","lamentably","declinature",
		"chianti","flume","miniaturization","semirevolutionary","otalgic",
		"munster","quaternary","peridotitic","deoxidizer","corner","lynch",
		"spasmodist","schoolman","goosey","azeotrope","gymslip","boluses","irl",
		"outtyrannized","whatever","agamogenesis","mervin","uncollegiate",
		"plural","calix","oared.","nonretention","nonasthmatic","bexley",
		"suckerlike","nontopographical","astrodynamics","intentionally",
		"precommitted","nonpassenger","continuativeness","epanorthosis",
		"garlandless","excludible","metapsychological","foetor","nuance",
		"insubstantial","sech","redissolving","toolbox","cts","anodyne",
		"alcaide","brankier","saice","entangleable","reseparation","rappelled",
		"nilghau.","tubal","satisfied","uricolytic","cannoneer","nontransposing",
		"charismatic","mosasaur","valry","dishcross","chirring","unlethargical",
		"pagan","zakat","throat","extraembryonal","penis","ornamental",
		"laryngal","equalize","diatreme","underrunning","saintlily","lom",
		"dolce","wyat","gamelan","theurgic","flickertail","hydronaut.","hardie",
		"cratch","creese","delos","braided","climate","staddlestone","magi",
		"deductibility","thermolytic","orchidologist","malinovsky","greenkeeper",
		"ampelopsis","clearheadedness","hess","idyll","undiscernable","allopath",
		"sogdiana","manlikely","diy","undermoral","fetting","bedazzle","assuror",
		"prayerfully","pomona","overqualifying.","watermark","overdearly",
		"ovambo","khos","fractionated","misbind","chemical","angkor","peruke",
		"cigar","radiocarbon","perviousness","prepositively","dulcify",
		"stirpiculturist","perionychium","innervation","mosaic","emerging",
		"unremonstrative","forespake","pistology","conglobating","gladly",
		"companionable","slenderise","robotize","guernsey","diaper.",
		"conception","denison","form","heavenless","psychologically",
		"uncontended","housemaid","indagated","spilling","artal","salts",
		"shipentine","cammaerts","mopboard","batumi","mislayer","mylitta",
		"folketing","watermelon","jerkiness","sundew","petrel","salinger",
		"graveyard","initiate","sow","supergaiety","reindorsement","lockhart.",
		"decolorised","ecliptic","angularness","deplumed","overpersuade",
		"vaticinal","etymologized","loiret","moosemilk","preeminent",
		"pyroligneous","unpawned","chat","thermosphere","prochurch","heroics",
		"glossless","availably","vas","palooka","satirical","effused","limply",
		"poultice","velites","pryer","calumnious","herodias","vorticose.",
		"nonnattily","monophthongal","suppliantness","dalmatia","pyrolignic",
		"frostwork","companionably","ivor","divorcive","janos","nonsublimation",
		"smolensk","elytroid","refuelled","gonydial","bebel","sclerodermatitis",
		"unpolluted","braille","sandersville","unnew","maculating",
		"prestidigitatory","nostalgy","cliquism","buckeye","apiaceous",
		"fredrich","unison.","transdiurnal","tocharian","cateringly",
		"rntgenographically","sync","dirhem","supercatastrophic",
		"microminiaturizing","pitchometer","administratrix","unsenescent",
		"francophone","sunburn","spittlebug","angulateness","misworshipping",
		"eliot","bro","kalb","unparolable","chavez","synthesist","unsterile",
		"redrove","supertrivial","justinianian","pipestone","spryest",
		"untagged.","micrify","outrant","isoprene","cucurbit","friendly",
		"idiotically","salifying","demonstrated","dascylus","individualise",
		"goanna","nix","photoflight","unchromed","signalman","normandy",
		"intermitting","uraeuses","chkalov","intravenously","bristols",
		"highflying","preexclusive","noninflationary","unapplauding",
		"unprisonable","nonhesitant","blacken","whittle.","assiniboine",
		"fremdly","thermoclinal","handset","dispiritedly","sexagesima",
		"exchanger","salvationist","overenthusiasm","natant","exculpated",
		"novaculite","commonality","epiglottides","durability","agoraphobia",
		"fluking","trusteeship","unsportsmanly","untalkative","filly","caspar",
		"disprized","multilinear","greenboard","nonadministrative","stagnantly",
		"lych","prominent.","gyniatry","immaterial","toft","palanquin","redress",
		"aegina","hodges","shiraz","bibliolatrist","sniff","bikol","unpennoned",
		"pond","hypanthia","immensurability","rial","paranoid","choko",
		"laestrygon","bold","multistratified","graft","corol","nonstatic",
		"peculiar","immixture","subnucleus","subfractionary","muezzin."}
