package main.java.com.example.watermarkapp;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FolderImporter {
    public static List<File> setupFolderImport(JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = fileChooser.showOpenDialog(frame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            File[] files = selectedFolder.listFiles((dir, name) -> {
                String lowerCaseName = name.toLowerCase();
                return lowerCaseName.endsWith(".jpg") || lowerCaseName.endsWith(".jpeg") || lowerCaseName.endsWith(".png");
            });
            if (files != null) {
                List<File> importedFiles = new ArrayList<>();
                for (File file : files) {
                    importedFiles.add(file);
                    System.out.println("文件夹导入图片: " + file.getAbsolutePath());
                }
                return importedFiles;
            }
        }
        return null;
    }
}