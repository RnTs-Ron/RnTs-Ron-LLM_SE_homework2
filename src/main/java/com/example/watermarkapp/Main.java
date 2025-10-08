package main.java.com.example.watermarkapp;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainWindow.createAndShowGUI();
        });
    }
}