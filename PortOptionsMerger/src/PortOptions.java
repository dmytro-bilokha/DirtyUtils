import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PortOptions {

    private static final Pattern OPTION_PATTERN = Pattern.compile("[\\p{Upper}\\p{Digit}_]+");
    private static final String NEW_LINE = System.lineSeparator();
    private static final String NEW_OPTION_LINE = "\\" + System.lineSeparator() + "\t\t";
    private static final int NEW_LINE_TRESHOLD = 80 - 15;

    private final Map<String, Status> optionsMap = new HashMap<>();

    public PortOptions addOptions(Status status, String[] options) {
        if (status != Status.SET && status != Status.UNSET)
            return this;
        for (String option : options) {
            if (!OPTION_PATTERN.matcher(option).matches())
                continue;
            optionsMap.put(option, status);
        }
        return this;
    }

    public String getSetOptionsString() {
        return formatOptions(getSortedOptionsWithStatus(Status.SET));
    }

    private List<String> getSortedOptionsWithStatus(Status status) {
        return optionsMap.entrySet()
                .stream()
                .filter(es -> es.getValue() == status)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    public String getUnsetOptionsString() {
        return formatOptions(getSortedOptionsWithStatus(Status.UNSET));
    }

    private String formatOptions(List<String> options) {
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

    public Status getOptionStatus(String option) {
        return optionsMap.getOrDefault(option, Status.NOT_DEFINED);
    }

    public String getFormatted(String setPrefix, String unsetPrefix) {
        return setPrefix + getSetOptionsString()
                + unsetPrefix + getUnsetOptionsString();
    }

    public enum Status {
        SET, UNSET, NOT_DEFINED;
    }

}
