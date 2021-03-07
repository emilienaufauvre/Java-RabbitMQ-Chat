package superchat;

import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;
import superchat.data.Message;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;


/**
 * Handle connections/disconnections,
 * and load/save messages history on start/shut off.
 */
public class Server
{
    public static void main(String[] args)
    {
        new Server(parseArgs(args));
    }

    public static String parseArgs(String[] args)
    {
        if (args.length < 1)
        {
            return "localhost";
        }

        return args[0];
    }


    // To get connection/disconnection requests from clients
    // (used in RPC i.e. server checking when connecting).
    private final static String QUEUE_CONNECTIONS =
            "rabbitmq://server/queue/connections_disconnections/";
    // Output exchange (redirect/show messages of clients).
    private final static String EXCHANGE_MESSAGES =
            "rabbitmq://server/exchange/messages/";
    // Output exchange (redirect/show connections/disconnections to clients).
    private final static String EXCHANGE_CONNECTIONS =
            "rabbitmq://server/exchange/connections_disconnections/";

    // Message backup path constants.
    private final String HOME_DIR_PATH = System.getProperty("user.home")
            + File.separator + ".superchat";
    private final String HISTORY_FILE_PATH = HOME_DIR_PATH + File.separator
            + "history2";

    // To communicate.
    private Connection mConnection;
    private Channel mChannel;
    private final Object mMonitor;
    // Current connected user pseudos.
    private final ArrayList<String> mUserNames;
    // All the messages.
    private ArrayList<Message> mMessages;


    public Server(String host)
    {
        mMonitor = new Object();
        mUserNames = new ArrayList<>();
        mMessages = new ArrayList<>();

        initCommunication(host);
        // Create/check existence of message history file.
        createHomeDir();
        createHistoryFile();
        // And retrieve this history (if needed).
        retrieveMessageHistory();
        // Save the messages when exiting.
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                {
                    saveMessageHistory();
                    closeRabbitMQ();
                }
            )
        );

        System.out.println("Server ready...");

        waitForConnections();
    }

    private void initCommunication(String host)
    {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        try
        {
            mConnection = factory.newConnection();
            mChannel = mConnection.createChannel();
            initInputOutput();
        }
        catch (IOException | TimeoutException e)
        {
            System.err.println("Error: " + e);
            System.exit(-1);
        }
    }

    private void initInputOutput() throws IOException
    {
        mChannel.exchangeDeclare(EXCHANGE_MESSAGES, "fanout");
        mChannel.exchangeDeclare(EXCHANGE_CONNECTIONS, "fanout");

        mChannel.queueDeclare(QUEUE_CONNECTIONS,
                false, false, false, null);
        String queueName = mChannel.queueDeclare().getQueue();
        mChannel.queueBind(queueName, EXCHANGE_MESSAGES, "");

        mChannel.basicConsume(QUEUE_CONNECTIONS, false,
                this::onConnection,
                consumerTag -> { });
        mChannel.basicConsume(queueName, true,
                this::onMessage,
                consumerTag -> { });

        // RPC connection queue:
        // - Remove all message in the queue which are not waiting for acknowledgement.
        //mChannel.queuePurge(QUEUE_CONNECTIONS);
        // - 1 unacknowledged message at once.
        //mChannel.basicQos(1);
    }

    /**
     * Handle the connection or disconnection (contained in "delivery")
     * of a client. Return true if correctly done.
     */
    private void onConnection(String consumerTag, Delivery delivery) throws IOException
    {
        // Get the data.
        superchat.data.Connection connection = SerializationUtils
                .deserialize(delivery.getBody());
        // Parse the data.
        if (connection.isIsConnecting())
        {
            boolean response = false;

            if (! mUserNames.contains(connection.getName()))
            {
                System.out.println("Connection success: " + connection.getName());
                // Can connect with this pseudo, and add this client to the chat.
                mUserNames.add(connection.getName());
                // Spread the connection to the other clients.
                mChannel.basicPublish(EXCHANGE_CONNECTIONS, "", null,
                        delivery.getBody());
                response = true;
            }
            else
            {
                System.out.println("Connection fail: " + connection.getName());
            }
            // Publish the response.
            AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                    .Builder()
                    .correlationId(delivery.getProperties().getCorrelationId())
                    .build();
            mChannel.basicPublish("", delivery.getProperties().getReplyTo(),
                    replyProps, SerializationUtils.serialize(response));
        }
        else
        {
            // Disconnecting.
            mUserNames.remove(connection.getName());
            System.out.println("Disconnection: " + connection.getName());
            // Spread the disconnection to the other clients.
            mChannel.basicPublish(EXCHANGE_CONNECTIONS, "", null,
                    delivery.getBody());
        }
        // Acknowledgment.
        mChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        // RabbitMq consumer worker thread notifies the RPC server owner thread.
        synchronized (mMonitor)
        {
            mMonitor.notify();
        }
    }

    /**
     * Retrieve the message in "delivery" to save it for the history.
     */
    private void onMessage(String consumerTag, Delivery delivery)
    {
        // Get the data.
        Message message = SerializationUtils.deserialize(delivery.getBody());
        mMessages.add(message);
        System.out.println("Message event: " + message.getName() + "> "
                + message.getContent());
    }

    /**
     * Wait and be prepared to consume the next connection request from RPC client.
     */
    @SuppressWarnings("InfiniteLoopStatement")
    private void waitForConnections()
    {
        while (true)
        {
            synchronized (mMonitor)
            {
                try
                {
                    mMonitor.wait();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public void retrieveMessageHistory()
    {
        try
        {
            // Open a stream to the file.
            ObjectInputStream stream = new ObjectInputStream(
                    new FileInputStream(HISTORY_FILE_PATH));
            // Then read the messages.
            @SuppressWarnings("unchecked")
            ArrayList<Message> messages = (ArrayList<Message>) stream.readObject();

            if (messages != null)
            {
                mMessages = messages;
            }

            stream.close();
        }
        catch (EOFException e)
        {
            // If no EOFException then the history file is just empty => normal behavior.
        }
        catch (Exception e)
        {
            // If no EOFException then the history file is empty.
            System.err.println("Error: cannot retrieve messages in the history file.");
        }
    }

    public void saveMessageHistory()
    {
        try
        {
            // Open a stream to the file.
            ObjectOutputStream stream = new ObjectOutputStream(
                    new FileOutputStream(HISTORY_FILE_PATH));
            // Then write the messages.
            stream.writeObject(mMessages);

            stream.close();
        }
        catch (Exception e)
        {
            System.err.println("Error: cannot save messages in the history file.");
        }
    }

    private void createHomeDir()
    {
        File homeDir = new File(HOME_DIR_PATH);

        try
        {
            if (! homeDir.exists() && ! homeDir.mkdirs())
            {
                System.err.println("Error: cannot create the Superchat home directory " +
                        HOME_DIR_PATH + ".");
                System.exit(-1);
            }
        }
        catch (Exception e)
        {
            System.err.println("Error: cannot create the Superchat home directory " +
                    HOME_DIR_PATH + ".");
            System.exit(-1);
        }
    }

    private void createHistoryFile()
    {
        File file = new File(HISTORY_FILE_PATH);

        try
        {
            if (! file.exists() && ! file.createNewFile())
            {
                System.err.println("Error: cannot create the history file " +
                        HISTORY_FILE_PATH + ".");
                System.exit(-1);
            }
        }
        catch (Exception e)
        {
            System.err.println("Error: cannot create the history file " +
                    HISTORY_FILE_PATH + ".");
            System.exit(-1);
        }
    }

    private void closeRabbitMQ()
    {
        try
        {
            mConnection.close();
        }
        catch (IOException e)
        {
            System.err.println("Error: when closing rabbitMQ connection" + e);
        }
    }
}