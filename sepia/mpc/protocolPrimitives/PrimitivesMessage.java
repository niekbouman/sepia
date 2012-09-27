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

import java.io.Serializable;

import mpc.MessageBase;

/**
 * Message used to exchange data among privacy peers of the operations from the
 * MpcShamirSharingProtocolPrimitives class.
 *
 * @author Dilip Many
 *
 */
public class PrimitivesMessage extends MessageBase implements Serializable  {
	private static final long serialVersionUID = -4839969958768481782L;

	/** contains the data for the operations from MpcShamirSharingProtocolPrimitives */
	long[] operationsData = null;



	/**
	 * creates a new Shamir Sharing Protocol Primitives message
	 *
	 * @param senderID		the ID of the sender
	 * @param senderIndex	the index of the sender
	 */
	public PrimitivesMessage(String senderID, int senderIndex) {
		super(senderID, senderIndex);
	}



	/**
	 * returns the data for the operations from MpcShamirSharingProtocolPrimitives
	 *
	 * @return	the data for the operations
	 */
	public long[] getOperationsData() {
		return operationsData;
	}


	/**
	 * sets the data for the operations from MpcShamirSharingProtocolPrimitives
	 *
	 * @param operationsData	the data for the operations
	 */
	public void setOperationsData(long[] operationsData) {
		this.operationsData = operationsData;
	}
}
