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
import mpc.protocolPrimitives.operationStates.GenericOperationState;


/**
 * ArrayMultiplication. This operation multiplies in two arrays in parallel.
 * E.g. if A = [1,2,3,4,5] and B = [5,4,3,2,1]
 * arraymult( a,b ) =  [1*5, 2*4, 3*3, 4*2, 5*1]
 * the two arrays must have equal length.
 *
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class ArrayMultiplication extends GenericOperationState implements IOperation {
	
	/** needed to hold temporary values */
	private long[] temp = null;

	/**
	 * creates an ArrayMultiplication sub-operation.
	 *
	 * @param data	array containing the 2 shares to multiply
	 */
	public ArrayMultiplication(long[] factor1, long[] factor2) {
		// store initial arguments
		setInitialData(factor1);
		setIntermediaryResult(factor2);
		// compute product
	
		return;
	}

	
	@Override
	public void doStep(Primitives primitives) throws PrimitivesException {
		// step1: multiply shares and generate truncation shares
		if(getCurrentStep() == 1 ){
			long[][] sharesForPPs = new long[primitives.getNumberOfPrivacyPeers()][getInitialData().length];
			temp = new long[primitives.getNumberOfPrivacyPeers()];
			for(int i = 0; i < getInitialData().length; i++){
				temp = primitives.getMpcShamirSharing().generateShare( primitives.getMpcShamirSharing().modMultiply(
					 getInitialData()[i], getIntermediaryResult()[i] ) );
				for(int j = 0; j < temp.length; j++){
					sharesForPPs[j][i] = temp[j];
				}
			}
			setSharesForPrivacyPeers(sharesForPPs);
			copyOwnShares(primitives.getMyPrivacyPeerIndex());
			incrementCurrentStep();
			return;
		}
		
		// step2: interpolate new shares from received shares
		if(getCurrentStep() == 2){
			long[] result = new long[ getSharesFromPrivacyPeers()[0].length ];
			for(int i = 0; i < getSharesFromPrivacyPeers()[0].length; i++){
				for(int j = 0; j < temp.length; j++){
					temp[j] = getSharesFromPrivacyPeers()[j][i];
				}
				result[i] = primitives.getMpcShamirSharing().interpolate( temp, true );
			}
			setFinalResult(result);
			return;
		}
		
		return;
	}

}
