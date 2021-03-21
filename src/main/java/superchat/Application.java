package superchat;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;


/**
 * The GUI bound with a "Client".
 */
public class Application
{
    public static void main(String[] args)
    {
        // Create the client.
        Client client = new Client(parseArgs(args));
        // Start the app with this client.
        SwingUtilities.invokeLater(() -> new Application(client));
        // Handle the ctrl-C exits (alt-F4 done with Swing).
        Runtime.getRuntime().addShutdownHook(new Thread(
                () ->
                {
                    if (client.isConnected())
                    {
                        client.disconnect();
                    }

                    client.closeRabbitMQ();
                }
            )
        );
    }

    public static String parseArgs(String[] args)
    {
        if (args.length < 1) 
        {
            return "localhost";
        }

        return args[0];
    }


    // Constants.
    private final String FONT = "";

    public final static SimpleAttributeSet ATTR_PLAIN 
        = new SimpleAttributeSet(); 
    public final static SimpleAttributeSet ATTR_BOLD 
        = new SimpleAttributeSet(); 
    public final static SimpleAttributeSet ATTR_ITALIC 
        = new SimpleAttributeSet(); 
    public final static SimpleAttributeSet ATTR_ERROR 
        = new SimpleAttributeSet(); 
    public final static SimpleAttributeSet ATTR_SERVER 
        = new SimpleAttributeSet(); 


    // To manage the client session and messages. 
    private final Client mClient;
    // App icon.
    private BufferedImage mIcon;
    // The GUI window.
    private JFrame mFrame;
    // The chat messages.
    private JTextPane mChatArea;
    // The connected user names.
    private DefaultListModel<String> mUserList;

    public Application(Client client)
    {
        // Load text styles for the chat.
        loadTextStyles();
        // Load the app icon.
        loadAssets();
        // Set pop up dialogs style.
        setDialogs();
        // Load the window.
        createFrame();
        // Load the client.
        mClient = client;
        mClient.bindWithGUI(this);
    }

    private void loadTextStyles()
    {
        // Bold.
        StyleConstants.setBold(ATTR_BOLD, true);
        StyleConstants.setFontSize(ATTR_BOLD, (int) convertFontSizeForWindows(25D));
        // Italic.
        StyleConstants.setItalic(ATTR_ITALIC, true);
        StyleConstants.setFontSize(ATTR_ITALIC, (int) convertFontSizeForWindows(25D));
        // Error.
        StyleConstants.setBold(ATTR_ERROR, true); 
        StyleConstants.setForeground(ATTR_ERROR, Color.red);
        StyleConstants.setFontSize(ATTR_ERROR, (int) convertFontSizeForWindows(25D));
        // Server.
        StyleConstants.setBold(ATTR_SERVER, true); 
        StyleConstants.setFontSize(ATTR_SERVER, (int) convertFontSizeForWindows(25D));
    }

    private void loadAssets()
    {
        try
        {
            // Read from jar.
            mIcon = ImageIO.read(getClass().getResource("/launcher.png"));
        }
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }

    private void setDialogs()
    {
        UIManager.put("OptionPane.messageFont", new Font(FONT, Font.BOLD, (int) convertFontSizeForWindows(30D)));
        UIManager.put("OptionPane.buttonFont", new Font(FONT, Font.PLAIN, (int) convertFontSizeForWindows(25D)));
        UIManager.put("TextField.font", new Font(FONT, Font.PLAIN, (int) convertFontSizeForWindows(25D)));
    }

    private void createFrame()
    {
        mFrame = new JFrame("Chat console");

        Container container = mFrame.getContentPane();
        container.setLayout(new BorderLayout());
        container.add(getRightPanel(), BorderLayout.CENTER);
        container.add(getLeftPanel(), BorderLayout.WEST);

        // On exit.
        mFrame.addWindowListener(new java.awt.event.WindowAdapter() 
                {
                    // Can't use lambda.
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) 
                    {
                        if (mClient.isConnected())
                        {
                            mClient.disconnect();
                        }

                        System.exit(0);
                    }
                }
        );
        // App icon.
        mFrame.setIconImage(mIcon);
        // Show on top.
        mFrame.setAlwaysOnTop(true);
        // Auto-exit when closing app.
        mFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Center app.
        mFrame.setLocationRelativeTo(null);
        // Fullscreen.
        mFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        // Default size.
        mFrame.pack();
        // Show it.
        mFrame.setVisible(true);
    }

    private JPanel getLeftPanel()
    {
        // Icon.
        GridBagConstraints constraints1 = new GridBagConstraints();
        constraints1.weightx = 0;
        constraints1.weighty = 0;
        constraints1.gridx = 0;
        constraints1.gridy = 0;
        // User names.
        GridBagConstraints constraints2 = new GridBagConstraints();
        constraints2.weightx = 1;
        constraints2.weighty = 1;
        constraints2.gridx = 0;
        constraints2.gridy = 1;
        constraints2.fill = GridBagConstraints.BOTH;
        // Navigation buttons. 
        GridBagConstraints constraints3 = new GridBagConstraints();
        constraints3.weightx = 1;
        constraints3.weighty = 0;
        constraints3.gridx = 0;
        constraints3.gridy = 2;
        constraints3.fill = GridBagConstraints.HORIZONTAL;
        constraints3.anchor = GridBagConstraints.PAGE_END;

        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.add(getIconPanel(), constraints1); 
        leftPanel.add(getUsersPanel(), constraints2); 
        leftPanel.add(getCommandsPanel(), constraints3); 

        return leftPanel;
    }

    private JPanel getRightPanel()
    {
        // Chat content panel.
        GridBagConstraints constraints1 = new GridBagConstraints();
        constraints1.weightx = 1;
        constraints1.weighty = 1;
        constraints1.gridx = 0;
        constraints1.gridy = 0;
        constraints1.fill = GridBagConstraints.BOTH;
        // Input user message panel.
        GridBagConstraints constraints2 = new GridBagConstraints();
        constraints2.weightx = 1;
        constraints2.weighty = 0;
        constraints2.gridx = 0;
        constraints2.gridy = 1;
        constraints2.fill = GridBagConstraints.HORIZONTAL;
        constraints2.anchor = GridBagConstraints.PAGE_END;

        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.add(getChatPanel(), constraints1); 
        rightPanel.add(getInputPanel(), constraints2);

        return rightPanel;
    }

    /**
     * Return the panel in which the user messages are displayed.
     */
    private JPanel getChatPanel()
    {
        // Message list.
        mChatArea = new JTextPane();
        mChatArea.setMargin(new Insets(20, 20, 20, 20));
        mChatArea.setFont(new Font(FONT, Font.PLAIN, (int) convertFontSizeForWindows(25D)));
        mChatArea.setEditable(false);
        addToChat("Welcome on super-chat v2.\n" +
                "You can log in using the button at the bottom left.\n\n",
                ATTR_ITALIC); 

        JScrollPane scrollPane = new JScrollPane(mChatArea);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(40, 20, 40, 40));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Return the panel in which the user write her/his message.
     */
    private JPanel getInputPanel()
    {
        // User input.
        JTextField textField = new JTextField();
        textField.setMargin(new Insets(20, 20, 20, 20));
        textField.setFont(new Font(FONT, Font.PLAIN, (int) convertFontSizeForWindows(25D)));
        textField.addActionListener(onSendInput(textField));
        // Send button.
        JButton button = new JButton("SEND");
        button.setFont(new Font(FONT, Font.BOLD, (int) convertFontSizeForWindows(25D)));
        button.addActionListener(onSendInput(textField));

        // User input
        GridBagConstraints constraints1 = new GridBagConstraints();
        constraints1.weightx = 0.9;
        constraints1.weighty = 0;
        constraints1.gridx = 0;
        constraints1.gridy = 0;
        constraints1.fill = GridBagConstraints.HORIZONTAL;
        // Send button. 
        GridBagConstraints constraints2 = new GridBagConstraints();
        constraints2.weightx = 0.1;
        constraints2.weighty = 0;
        constraints2.gridx = 1;
        constraints2.gridy = 0;
        constraints2.fill = GridBagConstraints.HORIZONTAL;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(40, 20, 40, 40));
        panel.add(textField, constraints1);
        panel.add(button, constraints2);

        return panel;
    }

    private ActionListener onSendInput(JTextField textField)
    {
        return e ->  
        {
            if (! mClient.isConnected())
            {
                addToChat("[Server]: Please log in to " +
                       "send messages.", ATTR_SERVER);  
                textField.setText("");
                return ;
            }

            String input = textField.getText();

            if (input != null && ! input.isEmpty())
            {
                if (! mClient.isConnected())
                {
                    mClient.connect(input);
                }
                else
                {
                    mClient.sendMessage(input);
                }

                textField.setText("");
            }
        };
    }

    /**
     * Return the panel which contains the app icon and title. 
     */
    private JPanel getIconPanel()
    {
        // App title.
        String APP_NAME = "Super-chat v2";
        JLabel label1 = new JLabel(APP_NAME, SwingConstants.CENTER);
        label1.setFont(new Font(FONT, Font.BOLD, (int) convertFontSizeForWindows(60D)));
        label1.setForeground(new Color(0x2484c2));
        JPanel panel_ = new JPanel(); // To force margins...
        panel_.setBorder(new EmptyBorder(40, 40, 80, 40));
        panel_.add(label1);
        // App icon
        JLabel label2 = new JLabel(convertIconWindows());

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(40, 40, 40, 20));
        panel.add(panel_, BorderLayout.NORTH);
        panel.add(label2, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Return the panel in which the connected user names are displayed. 
     */
    private JPanel getUsersPanel()
    {
        // Title.
        JLabel label = new JLabel("Current Users", JLabel.CENTER);
        label.setFont(new Font(FONT, Font.BOLD, (int) convertFontSizeForWindows(30D)));
        JPanel panel_ = new JPanel(); // To force margins...
        panel_.setBorder(new EmptyBorder(0, 0, 20, 0));
        panel_.add(label);
        // User names.
        mUserList = new DefaultListModel<>();
        JList<String> list = new JList<>(mUserList);
        list.setBorder(new EmptyBorder(40, 40, 40, 20));
        list.setFont(new Font(FONT, Font.PLAIN, (int) convertFontSizeForWindows(25D)));
        list.setVisibleRowCount(8);

        JScrollPane scrollPane = new JScrollPane(list);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(40, 40, 20, 20));
        panel.add(panel_, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Return the panel in which the command buttons (connect, disconnect, ect)
     * are available. 
     */
    private JPanel getCommandsPanel()
    {
        JButton button1 = new JButton("CONNECT");
        JButton button2 = new JButton("DISCONNECT");

        // Connect button.
        button1.setFont(new Font(FONT, Font.BOLD, (int) convertFontSizeForWindows(25D)));
        button1.addActionListener(onConnection(button1, button2));
        button1.setEnabled(true);
        // Disconnect button.
        button2.setFont(new Font(FONT, Font.BOLD, (int) convertFontSizeForWindows(25D)));
        button2.addActionListener(onDisconnection(button1, button2));
        button2.setEnabled(false);
        // Connect button.
        GridBagConstraints constraints1 = new GridBagConstraints();
        constraints1.weightx = 0.5;
        constraints1.weighty = 0;
        constraints1.gridx = 0;
        constraints1.gridy = 0;
        constraints1.fill = GridBagConstraints.HORIZONTAL;
        // Disconnect button.
        GridBagConstraints constraints2 = new GridBagConstraints();
        constraints2.weightx = 0.5;
        constraints2.weighty = 0;
        constraints2.gridx = 1;
        constraints2.gridy = 0;
        constraints2.fill = GridBagConstraints.HORIZONTAL;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 40, 40, 20));
        panel.add(button1, constraints1); 
        panel.add(button2, constraints2); 

        return panel;
    }

    private ActionListener onConnection(JButton connectButton, 
            JButton disconnectButton)
    {
        return e -> 
        {
            // Ask for user pseudo.
            // Build a pop up dialog:
            String name = JOptionPane.showInputDialog(mFrame, 
                    "Please enter your name:", "Connect",
                    JOptionPane.PLAIN_MESSAGE);

            // Try to connect on server.
            if (name != null)
            {
                if (! name.isEmpty())
                {
                    if (mClient.connect(name))
                    {
                        connectButton.setEnabled(false);
                        disconnectButton.setEnabled(true);
                    }
                }
                else
                {
                    addToChat("[Server]: Error on connection, " +
                            "your name was empty.", ATTR_ERROR); 
                }
            }
        };
    }

    private ActionListener onDisconnection(JButton connectButton, 
            JButton disconnectButton)
    {
        return e -> 
        {
            // Ask for confirmation.
            // Build a pop up dialog:
            int input = JOptionPane.showConfirmDialog(mFrame,
                    "Click ok if you want to log out.", "Disconnect", 
                    JOptionPane.DEFAULT_OPTION);

            if (input == 0)
            {
                // Disconnect on server.
                mClient.disconnect();
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
            }
        };
    }

    public void addToChat(String message, SimpleAttributeSet attributes) 
    {
        Document doc = mChatArea.getDocument();

        if (attributes == ATTR_ERROR || attributes == ATTR_SERVER)
        {
            message = "\n" + message + "\n\n";
        }
        else if (attributes == ATTR_PLAIN)
        {
            message = message + "\n";
        }

        try
        {
            doc.insertString(doc.getLength(), message, attributes);
        }
        catch (Exception ignored)
        {
        }

        mChatArea.setCaretPosition(doc.getLength());
    }

    public void addToUsersList(String name)
    {
        if (! mUserList.contains(name))
        {
            mUserList.addElement(name);
        }
    }

    public void removeFromUserList(String name)
    {
        mUserList.removeElement(name);
    }

    public void clearUsersList()
    {
        mUserList.clear();
    }

    public double convertFontSizeForWindows(double fontSize)
    {
        // Are we running within a Windows platform?
        if (System.getProperty("os.name").toLowerCase().contains("windows"))
        {
            // Yes, so let's convert the font size to accommodate windows.
            double screenWidth = Toolkit.getDefaultToolkit().getScreenSize().getWidth();
            double testedWidth = 1500D; // Tested windows platform.
            return fontSize / ((testedWidth / screenWidth) * 2D);
        }
        // No, just return the original font size.
        return fontSize;
    }

    public ImageIcon convertIconWindows()
    {
        // Are we running within a Windows platform?
        if (System.getProperty("os.name").toLowerCase().contains("windows"))
        {
            // Yes, scale it the smooth way.
            double screenWidth = Toolkit.getDefaultToolkit().getScreenSize().getWidth();
            double testedWidth = 1500D; // Tested windows platform.
            return new ImageIcon(mIcon.getScaledInstance(
                    (int) (180 * (screenWidth / testedWidth)),
                    (int) (180 * (screenWidth / testedWidth)),
                    java.awt.Image.SCALE_SMOOTH));
        }
        // No, just return the original.
        return new ImageIcon(mIcon);
    }
}