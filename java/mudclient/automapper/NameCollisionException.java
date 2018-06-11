package mudclient.automapper;

public class NameCollisionException extends RuntimeException{
  public NameCollisionException( String str ){
    super( str );
  }
}
