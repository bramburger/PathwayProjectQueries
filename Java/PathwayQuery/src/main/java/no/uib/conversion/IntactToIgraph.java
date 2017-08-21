package no.uib.conversion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * This class converts an intact file to igraph files.
 *
 * @author Marc Vaudel
 */
public class IntactToIgraph {

    /**
     * The main method takes an intact file and writes igraph files.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {

        try {
            
            

            args = new String[]{"C:\\Projects\\Bram\\graphs\\resources\\intact\\26496610.gz",
                "C:\\Projects\\Bram\\graphs\\resources\\swissprot_human_18.08.17.tab.gz",
                "C:\\Github\\post-association\\resources\\function\\intact",
                "26496610_mann"};

            IntactToIgraph intactToIgraph = new IntactToIgraph();

            File sifFile = new File(args[0]);
            File uniprotFile = new File(args[1]);
            File outputFolder = new File(args[2]);
            String baseName = args[3];

            System.out.println(new Date() + " Parsing uniprot file");

            HashSet<String> accessions = intactToIgraph.getUniprotAccessions(uniprotFile);

            System.out.println(new Date() + " Parsing Intact file");

            intactToIgraph.parseIntactFile(sifFile, accessions);

            System.out.println(new Date() + " Exporting results");
            intactToIgraph.writeIGraphFiles(outputFolder, baseName);

            int nEdges = intactToIgraph.getNEdges();
            System.out.println(new Date() + " " + nEdges + " interractions found");

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    /**
     * Map of complexes, from input to outputs.
     */
    private HashMap<String, HashSet<String>> interactions = new HashMap<>();
    /**
     * Set of all nodes.
     */
    private HashSet<String> allNodes = new HashSet<>();
    /**
     * Encoding.
     */
    public static final String encoding = "UTF-8";
    
    private BufferedWriter bw;
    
    public IntactToIgraph() throws IOException {
        bw = new BufferedWriter(new FileWriter(new File("C:\\Github\\post-association\\resources\\function\\mouse")));
    }

    /**
     * Parses the list of uniprot accessions from the given uniprot mapping
     * file.
     *
     * @param uniprotFile the uniprot mapping file
     *
     * @return the accessions in a set
     *
     * @throws IOException exception thrown if an error occurred while reading
     * the file.
     */
    private HashSet<String> getUniprotAccessions(File uniprotFile) throws IOException {

        HashSet<String> accessions = new HashSet<>(20201);

        InputStream fileStream = new FileInputStream(uniprotFile);
        InputStream gzipStream = new GZIPInputStream(fileStream);
        Reader decoder = new InputStreamReader(gzipStream, encoding);

        try (BufferedReader br = new BufferedReader(decoder)) {

            String line = br.readLine();
            while ((line = br.readLine()) != null) {

                String accession = line.substring(0, line.indexOf('\t'));
                accessions.add(accession);

            }
        }

        return accessions;
    }

    /**
     * Parses a intact file and populates the network attributes.
     *
     * @param intactFile the intact file
     * @param restrictionList list of accessions to retain
     *
     * @throws IOException exception thrown if an error occurred while reading
     * the file.
     */
    private void parseIntactFile(File intactFile, HashSet<String> restrictionList) throws IOException {

        InputStream fileStream = new FileInputStream(intactFile);
        InputStream gzipStream = new GZIPInputStream(fileStream);
        Reader decoder = new InputStreamReader(gzipStream, encoding);

        try (BufferedReader br = new BufferedReader(decoder)) {

            String line = br.readLine();
            while ((line = br.readLine()) != null) {

                String[] lineSplit = line.split("\t");

                HashSet<String> accessionsA = getAccessions(lineSplit[0], restrictionList);

                if (accessionsA.isEmpty()) {

                    accessionsA = getAccessions(lineSplit[2], restrictionList);

                }

                HashSet<String> accessionsB = getAccessions(lineSplit[1], restrictionList);

                if (accessionsB.isEmpty()) {

                    accessionsB = getAccessions(lineSplit[3], restrictionList);

                }

                if (!accessionsA.isEmpty() && !accessionsB.isEmpty()) {

                    allNodes.addAll(accessionsA);
                    allNodes.addAll(accessionsB);

                    for (String accession : accessionsA) {

                        HashSet<String> currentTargets = accessionsB.stream()
                                .filter(participant -> !participant.equals(accession))
                                .collect(Collectors.toCollection(HashSet::new));

                        HashSet<String> targets = interactions.get(accession);

                        if (targets == null) {

                            interactions.put(accession, currentTargets);

                        } else {

                            targets.addAll(currentTargets);

                        }
                    }
                }
            }
        }
        
        bw.close();
    }

    /**
     * Splits the intact entry and extracts the accessions present in the given
     * list.
     *
     * @param intactEntry the intact entry
     * @param accessions the accessions to look for
     *
     * @return the accessions found in a set
     */
    private HashSet<String> getAccessions(String intactEntry, HashSet<String> accessions) {

        String[] splittedEntry = intactEntry.split("\\|");
        HashSet<String> result = new HashSet<>(splittedEntry.length);

        for (String entry : splittedEntry) {
            if (entry.length() >= 16) {

                String accession = entry.substring(10);

                if (accessions.contains(accession)) {

                    result.add(accession);

                } else {

                    int dashIndex = accession.indexOf('-');

                    if (dashIndex > 0) {

                        accession = accession.substring(0, dashIndex);

                        if (accessions.contains(accession)) {

                            result.add(accession);

                        } else if (accession.length() > 5) {
                            
                            try {
                                
                            bw.write(accession);
                            bw.newLine();
                            
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            
                        }
                    } else if (accession.length() > 5) {
                            
                            try {
                                
                            bw.write(accession);
                            bw.newLine();
                            
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            
                        }
                }
            }
        }

        return result;
    }

    /**
     * Write the igraph files.
     *
     * @param folder the destination folder
     * @param baseFileName the base name for the edges and vertices files
     *
     * @throws IOException exception thrown if an error occurred while writing
     * the file
     */
    private void writeIGraphFiles(File folder, String baseFileName) throws IOException {

        File edgeFile = new File(folder, baseFileName + "_edges");

        FileOutputStream outputFileStream = new FileOutputStream(edgeFile);
        GZIPOutputStream outputGzipStream = new GZIPOutputStream(outputFileStream);
        OutputStreamWriter outputEncoder = new OutputStreamWriter(outputGzipStream, encoding);

        try (BufferedWriter bw = new BufferedWriter(outputEncoder)) {

            bw.write("from to type");
            bw.newLine();

            writeEdges(bw, interactions, "Complex");

        }

        File nodesFile = new File(folder, baseFileName + "_vertices");

        outputFileStream = new FileOutputStream(nodesFile);
        outputGzipStream = new GZIPOutputStream(outputFileStream);
        outputEncoder = new OutputStreamWriter(outputGzipStream, encoding);

        try (BufferedWriter bw = new BufferedWriter(outputEncoder)) {

            bw.write("id");
            bw.newLine();

            bw.write(
                    allNodes.stream()
                            .sorted()
                            .collect(Collectors.joining(System.lineSeparator()))
            );

        }

    }

    /**
     * Writes the given edges using the given writer. Writing exceptions are
     * thrown as runtime exception.
     *
     * @param bw the writer
     * @param targetsMap the accession to target map
     * @param category the category of the mapping
     */
    private void writeEdges(BufferedWriter bw, HashMap<String, HashSet<String>> targetsMap, String category) {
        targetsMap.keySet().stream()
                .sorted()
                .forEach(accession -> writeEdges(bw, accession, targetsMap.get(accession), category));

    }

    /**
     * Writes the given edges using the given writer. Writing exceptions are
     * thrown as runtime exception.
     *
     * @param bw the writer
     * @param accession the accession
     * @param targets the targets
     * @param category the category of the mapping
     */
    private void writeEdges(BufferedWriter bw, String accession, HashSet<String> targets, String category) {

        targets.stream()
                .sorted()
                .forEach(target -> writeEdge(bw, accession, target, category));
    }

    /**
     * Writes the given edge using the given writer. Writing exceptions are
     * thrown as runtime exception.
     *
     * @param bw the writer
     * @param accession the accession
     * @param target the target
     * @param category the category of the mapping
     */
    private void writeEdge(BufferedWriter bw, String accession, String target, String category) {

        try {

            StringBuilder sb = new StringBuilder(accession.length() + target.length() + category.length() + 2);
            sb.append(accession).append(' ').append(target).append(' ').append(category);
            bw.write(sb.toString());
            bw.newLine();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the number of edges found.
     *
     * @return the number of edges found
     */
    private int getNEdges() {

        return interactions.values().stream().mapToInt(targets -> targets.size()).sum();

    }
}