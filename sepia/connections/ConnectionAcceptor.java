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
import java.util.Observable;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import javax.net.SocketFactory;
import javax.net.ServerSocketFactory;
import java.net.ServerSocket;

/**
 * The connection acceptor is a thread that runs on privacy peer hosts and constantly accepts incoming
 * connections. This class implements {@link Observable} and notifies its observers with new socket connections.
 * @author martibur
 *
 */
public class ConnectionAcceptor extends Observable implements Runnable {

	private int listeningPort;
	private SSLContext sslContext;
	private ServerSocket serverSocket;
	private boolean stopped = false;
	
	/**
	 * Thread for accepting connections. 
	 * @param listeningPort the port for incoming connections
	 * @param sslContext the SSLContext, properly initialized for client authentication
	 */
	public ConnectionAcceptor(int listeningPort, SSLContext sslContext) {
		this.listeningPort = listeningPort;
		this.sslContext = sslContext;
	}
	
	/**
	 * Starts listening for connections on the given port.
	 */
	public void run() {
		try {
			//SSLServerSocketFactory socketFactory  = sslContext.getServerSocketFactory();
            ServerSocketFactory socketFactory = ServerSocketFactory.getDefault();
            serverSocket = (ServerSocket)socketFactory.createServerSocket(listeningPort);
			//serverSocket.setEnabledCipherSuites(serverSocket.getSupportedCipherSuites());
			//serverSocket.setNeedClientAuth(true);

		} catch (IOException e) {
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).severe("Could not open server socket. Details: "+e.getMessage());
			return;
		} 

		while(!stopped) {
			try {
				Socket socket = serverSocket.accept();
				setChanged();
				notifyObservers(socket);
			} catch (IOException e) {
				if(!stopped) {
					Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).severe("Problem during serverSocket.accept(). Details: "+e.getMessage());
				}
			}
		}
		
	}
	
	/**
	 * Closes the open server socket and finishes the thread.
	 */
	public void stopAccepting() {
		stopped = true;
		try {
			if (serverSocket!=null) {
				serverSocket.close();
			}
		} catch (IOException e) {
			// ignore
		}
	}

}
