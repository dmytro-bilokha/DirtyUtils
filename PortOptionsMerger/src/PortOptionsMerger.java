import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class PortOptionsMerger {

    private static final String BASE_PATH = "/usr/local/etc/poudriere.d/";
    private static final String MAKE_CONF_SUFFIX = "-make.conf";
    private static final String OPTIONS_DIRECTORY_SUFFIX = "-options";
    private static final Path OPTIONS_FILE = Paths.get("options");

    private final String makeFileLocation;
    private final String optionsDirectoryLocation;

    private final BsdPort globalPort = new BsdPort();
    private final Set<BsdPort> portsSet = new HashSet<>();

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
        globalPort.parseOptions(getFileLinesIterator(Paths.get(makeFileLocation)));
        try {
            Files.list(Paths.get(optionsDirectoryLocation))
                    .filter(path -> Files.isReadable(path) && Files.isExecutable(path) && Files.isDirectory(path))
                    .forEach(path -> portsSet.add(parseOptionsDirectory(path)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (PrintWriter pw = new PrintWriter(System.out)) {
            globalPort.writeOptions(pw);
            List<BsdPort> portList = new ArrayList<>(portsSet);
            Collections.sort(portList);
            for (BsdPort port : portList) {
                port.writeOptions(pw);
            }
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

    private BsdPort parseOptionsDirectory(Path optionsDirectory) {
        BsdPort port = new BsdPort(optionsDirectory.getFileName().toString());
        port.parseOptions(getFileLinesIterator(optionsDirectory.resolve(OPTIONS_FILE)));
        return port;
    }

}
