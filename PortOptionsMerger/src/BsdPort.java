
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BsdPort implements Comparable<BsdPort> {

    private static final Pattern SEPARATION_PATTERN = Pattern.compile("\\s");
    private static final Pattern OPTION_PATTERN = Pattern.compile("[\\p{Upper}\\p{Digit}_]+");
    private static final String NEW_LINE = System.lineSeparator();
    private static final String NEW_OPTION_LINE = "\\" + System.lineSeparator() + "\t\t\t";
    private static final int NEW_LINE_TRESHOLD = 80 - 15;

    private final Map<String, OptionStatus> optionsMap = new HashMap<>();
    private final String category;
    private final String name;
    private final String slashedName;
    private final String inOptionsSet;
    private final String inOptionsUnset;
    private final String outOptionsSet;
    private final String outOptionsUnset;

    public BsdPort(String fullName) {
        this.category = fullName.split("_")[0];
        this.name = fullName.substring(category.length() + 1);
        this.slashedName = category + '/' + name;
        this.inOptionsSet = "OPTIONS_FILE_SET+=";
        this.inOptionsUnset = "OPTIONS_FILE_UNSET+=";
        this.outOptionsSet = name + "_SET+=  ";
        this.outOptionsUnset = name + "_UNSET+=";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BsdPort that = (BsdPort) o;
        return Objects.equals(slashedName, that.slashedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slashedName);
    }

    public BsdPort() {
        this.category = "Global";
        this.name = "OPTIONS";
        this.slashedName = "Global ports";
        this.inOptionsSet = name + "_SET+=";
        this.inOptionsUnset = name + "_UNSET+=";
        this.outOptionsSet = name + "_SET+=  ";
        this.outOptionsUnset = name + "_UNSET+=";
    }


    public void parseOptions(Iterator<String> lineIterator) {
        OptionStatus lineStatus = OptionStatus.NOT_DEFINED;
        while (lineIterator.hasNext()) {
            String line = lineIterator.next().trim();
            if (line.startsWith(inOptionsSet)) {
                lineStatus = OptionStatus.SET;
                line = line.substring(inOptionsSet.length());
            } else if (line.startsWith(inOptionsUnset)) {
                lineStatus = OptionStatus.UNSET;
                line = line.substring(inOptionsUnset.length());
            }
            if (lineStatus != OptionStatus.NOT_DEFINED) {
                String[] options = SEPARATION_PATTERN.split(line);
                addOptions(lineStatus, options);
                if (options.length > 0 && !"\\".equals(options[options.length-1]))
                    lineStatus = OptionStatus.NOT_DEFINED;
            }
        }
    }

    private void addOptions(OptionStatus status, String[] options) {
        if (status != OptionStatus.SET && status != OptionStatus.UNSET)
            return;
        for (String option : options) {
            if (!OPTION_PATTERN.matcher(option).matches())
                continue;
            optionsMap.put(option, status);
        }
    }

    public OptionStatus getOptionStatus(String option) {
        return optionsMap.getOrDefault(option, OptionStatus.NOT_DEFINED);
    }

    public void writeOptions(Writer writer) throws IOException {
        writer.append(getOptionsComment())
                .append(getSetOptionsString())
                .append(getUnsetOptionsString())
                .append(NEW_LINE);
    }

    private String getOptionsComment() {
        return "# " + slashedName + " options" + NEW_LINE;
    }

    private String getSetOptionsString() {
        String optionsString = formatOptions(getSortedOptionsWithStatus(OptionStatus.SET));
        if (optionsString.isEmpty())
            return optionsString;
        return outOptionsSet + optionsString;

    }

    private List<String> getSortedOptionsWithStatus(OptionStatus status) {
        return optionsMap.entrySet()
                .stream()
                .filter(es -> es.getValue() == status)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    private String getUnsetOptionsString() {
        String optionsString = formatOptions(getSortedOptionsWithStatus(OptionStatus.UNSET));
        if (optionsString.isEmpty())
            return optionsString;
        return outOptionsUnset + optionsString;
    }

    private String formatOptions(List<String> options) {
        if (options.isEmpty())
            return "";
        StringBuilder outputBuilder = new StringBuilder("\t");
        int lineLength = 0;
        for (String option : options) {
            if (lineLength + option.length() > NEW_LINE_TRESHOLD && option.length() < NEW_LINE_TRESHOLD) {
                outputBuilder.append(NEW_OPTION_LINE);
                lineLength = 0;
            }
            outputBuilder
                    .append(option)
                    .append(' ');
            lineLength += option.length() + 1;
        }
        outputBuilder.append(NEW_LINE);
        return outputBuilder.toString();
    }

    @Override
    public int compareTo(BsdPort o) {
        return this.slashedName.compareTo(o.slashedName);
    }

    public enum OptionStatus {
        SET, UNSET, NOT_DEFINED;
    }

}