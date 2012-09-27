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

package services;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.logging.Logger;



/**
 * Provides a (counting/spectral) BloomFilter structure of arbitrary Size, methods for filter
 * manipulations such as insert or element check, etc.
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class BloomFilter {
	
	private int [] bf;
	private boolean b_cnt;
	private MultiHash mhash;
	private long nonZeroCounter;
	
	/**
	 * Converts an integer to a byte array
	 * @param val value to convert
	 * @return [0]..[3] MSB to LSB representation of val
	 */
    private byte[] intToByteArr(int val) {
        byte[] buffer = new byte[4];
   
        // >>> is unsigned  and >> signed bitshift
        buffer[0] = (byte) (val >>> 24);
        buffer[1] = (byte) (val >>> 16);
        buffer[2] = (byte) (val >>> 8);
        buffer[3] = (byte) val;
        
        return buffer;
    }
    
    /**
     * Iterates over the whole array and counts
     * @return the sum of all values in the internal array
     */
    private long arraySum(){
    	long sum = 0;
    	for(int a : bf){
    		sum += a;
    	}
    	return sum;
    }
    
    /**
     * Computes Logarithm to base 2
     * @param a input value
     * @return log2(a)
     */
    private static double log2(double a){
    	return Math.log(a)/Math.log(2);
    }
	
	/**
	 * Outputs a suggestion for practical BloomFilter parameters based on number of 
	 * inserted elements and the desired false positive rate.
	 * The suggested Size is about two orders of magnitude larger than the number of inserted elements.
	 * @param elements expected number of elements which will be inserted (Max = 2^31 -1)
	 * @param fpr desired false positive rate (e.g. 1e-4)
	 * @return [0] suggested Size, [1] Number of hash functions, [2] fpr with these parameters
	 */
	public static double [] getParameterEstimate(int elements, double fpr){
		double [] result = new double[3];
		// ideal filter length
		double ideal = elements*1.44*log2(1.0/fpr);
		// slightly pessimistic estimate of filter length: next greater power of 2
		result[0] = (int)Math.pow(2, Math.ceil(log2(ideal)));
		// number of needed hash values per element  ln(2)* m/n;  ln(2) = 0.6931
		// ceil: 1 too many is preferred over 1 too few
		result[1] = (int)Math.ceil(0.6931*result[0]/elements);
		// actual false positive Rate with parameters computed above
		result[2] = getFalsePositiveRate(elements, result[0], result[1]);
		return result;
	}

	/**
	 * Computes the false positive rate of a BloomFilter with dimensions as specified
	 * @param n number of items that will be inserted
	 * @param m length of the BloomFilter
	 * @param k number of hash values per item
	 * @return the false positive rate
	 */
	public static double getFalsePositiveRate(double n, double m, double k){
		return Math.pow((1-Math.pow((1-1/m),(n*k))), k);
	}
	
	/**
	 * Estimates the number of inserted elements based on number
	 * of positions not equal to zero, number of hash functions and 
	 * filter size.
	 * In case of a counting Bloom filter we can even give exact cardinalities.
	 * @param nonZeros sum of all array entries 
	 * @param m BloomFilter dimension or length
	 * @param k Number of hash functions used
	 * @param counting true if it is a counting BloomFilter
	 * @return the expected number of inserted elements
	 */
	public static double getCardinality (double nonZeros, double m, double k, boolean counting) {
		if(counting){
			return nonZeros/k;
		}else{
			if(nonZeros >= m){// filter is full, this would lead to n=inf
				return 5*m/k;
			}else{
				return Math.log(1.0-nonZeros/m)/(k*Math.log(1-1.0/m));
			}
		}
	}
	
	/**
	 * Computes the expected number of bits set true
	 * @param n Number of inserted elements
	 * @param m Length of the filter
	 * @param k Number of hash functions
	 * @return the expected number of bits set true
	 */
	public static double getExpectedTrueBits(double n, double m, double k){
		return m*(1-Math.pow(1-1/m,k*n));
	}

	private static double inverseExpectedTruebitsIntersection(double t_av, double t_final,
			double p, double m, double k){

		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).info("inverseExpectedTruebitsIntersection: t_av="+t_av+", t_final: "+t_final+", p="+p+", m="+m+", k="+k);
		if(t_final < 1){
			return 0;
		}

		double f_n = 0;
		double derivative = 0;
		// relative error of estimate
		final double epsilon = 0.0001;

		// we try to find n = number of items in the intersection
		// we start at a value of half of the average
		double n = 0;//getCardinality(t_av, m, k, false)/2;
		double n_new =  0;
		int stepcount = 0;
		do{
			n = n_new;
			stepcount++;
			// We want to find n, such that
			// t_final = S(t_av, n)
			// hence we want to find the zeros of
			// f(n) = S(t_av, n) - t_final

			// as derived in thesis
			f_n = m - m*Math.pow((1-1/m),n*k) + 
					Math.pow(m,1-p)*Math.pow(1-1/m, k*n*(1-p))*
					Math.pow(t_av-m*(1-Math.pow(1-1/m,k*n)), p) - t_final;


			derivative = k*m*Math.log(1-1/m)*Math.pow(1-1/m,k*n) *
						(
							p*(m-t_av)*Math.pow(m * Math.pow(1-1/m, k*n),-p) *
							Math.pow(m * (Math.pow(1-1/m,k*n) - 1) + t_av,p-1)
						+
							Math.pow(m * Math.pow(1-1/m,k*n), -p) *
							Math.pow(m *(Math.pow(1-1/m,k*n) -1) + t_av,p) - 1						
						);
			// sanity checks: if conditions apply,
			// we won't get significantly closer in next iterations
			//if(Double.isNaN(f_n/derivative)){
			if(Double.isNaN(n)){
				n = 0;
				break;
			}

			// newton step
			n_new = n - f_n/derivative;	

		}while(Math.abs(n-n_new) > epsilon);

		//System.out.println("Step: "+stepcount+", n: "+n);
		if (n < 0)
			return 0;
		else 
			return n;
	}
	
	
	/**
	 * Estimates the number of elements in the intersection A*B of two sets.
	 * Straight forward computation is not possible for intersection,
	 * because the same bit could be set by an element in A but not in A*B
	 * and by an element in B but not in A*B simultaneously.
	 * <br /><br />
	 * If intersectionCount > 1, it is assumed that the BloomFilter was created
	 * as follows: (initial filters Z and B)<br />
	 * 1. Z = Z*B;<br />
	 * 2. Z = Z*C;<br />
	 * 3. Z = Z*D;<br />
	 * 4. ...<br />
	 * Where |B| = |C| = |D| = ... all represent sets of equal cardinality resp
	 * very similar number of true bits. Hence Function call should be:<br />
	 *  (x,x, t_inter);
	 * 
	 * @param t_AV Number of true bits in the first/average filter
	 * @param tB Number of true bits in the second filter (only used if p == 2)
	 * @param t_Inter Number of true bits in the intersection
	 * @param k Number of hash functions
	 * @param m Length of the BloomFilter
	 * @param p Indicates how many sets have been intersected
	 * 			to obtain the BloomFilter which should be estimated now.
	 * @param counting true if it is a counting BloomFilter
	 * @return the expected number of set elements in the intersection
	 */
	public static double getIntersectionCardinality(double t_av, double tB, double t_Inter,
			double k, double m, double p, boolean counting){
		if(t_Inter < 1){
			return 0;
		}else{
//		if(p == 2){// as in paper Papapetrou 2010
//			return (Math.log(m-(t_Inter*m-t_av*tB)/(m-t_av-tB+t_Inter))-Math.log(m))
//								/
//						(k*Math.log(1-1/m));
		
		// my own calculation...
			return inverseExpectedTruebitsIntersection(t_av, t_Inter, p, m, k);
		}
	}
	
	/**
	 * Estimates the number of true bits in a BloomFilter obtained by intersection 
	 * of two BloomFilters A,B. 
	 * @param t_av Number of true bits in the first filter
	 * @param tbB Number of true bits in the second filter
	 * @param cardInt Number of elements in the intersection
	 * @param k Number of hash functions
	 * @param m Length of the BloomFilter
	 * @return
	 */
	public static int getIntersectionEstimatedTrueBits(double t_av, double tbB, double cardInt,
			double k, double m, double p){
		
		return (int)((t_av*tbB)+m*(1-Math.pow((1-1/m),k*cardInt))*(m-t_av-tbB)
									/
						(m*Math.pow((1-1/m),k*cardInt)));
	}
	
	/**
	 * Returns the actual length a BloomFilter will have, when it is instantiated with
	 * the specified value
	 * @param size Value you would use to construct the BloomFilter
	 * @return the next larger Power of two
	 */
	public static int getNextPowerOfTwo(int size){
		// if we typecast (int)Double.NaN  == 0;
		int bitsPerHash = (int)Math.ceil(Math.log(size)/Math.log(2.0));
		if(bitsPerHash <= 0){
			bitsPerHash = 1;
		}
		return (int)Math.pow(2, bitsPerHash);
	}
	
	/**
	 * Constructor
	 * @param hashes number of hash functions to use
	 * @param size length of the BloomFilter array, if no power of 2 is specified the next lager
	 * 		  power is assumed.
	 * @param counting true if a counting BloomFilter is needed
	 */
	public BloomFilter(int hashes ,int size, boolean counting){
		int Range = getNextPowerOfTwo(size);
		bf = new int[Range];
		b_cnt = counting;
		nonZeroCounter = 0;
		mhash = new MultiHash(hashes, Range);
	}
	
	/**
	 * Constructs a BloomFilter from an already present filter data array
	 * @param hashes number of hash functions to use
	 * @param filterarray underlying data structure
	 * @param counting true if a counting BloomFilter is needed
	 */
	public BloomFilter(int hashes, int [] filterarray, boolean counting){
		int size = filterarray.length;
		// make sure filter has power of 2
		int Range = getNextPowerOfTwo(size);
		bf = new int[Range];
		// copy array
		for(int i = 0; i < filterarray.length; i++){
			bf[i] = filterarray[i]; 
		}
		
		
		b_cnt = counting;
		nonZeroCounter = arraySum();
		mhash = new MultiHash(hashes, bf.length);
	}
	
	/**
	 * Constructs a BloomFilter from an already present filter data array
	 * @param hashes number of hash functions to use
	 * @param filterarray underlying data structure, (long) will be truncated to (int)
	 * @param counting true if a counting BloomFilter is needed
	 */
	public BloomFilter(int hashes, long [] filterarray, boolean counting){
		int size = filterarray.length;
		// make sure filter has power of 2
		int Range = getNextPowerOfTwo(size);
		bf = new int[Range];
		for(int i = 0; i < filterarray.length; i++){
			bf[i] = (int)filterarray[i]; // truncate to int
		}
		b_cnt = counting;
		nonZeroCounter = arraySum();
		mhash = new MultiHash(hashes, bf.length);
	}

	/**
	 * String representation of the BloomFilter array
	 */
	public String toString(){
		StringBuffer res = new StringBuffer();
		for(int i = 0; i < bf.length; i++){
			res.append(bf[i]).append(", ");
		}
		return res.toString();
	}

	/**
	 * @return the range of the BloomFilter
	 */
	public int getRange(){
		return bf.length;
	}
	
	/**
	 * @return the number of hash values computed per item
	 */
	public int getHashCount() {
		return mhash.getHashCount();
	}
	
	/**
	 * Resets the BloomFilter to all zeros
	 */
	public void reset(){
		bf = new int[bf.length];
		nonZeroCounter = 0;
	}
	
	
	/**
	 * @return the underlying array of the BloomFilter. Manipulations are possible.
	 */
	public int [] getArray(){
		return bf;
	}
	
	/**
	 * @return the sum of all array entries
	 */
	public long getNonZeros(){
		return nonZeroCounter;
	}
	

	
	/**
	 * Inserts a given String value into the BloomFilter
	 * @param val value to insert, assumes ISO-8859-1 encoding
	 * @return true if there was a collision during insertion 
	 * (either false positive, or value already present)
	 */
	public boolean insert(String val, int count) {
		boolean collision = false;
		try{
			// use a specific encoding to guarantee the same outcome on all platforms
			byte [] msg = val.getBytes("ISO-8859-1");
			collision = insert(msg, count);
		}catch(Exception e){
			e.printStackTrace();
		}
		return collision;
	}
	
	public boolean insert(String val) {
		return insert(val, 1);
	}

	
	/**
	 * Inserts a given byte array into the BloomFilter
	 * @param val value to insert
	 * @param count how many times to insert the item
	 * @return true if there was a collision during insertion 
	 * (either false positive, value already present or counter overflow)
	 * <br> Note: If the BloomFilter is counting, then false is returned unless 
	 * there was a counter overflow, then the counter will stay at max and true is returned.
	 */
	public boolean insert(byte [] val, int count) {
		int coll_count = 0;
		// compute hashes
		int [] indices = mhash.hash(val);
		// used to save difference to Integer.MAX_VALUE in case of counter overflow
		int temp;
		for(int i = 0; i < indices.length; i++){
			// insert and in case of non-counting check for collisions
			if(b_cnt){
				temp = Integer.MAX_VALUE - bf[indices[i]]; 
				bf[indices[i]] += count; // there will never be a collision in counting bf
				nonZeroCounter += count;
				if(bf[indices[i]] < 0) {// counter overflow
					bf[indices[i]] = Integer.MAX_VALUE; // set counter back to max value
					nonZeroCounter -= (count - temp);
				}
			}else{
				if(bf[indices[i]] > 0){
					coll_count++;
				}else{
					bf[indices[i]] = 1;
					nonZeroCounter++;
				}
			}
		}
		
		// we have a fp only if there was a collision for each hash function
		if(coll_count >= indices.length)
			return true;
		else
			return false;
	}
	
	public boolean insert(byte [] val) {
		return insert(val, 1);
	}
	
	/**
	 * Inserts a given integer into the BloomFilter
	 * @param val value to insert
	 * @return true if there was a collision during insertion 
	 * (either false positive, or value already present)
	 */
	public boolean insert(int val){
		return insert(intToByteArr(val));
	}
	
	public boolean insert(int val, int count){
		return insert(intToByteArr(val), count);
	}
	
	/**
	 * Checks if the specified String is already stored in the BloomFilter
	 * @param val string to check, assumes ISO-8859-1 encoding
	 * @return true if value was found
	 */
	public boolean check(String val) {
		boolean found = false;
		try{
			// use a specific encoding to guarantee the same outcome on all platforms
			byte [] msg = val.getBytes("ISO-8859-1");
			found = check(msg);
		}catch(Exception e){
			e.printStackTrace();
		}
		return found;
	}
	
	/**
	 * Checks if the specified byte array is already stored in the BloomFilter
	 * @param val value to check
	 * @return true if value was found
	 */
	public boolean check(byte [] val){
		// assume value is in filter
		boolean found = true;
		
		// compute hashes
		int [] indices = mhash.hash(val);
		
		for(int i = 0; i < indices.length; i++){
			
			// if for any hash function the value is not inside
			// then return false
			if(bf[indices[i]] <= 0){
				found = false;
				break;
			}
			
		}
		return found;
	}
	
	/**
	 * Checks if the specified integer is already stored in the BloomFilter
	 * @param val value to check
	 * @return true if value was found
	 */
	public boolean check(int val){
		return check(intToByteArr(val));
	}
	
	
	/**
	 * For a counting Bloom filter this function returns the number of times that
	 * an element is present in the filter. This is the minimum of all array positions
	 * at the hashed values.
	 * @param val value to count
	 * @return the count of this element
	 */
	public int getCount(byte[] val){
		int result = Integer.MAX_VALUE;
		// compute hashes
		int [] indices = mhash.hash(val);

		for(int i = 0; i < indices.length; i++){
			// find the minimum of all counters
			if(bf[indices[i]] <= result){
				result = indices[i];
			}
		}
		return result;
	}
	
	public int getCount(int val){
		return getCount(intToByteArr(val));
	}
	
	
	/**
	 * Tries to remove an element from the BloomFilter. If it is a non-counting filter removing
	 * elements is not allowed and the function will always return false. If it is a 
	 * counting BloomFilter and the element-check fails, then false is returned, too.
	 * @param val value to remove
	 * @return true if remove was successful
	 */
	public boolean remove(byte [] val) {
		if(!b_cnt || !check(val)){
			return false;
		}
		
		// compute hashes
		int [] indices = mhash.hash(val);
		for(int i = 0; i < indices.length; i++){
			bf[indices[i]]--; //remove
			nonZeroCounter--;
		}
		
		return true;
	}
	
	/**
	 * Tries to remove an element from the BloomFilter. If it is a non-counting filter removing
	 * elements is not allowed and the function will always return false. If it is a 
	 * counting BloomFilter and the element-check fails, then false is returned, too.
	 * @param val value to remove, assumes ISO-8859-1 encoding
	 * @return true if remove was successful
	 */
	public boolean remove(String val) {
		boolean removed = false;
		try{
			// use a specific encoding to guarantee the same outcome on all platforms
			byte [] msg = val.getBytes("ISO-8859-1");
			removed = remove(msg);
		}catch(Exception e){
			e.printStackTrace();
		}
		return removed;
	}
	
	public boolean isCounting(){
		return b_cnt;
	}
	
	
	public boolean isComparable(BloomFilter rhs){
		if(rhs.getRange() == this.getRange() && 
		   rhs.getHashCount() == this.getHashCount() &&
		   rhs.isCounting() == this.isCounting()){
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * Computes the intersection of two BloomFilters.
	 * The intersection is defined as min(this[i], rhs[i]) for all i.
	 * @param rhs BloomFilter
	 * @param cnt specify whether the resulting BloomFilter should be counting or not
	 * @return a BloomFilter representing the intersection of this and rhs
	 */
	public BloomFilter intersect(BloomFilter rhs, boolean cnt){
		BloomFilter filt = new BloomFilter(this.getHashCount(), this.bf, cnt);
		if(!isComparable(rhs)){
			return null;
		}else{
			// same for counting and non counting
			for(int i = 0; i < bf.length; i++){
				filt.bf[i] = Math.min(bf[i], rhs.getArray()[i]);
				if(!cnt){
					if(filt.bf[i] > 1){
						filt.bf[i] = 1;
					}
				}
			}
		}
		// update nonzerocounter
		filt.nonZeroCounter = filt.arraySum();
		return filt;
	}
	
	/**
	 * Computes the union of two BloomFilters.
	 * The union is defined as max(this[i], rhs[i]) for all i or
	 * this[i] + rhs[i] in case of a counting BloomFilter
	 * 
	 * @param rhs
	 * 	 * @param cnt specify whether the resulting BloomFilter should be counting or not
	 * @return a BloomFilter representing the union of this and rhs
	 */
	public BloomFilter join(BloomFilter rhs, boolean cnt){
		BloomFilter filt = new BloomFilter(this.getHashCount(), this.bf, this.isCounting());
		if(!isComparable(rhs)){
			return null;
		}else{
			if(cnt){ // sum
				for(int i = 0; i < bf.length; i++){
					filt.bf[i] = (bf[i] + rhs.getArray()[i]);
				}
			}else{ // only 1 or 0
				for(int i = 0; i < bf.length; i++){
					filt.bf[i] = Math.max(bf[i], rhs.getArray()[i]);
				}
			}
		}
		// update nonzerocounter
		filt.nonZeroCounter = filt.arraySum();
		return filt;
	}
	
	public static BloomFilter union(BloomFilter[] BFs, boolean counting){
		BloomFilter result = BFs[0];
		for(int i = 1; i < BFs.length; i++){
			result = result.join(BFs[i], counting);
		}
		return result;
	}
	
	public static BloomFilter intersection(BloomFilter[] BFs, boolean counting){
		BloomFilter result = BFs[0];
		for(int i = 1; i < BFs.length; i++){
			result = result.intersect(BFs[i], counting);
		}
		return result;
	}
	
	public static BloomFilter thresholdUnion(BloomFilter[] BFs, int T, boolean learnCount){
		BloomFilter result = union(BFs, true);
		result = result.reduceBy(T);
		if(!learnCount){
			// set all values > 1 to 1
			for(int i = 0; i < result.bf.length; i++){
				if(result.bf[i] > 1){
					result.bf[i] = 1;
				}
			}
		}
		return result;
	}
	
	public static BloomFilter weightedIntersection(BloomFilter[] Keys, BloomFilter[] Weights, int Tk, int Tw, boolean learnCount){
		BloomFilter k = thresholdUnion(Keys, Tk, false);
		BloomFilter w = thresholdUnion(Weights, Tw, true);
		BloomFilter result = k;
		result.b_cnt = learnCount;
		for(int i = 0; i < result.bf.length; i++){
			// cancel all key's that didn't reach both thresholds
			result.bf[i] = result.bf[i]*w.bf[i];
			if(!learnCount){
				if(result.bf[i] > 1){
					result.bf[i] = 1;
				}
			}
		}
		result.nonZeroCounter = result.arraySum();
		return result;
	}
	
	/**
	 * Returns a new BloomFilter where all entries smaller than the 
	 * specified threshold are set to zero. All entries greater or equal
	 * the threshold will remain the same.
	 * 
	 * @param threshold
	 * @return initial BloomFilter reduced by threshold
	 */
	public BloomFilter reduceBy(int threshold){
		BloomFilter filt = new BloomFilter(this.getHashCount(), this.bf, this.isCounting());
		for(int i = 0; i < filt.getArray().length; i++){
			if(filt.bf[i] < threshold){
				filt.bf[i] = 0;
			}
		}
		filt.nonZeroCounter = filt.arraySum();
		return filt;
	}


	public void writeToFile(String fileName) throws Exception {
		// NOTE: changing this class or MultiHash may break the file format compatibility

		FileWriter fw = new FileWriter(fileName);
		BufferedWriter bw = new BufferedWriter(fw);
		PrintWriter pw = new PrintWriter(bw);
		// Bloom Filter parameters
		pw.println("Bloom Filter parameters: isCounting, hash function count, filter length");
		pw.println(b_cnt);
		pw.println(mhash.getHashCount());
		pw.println(bf.length);
		// BF positions
		for(int iPosition = 0; iPosition < bf.length; iPosition++) {
			pw.println(bf[iPosition]);
		}
		pw.close();
		bw.close();
		fw.close();
	}
}

