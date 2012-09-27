// Copyright 2010-2012 Martin Burkhart (martibur@ethz.ch)
//
// This file is part of SEPIA. SEPIA is free software: you can redistribute 
// it and/or modify it under the terms of the GNU Lesser General Public 
// License as published by the Free Software Foundation, either version 3 
// of the License, or (at your option) any later version.
//
// SEPIA is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.

package connections;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import services.Services;
import services.Utils;

/**
 * Base class for connection managers. All the logic associated with creation
 * and failure of connections is encapsulated in the connection manager (CM).
 * The CM provides an interface for sending and receiving messages to targets
 * identified by an ID. The sockets used to send messages are managed by the CM
 * and hidden from the input/privacy peers. In case a socket is closed by the
 * other side, it is automatically removed from the set of active connections.
 * 
 * Upon a new communication round, temporary connections must be activated (
 * {@link #activateTemporaryConnections()}). That is, all temporary connections 
 * are copied to the pool of active connections and used for subsequent
 * messaging. All connections initiated later are first stored in the
 * temporary connection pool and are only made active when
 * {@link #activateTemporaryConnections()} is called again. This is to prevent newly
 * started input and privacy peers from interfering with an ongoing
 * communication round.
 * 
 * That is, the CM is used as follows:
 * 
 * <ol>
 * <li>Before a new round is started, call {@link #establishConnections()} or {@link #waitForConnections(int, int, int)} to
 * (re-)establish all possible connections.</li>
 * <li>Then call {@link #activateTemporaryConnections()} to activate all temporary
 * connections for the next round.</li>
 * <li>During a round, messages are sent and received using
 * {@link #sendMessage(String, Object)} and {@link #receiveMessage(String)}. If
 * connections go down, they are automatically removed from the active
 * connection pool. In case newly started peers are connecting, they are put in
 * the temporary connection pool and are available for the next round.</li>
 * <li>Go back to step 1.</li>
 * </ol>
 * 
 * @author martibur
 */
public abstract class ConnectionManager {
	protected Logger logger;

	protected String myId;
	protected List<PrivacyPeerAddress> privacyPeerAddresses;
	protected HashMap<String, SSLSocket> activeConnectionPool;
	protected HashMap<String, SSLSocket> temporaryConnectionPool;
	protected boolean useCompression = false;
	protected int minInputPeers = 0;
	protected int minPrivacyPeers = 3;
	protected int activeTimeout = 10000;

	private static final int SHORT_BREAK_MS = 2000;

    /** Mask for least significant byte */
    protected static final int LSB1 = 0xFF;
    /** Mask for 2nd significant byte */
    protected static final int LSB2 = 0xFF00;
    /** Mask for 3rd significant byte */
    protected static final int LSB3 = 0xFF0000;
    /** Mask for 4th significant byte */
    protected static final int LSB4 = 0xFF000000;

    // Variables used for message and bandwidth usage statistics. Note: As these members are static,
    // they account for the total communication of all threads (in the same JVM). 
    private static long totalMessagesReceived, totalMessagesSent, totalBytesReceived, totalUncompressedBytesReceived, totalBytesSent, totalUncompressedBytesSent;
    private static long thisRoundMessagesReceived, thisRoundMessagesSent, thisRoundBytesReceived, thisRoundUncompressedBytesReceived, thisRoundBytesSent, thisRoundUncompressedBytesSent;
    private static long numberOfFinishedRounds;

	/**
	 * SSL sockets created with the default SSL socket factories will not have
	 * any certificates associated to authenticate them. In order to associate
	 * certificates with SSL sockets, we need to use the SSLContext class to
	 * create SSL socket factories.
	 */
	protected SSLContext sslContext;

	/**
	 * Sorted list of privacy peer IDs. Derived from privacyPeerAddresses. 
	 */
	protected List<String> privacyPeerIDs;

	/**
	 * Constructor for the connection manageer.
	 * 
	 * @param myId
	 *            My own ID.
	 * @param privacyPeerAddresses
	 *            All the privacy peers
	 * @param sslContext
	 *            An SSLContext that was initialized for client authentication.
	 *            That is, the certificates from the local keystore were
	 *            associated with it.
	 */
	public ConnectionManager(String myId, List<PrivacyPeerAddress> privacyPeerAddresses, SSLContext sslContext) {
		this.myId = myId;
		this.privacyPeerAddresses = privacyPeerAddresses;
		activeConnectionPool = new HashMap<String, SSLSocket>();
		temporaryConnectionPool = new HashMap<String, SSLSocket>();
		this.sslContext = sslContext;

		// Create a local list of all the privacy peer IDs for easy matching.
		privacyPeerIDs = new ArrayList<String>();
		for (PrivacyPeerAddress ppa : privacyPeerAddresses) {
			privacyPeerIDs.add(ppa.id);
		}
		Collections.sort(privacyPeerIDs);
		logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	}

    /**
     * Resets the current round statistics.
     */
    public static void newStatisticsRound() {
    	thisRoundMessagesReceived=0; 
    	thisRoundMessagesSent=0;
    	thisRoundBytesReceived=0;
    	thisRoundUncompressedBytesReceived=0;
    	thisRoundBytesSent=0;
    	thisRoundUncompressedBytesSent=0;
    	numberOfFinishedRounds++;
    }

    /**
     * Returns a String with the connection statistics (messages, bytes).
     * @return the statistics
     */
    public static String getStatistics() {
    	return "ConnectionManager statistics: \n" +
    		   "--- Total      : MR="+totalMessagesReceived+", MS="+totalMessagesSent+", BR="+totalBytesReceived+", UBR="+totalUncompressedBytesReceived+", BS="+totalBytesSent+", UBS="+totalUncompressedBytesSent+"\n" +
    		   "--- This round : MR="+thisRoundMessagesReceived+", MS="+thisRoundMessagesSent+", BR="+thisRoundBytesReceived+", UBR="+thisRoundUncompressedBytesReceived+", BS="+thisRoundBytesSent+", UBS="+thisRoundUncompressedBytesSent+"\n"+ 
    		   "--- Avg. round : MR="+totalMessagesReceived/(numberOfFinishedRounds+1)+", MS="+totalMessagesSent/(numberOfFinishedRounds+1)+", BR="+totalBytesReceived/(numberOfFinishedRounds+1)+", UBR="+totalUncompressedBytesReceived/(numberOfFinishedRounds+1)+", BS="+totalBytesSent/(numberOfFinishedRounds+1)+", UBS="+totalUncompressedBytesSent/(numberOfFinishedRounds+1)+"\n";
    }

	
    /**
     * Receives messages over the socket connection
     *
     * @return The message that was received
     * @throws ClassNotFoundException 
     */
    public Object receiveMessage(Socket socket) throws IOException, ClassNotFoundException {
    	InputStream inStream = socket.getInputStream();

    	// read message length
        int expectedNumberOfBytes = read32(inStream);

        // read message
        byte[] messageBufferTempResult = new byte[expectedNumberOfBytes];
        int beginIndex = 0;
        int lengthToRead = expectedNumberOfBytes;
        int totalBytes = 0;
        int bytesRead = 0;
        while (totalBytes < expectedNumberOfBytes) {
            bytesRead = inStream.read(messageBufferTempResult, beginIndex, lengthToRead);

            if (bytesRead == -1) { // EOF
                break;
            } else {
                totalBytes += bytesRead;
                beginIndex = totalBytes;
                lengthToRead = expectedNumberOfBytes - beginIndex;
            }
        }

        // decompress and deserialize message
        ByteArrayInputStream byteInStream = new ByteArrayInputStream(messageBufferTempResult);
        ObjectInputStream objInStream = null;
        Inflater inflater = null;
        if(useCompression) {
            inflater = new Inflater();
            InflaterInputStream infInStream = new InflaterInputStream(byteInStream, inflater);
            objInStream = new ObjectInputStream(infInStream);
        } else {
        	objInStream = new ObjectInputStream(byteInStream);
        }
        Object message = objInStream.readObject();

        // Update connection statistics
        thisRoundMessagesReceived++;
        totalMessagesReceived++;
        thisRoundBytesReceived+=totalBytes;
        totalBytesReceived+=totalBytes;
        if(useCompression) {
            thisRoundUncompressedBytesReceived+=inflater.getTotalOut();
            totalUncompressedBytesReceived+=inflater.getTotalOut();
        }

        return message;
    }

	
	 /**
     * Send a message over the socket
     *
     * @param message the message to be sent
     */
    public void sendMessage(Socket socket, Object object) throws IOException {
    	/*
    	 * A direct composition of the ObjectOutpuputStream, DeflaterOutputStream, and socket.getOutputStream()
    	 * lead to occasional "broken pipe" and "unknown compression algorithm" exceptions.
    	 * A safer way, which also allows to collect statistics, is to first serialize and compress the object
    	 * locally and then send the message as a byte array.
    	 */
    	
    	// First serialize and (optionally) compress the message
    	ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
        ObjectOutputStream objOutStream = null;

        Deflater deflater = null;
    	DeflaterOutputStream defOutStream = null;
    	if(useCompression) {
			deflater = new Deflater(Deflater.BEST_SPEED);
			deflater.setStrategy(Deflater.HUFFMAN_ONLY);
            defOutStream = new DeflaterOutputStream(byteOutStream, deflater);
            objOutStream = new ObjectOutputStream(defOutStream);
        } else {
            objOutStream = new ObjectOutputStream(byteOutStream);
        }
        objOutStream.writeObject(object);
        objOutStream.flush();
        objOutStream.close();
        if(useCompression) {
            defOutStream.finish();
            defOutStream.flush();
        }
        byte[] byteMessage = byteOutStream.toByteArray();
        byteOutStream.close();

        // Now actually send the message
    	OutputStream outStream = socket.getOutputStream();
        write32(outStream, byteMessage.length);
        outStream.write(byteMessage);
        outStream.flush();

        // Update connection statistics
        thisRoundMessagesSent++;
        totalMessagesSent++;
        thisRoundBytesSent+=byteMessage.length;
        totalBytesSent+=byteMessage.length;
        if(useCompression) {
            thisRoundUncompressedBytesSent+=deflater.getTotalIn();
            totalUncompressedBytesSent+=deflater.getTotalIn();
        }
    }

	/**
	 * Copies temporary connections to the active connection pool. Call this
	 * method before a new communication round starts. Note: This method needs
	 * to be called at least once before messages can be sent.
	 */
	public synchronized void activateTemporaryConnections() {
		activeConnectionPool.putAll(temporaryConnectionPool);
		temporaryConnectionPool.clear();
	}

	/**
	 * Sends a message to a target. A connection to the recipient must be
	 * available in the connection pool. In case an exception occurs, the
	 * corresponding connection is removed from the active connection pool.
	 * 
	 * @param recipientId
	 *            the ID of the recipient.
	 * @param message
	 *            the message
	 * @return <code>true</code> if the message was sent successfully. <code>false</code> means that
	 *         the message was not sent successfully, either due to an exception
	 *         or because no connection to the recipient is available.
	 * @throws PrivacyViolationException 
	 */
	public boolean sendMessage(String recipientId, Object message) throws PrivacyViolationException {
		boolean sent = false;

		Socket socket = activeConnectionPool.get(recipientId);
		if (socket != null && socket.isConnected()) {
			try {
				sendMessage(socket, message);
				sent = true;
			} catch (IOException e) {
				logger.warning("(" + myId + ") IOException when sending message to " + recipientId + ". Details: "
						+ e.getMessage());

				// Remove the broken connection
				try {
					socket.close();
				} catch (IOException e1) {
					// ignore
				}
				removeActiveConnection(recipientId);
			}
		}

		if (!sent) {
			logger.warning("(" + myId + ") No connection available to deliver message to " + recipientId
					+ ". Message dropped.");
		}
		return sent;
	}

	/**
	 * Received a message from a sender. A connection to the sender must be
	 * available in the connection pool. In case an exception occurs, the
	 * corresponding connection is removed from the active connection pool.
	 * 
	 * @param senderId
	 *            the ID of the sender.
	 * @return The received message object. If any problem occurred
	 *         or no connection is available, null is returned.
	 * @throws PrivacyViolationException 
	 */
	public Object receiveMessage(String senderId) throws PrivacyViolationException {
		Object msg = null;
		boolean received = false;

		Socket socket = activeConnectionPool.get(senderId);
		if (socket != null && socket.isConnected()) {
			try {
				msg = receiveMessage(socket);
				received = true;
			} catch (IOException e) {
				logger.warning("(" + myId + ") IOException when receiving message from " + senderId + ". Details: "
						+ e.getMessage());

				// Remove the broken connection
				try {
					socket.close();
				} catch (IOException e1) {
					// ignore
				}
				removeActiveConnection(senderId);
			} catch (ClassNotFoundException e) {
				logger.severe(Utils.getStackTrace(e));
			}
		}

		if (!received) {
			logger.warning("(" + myId + ") No connection available to receive message from " + senderId + ".");
		}

		return msg;
	}

	/**
	 * Removes an entry from the active connection pool. Since this might be
	 * called simultaneously by multiple threads, it is synchronized to avoid a
	 * {@link ConcurrentModificationException}.
	 * 
	 * @param hostId the ID of the connected host.
	 * @throws PrivacyViolationException 
	 */
	protected synchronized void removeActiveConnection(String hostId) throws PrivacyViolationException {
		activeConnectionPool.remove(hostId);
		verifyPrivacy();
	}

	/**
	 * Adds an entry to the temporary connection pool. Since the main thread might simultaneously 
	 * count the number of peers, it is synchronized to avoid a {@link ConcurrentModificationException}.
	 * 
	 * @param hostId the ID of the connected host.
	 * @param socket the socket
	 * @throws PrivacyViolationException 
	 */
	protected synchronized void addTemporaryConnection(String hostId, SSLSocket socket) {
		temporaryConnectionPool.put(hostId, socket);
	}

	
	/**
	 * Returns my Id.
	 * 
	 * @return the Id
	 */
	public String getMyId() {
		return myId;
	}

	/**
	 * Returns all active connections.
	 * 
	 * @return the active connection pool.
	 */
	public HashMap<String, SSLSocket> getActiveConnectionPool() {
		return activeConnectionPool;
	}

	/**
	 * Returns all temporary connections.
	 * 
	 * @return the temporary connection pool.
	 */
	public HashMap<String, SSLSocket> getTemporaryConnectionPool() {
		return temporaryConnectionPool;
	}

	/**
	 * (Re-)establish connections to endpoints for which we take the initiative.
	 * Input peers connect to the privacy peers and privacy peers to
	 * each other. New connections are stored in the temporary connection pool.
	 */
	public abstract void establishConnections();

	/**
	 * Close all connections.
	 */
	public synchronized void closeConnections() {
		for (Socket socket : activeConnectionPool.values()) {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e1) {
					// ignore
				}
			}
		}
		activeConnectionPool.clear();

		for (Socket socket : temporaryConnectionPool.values()) {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e1) {
					// ignore
				}
			}
		}
		temporaryConnectionPool.clear();

	}
	
	/**
	 * Counts the number of connected input or privacy peers.
	 * 
	 * @param countPrivacyPeers <code>true</code> for counting privacy peers, <code>false</code> for counting input peers. 
	 * @param activeOnly only considers connections in the active connection pool.
	 * 
	 * @return the total number of connected peers
	 */
	public synchronized int getNumberOfConnectedPeers(boolean countPrivacyPeers, boolean activeOnly) {
		int count = 0;
		HashSet<String> ids = new HashSet<String>(activeConnectionPool.keySet());
		if (!activeOnly) {
			ids.addAll(temporaryConnectionPool.keySet());
		}
		
		for (String id : ids) {
			boolean isPrivacyPeer = privacyPeerIDs.contains(id);
			if ( (isPrivacyPeer&&countPrivacyPeers) || (!isPrivacyPeer&&!countPrivacyPeers) ) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Returns a sorted list of active peer IDs.
	 * @param wantPrivacyPeers <code>true</code> for privacy peers, <code>false</code> for input peers.
	 * @return the active peer IDs
	 */
	public synchronized List<String> getActivePeers(boolean wantPrivacyPeers) {
		ArrayList<String> idList = new ArrayList<String>();
		HashSet<String> ids = new HashSet<String>(activeConnectionPool.keySet());
		for (String id : ids) {
			boolean isPrivacyPeer = privacyPeerIDs.contains(id);
			if ( (isPrivacyPeer&&wantPrivacyPeers) || (!isPrivacyPeer&&!wantPrivacyPeers) ) {
				idList.add(id);
			}
		}
		Collections.sort(idList);
		return idList;
	}
		
	/**
	 * Checks the certificate associated to an sslSocket. 
	 * @param sslSocket the SSL socket.
	 */
	protected void checkCertificate(SSLSocket sslSocket) {
		SSLSession session = null;
		try {
			session = sslSocket.getSession();
			X509Certificate certificate = (X509Certificate) session.getPeerCertificates()[0];
			Principal principal = certificate.getSubjectDN();
			logger.info(session.getPeerHost() + " has presented a certificate belonging to [" + principal.getName()
					+ "]");
			logger.info("The certificate bears the valid signature of: [" + certificate.getIssuerDN().getName() + "]");

		} catch (SSLPeerUnverifiedException e) {
			logger.severe(session.getPeerHost() + ":" + session.getPeerPort()
					+ " did not present a valid certificate. Details: " + e.getMessage());
		}
	}

	/**
	 * Connects to an SSL server.
	 * @param host the host address
	 * @param serverPort the server port
	 * @return an SSL socket
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	protected SSLSocket createSSLSocket(String host, int serverPort) throws UnknownHostException, IOException,
			NoSuchAlgorithmException {
		SSLSocketFactory socketFactory = sslContext.getSocketFactory();
		SSLSocket socket = (SSLSocket) socketFactory.createSocket(InetAddress.getByName(host), serverPort);
		socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
		socket.setNeedClientAuth(true);
		socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
			public void handshakeCompleted(HandshakeCompletedEvent event) {
				logger.info("SSL handshake is completed. Chosen ciphersuite: " + event.getCipherSuite());
			}
		});
		checkCertificate(socket);
		return socket;
	}

	/**
	 * Enables/disables compression of messages. Default is no compression.
	 * 
	 * @param enableCompression
	 *            true for enabling compression
	 */
	public void setEnableCompression(boolean enableCompression) {
		useCompression = enableCompression;
		logger.info("Using compression: " + enableCompression);
	}
	
	/**
	 * Gets a sorted list of all <b>configured</b> privacy peer IDs.
	 * Note that not all of these are necessarily online. To get active
	 * IDs, call {@link #getActivePeers(boolean)}.
	 * @return all privacy peer IDs.
	 */
	public List<String> getConfiguredPrivacyPeerIDs () {
		return privacyPeerIDs;		
	}
	
	/**
	 * Repeatedly calls {@link #establishConnections()} until enough (active and
	 * temporary) connections are available and the active timeout (see
	 * {@link #getActiveTimeout()}) has occurred. Call this at the beginning of
	 * each computation round to ensure that the privacy policy is met and to
	 * allow new input/privacy peers to connect. The number of minimum input and
	 * privacy peer connections can be configured using
	 * {@link #setMinInputPeers(int)} and {@link #setMinPrivacyPeers(int)}.
	 */
	public void waitForConnections() {
		long lastConnection = System.currentTimeMillis();
		int connectedIPs = getNumberOfConnectedPeers(false, false);
		int connectedPPs = getNumberOfConnectedPeers(true, false);

		while (connectedIPs < minInputPeers || connectedPPs < minPrivacyPeers || (System.currentTimeMillis()-lastConnection) <= activeTimeout) {
			logger.info(Services.getFilterPassingLogPrefix()+"Waiting for more connections. (Connected input peers: "+connectedIPs+"/"+minInputPeers+", connected privacy peers: "+connectedPPs+"/"+minPrivacyPeers+")");
			establishConnections();
			int connectedIPsNow = getNumberOfConnectedPeers(false, false);
			int connectedPPsNow = getNumberOfConnectedPeers(true, false);
			
			if (connectedIPsNow!=connectedIPs || connectedPPsNow!=connectedPPs) {
				// we had a new connection. Update the timestamp.
				lastConnection = System.currentTimeMillis();
				connectedIPs = connectedIPsNow;
				connectedPPs = connectedPPsNow;
				logger.info("New connections found. (Connected input peers: "+connectedIPs+", connected privacy peers: "+connectedPPs+")");
			}
			
			// Take a short break
			try {
				Thread.sleep(SHORT_BREAK_MS);
			} catch (InterruptedException e) {
				// ignore
			}
		}
		logger.info(Services.getFilterPassingLogPrefix()+"Enough connections found and timout occurred. (Connected input peers: "+connectedIPs+", connected privacy peers: "+connectedPPs+")");
	}

	/**
	 * Tries to connect to a privacy peer if no suitable connection is already available.
	 * @param ppa the privacy peer address
	 */
	protected void connectToPrivacyPeer(PrivacyPeerAddress ppa) {
		// Do we already have an active connection?
		SSLSocket socket = activeConnectionPool.get(ppa.id);
		if (socket!=null && !socket.isConnected()) {
			// We have a malfunctioning socket. Close it now and open a new one below
			try {
				socket.close();
			} catch (IOException e) {
				// ignore
			}
			socket = null;
		}
		
		// Do we already have a temporary connection?
		if (socket==null) {
			socket = temporaryConnectionPool.get(ppa.id);
			if (socket!=null && !socket.isConnected()) {
				// We have a malfunctioning socket. Close it now and open a new one below
				try {
					socket.close();
				} catch (IOException e) {
					// ignore
				}
				socket = null;
			}
		}
		
		if (socket==null) {
			// no socket available. Try to create a new one.
			try {
				socket = createSSLSocket(ppa.host, ppa.serverPort);
				sendMessage(socket, myId);
				String ppId = (String)receiveMessage(socket);
				assert(ppId.equals(ppa.id));
				temporaryConnectionPool.put(ppa.id, socket);
				
				logger.log(Level.INFO, "("+myId+") Established connection to PP "+ppa.id+" ("+ppa.host+":"+ppa.serverPort+")");
			} catch (SocketException e) {
				logger.warning("("+myId+") Host "+ppa.id+" not reachable on "+ppa.host+":"+ppa.serverPort+". Details: "+e.getMessage());
			} catch (UnknownHostException e) {
				logger.severe(Utils.getStackTrace(e));
			} catch (IOException e) {
				logger.severe(Utils.getStackTrace(e));
			} catch (ClassNotFoundException e) {
				logger.severe(Utils.getStackTrace(e));
			} catch (NoSuchAlgorithmException e) {
				logger.severe(Utils.getStackTrace(e));				
			}
		} 
	}

	
	/**
	 * Gets the minimum number of required input peers.
	 * @return minimum number of required input peers
	 */
	public int getMinInputPeers() {
		return minInputPeers;
	}

	/**
	 * Sets the minimum number of required input peer connections. The default is 0.
	 * If run on an input peer, set this value to 0, because input peers don't connect to each other.
	 * @param minInputPeers minimum number of required input peers
	 */
	public void setMinInputPeers(int minInputPeers) {
		this.minInputPeers = minInputPeers;
	}

	/**
	 * Gets the minimum number of required privacy peers.
	 * @return minimum number of required privacy peers
	 */
	public int getMinPrivacyPeers() {
		return minPrivacyPeers;
	}

	/**
	 * Sets the minimum number of required privacy peer connections. The default is 3.
	 * Note that for privacy peers, the value from the config file needs to be decremented
	 * by 1, because they don't connect to themselves.
	 * @param minPrivacyPeers minimum number of required privacy peers
	 */
	public void setMinPrivacyPeers(int minPrivacyPeers) {
		this.minPrivacyPeers = minPrivacyPeers;
	}
	
	/**
	 * Checks whether enough active input and privacy peers are connected.
	 * If not, an exception is thrown. This method is called after a connection
	 * is lost, to check whether we can continue.
	 * @throws PrivacyViolationException 
	 */
	protected void verifyPrivacy() throws PrivacyViolationException {
		int activeIPs = getNumberOfConnectedPeers(false, true);
		int activePPs = getNumberOfConnectedPeers(true, true);
		
		if(activeIPs < minInputPeers || activePPs < minPrivacyPeers) {
			throw new PrivacyViolationException(activeIPs, activePPs, minInputPeers, minPrivacyPeers);
		}
	}
	
	/**
	 * Gets the active timeout. 
	 * @return the active timeout in milliseconds
	 */
	public int getActiveTimeout() {
		return activeTimeout;
	}

	/**
	 * Sets the active timeout. This timeout is the time for which {@link #waitForConnections()} waits
	 * after the last connection has been established. Default is 10,000ms.
	 * @param the active timeout in milliseconds.
	 */
	public void setActiveTimeout(int activeTimeout) {
		this.activeTimeout = activeTimeout;
	}

    /**
     * Writes a 32bit integer (8 bit at a time)
     * 
     * @param i The integer to send
     */
    private void write32(OutputStream outStream, int i) throws IOException {
        // '>>' will shift zeros (for neg. numbers too)     
        outStream.write((i & LSB4) >> 24);
        outStream.write((i & LSB3) >> 16);
        outStream.write((i & LSB2) >> 8);
        outStream.write(i & LSB1);
    }

    
    /**
     * Reads a 32bit integer (8 bit at a time)
     * 
     * @return Integer that was received
     * @throws IOException 
     */
    private int read32(InputStream inStream) throws IOException {
        int i1,i2,i3,i4;
        
        int t1 = inStream.read();
        if(t1 != -1) {
        	i1 = (t1 & LSB1) << 24;
        } else {
        	throw new IOException("End of inStream reached (read returned -1).");
        }
        
        int t2 = inStream.read();
        if(t2 != -1) {
        	i2 = (t2 & LSB1) << 16;
        } else {
        	throw new IOException("End of inStream reached (read returned -1).");
        }
        
        int t3 = inStream.read();
        if(t3 != -1) {
        	i3 = (t3 & LSB1) << 8;
        } else {
        	throw new IOException("End of inStream reached (read returned -1).");
        }
        
        int t4 = inStream.read();
        if(t4 != -1) {
        	i4 = (t4 & LSB1);
        } else {
        	throw new IOException("End of inStream reached (read returned -1).");
        }

        return (i1 | i2 | i3 | i4);
    }

}
