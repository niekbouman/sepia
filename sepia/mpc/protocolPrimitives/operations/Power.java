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
 * Power Operation, implemented using square-and-multiply.
 *
 * @author Dilip Many
 *
 */
public class Power extends GenericOperationState implements IOperation {
	/**
	 * creates a power sub-operation (x^n).
	 *
	 * @param data	array containing [x, n]
	 */
	public Power(long[] data) {
		// store initial arguments and create initial intermediary result
		setInitialData(data);
		long[] intermediaryResult = {1, data[0]};
		setIntermediaryResult(intermediaryResult);
		return;
	}


	/**
	 * do the next step of the power operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		/*
		 * normal square-and-multiply / fast-exponentiation algorithm:
		 * 
		 * long power(long x, unsigned long n) {
		 *	long multiplySubStepResult = 1;
		 *	long squareBaseSubStepResult = x;
		 *	while (n > 0) {
		 *		if( (n & 1) == 1 )
		 *			multiplySubStepResult *= squareBaseSubStepResult;
		 *		squareBaseSubStepResult *= squareBaseSubStepResult;
		 *		n /= 2;
		 *	}
		 *	return multiplySubStepResult;
		 * }
		 *
		 * modifications to reduce number of multiplications:
		 * - when encountering the first 1 bit in the exponent:
		 *		multiplySubStepResult = squareBaseSubStepResult;
		 *	 afterwards do the "multiply" sub-step normally (see above)
		 * - in the last step don't do the "square base" sub-step
		 *	 (as the result will not be used)
		 *
		 *
		 * usage of intermediaryResult:
		 * {multiplySubStepResult, squareBaseSubStepResult}
		 */

		long [] intialData = getInitialData();
		//long base = intialData[0];
		long exponent = intialData[1];
		String exponentString = Long.toBinaryString(exponent);

		// find position of first 1 bit in exponent
		int firstOneExponentBitPosition = 0;
		for(int i = 1; i <= exponentString.length(); i++) {
			int position = exponentString.length()-i;
			if( exponentString.substring(position, position+1).equals("1") ) {
				firstOneExponentBitPosition = i;
				break;
			}
		}

		if(getCurrentStep() <= exponentString.length()) {
			if(getSubOperations() == null) {
				// multiplication(s) weren't started yet
				int currentExponentBitPosition = exponentString.length()-getCurrentStep();
				String currentExponentBit = exponentString.substring(currentExponentBitPosition, currentExponentBitPosition+1);
				IOperation[] multiplicationStates = {null, null};
				// compute multiply sub-step
				if(getCurrentStep() == firstOneExponentBitPosition) {
					// when encountering the first 1 bit of the exponent, the "multiply" result is the current "squared base"
					long[] intermediaryResult = {getIntermediaryResult()[1], getIntermediaryResult()[1]};
					setIntermediaryResult(intermediaryResult);
//					primitives.log("doPowerStep: multiply: processed first \"1\" Bit");
					if(firstOneExponentBitPosition == exponentString.length()) {
						// if the exponent has only one 1 bit (in the most significant position),
						// we're done now: save final result
						long[] finalResult = new long[1];
						finalResult[0] = intermediaryResult[0];
						setFinalResult(finalResult);
						incrementCurrentStep();
//						primitives.log("doPowerStep: multiply: processed first and only \"1\" Bit");
						return;
					}
				} else {
					if(currentExponentBit.equals("1")) {
						long[] data = {getIntermediaryResult()[0], getIntermediaryResult()[1]};
						multiplicationStates[0] = new Multiplication(data);
						multiplicationStates[0].doStep(primitives);
//						primitives.log("doPowerStep: multiply: processed \"1\" Bit");
					} else {
						// "multiply" sub-step requires no multiplication (for exponent bit 0)
//						primitives.log("doPowerStep: multiply: processed \"0\" Bit");
					}
				}
				// square base
				if(getCurrentStep() < exponentString.length()) {
					long[] data = {getIntermediaryResult()[1], getIntermediaryResult()[1]};
					multiplicationStates[1] = new Multiplication(data);
					multiplicationStates[1].doStep(primitives);
//					primitives.log("doPowerStep: square base: processed Bit");
				} else {
					// (no base squaring needed in last power step)
//					primitives.log("doPowerStep: square base: processed last Bit (nothing done)");
				}
				setSubOperations(multiplicationStates);

			} else {
//				primitives.log("doPowerStep: (some multiplications are already running)");
				// multiplication(s) already running
				boolean multiplyStepRunning = false;
				boolean multiplyStepCompleted = false;
				boolean squareStepRunning = false;
				boolean squareStepCompleted = false;
				// check which of the "multiply" and "square" multiplications are still running or completed
				if(getSubOperations()[0] != null) {
					if(getSubOperations()[0].isOperationCompleted()) {
						multiplyStepCompleted = true;
					} else {
						multiplyStepRunning = true;
					}
				}
				if(getSubOperations()[1] != null) {
					if(getSubOperations()[1].isOperationCompleted()) {
						squareStepCompleted = true;
					} else {
						squareStepRunning = true;
					}
				}

				// execute the next step of the multiplications which are still running
				if(multiplyStepRunning) {
					getSubOperations()[0].doStep(primitives);
//					primitives.log("doPowerStep: doMultiplicationStep( state.getSubOperations()[0] ) [continue multiply step]");
					if(getSubOperations()[0].isOperationCompleted()) {
						multiplyStepCompleted = true;
						multiplyStepRunning = false;
//						primitives.log("doPowerStep: multiplication 0 completed");
					}
				}
				if(squareStepRunning) {
					getSubOperations()[1].doStep(primitives);
//					primitives.log("doPowerStep: doMultiplicationStep( state.getSubOperations()[1] ) [continue square step]");
					if(getSubOperations()[1].isOperationCompleted()) {
						squareStepCompleted = true;
						squareStepRunning = false;
//						primitives.log("doPowerStep: multiplication 1 completed");
					}
				}

				// construct intermediary results
				boolean nonLastPowerStepCompleted = false;
				boolean lastPowerStepCompleted = false;
				long[] intermediaryResult = new long[2];
				if((!multiplyStepCompleted && !multiplyStepRunning) && squareStepCompleted) {
					// multiplications of power step with exponent bit 0 (or first 1 bit) completed
					intermediaryResult[0] = getIntermediaryResult()[0];
					intermediaryResult[1] = getSubOperations()[1].getFinalResult()[0];
					nonLastPowerStepCompleted = true;
//					primitives.log("doPowerStep: (!multiplyStepCompleted && !multiplyStepRunning) && squareStepCompleted");
				}
				if(multiplyStepCompleted && squareStepCompleted) {
					// multiplications of (non-last) power step with (non-first) exponent bit 1 completed
					intermediaryResult[0] = getSubOperations()[0].getFinalResult()[0];
					intermediaryResult[1] = getSubOperations()[1].getFinalResult()[0];
					nonLastPowerStepCompleted = true;
//					primitives.log("doPowerStep: multiplyStepCompleted && squareStepCompleted");
				}
				if(multiplyStepCompleted && (!squareStepCompleted && !squareStepRunning)) {
					// multiplications of last power step completed
					intermediaryResult[0] = getSubOperations()[0].getFinalResult()[0];
					intermediaryResult[1] = getIntermediaryResult()[1];
					lastPowerStepCompleted = true;
//					primitives.log("doPowerStep: multiplyStepCompleted && (!squareStepCompleted && !squareStepRunning)");
				}

				// store intermediary results and start next step or store final result
				if(nonLastPowerStepCompleted || lastPowerStepCompleted) {
					setIntermediaryResult(intermediaryResult);
					setSubOperations(null);
//					primitives.log("doPowerStep: setIntermediaryResult, cleared SubOperations");
				}
				if(nonLastPowerStepCompleted) {
					incrementCurrentStep();
//					primitives.log("doPowerStep: increment power step and call powerStep again");
					doStep(primitives);
//					primitives.log("doPowerStep: completed call to powerStep");
				}
				if(lastPowerStepCompleted) {
					long[] finalResult = new long[1];
					finalResult[0] = intermediaryResult[0];
					setFinalResult(finalResult);
					incrementCurrentStep();
//					primitives.log("doPowerStep: completed last powerStep");
				}
			}
		}

		return;
	}
}
