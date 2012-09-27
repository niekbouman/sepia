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
 * Least Significant Bit Operation. Computes least-significant bit of a shared secret.
 * 
 * See [Nishide2007].
 *
 * @author Dilip Many
 *
 */
public class LeastSignificantBit extends GenericOperationState implements IOperation {
	/**
	 * creates a least significant bit sub-operation.
	 *
	 * @param data	the share of the number to compute the LSB of and optionally
	 *				the bit shares of the random number that shall be used
	 */
	public LeastSignificantBit(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the least significant bit operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		/*
		 * intermediaryResult is used to store the bits of the bitwise shared random number (r),
		 * the share of "cZero XOR rZero" and the share of the bitwise less than result
		 */

		// step1: start generating bitwise shared random number
		if(getCurrentStep() == 1) {
//			primitives.log("LSB: intermediaryResult={{rbits}, cZero XOR rZero, bitwise less-than result}");
			if(getInitialData().length != (1+primitives.getBitsCount())) {
				IOperation[] subOperations = new IOperation[1];
				subOperations[0] = new GenerateBitwiseRandomNumber(null);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
				return;
			} else {
				long[] intermediaryResult = new long[primitives.getBitsCount()+2];
				System.arraycopy(getInitialData(), 1, intermediaryResult, 0, primitives.getBitsCount());
//				primitives.log("LSB: using specified bitwise shared random number; intermediaryResult="+primitives.outputShares(intermediaryResult));
				setIntermediaryResult(intermediaryResult);
				incrementCurrentStep();
				// (don't return; immediately go to next step)
			}
		}

		// step2: finish generating bitwise shared random number,
		// compute c and start to reconstruct it
		if(getCurrentStep() == 2) {
			if(getSubOperations() != null) {
				getSubOperations()[0].doStep(primitives);
				if(getSubOperations()[0].isOperationCompleted()) {
					if(getSubOperations()[0].getFinalResult()[0] == -1L) {
						// generating bitwise shared random number failed
						long[] finalResult = new long[1];
						finalResult[0] = -1;
						setFinalResult(finalResult);
						return;
					}
					long[] intermediaryResult = new long[primitives.getBitsCount()+2];
					System.arraycopy(getSubOperations()[0].getFinalResult(), 0, intermediaryResult, 0, primitives.getBitsCount());
					setIntermediaryResult(intermediaryResult);
				}
			}
			// compute c and start to reconstruct it
			if(getIntermediaryResult() != null) {
				long[] data = new long[1];
				data[0] = primitives.getMpcShamirSharing().modAdd(getInitialData()[0], primitives.computeNumber( getIntermediaryResult() )); // (works as computeNumber only reads the first primitives.getBitsCount() bits...)
				IOperation[] subOperations = new IOperation[1];
				subOperations[0] = new Reconstruction(data);
				subOperations[0].doStep(primitives);
//				primitives.log("LSB: created reconstruction operation; data="+primitives.outputShares(data));
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step3: finish reconstruction and start bitwise less-than computation
		if(getCurrentStep() == 3) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				long[] intermediaryResult = getIntermediaryResult();
				long[] cBits = primitives.getBits(getSubOperations()[0].getFinalResult()[0]);
				// compute XOR of cZero (cBits[primitives.getBitsCount()-1]) and rZero (intermediaryResult[primitives.getBitsCount()-1])
				if(cBits[primitives.getBitsCount()-1] == 0) {
					intermediaryResult[primitives.getBitsCount()] = intermediaryResult[primitives.getBitsCount()-1];
				} else {
					intermediaryResult[primitives.getBitsCount()] = primitives.getMpcShamirSharing().modSubtract(1, intermediaryResult[primitives.getBitsCount()-1]);
				}
//				primitives.log("LSB: c reconstructed; intermediaryResult="+primitives.outputShares(intermediaryResult));
				// start bitwise less-than computation
				long[] data = new long[1+2*primitives.getBitsCount()];
				data[0] = 1;
				System.arraycopy(cBits, 0, data, 1, primitives.getBitsCount());
				System.arraycopy(intermediaryResult, 0, data, primitives.getBitsCount()+1, primitives.getBitsCount()); // bits of random number r
				IOperation[] subOperations = new IOperation[1];
				subOperations[0] = new BitwiseLessThan(data);
				subOperations[0].doStep(primitives);
//				primitives.log("LSB: created bitwise less-than operation; data="+primitives.outputShares(data));
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step4: finish bitwise less-than computation and start final result computation
		if(getCurrentStep() == 4) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				// store result of bitwise less-than computation
				long[] intermediaryResult = getIntermediaryResult();
				intermediaryResult[primitives.getBitsCount()+1] = getSubOperations()[0].getFinalResult()[0];
//				primitives.log("LSB: bitwise less-than operation completed; intermediaryResult="+primitives.outputShares(intermediaryResult));
				// start final result computation
				long[] data = new long[2];
				data[0] = primitives.getMpcShamirSharing().modMultiply(2, intermediaryResult[primitives.getBitsCount()+1]);
				data[1] = intermediaryResult[primitives.getBitsCount()];
				IOperation[] subOperations = new IOperation[1];
				subOperations[0] = new Multiplication(data);
				subOperations[0].doStep(primitives);
//				primitives.log("LSB: created multiplication operation; data="+primitives.outputShares(data));
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step5: finish final result computation and set final result
		if(getCurrentStep() == 5) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				long[] finalResult = new long[1];
				finalResult[0] = primitives.getMpcShamirSharing().modSubtract(primitives.getMpcShamirSharing().modAdd(getIntermediaryResult()[primitives.getBitsCount()+1], getIntermediaryResult()[primitives.getBitsCount()]), getSubOperations()[0].getFinalResult()[0]);
//				primitives.log("LSB: completed; finalResult="+primitives.outputShares(finalResult));
				setFinalResult(finalResult);
				incrementCurrentStep();
			}
			return;
		}

		// step6: operation completed

		return;
	}
}
