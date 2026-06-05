package com.l.gpom.client;

import com.l.gpom.config.GpomEarlyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EarlySplashWindow {
    private static final Logger LOGGER = LogManager.getLogger("GPOM Early Splash");
    private static final int WIDTH = 854;
    private static final int HEIGHT = 480;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean UPDATE_QUEUED = new AtomicBoolean(false);
    private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

    private static volatile String status = "Coremod bootstrap";
    private static volatile int phaseDone;
    private static volatile int phaseTotal;
    private static volatile String bootStage = "Coremod bootstrap";
    private static volatile int bootDone;
    private static volatile int bootTotal = 4;
    private static volatile long startedAt;
    private static volatile String packName = "Minecraft";
    private static volatile UiState ui;

    private EarlySplashWindow() {
    }

    public static void startIfEnabled() {
        if (!GpomEarlyConfig.earlySplashEnabled()) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.info("[EarlySplash] Disabled because the JVM is headless");
            return;
        }
        if (System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null) {
            LOGGER.info("[EarlySplash] Disabled because no graphical display environment is present");
            return;
        }
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        startedAt = System.nanoTime();
        packName = GpomEarlyConfig.earlySplashPackName();
        EventQueue.invokeLater(EarlySplashWindow::createWindow);
    }

    public static void setStatus(String newStatus) {
        if (newStatus == null || newStatus.trim().isEmpty()) {
            return;
        }
        status = newStatus.trim();
        queueUpdate();
    }

    public static void setPhaseProgress(String newPhase, int done, int total) {
        if (newPhase != null && !newPhase.trim().isEmpty()) {
            status = newPhase.trim();
        }
        phaseDone = Math.max(0, done);
        phaseTotal = Math.max(0, total);
        queueUpdate();
    }

    public static void setBootProgress(String newStage, int done, int total) {
        if (newStage != null && !newStage.trim().isEmpty()) {
            bootStage = newStage.trim();
            status = bootStage;
        }
        bootDone = Math.max(0, done);
        bootTotal = Math.max(0, total);
        queueUpdate();
    }

    public static void close(String reason) {
        if (!STARTED.get() || !CLOSED.compareAndSet(false, true)) {
            return;
        }
        if (reason != null && !reason.trim().isEmpty()) {
            status = reason.trim();
        }
        EventQueue.invokeLater(() -> {
            UiState snapshot = ui;
            if (snapshot != null) {
                snapshot.timer.stop();
                snapshot.frame.setVisible(false);
                snapshot.frame.dispose();
                ui = null;
            }
            LOGGER.info("[EarlySplash] Closed AWT progress window: {}", status);
        });
    }

    private static void createWindow() {
        if (CLOSED.get()) {
            return;
        }
        try {
            JFrame frame = new JFrame("Minecraft Forge");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setResizable(false);
            frame.setPreferredSize(new Dimension(WIDTH, HEIGHT));

            SplashPanel panel = new SplashPanel();
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            ActionListener repaint = event -> applySnapshot();
            Timer timer = new Timer(100, repaint);
            timer.setCoalesce(true);
            timer.start();

            ui = new UiState(frame, panel, timer);
            applySnapshot();
            LOGGER.info("[EarlySplash] Shown AWT progress window");
        } catch (Throwable throwable) {
            LOGGER.warn("[EarlySplash] Disabled after AWT startup failure", throwable);
            CLOSED.set(true);
        }
    }

    private static void queueUpdate() {
        if (!STARTED.get() || CLOSED.get()) {
            return;
        }
        if (!UPDATE_QUEUED.compareAndSet(false, true)) {
            return;
        }
        EventQueue.invokeLater(() -> {
            UPDATE_QUEUED.set(false);
            applySnapshot();
        });
    }

    private static void applySnapshot() {
        UiState snapshot = ui;
        if (snapshot == null || CLOSED.get()) {
            return;
        }

        long elapsedSeconds = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000_000L);
        int bootTotalSnapshot = bootTotal;
        int bootDoneSnapshot = clamp(bootDone, bootTotalSnapshot);
        int phaseTotalSnapshot = phaseTotal;
        int phaseDoneSnapshot = clamp(phaseDone, phaseTotalSnapshot);

        snapshot.frame.setTitle(title(elapsedSeconds, phaseDoneSnapshot, phaseTotalSnapshot, bootDoneSnapshot, bootTotalSnapshot));
        snapshot.panel.update(elapsedSeconds, bootDoneSnapshot, bootTotalSnapshot);
        snapshot.panel.repaint();
    }

    private static String title(long elapsedSeconds, int phaseDoneSnapshot, int phaseTotalSnapshot, int bootDoneSnapshot, int bootTotalSnapshot) {
        String phaseProgress = phaseTotalSnapshot > 0
                ? String.format(Locale.ROOT, " | %d/%d", phaseDoneSnapshot, phaseTotalSnapshot)
                : "";
        return String.format(Locale.ROOT, "Minecraft Forge | GPOM boot %d/%d | %s%s | %ds",
                bootDoneSnapshot,
                Math.max(0, bootTotalSnapshot),
                status,
                phaseProgress,
                elapsedSeconds);
    }

    private static int clamp(int value, int total) {
        if (total <= 0) {
            return Math.max(0, value);
        }
        return Math.min(Math.max(0, value), total);
    }

    private static float progress(int done, int total) {
        if (total <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, Math.max(0.0F, done / (float) total));
    }

    private static String progressText(String label, int done, int total) {
        if (total <= 0) {
            return label + ": waiting";
        }
        return String.format(Locale.ROOT, "%s: %d/%d", label, done, total);
    }

    private static String compact(String value, int max) {
        if (value == null || value.trim().isEmpty()) {
            return "Waiting";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }

    private static final class UiState {
        final JFrame frame;
        final SplashPanel panel;
        final Timer timer;

        UiState(JFrame frame, SplashPanel panel, Timer timer) {
            this.frame = frame;
            this.panel = panel;
            this.timer = timer;
        }
    }

    private static final class SplashPanel extends JPanel {
        private static final Color BACKGROUND_TOP = new Color(22, 22, 22);
        private static final Color BACKGROUND_BOTTOM = new Color(11, 11, 11);
        private static final Color TEXT = new Color(224, 224, 224);
        private static final Color MUTED = new Color(164, 164, 164);
        private static final Color BAR_BACKGROUND = new Color(56, 56, 56);
        private static final Color BAR_BORDER = new Color(118, 118, 118);
        private static final Color BAR_PRIMARY = new Color(184, 184, 184);
        private long elapsedSeconds;
        private int bootDoneSnapshot;
        private int bootTotalSnapshot;

        private SplashPanel() {
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setBackground(BACKGROUND_BOTTOM);
            setDoubleBuffered(true);
        }

        private void update(long elapsedSeconds, int bootDoneSnapshot, int bootTotalSnapshot) {
            this.elapsedSeconds = elapsedSeconds;
            this.bootDoneSnapshot = bootDoneSnapshot;
            this.bootTotalSnapshot = bootTotalSnapshot;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintBackground(g);
                paintTitle(g);
                paintProgress(g);
                paintFooter(g);
            } finally {
                g.dispose();
            }
        }

        private void paintBackground(Graphics2D g) {
            int height = getHeight();
            for (int y = 0; y < height; y++) {
                float t = y / (float) Math.max(1, height - 1);
                int red = (int) (BACKGROUND_TOP.getRed() * (1.0F - t) + BACKGROUND_BOTTOM.getRed() * t);
                int green = (int) (BACKGROUND_TOP.getGreen() * (1.0F - t) + BACKGROUND_BOTTOM.getGreen() * t);
                int blue = (int) (BACKGROUND_TOP.getBlue() * (1.0F - t) + BACKGROUND_BOTTOM.getBlue() * t);
                g.setColor(new Color(red, green, blue));
                g.drawLine(0, y, getWidth(), y);
            }
            g.setColor(new Color(255, 255, 255, 18));
            g.setStroke(new BasicStroke(1.0F));
            g.drawRect(18, 18, getWidth() - 37, getHeight() - 37);
        }

        private void paintTitle(Graphics2D g) {
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            drawCentered(g, "Minecraft Forge", 116);

            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            drawCentered(g, "General Purpose Optimization Mod", 145);
        }

        private void paintProgress(Graphics2D g) {
            int barWidth = 560;
            int barHeight = 18;
            int x = (getWidth() - barWidth) / 2;
            int y = 225;

            drawBar(g, x, y, barWidth, barHeight, progress(bootDoneSnapshot, bootTotalSnapshot), BAR_PRIMARY);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            drawCentered(g, compact(bootStage, 78), y - 10);
            drawCentered(g, progressText("Bootstrap", bootDoneSnapshot, bootTotalSnapshot), y + 43);
        }

        private void paintFooter(Graphics2D g) {
            g.setColor(MUTED);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            String footer = String.format(Locale.ROOT, "Starting %s  |  %ds elapsed", compact(packName, 48), elapsedSeconds);
            drawCentered(g, footer, getHeight() - 48);
        }

        private void drawBar(Graphics2D g, int x, int y, int width, int height, float value, Color fill) {
            g.setColor(BAR_BACKGROUND);
            g.fillRect(x, y, width, height);
            g.setColor(fill);
            g.fillRect(x + 1, y + 1, Math.max(0, (int) ((width - 2) * value)), height - 2);
            g.setColor(BAR_BORDER);
            g.drawRect(x, y, width, height);
        }

        private void drawCentered(Graphics2D g, String text, int baselineY) {
            FontMetrics metrics = g.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(text)) / 2;
            g.drawString(text, x, baselineY);
        }
    }
}
