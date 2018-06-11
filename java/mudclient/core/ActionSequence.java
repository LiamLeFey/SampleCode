package mudclient.core;

import java.util.ArrayList;
import java.io.IOException;

public class ActionSequence {
  private static final String[] STRING_ARRAY_TYPE = new String[]{};
  private ArrayList<String> sequence;

  public ActionSequence(){
    sequence = new ArrayList<String>();
  }
  public void addStep( String s ){
    sequence.add( s );
  }
  public String[] getSteps(){
    return sequence.toArray( STRING_ARRAY_TYPE );
  }
  public Object perform( VariableScope scope, WorldConnection foreground )
  throws IOException{
    Object result = null;
    int i;
    for( i = 0; i < sequence.size(); i++ ){
      result = CmdInterpreter.interpret( 
          sequence.get( i ), 
          scope, 
          foreground );
    }
    return result;
  }
  public String toString(){
    StringBuffer sb = new StringBuffer();
    int size = sequence.size();
    String s;
    for( int i = 0; i < size; i++ ){
      s = sequence.get( i );
      if( s.indexOf( '/' ) != -1 )
        sb.append('(').append(s).append(')');
      else
        sb.append(s);
      if( i < size-1 )
        sb.append('/');
    }
    return sb.toString();
  }
}
