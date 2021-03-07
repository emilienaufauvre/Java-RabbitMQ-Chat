package superchat.data;

import java.io.Serial;
import java.io.Serializable;


/**
 * Contain a connection or disconnection request.
 */
public class Connection implements Serializable
{
    @Serial
    private static final long serialVersionUID = -6893634578516949025L;

    // True if its a connection request, otherwise it's a
    // disconnection one.
    private final boolean mIsConnecting;
    // User name.
    private final String mName;

    public Connection(boolean isConnecting, String name)
    {
        mIsConnecting = isConnecting;
        mName = name;
    }

    public boolean isIsConnecting()
    {
        return mIsConnecting;
    }

    public String getName()
    {
        return mName;
    }
}