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
import mpc.protocolPrimitives.operationStates.GenericOperationState;


/**
 * Set union operation for shares of BloomFilter array positions.
 * Computes the bitwise OR for non-counting or sum for counting filters.
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class BloomFilterUnion extends GenericOperationState {

	/** specifies if the operation is done with counting or non-counting filters */
	private boolean b_isCounting;
	
	/** shares of the Bloom filters which are to join. Format:[Filter X][Position i]*/
	private long [][] bfshares;
	
	/** current Bloom filter index, we have do union of BF_position ,
	 *  where position = 1 .. #inputPeers */
	private int position;
	
	/** 
	 * Creates a new union operation. The given Bloom filters are joined
	 * 
	 */
	public BloomFilterUnion(long[][] data, boolean counting){
		bfshares = data;
		b_isCounting = counting;
	}

	public void doStep(Primitives primitives) throws PrimitivesException {
		IOperation[] subOperations = null;
		switch(getCurrentStep()){
		case 1:
			if(!b_isCounting){// non-counting filters
				// Use equal operation if 2 log fieldsize <= #players-1
				if(2*Services.log2(primitives.getFieldSize()) <= bfshares.length - 1 ){
					// bitwise OR:
					// 1. sum of all input values
					// 2. 1 - equal(sum, 0)
					long [] sum = new long[bfshares[0].length];
					//subOperations = new IOperation[sum.length];
					subOperations = new IOperation[1];
					
					for(int i = 0; i < bfshares[0].length; i++){
						// compute sum of all input values
						for(int j = 0; j < bfshares.length; j++){
							sum[i] =  primitives.getMpcShamirSharing().modAdd(sum[i], bfshares[j][i]);
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
					setCurrentStep(10); // go to case 10 to finish
					
				} else {
					// bitwise OR of position i of all filters
					// a OR b = a + b - a*b

					// initialize oprations
					// we can do all different positions in parallel
					subOperations = new IOperation[1];
					subOperations[0] = new ArrayMultiplication(bfshares[0],bfshares[1]);
					// we have to do the first step also in this round
					subOperations[0].doStep(primitives);
					// compute and save a + b in IntermediaryResults
					setIntermediaryResult(new long[bfshares[0].length]);
					for(int i=0; i<bfshares[0].length; i++){
						long[] data = new long[2];
						data[0] = bfshares[0][i];
						data[1] = bfshares[1][i];
						getIntermediaryResult()[i] = primitives.getMpcShamirSharing().modAdd(data[0], data[1]);
					}
					// set position to 2 because Bloom filter 0 and 1 are already done in this step
					position = 2; 
					setSubOperations(subOperations);
					incrementCurrentStep();
				}
			}else{// counting
			
				// Sum of position i of all filters
				long [] result = new long[bfshares[0].length];
				
				for(int j=0; j < bfshares[0].length; j++){
					long sum = 0;
					for(int i=0; i < bfshares.length; i++){
						sum = primitives.getMpcShamirSharing().modAdd(sum, bfshares[i][j]);
					}
					result[j] = sum;
				}
				// we are finished here step is incremented 2x so we don't reach case 2
				setFinalResult(result);
				incrementCurrentStep();
				incrementCurrentStep();
			}
			break;
		case 2:
			// we can just do 1 step at a time until we are completed
			
			// finish the old multiplications
			getSubOperations()[0].doStep(primitives);
			// save result in initial data
			setInitialData(getSubOperations()[0].getFinalResult());
			long multresult;	
			if(position < bfshares.length){
				for(int i=0; i < bfshares[0].length; i++){
					// multresult = a*b
					multresult = getInitialData()[i];
					// a+b  - a*b
					getInitialData()[i] = primitives.getMpcShamirSharing().modSubtract(getIntermediaryResult()[i], multresult);
					// last result + b to Intermediary
					getIntermediaryResult()[i] = primitives.getMpcShamirSharing().modAdd(getInitialData()[i], bfshares[position][i]);
				}
				// new multiplication
				subOperations = new IOperation[1];
				subOperations[0] = new ArrayMultiplication(getInitialData(), bfshares[position]);
				subOperations[0].doStep(primitives);
			}else{
				//long [] finalRes = new long[bfshares[0].length];
				for(int i=0; i<bfshares[0].length; i++){
					multresult = getInitialData()[i];
					// last result  a+b - a*b
					getInitialData()[i] = primitives.getMpcShamirSharing().modSubtract(getIntermediaryResult()[i], multresult);
				}
				setFinalResult(getInitialData());
				incrementCurrentStep();
				return;
			}
			setSubOperations(subOperations);
			position++;
			break;
		case 10:
			// non-counting union
			// we can just do 1 step at a time until we are completed
			subOperations = getSubOperations();
			boolean all_complete = true;
			for(int i=0; i<subOperations.length; i++){
				subOperations[i].doStep(primitives);
				if(!subOperations[i].isOperationCompleted()){
					all_complete = false;
				}
			}
			if(all_complete){
				// arrayequal directly returns the full array
				long[] result = subOperations[0].getFinalResult();
				for(int i=0; i < result.length; i++){
					result[i] = primitives.getMpcShamirSharing().modSubtract(1, result[i]);
				}
				setFinalResult(result);
				incrementCurrentStep();
			}
			break;
		default:
			// we need an empty round that just does nothing when final result is set
			break;
		}
	}
}
