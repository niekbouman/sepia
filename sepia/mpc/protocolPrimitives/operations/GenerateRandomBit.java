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
 * Generate Random Bit Operation.
 * This operation first generates a shared random number r. Then it multiplies
 * the value with itself and reconstructs r^2. If r^2!=0 it then computes the
 * random bit from r^2 and the share of the random value.
 * 
 * See [Nishide2007].
 *
 * @author Dilip Many
 *
 */
public class GenerateRandomBit extends GenericOperationState implements IOperation {
	/**
	 * creates a random bit generation sub-operation
	 *
	 * @param data	Ignored. This operation <i>generates</i> data and needs no input.
	 */
	public GenerateRandomBit(long[] data) {
		return;
	}


	/**
	 * do the next step of the random bit generation operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		// step1: start generating initial random number
		if(getCurrentStep() == 1) {
			IOperation[] subOperations = new IOperation[1];
			subOperations[0] = new GenerateRandomNumber(null);
			subOperations[0].doStep(primitives);
			setSubOperations(subOperations);
			incrementCurrentStep();
			return;
		}

		// step2: finish random number generation and start squaring
		if(getCurrentStep() == 2) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				long[] intermediaryResult = new long[1];
				intermediaryResult[0] = getSubOperations()[0].getFinalResult()[0];
				setIntermediaryResult(intermediaryResult);
				long[] data = new long[2];
				data[0] = intermediaryResult[0];
				data[1] = intermediaryResult[0];
				IOperation[] subOperations = new IOperation[1];
				subOperations[0] = new Multiplication(data);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step3: finish squaring and start reconstruction
		if(getCurrentStep() == 3) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				long[] data = new long[1];
				data[0] = getSubOperations()[0].getFinalResult()[0];
				IOperation[] subOperations = new IOperation[1];
				subOperations[0] = new Reconstruction(data);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		//step4: finish reconstruction and try to compute random bit share
		if(getCurrentStep() == 4) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				long rSquared = getSubOperations()[0].getFinalResult()[0];
				long[] result = new long[1];
				if(rSquared != 0) {
					long rRoot = primitives.getMpcShamirSharing().modSqrt(rSquared);
					// make sure that the root is from the lower half of the field
					if(rRoot > (primitives.getFieldSize()/2)) {
						rRoot = primitives.getFieldSize()-rRoot;	// (rRoot<fieldSize therefore the normal subtraction can be used)
					}
					// compute final random bit share
					result[0] = primitives.getMpcShamirSharing().modMultiply( primitives.getMpcShamirSharing().inverse(2), primitives.getMpcShamirSharing().modAdd( primitives.getMpcShamirSharing().modMultiply(primitives.getMpcShamirSharing().inverse(rRoot), getIntermediaryResult()[0]), 1) );
				} else {
					// can't compute random bit share from given random number share
					// operation failed...
					result[0] = -1;
				}
				setFinalResult(result);
				incrementCurrentStep();
			}
			return;
		}

		// step5: operation completed

		return;
	}
}
