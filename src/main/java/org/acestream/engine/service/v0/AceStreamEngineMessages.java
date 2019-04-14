package org.acestream.engine.service.v0;

public final class AceStreamEngineMessages {
	/**
     * Command to the service to register a client.
     * The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    public static final int MSG_REGISTER_CLIENT = 1;
    
    /**
     * Command to the service to unregister a client.
     * The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 2;
	
    /**
     * Command to the service to start engine.
     */
    public static final int MSG_START = 3;
    
    /**
     * Message from service, that service start unpack additional files if needed.
	 * Unpacking may be need after installation or updating.
     */
    public static final int MSG_ENGINE_UNPACKING = 4;
    
    /**
     * Message from service, that service starting Ace Stream engine and try connect to it.
     */
    public static final int MSG_ENGINE_STARTING = 5;
    
    /**
     * Message from service, that Ace Stream engine started successfully and 
     * or failed to start.
     * The Message's arg1 field contains a value of port, that engine is listens
     * or -1 if engine failed to strt.
	 */
    public static final int MSG_ENGINE_READY = 6;
    
    /**
     * Message from service, that Ace Stream engine stopped.
     */
    public static final int MSG_ENGINE_STOPPED = 7;

    public static final int MSG_PLAYLIST_UPDATED = 8;
    public static final int MSG_EPG_UPDATED = 9;
    public static final int MSG_RESTART_PLAYER = 10;
    public static final int MSG_SETTINGS_UPDATED = 11;
}
