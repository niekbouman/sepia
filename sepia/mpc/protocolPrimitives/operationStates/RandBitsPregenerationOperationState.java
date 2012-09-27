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

package mpc.protocolPrimitives.operationStates;

import mpc.protocolPrimitives.Primitives;
import mpc.protocolPrimitives.PrimitivesException;
import mpc.protocolPrimitives.operations.BatchGenerateBitwiseRandomNumbers;
import mpc.protocolPrimitives.operations.IOperation;


/**
 * Abstract class that helps when working with suboperations that need shared random number
 * bits such as the {@link mpc.protocolPrimitives.operations.LessThan} operation.
 * 
 * The basic design pattern for an operation extending this class would be as follows:
 * 
 * <pre>
 *  public void doStep(Primitives primitives) throws PrimitivesException {
 * 	...
 * 	switch(getCurrentStep()){
 * 	case 1:
 * 		if(generateRandomBits(getRandomNumberBitsNeeded(),primitives)){
 * 			// this makes sure all the randomness will be generated 
 * 			// before entering the first step.
 * 
 * 			//your code here
 * 		}
 * 		break;
 * 	...
 * 	default:
 * 		break;
 * 	}	
 * }
 * </pre>
 * 
 * It is important that you try generate the randomness at the highest possible
 * level for many operations in one single step, because random number generation
 * comes with a high overhead that can be amortized amongst the generated numbres.
 * 
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public abstract class RandBitsPregenerationOperationState extends GenericOperationState {

	/** state of the shared random bit generation needed for minimum in counting interseciton */
	private int generateRandomState = 0;
	
	/** contains the generated random numbers */
	private long[] randomness = null;
	
	/** keeps track of the already copied randomness */
	private int bitIndex=0;
	
	/**
	 * This method will copy the the needed random bits from the internal randomness
	 * array and return a new array containg those numbers.<br> <br>
	 * In most cases: <br>
	 * bitsNeeded = subOperation.getRandomNumbersNeeded(primitives)*primitives.getBitsCount()
	 * 
	 * @param bitsNeeded the #random bits needed 
	 * @return a new array containing the random numbers needed
	 */
	public long[] getRandomnessForSubOperation(int bitsNeeded){

		long[] bits = new long[bitsNeeded];
		System.arraycopy(randomness, bitIndex, bits, 0, bitsNeeded);
		bitIndex += bitsNeeded;
		
		return bits;
	}
	
	public boolean randomnessAlreadySet(){
		if (randomness != null){
			return true;
		}else {
			return false;
		}
	}
	
	/**
	 * Returns the number of shared random numbers needed
	 * 
	 * @param primitives the primitives.
	 * @return the number of shared random number bits needed
	 */
	public abstract int getRandomNumbersNeeded(Primitives primitives);
	
	/**
	 * Sets the required random number bits.
	 * @param bitShares the shares of random number bits.
	 */
	public void setRandomNumberBitShares(long[] bitShares){
		randomness = bitShares;
	}
	
	/**
	 * Generates bitwise random numbers needed for the less than operations
	 * @param randomNumbersNeeded
	 * @param primitives
	 * @return true if random bit generation has finished
	 * @throws PrimitivesException 
	 */
	protected boolean generateRandomBits(int randomNumbersNeeded, Primitives primitives) throws PrimitivesException{
		// is randomness already set ?
		if(randomness != null){
			return true;
		}
		
		IOperation [] rndGen = new IOperation[1];
		switch(generateRandomState){
		case 0:
			rndGen[0] = new BatchGenerateBitwiseRandomNumbers(new long[]{randomNumbersNeeded});
			rndGen[0].doStep(primitives);
			generateRandomState++;
			setSubOperations(rndGen);
			return false;
		case 1:
			rndGen = getSubOperations();
			rndGen[0].doStep(primitives);
			if(rndGen[0].isOperationCompleted()){
				randomness = rndGen[0].getFinalResult();
				generateRandomState = 0;
				return true;
			}
			return false;
		default:
			return false;
		}
	}
	
}
