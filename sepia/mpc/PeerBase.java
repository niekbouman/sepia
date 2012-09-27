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

package mpc;

import java.io.LineNumberReader;
import java.util.Observable;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import services.Stopper;
import services.Utils;
import connections.ConnectionManager;
import events.ExceptionEvent;
import events.GoodbyeEvent;

/**
 * A base class for input and privacy peers.
 * 
 * @author Lisa Barisic, ETH Zurich
 */
public abstract class PeerBase extends Observable {

    private static final int DEFAULT_SLEEPTIME = 1500;
	/** To say you're about to send your peer ID (hopefully unique :) */
    public static final String PEER_ID = "PEER_ID";
    /** Say you're about to send a share */
    public static final String SHARE_DELIVERY = "SHARE_DELIVERY";
    /** To say you're about to send a list holding disqualifications */
    public static final String DISQUALIFICATION_DELIVERY = "DISQUALIFICATION_DELIVERY";
    /** To say you're about to send the final result */
    public static final String RESULT_DELIVERY = "RESULT_DELIVERY";
    /** To say you're about to send the skipInputVerification flag */
    public static final String SKIP_INPUT_VERIFICATION_FLAG = "SKIP_INPUT_VERIFICATION_FLAG";
    /** Pseudo random generators */
    public static final String[] PRG_LIST = {"SHA1PRNG"};
    /** Minumum number of peers needed if result shall be reconstructed */
    public static final int MIN_PEERS_FOR_RECONSTRUCTION = 3;
	
    protected final String OUTPUT_FILE_PREFIX = "sepia_output_";
	
    protected Stopper stopper = null;
    protected Logger logger = null;
    protected int myPeerIndex = 0;
    protected String myPeerID; // Unique ID 
    protected ConnectionManager connectionManager = null;
    
    protected int minInputPeers = 0;
    protected int minPrivacyPeers = 0;
    
    protected Stopper protocolStopper = null;
    protected int currentTimeSlot = 0;
    protected int verificationsToDo = 0;
    protected int finalResultsToDo = 0;
    protected int goodbyesReceivedToDo = 0;
    protected String randomAlgorithm = null;
    protected LineNumberReader lineNumberReader = null;
    //protected SecureRandom random = null;
    protected Random random = null;

    /**
     * Initialize the MPC primitive
     */
    public abstract void  initialize() throws Exception;

    /**
     * Initializes a new round.
     */
    protected abstract void initializeNewRound();

    /**
     * Run the MPC protocol(s) over the given connection(s).
     */
    public abstract void runProtocol() throws Exception;

    protected abstract void initProperties() throws Exception;

    protected abstract void cleanUp() throws Exception;
    
    /**
     * Creates a new instance of a general MPC peer.
     * 
     * @param myPeerIndex The peer's number/index
     * @param stopper Stopper with which this thread can be stopped
     */
    public PeerBase(int myPeerIndex, ConnectionManager cm, Stopper stopper)  {
        this.logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        this.myPeerIndex = myPeerIndex;
        this.connectionManager = cm;
        this.stopper = stopper;
    }

    /**
     * Returns the connection manager.
     * @return the connection manager
     */
    public ConnectionManager getConnectionManager() {
    	return connectionManager;
    }
    
    /**
     * Processes the event when a MpcMessage was received from an observable.
     * 
     * @param messageBase The message received
     */
    protected synchronized void processMpcMessage(MessageBase messageBase) throws Exception {
        if (messageBase.isHelloMessage()) {
            logger.log(Level.INFO, "Received Hello message");

        } else if (messageBase.isGoodbyeMessage()) {
            if (messageBase.wasGoodbyeReceived()) {
                logger.log(Level.INFO, "Received goodbye message...");
                goodbyesReceivedToDo--;
                logger.log(Level.INFO, "Goodbyes to do: " + goodbyesReceivedToDo);
                if (goodbyesReceivedToDo <= 0) {
                    logger.log(Level.INFO, "Sending goodbye to observers...");
                    sendNotification(new GoodbyeEvent(this));
                }
            }
        }
    }

    protected synchronized boolean checkStopped() {
        if (stopper.isStopped()) {
            logger.log(Level.WARNING, "I was stopped... stop processing");
            stopProcessing();
            try {
                cleanUp();
            } catch (Exception e) {
            // Ignore, we've been stopped anyway...
            	logger.log(Level.SEVERE, "Exception occured in Thread.sleep(): "+Utils.getStackTrace(e));
            }
            return true;
        }
        return false;
    }

    protected synchronized void stopProcessing() {
        logger.log(Level.INFO, "Stopping mpc entropy protocols...");
        protocolStopper.setIsStopped(true);
    }

    /**
     * Sets changed and then notify your observers
     * 
     * @param event The event to send to the observers
     */
    protected synchronized void sendNotification(Object event) {
        logger.log(Level.INFO, "Notifying observers...");
        setChanged();
        notifyObservers(event);
    }

    /**
     * Notifes observers about an exceptional event and stops processing.
     * 
     * @param source Observable
     * @param exception Exception to be sent
     * @param message Additional message
     */
    protected synchronized void sendExceptionEvent(Object source, Exception exception, String message) {
        sendExceptionEvent(new ExceptionEvent(source, exception, message));
    }

    /**
     * Notifes observers about an exceptional event and stops processing.
     * 
     * @param source Observable
     * @param message Exception message
     */
    protected synchronized void sendExceptionEvent(Object source, String message) {
        sendExceptionEvent(source, new Exception(message), "");
    }

    /**
     * Notifes observers about an exceptional event and stops processing.
     * 
     * @param exceptionEvent Exception event
     */
    protected synchronized void sendExceptionEvent(ExceptionEvent exceptionEvent) {
        sendNotification(exceptionEvent);
        stopProcessing();
    }

    public synchronized int getCurrentTimeSlot() {
        return currentTimeSlot;
    }

    public synchronized String getMyPeerID() {
        return myPeerID;
    }

    public synchronized void setMyPeerID(String myPeerID) {
        this.myPeerID = myPeerID;
    }

    public synchronized int getMyPeerIndex() {
    	return myPeerIndex;
    }

    /**
     * Wait until next time slot bulk is ready (if any)
     *
     * @return true if next time slot bulk is ready for processing, false if
     *         i should stop processing...
     */
    public final boolean waitForNextTimeSlotData(int expectedTimeSlotRoundNumber) {
        while (currentTimeSlot != expectedTimeSlotRoundNumber) {
            logger.log(Level.INFO, "thread \"" + Thread.currentThread().getName() + "\": Wait until time slot round " + expectedTimeSlotRoundNumber + " is ready...");

            try {
                Thread.sleep(DEFAULT_SLEEPTIME);
            } catch (Exception e) {
            	// wake up...
            	logger.log(Level.SEVERE, "Exception occured in Thread.sleep(): "+Utils.getStackTrace(e));
            }

            // Leave if someone stopped you
            if (protocolStopper.isStopped()) {
                logger.log(Level.INFO, "I was stopped...");
                return false;
            }
        }
        return true;
    }
}
