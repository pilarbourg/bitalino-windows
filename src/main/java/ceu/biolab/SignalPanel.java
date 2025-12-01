package ceu.biolab;

import javax.swing.*;
import java.awt.*;

public class SignalPanel extends JPanel {
    private java.util.List<Integer> samples = new java.util.ArrayList<>();
    private static final int BASE_HEIGHT = 300;
    private static final int X_STEP = 1;

    public SignalPanel() {
        setPreferredSize(new Dimension(600, BASE_HEIGHT));
        setBackground(Color.WHITE);
    }

    public void clear() {
        samples.clear();
        repaint();
    }

    public void addSample(int value) {
        samples.add(value);

        int width = Math.max(600, samples.size() * X_STEP);
        setPreferredSize(new Dimension(width, BASE_HEIGHT));
        revalidate();
        repaint();

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


        int spacing = 16;

        g2.setColor(new Color(180, 180, 180, 120));
        float[] dash = {1f, spacing - 1f};
        g2.setStroke(new BasicStroke(
                1f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_BEVEL,
                0f,
                dash,
                0f
        ));

        for (int x = 0; x < w; x += spacing) {
            g2.drawLine(x, 0, x, h);
        }

        for (int y = 0; y < h; y += spacing) {
            g2.drawLine(0, y, w, y);
        }

        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(0, 0, 0, 150));
        int midY = h / 2;
        g2.drawLine(0, midY, w, midY);

        int min = samples.stream().min(Integer::compareTo).orElse(0);
        int max = samples.stream().max(Integer::compareTo).orElse(1);
        int range = Math.max(1, max - min);

        g2.setColor(new Color(198, 0, 0));
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
        double norm = (value - min) / (double) range;
        return (int) ((1.0 - norm) * (height - 10)) + 5;
    }
}

