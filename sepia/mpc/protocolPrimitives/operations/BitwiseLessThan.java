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
 * BitwiseLessThan Operation.
 * This operation computes:
 * <ol>
 * <li> which bits of the inputs are different (a_i != b_i) </li>
 * <li> the prefix-or of the previous bits which is a bit mask 00..11..
 *   that is 0 at the bit positions which are equal and 1 from the first
 *   different bit on</li>
 * <li> the difference of the prefix-or bits: the first bit which is different</li>
 * <li> the product of these bits and the bits of b</li>
 * <li> the sum of this product then is the final result</li>
 *</ol>
 *
 * See [Nishide2007].
 *
 * @author Dilip Many
 *
 */
public class BitwiseLessThan extends GenericOperationState implements IOperation {
	/**
	 * creates a bitwise less-than sub-operation
	 *
	 * @param data	the type of bitwise less-than to compute and the bit shares of
	 *				the values for which to compute the bitwise less-than.
	 */
	public BitwiseLessThan(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the bitwise less-than operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		/*
		 * intermediaryResult is used to store XOR results in steps 1, 2 and to store
		 * the multiplication results (e_i*b_i) in steps 3, 4
		 */

		long[] initialData = getInitialData();

		// step1: start the XOR computations
		if(getCurrentStep() == 1) {
			if(initialData[0] == 0) {
				IOperation[] subOperations = new IOperation[primitives.getBitsCount()];
				long[] data = null;
				for(int i = 0; i < primitives.getBitsCount(); i++) {
					data = new long[2];
					data[0] = primitives.getMpcShamirSharing().modMultiply(2, initialData[i+1]);
					data[1] = initialData[primitives.getBitsCount()+i+1];
					subOperations[i] = new Multiplication(data);
					subOperations[i].doStep(primitives);
				}
				setSubOperations(subOperations);
				incrementCurrentStep();
				return;
			} else {
				// one of the values is public, so the computation can be done locally
				long[] intermediaryResult = new long[primitives.getBitsCount()];
				for(int i = 0; i < primitives.getBitsCount(); i++) {
					intermediaryResult[i] = primitives.getMpcShamirSharing().modSubtract(primitives.getMpcShamirSharing().modAdd(initialData[i+1], initialData[primitives.getBitsCount()+i+1]), primitives.getMpcShamirSharing().modMultiply(primitives.getMpcShamirSharing().modMultiply(2, initialData[i+1]), initialData[primitives.getBitsCount()+i+1]));
				}
				setIntermediaryResult(intermediaryResult);
				incrementCurrentStep();
				// (don't return; immediately go to next step)
			}
		}

		// step2: finish XOR computations and start prefix-or computation
		if(getCurrentStep() == 2) {
			// complete XORs
			boolean allMultiplicationsCompleted = true;
			IOperation[] subOperations = getSubOperations();
			if(subOperations != null) {
				long[] intermediaryResult = new long[primitives.getBitsCount()];
				for(int i = 0; i < primitives.getBitsCount(); i++) {
					subOperations[i].doStep(primitives);
					if(subOperations[i].isOperationCompleted()) {
						intermediaryResult[i] = primitives.getMpcShamirSharing().modSubtract(primitives.getMpcShamirSharing().modAdd(initialData[i+1], initialData[primitives.getBitsCount()+i+1]), subOperations[i].getFinalResult()[0]);
					} else {
						allMultiplicationsCompleted = false;
					}
				}
				if(allMultiplicationsCompleted) {
					setIntermediaryResult(intermediaryResult);
				}
			}
			// start prefix-or
			if(allMultiplicationsCompleted) {
				subOperations = new IOperation[1];
				long[] data = getIntermediaryResult();
				subOperations[0] = new LinearPrefixOr(data);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			return;
		}

		// step3: finish prefix-or, compute differences of prefix-or bits, and start last batch of multiplications
		if(getCurrentStep() == 3) {
			IOperation prefixOrState = getSubOperations()[0];
			if(!prefixOrState.isOperationCompleted()) {
				prefixOrState.doStep(primitives);
			}
			if(prefixOrState.isOperationCompleted()) {
				// compute differences
				long[] prefixOrResult = prefixOrState.getFinalResult();
				long[] differences = new long[primitives.getBitsCount()];
				differences[0] = prefixOrResult[0];
				for(int i = 1; i < primitives.getBitsCount(); i++) {
					differences[i] = primitives.getMpcShamirSharing().modSubtract(prefixOrResult[i], prefixOrResult[i-1]);
				}
				// start last batch of multiplications
				if(initialData[0] != 2) {
					IOperation[] subOperations = new IOperation[primitives.getBitsCount()];
					long[] data = null;
					for(int i = 0; i < primitives.getBitsCount(); i++) {
						data = new long[2];
						data[0] = differences[i];
						data[1] = initialData[primitives.getBitsCount()+i+1];
						subOperations[i] = new Multiplication(data);
						subOperations[i].doStep(primitives);
					}
					setSubOperations(subOperations);
					incrementCurrentStep();
					return;
				} else {
					// b is public; do multiplications locally
					long[] intermediaryResult = new long[primitives.getBitsCount()];
					for(int i = 0; i < primitives.getBitsCount(); i++) {
						intermediaryResult[i] = primitives.getMpcShamirSharing().modMultiply(differences[i], initialData[primitives.getBitsCount()+i+1]);
					}
					setIntermediaryResult(intermediaryResult);
					setSubOperations(null);
					incrementCurrentStep();
					// (don't return; immediately go to next step)
				}
			} else {
				// (done next step of prefix-or; but it hasn't completed yet)
				return;
			}
		}

		// step4: finish multiplications and compute final result (sum)
		if(getCurrentStep() == 4) {
			boolean allMultiplicationsCompleted = true;
			IOperation[] subOperations = getSubOperations();
			if(subOperations != null) {
				long[] intermediaryResult = new long[primitives.getBitsCount()];
				for(int i = 0; i < primitives.getBitsCount(); i++) {
					subOperations[i].doStep(primitives);
					if(subOperations[i].isOperationCompleted()) {
						intermediaryResult[i] = subOperations[i].getFinalResult()[0];
					} else {
						allMultiplicationsCompleted = false;
					}
				}
				if(allMultiplicationsCompleted) {
					setIntermediaryResult(intermediaryResult);
				}
			}
			// compute final result
			if(allMultiplicationsCompleted) {
				long[] finalResult = new long[1];
				finalResult[0] = 0;
				for(int i = 0; i < primitives.getBitsCount(); i++) {
					finalResult[0] = primitives.getMpcShamirSharing().modAdd(finalResult[0], getIntermediaryResult()[i]);
				}
				setFinalResult(finalResult);
				incrementCurrentStep();
			}
			return;
		}

		// step5: operation completed

		return;
	}
}
