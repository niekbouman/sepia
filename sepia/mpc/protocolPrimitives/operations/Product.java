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
 * Product Operation. This operation multiplies in each step the factors with even index and odd index:
 * x_0*x_1, x_2*x_3, x_4*x_5, ...
 *
 * This process is then repeated until there is only 1 result which then is the final
 * result.
 *
 * @author Dilip Many
 *
 */
public class Product extends GenericOperationState implements IOperation {
	/**
	 * creates a product sub-operation.
	 *
	 * @param data	array containing the shares to multiply
	 */
	public Product(long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the product operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {

		// step1: multiply the first batch of factors
		if(getCurrentStep() == 1) {
			IOperation[] subOperations = new IOperation[ (int)getInitialData().length/2 ];
			int subOperationIndex = 0;
			for(int i = 0; i < getInitialData().length; i+=2) {
				if( (i+1) < getInitialData().length ) {
					// create multiplications of factors
					long[] data = new long[2];
					data[0] = getInitialData()[i];
					data[1] = getInitialData()[i+1];
					subOperations[subOperationIndex] = new Multiplication(data);
					subOperations[subOperationIndex].doStep(primitives);
					subOperationIndex++;
				} else {
					long[] intermediaryResult = new long[1];
					intermediaryResult[0] = getInitialData()[i];
					setIntermediaryResult(intermediaryResult);
				}
			}
			setSubOperations(subOperations);
			incrementCurrentStep();
			return;
		}

		// step2: complete running multiplications and multiply next batch of factors
		// if there still are more than one
		if(getCurrentStep() == 2) {
			// complete running multiplications
			int subOperationsCount = getSubOperations().length;
			boolean multiplicationsFinished=true;
			for(int i = 0; i < subOperationsCount; i++) {
				getSubOperations()[i].doStep(primitives);
				multiplicationsFinished &= getSubOperations()[i].isOperationCompleted(); 
			}
			if(!multiplicationsFinished) {
				// Do another message exchange
				return;
			}
			
			// create new multiplications
			IOperation[] newSubOperations = null;
			if(getIntermediaryResult() != null) {
				newSubOperations = new IOperation[(subOperationsCount+1)/2];
			} else {
				newSubOperations = new IOperation[subOperationsCount/2];
			}
			int subOperationIndex = 0;
			for(int i = 0; i < subOperationsCount; i+=2) {
				if( (i+1) < subOperationsCount) {
					long[] data = new long[2];
					data[0] = getSubOperations()[i].getFinalResult()[0];
					data[1] = getSubOperations()[i+1].getFinalResult()[0];
					newSubOperations[subOperationIndex] = new Multiplication(data);
					newSubOperations[subOperationIndex].doStep(primitives);
					subOperationIndex++;
				} else {
					// check if there is a leftover factor in intermediaryResult[0] to multiply with,
					// otherwise save the last factor as intermediaryResult[0]
					if(getIntermediaryResult() != null) {
						long[] data = new long[2];
						data[0] = getSubOperations()[i].getFinalResult()[0];
						data[1] = getIntermediaryResult()[0];
						newSubOperations[subOperationIndex] = new Multiplication(data);
						newSubOperations[subOperationIndex].doStep(primitives);
						subOperationIndex++;
						setIntermediaryResult(null);
					} else {
						long[] intermediaryResult = new long[1];
						intermediaryResult[0] = getSubOperations()[i].getFinalResult()[0];
						setIntermediaryResult(intermediaryResult);
					}
				}
			}

			if(!(subOperationIndex > 0)) {
				// there is no more multiplication running; set final result
				setFinalResult( getIntermediaryResult() );
				incrementCurrentStep();
			} else {
				setSubOperations(newSubOperations);
			}
			return;
		}

		// step3: operation completed

		return;
	}
}
