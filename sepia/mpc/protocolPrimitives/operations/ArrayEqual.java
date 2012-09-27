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
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class ArrayEqual extends GenericOperationState{
	
	public ArrayEqual(long[] data1, long[] data2){
		setInitialData(data1);
		setIntermediaryResult(data2);
	}

	@Override
	public void doStep(Primitives primitives) throws PrimitivesException {
		IOperation[] subOperations = null;
		switch(getCurrentStep()){
		case 1:
			// initialization
			// we want  data1 - data2 ?= 0
			for(int i = 0; i < getInitialData().length; i++){
				getInitialData()[i] = primitives.getMpcShamirSharing()
								.modSubtract(getInitialData()[i], getIntermediaryResult()[i]);
			}
			// schedule ArrayPower
			subOperations = new IOperation[1];
			subOperations[0] = new ArrayPower(getInitialData(), primitives.getMpcShamirSharing().getFieldSize()-1);
			subOperations[0].doStep(primitives);
			setSubOperations(subOperations);
			incrementCurrentStep();
			break;
		case 2:
			// doStep until finished
			getSubOperations()[0].doStep(primitives);
			if(getSubOperations()[0].isOperationCompleted()){
				// compute result of equal from result of power operation
				// equalresult = 1 - x^(q-1)
				long[] result = new long[getInitialData().length];
				for(int i = 0; i < result.length; i++){
					result[i] = primitives.getMpcShamirSharing().modSubtract(1, getSubOperations()[0].getFinalResult()[i]);
				}
				setFinalResult(result);
				incrementCurrentStep();
			}
			break;
		default:
			break;
		}
		
	}

}
