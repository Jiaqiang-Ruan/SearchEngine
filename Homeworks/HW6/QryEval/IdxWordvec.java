/**
 * Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.HashMap;
import java.nio.*;
import java.nio.file.*;

import org.jblas.FloatMatrix;
import org.jblas.Geometry;

/**
 * IdxWordvec provides access to a word2vec database that consists
 * of a word2vec file and an IdxWordvec.map file.
 */

public class IdxWordvec {

    //  --------------- Constants and variables ---------------------

    private static int dimensions = 0;
    private static int vocabularySize = 0;
    private static RandomAccessFile w2vFile = null;
    private static HashMap<String, Long> w2vMap = new HashMap<String, Long>();
    private static String w2vMapFilename = "IdxWordvec.map";
    private static String encoding = "ISO-8859-1";

    //  --------------- Methods ---------------------------------------


    /**
     * Close the currently open word2vec database, if any
     */
    public static void close() {
        IdxWordvec.dimensions = 0;
        IdxWordvec.vocabularySize = 0;
        if (IdxWordvec.w2vFile != null) {
            IdxWordvec.close();
            IdxWordvec.w2vFile = null;
        }
        IdxWordvec.w2vMap = null;
    }

    /**
     * The word2vec vector length.
     */
    public static int dimensions() {
        return IdxWordvec.dimensions;
    }

    /**
     * Get the word vector for term, or null if no such vector exists.
     *
     * @param term A term string.
     * @return the word2vec representation stored as a vector of floats.
     * @throws IOException Error accessing the word2vec database.
     */

    public static FloatMatrix get(String term) throws IOException {
        if (!IdxWordvec.w2vMap.containsKey(term)) {
            return null;
        }

        // Sadly, the binary encoding of floats in the word2vec file does
        // not match the binary encoding used by Java, so readFloat
        // doesn't work.  Read the bytes and do the conversion explicitly.

        byte[] bytes = new byte[4];
        float result[] = new float[IdxWordvec.dimensions];

        IdxWordvec.w2vFile.seek(IdxWordvec.w2vMap.get(term));
        for (int i = 0; i < IdxWordvec.dimensions; i++) {
            IdxWordvec.w2vFile.read(bytes);
            result[i] =
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        }

        // The word2vec code normalizes vectors after reading them, so we
        // must do the same.  This is copied from Google's distance.c.

//    double len = 0;
//    for (int a = 0; a < IdxWordvec.dimensions; a++) {
//      len += result[a] * result[a];
//    }
//    len = Math.sqrt(len);
//    for (int a = 0; a < IdxWordvec.dimensions; a++) {
//      result[a] /= len;
//    }

        // Above code commented in favor of Geometry package

        FloatMatrix vector = new FloatMatrix(result);
        // This is the code that normalizes a Float Matrix
        Geometry.normalize(vector);

        return vector.transpose();
    }

    /**
     * Open a word2vec database.
     *
     * @param path A directory and filename prefix for a word2vec database.
     * @throws FileNotFoundException Error accessing the word2vec database.
     * @throws IOException           Error accessing the word2vec database.
     */
    public static void open(String path)
            throws FileNotFoundException, IOException {
        String filepath = Paths.get(path, IdxWordvec.w2vMapFilename).toString();
        FileInputStream filestream = new FileInputStream(filepath);
        InputStreamReader filereader = new InputStreamReader(filestream, IdxWordvec.encoding);
        BufferedReader w2vMapRdr = new BufferedReader(filereader);

        // Read and store the map that tells the location of each term's
        // vector in the word2vec file.

        String w2vFilename = w2vMapRdr.readLine();
        String line = w2vMapRdr.readLine();
        String[] strings = line.split("\\s+|\\t+", 2);
        IdxWordvec.vocabularySize = Integer.parseInt(strings[1]);

        line = w2vMapRdr.readLine();
        strings = line.split("\\s+|\\t+", 2);
        IdxWordvec.dimensions = Integer.parseInt(strings[1]);

        for (int i = 0; i < IdxWordvec.vocabularySize; i++) {
            line = w2vMapRdr.readLine();
            strings = line.split("\t", 2);

            // Duplicates occur in the GoogleNews-vectors-negative300 file.
            // Use the first occurrence to improve retrieval speed.

            if (!IdxWordvec.w2vMap.containsKey(strings[0])) {
                IdxWordvec.w2vMap.put(strings[0], Long.parseLong(strings[1]));
            }
        }

        w2vMapRdr.close();

        // Open the word2vec file for random access.

        filepath = Paths.get(path, w2vFilename).toString();
        IdxWordvec.w2vFile = new RandomAccessFile(filepath, "r");
    }

    /**
     * The size of the word2vec vocabulary.
     */
    public static int vocabularySize() {
        return IdxWordvec.vocabularySize;
    }

}