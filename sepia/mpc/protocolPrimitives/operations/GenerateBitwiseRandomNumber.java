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
 * Generate Bitwise (Shared) Random Number Operation.
 *
 * This operation first generates bitsCount many shared random bits. If all
 * bits were created successfully it checks (using the bitwise less-than) if
 * the number constructed from these bits is smaller than the fieldSize, e.g.
 * a valid field element. If it is valid the bit shares are returned.
 * 
 * See [Nishide2007].
 *
 * @author Dilip Many
 *
 */
public class GenerateBitwiseRandomNumber extends GenericOperationState implements IOperation {
	/**
	 * creates a bitwise (shared) random number generation sub-operation
	 *
	 * @param data	null or the random bit shares that shall be used to compute the
	 *				bitwise shared random number
	 */
	public GenerateBitwiseRandomNumber(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the bitwise (shared) random number generation operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		// step1: start generating initial random bits
		if(getCurrentStep() == 1) {
			if(getInitialData() == null) {
				IOperation[] subOperations = new IOperation[primitives.getBitsCount()];
				for(int i = 0; i < primitives.getBitsCount(); i++) {
					subOperations[i] = new GenerateRandomBit(null);
					subOperations[i].doStep(primitives);
				}
				setSubOperations(subOperations);
				incrementCurrentStep();
				return;
			} else {
				// use specified bits
				setIntermediaryResult(getInitialData());
				incrementCurrentStep();
				// (don't return; immediately go to next step)
			}
		}

		// step2: finish random bits generation and start bitwise less-than
		if(getCurrentStep() == 2) {
			if(getSubOperations() != null) {
				for(int i = 0; i < primitives.getBitsCount(); i++) {
					getSubOperations()[i].doStep(primitives);
				}
				if(getSubOperations()[0].isOperationCompleted()) {
					long[] intermediaryResult = new long[primitives.getBitsCount()];
					for(int i = 0; i < primitives.getBitsCount(); i++) {
						intermediaryResult[i] = getSubOperations()[i].getFinalResult()[0];
						if(intermediaryResult[i] == -1L) {
							// random bit generation failed
							long[] finalResult = new long[1];
							finalResult[0] = -1;
							setFinalResult(finalResult);
							return;
						}
					}
					setIntermediaryResult(intermediaryResult);
				}
			}
			if(getIntermediaryResult() != null) {
				long[] data = new long[1+2*primitives.getBitsCount()];
				data[0] = 2;
				System.arraycopy(getIntermediaryResult(), 0, data, 1, primitives.getBitsCount());
				System.arraycopy(primitives.getBits(primitives.getFieldSize()), 0, data, primitives.getBitsCount()+1, primitives.getBitsCount());
				IOperation[] subOperations = new IOperation[1];
				subOperations[0] = new BitwiseLessThan(data);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step3: finish bitwise less-than and start result reconstruction
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

		// step4: finish bitwise less-than result reconstruction and set final result
		if(getCurrentStep() == 4) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				if(getSubOperations()[0].getFinalResult()[0] == 1L) {
					setFinalResult( getIntermediaryResult() );
				} else {
					long[] finalResult = new long[1];
					finalResult[0] = -1;
					setFinalResult(finalResult);
					return;
				}
				incrementCurrentStep();
			}
			return;
		}

		// step5: operation completed

		return;
	}
}
