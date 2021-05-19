import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.nio.file.Paths;

@Data
@NoArgsConstructor
public class FileHandler {
    private File rootDirectory = Paths.get("src", "main", "resources", "TestDirectory").toFile();
    private File currentDirectory = rootDirectory;




}
