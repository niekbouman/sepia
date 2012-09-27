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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import mpc.ShamirSharing;

/**
 * A helper class offering some useful constants and methods.
 *
 * @author Lisa Barisic, ETH Zurich
 */
public class Services {

    public static final String ARG_FILE_ADDITION_DOUBLE = "-a";
    public static final String ARG_FILE_COMPARISON_DOUBLE = "-c";
    public static final String ARG_FILE_COMPARISON_BIG_INTEGER = "-cb";
    public static final String CSV_SEPARATOR = ",";

    public static void main(String[] args) {
        int numberOfInputFiles;
        String[] inputFiles;
        String[] outputFiles;
        int j;
        boolean comparisonSuccessful;

        comparisonSuccessful = false;

        System.out.println("Arguments: ");
        for (String s : args) {
            System.out.println(s);
        }
        System.out.println("");

        if (args != null) {
            if (args.length < 4) {
                usage();
            } else {
                try {
                    numberOfInputFiles = Integer.valueOf(args[1]);
                    inputFiles = new String[numberOfInputFiles];
                    outputFiles = new String[args.length - numberOfInputFiles - 2];

                    j = 0;
                    for (int i = 2; i < (numberOfInputFiles + 2); i++) {
                        inputFiles[j] = args[i];
                        System.out.println("Input File: " + inputFiles[j]);
                        j++;
                    }
                    j = 0;
                    for (int i = (numberOfInputFiles + 2); i < args.length; i++) {
                        outputFiles[j] = args[i];
                        System.out.println("Output File: " + outputFiles[j]);
                        j++;
                    }

                    if (args[0].equals(ARG_FILE_ADDITION_DOUBLE)) {
                        compareFiles(inputFiles, outputFiles, false, true);
                    } else if (args[0].equals(ARG_FILE_COMPARISON_DOUBLE)) {
                        comparisonSuccessful = compareFiles(inputFiles, outputFiles, false, false);
                    } else if (args[0].equals(ARG_FILE_COMPARISON_BIG_INTEGER)) {
                        comparisonSuccessful = compareFiles(inputFiles, outputFiles, true, false);
                    } else {
                        System.out.println("Unknown argument: " + args[0]);
                        usage();
                        System.exit(0);
                    }

                } catch (Exception e) {
                    System.out.println("\n\n***Comparison NOT successful (" + e.getMessage() + "! *****\n");
                    usage();
                }
            }

            if (!args[0].equals(ARG_FILE_ADDITION_DOUBLE)) {
                if (comparisonSuccessful) {
                    System.out.println("\n\n***Comparison successful! *****\n");
                } else {
                    System.out.println("\n\n***Comparison NOT successful! *****\n");
                }
            } else {
                System.out.println("\n\n***File generated! *****\n");
            }


        } else {
            usage();
        }
    }

    public static void usage() {
        System.out.println("\n" + getApplicationName() + ": Comparison of file sum");
        System.out.println("\njava -jar sepia.jar <arguments>");
        System.out.println("The main class of the jar must be services.Services.");
        System.out.println("To change the main class: 'jar -fue sepia.jar services.Services");
        System.out.println("\n-----------------------------------------------------");
        System.out.println(ARG_FILE_ADDITION_DOUBLE + "  <numberOfInputFiles> <InputFile1> ... <inputFileN> <resultFile>");
        System.out.println("\tExample: java -jar sepia.jar " + ARG_FILE_ADDITION_DOUBLE + " 3 myFile01 myFile02 myFile03 myResultFile");
        System.out.println("\tAdds the items of all input files and writes the result to the result file.");
        System.out.println("\t(Data types: double)");
        System.out.println("\n-----------------------------------------------------");
        System.out.println(ARG_FILE_COMPARISON_DOUBLE + "  <numberOfInputFiles> <InputFile1> ... <inputFileN> <resultFile01> ... <resultFileM>");
        System.out.println("\tExample: java -jar sepia.jar " + ARG_FILE_COMPARISON_DOUBLE + " 3 myFile01 myFile02 myFile03 myResultFile01 myResultFile02");
        System.out.println("\tAdds the items of all input files and compares them to the ");
        System.out.println("\tcomputed sum of items of all output files (Data types: double)");
        System.out.println("\n-----------------------------------------------------");
        System.out.println(ARG_FILE_COMPARISON_BIG_INTEGER + "  <numberOfInputFiles> <InputFile1> ... <inputFileN> <resultFile01> ... <resultFileM>");
        System.out.println("\tExample: java -jar sepia.jar " + ARG_FILE_COMPARISON_BIG_INTEGER + " 3 myFile01 myFile02 myFile03 myResultFile01 myResultFile02");
        System.out.println("\tAdds the items of all input files and compares them to the ");
        System.out.println("\tcomputed sum of items of all output files (Data types: BigInteger)");
        System.out.println("\n-----------------------------------------------------");
        System.out.println("\nFile format (input and output files, comma-separated): timeStamp, startTime, stopTime, data[]");
        System.out.println("\n-----------------------------------------------------");
    }

    /**
     * Reads the columns of the files in the input file list, computes the
     * sum of each item and compares it to the sum of the items in the output
     * files. The first line of the file is expected to be a header line and
     * is thus ignored. Expected file format for detail lines:
     * <ul>
     * <li>Timestamp (ignored field)
     * <li>Start time of interval (ignored field)
     * <li>End time of interval (ignored field)
     * <li>Array of data
     * </ul>
     */
    private static boolean compareFiles(String[] inputFileList, String[] outputFileList, boolean useBigIntegers, boolean createOutputFile) throws Exception {
        boolean comparisonSuccessful;
        boolean roundSuccessful;
        int numberOfLines;
        LineNumberReader[] lineNumberReaderInputs;
        LineNumberReader[] lineNumberReaderOutputs;
        String nextLine;
        BigInteger[] inputResultBigInteger;
        BigInteger[] outputResultBigInteger;
        double[] inputResultDouble;
        double[] outputResultDouble;
        long[] inputResultLong;
        int numberOfInputFiles;
        int numberOfOutputFiles;
        boolean moreLinesToGo;
        String[] fields;

        comparisonSuccessful = true;
        moreLinesToGo = true;
        inputResultBigInteger = null;
        outputResultBigInteger = null;
        inputResultDouble = null;
        outputResultDouble = null;
        inputResultLong = null;
        fields = null;

        try {
            if (inputFileList.length <= 0) {
                throw new Exception("Input file list is empty!");
            }

            if (outputFileList.length <= 0) {
                throw new Exception("Ouput file list is empty!");
            }

            // Initialize file number readers
            System.out.println("\nInitializing file streams...");

            numberOfInputFiles = inputFileList.length;
            numberOfOutputFiles = outputFileList.length;
            lineNumberReaderInputs = new LineNumberReader[numberOfInputFiles];
            lineNumberReaderOutputs = new LineNumberReader[numberOfOutputFiles];

            for (int fileNumber = 0; fileNumber < numberOfInputFiles; fileNumber++) {
                lineNumberReaderInputs[fileNumber] = initializeLineNumberReader(inputFileList[fileNumber]);
                lineNumberReaderInputs[fileNumber].readLine(); // ignore header line
            }

            if (!createOutputFile) {
                for (int fileNumber = 0; fileNumber < numberOfOutputFiles; fileNumber++) {
                    lineNumberReaderOutputs[fileNumber] = initializeLineNumberReader(outputFileList[fileNumber]);
                    lineNumberReaderOutputs[fileNumber].readLine(); // ignore header line
                }
            } else {
                // Write a dummy header to the file
                printItem(outputFileList[0], "header...", null);
                fields = new String[3];
                fields[0] = "";
                fields[1] = "";
                fields[2] = "";
            }
            // Compare line-by-line
            if (!createOutputFile) {
                if (useBigIntegers) {
                    System.out.println("\nStarting comparison (using BigInteger data types)...");
                } else {
                    System.out.println("\nStarting comparison(using double data types)...");
                }
            } else {
                System.out.println("\nStarting computation (using long data types)...");
            }
            numberOfLines = 0;

            while (moreLinesToGo) {
                numberOfLines++;

                for (int fileNumber = 0; fileNumber < numberOfInputFiles; fileNumber++) {
                    nextLine = lineNumberReaderInputs[fileNumber].readLine();
                    if (nextLine != null) {
                        if (fileNumber != 0) {
                            if (useBigIntegers) {
                                inputResultBigInteger = addVectors(inputResultBigInteger, createBigIntegerArrayFromLine(nextLine));
                            } else {
                                inputResultDouble = addVectors(inputResultDouble, createDoubleArrayFromLine(nextLine));
                            }

                            if (createOutputFile) {
                                inputResultLong = addVectors(inputResultLong, createLongArrayFromLine(nextLine));
                                nextLine = nextLine.replace(",", ";");
                                fields = nextLine.split(";");
                            }
                        } else {
                            if (useBigIntegers) {
                                inputResultBigInteger = createBigIntegerArrayFromLine(nextLine);
                            } else {
                                inputResultDouble = createDoubleArrayFromLine(nextLine);
                            }

                            if (createOutputFile) {
                                inputResultLong = createLongArrayFromLine(nextLine);
                            }
                        }

                    } else {
                        System.out.println("No more lines in file " + inputFileList[fileNumber] + "! Stop comparison");
                        moreLinesToGo = false;
                    }
                }

                if (!createOutputFile) {
                    for (int fileNumber = 0; fileNumber < numberOfOutputFiles; fileNumber++) {
                        nextLine = lineNumberReaderOutputs[fileNumber].readLine();

                        if (nextLine != null) {
                            if (fileNumber != 0) {
                                if (useBigIntegers) {
                                    outputResultBigInteger = addVectors(outputResultBigInteger, createBigIntegerArrayFromLine(nextLine));
                                } else {
                                    outputResultDouble = addVectors(outputResultDouble, createDoubleArrayFromLine(nextLine));
                                }
                            } else {
                                if (useBigIntegers) {
                                    outputResultBigInteger = createBigIntegerArrayFromLine(nextLine);
                                } else {
                                    outputResultDouble = createDoubleArrayFromLine(nextLine);
                                }
                            }

                        } else {
                            System.out.println("No more lines in file " + outputFileList[fileNumber] + "! Stop comparison");
                            moreLinesToGo = false;
                        }
                    }
                }

                if (moreLinesToGo) {
                    if (!createOutputFile) {

                        // Compare the results for this line
                        if (useBigIntegers) {
                            roundSuccessful = compareVectors(inputResultBigInteger, outputResultBigInteger);
                        } else {
                            roundSuccessful = compareVectors(inputResultDouble, outputResultDouble);
                        }

                        if (roundSuccessful) {
                            System.out.println("Items in line " + numberOfLines + " match!");
                        } else {
                            System.out.println("Mismatch in line " + numberOfLines);
                            comparisonSuccessful = false;
                        }
                    } else {
                        // Create an output file
                        printItem(outputFileList[0], fields[0] + CSV_SEPARATOR + fields[1] + CSV_SEPARATOR + fields[2], inputResultLong);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error when comparing results: " + e.getMessage());
            throw e;
        }
        return comparisonSuccessful;
    }

    /**
     * Print record to file
     */
    protected synchronized static void printItem(String fileName, String beginOfLine, long[] data) throws Exception {
        FileOutputStream out;
        PrintStream printOut;

        // To avoid continuous opening/closing of th file it's just done once

        // Always append to file (creates new file if it doesn't exist)
        out = new FileOutputStream(fileName, true);
        printOut = new PrintStream(out);

        if (beginOfLine != null) {
            printOut.print(beginOfLine);
        }

        if (data != null) {
            for (long l : data) {
                printOut.print(CSV_SEPARATOR + l);
            }
        }

        // Close streams and files
        printOut.println();
        out.close();
        printOut.close();
    }

    /**
     * Initializes a line number reader (for given file name) that can be used
     * to read files line by line and get current line numbers.
     * 
     * @param file The file to read from
     */
    public synchronized static LineNumberReader initializeLineNumberReader(File file) throws Exception {
        FileInputStream fileInputStream;
        BufferedReader bufferedReader;
        LineNumberReader lineNumberReader;

        try {
            fileInputStream = new FileInputStream(file);
            bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            lineNumberReader = new LineNumberReader(bufferedReader);
        } catch (Exception e) {
            throw new Exception("Error when initializing line number reader for file " + file.getName() + " (" + e.getMessage() + ")");
        }
        return lineNumberReader;
    }

    
    /**
     * Initializes a line number reader (for given file name) that can be used
     * to read files line by line and get current line numbers.
     * 
     * @param fileName The file to read from
     */
    public synchronized static LineNumberReader initializeLineNumberReader(String fileName) throws Exception {
       return Services.initializeLineNumberReader(new File(fileName));
    }


    /**
     * Compares two vectors.
     */
    public synchronized static boolean compareVectors(BigInteger[] array01, BigInteger[] array02) throws Exception {
        boolean comparisonSuccessful;

        comparisonSuccessful = true;

        if (array01.length != array02.length) {
            System.out.println("Array dimensions don't match (" + array01.length + " / " + array02.length + ")");
            comparisonSuccessful = false;
        } else {
            for (int i = 0; i < array01.length; i++) {
                if (!array01[i].toString().equals(array02[i].toString())) {
                    System.out.println("Mismatch for item # " + (i + 1) + ": " + array01[i].toString() + "/" + array02[i].toString());
                    comparisonSuccessful = false;
                }
            }
        }
        return comparisonSuccessful;
    }

    /**
     * Compares two vectors.
     */
    public synchronized static boolean compareVectors(double[] array01, double[] array02) throws Exception {
        boolean comparisonSuccessful;

        comparisonSuccessful = true;

        if (array01.length != array02.length) {
            System.out.println("Array dimensions don't match (" + array01.length + " / " + array02.length + ")");
            comparisonSuccessful = false;
        } else {
            for (int i = 0; i < array01.length; i++) {
                if (array01[i] != array02[i]) {
                    System.out.println("Mismatch for item # " + (i + 1) + ": " + array01[i] + "/" + array02[i]);
                    comparisonSuccessful = false;
                }
            }
        }
        return comparisonSuccessful;
    }

    /**
     * Creates an array of data from a String line with format:
     * <ul>
     * <li>Timestamp (ignored field)
     * <li>Start time of interval (ignored field)
     * <li>End time of interval (ignored field)
     * <li>Array of data
     * </ul>
     */
    public synchronized static double[] createDoubleArrayFromLine(String line) throws Exception {
        String[] fields;
        double[] doubleData;
        int startOfDataIndex;

        startOfDataIndex = 3;
        line = line.replace(",", ";");
        fields = line.split(";");

        doubleData = new double[fields.length - startOfDataIndex];

        for (int i = startOfDataIndex; i < fields.length; i++) {
            doubleData[i - startOfDataIndex] = Double.valueOf(fields[i].trim());
        }
        return doubleData;
    }

    /**
     * Creates an array of data from a String line with format:
     * <ul>
     * <li>Timestamp (ignored field)
     * <li>Start time of interval (ignored field)
     * <li>End time of interval (ignored field)
     * <li>Array of data
     * </ul>
     */
    public synchronized static long[] createLongArrayFromLine(String line) throws Exception {
        String[] fields;
        long[] longData;
        int startOfDataIndex;

        startOfDataIndex = 3;
        line = line.replace(",", ";");
        fields = line.split(";");

        longData = new long[fields.length - startOfDataIndex];

        for (int i = startOfDataIndex; i < fields.length; i++) {
            longData[i - startOfDataIndex] = Long.valueOf(fields[i].trim());
        }
        return longData;
    }

    /**
     * Creates an array of data from a String line with format:
     * <ul>
     * <li>Timestamp (ignored field)
     * <li>Start time of interval (ignored field)
     * <li>End time of interval (ignored field)
     * <li>Array of data
     * </ul>
     */
    public synchronized static BigInteger[] createBigIntegerArrayFromLine(String line) throws Exception {
        String[] fields;
        BigInteger[] bigIntData;
        int startOfDataIndex;

        startOfDataIndex = 3;
        line = line.replace(",", ";");
        fields = line.split(";");

        bigIntData = new BigInteger[fields.length - startOfDataIndex];

        for (int i = startOfDataIndex; i < fields.length; i++) {
            bigIntData[i - startOfDataIndex] = new BigInteger(fields[i].trim());
        }
        return bigIntData;
    }

    /**
     * Computes the sum of each item in the input vectors given (dimensions
     * must match!).
     * 
     * @return Sum of each item of the input vectors
     */
    public synchronized static long[] addVectors(long[] inputVector01, long[] inputVector02) throws Exception {
        long[] resultVector;

        if (inputVector01.length <= 0) {
            throw new Exception("Input vector 01 has length zero!");
        }
        if (inputVector02.length <= 0) {
            throw new Exception("Input vector 02 has length zero!");
        }

        if (inputVector01.length != inputVector02.length) {
            throw new Exception("Dimensions don't match: " + inputVector01.length + " / " + inputVector02.length);
        }
        resultVector = new long[inputVector01.length];

        for (int item = 0; item < inputVector01.length; item++) {
            resultVector[item] = inputVector01[item] + inputVector02[item];
        }

        return resultVector;
    }

    /**
     * Computes the sum of each item in the input vectors given (dimensions
     * must match!).
     * 
     * @return Sum of each item of the input vectors
     */
    public synchronized static double[] addVectors(double[] inputVector01, double[] inputVector02) throws Exception {
        double[] resultVector;

        if (inputVector01.length <= 0) {
            throw new Exception("Input vector 01 has length zero!");
        }
        if (inputVector02.length <= 0) {
            throw new Exception("Input vector 02 has length zero!");
        }

        if (inputVector01.length != inputVector02.length) {
            throw new Exception("Dimensions don't match: " + inputVector01.length + " / " + inputVector02.length);
        }
        resultVector = new double[inputVector01.length];

        for (int item = 0; item < inputVector01.length; item++) {
            resultVector[item] = inputVector01[item] + inputVector02[item];
        }

        return resultVector;
    }

    /**
     * Computes the sum of each item in the input vectors given (dimensions
     * must match!).
     * 
     * @return Sum of each item of the input vectors
     */
    public synchronized static BigInteger[] addVectors(BigInteger[] inputVector01, BigInteger[] inputVector02) throws Exception {
        BigInteger[] resultVector;

        if (inputVector01.length <= 0) {
            throw new Exception("Input vector 01 has length zero!");
        }
        if (inputVector02.length <= 0) {
            throw new Exception("Input vector 02 has length zero!");
        }

        if (inputVector01.length != inputVector02.length) {
            throw new Exception("Dimensions don't match: " + inputVector01.length + " / " + inputVector02.length);
        }
        resultVector = new BigInteger[inputVector01.length];


        for (int item = 0; item < inputVector01.length; item++) {
            resultVector[item] = inputVector01[item].add(inputVector02[item]);
        }

        return resultVector;
    }

    /**
     * Serialize the given object (must implement Serializable)
     *
     * @param object The object that is serialized (must implement Serializable)
     *
     * @return Serialized object
     */
    public synchronized static byte[] serialize(Object object) throws Exception {
        byte[] serializedObject;
        ByteArrayOutputStream byteStream;
        ObjectOutputStream objectStream;

        serializedObject = null;

        byteStream = new ByteArrayOutputStream();
        objectStream = new ObjectOutputStream(byteStream);
        objectStream.writeObject(object);
        objectStream.flush();
        objectStream.close();
        serializedObject = byteStream.toByteArray();
        byteStream.close();

        return serializedObject;
    }

    /**
     * Prefix this string to your log message to pass logging filter in 
     * non-verbose mode
     */
    public static String getFilterPassingLogPrefix() {
        return "[LOG] ";
    }

    public static String getApplicationName() {
        return "sepia";
    }

    public static String getApplicationDescription() {
        return "Security through Private Information Aggregation";
    }

    /**
     * De-serialize given object
     *
     * @param serializedObject The serialized object to be de-serialized
     *
     * @return De-serialized object
     */
    public synchronized static Object deSerialize(byte[] serializedObject) throws Exception {
        Object object;
        ByteArrayInputStream byteStream;
        ObjectInputStream objectStream;

        object = null;

        byteStream = new ByteArrayInputStream(serializedObject);
        objectStream = new ObjectInputStream(byteStream);
        object = objectStream.readObject();
        objectStream.close();
        byteStream.close();
        return object;
    }

    /**
     * Print the given array
     */
    public synchronized static void printVector(String title, long[] vector, Logger logger) {
        StringBuilder line = new StringBuilder();

        logger.log(Level.INFO, title);

        if (vector != null) {
            for (long l : vector) {
                line = line.append(l).append("\t");
            }
            logger.log(Level.INFO, line.toString());
            line.delete(0, line.length());
        }

    }

    /**
     * Print the given array
     */
    public synchronized static void printVector(String title, int[] vector, Logger logger) {
        String vectorItems;

        logger.log(Level.INFO, title);

        vectorItems = "";
        if (vector != null) {
            for (int i : vector) {
                vectorItems += "\t" + i;
            }
            logger.log(Level.INFO, vectorItems);
        }
    }


    public synchronized static String getCurrentDateYYYYMMDD() {
        Calendar calendar;
        String year;
        String month;
        String day;

        calendar = Calendar.getInstance();

        year = String.valueOf(calendar.get(Calendar.YEAR));
        month = String.valueOf(calendar.get(Calendar.MONTH));
        if (month.length() < 2) {
            month = "0" + month;
        }
        day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
        if (day.length() < 2) {
            day = "0" + day;
        }
        return year + month + day;
    }

    public synchronized static String getCurrentTimeHHMMSS() {
        Calendar calendar;
        String hours;
        String minutes;
        String seconds;

        calendar = Calendar.getInstance();

        hours = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
        minutes = String.valueOf(calendar.get(Calendar.MINUTE));
        if (minutes.length() < 2) {
            minutes = "0" + minutes;
        }
        seconds = String.valueOf(calendar.get(Calendar.SECOND));
        if (seconds.length() < 2) {
            seconds = "0" + seconds;
        }
        return hours + minutes + seconds;
    }

    /**
     * Reads the file at the given location into term vector of strings
     * 
     * @param filePath Where to find the file
     * 
     * @return Each line of the file in term row of the string vector
     */
    public synchronized static Vector<String> readFile(String filePath) throws Exception {
        FileInputStream in;
        BufferedReader readIn;
        Vector<String> lines;
        String line;

        in = null;
        readIn = null;
        lines = new Vector<String>();

        try {
            in = new FileInputStream(filePath);
            readIn = new BufferedReader(new InputStreamReader(in));

            try {
                while (true) {
                    line = readIn.readLine();

                    if (line != null) {
                        lines.add(line);
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
            // EOF
            }
        } catch (Exception e) {
            throw new Exception("Error when reading from file: " + filePath + " -> " + e.getMessage());
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
            // Ignore...
            }
            try {
                if (readIn != null) {
                    readIn.close();
                }
            } catch (Exception e) {
            // Ignore...
            }

        }
        return lines;
    }

    /**
     * Write the lines of the given array into a file
     *
     * @param lines The lines that shall be written to the file
     * @param fileName The file's name
     */
    public synchronized static void writeFile(String[] lines, String fileName) throws Exception {
        FileOutputStream out;
        PrintStream printOut;

        out = new FileOutputStream(fileName, false);
        printOut = new PrintStream(out);

        for (String s : lines) {
            printOut.println(s);
        }

        out.close();
        printOut.close();
    }

    /**
     * Write the lines of the given array into term file
     *
     * @param line The line that shall be written to the file
     * @param fileName The file's name
     */
    public synchronized static void writeFile(String line, String fileName) throws Exception {
        String[] lines;

        lines = new String[1];
        lines[0] = line;
        writeFile(lines, fileName);
    }

    /**
     * Write the lines of the given array into a file
     *
     * @param bytes The bytes that shall be written to the file
     * @param fileName The file's name
     */
    public synchronized static void writeFile(byte[] bytes, String fileName) throws Exception {
        FileOutputStream out;
        PrintStream printOut;

        out = new FileOutputStream(fileName, false);
        printOut = new PrintStream(out);

        printOut.write(bytes);

        out.close();
        printOut.close();
    }

    /**
     * Checks if the file with the given file name (path) exists.
     * 
     * @param fileName File name
     * @return True if the file exists, false else
     */
    public synchronized static boolean checkFileExists(String fileName) {
        File myFile;

        myFile = new File(fileName);
        return myFile.exists();
    }

	/**
	 * Fast exponentiation: term ^ exponent
	 *
	 * (code copied from {@link ShamirSharing#fastExponentiation(long, long)}
	 * and adapted to use normal products instead of modulo computations)
	 */
	public static long fastExponentiation(long term, long exponent) throws Exception {
		long p;
		long q;
		String errorMessage;

		p = 1L;
		q = term;

		if (exponent > 0) {
			if (term != 0) {
				if (term != 1) {
					while (exponent > 0L) {
						if (1L == (exponent & 1L)) {
							p *= q;
						}

						exponent /= 2L;
						q <<= 2;
					}

				} else {
					return 1;
				}

			} else {
				return 0;
			}

		} else if (exponent == 0) {
			return 1;
		} else {
			errorMessage = "Fast Exponentiation: Only exponents >= 0 allowed (" + exponent + ")!";
			throw new Exception(errorMessage);
		}

		return p;
	}
	
	/**
     * Read properties from configuration file. The system specific property
     * 'file separator' is added to the properties
     *
     * @param propertyFileName The configuration file holding key-value pairs 
     *                         (key=value)
     *
     * @return The loaded properties
     */
    public synchronized static Properties loadProperties(String propertyFileName) throws Exception {
        Properties properties = new Properties();
        FileInputStream in = new FileInputStream(propertyFileName);

        try {
            properties.load(in);
        } catch (Exception e) {
        // ignore exception
        }
        in.close();

        return properties;
    }
    
    /**
     * Computes the logarithm to the base 2
     * @param val value of which the logarithm is computed
     * @return log2(val)
     */
    public static double log2(double val){
    	return Math.log(val)/Math.log(2);
    }

}
