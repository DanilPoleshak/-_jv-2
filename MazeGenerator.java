import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class MazeGenerator {

    private final Random random = new Random();

    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, // Вверх: верхняя стена
            {0, 1, 1},  // Вниз: нижняя стена
            {-1, 0, 2}, // Влево: левая стена
            {1, 0, 3}   // Вправо: правая стена
    };

    private static class Room {
        private boolean visited;

        private boolean topWall = true;
        private boolean bottomWall = true;
        private boolean leftWall = true;
        private boolean rightWall = true;
    }

    public Maze generateMazeWithWalls(
            int width,
            int height,
            double braidFactor,
            int targetWater
    ) {
        if (width < 5 || height < 5) {
            throw new IllegalArgumentException(
                    "Минимальный размер лабиринта: 5 x 5."
            );
        }

        if (targetWater < 0) {
            throw new IllegalArgumentException(
                    "Количество ресурсов не может быть отрицательным."
            );
        }

        if (!Double.isFinite(braidFactor)) {
            throw new IllegalArgumentException(
                    "Параметр дополнительных соединений должен быть числом."
            );
        }

        /*
         * Сохраняем правило твоего текущего генератора:
         * чётные размеры увеличиваются на единицу.
         */
        if (width % 2 == 0) {
            width++;
        }

        if (height % 2 == 0) {
            height++;
        }

        braidFactor = Math.max(0.0, Math.min(1.0, braidFactor));

        Maze maze = new Maze(width, height);

        fillWithWalls(maze);

        int roomColumns = (width - 1) / 2;
        int roomRows = (height - 1) / 2;

        Room[][] rooms = createRooms(roomColumns, roomRows);

        // Backtracker с явным стеком.
        carveRoomsIterative(rooms, roomColumns, roomRows);

        if (braidFactor > 0.0) {
            addLoops(rooms, roomColumns, roomRows, braidFactor);
        }

        drawRoomsToMaze(maze, rooms, roomColumns, roomRows);

        // Внешний контур НЕ удаляем.
        openEntranceAndExit(maze);

        placeResources(maze, targetWater);

        return maze;
    }

    private void fillWithWalls(Maze maze) {
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                maze.getCell(x, y).setWall(true);
            }
        }
    }

    private Room[][] createRooms(int columns, int rows) {
        Room[][] rooms = new Room[rows][columns];

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                rooms[row][column] = new Room();
            }
        }

        return rooms;
    }

    private void carveRoomsIterative(
            Room[][] rooms,
            int columns,
            int rows
    ) {
        Deque<Point> stack = new ArrayDeque<>();

        rooms[0][0].visited = true;
        stack.push(new Point(0, 0));

        while (!stack.isEmpty()) {
            Point current = stack.peek();

            List<int[]> availableDirections = new ArrayList<>();

            for (int[] direction : DIRECTIONS) {
                int nextColumn = current.x + direction[0];
                int nextRow = current.y + direction[1];

                if (nextColumn < 0 || nextColumn >= columns
                        || nextRow < 0 || nextRow >= rows) {
                    continue;
                }

                if (!rooms[nextRow][nextColumn].visited) {
                    availableDirections.add(direction);
                }
            }

            if (availableDirections.isEmpty()) {
                stack.pop();
                continue;
            }

            int[] selected = availableDirections.get(
                    random.nextInt(availableDirections.size())
            );

            int nextColumn = current.x + selected[0];
            int nextRow = current.y + selected[1];

            Room currentRoom = rooms[current.y][current.x];
            Room nextRoom = rooms[nextRow][nextColumn];

            removeWallBetween(currentRoom, nextRoom, selected[2]);

            nextRoom.visited = true;
            stack.push(new Point(nextColumn, nextRow));
        }
    }

    private void removeWallBetween(
            Room current,
            Room next,
            int wallIndex
    ) {
        switch (wallIndex) {
            case 0:
                current.topWall = false;
                next.bottomWall = false;
                break;

            case 1:
                current.bottomWall = false;
                next.topWall = false;
                break;

            case 2:
                current.leftWall = false;
                next.rightWall = false;
                break;

            case 3:
                current.rightWall = false;
                next.leftWall = false;
                break;

            default:
                throw new IllegalArgumentException(
                        "Неизвестный индекс стены: " + wallIndex
                );
        }
    }

    private boolean hasWall(Room room, int wallIndex) {
        switch (wallIndex) {
            case 0:
                return room.topWall;
            case 1:
                return room.bottomWall;
            case 2:
                return room.leftWall;
            case 3:
                return room.rightWall;
            default:
                throw new IllegalArgumentException(
                        "Неизвестный индекс стены: " + wallIndex
                );
        }
    }

    private int countWalls(Room room) {
        int count = 0;

        if (room.topWall) count++;
        if (room.bottomWall) count++;
        if (room.leftWall) count++;
        if (room.rightWall) count++;

        return count;
    }

    private void addLoops(
            Room[][] rooms,
            int columns,
            int rows,
            double braidFactor
    ) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                Room room = rooms[row][column];

                // Три стены означают один проход: тупик.
                if (countWalls(room) != 3
                        || random.nextDouble() >= braidFactor) {
                    continue;
                }

                List<int[]> candidates = new ArrayList<>();

                for (int[] direction : DIRECTIONS) {
                    int nextColumn = column + direction[0];
                    int nextRow = row + direction[1];

                    if (nextColumn < 0 || nextColumn >= columns
                            || nextRow < 0 || nextRow >= rows) {
                        continue;
                    }

                    if (hasWall(room, direction[2])) {
                        candidates.add(direction);
                    }
                }

                if (candidates.isEmpty()) {
                    continue;
                }

                int[] selected = candidates.get(
                        random.nextInt(candidates.size())
                );

                Room next = rooms[
                        row + selected[1]
                        ][
                        column + selected[0]
                        ];

                removeWallBetween(room, next, selected[2]);
            }
        }
    }

    private void drawRoomsToMaze(
            Maze maze,
            Room[][] rooms,
            int columns,
            int rows
    ) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                Room room = rooms[row][column];

                int x = column * 2 + 1;
                int y = row * 2 + 1;

                maze.getCell(x, y).setWall(false);

                // Каждую общую стену достаточно обработать один раз.
                if (!room.rightWall && column + 1 < columns) {
                    maze.getCell(x + 1, y).setWall(false);
                }

                if (!room.bottomWall && row + 1 < rows) {
                    maze.getCell(x, y + 1).setWall(false);
                }
            }
        }
    }

    private void openEntranceAndExit(Maze maze) {
        // Сыр находится в проёме левой стены: (0, 1).
        int cheeseX = maze.getExitX();
        int cheeseY = maze.getExitY();

        Cell cheeseCell = maze.getCell(cheeseX, cheeseY);

        cheeseCell.setWall(false);
        cheeseCell.setCheese(true);

        maze.getCell(cheeseX + 1, cheeseY).setWall(false);

        // Старт мыши — в проёме правой стены.
        int mouseX = maze.getWidth() - 1;
        int mouseY = maze.getHeight() - 2;

        maze.setMouseX(mouseX);
        maze.setMouseY(mouseY);

        maze.getCell(mouseX, mouseY).setWall(false);
        maze.getCell(mouseX - 1, mouseY).setWall(false);
    }

    private void placeResources(Maze maze, int targetWater) {
        List<Point> availableCells = new ArrayList<>();

        // Ресурсы размещаем только внутри контура.
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                Cell cell = maze.getCell(x, y);

                if (cell.isWall() || cell.isCheese()) {
                    continue;
                }

                // Оставляем клетки сразу у входа и сыра свободными.
                boolean nextToCheese =
                        x == maze.getExitX() + 1
                                && y == maze.getExitY();

                boolean nextToMouse =
                        x == maze.getMouseX() - 1
                                && y == maze.getMouseY();

                if (nextToCheese || nextToMouse) {
                    continue;
                }

                availableCells.add(new Point(x, y));
            }
        }

        int requiredCells = targetWater * 2;

        if (availableCells.size() < requiredCells) {
            throw new IllegalArgumentException(
                    "Для " + targetWater + " клеток воды и "
                            + targetWater + " электротравм "
                            + "недостаточно свободного места.\n"
                            + "Увеличьте размер лабиринта "
                            + "или уменьшите количество ресурсов."
            );
        }

        Collections.shuffle(availableCells, random);

        // Первые клетки — вода.
        for (int i = 0; i < targetWater; i++) {
            Point point = availableCells.get(i);

            maze.getCell(point.x, point.y).setWater(true);
        }

        // Следующие клетки — электротравмы.
        for (int i = targetWater; i < requiredCells; i++) {
            Point point = availableCells.get(i);

            maze.getCell(point.x, point.y).setShock(true);
        }
    }
}