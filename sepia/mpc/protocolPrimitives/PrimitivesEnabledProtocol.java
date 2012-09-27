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

package mpc.protocolPrimitives;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.logging.Level;

import mpc.ProtocolBase;
import mpc.protocolPrimitives.operations.IOperation;
import mpc.protocolPrimitives.operations.LessThan;
import services.Stopper;
import connections.ConnectionManager;
import connections.PrivacyPeerConnectionManager;
import connections.PrivacyViolationException;

/**
 * Abstract protocol class for concrete privacy peer to privacy peer
 * protocol classes that want to use the operations from
 * MpcShamirSharingProtocolPrimitives.
 * <p>
 * Before using the operations through this class the reference to the privacy
 * peer that created this protocol instance and the information about the
 * privacy peer at the other end (of the communication link) has to be set.
 * use: {@link #initializeProtocolPrimitives(PrimitivesEnabledPeer, PeerInfo)}
 *
 * @author Dilip Many
 *
 */
public abstract class PrimitivesEnabledProtocol extends ProtocolBase  {

	/** holds the message to be sent over the connection */
	protected PrimitivesMessage messageToSend;
	/** hold the last received message */
	protected PrimitivesMessage messageReceived;
	/** info object to hold information about peer at other end of the connection */
//	protected PeerInfo otherPeerInfo = null;
	/** defines the string which precedes a Shamir Sharing Protocol Primitives message */
	protected static String SHAMIR_SHARING_PROTOCOL_PRIMITIVES_MESSAGE = "SSPP_MSG";

	/** the privacy peer that created this protocol instance */
	private PrimitivesEnabledPeer primitivesEnabledPeer = null;
	/** the primitives used by the privacy peer that created this protocol instance */
	private Primitives primitives = null;

    /*
     *  Variables used for running time statistics. During doOperations(), one thread stops the elapsed time
     *  during computation and communication. Since all threads run in parallel and have roughly equal loads, this
     *  can be used as an approximation of the overall times for computation and communication. 
     */ 
    private static long totalComputationTime, totalCommunicationTime;
    private static long thisRoundComputationTime, thisRoundCommunicationTime;
    private static long numberOfFinishedRounds;

	/**
	 * Calls {@link ProtocolBase#ProtocolBase(int, ConnectionManager, String, String, int, int, Stopper)}.
	 */
	public PrimitivesEnabledProtocol(int threadNumber, ConnectionManager cm, String myPeerID, String otherPeerID, int myPeerIndex, int otherPeerIndex, Stopper stopper)  {
		super(threadNumber, cm, myPeerID, otherPeerID, myPeerIndex, otherPeerIndex, stopper);
	}


	/**
	 * initializes the protocol instance
	 *
	 * @param primitivesEnabledPeer		the privacy peer that created this protocol instance
	 * @param peerInfo											the information about the privacy peer at the other end of the communication link
	 */
	public void initializeProtocolPrimitives(PrimitivesEnabledPeer primitivesEnabledPeer) {
		this.primitivesEnabledPeer = primitivesEnabledPeer;
	}

	/**
	 * Executes the scheduled operations, including the synchronization of
	 * intermediate share values with the other privacy peers.
	 *
	 * @return				true, if operation execution was successful
	 * @throws PrimitivesException 
	 * @throws BrokenBarrierException 
	 * @throws InterruptedException 
	 * @throws PrivacyViolationException 
	 */
	public boolean doOperations() throws PrimitivesException, InterruptedException, BrokenBarrierException, PrivacyViolationException  {
		// check if necessary initializations were done correctly
		if((primitivesEnabledPeer == null) || (otherPeerID == null) ) {
			String errorMessage = "protocol instance not initialized with initializeProtocolPrimitives!";
			logger.log(Level.SEVERE, errorMessage);
			throw new PrimitivesException(errorMessage);
		}
		if(otherPeerIndex < 0) {
			String errorMessage = "protocol instance not initialized correctly: otherPeerInfo.getPeerIndex() = "+otherPeerIndex;
			logger.log(Level.SEVERE, errorMessage);
			throw new PrimitivesException(errorMessage);
		}
		if(primitives == null) {
			primitives = primitivesEnabledPeer.getPrimitives();
			if(primitives == null) {
				String errorMessage = "privacy peers protocol primitives instance is NULL!";
				logger.log(Level.SEVERE, errorMessage);
				throw new PrimitivesException(errorMessage);
			}
		}

		generateRandomNumbersIfNeeded();
		
		int currentOperationSetNumber = primitivesEnabledPeer.getCurrentOperationSetNumber();
		// get the ids of the operations that shall be done
		int[] ids = primitivesEnabledPeer.getOperationIDs();
		logger.info("thread " + Thread.currentThread().getId() + " is doing " + ids.length + " operations (operationSet=" + currentOperationSetNumber + ")");

		long start=0, stop=0;
		boolean amITakingTime = (primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await()==0); // choose one thread to keep the time
		
		if (amITakingTime) { 
			start = System.currentTimeMillis(); 
		}
		// process initial data
		primitives.processReceivedData();
		if (amITakingTime) { 
			stop = System.currentTimeMillis();
			long duration = stop-start;
			thisRoundComputationTime+=duration;
			totalComputationTime+=duration;
		}
		// send, receive and process data till operations are done
		int roundCounter = 1;
		while(!primitives.areOperationsCompleted()) {
			logger.info("thread " + Thread.currentThread().getId() + " is doing round " + roundCounter + " of operationSet=" + currentOperationSetNumber);
			if (amITakingTime) { 
				start = System.currentTimeMillis(); 
			}
			// data ready; send it; receive data from other peers
			sendReceiveOperationData(otherPeerIndex);
			if (amITakingTime) { 
				stop = System.currentTimeMillis();
				long duration = stop-start;
				thisRoundCommunicationTime+=duration;
				totalCommunicationTime+=duration;
			}
			
			if (amITakingTime) { 
				start = System.currentTimeMillis(); 
			}
			// process received data
			primitives.processReceivedData();
			if (amITakingTime) { 
				stop = System.currentTimeMillis();
				long duration = stop-start;
				thisRoundComputationTime+=duration;
				totalComputationTime+=duration;
			}
			roundCounter++;
		}
		// wait till all local threads completed the operation
		primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await();
		logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + " completed " + ids.length + " operations (operationSet=" + currentOperationSetNumber + ")");
		return true;
	}

	/**
	 * Checks whether currently scheduled operations require bitwise shared random numbers 
	 * and generates them in one batch, if needed.
	 * @throws PrimitivesException 
	 * @throws BrokenBarrierException 
	 * @throws InterruptedException 
	 * @throws PrivacyViolationException 
	 */
	private void generateRandomNumbersIfNeeded() throws PrimitivesException, InterruptedException, BrokenBarrierException, PrivacyViolationException {
		int randomNumbersNeeded=0;
		int bitsPerElement = primitives.getBitsCount();
		List<IOperation> ops = primitives.getOperations();
		for(int i=0; i<ops.size(); i++) {
			if (ops.get(i) instanceof LessThan) {
				randomNumbersNeeded += ((LessThan)ops.get(i)).getRandomNumbersNeeded(primitives);
			}
		}
			
		if (randomNumbersNeeded==0) {
			return; // No random numbers needed. Proceed as always.
		}
		
		if (primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await()==0) {
			logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + ": Automatically batch-generating "+randomNumbersNeeded+" bitwise-shared random numbers!");
			
			// Backup old operations and schedule random number generation
			primitives.pushOperations();
			primitives.initialize(1);
			primitives.batchGenerateBitwiseRandomNumbers(0, new long[]{randomNumbersNeeded});
		}
		primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await();
			
		// Perform random number generation
		doOperations();
		
		if (primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await()==0) {
			long[] preGeneratedRandomNumbers = primitives.getResult(0);
			
			// Restore old operations and add the random numbers
			int bitIndex=0;
			primitives.popOperations();
			List<IOperation> ppOps = primitives.getOperations();
			for(int op=0; op<ppOps.size(); op++) {
				if (ppOps.get(op) instanceof LessThan) {
					// set random bits
					LessThan lt = (LessThan)ppOps.get(op);
					int bitsNeeded = lt.getRandomNumbersNeeded(primitives)*bitsPerElement;
					long[] bits = new long[bitsNeeded];
					System.arraycopy(preGeneratedRandomNumbers, bitIndex, bits, 0, bitsNeeded);
					lt.setRandomNumberBitShares(bits);
					bitIndex += bitsNeeded;
				}
			}
		}
		primitivesEnabledPeer.getBarrierPP2PPProtocolThreads().await();
			
	}

	

	/**
	 * sends the data of the running operations and receives a message
	 *
	 * @param recipientPrivacyPeerIndex	peer index of recipient
	 * @throws PrimitivesException 
	 * @throws PrivacyViolationException 
	 */
	private void sendReceiveOperationData(int recipientPrivacyPeerIndex) throws PrimitivesException, PrivacyViolationException {
		messageToSend = new PrimitivesMessage(myPeerID, myPeerIndex);
		messageToSend.setOperationsData( primitives.getDataToSend(recipientPrivacyPeerIndex) );
		if(messageToSend.getOperationsData().length < 1) {
			String errorMessage = "no operations data to send for privacy peer "+recipientPrivacyPeerIndex+"!";
			throw new PrimitivesException(errorMessage);
		}

		logger.log(Level.INFO, "Thread "+Thread.currentThread().getId()+": Send/receive Shamir Sharing Protocol Primitives message...");
		if (PrivacyPeerConnectionManager.sendingFirst(myPeerID, otherPeerID)) {
			sendOperationData();
			receiveOperationData();
		} else {
			receiveOperationData();
			sendOperationData();
		}
	}


	/**
	 * Sends a Shamir Sharing Protocol Primitives message over the connection.
	 * @throws PrivacyViolationException 
	 */
	protected synchronized void sendOperationData() throws PrivacyViolationException {
		logger.log(Level.INFO, "Sending Shamir Sharing Protocol Primitives message (to " + otherPeerID + ")...");
		connectionManager.sendMessage(otherPeerID, SHAMIR_SHARING_PROTOCOL_PRIMITIVES_MESSAGE);
		connectionManager.sendMessage(otherPeerID, messageToSend);
	}

	/**
	 * Receives a Shamir Sharing Protocol Primitives message over the connection.
	 * (the received message is stored in the messageReceived variable)
	 * @throws PrivacyViolationException 
	 */
	protected synchronized void receiveOperationData() throws PrivacyViolationException {
		logger.log(Level.INFO, "Waiting for Shamir Sharing Protocol Primitives message to arrive (from " + otherPeerID + ")...");
		String messageType = (String)connectionManager.receiveMessage(otherPeerID);
		messageReceived = (PrimitivesMessage)connectionManager.receiveMessage(otherPeerID);
		
		/* 
		 * Here we need to deal with the possibility of a crashed remote privacy peer. In case
		 * the remote privacy peer is offline, we get <code>null</code> messages. 
		 */
		if (messageType==null || messageReceived==null) {
			// The other guy is down. Generate a dummy message in order not to stop the protocol execution.
			messageType = SHAMIR_SHARING_PROTOCOL_PRIMITIVES_MESSAGE;
			messageReceived = new PrimitivesMessage(otherPeerID, otherPeerIndex);
			messageReceived.setIsDummyMessage(true);
			logger.warning("Received empty message from "+otherPeerID+". Using a DUMMY message instead.");
		}
		
		if (messageType.equals(SHAMIR_SHARING_PROTOCOL_PRIMITIVES_MESSAGE)) {
			logger.info("Received " + messageType + " message from "+otherPeerID);
			
			if(messageReceived!=null) {
				primitivesEnabledPeer.processShamirSharingProtocolPrimitivesMessage(messageReceived);
			}
		} else {
			logger.log(Level.WARNING, "Received unexpected message type (expected: " + SHAMIR_SHARING_PROTOCOL_PRIMITIVES_MESSAGE + ", received: " + messageType);
		}
	}
	
    /**
     * Resets the current round statistics.
     */
    public static void newStatisticsRound() {
    	thisRoundComputationTime=0; 
    	thisRoundCommunicationTime=0;
    	numberOfFinishedRounds++;
    }

    /**
     * Returns a String with running time statistics.
     * @return the statistics
     */
    public static String getStatistics() {
    	return "PrimitivesEnabledProtocol statistics [seconds]: \n" +
    		   "--- Total      : Computation: "+totalComputationTime/1000.0+ ", Communication: "+totalCommunicationTime/1000.0+"\n" +
    		   "--- This round : Computation: "+thisRoundComputationTime/1000.0+ ", Communication: "+thisRoundCommunicationTime/1000.0+"\n" + 
    		   "--- Avg. round : Computation: "+(totalComputationTime/1000.0)/(numberOfFinishedRounds+1)+",  Communication: "+(totalCommunicationTime/1000.0)/(numberOfFinishedRounds+1)+" ("+100*((double)totalCommunicationTime)/(totalCommunicationTime+totalComputationTime) +"%) \n";
    }

}
