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
 * Less-Than Operation. Used for comparing two secrets a and b. Let 
 * <ul>
 * <li>(a < fieldSize/2)=w</li> 
 * <li>(b < fieldSize/2)=x</li>
 * <li>(a-b < fieldSize/2)=y</li>
 * </ul>
 *
 * Then, this operation computes:
 * <ol>
 * <li> w by computing 1-LSB(2*a) and similarly for b, a-b </li>
 * <li> x*y </li>
 * <li> w*(x+y-2*x*y) </li>
 * <li> the result w*(x+y-2*x*y)+1-y-x+x*y. </li>
 * </ol>
 * 
 * See [Nishide2007].
 *
 * @author Dilip Many
 *
 */
public class LessThan extends GenericOperationState implements IOperation {
	private Object predicateKeyA;
	private Object predicateKeyB;
	private Object predicateKeyAB;
	
	private boolean b_randomDataWasSet;
	
	/**
	 * creates a less-than sub-operation.
	 *
	 * @param data	a, b and three values indicating the knowledge about a, b, a-b and
	 *				optionally the bit shares of the random numbers that shall be used
	 */
	public LessThan(long[] data) {
		// store initial arguments
		setInitialData(data);
		b_randomDataWasSet = false;
		return;
	}

	/**
	 * The predicate keys are used for caching of intermediate results. In particular,
	 * the results of [a<p/2], [b<p/2], and [a-b<p/2] are cached between subsequent
	 * invocations of lessThan operations on the same secrets.
	 * @param keyA Uniquely identifies secret [A].
	 * @param keyB Uniquely identifies secret [B].
	 * @param keyAB Uniquely identifies secret [A-B].
	 */
	public void setPredicateKeys(Object keyA, Object keyB, Object keyAB) {
		predicateKeyA = keyA;
		predicateKeyB = keyB;
		predicateKeyAB = keyAB;
	}
	
	/**
	 * Returns the number of shared random numbers needed (between 0 and 3). If all the bits were provided with the initial
	 * data, this returns zero. 
	 * This method considers predicate caching, i.e., if a predicate is available in the cache, there is not need to generate
	 * a corresponding random number.
	 * 
	 * @param primitives the primitives.
	 * @return
	 */
	public int getRandomNumbersNeeded(Primitives primitives) {
		if(b_randomDataWasSet){
			return 0;
		}
		// Count the number of unknown predicates
		int predicateCount=0;
		long data[] = getInitialData();
				
		if(data[2]==-1) {
			if (predicateKeyA == null || primitives.getPredicateCache().get(predicateKeyA)==null ) {
				predicateCount++;
			}
		}
		if(data[3]==-1) {
			if (predicateKeyB == null || primitives.getPredicateCache().get(predicateKeyB)==null ) {
				predicateCount++;
			}
		}
		if(data[4]==-1) {
			if (predicateKeyAB == null || primitives.getPredicateCache().get(predicateKeyAB)==null ) {
				predicateCount++;
			}
		}
		
		int bitsNeeded = predicateCount * primitives.getBitsCount();
		if (data.length-5 >= bitsNeeded) {
			return 0;
		} else {
			return predicateCount;
		}
	}

	/**
	 * Sets the required random number bits.
	 * @param bitShares the shares of random number bits.
	 */
	public void setRandomNumberBitShares(long[] bitShares) {
		long[] dataOld = getInitialData();
		
		long[] data = new long[5+bitShares.length];
		for(int i=0; i<5; i++) {
			data[i] = dataOld[i];
		}
		System.arraycopy(bitShares, 0, data, 5, bitShares.length);
		
		b_randomDataWasSet = true;
		
		setInitialData(data);
	}
	
	/**
	 * do the next step of the less-than operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		/*
		 * intermediaryResult is used to store the result bits of the "...<fieldSize/2" computations,
		 * and the results of the two multiplications:
		 * intermediaryResult[0] = w
		 * intermediaryResult[1] = x
		 * intermediaryResult[2] = y
		 * intermediaryResult[3] = x*y
		 * intermediaryResult[4] = w*(...)
		 */

		long[] initialData = getInitialData();

		// step1: start the LSB computations
		if(getCurrentStep() == 1) {
//			primitives.log("less-than; initialData="+primitives.outputShares(initialData));
//			primitives.log("(a < fieldSize/2)=w, (b < fieldSize/2)=x, (a-b < fieldSize/2)=y\nintermediaryResult={w, x, y, x*y, w*(...)}");
			int bitwiseNumbersOfInputUsed = 0;
			IOperation[] subOperations = new IOperation[3];
			long[] intermediaryResult = new long[5];
			if(initialData[2] == -1L) {
				// Do we have it in the predicate cache?
				boolean shareSet = false;
				if(predicateKeyA!=null) {
					Long cachedShare = primitives.getPredicateCache().get(predicateKeyA);
					if(cachedShare!=null) {
						intermediaryResult[0] = cachedShare;
						shareSet = true;
					}
				}
				
				if(!shareSet) {
					// compute LSB of a
					if(initialData.length >= (5+primitives.getBitsCount())) {
						long[] data = new long[1+primitives.getBitsCount()];
						data[0] = primitives.getMpcShamirSharing().modMultiply(2, initialData[0]);
						System.arraycopy(initialData, 5, data, 1, primitives.getBitsCount());
						subOperations[0] = new LeastSignificantBit(data);
						subOperations[0].doStep(primitives);
						bitwiseNumbersOfInputUsed++;
					} else {
						long[] data = new long[1];
						data[0] = primitives.getMpcShamirSharing().modMultiply(2, initialData[0]);
						subOperations[0] = new LeastSignificantBit(data);
						subOperations[0].doStep(primitives);
					}
				}
			} else {
				intermediaryResult[0] = initialData[2];
//				primitives.log("no need to compute LSB of a; intermediaryResult="+primitives.outputShares(intermediaryResult));
			}
			if(initialData[3] == -1L) {
				// Do we have it in the predicate cache?
				boolean shareSet = false;
				if(predicateKeyB!=null) {
					Long cachedShare = primitives.getPredicateCache().get(predicateKeyB);
					if(cachedShare!=null) {
						intermediaryResult[1] = cachedShare;
						shareSet = true;
					}
				}
				
				if(!shareSet) {
					// compute LSB of b
					if(initialData.length >= (5+(1+bitwiseNumbersOfInputUsed)*primitives.getBitsCount())) {
						long[] data = new long[1+primitives.getBitsCount()];
						data[0] = primitives.getMpcShamirSharing().modMultiply(2, initialData[1]);
						System.arraycopy(initialData, 5+bitwiseNumbersOfInputUsed*primitives.getBitsCount(), data, 1, primitives.getBitsCount());
						subOperations[1] = new LeastSignificantBit(data);
						subOperations[1].doStep(primitives);
						bitwiseNumbersOfInputUsed++;
					} else {
						long[] data = new long[1];
						data[0] = primitives.getMpcShamirSharing().modMultiply(2, initialData[1]);
						subOperations[1] = new LeastSignificantBit(data);
						subOperations[1].doStep(primitives);
					}
				}
			} else {
				intermediaryResult[1] = initialData[3];
//				primitives.log("no need to compute LSB of b; intermediaryResult="+primitives.outputShares(intermediaryResult));
			}
			if(initialData[4] == -1L) {
				// Do we have it in the predicate cache?
				boolean shareSet = false;
				if(predicateKeyAB!=null) {
					Long cachedShare = primitives.getPredicateCache().get(predicateKeyAB);
					if(cachedShare!=null) {
						intermediaryResult[2] = cachedShare;
						shareSet = true;
					}
				}
				
				if(!shareSet) {
					// compute LSB of a-b
					if(initialData.length >= (5+(1+bitwiseNumbersOfInputUsed)*primitives.getBitsCount())) {
						long[] data = new long[1+primitives.getBitsCount()];
						data[0] = primitives.getMpcShamirSharing().modMultiply(2, primitives.getMpcShamirSharing().modSubtract(initialData[0], initialData[1]));
						System.arraycopy(initialData, 5+bitwiseNumbersOfInputUsed*primitives.getBitsCount(), data, 1, primitives.getBitsCount());
						subOperations[2] = new LeastSignificantBit(data);
						subOperations[2].doStep(primitives);
						bitwiseNumbersOfInputUsed++;
					} else {
						long[] data = new long[1];
						data[0] = primitives.getMpcShamirSharing().modMultiply(2, primitives.getMpcShamirSharing().modSubtract(initialData[0], initialData[1]));
						subOperations[2] = new LeastSignificantBit(data);
						subOperations[2].doStep(primitives);
					}
				}
			} else {
				intermediaryResult[2] = initialData[4];
//				primitives.log("no need to compute LSB of a-b; intermediaryResult="+primitives.outputShares(intermediaryResult));
			}
			setSubOperations(subOperations);
			setIntermediaryResult(intermediaryResult);
			incrementCurrentStep();
			// check if any sub-operations are running (if not directly go to next step)
			if( (subOperations[0] != null) || (subOperations[1] != null) || (subOperations[2] != null) ) {
//				primitives.log("some LSB computations are in progress; returning (end of step 1)...");
				return;
			}
		}

		// step2: finish the LSB computations and start multiplications
		if(getCurrentStep() == 2) {
			/** the number of LSB computations that are done */
			int lsbComputedCount = 0;
			IOperation[] subOperations = getSubOperations();
			for(int subOperationIndex = 0; subOperationIndex < 3; subOperationIndex++) {
				// if LSB computation exists...
				if(subOperations[subOperationIndex] != null) {
					// ...do next step... 
					subOperations[subOperationIndex].doStep(primitives);
					// ...and if it completed successfully, compute "...<fieldSize/2" result
					if(subOperations[subOperationIndex].isOperationCompleted()) {
						if(subOperations[subOperationIndex].getFinalResult()[0] == -1L) {
							// LSB computation failed
							long[] finalResult = new long[1];
							finalResult[0] = -1;
							setFinalResult(finalResult);
//							primitives.log("some LSB computation failed!");
							return;
						} else {
							long predicateShare = primitives.getMpcShamirSharing().modSubtract(1, subOperations[subOperationIndex].getFinalResult()[0]);
							getIntermediaryResult()[subOperationIndex] = predicateShare;
//							primitives.log("some LSB computation ("+subOperationIndex+") finished; intermediaryResult="+primitives.outputShares(getIntermediaryResult()));
							
							// Cache the result
							if (predicateKeyA!=null && subOperationIndex==0) {
								primitives.getPredicateCache().put(predicateKeyA, predicateShare);
							} else if (predicateKeyB!=null && subOperationIndex==1) {
								primitives.getPredicateCache().put(predicateKeyB, predicateShare);
							} else if (predicateKeyAB!=null && subOperationIndex==2) {
							  primitives.getPredicateCache().put(predicateKeyAB, predicateShare);
							}
						}
						lsbComputedCount++;
					}
				} else {
					lsbComputedCount++;
				}
			}
			// start x*y multiplication if all LSB computations finished
			if(lsbComputedCount == 3) {
				long[] intermediaryResult = getIntermediaryResult();
				if( (initialData[3] != -1L) || (initialData[4] != -1L) ) {
					intermediaryResult[3] = primitives.getMpcShamirSharing().modMultiply(intermediaryResult[1], intermediaryResult[2]);
//					primitives.log("computing x*y locally; intermediaryResult="+primitives.outputShares(intermediaryResult));
					setSubOperations(null);
					incrementCurrentStep();
					// (don't return; immediately go to next step)
				} else {
					long[] data = new long[2];
					data[0] = intermediaryResult[1];
					data[1] = intermediaryResult[2];
					IOperation[] newSubOperations = new IOperation[1];
					newSubOperations[0] = new Multiplication(data);
					newSubOperations[0].doStep(primitives);
//					primitives.log("started x*y MPC computation; data="+primitives.outputShares(data));
					setSubOperations(newSubOperations);
					incrementCurrentStep();
					return;
				}
			} else {
				return;
			}
		}

		// step3: finish x*y multiplication and start w*(...) multiplication
		if(getCurrentStep() == 3) {
			boolean multiplicationCompleted = true;
			if(getSubOperations() != null) {
				getSubOperations()[0].doStep(primitives);
				if(getSubOperations()[0].isOperationCompleted()) {
					getIntermediaryResult()[3] = getSubOperations()[0].getFinalResult()[0];
//					primitives.log("x*y MPC computation finished; intermediaryResult="+primitives.outputShares(getIntermediaryResult()));
				} else {
					multiplicationCompleted = false;
				}
			}
			if(multiplicationCompleted) {
				// start w*(...) multiplication
				long[] intermediaryResult = getIntermediaryResult();
				if( (initialData[2] != -1L) || ((initialData[3] != -1L) && (initialData[4] != -1L)) ) {
					intermediaryResult[4] = primitives.getMpcShamirSharing().modMultiply(intermediaryResult[0], primitives.getMpcShamirSharing().modSubtract(primitives.getMpcShamirSharing().modAdd(intermediaryResult[1], intermediaryResult[2]), primitives.getMpcShamirSharing().modMultiply(2, intermediaryResult[3])));
//					primitives.log("computing w*(...) locally; intermediaryResult="+primitives.outputShares(intermediaryResult));
					setSubOperations(null);
					incrementCurrentStep();
					// (don't return; immediately go to next step)
				} else {
					long[] data = new long[2];
					data[0] = intermediaryResult[0];
					data[1] = primitives.getMpcShamirSharing().modSubtract(primitives.getMpcShamirSharing().modAdd(intermediaryResult[1], intermediaryResult[2]), primitives.getMpcShamirSharing().modMultiply(2, intermediaryResult[3]));
					IOperation[] newSubOperations = new IOperation[1];
					newSubOperations[0] = new Multiplication(data);
					newSubOperations[0].doStep(primitives);
//					primitives.log("started w*(...) MPC computation; data="+primitives.outputShares(data));
					setSubOperations(newSubOperations);
					incrementCurrentStep();
					return;
				}
			} else {
				return;
			}
		}

		// step4: finish w*(...) multiplication and compute final result
		if(getCurrentStep() == 4) {
			boolean multiplicationCompleted = true;
			if(getSubOperations() != null) {
				getSubOperations()[0].doStep(primitives);
				if(getSubOperations()[0].isOperationCompleted()) {
					getIntermediaryResult()[4] = getSubOperations()[0].getFinalResult()[0];
//					primitives.log("w*(...) MPC computation finished; intermediaryResult="+primitives.outputShares(getIntermediaryResult()));
				} else {
					multiplicationCompleted = false;
				}
			}
			if(multiplicationCompleted) {
				long[] finalResult = new long[1];
				finalResult[0] = primitives.getMpcShamirSharing().modAdd(primitives.getMpcShamirSharing().modSubtract(primitives.getMpcShamirSharing().modSubtract(primitives.getMpcShamirSharing().modAdd(getIntermediaryResult()[4], 1), getIntermediaryResult()[2]), getIntermediaryResult()[1]), getIntermediaryResult()[3]);
//				primitives.log("computed final result: "+primitives.outputShares(finalResult));
				setFinalResult(finalResult);
				incrementCurrentStep();
				return;
			}
		}

		// step5: operation completed

		return;
	}
}
