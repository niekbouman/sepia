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

import java.io.Serializable;

/**
 * A message for a general MPC message that is exchanged between peers.
 *
 * @author Lisa Barisic, ETH Zurich
 */
public class MessageBase implements Serializable {
	private static final long serialVersionUID = 7307382590667505236L;

	protected int roundNumber = 0;
    protected String senderID = null;
    protected int senderIndex = 0;
    protected String message = null;
    protected int timeSlotCount = 0;
    protected int metricCount = 0;
    protected boolean isHelloMessage = false;
    protected boolean isGoodbyeMessage = false;
    protected boolean wasGoodbyeReceived = true; // Distinguish between goodbyes received and sent
    protected boolean isVerificationSuccessful = true;
    protected boolean isDummyMessage = false;
    /**
     * Creates a new instance of a message.
     * 
     * @param senderID Sender's ID
     * @param senderIndex Sender's number or index
     */
    public MessageBase(String senderID, int senderIndex) {
        this.senderID = senderID;
        this.senderIndex = senderIndex;
    }

    /**
     * Creates a new instance of a message.
     * 
     * @param roundNumber Current round number
     * @param senderID Sender's ID
     * @param senderIndex Sender's number or index
     */
    public MessageBase(int roundNumber, String senderID, int senderIndex) {
        this(senderID, senderIndex);
        this.roundNumber = roundNumber;
    }

    /**
     * Dummy messages are used to notify observers even though the connection counterpart is offline.
     * @return <true> if this is a dummy message.
     */
	public boolean isDummyMessage() {
		return isDummyMessage;
	}

	 /**
     * Dummy messages are used to notify observers even though the connection counterpart is offline.
     * @param isDummyMessage <true> if this is a dummy message.
     */
	public void setIsDummyMessage(boolean isDummyMessage) {
		this.isDummyMessage = isDummyMessage;
	}

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getSenderID() {
        return senderID;
    }

    public void setSenderID(String senderID) {
        this.senderID = senderID;
    }

    public int getTimeSlotCount() {
        return timeSlotCount;
    }

    public void setTimeSlotCount(int timeSlotCount) {
        this.timeSlotCount = timeSlotCount;
    }

    public int getMetricCount() {
        return metricCount;
    }

    public void setMetricCount(int metricCount) {
        this.metricCount = metricCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isGoodbyeMessage() {
        return isGoodbyeMessage;
    }
    
    public void setIsGoodbyeMessage(boolean isGoodbyeMessage) {
        this.isGoodbyeMessage = isGoodbyeMessage;
    }
    
    public boolean isHelloMessage() {
        return isHelloMessage;
    }

    public void setIsHelloMessage(boolean isHelloMessage) {
        this.isHelloMessage = isHelloMessage;
    }

    public int getSenderIndex() {
        return senderIndex;
    }

    public void setSenderIndex(int senderIndex) {
        this.senderIndex = senderIndex;
    }

    public boolean wasGoodbyeReceived() {
        return wasGoodbyeReceived;
    }

    public void setWasGoodbyeReceived(boolean wasGoodbyeReceived) {
        this.wasGoodbyeReceived = wasGoodbyeReceived;
    }

    public boolean isVerificationSuccessful() {
        return isVerificationSuccessful;
    }

    public void setIsVerificationSuccessful(boolean isVerificationSuccessful) {
        this.isVerificationSuccessful = isVerificationSuccessful;
    }
}
