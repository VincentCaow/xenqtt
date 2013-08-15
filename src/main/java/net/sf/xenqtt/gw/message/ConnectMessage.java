package net.sf.xenqtt.gw.message;

import java.nio.ByteBuffer;

import javax.naming.LimitExceededException;

/**
 * The MQTT connect message. When a TCP/IP socket connection is established from a client to a server, a protocol level
 * session must be created using a CONNECT flow.
 */
public final class ConnectMessage extends MqttMessage {

	private final byte flags;
	private final String protocolName;
	private final int protocolVersion;
	private final String clientId;
	private final String userName;
	private final String password;
	private final String willTopic;
	private final String willMessage;
	private final int keepAliveSeconds;

	public ConnectMessage(ByteBuffer buffer, int remainingLength) {
		super(buffer, remainingLength);

		this.protocolName = getString();
		this.protocolVersion = buffer.get() & 0xff;
		this.flags = buffer.get();
		this.keepAliveSeconds = buffer.getShort() & 0xffff;
		this.clientId = getString();

		boolean willFlag = (flags & 0x02) == 0x02;
		this.willTopic = willFlag ? getString() : null;
		this.willMessage = willFlag ? getString() : null;
		this.userName = (flags & 0x80) == 0x80 && buffer.hasRemaining() ? getString() : null;
		this.password = (flags & 0x40) == 0x40 && buffer.hasRemaining() ? getString() : null;
	}

	public ConnectMessage(String clientId, String userName, String password, boolean cleanSession,
			int keepAliveSeconds, String willTopic, String willMessage, boolean willRetain) {
		super(MessageType.CONNECT, false, QoS.AT_MOST_ONCE, false, );

		if (willTopic == null) {
			if (willMessage != null) {
				willMessage = null;
			}
			if (willRetain) {
				willRetain = false;
			}
		} else if (willMessage == null) {
			willMessage = "";
		}
		if (userName == null && password != null) {
			password = null;
		}

		this.protocolName = "MQIsdp";
		this.protocolVersion = 3;
		this.flags = buildFlags(cleanSession, willRetain);
		this.keepAliveSeconds = keepAliveSeconds;
		this.clientId = clientId;
		this.willTopic = willTopic;
		this.willMessage = willMessage;
		this.userName = userName;
		this.password = password;

		putString(protocolName);
		buffer.put((byte) protocolVersion);
		buffer.put(flags);
		buffer.putShort((short) keepAliveSeconds);
		putString(clientId);
		putString(willTopic);
		putString(willMessage);
		putString(userName);
		putString(password);
	}

	/**
	 * String that represents the protocol name MQIsdp, capitalized as shown.
	 */
	public String getProtocolName() {
		return protocolName;
	}

	/**
	 * The revision level of the protocol used by the client. The current version of the protocol is 3
	 */
	public int getProtocolVersion() {
		return protocolVersion;
	}

	/**
	 * If not set, then the server must store the subscriptions of the client after it disconnects. This includes
	 * continuing to store QoS 1 and QoS 2 messages for the subscribed topics so that they can be delivered when the
	 * client reconnects. The server must also maintain the state of in-flight messages being delivered at the point the
	 * connection is lost. This information must be kept until the client reconnects.
	 * <p>
	 * If set, then the server must discard any previously maintained information about the client and treat the
	 * connection as "clean". The server must also discard any state when the client disconnects.
	 * <p>
	 * Typically, a client will operate in one mode or the other and not change. The choice will depend on the
	 * application. A clean session client will not receive stale information and it must resubscribe each time it
	 * connects. A non-clean session client will not miss any QoS 1 or QoS 2 messages that were published whilst it was
	 * disconnected. QoS 0 messages are never stored, since they are delivered on a best efforts basis.
	 * <p>
	 * This flag was formerly known as "Clean start". It has been renamed to clarify the fact it applies to the whole
	 * session and not just to the initial connect.
	 * <p>
	 * A server may provide an administrative mechanism for clearing stored information about a client which can be used
	 * when it is known that a client will never reconnect.
	 */
	public boolean isCleanSession() {
		return (flags & 0x02) == 0x02;
	}

	/**
	 * The Client Identifier (Client ID) is between 1 and 23 characters long, and uniquely identifies the client to the
	 * server. It must be unique across all clients connecting to a single server, and is the key in handling Message
	 * IDs messages with QoS levels 1 and 2. If the Client ID contains more than 23 characters, the server responds to
	 * the CONNECT message with a CONNACK return code 2: Identifier Rejected.
	 */
	public String getClientId() {
		return clientId;
	}

	/**
	 * The Will Message is published to the Will Topic. The QoS level is {@link QoS#AT_LEAST_ONCE}, and the RETAIN
	 * status is defined by {@link #isWillRetain()}.
	 * <p>
	 * Null if there is no Will Message.
	 */
	public String getWillTopic() {
		return willTopic;
	}

	/**
	 * The Will Message defines the content of the message that is published to the Will Topic if the client is
	 * unexpectedly disconnected. This may be a zero-length message.
	 * <p>
	 * Although the Will Message is UTF-8 encoded in the CONNECT message, when it is published to the Will Topic only
	 * the bytes of the message are sent, not the first two length bytes. The message must therefore only consist of
	 * 7-bit ASCII characters.
	 * <p>
	 * Null if there is no Will Message. Zero length string if there is an empty Will Message.
	 */
	public String getWillMessage() {
		return willMessage;
	}

	/**
	 * The retain value of the Will message. False if either retain is false or there is no Will Message.
	 */
	public boolean isWillRetain() {
		return (flags & 0x20) == 0x20;
	}

	/**
	 * The user name identifies the name of the user who is connecting, which can be used for authentication. It is
	 * recommended that user names are kept to 12 characters or fewer, but it is not required.
	 * <p>
	 * Note that, for compatibility with the original MQTT V3 specification, the Remaining Length field from the fixed
	 * header takes precedence over the User Name flag. Server implementations must allow for the possibility that the
	 * User Name flag is set, but the User Name string is missing. This is valid, and connections should be allowed to
	 * continue.
	 * <p>
	 * Null if there is no user name.
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * If the Password flag is set, this is the next UTF-encoded string. The password corresponding to the user who is
	 * connecting, which can be used for authentication. It is recommended that passwords are kept to 12 characters or
	 * fewer, but it is not required.
	 * <p>
	 * Note that, for compatibility with the original MQTT V3 specification, the Remaining Length field from the fixed
	 * header takes precedence over the Password flag. Server implementations must allow for the possibility that the
	 * Password flag is set, but the Password string is missing. This is valid, and connections should be allowed to
	 * continue.
	 * <p>
	 * Null if there is no password. If there is no username there can be no password.
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * The Keep Alive timer, measured in seconds, defines the maximum time interval between messages received from a
	 * client. It enables the server to detect that the network connection to a client has dropped, without having to
	 * wait for the long TCP/IP timeout. The client has a responsibility to send a message within each Keep Alive time
	 * period. In the absence of a data-related message during the time period, the client sends a PINGREQ message,
	 * which the server acknowledges with a PINGRESP message.
	 * <p>
	 * If the server does not receive a message from the client within one and a half times the Keep Alive time period
	 * (the client is allowed "grace" of half a time period), it disconnects the client as if the client had sent a
	 * DISCONNECT message. This action does not impact any of the client's subscriptions. See DISCONNECT for more
	 * details.
	 * <p>
	 * If a client does not receive a PINGRESP message within a Keep Alive time period after sending a PINGREQ, it
	 * should close the TCP/IP socket connection.
	 * <p>
	 * The Keep Alive timer is a 16-bit value that represents the number of seconds for the time period. The actual
	 * value is application-specific, but a typical value is a few minutes. The maximum value is approximately 18 hours.
	 * A value of zero (0) means the client is not disconnected.
	 */
	public int getKeepAliveSeconds() {
		return keepAliveSeconds;
	}

	private byte buildFlags(boolean cleanSession, boolean willRetain) {

		int flags = 0;
		if (userName != null) {
			flags |= 0x80; // bit 7
		}
		if (password != null) {
			flags |= 0x40; // bit 6
		}
		if (willRetain) {
			flags |= 0x20;
		}
		// bit 5 is the Will Retain which is always 0
		if (willTopic != null) {
			flags |= 0x0c; // Will Q0S (bits 4 and 3) set to 01 and Will Flag (bit 2) set to 1
		}
		if (cleanSession) {
			flags |= 0x02;
		}
		// bit 0 is unused

		return (byte) flags;
	}

}
