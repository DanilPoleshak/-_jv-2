import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MazeWindow extends JPanel {

    private static final Color SIDEBAR_COLOR = new Color(48, 48, 48);
    private static final Color BACKGROUND_COLOR = new Color(65, 65, 65);
    private static final Color TITLE_COLOR = new Color(120, 205, 255);

    private Maze maze;
    private int cellSize = 25;

    private final QLearningAgent qAgent = new QLearningAgent();
    private final MazeGenerator generator = new MazeGenerator();

    private final JTextField widthField = new JTextField("25");
    private final JTextField heightField = new JTextField("17");
    private final JTextField episodesField = new JTextField("3000");

    private final JComboBox<String> complexityBox =
            new JComboBox<>(new String[]{
                    "0%",
                    "50%",
                    "100%"
            });

    private final JComboBox<Integer> waterBox =
            new JComboBox<>(new Integer[]{
                    3, 4, 5, 6, 7
            });

    private final JComboBox<String> trainingSpeedBox =
            new JComboBox<>(new String[]{
                    "Медленно",
                    "Обычно",
                    "Быстро",
                    "Максимально"
            });

    private final JButton generateButton =
            new JButton("Создать");

    private final JButton trainButton =
            new JButton("Обучить");

    private final JButton trainingStepsButton =
            new JButton("Шагов за эпизод: 5000");

    private final JButton applySpeedButton =
            new JButton("Применить скорость");

    private final JButton startButton =
            new JButton("Старт");

    private final JButton stepButton =
            new JButton("Шаг");

    private final JButton stopButton =
            new JButton("Пауза");

    private final JButton qTableButton =
            new JButton("Q-таблица");

    private final JLabel statusLabel =
            new JLabel("Подготовка...");

    private final JPanel mazeRenderPanel;
    private final JScrollPane mazeScrollPane;
    private final JScrollPane settingsScrollPane;

    private Timer trainingTimer;
    private Timer movementTimer;

    private int selectedTrainingSteps = 5000;

    private int trainingStepsPerTick = 100;
    private int trainingDelayMs = 20;

    private String appliedTrainingSpeed = "Обычно";

    private boolean isTrainingMode;
    private boolean movementFinished;
    private boolean showFinalPath;

    private Point initialMousePosition;

    private List<Point> calculatedPath = new ArrayList<>();
    private List<Point> finalPath = new ArrayList<>();

    private int currentStepIndex;
    private int movementSteps;
    private int collectedWater;
    private int shockCount;

    private double totalReward;

    public MazeWindow() {
        setLayout(new BorderLayout(8, 0));
        setBackground(BACKGROUND_COLOR);

        complexityBox.setSelectedIndex(1);
        waterBox.setSelectedItem(3);
        trainingSpeedBox.setSelectedItem("Обычно");

        episodesField.setToolTipText(
                "Общее количество эпизодов обучения на текущей карте"
        );

        trainingStepsButton.setToolTipText(
                "Максимальное количество действий мыши "
                        + "в одном эпизоде обучения"
        );

        // ---------- Левая панель ----------

        JPanel leftPanel = createSidebar();

        settingsScrollPane = new JScrollPane(leftPanel);
        settingsScrollPane.setBorder(null);

        settingsScrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );

        settingsScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );

        settingsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        settingsScrollPane.getViewport().setBackground(SIDEBAR_COLOR);

        Rectangle screenBounds = getScreenBounds();

        settingsScrollPane.setPreferredSize(
                new Dimension(
                        280,
                        Math.min(
                                620,
                                Math.max(180, screenBounds.height - 140)
                        )
                )
        );

        add(settingsScrollPane, BorderLayout.WEST);

        // ---------- Область лабиринта ----------

        mazeRenderPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);

                if (maze == null) {
                    return;
                }

                Graphics2D g = (Graphics2D) graphics.create();

                try {
                    drawMaze(g);

                    if (showFinalPath) {
                        drawFinalPath(g);
                    }

                    drawCheese(g);

                    if (isTrainingMode) {
                        Point virtualMouse = qAgent.getVirtualMouse();

                        if (virtualMouse != null) {
                            drawMouse(
                                    g,
                                    virtualMouse.x * cellSize,
                                    virtualMouse.y * cellSize,
                                    true
                            );
                        }

                    } else {
                        drawMouse(
                                g,
                                maze.getMouseX() * cellSize,
                                maze.getMouseY() * cellSize,
                                false
                        );
                    }

                } finally {
                    g.dispose();
                }
            }
        };

        mazeRenderPanel.setBackground(BACKGROUND_COLOR);

        mazeScrollPane = new JScrollPane(mazeRenderPanel);
        mazeScrollPane.setBorder(null);

        mazeScrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        mazeScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        mazeScrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        add(mazeScrollPane, BorderLayout.CENTER);

        // ---------- Строка состояния ----------

        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBackground(new Color(38, 38, 38));
        statusLabel.setOpaque(true);

        statusLabel.setBorder(
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        );

        JScrollPane statusScrollPane = new JScrollPane(statusLabel);

        statusScrollPane.setBorder(null);

        statusScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_NEVER
        );

        statusScrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );

        statusScrollPane.setPreferredSize(new Dimension(0, 48));

        add(statusScrollPane, BorderLayout.SOUTH);

        // ---------- Обработчики ----------

        generateButton.addActionListener(e -> handleGeneration());

        trainButton.addActionListener(e -> trainAgain());

        trainingStepsButton.addActionListener(
                e -> chooseTrainingSteps()
        );

        applySpeedButton.addActionListener(
                e -> applyTrainingSpeed()
        );

        startButton.addActionListener(e -> startAutoSolve());

        stepButton.addActionListener(e -> executeSingleStep());

        stopButton.addActionListener(e -> stopCurrentOperation());

        qTableButton.addActionListener(e -> showQTableDialog());

        updateControls();

        // Только создаём лабиринт.
        // Обучение запускается отдельной кнопкой.
        handleGeneration();
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel();

        panel.setLayout(
                new BoxLayout(panel, BoxLayout.Y_AXIS)
        );

        panel.setBackground(SIDEBAR_COLOR);

        panel.setBorder(
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        );

        addSidebarTitle(panel, "ЛАБИРИНТ");

        addSidebarField(panel, "Ширина:", widthField);
        addSidebarField(panel, "Высота:", heightField);

        addSidebarField(
                panel,
                "Дополнительные петли:",
                complexityBox
        );

        addSidebarField(
                panel,
                "Количество ресурсов:",
                waterBox
        );

        addSidebarButton(panel, generateButton);

        panel.add(Box.createVerticalStrut(14));

        addSidebarTitle(panel, "ОБУЧЕНИЕ");

        addSidebarField(
                panel,
                "Количество эпизодов:",
                episodesField
        );

        addSidebarButton(panel, trainingStepsButton);

        addSidebarField(
                panel,
                "Скорость обучения:",
                trainingSpeedBox
        );

        addSidebarButton(panel, applySpeedButton);
        addSidebarButton(panel, trainButton);

        panel.add(Box.createVerticalStrut(14));

        addSidebarTitle(panel, "ДВИЖЕНИЕ МЫШИ");

        addSidebarButton(panel, startButton);
        addSidebarButton(panel, stepButton);
        addSidebarButton(panel, stopButton);
        addSidebarButton(panel, qTableButton);

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private void addSidebarTitle(JPanel panel, String text) {
        JLabel title = new JLabel(text);

        title.setForeground(TITLE_COLOR);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(10));
    }

    private void addSidebarField(
            JPanel panel,
            String text,
            JComponent component
    ) {
        JLabel label = new JLabel(text);

        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        component.setAlignmentX(Component.LEFT_ALIGNMENT);

        component.setPreferredSize(new Dimension(230, 30));
        component.setMinimumSize(new Dimension(100, 30));
        component.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 30)
        );

        panel.add(label);
        panel.add(Box.createVerticalStrut(4));
        panel.add(component);
        panel.add(Box.createVerticalStrut(9));
    }

    private void addSidebarButton(
            JPanel panel,
            JButton button
    ) {
        button.setFont(new Font("SansSerif", Font.BOLD, 11));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);

        button.setPreferredSize(new Dimension(230, 32));
        button.setMinimumSize(new Dimension(100, 32));
        button.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 32)
        );

        panel.add(button);
        panel.add(Box.createVerticalStrut(7));
    }

    private Rectangle getScreenBounds() {
        return GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
    }

    private int readInteger(
            JTextField field,
            String name,
            int min,
            int max
    ) {
        int value;

        try {
            value = Integer.parseInt(field.getText().trim());

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + ": введите целое число."
            );
        }

        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + ": допустимые значения от "
                            + min + " до " + max + "."
            );
        }

        return value;
    }

    private void handleGeneration() {
        if (isTrainingMode || isMovementRunning()) {
            return;
        }

        try {
            int width = readInteger(
                    widthField,
                    "Ширина",
                    5,
                    50
            );

            int height = readInteger(
                    heightField,
                    "Высота",
                    5,
                    50
            );

            double braidFactor;

            switch (complexityBox.getSelectedIndex()) {
                case 1:
                    braidFactor = 0.5;
                    break;

                case 2:
                    braidFactor = 1.0;
                    break;

                default:
                    braidFactor = 0.0;
            }

            int resourceCount =
                    (Integer) waterBox.getSelectedItem();

            Maze newMaze = generator.generateMazeWithWalls(
                    width,
                    height,
                    braidFactor,
                    resourceCount
            );

            stopTimers();

            qAgent.prepareTraining(newMaze);
            qAgent.setMaxEpisodeSteps(selectedTrainingSteps);

            maze = newMaze;

            initialMousePosition = new Point(
                    maze.getMouseX(),
                    maze.getMouseY()
            );

            resetMovementData();
            resizeMazePanel();

            statusLabel.setText(
                    "Карта "
                            + maze.getWidth() + " × "
                            + maze.getHeight()
                            + " создана. Настройте обучение "
                            + "и нажмите «Обучить»."
            );

            updateControls();
            mazeRenderPanel.repaint();

        } catch (IllegalArgumentException | IllegalStateException exception) {
            updateControls();

            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Ошибка создания лабиринта",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void resizeMazePanel() {
        int maxSize = Math.max(
                maze.getWidth(),
                maze.getHeight()
        );

        if (maxSize <= 15) {
            cellSize = 35;

        } else if (maxSize <= 25) {
            cellSize = 25;

        } else {
            cellSize = 18;
        }

        int pixelWidth = maze.getWidth() * cellSize;
        int pixelHeight = maze.getHeight() * cellSize;

        mazeRenderPanel.setPreferredSize(
                new Dimension(pixelWidth, pixelHeight)
        );

        Rectangle screenBounds = getScreenBounds();

        int availableWidth = Math.max(
                150,
                screenBounds.width - 340
        );

        int availableHeight = Math.max(
                180,
                screenBounds.height - 140
        );

        mazeScrollPane.setPreferredSize(
                new Dimension(
                        Math.min(pixelWidth + 20, availableWidth),
                        Math.min(pixelHeight + 20, availableHeight)
                )
        );

        revalidate();
        mazeRenderPanel.repaint();

        Window window = SwingUtilities.getWindowAncestor(this);

        if (window != null) {
            window.pack();

            int windowWidth = Math.min(
                    window.getWidth(),
                    screenBounds.width
            );

            int windowHeight = Math.min(
                    window.getHeight(),
                    screenBounds.height
            );

            window.setSize(windowWidth, windowHeight);
            window.setLocationRelativeTo(null);
        }
    }

    private void resetMovementData() {
        calculatedPath = new ArrayList<>();
        finalPath = new ArrayList<>();

        currentStepIndex = 0;
        movementSteps = 0;
        collectedWater = 0;
        shockCount = 0;

        totalReward = 0.0;

        movementFinished = false;
        showFinalPath = false;
    }

    private void chooseTrainingSteps() {
        if (isTrainingMode || isMovementRunning()) {
            return;
        }

        while (true) {
            String input = JOptionPane.showInputDialog(
                    this,
                    "Максимальное количество шагов за один эпизод.\n"
                            + "Введите целое число от 1 до 1000000.\n\n"
                            + "Если цель не достигнута за это время,\n"
                            + "мышь вернётся на старт.",
                    Integer.toString(selectedTrainingSteps)
            );

            if (input == null) {
                return;
            }

            try {
                int value = Integer.parseInt(input.trim());

                if (value < 1 || value > 1000000) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Введите число от 1 до 1000000.",
                            "Недопустимое количество шагов",
                            JOptionPane.ERROR_MESSAGE
                    );
                    continue;
                }

                selectedTrainingSteps = value;

                trainingStepsButton.setText(
                        "Шагов за эпизод: "
                                + selectedTrainingSteps
                );

                statusLabel.setText(
                        "Выбран лимит: "
                                + selectedTrainingSteps
                                + " шагов за эпизод. "
                                + "Он применяется при запуске обучения."
                );

                revalidate();
                repaint();

                return;

            } catch (NumberFormatException exception) {
                JOptionPane.showMessageDialog(
                        this,
                        "Введите целое число, например 5000.",
                        "Ошибка ввода",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void applyTrainingSpeed() {
        switch (trainingSpeedBox.getSelectedIndex()) {
            case 0:
                trainingStepsPerTick = 1;
                trainingDelayMs = 100;
                break;

            case 1:
                trainingStepsPerTick = 100;
                trainingDelayMs = 20;
                break;

            case 2:
                trainingStepsPerTick = 500;
                trainingDelayMs = 10;
                break;

            case 3:
                trainingStepsPerTick = 2000;
                trainingDelayMs = 5;
                break;

            default:
                trainingStepsPerTick = 100;
                trainingDelayMs = 20;
        }

        appliedTrainingSpeed =
                (String) trainingSpeedBox.getSelectedItem();

        if (trainingTimer != null) {
            trainingTimer.setDelay(trainingDelayMs);
            trainingTimer.setInitialDelay(trainingDelayMs);
        }

        if (!isTrainingMode) {
            statusLabel.setText(
                    "Скорость обучения: "
                            + appliedTrainingSpeed
            );
        }
    }

    private void trainAgain() {
        if (maze == null
                || isTrainingMode
                || isMovementRunning()) {
            return;
        }

        if (movementFinished || movementSteps > 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Мышь уже начала движение и изменила ресурсы.\n"
                            + "Для нового обучения создайте новый лабиринт.",
                    "Обучение недоступно",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        try {
            int targetEpisodes = readInteger(
                    episodesField,
                    "Количество эпизодов",
                    1,
                    1000000
            );

            int completedEpisodes = qAgent.getCurrentEpisode();

            if (targetEpisodes <= completedEpisodes) {
                JOptionPane.showMessageDialog(
                        this,
                        "Уже выполнено эпизодов: "
                                + completedEpisodes + ".\n"
                                + "Введите общее число эпизодов больше "
                                + completedEpisodes + ".",
                        "Продолжение обучения",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            qAgent.setMaxEpisodeSteps(selectedTrainingSteps);

            calculatedPath.clear();
            currentStepIndex = 0;

            finalPath.clear();
            showFinalPath = false;

            startTraining(targetEpisodes);

        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Ошибка параметров обучения",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void startTraining(final int targetEpisodes) {
        if (maze == null) {
            return;
        }

        if (trainingTimer != null) {
            trainingTimer.stop();
        }

        applyTrainingSpeed();

        isTrainingMode = true;
        updateControls();

        trainingTimer = new Timer(trainingDelayMs, e -> {
            try {
                boolean continuing = true;

                long deadline =
                        System.nanoTime() + 8_000_000L;

                int steps = 0;

                while (continuing
                        && steps < trainingStepsPerTick
                        && System.nanoTime() < deadline) {

                    continuing = qAgent.makeTrainingStep(
                            maze,
                            targetEpisodes
                    );

                    steps++;
                }

                statusLabel.setText(
                        "Эпизоды: "
                                + qAgent.getCurrentEpisode()
                                + " / " + targetEpisodes
                                + " | Шаги: "
                                + qAgent.getEpisodeSteps()
                                + " / "
                                + qAgent.getMaxEpisodeSteps()
                                + " | Успешных: "
                                + qAgent.getSuccessfulEpisodes()
                                + " | Скорость: "
                                + appliedTrainingSpeed
                );

                mazeRenderPanel.repaint();

                if (!continuing) {
                    ((Timer) e.getSource()).stop();
                    finishTraining();
                }

            } catch (RuntimeException exception) {
                ((Timer) e.getSource()).stop();

                isTrainingMode = false;
                updateControls();

                statusLabel.setText(
                        "Обучение остановлено из-за ошибки."
                );

                JOptionPane.showMessageDialog(
                        MazeWindow.this,
                        exception.getMessage(),
                        "Ошибка обучения",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        trainingTimer.start();
    }

    private void finishTraining() {
        isTrainingMode = false;

        // Реальное движение — только по обученной политике.
        calculatedPath = qAgent.buildLearnedPath(maze);

        currentStepIndex =
                calculatedPath.isEmpty() ? 0 : 1;

        updateControls();
        mazeRenderPanel.repaint();

        if (calculatedPath.isEmpty()) {
            statusLabel.setText(
                    "Маршрут пока не выучен. Успешных эпизодов: "
                            + qAgent.getSuccessfulEpisodes()
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Обучение завершено, но успешный маршрут\n"
                            + "пока не выучен.\n\n"
                            + "Успешных эпизодов: "
                            + qAgent.getSuccessfulEpisodes()
                            + ".\n\n"
                            + "Увеличьте общее число эпизодов\n"
                            + "или лимит шагов и продолжите обучение.\n"
                            + "Карта и Q-таблица сохраняются.",
                    "Результат обучения",
                    JOptionPane.WARNING_MESSAGE
            );

        } else {
            statusLabel.setText(
                    "Обучение завершено. Маршрут: "
                            + (calculatedPath.size() - 1)
                            + " шагов. Нажмите «Старт» или «Шаг»."
            );
        }
    }

    private boolean isMovementRunning() {
        return movementTimer != null
                && movementTimer.isRunning();
    }

    private void startAutoSolve() {
        if (isTrainingMode
                || isMovementRunning()
                || movementFinished
                || calculatedPath.isEmpty()
                || currentStepIndex >= calculatedPath.size()) {
            return;
        }

        if (movementTimer != null) {
            movementTimer.stop();
        }

        movementTimer = new Timer(
                120,
                e -> executeSingleStep()
        );

        movementTimer.start();

        statusLabel.setText(
                "Мышь движется по маршруту обученного агента."
        );

        updateControls();
    }

    private void executeSingleStep() {
        if (isTrainingMode || movementFinished) {
            return;
        }

        if (calculatedPath.isEmpty()
                || currentStepIndex >= calculatedPath.size()) {

            if (movementTimer != null) {
                movementTimer.stop();
            }

            updateControls();
            return;
        }

        Point next = calculatedPath.get(currentStepIndex);

        int distance =
                Math.abs(next.x - maze.getMouseX())
                        + Math.abs(next.y - maze.getMouseY());

        Cell cell = maze.getCell(next.x, next.y);

        if (distance != 1
                || cell == null
                || cell.isWall()) {

            if (movementTimer != null) {
                movementTimer.stop();
            }

            calculatedPath.clear();
            updateControls();

            JOptionPane.showMessageDialog(
                    this,
                    "Маршрут содержит недопустимый переход.",
                    "Ошибка движения",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        maze.setMouseX(next.x);
        maze.setMouseY(next.y);

        movementSteps++;
        totalReward -= 0.2;

        if (cell.isWater()) {
            cell.setWater(false);

            collectedWater++;
            totalReward += 20.0;
        }

        if (cell.isShock()) {
            shockCount++;
            totalReward -= 50.0;
        }

        currentStepIndex++;

        if (currentStepIndex >= calculatedPath.size()) {
            finishMovement();

        } else {
            statusLabel.setText(
                    "Шаги: " + movementSteps
                            + " | Вода: " + collectedWater
                            + " | Электротравмы: " + shockCount
                            + " | Сумма наград: "
                            + String.format("%.1f", totalReward)
            );
        }

        updateControls();
        mazeRenderPanel.repaint();
    }

    private void finishMovement() {
        if (movementTimer != null) {
            movementTimer.stop();
        }

        boolean atCheese =
                maze.getMouseX() == maze.getExitX()
                        && maze.getMouseY() == maze.getExitY();

        boolean waterRemaining = false;

        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                if (maze.getCell(x, y).isWater()) {
                    waterRemaining = true;
                }
            }
        }

        if (!atCheese || waterRemaining) {
            calculatedPath.clear();
            updateControls();

            JOptionPane.showMessageDialog(
                    this,
                    "Маршрут завершился, но цель не выполнена:\n"
                            + "нужно собрать всю воду и достичь сыра.",
                    "Цель не выполнена",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        totalReward += 100.0;
        movementFinished = true;

        Point cheesePosition = new Point(
                maze.getExitX(),
                maze.getExitY()
        );

        // BFS применяется только для итоговой подсветки.
        finalPath = findShortestSafePath(
                initialMousePosition,
                cheesePosition
        );

        showFinalPath = !finalPath.isEmpty();

        String pathInformation;

        if (showFinalPath) {
            pathInformation =
                    "Кратчайший путь без молний: "
                            + (finalPath.size() - 1)
                            + " шагов.\n"
                            + "Он показан зелёным от старта до сыра.\n"
                            + "Сбор воды в этом пути не учитывается.";

        } else {
            pathInformation =
                    "Путь от старта до сыра без молний отсутствует.\n"
                            + "Безопасный кратчайший путь не подсвечен.";
        }

        statusLabel.setText(
                "Цель достигнута!"
                        + " | Шаги: " + movementSteps
                        + " | Вода: " + collectedWater
                        + " | Электротравмы: " + shockCount
                        + " | Итог: "
                        + String.format("%.1f", totalReward)
                        + (showFinalPath
                        ? " | Безопасный путь: "
                        + (finalPath.size() - 1)
                        : " | Безопасного пути нет")
        );

        updateControls();
        mazeRenderPanel.repaint();

        JOptionPane.showMessageDialog(
                this,
                "Мышь собрала всю воду и получила сыр!\n\n"
                        + "Фактический маршрут: "
                        + movementSteps + " шагов.\n"
                        + "Собрано воды: "
                        + collectedWater + ".\n"
                        + "Электротравм: "
                        + shockCount + ".\n"
                        + "Сумма наград: "
                        + String.format("%.1f", totalReward)
                        + ".\n\n"
                        + pathInformation,
                "Результат",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private List<Point> findShortestSafePath(
            Point start,
            Point finish
    ) {
        List<Point> path = new ArrayList<>();

        if (maze == null
                || start == null
                || finish == null) {
            return path;
        }

        Cell startCell =
                maze.getCell(start.x, start.y);

        Cell finishCell =
                maze.getCell(finish.x, finish.y);

        if (startCell == null
                || finishCell == null
                || startCell.isWall()
                || finishCell.isWall()
                || startCell.isShock()
                || finishCell.isShock()) {
            return path;
        }

        boolean[][] visited =
                new boolean[maze.getHeight()][maze.getWidth()];

        Deque<Point> queue = new ArrayDeque<>();
        Map<Point, Point> parents = new HashMap<>();

        Point startPoint = new Point(start);

        queue.addLast(startPoint);
        visited[startPoint.y][startPoint.x] = true;

        int[][] directions = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();

            if (current.equals(finish)) {
                Point point = current;

                while (point != null) {
                    path.add(point);
                    point = parents.get(point);
                }

                Collections.reverse(path);
                return path;
            }

            for (int[] direction : directions) {
                int nx = current.x + direction[0];
                int ny = current.y + direction[1];

                Cell cell = maze.getCell(nx, ny);

                if (cell == null
                        || cell.isWall()
                        || cell.isShock()
                        || visited[ny][nx]) {
                    continue;
                }

                Point next = new Point(nx, ny);

                visited[ny][nx] = true;
                parents.put(next, current);
                queue.addLast(next);
            }
        }

        return path;
    }

    private void stopCurrentOperation() {
        if (isTrainingMode) {
            if (trainingTimer != null) {
                trainingTimer.stop();
            }

            isTrainingMode = false;
            calculatedPath.clear();

            statusLabel.setText(
                    "Обучение прервано. Q-таблица сохранена. "
                            + "Можно продолжить обучение."
            );

        } else {
            if (movementTimer != null) {
                movementTimer.stop();
            }

            statusLabel.setText(
                    "Движение приостановлено. "
                            + "Нажмите «Старт» или «Шаг»."
            );
        }

        updateControls();
        mazeRenderPanel.repaint();
    }

    private void stopTimers() {
        if (trainingTimer != null) {
            trainingTimer.stop();
        }

        if (movementTimer != null) {
            movementTimer.stop();
        }

        isTrainingMode = false;
    }

    private void updateControls() {
        boolean moving = isMovementRunning();
        boolean busy = isTrainingMode || moving;

        boolean canTrain =
                !busy
                        && maze != null
                        && !movementFinished
                        && movementSteps == 0;

        boolean hasPath =
                maze != null
                        && !movementFinished
                        && currentStepIndex < calculatedPath.size();

        widthField.setEnabled(!busy);
        heightField.setEnabled(!busy);
        complexityBox.setEnabled(!busy);
        waterBox.setEnabled(!busy);

        episodesField.setEnabled(canTrain);
        trainingStepsButton.setEnabled(canTrain);

        generateButton.setEnabled(!busy);
        trainButton.setEnabled(canTrain);

        trainButton.setText(
                qAgent.getCurrentEpisode() == 0
                        ? "Обучить"
                        : "Продолжить обучение"
        );

        startButton.setEnabled(!busy && hasPath);
        stepButton.setEnabled(!busy && hasPath);

        stopButton.setEnabled(busy);

        stopButton.setText(
                isTrainingMode
                        ? "Прервать обучение"
                        : "Пауза"
        );

        qTableButton.setEnabled(
                maze != null && !isTrainingMode
        );

        // Скорость можно менять во время обучения.
        trainingSpeedBox.setEnabled(!moving);
        applySpeedButton.setEnabled(!moving);
    }

    private void showQTableDialog() {
        if (maze == null) {
            return;
        }

        JTextArea textArea =
                new JTextArea(qAgent.getQTableText());

        textArea.setFont(
                new Font("Monospaced", Font.PLAIN, 12)
        );

        textArea.setEditable(false);
        textArea.setCaretPosition(0);

        JScrollPane scrollPane =
                new JScrollPane(textArea);

        Rectangle screenBounds = getScreenBounds();

        scrollPane.setPreferredSize(
                new Dimension(
                        Math.min(760, Math.max(200, screenBounds.width - 100)),
                        Math.min(450, Math.max(150, screenBounds.height - 150))
                )
        );

        JOptionPane.showMessageDialog(
                this,
                scrollPane,
                "Q-таблица: положение и выпитая вода",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void drawMaze(Graphics2D g) {
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF
        );

        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                Cell cell = maze.getCell(x, y);

                int pixelX = x * cellSize;
                int pixelY = y * cellSize;

                g.setColor(
                        cell.isWall()
                                ? Color.BLACK
                                : Color.WHITE
                );

                g.fillRect(
                        pixelX,
                        pixelY,
                        cellSize,
                        cellSize
                );

                if (cell.isWall()) {
                    continue;
                }

                if (cellSize >= 12) {
                    g.setColor(new Color(215, 215, 215));

                    g.drawRect(
                            pixelX,
                            pixelY,
                            cellSize - 1,
                            cellSize - 1
                    );
                }

                if (cell.isWater()) {
                    drawWater(g, pixelX, pixelY);
                }

                if (cell.isShock()) {
                    drawShock(g, pixelX, pixelY);
                }
            }
        }
    }

    private void drawWater(Graphics2D g, int x, int y) {
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int pad = cellSize / 4;
        int size = cellSize - 2 * pad;

        g.setColor(new Color(30, 144, 255));

        g.fillOval(
                x + pad,
                y + pad + size / 3,
                size,
                Math.max(3, size * 2 / 3)
        );

        int[] xs = {
                x + cellSize / 2,
                x + pad,
                x + cellSize - pad
        };

        int[] ys = {
                y + pad,
                y + pad + size / 2,
                y + pad + size / 2
        };

        g.fillPolygon(xs, ys, 3);

        g.setColor(new Color(255, 255, 255, 190));

        g.fillOval(
                x + cellSize / 2 - 2,
                y + cellSize / 2,
                Math.max(2, cellSize / 10),
                Math.max(3, cellSize / 6)
        );
    }

    private void drawShock(Graphics2D g, int x, int y) {
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setColor(new Color(255, 225, 100));

        g.fillOval(
                x + 2,
                y + 2,
                cellSize - 4,
                cellSize - 4
        );

        int[] xs = {
                x + cellSize * 3 / 5,
                x + cellSize / 3,
                x + cellSize / 2,
                x + cellSize * 2 / 5,
                x + cellSize * 3 / 4,
                x + cellSize * 3 / 5
        };

        int[] ys = {
                y + cellSize / 6,
                y + cellSize / 2,
                y + cellSize / 2,
                y + cellSize * 5 / 6,
                y + cellSize * 2 / 5,
                y + cellSize * 2 / 5
        };

        g.setColor(new Color(200, 55, 20));
        g.fillPolygon(xs, ys, xs.length);
    }

    private void drawCheese(Graphics2D g) {
        int x = maze.getExitX() * cellSize;
        int y = maze.getExitY() * cellSize;

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int pad = Math.max(2, cellSize / 6);

        int[] xs = {
                x + pad,
                x + cellSize - pad,
                x + cellSize / 2
        };

        int[] ys = {
                y + cellSize - pad,
                y + cellSize - pad,
                y + pad
        };

        g.setColor(new Color(255, 201, 14));
        g.fillPolygon(xs, ys, 3);

        g.setColor(new Color(220, 145, 10));

        int hole = Math.max(2, cellSize / 7);

        g.fillOval(
                x + cellSize / 3,
                y + cellSize * 3 / 5,
                hole,
                hole
        );

        g.fillOval(
                x + cellSize / 2,
                y + cellSize / 2,
                hole,
                hole
        );
    }

    private void drawMouse(
            Graphics2D g,
            int x,
            int y,
            boolean training
    ) {
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int pad = Math.max(2, cellSize / 6);
        int size = cellSize - 2 * pad;

        Color bodyColor = training
                ? new Color(46, 204, 113, 210)
                : new Color(140, 140, 145);

        g.setColor(bodyColor);

        g.fillOval(
                x + pad,
                y + pad + size / 5,
                size,
                size * 3 / 4
        );

        int ear = Math.max(3, size / 3);

        g.fillOval(
                x + pad + size / 3,
                y + pad,
                ear,
                ear
        );

        g.fillOval(
                x + pad + size * 2 / 3,
                y + pad,
                ear,
                ear
        );

        g.setColor(new Color(255, 180, 195));

        int innerEar = Math.max(2, ear / 2);

        g.fillOval(
                x + pad + size / 3 + ear / 4,
                y + pad + ear / 4,
                innerEar,
                innerEar
        );

        g.fillOval(
                x + pad + size * 2 / 3 + ear / 4,
                y + pad + ear / 4,
                innerEar,
                innerEar
        );

        g.setColor(Color.BLACK);

        int eye = Math.max(2, cellSize / 12);

        g.fillOval(
                x + pad + size / 4,
                y + pad + size / 3,
                eye,
                eye
        );

        g.setColor(new Color(255, 105, 180));

        g.fillOval(
                x + pad - 1,
                y + pad + size / 2,
                Math.max(3, cellSize / 9),
                Math.max(3, cellSize / 9)
        );
    }

    private void drawFinalPath(Graphics2D g) {
        if (finalPath.size() < 2) {
            return;
        }

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setColor(new Color(46, 204, 113, 180));

        g.setStroke(
                new BasicStroke(
                        Math.max(2f, cellSize / 5f),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                )
        );

        for (int i = 0; i < finalPath.size() - 1; i++) {
            Point first = finalPath.get(i);
            Point second = finalPath.get(i + 1);

            g.drawLine(
                    first.x * cellSize + cellSize / 2,
                    first.y * cellSize + cellSize / 2,
                    second.x * cellSize + cellSize / 2,
                    second.y * cellSize + cellSize / 2
            );
        }

        g.setStroke(new BasicStroke(1));
    }
}