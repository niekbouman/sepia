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
 * ArrayPower operation using square and multiply. 
 * 
 * normal square-and-multiply / fast-exponentiation algorithm:
 * 
 * long power(long x, unsigned long n) {
 *	long multiplySubStepResult = 1;
 *	long squareBaseSubStepResult = x;
 *	while (n > 0) {
 *		if( (n & 1) == 1 )
 *			multiplySubStepResult *= squareBaseSubStepResult;
 *		squareBaseSubStepResult *= squareBaseSubStepResult;
 *		n /= 2;
 *	}
 *	return multiplySubStepResult;
 * }
 *
 * @author Manuel Widmer, ETH Zurich
 */
public class ArrayPower extends GenericOperationState{

	/** exponent */
	private long ex ;
	private boolean firstMult;
	
	public ArrayPower(long[]data , long exponent){
		setInitialData(data);
		ex = exponent;
		firstMult = true;
	}
	

	@Override
	public void doStep(Primitives primitives) throws PrimitivesException {
		/* modifications to reduce number of multiplications:
		 * - when encountering the first 1 bit in the exponent:
		 *		multiplySubStepResult = squareBaseSubStepResult;
		 *	 afterwards do the "multiply" sub-step normally (see above)
		 * - in the last step don't do the "square base" sub-step
		 *	 (as the result will not be used)
		 *
		 * subOperations[0] will always be the square operation
		 * subOperations[1] is the multiplication (exept in the last step [case 3])
		 * where no square is needed anymore
		 * 
		 * initalData = squareResult
		 * intermediaryResults = multiplication result
		 * 
		 */

		IOperation[] subOperations = null;
		switch(getCurrentStep()){
		case 1:
			if((ex & 1L) == 1){
				// multiply  1 * initial data
				setIntermediaryResult(getInitialData());
				if(ex == 1){// we actually don't need to compute ^1
					setFinalResult(getInitialData());
					setCurrentStep(99);
				}
				firstMult = false;
			}
			// always square
			subOperations = new IOperation[1];
			subOperations[0] = new ArrayMultiplication(getInitialData(), getInitialData());
			subOperations[0].doStep(primitives);
			setSubOperations(subOperations);
			
			//unsigned shift
			ex = ex>>>1;
			if(ex == 1){
				setCurrentStep(3); // directly jump to last step
			}else{
				incrementCurrentStep();
			}
			break;
		case 2:
			// finish square step
			getSubOperations()[0].doStep(primitives);
			setInitialData(getSubOperations()[0].getFinalResult());
			if(getSubOperations().length == 2){
				 // finish multiplication if present
				getSubOperations()[1].doStep(primitives);
				setIntermediaryResult(getSubOperations()[1].getFinalResult());
			}
			
			if((ex & 1L) == 1){
				// multiply
				if(firstMult){
					firstMult = false;
					// we dont need to compute 1*x 
					setIntermediaryResult(getInitialData());
					// initialize for square 
					subOperations = new IOperation[1];
				}else{
					// initialize for square & mult
					subOperations = new IOperation[2];
					subOperations[1] = new ArrayMultiplication(getIntermediaryResult(), getInitialData());
					subOperations[1].doStep(primitives);
				}
			}else{
				// initialize for square
				subOperations = new IOperation[1];
			}
			//square
			subOperations[0] = new ArrayMultiplication(getInitialData(), getInitialData());
			subOperations[0].doStep(primitives);
			setSubOperations(subOperations);
			//unsigned shift
			ex = ex>>>1;
			if(ex == 1){
				incrementCurrentStep(); // last step
			}
			break;
		case 3:
			// last step only multiplication is needed no squaring anymore
			// finish square step
			getSubOperations()[0].doStep(primitives);
			setInitialData(getSubOperations()[0].getFinalResult());
			if(getSubOperations().length == 2){
				 // finish multiplication if present
				getSubOperations()[1].doStep(primitives);
				setIntermediaryResult(getSubOperations()[1].getFinalResult());
			}
			
			// schedule multiplication
			// multiply
			if(firstMult){
				// we dont need to compute 1*x 
				// we can directly set the final Result
				setFinalResult(getInitialData());
				setCurrentStep(99);
			}else{
				// schedule the last multiplication
				subOperations = new IOperation[1];
				subOperations[0] = new ArrayMultiplication(getIntermediaryResult(), getInitialData());
				subOperations[0].doStep(primitives);
				setSubOperations(subOperations);
				incrementCurrentStep();
			}
			break;
		case 4:
			// finish the last multiplication
			getSubOperations()[0].doStep(primitives);
			setFinalResult(getSubOperations()[0].getFinalResult());
			incrementCurrentStep();
			break;
		default:
			break;
		}
	}
	
	

}
