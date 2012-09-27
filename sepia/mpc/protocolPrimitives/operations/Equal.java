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
 * This equal operation uses Fermat's Little Theorem to compute
 * if two shared secret values are equal:
 * Let GF(p) be a finite field of prime order (size) p. Then:
 * 
 * <ul>
 * <li>a^(p-1)=1	if a != 0</li>
 * <li>a^(p-1)=0	if a == 0</li>
 * </ul>
 * 
 * Therefore this operation computes:
 * 1- (a-b)^(p-1).
 *
 * @author Dilip Many
 *
 */
public class Equal extends GenericOperationState implements IOperation {
	/**
	 * creates an equal sub-operation.
	 *
	 * @param data	array containing the 2 shares to test for equality
	 */
	public Equal(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the equal operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		// step1: compute the difference of the shares and compute the (fieldSize-1)-th power of it
		if(getCurrentStep() == 1) {
			// create state and store initial arguments
			long[] data = {primitives.getMpcShamirSharing().modSubtract(getInitialData()[0], getInitialData()[1]), primitives.getMpcShamirSharing().getFieldSize()-1};
			IOperation[] subOperationState = {new Power(data)};
			subOperationState[0].doStep(primitives);

			setSubOperations(subOperationState);
			incrementCurrentStep();
			return;
		}

		// step2: finish power sub-operation and compute the final result
		if(getCurrentStep() == 2) {
			// if sub-operation hasn't completed yet, continue it...
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()) {
				// compute result of equal from result of power operation
				long[] result = new long[1];
				result[0] = primitives.getMpcShamirSharing().modSubtract(1, getSubOperations()[0].getFinalResult()[0]);
				setFinalResult(result);
				incrementCurrentStep();
			}
		}

		// step3: operation completed

		return;
	}



	/**
	 * returns the optimal field size for the Equal 
	 * operation, where optimal means: a prime larger than maxValue, s.t.
	 * the number of multiplications used in the execution of
	 * the equal operation is minimized.
	 *
	 * @param maxValue	the max. value that has to fit into the field
	 * @return			the optimal field size
	 */
	public static long getOptimalFieldSizeForEqual(long maxValue) {
		/*
		 * in general the primes were computed by PrimeNumberGenerator v.4.15
		 * and additionally verified by:
		 * 1) http://www.numberempire.com/primenumbers.php
		 * 2) http://www.prime-numbers.org/
		 */
		// ORDERED array of primes (smallest first)
		long[] primes = {3L,
						 5L,
						 17L,
						 19L,
						 41L,
						 257L,
						 769L,
						 1153L,
						 2113L,
						 4129L,
						 12289L,
						 65537L,
						 65539L,
						 163841L,
						 270337L,
						 786433L,
						 1179649L,
						 2101249L,
						 4194433L,
						 8650753L,
						 16777729L,
						 69206017L,
						 167772161L,
						 270532609L,
						 537133057L,
						 1107296257L,
						 3221225473L,
						 6442713089L,		// primes up to this one verified by 1) and 2)
						 12884901893L,		// primes from this one on only verified by 1)
						 77309411329L,
						 206158430209L,
						 347892350977L,
						 1378684502017L,
						 2783138807809L,
						 6597069766657L,
						 9354438770689L,
						 26456998543361L,
						 46179488366593L,
						 106652627894273L,
						 215504279044097L,
						 351878080626689L,
						 703689589260289L,
						 1689949371891713L,
						 2256266579673089L,
						 7881299347898369L,
						 13515196928622593L,
						 31525197391593473L,
						 54113564272623617L,
						 112589990684262401L,
						 180143985094819841L,
						 432345568522534913L,
						 882705526964617217L,
						 1152923703630102529L,
						 2308094809027379201L,
						 4611686018429485057L};

		// look in array for smallest prime which is larger than the input value
		for(int i = 0; i < primes.length; i++) {
			if(primes[i] > maxValue) {
				return primes[i];
			}
		}

		/**
		 * return default prime: biggest prime smaller than MAX_LONG
		 * WARNING: this prime has LOTS of "1" Bits and is therefore
		 * a VERY BAD choice!!!
		 * (requires 122 multiplications for equal!)
		 */
		return 9223372036854775783L;
	}
}
