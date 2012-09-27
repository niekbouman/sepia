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
import mpc.protocolPrimitives.operationStates.GenericOperationState;
import services.BloomFilter;



/**
 * Cardinality operation for shares of BloomFilter array positions.
 *
 * Computes the sum of all array positions or counters. The Cardinality then 
 * has to be computed locally via {@link BloomFilter#getCardinality(double, double, double, boolean)}
 * or {@link BloomFilter#getIntersectionCardinality(double, double, double, double, double, double, boolean)} 
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class BloomFilterCardinality extends GenericOperationState{
	
	/**
	 * Initializes a new cardinality operation. Takes the shares of all Bloom filter positions
	 * and computes the sum.
	 * @param data shares of the bloom filter positions
	 */
	public BloomFilterCardinality(long[] data){
		setInitialData(data);
	}
	
	public void doStep(Primitives primitives) {
		// just sum up all true bits or counters respectively
		if(getCurrentStep()==1){
			long [] result = new long[1];
			result[0] = 0;
			for(int i = 0; i < getInitialData().length; i++){
				result[0] = primitives.getMpcShamirSharing().modAdd(result[0], getInitialData()[i]);
			}
			setFinalResult(result);
			incrementCurrentStep();
		}
	}
	
	

}
