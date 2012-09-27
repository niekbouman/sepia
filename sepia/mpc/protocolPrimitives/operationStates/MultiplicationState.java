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
 * class to store the state information of a multiplication operation
 * <p>
 * The number of variables used by this class was reduced as much as possible.
 * To achieve that many impure tricks were applied that work in conjunction with
 * the implementation of the multiplication operation. But due to that, this
 * operation state is (in general) not usable by other operations.
 *
 * @author Dilip Many
 *
 */
public abstract class MultiplicationState implements IOperation {

	/** the shares received from the privacy peers; format: [privacyPeerIndex] */
	private long[] sharesFromPrivacyPeers = null;
	/** the shares for the privacy peers (to be sent); format: [privacyPeerIndex] */
	private long[] sharesForPrivacyPeers = null;



	/**
	 * creates a new operation state object
	 */
	protected MultiplicationState() {
	}


	/**
	 * ASSUMPTIONS made by operation state:
	 * - there are at least 2 privacy peers
	 * - the final result is a long array containing 1 value
	 * - the operation sends/receives shares once
	 * - the operation has only 2 (computation) steps
	 * - until the shares for the privacy peers are set, the operation is in step 1
	 * - after the shares from the privacy peers were set the operation is in step 2
	 * - after the final result was set the operation is completed (in step 3)
	 *
	 *
	 * Implementation:
	 *
	 * sharesForPrivacyPeers:
	 * The sharesForPrivacyPeers is initially used to hold the initial data and after
	 * completing the first step holds the shares for the privacy peers.
	 *
	 * sharesFromPrivacyPeers:
	 * After setting the shares for the privacy peers this array is used to hold the
	 * shares received from the privacy peers until the final result is set. When the
	 * final result is set it will be stored in this array.
	 */


	/**
	 * returns the current step (number)
	 *
	 * @return	the current step (number)
	 */
	protected final int getCurrentStep() {
		if(sharesFromPrivacyPeers == null) {
			// sharesFromPrivacyPeers were not set yet, so we are still in step 1
			return 1;
		}
		if(sharesFromPrivacyPeers.length > 1) {
			// sharesFromPrivacyPeers were set, so we are in step 2
			return 2;
		}
		if(isOperationCompleted()) {
			return 3;
		}
		// state unrecognized; return step 0
		return 0;
	}


	/**
	 * returns the initial data (arguments)
	 *
	 * @return	the initial data (arguments)
	 */
	protected final long[] getInitialData() {
		return sharesForPrivacyPeers;
	}


	/**
	 * stores the the initial data (arguments)
	 *
	 * @param initialData	the initial data (arguments) to store
	 */
	protected final void setInitialData(long[] initialData) {
		sharesForPrivacyPeers = initialData;
	}


	/**
	 * sets the shares for the privacy peers
	 *
	 * @param sharesForPrivacyPeers	the shares for the privacy peers
	 */
	protected final void setSharesForPrivacyPeers(long[] sharesForPrivacyPeers) {
		this.sharesForPrivacyPeers = sharesForPrivacyPeers;
		// initialize sharesFromPrivacyPeers to the same size
		// as we will receive as much data as we send
		sharesFromPrivacyPeers = new long[sharesForPrivacyPeers.length];
	}


	/**
	 * copy my own shares (I wont send them "normally" to myself)
	 *
	 * @param myPrivacyPeerIndex
	 */
	protected final void copyOwnShares(int myPrivacyPeerIndex) {
		sharesFromPrivacyPeers[myPrivacyPeerIndex] = sharesForPrivacyPeers[myPrivacyPeerIndex];
	}


	/**
	 * copies the shares for the privacy peer into the specified array beginning
	 * at the specified position
	 *
	 * @param privacyPeerIndex	index of the privacy peer for which the shares shall be returned
	 * @param data		the array to copy the shares to
	 * @param position	the position from which on to copy the shares
	 * @return			the next free position in the array
	 */
	public final int copySharesForPrivacyPeer(int privacyPeerIndex, long[] data, int position) {
		int nextSlot = position;
		// add own shares
		if(sharesForPrivacyPeers != null) {
			data[position] = sharesForPrivacyPeers[privacyPeerIndex];
			nextSlot += 1;
		}
		return nextSlot;
	}


	/**
	 * returns the shares from the privacy peers
	 *
	 * @return	the shares from the privacy peers; format: [privacyPeerIndex]
	 */
	protected final long[] getSharesFromPrivacyPeers() {
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
	public final int copySharesFromPrivacyPeer(int privacyPeerIndex, long[] data, int position) {
		// get own shares
		if(sharesFromPrivacyPeers != null) {
			sharesFromPrivacyPeers[privacyPeerIndex] = (data != null) ? data[position] : ShamirSharing.MISSING_SHARE;
			position += 1;
		}

		return position;
	}


	/**
	 * returns the number of the shares to send to a privacy peer
	 *
	 * @return	the number of the shares to send to a privacy peer
	 */
	public final int getSharesForPrivacyPeerCount() {
		return 1;
	}


	/**
	 * sets the final result of the operation (for later pick up)
	 * (automatically sets the operation as completed too)
	 *
	 * @param finalResult	the final result to set
	 */
	protected final void setFinalResult(long[] finalResult) {
		sharesFromPrivacyPeers = finalResult;
	}


	/**
	 * indicates if the operation is completed
	 *
	 * @return	true if operation is completed
	 */
	public final boolean isOperationCompleted() {
		if(sharesFromPrivacyPeers != null) {
			if(sharesFromPrivacyPeers.length == 1) {
				return true;
			}
		}
		return false;
	}


	/**
	 * retrieves the final result
	 *
	 * @return	the final result
	 */
	public final long[] getFinalResult() {
		return sharesFromPrivacyPeers;
	}
}
