import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaFile {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\h*import .*");
    private static final Pattern IMPORT_TABLE_PATTERN = Pattern.compile("^\\h*import\\h+javax\\.persistence\\.Table.*");
    private static final Pattern IMPORT_COLUMN_PATTERN = Pattern.compile("^\\h*import\\h+javax\\.persistence\\.Column.*");
    private static final Pattern CLASS_PATTERN = Pattern.compile("^\\h*public\\h+(abstract\\h+)?class.*");
    private static final Pattern ENTITY_PATTERN = Pattern.compile("^\\h*@Entity.*");
    private static final Pattern MAPPED_SUPERCLASS_PATTERN = Pattern.compile("^\\h*@MappedSuperclass.*");
    private static final Pattern TABLE_PATTERN = Pattern.compile("^\\h*@Table\\(\\h*name.*");
    private static final Pattern GENERAL_FIELD_PATTERN = Pattern.compile("^\\h*(private|protected)\\h+[0-9a-zA-Z<>]+\\h+.*;.*");
    private static final Pattern FIELD_PATTERN
            = Pattern.compile("^(?<spaces>\\h*)(private|protected)\\h+(String|DateTime|Date|int|Integer|[Ll]ong|[Bb]oolean"
            + "|[Bb]yte|[Ss]hort)\\h+(?<name>\\w+).*;.*");
    private static final Pattern STOPPER
            = Pattern.compile("^\\h*@(JoinTable|Transient|JoinColumn|Column\\(.*name\\h*=\\h*\").*");
    private List<String> content;
    private Path filePath;
    private String className;
    private Integer firstImportLine;
    private Integer lastImportLine;
    private Integer classLine;
    private boolean entityPresent = false;
    private boolean mappedSuperclassPresent = false;
    private boolean entityTablePresent = false;
    private boolean importTablePresent = false;
    private boolean importColumnPresent = false;

    public JavaFile(Path filePath) throws IOException {
        this.filePath = filePath;
        String fileName = filePath.getName(filePath.getNameCount()-1).toString();
        className = fileName.replaceFirst("\\.java$", "");
        content = Files.readAllLines(filePath);
        for (int lineNumber = 0; lineNumber < content.size(); lineNumber++) {
            String fileLine = content.get(lineNumber);
            checkClassLine(lineNumber, fileLine);
            checkEntity(fileLine);
            checkMappedSuperclass(fileLine);
            updateImportLinesRange(lineNumber, fileLine);
            checkImportTable(fileLine);
            checkImportColumn(fileLine);
            checkEntityTable(fileLine);
        }
        validate();
    }

    private void checkClassLine(int lineNumber, String fileLine) {
        if (classLine != null)
            return;
        if (CLASS_PATTERN.matcher(fileLine).matches())
            classLine = lineNumber;
    }

    private void checkEntity(String fileLine) {
        if (classLine != null || entityPresent)
            return;
        if (ENTITY_PATTERN.matcher(fileLine).matches())
            entityPresent = true;
    }

    private void checkMappedSuperclass(String fileLine) {
        if (classLine != null || mappedSuperclassPresent)
            return;
        if (MAPPED_SUPERCLASS_PATTERN.matcher(fileLine).matches())
            mappedSuperclassPresent = true;
    }

    private void updateImportLinesRange(int lineNumber, String fileLine) {
        if (classLine != null)
            return;
        if (!IMPORT_PATTERN.matcher(fileLine).matches())
            return;
        if (firstImportLine == null)
            firstImportLine = lineNumber;
        lastImportLine = lineNumber;
    }

    private void checkImportTable(String fileLine) {
        if (classLine != null || importTablePresent)
            return;
        if (IMPORT_TABLE_PATTERN.matcher(fileLine).matches())
            importTablePresent = true;
    }

    private void checkImportColumn(String fileLine) {
        if (classLine != null || importColumnPresent)
            return;
        if (IMPORT_COLUMN_PATTERN.matcher(fileLine).matches())
            importColumnPresent = true;
    }

    private void checkEntityTable(String fileLine) {
        if (classLine != null || entityTablePresent)
            return;
        if (TABLE_PATTERN.matcher(fileLine).matches())
            entityTablePresent = true;
    }

    private void validate() {
        if (classLine == null)
            throw new IllegalStateException("Class line not found");
        if (!entityPresent && !mappedSuperclassPresent)
            throw new IllegalStateException("Neither @Entity, nor @MappedSuperclass line found");
    }

    private String convertToDbFormat(String name) {
        StringBuilder dbNameBuilder = new StringBuilder();
        boolean wasLastCharUpperCase = true;
        for (char currentSymbol : name.toCharArray()) {
            if (Character.isUpperCase(currentSymbol)) {
                if (!wasLastCharUpperCase)
                    dbNameBuilder.append('_');
                wasLastCharUpperCase = true;
            } else {
                wasLastCharUpperCase = false;
            }
            dbNameBuilder.append(Character.toUpperCase(currentSymbol));
        }
        return dbNameBuilder.toString();
    }

    public void process() {
        if (!importColumnPresent)
            addImportColumn();
        if (!entityTablePresent && !mappedSuperclassPresent) {
            addImportTable();
            addEntityTable();
        }
        addColumnAnnotations();
    }

    private void addImportTable() {
        content.add(lastImportLine, "import javax.persistence.Table;");
        incrementLineNumbersAfterNewImport();
        importTablePresent = true;
    }

    private void incrementLineNumbersAfterNewImport() {
        lastImportLine++;
        classLine++;
    }

    private void addImportColumn() {
        content.add(lastImportLine, "import javax.persistence.Column;");
        incrementLineNumbersAfterNewImport();
        importColumnPresent = true;
    }

    private void addEntityTable() {
        StringBuilder tableAnnotationBuilder = new StringBuilder("@Table(name = \"");
        tableAnnotationBuilder.append(convertToDbFormat(className));
        tableAnnotationBuilder.append("\")");
        content.add(classLine, tableAnnotationBuilder.toString());
        classLine++;
    }

    private void addColumnAnnotations() {
        List<String> resultList = new ArrayList<>(content.subList(0, classLine + 1));
        int lastFieldLineNumber = classLine +1;
        for (int lineNumber = classLine + 1; lineNumber < content.size(); lineNumber++) {
            String line = content.get(lineNumber);
            Matcher matcher = FIELD_PATTERN.matcher(line);
            if (matcher.matches() && shouldInsertColumnAnnotation(lastFieldLineNumber, lineNumber)) {
                String spaces = matcher.group("spaces");
                String fieldName = matcher.group("name");
                StringBuilder columnAnnotationBuilder = new StringBuilder(spaces);
                columnAnnotationBuilder.append("@Column(name = \"");
                columnAnnotationBuilder.append(convertToDbFormat(fieldName));
                columnAnnotationBuilder.append("\")");
                resultList.add(columnAnnotationBuilder.toString());
            }
            if (GENERAL_FIELD_PATTERN.matcher(line).matches())
                lastFieldLineNumber = lineNumber;
            resultList.add(line);
        }
        content = resultList;
    }

    private boolean shouldInsertColumnAnnotation(int lastFieldLineNumber, int currentLineNumber) {
        for (int lineNumber = lastFieldLineNumber + 1; lineNumber < currentLineNumber; lineNumber++) {
            if(STOPPER.matcher(content.get(lineNumber)).matches())
                return false;
        }
        return true;
    }

    public void save() throws IOException {
        Files.write(filePath, content, StandardOpenOption.WRITE);
    }
}
