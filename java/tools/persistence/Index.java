package tools.persistence;

/** This interface is for generating indexes for Streamable objects.
 * generally, if you want to use something other than an id value
 * of a Storable object, you can use this.  For instance, if you
 * had a Storable String class, and often looked them up by
 * the 4th through the 10th characters, you could create an instance
 * of Index that checked the hashCode of just those characters, and
 * this would make these objects much easier to retrieve in a
 * PersistentStore.<br>
 *
 * Generally, you also would provide a method which passed just
 * the info and returned the same value, for instance in our String
 * example, you could create a method which took a string of 7 
 * characters length, and returned the code.
 */
public interface Index{
  public int getCode( Object o );
}
