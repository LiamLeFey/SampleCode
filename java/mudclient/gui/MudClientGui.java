package mudclient.gui;

/**
 *  
 */
import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.text.Segment;
import javax.swing.text.AttributeSet;
import mudclient.core.*;

public class MudClientGui implements UserInterface, ActionListener{

  private JFrame mainFrame;
  private JTabbedPane worldViews;
  private JTextField inputField;
  private FontSelector fontSelector;
  private JMenuItem fontMenuItem;
  private JMenuItem lineWrapMenuItem;

  private static final String INFO_LABEL = "-info-";

  private Color fgColor = AnsiCode.WHITE;
  private Color bgColor = AnsiCode.BLACK;
  private Font font;

  private boolean infoOnCurrent = true;
  private boolean echoInputToOutput = true;

  public MudClientGui(){

    worldViews = new JTabbedPane();

    inputField = new JTextField();
    inputField.addActionListener( this );

    mainFrame = new JFrame("Mud Client");
    createAndInitialize();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        MudClientGui.this.show();
      }
    });
  }
  /**
   * create the visual layout.  For thread safety,
   * this method should be invoked from the
   * event-dispatching thread.
   */
  private void createAndInitialize() {
    //Create and set up the window.
    mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // make the menu bar

    JMenuBar menuBar = new JMenuBar();
    JMenu settingsMenu = new JMenu( "Settings" );
    settingsMenu.setMnemonic(KeyEvent.VK_S);
    menuBar.add( settingsMenu );
    fontMenuItem = new JMenuItem( "Font..." );
    fontMenuItem.addActionListener( this );
    settingsMenu.add( fontMenuItem );
    lineWrapMenuItem = new JCheckBoxMenuItem( "Line Wrap" );
    lineWrapMenuItem.addActionListener( this );

    mainFrame.setJMenuBar( menuBar );
    
    Container contentPane = mainFrame.getContentPane();
    // buttons, output, input, from top to bottom
    contentPane.setLayout(new BorderLayout());

    worldViews = new JTabbedPane();
    worldViews.addTab( INFO_LABEL, new WorldView() );

    contentPane.add( worldViews, BorderLayout.CENTER );
    contentPane.add( inputField, BorderLayout.SOUTH );
    //Display the window.
    mainFrame.pack();

    // hack: since my system does not implement Font.MONOSPACED correctly
    setFont( new Font( "DejaVu Sans Mono", Font.PLAIN, 12 ) );

    setForegroundGlobally( fgColor );
    setBackgroundGlobally( bgColor );

    // we want default focus to go to the inputField
    mainFrame.addWindowFocusListener( new WindowAdapter() {
        public void windowGainedFocus(WindowEvent e) {
          inputField.requestFocusInWindow();
        }
    });
    inputField.requestFocusInWindow();
  }
  public void show(){
    mainFrame.setVisible(true);
  }
  public String addConnection( String name ){
    int i = worldViews.indexOfTab( name );
    if( i != -1 ){
      int suffix = 2;
      while( worldViews.indexOfTab( name + "<" + suffix + ">" ) != -1 ){
        suffix++;
      }
      name = name + "<" + suffix + ">";
    }
    WorldView wv = new WorldView();

    wv.setFont( font );
    wv.setForeground( fgColor );
    wv.setBackground( bgColor );
    worldViews.addTab( name, wv );
    switchToConnection( name );
    return name;
  }
  public void switchToConnection( String name ){
    worldViews.setSelectedIndex( worldViews.indexOfTab( name ) );
  }
  public String getCurrentConnection(){
    return worldViews.getTitleAt( worldViews.getSelectedIndex() );
  }
  public void display( String str ){
    Segment s = new Segment();
    s.array = str.toCharArray();
    s.count = s.array.length;
    s.offset = 0;
    display( s );
  }
  public void display( Segment s ){
    String current = getCurrentConnection();
    display( INFO_LABEL, s );
    if( infoOnCurrent && ! INFO_LABEL.equals( current ) )
      display( current, s );
  }
  public void display( String connection, String str ){
    Segment s = new Segment();
    s.array = str.toCharArray();
    s.count = s.array.length;
    s.offset = 0;
    display( connection, s );
  }
  // workhorse method.  all other display(...) methods call this one.
  public void display( String connection, Segment s ){
    WorldView awv = getWorldView( connection );
    if( awv != null )
      getWorldView( connection ).display( s );
  }
  public Color getForeground(){
    return getActiveWorldView().getForeground();
  }
  public Color getForeground( String tabLabel ){
    return getWorldView( tabLabel ).getForeground();
  }
  public void setForegroundGlobally( Color c ){
    WorldView[] wvs = getAllWorldViews();
    for( int i = wvs.length-1; i >= 0; i-- ){
      wvs[i].setForeground( c );
    }
    inputField.setForeground( c );
  }
  public void setForeground( Color c ){
    getActiveWorldView().setForeground( c );
    inputField.setForeground( c );
  }
  public Color getBackground(){
    return getActiveWorldView().getBackground();
  }
  public Color getBackground( String tabLabel ){
    return getWorldView( tabLabel ).getBackground();
  }
  public void setBackgroundGlobally( Color c ){
    WorldView[] wvs = getAllWorldViews();
    for( int i = wvs.length-1; i >= 0; i-- ){
      wvs[i].setBackground( c );
    }
    inputField.setBackground( c );
  }
  public void setBackground( Color c ){
    getActiveWorldView().setBackground( c );
    inputField.setBackground( c );
  }
  public Font getFont(){
    return font;
  }
  public void setFont( Font f ){
    font = f;
    WorldView[] wvs = getAllWorldViews();
    for( int i = wvs.length-1; i >= 0; i-- ){
      wvs[i].setFont( f );
    }
    inputField.setFont( f );
  }

  private String[] getAllTabNames(){
    String[] retVal = new String[ worldViews.getTabCount() ];
    for( int i = retVal.length-1; i >= 0; i-- ){
      retVal[ i ] = worldViews.getTitleAt( i );
    }
    return retVal;

  }
  private WorldView[] getAllWorldViews(){
    WorldView[] retVal = new WorldView[ worldViews.getTabCount() ];
    for( int i = retVal.length-1; i >= 0; i-- ){
      retVal[ i ] = (WorldView)worldViews.getComponentAt( i );
    }
    return retVal;
  }
  // gets the active (currently selected in tabPane) worldView
  private WorldView getActiveWorldView(){
    return (WorldView) worldViews.getSelectedComponent();
  }
  private WorldView getWorldView( String tabLabel ){
    return (WorldView) worldViews.getComponentAt(
                         worldViews.indexOfTab( tabLabel ) );
  }
  public void actionPerformed( ActionEvent ae ){
    if( ae.getSource() == inputField ){
      String input = inputField.getText();
      inputField.setText("");

      WorldView awv = getActiveWorldView();
      if( echoInputToOutput ){
        awv.getOutputView().getTextLineBuffer().append( input.toCharArray() );
        awv.getOutputView().getTextLineBuffer().append( "\n".toCharArray() );
      }
      awv.logInput( input );
      awv.logInput( "\n" );
      MudClient.handleInput( getCurrentConnection(), input );

    } else if( ae.getSource() == fontMenuItem ){
      if( fontSelector == null ) fontSelector = new FontSelector( this );
      fontSelector.setVisible( true );
    }
  }
  /*
  public static void main(String[] args) {
    // to do: take some command line arguments and do some customization
    MudClientGui gui = new MudClientGui();
  }*/
}

