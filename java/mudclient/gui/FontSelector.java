package mudclient.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

public class FontSelector extends JFrame 
                          implements ActionListener, ChangeListener, ItemListener{
  private MudClientGui myParent;
  private JComponent testPanel;
  private JButton okButton, applyButton, cancelButton;
  private JComboBox fonts, styles;
  private JSpinner sizes;
  private String currentFont;
  private String[] styleNames = { "Plain", "Bold", "Italic", "Bold Italic" };
  private int currentStyle;
  private int currentSize;

  public FontSelector( MudClientGui parent ){
    myParent = parent;
    Font f = myParent.getFont();
    currentFont = f.getName();
    currentStyle = f.getStyle();
    currentSize = f.getSize();

    Container contentPane = getContentPane();
    JPanel selectionsPanel = new JPanel();
    
    selectionsPanel.setLayout(new BoxLayout(selectionsPanel, BoxLayout.X_AXIS));
    selectionsPanel.add( new JLabel( "Font:" ) );
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    fonts = new JComboBox( ge.getAvailableFontFamilyNames() );
    fonts.setSelectedItem( currentFont );
    fonts.setMaximumRowCount( 10 );
    fonts.addItemListener( this );
    selectionsPanel.add( fonts );

    selectionsPanel.add( new JLabel( "Style:" ) );
    styles = new JComboBox( styleNames );
    styles.setSelectedItem( styleNames[ currentStyle ] );
    styles.addItemListener( this );
    selectionsPanel.add( styles );

    selectionsPanel.add( new JLabel( "Size:" ) );
    sizes = new JSpinner( new SpinnerNumberModel( currentSize, 6, 60, 1 ) );
    sizes.addChangeListener( this );
    selectionsPanel.add( sizes );

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    okButton = new JButton( "OK" );
    applyButton = new JButton( "Apply" );
    cancelButton = new JButton( "Cancel" );
    buttonsPanel.add( okButton );
    buttonsPanel.add( applyButton );
    buttonsPanel.add( cancelButton );
    okButton.addActionListener( this );
    applyButton.addActionListener( this );
    cancelButton.addActionListener( this );

    testPanel = new TextTestPanel();
    testPanel.setFont(new Font( currentFont, currentStyle, currentSize));
    testPanel.setBackground(Color.white);
    testPanel.setForeground(Color.black);
    testPanel.setPreferredSize( new Dimension( 500, 100 ) );

    contentPane.setLayout( new BorderLayout() );
    contentPane.add(BorderLayout.NORTH, selectionsPanel);
    contentPane.add(BorderLayout.CENTER, buttonsPanel);
    contentPane.add(BorderLayout.SOUTH, testPanel);
    pack();
  }
  public void itemStateChanged(ItemEvent e) {
    if (e.getStateChange() != ItemEvent.SELECTED) return;
    if (e.getSource() == fonts)
      currentFont = (String)fonts.getSelectedItem();
    else
      currentStyle = styles.getSelectedIndex();
    testPanel.setFont( new Font( currentFont, currentStyle, currentSize ) );
    testPanel.repaint();
  }
  public void stateChanged( ChangeEvent e ){
    try{
      currentSize = Integer.parseInt( sizes.getModel().getValue().toString() );
      testPanel.setFont( new Font( currentFont, currentStyle, currentSize ) );
      testPanel.repaint();
    } catch ( NumberFormatException ee ) {}
  }
  public void actionPerformed( ActionEvent e ){
    Object source = e.getSource();
    if( source == okButton ){
      myParent.setFont( new Font( currentFont, currentStyle, currentSize ) );
      setVisible( false );
    } else if( source == applyButton ){
      myParent.setFont( new Font( currentFont, currentStyle, currentSize ) );
    } else if( source == cancelButton ){
      Font f = myParent.getFont();
      currentFont = f.getName();
      currentStyle = f.getStyle();
      currentSize = f.getSize();
      fonts.setSelectedItem( currentFont );
      styles.setSelectedItem( styleNames[ currentStyle ] );
      sizes.setValue( new Integer( currentSize ) );
      setVisible( false );
    }
  }
  private class TextTestPanel extends JComponent{
    public void paintComponent( Graphics g ){
      super.paintComponent( g );
      g.setFont( getFont() );
      g.drawString( "ABCD abce 1234 www iii", 
                    3,
                    (int) g.getFontMetrics().getHeight() );
    }
  }
}
