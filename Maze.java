// Maze.java
public class Maze {
    private int width;
    private int height;
    private Cell[][] grid;

    private int mouseX;
    private int mouseY;
    private final int exitX = 0;
    private final int exitY = 1;

    // Конструктор теперь просто инициализирует сетку, подстраиваясь под генератор
    public Maze(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new Cell[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = new Cell();
            }
        }

        // Базовый спавн мыши на самом краю фактической матрицы
        this.mouseX = width - 1;
        this.mouseY = height - 1;
    }

    public int getMouseX() { return mouseX; }
    public void setMouseX(int mouseX) { this.mouseX = mouseX; }

    public int getMouseY() { return mouseY; }
    public void setMouseY(int mouseY) { this.mouseY = mouseY; }

    public int getExitX() { return exitX; }
    public int getExitY() { return exitY; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public Cell getCell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return grid[y][x];
    }

    public void print() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                System.out.print(grid[y][x] + " ");
            }
            System.out.println();
        }
    }
}
