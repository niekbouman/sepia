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
 * Small Interval Test Operation.
 * 
 * To compute if x is in [l,u] this operation first constructs a polynomial
 * 
 * <p/>
 * y(x) = (x-l)*(x-(l+1))*(x-(l+2))*...*(x-(u-1))*(x-u).
 * <p/>
 * it then evaluates if y(x)==0 using the {@link Equal} operation (then x lies in the interval).
 *
 * @author Dilip Many
 *
 */
public class SmallIntervalTest extends GenericOperationState implements IOperation {
	/**
	 * creates a small interval test sub-operation.
	 *
	 * @param data	array containing the share, the public lower bound and
	 *				the upper bound: [x, l, u]
	 */
	public SmallIntervalTest(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the small interval test operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		// step1: create (x-a) factors of polynomial and compute product
		if(getCurrentStep() == 1) {
			long secretShare = getInitialData()[0];
			long lowerBound = getInitialData()[1];
			long upperBound = getInitialData()[2];
			if(lowerBound == upperBound) {
				IOperation[] newSubOperations = new IOperation[1];
				long[] data = new long[2];
				data[0] = lowerBound;
				data[1] = secretShare;
				newSubOperations[0] = new Equal(data);
				newSubOperations[0].doStep(primitives);
				setSubOperations(newSubOperations);
				incrementCurrentStep();
				incrementCurrentStep();
				return;
			}
			IOperation[] subOperations = new IOperation[1];
			long[] data = new long[(int) (upperBound-lowerBound+1)];
			for(int i = 0; i < data.length; i++) {
				data[i] = primitives.getMpcShamirSharing().modSubtract(secretShare, lowerBound+i);
			}
			subOperations[0] = new Product(data);
			subOperations[0].doStep(primitives);
			setSubOperations(subOperations);
			incrementCurrentStep();
			return;
		}

		// step2: complete computing product
		if(getCurrentStep() == 2) {
			if(!getSubOperations()[0].isOperationCompleted()) {
				getSubOperations()[0].doStep(primitives);
			}
			if(getSubOperations()[0].isOperationCompleted()) {
				// start equal sub-operation
				IOperation[] newSubOperations = new IOperation[1];
				long[] data = new long[2];
				data[0] = 0;
				data[1] = getSubOperations()[0].getFinalResult()[0];
				newSubOperations[0] = new Equal(data);
				newSubOperations[0].doStep(primitives);
				setSubOperations(newSubOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step3: complete running equal operation and set final result
		if(getCurrentStep() == 3) {
			if(!getSubOperations()[0].isOperationCompleted()) {
				getSubOperations()[0].doStep(primitives);
			}
			if(getSubOperations()[0].isOperationCompleted()) {
				setFinalResult( getSubOperations()[0].getFinalResult() );
				incrementCurrentStep();
			}
		}

		// step4: operation completed

		return;
	}
}
