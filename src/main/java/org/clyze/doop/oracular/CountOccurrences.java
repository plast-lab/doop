package org.clyze.doop.oracular;
import java.io.*;
import java.util.*;

public class CountOccurrences {
    public static void run(File inputFile, File outputFile) {
        // if (args.length < 2) {
        //     System.out.println("Usage: java CountOccurrences <input-file> <output-file>");
        //     return;
        // }

        // File inputFile = args[0];
        // String outputFile = args[1];
        Map<String, Integer> countMap = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t");
                if (columns.length < 4) continue; // Skip lines with less than 4 columns

                String key = columns[2] + "\t" + columns[3];
                countMap.put(key, countMap.getOrDefault(key, 0) + 1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Write results to a file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
                writer.write(entry.getKey().split("\t")[1] + "\t" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
