package ceu.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.bluetooth.RemoteDevice;
import javax.swing.border.EmptyBorder;

public class BitalinoApp extends JFrame {

    private BITalino bitalino;
    private JComboBox<Integer> samplingCombo;
    private JComboBox<String> typeCombo;
    private JButton connectBtn, startBtn, stopBtn, closeBtn;
    private JTextArea outputArea;
    private JScrollPane scrollPane;
    private JTextField macField;
    private JButton saveBtn;
    private BufferedWriter writer;
    private boolean firstSample = true;
    private File currentAcquisitionFile;
    private SignalPanel signalPanel;


    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread acquisitionThread;

    public BitalinoApp() {
        super("BITalino Bluetooth GUI");

        // === Ventana moderna ===
        setUndecorated(true);                        // sin barra de título ni X
        setExtendedState(JFrame.MAXIMIZED_BOTH);     // pantalla completa
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // solo se cierra con tu botón "Close"

        bitalino = new BITalino();

        // === Barra superior (estilo toolbar) ===
        // === Barra superior EN UNA SOLA LÍNEA ===
        JPanel topPanel = new JPanel();
        topPanel.setBackground(new Color(255, 180, 180, 168));
        topPanel.setBorder(new EmptyBorder(20, 30, 20, 30));
        topPanel.setPreferredSize(new Dimension(1920, 120));

// Layout horizontal estilo "flexbox"
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));

// === Fuentes grandes ===
        Font labelFont = new Font("SansSerif", Font.BOLD, 18);
        Font fieldFont = new Font("SansSerif", Font.PLAIN, 18);
        Font buttonFont = new Font("SansSerif", Font.BOLD, 18);

// ---------- MAC LABEL ----------
        JLabel macLabel = new JLabel("MAC Address:");
        macLabel.setFont(labelFont);
        macLabel.setForeground(Color.WHITE);
        topPanel.add(macLabel);
        topPanel.add(Box.createRigidArea(new Dimension(15, 0)));

// ---------- CAMPO MAC (mitad de la pantalla) ----------
        macField = new JTextField();
        macField.setFont(fieldFont);
        macField.setMaximumSize(new Dimension(900, 40)); // mitad del monitor aprox
        macField.setPreferredSize(new Dimension(900, 40));
        topPanel.add(macField);

        topPanel.add(Box.createRigidArea(new Dimension(40, 0)));


// ---------- TYPE ----------
        JLabel typeLabel = new JLabel("Type of recording");
        typeLabel.setFont(labelFont);
        typeLabel.setForeground(Color.WHITE);
        topPanel.add(typeLabel);

        topPanel.add(Box.createRigidArea(new Dimension(15, 0)));

        typeCombo = new JComboBox<>(new String[]{"EMG", "ECG"});
        typeCombo.setFont(fieldFont);
        typeCombo.setMaximumSize(new Dimension(120, 40));
        topPanel.add(typeCombo);

        topPanel.add(Box.createRigidArea(new Dimension(40, 0)));


// ---------- SAMPLING ----------
        JLabel srLabel = new JLabel("Sampling Rate:");
        srLabel.setFont(labelFont);
        srLabel.setForeground(Color.WHITE);
        topPanel.add(srLabel);

        topPanel.add(Box.createRigidArea(new Dimension(15, 0)));

        samplingCombo = new JComboBox<>(new Integer[]{10, 100, 1000});
        samplingCombo.setFont(fieldFont);
        samplingCombo.setMaximumSize(new Dimension(120, 40));
        topPanel.add(samplingCombo);

        topPanel.add(Box.createRigidArea(new Dimension(40, 0)));


// === BOTONES GRANDES ===
        connectBtn = createToolbarButton("Connect");
        startBtn   = createToolbarButton("Start");
        stopBtn    = createToolbarButton("Stop");
        saveBtn    = createToolbarButton("Save");
        closeBtn   = createToolbarButton("Close");

        connectBtn.setFont(buttonFont);
        startBtn.setFont(buttonFont);
        stopBtn.setFont(buttonFont);
        saveBtn.setFont(buttonFont);
        closeBtn.setFont(buttonFont);

        Dimension bigButtonSize = new Dimension(130, 45);
        connectBtn.setPreferredSize(bigButtonSize);
        startBtn.setPreferredSize(bigButtonSize);
        stopBtn.setPreferredSize(bigButtonSize);
        saveBtn.setPreferredSize(bigButtonSize);
        closeBtn.setPreferredSize(bigButtonSize);

        connectBtn.setEnabled(true);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        saveBtn.setEnabled(false);

// Añadir botones
        topPanel.add(connectBtn);
        topPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        topPanel.add(startBtn);
        topPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        topPanel.add(stopBtn);
        topPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        topPanel.add(saveBtn);
        topPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        topPanel.add(closeBtn);



        // === Zona inferior: gráfica + logs ===
        outputArea = new JTextArea(8, 50);
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        scrollPane = new JScrollPane(outputArea);

        signalPanel = new SignalPanel();

        // Scroll horizontal para la señal
        JScrollPane signalScroll = new JScrollPane(
                signalPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        signalScroll.getViewport().setBackground(Color.BLACK);

        // Split vertical: arriba gráfica (con scroll), abajo logs
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                signalScroll,
                scrollPane
        );
        splitPane.setResizeWeight(0.7);

        // === Layout principal ===
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);  // <-- solo el splitPane, nada más

        pack();
        setLocationRelativeTo(null); // en fullscreen no importa mucho, pero lo dejamos

        // === Listeners ===
        connectBtn.addActionListener(this::connectAction);
        startBtn.addActionListener(this::startAction);
        stopBtn.addActionListener(this::stopAction);
        closeBtn.addActionListener(e -> {
            stopAcquisition();
            closeDevice();
            System.exit(0);
        });
        saveBtn.addActionListener(e -> saveFileAction());
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();

        setSize(screenSize);
        setLocation(0, 0);
    }

    private JButton createToolbarButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setBackground(new Color(255, 255, 255));
        btn.setForeground(Color.BLACK);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));

        return btn;
    }



    private void connectAction(ActionEvent e) {
        String mac = macField.getText().trim();
        if (mac.isEmpty()) {
            outputArea.append("Please enter the MAC address.\n");
            return;
        }

        int rate = (Integer) samplingCombo.getSelectedItem();
        outputArea.append("Connecting to " + mac + " at " + rate + " Hz...\n");

        new Thread(() -> {
            try {
                bitalino.open(mac, rate); // <-- directly use MAC
                SwingUtilities.invokeLater(() -> {
                    outputArea.append("Connected!\n");
                    connectBtn.setEnabled(false);
                    startBtn.setEnabled(true);
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("Connection failed: " + ex.getMessage() + "\n"));
            }
        }).start();
    }

    private void startAction(ActionEvent e) {
        int [] channels = null;
        String type = typeCombo.getSelectedItem().toString();
        if (type.equals("EMG")) {
            channels = new int[]{0};   // a1
        } else if (type.equals("ECG")) {
            channels = new int[]{1};   // a2
        } else {
            outputArea.append("Please select a type of recording.\n");
            return; // evita continuar con channels = null
        }


        try {
            currentAcquisitionFile = File.createTempFile("bitalino_recording_", ".txt");
            writer = new BufferedWriter(new FileWriter(currentAcquisitionFile, false));

            writer.write(String.valueOf(samplingCombo.getSelectedItem()));
            writer.newLine();
            bitalino.start(channels);
            outputArea.append("Acquisition started on channel A1");
            outputArea.append("\n");
            running.set(true);
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);

            acquisitionThread = new Thread(this::readLoop);
            acquisitionThread.start();
        } catch (Throwable ex) {
            outputArea.append("Error starting acquisition: " + ex.getMessage() + "\n");
        }
    }

    private void stopAction(ActionEvent e) {
        stopAcquisition();
        if (currentAcquisitionFile != null && currentAcquisitionFile.exists()) {
            saveBtn.setEnabled(true);
        }
    }

    private void stopAcquisition() {
        running.set(false);
        if (acquisitionThread != null) {
            try {
                acquisitionThread.join();
            } catch (InterruptedException ignored) {
            }
        }
        try {
            bitalino.stop();
            //outputArea.append("Acquisition stopped.\n");
        } catch (Exception ex) {
            outputArea.append("Error stopping acquisition: " + ex.getMessage() + "\n");
        }
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);

    }

    private void closeDevice() {
        try {
            bitalino.close();
            outputArea.append("Device closed.\n");
        } catch (Exception ex) {
            outputArea.append("Error closing device: " + ex.getMessage() + "\n");
        }
    }

    private void saveFileAction() {
        if (currentAcquisitionFile == null) {
            JOptionPane.showMessageDialog(this, "No recording available to save.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save recording");
        chooser.setSelectedFile(new File("bitalino_recording.txt"));

        int option = chooser.showSaveDialog(this);

        if (option == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try {
                java.nio.file.Files.copy(
                        currentAcquisitionFile.toPath(),
                        dest.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

                outputArea.append("File saved to: " + dest.getAbsolutePath() + "\n");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage());
            }
        }
    }


    private void readLoop() {
        final int blockSize = 10;
        long startTimeMs = System.currentTimeMillis();

        try {
            // Segunda línea: empezamos una línea de datos
            firstSample = true;

            while (running.get()) {
                Frame[] frames = bitalino.read(blockSize);

                for (Frame f : frames) {
                    int a2 = f.analog[0];

                    // Escribir en buffer → valores separados por comas
                    if (!firstSample) {
                        writer.write(",");
                    }
                    writer.write(Integer.toString(a2));
                    firstSample = false;

                    double ts = (System.currentTimeMillis() - startTimeMs) / 1000.0;
                    /*String text = String.format("t=%.3f s | value = %d%n", ts, a2);
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append(text);
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                    });*/
                    String text = "t=" + ts + " | value = " + a2 + "\n";
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append(text);
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                        if (signalPanel != null) {
                            signalPanel.addSample(a2);
                        }
                    });

                }
            }

            // Cuando termina la adquisición → cerrar línea
            writer.newLine();
            writer.flush();

            SwingUtilities.invokeLater(() ->
                    outputArea.append("Acquisition stopped.\n"));

        } catch (Throwable ex) {
            SwingUtilities.invokeLater(() ->
                    outputArea.append("Error during acquisition: " + ex.getMessage() + "\n"));
        } finally {
            try {
                if (writer != null) writer.close();
            } catch (Exception ignored) {}
        }
    }
//98:D3:91:FD:69:49
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            // O si tienes FlatLaf añadido:
            // FlatLightLaf.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> {
                BitalinoApp app = new BitalinoApp();
                app.setVisible(true);
            });
        });



    }
}