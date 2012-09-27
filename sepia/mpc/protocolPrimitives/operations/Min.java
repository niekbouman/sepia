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
 * Minimum operation. This operation computs min(x1,...,xn) of n shared values.
 * min(x1,x2), min(x3,4), etc are computed pairwise. Then the min of the results
 * is computed and so on, until there is only 1 final result. Hence log n min(a,b)'s are computed.
 * 
 * There is also a sequential option.
 * Bitwise shared random numbers are needed for this operation. If nothing is specified, it will generate
 * the numbers needed on its own. However performance-wise it is much more efficient
 * if the random bits for multiple min operations are precomputed in a single batch and then
 * assigned to the operations by {@link Min.setRandomNumberBitShares(long[] bitShares)}
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class Min extends RandBitsPregenerationOperationState {

	private long[] intermediaryLT = null;

	//private long[] randomness = null;

	private int unkownAttributes = 0;
	private long knowledge = -1;

	private boolean reduceRounds = false;
	
	//private int bitIndex=0;

	//private int generateRandomState = 0;
	
	/** the current number index when computing sequential minimum */
	private int position = 1;

	/**
	 * Constructs a new minimum operation that by default uses fewer rounds.
	 * If you want to do sequential minimums between two values use the other 
	 * constructor!
	 * 
	 * data contains the numbers of which the minimum should be found
	 * knowledge contains additional knowledge about the numbers,
	 * 
	 * <p>
	 * <li> knowledge =	1 if all data[i] <= fieldSize/2,
	 *					0 if all data[i] > fieldSize/2,
	 *					-1 if neither of the above applies.
	 * </p>
	 * <br>
	 * 
	 * Only specify 1 or 0 if it holds for all numbers given in data[].
	 * if some numbers are > fieldsize/2 and some are <= fieldsize/2
	 * please specify -1.<br>
	 * 
	 * The minimum is computed between all even and odd indices is computed
	 * in parallel in each round. E.g.:<br>
	 * min(data[0],data[1],data[2],data[3],data[4]) -><br>
	 * a = min(data[0],data[1]), b = min(data[2],data[3]) c = data[4]<br>
	 * d = min(a,b) ,  c<br>
	 * final = min(d, c);<br><br>
	 *
	 * This process is then repeated until there is only 1 result which then is the final
	 * result.<br>
	 * 
	 * There is also the option to compute the minimum in sequenctial manner:
	 * E.g.:<br>
	 * a = min(data[0], data[1])<br>
	 * b = min(a, data[2])<br>
	 * c = min(b, data[3])<br>
	 * final = min(c, data[4]) ...<br>
	 *<br>
	 * In general if the minimum of very many values has to be computed it might be better to
	 * consider the sequential option (more rounds). The performance might be 
	 * better than the round optimized variant, since the memory is used more
	 * efficiently and the computational load on privacy peers is equal in every round.<br>
	 * 
	 * You have to try out yourself what works best for your application.
	 *
	 * @param data shares of numbers of which the minimum is coumputed
	 * @param knowledge additional information about the numbers
	 */
	public Min(long[] data, long knowledge){
		setInitialData(data);
		intermediaryLT = null;
		this.knowledge = knowledge;
		if(knowledge == -1){
			unkownAttributes = 3;
		}else {
			unkownAttributes = 1;
		}
		reduceRounds = true;
	}

	/**
	 * Constructs a new minimum operation.
	 * 
	 * data contains the numbers of which the minimum should be found
	 * knowledge contains additional knowledge about the numbers,
	 * 
	 * <p>
	 * <li> knowledge =	1 if all data[i] <= fieldSize/2,
	 *					0 if all data[i] > fieldSize/2,
	 *					-1 if neither of the above applies.
	 * </p>
	 * <br>
	 * 
	 * Only specify 1 or 0 if it holds for all numbers given in data[].
	 * if some numbers are > fieldsize/2 and some are <= fieldsize/2
	 * please specify -1.<br>
	 * 
	 * The minimum is computed between all even and odd indices is computed
	 * in parallel in each round. E.g.:<br>
	 * min(data[0],data[1],data[2],data[3],data[4]) -><br>
	 * a = min(data[0],data[1]), b = min(data[2],data[3]) c = data[4]<br>
	 * d = min(a,b) ,  c<br>
	 * final = min(d, c);<br><br>
	 *
	 * This process is then repeated until there is only 1 result which then is the final
	 * result.<br>
	 * 
	 * There is also the option to compute the minimum in sequenctial manner:
	 * E.g.:<br>
	 * a = min(data[0], data[1])<br>
	 * b = min(a, data[2])<br>
	 * c = min(b, data[3])<br>
	 * final = min(c, data[4]) ...<br>
	 *<br>
	 * In general if the minimum of very many values has to be computed it might be better to
	 * consider the sequential option (more rounds). The performance might be 
	 * better than the round optimized variant, since the memory is used more
	 * efficiently and the computational load on privacy peers is equal in every round.<br>
	 * 
	 * You have to try out yourself what works best for your application.
	 *
	 * @param data shares of numbers of which the minimum is coumputed
	 * @param knowledge additional information about the numbers
	 * @param moreRounds
	 */
	public Min(long[] data, long knowledge, boolean fewRounds){
		setInitialData(data);
		intermediaryLT = null;
		this.knowledge = knowledge;
		if(knowledge == -1){
			unkownAttributes = 3;
		}else {
			unkownAttributes = 1;
		}
		reduceRounds = fewRounds;
	}

	public void doStep(Primitives primitives) throws PrimitivesException {
		// minimum = lt(a,b)*a + (1-lt(a,b))*b
		IOperation[] subOperations = null;
		long[] data = null;
		long[] minimums = null;
		switch(getCurrentStep()){
		case 1: 
			if(reduceRounds){
				// generate bitwise shared random numbers
				if(generateRandomBits(getRandomNumbersNeeded(primitives) , primitives)){
					// bitwise random generation is finished!
					// initialize
					subOperations = new IOperation[(int)getInitialData().length/2];
					// account for odd length
					int length = (int)Math.ceil((double)getInitialData().length/2.0);

					for(int i = 0; i < subOperations.length; i++){
						data = new long[5];
						data[0] = getInitialData()[2*i]; // also minimums of previous rounds are saved here
						data[1] = getInitialData()[2*i + 1];
						data[2] = knowledge;
						data[3] = knowledge;
						data[4] = -1;
						subOperations[i] = new LessThan(data);
						// supply random bits
						int bitsNeeded =((LessThan)subOperations[i]).getRandomNumbersNeeded(primitives)
						* primitives.getBitsCount(); //numbers * bits per number
						//long[] bits = new long[bitsNeeded];
						//System.arraycopy(randomness, bitIndex, bits, 0, bitsNeeded);
						((LessThan)subOperations[i]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
						//bitIndex += bitsNeeded;
						// do first step
						subOperations[i].doStep(primitives);
					}
					// init intermediate results for less thans
					intermediaryLT = new long[subOperations.length];

					// initialize temporary minimums
					minimums = new long[length];
					// if we have an odd number of inputs
					if(subOperations.length < length){
						// add last value as potential minimum
						minimums[length - 1] = getInitialData()[2*length-2];
					}
					setIntermediaryResult(minimums);
					setSubOperations(subOperations);
					incrementCurrentStep();
				}
			}else{
				// generate bitwise shared random numbers
				if(generateRandomBits(getRandomNumbersNeeded(primitives) , primitives)){
					// bitwise random generation is finished!
					// initialize
					subOperations = new IOperation[1]; // only 1 at a time
					data = new long[5];
					if(position == 1){ // first round ?
						setIntermediaryResult(new long[]{getInitialData()[0]});
					}
					// use previous result and initialData[position]
					data[0] = getIntermediaryResult()[0]; // last minimum is saved here
					data[1] = getInitialData()[position];
					data[2] = knowledge;
					data[3] = knowledge;
					data[4] = -1;
					subOperations[0] = new LessThan(data);
					// supply random bits
					int bitsNeeded =((LessThan)subOperations[0]).getRandomNumbersNeeded(primitives)
					* primitives.getBitsCount(); //numbers * bits per number
					//long[] bits = new long[bitsNeeded];
					//System.arraycopy(randomness, bitIndex, bits, 0, bitsNeeded);
					((LessThan)subOperations[0]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
					//bitIndex += bitsNeeded;
					
					// do first step
					subOperations[0].doStep(primitives);
					setSubOperations(subOperations);
					incrementCurrentStep();
				}
			}
			break;
		case 2 : // compute less than steps until we have results
			if(reduceRounds){
				// we can just do 1 step at a time until we are completed
				subOperations = getSubOperations();
				boolean all_complete = true;
				for(int i = 0; i < subOperations.length; i++){
					if(!subOperations[i].isOperationCompleted()){
						subOperations[i].doStep(primitives);
						if(subOperations[i].isOperationCompleted()){
							// add result 
							intermediaryLT[i] = subOperations[i].getFinalResult()[0];
						}else{
							all_complete = false;
						}
					}
				}
				if(all_complete){
					// all less thans are complete, schedule multiplications
					// minimum = lt(a,b)*a + (1-lt(a,b))*b
					// we need 2 multiplications per less than
					subOperations = new IOperation[2*intermediaryLT.length];
					for(int i = 0; i < intermediaryLT.length; i++){
						// lt(a,b)*a
						data = new long[2];
						data[0] = getInitialData()[2*i];
						data[1] = intermediaryLT[i];
						subOperations[2*i] = new Multiplication(data); 
						subOperations[2*i].doStep(primitives);

						// (1-lt(a,b))*b
						data = new long[2];
						data[0] = getInitialData()[2*i + 1];
						data[1] = primitives.getMpcShamirSharing().modSubtract(1, intermediaryLT[i]);
						subOperations[2*i + 1] = new Multiplication(data);
						subOperations[2*i + 1] .doStep(primitives);
					}
					setSubOperations(subOperations);
					incrementCurrentStep();
				}
			}else{ // less then until complete
				getSubOperations()[0].doStep(primitives);
				if( getSubOperations()[0].isOperationCompleted()){
					// minimum = lt(a,b)*a + (1-lt(a,b))*b
					// we need 2 multiplications per less than
					subOperations = new IOperation[2];
					data = new long[2];
					// lt(a,b)*a
					data[0] = getInitialData()[0];
					data[1] = getSubOperations()[0].getFinalResult()[0];
					subOperations[0] = new Multiplication(data);
					subOperations[0].doStep(primitives);
					// (1-lt(a,b)) * b
					data = new long[2];
					data[0] = getInitialData()[position];
					data[1] = primitives.getMpcShamirSharing()
					.modSubtract(1, getSubOperations()[0].getFinalResult()[0]);
					subOperations[1] = new Multiplication(data);
					subOperations[1].doStep(primitives);
					setSubOperations(subOperations);
					incrementCurrentStep();
				}
			}
			break;
		case 3:
			if(reduceRounds){
				// finish off multiplications
				subOperations = getSubOperations();
				minimums = getIntermediaryResult();
				for(int i = 0; i < intermediaryLT.length; i++){
					subOperations[2*i].doStep(primitives);

					subOperations[2*i + 1].doStep(primitives);

					minimums[i] = primitives.getMpcShamirSharing()
					.modAdd(subOperations[2*i].getFinalResult()[0],
							subOperations[2*i + 1].getFinalResult()[0]);
				}

				//repeat until there is only 1 result left
				if(minimums.length == 1){
					setFinalResult(minimums);
					incrementCurrentStep();
				}else{
					// go back to Step 2 and execute Step 1 directly here
					setInitialData(minimums);
					setCurrentStep(2);
					
					//=== COPY PASTE FROM STEP 1 ==========
					// initialize
					subOperations = new IOperation[(int)getInitialData().length/2];
					// account for odd length
					int length = (int)Math.ceil((double)getInitialData().length/2.0);

					for(int i = 0; i < subOperations.length; i++){
						data = new long[5];
						data[0] = getInitialData()[2*i]; // also minimums of previous rounds are save here
						data[1] = getInitialData()[2*i + 1];
						data[2] = knowledge;
						data[3] = knowledge;
						data[4] = -1;
						subOperations[i] = new LessThan(data);
						// supply random bits
						int bitsNeeded =((LessThan)subOperations[i]).getRandomNumbersNeeded(primitives)
						* primitives.getBitsCount(); //numbers * bits per number
						//long[] bits = new long[bitsNeeded];
						//System.arraycopy(randomness, bitIndex, bits, 0, bitsNeeded);
						((LessThan)subOperations[i]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
						//bitIndex += bitsNeeded;
						// do first step
						subOperations[i].doStep(primitives);
					}
					// init intermediate results for less thans
					intermediaryLT = new long[subOperations.length];

					// initialize temporary minimums
					minimums = new long[length];
					// if we have an odd number of inputs
					if(subOperations.length < length){
						// add last value as potential minimum
						minimums[length - 1] = getInitialData()[2*length-2];
					}
					setIntermediaryResult(minimums);
					setSubOperations(subOperations);
					
					//=== COPY PASTE Finish FROM STEP 1 ==========
					
				}
			}else{
				// finish multimplications
				getSubOperations()[0].doStep(primitives);
				getSubOperations()[1].doStep(primitives);
				// save minimum in initialData[0]
				getIntermediaryResult()[0] = primitives.getMpcShamirSharing()
					.modAdd(getSubOperations()[0].getFinalResult()[0],
							getSubOperations()[1].getFinalResult()[0]);
				// increment round
				position++;
				if(position < getInitialData().length ){
					// go back to Step 2 and execute Step 1 directly here
					setCurrentStep(2);
					
					//=== COPY PASTE FROM STEP 1 ==========
					// initialize
					subOperations = new IOperation[1]; // only 1 at a time
					data = new long[5];
					if(position == 1){ // first round ?
						setIntermediaryResult(new long[]{getInitialData()[0]});
					}
					// use previous result and initialData[position]
					data[0] = getIntermediaryResult()[0]; // last minimum is saved here
					data[1] = getInitialData()[position];
					data[2] = knowledge;
					data[3] = knowledge;
					data[4] = -1;
					subOperations[0] = new LessThan(data);
					// supply random bits
					int bitsNeeded =((LessThan)subOperations[0]).getRandomNumbersNeeded(primitives)
					* primitives.getBitsCount(); //numbers * bits per number
					//long[] bits = new long[bitsNeeded];
					//System.arraycopy(randomness, bitIndex, bits, 0, bitsNeeded);
					((LessThan)subOperations[0]).setRandomNumberBitShares(getRandomnessForSubOperation(bitsNeeded));
					//bitIndex += bitsNeeded;
					
					// do first step
					subOperations[0].doStep(primitives);
					setSubOperations(subOperations);
					//=== COPY PASTE Finish FROM STEP 1 ==========
				}else{
					// we are finished!
					setFinalResult(getIntermediaryResult());
					incrementCurrentStep();
				}
			}
			break;
		default :
			// do nothing here
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
//		if(randomness != null){
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
//				randomness = rndGen[0].getFinalResult();
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
		if( !randomnessAlreadySet() ){
			// number of minimum operations
			return (getInitialData().length-1) * (int)unkownAttributes;
		}else{
			return 0;
		}
	}



}
