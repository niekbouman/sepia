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

public class BloomFilterWeightedIntersection extends RandBitsPregenerationOperationState {
	/** specifies if the operation is done with counting or non-counting filters */
	private boolean learnWeights;
	
	/** shares of the Bloom filters which are to join. Format:[Filter X][Position i]*/
	private long [][] bfsharesKeys;
	private long [][] bfsharesWeights;
	
	/** Thresholds for minimum number of Players with a key/ minimum accumulated weight */
	private long minK, minW;
	
	/** 
	 * Creates a new weighted set intersection operation
	 * @param Keys Bloom filter shares of the keys, Format:[Filter X][Position i]
	 * @param Weights Bloom filter shares of the Weights, Format:[Filter X][Position i]
	 * @param Tk Threshold for the number of players who must have the Key
	 * @param Tw Threshold for the accumulated weight
	 * @param counting If false only a non-counting Bloom filter containing the keys
	 * 			 		will be reconstructed
	 * 				  If true a counting Bloom filter with accumulated weights
	 * 					will be reconstructed
	 */
	public BloomFilterWeightedIntersection(long[][] Keys, long[][] Weights, long Tk, long Tw, boolean counting){
		bfsharesKeys = Keys;
		bfsharesWeights = Weights;
		minK = Tk;
		minW = Tw;
		learnWeights = counting;
	}

	@Override
	public void doStep(Primitives primitives) throws PrimitivesException {
		IOperation[] subOperations = null;
		boolean finished = true;
		switch(getCurrentStep()){
		case 1:
			// generate randomness for lessThan
			if(generateRandomBits(getRandomNumbersNeeded(primitives), primitives)){
				// compute threshold union of Keys and Weights
				subOperations = new IOperation[2];
				subOperations[0] = new BloomFilterThresholdUnion(bfsharesKeys, minK, false);
				subOperations[1] = new BloomFilterThresholdUnion(bfsharesWeights, minW, true);
				int bitsNeeded = ((BloomFilterThresholdUnion)subOperations[0])
						.getRandomNumbersNeeded(primitives)* primitives.getBitsCount();
				((BloomFilterThresholdUnion)subOperations[0]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
				((BloomFilterThresholdUnion)subOperations[1]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
				subOperations[0].doStep(primitives);
				subOperations[1].doStep(primitives);
				// counting union is local computation only so we are already finished here
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			break;
		case 2:// do step until union is finished
			finished = true;
			for(int i = 0; i < getSubOperations().length; i++){
				getSubOperations()[i].doStep(primitives);
				finished = finished && getSubOperations()[i].isOperationCompleted();
			}
			if(finished){
				// multiply the two resulting Bloom filters
				subOperations = new IOperation[1];
				subOperations[0] = new ArrayMultiplication(
						getSubOperations()[0].getFinalResult(), //keys
						getSubOperations()[1].getFinalResult()); // weights
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			break;
		case 3: // finish array Mult
			getSubOperations()[0].doStep(primitives);
			if(learnWeights){
				setFinalResult(getSubOperations()[0].getFinalResult());
				setCurrentStep(99); // finished.
			}else{
				// we need to hide the weights
				// done by BF(i) = 1 - equal(BF(i), 0);
				subOperations = new IOperation[1];
				subOperations[0] = new ArrayEqual(getSubOperations()[0].getFinalResult(), 
						new long[getSubOperations()[0].getFinalResult().length]);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			break;
		case 4:// finish equal computations
			finished = true;
			for(int i = 0; i < getSubOperations().length; i++){
				getSubOperations()[i].doStep(primitives);
				finished = finished && getSubOperations()[i].isOperationCompleted();
			}
			if(finished){
				long[] result = getSubOperations()[0].getFinalResult();
				// 1- equal
				for(int i = 0; i < result.length; i++){
					result[i] = primitives.getMpcShamirSharing().modSubtract(1, 
							result[i]);
				}
				setFinalResult(result);
				incrementCurrentStep();
			}
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
			// we need two threshold union operations each needing bfshares[0].length
			// LessThan operations which need 1 random number
			return 2*bfsharesKeys[0].length;
		}
	}
}
