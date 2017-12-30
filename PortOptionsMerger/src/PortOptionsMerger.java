import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortOptionsMerger {

    private static final String BASE_PATH = "/usr/local/etc/poudriere.d/";
    private static final String MAKE_CONF_SUFFIX = "-make.conf";
    private static final String OPTIONS_DIRECTORY_SUFFIX = "-options";
    private static final Path OPTIONS_FILE = Paths.get("options");
    private static final Pattern SET_UNSET_PATTERN = Pattern.compile("_(UN)?SET\\+=");

    private final String makeFileLocation;
    private final String optionsDirectoryLocation;

    private final BsdPort globalPort = new BsdPort();
    private final Map<String, BsdPort> portsMap = new HashMap<>();

    public PortOptionsMerger(String prefix) {
        this.makeFileLocation = BASE_PATH + prefix + MAKE_CONF_SUFFIX;
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
        parseMakeFile();
        parseOptionsFiles();
        deduplicateOptions();
        printMergedMakeFile();
    }

    private void parseMakeFile() {
        Path makeFile = Paths.get(makeFileLocation);
        if (!(Files.isReadable(makeFile) && Files.isRegularFile(makeFile)))
            return;
        for (Iterator<String> makeFileLinesIterator = getFileLinesIterator(makeFile); makeFileLinesIterator.hasNext(); ) {
            String makeFileLine = makeFileLinesIterator.next().trim();
            String portName;
            Matcher matcher = SET_UNSET_PATTERN.matcher(makeFileLine);
            if (makeFileLine.startsWith("#") || !matcher.find())
                continue;
            portName = makeFileLine.substring(0, matcher.start());
            if (globalPort.getName().equals(portName)) {
                globalPort.parseMakeFile(makeFileLine, makeFileLinesIterator);
            } else {
                BsdPort port = portsMap.computeIfAbsent(portName, BsdPort::new);
                port.parseMakeFile(makeFileLine, makeFileLinesIterator);
            }
        }
    }

    private void parseOptionsFiles() {
        try {
            Path optionsDirectory = Paths.get(optionsDirectoryLocation);
            if (!(Files.isReadable(optionsDirectory)
                    && Files.isExecutable(optionsDirectory)
                    && Files.isDirectory(optionsDirectory)))
                return;
            Files.list(optionsDirectory)
                    .filter(path -> Files.isReadable(path) && Files.isExecutable(path) && Files.isDirectory(path))
                    .forEach(this::parseOptionsDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Iterator<String> getFileLinesIterator(Path filePath) {
        try {
            return Files.lines(filePath).iterator();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file " + filePath, e);
        }
    }

    private void parseOptionsDirectory(Path optionsDirectory) {
        String portName = optionsDirectory.getFileName().toString();
        BsdPort port = portsMap.computeIfAbsent(portName, BsdPort::new);
        port.parseOptionsFile(getFileLinesIterator(optionsDirectory.resolve(OPTIONS_FILE)));
    }

    private void deduplicateOptions() {
        for (BsdPort port : portsMap.values()) {
            port.removeDuplicatingOptions(globalPort);
        }
    }

    private void printMergedMakeFile() {
        try (PrintWriter pw = new PrintWriter(System.out)) {
            globalPort.writeOptions(pw);
            List<BsdPort> portList = new ArrayList<>(portsMap.values());
            Collections.sort(portList);
            for (BsdPort port : portList) {
                port.writeOptions(pw);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
