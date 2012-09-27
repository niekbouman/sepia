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
 * Linear Prefix-Or Operation.
 * This operation computes the Prefix-OR from [Nishide2007] in a linear fashion:
 * It first computes the OR of the first two bits, then it computes the
 * OR of the first two and the third bit, then of the first three and
 * the fourth, ...
 * <ul>
 * <li>z0 = x0</li>
 * <li>z1 = z0 OR x1</li>
 * <li>z2 = z1 OR x2</li>
 * <li>z3 = z2 OR x3</li>
 * <li>...</li>
 *</ul>
 *
 * The OR of a and b is computed as a+b-a*b.
 * 
 *
 * @author Dilip Many
 *
 */
public class LinearPrefixOr extends GenericOperationState implements IOperation {
	/**
	 * creates a linear prefix-or sub-operation.
	 *
	 * @param data	the bit shares of the value of which to compute the prefix-or
	 */
	public LinearPrefixOr(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the linear prefix-or operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		/*
		 * The intermediaryResult is used to store the bits of the Prefix-OR.
		 */

		long[] initialData = getInitialData();

		// step1: start OR computation of first two bits
		if(getCurrentStep() == 1) {
			// first bit of prefix-or is equal to first input bit
			long[] intermediaryResult = new long[initialData.length];
			intermediaryResult[0] = initialData[0];
			setIntermediaryResult(intermediaryResult);
			if(initialData.length > 1) {
				// compute OR of first two bit shares
				IOperation[] subOperations = new IOperation[1];
				long[] data = new long[2];
				data[0] = intermediaryResult[0];
				data[1] = initialData[1];
				subOperations[0] = new Multiplication(data);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
			} else {
				// if the input is only one bit share that is the final result as well
				setFinalResult( intermediaryResult );
			}
			incrementCurrentStep();
			return;
		}

		// step2-X: finish previous OR computation and start next one if necessary
		if((1 < getCurrentStep()) && (getCurrentStep() <= initialData.length)) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				long[] intermediaryResult = getIntermediaryResult();
				// compute the OR
				intermediaryResult[ (getCurrentStep()-1) ] = primitives.getMpcShamirSharing().modSubtract( primitives.getMpcShamirSharing().modAdd(intermediaryResult[(getCurrentStep()-2)], initialData[(getCurrentStep()-1)]), getSubOperations()[0].getFinalResult()[0]);
				// start next OR computation if necessary
				if(getCurrentStep() < initialData.length) {
					long[] data = new long[2];
					data[0] = intermediaryResult[ (getCurrentStep()-1) ];
					data[1] = initialData[getCurrentStep()];
					IOperation[] subOperations = new IOperation[1];
					subOperations[0] = new Multiplication(data);
					subOperations[0].doStep(primitives);
					setSubOperations(subOperations);
				} else {
					// the last OR computation was completed, store final result
					setFinalResult( intermediaryResult );
				}
				incrementCurrentStep();
			}
			return;
		}

		return;
	}
}
