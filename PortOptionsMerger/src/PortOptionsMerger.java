import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class PortOptionsMerger {

    private static final String BASE_PATH = "/usr/local/etc/poudriere.d/";
    private static final String MAKEFILE_SUFFIX = "-make.conf";
    private static final String OPTIONS_DIRECTORY_SUFFIX = "-options";
    private static final String CATEGORY_NAME_SPLITTER = "_";
    private static final String MAKEFILE_SET_PREFIX = "OPTIONS_SET+=";
    private static final String MAKEFILE_UNSET_PREFIX = "OPTIONS_UNSET+=";

    private final String makeFileLocation;
    private final String optionsDirectoryLocation;

    public PortOptionsMerger(String prefix) {
        this.makeFileLocation = BASE_PATH + prefix + MAKEFILE_SUFFIX;
        this.optionsDirectoryLocation = BASE_PATH + prefix + OPTIONS_DIRECTORY_SUFFIX;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Provide one option - file/directory prefix");
            return;
        }
        PortOptionsMerger merger = new PortOptionsMerger(args[0]);
        merger.merge();
    }

    public void merge() {
        OptionsParser globalOptionsParser = new OptionsParser(MAKEFILE_SET_PREFIX, MAKEFILE_UNSET_PREFIX);
        PortOptions globalOptions = parseOptionsFile(makeFileLocation, globalOptionsParser);
        System.out.print(globalOptions.getFormatted(MAKEFILE_SET_PREFIX, MAKEFILE_UNSET_PREFIX));
    }

    private PortOptions parseOptionsFile(String fileLocation, OptionsParser optionsParser) {
        try {
            Stream<String> fileLinesStream = Files.lines(Paths.get(fileLocation));
            return optionsParser.parse(fileLinesStream.iterator());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file " + fileLocation, e);
        }
    }
}
