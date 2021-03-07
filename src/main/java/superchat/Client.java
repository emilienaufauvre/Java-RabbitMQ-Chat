package superchat;

import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;
import superchat.data.Message;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;


/**
 * Communicate with other clients by sending its messages to the
 * server which redirects them, and update the GUI.
 */
public class Client
{
    // /!\ The same as Server.java; needs to be modified in both
    // files if modified. Private scope + duplicate because of the jar
    // distribution (we don't want to include Server.java in the client.jar).
    private final static String QUEUE_CONNECTIONS =
            "rabbitmq://server/queue/connections_disconnections/";
    private final static String EXCHANGE_MESSAGES =
            "rabbitmq://server/exchange/messages/";
    private final static String EXCHANGE_CONNECTIONS =
            "rabbitmq://server/exchange/connections_disconnections/";

    // To communicate.
    private Connection mConnection;
    private Channel mChannel;
    // Current user state.
    private boolean mIsConnected;
    private String mName;
    // To display messages and connected users.
    private Application mApp;

    public Client(String host)
    {
        initCommunication(host);
    }

    private void initCommunication(String host)
    {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        try
        {
            mConnection = factory.newConnection();
            mChannel = mConnection.createChannel();
            // Get a queue to receive the messages from the server.
            String queueName1 = mChannel.queueDeclare().getQueue();
            mChannel.queueBind(queueName1, EXCHANGE_MESSAGES, "");
            mChannel.basicConsume(queueName1, true,
                    this::onReceiveMessage,
                    consumerTag -> { });
            // Get a queue to receive the connections/disconnections from the server.
            String queueName2 = mChannel.queueDeclare().getQueue();
            mChannel.queueBind(queueName2, EXCHANGE_CONNECTIONS, "");
            mChannel.basicConsume(queueName2, true,
                    this::onReceiveConnection,
                    consumerTag -> { });
        }
        catch (TimeoutException | IOException e)
        {
            System.err.println("Error: " + e);
            System.exit(-1);
        }
    }

    /**
     * Bind this client with the current running GUI, to print the messages
     * and connected users.
     */
    public void bindWithGUI(Application app)
    {
        mApp = app;
    }

    /**
     * Connect the user to the server, and return true if successful.
     */
    public boolean connect(String name)
    {
        mApp.addToChat("[Server]: Initiating your connection...",
                superchat.Application.ATTR_SERVER);

        try
        {
            if (! connectRPC(name))
            {
                mApp.addToChat("[Server]: Error, this pseudo is not available.",
                        superchat.Application.ATTR_ERROR);
                return false;
            }
            else
            {
                // Successfully connected.
                mName = name;
                mIsConnected = true;
            }
        }
        catch (Exception e)
        {
            mApp.addToChat("[Server]: Error with the server, try again or " +
                    "relaunch the app.", superchat.Application.ATTR_ERROR);
            return false;
        }

        // TODOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO TODO
        //retrieveMessages();

        mApp.addToChat("[Server]: You are connected as \"" + mName + "\".",
                superchat.Application.ATTR_SERVER);

        return true;
    }

    /**
     * Try to connect the client on the server, and wait for its response
     * (true if user correctly created, false if not i.e. pseudo already exists).
     */
    private boolean connectRPC(String name) throws IOException, InterruptedException
    {
        // Create the connection request.
        superchat.data.Connection connection =
                new superchat.data.Connection(true, name);

        final String corrId = UUID.randomUUID().toString();

        String replyQueueName = mChannel.queueDeclare().getQueue();
        AMQP.BasicProperties props = new AMQP.BasicProperties
                .Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();

        // Publish the user with the pseudo on the server side.
        mChannel.basicPublish("", QUEUE_CONNECTIONS, props,
                SerializationUtils.serialize(connection));
        // Get the response.
        final BlockingQueue<Boolean> response = new ArrayBlockingQueue<>(1);

        String ctag = mChannel.basicConsume(replyQueueName, true,
                (consumerTag, delivery) ->
                {
                    if (delivery.getProperties().getCorrelationId().equals(corrId))
                    {
                        response.offer(SerializationUtils
                                .deserialize(delivery.getBody()));
                    }
                },
                consumerTag -> { }
        );

        boolean result = response.take();
        mChannel.basicCancel(ctag);

        return result;
    }

    /**
     * Disconnect the user of the server by releasing her/his pseudo.
     */
    public void disconnect()
    {
        mApp.addToChat("[Server]: Initiating your disconnection...",
                superchat.Application.ATTR_SERVER);

        superchat.data.Connection disconnection =
                new superchat.data.Connection(false, mName);
        try
        {
            // Try to unbind the user on the server side.
            mChannel.basicPublish("", QUEUE_CONNECTIONS,
                    null, SerializationUtils.serialize(disconnection));
            mIsConnected = false;
        }
        catch (Exception e)
        {
            mApp.addToChat("[Server]: Error, cannot completely disconnect you. " +
                            "Your username may be unavailable until the server restarts." +
                            "If the application seems to be not running correctly, please " +
                            "restart it.",
                    superchat.Application.ATTR_ERROR);
            return;
        }

        // Remove the connected users.
        mApp.clearUsersList();

        mApp.addToChat("[Server]: Disconnection finished.",
                superchat.Application.ATTR_SERVER);
    }

    /**
     * Send the user message to the clients and the server.
     */
    public void sendMessage(String message)
    {
        // Current time.
        String DATE_FORMAT = "HH:mm:ss";
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        // Encapsulate the message data.
        Message msg = new Message(mName, message, time);

        try
        {
            // Spread the message to the other clients (and server).
            mChannel.basicPublish(EXCHANGE_MESSAGES, "", null,
                    SerializationUtils.serialize(msg));
        }
        catch (IOException e)
        {
            mApp.addToChat("[Server]: Error, cannot distribute this message.",
                    Application.ATTR_ERROR);
        }
    }

    /**
     * Consume the message received in "delivery" by printing it in the chat.
     */
    private void onReceiveMessage(String consumerTag, Delivery delivery)
    {
        Message message = SerializationUtils.deserialize(delivery.getBody());

        mApp.addToChat("(" + message.getTime() + ") ", Application.ATTR_BOLD);
        mApp.addToChat(message.getName() + ": ", Application.ATTR_BOLD);
        mApp.addToChat(message.getContent(), Application.ATTR_PLAIN);
    }

    /**
     * Consume the connection/disconnection received in "delivery" by printing it in the chat.
     */
    private void onReceiveConnection(String consumerTag, Delivery delivery)
    {
        superchat.data.Connection connection =
                SerializationUtils.deserialize(delivery.getBody());

        if (connection.isIsConnecting())
        {
            mApp.addToChat(connection.getName() + " is connected.",
                    Application.ATTR_SERVER);
            mApp.addToUsersList(connection.getName());
        }
        else
        {
            mApp.addToChat(connection.getName() + " is disconnected.",
                    Application.ATTR_SERVER);
            mApp.removeFromUserList(connection.getName());
        }
    }

    public boolean isConnected()
    {
        return mIsConnected;
    }

    public void closeRabbitMQ()
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