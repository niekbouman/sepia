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
 * Same as the Product Operation but for Arrays.
 * This operation multiplies in each step the factors with even index and odd index:
 * a_1=[]
 * a_2=[]
 * a_3=[]
 * a_4=[]
 * 
 * a_12 = a_1 .* a_2
 * a_34 = a_3 .* a_4
 * 
 * final = a_12 .* a_34
 *
 * This process is then repeated until there is only 1 result which then is the final
 * result.
 * 
 * There is also the possiblity to do only one ArrayMultiplication at a time
 * in this case the result is computed as:
 * res = a_1 * a_2;
 * res = res * a_3;
 * res = res * a_4; 
 * ...
 * 
 * The same number of multiplications is needed, but instead of log(n) rounds this needs
 * n rounds.
 * 
 * In general if the arrays are large sequential ArrayMultiplications (more rounds)
 * perform better than the round optimized variant, since the memory is used more
 * efficiently and the computational load on privacy peers is equal in every round.
 * 
 * Performance measurments showed that memory savings of up to 50% are possible and the 
 * running time was also reduce by factor 2 (Array.length was 2^20). But for small arrays
 * setting the option to true might be faster.
 * 
 * @author Manuel Widmer, ETH Zurich
 */
public class ArrayProduct extends GenericOperationState implements IOperation {

	/** format: [inputPeer][value] */
	private long[][] initialShares = null;

	private boolean lessRounds;
	private int roundCounter; 

	public ArrayProduct(long data[][]){
		initialShares = data;
		lessRounds = false; // this will be faster and cosume less memory with large arrays
		roundCounter = 2;
	}

	public ArrayProduct(long data[][], boolean fewRounds){
		initialShares = data;
		lessRounds = fewRounds;
		roundCounter = 2;
	}

	@Override
	public void doStep(Primitives primitives) throws PrimitivesException {

		IOperation[] subOperations = null;
		// prepare first batch of operations
		if(getCurrentStep() == 1){
			if(lessRounds){ // default behaviour
				subOperations = new IOperation[(int)initialShares.length/2];
				// account for odd length
				int length = (int)Math.ceil((double)initialShares.length/2.0);
				for(int i = 0; i < subOperations.length; i++){
					subOperations[i] = new ArrayMultiplication(initialShares[2*i], initialShares[2*i+1]);
					subOperations[i].doStep(primitives);
				}
				// if we have an odd number of inputs save the last array
				if(subOperations.length < length){
					setIntermediaryResult(initialShares[2*subOperations.length]);
				}else{
					setIntermediaryResult(null);
				}

			}else{// only 1 mult per round
				subOperations = new IOperation[1];
				subOperations[0] = new ArrayMultiplication(initialShares[0], initialShares[1]);
				subOperations[0].doStep(primitives);
			}
			setSubOperations(subOperations);
			incrementCurrentStep();
			return;
		}

		if(getCurrentStep() == 2){
			if(lessRounds){ // default behaviour
				int subOperationsCount = getSubOperations().length;
				// complete running ArrayMultiplications
				for(int i = 0; i < subOperationsCount; i++) {
					getSubOperations()[i].doStep(primitives);

					// CAUTION: DARK MAGIC HERE (to save some memory we reuse initialShares)
					initialShares[i] = getSubOperations()[i].getFinalResult();
				}

				// test if we have left over arguments
				if(getIntermediaryResult() != null){
					// DARK MAGIC AGAIN
					initialShares[subOperationsCount] = getIntermediaryResult();
					subOperationsCount += 1;
				}

				// check if we are finished
				if (subOperationsCount == 1){
					incrementCurrentStep();
					setFinalResult(initialShares[0]);
					return;
				}

				// create new multiplications
				subOperations = new IOperation[subOperationsCount/2];
				int length = (int)Math.ceil((double)subOperationsCount/2.0);
				for(int i = 0; i < subOperations.length; i++){
					subOperations[i] = new ArrayMultiplication(initialShares[2*i], initialShares[2*i+1]);
					subOperations[i].doStep(primitives);
				}
				// check if we have an odd length
				if(subOperations.length < length){
					setIntermediaryResult(initialShares[2*subOperations.length]);
				}else{
					setIntermediaryResult(null);
				}
				setSubOperations(subOperations);
				return;
			}else{ // only 1 multiplication per round
				// finish old mult
				getSubOperations()[0].doStep(primitives);
				
				initialShares[0] = getSubOperations()[0].getFinalResult();
				if(roundCounter >= initialShares.length){
					incrementCurrentStep();
					setFinalResult(initialShares[0]);
					return;
				}
				// start new mult
				subOperations = new IOperation[1];
				subOperations[0] = new ArrayMultiplication(initialShares[0], 
															initialShares[roundCounter]);
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				roundCounter++;
				return;
			}
		}
		return;
	}

}
