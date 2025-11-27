package ceu.biolab;

import javax.swing.*;
import java.awt.*;

public class SignalPanel extends JPanel {
    private java.util.List<Integer> samples = new java.util.ArrayList<>();
    private static final int BASE_HEIGHT = 300;
    private static final int X_STEP = 2;  // píxeles entre muestras

    public SignalPanel() {
        setPreferredSize(new Dimension(600, BASE_HEIGHT));
        setBackground(Color.WHITE);
    }

    public void addSample(int value) {
        samples.add(value);

        // 1) Ajustar ancho según nº de muestras
        int width = Math.max(600, samples.size() * X_STEP);
        setPreferredSize(new Dimension(width, BASE_HEIGHT));
        revalidate();
        repaint();

        // 2) Autoscroll al final (últimos samples)
        SwingUtilities.invokeLater(() -> {
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(
                    JViewport.class, this
            );
            if (viewport != null) {
                Rectangle viewRect = viewport.getViewRect();
                int newX = getWidth() - viewRect.width;
                if (newX < 0) newX = 0;
                viewport.setViewPosition(new Point(newX, viewRect.y));
            }
        });
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (samples.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // === GRID: líneas de puntitos formando cuadrados ===
        int spacing = 20; // tamaño del cuadrito (puedes cambiar a 10, 25, etc.)

        g2.setColor(new Color(180, 180, 180, 120)); // gris suave
        float[] dash = {1f, spacing - 1f};          // 1px dibujado, resto hueco
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(
                1f,
                BasicStroke.CAP_ROUND,   // extremos redondos → parecen puntos
                BasicStroke.JOIN_BEVEL,
                0f,
                dash,
                0f
        ));

        // líneas verticales de puntitos
        for (int x = 0; x < w; x += spacing) {
            g2.drawLine(x, 0, x, h);
        }
        // líneas horizontales de puntitos
        for (int y = 0; y < h; y += spacing) {
            g2.drawLine(0, y, w, y);
        }

        // línea central (si la quieres más marcada)
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(150, 150, 150, 150));
        int midY = h / 2;
        g2.drawLine(0, midY, w, midY);

        // === A partir de aquí, dibujas la señal como antes ===
        int min = samples.stream().min(Integer::compareTo).orElse(0);
        int max = samples.stream().max(Integer::compareTo).orElse(1);
        int range = Math.max(1, max - min);

        g2.setColor(new Color(255, 100, 100, 229));
        g2.setStroke(new BasicStroke(1.5f));

        int prevX = 0;
        int prevY = mapSampleToY(samples.get(0), min, range, h);
        for (int i = 1; i < samples.size(); i++) {
            int x = i * X_STEP;
            int y = mapSampleToY(samples.get(i), min, range, h);
            g2.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
    }


    private int mapSampleToY(int value, int min, int range, int height) {
        // Normaliza y pone 0 arriba, 1 abajo
        double norm = (value - min) / (double) range;
        return (int) ((1.0 - norm) * (height - 10)) + 5;
    }
}

