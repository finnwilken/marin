package org.tudo.sse.analyses;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tudo.sse.ArtifactFactory;
import org.tudo.sse.CLIException;
import org.tudo.sse.model.Artifact;
import org.tudo.sse.model.ArtifactIdent;
import org.tudo.sse.model.index.Package;
import org.tudo.sse.model.pom.License;
import org.tudo.sse.model.pom.PomInformation;
import org.tudo.sse.utils.ArtifactConfigParser;
import org.tudo.sse.utils.IndexIterator;
import org.tudo.sse.utils.MavenCentralAnalysisFactory;
import scala.Tuple2;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MavenCentralArtifactAnalysisTest {
    final MavenCentralArtifactAnalysis analysisUnderTest = MavenCentralAnalysisFactory.buildEmptyAnalysisWithIndexRequirement();
    final String base = "https://repo1.maven.org/maven2/";
    final Map<String, Object> json;
    final Gson gson = new Gson();

    {
        InputStream resource = this.getClass().getClassLoader().getResourceAsStream("MavenAnalysis.json");
        assert resource != null;
        Reader targetReader = new InputStreamReader(resource);
        json = gson.fromJson(targetReader, new TypeToken<Map<String, Object>>() {}.getType());
    }

    @AfterEach
    public void cleanup(){
        ArtifactFactory.artifacts.clear();
    }

    @Test
    @DisplayName("The CLI parser must parse common argument values correctly")
    void parseCLIRegular() throws IOException{
        Path tmpDir = Files.createTempDirectory("maven-resolution-files");

        ArtifactConfigParser.ArtifactConfig[] configs = new  ArtifactConfigParser.ArtifactConfig[]{
                parseCLI("--skip-take 500:223"),
                parseCLI("--inputs src/test/resources/localPom.xml"),
                parseCLI("--progress-restore-file src/test/resources/localPom.xml --inputs src/test/resources/localPom.xml"),
                parseCLI("--since-until 53245:13243"),
                parseCLI("--inputs src/test/resources/localPom.xml --progress-restore-file src/test/resources/localPom.xml"),
                parseCLI("--progress-restore-file src/test/resources/localPom.xml"),
                parseCLI("--output " + tmpDir.toString())
        };

        List<List<String>> expected = (List<List<String>>) json.get("cliParsePos");

        for(int i = 0; i < configs.length; i++){
            final List<String> currExpected = expected.get(i);
            final ArtifactConfigParser.ArtifactConfig currConfig = configs[i];

            assertEquals(asInt(currExpected.get(0)), currConfig.skip);
            assertEquals(asInt(currExpected.get(1)), currConfig.take);
            assertEquals(asInt(currExpected.get(2)), currConfig.since);
            assertEquals(asInt(currExpected.get(3)), currConfig.until);

            final String expectedInputFile = currExpected.get(4);

            if(expectedInputFile.equalsIgnoreCase("null")){
                assertNull(currConfig.inputListFile);
            } else {
                assertEquals(expectedInputFile, currConfig.inputListFile.toString().replace("\\","/"));
            }

            final String expectedIndexFile = currExpected.get(5);

            if(expectedIndexFile.equalsIgnoreCase("null")){
                assertNull(currConfig.progressRestoreFile);
            } else {
                assertEquals(expectedIndexFile, currConfig.progressRestoreFile.toString().replace("\\","/"));
            }

            assertEquals(Boolean.parseBoolean(currExpected.get(6)), currConfig.outputEnabled);
        }
    }

    @Test
    @DisplayName("The CLI parser must parse common argument values correctly using shorthand argument names")
    void parseCLIShorthands() throws IOException{
        Path tmpDir = Files.createTempDirectory("maven-resolution-files");

        ArtifactConfigParser.ArtifactConfig[] configs = new  ArtifactConfigParser.ArtifactConfig[]{
                parseCLI("-st 500:223"),
                parseCLI("-i src/test/resources/localPom.xml"),
                parseCLI("-prf src/test/resources/localPom.xml -i src/test/resources/localPom.xml"),
                parseCLI("-su 53245:13243"),
                parseCLI("-i src/test/resources/localPom.xml -prf src/test/resources/localPom.xml"),
                parseCLI("-prf src/test/resources/localPom.xml"),
                parseCLI("-o " + tmpDir.toString())
        };

        List<List<String>> expected = (List<List<String>>) json.get("cliParsePos");

        for(int i = 0; i < configs.length; i++){
            final List<String> currExpected = expected.get(i);
            final ArtifactConfigParser.ArtifactConfig currConfig = configs[i];

            assertEquals(asInt(currExpected.get(0)), currConfig.skip);
            assertEquals(asInt(currExpected.get(1)), currConfig.take);
            assertEquals(asInt(currExpected.get(2)), currConfig.since);
            assertEquals(asInt(currExpected.get(3)), currConfig.until);

            final String expectedInputFile = currExpected.get(4);

            if(expectedInputFile.equalsIgnoreCase("null")){
                assertNull(currConfig.inputListFile);
            } else {
                assertEquals(expectedInputFile, currConfig.inputListFile.toString().replace("\\","/"));
            }

            final String expectedIndexFile = currExpected.get(5);

            if(expectedIndexFile.equalsIgnoreCase("null")){
                assertNull(currConfig.progressRestoreFile);
            } else {
                assertEquals(expectedIndexFile, currConfig.progressRestoreFile.toString().replace("\\","/"));
            }

            assertEquals(Boolean.parseBoolean(currExpected.get(6)), currConfig.outputEnabled);
        }
    }

    @Test
    @DisplayName("The CLI parser must reject invalid inputs")
    void parseCmdLineNegative() {
        List<String> cliInputs = new ArrayList<>();
        cliInputs.add("--skip-take 500:223:3");
        cliInputs.add("--inputs -/xcd/");
        cliInputs.add("-st 88880000 -su --inputs path/to/file -ip path/to/file");
        cliInputs.add("--inputs");


        for(String input : cliInputs) {
            assertThrows(RuntimeException.class, () -> parseCLI(input));
        }
    }

    @Test
    @DisplayName("An analysis must adhere to pagination configurations")
    void walkPaginated() {
        List<Tuple2<Integer, Integer>> inputs = new ArrayList<>();
        inputs.add(new Tuple2<>(500, 10));
        inputs.add(new Tuple2<>(0, 10));
        inputs.add(new Tuple2<>(50000, 100));
        inputs.add(new Tuple2<>(763, 20));

        for(Tuple2 input : inputs) {
            int start1 = (int) input._1;
            int take = (int) input._2;

            int start2 = (start1 + take) - 1;

            try {
                IndexIterator iterator = new IndexIterator(new URI(base), start1);
                List<ArtifactIdent> collected1 = new ArrayList<>(analysisUnderTest.walkPaginated(take, iterator));

                Artifact lastOne = ArtifactFactory.getArtifact(collected1.get(collected1.size() - 1));
                int i = 2;
                while(lastOne.getIndexInformation().getIndex() > start2) {
                    lastOne = ArtifactFactory.getArtifact(collected1.get(collected1.size() - i));
                    i++;
                }

                iterator = new IndexIterator(new URI(base), start2);

                List<ArtifactIdent> collected2 = new ArrayList<>(analysisUnderTest.walkPaginated(1, iterator));
                long lastOne2 = ArtifactFactory.getArtifact(collected2.get(0)).getIndexInformation().getIndex();
                assertEquals(lastOne.getIndexInformation().getIndex(), lastOne2);
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    @DisplayName("An analysis must store its last processed index regardless of the store interval")
    void indexProcessorProgress() {

        List<String[]> cliInputs = new ArrayList<>();
        String[] args = {"-st", "200:70"};
        cliInputs.add(args);
        args = new String[] {"-st", "499:11", "-pof", "src/test/resources/stop.txt"};
        cliInputs.add(args);
        args = new String[]{"-st", "500:10"};
        cliInputs.add(args);
        args = new String[]{"-st", "275:70", "-pof", "src/test/resources/stop.txt", "-spi", "1000000"};
        cliInputs.add(args);

        int[] expectedEndings = {270, 510, 510, 345};

        int i = 0;
        for(String[] arg : cliInputs) {
            MavenCentralArtifactAnalysis tester = MavenCentralAnalysisFactory.buildEmptyAnalysisWithNoRequirements();
            tester.runAnalysis(arg);

            Path indexPath = tester.getSetupInfo().progressOutputFile;
            assert(indexPath != null);
            long ending = getEndingIndex(indexPath);

            assertEquals(expectedEndings[i], ending);

            i++;
        }
    }

    @Test
    @DisplayName("An analysis must correctly apply CLI options when reading custom input lists")
    void readIdentsIn() throws URISyntaxException, IOException {
        List<String[]> cliInputs = new ArrayList<>();
        String[] args = {"--inputs", "src/main/resources/coordinates.txt"};
        cliInputs.add(args);
        args = new String[] {"--inputs", "src/main/resources/coordinates.txt", "-pof", "src/test/resources/stop.txt"};
        cliInputs.add(args);
        args = new String[]{"--inputs", "src/main/resources/coordinates.txt", "-st", "4:5"};
        cliInputs.add(args);
        args = new String[]{"--inputs", "src/main/resources/coordinates.txt", "-st", "4:5", "-pof", "src/test/resources/stop.txt"};
        cliInputs.add(args);
        args = new String[]{"--inputs", "src/main/resources/coordinates.txt", "-prf", "src/test/resources/testingIndexPosition.txt"};
        cliInputs.add(args);
        args = new String[]{"--inputs", "src/main/resources/coordinates.txt", "-prf", "src/test/resources/testingIndexPosition.txt", "-pof", "src/test/resources/stop.txt"};
        cliInputs.add(args);

        List<List<String>> expected = (List<List<String>>) json.get("readIdentsIn");
        int[] expectedEndings = {10, 10, 9, 9, 10, 10};

        int i = 0;
        for(String[] arg : cliInputs) {
            List<String> curExpected = expected.get(i);
            MavenCentralArtifactAnalysis tester = MavenCentralAnalysisFactory.buildEmptyAnalysisWithNoRequirements();

            // Apply arguments, check progress is stored correctly
            tester.runAnalysis(arg);
            Path progressFile = tester.getSetupInfo().progressOutputFile;
            long ending = getEndingIndex(progressFile);
            assertEquals(expectedEndings[i], ending);

            // Process file a second time to obtain return value
            List<ArtifactIdent> idents = tester.processArtifactsFromInputFile();
            assertEquals(curExpected.size(), idents.size());



            for(ArtifactIdent actual: idents) {
                final String actualCoordinates = actual.getCoordinates();
                assert(curExpected.contains(actualCoordinates));
            }
            i++;
        }
    }

    @Test
    @DisplayName("An analysis must produce the same results in multithreaded mode")
    void checkMultiThreading() {
        List<String[]> singleArgs = new ArrayList<>();
        List<String[]> multiArgs = new ArrayList<>();

        singleArgs.add(new String[]{"-st", "10:1000"});
        multiArgs.add(new String[]{"--threads", "5", "-st", "10:1000"});

        singleArgs.add(new String[]{"--inputs", "src/main/resources/coordinates.txt"});
        multiArgs.add(new String[]{"--threads", "5", "--inputs", "src/main/resources/coordinates.txt"});

        for(int i = 0; i < singleArgs.size(); i++) {

            MavenCentralArtifactAnalysis tester = MavenCentralAnalysisFactory.buildEmptyAnalysisWithPomRequirement();

            tester.runAnalysis(singleArgs.get(i));
            Set<ArtifactIdent> singleResult = ArtifactFactory.artifacts.keySet();
            cleanup();

            tester.runAnalysis(multiArgs.get(i));
            Set<ArtifactIdent> multiResult = ArtifactFactory.artifacts.keySet();
            cleanup();

            assertEquals(singleResult.size(), multiResult.size());

            for(ArtifactIdent single : singleResult) {
                assert(multiResult.contains(single));
            }
        }
    }

    @Test
    @DisplayName("An analysis must execute without exception for basic use cases")
    void checkUseCases() {
        MavenCentralArtifactAnalysis jarUseCase = new MavenCentralArtifactAnalysis(false, false, false, true) {
            public long numberOfClassfiles = 0;
            @Override
            public void analyzeArtifact(Artifact current) {
                if(current.getJarInformation() != null) {
                    numberOfClassfiles += current.getJarInformation().getNumClassFiles();
                }
            }

        };
        assertDoesNotThrow( () -> jarUseCase.runAnalysis(new String[]{"-st", "0:300"}));

        MavenCentralArtifactAnalysis pomUseCase = new MavenCentralArtifactAnalysis(false, true, false, false) {
            public final Set<License> uniqueLicenses = new HashSet<>();
            @Override
            public void analyzeArtifact(Artifact toAnalyze) {
                if(toAnalyze.getPomInformation() != null) {
                    PomInformation info = toAnalyze.getPomInformation();
                    if(!info.getRawPomFeatures().getLicenses().isEmpty()) {
                        for(License license : info.getRawPomFeatures().getLicenses()) {
                            if(!uniqueLicenses.contains(license)) {
                                uniqueLicenses.add(license);
                            }
                        }
                    }
                }
            }
        };

        assertDoesNotThrow( () -> pomUseCase.runAnalysis(new String[]{"-st", "0:300"}));

        MavenCentralArtifactAnalysis indexUseCase = new MavenCentralArtifactAnalysis(true, false, false, false) {
            public final Set<Artifact> hasJavadocs = new HashSet<>();
            @Override
            public void analyzeArtifact(Artifact toAnalyze) {
                if(toAnalyze.getIndexInformation() != null) {
                    List<Package> packages = toAnalyze.getIndexInformation().getPackages();
                    for(Package current : packages) {
                        if(current.getJavadocExists() > 0) {
                            hasJavadocs.add(toAnalyze);
                            break;
                        }
                    }
                }
            }
        };

        assertDoesNotThrow( () -> indexUseCase.runAnalysis(new String[]{"-st", "0:300"}));
    }

    private long getEndingIndex(Path fileName) {
        BufferedReader indexReader;
        try {
            indexReader = new BufferedReader(new FileReader(fileName.toFile()));
            String line = indexReader.readLine();
            return Integer.parseInt(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ArtifactConfigParser.ArtifactConfig parseCLI(String cli) {
        try {
            final ArtifactConfigParser parser = new ArtifactConfigParser();
            if(cli.isBlank()) return parser.parseArtifactConfig(new String[] {});
            else return parser.parseArtifactConfig(cli.split(" "));
        } catch(CLIException clix){
            throw new RuntimeException(clix);
        }

    }

    private int asInt(String s){
        return Integer.parseInt(s);
    }


}