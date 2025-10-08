package main.java.com.example.watermarkapp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class ConfigManager {
    private static final String DATA_DIR = "./data";
    private static final String TEMPLATES_FILE = DATA_DIR + "/templates.properties";
    private static final String CONFIG_FILE = DATA_DIR + "/app_config.properties";

    public interface TemplateLoadCallback {
        void loadTemplate(String text, Color color, int transparency, int x, int y);
        void loadImageWatermark(String imagePath, int transparency, int x, int y);
    }

    // 保存模板
    public static boolean saveTemplate(String name, String text, Color color, int transparency, int x, int y, String imagePath) {
        try {
            // 确保数据目录存在
            Files.createDirectories(Paths.get(DATA_DIR));

            // 读取现有模板
            Properties templates = readTemplatesFile();

            // 创建模板键
            String templateKey = "template." + name;
            if (imagePath != null && !imagePath.isEmpty()) {
                templates.setProperty(templateKey + ".image_path", imagePath);
            } else {
                templates.setProperty(templateKey + ".text", text);
                templates.setProperty(templateKey + ".color_r", String.valueOf(color.getRed()));
                templates.setProperty(templateKey + ".color_g", String.valueOf(color.getGreen()));
                templates.setProperty(templateKey + ".color_b", String.valueOf(color.getBlue()));
            }
            templates.setProperty(templateKey + ".transparency", String.valueOf(transparency));
            templates.setProperty(templateKey + ".x", String.valueOf(x));
            templates.setProperty(templateKey + ".y", String.valueOf(y));

            // 保存模板列表
            String templateList = templates.getProperty("template.list", "");
            if (!templateList.contains(name)) {
                if (!templateList.isEmpty()) {
                    templateList += ",";
                }
                templateList += name;
                templates.setProperty("template.list", templateList);
            }

            // 写回文件
            try (FileOutputStream out = new FileOutputStream(TEMPLATES_FILE)) {
                templates.store(out, "Watermark Templates");
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 显示模板管理对话框
    public static void showTemplateManagementDialog(JFrame parent, TemplateLoadCallback callback) {
        JDialog dialog = new JDialog(parent, "管理模板", true);
        dialog.setSize(400, 300);
        dialog.setLayout(new BorderLayout());
        dialog.setLocationRelativeTo(parent);

        // 模板列表
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> templateList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(templateList);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 5, 5));
        JButton loadButton = new JButton("加载");
        JButton deleteButton = new JButton("删除");
        JButton setDefaultButton = new JButton("设为默认");
        JButton closeButton = new JButton("关闭");

        buttonPanel.add(loadButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(setDefaultButton);
        buttonPanel.add(closeButton);

        // 自动加载设置
        JPanel autoLoadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox autoLoadCheckbox = new JCheckBox("自动加载上一次关闭时的设置");
        autoLoadPanel.add(autoLoadCheckbox);

        // 读取当前设置
        boolean autoLoadEnabled = isAutoLoadEnabled();
        autoLoadCheckbox.setSelected(autoLoadEnabled);

        // 加载模板列表
        loadTemplateList(listModel);

        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.add(autoLoadPanel, BorderLayout.NORTH);

        // 按钮事件
        loadButton.addActionListener(e -> {
            int selectedIndex = templateList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String templateName = listModel.getElementAt(selectedIndex);
                loadTemplate(templateName, callback);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "请先选择一个模板！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });

        deleteButton.addActionListener(e -> {
            int selectedIndex = templateList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String templateName = listModel.getElementAt(selectedIndex);
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "确定要删除模板 '" + templateName + "' 吗？", "确认删除", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    if (deleteTemplate(templateName)) {
                        listModel.remove(selectedIndex);
                        JOptionPane.showMessageDialog(dialog, "模板删除成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(dialog, "模板删除失败！", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "请先选择一个模板！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });

        setDefaultButton.addActionListener(e -> {
            int selectedIndex = templateList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String templateName = listModel.getElementAt(selectedIndex);
                if (setDefaultTemplate(templateName)) {
                    JOptionPane.showMessageDialog(dialog, "默认模板设置成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(dialog, "默认模板设置失败！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "请先选择一个模板！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });

        closeButton.addActionListener(e -> {
            // 保存自动加载设置
            setAutoLoadEnabled(autoLoadCheckbox.isSelected());
            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    // 加载模板
    public static void loadTemplate(String name, TemplateLoadCallback callback) {
        try {
            Properties templates = readTemplatesFile();
            String templateKey = "template." + name;

            if (templates.containsKey(templateKey + ".image_path")) {
                String imagePath = templates.getProperty(templateKey + ".image_path");
                int transparency = Integer.parseInt(templates.getProperty(templateKey + ".transparency"));
                int x = Integer.parseInt(templates.getProperty(templateKey + ".x"));
                int y = Integer.parseInt(templates.getProperty(templateKey + ".y"));
                callback.loadImageWatermark(imagePath, transparency, x, y);

                // 保存为最后使用的配置
                saveLastConfig(null, null, transparency, x, y, imagePath);
            } else if (templates.containsKey(templateKey + ".text")) {
                String text = templates.getProperty(templateKey + ".text");
                int r = Integer.parseInt(templates.getProperty(templateKey + ".color_r"));
                int g = Integer.parseInt(templates.getProperty(templateKey + ".color_g"));
                int b = Integer.parseInt(templates.getProperty(templateKey + ".color_b"));
                int transparency = Integer.parseInt(templates.getProperty(templateKey + ".transparency"));
                int x = Integer.parseInt(templates.getProperty(templateKey + ".x"));
                int y = Integer.parseInt(templates.getProperty(templateKey + ".y"));

                Color color = new Color(r, g, b);
                callback.loadTemplate(text, color, transparency, x, y);

                // 保存为最后使用的配置
                saveLastConfig(text, color, transparency, x, y, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "加载模板失败！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 加载最后配置
    public static void loadLastConfig(TemplateLoadCallback callback) {
        try {
            if (!Files.exists(Paths.get(CONFIG_FILE))) {
                return;
            }

            Properties config = readConfigFile();

            // 检查是否启用自动加载
            if (!Boolean.parseBoolean(config.getProperty("auto_load_enabled", "false"))) {
                return;
            }

            // 检查是否有最后使用的配置
            if (config.containsKey("last_config.text")) {
                String text = config.getProperty("last_config.text");
                int r = Integer.parseInt(config.getProperty("last_config.color_r"));
                int g = Integer.parseInt(config.getProperty("last_config.color_g"));
                int b = Integer.parseInt(config.getProperty("last_config.color_b"));
                int transparency = Integer.parseInt(config.getProperty("last_config.transparency"));
                int x = Integer.parseInt(config.getProperty("last_config.x"));
                int y = Integer.parseInt(config.getProperty("last_config.y"));

                Color color = new Color(r, g, b);
                callback.loadTemplate(text, color, transparency, x, y);
            } else {
                // 如果没有最后配置，尝试加载默认模板
                String defaultTemplate = config.getProperty("default_template", "");
                if (!defaultTemplate.isEmpty()) {
                    loadTemplate(defaultTemplate, callback);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 私有辅助方法
    private static Properties readTemplatesFile() {
        Properties templates = new Properties();
        try {
            if (Files.exists(Paths.get(TEMPLATES_FILE))) {
                try (FileInputStream in = new FileInputStream(TEMPLATES_FILE)) {
                    templates.load(in);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return templates;
    }

    private static void loadTemplateList(DefaultListModel<String> listModel) {
        try {
            Properties templates = readTemplatesFile();
            String templateList = templates.getProperty("template.list", "");
            if (!templateList.isEmpty()) {
                String[] templateNames = templateList.split(",");
                for (String name : templateNames) {
                    if (!name.trim().isEmpty()) {
                        listModel.addElement(name.trim());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean deleteTemplate(String name) {
        try {
            Properties templates = readTemplatesFile();
            String templateKey = "template." + name;

            // 删除模板属性
            templates.remove(templateKey + ".text");
            templates.remove(templateKey + ".color_r");
            templates.remove(templateKey + ".color_g");
            templates.remove(templateKey + ".color_b");
            templates.remove(templateKey + ".transparency");
            templates.remove(templateKey + ".x");
            templates.remove(templateKey + ".y");

            // 从模板列表中移除
            String templateList = templates.getProperty("template.list", "");
            List<String> templateNames = new ArrayList<>(Arrays.asList(templateList.split(",")));
            templateNames.remove(name);
            templates.setProperty("template.list", String.join(",", templateNames));

            // 写回文件
            try (FileOutputStream out = new FileOutputStream(TEMPLATES_FILE)) {
                templates.store(out, "Watermark Templates");
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean setDefaultTemplate(String name) {
        try {
            Properties config = readConfigFile();
            config.setProperty("default_template", name);

            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                config.store(out, "Application Configuration");
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Properties readConfigFile() {
        Properties config = new Properties();
        try {
            if (Files.exists(Paths.get(CONFIG_FILE))) {
                try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                    config.load(in);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return config;
    }

    private static boolean isAutoLoadEnabled() {
        try {
            Properties config = readConfigFile();
            return Boolean.parseBoolean(config.getProperty("auto_load_enabled", "false"));
        } catch (Exception e) {
            return false;
        }
    }

    private static void setAutoLoadEnabled(boolean enabled) {
        try {
            Properties config = readConfigFile();
            config.setProperty("auto_load_enabled", String.valueOf(enabled));

            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                config.store(out, "Application Configuration");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveLastConfig(String text, Color color, int transparency, int x, int y, String imagePath) {
        try {
            Properties config = readConfigFile();

            if (text != null && color != null) {
                config.setProperty("last_config.text", text);
                config.setProperty("last_config.color_r", String.valueOf(color.getRed()));
                config.setProperty("last_config.color_g", String.valueOf(color.getGreen()));
                config.setProperty("last_config.color_b", String.valueOf(color.getBlue()));
            }
            if (imagePath != null) {
                config.setProperty("last_config.image_path", imagePath);
            }
            config.setProperty("last_config.transparency", String.valueOf(transparency));
            config.setProperty("last_config.x", String.valueOf(x));
            config.setProperty("last_config.y", String.valueOf(y));

            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                config.store(out, "Application Configuration");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}