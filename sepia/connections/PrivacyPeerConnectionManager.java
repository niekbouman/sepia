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

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.net.Socket;

import services.Utils;

/**
 * Manages all the connections of a single privacy peer.
 * 
 * Incoming connections are handled by a separate {@link ConnectionAcceptor}
 * thread. To start/stop the connection acceptor thread call {@link #start()}/
 * {@link #stop()}. The ConnectionManager is an observer of the connection
 * acceptor thread and gets notified upon a new socket connection. New
 * connections are then put in the temporary connection pool. Connections for
 * which this privacy peer takes the initiative (see
 * {@link #doITakeInitiative(String)} are established in
 * {@link #establishConnections()}.
 * 
 * @author martibur
 */

public class PrivacyPeerConnectionManager extends ConnectionManager implements Observer {
	private ConnectionAcceptor ca;
	private boolean started;
	private int listeningPort;

	/**
	 * See {@link ConnectionManager#ConnectionManager(String, List, SSLContext)}.
	 * 
	 * @param listeningPort
	 *            The server port used for incoming connections.
	 */
	public PrivacyPeerConnectionManager(String myId, int listeningPort, List<PrivacyPeerAddress> privacyPeerAddresses,
			SSLContext sslContext) {
		super(myId, privacyPeerAddresses, sslContext);
		this.listeningPort = listeningPort;
		started = false;
	}

	/**
	 * Starts listening for incoming connections.
	 */
	public void start() {
		if (!started) {
			logger.log(Level.INFO, "Privacy Peer (id=" + myId + ") accepts connections on port " + listeningPort + ".");

			ca = new ConnectionAcceptor(listeningPort, sslContext);
			ca.addObserver(this);
			Thread th = new Thread(ca, "Connection Acceptor");
			th.start();
			started = true;
		}
	}

	/**
	 * Stops listening for incoming connections.
	 */
	public void stop() {
		if (started) {
			logger.log(Level.INFO, "Privacy Peer (id=" + myId + ") stops listening on port " + listeningPort + ".");
			ca.stopAccepting();
			ca = null;
			started = false;
		}
	}

	/**
	 * Attempts to (re-)open all connections to privacy peers <b>for which we
	 * are responsible to initiate the connection</b> (see
	 * {@link #doITakeInitiative(String)}. A connection to a specific endpoint
	 * is only initiated if no connection (or no working connection) is
	 * currently available. Input peers and privacy peers for which we do not
	 * take the initiative will connect to us.
	 */
	public void establishConnections() {
		if (!started) {
			return;
		}
		for (PrivacyPeerAddress ppa : privacyPeerAddresses) {
			if (doITakeInitiative(ppa.id)) {
				connectToPrivacyPeer(ppa);
			}
		}
	}

	protected boolean doITakeInitiative(String otherId) {
		return sendingFirst(myId, otherId);
	}

	/**
	 * Decides who is initiating a connection between two privacy peers based on
	 * the lexicographical order of the two IDs. The smaller ID initiates the
	 * connection and sends first when data is exchanged.
	 * 
	 * @param id1 the first ID
	 * @param id2 the second ID
	 * @return <code>true</code> if id1 sends first.
	 */
	public static boolean sendingFirst(String id1, String id2) {
		return id1.compareTo(id2) < 0;
	}
	
	/**
	 * This method handles notifications from the connection accepting thread.
	 * @param obs The observable (the connection thread)
	 * @param obj The new socket connection
	 */
	@Override
	public void update(Observable obs, Object obj) {
		if (obj instanceof Socket) {
			// We got a new incoming connection
			Socket socket = (Socket)obj;

			// Read the ID of our new friend.
			try {
				// printSSLSocketInfo(socket);
				// socket.startHandshake();
				//checkCertificate(socket);
				String id = (String) receiveMessage(socket);
				addTemporaryConnection(id, socket);
				sendMessage(socket, myId);

				logger.log(Level.INFO, "(" + myId + ") New connection from id " + id + ".");
			} catch (IOException e) {
				logger.severe(Utils.getStackTrace(e));
			} catch (ClassNotFoundException e) {
				logger.severe(Utils.getStackTrace(e));
			}
		}
	}

	/**
	 * Returns whether the connection accepting thread is started.
	 * 
	 * @return true if the thread is running
	 */
	public boolean isStarted() {
		return started;
	}

}
