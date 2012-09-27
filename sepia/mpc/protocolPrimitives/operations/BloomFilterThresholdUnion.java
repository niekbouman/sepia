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
import mpc.protocolPrimitives.operationStates.RandBitsPregenerationOperationState;

/**
 * Threshold Union operation for shares of BloomFilter array positions.
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class BloomFilterThresholdUnion extends RandBitsPregenerationOperationState {
	/** shares of the Bloom filters which are to join. Format:[Filter X][Position i]*/
	private long [][] bfshares;

	/** Threshold for element reduction */
	private long d;
	
	/** Tells wether a counting or a non-counting Bloom filter shall be output */
	private boolean counting = false;
	
	
	/** 
	 * Creates a new threshold union operation
	 * @param data Bloom filter shares for the operation, Format:[Filter X][Position i]
	 * @param threshold only bloomfilter positions >= threshold will remain
	 * @param learnCount if true a counting BloomFilter is returned where the counts
	 * 						reflect the number of elements in the union
	 * 					falso returns a non-counting BloomFilter 
	 */
	public BloomFilterThresholdUnion(long[][] data, long threshold, boolean learnCount){
		bfshares = data;
		d = threshold;
		counting = learnCount;
	}
	
	
	@Override
	public void doStep(Primitives primitives) throws PrimitivesException {
		IOperation[] subOperations = null;
		switch(getCurrentStep()){
		case 1:
			// generate randomness for lessThan
			if(generateRandomBits(getRandomNumbersNeeded(primitives), primitives)){
				// compute counting union of Keys and Weights
				subOperations = new IOperation[1];
				subOperations[0] = new BloomFilterUnion(bfshares, true);
				subOperations[0].doStep(primitives);
				// counting union is local computation only so we are already finished here
				setIntermediaryResult(subOperations[0].getFinalResult());
				// now we schedule the less than operations
				// we want to know whether BF(i) >= threshold
				// -->    1 - LessThan(BF(i), threshold)
				subOperations = new IOperation[bfshares[0].length];
				for(int i = 0; i < subOperations.length; i++){
					long[] data = new long[5];
					data[0] = getIntermediaryResult()[i];
					data[1] = d;
					// TODO: change this if a,b < fieldsize/2 cannot be assumed
					data[2] = 1;
					data[3] = 1;
					data[4] = -1;
					subOperations[i] = new LessThan(data);
					int bitsNeeded = ((LessThan)subOperations[i]).getRandomNumbersNeeded(primitives)* primitives.getBitsCount();
					((LessThan)subOperations[i]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
					subOperations[i].doStep(primitives);
				}
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			break;
		case 2:// do step until LessThan is finished
			subOperations = getSubOperations();
			boolean allFinished = true;
			for(int i = 0; i < subOperations.length; i++){
				subOperations[i].doStep(primitives);
				allFinished = (allFinished && subOperations[i].isOperationCompleted());
			}
			
			if(allFinished){
				// save results in Initial Data
				setInitialData(new long[subOperations.length]);
				for(int i = 0; i < subOperations.length; i++){
					// we want to know whether BF(i) >= threshold
					// -->    1 - LessThan(BF(i), threshold)
					getInitialData()[i] = primitives.getMpcShamirSharing()
									.modSubtract(1, subOperations[i].getFinalResult()[0]);
				}
				
				if(counting){// we need another multiplication
					subOperations = new IOperation[1];
					subOperations[0] = new ArrayMultiplication(getInitialData(), getIntermediaryResult());
					subOperations[0].doStep(primitives);
					setSubOperations(subOperations);
					incrementCurrentStep();
					
				}else{ // non counting we can directly open
					setFinalResult(getInitialData());
					setCurrentStep(99);
				}
			}
			
			break;
		case 3:
			// finish off multiplications and set final result
			getSubOperations()[0].doStep(primitives);
			setFinalResult(getSubOperations()[0].getFinalResult());
			incrementCurrentStep();
			break;
		default:
			break;
		}
		
	}

	@Override
	public int getRandomNumbersNeeded(Primitives primitives) {
		if(randomnessAlreadySet()){
			return 0;
		}else{
			// we need 1 lessThan operation per BF position
			// the lassThan will use the condition a,b < fieldsize/2
			// hence we only need 1 number per lessThan
			return bfshares[0].length;
		}
	}

}
