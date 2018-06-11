package mudclient.core;

import java.util.*;

public class VariableScope{
  private static final String[] KEY_TYPE = new String[]{};
  private VariableScope parent;
  private HashMap<String, Object> variables;

  public VariableScope( VariableScope parent ){
    this.parent = parent;
    variables = new HashMap<String, Object>();
  }

  public HashSet<String> getLocalVarNames(){
    return new HashSet<String>( variables.keySet() );
  }
  public HashSet<String>  getRootVarNames(){
    return new HashSet<String>( getRoot().variables.keySet() );
  }
  public HashSet<String>  getVarNames(){
    HashSet<String> keys;
    if( parent == null )
      keys = new HashSet<String>();
    else
      keys = parent.getVarNames();
    keys.addAll( variables.keySet() );
    return keys;
  }

  public Object getValue( String varName ){
    Object o = variables.get( varName );
    if( o != null )
      return o;
    if( parent != null )
      return parent.getValue( varName );
    return null;
  }
  public Object setValue( String varName, Object value ){
    return variables.put( varName, value );
  }
  public VariableScope getRoot(){
    VariableScope r = this;
    while( r.parent != null )
      r = r.parent;
    return r;
  }
  public Object setRootValue( String varName, Object value ){
    return getRoot().variables.put( varName, value );
  }
  public VariableScope getParent(){
    return parent;
  }
  public Object unSetVariable( String varName ){
    if( variables.containsKey( varName ) )
      return variables.remove( varName );
    if( parent == null )
      return null;
    return parent.unSetVariable( varName );
  }
  public Object unSetLocalVariable( String varName ){
    return variables.remove( varName );
  }
  public boolean isVariable( String varName ){
    if( variables.containsKey( varName ) )
      return true;
    return parent != null && parent.isVariable( varName );
  }
  public boolean isLocalVariable( String varName ){
    return variables.containsKey( varName );
  }
  public String getDefineString( String varName ){
    if( varName.matches("_.*") ) return "";
    Object value = variables.get( varName );
    if( value != null )
      return "/def " + (parent == null ? "-g " : "") + varName + " " + value;
    return (parent == null ? null : parent.getDefineString( varName ));
  }
}
