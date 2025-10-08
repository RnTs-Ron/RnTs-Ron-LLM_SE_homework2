package main.java.com.example.watermarkapp;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class ImageExporter {
    public static void setupExport(JFrame frame, List<File> images, int namingRule, String format, BufferedImage watermarkedImage) {
        if (watermarkedImage == null) {
            JOptionPane.showMessageDialog(frame, "请先添加水印！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("选择导出目录");
        int returnValue = fileChooser.showSaveDialog(frame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File outputDir = fileChooser.getSelectedFile();
            
            // 检查输出目录是否与原图片目录相同
            for (File image : images) {
                if (image.getParentFile().equals(outputDir)) {
                    JOptionPane.showMessageDialog(frame, "为防止覆盖原文件，请选择不同的输出目录！", "警告", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            
            int successCount = 0;
            int totalCount = images.size();
            
            for (File image : images) {
                String originalName = image.getName();
                String newName = generateNewFileName(originalName, namingRule, format);
                
                Path target = Paths.get(outputDir.getAbsolutePath(), newName);
                try {
                    // 确保输出目录存在
                    Files.createDirectories(outputDir.toPath());
                    
                    // 保存带水印的图片
                    String outputFormat = format.equals("JPEG") ? "jpg" : format.toLowerCase();
                    boolean success = javax.imageio.ImageIO.write(watermarkedImage, outputFormat, target.toFile());
                    
                    if (success) {
                        successCount++;
                        System.out.println("导出图片成功: " + target);
                    } else {
                        System.err.println("导出图片失败: " + target);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(frame, "导出失败: " + target.getFileName() + "\n错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
            
            if (successCount == totalCount) {
                JOptionPane.showMessageDialog(frame, "导出完成！成功导出 " + successCount + " 张图片。", "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "部分导出完成！成功导出 " + successCount + "/" + totalCount + " 张图片。", "提示", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    
    private static String generateNewFileName(String originalName, int namingRule, String format) {
        String newName;
        switch (namingRule) {
            case 0: // 保留原文件名
                newName = originalName;
                break;
            case 1: // 添加前缀
                newName = "wm_" + originalName;
                break;
            case 2: // 添加后缀
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > 0) {
                    String nameWithoutExt = originalName.substring(0, dotIndex);
                    String extension = originalName.substring(dotIndex);
                    newName = nameWithoutExt + "_watermarked" + extension;
                } else {
                    newName = originalName + "_watermarked";
                }
                break;
            default:
                newName = originalName;
        }
        
        // 根据用户选择的格式调整文件扩展名
        if (format.equals("PNG") && !newName.toLowerCase().endsWith(".png")) {
            newName = newName.replaceAll("\\.\\w+$", ".png");
        } else if (format.equals("JPEG") && !newName.toLowerCase().endsWith(".jpg") && !newName.toLowerCase().endsWith(".jpeg")) {
            newName = newName.replaceAll("\\.\\w+$", ".jpg");
        }
        
        return newName;
    }

    public static int showExportDialog(JFrame frame, String currentFileName) {
        Object[] options = {"保留原文件名", "添加前缀", "添加后缀"};
        String message = "<html><body><h3>选择命名规则：</h3><ul>" +
                "<li>保留原文件名：" + currentFileName + " → " + currentFileName + "</li>" +
                "<li>添加前缀：" + currentFileName + " → wm_" + currentFileName + "</li>" +
                "<li>添加后缀：" + currentFileName + " → " + currentFileName.replaceAll("\\.\\w+$", "") + "_watermarked" + currentFileName.substring(currentFileName.lastIndexOf('.')) + "</li></ul></body></html>";
        return JOptionPane.showOptionDialog(frame, message, "导出设置",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    }
}