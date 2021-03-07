package superchat.data;

import java.io.Serial;
import java.io.Serializable;


/**
 * Contain a user message.
 */
public class Message implements Serializable
{
    @Serial
    private static final long serialVersionUID = -4830937675487871354L;

    // User name.
    private final String mName;
    // Message content.
    private final String mContent;
    // Time when sent.
    private final String mTime;

    public Message(String name, String content, String time)
    {
        mName = name;
        mContent = content;
        mTime = time;
    }

    public String getName()
    {
        return mName;
    }

    public String getContent()
    {
        return mContent;
    }

    public String getTime()
    {
        return mTime;
    }
}