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


import java.util.logging.Level;

import mpc.protocolPrimitives.Primitives;
import mpc.protocolPrimitives.PrimitivesException;
import mpc.protocolPrimitives.operationStates.GenericOperationState;


/**
 * Batch Generate Bitwise (Shared) Random Number Operation.
 * This operation first estimates the number of bitwise shared random numbers that
 * is has to try to generate to hopefully get the requested amount. From that it
 * computes the number of bits it has to attempt to generate to get the required
 * amount.
 * Then the operation attempts to generate the shared bits. From the successfully
 * generated bits it attempts to generate as many bitwise shared random numbers as
 * possible.
 * If the amount of generated bitwise shared random numbers is less than requested
 * it repeats the above steps for the remaining amount it has to generate.
 *
 * @author Dilip Many
 *
 */
public class BatchGenerateBitwiseRandomNumbers extends GenericOperationState implements IOperation {
	/**
	 * creates a batch generate bitwise (shared) random numbers sub-operation
	 *
	 * @param data	the number of bitwise random numbers to generate
	 */
	public BatchGenerateBitwiseRandomNumbers(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * Do the next step of the batch generate bitwise (shared) random numbers operation.
	 *
	 * @param primitives the primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {

		// step1: start generating initial random bits
		if(getCurrentStep() == 1) {
			// estimate number of bitwise shared random numbers to attempt to generate
			// to successfully get the requested amount
			double nextLargerPowerOfTwo = Math.pow(2, Long.toBinaryString(primitives.getFieldSize()).length());
			double dFieldSize = (double)primitives.getFieldSize();
			long bitwiseGenerationAttempts = (long)(getInitialData()[0] / (dFieldSize/nextLargerPowerOfTwo)) +2;
			long bitGenerationAttempts = (long)(bitwiseGenerationAttempts*primitives.getBitsCount() / ((dFieldSize-2)/dFieldSize) );
			// start bit generation
			IOperation[] subOperations = null;
			if(bitGenerationAttempts < Integer.MAX_VALUE) {
				subOperations = new IOperation[(int)bitGenerationAttempts];
			} else {
				subOperations = new IOperation[Integer.MAX_VALUE];
			}
			for(int i = 0; i < subOperations.length; i++) {
				subOperations[i] = new GenerateRandomBit(null);
				subOperations[i].doStep(primitives);
			}
			setSubOperations(subOperations);
			incrementCurrentStep();
			return;
		}

		// step2: finish random bits generation and start bitwise shared random number generation
		if(getCurrentStep() == 2) {
			int completedBitGenerationAttempts = 0;
			long[] generatedBits = new long[getSubOperations().length];
			int nextResult = 0;
			for(int i = 0; i < getSubOperations().length; i++) {
				getSubOperations()[i].doStep(primitives);
				if(getSubOperations()[i].isOperationCompleted()) {
					// store results of successful generations (and count completed attempts)
					if(getSubOperations()[i].getFinalResult()[0] != -1L) {
						generatedBits[nextResult] = getSubOperations()[i].getFinalResult()[0];
						nextResult++;
					}
					completedBitGenerationAttempts++;
				}
			}
			// if all bit generation attempts completed, use the bits to attempt to generate
			// the bitwise shared random numbers
			if(completedBitGenerationAttempts == getSubOperations().length) {
				setSubOperations(null);
				IOperation[] subOperations = new IOperation[nextResult/primitives.getBitsCount()];
				long[] data = null;
				int nextBit = 0;
				for(int i = 0; i < subOperations.length; i++) {
					data = new long[primitives.getBitsCount()];
					System.arraycopy(generatedBits, nextBit, data, 0, primitives.getBitsCount());
					nextBit += primitives.getBitsCount();
					subOperations[i] = new GenerateBitwiseRandomNumber(data);
					subOperations[i].doStep(primitives);
				}
				generatedBits = null;
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step3: finish bitwise shared random number generation and restart operation for remaining requested numbers
		if(getCurrentStep() == 3) {
			int nextResult = 0;
			int completedBitwiseGenerationAttempts = 0;
			for(int i = 0; i < getSubOperations().length; i++) {
				getSubOperations()[i].doStep(primitives);
				// count successful and completed attempts
				if(getSubOperations()[i].isOperationCompleted()) {
					if(getSubOperations()[i].getFinalResult()[0] != -1L) {
						nextResult++;
					}
					completedBitwiseGenerationAttempts++;
				}
			}
			// if all completed store results and generate remaining requested numbers
			if(completedBitwiseGenerationAttempts == getSubOperations().length) {
				long[] intermediaryResult = new long[nextResult*primitives.getBitsCount()];
				int nextBit = 0;
				for(int i = 0; i < getSubOperations().length; i++) {
					if(getSubOperations()[i].isOperationCompleted()) {
						// store results of successful generations
						if(getSubOperations()[i].getFinalResult()[0] != -1L) {
							System.arraycopy(getSubOperations()[i].getFinalResult(), 0, intermediaryResult, nextBit, primitives.getBitsCount());
							nextBit += primitives.getBitsCount();
						}
					}
				}
				setIntermediaryResult(intermediaryResult);
				// create batch generate bitwise random numbers sub-operation
				
				if(nextResult < getInitialData()[0]) {
					primitives.getLogger().log(Level.INFO, "successfully generated "+nextResult+" bitwise shared random numbers, but need "+getInitialData()[0]);
					IOperation[] subOperations = new IOperation[1];
					long[] data = new long[1];
					data[0] = getInitialData()[0]-nextResult;
					subOperations[0] = new BatchGenerateBitwiseRandomNumbers(data);
					subOperations[0].doStep(primitives);
					setSubOperations(subOperations);
				} else {
					// enough bitwise shared random numbers generated; store the final result
					setSubOperations(null);
					setFinalResult(intermediaryResult);
					incrementCurrentStep();
				}
				incrementCurrentStep();
			}
			return;
		}

		// step4: complete batch generate bitwise random numbers sub-operation
		if(getCurrentStep() == 4) {
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				// copy results
				int intermediaryResultLength = getIntermediaryResult().length;
				long[] finalResult = new long[intermediaryResultLength + getSubOperations()[0].getFinalResult().length];
				System.arraycopy(getIntermediaryResult(), 0, finalResult, 0, intermediaryResultLength);
				setIntermediaryResult(null);
				System.arraycopy(getSubOperations()[0].getFinalResult(), 0, finalResult, intermediaryResultLength, getSubOperations()[0].getFinalResult().length);
				setSubOperations(null);
				setFinalResult(finalResult);
				incrementCurrentStep();
			}
			return;
		}

		// step5: operation completed

		return;
	}
}
