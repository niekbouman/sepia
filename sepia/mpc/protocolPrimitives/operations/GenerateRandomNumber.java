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
import mpc.protocolPrimitives.operationStates.MultiplicationState;


/**
 * In this operation every privacy peer generates a random value (within the field)
 * and creates and distributes the shares of the value. Then the privacy peers
 * just add up all the shares they received to get a share of the shared random
 * number.
 *
 * @author Dilip Many
 *
 */
public class GenerateRandomNumber extends MultiplicationState implements IOperation {
	/**
	 * creates a random number generation sub-operation.
	 *
	 * @param data	Ignored. This operation <i>generates</i> data and needs no input.
	 */
	public GenerateRandomNumber(long[] data) {
		return;
	}


	/**
	 * do the next step of the random number generation operation
	 *
	 * @param primitives the protocol primitives.
	 */
	public void doStep(Primitives primitives) {
		// step1: generate initial random number share and shares of it
		if(getCurrentStep() == 1) {
			setSharesForPrivacyPeers( primitives.getMpcShamirSharing().generateShare( primitives.getMpcShamirSharing().mod( primitives.getRandom().nextLong() ) ) );
			copyOwnShares(primitives.getMyPrivacyPeerIndex());
			return;
		}

		// step2: add up received shares
		if(getCurrentStep() == 2) {
			long[] data = getSharesFromPrivacyPeers();
			long[] result = new long[1];
			result[0] = 0;
			for(int i = 0; i < data.length; i++) {
				result[0] = primitives.getMpcShamirSharing().modAdd(result[0], data[i]);
			}
			setFinalResult(result);
			return;
		}

		// step3: operation completed

		return;
	}
}
