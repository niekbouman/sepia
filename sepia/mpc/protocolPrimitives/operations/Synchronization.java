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

import mpc.ShamirSharing;
import mpc.protocolPrimitives.Primitives;
import mpc.protocolPrimitives.PrimitivesException;
import mpc.protocolPrimitives.operationStates.GenericOperationState;

/**
 * This operation allows to synchronize an array of {0,1} values. For each
 * position, the logical AND of the values from all privacy peers is computed.
 * This can, e.g., be used to synchronize the set of responsive input peers
 * among the privacy peers.
 * 
 * @author Martin Burkhart
 */
public class Synchronization extends GenericOperationState {

	/**
	 * Creates a synchronization operation.
	 * 
	 * @param data
	 *            an array of {0,1} values. Each position indicates the local
	 *            presence of a certain element.
	 */
	public Synchronization(long[] data) {
		// store initial arguments and create initial intermediary result
		setInitialData(data);
		return;
	}

	/**
	 * Performs the next step of the synchronization operation.
	 */
	@Override
	public void doStep(Primitives primitives) throws PrimitivesException {
		// Step 1: Send the local data array to all privacy peers
		if (getCurrentStep() == 1) {
			long[] data = getInitialData();
			long[][] shares = new long[primitives.getNumberOfPrivacyPeers()][data.length];

			for (int i = 0; i < primitives.getNumberOfPrivacyPeers(); i++) {
				shares[i] = data;
			}

			// The wording here is a bit confusing. These data are not really
			// shares of a secret!
			setSharesForPrivacyPeers(shares);
			copyOwnShares(primitives.getMyPrivacyPeerIndex());
			incrementCurrentStep();
			return;
		}

		// Step 2: Compute the logical AND on each position
		if (getCurrentStep() == 2) {
			long[] aggregateData = new long[getInitialData().length];
			// Initially, all elements are set to 1
			for (int i = 0; i < aggregateData.length; i++) {
				aggregateData[i] = 1;
			}

			long[][] shares = getSharesFromPrivacyPeers();
			for (int ppIndex = 0; ppIndex < primitives.getNumberOfPrivacyPeers(); ppIndex++) {
				long[] currentData = shares[ppIndex];

				// Do the logical AND of the aggregate with the current data
				for (int i = 0; i < aggregateData.length; i++) {
					if (currentData[i] != ShamirSharing.MISSING_SHARE) {
						aggregateData[i] &= currentData[i];
					}
				}

			}
			setFinalResult(aggregateData);
			incrementCurrentStep();
		}

		// Step 3: done

	}

}
