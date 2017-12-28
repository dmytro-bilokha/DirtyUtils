import java.util.Iterator;
import java.util.regex.Pattern;

public class OptionsParser {

    private static final Pattern SEPARATION_PATTERN = Pattern.compile("\\s");

    private final String setPattern;
    private final String unsetPattern;

    public OptionsParser(String setPattern, String unsetPattern) {
        this.setPattern = setPattern;
        this.unsetPattern = unsetPattern;
    }

    public PortOptions parse(Iterator<String> lineIterator) {
        PortOptions portOptions = new PortOptions();
        PortOptions.Status lineStatus = PortOptions.Status.NOT_DEFINED;
        while (lineIterator.hasNext()) {
            String line = lineIterator.next().trim();
            if (line.startsWith(setPattern)) {
                lineStatus = PortOptions.Status.SET;
                line = line.substring(setPattern.length());
            } else if (line.startsWith(unsetPattern)) {
                lineStatus = PortOptions.Status.UNSET;
                line = line.substring(unsetPattern.length());
            }
            if (lineStatus != PortOptions.Status.NOT_DEFINED) {
                String[] options = SEPARATION_PATTERN.split(line);
                portOptions.addOptions(lineStatus, options);
                if (options.length > 0 && !"\\".equals(options[options.length-1]))
                    lineStatus = PortOptions.Status.NOT_DEFINED;
            }
        }
        return portOptions;
    }

}
