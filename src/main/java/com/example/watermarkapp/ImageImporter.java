package main.java.com.example.watermarkapp;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ImageImporter {
    public static void setupDragAndDrop(JPanel panel, JFrame frame, DefaultListModel<String> fileListModel, List<File> importedFiles) {
        panel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }

                Transferable transferable = support.getTransferable();
                try {
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        fileListModel.addElement(file.getName());
                        importedFiles.add(file);
                        System.out.println("拖拽导入图片: " + file.getAbsolutePath());
                    }
                    return true;
                } catch (UnsupportedFlavorException | IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        });

        new DropTarget(panel, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                event.acceptDrop(DnDConstants.ACTION_COPY);
                Transferable transferable = event.getTransferable();
                try {
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        fileListModel.addElement(file.getName());
                        importedFiles.add(file);
                        System.out.println("拖拽导入图片: " + file.getAbsolutePath());
                    }
                    event.dropComplete(true);
                } catch (UnsupportedFlavorException | IOException e) {
                    e.printStackTrace();
                    event.dropComplete(false);
                }
            }
        });
    }
}