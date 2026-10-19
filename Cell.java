// Cell.java
public class Cell {
    private boolean isWall;      // стена
    private boolean isCheese;    // большой сыр (+Z)
    private boolean isWater;     // вода (+x)
    private boolean isShock;     // электротравма (-y)

    public Cell() {
        this.isWall = false;
        this.isCheese = false;
        this.isWater = false;
        this.isShock = false;
    }

    public boolean isWall() {
        return isWall;
    }

    public void setWall(boolean wall) {
        isWall = wall;
    }

    public boolean isCheese() {
        return isCheese;
    }

    public void setCheese(boolean cheese) {
        isCheese = cheese;
    }

    public boolean isWater() {
        return isWater;
    }

    public void setWater(boolean water) {
        isWater = water;
    }

    public boolean isShock() {
        return isShock;
    }

    public void setShock(boolean shock) {
        isShock = shock;
    }

    @Override
    public String toString() {
        if (isWall) return "#";
        if (isCheese) return "C";
        if (isWater) return "W";
        if (isShock) return "E";
        return ".";
    }
}