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

package mpc.protocolPrimitives.operations;


import mpc.protocolPrimitives.Primitives;
import mpc.protocolPrimitives.PrimitivesException;


/**
 * This interface defines the functions that operations of the
 * {@link test.protocolPrimitives.operations} package have to implement.
 * <p>
 * Some of the functions might be implemented in the abstract operation state classes in
 * {@link mpc.protocolPrimitives.operationStates} from which then the concrete operation classes
 * can inherit.
 *
 * @author Dilip Many
 *
 */
public interface IOperation {
	/**
	 * executes the next step of the operation
	 *
	 * (make sure that all shares from all other privacy peers were set before calling this function!)
	 * @throws PrimitivesException
	 */
	public void doStep(final Primitives primitives) throws PrimitivesException;


	/**
	 * copies the shares for the privacy peer into the specified array beginning
	 * at the specified position
	 *
	 * @param privacyPeerIndex	index of the privacy peer for which the shares shall be returned
	 * @param data				the array to copy the shares to
	 * @param position			the position from which on to copy the shares
	 * @return					the next free position in the array
	 */
	public int copySharesForPrivacyPeer(int privacyPeerIndex, long[] data, int position);


	/**
	 * sets the shares received from the privacy peer (specified by its index)
	 *
	 * @param privacyPeerIndex	the index of the privacy peer for which to set the shares
	 * @param data			the shares to set. In case the privacy peer is offline, set data to <code>null</code>.
	 * @param nextSlot		the next slot
	 */
	public int copySharesFromPrivacyPeer(int privacyPeerIndex, long[] data, int nextSlot);


	/**
	 * returns the number of the shares to send to a privacy peer
	 * (from this operation and sub-operations)
	 *
	 * @return	the number of the shares to send to a privacy peer
	 */
	public int getSharesForPrivacyPeerCount();


	/**
	 * indicates if the operation is completed
	 *
	 * @return	true if operation is completed
	 */
	public boolean isOperationCompleted();


	/**
	 * retrieves the final result
	 *
	 * @return	the final result
	 */
	public long[] getFinalResult();
}
