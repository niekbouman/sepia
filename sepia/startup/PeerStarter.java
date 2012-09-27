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

package startup;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import mpc.PeerBase;
import mpc.PeerFactory;
import mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import services.Services;
import services.Stopper;
import services.Utils;
import connections.ConnectionManager;
import connections.InputPeerConnectionManager;
import connections.PrivacyPeerAddress;
import connections.PrivacyPeerConnectionManager;
import events.ExceptionEvent;
import events.FinalResultEvent;
import events.GoodbyeEvent;

/**
 * Starts input and privacy peers.
 */
public class PeerStarter extends Observable implements Observer, Runnable {

	protected String myID = null;
	protected int myServerPort;
	protected int minInputPeers = 0;
	protected int minPrivacyPeers = 0;
	protected int numberOfTimeSlots = 0;
	protected int numberOfItems = 0;
	protected int timeSlotsToDo = 0;
	protected int timeout = 0;
	protected boolean isInputPeer;
	protected List<PrivacyPeerAddress> privacyPeers = null;
	protected ConnectionManager connectionManager = null;
	protected Logger logger = null;
	protected String errorMessage = null;
	protected Stopper stopper = null;
	protected Stopper mpcStopper = null;
	protected PeerBase peer = null;
	protected boolean useCompression = false;
	protected String outputFileName;
	private long timestampStartRound;

	/**
	 * Superclass of all peers.
	 * 
	 * @param stopper
	 *            Can be used to stop a running thread.
	 * @param isInputPeer
	 *            true if it is an input peer, false for privacy peers
	 */
	public PeerStarter(Stopper stopper, boolean isInputPeer) {
		this.logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		this.stopper = stopper;
		mpcStopper = new Stopper();
		outputFileName = null;
		timestampStartRound = System.currentTimeMillis();
		this.isInputPeer = isInputPeer;

		initProperties();
	}

	/**
	 * Closes all connections.
	 */
	protected void stopConnectionManagers() {
		if(connectionManager!=null) {
			if (!isInputPeer) {
				((PrivacyPeerConnectionManager)connectionManager).stop();
			}
			connectionManager.closeConnections();
		}
	}

	public int getNumberOfTimeSlots() {
		return numberOfTimeSlots;
	}

	protected synchronized void createMPCinstance() throws Exception {
		// not interested in the peer number
		peer = PeerFactory.getPeerInstance(isInputPeer, 0, connectionManager, mpcStopper); 
		peer.initialize();
	}

	/**
	 * Reads the properties and sets internal values accordingly.
	 */
	public void initProperties() {
		Properties properties = ConfigFile.getInstance().getProperties();
		timeout = Integer.valueOf(properties.getProperty(ConfigFile.PROP_TIMEOUT, ConfigFile.DEFAULT_TIMEOUT));
		minInputPeers = Integer.valueOf(properties.getProperty(ConfigFile.PROP_MIN_INPUTPEERS));
		minPrivacyPeers = Integer.valueOf(properties.getProperty(ConfigFile.PROP_MIN_PRIVACYPEERS));
		myID = properties.getProperty(ConfigFile.PROP_MY_PEER_ID);
		numberOfTimeSlots = Integer.valueOf(properties.getProperty(ConfigFile.PROP_NUMBER_OF_TIME_SLOTS));
		timeSlotsToDo = numberOfTimeSlots;
		numberOfItems = Integer.valueOf(properties.getProperty(ConfigFile.PROP_NUMBER_OF_ITEMS));
		useCompression = Boolean.parseBoolean(properties.getProperty(ConfigFile.PROP_CONNECTION_USE_COMPRESSION));

		// Parse the privacy peer config entry
		privacyPeers = new ArrayList<PrivacyPeerAddress>();
		String ppString = properties.getProperty(ConfigFile.PROP_ACTIVE_PRIVACY_PEERS);
		if (ppString != null) {
			String[] pps = ppString.split(";");
			for (String pp : pps) {
				String[] parts = pp.split(":");
				PrivacyPeerAddress ppa = new PrivacyPeerAddress(parts[0], parts[1], Integer.parseInt(parts[2]));
				privacyPeers.add(ppa);

				if (myID.equals(ppa.id)) {
					// note down my server port
					myServerPort = ppa.serverPort;
				}
			}

			/*
			 * All must have the same order to ensure that each privacy peer
			 * gets shares for the same evaluation point from all the input
			 * peers
			 */
			Collections.sort(privacyPeers);
		} else {
			logger.severe("No privacy peers configured!");
		}

		// Output the properties that were set
		logger.info("The following properties were set:");
		logger.info("minInputPeers: " + minInputPeers);
		logger.info("minPrivacyPeers: " + minPrivacyPeers);
		logger.info("timeout: " + timeout);
		logger.info("use compression: " + useCompression);
		logger.info("my peer ID: " + myID);

		StringBuffer ppsb = new StringBuffer("Configured privacy peers:");
		for (PrivacyPeerAddress ppa : privacyPeers) {
			ppsb.append("\n");
			ppsb.append(ppa.toString());
		}
		logger.info(ppsb.toString());
	}

	protected void stopProcessing() {
		logger.log(Level.INFO, Services.getFilterPassingLogPrefix() + "Shutting down SEPIA...");
		mpcStopper.setIsStopped(true);
		stopConnectionManagers();
		peer = null;
		
		// That's all folks!
	}

	protected boolean checkStopped() {
		if (stopper.isStopped()) {
			logger.log(Level.INFO, Services.getFilterPassingLogPrefix() + "Processing stopped by stopper!");
			stopProcessing();
			return true;
		}
		return false;
	}

	/**
	 * Start a MPC primitive and let it exchange data with the privacy peers.
	 * This is added to the mpc's observer list to receive the final results.
	 */
	protected synchronized void startMPC() throws Exception {
		peer.addObserver(this);
		peer.runProtocol();
	}

	public synchronized void update(Observable observable, Object object) {
		ExceptionEvent exceptionEvent;

		logger.log(Level.INFO, "Received notification from observable...(" + observable.getClass().getName() + ")");

		if (checkStopped()) {
			return;
		}

		try {
			if (object instanceof ExceptionEvent) {
				processExceptionEvent((ExceptionEvent) object, observable);

			} else if (observable instanceof PeerBase) {
				processMpcEvent(object, observable);

			} else {
				logger.log(Level.WARNING, "Unexpected notifier: " + observable.getClass());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error when processing event: " + Utils.getStackTrace(e) + "("
					+ observable.getClass().getName() + ")");
			exceptionEvent = new ExceptionEvent(this, new Exception("Error when processing event: " + e.getMessage()));
			notify(exceptionEvent.getMessage(), exceptionEvent);
			stopProcessing();
		}
	}

	private void processExceptionEvent(ExceptionEvent exceptionEvent, Observable observable) throws Exception {
		logger.log(Level.SEVERE, "Received Exception Event: " + exceptionEvent.getMessage());
		logger.log(Level.SEVERE, "Exception occurred at: " + Utils.getStackTrace(exceptionEvent.getException()));

		notify("Exception event received from " + observable.getClass().getName(), exceptionEvent);
		stopProcessing();
	}

	private void processMpcEvent(Object object, Observable observable) throws Exception {
		FinalResultEvent finalResultEvent;

		if (object instanceof FinalResultEvent) {
			logger.log(Level.INFO, "Received FinalResultEvent ");
			finalResultEvent = (FinalResultEvent) object;
			/*
			 * The round may be successful if enough peer's verification succeeded,
			 * but if a peer is disqualified he will get zeros as result for this round
			 */
			logger.info( Services.getFilterPassingLogPrefix() + "Round was successful: " + finalResultEvent.isWholeRoundSuccessful());
			logger.info( "Verification of my inputs was successful: " + finalResultEvent.isVerificationSuccessful());

			timeSlotsToDo--;

			// Output various connection and running time statistics
			logger.info(Services.getFilterPassingLogPrefix()+ConnectionManager.getStatistics());
			ConnectionManager.newStatisticsRound();
			if (!isInputPeer) {
				logger.log(Level.INFO, Services.getFilterPassingLogPrefix() + PrimitivesEnabledProtocol.getStatistics());
//				PrimitivesEnabledProtocol.newStatisticsRound();
			}

			// Output timing statistics
			long currentTs = System.currentTimeMillis();
			logger.log(Level.INFO, Services.getFilterPassingLogPrefix() + "--> Running time of round (including connection discovery!): "
					+ (currentTs - timestampStartRound) / 1000.0 + " seconds.");
			timestampStartRound = currentTs;

			logger.log(Level.INFO, Services.getFilterPassingLogPrefix() + "Time slots to do: " + timeSlotsToDo);

			if (timeSlotsToDo <= 0) {
				logger.log(Level.INFO, Services.getFilterPassingLogPrefix()
						+ "Secret Sharing done for all time slots...");

				// We're done! 
				stopProcessing();
			}

			// Notify our own observer and tell them the result
			notify("Analyzer result", finalResultEvent);
		} else if (object instanceof GoodbyeEvent) {
			logger.log(Level.WARNING, "Received goodbye event...");
			stopProcessing();
			notify("Bye bye...", object);
		} else {
			logger.log(Level.WARNING, "Unexpected Event type: " + object.getClass());
		}
	}

	protected void sendExceptionEvent(Exception exception, String message) {
		ExceptionEvent event;

		event = new ExceptionEvent(this, exception, message);
		notify(message, event);
	}

	/**
	 * Sets changed and then notify your observers
	 * 
	 * @param event
	 *            The event to send to the observers
	 */
	protected synchronized void notify(String comment, Object event) {
		logger.log(Level.INFO, comment + ": Notifying observers...");
		setChanged();
		notifyObservers(event);
	}

	/**
	 * Creates the peer instance and the connection manager. Then, connections
	 * between peers are established, and the protocol is started.
	 */
	public synchronized void run() {
		try {
			SSLContext sslContext = initKeyStore();
			if (isInputPeer) {
				connectionManager = new InputPeerConnectionManager(myID, privacyPeers, sslContext);
				connectionManager.setEnableCompression(useCompression);
				// Other input peers don't connect to us
				connectionManager.setMinInputPeers(0);
				connectionManager.setMinPrivacyPeers(minPrivacyPeers);
				connectionManager.setActiveTimeout(timeout);
			} else {
				connectionManager = new PrivacyPeerConnectionManager(myID, myServerPort, privacyPeers, sslContext);
				connectionManager.setEnableCompression(useCompression);
				connectionManager.setMinInputPeers(minInputPeers);
				connectionManager.setActiveTimeout(timeout);
				/*
				 * For privacy peers, the expected number of privacy peer connections is minPrivacyPeers-1,
				 * because they are a privacy peer themselves.
				 */
				connectionManager.setMinPrivacyPeers(minPrivacyPeers-1);
				((PrivacyPeerConnectionManager) connectionManager).start();
			}

			// Start MPC primitive and add this to the observer list
			logger.info("Starting MPC...");
			createMPCinstance();
			startMPC();

		} catch (Exception e) {
			String message = "Unexpected error in run(): " + Utils.getStackTrace(e)
					+ " -> Notify observers and then clean up...";
			logger.severe(message);

			// Notify my observers...
			sendExceptionEvent(e, message);
			stopProcessing();
		}
	}

	/**
	 * Loads and initializes the keystore. Creates an SSLContext, which has the
	 * local certificates associated for client authentication.
	 * 
	 * @note You can add a keystore with trusted certificates via the VM
	 *       parameter "-Djavax.net.ssl.trustStore".
	 * @return an the SSLContext for client authentication.
	 */
	private SSLContext initKeyStore() {
		KeyStore keyStore;
		SSLContext sslContext = null;

		Properties properties = ConfigFile.getInstance().getProperties();

		try {
			keyStore = KeyStore.getInstance("JKS");
			String keystoreName = properties.getProperty(ConfigFile.PROP_KEY_STORE);
			keyStore.load(new FileInputStream(keystoreName), properties.getProperty(ConfigFile.PROP_KEY_STORE_PASSWORD)
					.toCharArray());
			logger.info("Loaded keystore from file: " + keystoreName);

			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
			keyManagerFactory.init(keyStore, properties.getProperty(ConfigFile.PROP_KEY_PASSWORD).toCharArray());
			logger.info("KeyStore class: " + keyStore.getClass());
			logger.info("KeyStore Type/Provider/Size: " + keyStore.getType() + "/" + keyStore.getProvider() + "/"
					+ keyStore.size());

			// Init the SSLContext with the certificates from the local keystore
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		} catch (KeyStoreException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (NoSuchAlgorithmException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (CertificateException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (FileNotFoundException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (IOException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (UnrecoverableKeyException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (KeyManagementException e) {
			logger.severe(Utils.getStackTrace(e));
		}
		return sslContext;
	}
}
