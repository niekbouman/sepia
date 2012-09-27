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

import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;

import services.Stopper;
import connections.ConnectionManager;

/**
 * A general MPC protocol class.
 * 
 * @author Lisa Barisic, ETH Zurich
 */
public abstract class ProtocolBase extends Observable implements Runnable {

    /** my ID */
    protected String myPeerID = null;
    /** The ID of my counterpart */
    protected String otherPeerID = null;

    protected ConnectionManager connectionManager;
    protected static final int DEFAULT_SLEEPTIME = 1500;
    protected int myThreadNumber = 0;
    protected int myPeerIndex = 0;
    protected int otherPeerIndex = -1;
    protected int numberOfParticipants;
    protected int timeSlotCount;
    protected int metricCount;
    protected Logger logger = null;
    protected Stopper stopper = null;
    protected boolean goodbyesDone = false;

    /**
     * Creates a new peer that shares data (connected to one other peer)
     *
     * @param threadNumber This peer's thread number (for identification when
     *                     notifying observers)
     *                     
     * @param cm the connection manager
     * @param myPeerID The local peer's own ID (to be sent to other peers)
     * @param otherPeerID the other peer's ID
     * @param myPeerIndex The peer's own number/index (to be sent to other peers)
     * @param otherPeerIndex The other peer's number/index
     * @param stopper the stopper 
     */
    public ProtocolBase(int threadNumber, ConnectionManager cm, String myPeerID, String otherPeerID, int myPeerIndex, int otherPeerIndex,Stopper stopper) {
        this.myThreadNumber = threadNumber;
        this.connectionManager = cm;
        this.myPeerID = myPeerID;
        this.otherPeerID = otherPeerID;
        this.myPeerIndex = myPeerIndex;
        this.otherPeerIndex = otherPeerIndex;
        this.logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        this.stopper = stopper;
    }

    protected void initialize(int timeSlotCount, int metricCount, int numberOfParticipants) {
        this.timeSlotCount = timeSlotCount;
        this.metricCount = metricCount;
        this.numberOfParticipants = numberOfParticipants;
    }

    protected abstract void sendMessage() throws Exception;

    protected abstract void receiveMessage() throws Exception;

    /**
     * Sets changed and then notifies the observers
     */
    protected synchronized void notify(Object object) {
        logger.log(Level.INFO, "Notifying observers...");
        setChanged();
        notifyObservers(object);
    }

    public int getMyPeerIndex() {
        return myPeerIndex;
    }

    public void setMyPeerIndex(int myPeerIndex) {
        this.myPeerIndex = myPeerIndex;
    }

	/**
	 * @return the ID of the peer which started this protocol instance
	 */
	public String getMyPeerID() {
		return myPeerID;
	}
}


