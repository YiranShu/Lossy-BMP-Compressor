import javax.swing.*;
import java.io.File;

public class FileSelector extends JFrame {
    private String path;

    public String getPath() {
        return path;
    }

    public FileSelector() { // get the absolute path of the selected file
        path = null;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.showDialog(new JLabel(), "select");
        File file = fileChooser.getSelectedFile();
        if(file != null && file.isFile()){
            path = file.getAbsolutePath();
        }
    }
}
