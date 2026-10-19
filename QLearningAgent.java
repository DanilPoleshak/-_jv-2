import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class QLearningAgent {

    // Индексы действий:
    // 0 — вправо, 1 — влево, 2 — вниз, 3 — вверх.
    private static final int[][] DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };

    private static final double ALPHA = 0.2;
    private static final double GAMMA = 0.99;

    private static final double STEP_REWARD = -0.2;
    private static final double WATER_REWARD = 20.0;
    private static final double SHOCK_REWARD = -50.0;
    private static final double CHEESE_REWARD = 100.0;

    private static final double INITIAL_EPSILON = 0.8;
    private static final double MIN_EPSILON = 0.05;

    private final Random random = new Random();

    // Состояние включает координаты и набор выпитой воды.
    private final Map<State, double[]> qTable = new HashMap<>();

    // Каждой клетке воды назначается номер бита.
    private final Map<Point, Integer> waterIndices = new HashMap<>();

    private Maze trainingMaze;
    private State currentState;
    private Point startPosition;

    private int allWaterMask;

    private int currentEpisode;
    private int episodeSteps;
    private int successfulEpisodes;

    private int maxEpisodeSteps = 5000;

    private double episodeReward;
    private double lastEpisodeReward;

    /**
     * Подготавливает агента для новой карты.
     * Очищает обучение, но не выполняет тренировочные шаги.
     */
    public void prepareTraining(Maze maze) {
        if (maze == null) {
            throw new IllegalArgumentException(
                    "Лабиринт не может быть null."
            );
        }

        Point newStart = new Point(
                maze.getMouseX(),
                maze.getMouseY()
        );

        Cell startCell = maze.getCell(newStart.x, newStart.y);
        Cell cheeseCell = maze.getCell(
                maze.getExitX(),
                maze.getExitY()
        );

        if (startCell == null || startCell.isWall()) {
            throw new IllegalArgumentException(
                    "Старт мыши находится в стене "
                            + "или за пределами лабиринта."
            );
        }

        if (cheeseCell == null || cheeseCell.isWall()) {
            throw new IllegalArgumentException(
                    "Клетка сыра находится в стене "
                            + "или за пределами лабиринта."
            );
        }

        Map<Point, Integer> newWaterIndices = new HashMap<>();
        int waterIndex = 0;

        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                Cell cell = maze.getCell(x, y);

                if (!cell.isWall() && cell.isWater()) {
                    if (waterIndex >= 20) {
                        throw new IllegalArgumentException(
                                "Эта версия агента поддерживает "
                                        + "не более 20 клеток воды."
                        );
                    }

                    newWaterIndices.put(
                            new Point(x, y),
                            waterIndex++
                    );
                }
            }
        }

        trainingMaze = maze;
        startPosition = newStart;

        qTable.clear();

        waterIndices.clear();
        waterIndices.putAll(newWaterIndices);

        allWaterMask = (1 << waterIndex) - 1;

        currentEpisode = 0;
        successfulEpisodes = 0;
        lastEpisodeReward = 0.0;

        resetEpisode();
    }

    /**
     * Устанавливает лимит действий в одном эпизоде.
     * Не очищает накопленную Q-таблицу.
     */
    public void setMaxEpisodeSteps(int maxEpisodeSteps) {
        if (maxEpisodeSteps < 1 || maxEpisodeSteps > 1000000) {
            throw new IllegalArgumentException(
                    "Количество шагов должно быть "
                            + "от 1 до 1000000."
            );
        }

        this.maxEpisodeSteps = maxEpisodeSteps;
    }

    public int getMaxEpisodeSteps() {
        return maxEpisodeSteps;
    }

    public int getEpisodeSteps() {
        return episodeSteps;
    }

    /**
     * Возвращает виртуальную мышь на старт.
     * Карта и Q-таблица сохраняются.
     */
    private void resetEpisode() {
        int initialMask = collectWater(
                startPosition.x,
                startPosition.y,
                0
        );

        currentState = new State(
                startPosition.x,
                startPosition.y,
                initialMask
        );

        episodeSteps = 0;
        episodeReward = 0.0;
    }

    /**
     * Выполняет один шаг обучения.
     *
     * Возвращает false, когда достигнуто
     * заданное общее количество эпизодов.
     */
    public boolean makeTrainingStep(
            Maze maze,
            int targetEpisodes
    ) {
        checkTrainingMaze(maze);

        if (targetEpisodes < 1) {
            throw new IllegalArgumentException(
                    "Количество эпизодов должно быть больше нуля."
            );
        }

        if (currentEpisode >= targetEpisodes) {
            return false;
        }

        // Например, пользователь уменьшил лимит после паузы.
        if (episodeSteps >= maxEpisodeSteps) {
            finishEpisode(false);
            return currentEpisode < targetEpisodes;
        }

        if (isGoal(maze, currentState)) {
            finishEpisode(true);
            return currentEpisode < targetEpisodes;
        }

        List<Integer> actions = availableActions(
                maze,
                currentState
        );

        if (actions.isEmpty()) {
            throw new IllegalStateException(
                    "Мышь находится в изолированной клетке."
            );
        }

        double progress = Math.min(
                1.0,
                (double) currentEpisode
                        / Math.max(1, targetEpisodes - 1)
        );

        double epsilon = INITIAL_EPSILON * Math.pow(
                MIN_EPSILON / INITIAL_EPSILON,
                progress
        );

        int action;

        if (random.nextDouble() < epsilon) {
            action = actions.get(
                    random.nextInt(actions.size())
            );
        } else {
            action = bestAction(
                    currentState,
                    actions,
                    true
            );
        }

        Transition result = transition(
                maze,
                currentState,
                action
        );

        double[] values = valuesFor(currentState);

        double futureValue = 0.0;

        // После достижения цели будущие действия отсутствуют.
        if (!result.terminal) {
            List<Integer> nextActions = availableActions(
                    maze,
                    result.next
            );

            if (!nextActions.isEmpty()) {
                int nextAction = bestAction(
                        result.next,
                        nextActions,
                        false
                );

                futureValue =
                        valuesFor(result.next)[nextAction];
            }
        }

        double oldQ = values[action];

        values[action] = oldQ + ALPHA * (
                result.reward
                        + GAMMA * futureValue
                        - oldQ
        );

        currentState = result.next;

        episodeSteps++;
        episodeReward += result.reward;

        if (result.terminal || episodeSteps >= maxEpisodeSteps) {
            finishEpisode(result.terminal);
        }

        return currentEpisode < targetEpisodes;
    }

    private void finishEpisode(boolean successful) {
        if (successful) {
            successfulEpisodes++;
        }

        lastEpisodeReward = episodeReward;
        currentEpisode++;

        resetEpisode();
    }

    private boolean isGoal(Maze maze, State state) {
        return state.x == maze.getExitX()
                && state.y == maze.getExitY()
                && state.waterMask == allWaterMask;
    }

    private Transition transition(
            Maze maze,
            State state,
            int action
    ) {
        int nx = state.x + DIRECTIONS[action][0];
        int ny = state.y + DIRECTIONS[action][1];

        Cell cell = maze.getCell(nx, ny);

        if (cell == null || cell.isWall()) {
            throw new IllegalArgumentException(
                    "Выбрано недопустимое направление."
            );
        }

        int nextMask = collectWater(
                nx,
                ny,
                state.waterMask
        );

        double reward = STEP_REWARD;

        // Награда за воду выдаётся только при первом посещении
        // соответствующего ресурса в текущем эпизоде.
        if (nextMask != state.waterMask) {
            reward += WATER_REWARD;
        }

        // Молния проходима, но каждое попадание даёт штраф.
        if (cell.isShock()) {
            reward += SHOCK_REWARD;
        }

        State nextState = new State(nx, ny, nextMask);
        boolean terminal = isGoal(maze, nextState);

        if (terminal) {
            reward += CHEESE_REWARD;
        }

        return new Transition(
                nextState,
                reward,
                terminal
        );
    }

    private int collectWater(int x, int y, int mask) {
        Integer index = waterIndices.get(new Point(x, y));

        if (index == null) {
            return mask;
        }

        return mask | (1 << index);
    }

    private List<Integer> availableActions(
            Maze maze,
            State state
    ) {
        List<Integer> actions = new ArrayList<>();

        for (int action = 0; action < DIRECTIONS.length; action++) {
            int nx = state.x + DIRECTIONS[action][0];
            int ny = state.y + DIRECTIONS[action][1];

            Cell cell = maze.getCell(nx, ny);

            if (cell != null && !cell.isWall()) {
                actions.add(action);
            }
        }

        return actions;
    }

    private double[] valuesFor(State state) {
        return qTable.computeIfAbsent(
                state,
                key -> new double[4]
        );
    }

    private int bestAction(
            State state,
            List<Integer> actions,
            boolean randomTie
    ) {
        double[] values = valuesFor(state);

        double bestValue = Double.NEGATIVE_INFINITY;
        List<Integer> bestActions = new ArrayList<>();

        for (int action : actions) {
            double value = values[action];

            if (value > bestValue + 1e-9) {
                bestValue = value;

                bestActions.clear();
                bestActions.add(action);

            } else if (Math.abs(value - bestValue) <= 1e-9) {
                bestActions.add(action);
            }
        }

        if (randomTie) {
            return bestActions.get(
                    random.nextInt(bestActions.size())
            );
        }

        return bestActions.get(0);
    }

    /**
     * Извлекает маршрут из обученной Q-таблицы.
     * Не использует BFS и не изменяет ресурсы карты.
     *
     * Пустой список означает, что успешная
     * детерминированная политика пока не выучена.
     */
    public List<Point> buildLearnedPath(Maze maze) {
        checkTrainingMaze(maze);

        State state = new State(
                startPosition.x,
                startPosition.y,
                collectWater(
                        startPosition.x,
                        startPosition.y,
                        0
                )
        );

        List<Point> path = new ArrayList<>();
        Set<State> visitedStates = new HashSet<>();

        path.add(new Point(state.x, state.y));

        if (isGoal(maze, state)) {
            return path;
        }

        /*
         * Лимит извлечения маршрута отделён от лимита обучения.
         * В успешной детерминированной политике состояние
         * не должно повторяться.
         */
        int pathLimit = qTable.size();

        for (int step = 0; step < pathLimit; step++) {
            if (!visitedStates.add(state)) {
                return Collections.emptyList();
            }

            if (!qTable.containsKey(state)) {
                return Collections.emptyList();
            }

            List<Integer> actions = availableActions(
                    maze,
                    state
            );

            if (actions.isEmpty()) {
                return Collections.emptyList();
            }

            int action = bestAction(
                    state,
                    actions,
                    false
            );

            Transition result = transition(
                    maze,
                    state,
                    action
            );

            state = result.next;
            path.add(new Point(state.x, state.y));

            if (result.terminal) {
                return path;
            }
        }

        return Collections.emptyList();
    }

    public String getQTableText() {
        StringBuilder text = new StringBuilder();

        text.append(
                " X   Y   Маска воды | Вправо | Влево | Вниз | Вверх\n"
        );

        text.append(
                "----------------------------------------------------\n"
        );

        if (qTable.isEmpty()) {
            text.append(
                    "Таблица пуста. Нажмите «Обучить».\n"
            );
            return text.toString();
        }

        List<State> states = new ArrayList<>(qTable.keySet());

        states.sort((a, b) -> {
            int result = Integer.compare(
                    a.waterMask,
                    b.waterMask
            );

            if (result != 0) {
                return result;
            }

            result = Integer.compare(a.y, b.y);

            if (result != 0) {
                return result;
            }

            return Integer.compare(a.x, b.x);
        });

        for (State state : states) {
            double[] values = qTable.get(state);

            text.append(String.format(
                    "%3d %3d %10s | %7.2f | %7.2f | %7.2f | %7.2f%n",
                    state.x,
                    state.y,
                    Integer.toBinaryString(state.waterMask),
                    values[0],
                    values[1],
                    values[2],
                    values[3]
            ));
        }

        return text.toString();
    }

    public Point getVirtualMouse() {
        if (currentState == null) {
            return null;
        }

        return new Point(
                currentState.x,
                currentState.y
        );
    }

    public int getCurrentEpisode() {
        return currentEpisode;
    }

    public int getSuccessfulEpisodes() {
        return successfulEpisodes;
    }

    public double getLastEpisodeReward() {
        return lastEpisodeReward;
    }

    private void checkTrainingMaze(Maze maze) {
        if (currentState == null || trainingMaze == null) {
            throw new IllegalStateException(
                    "Сначала вызовите prepareTraining(maze)."
            );
        }

        if (maze != trainingMaze) {
            throw new IllegalArgumentException(
                    "Передан другой лабиринт. "
                            + "Для новой карты вызовите prepareTraining()."
            );
        }
    }

    private static final class State {

        private final int x;
        private final int y;
        private final int waterMask;

        private State(int x, int y, int waterMask) {
            this.x = x;
            this.y = y;
            this.waterMask = waterMask;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof State)) {
                return false;
            }

            State other = (State) object;

            return x == other.x
                    && y == other.y
                    && waterMask == other.waterMask;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, waterMask);
        }
    }

    private static final class Transition {

        private final State next;
        private final double reward;
        private final boolean terminal;

        private Transition(
                State next,
                double reward,
                boolean terminal
        ) {
            this.next = next;
            this.reward = reward;
            this.terminal = terminal;
        }
    }
}