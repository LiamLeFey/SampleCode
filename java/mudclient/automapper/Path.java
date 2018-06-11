package mudclient.automapper;

import java.util.*;
import tools.IntSet;
import tools.Heap;


public class Path{

  // the list of steps to get from pointA to pointB
  private List steps;
  private int extraCost = 0;

  private Path(){
    steps = new ArrayList();
  }
  private Path( Object[] os ){
    steps = new ArrayList( Arrays.asList( os ) );
  }
  private void addExtraCost( int c ){
    extraCost += c ;
  }
  private void prepend( SpecialExit e, int i, int cost ){
    steps.add( 0, new SEWrapper( e, i ) );
    extraCost += cost;
  }
  private void prepend( String e ){
    steps.add( 0, e );
  }
  private int getExtraCost(){
    return extraCost;
  }
  private void prepend( Path otherPath ){
    ArrayList newSteps = new ArrayList();
    int i;
    int otherLength = otherPath.getLength();
    int oldLength = steps.size();
    for( i = 0; i < otherLength; i++ ){
      newSteps.add( otherPath.steps.get( i ) );
    }
    for( i = 0; i < oldLength; i++ ){
      newSteps.add( steps.get( i ) );
    }
    extraCost += otherPath.getExtraCost();
    steps = newSteps;
  }
  private void add( Path otherPath ){
    int i;
    int otherPathLength = otherPath.steps.size();
    for (i = 0; i < otherPathLength; i++ ){
      steps.add( otherPath.steps.get( i ) );
    }
    extraCost += otherPath.getExtraCost();
  }
  private void add( SpecialExit e, int i, int cost ){
    steps.add( new SEWrapper( e, i ) );
    extraCost += cost;
  }
  private void add( String e ){
    steps.add( e );
  }
  int getLength(){
    return steps.size();
  }
  int getCost(){
    return getLength() + getExtraCost();
  }

  public String getSteps(){
    Iterator it = steps.iterator();
    StringBuffer sb = new StringBuffer();
    String temps;
    while( it.hasNext() ){
      Object o = it.next();
      if( o instanceof String ){
        sb.append( o + "\n" );
      }else{
        temps = ((SEWrapper)o).getSequence();
        sb.append( temps );
        if( temps.indexOf( SpecialExit.BREAKPATH ) != -1 )
          break;
      }
    }
    return sb.toString();
  }
  public static int getPathCost(
      int pointA,
      int pointB,
      RoomManager rm,
      RegionManager rgm,
      SpecialExitManager sem){
    IntSet visited = new IntSet();
    Heap nodes = new Heap();
    int i, j, k, dest;
    //visited.add( pointA );
    int regionA = rm.get( pointA ).getRegion();
    int regionB = rm.get( pointB ).getRegion();
    int[] AExits = rgm.get(regionA).getExits();
    int[] BEntrances = rgm.get(regionB).getEntrances();
    IntSet BEntranceSet = new IntSet( BEntrances.length+1 );
    if( regionA == regionB )
      BEntranceSet.add( pointA );
    for( i = 0; i < BEntrances.length; i++ )
      BEntranceSet.add( BEntrances[i] );
    int[] costs, dests;
    Room r, r2;
    Region reg;
    SpecialExit se;
    nodes.add( new CostSearchElement( 0, pointA ) );
    costs = getIntraRegionCosts( pointA, AExits, rm, sem );
    for( i = 0; i < costs.length; i++ )
      if( ! visited.contains( AExits[i] ) && costs[i] >= 0 )
        nodes.add( new CostSearchElement( costs[i], AExits[i] ) );
    while( nodes.hasNext() ){
      CostSearchElement e = (CostSearchElement)nodes.next();
      if( e.destination == pointB )
        return e.cost;
      if( visited.contains( e.destination ) )
        continue;
      visited.add( e.destination );
      if( BEntranceSet.contains( e.destination ) ){
        int cost = getIntraRegionCosts( e.destination, 
            new int[]{pointB}, rm, sem )[0];
        if( cost >= 0 ){
          nodes.add( new CostSearchElement( e.cost + cost, pointB ) );
        }
      }
      r = rm.get( e.destination );
      if( r == null )
        continue;
      reg = rgm.get( r.getRegion() );
      if( reg.isEntrance( r.getID() ) ){
        int[][] exitLengths = reg.getCachedExitLengths( r.getID() );
        for( j = 0; j < exitLengths.length; j++ )
          if( ! visited.contains( exitLengths[j][0] ) )
            nodes.add( new CostSearchElement( 
                  e.cost + exitLengths[j][1], exitLengths[j][0] ) );
      }else{
        String[] cmds = r.getExits();
        for( j = 0; j < cmds.length; j++ ){
          dest = r.getDest( cmds[j] );
          if( dest == Exit.SPECIAL_EXIT ){
            se = sem.get( r.getID(), cmds[j] );
            dests = se.getDestinations();
            costs = se.getDestinationCosts();
            for( k = 0; k < dests.length; k++ )
              if( ! visited.contains( dests[k] ) 
                  && (r2 = rm.get( dests[k] )) != null 
                  && r2.getRegion() != r.getRegion() )
                nodes.add( new CostSearchElement( 
                      e.cost+costs[k], dests[k] ) );
          }else if( ! visited.contains( dest ) 
              && (r2 = rm.get( dest )) != null 
              && r2.getRegion() != r.getRegion() )
            nodes.add( new CostSearchElement( e.cost+1, dest ) );
        }
      }
    }
    return -1;
  }
  static int[] getIntraRegionCosts( int pointA, int[] pointsB, 
      RoomManager rm, SpecialExitManager sem ){
    CostSearchElement e;
    int i, j, k;
    int region = rm.get( pointA ).getRegion();
    int pathsToFind = pointsB.length;
    int[] returnValues = new int[ pointsB.length ];
    Arrays.fill( returnValues, -1 );

    for( i = 0; i < pointsB.length; i++ ){
      if( region != rm.get( pointsB[ i ] ).getRegion() ){
        pathsToFind--;
      }else if( pointA == pointsB[ i ] ){
        returnValues[ i ] = 0;
        pathsToFind--;
      }
    }
    // so much for the simple cases.
    Heap nodes = new Heap();
    IntSet visited = new IntSet();
    Room r;
    SpecialExit se;
    String[] exits;
    int[] dests, seDests, seCosts;
    // kick start the nodes list
    //visited.add( pointA );
    nodes.add( new CostSearchElement( 0, pointA ) );
    while( nodes.hasNext() && pathsToFind > 0 ){
      e = (CostSearchElement)nodes.next( );
      if( visited.contains( e.destination ) )
        continue;
      k = e.destination;
      for( j = 0; j < pointsB.length; j++ ){
        if( returnValues[j] == -1 && k == pointsB[ j ] ){
          returnValues[ j ] = e.cost;
          pathsToFind--;
        }
      }
      r = rm.get( k );
      visited.add( k );
      exits = r.getExits();
      dests = new int[ exits.length ];
      for( j = 0; j < exits.length; j++ )
        dests[ j ] = r.getDest( exits[ j ] );
      for( j = 0; j < exits.length; j++ ){
        if( dests[j] == Exit.SPECIAL_EXIT ){
          se = sem.get( k, exits[j] );
          seDests = se.getDestinations();
          seCosts = se.getDestinationCosts();
          for( k = 0; k < seDests.length; k++ ){
            if( seDests[k] != Exit.UNASSIGNED_DESTINATION
                && ! visited.contains( seDests[k] )
                && (r = rm.get( seDests[k] )) != null
                && r.getRegion() == region )
              nodes.add( new CostSearchElement( 
                    e.cost + seCosts[k], 
                    seDests[k] ) );
          }
        }else if( dests[j] != Exit.UNASSIGNED_DESTINATION
            && ! visited.contains( dests[j] )
            && (r = rm.get( dests[j] )) != null
            && r.getRegion() == region )
          nodes.add( new CostSearchElement( 
                  e.cost + 1, dests[j] ) );
      }
    }
    return returnValues;
  }
  public static Path getShortestPath( 
      int pointA, 
      int pointB, 
      RoomManager rm,
      RegionManager rgm,
      SpecialExitManager sem){
    int i, j, k, l;
    int regionA = rm.get( pointA ).getRegion();
    int regionB = rm.get( pointB ).getRegion();
    int[] AExits = rgm.get(regionA).getExits();
    int[] BEntrances = rgm.get(regionB).getEntrances();

    IntSet BEntranceSet = new IntSet( BEntrances.length+1 );
    if( regionA == regionB )
      BEntranceSet.add( pointA );
    for( i = 0; i < BEntrances.length; i++ )
      BEntranceSet.add( BEntrances[i] );

    IntSet visited = new IntSet();
    Heap nodes = new Heap();
    Path[] tempPaths;
    int[] costs;
    int[] dests;

    SearchElement element = new SearchElement( 0, null, null, pointA );
    // start by putting in pointA
    nodes.add( element );
    Region reg;
    reg = rgm.get( regionA );

    // add costs to regionA exits
    if( ! reg.isEntrance( pointA ) ){
      costs = getIntraRegionCosts( pointA, AExits, rm, sem );
      for( i = 0; i < costs.length; i++ )
        if( costs[i] >= 0 )
          nodes.add( new SearchElement( costs[i], element, null, AExits[i] ) );
    }
    int regID;
    Room r;
    i = 0;
    while( nodes.hasNext() ){
      element = (SearchElement)nodes.next();
      if( visited.contains( element.destination ) )
        continue;
      if( element.destination == pointB ){ // woo hoo!
        return getPathFromSearchElement( element, rm, sem );
      }else{
        if( BEntranceSet.contains( element.destination ) ){
          int cost = getIntraRegionCosts( element.destination, 
                new int[]{ pointB }, rm, sem )[0];
          if( cost >= 0 ){
            cost += element.cost;
            nodes.add( new SearchElement( cost, element, null, pointB ) );
          }
        }
        // add the cached ones from region
        regID = rm.get(element.destination).getRegion();
        reg = rgm.get(regID);
        if( reg.isEntrance( element.destination ) ){
          int[][] regionExits = reg.getCachedExitLengths( element.destination );
          for( j = 0; j < regionExits.length; j++ ){
            if( ! visited.contains( regionExits[j][0] ) ){
              nodes.add(
                  new SearchElement(
                    element.cost + regionExits[j][1],
                    element,
                    null,
                    regionExits[j][0]) );
            }
          }
        //}else{
        }
        if( reg.isExit( element.destination ) ){
          // get all destinations that leave the region.
          r = rm.get( element.destination );
          String[] cmds = r.getExits();
          for( j = 0; j < cmds.length; j++ ){
            k = r.getDest( cmds[j] );
            if( k == Exit.SPECIAL_EXIT ){
              SpecialExit se = sem.get( element.destination, cmds[j]);
              dests = se.getDestinations();
              costs = se.getDestinationCosts();
              for( l = 0; l < dests.length; l++ )
                if( ! visited.contains( dests[l] ) 
                    && rm.get( dests[l] ).getRegion() != reg.getID() )
                  nodes.add( new SearchElement(
                        element.cost + costs[l],
                        element,
                        se,
                        dests[l] ) );
            }else if( k != Exit.UNASSIGNED_DESTINATION 
                && ! visited.contains( k )
                && rm.get( k ).getRegion() != reg.getID() )
              nodes.add( new SearchElement( element.cost + 1,
                      element,
                      cmds[j],
                      k ) );
          }
        }
      }
      visited.add( element.destination );
      i++;
    }
    return null;
  }

  private static Path getPathFromSearchElement( 
      SearchElement el, 
      RoomManager rmMgr, 
      SpecialExitManager seMgr ){
    Path returnValue = new Path();
    Path tempPath;
    int i, cost;
    while( el.previous != null ){
      if( el.exit != null ){
        if( el.exit instanceof SpecialExit ){
          cost = el.cost - el.previous.cost;
          returnValue.prepend( 
              (SpecialExit)el.exit, 
              ((SpecialExit)el.exit).getDestinationIndex( el.destination ),
              cost );
        }else{
          returnValue.prepend( (String)el.exit );
        }
      }else{
        tempPath = getIntraRegionPaths( 
            el.previous.destination, 
            new int[]{el.destination}, 
            rmMgr, seMgr )[0];
        returnValue.prepend( tempPath );
      }
      el = el.previous;
    }
    return returnValue;
  }

  // this will get the path from pointA to pointsB if they are in
  // the same region.  Otherwise it returns a null.
  // we could potentially not have this limitation, but we might end up
  // traversing the entire map to find the path.  This is the option
  // that Regions were created to avoid.
  static Path[] getIntraRegionPaths( 
      int pointA, 
      int pointsB[], 
      RoomManager rm,
      SpecialExitManager sem){
    int j;
    int i;
    int region = rm.get( pointA ).getRegion();
    int pathsToFind = pointsB.length;

    SearchElement[] endpoints = new SearchElement[ pointsB.length ];
    java.util.Arrays.fill( endpoints, null );

    Path[] returnPaths = new Path[ pointsB.length ];
    // simplest cases first
    for( i = 0; i < pointsB.length; i++ ){
      if( region != rm.get( pointsB[ i ] ).getRegion() ){
        returnPaths[ i ] = null;
        pathsToFind--;
      }else if( pointA == pointsB[ i ] ){
        returnPaths[ i ] = new Path();
        pathsToFind--;
      }
    }
    // so much for the simple cases.
    Heap nodes = new Heap();
    IntSet visited = new IntSet();
    Room r;
    SpecialExit se;
    int k; // key
    String[] exits;
    int[] dests, seDests, seCosts;
    SearchElement element;


    // kick start the nodes list
    //visited.add( pointA );
    nodes.add( new SearchElement( 0, null, null, pointA ) );
    while( nodes.hasNext() && pathsToFind > 0 ){
      element = (SearchElement)nodes.next( );
      if( visited.contains( element.destination ) )
        continue;
      k = element.destination;
      for( j = 0; j < pointsB.length; j++ ){
        if( endpoints[j] == null && k == pointsB[ j ] ){
          endpoints[ j ] = element;
          pathsToFind--;
        }
      }
      r = rm.get( k );
      visited.add( k );
      exits = r.getExits();
      dests = new int[ exits.length ];
      for( j = 0; j < exits.length; j++ )
        dests[ j ] = r.getDest( exits[ j ] );
      for( j = 0; j < exits.length; j++ ){
        if( dests[j] == Exit.SPECIAL_EXIT ){
          se = sem.get( k, exits[j] );
          seDests = se.getDestinations();
          seCosts = se.getDestinationCosts();
          for( k = 0; k < seDests.length; k++ ){
            if( seDests[k] != Exit.UNASSIGNED_DESTINATION
                && ! visited.contains( seDests[k] )
                && (r = rm.get( seDests[k] )) != null
                && r.getRegion() == region )
              nodes.add( new SearchElement( 
                    element.cost + seCosts[k], 
                    element, 
                    se, 
                    seDests[k] ) );
          }
        }else if( dests[j] != Exit.UNASSIGNED_DESTINATION
            && ! visited.contains( dests[j] )
            && (r = rm.get( dests[j] )) != null
            && r.getRegion() == region )
          nodes.add( new SearchElement( 
                  element.cost + 1, element, exits[ j ], dests[j] ) );
      }
    }
    ArrayList reversePath;
    for( i = 0; i < endpoints.length; i++ ){
      if( endpoints[ i ] != null ){
        element = endpoints[ i ];
        reversePath = new ArrayList();
        while( element != null ){
          reversePath.add( element );
          element = element.previous;
        }
        returnPaths[ i ] = new Path();
        for( j = reversePath.size() - 1; j >= 0; j-- ){
          element = (SearchElement)reversePath.get( j );
          if( element.exit != null )
            if( element.exit instanceof String )
              returnPaths[ i ].add( (String)element.exit );
            else
              returnPaths[ i ].add( 
                  (SpecialExit)element.exit,
                  ((SpecialExit)element.exit)
                    .getDestinationIndex( element.destination ),
                  element.cost - element.previous.cost );
        }
      }
    }
    return returnPaths;
  }
  private static class SearchElement implements Comparable{
    private int cost;
    private SearchElement previous;
    private Object exit;
    private int destination;

    private SearchElement( int cost, 
        SearchElement previous, 
        Object exit, 
        int destination ){
      this.cost = cost;
      this.previous = previous;
      this.exit = exit;
      this.destination = destination;
    }
    public int compareTo( Object other ){
      return cost - ((SearchElement)other).cost;
    }
  }
  private static class CostSearchElement implements Comparable{
    private int cost;
    private int destination;
    private CostSearchElement( int cost, int destination ){
      this.cost = cost;
      this.destination = destination;
    }
    public int compareTo( Object other ){
      return cost - ((CostSearchElement)other).cost;
    }
  }
  private static class SEWrapper{
    SpecialExit se;
    int i;
    private SEWrapper( SpecialExit se, int i ){
      this.se = se;
      this.i = i;
    }
    private String getSequence(){
      return se.getSequence( i );
    }
  }
}
