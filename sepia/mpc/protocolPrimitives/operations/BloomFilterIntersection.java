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

import services.Services;
import mpc.protocolPrimitives.Primitives;
import mpc.protocolPrimitives.PrimitivesException;
import mpc.protocolPrimitives.operationStates.RandBitsPregenerationOperationState;

/**
 * Set Intersection operation for shares of BloomFilter array positions.
 * Computes the bitwise AND for non-counting or minimum() for counting filters.
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class BloomFilterIntersection extends RandBitsPregenerationOperationState {

	/** specifies if the operation is done with counting or non-counting filters */
	protected boolean b_isCounting;
	
	/** shares of the Bloom filters which are to intersect, format: [Filter X][Position i]*/
	private long [][] bfshares;
	
	/** state of the shared random bit generation needed for minimum in counting interseciton */
	//private int generateRandomState = 0;
	
	//private int bitIndex=0;
	
	/**
	 * Constructor sets the initial data for computation
	 * @param data shares of the Bloom filters, format: [Filter X][Position i]
	 * @param counting true if counting filters are used
	 */
	public BloomFilterIntersection(long[][] data, boolean counting){
		bfshares = data;
		b_isCounting = counting;
	}
	
	
	/**
	 * next step of intersection
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		IOperation[] subOperations = null;

		switch (getCurrentStep()){
		case 1:
			// initialize oprations

			if(!b_isCounting){// non-counting filters
				// Use equal operation if 2 log fieldsize <= #players-1
				if(2*Services.log2(primitives.getFieldSize()) <= bfshares.length-1 ){
					// bitwise AND:
					// 1. sum of all negated input values
					// 2. equal(sum, 0)
					
					long [] sum = new long[bfshares[0].length];
					//subOperations = new IOperation[sum.length];
					subOperations = new IOperation[1];
					
					for(int i = 0; i < bfshares[0].length; i++){
						// compute sum of all negated input values
						for(int j = 0; j < bfshares.length; j++){
							long neg = primitives.getMpcShamirSharing().modSubtract(1, bfshares[j][i]);
							sum[i] =  primitives.getMpcShamirSharing().modAdd(sum[i], neg);
						}
//						long data[]  = new long[2];
//						data[0]= sum[i];
//						data[1]= 0;
//						// schedule equal operation
//						subOperations[i] = new Equal(data);
//						subOperations[i].doStep(primitives);
					}
					subOperations[0] = new ArrayEqual(sum, new long[bfshares[0].length]);
					subOperations[0].doStep(primitives);
					setSubOperations(subOperations);
					incrementCurrentStep();
					
				}else{ // use multiplications
					subOperations = new IOperation[1];
					// bitwise AND of position i of all filters
					subOperations[0] = new ArrayProduct(bfshares, false);
					subOperations[0].doStep(primitives);
					setSubOperations(subOperations);
					incrementCurrentStep();
				}
			}else{// counting
				// Pregeneration of bitwise shared random numbers
				// randomness will be saved in IntermediaryResults
				if(generateRandomBits(getRandomNumbersNeeded(primitives) , primitives)){

					subOperations = new IOperation[bfshares[0].length];

					for(int i=0; i<subOperations.length; i++){
						// we need new memory or we will overwrite the previous operation
						long[] temp = new long[bfshares.length]; 
						// Min of position i of all filters
						// collect shares from all Bloom filters for position i
						for(int j = 0; j < bfshares.length; j++){
							temp[j] = bfshares[j][i];
						}
						// ATTENTION: in case of counting Bloom filters it is assumed
						// that every position is <= fieldsize/2
						subOperations[i] = new Min(temp, 1, false);
						
						// set random bitshares for the minOperations
						int bitsNeeded =((Min)subOperations[i]).getRandomNumbersNeeded(primitives)
						* primitives.getBitsCount(); //numbers * bits per number
						
						//long[] bits = new long[bitsNeeded];
						//System.arraycopy(getIntermediaryResult(), bitIndex, bits, 0, bitsNeeded);
						
						((Min)subOperations[i]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
						//bitIndex += bitsNeeded;
						
						// we have to do the first step also in this round
						subOperations[i].doStep(primitives);
					}
					setSubOperations(subOperations);
					incrementCurrentStep();
				}
			}
			break;
		case 2:
			// we can just do 1 step at a time until we are completed
			subOperations = getSubOperations();
			boolean all_complete = true;
			for(int i=0; i<subOperations.length; i++){
				// are we already finished ?!
				if(!subOperations[i].isOperationCompleted()){
					// do step only for those who aren't finished
					subOperations[i].doStep(primitives);
					if(!subOperations[i].isOperationCompleted()){
						all_complete = false;
					}
				}	
			}
			if(all_complete){
				if(b_isCounting){
					// we have to collect each minimum
					long[] result = new long[subOperations.length];
					for(int i=0; i<subOperations.length; i++){
						result[i] = subOperations[i].getFinalResult()[0];
					}
					setFinalResult(result);
					incrementCurrentStep();
				}else{
					// Use equal operation if 2 log fieldsize <= #players-1
//					if(2*Services.log2(primitives.getFieldSize()) <= bfshares.length-1 ){
//						long[] result = new long[subOperations.length];
//						for(int i=0; i<subOperations.length; i++){
//							result[i] = subOperations[i].getFinalResult()[0];
//						}
//						setFinalResult(result);
//					} else {
						// ArrayProduct was used
						setFinalResult(subOperations[0].getFinalResult());
//					}
					incrementCurrentStep();
				}
			}
			break;
		default:
			// we need an empty round that just does nothing when final result is set
			break;
		}
	}
	
	
//	/**
//	 * Generates bitwise random numbers needed for the less than operations
//	 * @param randomNumbersNeeded
//	 * @param primitives
//	 * @return true if random bit generation has finished
//	 * @throws PrimitivesException 
//	 */
//	protected boolean generateRandomNumbers(int randomNumbersNeeded, Primitives primitives) throws PrimitivesException{
//		// is randomness already set ?
//		if(getIntermediaryResult() != null){
//			return true;
//		}
//		
//		IOperation [] rndGen = new IOperation[1];
//		switch(generateRandomState){
//		case 0:
//			rndGen[0] = new BatchGenerateBitwiseRandomNumbers(new long[]{randomNumbersNeeded});
//			rndGen[0].doStep(primitives);
//			generateRandomState++;
//			setSubOperations(rndGen);
//			return false;
//		case 1:
//			rndGen = getSubOperations();
//			rndGen[0].doStep(primitives);
//			if(rndGen[0].isOperationCompleted()){
//				setIntermediaryResult(rndGen[0].getFinalResult());
//				generateRandomState = 0;
//				return true;
//			}
//			return false;
//		default:
//			return false;
//		}
//	}


	@Override
	public int getRandomNumbersNeeded(Primitives primitives) {
		if(b_isCounting && !randomnessAlreadySet()){
			// we need 1 Min operation per BF position 
			// the Min will use the condition a,b < fieldsize/2
			// hence we need (#BF - 1) numbers per Min operation
			return (bfshares.length-1)*bfshares[0].length ;
		}else{
			return 0; // no randomness needed for non-counting
		}
	}

}


