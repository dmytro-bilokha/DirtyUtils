import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class EntityCorrector {

    private List<String> filePaths;

    private EntityCorrector(String[] args) {
        filePaths = Arrays.asList(args);
    }

    public void correct() {
        for (String filePathString : filePaths) {
            System.out.println("Processing file " + filePathString);
            Path filePath = Paths.get(filePathString);
            processFile(filePath);
        }
    }

    private void processFile(Path filePath) {
        try {
            JavaFile javaFile = new JavaFile(filePath);
            javaFile.process();
            javaFile.save();
        } catch (IOException | IllegalStateException | IllegalArgumentException ex) {
            System.err.println("Failed to process file=" + filePath);
            System.err.println(ex.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Pass list of source filePaths as arguments!");
            return;
        }
        EntityCorrector entityCorrector = new EntityCorrector(args);
        entityCorrector.correct();
    }

}
