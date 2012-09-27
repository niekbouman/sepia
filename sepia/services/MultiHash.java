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

import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hashing class for use with BloomFilters, provides methods to get multiple different
 * hash values for a single input value. It is based on javax.crypto.Mac and supports
 * either SHA1 or MD5 as hashing algorithms.
 * 
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class MultiHash {
	
	private String ALGORITHM = "HmacMD5";
	/** used to generate the keys for the hmac functions */
	private final static long seed = (0xabcdef01L<<32 | 0x23456789L);
	private SecretKeySpec [] sks;
	private Mac [] hmac;
	private int bitsPerHash;
	private int nrHashes;
	
	private Mac [] manyHmac;
	
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
     * Returns a bit mask with the specified number of bits being '1'. 
     * E.g. getBitMask(4) = 0x0000000f;
     * @param nrBits Number of consecutive '1' bits 
     * @return bit mask
     */
	private int getBitMask(int nrBits){
		return (int)Math.pow(2, nrBits) - 1;
	}
	
	/**
	 * @return The maximum range of the hash functions.
	 */
	public int getRange() {
		return (int)Math.pow(2, bitsPerHash);
	}
	
	/**
	 * @return Number of hash values that this instance of Multihash produces
	 */
	public int getHashCount() {
		return nrHashes;
	}
	
	public String getAlgorithm() {
		return ALGORITHM;
	}
	
	/**
	 * Creates and initializes a new MultiHashfunction.
	 * @param HashCount Number of distinct hash values needed per query
	 * @param Range Range of the hash values, if no power of 2 is specified the next lager
	 * 		  power is assumed. Maximum allowed is 2^30.
	 * @param alg specifies the hashing algorithm, can be either "HmacMD5" or "HmacSHA1"
	 */
	public MultiHash(int HashCount, int Range, String alg){
		nrHashes = HashCount;
		Random m = new Random(seed);
		ALGORITHM = alg;
		// bits per hash value
		bitsPerHash = (int)Math.ceil(Math.log((double)Range)/Math.log(2.0));
		
		if(bitsPerHash == 0){
			bitsPerHash = 1;
		}
		
		// number of HmacMD5 or HmacSHA1 keys needed 
		// (HmacMD5 produces 128 bit [16 byte] hashes)
		// (HmacSHA1 produces 160 bit [20 byte] hashes)
		int numKeys;
		if(ALGORITHM.equals("HmacMD5")){
			numKeys = (int)Math.ceil((double)HashCount*bitsPerHash/128.0);
		}else if(ALGORITHM.equals("HmacSHA1")){
			numKeys = (int)Math.ceil((double)HashCount*bitsPerHash/160.0);
		}else {
			ALGORITHM = "HmacMD5";
			numKeys = (int)Math.ceil((double)HashCount*bitsPerHash/128.0);
		}
		
		sks = new SecretKeySpec[numKeys];
		hmac = new Mac[numKeys];
		
		// initialize hmac functions
		for(int i = 0; i < numKeys; i++){
			// generate some randomness
			byte [] keymaterial = new byte[64]; //secret keys needed for HmacMD5 are 64 bytes
			m.nextBytes(keymaterial);
			sks[i] = new SecretKeySpec(keymaterial, ALGORITHM);
			try{
				hmac[i] = Mac.getInstance(ALGORITHM);
				hmac[i].init(sks[i]);
			} catch (Exception e){
				e.printStackTrace();
			}
		}
		// initialize some more macs for slow test
		manyHmac = new Mac[nrHashes];
		sks = new SecretKeySpec[nrHashes];
		// initialize hmac functions
		for(int i = 0; i < nrHashes; i++){
			// generate some randomness
			byte [] keymaterial = new byte[64]; //secret keys needed for HmacMD5 are 64 bytes
			m.nextBytes(keymaterial);
			sks[i] = new SecretKeySpec(keymaterial, ALGORITHM);
			try{
				manyHmac[i] = Mac.getInstance(ALGORITHM);
				manyHmac[i].init(sks[i]);
			} catch (Exception e){
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Creates and initializes a new MultiHashfunction with default "HmacMD5"
	 * hashing algorithm
	 * @param HashCount Number of distinct hash values needed per query
	 * @param Range Range of the hash values, if no power of 2 is specified the next lager
	 * 		  power is assumed. Maximum allowed is 2^30.
	 */
	public MultiHash(int HashCount, int Range) {
		this(HashCount, Range, "HmacMD5");
	}
	
	/**
	 * Computes one or several hmac of the given input. The final output is
	 * generated by splitting up the hmac into several smaller parts.
	 * @param val String to hash
	 * @return array of different hash values for the given input
	 */
	public int [] hash(String val) {
		byte [] msg = null;
		try{
			msg = val.getBytes("ISO-8859-1");
		} catch (Exception e){
			e.printStackTrace();
		}
		return hash(msg);
	}
	
	/**
	 * Computes one or several hmac of the given input. The final output is
	 * generated by splitting up the hmac into several smaller parts.
	 * @param val Integer to hash
	 * @return array of different hash values for the given input
	 */
	public int [] hash(int val) {
		return hash(intToByteArr(val));
	}
	
	/**
	 * Computes one or several hmac of the given input. The final output is
	 * generated by splitting up the hmac into several smaller parts.
	 * @param val Byte array to hash
	 * @return array of different hash values for the given input
	 */
	public int [] hash(byte [] val) {
		int [] result = new int[nrHashes];
		long mask = 0;
		long h = 0, filter = 0;
		int beginByte = 0,shift = 0,bytesToCombine = 0;

		// depending on the algorithm a different number of bytes is produced as hashresult
		// HmacMD5: 	16 bytes
		// HmacSHA1: 	20 bytes
		byte [] buffer = null;
		// calculate the hashes and merge it to one big array
		for(int i = 0; i < hmac.length; i++) {
			byte [] hashval = hmac[i].doFinal(val);
			// initialize buffer in the first round 
			if (i == 0){
				buffer = new byte[hmac.length*hashval.length];
			}
			System.arraycopy(hashval, 0, buffer, i*hashval.length, hashval.length);
		}
			
		// select the correct bits from the big hash array
		for(int k = 0; k < nrHashes; k++){
			// compute offsets and indexes
			beginByte = ((k*bitsPerHash)>>>3);
			shift = (k*bitsPerHash)%8;
			bytesToCombine = 1 + ((shift + bitsPerHash-1)>>>3);

			//prepare the mask
			mask = ((long)getBitMask(bitsPerHash)) << shift;

			// concatenate partial hash of interest
			h = 0;
			for(int n = 0; n < bytesToCombine; n++){
				long unsigned = (long)(0x000000ff & buffer[beginByte+n]);
				h +=  (unsigned<<n*8);
			}

			// apply bit-mask
			filter = h & mask;
			// shift back
			result[k] = (int)(filter >>> shift);
			
		}

		return result;
	}
	
	/**
	 * Computes one or several hmac of the given input. The final output is
	 * generated by computing nrOfHashes different hmacs and truncating each to 
	 * the correct length. This method is slower than the normal
	 * hash method, especially when many hash values are needed.
	 * @param val Byte array to hash
	 * @return array of different hash values for the given input
	 */
	public int [] slowHash(byte [] val){
		int [] result = new int[nrHashes];
		long mask = 0;
		long h = 0;
		// compute hash and truncate to correct length
		for(int i = 0; i < nrHashes; i++){
			byte [] hashval = manyHmac[i].doFinal(val);
			//prepare the mask
			mask = ((long)getBitMask(bitsPerHash));
			
			// combine 4 bytes (since hash range is at most MAX_INTEGER)
			for(int n = 0; n < 4; n++){
				long unsigned = (long)(0x000000ff & hashval[n]);
				h +=  (unsigned<<n*8);
			}
			
			result[i] = (int)(h & mask);
			
		}
		return result;
	}
	
	public int [] slowHash(String val){
		byte [] msg = null;
		try{
			msg = val.getBytes("ISO-8859-1");
		} catch (Exception e){
			e.printStackTrace();
		}
		return slowHash(msg);
	}
	
	public int [] slowHash(int val) {
		return slowHash(intToByteArr(val));
	}

}
