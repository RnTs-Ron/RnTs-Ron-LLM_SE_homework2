package main.java.com.example.watermarkapp;

import main.java.com.example.watermarkapp.FolderImporter;
import main.java.com.example.watermarkapp.ImageExporter;
import main.java.com.example.watermarkapp.ImageImporter;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.LookupOp;
import java.awt.image.ShortLookupTable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainWindow {
    private static DefaultListModel<String> fileListModel;
    private static JList<String> fileList;
    private static List<File> importedFiles = new ArrayList<>();
    private static JLabel imagePreview;
    private static JSlider transparencySlider;
    private static String watermarkText = "";
    private static ImageIcon currentIcon;
    private static BufferedImage watermarkedImage;
    private static Color watermarkColor = Color.BLACK;
    private static int watermarkX = 0;
    private static int watermarkY = 0;
    private static boolean isDragging = false;
    private static boolean isResizing = false;
    private static Point dragOffset = new Point();

    // 图片水印相关变量
    private static BufferedImage originalWatermarkImage;
    private static BufferedImage currentWatermarkImage;
    private static boolean isImageWatermark = false;
    private static int watermarkWidth = 100;
    private static int watermarkHeight = 100;
    private static final int RESIZE_HANDLE_SIZE = 8;

    public static void createAndShowGUI() {
        JFrame frame = new JFrame("图片水印工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // 左侧文件列表
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index >= 0 && index < importedFiles.size()) {
                    File file = importedFiles.get(index);
                    try {
                        BufferedImage thumbnail = ImageIO.read(file);
                        if (thumbnail != null) {
                            ImageIcon icon = new ImageIcon(thumbnail.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
                            label.setIcon(icon);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return label;
            }
        });
        JScrollPane listScrollPane = new JScrollPane(fileList);
        listScrollPane.setPreferredSize(new Dimension(200, 0));

        // 中央面板（包含图片预览和右侧控制区域）
        JPanel centerPanel = new JPanel(new BorderLayout());

        // 图片预览面板
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        imagePanel.setBackground(Color.WHITE);

        // 图片预览区域
        imagePreview = new JLabel();
        imagePreview.setHorizontalAlignment(JLabel.CENTER);
        imagePreview.setVerticalAlignment(JLabel.CENTER);
        imagePanel.add(imagePreview, BorderLayout.CENTER);

        // 右侧控制面板
        JPanel rightControlPanel = new JPanel();
        rightControlPanel.setLayout(new BoxLayout(rightControlPanel, BoxLayout.Y_AXIS));
        rightControlPanel.setBorder(BorderFactory.createTitledBorder("水印控制"));
        rightControlPanel.setPreferredSize(new Dimension(250, 0));

        // 1. 透明度控制区域
        JPanel transparencyPanel = new JPanel(new BorderLayout());
        transparencyPanel.setBorder(BorderFactory.createTitledBorder("透明度"));
        transparencySlider = new JSlider(0, 100, 50);
        transparencySlider.setMajorTickSpacing(25);
        transparencySlider.setMinorTickSpacing(5);
        transparencySlider.setPaintTicks(true);
        transparencySlider.setPaintLabels(true);
        JLabel transparencyLabel = new JLabel("50%", JLabel.CENTER);
        transparencyPanel.add(transparencySlider, BorderLayout.CENTER);
        transparencyPanel.add(transparencyLabel, BorderLayout.SOUTH);

        // 添加透明度变化监听器
        transparencySlider.addChangeListener(e -> {
            transparencyLabel.setText(transparencySlider.getValue() + "%");
            if (fileList.getSelectedIndex() >= 0) {
                if (isImageWatermark && currentWatermarkImage != null) {
                    addImageWatermark();
                } else if (watermarkText != null && !watermarkText.isEmpty()) {
                    addWatermark();
                }
            }
        });

        // 2. 颜色控制区域
        JPanel colorPanel = new JPanel(new BorderLayout());
        colorPanel.setBorder(BorderFactory.createTitledBorder("水印颜色"));

        // 颜色选择按钮
        JButton colorButton = new JButton("RGB调色盘");
        colorButton.setEnabled(false); // 默认禁用

        // 当前颜色预览
        JLabel colorPreview = new JLabel("当前颜色");
        colorPreview.setOpaque(true);
        colorPreview.setBackground(Color.BLACK);
        colorPreview.setForeground(Color.WHITE);
        colorPreview.setPreferredSize(new Dimension(100, 20));
        colorPreview.setHorizontalAlignment(JLabel.CENTER);

        colorButton.addActionListener(e -> {
            Color selectedColor = JColorChooser.showDialog(frame, "选择水印颜色", watermarkColor);
            if (selectedColor != null) {
                watermarkColor = selectedColor;
                colorPreview.setBackground(watermarkColor);

                // 根据颜色亮度调整文字颜色以确保可读性
                int brightness = (int)((watermarkColor.getRed() * 0.299) +
                        (watermarkColor.getGreen() * 0.587) +
                        (watermarkColor.getBlue() * 0.114));
                colorPreview.setForeground(brightness > 128 ? Color.BLACK : Color.WHITE);

                if (fileList.getSelectedIndex() >= 0) {
                    if (isImageWatermark && currentWatermarkImage != null) {
                        applyColorFilterToWatermark();
                        addImageWatermark();
                    } else if (watermarkText != null && !watermarkText.isEmpty()) {
                        addWatermark();
                    }
                }
            }
        });

        JPanel colorControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        colorControlPanel.add(colorButton);
        colorControlPanel.add(colorPreview);
        colorPanel.add(colorControlPanel, BorderLayout.CENTER);

        // 3. 九宫格位置控制区域
        JPanel positionPanel = new JPanel(new GridLayout(3, 3, 5, 5));
        positionPanel.setBorder(BorderFactory.createTitledBorder("预设位置"));
        String[] positions = {"左上", "上中", "右上", "左中", "正中", "右中", "左下", "下中", "右下"};
        for (int i = 0; i < 9; i++) {
            JButton posBtn = new JButton(positions[i]);
            posBtn.setFont(new Font("微软雅黑", Font.PLAIN, 10));
            final int position = i;
            posBtn.addActionListener(e -> {
                // 检查是否有图片
                if (importedFiles.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "请先导入图片！", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // 检查是否选择了图片
                int selectedIndex = fileList.getSelectedIndex();
                if (selectedIndex < 0) {
                    JOptionPane.showMessageDialog(frame, "请先选择一张图片！", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // 检查是否有水印
                if ((!isImageWatermark || currentWatermarkImage == null) &&
                        (watermarkText == null || watermarkText.trim().isEmpty())) {
                    JOptionPane.showMessageDialog(frame, "请先添加水印！", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                setWatermarkPosition(position);
                if (isImageWatermark && currentWatermarkImage != null) {
                    addImageWatermark();
                } else {
                    addWatermark();
                }
            });
            positionPanel.add(posBtn);
        }

        // 4. 模板管理区域
        JPanel templatePanel = new JPanel(new GridLayout(1, 2, 5, 5));
        templatePanel.setBorder(BorderFactory.createTitledBorder("配置管理"));

        JButton saveTemplateButton = new JButton("保存模板");
        JButton manageTemplateButton = new JButton("管理模板");

        // 在 MainWindow.java 中找到保存模板按钮的事件处理代码，修改如下：

        saveTemplateButton.addActionListener(e -> {
            // 检查是否是图片水印
            if (isImageWatermark && currentWatermarkImage != null) {
                JOptionPane.showMessageDialog(frame, "暂不支持保存图片水印模板", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if ((!isImageWatermark || currentWatermarkImage == null) &&
                    (watermarkText == null || watermarkText.trim().isEmpty())) {
                JOptionPane.showMessageDialog(frame, "请先添加水印！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String templateName = JOptionPane.showInputDialog(frame, "请输入模板名称：", "保存模板", JOptionPane.PLAIN_MESSAGE);
            if (templateName != null && !templateName.trim().isEmpty()) {
                boolean success = ConfigManager.saveTemplate(
                        templateName.trim(),
                        watermarkText,
                        watermarkColor,
                        transparencySlider.getValue(),
                        watermarkX,
                        watermarkY,
                        isImageWatermark && currentWatermarkImage != null ? originalWatermarkImage.toString() : ""
                );

                if (success) {
                    JOptionPane.showMessageDialog(frame, "模板保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame, "模板保存失败！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        manageTemplateButton.addActionListener(e -> {
            ConfigManager.showTemplateManagementDialog(frame, new ConfigManager.TemplateLoadCallback() {
            @Override
            public void loadTemplate(String text, Color color, int transparency, int x, int y) {
                MainWindow.loadTemplate(text, color, transparency, x, y);
            }

            @Override
            public void loadImageWatermark(String imagePath, int transparency, int x, int y) {
                MainWindow.loadImageWatermark(imagePath, transparency, x, y);
            }
        });
        });

        templatePanel.add(saveTemplateButton);
        templatePanel.add(manageTemplateButton);

        // 5. 按钮控制区域
        JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        buttonPanel.setBorder(BorderFactory.createTitledBorder("操作"));

        // 组装右侧控制面板
        rightControlPanel.add(Box.createVerticalStrut(10));
        rightControlPanel.add(transparencyPanel);
        rightControlPanel.add(Box.createVerticalStrut(10));
        rightControlPanel.add(colorPanel);
        rightControlPanel.add(Box.createVerticalStrut(10));
        rightControlPanel.add(positionPanel);
        rightControlPanel.add(Box.createVerticalStrut(10));
        rightControlPanel.add(templatePanel);
        rightControlPanel.add(Box.createVerticalStrut(10));
        rightControlPanel.add(buttonPanel);
        rightControlPanel.add(Box.createVerticalGlue());

        // 组装中央面板
        centerPanel.add(imagePanel, BorderLayout.CENTER);
        centerPanel.add(rightControlPanel, BorderLayout.EAST);

        // 导入图片按钮
        JButton importButton = new JButton("导入图片");
        importButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setMultiSelectionEnabled(true);
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("图片文件", "jpg", "jpeg", "png"));
            int returnValue = fileChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                for (File file : fileChooser.getSelectedFiles()) {
                    fileListModel.addElement(file.getName());
                    importedFiles.add(file);
                    System.out.println("导入图片: " + file.getAbsolutePath());
                }
            }
        });
        buttonPanel.add(importButton);

        // 导入文件夹按钮
        JButton folderButton = new JButton("导入文件夹");
        folderButton.addActionListener(e -> {
            List<File> files = FolderImporter.setupFolderImport(frame);
            if (files != null) {
                for (File file : files) {
                    fileListModel.addElement(file.getName());
                    importedFiles.add(file);
                }
            }
        });
        buttonPanel.add(folderButton);

        // 添加水印按钮
        JButton watermarkButton = new JButton("添加水印");
        watermarkButton.addActionListener(e -> {
            if (importedFiles.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请先导入图片！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (fileList.getSelectedIndex() < 0) {
                JOptionPane.showMessageDialog(frame, "请先选择一张图片！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Object[] watermarkOptions = {"文本水印", "图片水印"};
            String watermarkType = (String) JOptionPane.showInputDialog(frame, "请选择水印类型：", "添加水印", JOptionPane.PLAIN_MESSAGE, null, watermarkOptions, watermarkOptions[0]);
            if (watermarkType != null) {
                if (watermarkType.equals("文本水印")) {
                    String inputText = JOptionPane.showInputDialog(frame, "请输入水印文本：", "添加水印", JOptionPane.PLAIN_MESSAGE);
                    if (inputText != null && !inputText.trim().isEmpty()) {
                        watermarkText = inputText.trim();
                        isImageWatermark = false;
                        // 设置默认位置（右下角）
                        setWatermarkPosition(8);
                        int transparency = transparencySlider.getValue();
                        System.out.println("水印文本: " + watermarkText + ", 透明度: " + transparency + "%");
                        // 添加水印并更新预览
                        addWatermark();
                        // 启用调色盘按钮
                        colorButton.setEnabled(true);
                    }
                } else if (watermarkType.equals("图片水印")) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG 图片", "png"));
                    int returnValue = fileChooser.showOpenDialog(frame);
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        File watermarkImageFile = fileChooser.getSelectedFile();
                        try {
                            originalWatermarkImage = javax.imageio.ImageIO.read(watermarkImageFile);
                            if (originalWatermarkImage == null) {
                                JOptionPane.showMessageDialog(frame, "无法加载图片水印！", "错误", JOptionPane.ERROR_MESSAGE);
                                return;
                            }

                            // 设置默认大小为原图的1/4
                            File selectedFile = importedFiles.get(fileList.getSelectedIndex());
                            ImageIcon originalIcon = new ImageIcon(selectedFile.getAbsolutePath());
                            Image originalImage = originalIcon.getImage();

                            int originalWidth = originalImage.getWidth(null);
                            int originalHeight = originalImage.getHeight(null);

                            // 计算默认大小
                            watermarkWidth = Math.min(originalWidth / 4, originalWatermarkImage.getWidth());
                            watermarkHeight = (int) ((double) watermarkWidth / originalWatermarkImage.getWidth() * originalWatermarkImage.getHeight());

                            // 创建缩放后的水印图片
                            currentWatermarkImage = resizeImage(originalWatermarkImage, watermarkWidth, watermarkHeight);

                            isImageWatermark = true;
                            watermarkText = ""; // 清空文本水印

                            // 设置默认位置（右下角）
                            setWatermarkPosition(8);

                            // 添加水印并更新预览
                            addImageWatermark();
                            updatePreview();

                            // 启用调色盘按钮
                            colorButton.setEnabled(true);

                            JOptionPane.showMessageDialog(frame,
                                    "图片水印已添加！\n" +
                                            "- 拖拽水印内部可移动位置\n" +
                                            "- 拖拽右下角可调整大小\n" +
                                            "- 使用调色盘可改变色调",
                                    "提示", JOptionPane.INFORMATION_MESSAGE);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(frame, "加载图片水印失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });
        buttonPanel.add(watermarkButton);

        // 导出图片按钮
        JButton exportButton = new JButton("导出图片");
        exportButton.addActionListener(e -> {
            if (importedFiles.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请先导入图片！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (fileList.getSelectedIndex() < 0) {
                JOptionPane.showMessageDialog(frame, "请先选择一张图片！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (watermarkedImage == null) {
                JOptionPane.showMessageDialog(frame, "请先添加水印！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            File currentFile = importedFiles.get(fileList.getSelectedIndex());
            int namingRule = ImageExporter.showExportDialog(frame, currentFile.getName());
            if (namingRule != JOptionPane.CLOSED_OPTION && namingRule >= 0) {
                // 弹出格式选择对话框
                Object[] formatOptions = {"PNG", "JPEG"};
                String format = (String) JOptionPane.showInputDialog(frame, "选择保存格式：", "导出设置",
                        JOptionPane.PLAIN_MESSAGE, null, formatOptions, formatOptions[0]);
                if (format != null) {
                    // 只导出当前选中的图片
                    List<File> currentFileList = new ArrayList<>();
                    currentFileList.add(currentFile);
                    ImageExporter.setupExport(frame, currentFileList, namingRule, format, watermarkedImage);
                }
            }
        });
        buttonPanel.add(exportButton);

        // 清除水印按钮
        JButton clearWatermarkButton = new JButton("清除水印");
        clearWatermarkButton.addActionListener(e -> {
            watermarkedImage = null;
            watermarkText = "";
            isImageWatermark = false;
            originalWatermarkImage = null;
            currentWatermarkImage = null;
            updatePreview();
            colorButton.setEnabled(false); // 禁用调色盘按钮
            // 重置颜色预览到默认状态
            watermarkColor = Color.BLACK;
            colorPreview.setBackground(Color.BLACK);
            colorPreview.setForeground(Color.WHITE);
            System.out.println("水印已清除");
        });
        buttonPanel.add(clearWatermarkButton);

        frame.add(listScrollPane, BorderLayout.WEST);
        frame.add(centerPanel, BorderLayout.CENTER);

        // 设置拖拽导入
        ImageImporter.setupDragAndDrop(imagePanel, frame, fileListModel, importedFiles);

        // 点击文件列表显示图片
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                updatePreview();
                // 如果有水印，启用调色盘按钮
                if ((watermarkText != null && !watermarkText.isEmpty()) ||
                        (isImageWatermark && currentWatermarkImage != null)) {
                    colorButton.setEnabled(true);
                }
            }
        });

        // 添加鼠标拖拽水印功能
        setupWatermarkDragging();

        // 添加组件监听器，当窗口大小变化时重新调整图片显示
        imagePreview.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                // 延迟更新预览，避免频繁重绘
                SwingUtilities.invokeLater(() -> {
                    if (fileList.getSelectedIndex() >= 0) {
                        updatePreview();
                    }
                });
            }
        });

        // 启动时尝试加载上次配置
        ConfigManager.loadLastConfig(new ConfigManager.TemplateLoadCallback() {
            @Override
            public void loadTemplate(String text, Color color, int transparency, int x, int y) {
                MainWindow.loadTemplate(text, color, transparency, x, y);
            }

            @Override
            public void loadImageWatermark(String imagePath, int transparency, int x, int y) {
                MainWindow.loadImageWatermark(imagePath, transparency, x, y);
            }
        });

        frame.setVisible(true);
    }

    // 加载模板的方法
    private static void loadTemplate(String text, Color color, int transparency, int x, int y) {
        watermarkText = text;
        watermarkColor = color;
        isImageWatermark = false;
        transparencySlider.setValue(transparency);
        watermarkX = x;
        watermarkY = y;

        // 更新颜色预览
        Component[] rightComponents = ((JPanel) imagePreview.getParent().getParent()).getComponents();
        JPanel rightPanel = (JPanel) rightComponents[1]; // 右侧控制面板
        Component[] rightPanelComponents = rightPanel.getComponents();

        // 找到颜色面板
        for (Component comp : rightPanelComponents) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                Border border = panel.getBorder();
                if (border instanceof TitledBorder) {
                    TitledBorder titledBorder = (TitledBorder) border;
                    if ("水印颜色".equals(titledBorder.getTitle())) {
                        // 找到颜色预览标签
                        JPanel colorControlPanel = (JPanel) panel.getComponent(0);
                        JLabel colorPreview = (JLabel) colorControlPanel.getComponent(1);
                        colorPreview.setBackground(watermarkColor);

                        // 根据颜色亮度调整文字颜色以确保可读性
                        int brightness = (int)((watermarkColor.getRed() * 0.299) +
                                (watermarkColor.getGreen() * 0.587) +
                                (watermarkColor.getBlue() * 0.114));
                        colorPreview.setForeground(brightness > 128 ? Color.BLACK : Color.WHITE);

                        // 启用调色盘按钮
                        JButton colorButton = (JButton) colorControlPanel.getComponent(0);
                        colorButton.setEnabled(true);
                        break;
                    }
                }
            }
        }

        // 如果有选中的图片，应用水印
        if (fileList.getSelectedIndex() >= 0 && !importedFiles.isEmpty()) {
            addWatermark();
        }

        System.out.println("模板加载成功: " + text);
    }

    private static void loadImageWatermark(String imagePath, int transparency, int x, int y) {
        try {
            originalWatermarkImage = javax.imageio.ImageIO.read(new File(imagePath));
            if (originalWatermarkImage == null) {
                JOptionPane.showMessageDialog(null, "无法加载图片水印！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 设置默认大小为原图的1/4
            File selectedFile = importedFiles.get(fileList.getSelectedIndex());
            ImageIcon originalIcon = new ImageIcon(selectedFile.getAbsolutePath());
            Image originalImage = originalIcon.getImage();

            int originalWidth = originalImage.getWidth(null);
            int originalHeight = originalImage.getHeight(null);

            // 计算默认大小
            watermarkWidth = Math.min(originalWidth / 4, originalWatermarkImage.getWidth());
            watermarkHeight = (int) ((double) watermarkWidth / originalWatermarkImage.getWidth() * originalWatermarkImage.getHeight());

            // 创建缩放后的水印图片
            currentWatermarkImage = resizeImage(originalWatermarkImage, watermarkWidth, watermarkHeight);

            isImageWatermark = true;
            watermarkText = ""; // 清空文本水印
            transparencySlider.setValue(transparency);
            watermarkX = x;
            watermarkY = y;

            // 更新颜色预览
            Component[] rightComponents = ((JPanel) imagePreview.getParent().getParent()).getComponents();
            JPanel rightPanel = (JPanel) rightComponents[1]; // 右侧控制面板
            Component[] rightPanelComponents = rightPanel.getComponents();

            // 找到颜色面板
            for (Component comp : rightPanelComponents) {
                if (comp instanceof JPanel) {
                    JPanel panel = (JPanel) comp;
                    Border border = panel.getBorder();
                    if (border instanceof TitledBorder) {
                        TitledBorder titledBorder = (TitledBorder) border;
                        if ("水印颜色".equals(titledBorder.getTitle())) {
                            // 启用调色盘按钮
                            JButton colorButton = (JButton) ((JPanel) panel.getComponent(0)).getComponent(0);
                            colorButton.setEnabled(true);
                            break;
                        }
                    }
                }
            }

            // 如果有选中的图片，应用水印
            if (fileList.getSelectedIndex() >= 0 && !importedFiles.isEmpty()) {
                addImageWatermark();
            }

            System.out.println("图片水印模板加载成功: " + imagePath);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "加载图片水印失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private static void updatePreview() {
        int index = fileList.getSelectedIndex();
        if (index >= 0 && index < importedFiles.size()) {
            Image image;
            if (watermarkedImage != null) {
                image = watermarkedImage;
            } else {
                File selectedFile = importedFiles.get(index);
                ImageIcon originalIcon = new ImageIcon(selectedFile.getAbsolutePath());
                image = originalIcon.getImage();
            }

            int panelWidth = imagePreview.getWidth();
            int panelHeight = imagePreview.getHeight();

            if (panelWidth <= 0 || panelHeight <= 0) {
                panelWidth = 600;
                panelHeight = 400;
            }

            int imageWidth = image.getWidth(null);
            int imageHeight = image.getHeight(null);

            double widthRatio = (double) panelWidth / imageWidth;
            double heightRatio = (double) panelHeight / imageHeight;
            double ratio = Math.min(widthRatio, heightRatio);
            ratio = Math.min(ratio, 1.0);

            int scaledWidth = (int) (imageWidth * ratio);
            int scaledHeight = (int) (imageHeight * ratio);

            Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            currentIcon = new ImageIcon(scaledImage);
            imagePreview.setIcon(currentIcon);

            imagePreview.revalidate();
            imagePreview.repaint();
        } else {
            imagePreview.setIcon(null);
            currentIcon = null;
        }
    }

    private static void addWatermark() {
        int index = fileList.getSelectedIndex();
        if (index >= 0 && index < importedFiles.size()) {
            try {
                File selectedFile = importedFiles.get(index);
                ImageIcon icon = new ImageIcon(selectedFile.getAbsolutePath());
                Image image = icon.getImage();

                if (image.getWidth(null) == -1 || image.getHeight(null) == -1) {
                    JOptionPane.showMessageDialog(null, "图片加载失败，请检查文件格式！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                int width = image.getWidth(null);
                int height = image.getHeight(null);
                watermarkedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = watermarkedImage.createGraphics();

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g2d.drawImage(image, 0, 0, null);

                Font font = new Font("微软雅黑", Font.BOLD, Math.max(width / 20, 16));
                g2d.setFont(font);

                int alpha = (int) (transparencySlider.getValue() * 2.55);

                Color watermarkColorWithAlpha = new Color(
                        watermarkColor.getRed(),
                        watermarkColor.getGreen(),
                        watermarkColor.getBlue(),
                        alpha
                );

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(watermarkText);
                int textHeight = fm.getHeight();

                int x = watermarkX;
                int y = watermarkY;

                g2d.setColor(new Color(0, 0, 0, alpha / 2));
                g2d.drawString(watermarkText, x + 2, y + 2);

                g2d.setColor(watermarkColorWithAlpha);
                g2d.drawString(watermarkText, x, y);

                g2d.dispose();

                updatePreview();

                System.out.println("水印添加成功！透明度: " + transparencySlider.getValue() + "%");

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "添加水印时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    // 添加图片水印的方法
    private static void addImageWatermark() {
        int index = fileList.getSelectedIndex();
        if (index >= 0 && index < importedFiles.size()) {
            try {
                File selectedFile = importedFiles.get(index);
                ImageIcon icon = new ImageIcon(selectedFile.getAbsolutePath());
                Image image = icon.getImage();

                if (image.getWidth(null) == -1 || image.getHeight(null) == -1) {
                    JOptionPane.showMessageDialog(null, "图片加载失败，请检查文件格式！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                int width = image.getWidth(null);
                int height = image.getHeight(null);
                watermarkedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = watermarkedImage.createGraphics();

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                // 绘制原始图片
                g2d.drawImage(image, 0, 0, null);

                // 设置透明度
                float alpha = transparencySlider.getValue() / 100.0f;
                AlphaComposite alphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
                g2d.setComposite(alphaComposite);

                // 绘制水印图片
                if (currentWatermarkImage != null) {
                    g2d.drawImage(currentWatermarkImage, watermarkX, watermarkY, watermarkWidth, watermarkHeight, null);
                }

                g2d.dispose();

                updatePreview();

                System.out.println("图片水印添加成功！位置: (" + watermarkX + ", " + watermarkY +
                        "), 大小: " + watermarkWidth + "x" + watermarkHeight +
                        ", 透明度: " + transparencySlider.getValue() + "%");

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "添加图片水印时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    // 应用颜色滤镜到水印图片
    private static void applyColorFilterToWatermark() {
        if (originalWatermarkImage == null) return;

        BufferedImage filteredImage = new BufferedImage(
                originalWatermarkImage.getWidth(),
                originalWatermarkImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        // 创建颜色查找表来应用色调（包含 Alpha 通道）
        short[] red = new short[256];
        short[] green = new short[256];
        short[] blue = new short[256];
        short[] alpha = new short[256];

        float[] hsb = Color.RGBtoHSB(
                watermarkColor.getRed(),
                watermarkColor.getGreen(),
                watermarkColor.getBlue(),
                null
        );

        for (int i = 0; i < 256; i++) {
            // 保持亮度，只改变色调和饱和度
            float brightness = i / 255.0f;
            Color newColor = Color.getHSBColor(hsb[0], hsb[1], brightness);
            red[i] = (short) newColor.getRed();
            green[i] = (short) newColor.getGreen();
            blue[i] = (short) newColor.getBlue();
            alpha[i] = (short) i; // Alpha 通道保持不变
        }

        short[][] lookupData = {red, green, blue, alpha};
        ShortLookupTable lookupTable = new ShortLookupTable(0, lookupData);
        LookupOp lookupOp = new LookupOp(lookupTable, null);

        // 应用滤镜
        lookupOp.filter(originalWatermarkImage, filteredImage);

        // 重新缩放应用了滤镜的图片
        currentWatermarkImage = resizeImage(filteredImage, watermarkWidth, watermarkHeight);

        // 更新预览
        if (fileList.getSelectedIndex() >= 0) {
            addImageWatermark();
        }
    }

    // 调整图片大小
    private static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }

    private static void setWatermarkPosition(int position) {
        int index = fileList.getSelectedIndex();
        if (index >= 0 && index < importedFiles.size()) {
            File selectedFile = importedFiles.get(index);
            ImageIcon icon = new ImageIcon(selectedFile.getAbsolutePath());
            Image image = icon.getImage();

            int width = image.getWidth(null);
            int height = image.getHeight(null);

            if (isImageWatermark && currentWatermarkImage != null) {
                // 图片水印的位置计算
                switch (position) {
                    case 0: // 左上
                        watermarkX = 20;
                        watermarkY = 20;
                        break;
                    case 1: // 上中
                        watermarkX = (width - watermarkWidth) / 2;
                        watermarkY = 20;
                        break;
                    case 2: // 右上
                        watermarkX = width - watermarkWidth - 20;
                        watermarkY = 20;
                        break;
                    case 3: // 左中
                        watermarkX = 20;
                        watermarkY = (height - watermarkHeight) / 2;
                        break;
                    case 4: // 正中
                        watermarkX = (width - watermarkWidth) / 2;
                        watermarkY = (height - watermarkHeight) / 2;
                        break;
                    case 5: // 右中
                        watermarkX = width - watermarkWidth - 20;
                        watermarkY = (height - watermarkHeight) / 2;
                        break;
                    case 6: // 左下
                        watermarkX = 20;
                        watermarkY = height - watermarkHeight - 20;
                        break;
                    case 7: // 下中
                        watermarkX = (width - watermarkWidth) / 2;
                        watermarkY = height - watermarkHeight - 20;
                        break;
                    case 8: // 右下
                        watermarkX = width - watermarkWidth - 20;
                        watermarkY = height - watermarkHeight - 20;
                        break;
                }
            } else if (watermarkText != null && !watermarkText.isEmpty()) {
                // 文本水印的位置计算（保持原有逻辑）
                BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D tempG2d = tempImg.createGraphics();
                Font font = new Font("微软雅黑", Font.BOLD, Math.max(width / 20, 16));
                tempG2d.setFont(font);
                FontMetrics fm = tempG2d.getFontMetrics();
                int textWidth = fm.stringWidth(watermarkText);
                int textHeight = fm.getHeight();
                tempG2d.dispose();

                switch (position) {
                    case 0:
                        watermarkX = 20;
                        watermarkY = textHeight + 10;
                        break;
                    case 1:
                        watermarkX = (width - textWidth) / 2;
                        watermarkY = textHeight + 10;
                        break;
                    case 2:
                        watermarkX = width - textWidth - 20;
                        watermarkY = textHeight + 10;
                        break;
                    case 3:
                        watermarkX = 20;
                        watermarkY = height / 2;
                        break;
                    case 4:
                        watermarkX = (width - textWidth) / 2;
                        watermarkY = height / 2;
                        break;
                    case 5:
                        watermarkX = width - textWidth - 20;
                        watermarkY = height / 2;
                        break;
                    case 6:
                        watermarkX = 20;
                        watermarkY = height - 20;
                        break;
                    case 7:
                        watermarkX = (width - textWidth) / 2;
                        watermarkY = height - 20;
                        break;
                    case 8:
                        watermarkX = width - textWidth - 20;
                        watermarkY = height - 20;
                        break;
                }
            }
        }
    }

    private static void setupWatermarkDragging() {
        imagePreview.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (watermarkedImage != null) {
                    Point clickPoint = getImageCoordinate(e.getPoint());
                    if (clickPoint != null) {
                        if (isImageWatermark && currentWatermarkImage != null) {
                            // 图片水印的拖拽和调整大小
                            if (isOnResizeHandle(clickPoint)) {
                                isResizing = true;
                                imagePreview.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                            } else if (isClickOnWatermark(clickPoint)) {
                                isDragging = true;
                                dragOffset.x = clickPoint.x - watermarkX;
                                dragOffset.y = clickPoint.y - watermarkY;
                                imagePreview.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                            }
                        } else if (watermarkText != null && !watermarkText.isEmpty() && isClickOnWatermark(clickPoint)) {
                            // 文本水印的拖拽
                            isDragging = true;
                            dragOffset.x = clickPoint.x - watermarkX;
                            dragOffset.y = clickPoint.y - watermarkY;
                            imagePreview.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        }
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDragging || isResizing) {
                    isDragging = false;
                    isResizing = false;
                    imagePreview.setCursor(Cursor.getDefaultCursor());
                    System.out.println("操作结束，最终水印位置: (" + watermarkX + ", " + watermarkY +
                            "), 大小: " + watermarkWidth + "x" + watermarkHeight);
                }
            }
        });

        imagePreview.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if ((isDragging || isResizing) && watermarkedImage != null) {
                    Point dragPoint = getImageCoordinate(e.getPoint());
                    if (dragPoint != null) {
                        if (isImageWatermark && currentWatermarkImage != null) {
                            if (isResizing) {
                                // 调整大小
                                int newWidth = Math.max(10, dragPoint.x - watermarkX);
                                int newHeight = Math.max(10, dragPoint.y - watermarkY);

                                // 保持宽高比
                                double aspectRatio = (double) originalWatermarkImage.getWidth() / originalWatermarkImage.getHeight();
                                if (newWidth / aspectRatio > newHeight) {
                                    newWidth = (int) (newHeight * aspectRatio);
                                } else {
                                    newHeight = (int) (newWidth / aspectRatio);
                                }

                                watermarkWidth = newWidth;
                                watermarkHeight = newHeight;

                                // 重新缩放水印图片
                                currentWatermarkImage = resizeImage(
                                        originalWatermarkImage,
                                        watermarkWidth,
                                        watermarkHeight
                                );

                                // 如果应用了颜色滤镜，重新应用
                                if (!watermarkColor.equals(Color.BLACK)) {
                                    applyColorFilterToWatermark();
                                }
                            } else if (isDragging) {
                                // 移动位置
                                int newX = dragPoint.x - dragOffset.x;
                                int newY = dragPoint.y - dragOffset.y;

                                // 限制在图片范围内
                                File selectedFile = importedFiles.get(fileList.getSelectedIndex());
                                ImageIcon icon = new ImageIcon(selectedFile.getAbsolutePath());
                                Image image = icon.getImage();

                                watermarkX = Math.max(0, Math.min(newX, image.getWidth(null) - watermarkWidth));
                                watermarkY = Math.max(0, Math.min(newY, image.getHeight(null) - watermarkHeight));
                            }

                            addImageWatermark();
                        } else if (isDragging && watermarkText != null && !watermarkText.isEmpty()) {
                            // 文本水印的移动
                            int newX = dragPoint.x - dragOffset.x;
                            int newY = dragPoint.y - dragOffset.y;

                            BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D tempG2d = tempImg.createGraphics();
                            Font font = new Font("微软雅黑", Font.BOLD, Math.max(watermarkedImage.getWidth() / 20, 16));
                            tempG2d.setFont(font);
                            FontMetrics fm = tempG2d.getFontMetrics();
                            int textWidth = fm.stringWidth(watermarkText);
                            int textHeight = fm.getHeight();
                            int textAscent = fm.getAscent();
                            tempG2d.dispose();

                            watermarkX = Math.max(0, Math.min(newX, watermarkedImage.getWidth() - textWidth));
                            watermarkY = Math.max(textAscent, Math.min(newY, watermarkedImage.getHeight() - (textHeight - textAscent)));

                            addWatermark();
                        }
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (watermarkedImage != null) {
                    Point hoverPoint = getImageCoordinate(e.getPoint());
                    if (hoverPoint != null) {
                        if (isImageWatermark && currentWatermarkImage != null) {
                            if (isOnResizeHandle(hoverPoint)) {
                                imagePreview.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                            } else if (isClickOnWatermark(hoverPoint)) {
                                imagePreview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            } else {
                                imagePreview.setCursor(Cursor.getDefaultCursor());
                            }
                        } else if (watermarkText != null && !watermarkText.isEmpty() && isClickOnWatermark(hoverPoint)) {
                            imagePreview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        } else {
                            imagePreview.setCursor(Cursor.getDefaultCursor());
                        }
                    }
                }
            }
        });
    }

    // 检查是否在调整大小的手柄上
    private static boolean isOnResizeHandle(Point point) {
        if (!isImageWatermark || currentWatermarkImage == null) return false;

        int handleX = watermarkX + watermarkWidth;
        int handleY = watermarkY + watermarkHeight;

        return point.x >= handleX - RESIZE_HANDLE_SIZE && point.x <= handleX + RESIZE_HANDLE_SIZE &&
                point.y >= handleY - RESIZE_HANDLE_SIZE && point.y <= handleY + RESIZE_HANDLE_SIZE;
    }

    private static Point getImageCoordinate(Point screenPoint) {
        if (watermarkedImage == null) return null;

        int labelWidth = imagePreview.getWidth();
        int labelHeight = imagePreview.getHeight();
        int originalWidth = watermarkedImage.getWidth();
        int originalHeight = watermarkedImage.getHeight();

        double widthRatio = (double) labelWidth / originalWidth;
        double heightRatio = (double) labelHeight / originalHeight;
        double ratio = Math.min(widthRatio, heightRatio);

        int scaledWidth = (int) (originalWidth * ratio);
        int scaledHeight = (int) (originalHeight * ratio);

        int offsetX = (labelWidth - scaledWidth) / 2;
        int offsetY = (labelHeight - scaledHeight) / 2;

        if (screenPoint.x < offsetX || screenPoint.x > offsetX + scaledWidth ||
                screenPoint.y < offsetY || screenPoint.y > offsetY + scaledHeight) {
            return null;
        }

        int imageX = (int) Math.round((screenPoint.x - offsetX) / ratio);
        int imageY = (int) Math.round((screenPoint.y - offsetY) / ratio);

        imageX = Math.max(0, Math.min(imageX, originalWidth - 1));
        imageY = Math.max(0, Math.min(imageY, originalHeight - 1));

        return new Point(imageX, imageY);
    }

    private static boolean isClickOnWatermark(Point point) {
        if (isImageWatermark && currentWatermarkImage != null) {
            // 图片水印的点击检测
            return point.x >= watermarkX && point.x <= watermarkX + watermarkWidth &&
                    point.y >= watermarkY && point.y <= watermarkY + watermarkHeight;
        } else if (watermarkText != null && !watermarkText.isEmpty() && watermarkedImage != null) {
            // 文本水印的点击检测（保持原有逻辑）
            BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tempG2d = tempImg.createGraphics();
            Font font = new Font("微软雅黑", Font.BOLD, Math.max(watermarkedImage.getWidth() / 20, 16));
            tempG2d.setFont(font);
            FontMetrics fm = tempG2d.getFontMetrics();
            int textWidth = fm.stringWidth(watermarkText);
            int textHeight = fm.getHeight();
            int textAscent = fm.getAscent();
            tempG2d.dispose();

            int watermarkLeft = watermarkX;
            int watermarkRight = watermarkX + textWidth;
            int watermarkTop = watermarkY - textAscent;
            int watermarkBottom = watermarkY + (textHeight - textAscent);

            int tolerance = 10;
            return point.x >= watermarkLeft - tolerance && point.x <= watermarkRight + tolerance &&
                    point.y >= watermarkTop - tolerance && point.y <= watermarkBottom + tolerance;
        }
        return false;
    }
}