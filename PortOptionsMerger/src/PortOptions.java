import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PortOptions {

    private static final Pattern OPTION_PATTERN = Pattern.compile("[\\p{Upper}\\p{Digit}]+");
    private static final String NEW_LINE = System.lineSeparator();
    private static final String NEW_OPTION_LINE = "\\" + System.lineSeparator() + "\t\t";
    private static final int NEW_LINE_TRESHOLD = 80 - 15;

    private final Set<String> setOptions = new HashSet<>();
    private final Set<String> unsetOptions = new HashSet<>();

    public PortOptions setOption(String option) {
        unsetOptions.remove(option);
        setOptions.add(option);
        return this;
    }

    public PortOptions unsetOption(String option) {
        setOptions.remove(option);
        unsetOptions.add(option);
        return this;
    }

    public PortOptions addOptions(Status status, String[] options) {
        if (status != Status.SET && status != Status.UNSET)
            return this;
        for (String option : options) {
            if (!OPTION_PATTERN.matcher(option).matches())
                continue;
            if (status == Status.SET)
                setOption(option);
            if (status == Status.UNSET)
                unsetOption(option);
        }
        return this;
    }

    public String getSetOptionsString() {
        return formatOptions(setOptions);
    }

    public String getUnsetOptionsString() {
        return formatOptions(unsetOptions);
    }

    private String formatOptions(Collection<String> optionsToFormat) {
        List<String> options = new ArrayList<>(optionsToFormat);
        Collections.sort(options);
        StringBuilder outputBuilder = new StringBuilder("\t");
        for (String option : options) {
            if (outputBuilder.length() + option.length() > NEW_LINE_TRESHOLD && option.length() < NEW_LINE_TRESHOLD) {
                outputBuilder.append(NEW_OPTION_LINE);
            }
            outputBuilder
                    .append(option)
                    .append(' ');
        }
        outputBuilder.append(NEW_LINE);
        return outputBuilder.toString();
    }

    public Status getOptionStatus(String option) {
        if (setOptions.contains(option))
            return Status.SET;
        if (unsetOptions.contains(option))
            return Status.UNSET;
        return Status.NOT_DEFINED;
    }

    public enum Status {
        SET, UNSET, NOT_DEFINED;
    }

}
