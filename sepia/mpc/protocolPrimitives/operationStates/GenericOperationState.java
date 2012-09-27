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

package mpc.protocolPrimitives.operationStates;

import mpc.ShamirSharing;
import mpc.protocolPrimitives.operations.IOperation;


/**
 * Generic class to store the state information of an operation.
 * <p>
 * Has a step counter, an array for the initial data, intermediary
 * results, the final result and sub-operations.
 * Has 2d-arrays for the shares to send and receive.
 *
 * @author Dilip Many
 *
 */
public abstract class GenericOperationState implements IOperation {

	/** the current step that is being done */
	private int currentStep = 0;
	/** holds the initial arguments/data of the operation */
	private long[] initialData = null;
	/** holds some intermediary result of the operation */
	private long[] intermediaryResult = null;
	/** the shares received from the privacy peers; format: [privacyPeerIndex][shareIndex] */
	private long[][] sharesFromPrivacyPeers = null;
	/** the shares for the privacy peers (to be sent); format: [privacyPeerIndex][shareIndex] */
	private long[][] sharesForPrivacyPeers = null;
	/** the final results of the operation */
	private long[] finalResult = null;

	/** holds the sub-operations called by this one (null if none) */
	private IOperation[] subOperations = null;



	/**
	 * creates a new operation state object
	 */
	protected GenericOperationState() {
		currentStep = 1;
	}



	/**
	 * returns the current step (number), default value after object creation is 1
	 * 
	 * @return	the current step (number)
	 */
	protected int getCurrentStep() {
		return currentStep;
	}

	/**
	 * sets the current step to the number specified
	 * @param n step number to set
	 */
	protected void setCurrentStep(int n){
		currentStep = n;
	}

	/**
	 * increments the step number by one
	 */
	protected void incrementCurrentStep() {
		this.currentStep++;
	}


	/**
	 * returns the initial data (arguments)
	 *
	 * @return	the initial data (arguments)
	 */
	protected long[] getInitialData() {
		return initialData;
	}


	/**
	 * stores the the initial data (arguments)
	 *
	 * @param initialData	the initial data (arguments) to store
	 */
	protected void setInitialData(long[] initialData) {
		this.initialData = initialData;
	}


	/**
	 * returns the intermediary result
	 *
	 * @return	the intermediary result
	 */
	protected long[] getIntermediaryResult() {
		return intermediaryResult;
	}


	/**
	 * sets the intermediary result
	 *
	 * @param intermediaryResult	the intermediary result to set
	 */
	protected void setIntermediaryResult(long[] intermediaryResult) {
		this.intermediaryResult = intermediaryResult;
	}


	/**
	 * sets the shares for the privacy peers
	 *
	 * @param sharesForPrivacyPeers		the shares for the privacy peers
	 */
	protected void setSharesForPrivacyPeers(long[][] sharesForPrivacyPeers) {
		this.sharesForPrivacyPeers = sharesForPrivacyPeers;
		// initialize sharesFromPrivacyPeers to the same size
		// as we will receive as much data as we send
		sharesFromPrivacyPeers = new long[sharesForPrivacyPeers.length][sharesForPrivacyPeers[0].length];
	}


	/**
	 * copy my own shares (I wont send them "normally" to myself)
	 *
	 * @param myPrivacyPeerIndex
	 */
	protected void copyOwnShares(int myPrivacyPeerIndex) {
		sharesFromPrivacyPeers[myPrivacyPeerIndex] = sharesForPrivacyPeers[myPrivacyPeerIndex];
	}


	/**
	 * copies the shares for the privacy peer into the specified array beginning
	 * at the specified position
	 *
	 * @param privacyPeerIndex	index of the privacy peer for which the shares shall be returned
	 * @param data				the array to copy the shares to
	 * @param position			the position from which on to copy the shares
	 * @return					the next free position in the array
	 */
	public int copySharesForPrivacyPeer(int privacyPeerIndex, long[] data, int position) {
		int nextSlot = position;
		// add own shares
		if(sharesForPrivacyPeers != null) {
			System.arraycopy(sharesForPrivacyPeers[privacyPeerIndex], 0, data, nextSlot, sharesForPrivacyPeers[privacyPeerIndex].length);
			nextSlot += sharesForPrivacyPeers[privacyPeerIndex].length;
		}
		// add shares from (not completed) sub-operations
		if(subOperations != null) {
			for(int subOperationIndex = 0; subOperationIndex < subOperations.length; subOperationIndex++) {
				if(subOperations[subOperationIndex] != null) {
					if(!subOperations[subOperationIndex].isOperationCompleted()) {
						nextSlot = subOperations[subOperationIndex].copySharesForPrivacyPeer(privacyPeerIndex, data, nextSlot);
					}
				}
			}
		}
		return nextSlot;
	}


	/**
	 * returns the shares from the privacy peers
	 *
	 * @return	the shares from the privacy peers; format: [privacyPeerIndex][shareIndex]
	 */
	protected long[][] getSharesFromPrivacyPeers() {
		return sharesFromPrivacyPeers;
	}


	/**
	 * sets the shares received from the privacy peer (specified by its index)
	 *
	 * @param privacyPeerIndex	the index of the privacy peer for which to set the shares
	 * @param data				the array to copy the shares from
	 * @param position			the position from which on to copy the shares
	 * @return					the next unused position in the array
	 */
	public int copySharesFromPrivacyPeer(int privacyPeerIndex, long[] data, int position) {
		// get own shares
		if(sharesFromPrivacyPeers != null) {
			if (data!=null) {
				System.arraycopy(data, position, sharesFromPrivacyPeers[privacyPeerIndex], 0, sharesFromPrivacyPeers[privacyPeerIndex].length);
			} else {
				for(int i=0; i<sharesFromPrivacyPeers[privacyPeerIndex].length; i++) {
					sharesFromPrivacyPeers[privacyPeerIndex][i] = ShamirSharing.MISSING_SHARE;
				}
			}
			position += sharesFromPrivacyPeers[privacyPeerIndex].length;
		}
		// let (not completed) sub-operations get their shares
		if(subOperations != null) {
			for(int subOperationIndex = 0; subOperationIndex < subOperations.length; subOperationIndex++) {
				if(subOperations[subOperationIndex] != null) {
					if(!subOperations[subOperationIndex].isOperationCompleted()) {
						position = subOperations[subOperationIndex].copySharesFromPrivacyPeer(privacyPeerIndex, data, position);
					}
				}
			}
		}
		return position;
	}


	/**
	 * returns the number of the shares to send to a privacy peer
	 * (from this operation and sub-operations)
	 *
	 * @return	the number of the shares to send to a privacy peer
	 */
	public int getSharesForPrivacyPeerCount() {
		int count = 0;
		if(sharesForPrivacyPeers != null) {
			count += sharesForPrivacyPeers[0].length;
		}
		if(subOperations != null) {
			for(int subOperationIndex = 0; subOperationIndex < subOperations.length; subOperationIndex++) {
				if(subOperations[subOperationIndex] != null) {
					if(!subOperations[subOperationIndex].isOperationCompleted()) {
						count += subOperations[subOperationIndex].getSharesForPrivacyPeerCount();
					}
				}
			}
		}
		return count;
	}


	/**
	 * sets the final result of the operation (for later pick up)
	 * (automatically sets the operation as completed too)
	 *
	 * @param finalResult	the final result to set
	 */
	protected void setFinalResult(long[] finalResult) {
		this.finalResult = finalResult;
	}


	/**
	 * indicates if the operation is completed
	 *
	 * @return	true if operation is completed
	 */
	public boolean isOperationCompleted() {
		if(finalResult == null) {
			return false;
		} else {
			return true;
		}
	}


	/**
	 * retrieves the final result
	 *
	 * @return	the final result
	 */
	public long[] getFinalResult() {
		return finalResult;
	}



	/**
	 * returns the sub-operations
	 *
	 * @return	the sub-operations
	 */
	public IOperation[] getSubOperations() {
		return subOperations;
	}


	/**
	 * sets the sub-operations
	 *
	 * @param subOperations	the sub-operations to set
	 */
	protected void setSubOperations(IOperation[] subOperations) {
		this.subOperations = subOperations;
	}
}
