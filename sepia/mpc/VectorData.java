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

package mpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import services.Services;

/**
 * This class holds input data organized in vectors, e.g., a sequence of volume metric values or
 * a histogram. It is, for instance, used by the addition, entropy, and unique count protocol.
 * 
 * @author Martin Burkhart
 * 
 */
public class VectorData implements Serializable{
	private static final long serialVersionUID = -8392988851788249594L;

	private static final String CSV_SEPARATOR = ";";

	/** stores the number of vector elements. */
	protected int elementCount;
	
	/** holds the input data as read from the file. */
	protected long[] input;
	/** holds the output data, i.e., the result of the aggregation. */
	protected long[] output;
	
	/** If the result of the computation is not a vector it is stored in this field. 
	 * E.g., when entropy or unique count is computed, the distribution is summarized 
	 * in a single output value)*/
	protected Double aggregatedOutput;
	
	/**
	 * Default constructor.
	 */
	public VectorData() {
	}
	
	/**
	 * Reads the vector data from a file and stores it in the input array.
	 * The file is expected to contain a single row with comma-separated values.
	 * 	 
	 * @param file			file to read from
	 * @throws IOException 
	 */
	public void readDataFromFile(File file) throws IOException {
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		logger.log(Level.INFO, Services.getFilterPassingLogPrefix()+"Reading input file: "+file.getName());
		
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
		String line = bufferedReader.readLine();
		line = line.replace(",", CSV_SEPARATOR);
        String[] elements = line.split(CSV_SEPARATOR);

        elementCount = elements.length;
        input = new long[elementCount];
        output = new long[elementCount];

        for (int i = 0; i < elementCount; i++) {
            input[i] = Long.valueOf(elements[i].trim());
        }

		logger.log(Level.INFO, "Read "+elementCount+" elements.");
		bufferedReader.close();
	}


	/**
	 * Writes the output to a file. The output file will be comma-separated.
	 * If the aggregated result was set using {@link #setAggregatedOutput(Double)} then the
	 * aggregated result is written, otherwise the output vector is written.
	 * @param file file to write to
	 * @throws FileNotFoundException 
	 */
	public void writeOutputToFile(File file) throws FileNotFoundException {
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		logger.log(Level.INFO, Services.getFilterPassingLogPrefix()+"Writing output file: "+file.getName());
		
		StringBuffer line = new StringBuffer();
		if (aggregatedOutput != null) {
			line.append(aggregatedOutput);
		} else {
			for(int i=0; i<elementCount; i++) {
				if (i>0) {
					line.append(CSV_SEPARATOR);
					line.append(" ");
				}
				line.append(output[i]);
			}
		}
		PrintWriter writer = new PrintWriter(new FileOutputStream(file));
		writer.print(line.toString());
		writer.flush();
		writer.close();
	}
	
	// ----------------------
	// Getters and setters
	// ----------------------
	
	/**
	 * @return the elementCount
	 */
	public int getElementCount() {
		return elementCount;
	}

	/**
	 * @return the input
	 */
	public long[] getInput() {
		return input;
	}

	/**
	 * @param input the input to set
	 */
	public void setInput(long[] input) {
		this.input = input;
		elementCount = (input == null) ? 0 : input.length;
	}

	/**
	 * @return the output
	 */
	public long[] getOutput() {
		return output;
	}

	/**
	 * @param output the output to set
	 */
	public void setOutput(long[] output) {
		this.output = output;
		elementCount = (output == null) ? 0 : output.length;
	}

	/**
	 * @return the aggregatedResult
	 */
	public Double getAggregatedOutput() {
		return aggregatedOutput;
	}

	/**
	 * @param aggregatedOutput the aggregated output to set
	 */
	public void setAggregatedOutput(Double aggregatedOutput) {
		this.aggregatedOutput = aggregatedOutput;
	}

}
